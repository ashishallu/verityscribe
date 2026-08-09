import asyncio
import logging

from fastapi import APIRouter, Depends, HTTPException, Query
from fastapi.security import HTTPAuthorizationCredentials
from fastapi.concurrency import run_in_threadpool
from ..core.security import current_claims, require_roles
from ..core.security import bearer
from ..repositories.supabase_repository import SupabaseRepository

logger = logging.getLogger(__name__)
_DATABASE_TIMEOUT_SECONDS = 20


async def _database_call(operation):
    try:
        return await asyncio.wait_for(
            run_in_threadpool(operation),
            timeout=_DATABASE_TIMEOUT_SECONDS,
        )
    except asyncio.TimeoutError:
        logger.exception("Supabase database request timed out")
        raise HTTPException(status_code=504, detail="Database request timed out")

def resource_router(resource: str, write_roles: tuple[str, ...] = ("doctor", "hospital_admin", "super_admin")) -> APIRouter:
    router = APIRouter(prefix=f"/{resource}", tags=[resource])
    @router.get("")
    async def list_resources(page: int = Query(1, ge=1), page_size: int = Query(25, ge=1, le=100), search: str | None = None, sort: str = "created_at", descending: bool = True, credentials: HTTPAuthorizationCredentials = Depends(bearer), claims: dict = Depends(current_claims)):
        return await _database_call(lambda: SupabaseRepository(resource, credentials.credentials).list(page, page_size, search, sort, descending, {}))
    @router.get("/{resource_id}")
    async def get_resource(resource_id: str, credentials: HTTPAuthorizationCredentials = Depends(bearer), claims: dict = Depends(current_claims)):
        record = await _database_call(lambda: SupabaseRepository(resource, credentials.credentials).get(resource_id))
        if not record: raise HTTPException(status_code=404, detail="Resource not found")
        return {"data": record}
    @router.post("")
    async def create_resource(payload: dict, credentials: HTTPAuthorizationCredentials = Depends(bearer), claims: dict = Depends(require_roles(*write_roles))):
        return {"data": await _database_call(lambda: SupabaseRepository(resource, credentials.credentials).create(payload))}
    @router.patch("/{resource_id}")
    async def update_resource(resource_id: str, payload: dict, credentials: HTTPAuthorizationCredentials = Depends(bearer), claims: dict = Depends(require_roles(*write_roles))):
        return {"data": await _database_call(lambda: SupabaseRepository(resource, credentials.credentials).update(resource_id, payload))}
    @router.delete("/{resource_id}")
    async def delete_resource(resource_id: str, credentials: HTTPAuthorizationCredentials = Depends(bearer), claims: dict = Depends(require_roles("hospital_admin", "super_admin"))):
        await _database_call(lambda: SupabaseRepository(resource, credentials.credentials).delete(resource_id))
        return {"data": {"id": resource_id, "deleted": True}}
    return router
