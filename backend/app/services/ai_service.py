from dataclasses import dataclass
import json
import os
from urllib.request import Request, urlopen


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
    SECONDARY_ASR_MODEL = "ai4bharat/indic-conformer-600m-multilingual"
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
        headers = {"Content-Type": "application/octet-stream", "X-Filename": filename, "X-Model": model}
        if token:
            headers["Authorization"] = f"Bearer {token}"
        request = Request(provider_url, data=audio, headers=headers, method="POST")
        try:
            with urlopen(request, timeout=120) as response:
                body = json.loads(response.read().decode())
            text = body.get("text") if isinstance(body, dict) else None
            if not text: raise RuntimeError("ASR provider returned no transcript")
            return str(text)
        except Exception as exc:
            raise RuntimeError("ASR provider request failed") from exc

    def transcribe_consensus(self, audio: bytes, filename: str = "recording.wav") -> TranscriptConsensus:
        primary_url = os.getenv("AI_ASR_PRIMARY_BASE_URL") or os.getenv("AI_ASR_BASE_URL")
        secondary_url = os.getenv("AI_ASR_SECONDARY_BASE_URL")
        if not primary_url or not secondary_url:
            raise RuntimeError("Two ASR providers are not configured")
        primary = self._transcribe_with(audio, filename, primary_url, self.ASR_MODEL)
        secondary = self._transcribe_with(audio, filename, secondary_url, self.SECONDARY_ASR_MODEL)
        final_text, conflicts = self.reconcile_transcripts(primary, secondary)
        return TranscriptConsensus(primary=primary, secondary=secondary, final_text=final_text, conflicts=conflicts)

    def reconcile_transcripts(self, primary: str, secondary: str) -> tuple[str, list[str]]:
        if primary.strip() == secondary.strip():
            return primary.strip(), []
        token = os.getenv("HF_TOKEN") or os.getenv("HUGGINGFACE_TOKEN")
        provider_url = os.getenv("AI_TRANSCRIPT_RECONCILER_BASE_URL") or os.getenv("AI_LLM_BASE_URL") or f"https://api-inference.huggingface.co/models/{self.LLM_MODEL}"
        if provider_url.startswith("https://api-inference.huggingface.co") and not token:
            raise RuntimeError("Transcript reconciler is not configured")
        prompt = ("Compare two automatic speech-recognition transcripts from the same clinical conversation. "
                  "Return only JSON: {\"final_transcript\": string, \"conflicts\": [string]}. "
                  "Do not invent clinical facts. Retain uncertainty where audio is unclear.\n\n"
                  f"TRANSCRIPT A:\n{primary}\n\nTRANSCRIPT B:\n{secondary}")
        payload = self._request(provider_url, {"inputs": prompt, "parameters": {"return_full_text": False}}, token)
        generated = payload.get("generated_text") if isinstance(payload, dict) else None
        if not isinstance(generated, str):
            raise RuntimeError("Transcript reconciler returned an invalid response")
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
