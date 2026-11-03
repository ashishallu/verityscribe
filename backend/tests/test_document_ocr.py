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

    def test_digital_pdf_real_pdfium_geometry_reconstructs_table_row(self):
        objects = [
            b"<< /Type /Catalog /Pages 2 0 R >>",
            b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 600 800] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
            b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
        ]
        stream = (b"BT /F1 12 Tf 1 0 0 1 50 740 Tm (Investigation) Tj "
                  b"1 0 0 1 240 740 Tm (Observed Value) Tj 1 0 0 1 380 740 Tm (Units) Tj "
                  b"1 0 0 1 450 740 Tm (Reference Range) Tj 1 0 0 1 50 710 Tm (Platelet Count) Tj "
                  b"1 0 0 1 240 710 Tm (72,000) Tj 1 0 0 1 380 710 Tm (/uL) Tj "
                  b"1 0 0 1 450 710 Tm (150,000-450,000) Tj ET")
        objects.append(b"<< /Length " + str(len(stream)).encode() + b" >> stream\n" + stream + b"\nendstream")
        pdf = bytearray(b"%PDF-1.4\n")
        offsets = [0]
        for index, obj in enumerate(objects, 1):
            offsets.append(len(pdf))
            pdf.extend(f"{index} 0 obj\n".encode() + obj + b"\nendobj\n")
        xref = len(pdf)
        pdf.extend(f"xref\n0 {len(objects) + 1}\n".encode())
        pdf.extend(b"0000000000 65535 f \n")
        for offset in offsets[1:]:
            pdf.extend(f"{offset:010d} 00000 n \n".encode())
        pdf.extend(f"trailer << /Size {len(objects) + 1} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF".encode())

        text, results = document_ocr.extract_pdf_document(bytes(pdf), 5)
        self.assertIn("[Page 1]", text)
        self.assertEqual(len(results), 1)
        self.assertEqual(results[0]["test_name"], "Platelet Count")
        self.assertEqual(results[0]["result_value"], "72,000")
        self.assertEqual(results[0]["reference_range"], "150,000-450,000")
        self.assertTrue(results[0]["table_bounds"]["y1"] > results[0]["table_bounds"]["y0"])

    @patch("app.services.document_ocr._run_tesseract_tsv", return_value=[
        {"text": "Platelet", "x0": 0, "y0": 20, "x1": 55, "y1": 30, "confidence": 95, "block_num": 1, "par_num": 1, "line_num": 1},
        {"text": "Count", "x0": 60, "y0": 20, "x1": 100, "y1": 30, "confidence": 95, "block_num": 1, "par_num": 1, "line_num": 1},
    ])
    def test_image_document_uses_positioned_tsv_and_preserves_text(self, _tsv):
        text, results = document_ocr.extract_image_document(self._png())
        self.assertEqual(text, "Platelet Count")
        self.assertEqual(results, [])  # no table header, so no inferred test result

    def test_tsv_parser_preserves_geometric_cells_and_confidence(self):
        tsv = (
            "level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext\n"
            "5\t1\t1\t1\t1\t1\t0\t0\t80\t10\t98.4\tInvestigation\n"
            "5\t1\t1\t1\t1\t2\t150\t0\t60\t10\t94\tValue\n"
        ).encode()
        with patch("app.services.document_ocr.subprocess.run") as run:
            run.return_value.returncode = 0
            run.return_value.stdout = tsv
            words = document_ocr._run_tesseract_tsv(b"png")
        self.assertEqual(words[0]["text"], "Investigation")
        self.assertEqual(words[0]["confidence"], 98.4)
        self.assertEqual(words[1]["x0"], 150)
        self.assertEqual(
            document_ocr._merge_pdf_page_text("Hemoglobin 12.4", "Hemoglobin 12.4"),
            "Hemoglobin 12.4",
        )


if __name__ == "__main__":
    unittest.main()
