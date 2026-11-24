from unittest.mock import patch
from types import SimpleNamespace
import json
import httpx

from app.core.config import Settings
from app.services import gemini_live as gemini_live_module


def test_gemini_live_stays_unconfigured_without_backend_key():
    with patch.object(gemini_live_module, "settings", return_value=Settings(google_api_key="")):
        service = gemini_live_module.GeminiLiveService()

    assert not service.configured


def test_summary_timeout_defaults_are_bounded_and_environment_overridable(monkeypatch):
    monkeypatch.delenv("AI_SUMMARY_TIMEOUT_SECONDS", raising=False)
    config = Settings(_env_file=None)
    assert config.gemini_summary_primary_timeout_seconds == 8.0
    assert config.gemini_summary_fallback_timeout_seconds == 8.0
    assert config.ai_summary_timeout_seconds == 12.0

    monkeypatch.setenv("AI_SUMMARY_TIMEOUT_SECONDS", "10")
    assert Settings(_env_file=None).ai_summary_timeout_seconds == 10.0


def test_live_model_defaults_to_documented_target_and_keeps_rollback(monkeypatch):
    monkeypatch.delenv("GEMINI_LIVE_MODEL", raising=False)
    assert Settings(_env_file=None).gemini_live_model == "gemini-3.8-live"

    monkeypatch.setenv(
        "GEMINI_LIVE_MODEL", "gemini-2.5-flash-native-audio-preview-12-2025"
    )
    assert (
        Settings(_env_file=None).gemini_live_model
        == "gemini-2.5-flash-native-audio-preview-12-2025"
    )


def test_tool_callbacks_resolve_session_from_adk_state_not_session_attribute():
    context = SimpleNamespace(state={"ai_consultation_session_id": "consultation-123"})

    assert gemini_live_module._tool_session_id(context) == "consultation-123"
    assert gemini_live_module._tool_session_id(SimpleNamespace(state={})) is None
    # ADK 1.15 ToolContext has state and does not expose .session.
    assert not hasattr(context, "session")


def test_session_state_seeds_server_owned_consultation_id_for_tool_callbacks():
    state = gemini_live_module._session_state(
        "consultation-safe-test", {"appointment_id": "appointment-safe-test"}
    )

    assert state["ai_consultation_session_id"] == "consultation-safe-test"
    assert json.loads(state["clinical_context_json"])["appointment_id"] == "appointment-safe-test"
    assert gemini_live_module._tool_session_id(SimpleNamespace(state=state)) == "consultation-safe-test"


def test_adk_can_build_schema_for_nested_tool_context_callback():
    # This is the same schema-building path ADK uses before its first Live
    # event. A nested ToolContext annotation otherwise remains a string and
    # ADK's get_type_hints() raises NameError because the local import is not
    # in the callback's module globals.
    from google.adk.tools.function_tool import FunctionTool
    from google.adk.tools.tool_context import ToolContext

    def make_callback():
        async def clinical_context(question: str, tool_context: ToolContext) -> str:
            return question

        gemini_live_module._bind_tool_context_annotation(clinical_context, ToolContext)
        return clinical_context

    declaration = FunctionTool(make_callback())._get_declaration()

    assert declaration is not None
    assert declaration.name == "clinical_context"
    parameters = declaration.parameters
    properties = (
        parameters.properties
        if parameters is not None
        else declaration.parameters_json_schema.get("properties", {})
    )
    assert "question" in properties


def test_adk_live_run_config():
    # Build the service shell around the installed SDK's actual RunConfig types
    # while replacing provider/session operations with deterministic test doubles.
    service = gemini_live_module.GeminiLiveService.__new__(gemini_live_module.GeminiLiveService)
    service.model = "gemini-2.5-flash-native-audio-preview-12-2025"
    service.voice = "Aoede"
    service.silence_duration_ms = 700
    service.agent = object()
    service.runner = object()
    service.sessions = object()
    service.handlers = {}
    assert service.configured
    run_config = service.run_config()
    assert str(run_config.streaming_mode).endswith("BIDI")
    assert [
        getattr(modality, "value", str(modality).split(".")[-1]).upper()
        for modality in run_config.response_modalities
    ] == ["AUDIO"]
    assert run_config.speech_config.voice_config.prebuilt_voice_config.voice_name == "Aoede"
    assert run_config.input_audio_transcription is not None
    assert run_config.output_audio_transcription is not None
    assert run_config.realtime_input_config is not None
    vad = run_config.realtime_input_config.automatic_activity_detection
    assert vad is not None
    assert vad.disabled is False
    assert vad.silence_duration_ms == 700
    # The installed Gemini API SDK rejects session_resumption.transparent.
    # Omit it unless the provider API explicitly supports this option.
    assert run_config.session_resumption is None


