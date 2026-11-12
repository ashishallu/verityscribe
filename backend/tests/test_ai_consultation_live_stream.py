import asyncio
import uuid
from datetime import datetime, timezone
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest
from fastapi import HTTPException

from app.routers import ai_consultations
from app.services import gemini_live as gemini_live_module


def test_interrupted_gemini_event_drops_stale_caption_and_audio():
    stale = SimpleNamespace(
        interrupted=True,
        content=SimpleNamespace(parts=[SimpleNamespace(
            inline_data=SimpleNamespace(data=b"cancelled-pcm"),
        )]),
        output_transcription=SimpleNamespace(text="cancelled caption"),
    )
    current = SimpleNamespace(
        interrupted=False,
        content=SimpleNamespace(parts=[SimpleNamespace(
            inline_data=SimpleNamespace(data=b"current-pcm"),
        )]),
        output_transcription=SimpleNamespace(text="current caption"),
    )

    stale_caption, stale_audio = ai_consultations._live_assistant_output(stale)
    current_caption, current_audio = ai_consultations._live_assistant_output(current)
    assert stale_caption is None
    assert stale_audio == []
    assert current_caption.text == "current caption"
    assert current_audio == [b"current-pcm"]


def test_ai_virtual_cannot_fall_back_to_http_llm(monkeypatch):
    monkeypatch.setattr(ai_consultations, "get_current_patient", lambda _claims: {"id": "patient-1"})
    monkeypatch.setattr(ai_consultations, "db", lambda: object())
    monkeypatch.setattr(ai_consultations, "_session_or_404", lambda *args, **kwargs: {
        "id": "session-1", "mode": "ai_virtual",
    })

    with pytest.raises(HTTPException) as error:
        ai_consultations.send_message(
            "session-1",
            ai_consultations.MessageRequest(message="hello", request_key="key-1"),
            claims={"sub": "patient-1"},
        )

    assert error.value.status_code == 409
    assert "Gemini Live" in error.value.detail


@pytest.mark.parametrize(
    ("ticket_row", "session_id", "patient_id", "expected"),
    [
        (None, "session-1", "patient-1", "ticket_invalid"),
        ({"session_id": "other", "patient_id": "patient-1", "expires_at": "2030-01-01T00:00:00Z"}, "session-1", "patient-1", "ticket_session_mismatch"),
        ({"session_id": "session-1", "patient_id": "other", "expires_at": "2030-01-01T00:00:00Z"}, "session-1", "patient-1", "patient_mismatch"),
        ({"session_id": "session-1", "patient_id": "patient-1", "expires_at": "2020-01-01T00:00:00Z"}, "session-1", "patient-1", "ticket_expired"),
        ({"session_id": "session-1", "patient_id": "patient-1", "expires_at": "2030-01-01T00:00:00Z", "consumed_at": "2029-01-01T00:00:00Z"}, "session-1", "patient-1", "ticket_already_consumed"),
    ],
)
def test_voice_ticket_rpc_predicate_diagnostics(ticket_row, session_id, patient_id, expected):
    assert ai_consultations._voice_ticket_state_rejection(
        ticket_row,
        session_id=session_id,
        patient_id=patient_id,
        now=datetime(2028, 1, 1, tzinfo=timezone.utc),
    ) == expected


def test_voice_ticket_rpc_predicate_accepts_matching_unconsumed_ticket():
    assert ai_consultations._voice_ticket_state_rejection(
        {
            "session_id": "session-1",
            "patient_id": "patient-1",
            "expires_at": "2030-01-01T00:00:00Z",
            "consumed_at": None,
        },
        session_id="session-1",
        patient_id="patient-1",
        now=datetime(2028, 1, 1, tzinfo=timezone.utc),
    ) is None


@pytest.mark.parametrize("value, expected", [(True, True), (False, False), ([True], True), ([False], False), ("true", False), (1, False)])
def test_voice_ticket_rpc_response_requires_boolean(value, expected):
    assert ai_consultations._rpc_boolean(value) is expected


