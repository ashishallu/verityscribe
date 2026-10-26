import os
import json
import base64
from io import BytesIO
import sys
import types
import unittest
import wave
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
        credentials = patch.dict(os.environ, {
            "CLOUDFLARE_ACCOUNT_ID": "cf-account-test",
            "CLOUDFLARE_API_TOKEN": "cf-token-test",
            "CLOUDFLARE_AI_GATEWAY": "test-gateway",
        })
        credentials.start()
        self.addCleanup(credentials.stop)

    TWO_MODEL_CONFIG = json.dumps([
        {"model": "@cf/openai/whisper-large-v3-turbo", "provider": "cloudflare", "weight": 1.1},
        {"model": "@cf/deepgram/flux", "provider": "cloudflare", "weight": 1.15},
    ])

    def test_single_provider_result_is_not_duplicated_as_two_drafts(self):
        def transcribe(_audio, _filename, _url, model, _provider="auto"):
            if model == "@cf/openai/whisper-large-v3-turbo":
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
        def transcribe(_audio, _filename, _url, model, _provider="auto"):
            return "I have a cough." if model == "@cf/openai/whisper-large-v3-turbo" else "I have cough."

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
        def transcribe(_audio, _filename, _url, model, _provider="auto"):
            return "Patient has a cough." if model == "@cf/openai/whisper-large-v3-turbo" else "patient has a cough"

        with patch.dict(os.environ, {"AI_ASR_MODELS_JSON": self.TWO_MODEL_CONFIG, "HF_TOKEN": "test-token"}), patch.object(self.provider, "_transcribe_with", side_effect=transcribe), patch.object(
            self.provider, "_hf_chat", side_effect=AssertionError("should skip LLM")
        ):
            result = self.provider.transcribe_consensus(b"audio", "visit.wav")

        self.assertEqual(result.reconciliation_status, "identical")
        self.assertEqual(result.final_text, "patient has a cough")

    @patch.dict(os.environ, {"AI_ASR_MODELS_JSON": ""})
    def test_default_setup_preserves_three_working_models_and_has_two_replacements(self):
        configs = self.provider._asr_model_configs()

        self.assertEqual(len(configs), 5)
        self.assertEqual(len({entry["model"] for entry in configs}), 5)
        providers = {entry["model"]: entry["provider"] for entry in configs}
        self.assertEqual(set(providers), {
            "@cf/openai/whisper", "@cf/openai/whisper-tiny-en",
            "@cf/deepgram/nova-3", "@cf/deepgram/flux",
            "@cf/openai/whisper-large-v3-turbo",
        })
        self.assertEqual(set(providers.values()), {"cloudflare"})
        self.assertEqual([entry["model"] for entry in configs[:3]], [
            "@cf/openai/whisper", "@cf/openai/whisper-tiny-en", "@cf/deepgram/nova-3"
        ])

    def test_three_existing_cloudflare_model_wire_formats_are_preserved(self):
        class Response:
            def __enter__(self): return self
            def __exit__(self, *_args): pass
            def read(self): return b'{"success":true,"result":{"text":"baseline transcript"}}'

        with patch.object(ai_service, "urlopen", return_value=Response()) as post:
            for model in ("@cf/openai/whisper", "@cf/openai/whisper-tiny-en"):
                with self.subTest(model=model):
                    self.assertEqual(
                        self.provider._transcribe_with(b"wav-bytes", "visit.wav", None, model, "cloudflare"),
                        "baseline transcript",
                    )
                    request = post.call_args.args[0]
                    self.assertEqual(json.loads(request.data), {"audio": list(b"wav-bytes")})
                    self.assertEqual(request.get_header("Content-type"), "application/json")

            self.provider._transcribe_with(
                b"wav-bytes", "visit.wav", None, "@cf/deepgram/nova-3", "cloudflare"
            )
            request = post.call_args.args[0]
            self.assertEqual(request.data, b"wav-bytes")
            self.assertEqual(request.get_header("Content-type"), "audio/wav")

    def test_cloudflare_turbo_uses_documented_base64_audio_shape(self):
        class Response:
            def __enter__(self): return self
            def __exit__(self, *_args): pass
            def read(self): return b'{"success":true,"result":{"transcription_info":{"text":"turbo transcript"}}}'

        audio = b"sample-wav-payload"
        with patch.object(ai_service, "urlopen", return_value=Response()) as post:
            text = self.provider._transcribe_with(
                audio, "visit.wav", None,
                "@cf/openai/whisper-large-v3-turbo", "cloudflare",
            )
        self.assertEqual(text, "turbo transcript")
        request = post.call_args.args[0]
        self.assertEqual(json.loads(request.data), {"audio": base64.b64encode(audio).decode("ascii")})
        self.assertIn("/ai/run/@cf/openai/whisper-large-v3-turbo", request.full_url)

    def test_flux_websocket_replays_pcm_and_collects_final_turn_transcript(self):
        import websockets.sync.client

        pcm = b"\x01\x00" * 160
        wav_buffer = BytesIO()
        with wave.open(wav_buffer, "wb") as wav_file:
            wav_file.setnchannels(1)
            wav_file.setsampwidth(2)
            wav_file.setframerate(16000)
            wav_file.writeframes(pcm)
        sent = []

        class Socket:
            def __enter__(self): return self
            def __exit__(self, *_args): pass
            def send(self, message): sent.append(message)
            def recv(self, timeout=None):
                if not hasattr(self, "received"):
                    self.received = True
                    return json.dumps({
                        "type": "TurnInfo",
                        "event": "EndOfTurn",
                        "transcript": "Flux transcript",
                    })
                raise TimeoutError("done")

        with patch.object(websockets.sync.client, "connect", return_value=Socket()) as connect:
            transcript = self.provider._transcribe_with(
                wav_buffer.getvalue(), "visit.wav", None, "@cf/deepgram/flux", "cloudflare"
            )
        self.assertEqual(transcript, "Flux transcript")
        self.assertEqual(sent[0], pcm)
        self.assertEqual(json.loads(sent[-1]), {"type": "CloseStream"})
        self.assertIn("model=%40cf%2Fdeepgram%2Fflux", connect.call_args.args[0])
        self.assertEqual(
            connect.call_args.kwargs["additional_headers"]["cf-aig-authorization"],
            "cf-token-test",
        )

    def test_flux_requires_valid_pcm_wav(self):
        with self.assertRaisesRegex(RuntimeError, "valid PCM WAV"):
            self.provider._transcribe_flux(b"not-wav", "acct", "token", 10)

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
                RuntimeError, "provider_permission_denied"
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
            with self.assertRaisesRegex(RuntimeError, "model_access_denied"):
                self.provider.transcribe_consensus(b"audio", "visit.wav")

    @patch.dict(os.environ, {"AI_ASR_MODELS_JSON": "", "HF_TOKEN": "test-token"})
    def test_wrapped_http_status_and_reason_are_identified_without_leaking_body(self):
        class HTTPProviderFailure(Exception):
            pass

        def fail_provider(*_args, **_kwargs):
            try:
                raise HTTPProviderFailure("401 Client Error: Invalid token; private body")
            except HTTPProviderFailure as exc:
                raise RuntimeError("Hugging Face ASR request failed (HTTPError)") from exc

        with patch.object(self.provider, "_transcribe_with", side_effect=fail_provider):
            with self.assertRaisesRegex(RuntimeError, "provider_authentication_failed") as error:
                self.provider.transcribe_consensus(b"audio", "visit.wav")
        self.assertNotIn("private body", str(error.exception))

    @patch.dict(os.environ, {"AI_ASR_MODELS_JSON": "", "HF_TOKEN": "test-token"})
    def test_unsupported_model_provider_error_is_actionable(self):
        def fail_provider(*_args, **_kwargs):
            try:
                raise ValueError("Model not supported by provider deepinfra")
            except ValueError as exc:
                raise RuntimeError("Hugging Face ASR request failed (ValueError)") from exc

        with patch.object(self.provider, "_transcribe_with", side_effect=fail_provider):
            with self.assertRaisesRegex(RuntimeError, "model_unsupported"):
                self.provider.transcribe_consensus(b"audio", "visit.wav")

    @patch.dict(os.environ, {"AI_ASR_MODELS_JSON": "", "HF_TOKEN": "test-token"})
    def test_unclassified_provider_failure_preserves_safe_exception_category(self):
        class TransportProtocolFailure(Exception):
            pass

        with patch.object(
            self.provider,
            "_transcribe_with",
            side_effect=TransportProtocolFailure("private request content must not be shown"),
        ):
            with self.assertRaisesRegex(RuntimeError, "provider_transportprotocolfailure") as error:
                self.provider.transcribe_consensus(b"audio", "visit.wav")
        self.assertNotIn("private request content", str(error.exception))

    def test_paid_external_asr_route_is_rejected_without_network_access(self):
        with patch.object(ai_service, "InferenceClient", side_effect=AssertionError("no provider call allowed")):
            with self.assertRaisesRegex(RuntimeError, "restricted to the approved Cloudflare"):
                self.provider._transcribe_with(
                    b"audio", "visit.wav", None, "Qwen/Qwen3-ASR-1.7B", "deepinfra"
                )

    def test_transient_provider_failure_retries_once_and_reports_retry_count(self):
        with patch.dict(os.environ, {"AI_ASR_MAX_RETRIES": "1"}), patch.object(
            self.provider, "_transcribe_with", side_effect=[TimeoutError("timeout"), "usable text"]
        ) as request, patch.object(ai_service, "sleep"):
            text, retries = self.provider._transcribe_with_retries(
                b"audio", "visit.wav", None, "openai/whisper-large-v3-turbo", "auto"
            )
        self.assertEqual(text, "usable text")
        self.assertEqual(retries, 1)
        self.assertEqual(request.call_count, 2)

    def test_permanent_provider_failure_is_not_retried(self):
        with patch.dict(os.environ, {"AI_ASR_MAX_RETRIES": "2"}), patch.object(
            self.provider, "_transcribe_with", side_effect=ValueError("invalid request")
        ) as request:
            with self.assertRaises(ai_service.ASRRetryError) as caught:
                self.provider._transcribe_with_retries(
                    b"audio", "visit.wav", None, "openai/whisper-large-v3-turbo", "auto"
                )
        self.assertEqual(caught.exception.retry_count, 0)
        self.assertEqual(request.call_count, 1)

    @patch.dict(os.environ, {"AI_ASR_MODELS_JSON": "", "HF_TOKEN": "test-token"})
    def test_all_failed_result_retains_model_states_and_request_id(self):
        with patch.object(self.provider, "_transcribe_with", side_effect=TimeoutError("provider timed out")):
            with self.assertRaises(ai_service.ASRAllModelsFailed) as caught:
                self.provider.transcribe_consensus(b"audio", "visit.wav", "correlation-test")
        self.assertEqual(caught.exception.request_id, "correlation-test")
        self.assertEqual(len(caught.exception.predictions), 5)
        self.assertTrue(all(item.status == "TIMEOUT" for item in caught.exception.predictions))
        payload = self.provider.failed_consensus_payload(caught.exception.predictions)
        self.assertEqual(payload["execution_status"], "all_failed")
        self.assertTrue(all(item["text"] == "" for item in payload["predictions"]))

    def test_external_or_hugging_face_routes_are_rejected_to_prevent_paid_asr(self):
        config = json.dumps([{"model": "openai/whisper-large-v3-turbo", "provider": "auto"}])
        with patch.dict(os.environ, {"AI_ASR_MODELS_JSON": config}):
            with self.assertRaisesRegex(RuntimeError, "paid/external routes are disabled"):
                self.provider._asr_model_configs()

    @patch.dict(os.environ, {"HF_TOKEN": "test-token"})
    def test_five_models_run_and_return_individual_predictions(self):
        configs = [
            {"model": model, "provider": "cloudflare", "weight": weight}
            for model, _provider, weight in self.provider.DEFAULT_ASR_MODELS
        ]
        with patch.dict(os.environ, {"AI_ASR_MODELS_JSON": json.dumps(configs)}), patch.object(
            self.provider, "_transcribe_with", side_effect=lambda _a, _f, _url, model, _p="auto": model
        ), patch.object(
            self.provider, "reconcile_ensemble", return_value=("curated text", [])
        ) as reconcile:
            result = self.provider.transcribe_consensus(b"audio", "visit.wav")

        self.assertEqual(len(result.predictions), 5)
        self.assertTrue(all(item.status == "SUCCESS" for item in result.predictions))
        self.assertEqual(result.execution_status, "all_success")
        self.assertEqual(result.final_text, "curated text")
        reconcile.assert_called_once()

    def test_cloudflare_quota_429_is_not_retried(self):
        class QuotaExceeded(Exception):
            status_code = 429

        with patch.dict(os.environ, {"AI_ASR_MAX_RETRIES": "2"}), patch.object(
            self.provider, "_transcribe_with", side_effect=QuotaExceeded("quota exhausted")
        ) as request:
            with self.assertRaises(ai_service.ASRRetryError) as caught:
                self.provider._transcribe_with_retries(
                    b"audio", "visit.wav", None, "@cf/openai/whisper", "cloudflare"
                )
        self.assertEqual(caught.exception.retry_count, 0)
        self.assertEqual(request.call_count, 1)


if __name__ == "__main__":
    unittest.main()
