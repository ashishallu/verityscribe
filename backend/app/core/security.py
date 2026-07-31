from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from supabase import create_client
from .config import settings

bearer = HTTPBearer(auto_error=False)

def current_claims(credentials: HTTPAuthorizationCredentials | None = Depends(bearer)) -> dict:
    if not credentials:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Missing bearer token")
    config = settings()
    if not config.supabase_url or not config.supabase_publishable_key:
        raise HTTPException(status_code=503, detail="Supabase API configuration is not configured")
    try:
        user = create_client(config.supabase_url, config.supabase_publishable_key).auth.get_user(credentials.credentials).user
        if not user:
            raise ValueError("No authenticated user returned")
        return {
            "sub": user.id,
            "email": user.email,
            "app_metadata": user.app_metadata or {},
        }
    except Exception as error:
        raise HTTPException(status_code=401, detail="Invalid access token") from error

def require_roles(*roles: str):
    def guard(claims: dict = Depends(current_claims)) -> dict:
        role = claims.get("app_metadata", {}).get("role")
        if role not in roles:
            raise HTTPException(status_code=403, detail="Insufficient role")
        return claims
    return guard