def test_outgoing_postgrest_role_diagnostic_never_returns_token():
    import base64
    import json

    role_payload = base64.urlsafe_b64encode(json.dumps({"role": "service_role"}).encode()).decode().rstrip("=")
    fake = SimpleNamespace(postgrest=SimpleNamespace(session=SimpleNamespace(
        headers={"Authorization": f"Bearer header.{role_payload}.signature"},
    )))
    assert ai_consultations._postgrest_authorization_role(fake) == "service_role"
    patient_payload = base64.urlsafe_b64encode(json.dumps({"role": "authenticated"}).encode()).decode().rstrip("=")
    fake.postgrest.session.headers["Authorization"] = f"Bearer header.{patient_payload}.signature"
    assert ai_consultations._postgrest_authorization_role(fake) == "authenticated"


def test_local_web_origin_is_allowed_but_unknown_remote_origin_is_not():
    allowed = {"https://verityscribe-patient-app.onrender.com"}
    assert ai_consultations._voice_origin_allowed("http://127.0.0.1:5003", allowed)
    assert ai_consultations._voice_origin_allowed("http://localhost:5000", allowed)
    assert ai_consultations._voice_origin_allowed(
        "https://verityscribe-patient-app.onrender.com/", allowed,
    )
    assert not ai_consultations._voice_origin_allowed("https://unexpected.example", allowed)


