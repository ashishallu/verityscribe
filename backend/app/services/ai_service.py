from dataclasses import dataclass


@dataclass(frozen=True)
class AIDraft:
    summary_text: str
    key_points: list[str]
    diagnoses: list[str]
    medicines: list[dict[str, str | None]]
    notes: str


class AIProvider:
    """Replaceable boundary for a self-hosted ASR/LLM provider."""

    def generate_draft(self, transcript_text: str) -> AIDraft:
        text = transcript_text.strip()
        if not text:
            raise ValueError("Transcript is empty")
        return AIDraft(
            summary_text=text,
            key_points=[text[:500]],
            diagnoses=[],
            medicines=[],
            notes="AI-generated draft; doctor review required.",
        )


ai_provider = AIProvider()
