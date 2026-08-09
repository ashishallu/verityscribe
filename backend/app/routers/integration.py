from datetime import date
from typing import Any

from fastapi import APIRouter, Depends, HTTPException, Query, status
from pydantic import BaseModel, Field
from supabase import Client, create_client

from ..core.config import settings
from ..core.security import current_claims, get_current_doctor, get_current_patient, get_current_profile, require_roles

router = APIRouter(tags=["integration"])


class AppointmentCreate(BaseModel):
    doctor_id: str
    hospital_id: str
    appointment_date: date
    appointment_time: str = Field(min_length=1, max_length=20)
    consultation_type: str
    reason_for_visit: str | None = None
    notes: str | None = None


class ConsultationCreate(BaseModel):
    appointment_id: str
    symptoms: str | None = None
    diagnosis: str | None = None
    treatment_plan: str | None = None
    follow_up_date: date | None = None
    consultation_type: str


def db() -> Client:
    cfg = settings()
    if not cfg.supabase_url or not cfg.supabase_service_role_key:
        raise HTTPException(status_code=503, detail="Integration service is unavailable")
    return create_client(cfg.supabase_url, cfg.supabase_service_role_key)


def _list(client: Client, table: str, page: int, page_size: int, search: str | None, hospital_id: str | None = None) -> dict[str, Any]:
    query = client.table(table).select("*", count="exact")
    if hospital_id:
        query = query.eq("hospital_id", hospital_id)
    if search:
        query = query.ilike("name", f"%{search}%")
    result = query.order("created_at", desc=True).range((page - 1) * page_size, page * page_size - 1).execute()
    return {"data": result.data or [], "meta": {"page": page, "page_size": page_size, "total": result.count or 0}}


@router.get("/hospitals")
def hospitals(page: int = Query(1, ge=1), page_size: int = Query(25, ge=1, le=100), search: str | None = None, _: dict = Depends(current_claims)):
    return _list(db(), "hospitals", page, page_size, search)


@router.get("/departments")
def departments(page: int = Query(1, ge=1), page_size: int = Query(25, ge=1, le=100), search: str | None = None, hospital_id: str | None = None, _: dict = Depends(current_claims)):
    return _list(db(), "departments", page, page_size, search, hospital_id)


@router.get("/hospitals/{hospital_id}/departments")
def hospital_departments(hospital_id: str, page: int = Query(1, ge=1), page_size: int = Query(25, ge=1, le=100), search: str | None = None, _: dict = Depends(current_claims)):
    hospital = db().table("hospitals").select("id").eq("id", hospital_id).maybe_single().execute().data
    if not hospital:
        raise HTTPException(status_code=404, detail="Hospital not found")
    return _list(db(), "departments", page, page_size, search, hospital_id)


@router.get("/doctors/directory")
def doctor_directory(page: int = Query(1, ge=1), page_size: int = Query(25, ge=1, le=100), hospital_id: str | None = None, department_id: str | None = None, _: dict = Depends(current_claims)):
    client = db()
    query = client.table("doctors").select("*, profiles!inner(id,first_name,last_name,email,phone), hospitals!inner(id,name), departments!inner(id,name,hospital_id)", count="exact").is_("deleted_at", "null").eq("is_available", True)
    if hospital_id:
        query = query.eq("hospital_id", hospital_id)
    if department_id:
        query = query.eq("department_id", department_id)
    result = query.range((page - 1) * page_size, page * page_size - 1).execute()
    data = []
    for row in result.data or []:
        profile = row.pop("profiles", {}) or {}
        hospital = row.pop("hospitals", {}) or {}
        department = row.pop("departments", {}) or {}
        data.append({**row, "first_name": profile.get("first_name"), "last_name": profile.get("last_name"), "hospital": hospital, "department": department})
    return {"data": data, "meta": {"page": page, "page_size": page_size, "total": result.count or 0}}


@router.get("/me")
def me(profile: dict = Depends(get_current_profile)):
    return {"data": profile}


@router.get("/me/patient")
def me_patient(patient: dict = Depends(get_current_patient)):
    return {"data": patient}


@router.get("/me/doctor")
def me_doctor(doctor: dict = Depends(get_current_doctor)):
    return {"data": doctor}


def _appointment_detail(client: Client, appointment: dict) -> dict[str, Any]:
    patient = client.table("profiles").select("id,first_name,last_name,email,phone").eq("id", appointment["patient_id"]).maybe_single().execute().data
    doctor = client.table("doctors").select("*,profiles!inner(id,first_name,last_name,specialization),hospitals!inner(id,name),departments!inner(id,name)").eq("id", appointment["doctor_id"]).maybe_single().execute().data
    return {"appointment": appointment, "patient": patient, "doctor": doctor}


@router.post("/appointments", status_code=status.HTTP_201_CREATED)
def create_appointment(payload: AppointmentCreate, patient: dict = Depends(get_current_patient)):
    client = db()
    doctor = client.table("doctors").select("id,hospital_id,department_id,is_available,deleted_at").eq("id", payload.doctor_id).maybe_single().execute().data
    if not doctor or doctor.get("deleted_at") or not doctor.get("is_available"):
        raise HTTPException(status_code=404, detail="Available doctor not found")
    if doctor.get("hospital_id") != payload.hospital_id:
        raise HTTPException(status_code=422, detail="Doctor does not belong to the selected hospital")
    if payload.appointment_date < date.today():
        raise HTTPException(status_code=422, detail="Appointment date cannot be in the past")
    conflict = client.table("appointments").select("id").eq("doctor_id", payload.doctor_id).eq("appointment_date", payload.appointment_date.isoformat()).eq("appointment_time", payload.appointment_time).not_.in_("status", ["cancelled", "no_show"]).limit(1).execute().data
    if conflict:
        raise HTTPException(status_code=409, detail="The selected appointment slot is unavailable")
    row = {"patient_id": patient["id"], "doctor_id": payload.doctor_id, "hospital_id": payload.hospital_id, "appointment_date": payload.appointment_date.isoformat(), "appointment_time": payload.appointment_time, "consultation_type": payload.consultation_type, "reason_for_visit": payload.reason_for_visit, "notes": payload.notes, "status": "scheduled"}
    created = client.table("appointments").insert(row).execute().data[0]
    return {"data": _appointment_detail(client, created)}


