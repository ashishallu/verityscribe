"""Shared appointment expiry rules based on the scheduled appointment start."""
from datetime import datetime, timedelta, timezone
from typing import Any
from zoneinfo import ZoneInfo

from fastapi import HTTPException


APPOINTMENT_LIFETIME = timedelta(minutes=30)
APPOINTMENT_TIMEZONE = ZoneInfo("Asia/Kolkata")


def appointment_expires_at(appointment: dict[str, Any]) -> datetime:
    """Return scheduled start + 30 minutes as a UTC-aware timestamp.

    Appointment date/time values are stored as a date and a local wall-clock
    time. The current booking flow is India-local; explicit offsets in stored
    appointment_time values are honored when present.
    """
    date_value = appointment.get("appointment_date")
    time_value = appointment.get("appointment_time")
    if not isinstance(date_value, str) or not date_value.strip():
        raise ValueError("Appointment scheduled date is unavailable")
    if not isinstance(time_value, str) or not time_value.strip():
        raise ValueError("Appointment scheduled time is unavailable")
    try:
        scheduled_start = datetime.fromisoformat(
            f"{date_value.strip()}T{time_value.strip().replace('Z', '+00:00')}"
        )
    except ValueError as exc:
        raise ValueError("Appointment scheduled date or time is invalid") from exc
    if scheduled_start.tzinfo is None:
        scheduled_start = scheduled_start.replace(tzinfo=APPOINTMENT_TIMEZONE)
    return scheduled_start.astimezone(timezone.utc) + APPOINTMENT_LIFETIME


def appointment_is_expired(
    appointment: dict[str, Any], *, now: datetime | None = None,
) -> bool:
    current = now or datetime.now(timezone.utc)
    if current.tzinfo is None:
        current = current.replace(tzinfo=timezone.utc)
    return current.astimezone(timezone.utc) >= appointment_expires_at(appointment)


def ensure_appointment_not_expired(
    appointment: dict[str, Any], *, now: datetime | None = None,
) -> datetime:
    """Fail closed when the timestamp is unknown and reject at the exact boundary."""
    try:
        expires_at = appointment_expires_at(appointment)
    except ValueError as exc:
        raise HTTPException(
            status_code=503, detail="Appointment expiry could not be verified",
        ) from exc
    if appointment_is_expired(appointment, now=now):
        raise HTTPException(
            status_code=410,
            detail="This appointment expired 30 minutes after its scheduled start time",
        )
    return expires_at


def appointment_expiry_payload(appointment: dict[str, Any]) -> dict[str, Any]:
    """Add a derived, additive expires_at field without changing stored rows."""
    result = dict(appointment)
    result["expires_at"] = appointment_expires_at(appointment).isoformat()
    return result
