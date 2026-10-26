import io
import sys
import types
import unittest
from unittest.mock import patch

from PIL import Image
from pypdf import PdfWriter

from app.services import document_ocr
from app.services.ai_service import AIProvider


class LocalDocumentOCRTests(unittest.TestCase):
    def _png(self) -> bytes:
        output = io.BytesIO()
        Image.new("RGB", (120, 80), "white").save(output, format="PNG")
        return output.getvalue()

    @patch("app.services.document_ocr._run_tesseract", return_value="Hemoglobin 12.4 g/dL")
    def test_image_ocr_uses_local_tesseract_and_returns_recognized_text(self, run_ocr):
        extracted = AIProvider().extract_document_text(self._png(), "image/png")
        self.assertIn("Hemoglobin 12.4 g/dL", extracted)
        run_ocr.assert_called_once()

    def test_image_dimensions_are_checked_before_ocr(self):
        with patch("app.services.document_ocr.MAX_IMAGE_PIXELS", 100):
            with self.assertRaisesRegex(ValueError, "image is invalid or exceeds"):
                document_ocr.extract_image_text(self._png())

    def test_invalid_image_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "image is invalid"):
            document_ocr.extract_image_text(b"not an image")

    @patch("app.services.document_ocr._run_tesseract", return_value=" ")
    def test_empty_ocr_output_is_empty_and_not_fabricated(self, _run):
        self.assertEqual(document_ocr.extract_image_text(self._png()), "")

    @patch("app.services.document_ocr.subprocess.run", side_effect=FileNotFoundError)
    def test_missing_tesseract_is_a_safe_runtime_error(self, _run):
        with self.assertRaises(document_ocr.DocumentOCRUnavailable) as error:
            document_ocr._run_tesseract(b"image")
        self.assertEqual(error.exception.category, "engine_not_installed")

    @patch("app.services.document_ocr.subprocess.run", side_effect=__import__("subprocess").TimeoutExpired("tesseract", 1))
    def test_ocr_timeout_is_bounded(self, _run):
        with self.assertRaises(document_ocr.DocumentOCRUnavailable) as error:
            document_ocr._run_tesseract(b"image")
        self.assertEqual(error.exception.category, "ocr_timeout")

    def test_text_pdf_keeps_page_boundaries_without_remote_inference(self):
        writer = PdfWriter()
        page = writer.add_blank_page(width=100, height=100)
        page.extract_text = lambda: "Synthetic report value: Hemoglobin 12.4 g/dL"
        output = io.BytesIO()
        writer.write(output)
        with patch("pypdf.PdfReader") as reader:
            reader.return_value.pages = [types.SimpleNamespace(extract_text=lambda: "Synthetic report value: Hemoglobin 12.4 g/dL", images=[])]
            reader.return_value.is_encrypted = False
            text = document_ocr.extract_pdf_text(output.getvalue(), 5)
        self.assertIn("[Page 1]", text)
        self.assertIn("Hemoglobin 12.4 g/dL", text)

    def test_scanned_pdf_pages_are_rendered_then_ocrd(self):
        writer = PdfWriter()
        writer.add_blank_page(width=100, height=100)
        output = io.BytesIO()
        writer.write(output)

        class Bitmap:
            def to_pil(self):
                return Image.new("RGB", (80, 100), "white")

        class RenderedPage:
            def get_size(self): return (100, 100)
            def render(self, **_kwargs): return Bitmap()
            def close(self): pass

        class FakePdf:
            def __init__(self, _content): pass
            def __len__(self): return 1
            def __getitem__(self, _index): return RenderedPage()
            def close(self): pass

        fake_module = types.SimpleNamespace(PdfDocument=FakePdf)
        with patch("pypdf.PdfReader") as reader, patch.dict(sys.modules, {"pypdfium2": fake_module}), patch.object(document_ocr, "_run_tesseract", return_value="Glucose 95 mg/dL"):
            reader.return_value.pages = [types.SimpleNamespace(extract_text=lambda: "", images=[])]
            reader.return_value.is_encrypted = False
            text = document_ocr.extract_pdf_text(output.getvalue(), 5)
        self.assertIn("[Page 1]", text)
        self.assertIn("Glucose 95 mg/dL", text)

    def test_mixed_pdf_page_preserves_embedded_and_ocr_text(self):
        self.assertEqual(
            document_ocr._merge_pdf_page_text("Patient: A", "Hemoglobin 12.4"),
            "Patient: A\n[Additional text recognized from page image]\nHemoglobin 12.4",
        )
        self.assertEqual(
            document_ocr._merge_pdf_page_text("Hemoglobin 12.4", "Hemoglobin 12.4"),
            "Hemoglobin 12.4",
        )


if __name__ == "__main__":
    unittest.main()
