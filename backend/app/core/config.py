from functools import lru_cache
from pathlib import Path

from pydantic import AliasChoices, Field
from pydantic_settings import BaseSettings, SettingsConfigDict


DEFAULT_CORS_ORIGINS = (
    "http://localhost:3000,"
    "http://localhost:8080,"
    "https://verityscribe-admin-portal.vercel.app"
)
BACKEND_ROOT = Path(__file__).resolve().parents[2]


class Settings(BaseSettings):
    app_name: str = "VerityScribe API"
    environment: str = "development"
    api_prefix: str = "/api/v1"
    supabase_url: str = ""
    supabase_publishable_key: str = ""
    supabase_service_role_key: str = ""
    supabase_jwt_secret: str = ""
    huggingface_token: str = ""
    google_api_key: str = ""
    cloudflare_account_id: str = ""
    cloudflare_api_token: str = ""
    cloudflare_chat_model: str = Field(
        default="@cf/qwen/qwen3.8-27b",
        validation_alias=AliasChoices("CLOUDFLARE_CHAT_MODEL", "CLOUDFLARE_SUMMARY_MODEL"),
    )
    cloudflare_summary_model: str = Field(
        default="@cf/meta/llama-3.3-70b-instruct-fp8-fast",
        validation_alias=AliasChoices(
            "AI_CONSULTATION_SUMMARY_CLOUDFLARE_MODEL", "CLOUDFLARE_SUMMARY_MODEL"
        ),
    )
    gemini_chat_model: str = Field(
        default="gemini-3.8-flash",
        validation_alias=AliasChoices("GEMINI_CHAT_MODEL", "GEMINI_MODEL"),
    )
    gemini_chat_fallback_model: str = Field(
        default="gemini-3.5-flash-lite",
        validation_alias=AliasChoices("GEMINI_CHAT_FALLBACK_MODEL", "GEMINI_FALLBACK_MODEL"),
    )
    ai_chat_timeout_seconds: float = 35.0
    # Defaults align with the current Gemini Live implementation; deployments
    # may override either model through their process environment.
    gemini_live_model: str = "gemini-3.8-live"
    gemini_live_voice: str = "Aoede"
    gemini_live_silence_duration_ms: int = 600
    gemini_summary_model: str = Field(
        default="gemini-3.8-flash",
        validation_alias=AliasChoices("GEMINI_SUMMARY_MODEL"),
    )
    gemini_summary_fallback_model: str = Field(
        default="gemini-2.5-flash-lite",
        validation_alias=AliasChoices("GEMINI_SUMMARY_FALLBACK_MODEL"),
    )
    gemini_summary_primary_timeout_seconds: float = 8.0
    gemini_summary_fallback_timeout_seconds: float = 8.0
    ai_summary_timeout_seconds: float = Field(
        default=12.0,
        validation_alias=AliasChoices("AI_SUMMARY_TIMEOUT_SECONDS"),
    )
    cors_origins: str = DEFAULT_CORS_ORIGINS
    # Resolve backend/.env regardless of caller cwd. Pydantic Settings gives
    # process environment variables precedence over values loaded from dotenv.
    model_config = SettingsConfigDict(
        env_file=BACKEND_ROOT / ".env", extra="ignore", populate_by_name=True,
    )

    @property
    def cors_origin_list(self) -> list[str]:
        """Return valid configured origins, including a safe production fallback."""
        origins = [origin.strip().rstrip("/") for origin in self.cors_origins.split(",") if origin.strip()]
        return origins or DEFAULT_CORS_ORIGINS.split(",")


@lru_cache
def settings() -> Settings:
    return Settings()
