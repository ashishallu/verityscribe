from unittest.mock import patch
from types import SimpleNamespace
import json

from app.core.config import Settings
from app.services import gemini_live as gemini_live_module


def test_gemini_live_stays_unconfigured_without_backend_key():
    with patch.object(gemini_live_module, "settings", return_value=Settings(google_api_key="")):
        service = gemini_live_module.GeminiLiveService()

    assert not service.configured


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
    # The installed Gemini API SDK rejects session_resumption.transparent.
    # Omit it unless the provider API explicitly supports this option.
    assert run_config.session_resumption is None


def test_virtual_summary_uses_gemini_json_and_preserves_incomplete_state():
    service = gemini_live_module.GeminiLiveService.__new__(gemini_live_module.GeminiLiveService)
    service.runner = object()
    service.sessions = object()
    service.types = SimpleNamespace(GenerateContentConfig=lambda **kwargs: kwargs)
    request = {}

    class Models:
        def generate_content(self, **kwargs):
            request.update(kwargs)
            return SimpleNamespace(text=json.dumps({
                "clinical_summary": "Patient-reported headache for two days.",
                "chief_complaint": "Headache",
            }))

    service.summary_client = SimpleNamespace(models=Models())
    summary = service.generate_consultation_summary(
        transcript=[{"role": "patient", "content": "I have a headache for two days."}],
        patient_context={"allergies": []}, appointment={"id": "appt-1"},
        interview_complete=False,
    )

    assert gemini_live_module.SUMMARY_MODEL == "gemini-3.8-flash"
    assert request["model"] == "gemini-3.8-flash"
    assert request["config"]["response_mime_type"] == "application/json"
    # Gemini 3.8 Flash no longer accepts legacy sampling parameters such as
    # temperature; sending one makes structured summaries fail and invokes
    # the low-quality factual fallback instead.
    assert "temperature" not in request["config"]
    assert summary["interview_complete"] is False
    assert summary["review_required"] is True
    assert summary["draft_prescription"] is None
