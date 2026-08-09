from dataclasses import dataclass
import os


@dataclass(frozen=True)
class AIDraft:
    summary_text: str
    key_points: list[str]
    diagnoses: list[str]
    medicines: list[dict[str, str | None]]
    notes: str


class AIProvider:
    """Replaceable boundary for a real self-hosted ASR/LLM provider."""

    def generate_draft(self, transcript_text: str) -> AIDraft:
        if not transcript_text.strip():
            raise ValueError("Transcript is empty")
        provider_url = os.getenv("AI_LLM_BASE_URL")
        if not provider_url:
            raise RuntimeError("AI provider is not configured")
        raise RuntimeError("Configured AI provider adapter is unavailable")


ai_provider = AIProvider()