@pytest.mark.parametrize("disconnect", [False, True])
@pytest.mark.parametrize("rpc_data", [True, False])
def test_live_websocket_streams_audio_and_cleans_up(monkeypatch, disconnect, rpc_data, caplog):
    caplog.set_level("INFO")
    session_id = str(uuid.uuid4())
    ticket_id = str(uuid.uuid4())
    persisted = []
    queues = []
    rpc_calls = []

    class FakeQueue:
        def __init__(self):
            self.audio = []
            self.contents = []
            self.closed = False
            queues.append(self)

        def send_content(self, content):
            self.contents.append(content)

        def send_realtime(self, blob):
            self.audio.append(blob)

        def close(self):
            self.closed = True

    class FakeSocket:
        scope = {"subprotocols": ["verityscribe-ticket.test-ticket"]}
        headers = {}

        def __init__(self):
            self.events = []
            self.audio = []
            self.finished = asyncio.Event()

        async def accept(self, **kwargs):
            self.accepted_protocol = kwargs["subprotocol"]

        async def close(self, **kwargs):
            self.close_code = kwargs.get("code")

        async def send_json(self, event):
            self.events.append(event)
            if event.get("type") == "turn_complete":
                self.finished.set()

        async def send_bytes(self, data):
            self.audio.append(data)

        async def receive(self):
            if not getattr(self, "sent_audio", False):
                self.sent_audio = True
                return {"type": "websocket.receive", "bytes": b"\x10\x00\x20\x00"}
            if not getattr(self, "sent_text", False):
                self.sent_text = True
                return {"type": "websocket.receive", "text": '{"type":"text","text":"What should I know?"}'}
            await self.finished.wait()
            if disconnect:
                return {"type": "websocket.disconnect"}
            return {"type": "websocket.receive", "text": '{"type":"end"}'}

    class FakeClient:
        def rpc(self, name, params):
            assert name == "consume_ai_consultation_voice_ticket"
            assert params["p_session_id"] == session_id
            rpc_calls.append((name, params))
            return SimpleNamespace(execute=lambda: SimpleNamespace(data=rpc_data))

        def table(self, name):
            if name == "appointments":
                class AppointmentQuery:
                    def select(self, *_args): return self
                    def eq(self, *_args): return self
                    def maybe_single(self): return self
                    def execute(self):
                        return SimpleNamespace(data={
                            "id": "appointment-1",
                            "appointment_date": "2030-01-01",
                            "appointment_time": "12:00",
                        })
                return AppointmentQuery()
            if name == "ai_consultation_voice_tickets":
                class TicketQuery:
                    def select(self, *_args): return self
                    def eq(self, *_args): return self
                    def maybe_single(self): return self
                    def execute(self):
                        return SimpleNamespace(data={
                            "session_id": session_id,
                            "patient_id": "patient-1",
                            "expires_at": "2030-01-01T00:00:00+00:00",
                            "consumed_at": None,
                        })
                return TicketQuery()
            assert name == "ai_consultation_messages"

            class Query:
                def select(self, *_args):
                    return self

                def eq(self, *_args):
                    return self

                def limit(self, *_args):
                    return self

                def execute(self):
                    return SimpleNamespace(data=[])

                def insert(self, row):
                    persisted.append(row)
                    return SimpleNamespace(execute=lambda: SimpleNamespace(data=[row]))

            return Query()

    class FakeLive:
        configured = True
        initialization_error = None
        initialize_calls = 0

        def __init__(self):
            self.create_session = AsyncMock()
            self.delete_session = AsyncMock()
            self.runner = SimpleNamespace(run_live=self.run_live)
            self.request_queue_type = FakeQueue
            self.types = SimpleNamespace(
                Content=lambda **kwargs: SimpleNamespace(**kwargs),
                Part=lambda **kwargs: SimpleNamespace(**kwargs),
                Blob=lambda **kwargs: SimpleNamespace(**kwargs),
            )

        def initialize(self):
            self.initialize_calls += 1
            return True

        def run_live(self, **kwargs):
            async def events():
                yield SimpleNamespace(
                    interrupted=False,
                    content=SimpleNamespace(parts=[SimpleNamespace(
                        inline_data=SimpleNamespace(data=b"\x01\x00\x02\x00"),
                    )]),
                    input_transcription=SimpleNamespace(text="hello", finished=True),
                    output_transcription=SimpleNamespace(text="Hi there.", finished=True),
                    turn_complete=True,
                )
                # Keep the Live iterator alive until the simulated browser end
                # or disconnect cancels the paired streaming task.
                await asyncio.Event().wait()

            return events()

        def run_config(self):
            return object()

    async def inline_threadpool(function, *args, **kwargs):
        return function(*args, **kwargs)

    fake_socket = FakeSocket()
    fake_live = FakeLive()
    monkeypatch.setattr(ai_consultations.jwt, "decode", lambda *args, **kwargs: {
        "sub": "patient-1", "session_id": session_id, "jti": ticket_id,
    })
    monkeypatch.setattr(ai_consultations, "_voice_ticket_secret", lambda: "test-secret")
    monkeypatch.setattr(ai_consultations, "_session_or_404", lambda *args, **kwargs: {
        "id": session_id,
        "patient_id": "patient-1",
        "appointment_id": "appointment-1",
        "hospital_id": "hospital-1",
        "mode": "ai_virtual",
        "voice_consent_at": "consented",
        "similar_case_consent_at": None,
        "status": "in_progress",
    })
    monkeypatch.setattr(ai_consultations, "_messages", lambda *args: [])
    monkeypatch.setattr(ai_consultations, "db", FakeClient)
    monkeypatch.setattr(ai_consultations, "run_in_threadpool", inline_threadpool)
    monkeypatch.setattr(gemini_live_module, "gemini_live", fake_live)
    monkeypatch.setattr(ai_consultations, "gemini_live", fake_live)

    asyncio.run(asyncio.wait_for(
        ai_consultations.voice_stream(fake_socket, session_id), timeout=10,
    ))

    assert len(rpc_calls) == 1
    if not rpc_data:
        assert getattr(fake_socket, "close_code", None) == 4401
        assert not hasattr(fake_socket, "accepted_protocol")
        assert fake_live.initialize_calls == 0
        assert "ticket_consumed=false" in caplog.text
        assert "rpc_returned_false" in caplog.text
        return

    assert fake_socket.accepted_protocol == "verityscribe-ticket.test-ticket"
    assert fake_live.initialize_calls == 1
    assert any(event["type"] == "ready" for event in fake_socket.events)
    assert any(event["type"] == "caption" and event["role"] == "patient"
               for event in fake_socket.events)
    assert any(event["type"] == "caption" and event["role"] == "assistant"
               for event in fake_socket.events)
    assert b"\x01\x00\x02\x00" in fake_socket.audio
    assert any(event["type"] == "turn_complete" for event in fake_socket.events)
    assert any(event["type"] == "session_saved" for event in fake_socket.events)
    assert {row["role"] for row in persisted} == {"patient", "assistant"}
    assert len(queues[0].audio) == 1
    assert queues[0].audio[0].data == b"\x10\x00\x20\x00"
    assert queues[0].audio[0].mime_type == "audio/pcm;rate=16000"
    assert len(queues[0].contents) == 2  # Kickoff and in-session typed message.
    assert queues[0].contents[1].parts[0].text == "What should I know?"
    assert queues[0].closed
    fake_live.create_session.assert_awaited_once()
    fake_live.delete_session.assert_awaited_once_with(
        user_id="patient-1", session_id=session_id,
    )


