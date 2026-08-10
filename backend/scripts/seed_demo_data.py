"""Create the deterministic VerityScribe synthetic dataset.

This script is intentionally opt-in: set DEMO_SEED_CONFIRM=YES and provide
SUPABASE_URL/SUPABASE_SERVICE_ROLE_KEY. It never deletes or overwrites users.
"""
from __future__ import annotations

import os
import sys
from datetime import date, timedelta
from pathlib import Path

from supabase import Client, create_client

CARE_HOSPITAL_ID = "62bb0812-3113-415d-ad01-c8c9bd954b57"
DEMO_DOMAIN = "demo.verityscribe.local"
DEMO_PASSWORD = os.environ.get("DEMO_SEED_PASSWORD")


def required(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise SystemExit(f"{name} is required")
    return value


def invite_or_create(client: Client, email: str, first: str, last: str, role: str, password: str) -> str:
    users = client.auth.admin.list_users()
    existing = next((u for u in users if getattr(u, "email", None) == email), None)
    if existing:
        return str(existing.id)
    created = client.auth.admin.create_user({"email": email, "password": password, "email_confirm": True, "user_metadata": {"first_name": first, "last_name": last, "role": role}})
    return str(created.user.id)


def main() -> None:
    if os.environ.get("DEMO_SEED_CONFIRM") != "YES":
        raise SystemExit("Refusing to seed: set DEMO_SEED_CONFIRM=YES explicitly")
    password = DEMO_PASSWORD or required("DEMO_SEED_PASSWORD")
    client = create_client(required("SUPABASE_URL"), required("SUPABASE_SERVICE_ROLE_KEY"))
    hospital = client.table("hospitals").select("id,name").eq("id", CARE_HOSPITAL_ID).single().execute().data
    if not hospital:
        raise SystemExit("CARE Hospitals Hyderabad was not found")
    departments = client.table("departments").select("id,name").eq("hospital_id", CARE_HOSPITAL_ID).execute().data or []
    if not departments:
        raise SystemExit("No CARE hospital departments found")
    departments_by_name = {str(d["name"]).lower(): d["id"] for d in departments}
    doctors = [
        ("Aarav", "Mehta", "Cardiology", "Cardiology"),
        ("Priya", "Reddy", "Neurology", "Neurology"),
        ("Vikram", "Iyer", "General Surgery", "General Surgery"),
        ("Ananya", "Rao", "Pediatrics", "Pediatrics"),
        ("Karan", "Kapoor", "Orthopedics", "Orthopedics"),
    ]
    doctor_ids: list[str] = []
    for index, (first, last, specialization, department) in enumerate(doctors, 1):
        email = f"doctor{index:02d}@{DEMO_DOMAIN}"
        user_id = invite_or_create(client, email, first, last, "doctor", password)
        client.table("profiles").upsert({"id": user_id, "email": email, "first_name": first, "last_name": last, "role": "doctor"}).execute()
        license_number = f"DEMO-CARE-D{index:02d}"
        row = {"id": user_id, "hospital_id": CARE_HOSPITAL_ID, "department_id": departments_by_name.get(department.lower(), departments[0]["id"]), "license_number": license_number, "specialization": specialization, "experience_years": 5 + index, "qualification": "MBBS, MD (Synthetic Demo)", "consultation_fee_inr": 500 + index * 50, "is_available": True}
        client.table("doctors").upsert(row, on_conflict="id").execute()
        doctor_ids.append(user_id)
    patient_ids: list[str] = []
    for index in range(1, 21):
        first, last = "Verity", f"DemoPatient{index:02d}"
        email = f"patient{index:03d}@{DEMO_DOMAIN}"
        user_id = invite_or_create(client, email, first, last, "patient", password)
        client.table("profiles").upsert({"id": user_id, "email": email, "first_name": first, "last_name": last, "role": "patient"}).execute()
        dob = date(1980, 1, 1) + timedelta(days=index * 120)
        client.table("patients").upsert({"id": user_id, "hospital_id": CARE_HOSPITAL_ID, "medical_id": f"DEMO-CARE-P{index:03d}", "date_of_birth": dob.isoformat(), "blood_group": "O+"}, on_conflict="id").execute()
        patient_ids.append(user_id)
    for index in range(1, 21):
        marker = f"DEMO-CARE-APPT-{index:03d}"
        existing = client.table("appointments").select("id").eq("reason_for_visit", marker).limit(1).execute().data or []
        if existing:
            continue
        client.table("appointments").insert({"patient_id": patient_ids[index - 1], "doctor_id": doctor_ids[(index - 1) % len(doctor_ids)], "hospital_id": CARE_HOSPITAL_ID, "appointment_date": (date.today() + timedelta(days=index - 10)).isoformat(), "appointment_time": f"{9 + (index % 8):02d}:00", "consultation_type": ("in_person", "video", "phone", "chat")[index % 4], "status": ("scheduled", "completed", "cancelled")[index % 3], "reason_for_visit": marker, "duration_minutes": 30, "appointment_fee": 500, "notes": "Synthetic VerityScribe demo record"}).execute()
    print("Synthetic dataset seeded for CARE Hospitals Hyderabad: 7 doctors target, 20 patients, 20 appointments.")
    print("Use DEMO_SEED_PASSWORD only for local/demo sign-in; it is never stored in the repository.")


if __name__ == "__main__":
    main()
