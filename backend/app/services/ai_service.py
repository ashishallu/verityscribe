from dataclasses import dataclass
from concurrent.futures import ThreadPoolExecutor
import base64
from io import BytesIO
from urllib.parse import quote
import wave
from difflib import SequenceMatcher
from datetime import datetime, timezone
import json
import logging
import math
import os
import random
import re
import traceback
from time import perf_counter
from time import sleep
from uuid import uuid4
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from huggingface_hub import InferenceClient


logger = logging.getLogger(__name__)
_ASR_EXECUTOR = ThreadPoolExecutor(max_workers=10, thread_name_prefix="cloudflare-asr")


@dataclass(frozen=True)
class AIDraft:
    summary_text: str
    key_points: list[str]
    diagnoses: list[str]
    medicines: list[dict[str, str | None]]
    notes: str


@dataclass(frozen=True)
class ASRPrediction:
    model: str
    weight: float
    status: str
    text: str
    latency_ms: int
    error_code: str | None = None
    started_at: str | None = None
    completed_at: str | None = None
    retry_count: int = 0
    diagnostic: str | None = None


@dataclass(frozen=True)
class TranscriptConsensus:
    primary: str
    secondary: str
    final_text: str
    conflicts: list[str]
    reconciliation_status: str = "reconciled"
    predictions: list[ASRPrediction] | None = None
    execution_status: str = "complete"


class ASRAllModelsFailed(RuntimeError):
    """All providers failed; keep safe per-model results for the API layer."""

    def __init__(self, predictions: list[ASRPrediction], request_id: str):
        self.predictions = predictions
        self.request_id = request_id
        failures = ", ".join(
            f"{item.model}:{item.error_code or 'provider_error'}" for item in predictions
        )
        super().__init__(f"All configured ASR models failed ({failures})")


class ASRRetryError(RuntimeError):
    def __init__(self, retry_count: int):
        self.retry_count = retry_count
        super().__init__("ASR provider request failed after bounded retries")


