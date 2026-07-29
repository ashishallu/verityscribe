from fastapi import APIRouter, Depends, HTTPException, Query
from fastapi.security import HTTPAuthorizationCredentials
from ..core.security import current_claims, require_roles
from ..core.security import bearer
from ..repositories.supabase_repository import SupabaseRepository

def resource_router(resource: str, write_roles: tuple[str, ...] = ("doctor", "hospital_admin", "super_admin")) -> APIRouter:
    router = APIRouter(prefix=f"/{resource}", tags=[resource])
    @router.get("")
    async def list_resources(page: int = Query(1, ge=1), page_size: int = Query(25, ge=1, le=100), search: str | None = None, sort: str = "created_at", descending: bool = True, credentials: HTTPAuthorizationCredentials = Depends(bearer), claims: dict = Depends(current_claims)):
        return SupabaseRepository(resource, credentials.credentials).list(page, page_size, search, sort, descending, {})
    @router.get("/{resource_id}")
    async def get_resource(resource_id: str, credentials: HTTPAuthorizationCredentials = Depends(bearer), claims: dict = Depends(current_claims)):
        record = SupabaseRepository(resource, credentials.credentials).get(resource_id)
        if not record: raise HTTPException(status_code=404, detail="Resource not found")
        return {"data": record}
    @router.post("")
    async def create_resource(payload: dict, credentials: HTTPAuthorizationCredentials = Depends(bearer), claims: dict = Depends(require_roles(*write_roles))):
        return {"data": SupabaseRepository(resource, credentials.credentials).create(payload)}
    @router.patch("/{resource_id}")
    async def update_resource(resource_id: str, payload: dict, credentials: HTTPAuthorizationCredentials = Depends(bearer), claims: dict = Depends(require_roles(*write_roles))):
        return {"data": SupabaseRepository(resource, credentials.credentials).update(resource_id, payload)}
    @router.delete("/{resource_id}")
    async def delete_resource(resource_id: str, credentials: HTTPAuthorizationCredentials = Depends(bearer), claims: dict = Depends(require_roles("hospital_admin", "super_admin"))):
        SupabaseRepository(resource, credentials.credentials).delete(resource_id)
        return {"data": {"id": resource_id, "deleted": True}}
    return router