def test_virtual_summary_uses_gemini_json_and_preserves_incomplete_state():
    service = gemini_live_module.GeminiLiveService.__new__(gemini_live_module.GeminiLiveService)
    service.runner = object()
    service.sessions = object()
    service.google_api_key = "test-key-not-real"
    request = {}

    class Response:
        status_code = 200
        def raise_for_status(self):
            return None
        def json(self):
            return {"candidates": [{"content": {"parts": [{"text": json.dumps({
                "clinical_summary": "Problem: Knee pain.",
                "chief_complaint": "Knee pain",
            })}]}}]}

    def post(url, **kwargs):
        request.update({"url": url, **kwargs})
        return Response()

    service.summary_client = object()
    service.summary_model = "test-fast-summary-model"
    transcript = [
        {"role": "patient", "content": f"Earlier detail {index}: right knee pain."}
        for index in range(105)
    ]
    transcript.extend([
        {"role": "assistant", "content": "Did you have surgery on that knee?"},
        {"role": "patient", "content": "Yes, three years ago."},
    ])
    with patch.object(gemini_live_module.httpx, "post", side_effect=post):
        summary = service.generate_consultation_summary(
            transcript=transcript,
            patient_context={"allergies": []}, appointment={"id": "appt-1"},
            interview_complete=False,
        )

    assert gemini_live_module.SUMMARY_MODEL == "gemini-3.8-flash"
    assert request["url"].endswith("/models/test-fast-summary-model:generateContent")
    assert request["headers"]["x-goog-api-key"] == "test-key-not-real"
    config = request["json"]["generationConfig"]
    assert config["responseMimeType"] == "application/json"
    assert config["maxOutputTokens"] == 800
    assert config["thinkingConfig"]["thinkingLevel"] == "LOW"
    assert "temperature" not in config
    assert summary["clinical_summary"] == "Problem: Knee pain."
    assert summary["summary_generation_provider"] == "gemini"
    assert summary["interview_complete"] is False
    assert summary["review_required"] is True
    assert summary["draft_prescription"] is None
    serialized = request["json"]["contents"][0]["parts"][0]["text"]
    assert "Earlier detail 0" in serialized
    assert "Earlier detail 104" in serialized
    assert "three years ago" in serialized
    assert "interviewer turns are question/context only" in serialized.lower()
    assert "Problem; Symptoms and course" in serialized
    assert "Medications" in serialized
    assert "physician review is mandatory" in serialized.lower()
    assert "entire" not in serialized.lower()  # concise summary contract, not an exhaustive narrative


def test_virtual_summary_uses_cloudflare_when_gemini_times_out():
    service = gemini_live_module.GeminiLiveService.__new__(gemini_live_module.GeminiLiveService)
    service.summary_client = object()
    service.google_api_key = "test-key-not-real"
    service.summary_model = "test-fast-summary-model"
    cloudflare_summary = json.dumps({
        "clinical_summary": "Problem: Headache. Symptoms and course: 3 days, 7/10.",
        "chief_complaint": "Headache",
        "severity_frequency": ["Pain rated 7/10"],
    })
    with patch.object(gemini_live_module.httpx, "post", side_effect=httpx.ReadTimeout("provider timeout")), \
         patch("app.services.ai_service.ai_provider.summarize_consultation", return_value=cloudflare_summary) as fallback:
        result = service.generate_consultation_summary(
            transcript=[{"role": "patient", "content": "Headache for 3 days, pain is 7 out of 10"}],
            patient_context={}, appointment={"id": "fictional-appointment"},
            interview_complete=False,
        )

    assert result["summary_generation_provider"] == "cloudflare"
    assert result["clinical_summary"].startswith("Problem: Headache")
    assert result["interview_complete"] is False
    assert result["review_required"] is True
    assert result["draft_prescription"] is None
    prompt = fallback.call_args.args[0]
    assert "Never fill gaps" in prompt
    assert "Headache for 3 days" in prompt


