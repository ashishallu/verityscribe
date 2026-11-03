"""Bounded, local OCR for private patient documents.

No document bytes leave the API process. Tesseract is deliberately run one page
at a time so the service does not need to load a vision model into Render's
small web-service instance.
"""

from __future__ import annotations

from io import BytesIO
import csv
import io
import re
import subprocess
import os
import time
from statistics import median
from typing import Any

from .document_tables import extract_result_rows


MAX_IMAGE_PIXELS = 18_000_000
MAX_IMAGE_SIDE = 3_000
MAX_OCR_TEXT = 30_000
OCR_TIMEOUT_SECONDS = 35
MAX_DOCUMENT_OCR_SECONDS = 90
OCR_LANGUAGES = "eng+kan"


class DocumentOCRUnavailable(RuntimeError):
    """Local OCR dependency or execution failure; safe for logs/UI categorizing."""

    def __init__(self, category: str):
        self.category = category
        super().__init__(f"Local OCR unavailable ({category})")


def validate_image_dimensions(image_bytes: bytes) -> None:
    """Check format and dimensions from image headers before private Storage writes."""
    from PIL import Image, UnidentifiedImageError

    try:
        with Image.open(BytesIO(image_bytes)) as image:
            if image.format not in {"JPEG", "PNG", "WEBP"}:
                raise ValueError("unsupported image format")
            width, height = image.size
            if width <= 0 or height <= 0 or width * height > MAX_IMAGE_PIXELS:
                raise ValueError("image dimensions exceed the supported limit")
            image.verify()
    except (UnidentifiedImageError, OSError, ValueError) as exc:
        raise ValueError("The uploaded image is invalid or exceeds the image limits") from exc


def _prepare_image(image_bytes: bytes) -> bytes:
    try:
        from PIL import Image, ImageOps, UnidentifiedImageError

        with Image.open(BytesIO(image_bytes)) as source:
            if source.format not in {"JPEG", "PNG", "WEBP"}:
                raise ValueError("unsupported image format")
            width, height = source.size
            if width <= 0 or height <= 0 or width * height > MAX_IMAGE_PIXELS:
                raise ValueError("image dimensions exceed the supported limit")
            image = ImageOps.exif_transpose(source)
            image.thumbnail((MAX_IMAGE_SIDE, MAX_IMAGE_SIDE), Image.Resampling.LANCZOS)
            image = ImageOps.autocontrast(image.convert("L"))
            output = BytesIO()
            image.save(output, format="PNG", optimize=True)
            return output.getvalue()
    except (UnidentifiedImageError, OSError, ValueError) as exc:
        raise ValueError("The uploaded image is invalid or exceeds the image limits") from exc


def _run_tesseract(png_bytes: bytes, timeout_seconds: float = OCR_TIMEOUT_SECONDS) -> str:
    try:
        result = subprocess.run(
            ["tesseract", "stdin", "stdout", "-l", OCR_LANGUAGES, "--oem", "1", "--psm", "6"],
            input=png_bytes,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            env={**os.environ, "OMP_THREAD_LIMIT": "1"},
            timeout=max(0.1, min(timeout_seconds, OCR_TIMEOUT_SECONDS)),
            check=False,
        )
    except FileNotFoundError as exc:
        raise DocumentOCRUnavailable("engine_not_installed") from exc
    except subprocess.TimeoutExpired as exc:
        raise DocumentOCRUnavailable("ocr_timeout") from exc
    except OSError as exc:
        raise DocumentOCRUnavailable("engine_execution_failed") from exc
    if result.returncode != 0:
        raise DocumentOCRUnavailable("engine_execution_failed")
    return result.stdout.decode("utf-8", errors="replace").strip()


def extract_image_text(image_bytes: bytes) -> str:
    """OCR a supported image after dimension checks, EXIF rotation and downscaling."""
    prepared = _prepare_image(image_bytes)
    text = _run_tesseract(prepared)
    return text[:MAX_OCR_TEXT].strip()


