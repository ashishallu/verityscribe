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

        return {
            "sub": user.id,
            "email": user.email,
            "app_metadata": user.app_metadata or {},
        }

    except asyncio.TimeoutError:
        logger.exception("Supabase access-token validation timed out")
        raise HTTPException(status_code=503, detail="Authentication service timed out")
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
