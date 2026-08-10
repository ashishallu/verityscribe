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


class AIProvider:
    ASR_MODEL = "openai/whisper-large-v3-turbo"
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

    def transcribe(self, audio: bytes, filename: str = "recording.wav") -> str:
        """Call the internal Whisper service; audio storage remains separate."""
        provider_url = os.getenv("AI_ASR_BASE_URL")
        if not provider_url:
            raise RuntimeError("ASR provider is not configured")
        request = Request(provider_url, data=audio, headers={"Content-Type": "application/octet-stream", "X-Filename": filename, "X-Model": self.ASR_MODEL}, method="POST")
        try:
            with urlopen(request, timeout=120) as response:
                body = json.loads(response.read().decode())
            text = body.get("text") if isinstance(body, dict) else None
            if not text: raise RuntimeError("ASR provider returned no transcript")
            return str(text)
        except Exception as exc:
            raise RuntimeError("ASR provider request failed") from exc

    def answer(self, prompt: str) -> str:
        provider_url = os.getenv("AI_LLM_BASE_URL") or f"https://api-inference.huggingface.co/models/{self.LLM_MODEL}"
        token = os.getenv("HF_TOKEN") or os.getenv("HUGGINGFACE_TOKEN")
        if provider_url.startswith("https://api-inference.huggingface.co") and not token:
            raise RuntimeError("AI provider is not configured")
        payload = self._request(provider_url, {"inputs": prompt, "parameters": {"return_full_text": False}}, token)
        if isinstance(payload, dict) and isinstance(payload.get("generated_text"), str): return payload["generated_text"]
        raise RuntimeError("AI provider returned an invalid answer")


ai_provider = AIProvider()
