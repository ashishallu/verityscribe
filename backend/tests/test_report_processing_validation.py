from io import BytesIO
import unittest
from unittest.mock import patch

from pypdf import PdfWriter
from app.routers.integration import (
    MAX_CHAT_PDF_PAGES,
    ReportValidationError,
    _extract_pdf_text,
    _report_content_type,
    _validate_report_size,
    _report_text_is_persisted,
)


class ReportProcessingValidationTests(unittest.TestCase):
    def test_detects_real_file_signature_instead_of_claimed_mime(self):
        jpeg = b"\xff\xd8\xff" + b"synthetic"
        self.assertEqual(_report_content_type(None, jpeg), "image/jpeg")
        self.assertIsNone(_report_content_type(None, b"not an image"))

    def test_rejects_corrupt_pdf_with_useful_category(self):
        with self.assertRaises(ReportValidationError) as error:
            _extract_pdf_text(b"%PDF-not-a-valid-document")
        self.assertEqual(error.exception.category, "pdf_extraction_failed")

    def test_rejects_password_protected_pdf(self):
        writer = PdfWriter()
        writer.add_blank_page(width=100, height=100)
        writer.encrypt("secret")
        output = BytesIO()
        writer.write(output)
        with self.assertRaises(ReportValidationError) as error:
            _extract_pdf_text(output.getvalue())
        self.assertEqual(error.exception.category, "pdf_password_protected")

    def test_rejects_pdf_over_page_limit(self):
        writer = PdfWriter()
        for _ in range(MAX_CHAT_PDF_PAGES + 1):
            writer.add_blank_page(width=100, height=100)
        output = BytesIO()
        writer.write(output)
        with self.assertRaises(ReportValidationError) as error:
            _extract_pdf_text(output.getvalue())
        self.assertEqual(error.exception.category, "pdf_page_limit_exceeded")

    def test_rejects_oversized_upload(self):
        with patch("app.routers.integration.MAX_CHAT_REPORT_BYTES", 128):
            with self.assertRaises(ReportValidationError) as error:
                _validate_report_size(b"x" * 129)
        self.assertEqual(error.exception.category, "file_too_large")

    def test_accepts_limit_sized_upload(self):
        with patch("app.routers.integration.MAX_CHAT_REPORT_BYTES", 128):
            _validate_report_size(b"x" * 128)

    def test_metadata_without_saved_extraction_is_not_considered_complete(self):
        class Response:
            data = []

        class Query:
            def select(self, *_args): return self
            def eq(self, *_args): return self
            def limit(self, *_args): return self
            def execute(self): return Response()

        class Client:
            def table(self, name):
                self.asserted_table = name
                return Query()

        self.assertFalse(_report_text_is_persisted(Client(), "document-id", "patient-id"))


if __name__ == "__main__":
    unittest.main()
