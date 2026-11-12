import json
from datetime import datetime, timezone
from types import SimpleNamespace

from app.routers import ai_consultations


class _Query:
    def __init__(self, rows, filters=None, insert_row=None):
        self.rows = rows
        self.filters = filters or {}
        self.insert_row = insert_row

    def select(self, *_args):
        return self

    def eq(self, field, value):
        self.filters[field] = value
        return self

    def maybe_single(self):
        return self

    def limit(self, _count):
        return self

    def order(self, *_args):
        return self

    def insert(self, row):
        self.insert_row = row
        return self

    def execute(self):
        if self.insert_row is not None:
            row = dict(self.insert_row, id=f"message-{len(self.rows) + 1}")
            duplicate = any(
                existing.get("session_id") == row.get("session_id")
                and existing.get("request_key") == row.get("request_key")
                and existing.get("role") == row.get("role")
                for existing in self.rows
            )
            if duplicate:
                raise RuntimeError("duplicate key")
            self.rows.append(row)
            return SimpleNamespace(data=[row])
        result = [
            row for row in self.rows
            if all(row.get(field) == value for field, value in self.filters.items())
        ]
        return SimpleNamespace(data=result[0] if "id" in self.filters else result)


class _Client:
    def __init__(self):
        self.messages = []

    def table(self, name):
        assert name == "ai_consultation_messages"
        return _Query(self.messages)


def test_direct_credential_keeps_patient_context_server_side(monkeypatch):
    patient_marker = "PRIVATE_PATIENT_CONTEXT_SENTINEL"
    captured = {}

    class Config:
        def __init__(self, **kwargs):
            self.kwargs = kwargs

        def model_dump(self, **_kwargs):
            return {
                "responseModalities": ["AUDIO"],
                "speechConfig": {"voiceConfig": {"prebuiltVoiceConfig": {"voiceName": "Aoede"}}},
                "inputAudioTranscription": {},
                "realtimeInputConfig": {"automaticActivityDetection": {"disabled": False}},
                "systemInstruction": {"parts": [{"text": patient_marker}]},
            }

    def construct(**kwargs):
        return SimpleNamespace(**kwargs)

    types = SimpleNamespace(
        Modality=SimpleNamespace(AUDIO="AUDIO"),
        PrebuiltVoiceConfig=construct,
        VoiceConfig=construct,
        SpeechConfig=construct,
        AudioTranscriptionConfig=construct,
        AutomaticActivityDetection=construct,
        RealtimeInputConfig=construct,
        Content=construct,
        Part=construct,
        LiveConnectConfig=Config,
        HttpOptions=construct,
        LiveConnectConstraints=construct,
        CreateAuthTokenConfig=construct,
    )

    token = SimpleNamespace(
        name="ephemeral-token-for-test",
        expire_time=datetime(2030, 1, 1, tzinfo=timezone.utc),
    )

    class Tokens:
        def create(self, *, config):
            captured["token_config"] = config
            return token

    live = SimpleNamespace(
        model="models/gemini-test-live",
        voice="Aoede",
        silence_duration_ms=600,
        types=types,
        summary_client=SimpleNamespace(auth_tokens=Tokens()),
        initialize_client=lambda: True,
    )
    client = object()
    monkeypatch.setattr(ai_consultations, "get_current_patient", lambda _claims: {"id": "patient-1"})
    monkeypatch.setattr(ai_consultations, "db", lambda: client)
    monkeypatch.setattr(ai_consultations, "_session_or_404", lambda *_a, **_k: {
        "id": "session-1", "appointment_id": "appointment-1", "mode": "ai_virtual",
        "voice_consent_at": "consented", "status": "in_progress",
    })

    class AppointmentQuery:
        def select(self, *_args): return self
        def eq(self, *_args): return self
        def maybe_single(self): return self
        def execute(self):
            return SimpleNamespace(data={
                "id": "appointment-1", "appointment_date": "2030-01-01",
                "appointment_time": "12:00",
            })

    monkeypatch.setattr(ai_consultations, "db", lambda: SimpleNamespace(
        table=lambda _name: AppointmentQuery(),
    ))
    monkeypatch.setattr(ai_consultations, "ensure_appointment_not_expired", lambda _row: None)
    monkeypatch.setattr(ai_consultations, "patient_context", lambda *_args: {"note": patient_marker})
    monkeypatch.setattr(ai_consultations, "gemini_live", live)

    response = ai_consultations.create_direct_live_credential(
        "session-1", claims={"sub": "patient-1"},
    )["data"]

    serialized_response = json.dumps(response)
    assert response["credential"] == token.name
    assert response["model"] == live.model
    assert "systemInstruction" not in response["config"]
    assert "system_instruction" not in response["config"]
    assert patient_marker not in serialized_response
    assert response["config"]["generationConfig"]["responseModalities"] == ["AUDIO"]
    assert response["config"]["generationConfig"]["speechConfig"]["voiceConfig"][
        "prebuiltVoiceConfig"]["voiceName"] == "Aoede"
    assert response["config"]["inputAudioTranscription"] == {}
    assert response["config"]["realtimeInputConfig"]["automaticActivityDetection"][
        "disabled"] is False
    assert "responseModalities" not in response["config"]
    assert "speechConfig" not in response["config"]
    bound_config = captured["token_config"].live_connect_constraints.config
    assert bound_config.kwargs["system_instruction"].parts[0].text.find(patient_marker) >= 0
    assert captured["token_config"].uses == 1
    assert captured["token_config"].live_connect_constraints.model == live.model


def test_direct_turn_retry_is_idempotent(monkeypatch):
    patient = {"id": "patient-1"}
    session = {"id": "session-1", "mode": "ai_virtual", "status": "in_progress"}
    client = _Client()
    monkeypatch.setattr(ai_consultations, "get_current_patient", lambda _claims: patient)
    monkeypatch.setattr(ai_consultations, "_session_or_404", lambda *_a, **_k: session)
    monkeypatch.setattr(ai_consultations, "db", lambda: client)

    payload = ai_consultations.DirectLiveTurnRequest(
        request_key="turn-0001", input_text="My knee hurts.", output_text="When did it start?",
    )
    first = ai_consultations.persist_direct_live_turn("session-1", payload, claims={"sub": "patient-1"})
    second = ai_consultations.persist_direct_live_turn("session-1", payload, claims={"sub": "patient-1"})

    assert len(client.messages) == 2
    assert len(first["data"]["messages"]) == 2
    assert len(second["data"]["messages"]) == 2
