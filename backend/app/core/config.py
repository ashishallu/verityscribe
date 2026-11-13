from functools import lru_cache
from pathlib import Path

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
    # Defaults align with the current Gemini Live implementation; deployments
    # may override either model through their process environment.
    gemini_live_model: str = "gemini-3.8-live"
    gemini_live_voice: str = "Aoede"
    gemini_live_silence_duration_ms: int = 600
    gemini_summary_model: str = "gemini-3.8-flash"
    cors_origins: str = DEFAULT_CORS_ORIGINS
    # Resolve backend/.env regardless of caller cwd. Pydantic Settings gives
    # process environment variables precedence over values loaded from dotenv.
    model_config = SettingsConfigDict(env_file=BACKEND_ROOT / ".env", extra="ignore")

    @property
    def cors_origin_list(self) -> list[str]:
        """Return valid configured origins, including a safe production fallback."""
        origins = [origin.strip().rstrip("/") for origin in self.cors_origins.split(",") if origin.strip()]
        return origins or DEFAULT_CORS_ORIGINS.split(",")


@lru_cache
def settings() -> Settings:
    return Settings()
