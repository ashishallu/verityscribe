from functools import lru_cache
from pydantic_settings import BaseSettings, SettingsConfigDict


DEFAULT_CORS_ORIGINS = (
    "http://localhost:3000,"
    "http://localhost:8080,"
    "https://verityscribe-admin-portal.vercel.app"
)


class Settings(BaseSettings):
    app_name: str = "VerityScribe API"
    environment: str = "development"
    api_prefix: str = "/api/v1"
    supabase_url: str = ""
    supabase_publishable_key: str = ""
    supabase_service_role_key: str = ""
    supabase_jwt_secret: str = ""
    huggingface_token: str = ""
    cors_origins: str = DEFAULT_CORS_ORIGINS
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    @property
    def cors_origin_list(self) -> list[str]:
        """Return valid configured origins, including a safe production fallback."""
        origins = [origin.strip().rstrip("/") for origin in self.cors_origins.split(",") if origin.strip()]
        return origins or DEFAULT_CORS_ORIGINS.split(",")

@lru_cache
def settings() -> Settings:
    return Settings()
