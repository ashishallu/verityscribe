from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from jose import JWTError, jwt
from .config import settings

bearer = HTTPBearer(auto_error=False)

def current_claims(credentials: HTTPAuthorizationCredentials | None = Depends(bearer)) -> dict:
    if not credentials:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Missing bearer token")
    if not settings().supabase_jwt_secret:
        raise HTTPException(status_code=503, detail="Supabase JWT verification is not configured")
    try:
        return jwt.decode(credentials.credentials, settings().supabase_jwt_secret, algorithms=["HS256"], audience="authenticated")
    except JWTError as error:
        raise HTTPException(status_code=401, detail="Invalid access token") from error

def require_roles(*roles: str):
    def guard(claims: dict = Depends(current_claims)) -> dict:
        role = claims.get("app_metadata", {}).get("role")
        if role not in roles:
            raise HTTPException(status_code=403, detail="Insufficient role")
        return claims
    return guard
