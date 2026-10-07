import os
import json
import sys
import types
import unittest
from unittest.mock import patch

# These tests exercise only the provider-agnostic consensus logic. Keep them
# runnable in a lightweight test environment without the optional hosted-AI SDK.
if "huggingface_hub" not in sys.modules:
    hf_stub = types.ModuleType("huggingface_hub")
    hf_stub.InferenceClient = object
    sys.modules["huggingface_hub"] = hf_stub

from app.services import ai_service
from app.services.ai_service import AIProvider


class TranscriptConsensusTests(unittest.TestCase):
    def setUp(self):
        self.provider = AIProvider()

    TWO_MODEL_CONFIG = json.dumps([
        {"model": "openai/whisper-large-v3-turbo", "endpoint": "https://asr-primary.test", "weight": 1.1},
        {"model": "openai/whisper-large-v3", "endpoint": "https://asr-secondary.test", "weight": 1.15},
    ])

    def test_single_provider_result_is_not_duplicated_as_two_drafts(self):
        def transcribe(_audio, _filename, url, _model, _provider="auto"):
            if url.endswith("primary.test"):
                raise RuntimeError("provider unavailable")
            return "Patient reports cough."

        with patch.dict(os.environ, {"AI_ASR_MODELS_JSON": self.TWO_MODEL_CONFIG, "HF_TOKEN": "test-token"}), patch.object(
            self.provider, "_transcribe_with", side_effect=transcribe
        ):
            result = self.provider.transcribe_consensus(b"audio", "visit.wav")

        self.assertEqual(result.primary, "")
        self.assertEqual(result.secondary, "Patient reports cough.")
        self.assertEqual(result.final_text, "Patient reports cough.")
        self.assertEqual(result.reconciliation_status, "single_provider")
        self.assertTrue(result.conflicts)

    def test_two_drafts_are_reconciled_and_labeled(self):
        def transcribe(_audio, _filename, url, _model, _provider="auto"):
            return "I have a cough." if url.endswith("primary.test") else "I have cough."

        with patch.dict(os.environ, {"AI_ASR_MODELS_JSON": self.TWO_MODEL_CONFIG, "HF_TOKEN": "test-token"}), patch.object(self.provider, "_transcribe_with", side_effect=transcribe), patch.object(
            self.provider,
            "reconcile_ensemble",
            return_value=("I have a cough.", []),
        ):
            result = self.provider.transcribe_consensus(b"audio", "visit.wav")

        self.assertEqual(result.primary, "I have a cough.")
        self.assertEqual(result.secondary, "I have cough.")
        self.assertEqual(result.reconciliation_status, "reconciled")

    def test_equivalent_drafts_skip_llm_reconciliation(self):
        def transcribe(_audio, _filename, url, _model, _provider="auto"):
            return "Patient has a cough." if url.endswith("primary.test") else "patient has a cough"

        with patch.dict(os.environ, {"AI_ASR_MODELS_JSON": self.TWO_MODEL_CONFIG, "HF_TOKEN": "test-token"}), patch.object(self.provider, "_transcribe_with", side_effect=transcribe), patch.object(
            self.provider, "_hf_chat", side_effect=AssertionError("should skip LLM")
        ):
            result = self.provider.transcribe_consensus(b"audio", "visit.wav")

        self.assertEqual(result.reconciliation_status, "identical")
        self.assertEqual(result.final_text, "patient has a cough")

    @patch.dict(os.environ, {"AI_ASR_MODELS_JSON": "", "HF_TOKEN": "test-token"})
    def test_default_setup_configures_five_provider_routed_models(self):
        configs = self.provider._asr_model_configs()

        self.assertEqual(len(configs), 5)
        self.assertEqual(len({entry["model"] for entry in configs}), 5)
        providers = {entry["model"]: entry["provider"] for entry in configs}
        self.assertEqual(providers["openai/whisper-large-v3"], "fal-ai")
        self.assertEqual(providers["Qwen/Qwen3-ASR-1.7B"], "deepinfra")
        self.assertEqual(providers["nvidia/nemotron-3.5-asr-streaming-0.6b"], "fal-ai")
        self.assertIn("openai/whisper-large-v3", {entry["model"] for entry in configs})
        self.assertIn("Qwen/Qwen3-ASR-1.7B", {entry["model"] for entry in configs})

    @patch.dict(os.environ, {"AI_ASR_MODELS_JSON": "", "HF_TOKEN": "test-token"})
    def test_all_provider_failures_report_safe_actionable_codes(self):
        class ProviderFailure(Exception):
            status_code = 403

        def fail_provider(*_args, **_kwargs):
            try:
                raise ProviderFailure("private response")
            except ProviderFailure as exc:
                raise RuntimeError("request failed") from exc

        with patch.object(
            self.provider,
            "_transcribe_with",
            side_effect=fail_provider,
        ):
            with self.assertRaisesRegex(
                RuntimeError, "inference_provider_permission_or_billing"
            ):
                self.provider.transcribe_consensus(b"audio", "visit.wav")

    @patch.dict(os.environ, {"AI_ASR_MODELS_JSON": "", "HF_TOKEN": "test-token"})
    def test_gated_model_provider_failure_is_identified(self):
        class ProviderFailure(Exception):
            status_code = 403

        def fail_provider(*_args, **_kwargs):
            try:
                raise ProviderFailure("Please agree to share contact information to access this gated model")
            except ProviderFailure as exc:
                raise RuntimeError("Hugging Face ASR request failed") from exc

        with patch.object(self.provider, "_transcribe_with", side_effect=fail_provider):
            with self.assertRaisesRegex(RuntimeError, "gated_model_access_required"):
                self.provider.transcribe_consensus(b"audio", "visit.wav")

    @patch.dict(os.environ, {"HF_TOKEN": "test-token"})
    def test_provider_routed_model_uses_its_configured_provider(self):
        client_kwargs = []

        class FakeResponse:
            text = "clinical transcription"

        class FakeClient:
            def __init__(self, **kwargs):
                self.kwargs = kwargs
                client_kwargs.append(kwargs)

            def automatic_speech_recognition(self, audio, model):
                self.audio = audio
                self.model = model
                return FakeResponse()

        with patch.object(ai_service, "InferenceClient", FakeClient):
            text = self.provider._transcribe_with(
                b"audio", "visit.wav", None, "Qwen/Qwen3-ASR-0.6B", "deepinfra"
            )

        self.assertEqual(text, "clinical transcription")
        self.assertEqual(client_kwargs[0]["provider"], "deepinfra")

    @patch.dict(os.environ, {"HF_TOKEN": ""})
    def test_five_models_run_and_return_individual_predictions(self):
        configs = [
            {"model": f"org/model-{index}", "endpoint": f"https://model-{index}.test", "weight": 1 + index / 10}
            for index in range(5)
        ]
        with patch.dict(os.environ, {"AI_ASR_MODELS_JSON": json.dumps(configs)}), patch.object(
            self.provider, "_transcribe_with", side_effect=lambda _a, _f, url, _m, _p="auto": url.rsplit("-", 1)[1].split(".", 1)[0]
        ), patch.object(
            self.provider, "reconcile_ensemble", return_value=("curated text", [])
        ) as reconcile:
            result = self.provider.transcribe_consensus(b"audio", "visit.wav")

        self.assertEqual(len(result.predictions), 5)
        self.assertTrue(all(item.status == "success" for item in result.predictions))
        self.assertEqual(result.final_text, "curated text")
        reconcile.assert_called_once()


if __name__ == "__main__":
    unittest.main()
