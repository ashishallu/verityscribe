import io
import json
import unittest
from unittest.mock import patch
from urllib.error import HTTPError

from app.services.ai_service import AIProvider


class CloudflareDocumentExtractionTests(unittest.TestCase):
    def setUp(self):
        self.provider = AIProvider()

    @patch.dict(
        "os.environ",
        {"CLOUDFLARE_VISION_MODEL": "@cf/meta/llama-3.2-11b-vision-instruct"},
        clear=False,
    )
    @patch("app.services.ai_service.urlopen")
    def test_sends_image_as_data_url_and_reads_cloudflare_response(self, mock_urlopen):
        mock_urlopen.return_value = io.BytesIO(
            json.dumps({"success": True, "result": {"response": "Medicine: [unclear]"}}).encode()
        )

        text = self.provider._extract_document_text_cloudflare(
            b"sample-image", "image/jpeg", "account-123", "secret-token"
        )

        self.assertEqual(text, "Extracted from uploaded document: Medicine: [unclear]")
        request = mock_urlopen.call_args.args[0]
        self.assertEqual(
            request.full_url,
            "https://api.cloudflare.com/client/v4/accounts/account-123/ai/run/"
            "@cf/meta/llama-3.2-11b-vision-instruct",
        )
        self.assertEqual(request.get_header("Authorization"), "Bearer secret-token")
        payload = json.loads(request.data)
        self.assertEqual(payload["image"], "data:image/jpeg;base64,c2FtcGxlLWltYWdl")
        self.assertEqual(payload["temperature"], 0)
        self.assertIn("Mark uncertain handwriting as [unclear]", payload["messages"][0]["content"])

    @patch("app.services.ai_service.urlopen")
    def test_capacity_errors_are_retryable_and_do_not_expose_provider_body(self, mock_urlopen):
        mock_urlopen.side_effect = HTTPError(
            "https://api.cloudflare.com/test", 429, "rate limited", {}, io.BytesIO(b"private")
        )

        with self.assertRaises(RuntimeError) as error:
            self.provider._extract_document_text_cloudflare(
                b"sample-image", "image/jpeg", "account-123", "secret-token"
            )

        self.assertEqual(error.exception.category, "vision_capacity_exhausted")
        self.assertEqual(error.exception.status_code, 429)
        self.assertNotIn("private", str(error.exception))

    @patch("app.services.ai_service.urlopen")
    def test_model_terms_failure_is_classified_without_logging_provider_message(self, mock_urlopen):
        mock_urlopen.side_effect = HTTPError(
            "https://api.cloudflare.com/test", 403, "forbidden", {},
            io.BytesIO(json.dumps({"errors": [{"code": 5016, "message": "secret details"}]}).encode()),
        )

        with self.assertRaises(RuntimeError) as error:
            self.provider._extract_document_text_cloudflare(
                b"sample-image", "image/jpeg", "account-123", "secret-token"
            )

        self.assertEqual(error.exception.category, "vision_model_terms_required")
        self.assertEqual(error.exception.provider_code, 5016)
        self.assertNotIn("secret details", str(error.exception))
        self.assertNotIn("secret-token", str(error.exception))

    def test_empty_model_response_is_rejected(self):
        with patch(
            "app.services.ai_service.urlopen",
            return_value=io.BytesIO(json.dumps({"success": True, "result": {"response": " "}}).encode()),
        ):
            with self.assertRaisesRegex(RuntimeError, "no readable text"):
                self.provider._extract_document_text_cloudflare(
                    b"sample-image", "image/jpeg", "account-123", "secret-token"
                )


if __name__ == "__main__":
    unittest.main()
