import asyncio
from datetime import datetime, timedelta, timezone
from types import SimpleNamespace
from zoneinfo import ZoneInfo

import pytest
from fastapi import HTTPException

from app.routers import ai_consultations
from app.routers import integration
from app.services.appointment_expiry import (
    appointment_expiry_payload,
    appointment_expires_at,
    appointment_is_expired,
)


LOCAL_TZ = ZoneInfo("Asia/Kolkata")


def schedule_fields(start: datetime) -> dict[str, str]:
    local = start.astimezone(LOCAL_TZ)
    return {"appointment_date": local.date().isoformat(), "appointment_time": local.strftime("%H:%M:%S")}


def test_expiry_is_thirty_minutes_from_scheduled_start_not_creation():
    scheduled = datetime(2026, 11, 10, 10, 0, tzinfo=LOCAL_TZ)
    created = scheduled - timedelta(days=60)
    appointment = {
        "created_at": created.isoformat(),
        **schedule_fields(scheduled),
    }

    assert appointment_expires_at(appointment) == scheduled.astimezone(timezone.utc) + timedelta(minutes=30)
    assert not appointment_is_expired(
        appointment, now=scheduled + timedelta(minutes=29, seconds=59)
    )
    assert appointment_is_expired(
        appointment, now=scheduled + timedelta(minutes=30)
    )


def test_naive_scheduled_time_uses_appointment_local_timezone():
    appointment = {"appointment_date": "2026-11-10", "appointment_time": "10:00"}
    assert appointment_expires_at(appointment) == datetime(
        2026, 11, 10, 5, 0, tzinfo=timezone.utc
    )


def test_response_derives_expiry_without_mutating_database_row():
    original = {"id": "appointment-1", "appointment_date": "2026-11-10", "appointment_time": "10:00"}
    enriched = appointment_expiry_payload(original)
    assert enriched["expires_at"] == "2026-11-10T05:00:00+00:00"
    assert "expires_at" not in original


def test_missing_scheduled_start_fails_closed():
    with pytest.raises(ValueError, match="scheduled"):
        appointment_expires_at({"created_at": "2026-11-10T10:00:00Z"})


def test_expired_ai_appointment_start_returns_410_before_session_creation(monkeypatch):
    start = datetime.now(timezone.utc) - timedelta(minutes=30, seconds=1)

    class Query:
        def select(self, *_args):
            return self

        def eq(self, *_args):
            return self

        def maybe_single(self):
            return self

        def execute(self):
            return SimpleNamespace(data={
                "id": "appointment-expired",
                "patient_id": "patient-1",
                "doctor_id": "doctor-1",
                "hospital_id": "hospital-1",
                "consultation_type": "ai_virtual",
                "status": "scheduled",
                "created_at": (start - timedelta(days=1)).isoformat(),
                **schedule_fields(start),
            })

    class Client:
        def table(self, _name):
            return Query()

    monkeypatch.setattr(ai_consultations, "get_current_patient", lambda _claims: {"id": "patient-1"})
    monkeypatch.setattr(ai_consultations, "db", lambda: Client())

    with pytest.raises(HTTPException) as error:
        ai_consultations.start_consultation(
            "appointment-expired",
            ai_consultations.StartRequest(consent=True, voice_consent=True),
            claims={"sub": "patient-1", "app_metadata": {"role": "patient"}},
        )

    assert error.value.status_code == 410
    assert "30 minutes" in error.value.detail


def test_expired_appointment_cannot_get_a_new_voice_ticket(monkeypatch):
    class Query:
        def select(self, *_args): return self
        def eq(self, *_args): return self
        def maybe_single(self): return self
        def execute(self):
            return SimpleNamespace(data={
                "id": "appointment-1",
                **schedule_fields(datetime.now(timezone.utc) - timedelta(minutes=31)),
            })

    class Client:
        def table(self, _name): return Query()

    monkeypatch.setattr(ai_consultations, "get_current_patient", lambda _claims: {"id": "patient-1"})
    monkeypatch.setattr(ai_consultations, "_session_or_404", lambda *_args, **_kwargs: {
        "id": "session-1", "appointment_id": "appointment-1", "mode": "ai_virtual",
        "voice_consent_at": "consented", "status": "in_progress",
    })
    monkeypatch.setattr(ai_consultations, "db", lambda: Client())

    with pytest.raises(HTTPException) as error:
        ai_consultations.create_voice_ticket("session-1", claims={"sub": "patient-1"})

    assert error.value.status_code == 410


