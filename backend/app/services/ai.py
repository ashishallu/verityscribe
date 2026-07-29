"""Server-side AI service boundaries; providers are configured only by environment."""
from dataclasses import dataclass
from ..core.config import settings

@dataclass
class VerificationResult:
    confidence: float
    approved: bool
    reasons: list[str]

class SpeechService:
    async def transcribe(self, audio_path: str) -> str:
        if not settings().huggingface_token: raise RuntimeError("HUGGINGFACE_TOKEN is not configured")
        raise NotImplementedError("Connect the selected ASR provider in deployment configuration")

class VerificationService:
    async def verify(self, extraction: dict) -> VerificationResult:
        required = ("diagnosis", "medicines")
        missing = [key for key in required if not extraction.get(key)]
        return VerificationResult(confidence=0.95 if not missing else 0.45, approved=not missing, reasons=missing)

class RagService:
    async def answer(self, patient_id: str, question: str) -> dict:
        return {"patient_id": patient_id, "answer": "RAG provider not configured", "citations": [], "grounded": False}
