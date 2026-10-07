from dataclasses import dataclass
from concurrent.futures import ThreadPoolExecutor
import base64
from difflib import SequenceMatcher
import json
import logging
import math
import os
import re
from time import perf_counter
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from huggingface_hub import InferenceClient


logger = logging.getLogger(__name__)


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


@dataclass(frozen=True)
class TranscriptConsensus:
    primary: str
    secondary: str
    final_text: str
    conflicts: list[str]
    reconciliation_status: str = "reconciled"
    predictions: list[ASRPrediction] | None = None


class AIProvider:
    ASR_MODEL = os.getenv("AI_ASR_PRIMARY_MODEL", "openai/whisper-large-v3-turbo")
    SECONDARY_ASR_MODEL = os.getenv("AI_ASR_SECONDARY_MODEL", "openai/whisper-large-v3")
    LLM_MODEL = "Qwen/Qwen3-8B"
    DEFAULT_ASR_MODELS = (
        # Let the Hub select a currently live provider per model. Hard-coded
        # model/provider pairs can silently become invalid as provider catalogs
        # change, leaving the whole ensemble unavailable.
        ("openai/whisper-large-v3", "auto", 1.20),
        ("openai/whisper-large-v3-turbo", "auto", 1.15),
        ("Qwen/Qwen3-ASR-1.7B", "auto", 1.15),
        ("nvidia/nemotron-3.5-asr-streaming-0.6b", "auto", 1.00),
        ("CohereLabs/cohere-transcribe-03-2026", "auto", 1.10),
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
                token = os.getenv("HF_TOKEN") or os.getenv("HUGGINGFACE_TOKEN")
                endpoint = str(item.get("endpoint", "")).strip()
                provider = str(item.get("provider", "auto")).strip().lower()
                if not endpoint and not token:
                    raise RuntimeError("HF_TOKEN is required for Hugging Face-routed ASR models")
                if not model or not math.isfinite(weight) or weight <= 0:
                    raise RuntimeError("Each ASR model requires a model id and positive weight")
                if endpoint and not endpoint.startswith(("http://", "https://")):
                    raise RuntimeError("Custom ASR endpoints must use HTTP or HTTPS")
                if not endpoint and provider not in {
                    "auto", "hf-inference", "fal-ai", "deepinfra", "together"
                }:
                    raise RuntimeError("Unsupported Hugging Face ASR provider")
                configs.append({
                    "model": model,
                    "endpoint": endpoint or None,
                    "provider": provider,
                    "weight": weight,
                })
            if len({entry["model"] for entry in configs}) != len(configs):
                raise RuntimeError("ASR model ids must be unique")
            return configs

        # Run five distinct provider-backed models by default. Custom endpoints
        # and weights can still be supplied explicitly through AI_ASR_MODELS_JSON.
        token = os.getenv("HF_TOKEN") or os.getenv("HUGGINGFACE_TOKEN")
        if not token:
            raise RuntimeError("HF_TOKEN is required for the five-model ASR ensemble")
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
        token = os.getenv("HF_TOKEN") or os.getenv("HUGGINGFACE_TOKEN")
        timeout = max(10.0, min(180.0, float(os.getenv("AI_ASR_TIMEOUT_SECONDS", "60"))))
        # Use the official client for Hugging Face's routed endpoint. It owns
        # the provider-specific ASR serialization and avoids fragile manual
        # HTTP payload construction.
        if provider_url is None or provider_url.startswith("https://router.huggingface.co/"):
            if not token:
                raise RuntimeError("Hugging Face token is not configured")
            try:
                response = InferenceClient(
                    provider=provider,
                    api_key=token,
                    timeout=timeout,
                    # The SDK receives bytes rather than a filesystem path,
                    # so it cannot infer their type. HF rejects a missing
                    # content type even for a valid WAV container.
                    headers={"Content-Type": "audio/wav"},
                ).automatic_speech_recognition(audio, model=model)
                text = getattr(response, "text", None)
                if not isinstance(text, str) or not text.strip():
                    raise RuntimeError("ASR provider returned no transcript")
                return text.strip()
            except Exception as exc:
                raise RuntimeError(
                    f"Hugging Face ASR request failed ({type(exc).__name__})"
                ) from exc
        # Hugging Face's Inference Providers ASR API accepts an explicit JSON
        # `inputs` base64 payload.  The previous raw-byte request was rejected
        # by the router with HTTP 400 even though the stored WAV itself was
        # valid, so keep this format independent of proxy MIME sniffing.
        headers = {"Content-Type": "application/json"}
        if token:
            headers["Authorization"] = f"Bearer {token}"
        payload = {"inputs": base64.b64encode(audio).decode("ascii")}
        request = Request(
            provider_url,
            data=json.dumps(payload).encode("utf-8"),
            headers=headers,
            method="POST",
        )
        try:
            with urlopen(request, timeout=timeout) as response:
                body = json.loads(response.read().decode())
            text = body.get("text") if isinstance(body, dict) else None
            if not text: raise RuntimeError("ASR provider returned no transcript")
            return str(text)
        except HTTPError as exc:
            # Keep the response body out of logs because it may contain
            # provider diagnostics related to patient audio.
            raise RuntimeError(f"ASR provider request failed (HTTP {exc.code})") from exc
        except Exception as exc:
            raise RuntimeError(
                f"ASR provider request failed ({type(exc).__name__})"
            ) from exc

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

    def transcribe_consensus(self, audio: bytes, filename: str = "recording.wav") -> TranscriptConsensus:
        configs = self._asr_model_configs()

        def provider_error_code(exc: Exception) -> str:
            """Return actionable, non-sensitive provider diagnostics."""
            chain: list[Exception] = []
            current: Exception | None = exc
            while current is not None and current not in chain:
                chain.append(current)
                current = current.__cause__ or current.__context__
            statuses = []
            names = []
            for item in chain:
                names.append(type(item).__name__.lower())
                status = getattr(item, "status_code", None)
                response = getattr(item, "response", None)
                status = status or getattr(response, "status_code", None)
                if isinstance(status, int):
                    statuses.append(status)
            status = next((code for code in statuses if code in {401, 402, 403, 404, 400, 429} or code >= 500), None)
            if status == 401:
                return "authentication_failed"
            if status == 402:
                return "provider_billing_required"
            if status == 403:
                return "inference_provider_permission_or_billing"
            if status == 404:
                return "model_provider_unavailable"
            if status == 400:
                return "invalid_audio_or_model_request"
            if status == 429:
                return "provider_rate_limited"
            if status is not None and status >= 500:
                return "provider_unavailable"
            if any("timeout" in name for name in names):
                return "provider_timeout"
            return "provider_error"

        def run_model(config: dict[str, str | float | None]) -> ASRPrediction:
            started = perf_counter()
            model = str(config["model"])
            try:
                endpoint = config.get("endpoint")
                text = self._transcribe_with(
                    audio,
                    filename,
                    str(endpoint) if endpoint else None,
                    model,
                    str(config.get("provider", "auto")),
                ).strip()
                if not text:
                    raise RuntimeError("empty_transcript")
                return ASRPrediction(model, float(config["weight"]), "success", text,
                                     int((perf_counter() - started) * 1000))
            except Exception as exc:
                error_code = provider_error_code(exc)
                logger.warning(
                    "ASR model unavailable model=%s provider=%s error_code=%s latency_ms=%d",
                    model,
                    config.get("provider", "auto"),
                    error_code,
                    int((perf_counter() - started) * 1000),
                )
                return ASRPrediction(model, float(config["weight"]), "unavailable", "",
                                     int((perf_counter() - started) * 1000),
                                     "empty_transcript" if str(exc) == "empty_transcript" else error_code)

        # Submit every configured recognizer before waiting, so wall time is
        # bounded by the slowest provider rather than the sum of all providers.
        with ThreadPoolExecutor(max_workers=len(configs)) as pool:
            predictions = list(pool.map(run_model, configs))
        successful = [item for item in predictions if item.status == "success"]
        if not successful:
            failures = ", ".join(
                f"{item.model}:{item.error_code or 'unknown'}" for item in predictions
            )
            raise RuntimeError(f"All configured ASR models were unavailable ({failures})")

        conflicts = [
            f"{item.model} did not return a transcript ({item.error_code})."
            for item in predictions if item.status != "success"
        ]
        if len(successful) == 1:
            final_text = successful[0].text
            status = "single_provider"
        elif len({self._normalized_transcript(item.text) for item in successful}) == 1:
            final_text = max(successful, key=lambda item: item.weight).text
            status = "identical"
        else:
            try:
                final_text, reconciliation_conflicts = self.reconcile_ensemble(successful)
                conflicts.extend(reconciliation_conflicts)
                status = "reconciled"
            except RuntimeError:
                final_text = self._weighted_medoid(successful)
                conflicts.append("Automated reconciliation was unavailable; weighted model agreement selected a draft for clinician review.")
                status = "fallback_selected"

        return TranscriptConsensus(
            primary=predictions[0].text if predictions[0].status == "success" else "",
            secondary=(predictions[1].text if len(predictions) > 1 and predictions[1].status == "success" else ""),
            final_text=final_text,
            conflicts=conflicts,
            reconciliation_status=status,
            predictions=predictions,
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
            "conflicts": consensus.conflicts,
            "predictions": [
                {
                    "model": item.model,
                    "weight": item.weight,
                    "status": item.status,
                    "text": item.text,
                    "latency_ms": item.latency_ms,
                    "error_code": item.error_code,
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