def test_expired_appointment_cannot_bypass_expiry_with_existing_voice_ticket(monkeypatch):
    session_id = "4c7be7c6-a609-4bce-a7f5-2e23aa46edba"
    ticket_id = "c857ad16-c363-4b3d-9e80-28e631a9773c"

    class Query:
        def select(self, *_args): return self
        def eq(self, *_args): return self
        def maybe_single(self): return self
        def execute(self):
            return SimpleNamespace(data={
                "id": "appointment-1",
                **schedule_fields(datetime.now(timezone.utc) - timedelta(minutes=31)),
            })

    class Client:
        def table(self, _name): return Query()

    class Socket:
        scope = {"subprotocols": ["verityscribe-ticket.token"]}
        headers = {}
        async def close(self, **kwargs): self.close_info = kwargs

    socket = Socket()
    monkeypatch.setattr(ai_consultations, "_voice_ticket_secret", lambda: "test-secret")
    monkeypatch.setattr(ai_consultations.jwt, "decode", lambda *_args, **_kwargs: {
        "sub": "patient-1", "session_id": session_id, "jti": ticket_id,
    })
    monkeypatch.setattr(ai_consultations, "_session_or_404", lambda *_args, **_kwargs: {
        "id": session_id, "appointment_id": "appointment-1", "patient_id": "patient-1",
        "mode": "ai_virtual", "voice_consent_at": "consented", "status": "in_progress",
    })
    monkeypatch.setattr(ai_consultations, "db", lambda: Client())

    asyncio.run(ai_consultations.voice_stream(socket, session_id))

    assert socket.close_info["code"] == 4403
    assert "unavailable" in socket.close_info["reason"].lower()


def test_expired_appointment_cannot_start_doctor_consultation(monkeypatch):
    class Query:
        def select(self, *_args): return self
        def eq(self, *_args): return self
        def maybe_single(self): return self
        def execute(self):
            return SimpleNamespace(data={
                "id": "appointment-1", "patient_id": "patient-1", "doctor_id": "doctor-1",
                **schedule_fields(datetime.now(timezone.utc) - timedelta(minutes=31)),
            })

    class Client:
        def table(self, _name): return Query()

    monkeypatch.setattr(integration, "db", lambda: Client())
    with pytest.raises(HTTPException) as error:
        integration.create_consultation(
            integration.ConsultationCreate(
                appointment_id="appointment-1", consultation_type="in_person",
            ),
            doctor={"id": "doctor-1"},
        )
    assert error.value.status_code == 410


def test_expired_appointment_cannot_start_appointment_voice_draft(monkeypatch):
    class Query:
        def select(self, *_args): return self
        def eq(self, *_args): return self
        def maybe_single(self): return self
        def execute(self):
            return SimpleNamespace(data={
                "id": "appointment-1", "patient_id": "patient-1", "doctor_id": "doctor-1",
                "status": "scheduled",
                "created_at": (datetime.now(timezone.utc) - timedelta(days=1)).isoformat(),
                **schedule_fields(datetime.now(timezone.utc) - timedelta(minutes=31)),
            })

    class Client:
        def table(self, _name): return Query()

    monkeypatch.setattr(integration, "db", lambda: Client())
    with pytest.raises(HTTPException) as error:
        asyncio.run(integration.upload_patient_voice_draft(
            "appointment-1", request=None, audio=None, patient={"id": "patient-1"},
        ))
    assert error.value.status_code == 410


