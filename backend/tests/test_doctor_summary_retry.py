from app.routers import ai_consultations


class _Result:
    def __init__(self, data):
        self.data = data


class _Query:
    def __init__(self, client, table):
        self.client = client
        self.table_name = table
        self.filters = {}
        self.payload = None
        self.single_result = False

    def select(self, *_args, **_kwargs):
        return self

    def update(self, payload):
        self.payload = dict(payload)
        return self

    def eq(self, key, value):
        self.filters[key] = value
        return self

    def maybe_single(self):
        self.single_result = True
        return self

    def execute(self):
        rows = self.client.tables.setdefault(self.table_name, [])
        matched = [row for row in rows if all(row.get(k) == v for k, v in self.filters.items())]
        if self.payload is not None:
            for row in matched:
                row.update(self.payload)
        return _Result((matched[0] if matched else None) if self.single_result else matched)


class _Client:
    def __init__(self, session, appointment, messages):
        self.tables = {
            "ai_consultation_sessions": [session],
            "appointments": [appointment],
            "ai_consultation_messages": messages,
            "profiles": [],
        }

    def table(self, name):
        return _Query(self, name)


def _setup(monkeypatch, *, generation_error=None):
    session = {
        "id": "session-1",
        "appointment_id": "appointment-1",
        "patient_id": "patient-1",
        "doctor_id": "doctor-1",
        "hospital_id": "hospital-1",
        "mode": "ai_chat",
        "status": "awaiting_doctor_review",
        "summary": {
            "clinical_summary": "Saved fallback draft",
            "summary_generation_status": "failed",
            "summary_error_reason": "gemini_503__cloudflare_429",
        },
    }
    appointment = {
        "id": "appointment-1",
        "doctor_id": "doctor-1",
        "patient_id": "patient-1",
        "consultation_type": "ai_chat",
    }
    messages = [{"id": "message-1", "session_id": "session-1", "role": "patient", "content": "Headache for two days."}]
    client = _Client(session, appointment, messages)
    monkeypatch.setattr(ai_consultations, "db", lambda: client)
    monkeypatch.setattr(ai_consultations, "_messages", lambda _client, _id: messages)
    monkeypatch.setattr(ai_consultations, "generate_summary", lambda *_args, **_kwargs: (_ for _ in ()).throw(generation_error) if generation_error else {"clinical_summary": "Patient reports headache for two days.", "summary_generation_provider": "gemini"})
    return client


def test_doctor_retry_regenerates_and_returns_doctor_detail_shape(monkeypatch):
    client = _setup(monkeypatch)

    result = ai_consultations.retry_doctor_consultation_summary(
        "session-1", doctor={"id": "doctor-1"}
    )

    data = result["data"]
    assert data["session"]["status"] == "awaiting_doctor_review"
    assert data["session"]["summary"]["summary_generation_status"] == "generated"
    assert data["session"]["summary"]["clinical_summary"] == "Patient reports headache for two days."
    assert data["appointment"]["id"] == "appointment-1"
    assert data["patient"] is None
    assert client.tables["ai_consultation_sessions"][0]["status"] == "awaiting_doctor_review"


def test_doctor_retry_preserves_failure_reason_when_generation_still_fails(monkeypatch):
    class ProviderFailure(RuntimeError):
        summary_error_reason = "gemini_503__cloudflare_unconfigured"

    client = _setup(monkeypatch, generation_error=ProviderFailure("provider unavailable"))

    result = ai_consultations.retry_doctor_consultation_summary(
        "session-1", doctor={"id": "doctor-1"}
    )

    summary = result["data"]["session"]["summary"]
    assert summary["summary_generation_status"] == "failed"
    assert summary["summary_error_reason"] == "gemini_503__cloudflare_unconfigured"
    assert "Headache for two days" in summary["clinical_summary"]
    assert client.tables["ai_consultation_sessions"][0]["status"] == "awaiting_doctor_review"


def test_doctor_retry_rejects_session_not_owned_by_doctor(monkeypatch):
    client = _setup(monkeypatch)
    client.tables["ai_consultation_sessions"][0]["doctor_id"] = "different-doctor"

    from fastapi import HTTPException
    try:
        ai_consultations.retry_doctor_consultation_summary(
            "session-1", doctor={"id": "doctor-1"}
        )
    except HTTPException as exc:
        assert exc.status_code == 404
    else:
        raise AssertionError("A different doctor's session must not be accessible")