def test_virtual_summary_uses_cloudflare_for_nested_json_in_gemini_summary():
    service = gemini_live_module.GeminiLiveService.__new__(gemini_live_module.GeminiLiveService)
    service.summary_client = object()
    service.google_api_key = "test-key-not-real"
    service.summary_model = "test-fast-summary-model"

    class Response:
        status_code = 200
        def raise_for_status(self):
            return None
        def json(self):
            nested = json.dumps({"clinical_summary": '```json\\n{ "problem": "fever" }\\n```'})
            return {"candidates": [{"content": {"parts": [{"text": nested}]}}]}

    cloudflare_summary = json.dumps({
        "clinical_summary": "Problem: Patient-reported fever.",
        "symptoms": ["Fever"],
    })
    with patch.object(gemini_live_module.httpx, "post", return_value=Response()), \
         patch("app.services.ai_service.ai_provider.summarize_consultation", return_value=cloudflare_summary) as fallback:
        result = service.generate_consultation_summary(
            transcript=[{"role": "patient", "content": "I have a fever"}],
            patient_context={}, appointment={"id": "synthetic-appointment"},
            interview_complete=True,
        )

    assert result["summary_generation_provider"] == "cloudflare"
    assert result["clinical_summary"] == "Problem: Patient-reported fever."
    fallback.assert_called_once()


def test_virtual_summary_retries_transient_gateway_timeout_once():
    service = gemini_live_module.GeminiLiveService.__new__(gemini_live_module.GeminiLiveService)
    service.summary_client = object()
    service.google_api_key = "test-key-not-real"
    service.summary_model = "test-fast-summary-model"
    calls = []

    class Response:
        def __init__(self, status_code, data=None):
            self.status_code = status_code
            self.data = data or {}
        def raise_for_status(self):
            if self.status_code >= 400:
                response = httpx.Response(
                    self.status_code,
                    request=httpx.Request("POST", "https://example.test"),
                    headers={"Retry-After": "3"},
                )
                raise httpx.HTTPStatusError(
                    "gateway timeout", request=response.request, response=response
                )
        def json(self):
            return self.data

    def post(url, **kwargs):
        calls.append((url, kwargs))
        if len(calls) == 1:
            return Response(504)
        return Response(200, {"candidates": [{"content": {"parts": [{
            "text": json.dumps({"clinical_summary": "Problem: Headache."})
        }]}}]})

    with patch.object(gemini_live_module.httpx, "post", side_effect=post), \
         patch.object(gemini_live_module.time, "sleep") as sleep:
        result = service.generate_consultation_summary(
            transcript=[{"role": "patient", "content": "Headache for two days"}],
            patient_context={"allergies": [], "irrelevant_large_field": "should not be sent"},
            appointment={"id": "appointment"}, interview_complete=True,
        )
    assert len(calls) == 2
    sleep.assert_not_called()
    assert result["clinical_summary"] == "Problem: Headache."
    prompt = calls[-1][1]["json"]["contents"][0]["parts"][0]["text"]
    assert "irrelevant_large_field" not in prompt
    assert "Headache for two days" in prompt


def test_virtual_summary_backs_off_after_read_timeout_before_retry():
    service = gemini_live_module.GeminiLiveService.__new__(gemini_live_module.GeminiLiveService)
    service.summary_client = object()
    service.google_api_key = "test-key-not-real"
    service.summary_model = "test-fast-summary-model"
    calls = []

    class Response:
        status_code = 200
        def raise_for_status(self):
            return None
        def json(self):
            return {"candidates": [{"content": {"parts": [{
                "text": json.dumps({"clinical_summary": "Problem: Headache."})
            }]}}]}

    def post(url, **kwargs):
        calls.append(url)
        if len(calls) == 1:
            raise httpx.ReadTimeout("temporary provider delay")
        return Response()

    with patch.object(gemini_live_module.httpx, "post", side_effect=post), \
         patch.object(gemini_live_module.time, "sleep") as sleep:
        result = service.generate_consultation_summary(
            transcript=[{"role": "patient", "content": "Headache for two days"}],
            patient_context={}, appointment={"id": "appointment"},
            interview_complete=False,
        )

    assert len(calls) == 2
    sleep.assert_not_called()
    assert result["clinical_summary"] == "Problem: Headache."


def test_virtual_summary_uses_gemini_flash_lite_after_primary_503():
    service = gemini_live_module.GeminiLiveService.__new__(gemini_live_module.GeminiLiveService)
    service.summary_client = object()
    service.google_api_key = "test-key-not-real"
    service.summary_model = "gemini-3.8-flash"
    service.summary_fallback_model = "gemini-3.5-flash-lite"
    calls = []

    def post(url, **kwargs):
        calls.append((url, kwargs))
        if len(calls) == 1:
            response = httpx.Response(
                503, request=httpx.Request("POST", url),
                json={"error": {"status": "UNAVAILABLE"}},
            )
            response.raise_for_status()
        return httpx.Response(
            200,
            request=httpx.Request("POST", url),
            json={"candidates": [{"content": {"parts": [{
                "text": json.dumps({
                    "clinical_summary": "Problem: Patient-reported headache.",
                    "chief_complaint": "Headache",
                })
            }]}}]},
        )

    with patch.object(gemini_live_module.httpx, "post", side_effect=post):
        result = service.generate_consultation_summary(
            transcript=[{"role": "patient", "content": "I have had a headache for two days."}],
            patient_context={}, appointment={"id": "synthetic-appointment"},
            interview_complete=True,
        )

    assert len(calls) == 2
    assert calls[0][0].endswith("/models/gemini-3.8-flash:generateContent")
    assert calls[1][0].endswith("/models/gemini-3.5-flash-lite:generateContent")
    assert calls[1][1]["json"]["contents"] == calls[0][1]["json"]["contents"]
    assert calls[1][1]["json"]["generationConfig"]["thinkingConfig"]["thinkingLevel"] == "MINIMAL"
    assert result["summary_generation_provider"] == "gemini_fallback"


