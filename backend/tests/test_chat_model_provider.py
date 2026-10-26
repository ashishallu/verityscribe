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
        with patch.dict("os.environ", {}, clear=True):
            with self.assertRaisesRegex(RuntimeError, "Cloudflare Workers AI is not configured"):
                self.provider.answer("What is a balanced diet?")


if __name__ == "__main__":
    unittest.main()
