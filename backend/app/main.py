import logging
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from .core.config import settings
from .routers.resources import resource_router
from .middleware import RequestContextMiddleware

config = settings()
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
app = FastAPI(title=config.app_name, version="0.1.0", openapi_url=f"{config.api_prefix}/openapi.json")
app.add_middleware(CORSMiddleware, allow_origins=[x.strip() for x in config.cors_origins.split(',')], allow_credentials=True, allow_methods=["*"], allow_headers=["*"])
app.add_middleware(RequestContextMiddleware)

@app.get("/health")
async def health() -> dict: return {"status": "ok", "environment": config.environment}

for name in ("patients", "doctors", "appointments", "consultations", "prescriptions", "reports", "medicines", "insurance", "notifications", "chat", "analytics", "admin", "voice"):
    app.include_router(resource_router(name), prefix=config.api_prefix)
