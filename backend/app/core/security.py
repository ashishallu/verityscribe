import logging
import asyncio
import time
from functools import lru_cache

from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
import httpx
from supabase import ClientOptions, create_client
from .config import settings
from supabase import Client

bearer = HTTPBearer(auto_error=False)
logger = logging.getLogger(__name__)
SUPABASE_REQUEST_TIMEOUT_SECONDS = 8
SUPABASE_CONNECT_TIMEOUT_SECONDS = 4


def _client_options() -> ClientOptions:
    # Supabase Python defaults PostgREST calls to 120s. Bound both the
    # transport and PostgREST timeouts so abandoned requests cannot linger.
    return ClientOptions(
        postgrest_client_timeout=SUPABASE_REQUEST_TIMEOUT_SECONDS,
        httpx_client=httpx.Client(
            timeout=httpx.Timeout(SUPABASE_REQUEST_TIMEOUT_SECONDS, connect=SUPABASE_CONNECT_TIMEOUT_SECONDS)
        ),
    )


@lru_cache(maxsize=1)
def _auth_client(url: str, publishable_key: str) -> Client:
    return create_client(url, publishable_key, options=_client_options())


@lru_cache(maxsize=1)
def _role_client(url: str, service_role_key: str) -> Client:
    return create_client(url, service_role_key, options=_client_options())


async def _timed_auth_stage(stage: str, operation, timeout: int = SUPABASE_REQUEST_TIMEOUT_SECONDS):
    started = time.perf_counter()
    try:
        result = await asyncio.wait_for(asyncio.to_thread(operation), timeout=timeout)
    except Exception as exc:
        logger.warning(
            "supabase_auth stage=%s outcome=error duration_ms=%d error_type=%s",
            stage,
            round((time.perf_counter() - started) * 1000),
            type(exc).__name__,
        )
        raise
    logger.info(
        "supabase_auth stage=%s outcome=ok duration_ms=%d",
        stage,
        round((time.perf_counter() - started) * 1000),
    )
    return result

async def current_claims(credentials: HTTPAuthorizationCredentials | None = Depends(bearer)) -> dict:
    if not credentials:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Missing bearer token")

    config = settings()
    if not config.supabase_url or not config.supabase_publishable_key:
        logger.error("Supabase authentication is not configured")
        raise HTTPException(status_code=503, detail="Supabase authentication is unavailable")

    try:
        client = _auth_client(config.supabase_url, config.supabase_publishable_key)
        response = await _timed_auth_stage(
            "validate_access_token",
            lambda: client.auth.get_user(credentials.credentials),
        )
        user = response.user
        if not user:
            raise ValueError("Supabase returned no authenticated user")

        # Authorization is based on the canonical database profile, not mutable
        # raw user metadata or stale JWT claims. The service key remains inside
        # FastAPI and is never returned to the caller.
        if not config.supabase_service_role_key:
            logger.error("Supabase service role is not configured for role resolution")
            raise HTTPException(status_code=503, detail="Authorization service is unavailable")
        profile_client = _role_client(config.supabase_url, config.supabase_service_role_key)
        profile = await _timed_auth_stage(
            "resolve_profile_role",
            lambda: profile_client.table("profiles").select("role").eq("id", user.id).maybe_single().execute().data,
        )
        if not profile or not profile.get("role"):
            raise HTTPException(status_code=403, detail="User profile role is not configured")

        app_metadata = dict(user.app_metadata or {})
        app_metadata["role"] = profile["role"]
        return {
            "sub": user.id,
            "email": user.email,
            "app_metadata": app_metadata,
        }

    except asyncio.TimeoutError:
        logger.exception("Supabase access-token validation timed out")
        raise HTTPException(status_code=503, detail="Authentication service timed out")
    except HTTPException:
        raise
    except Exception:
        logger.exception("Supabase access-token validation failed")
        raise HTTPException(status_code=401, detail="Invalid access token")


def require_roles(*roles: str):
    def guard(claims: dict = Depends(current_claims)) -> dict:
        role = claims.get("app_metadata", {}).get("role")
        if role not in roles:
            raise HTTPException(status_code=403, detail="Insufficient role")
        return claims

    return guard


def _profile_client() -> Client:
    config = settings()
    if not config.supabase_url or not config.supabase_service_role_key:
        raise HTTPException(status_code=503, detail="Authorization service is unavailable")
    return create_client(config.supabase_url, config.supabase_service_role_key)


def _profile_for_claims(claims: dict) -> dict:
    profile = _profile_client().table("profiles").select("*").eq("id", claims["sub"]).maybe_single().execute().data
    if not profile:
        raise HTTPException(status_code=404, detail="Profile not found")
    return profile


def get_current_profile(claims: dict = Depends(current_claims)) -> dict:
    return _profile_for_claims(claims)


def get_current_patient(claims: dict = Depends(current_claims)) -> dict:
    profile = _profile_for_claims(claims)
    if profile.get("role") != "patient":
        raise HTTPException(status_code=403, detail="Patient access required")
    patient = _profile_client().table("patients").select("*").eq("id", claims["sub"]).maybe_single().execute().data
    if not patient:
        raise HTTPException(status_code=404, detail="Patient record not found")
    return patient


def get_current_doctor(claims: dict = Depends(current_claims)) -> dict:
    profile = _profile_for_claims(claims)
    if profile.get("role") != "doctor":
        raise HTTPException(status_code=403, detail="Doctor access required")
    doctor = _profile_client().table("doctors").select("*").eq("id", claims["sub"]).maybe_single().execute().data
    if not doctor:
        raise HTTPException(status_code=404, detail="Doctor record not found")
    return doctor
