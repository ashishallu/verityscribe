import asyncio

import pytest

from app.core.config import Settings
from app.services.gemini_live import GeminiLiveService


def test_backend_dotenv_defaults_and_model_settings():
    # Keep this deterministic and avoid reading/logging a developer's actual key.
    config = Settings(_env_file=None, google_api_key="")

    assert config.google_api_key == ""
    assert config.gemini_live_model == "gemini-2.5-flash-native-audio-preview-12-2025"
    assert config.gemini_live_voice == "Aoede"


def test_live_service_stays_optional_without_api_key():
    service = GeminiLiveService(Settings(google_api_key=""))

    assert not service.configured
    assert service.agent is None
    assert service.runner is None


def test_live_service_initializes_genai_adk_and_live_config_without_network():
    pytest.importorskip("google.genai")
    pytest.importorskip("google.adk")
    service = GeminiLiveService(Settings(google_api_key="local-test-key"))
    try:
        assert service.configured
        assert service.agent is not None
        assert service.runner is not None
        assert service.agent.model == "gemini-2.5-flash-native-audio-preview-12-2025"

        live_config = service.live_connect_config()
        assert live_config.streaming_mode.value == "bidi"
        assert live_config.response_modalities == ["AUDIO"]
        assert live_config.speech_config.voice_config.prebuilt_voice_config.voice_name == "Aoede"
        assert live_config.input_audio_transcription is not None
        assert live_config.output_audio_transcription is not None
    finally:
        asyncio.run(service.aclose())


def test_live_agent_clinical_tool_uses_session_scoped_handler():
    pytest.importorskip("google.adk")
    service = GeminiLiveService(Settings(google_api_key="local-test-key"))
    try:
        async def answer(question):
            return f"verified:{question}"

        service._clinical_handlers["adk-session"] = answer
        tool = service.agent.tools[0]

        class Session:
            id = "adk-session"

        class ToolContext:
            session = Session()

        result = asyncio.run(tool.func("what is recorded?", ToolContext()))
        assert result == "verified:what is recorded?"
        from types import SimpleNamespace
        instruction = service._instruction(SimpleNamespace(state={
            "clinical_context": {"allergies": [{"allergen": "peanut"}]},
            "recent_transcript": [],
        }))
        assert '"allergen": "peanut"' in instruction
    finally:
        asyncio.run(service.aclose())


def test_fastapi_lifespan_initializes_without_gemini_key():
    pytest.importorskip("fastapi")
    from fastapi.testclient import TestClient

    from app.main import app

    with TestClient(app) as client:
        assert client.get("/health").status_code == 200
        assert not app.state.gemini_live.configured
