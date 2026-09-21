from dataclasses import dataclass
from concurrent.futures import ThreadPoolExecutor
import base64
import json
import logging
import os
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
class TranscriptConsensus:
    primary: str
    secondary: str
    final_text: str
    conflicts: list[str]


class AIProvider:
    ASR_MODEL = "openai/whisper-large-v3-turbo"
    # Both models are served by Hugging Face Inference Providers. The former
    # Indic Conformer choice requires a separately deployed endpoint, so it
    # remains usable through AI_ASR_SECONDARY_BASE_URL but cannot be used as
    # the hosted default.
    SECONDARY_ASR_MODEL = "openai/whisper-large-v3"
    LLM_MODEL = "Qwen/Qwen3-8B"

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

    def _request(self, url: str, payload: dict, token: str | None = None) -> dict:
        headers = {"Content-Type": "application/json"}
        if token: headers["Authorization"] = f"Bearer {token}"
        request = Request(url, data=json.dumps(payload).encode(), headers=headers, method="POST")
        try:
            with urlopen(request, timeout=120) as response:
                body = json.loads(response.read().decode())
        except Exception as exc:
            raise RuntimeError("AI provider request failed") from exc
        if not isinstance(body, dict):
            raise RuntimeError("AI provider returned a non-object response")
        return body

    def _transcribe_with(self, audio: bytes, filename: str, provider_url: str, model: str) -> str:
        if not provider_url:
            raise RuntimeError("ASR provider is not configured")
        token = os.getenv("HF_TOKEN") or os.getenv("HUGGINGFACE_TOKEN")
        # Use the official client for Hugging Face's routed endpoint. It owns
        # the provider-specific ASR serialization and avoids fragile manual
        # HTTP payload construction.
        if provider_url.startswith("https://router.huggingface.co/hf-inference/"):
            if not token:
                raise RuntimeError("Hugging Face token is not configured")
            try:
                response = InferenceClient(
                    provider="hf-inference",
                    api_key=token,
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
            with urlopen(request, timeout=120) as response:
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

    def _hf_asr_url(self, model: str) -> str:
        return f"https://router.huggingface.co/hf-inference/models/{model}"

    def _hf_chat(self, prompt: str, token: str) -> str:
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
            with urlopen(request, timeout=120) as response:
                body = json.loads(response.read().decode())
            content = body["choices"][0]["message"]["content"]
            if not isinstance(content, str) or not content.strip():
                raise ValueError("empty completion")
            return content.strip()
        except Exception as exc:
            raise RuntimeError("Transcript reconciler request failed") from exc

    def transcribe_consensus(self, audio: bytes, filename: str = "recording.wav") -> TranscriptConsensus:
        token = os.getenv("HF_TOKEN") or os.getenv("HUGGINGFACE_TOKEN")
        primary_url = (os.getenv("AI_ASR_PRIMARY_BASE_URL") or
                       os.getenv("AI_ASR_BASE_URL") or
                       (self._hf_asr_url(self.ASR_MODEL) if token else ""))
        secondary_url = (os.getenv("AI_ASR_SECONDARY_BASE_URL") or
                         (self._hf_asr_url(self.SECONDARY_ASR_MODEL) if token else ""))
        if not primary_url or not secondary_url:
            raise RuntimeError("Two ASR providers are not configured")
        # Run the independent transcription agents concurrently. A draft is
        # still useful when one hosted provider is temporarily unavailable, so
        # retain a successful result instead of discarding the recording.
        primary_error: Exception | None = None
        secondary_error: Exception | None = None
        with ThreadPoolExecutor(max_workers=2) as pool:
            primary_future = pool.submit(
                self._transcribe_with, audio, filename, primary_url, self.ASR_MODEL
            )
            secondary_future = pool.submit(
                self._transcribe_with, audio, filename, secondary_url,
                self.SECONDARY_ASR_MODEL
            )
            try:
                primary = primary_future.result()
            except Exception as exc:
                primary_error = exc
                primary = ""
            try:
                secondary = secondary_future.result()
            except Exception as exc:
                secondary_error = exc
                secondary = ""
        if not primary and not secondary:
            raise RuntimeError(
                "All ASR providers failed "
                f"(primary: {primary_error}; secondary: {secondary_error})"
            ) from (primary_error or secondary_error)
        if not primary:
            primary = secondary
        if not secondary:
            secondary = primary
        conflicts: list[str] = []
        if primary_error:
            conflicts.append("Primary speech provider was unavailable; the secondary transcript is shown.")
        if secondary_error:
            conflicts.append("Secondary speech provider was unavailable; the primary transcript is shown.")
        try:
            final_text, reconciliation_conflicts = self.reconcile_transcripts(primary, secondary)
            conflicts.extend(reconciliation_conflicts)
        except RuntimeError:
            # Reconciliation improves a draft but must never block delivery of
            # an already successful speech-to-text result.
            final_text = primary
            conflicts.append("Transcript reconciliation is pending clinician review.")
        return TranscriptConsensus(primary=primary, secondary=secondary, final_text=final_text, conflicts=conflicts)

    def reconcile_transcripts(self, primary: str, secondary: str) -> tuple[str, list[str]]:
        if primary.strip() == secondary.strip():
            return primary.strip(), []
        token = os.getenv("HF_TOKEN") or os.getenv("HUGGINGFACE_TOKEN")
        provider_url = os.getenv("AI_TRANSCRIPT_RECONCILER_BASE_URL") or os.getenv("AI_LLM_BASE_URL")
        if not provider_url and not token:
            raise RuntimeError("Transcript reconciler is not configured")
        prompt = ("Compare two automatic speech-recognition transcripts from the same clinical conversation. "
                  "Return only JSON: {\"final_transcript\": string, \"conflicts\": [string]}. "
                  "Do not invent clinical facts. Retain uncertainty where audio is unclear.\n\n"
                  f"TRANSCRIPT A:\n{primary}\n\nTRANSCRIPT B:\n{secondary}")
        if provider_url:
            payload = self._request(provider_url, {"inputs": prompt, "parameters": {"return_full_text": False}}, token)
            generated = payload.get("generated_text") if isinstance(payload, dict) else None
            if not isinstance(generated, str):
                raise RuntimeError("Transcript reconciler returned an invalid response")
        else:
            generated = self._hf_chat(prompt, token)
        try:
            result = json.loads(generated.strip().removeprefix("```json").removesuffix("```"))
            final_text = str(result["final_transcript"]).strip()
            conflicts = [str(item) for item in result.get("conflicts", [])]
        except (KeyError, TypeError, ValueError, json.JSONDecodeError) as exc:
            raise RuntimeError("Transcript reconciler returned invalid JSON") from exc
        if not final_text:
            raise RuntimeError("Transcript reconciler returned no transcript")
        return final_text, conflicts

    def transcribe(self, audio: bytes, filename: str = "recording.wav") -> str:
        """Backward-compatible single-result access for existing callers."""
        return self.transcribe_consensus(audio, filename).final_text

    def answer(self, prompt: str) -> str:
        provider_url = os.getenv("AI_LLM_BASE_URL") or f"https://api-inference.huggingface.co/models/{self.LLM_MODEL}"
        token = os.getenv("HF_TOKEN") or os.getenv("HUGGINGFACE_TOKEN")
        if provider_url.startswith("https://api-inference.huggingface.co") and not token:
            raise RuntimeError("AI provider is not configured")
        payload = self._request(provider_url, {"inputs": prompt, "parameters": {"return_full_text": False}}, token)
        if isinstance(payload, dict) and isinstance(payload.get("generated_text"), str): return payload["generated_text"]
        raise RuntimeError("AI provider returned an invalid answer")


ai_provider = AIProvider()