def _run_tesseract_tsv(png_bytes: bytes, timeout_seconds: float = OCR_TIMEOUT_SECONDS) -> list[dict]:
    """Get positioned OCR words in one bounded Tesseract pass."""
    try:
        result = subprocess.run(
            ["tesseract", "stdin", "stdout", "-l", OCR_LANGUAGES, "--oem", "1", "--psm", "6", "tsv"],
            input=png_bytes,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            env={**os.environ, "OMP_THREAD_LIMIT": "1"},
            timeout=max(0.1, min(timeout_seconds, OCR_TIMEOUT_SECONDS)),
            check=False,
        )
    except FileNotFoundError as exc:
        raise DocumentOCRUnavailable("engine_not_installed") from exc
    except subprocess.TimeoutExpired as exc:
        raise DocumentOCRUnavailable("ocr_timeout") from exc
    except OSError as exc:
        raise DocumentOCRUnavailable("engine_execution_failed") from exc
    if result.returncode != 0:
        raise DocumentOCRUnavailable("engine_execution_failed")
    try:
        rows = csv.DictReader(io.StringIO(result.stdout.decode("utf-8", errors="replace")), delimiter="\t")
        words = []
        for row in rows:
            text = (row.get("text") or "").strip()
            if not text:
                continue
            words.append({
                "text": text,
                "x0": float(row["left"]), "y0": float(row["top"]),
                "x1": float(row["left"]) + float(row["width"]),
                "y1": float(row["top"]) + float(row["height"]),
                "confidence": max(0.0, float(row.get("conf", 0))),
                "block_num": int(row.get("block_num", 0)),
                "par_num": int(row.get("par_num", 0)),
                "line_num": int(row.get("line_num", 0)),
            })
        return words
    except (KeyError, TypeError, ValueError) as exc:
        raise DocumentOCRUnavailable("ocr_output_invalid") from exc


def _text_from_positioned_words(words: list[dict]) -> str:
    """Rebuild readable source text from TSV without losing page line order."""
    if not words:
        return ""
    rows: dict[tuple[int, int, int], list[dict]] = {}
    # Tesseract TSV includes block/paragraph/line ids. Keep that grouping for
    # source text; table parsing independently uses the word coordinates.
    # The parser itself only needs geometry, so callers may pass optional ids.
    for word in words:
        rows.setdefault((int(word.get("block_num", 0)), int(word.get("par_num", 0)), int(word.get("line_num", 0))), []).append(word)
    lines = []
    for row in rows.values():
        row.sort(key=lambda item: item["x0"])
        lines.append((min(item["y0"] for item in row), " ".join(item["text"] for item in row)))
    return "\n".join(text for _y, text in sorted(lines))[:MAX_OCR_TEXT].strip()


def extract_image_document(image_bytes: bytes) -> tuple[str, list[dict]]:
    """Extract readable text and table rows from one local OCR pass."""
    prepared = _prepare_image(image_bytes)
    words = _run_tesseract_tsv(prepared)
    text = _text_from_positioned_words(words)
    return text, extract_result_rows(words, page_number=1)


