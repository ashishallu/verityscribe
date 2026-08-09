import logging
import time
from typing import Any

import httpx
from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials
from pydantic import BaseModel, Field
from supabase import Client, create_client

from ..core.config import settings
from ..core.security import bearer, require_roles

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/doctors", tags=["doctors"])


class DoctorProvisionRequest(BaseModel):
    email: str = Field(pattern=r"^[^@\s]+@[^@\s]+\.[^@\s]+$", max_length=320)
    first_name: str = Field(min_length=1, max_length=100)
    last_name: str = Field(min_length=1, max_length=100)
    phone: str | None = Field(default=None, max_length=30)
    hospital_id: str
    department_id: str
    license_number: str = Field(min_length=1, max_length=100)
    specialization: str = Field(min_length=1, max_length=150)
    experience_years: int = Field(ge=0, le=80)
    qualification: str = Field(min_length=1, max_length=200)
    consultation_fee_inr: float = Field(ge=0)
    is_available: bool = True


def _admin_client() -> Client:
    cfg = settings()
    if not cfg.supabase_url or not cfg.supabase_service_role_key:
        raise HTTPException(status_code=503, detail="Doctor provisioning is unavailable")
    return create_client(cfg.supabase_url, cfg.supabase_service_role_key)


def _invite_user(payload: DoctorProvisionRequest) -> str:
    cfg = settings()
    response = httpx.post(
        f"{cfg.supabase_url.rstrip('/')}/auth/v1/invite",
        headers={"apikey": cfg.supabase_service_role_key, "Authorization": f"Bearer {cfg.supabase_service_role_key}"},
        json={"email": payload.email, "data": {"role": "doctor", "first_name": payload.first_name, "last_name": payload.last_name}},
        timeout=20,
    )
    if response.status_code >= 400:
        detail = response.json().get("msg", "Unable to invite doctor") if response.headers.get("content-type", "").startswith("application/json") else "Unable to invite doctor"
        raise HTTPException(status_code=409 if response.status_code in (400, 422) else 502, detail=detail)
    user_id = response.json().get("id")
    if not user_id:
        raise RuntimeError("Supabase invitation did not return a user id")
    return user_id


@router.post("/provision", status_code=status.HTTP_201_CREATED)
def provision_doctor(payload: DoctorProvisionRequest, _: dict = Depends(require_roles("hospital_admin", "super_admin")), credentials: HTTPAuthorizationCredentials = Depends(bearer)) -> dict[str, Any]:
    client = _admin_client()
    hospital = client.table("hospitals").select("id").eq("id", payload.hospital_id).maybe_single().execute().data
    department = client.table("departments").select("id,hospital_id").eq("id", payload.department_id).maybe_single().execute().data
    if not hospital:
        raise HTTPException(status_code=422, detail="Hospital not found")
    if not department or department.get("hospital_id") != payload.hospital_id:
        raise HTTPException(status_code=422, detail="Department does not belong to the selected hospital")
    try:
        duplicate_result = (
            client.table("profiles")
            .select("id")
            .eq("email", payload.email)
            .limit(1)
            .execute()
        )
        existing = duplicate_result.data[0] if duplicate_result.data else None
    except Exception as exc:
        logger.exception("Unable to check for an existing doctor profile")
        raise HTTPException(status_code=502, detail="Unable to verify whether the doctor email already exists") from exc
    if existing:
        raise HTTPException(status_code=409, detail="A profile already exists for this email")

    user_id: str | None = None
    try:
        user_id = _invite_user(payload)
        profile = None
        for _ in range(10):
            profile = client.table("profiles").select("id,email,first_name,last_name,role,phone").eq("id", user_id).maybe_single().execute().data
            if profile:
                break
            time.sleep(0.5)
        if not profile:
            raise RuntimeError("Profile trigger did not create the invited user profile")
        profile_update = {"first_name": payload.first_name, "last_name": payload.last_name, "role": "doctor"}
        if payload.phone:
            profile_update["phone"] = payload.phone
        profile = client.table("profiles").update(profile_update).eq("id", user_id).execute().data[0]
        doctor = client.table("doctors").insert({"id": user_id, "hospital_id": payload.hospital_id, "department_id": payload.department_id, "license_number": payload.license_number, "specialization": payload.specialization, "experience_years": payload.experience_years, "qualification": payload.qualification, "consultation_fee_inr": payload.consultation_fee_inr, "is_available": payload.is_available}).execute().data[0]
        return {"data": {"profile": profile, "doctor": doctor}}
    except HTTPException:
        raise
    except Exception as exc:
        logger.exception("Doctor provisioning failed")
        if user_id:
            try:
                client.auth.admin.delete_user(user_id)
            except Exception:
                logger.exception("Failed to clean up invited Auth user %s", user_id)
        raise HTTPException(status_code=502, detail="Unable to provision doctor") from exc
