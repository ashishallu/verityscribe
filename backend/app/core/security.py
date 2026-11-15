import asyncio
import hashlib
import logging
import threading
import time
from concurrent.futures import Future
from functools import lru_cache

import httpx
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from supabase import Client, ClientOptions, create_client

from .config import settings

bearer = HTTPBearer(auto_error=False)
logger = logging.getLogger(__name__)
SUPABASE_REQUEST_TIMEOUT_SECONDS = 8
SUPABASE_CONNECT_TIMEOUT_SECONDS = 4
# Appointment/dashboard screens issue several authenticated requests at once.
# Coalesce the canonical role lookup briefly so that burst does not fan out into
# identical Supabase profile reads. Access tokens are still validated per request.
PROFILE_ROLE_CACHE_TTL_SECONDS = 15
AUTH_STAGE_TIMEOUT_SECONDS = 18
_profile_role_cache: dict[str, tuple[str, float]] = {}
_profile_role_locks: dict[str, threading.Lock] = {}
_profile_role_cache_lock = threading.Lock()
_auth_inflight: dict[str, Future] = {}
_auth_inflight_lock = threading.Lock()


def _client_options() -> ClientOptions:
    """Bound both the shared HTTP transport and PostgREST requests."""
    return ClientOptions(
        postgrest_client_timeout=SUPABASE_REQUEST_TIMEOUT_SECONDS,
        httpx_client=httpx.Client(
            timeout=httpx.Timeout(
                SUPABASE_REQUEST_TIMEOUT_SECONDS,
                connect=SUPABASE_CONNECT_TIMEOUT_SECONDS,
            ),
        ),
    )


@lru_cache(maxsize=1)
def _auth_client(url: str, publishable_key: str) -> Client:
    return create_client(url, publishable_key, options=_client_options())


@lru_cache(maxsize=1)
def _role_client(url: str, service_role_key: str) -> Client:
    return create_client(url, service_role_key, options=_client_options())


def _profile_role(role_client: Client, user_id: str) -> str | None:
    """Read the canonical role once per short burst of requests for a user."""
    now = time.monotonic()
    with _profile_role_cache_lock:
        cached = _profile_role_cache.get(user_id)
        if cached and cached[1] > now:
            return cached[0]
        lock = _profile_role_locks.setdefault(user_id, threading.Lock())

    # Requests for the same user share the first in-flight PostgREST lookup.
    with lock:
        now = time.monotonic()
        with _profile_role_cache_lock:
            cached = _profile_role_cache.get(user_id)
            if cached and cached[1] > now:
                return cached[0]

        profile = (
            role_client.table("profiles")
            .select("role")
            .eq("id", user_id)
            .maybe_single()
            .execute()
            .data
        )
        role = profile.get("role") if profile else None
        if role:
            with _profile_role_cache_lock:
                # Keep this process-local cache bounded in long-running services.
                if len(_profile_role_cache) >= 1024:
                    expired = [key for key, value in _profile_role_cache.items() if value[1] <= now]
                    for key in expired:
                        _profile_role_cache.pop(key, None)
                if len(_profile_role_cache) < 1024:
                    _profile_role_cache[user_id] = (
                        str(role), now + PROFILE_ROLE_CACHE_TTL_SECONDS
                    )
        return str(role) if role else None


def _clear_profile_role_cache() -> None:
    """Clear role cache for tests and explicit local process maintenance."""
    with _profile_role_cache_lock:
        _profile_role_cache.clear()
        _profile_role_locks.clear()


def _validated_user(auth_client: Client, access_token: str):
    """Share an in-flight get_user call for concurrent requests using one JWT."""
    token_digest = hashlib.sha256(access_token.encode("utf-8")).hexdigest()
    with _auth_inflight_lock:
        future = _auth_inflight.get(token_digest)
        is_owner = future is None
        if is_owner:
            future = Future()
            _auth_inflight[token_digest] = future

    if not is_owner:
        return future.result()

    try:
        user = auth_client.auth.get_user(access_token).user
        if not user:
            raise ValueError("Supabase returned no authenticated user")
        future.set_result(user)
        return user
    except BaseException as exc:
        future.set_exception(exc)
        raise
    finally:
        with _auth_inflight_lock:
            if _auth_inflight.get(token_digest) is future:
                _auth_inflight.pop(token_digest, None)