def test_hang_up_during_gemini_startup_closes_queue_and_session(monkeypatch):
    session_id = str(uuid.uuid4())
    ticket_id = str(uuid.uuid4())
    queues = []

    class FakeQueue:
        def __init__(self):
            self.closed = False
            queues.append(self)

        def send_content(self, _content):
            pass

        def close(self):
            self.closed = True

    class FakeSocket:
        scope = {"subprotocols": ["verityscribe-ticket.test-ticket"]}
        headers = {}

        def __init__(self):
            self.events = []

        async def accept(self, **_kwargs):
            pass

        async def close(self, **_kwargs):
            pass

        async def send_json(self, event):
            self.events.append(event)

        async def receive(self):
            return {"type": "websocket.receive", "text": '{"type":"end"}'}

    class FakeClient:
        def table(self, name):
            if name == "ai_consultation_voice_tickets":
                class TicketQuery:
                    def select(self, *_args): return self
                    def eq(self, *_args): return self
                    def maybe_single(self): return self
                    def execute(self):
                        return SimpleNamespace(data={
                            "session_id": session_id,
                            "patient_id": "patient-1",
                            "expires_at": "2030-01-01T00:00:00+00:00",
                            "consumed_at": None,
                        })
                return TicketQuery()
            assert name == "appointments"

            class AppointmentQuery:
                def select(self, *_args): return self
                def eq(self, *_args): return self
                def maybe_single(self): return self
                def execute(self):
                    return SimpleNamespace(data={
                        "id": "appointment-1",
                        "appointment_date": "2030-01-01",
                        "appointment_time": "12:00",
                    })

            return AppointmentQuery()

        def rpc(self, *_args):
            return SimpleNamespace(execute=lambda: SimpleNamespace(data=True))

    class FakeLive:
        initialization_error = None

        def __init__(self):
            self.create_session = AsyncMock()
            self.delete_session = AsyncMock()
            self.request_queue_type = FakeQueue
            self.types = SimpleNamespace(
                Content=lambda **kwargs: SimpleNamespace(**kwargs),
                Part=lambda **kwargs: SimpleNamespace(**kwargs),
            )
            self.runner = SimpleNamespace(run_live=self.run_live)

        def initialize(self):
            return True

        def run_live(self, **_kwargs):
            async def events():
                await asyncio.Event().wait()
                yield None

            return events()

        def run_config(self):
            return object()

    async def inline_threadpool(function, *args, **kwargs):
        return function(*args, **kwargs)

    socket = FakeSocket()
    live = FakeLive()
    monkeypatch.setattr(ai_consultations.jwt, "decode", lambda *_args, **_kwargs: {
        "sub": "patient-1", "session_id": session_id, "jti": ticket_id,
    })
    monkeypatch.setattr(ai_consultations, "_voice_ticket_secret", lambda: "test-secret")
    monkeypatch.setattr(ai_consultations, "_session_or_404", lambda *_args, **_kwargs: {
        "id": session_id, "patient_id": "patient-1", "appointment_id": "appointment-1",
        "hospital_id": "hospital-1", "mode": "ai_virtual", "voice_consent_at": "consented",
        "similar_case_consent_at": None, "status": "in_progress",
    })
    monkeypatch.setattr(ai_consultations, "_messages", lambda *_args: [])
    monkeypatch.setattr(ai_consultations, "db", FakeClient)
    monkeypatch.setattr(ai_consultations, "run_in_threadpool", inline_threadpool)
    monkeypatch.setattr(ai_consultations, "gemini_live", live)

    asyncio.run(asyncio.wait_for(ai_consultations.voice_stream(socket, session_id), timeout=3))

    assert not any(event["type"] == "ready" for event in socket.events)
    assert any(event["type"] == "session_saved" for event in socket.events)
    assert queues[0].closed
    live.create_session.assert_awaited_once()
    live.delete_session.assert_awaited_once_with(user_id="patient-1", session_id=session_id)
