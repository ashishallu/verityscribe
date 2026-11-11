import logging
import os
import asyncio
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from .core.config import settings
from .routers.resources import resource_router
from .routers.doctors import router as doctors_provision_router
from .routers.integration import router as integration_router
from .routers.ai_consultations import router as ai_consultations_router
from .services.gemini_live import gemini_live
from .middleware import RequestContextMiddleware

config = settings()
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
app = FastAPI(title=config.app_name, version="0.1.0", openapi_url=f"{config.api_prefix}/openapi.json")
app.add_middleware(RequestContextMiddleware)
# Add CORS last so it is the outermost middleware and also decorates errors.
app.add_middleware(
    CORSMiddleware,
    allow_origins=config.cors_origin_list,
    allow_origin_regex=r"https?://(localhost|127\.0\.0\.1)(:\d+)?$",
    allow_credentials=True,
    allow_methods=["GET", "POST", "PATCH", "DELETE", "OPTIONS"],
    allow_headers=["Authorization", "Content-Type", "X-Upload-Id"],
    max_age=600,
)


@app.on_event("startup")
async def warm_gemini_live_agent() -> None:
    """Load the comparatively heavy ADK stack before the first patient call.

    Keep startup/health responsive; the first voice request can safely await
    the same lock-protected initializer while this background warm-up runs.
    """
    if not config.google_api_key:
        logging.getLogger(__name__).info(
            "AI Virtual warm-up skipped reason=google_api_key_not_configured",
        )
        return

    async def initialize_in_background() -> None:
        started = asyncio.get_running_loop().time()
        initialized = await asyncio.to_thread(gemini_live.initialize)
        logging.getLogger(__name__).info(
            "AI Virtual warm-up complete initialized=%s elapsed_ms=%d error_type=%s",
            initialized,
            round((asyncio.get_running_loop().time() - started) * 1000),
            gemini_live.initialization_error or "none",
        )

    app.state.gemini_live_warmup_task = asyncio.create_task(
        initialize_in_background(),
    )

@app.get("/health")
async def health() -> dict:
    """Expose a non-sensitive release marker so deploy verification is exact."""
    release = os.getenv("RENDER_GIT_COMMIT") or os.getenv("GIT_COMMIT") or "local"
    return {"status": "ok", "environment": config.environment, "release": release[:12]}

app.include_router(integration_router, prefix=config.api_prefix)
app.include_router(ai_consultations_router, prefix=config.api_prefix)
for name in ("patients", "doctors", "prescriptions", "reports", "insurance", "analytics", "admin", "voice"):
    app.include_router(resource_router(name), prefix=config.api_prefix)
app.include_router(doctors_provision_router, prefix=config.api_prefix)