def _pdfium_positioned_words(page: Any) -> list[dict]:
    """Recover positioned words from a digital PDF using the existing PDFium dependency."""
    textpage = page.get_textpage()
    try:
        chars = []
        page_height = float(page.get_size()[1])
        for index in range(textpage.count_chars()):
            char = textpage.get_text_range(index, 1)
            if not char or char.isspace():
                continue
            try:
                left, bottom, right, top = textpage.get_charbox(index)
            except (ValueError, RuntimeError):
                continue
            if right <= left or top <= bottom:
                continue
            chars.append({"text": char, "x0": float(left), "y0": page_height - float(top),
                          "x1": float(right), "y1": page_height - float(bottom)})
        if not chars:
            return []

        # Build character lines by vertical geometry, then split words at PDF
        # whitespace gaps. Explicit spaces are omitted by PDFium's char boxes.
        chars.sort(key=lambda item: ((item["y0"] + item["y1"]) / 2, item["x0"]))
        lines: list[list[dict]] = []
        for char in chars:
            center = (char["y0"] + char["y1"]) / 2
            height = max(1.0, char["y1"] - char["y0"])
            if lines:
                prior = lines[-1]
                prior_center = sum((item["y0"] + item["y1"]) / 2 for item in prior) / len(prior)
                prior_height = median(max(1.0, item["y1"] - item["y0"]) for item in prior)
                if abs(center - prior_center) <= max(height, prior_height) * 0.6:
                    prior.append(char)
                    continue
            lines.append([char])
        words: list[dict] = []
        for line in lines:
            line.sort(key=lambda item: item["x0"])
            char_widths = [max(1.0, item["x1"] - item["x0"]) for item in line]
            gap_limit = max(1.5, median(char_widths) * 0.65)
            current = [line[0]]
            groups = []
            for char in line[1:]:
                if char["x0"] - current[-1]["x1"] > gap_limit:
                    groups.append(current)
                    current = [char]
                else:
                    current.append(char)
            groups.append(current)
            for group in groups:
                words.append({"text": "".join(item["text"] for item in group),
                              "x0": min(item["x0"] for item in group),
                              "y0": min(item["y0"] for item in group),
                              "x1": max(item["x1"] for item in group),
                              "y1": max(item["y1"] for item in group),
                              "confidence": 100.0})
        return words
    finally:
        textpage.close()


def _merge_pdf_page_text(embedded: str, ocr: str) -> str:
    embedded, ocr = embedded.strip(), ocr.strip()
    if not embedded:
        return ocr
    if not ocr:
        return embedded
    normalize = lambda value: set(re.findall(r"\w+", value.lower()))
    left, right = normalize(embedded), normalize(ocr)
    overlap = len(left & right) / max(1, len(left | right))
    if left and right and overlap >= 0.65:
        return embedded if len(embedded) >= len(ocr) else ocr
    return f"{embedded}\n[Additional text recognized from page image]\n{ocr}"


def extract_pdf_text(content: bytes, max_pages: int) -> str:
    """Extract embedded text and OCR scanned/mixed PDF pages without dropping pages."""
    from pypdf import PdfReader

    reader = PdfReader(BytesIO(content))
    if reader.is_encrypted:
        raise ValueError("This PDF is password-protected. Upload an unlocked copy.")
    page_count = len(reader.pages)
    if page_count == 0:
        raise ValueError("This PDF has no pages.")
    if page_count > max_pages:
        raise ValueError(f"This PDF has {page_count} pages; the current limit is {max_pages}.")

    pdf = None
    extracted_pages: list[str] = []
    deadline = time.monotonic() + MAX_DOCUMENT_OCR_SECONDS
    try:
        for index, page in enumerate(reader.pages):
            embedded = (page.extract_text() or "").strip()
            # Text-only pages do not incur OCR latency. A page with images and
            # embedded text is treated as mixed: render it to avoid silently
            # omitting labels that exist only in the raster portion.
            has_images = False
            try:
                has_images = bool(list(page.images))
            except Exception as exc:
                raise ValueError(
                    f"PDF page {index + 1} image content could not be verified"
                ) from exc
            if embedded and not has_images:
                page_text = embedded
            else:
                if pdf is None:
                    try:
                        import pypdfium2 as pdfium
                    except ImportError as exc:
                        raise DocumentOCRUnavailable("pdf_renderer_not_installed") from exc
                    pdf = pdfium.PdfDocument(content)
                    if len(pdf) != page_count:
                        raise ValueError("PDF page count could not be verified")
                rendered_page = pdf[index]
                try:
                    width, height = rendered_page.get_size()
                    if width <= 0 or height <= 0:
                        raise ValueError("PDF page has invalid dimensions")
                    scale = min(1.8, MAX_IMAGE_SIDE / max(width, height))
                    bitmap = rendered_page.render(scale=scale, rev_byteorder=True)
                    image = bitmap.to_pil()
                    image.thumbnail((MAX_IMAGE_SIDE, MAX_IMAGE_SIDE))
                    output = BytesIO()
                    image.convert("RGB").save(output, format="PNG", optimize=True)
                    remaining = deadline - time.monotonic()
                    if remaining <= 0:
                        raise DocumentOCRUnavailable("document_timeout")
                    recognized = _run_tesseract(output.getvalue(), timeout_seconds=remaining)
                finally:
                    rendered_page.close()
                page_text = _merge_pdf_page_text(embedded, recognized)
            if not page_text.strip():
                raise ValueError(f"No readable text could be extracted from PDF page {index + 1}")
            extracted_pages.append(f"[Page {index + 1}]\n{page_text}")
    finally:
        if pdf is not None:
            pdf.close()

    return "\n\n".join(extracted_pages)[:MAX_OCR_TEXT].strip()


