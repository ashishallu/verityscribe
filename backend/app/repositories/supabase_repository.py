from typing import Any
from supabase import Client, create_client
from ..core.config import settings

class SupabaseRepository:
    """RLS-aware repository. Requests use the caller's JWT; service key is never used here."""
    def __init__(self, table: str, access_token: str):
        self.table = table
        cfg = settings()
        if not cfg.supabase_url or not cfg.supabase_publishable_key:
            raise RuntimeError("Supabase API configuration is missing")
        self.client: Client = create_client(cfg.supabase_url, cfg.supabase_publishable_key)
        self.client.postgrest.auth(access_token)

    _search_columns = {
        "patients": ("medical_id", "full_name", "phone", "email"),
        "doctors": ("full_name", "specialization", "license_number"),
        "appointments": ("reason", "status"),
        "consultations": ("diagnosis", "status"),
        "reports": ("title", "report_type", "status"),
        "prescriptions": ("diagnosis", "status"),
        "medicines": ("name", "generic_name"),
        "insurance": ("provider", "policy_number", "status"),
        "notifications": ("title", "message", "status"),
    }

    def list(self, page: int, page_size: int, search: str | None, sort: str, descending: bool, filters: dict[str, str]) -> dict[str, Any]:
        query = self.client.table(self.table).select("*", count="exact")
        for column, value in filters.items():
            query = query.eq(column, value)
        if search:
            columns = self._search_columns.get(self.table, ("id",))
            query = query.or_(",".join(f"{column}.ilike.%{search}%" for column in columns))
        response = query.order(sort, desc=descending).range((page - 1) * page_size, page * page_size - 1).execute()
        return {"data": response.data, "meta": {"page": page, "page_size": page_size, "total": response.count or 0}}

    def get(self, resource_id: str) -> dict[str, Any] | None:
        response = self.client.table(self.table).select("*").eq("id", resource_id).maybe_single().execute()
        return response.data

    def create(self, payload: dict[str, Any]) -> dict[str, Any]:
        return self.client.table(self.table).insert(payload).execute().data[0]

    def update(self, resource_id: str, payload: dict[str, Any]) -> dict[str, Any]:
        return self.client.table(self.table).update(payload).eq("id", resource_id).execute().data[0]

    def delete(self, resource_id: str) -> None:
        self.client.table(self.table).delete().eq("id", resource_id).execute()