def test_virtual_summary_skips_primary_in_recent_429_cooldown(monkeypatch):
    service = gemini_live_module.GeminiLiveService.__new__(gemini_live_module.GeminiLiveService)
    service.summary_client = object()
    service.google_api_key = "test-cooldown-key"
    service.summary_model = "gemini-3.8-flash"
    service.summary_fallback_model = "gemini-3.5-flash-lite"
    from app.services import ai_service
    cooldown_key = ai_service._gemini_quota_key(service.google_api_key, service.summary_model)
    monkeypatch.setitem(
        ai_service._GEMINI_QUOTA_COOLDOWNS,
        cooldown_key,
        ai_service.time.monotonic() + 60,
    )
    calls = []

    def post(url, **kwargs):
        calls.append(url)
        if "gemini-3.8-flash" in url:
            raise AssertionError("A Gemini model in cooldown must not be called")
        return httpx.Response(
            200,
            request=httpx.Request("POST", url),
            json={"candidates": [{"content": {"parts": [{
                "text": json.dumps({"clinical_summary": "Problem: Fever."})
            }]}}]},
        )

    with patch.object(gemini_live_module.httpx, "post", side_effect=post):
        result = service.generate_consultation_summary(
            transcript=[{"role": "patient", "content": "I have a fever."}],
            patient_context={}, appointment={"id": "synthetic-appointment"},
            interview_complete=True,
        )

    assert len(calls) == 1
    assert calls[0].endswith("/models/gemini-3.5-flash-lite:generateContent")
    assert result["summary_generation_provider"] == "gemini_fallback"
    assert result["review_required"] is True
    assert result["draft_prescription"] is None


def test_virtual_summary_uses_fallback_without_retrying_primary_after_daily_quota_exhaustion():
    service = gemini_live_module.GeminiLiveService.__new__(gemini_live_module.GeminiLiveService)
    service.summary_client = object()
    service.google_api_key = "test-key-not-real"
    service.summary_model = "test-fast-summary-model"
    calls = []

    def post(url, **kwargs):
        calls.append(url)
        if len(calls) == 1:
            response = httpx.Response(
                429,
                request=httpx.Request("POST", url),
                json={"error": {
                    "status": "RESOURCE_EXHAUSTED",
                    "details": [{
                        "@type": "type.googleapis.com/google.rpc.RetryInfo",
                        "retryDelay": "53925s",
                    }],
                }},
            )
            response.raise_for_status()
        return httpx.Response(
            200,
            request=httpx.Request("POST", url),
            json={"candidates": [{"content": {"parts": [{
                "text": json.dumps({
                    "clinical_summary": "Problem: Headache.",
                    "chief_complaint": "Headache",
                })
            }]}}]},
        )

    with patch.object(gemini_live_module.httpx, "post", side_effect=post):
        result = service.generate_consultation_summary(
            transcript=[{"role": "patient", "content": "Headache for three days"}],
            patient_context={}, appointment={"id": "appointment"},
            interview_complete=False,
        )

    assert len(calls) == 2
    assert calls[0].endswith("/models/test-fast-summary-model:generateContent")
    assert calls[1].endswith("/models/gemini-3.5-flash-lite:generateContent")
    assert result["summary_generation_provider"] == "gemini_fallback"
    assert result["review_required"] is True


def test_summary_model_is_configurable_without_changing_live_model(monkeypatch):
    monkeypatch.setenv("GEMINI_LIVE_MODEL", "gemini-3.8-live")
    monkeypatch.setenv("GEMINI_SUMMARY_MODEL", "gemini-3.8-flash")
    monkeypatch.setenv("GEMINI_SUMMARY_FALLBACK_MODEL", "gemini-3.5-flash-lite")
    cfg = Settings(_env_file=None)

    assert cfg.gemini_live_model == "gemini-3.8-live"
    assert cfg.gemini_summary_model == "gemini-3.8-flash"
    assert cfg.gemini_summary_fallback_model == "gemini-3.5-flash-lite"