def extract_pdf_document(content: bytes, max_pages: int) -> tuple[str, list[dict]]:
    """Extract PDF text plus positioned table rows, without hosted vision calls."""
    from pypdf import PdfReader

    reader = PdfReader(BytesIO(content))
    if reader.is_encrypted:
        raise ValueError("This PDF is password-protected. Upload an unlocked copy.")
    page_count = len(reader.pages)
    if page_count == 0:
        raise ValueError("This PDF has no pages.")
    if page_count > max_pages:
        raise ValueError(f"This PDF has {page_count} pages; the current limit is {max_pages}.")

    pdf = None
    extracted_pages: list[str] = []
    structured: list[dict] = []
    deadline = time.monotonic() + MAX_DOCUMENT_OCR_SECONDS
    try:
        for index, page in enumerate(reader.pages):
            embedded = (page.extract_text() or "").strip()
            try:
                has_images = bool(list(page.images))
            except Exception as exc:
                raise ValueError(f"PDF page {index + 1} image content could not be verified") from exc
            positioned_words: list[dict] = []
            if embedded and not has_images:
                # Digital PDF coordinates are read locally via the PDFium
                # dependency already used to render scanned pages.
                if pdf is None:
                    try:
                        import pypdfium2 as pdfium
                        pdf = pdfium.PdfDocument(content)
                    except (ImportError, OSError, RuntimeError):
                        pdf = False  # preserve raw text if optional geometry is unavailable
                if pdf and len(pdf) == page_count:
                    positioned_words = _pdfium_positioned_words(pdf[index])
                page_text = embedded
            else:
                if pdf is None:
                    try:
                        import pypdfium2 as pdfium
                    except ImportError as exc:
                        raise DocumentOCRUnavailable("pdf_renderer_not_installed") from exc
                    pdf = pdfium.PdfDocument(content)
                    if len(pdf) != page_count:
                        raise ValueError("PDF page count could not be verified")
                rendered_page = pdf[index]
                try:
                    width, height = rendered_page.get_size()
                    if width <= 0 or height <= 0:
                        raise ValueError("PDF page has invalid dimensions")
                    scale = min(1.8, MAX_IMAGE_SIDE / max(width, height))
                    bitmap = rendered_page.render(scale=scale, rev_byteorder=True)
                    image = bitmap.to_pil()
                    image.thumbnail((MAX_IMAGE_SIDE, MAX_IMAGE_SIDE))
                    output = BytesIO()
                    image.convert("RGB").save(output, format="PNG", optimize=True)
                    remaining = deadline - time.monotonic()
                    if remaining <= 0:
                        raise DocumentOCRUnavailable("document_timeout")
                    positioned_words = _run_tesseract_tsv(output.getvalue(), timeout_seconds=remaining)
                    recognized = _text_from_positioned_words(positioned_words)
                finally:
                    rendered_page.close()
                page_text = _merge_pdf_page_text(embedded, recognized)
            if not page_text.strip():
                raise ValueError(f"No readable text could be extracted from PDF page {index + 1}")
            extracted_pages.append(f"[Page {index + 1}]\n{page_text}")
            structured.extend(extract_result_rows(positioned_words, page_number=index + 1,
                                                  table_index_start=(max((r["table_index"] for r in structured), default=0) + 1)))
    finally:
        if pdf and pdf is not False:
            pdf.close()
    return "\n\n".join(extracted_pages)[:MAX_OCR_TEXT].strip(), structured
