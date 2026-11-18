import json
import unittest
from io import BytesIO
from urllib.error import HTTPError
from unittest.mock import patch

from app.services.ai_service import AIProvider


class CloudflareChatProviderTests(unittest.TestCase):
    def setUp(self):
        self.provider = AIProvider()

    def test_chat_uses_workers_ai_and_parses_documented_response(self):
        class Response:
            def __enter__(self):
                return self

            def __exit__(self, *_args):
                return False

            def read(self):
                return json.dumps({
                    "success": True,
                    "result": {"response": "Drink water and rest."},
                }).encode()

        env = {
            "CLOUDFLARE_ACCOUNT_ID": "account-test",
            "CLOUDFLARE_API_TOKEN": "secret-test-token",
            "HF_TOKEN": "must-not-be-used",
            "HUGGINGFACE_TOKEN": "must-not-be-used",
        }
        with patch.dict("os.environ", env, clear=True), \
             patch("app.services.ai_service.urlopen", return_value=Response()) as request:
            answer = self.provider.answer("How can I manage a mild headache?")

        self.assertEqual(answer, "Drink water and rest.")
        sent_request = request.call_args.args[0]
        self.assertEqual(
            sent_request.full_url,
            f"https://api.cloudflare.com/client/v4/accounts/account-test/ai/run/{self.provider.CHAT_MODEL}",
        )
        self.assertEqual(sent_request.get_header("Authorization"), "Bearer secret-test-token")
        payload = json.loads(sent_request.data)
        self.assertEqual(payload["messages"], [{
            "role": "user", "content": "How can I manage a mild headache?",
        }])
        self.assertEqual(payload["max_tokens"], 260)

    def test_chat_parses_openai_compatible_workers_ai_response(self):
        class Response:
            def __enter__(self):
                return self

            def __exit__(self, *_args):
                return False

            def read(self):
                return json.dumps({
                    "success": True,
                    "result": {
                        "response": None,
                        "choices": [{"message": {"content": "How long have you had the fever?"}}],
                    },
                }).encode()

        with patch.dict("os.environ", {
            "CLOUDFLARE_ACCOUNT_ID": "account-test",
            "CLOUDFLARE_API_TOKEN": "secret-test-token",
        }, clear=True), patch(
            "app.services.ai_service.urlopen", return_value=Response(),
        ) as request:
            answer = self.provider._cloudflare_chat(
                "Patient reports fever", timeout=10, max_tokens=512,
                disable_reasoning=True,
            )

        self.assertEqual(answer, "How long have you had the fever?")
        payload = json.loads(request.call_args.args[0].data)
        self.assertTrue(payload["messages"][0]["content"].endswith("/no_think"))

    def test_consultation_summary_uses_configured_cloudflare_fallback_model(self):
        from app.core.config import Settings

        class Response:
            def __enter__(self):
                return self

            def __exit__(self, *_args):
                return False

            def read(self):
                return json.dumps({
                    "success": True,
                    "result": {"response": '{"clinical_summary":"Problem: headache."}'},
                }).encode()

        env = {
            "CLOUDFLARE_ACCOUNT_ID": "account-test",
            "CLOUDFLARE_API_TOKEN": "secret-test-token",
            "AI_SUMMARY_TIMEOUT_SECONDS": "40",
            "CLOUDFLARE_SUMMARY_MODEL": "@cf/qwen/qwen3.8-27b",
        }
        with (
            patch.dict("os.environ", env, clear=True),
            patch("app.services.ai_service.settings", return_value=Settings(
                _env_file=None, cloudflare_summary_model="@cf/qwen/qwen3.8-27b",
            )),
            patch("app.services.ai_service.urlopen", return_value=Response()) as request,
        ):
            answer = self.provider.summarize_consultation("Create a factual summary")

        self.assertEqual(answer, '{"clinical_summary":"Problem: headache."}')
        sent_request = request.call_args.args[0]
        self.assertTrue(sent_request.full_url.endswith("/ai/run/@cf/qwen/qwen3.8-27b"))
        self.assertEqual(sent_request.get_header("Authorization"), "Bearer secret-test-token")
        payload = json.loads(sent_request.data)
        self.assertEqual(payload["max_tokens"], 900)
        self.assertTrue(payload["messages"][0]["content"].endswith("/no_think"))
        self.assertEqual(payload["reasoning_effort"], "low")
        self.assertEqual(request.call_args.kwargs["timeout"], 40.0)

    def test_quota_error_is_sanitized_and_does_not_fall_back_to_huggingface(self):
        env = {
            "CLOUDFLARE_ACCOUNT_ID": "account-test",
            "CLOUDFLARE_API_TOKEN": "secret-test-token",
            "HF_TOKEN": "must-not-be-used",
            "HUGGINGFACE_TOKEN": "must-not-be-used",
        }
        error = HTTPError(
            "https://api.cloudflare.com/", 402, "Payment Required", None,
            BytesIO(b"sensitive provider response"),
        )
        with patch.dict("os.environ", env, clear=True), \
             patch("app.services.ai_service.urlopen", side_effect=error), \
             patch.object(self.provider, "_hf_chat", side_effect=AssertionError("fallback attempted")):
            with self.assertRaisesRegex(
                RuntimeError, r"Cloudflare Workers AI request failed \(HTTP 402\)"
            ) as caught:
                self.provider.answer("What does my report say?")
        self.assertNotIn("sensitive provider response", str(caught.exception))

    def test_missing_cloudflare_credentials_fails_closed(self):
        from app.core.config import Settings

        with patch.dict("os.environ", {}, clear=True), patch(
            "app.services.ai_service.settings",
            return_value=Settings(_env_file=None),
        ):
            with self.assertRaisesRegex(RuntimeError, "Cloudflare Workers AI is not configured"):
                self.provider.answer("What is a balanced diet?")

    def test_appointment_consultation_uses_gemini_flash(self):
        from types import SimpleNamespace
        from app.core.config import Settings

        generate = unittest.mock.Mock(return_value=SimpleNamespace(text="How long have you had the fever?"))
        client = SimpleNamespace(models=SimpleNamespace(generate_content=generate))
        with patch("google.genai.Client", return_value=client) as constructor, patch(
            "app.services.ai_service.settings",
            return_value=Settings(_env_file=None, google_api_key="test-key", gemini_chat_model="gemini-3.8-flash"),
        ):
            reply = self.provider.answer_consultation("Patient reports fever")
        self.assertEqual(reply, "How long have you had the fever?")
        self.assertEqual(constructor.call_args.kwargs["api_key"], "test-key")
        self.assertEqual(generate.call_args.kwargs["model"], "gemini-3.8-flash")
        self.assertEqual(generate.call_args.kwargs["config"]["max_output_tokens"], 220)

    def test_ai_chat_intake_uses_larger_output_budget(self):
        from types import SimpleNamespace
        from app.core.config import Settings

        response = SimpleNamespace(
            text="I’m sorry you have a fever. When did it start?",
            candidates=[SimpleNamespace(finish_reason=SimpleNamespace(name="STOP"))],
            usage_metadata=SimpleNamespace(candidates_token_count=24, thoughts_token_count=64),
        )
        generate = unittest.mock.Mock(return_value=response)
        client = SimpleNamespace(models=SimpleNamespace(generate_content=generate))
        with patch("google.genai.Client", return_value=client), patch(
            "app.services.ai_service.settings",
            return_value=Settings(_env_file=None, google_api_key="test-key", gemini_chat_model="gemini-3.8-flash"),
        ):
            reply = self.provider.answer_consultation("Patient reports fever", intake_reply=True)

        self.assertEqual(reply, response.text)
        self.assertEqual(generate.call_args.kwargs["config"]["max_output_tokens"], 1024)

    def test_ai_chat_summary_requests_complete_json_with_large_output_budget(self):
        from types import SimpleNamespace
        from app.core.config import Settings

        response = SimpleNamespace(
            text='{"clinical_summary":"Patient-reported fever."}',
            candidates=[SimpleNamespace(finish_reason=SimpleNamespace(name="STOP"))],
            usage_metadata=SimpleNamespace(candidates_token_count=30, thoughts_token_count=80),
        )
        generate = unittest.mock.Mock(return_value=response)
        client = SimpleNamespace(models=SimpleNamespace(generate_content=generate))
        with patch("google.genai.Client", return_value=client), patch(
            "app.services.ai_service.settings",
            return_value=Settings(_env_file=None, google_api_key="test-key", gemini_chat_model="gemini-3.8-flash"),
        ):
            reply = self.provider.answer_consultation("Create a factual summary", chat_summary=True)

        self.assertEqual(reply, response.text)
        self.assertEqual(generate.call_args.kwargs["config"]["max_output_tokens"], 4096)
        self.assertEqual(generate.call_args.kwargs["config"]["response_mime_type"], "application/json")

    def test_truncated_ai_chat_summary_uses_cloudflare_output_budget(self):
        from types import SimpleNamespace
        from app.core.config import Settings

        truncated = SimpleNamespace(
            text='{"clinical_summary":"Partial',
            candidates=[SimpleNamespace(finish_reason=SimpleNamespace(name="MAX_TOKENS"))],
            usage_metadata=SimpleNamespace(candidates_token_count=4, thoughts_token_count=4092),
        )
        client = SimpleNamespace(models=SimpleNamespace(generate_content=unittest.mock.Mock(return_value=truncated)))
        with (
            patch("google.genai.Client", return_value=client),
            patch("app.services.ai_service.settings", return_value=Settings(
                _env_file=None, google_api_key="test-key", gemini_chat_model="gemini-3.8-flash",
            )),
            patch.object(self.provider, "_cloudflare_chat", return_value='{"clinical_summary":"Patient reports fever."}') as fallback,
        ):
            answer = self.provider.answer_consultation("Create summary", chat_summary=True)

        self.assertIn("Patient reports fever", answer)
        self.assertEqual(fallback.call_args.kwargs["max_tokens"], 1000)
        self.assertTrue(fallback.call_args.kwargs["disable_reasoning"])

    def test_ai_chat_truncated_gemini_output_uses_complete_cloudflare_fallback(self):
        from types import SimpleNamespace
        from app.core.config import Settings

        truncated = SimpleNamespace(
            text="I am sorry to hear that you are",
            candidates=[SimpleNamespace(finish_reason=SimpleNamespace(name="MAX_TOKENS"))],
            usage_metadata=SimpleNamespace(candidates_token_count=4, thoughts_token_count=212),
        )
        client = SimpleNamespace(models=SimpleNamespace(generate_content=unittest.mock.Mock(return_value=truncated)))
        with (
            patch("google.genai.Client", return_value=client),
            patch("app.services.ai_service.settings", return_value=Settings(
                _env_file=None, google_api_key="test-key", gemini_chat_model="gemini-3.8-flash",
            )),
            patch.object(self.provider, "_cloudflare_chat", return_value="When did the fever start?") as fallback,
        ):
            reply = self.provider.answer_consultation("Patient reports fever", intake_reply=True)

        self.assertEqual(reply, "When did the fever start?")
        fallback.assert_called_once()
        self.assertTrue(fallback.call_args.kwargs["disable_reasoning"])

    def test_appointment_consultation_retries_503_then_returns_model_reply(self):
        from types import SimpleNamespace
        from app.core.config import Settings
        transient = RuntimeError("temporary unavailable")
        transient.code = 503
        generate = unittest.mock.Mock(side_effect=[transient, SimpleNamespace(text="How long has it lasted?")])
        fake_client = SimpleNamespace(models=SimpleNamespace(generate_content=generate))
        with (
            patch("google.genai.Client", return_value=fake_client),
            patch("app.services.ai_service.settings", return_value=Settings(
                _env_file=None, google_api_key="test-key", gemini_chat_model="gemini-3.8-flash",
            )),
            patch("app.services.ai_service.time.sleep") as sleep,
            patch("app.services.ai_service.random.uniform", return_value=0.1),
        ):
            reply = self.provider.answer_consultation("Patient reports a fever")
        self.assertEqual(reply, "How long has it lasted?")
        self.assertEqual(generate.call_count, 2)
        sleep.assert_called_once_with(0.6)

    def test_appointment_consultation_stops_after_three_transient_failures(self):
        from app.core.config import Settings
        transient = RuntimeError("temporary unavailable")
        transient.code = 503
        generate = unittest.mock.Mock(side_effect=transient)
        fake_client = type("FakeClient", (), {"models": type("Models", (), {"generate_content": generate})()})()
        with (
            patch("google.genai.Client", return_value=fake_client),
            patch("app.services.ai_service.settings", return_value=Settings(
                _env_file=None, google_api_key="test-key", gemini_chat_model="gemini-3.8-flash",
            )),
            patch("app.services.ai_service.time.sleep"),
        ):
            with self.assertRaisesRegex(RuntimeError, "after 3 attempts"):
                self.provider.answer_consultation("Harmless retry test")
        self.assertEqual(generate.call_count, 3)

    def test_appointment_consultation_uses_cloudflare_after_gemini_504_retries(self):
        from app.core.config import Settings
        transient = RuntimeError("gateway timeout")
        transient.code = 504
        generate = unittest.mock.Mock(side_effect=transient)
        fake_client = type("FakeClient", (), {"models": type("Models", (), {"generate_content": generate})()})()
        with (
            patch("google.genai.Client", return_value=fake_client),
            patch("app.services.ai_service.settings", return_value=Settings(
                _env_file=None, google_api_key="test-key", gemini_chat_model="gemini-3.8-flash",
            )),
            patch("app.services.ai_service.time.sleep"),
            patch.object(self.provider, "_cloudflare_chat", return_value="How long have you had the fever?") as fallback,
        ):
            reply = self.provider.answer_consultation("Patient reports fever")
        self.assertEqual(reply, "How long have you had the fever?")
        self.assertEqual(generate.call_count, 3)
        fallback.assert_called_once()
        self.assertEqual(fallback.call_args.kwargs["max_tokens"], 512)
        self.assertTrue(fallback.call_args.kwargs["disable_reasoning"])


if __name__ == "__main__":
    unittest.main()