def _retry_transport_once(operation):
    """Retry one transient Supabase transport failure; never retry auth denials."""
    try:
        return operation()
    except httpx.TransportError:
        time.sleep(0.1)
        return operation()


async def _timed_auth_stage(stage: str, operation, timeout: int = AUTH_STAGE_TIMEOUT_SECONDS):
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


async def current_claims(
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer),
) -> dict:
    if not credentials:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing bearer token",
        )

    config = settings()
    if not config.supabase_url or not config.supabase_publishable_key:
        logger.error("Supabase authentication is not configured")
        raise HTTPException(status_code=503, detail="Supabase authentication is unavailable")

    try:
        auth_client = _auth_client(config.supabase_url, config.supabase_publishable_key)
        response = await _timed_auth_stage(
            "validate_access_token",
            lambda: _retry_transport_once(
                lambda: _validated_user(auth_client, credentials.credentials)
            ),
        )
        user = response

        # Resolve the canonical role from the server-side profile after the
        # access token has been validated. Never trust mutable user_metadata.
        if not config.supabase_service_role_key:
            logger.error("Supabase service role is not configured for role resolution")
            raise HTTPException(status_code=503, detail="Authorization service is unavailable")
        role_client = _role_client(config.supabase_url, config.supabase_service_role_key)
        profile_role = await _timed_auth_stage(
            "resolve_profile_role",
            lambda: _retry_transport_once(lambda: _profile_role(role_client, user.id)),
        )
        if not profile_role:
            raise HTTPException(status_code=403, detail="User profile role is not configured")

        app_metadata = dict(user.app_metadata or {})
        app_metadata["role"] = profile_role
        return {"sub": user.id, "email": user.email, "app_metadata": app_metadata}

    except asyncio.TimeoutError:
        logger.exception("Supabase access-token validation timed out")
        raise HTTPException(status_code=503, detail="Authentication service timed out")
    except httpx.TimeoutException:
        logger.exception("Supabase authentication dependency timed out")
        raise HTTPException(status_code=503, detail="Authentication service timed out")
    except httpx.TransportError:
        logger.exception("Supabase authentication dependency is unavailable")
        raise HTTPException(status_code=503, detail="Authentication service is unavailable")
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
    return _role_client(config.supabase_url, config.supabase_service_role_key)


def _profile_for_claims(claims: dict) -> dict:
    profile = (
        _profile_client()
        .table("profiles")
        .select("*")
        .eq("id", claims["sub"])
        .maybe_single()
        .execute()
        .data
    )
    if not profile:
        raise HTTPException(status_code=404, detail="Profile not found")
    return profile


def get_current_profile(claims: dict = Depends(current_claims)) -> dict:
    return _profile_for_claims(claims)


def get_current_patient(claims: dict = Depends(current_claims)) -> dict:
    # current_claims already supplies the canonical server-resolved role.
    if claims.get("app_metadata", {}).get("role") != "patient":
        raise HTTPException(status_code=403, detail="Patient access required")
    patient = (
        _profile_client()
        .table("patients")
        .select("*")
        .eq("id", claims["sub"])
        .maybe_single()
        .execute()
        .data
    )
    if not patient:
        raise HTTPException(status_code=404, detail="Patient record not found")
    return patient


def get_current_doctor(claims: dict = Depends(current_claims)) -> dict:
    if claims.get("app_metadata", {}).get("role") != "doctor":
        raise HTTPException(status_code=403, detail="Doctor access required")
    doctor = (
        _profile_client()
        .table("doctors")
        .select("*")
        .eq("id", claims["sub"])
        .maybe_single()
        .execute()
        .data
    )
    if not doctor:
        raise HTTPException(status_code=404, detail="Doctor record not found")
    return doctor
