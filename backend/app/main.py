import logging
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from .core.config import settings
from .routers.resources import resource_router
from .routers.doctors import router as doctors_provision_router
from .routers.integration import router as integration_router
from .middleware import RequestContextMiddleware

config = settings()
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
app = FastAPI(title=config.app_name, version="0.1.0", openapi_url=f"{config.api_prefix}/openapi.json")
app.add_middleware(RequestContextMiddleware)
# Add CORS last so it is the outermost middleware and also decorates errors.
app.add_middleware(
    CORSMiddleware,
    allow_origins=config.cors_origin_list,
    allow_credentials=True,
    allow_methods=["GET", "POST", "PATCH", "DELETE", "OPTIONS"],
    allow_headers=["Authorization", "Content-Type"],
    max_age=600,
)

@app.get("/health")
async def health() -> dict: return {"status": "ok", "environment": config.environment}

app.include_router(integration_router, prefix=config.api_prefix)
for name in ("patients", "doctors", "prescriptions", "reports", "insurance", "chat", "analytics", "admin", "voice"):
    app.include_router(resource_router(name), prefix=config.api_prefix)
app.include_router(doctors_provision_router, prefix=config.api_prefix)
