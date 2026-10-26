from dataclasses import dataclass
from concurrent.futures import ThreadPoolExecutor
import base64
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
from .document_ocr import extract_image_text


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
    # Patient chat and attached-report Q&A use the existing Cloudflare account.
    # Keep chatbot inference off the exhausted Hugging Face paid route.
    CHAT_MODEL = "@cf/qwen/qwen3-30b-a3b-fp8"
    DEFAULT_ASR_MODELS = (
        # Four user-verified working Cloudflare routes. Keep their weights and
        # wire formats stable; Flux was removed after repeated Gateway 401s.
        ("@cf/openai/whisper", "cloudflare", 1.10),
        ("@cf/openai/whisper-tiny-en", "cloudflare", 0.80),
        ("@cf/deepgram/nova-3", "cloudflare", 1.10),
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
                raise RuntimeError("AI_ASR_MODELS_JSON must define between one and five entries")
            # Older Render overrides may still list Flux. Drop that retired
            # model so its stale setting cannot break the four working routes.
            models = [
                item for item in models
                if not isinstance(item, dict)
                or str(item.get("model", "")).strip() != "@cf/deepgram/flux"
            ]
            if not 1 <= len(models) <= 4:
                raise RuntimeError("AI_ASR_MODELS_JSON must define between one and four supported models")
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
        """Run local OCR only; document images are never sent to hosted models."""
        if content_type not in {"image/jpeg", "image/png", "image/webp"}:
            raise ValueError("Unsupported image content type for OCR")
        text = extract_image_text(image)
        if not text:
            raise ValueError("No readable text was found in this image")
        return "Extracted from uploaded document: " + text[:30000]

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

        # Bound in-flight calls while starting every configured model in parallel.
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
        timeout = max(10.0, min(60.0, float(os.getenv("AI_CHAT_TIMEOUT_SECONDS", "35"))))
        return self._cloudflare_chat(prompt, timeout=timeout, max_tokens=260)

    def _cloudflare_chat(self, prompt: str, *, timeout: float, max_tokens: int) -> str:
        """Generate patient-chat text with Workers AI, without a paid fallback."""
        account_id = os.getenv("CLOUDFLARE_ACCOUNT_ID", "").strip()
        token = os.getenv("CLOUDFLARE_API_TOKEN", "").strip()
        if not account_id or not token:
            raise RuntimeError("Cloudflare Workers AI is not configured")

        url = (
            "https://api.cloudflare.com/client/v4/accounts/"
            f"{account_id}/ai/run/{self.CHAT_MODEL}"
        )
        request = Request(
            url,
            data=json.dumps({
                "messages": [{"role": "user", "content": prompt}],
                "temperature": 0.1,
                "max_tokens": max(64, min(1000, max_tokens)),
            }).encode("utf-8"),
            headers={
                "Content-Type": "application/json",
                "Authorization": f"Bearer {token}",
            },
            method="POST",
        )
        try:
            with urlopen(request, timeout=timeout) as response:
                body = json.loads(response.read().decode("utf-8"))
        except HTTPError as exc:
            # Status is useful for quota/permission diagnosis; never propagate
            # provider response text, which might contain request data.
            raise RuntimeError(f"Cloudflare Workers AI request failed (HTTP {exc.code})") from exc
        except Exception as exc:
            raise RuntimeError(f"Cloudflare Workers AI request failed ({type(exc).__name__})") from exc

        if not isinstance(body, dict) or body.get("success") is False:
            raise RuntimeError("Cloudflare Workers AI returned an unsuccessful response")
        result = body.get("result")
        answer = result.get("response") if isinstance(result, dict) else None
        if not isinstance(answer, str) or not answer.strip():
            raise RuntimeError("Cloudflare Workers AI returned an empty answer")
        return answer.strip()


ai_provider = AIProvider()
