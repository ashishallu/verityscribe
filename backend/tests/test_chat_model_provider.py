import json
import unittest
from unittest.mock import patch

from app.services.ai_service import AIProvider


class CloudflareChatProviderTests(unittest.TestCase):
    def setUp(self):
        self.provider = AIProvider()

    def test_patient_chat_uses_gemini_and_returns_model_text(self):
        from types import SimpleNamespace
        from app.core.config import Settings

        generate = unittest.mock.Mock(return_value=SimpleNamespace(text="Drink water and rest."))
        client = SimpleNamespace(models=SimpleNamespace(generate_content=generate))
        with patch("google.genai.Client", return_value=client), patch(
            "app.services.ai_service.settings",
            return_value=Settings(
                _env_file=None, google_api_key="test-key", gemini_chat_model="gemini-3.8-flash",
            ),
        ):
            answer = self.provider.answer("How can I manage a mild headache?")

        self.assertEqual(answer, "Drink water and rest.")
        self.assertEqual(generate.call_args.kwargs["model"], "gemini-3.8-flash")
        self.assertEqual(generate.call_args.kwargs["contents"], "How can I manage a mild headache?")

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

    def test_permanent_gemini_error_fails_without_provider_fallback(self):
        from types import SimpleNamespace
        from app.core.config import Settings

        permanent_error = RuntimeError("invalid request")
        permanent_error.code = 400
        generate = unittest.mock.Mock(side_effect=permanent_error)
        client = SimpleNamespace(models=SimpleNamespace(generate_content=generate))
        with (
            patch("google.genai.Client", return_value=client),
            patch("app.services.ai_service.settings", return_value=Settings(
                _env_file=None, google_api_key="test-key", gemini_chat_model="gemini-3.8-flash",
            )),
            patch.object(self.provider, "_cloudflare_chat", side_effect=AssertionError("fallback attempted")),
        ):
            with self.assertRaisesRegex(RuntimeError, "Gemini patient chat request failed") as caught:
                self.provider.answer("What does my report say?")
        self.assertNotIn("invalid request", str(caught.exception))
        self.assertEqual(generate.call_count, 1)

    def test_missing_gemini_credentials_fails_closed(self):
        from app.core.config import Settings

        with patch.dict("os.environ", {}, clear=True), patch(
            "app.services.ai_service.settings",
            return_value=Settings(_env_file=None),
        ):
            with self.assertRaisesRegex(RuntimeError, "Gemini patient chat is not configured"):
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
            reply = self.provider.answer_consultation("Patient reports a fever", intake_reply=True)
        self.assertEqual(reply, "How long has it lasted?")
        self.assertEqual(generate.call_count, 2)
        self.assertEqual(generate.call_args_list[1].kwargs["model"], "gemini-3.5-flash-lite")
        sleep.assert_not_called()

    def test_appointment_consultation_uses_gemini_35_immediately_after_primary_503(self):
        from types import SimpleNamespace
        from app.core.config import Settings
        transient = RuntimeError("temporary unavailable")
        transient.code = 503
        primary_failure = unittest.mock.Mock(side_effect=transient)
        fallback_response = SimpleNamespace(
            text="When did the fever begin?",
            candidates=[SimpleNamespace(finish_reason=SimpleNamespace(name="STOP"))],
            usage_metadata=SimpleNamespace(candidates_token_count=8),
        )

        def generate_content(**kwargs):
            if kwargs["model"] == "gemini-3.8-flash":
                return primary_failure(**kwargs)
            return fallback_response

        generate = unittest.mock.Mock(side_effect=generate_content)
        fake_client = SimpleNamespace(models=SimpleNamespace(generate_content=generate))
        with (
            patch("google.genai.Client", return_value=fake_client),
            patch("app.services.ai_service.settings", return_value=Settings(
                _env_file=None,
                google_api_key="test-key",
                gemini_chat_model="gemini-3.8-flash",
                gemini_chat_fallback_model="gemini-3.5-flash-lite",
            )),
            patch("app.services.ai_service.time.sleep") as sleep,
        ):
            reply = self.provider.answer_consultation(
                "Full history: patient has had fever for two days. Latest: still feverish.",
                intake_reply=True,
            )

        self.assertEqual(reply, "When did the fever begin?")
        self.assertEqual([call.kwargs["model"] for call in generate.call_args_list], [
            "gemini-3.8-flash", "gemini-3.5-flash-lite",
        ])
        self.assertEqual(generate.call_args_list[1].kwargs["contents"], generate.call_args_list[0].kwargs["contents"])
        sleep.assert_not_called()

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
                self.provider.answer_consultation("Harmless retry test", intake_reply=True)
        self.assertEqual(generate.call_count, 4)  # primary, backup once, then two primary retries

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
            reply = self.provider.answer_consultation("Patient reports fever", intake_reply=True)
        self.assertEqual(reply, "How long have you had the fever?")
        self.assertEqual(generate.call_count, 4)  # primary, backup once, then two primary retries
        fallback.assert_called_once()
        self.assertEqual(fallback.call_args.kwargs["max_tokens"], 512)
        self.assertTrue(fallback.call_args.kwargs["disable_reasoning"])


if __name__ == "__main__":
    unittest.main()