@pytest.mark.parametrize("mode", ["in_person", "video", "phone", "chat"])
def test_regular_consultation_modes_remain_startable_before_expiry(monkeypatch, mode):
    appointment = {
        "id": "appointment-1", "patient_id": "patient-1", "doctor_id": "doctor-1",
        "created_at": datetime.now(timezone.utc).isoformat(),
        **schedule_fields(datetime.now(timezone.utc) + timedelta(minutes=5)),
    }

    class Query:
        def __init__(self, client, table): self.client = client; self.table = table; self.inserted = False
        def select(self, *_args): return self
        def eq(self, *_args): return self
        def maybe_single(self): return self
        def limit(self, *_args): return self
        def execute(self):
            if self.table == "appointments": return SimpleNamespace(data=appointment)
            if self.table == "consultations":
                return SimpleNamespace(data=[self.client.consultation] if self.client.consultation else [])
            return SimpleNamespace(data=[self.client.consultation] if self.inserted else [])
        def insert(self, row):
            self.client.consultation = {"id": "consultation-1", **row}
            self.inserted = True
            return self

    class Client:
        consultation = None
        def table(self, name): return Query(self, name)

    monkeypatch.setattr(integration, "db", lambda: Client())
    response = integration.create_consultation(
        integration.ConsultationCreate(
            appointment_id="appointment-1", consultation_type=mode,
        ),
        doctor={"id": "doctor-1"},
    )
    assert response["data"]["consultation_type"] == mode


@pytest.mark.parametrize("mode", ["ai_chat", "ai_virtual"])
def test_ai_consultation_modes_remain_startable_before_expiry(monkeypatch, mode):
    appointment = {
        "id": "appointment-1", "patient_id": "patient-1", "doctor_id": "doctor-1",
        "hospital_id": "hospital-1", "consultation_type": mode, "status": "scheduled",
        "created_at": datetime.now(timezone.utc).isoformat(),
        **schedule_fields(datetime.now(timezone.utc) + timedelta(minutes=5)),
    }

    class Query:
        def __init__(self, client, table): self.client = client; self.table = table; self.inserted = False
        def select(self, *_args): return self
        def eq(self, *_args): return self
        def maybe_single(self): return self
        def order(self, *_args, **_kwargs): return self
        def limit(self, *_args): return self
        def execute(self):
            if self.table == "appointments": return SimpleNamespace(data=appointment)
            if self.table == "ai_consultation_sessions":
                return SimpleNamespace(data=[self.client.session] if self.inserted else self.client.session)
            if self.table == "ai_consultation_messages": return SimpleNamespace(data=[])
            return SimpleNamespace(data=[self.client.session] if self.inserted else [])
        def insert(self, row):
            self.client.session = {"id": "session-1", "status": "in_progress", "appointment_id": "appointment-1",
                        "patient_id": "patient-1", "doctor_id": "doctor-1", "hospital_id": "hospital-1",
                        "mode": mode, **row}
            self.inserted = True
            return self

    class Client:
        session = None
        def table(self, name): return Query(self, name)

    monkeypatch.setattr(ai_consultations, "get_current_patient", lambda _claims: {"id": "patient-1"})
    monkeypatch.setattr(ai_consultations, "db", lambda: Client())
    response = ai_consultations.start_consultation(
        "appointment-1",
        ai_consultations.StartRequest(consent=True, voice_consent=mode == "ai_virtual"),
        claims={"sub": "patient-1", "app_metadata": {"role": "patient"}},
    )
    assert response["data"]["session"]["mode"] == mode


def test_hangup_before_first_transcript_saves_incomplete_review_draft(monkeypatch):
    session = {"id": "session-1", "status": "in_progress", "mode": "ai_virtual"}
    writes = []

    class UpdateQuery:
        def __init__(self, row): self.row = row
        def update(self, values): self.row.update(values); writes.append(values); return self
        def eq(self, *_args): return self
        def execute(self): return SimpleNamespace(data=[self.row])

    class Client:
        def table(self, _name): return UpdateQuery(dict(session))

    monkeypatch.setattr(ai_consultations, "get_current_patient", lambda _claims: {"id": "patient-1"})
    monkeypatch.setattr(ai_consultations, "db", lambda: Client())
    monkeypatch.setattr(ai_consultations, "_session_or_404", lambda *_args, **_kwargs: dict(session))
    monkeypatch.setattr(ai_consultations, "_messages", lambda *_args: [])
    monkeypatch.setattr(ai_consultations, "_response", lambda _client, row: {"session": row, "messages": []})

    response = ai_consultations.end_consultation("session-1", claims={"sub": "patient-1"})

    saved = response["data"]["session"]
    assert saved["status"] == "awaiting_doctor_review"
    assert saved["summary"]["interview_complete"] is False
    assert saved["summary"]["interview_state"] == "ended_early"
    assert "No clinical information was recorded" in saved["summary"]["clinical_summary"]
    assert len(writes) == 1
