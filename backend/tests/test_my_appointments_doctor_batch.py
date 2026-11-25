from datetime import datetime, timedelta
from types import SimpleNamespace
from zoneinfo import ZoneInfo

from app.routers import integration


class Query:
    SESSION_COLUMNS = {"id", "appointment_id", "status", "mode", "summary", "updated_at"}

    def __init__(self, data, table_name=""):
        self.data = data
        self.table_name = table_name
        self.in_values = None

    def select(self, columns="*", **_kwargs):
        if self.table_name == "ai_consultation_sessions" and columns != "*":
            requested = {column.strip() for column in columns.split(",")}
            invalid = requested - self.SESSION_COLUMNS
            if invalid:
                raise AssertionError(f"Invalid ai_consultation_sessions columns: {sorted(invalid)}")
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
        self.appointment_query = Query(appointments, "appointments")
        self.session_query = Query(sessions, "ai_consultation_sessions")
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
        # The endpoint filters by calendar date, not appointment time. An
        # appointment earlier today remains visible for the entire IST date.
        {"id": "today-morning", "appointment_date": today.isoformat(), "appointment_time": "09:00"},
        {"id": "future", "appointment_date": (today + timedelta(days=1)).isoformat()},
    ]
    session = {
        "id": "session-1", "appointment_id": "today-morning", "status": "awaiting_doctor_review",
        "mode": "ai_chat", "summary": {"clinical_summary": "Reported symptoms"},
        "updated_at": "2026-11-25T00:00:00Z",
    }
    client = Client(rows, [session])
    monkeypatch.setattr(integration, "db", lambda: client)
    monkeypatch.setattr(
        integration, "_appointment_details",
        lambda _client, active, **_kwargs: [
            {**row, "patient": {"full_name": "Example Patient", "age": 42, "gender": "female"}}
            for row in active
        ],
    )

    response = integration.my_appointments(
        include_past=False,
        claims={"sub": "doctor-1", "app_metadata": {"role": "doctor"}},
    )

    appointments = response["data"]
    assert [row["id"] for row in appointments] == ["today-morning", "future"]
    assert appointments[0]["patient"] == {"full_name": "Example Patient", "age": 42, "gender": "female"}
    assert appointments[0]["ai_session"] == session
    assert appointments[1]["ai_session"] is None
    assert client.table_calls.count("ai_consultation_sessions") == 1
    assert client.session_query.in_values == ["today-morning", "future"]


def test_doctor_appointment_details_batch_adds_demographics_only_when_requested():
    today = datetime.now(ZoneInfo("Asia/Kolkata")).date()
    patient = {
        "id": "patient-1", "first_name": "Alex", "last_name": "Example",
        "email": "alex@example.test", "phone": "000", "date_of_birth": "1984-01-01",
        "gender": "female", "profile_image_url": None,
    }

    class RelationQuery:
        def __init__(self, rows):
            self.rows = rows
            self.ids = None

        def select(self, *_args, **_kwargs):
            return self

        def in_(self, _column, ids):
            self.ids = ids
            return self

        def execute(self):
            rows = self.rows if self.ids is None else [row for row in self.rows if row.get("id") in self.ids]
            return SimpleNamespace(data=rows)

    class RelationClient:
        def __init__(self):
            self.queries = {
                "profiles": RelationQuery([patient]),
                "doctors": RelationQuery([{"id": "doctor-1"}]),
            }

        def table(self, name):
            return self.queries[name]

    client = RelationClient()
    result = integration._appointment_details(
        client,
        [{
            "id": "appointment-1", "patient_id": "patient-1", "doctor_id": "doctor-1",
            "appointment_date": today.isoformat(), "appointment_time": "09:00",
        }],
        include_patient_demographics=True,
    )

    assert result[0]["patient"]["full_name"] == "Alex Example"
    assert result[0]["patient"]["gender"] == "female"
    assert result[0]["patient"]["age"] == today.year - 1984 - ((today.month, today.day) < (1, 1))


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