class AIProvider:
    ASR_MODEL = os.getenv("AI_ASR_PRIMARY_MODEL", "@cf/deepgram/nova-3")
    SECONDARY_ASR_MODEL = os.getenv("AI_ASR_SECONDARY_MODEL", "@cf/openai/whisper-large-v3-turbo")
    LLM_MODEL = "Qwen/Qwen3-8B"
    DEFAULT_ASR_MODELS = (
        # Preserve the three reported-working Cloudflare routes and their
        # existing weights. Replace the two failed Turbo routes with the
        # Cloudflare batch Turbo adapter and the distinct real-time Flux model.
        ("@cf/openai/whisper", "cloudflare", 1.10),
        ("@cf/openai/whisper-tiny-en", "cloudflare", 0.80),
        ("@cf/deepgram/nova-3", "cloudflare", 1.10),
        ("@cf/deepgram/flux", "cloudflare", 1.00),
        ("@cf/openai/whisper-large-v3-turbo", "cloudflare", 1.15),
    )

    def _asr_model_configs(self) -> list[dict[str, str | float | None]]:
        raw = os.getenv("AI_ASR_MODELS_JSON", "").strip()
        if raw:
            try:
                models = json.loads(raw)
            except json.JSONDecodeError as exc:
                raise RuntimeError("AI_ASR_MODELS_JSON must contain a JSON array") from exc
            if not isinstance(models, list) or not 1 <= len(models) <= 5:
                raise RuntimeError("AI_ASR_MODELS_JSON must define between one and five models")
            configs = []
            for item in models:
                if not isinstance(item, dict):
                    raise RuntimeError("Each ASR model entry must be an object")
                model = str(item.get("model", "")).strip()
                try:
                    weight = float(item.get("weight", 1.0))
                except (TypeError, ValueError) as exc:
                    raise RuntimeError("ASR model weights must be numeric") from exc
                endpoint = str(item.get("endpoint", "")).strip()
                provider = str(item.get("provider", "auto")).strip().lower()
                if not model or not math.isfinite(weight) or weight <= 0:
                    raise RuntimeError("Each ASR model requires a model id and positive weight")
                if endpoint or provider != "cloudflare":
                    raise RuntimeError(
                        "ASR is restricted to the configured Cloudflare Workers AI models; paid/external routes are disabled"
                    )
                if model not in {entry[0] for entry in self.DEFAULT_ASR_MODELS}:
                    raise RuntimeError("ASR model is not on the approved zero-cost Cloudflare allowlist")
                if not os.getenv("CLOUDFLARE_ACCOUNT_ID", "").strip() or not os.getenv("CLOUDFLARE_API_TOKEN", "").strip():
                    raise RuntimeError("Cloudflare ASR requires CLOUDFLARE_ACCOUNT_ID and CLOUDFLARE_API_TOKEN")
                configs.append({
                    "model": model,
                    "endpoint": None,
                    "provider": provider,
                    "weight": weight,
                })
            if len({entry["model"] for entry in configs}) != len(configs):
                raise RuntimeError("ASR model ids must be unique")
            return configs

        if not os.getenv("CLOUDFLARE_ACCOUNT_ID", "").strip() or not os.getenv("CLOUDFLARE_API_TOKEN", "").strip():
            raise RuntimeError("Cloudflare ASR requires CLOUDFLARE_ACCOUNT_ID and CLOUDFLARE_API_TOKEN")
        return [
            {
                "model": model,
                "endpoint": None,
                "provider": provider,
                "weight": weight,
            }
            for model, provider, weight in self.DEFAULT_ASR_MODELS
        ]
    def extract_document_text(self, image: bytes, content_type: str) -> str:
        """Extract image evidence using Cloudflare when configured, otherwise HF."""
        cloudflare_account_id = os.getenv("CLOUDFLARE_ACCOUNT_ID", "").strip()
        cloudflare_token = os.getenv("CLOUDFLARE_API_TOKEN", "").strip()
        if cloudflare_account_id or cloudflare_token:
            if not cloudflare_account_id or not cloudflare_token:
                raise RuntimeError(
                    "Cloudflare document extraction requires both CLOUDFLARE_ACCOUNT_ID "
                    "and CLOUDFLARE_API_TOKEN"
                )
            return self._extract_document_text_cloudflare(
                image, content_type, cloudflare_account_id, cloudflare_token
            )

        return self._extract_document_text_huggingface(image, content_type)

    def _extract_document_text_cloudflare(
        self, image: bytes, content_type: str, account_id: str, token: str
    ) -> str:
        """Send an uploaded image directly to Cloudflare Workers AI, never to the client."""
        model = os.getenv(
            "CLOUDFLARE_VISION_MODEL", "@cf/meta/llama-3.2-11b-vision-instruct"
        ).strip()
        if not model or not re.fullmatch(r"@[A-Za-z0-9._-]+/[A-Za-z0-9._/-]+", model):
            raise RuntimeError("Cloudflare vision model configuration is invalid")
        image_data_url = (
            f"data:{content_type};base64,{base64.b64encode(image).decode('ascii')}"
        )
        payload = {
            "image": image_data_url,
            "messages": [{
                "role": "user",
                "content": (
                    "Transcribe this medical document. Extract only clearly legible text, "
                    "including medicine names, doses, instructions, diagnoses, and measured "
                    "values. Preserve the document's wording. Mark uncertain handwriting "
                    "as [unclear]. Never guess, infer, or add missing facts. Return concise "
                    "plain text only."
                ),
            }],
            "temperature": 0,
            "max_tokens": 420,
        }
        url = (
            "https://api.cloudflare.com/client/v4/accounts/"
            f"{account_id}/ai/run/{model}"
        )
        request = Request(
            url,
            data=json.dumps(payload).encode("utf-8"),
            headers={
                "Authorization": f"Bearer {token}",
                "Content-Type": "application/json",
            },
            method="POST",
        )
        try:
            with urlopen(request, timeout=45) as response:
                body = json.loads(response.read().decode("utf-8"))
        except HTTPError as exc:
            # Do not include or log provider response bodies: they may echo
            # private prompt or patient-document content.
            if exc.code == 429 or exc.code >= 500:
                raise RuntimeError(
                    f"Cloudflare vision provider capacity error (HTTP {exc.code})"
                ) from exc
            raise RuntimeError(
                f"Cloudflare vision document extraction failed (HTTP {exc.code})"
            ) from exc
        except Exception as exc:
            raise RuntimeError(
                f"Cloudflare vision document extraction failed ({type(exc).__name__})"
            ) from exc

        if not isinstance(body, dict) or body.get("success") is False:
            raise RuntimeError("Cloudflare vision provider returned an unsuccessful response")
        result = body.get("result")
        text = result.get("response") if isinstance(result, dict) else result
        if not isinstance(text, str) or not text.strip():
            raise RuntimeError("Cloudflare vision provider returned no readable text")
        return "Extracted from uploaded document: " + text.strip()[:30000]

    def _extract_document_text_huggingface(
        self, image: bytes, content_type: str
    ) -> str:
        """Existing Hugging Face fallback, kept unchanged for deployments without CF keys."""
        token = os.getenv("HF_TOKEN") or os.getenv("HUGGINGFACE_TOKEN")
        if not token:
            raise RuntimeError("Hugging Face token is not configured")
        preferred_model = os.getenv("HF_DOCUMENT_VLM_MODEL", "Qwen/Qwen2.5-VL-3B-Instruct")
        # Use models listed for HF Inference Providers' VLM chat task. SmolVLM
        # is not consistently exposed by the router; a rejected fallback must
        # never hide an earlier capacity error from the upload retry handler.
        fallback_model = os.getenv("HF_DOCUMENT_VLM_FALLBACK_MODEL", "zai-org/GLM-4.5V")
        models = list(dict.fromkeys((preferred_model, fallback_model)))
        image_url = (
            f"data:{content_type};base64,"
            f"{base64.b64encode(image).decode('ascii')}"
        )
        last_error: Exception | None = None
        capacity_error: Exception | None = None
        for model in models:
            try:
                # hf-inference no longer hosts the document-QA and BLIP
                # models returned by its task defaults. Use Hugging Face's
                # automatic provider routing with a vision-language model.
                response = InferenceClient(api_key=token, timeout=20).chat_completion(
                    model=model,
                    messages=[{
                        "role": "user",
                        "content": [
                            {
                                "type": "text",
                                "text": (
                                    "Read this medical document. Return a concise transcription of only clearly "
                                    "visible names, medicine/dose/instructions, diagnoses, and measurements. "
                                    "Mark uncertain handwriting as [unclear]. Never guess or add facts."
                                ),
                            },
                            {"type": "image_url", "image_url": {"url": image_url}},
                        ],
                    }],
                    temperature=0,
                    max_tokens=420,
                )
                text = response.choices[0].message.content
                if not isinstance(text, str) or not text.strip():
                    raise RuntimeError("Vision model returned no readable text")
                return "Extracted from uploaded document: " + text.strip()[:30000]
            except Exception as exc:
                last_error = exc
                message = str(exc).lower()
                if any(marker in message for marker in ("capacity", "503", "429", "temporarily unavailable")):
                    capacity_error = exc
                # Move to the alternate model immediately; another request to
                # the same saturated provider only increases upload latency.
        # Preserve transient status across provider/model fallbacks. If Qwen
        # reports capacity and the second model is unsupported, the upload is
        # still retryable; returning the last exception would make the router
        # delete the securely stored file and report a permanent failure.
        final_error = capacity_error or last_error
        reason = "capacity_exhausted" if capacity_error else type(final_error).__name__
        raise RuntimeError(
            f"Hugging Face vision document extraction failed ({reason})"
        ) from final_error

    def generate_draft(self, transcript_text: str) -> AIDraft:
        if not transcript_text.strip():
            raise ValueError("Transcript is empty")
        provider_url = os.getenv("AI_LLM_BASE_URL") or f"https://api-inference.huggingface.co/models/{self.LLM_MODEL}"
        token = os.getenv("HF_TOKEN") or os.getenv("HUGGINGFACE_TOKEN")
        if provider_url.startswith("https://api-inference.huggingface.co") and not token:
            raise RuntimeError("AI provider is not configured")
        if not provider_url:
            raise RuntimeError("AI provider is not configured")
        prompt = (
            "Return JSON with keys summary_text, key_points, diagnoses, medicines, notes. "
            "This is an assistive clinical draft; do not state conclusions as confirmed.\n\n"
            + transcript_text.strip()
        )
        payload = self._request(provider_url, {"inputs": prompt, "parameters": {"return_full_text": False}}, token)
        try:
            return AIDraft(
                summary_text=str(payload["summary_text"]),
                key_points=[str(item) for item in payload.get("key_points", [])],
                diagnoses=[str(item) for item in payload.get("diagnoses", [])],
                medicines=[dict(item) for item in payload.get("medicines", [])],
                notes=str(payload.get("notes", "")),
            )
        except (KeyError, TypeError, ValueError) as exc:
            raise RuntimeError("AI provider returned an invalid draft") from exc

    def _request(self, url: str, payload: dict, token: str | None = None, timeout: float = 120) -> dict:
        headers = {"Content-Type": "application/json"}
        if token: headers["Authorization"] = f"Bearer {token}"
        request = Request(url, data=json.dumps(payload).encode(), headers=headers, method="POST")
        try:
            with urlopen(request, timeout=timeout) as response:
                body = json.loads(response.read().decode())
        except Exception as exc:
            raise RuntimeError("AI provider request failed") from exc
        if not isinstance(body, dict):
            raise RuntimeError("AI provider returned a non-object response")
        return body

    def _transcribe_with(
        self,
        audio: bytes,
        filename: str,
        provider_url: str | None,
        model: str,
        provider: str = "auto",
    ) -> str:
        # One stalled provider must not hold the complete ensemble for nearly
        # a minute. Keep an env override for longer recordings, but bound it.
        timeout = max(10.0, min(90.0, float(os.getenv("AI_ASR_TIMEOUT_SECONDS", "45"))))

        if provider != "cloudflare" or provider_url is not None:
            raise RuntimeError("ASR is restricted to the approved Cloudflare Workers AI routes")
        return self._transcribe_with_cloudflare(audio, model, timeout)

    def _transcribe_with_cloudflare(self, audio: bytes, model: str, timeout: float) -> str:
        account_id = os.getenv("CLOUDFLARE_ACCOUNT_ID", "").strip()
        token = os.getenv("CLOUDFLARE_API_TOKEN", "").strip()
        if not account_id or not token:
            raise RuntimeError("Cloudflare ASR credentials are not configured")

        if model == "@cf/deepgram/flux":
            return self._transcribe_flux(audio, account_id, token, timeout)

        url = (
            "https://api.cloudflare.com/client/v4/accounts/"
            f"{account_id}/ai/run/{model}"
        )
        # Keep the request formats of the three known-working models exactly
        # as they were. Cloudflare's published Turbo example specifically uses
        # Base64 audio, which fixes the previous byte-array request rejection.
        headers = {"Authorization": f"Bearer {token}"}
        if model == "@cf/deepgram/nova-3":
            # Preserve the original, user-reported-working Nova-3 wire format.
            headers["Content-Type"] = "audio/wav"
            request_body = audio
        else:
            headers["Content-Type"] = "application/json"
            payload = (
                {"audio": base64.b64encode(audio).decode("ascii")}
                if model == "@cf/openai/whisper-large-v3-turbo"
                else {"audio": list(audio)}
            )
            request_body = json.dumps(payload).encode("utf-8")
        request = Request(url, data=request_body, headers=headers, method="POST")
        try:
            with urlopen(request, timeout=timeout) as response:
                body = json.loads(response.read().decode("utf-8"))
        except HTTPError as exc:
            raise RuntimeError(f"Cloudflare ASR request failed (HTTP {exc.code})") from exc
        except Exception as exc:
            raise RuntimeError(f"Cloudflare ASR request failed ({type(exc).__name__})") from exc
        if not isinstance(body, dict) or body.get("success") is False:
            raise RuntimeError("Cloudflare ASR returned an unsuccessful response")
        result = body.get("result")
        text = result.get("text") if isinstance(result, dict) else None
        if not text and isinstance(result, dict):
            transcript_info = result.get("transcription_info")
            text = transcript_info.get("text") if isinstance(transcript_info, dict) else None
        if not text and isinstance(result, dict):
            channels = result.get("results", {}).get("channels", [])
            if channels and isinstance(channels[0], dict):
                alternatives = channels[0].get("alternatives", [])
                if alternatives and isinstance(alternatives[0], dict):
                    text = alternatives[0].get("transcript")
        if not isinstance(text, str) or not text.strip():
            raise RuntimeError("Cloudflare ASR returned no transcript")
        return text.strip()

    @staticmethod
    def _wav_pcm16_mono_16khz(audio: bytes) -> bytes:
        """Extract the app's documented PCM WAV format for Deepgram streaming."""
        try:
            with wave.open(BytesIO(audio), "rb") as wav_file:
                if (wav_file.getnchannels(), wav_file.getsampwidth(), wav_file.getframerate()) != (1, 2, 16000):
                    raise RuntimeError("Flux requires mono 16-bit PCM WAV audio at 16 kHz")
                return wav_file.readframes(wav_file.getnframes())
        except (wave.Error, EOFError) as exc:
            raise RuntimeError("Flux requires a valid PCM WAV recording") from exc

    def _transcribe_flux(self, audio: bytes, account_id: str, token: str, timeout: float) -> str:
        gateway = os.getenv("CLOUDFLARE_AI_GATEWAY", "default").strip() or "default"
        try:
            from websockets.sync.client import connect
        except ImportError as exc:
            raise RuntimeError("Cloudflare Flux requires the websockets runtime") from exc

        pcm = self._wav_pcm16_mono_16khz(audio)
        url = (
            "wss://gateway.ai.cloudflare.com/v1/"
            f"{quote(account_id, safe='')}/{quote(gateway, safe='')}/workers-ai"
            "?model=%40cf%2Fdeepgram%2Fflux&encoding=linear16&sample_rate=16000"
            "&eot_timeout_ms=1000&eot_threshold=0.5"
        )
        transcripts: list[str] = []
        try:
            with connect(
                url,
                # AI Gateway's provider-native WebSocket auth expects the raw
                # Cloudflare API token in this header (unlike REST Bearer auth).
                additional_headers={"cf-aig-authorization": token},
                open_timeout=min(timeout, 15.0),
                close_timeout=2.0,
            ) as socket:
                # 100 ms PCM chunks keep the live-stream framing modest while
                # allowing buffered recordings to be replayed without storing
                # or re-encoding additional copies of patient audio.
                chunk_bytes = 16000 * 2 // 10
                for offset in range(0, len(pcm), chunk_bytes):
                    socket.send(pcm[offset:offset + chunk_bytes])
                socket.send(json.dumps({"type": "CloseStream"}))
                deadline = perf_counter() + timeout
                while perf_counter() < deadline:
                    try:
                        raw_message = socket.recv(timeout=max(0.1, deadline - perf_counter()))
                    except TimeoutError:
                        break
                    except Exception:
                        if transcripts:
                            break
                        raise
                    if isinstance(raw_message, bytes):
                        raw_message = raw_message.decode("utf-8", errors="replace")
                    try:
                        message = json.loads(raw_message)
                    except (json.JSONDecodeError, TypeError):
                        continue
                    event = str(message.get("event") or message.get("type") or "")
                    transcript = message.get("transcript")
                    if not transcript:
                        transcript = (
                            message.get("channel", {})
                            .get("alternatives", [{}])[0]
                            .get("transcript")
                        )
                    if isinstance(transcript, str) and transcript.strip() and event in {
                        "EndOfTurn", "Results", "result"
                    }:
                        transcripts.append(transcript.strip())
                    if event in {"CloseStream", "closed"}:
                        break
        except Exception as exc:
            raise RuntimeError(f"Cloudflare Flux request failed ({type(exc).__name__})") from exc
        text = " ".join(dict.fromkeys(transcripts)).strip()
        if not text:
            raise RuntimeError("Cloudflare Flux returned no final transcript")
        return text

    @staticmethod
    def _exception_chain(exc: Exception) -> list[Exception]:
        chain: list[Exception] = []
        current: Exception | None = exc
        while current is not None and current not in chain:
            chain.append(current)
            current = current.__cause__ or current.__context__
        return chain

    @classmethod
    def _provider_status(cls, exc: Exception) -> int | None:
        for item in cls._exception_chain(exc):
            response = getattr(item, "response", None)
            status = (
                getattr(item, "status_code", None)
                or getattr(item, "status", None)
                or getattr(response, "status_code", None)
                or getattr(response, "status", None)
            )
            if isinstance(status, int):
                return status
            match = re.search(r"\b([45]\d\d)\s+(?:client|server) error\b|\bhttp\s+([45]\d\d)\b", str(item), re.I)
            if match:
                return int(next(group for group in match.groups() if group))
        return None

    @classmethod
    def _provider_error_code(cls, exc: Exception) -> str:
        chain = cls._exception_chain(exc)
        diagnostic_parts = [str(item).lower() for item in chain]
        for item in chain:
            response = getattr(item, "response", None)
            response_text = getattr(response, "text", None) if response is not None else None
            if isinstance(response_text, str):
                diagnostic_parts.append(response_text.lower())
        diagnostic = " ".join(diagnostic_parts)
        status = cls._provider_status(exc)
        if "requires cloudflare_ai_gateway" in diagnostic:
            return "provider_configuration_missing"
        if any(term in diagnostic for term in (
            "gated", "agree to share", "contact information", "accept the conditions",
        )):
            return "model_access_denied"
        if any(term in diagnostic for term in (
            "model not supported", "not supported by any provider", "unsupported model",
            "provider does not support", "unsupported task",
        )):
            return "model_unsupported"
        if any(term in diagnostic for term in (
            "invalid token", "invalid credentials", "unauthorized", "authentication failed",
        )) or status == 401:
            return "provider_authentication_failed"
        if any(term in diagnostic for term in (
            "insufficient credits", "insufficient balance", "payment required",
            "billing is required", "billing limit", "out of credits",
        )) or status == 402:
            return "provider_billing_required"
        if any(term in diagnostic for term in (
            "rate limit", "rate_limit", "too many requests", "quota exceeded",
        )) or status == 429:
            return "provider_rate_limited"
        if status in {408, 425, 504} or any(
            "timeout" in type(item).__name__.lower() for item in chain
        ):
            return "provider_timeout"
        if status in {400, 413, 415, 422}:
            return "invalid_audio_or_model_request"
        if status in {403}:
            return "provider_permission_denied"
        if status in {404}:
            return "model_or_provider_unavailable"
        if status and status >= 500:
            return "provider_server_error"
        if any(isinstance(item, ValueError) for item in chain):
            return "provider_value_error_unresolved"
        root = type(chain[-1]).__name__.lower() if chain else "unknown"
        return f"provider_{re.sub(r'[^a-z0-9]+', '_', root).strip('_')}"

    @classmethod
    def _exception_origin(cls, exc: Exception) -> str:
        """Return source location metadata only; omit exception messages/locals."""
        root = cls._exception_chain(exc)[-1]
        frames = traceback.extract_tb(root.__traceback__) if root.__traceback__ else []
        if not frames:
            return "unknown"
        frame = frames[-1]
        return f"{os.path.basename(frame.filename)}:{frame.lineno}:{frame.name}"

    @classmethod
    def _safe_provider_excerpt(cls, exc: Exception) -> str:
        """Return only a known-safe provider error clue, never arbitrary response text."""
        status = cls._provider_status(exc)
        candidates: list[str] = []
        for item in cls._exception_chain(exc):
            response = getattr(item, "response", None)
            response_text = getattr(response, "text", None) if response is not None else None
            if isinstance(response_text, str) and response_text.strip():
                # Prefer common structured provider error fields; discard the
                # rest of the body because it could echo submitted content.
                try:
                    parsed = json.loads(response_text)
                    if isinstance(parsed, dict):
                        error = parsed.get("error")
                        if isinstance(error, dict):
                            candidates.extend(str(error.get(key, "")) for key in ("code", "type", "message"))
                        elif isinstance(error, str):
                            candidates.append(error)
                        candidates.extend(str(parsed.get(key, "")) for key in ("code", "message"))
                except (ValueError, TypeError):
                    pass
            message = str(item)
            if re.search(r"model not supported|unsupported (?:model|provider|task)|gated|rate limit|invalid token", message, re.I):
                candidates.append(message)
        source = " ".join(part for part in candidates if part).strip()
        safe_clues = (
            (r"model not supported(?: by provider)?", "model not supported by selected provider"),
            (r"unsupported (?:model|provider|task)", "unsupported model/provider/task"),
            (r"gated", "model is gated"),
            (r"agree to share", "model access terms not accepted"),
            (r"invalid token", "token rejected"),
            (r"rate.?limit|too many requests", "provider rate limit reached"),
            (r"insufficient (?:credits|balance)|billing required|payment required", "provider billing/credits required"),
            (r"invalid (?:audio|request|payload)", "provider rejected request format"),
        )
        clue = next((label for pattern, label in safe_clues if re.search(pattern, source, re.I)), "provider response detail omitted")
        return f"{clue}; HTTP {status}" if status else clue

    def _transcribe_with_retries(
        self, audio: bytes, filename: str, provider_url: str | None,
        model: str, provider: str,
    ) -> tuple[str, int]:
        maximum_retries = max(0, min(2, int(os.getenv("AI_ASR_MAX_RETRIES", "1"))))
        for attempt in range(maximum_retries + 1):
            try:
                return self._transcribe_with(audio, filename, provider_url, model, provider), attempt
            except Exception as exc:
                status = self._provider_status(exc)
                # A Cloudflare 429 includes exhausted free neurons; retrying
                # cannot help before quota reset and would duplicate spend on
                # accounts that are not on Workers Free.
                transient = (status in {408, 425, 500, 502, 503, 504}
                             or (status == 429 and provider != "cloudflare")) or any(
                    "timeout" in type(item).__name__.lower()
                    or isinstance(item, (ConnectionError, TimeoutError))
                    for item in self._exception_chain(exc)
                )
                if not transient or attempt >= maximum_retries:
                    raise ASRRetryError(attempt) from exc
                delay = min(4.0, 0.5 * (2 ** attempt)) + random.uniform(0.0, 0.25)
                sleep(delay)
        raise RuntimeError("ASR retry loop ended unexpectedly")

    def _hf_chat(self, prompt: str, token: str, timeout: float = 120) -> str:
        """Call Hugging Face's OpenAI-compatible chat endpoint server-side."""
        payload = {
            "model": self.LLM_MODEL,
            "messages": [{"role": "user", "content": prompt}],
            "temperature": 0,
            "stream": False,
        }
        request = Request(
            "https://router.huggingface.co/v1/chat/completions",
            data=json.dumps(payload).encode(),
            headers={
                "Content-Type": "application/json",
                "Authorization": f"Bearer {token}",
            },
            method="POST",
        )
        try:
            with urlopen(request, timeout=timeout) as response:
                body = json.loads(response.read().decode())
            content = body["choices"][0]["message"]["content"]
            if not isinstance(content, str) or not content.strip():
                raise ValueError("empty completion")
            return content.strip()
        except Exception as exc:
            raise RuntimeError("Transcript reconciler request failed") from exc

    def transcribe_consensus(
        self, audio: bytes, filename: str = "recording.wav", request_id: str | None = None
    ) -> TranscriptConsensus:
        configs = self._asr_model_configs()
        correlation_id = request_id or str(uuid4())

        def run_model(config: dict[str, str | float | None]) -> ASRPrediction:
            started_clock = datetime.now(timezone.utc)
            started = perf_counter()
            model = str(config["model"])
            provider = str(config.get("provider", "auto"))
            retry_count = 0
            try:
                text, retry_count = self._transcribe_with_retries(
                    audio, filename,
                    str(config["endpoint"]) if config.get("endpoint") else None,
                    model, provider,
                )
                text = text.strip()
                if not text:
                    raise RuntimeError("ASR provider returned no usable transcript")
                elapsed = round((perf_counter() - started) * 1000)
                completed = datetime.now(timezone.utc)
                logger.info(
                    "ASR model complete request_id=%s model=%s provider=%s status=SUCCESS latency_ms=%d retries=%d",
                    correlation_id, model, provider, elapsed, retry_count,
                )
                return ASRPrediction(
                    model, float(config["weight"]), "SUCCESS", text, elapsed,
                    started_at=started_clock.isoformat(), completed_at=completed.isoformat(),
                    retry_count=retry_count,
                )
            except Exception as exc:
                retry_count = getattr(exc, "retry_count", retry_count)
                elapsed = round((perf_counter() - started) * 1000)
                completed = datetime.now(timezone.utc)
                code = self._provider_error_code(exc)
                status = self._provider_status(exc)
                state = "TIMEOUT" if code == "provider_timeout" else (
                    "ACCESS_DENIED" if code in {"model_access_denied", "provider_authentication_failed", "provider_permission_denied"}
                    else "UNSUPPORTED" if code == "model_unsupported"
                    else "FAILED"
                )
                diagnostic = self._safe_provider_excerpt(exc)
                origin = self._exception_origin(exc)
                logger.warning(
                    "ASR model failed request_id=%s model=%s provider=%s status=%s error_code=%s http_status=%s latency_ms=%d retries=%d origin=%s diagnostic=%s",
                    correlation_id, model, provider, state, code, status, elapsed,
                    retry_count, origin, diagnostic,
                )
                return ASRPrediction(
                    model, float(config["weight"]), state, "", elapsed, code,
                    started_clock.isoformat(), completed.isoformat(), retry_count, diagnostic,
                )

        # Bound in-flight calls while still starting all four/five models in parallel.
        predictions = list(_ASR_EXECUTOR.map(run_model, configs))
        successful = [item for item in predictions if item.status == "SUCCESS"]
        if not successful:
            raise ASRAllModelsFailed(predictions, correlation_id)

        conflicts = [
            f"{item.model} did not return a transcript ({item.error_code or item.status})."
            for item in predictions if item.status != "SUCCESS"
        ]
        if len(successful) == 1:
            final_text = successful[0].text
            reconciliation_status = "single_provider"
        elif len({self._normalized_transcript(item.text) for item in successful}) == 1:
            final_text = max(successful, key=lambda item: item.weight).text
            reconciliation_status = "identical"
        else:
            try:
                final_text, reconciliation_conflicts = self.reconcile_ensemble(successful)
                conflicts.extend(reconciliation_conflicts)
                reconciliation_status = "reconciled"
            except RuntimeError:
                final_text = self._weighted_medoid(successful)
                conflicts.append(
                    "Automated reconciliation was unavailable; model differences require clinician review."
                )
                reconciliation_status = "fallback_selected"

        return TranscriptConsensus(
            primary=predictions[0].text if predictions[0].status == "SUCCESS" else "",
            secondary=(predictions[1].text if len(predictions) > 1 and predictions[1].status == "SUCCESS" else ""),
            final_text=final_text, conflicts=conflicts,
            reconciliation_status=reconciliation_status, predictions=predictions,
            execution_status="all_success" if len(successful) == len(predictions) else "partial_success",
        )

    def reconcile_ensemble(self, predictions: list[ASRPrediction]) -> tuple[str, list[str]]:
        if len(predictions) < 2:
            raise RuntimeError("At least two successful ASR drafts are needed")
        prompt_items = [
            {"model": item.model, "weight": item.weight, "transcript": item.text}
            for item in predictions
        ]
        prompt = (
            "Reconcile the following ASR drafts from one clinical recording. Return only JSON with "
            "keys final_transcript (string) and conflicts (array of strings). Use model weights as "
            "evidence weights: a clinical detail is eligible only when at least two models support it "
            "and their combined weight is at least 55% of the total successful-model weight. Weights "
            "must never override a direct contradiction in a clinical detail. Keep the final transcript "
            "concise and faithful; remove filler and repeated phrases. Include clinical facts, medicine "
            "names, doses, measurements, and negations only when the support threshold is met. If models "
            "differ or a detail appears below that threshold, leave it out of the final transcript and "
            "name the model(s) and competing wording in conflicts. Never guess, complete unclear audio, "
            "or invent details.\n\n"
            + json.dumps(prompt_items, ensure_ascii=False)
        )
        token = os.getenv("HF_TOKEN") or os.getenv("HUGGINGFACE_TOKEN")
        provider_url = os.getenv("AI_TRANSCRIPT_RECONCILER_BASE_URL") or os.getenv("AI_LLM_BASE_URL")
        timeout = max(10.0, min(120.0, float(os.getenv("AI_TRANSCRIPT_RECONCILER_TIMEOUT_SECONDS", "35"))))
        if provider_url:
            payload = self._request(provider_url, {"inputs": prompt, "parameters": {"return_full_text": False}}, token, timeout)
            generated = payload.get("generated_text") if isinstance(payload, dict) else None
        elif token:
            generated = self._hf_chat(prompt, token, timeout=timeout)
        else:
            raise RuntimeError("Transcript reconciler is not configured")
        if not isinstance(generated, str):
            raise RuntimeError("Transcript reconciler returned an invalid response")
        try:
            match = re.search(r"\{.*\}", generated, flags=re.DOTALL)
            if not match:
                raise ValueError("no JSON object")
            result = json.loads(match.group(0))
            final_text = str(result["final_transcript"]).strip()
            conflicts = [str(item) for item in result.get("conflicts", [])]
        except (KeyError, TypeError, ValueError, json.JSONDecodeError) as exc:
            raise RuntimeError("Transcript reconciler returned invalid JSON") from exc
        if not final_text:
            raise RuntimeError("Transcript reconciler returned no transcript")
        return final_text, conflicts

    @classmethod
    def _weighted_medoid(cls, predictions: list[ASRPrediction]) -> str:
        def score(candidate: ASRPrediction) -> float:
            own = candidate.weight
            agreement = sum(
                other.weight * SequenceMatcher(
                    None,
                    cls._normalized_transcript(candidate.text),
                    cls._normalized_transcript(other.text),
                ).ratio()
                for other in predictions if other is not candidate
            )
            return own + agreement
        return max(predictions, key=score).text

    @staticmethod
    def consensus_payload(consensus: TranscriptConsensus) -> dict:
        predictions = consensus.predictions or []
        return {
            "primary_model": predictions[0].model if predictions else None,
            "secondary_model": predictions[1].model if len(predictions) > 1 else None,
            "primary_transcript": consensus.primary,
            "secondary_transcript": consensus.secondary,
            "reconciliation_status": consensus.reconciliation_status,
            "execution_status": consensus.execution_status,
            "conflicts": consensus.conflicts,
            "predictions": [
                {
                    "model": item.model,
                    "weight": item.weight,
                    "status": item.status,
                    "text": item.text,
                    "latency_ms": item.latency_ms,
                    "error_code": item.error_code,
                    "started_at": item.started_at,
                    "completed_at": item.completed_at,
                    "retry_count": item.retry_count,
                }
                for item in predictions
            ],
        }

    @staticmethod
    def failed_consensus_payload(predictions: list[ASRPrediction]) -> dict:
        """Safe operational summary for an all-model failure; no transcript/body data."""
        return {
            "execution_status": "all_failed",
            "predictions": [
                {
                    "model": item.model,
                    "weight": item.weight,
                    "status": item.status,
                    "text": "",
                    "latency_ms": item.latency_ms,
                    "error_code": item.error_code,
                    "started_at": item.started_at,
                    "completed_at": item.completed_at,
                    "retry_count": item.retry_count,
                }
                for item in predictions
            ],
        }

    def reconcile_transcripts(self, primary: str, secondary: str) -> tuple[str, list[str]]:
        predictions = [
            ASRPrediction(self.ASR_MODEL, 1.10, "success", primary, 0),
            ASRPrediction(self.SECONDARY_ASR_MODEL, 1.15, "success", secondary, 0),
        ]
        return self.reconcile_ensemble(predictions)

    @staticmethod
    def _normalized_transcript(text: str) -> str:
        """Normalize punctuation/case so equivalent ASR drafts skip extra LLM latency."""
        return " ".join(re.findall(r"[a-z0-9']+", text.lower()))

    @staticmethod
    def _best_transcript(primary: str, secondary: str) -> str:
        """Deterministically select the cleaner ASR draft if LLM reconciliation fails."""
        filler = {"um", "uh", "erm", "hmm", "ah", "like", "you", "know"}

        def score(text: str) -> tuple[int, int, int]:
            words = re.findall(r"[a-z0-9']+", text.lower())
            meaningful = [word for word in words if word not in filler]
            # More unique content and fewer filler tokens are preferable. The
            # final length component makes ties deterministic without treating
            # the first provider as inherently better.
            return (len(set(meaningful)), -sum(word in filler for word in words), len(meaningful))

        return max((primary.strip(), secondary.strip()), key=score)

    def transcribe(self, audio: bytes, filename: str = "recording.wav") -> str:
        """Backward-compatible single-result access for existing callers."""
        return self.transcribe_consensus(audio, filename).final_text

    def answer(self, prompt: str) -> str:
        token = os.getenv("HF_TOKEN") or os.getenv("HUGGINGFACE_TOKEN")
        provider_url = os.getenv("AI_LLM_BASE_URL")
        if not token and not provider_url:
            raise RuntimeError("AI provider is not configured")
        if provider_url:
            payload = self._request(provider_url, {"inputs": prompt, "parameters": {"return_full_text": False}}, token)
            if isinstance(payload, dict) and isinstance(payload.get("generated_text"), str):
                return payload["generated_text"].strip()
            raise RuntimeError("AI provider returned an invalid answer")
        try:
            response = InferenceClient(api_key=token).chat_completion(
                model=self.LLM_MODEL,
                messages=[{"role": "user", "content": prompt}],
                temperature=0.1,
                # Chat answers are intentionally brief.  Reducing completion
                # length cuts hosted inference time as well as token usage.
                max_tokens=260,
            )
            answer = response.choices[0].message.content
            if not isinstance(answer, str) or not answer.strip():
                raise RuntimeError("AI provider returned an empty answer")
            return answer.strip()
        except Exception as exc:
            raise RuntimeError(f"Hugging Face chat request failed ({type(exc).__name__})") from exc


ai_provider = AIProvider()
