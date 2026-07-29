"""Seed only after real Supabase Auth users have been provisioned."""
from dataclasses import dataclass
from supabase import create_client
from .core.config import settings

@dataclass(frozen=True)
class SeedIdentity:
    user_id: str
    role: str
    full_name: str
    hospital_id: str | None = None

def seed_identities(identities: list[SeedIdentity]) -> None:
    """Upserts profiles and linked patient/doctor records for existing auth.users IDs only."""
    cfg = settings()
    if not cfg.supabase_service_role_key:
        raise RuntimeError("SUPABASE_SERVICE_ROLE_KEY is required for controlled server-side seeding")
    client = create_client(cfg.supabase_url, cfg.supabase_service_role_key)
    for identity in identities:
        client.table("profiles").upsert({"id": identity.user_id, "role": identity.role, "full_name": identity.full_name, "hospital_id": identity.hospital_id}).execute()
        if identity.role == "patient":
            client.table("patients").upsert({"id": identity.user_id, "medical_id": f"VS-{identity.user_id[:8].upper()}"}).execute()
        elif identity.role == "doctor":
            client.table("doctors").upsert({"id": identity.user_id, "license_number": f"LIC-{identity.user_id[:8].upper()}"}).execute()
