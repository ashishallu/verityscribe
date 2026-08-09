import logging
import asyncio

from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from supabase import create_client
from .config import settings

bearer = HTTPBearer(auto_error=False)
logger = logging.getLogger(__name__)

async def current_claims(credentials: HTTPAuthorizationCredentials | None = Depends(bearer)) -> dict:
    if not credentials:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Missing bearer token")

    config = settings()
    if not config.supabase_url or not config.supabase_publishable_key:
        logger.error("Supabase authentication is not configured")
        raise HTTPException(status_code=503, detail="Supabase authentication is unavailable")

    try:
        client = create_client(config.supabase_url, config.supabase_publishable_key)
        response = await asyncio.wait_for(
            asyncio.to_thread(client.auth.get_user, credentials.credentials),
            timeout=15,
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
        profile_client = create_client(config.supabase_url, config.supabase_service_role_key)
        profile = await asyncio.wait_for(
            asyncio.to_thread(
                lambda: profile_client.table("profiles").select("role").eq("id", user.id).maybe_single().execute().data
            ),
            timeout=15,
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
