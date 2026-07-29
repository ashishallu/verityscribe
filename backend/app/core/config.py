from functools import lru_cache
from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    app_name: str = "VerityScribe API"
    environment: str = "development"
    api_prefix: str = "/api/v1"
    supabase_url: str = ""
    supabase_publishable_key: str = ""
    supabase_service_role_key: str = ""
    supabase_jwt_secret: str = ""
    huggingface_token: str = ""
    cors_origins: str = "http://localhost:3000,http://localhost:8080"
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

@lru_cache
def settings() -> Settings:
    return Settings()
