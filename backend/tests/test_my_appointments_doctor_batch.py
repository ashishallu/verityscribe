from datetime import datetime, timedelta
from types import SimpleNamespace
from zoneinfo import ZoneInfo

from app.routers import integration


class Query:
    def __init__(self, data):
        self.data = data
        self.in_values = None

    def select(self, *_args):
        return self

    def eq(self, *_args):
        return self

    def order(self, *_args):
        return self

    def in_(self, _column, values):
        self.in_values = values
        return self

    def execute(self):
        return SimpleNamespace(data=self.data)


class Client:
    def __init__(self, appointments, sessions):
        self.appointment_query = Query(appointments)
        self.session_query = Query(sessions)
        self.table_calls = []

    def table(self, name):
        self.table_calls.append(name)
        return {
            "appointments": self.appointment_query,
            "ai_consultation_sessions": self.session_query,
        }[name]


def test_doctor_appointments_filters_past_and_batch_attaches_ai_sessions(monkeypatch):
    today = datetime.now(ZoneInfo("Asia/Kolkata")).date()
    rows = [
        {"id": "old", "appointment_date": (today - timedelta(days=1)).isoformat()},
        {"id": "today", "appointment_date": today.isoformat()},
        {"id": "future", "appointment_date": (today + timedelta(days=1)).isoformat()},
    ]
    session = {
        "id": "session-1", "appointment_id": "today", "status": "awaiting_doctor_review",
        "mode": "ai_chat", "summary": {"clinical_summary": "Reported symptoms"},
        "summary_generation_status": "generated", "updated_at": "2026-11-25T00:00:00Z",
    }
    client = Client(rows, [session])
    monkeypatch.setattr(integration, "db", lambda: client)
    monkeypatch.setattr(
        integration, "_appointment_details",
        lambda _client, active: [dict(row) for row in active],
    )

    response = integration.my_appointments(
        include_past=False,
        claims={"sub": "doctor-1", "app_metadata": {"role": "doctor"}},
    )

    appointments = response["data"]
    assert [row["id"] for row in appointments] == ["today", "future"]
    assert appointments[0]["ai_session"] == session
    assert appointments[1]["ai_session"] is None
    assert client.table_calls.count("ai_consultation_sessions") == 1
    assert client.session_query.in_values == ["today", "future"]


def test_patient_appointments_response_path_is_unchanged(monkeypatch):
    rows = [{"id": "past", "appointment_date": "2020-01-01"}]
    client = Client(rows, [])
    expected = [{"id": "past", "patient": {"id": "patient-1"}}]
    monkeypatch.setattr(integration, "db", lambda: client)
    monkeypatch.setattr(integration, "_appointment_details", lambda _client, active: expected)

    response = integration.my_appointments(
        include_past=False,
        claims={"sub": "patient-1", "app_metadata": {"role": "patient"}},
    )

    assert response == {"data": expected}
    assert client.table_calls == ["appointments"]