@router.get("/me/appointments")
def my_appointments(claims: dict = Depends(current_claims)):
    client = db()
    role = claims.get("app_metadata", {}).get("role")
    column = "patient_id" if role == "patient" else "doctor_id" if role == "doctor" else None
    if not column:
        raise HTTPException(status_code=403, detail="Appointment scope is not available for this role")
    result = client.table("appointments").select("*").eq(column, claims["sub"]).order("appointment_date").order("appointment_time").execute()
    return {"data": [_appointment_detail(client, row) for row in (result.data or [])]}


@router.get("/appointments")
def scoped_appointments(page: int = Query(1, ge=1), page_size: int = Query(25, ge=1, le=100), claims: dict = Depends(current_claims)):
    client = db()
    role = claims.get("app_metadata", {}).get("role")
    query = client.table("appointments").select("*", count="exact")
    if role == "hospital_admin":
        profile = get_current_profile(claims)
        if not profile.get("hospital_id"):
            raise HTTPException(status_code=403, detail="Hospital scope is not configured")
        query = query.eq("hospital_id", profile["hospital_id"])
    elif role != "super_admin":
        raise HTTPException(status_code=403, detail="Insufficient role")
    result = query.order("appointment_date", desc=True).range((page - 1) * page_size, page * page_size - 1).execute()
    return {"data": [_appointment_detail(client, row) for row in (result.data or [])], "meta": {"page": page, "page_size": page_size, "total": result.count or 0}}


@router.get("/appointments/{appointment_id}")
def appointment_detail(appointment_id: str, claims: dict = Depends(current_claims)):
    client = db()
    appointment = client.table("appointments").select("*").eq("id", appointment_id).maybe_single().execute().data
    if not appointment:
        raise HTTPException(status_code=404, detail="Appointment not found")
    role = claims.get("app_metadata", {}).get("role")
    if role == "patient" and appointment.get("patient_id") != claims["sub"]:
        raise HTTPException(status_code=403, detail="You cannot access this appointment")
    if role == "doctor" and appointment.get("doctor_id") != claims["sub"]:
        raise HTTPException(status_code=403, detail="You cannot access this appointment")
    if role not in {"patient", "doctor", "hospital_admin", "super_admin"}:
        raise HTTPException(status_code=403, detail="Insufficient role")
    return {"data": _appointment_detail(client, appointment)}


@router.post("/consultations", status_code=status.HTTP_201_CREATED)
def create_consultation(payload: ConsultationCreate, doctor: dict = Depends(get_current_doctor)):
    client = db()
    appointment = client.table("appointments").select("id,patient_id,doctor_id").eq("id", payload.appointment_id).maybe_single().execute().data
    if not appointment:
        raise HTTPException(status_code=404, detail="Appointment not found")
    if appointment.get("doctor_id") != doctor["id"]:
        raise HTTPException(status_code=403, detail="You cannot create a consultation for this appointment")
    existing = client.table("consultations").select("id").eq("appointment_id", payload.appointment_id).limit(1).execute().data
    if existing:
        raise HTTPException(status_code=409, detail="A consultation already exists for this appointment")
    created = client.table("consultations").insert({"appointment_id": payload.appointment_id, "patient_id": appointment["patient_id"], "doctor_id": doctor["id"], "symptoms": payload.symptoms, "diagnosis": payload.diagnosis, "treatment_plan": payload.treatment_plan, "follow_up_date": payload.follow_up_date.isoformat() if payload.follow_up_date else None, "consultation_type": payload.consultation_type}).execute().data[0]
    return {"data": created}


@router.get("/me/consultations")
def my_consultations(claims: dict = Depends(current_claims)):
    role = claims.get("app_metadata", {}).get("role")
    if role not in {"patient", "doctor"}:
        raise HTTPException(status_code=403, detail="Consultation scope is not available for this role")
    column = "patient_id" if role == "patient" else "doctor_id"
    result = db().table("consultations").select("*").eq(column, claims["sub"]).order("created_at", desc=True).execute()
    return {"data": result.data or []}


@router.get("/consultations")
def scoped_consultations(page: int = Query(1, ge=1), page_size: int = Query(25, ge=1, le=100), claims: dict = Depends(current_claims)):
    role = claims.get("app_metadata", {}).get("role")
    client = db()
    query = client.table("consultations").select("*", count="exact")
    if role == "hospital_admin":
        profile = get_current_profile(claims)
        if not profile.get("hospital_id"):
            raise HTTPException(status_code=403, detail="Hospital scope is not configured")
        appointments = client.table("appointments").select("id").eq("hospital_id", profile["hospital_id"]).execute().data or []
        query = query.in_("appointment_id", [item["id"] for item in appointments]) if appointments else query.limit(0)
    elif role != "super_admin":
        raise HTTPException(status_code=403, detail="Insufficient role")
    result = query.order("created_at", desc=True).range((page - 1) * page_size, page * page_size - 1).execute()
    return {"data": result.data or [], "meta": {"page": page, "page_size": page_size, "total": result.count or 0}}
