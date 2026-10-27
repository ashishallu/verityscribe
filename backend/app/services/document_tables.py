"""Conservative reconstruction of medical result rows from positioned text.

The OCR/PDF engines remain responsible for reading characters. This module
uses their word geometry to associate headers and cells without changing the
source text or coercing clinical values into numbers.
"""

from __future__ import annotations

from collections import defaultdict
import re
from statistics import median
from typing import Any, Iterable


_HEADER_ALIASES = {
    "test_name": {"test", "test name", "investigation", "parameter", "analyte", "examination"},
    "result_value": {"result", "observed value", "value", "result value", "findings", "reading"},
    "unit": {"unit", "units"},
    "reference_range": {"reference range", "normal range", "biological reference interval", "reference interval", "range"},
    "result_status": {"flag", "status", "remark", "remarks", "interpretation"},
    "specimen": {"specimen", "sample"},
    "collection_date": {"collection date", "collected on", "collection time"},
    "report_date": {"report date", "reported on", "date"},
}


def _normalise(value: str) -> str:
    return " ".join(re.sub(r"[^a-z0-9]+", " ", value.lower()).split())


def _word_lines(words: Iterable[dict[str, Any]]) -> list[list[dict[str, Any]]]:
    rows: list[list[dict[str, Any]]] = []
    for word in sorted(words, key=lambda item: ((float(item["y0"]) + float(item["y1"])) / 2, float(item["x0"]))):
        y = (float(word["y0"]) + float(word["y1"])) / 2
        height = max(1.0, float(word["y1"]) - float(word["y0"]))
        if rows:
            previous_y = median((float(item["y0"]) + float(item["y1"])) / 2 for item in rows[-1])
            previous_height = median(max(1.0, float(item["y1"]) - float(item["y0"])) for item in rows[-1])
            if abs(y - previous_y) <= max(height, previous_height) * 0.65:
                rows[-1].append(word)
                continue
        rows.append([word])
    for row in rows:
        row.sort(key=lambda item: float(item["x0"]))
    return rows


def _cells(line: list[dict[str, Any]]) -> list[dict[str, Any]]:
    if not line:
        return []
    heights = [max(1.0, float(word["y1"]) - float(word["y0"])) for word in line]
    gap_limit = max(5.0, median(heights) * 1.4)
    clusters: list[list[dict[str, Any]]] = [[line[0]]]
    for word in line[1:]:
        gap = float(word["x0"]) - float(clusters[-1][-1]["x1"])
        if gap > gap_limit:
            clusters.append([word])
        else:
            clusters[-1].append(word)
    return [{
        "text": " ".join(str(word["text"]).strip() for word in cluster if str(word["text"]).strip()),
        "x0": min(float(word["x0"]) for word in cluster),
        "x1": max(float(word["x1"]) for word in cluster),
        "confidence": min((float(word.get("confidence", 100)) for word in cluster), default=0.0),
    } for cluster in clusters]


def _headers(cells: list[dict[str, Any]]) -> dict[str, dict[str, Any]]:
    found: dict[str, dict[str, Any]] = {}
    for cell in cells:
        label = _normalise(cell["text"])
        for field, aliases in _HEADER_ALIASES.items():
            if label in aliases:
                found[field] = cell
    return found


def extract_result_rows(
    positioned_words: Iterable[dict[str, Any]], *, page_number: int = 1,
    table_index_start: int = 1,
) -> list[dict[str, Any]]:
    """Associate cells under a recognizable header row; ambiguous rows stay flagged.

    Word dictionaries require text/x0/y0/x1/y1; confidence is optional and is
    conventionally a percentage from OCR. A value is never inferred from a
    reference interval.
    """
    lines = _word_lines(positioned_words)
    results: list[dict[str, Any]] = []
    table_index = table_index_start - 1
    row_index = 0
    active_headers: dict[str, dict[str, Any]] | None = None
    header_y = 0.0
    header_centres: dict[str, float] = {}
    active_rows: list[dict[str, Any]] = []
    active_table_bounds: dict[str, float] | None = None
    active_column_bounds: dict[str, dict[str, float]] = {}

    def close_table() -> None:
        if not active_rows or not active_table_bounds:
            return
        for row in active_rows:
            row["table_bounds"] = dict(active_table_bounds)
    for line in lines:
        cells = _cells(line)
        headers = _headers(cells)
        if "test_name" in headers and ("result_value" in headers or "unit" in headers or "reference_range" in headers):
            close_table()
            active_rows = []
            active_table_bounds = None
            active_headers = headers
            header_y = median((float(word["y0"]) + float(word["y1"])) / 2 for word in line)
            header_centres = {key: (float(cell["x0"]) + float(cell["x1"])) / 2 for key, cell in headers.items()}
            ordered = sorted(header_centres.items(), key=lambda pair: pair[1])
            active_column_bounds = {}
            for index, (key, centre) in enumerate(ordered):
                left = ((ordered[index - 1][1] + centre) / 2 if index else float(headers[key]["x0"]))
                right = ((centre + ordered[index + 1][1]) / 2 if index + 1 < len(ordered)
                         else float(headers[key]["x1"]))
                active_column_bounds[key] = {"x0": left, "x1": right}
            active_table_bounds = {
                "x0": min(float(cell["x0"]) for cell in headers.values()),
                "y0": min(float(word["y0"]) for word in line),
                "x1": max(float(cell["x1"]) for cell in headers.values()),
                "y1": max(float(word["y1"]) for word in line),
            }
            table_index += 1
            row_index = 0
            continue
        if not active_headers:
            continue
        y = median((float(word["y0"]) + float(word["y1"])) / 2 for word in line)
        if y <= header_y:
            continue
        if any(_normalise(cell["text"]) in {"interpretation", "comments", "comment", "conclusion"} for cell in cells):
            active_headers = None
            continue
        if not cells:
            continue

        # Assign each visually separated cell to the nearest detected header.
        assignments: dict[str, list[dict[str, Any]]] = defaultdict(list)
        centers = sorted(header_centres.items(), key=lambda pair: pair[1])
        for cell in cells:
            centre = (float(cell["x0"]) + float(cell["x1"])) / 2
            nearest = min(centers, key=lambda pair: abs(pair[1] - centre))[0]
            assignments[nearest].append(cell)

        row_cells = []
        for cell in cells:
            centre = (float(cell["x0"]) + float(cell["x1"])) / 2
            nearest = min(centers, key=lambda pair: abs(pair[1] - centre))[0]
            crossed = [key for key, bounds in active_column_bounds.items()
                       if float(cell["x0"]) < bounds["x1"] and float(cell["x1"]) > bounds["x0"]]
            row_cells.append({"column": nearest, "text": cell["text"],
                              "bounds": {"x0": float(cell["x0"]), "y0": min(float(word["y0"]) for word in line),
                                         "x1": float(cell["x1"]), "y1": max(float(word["y1"]) for word in line)},
                              "confidence": max(0.0, min(float(cell["confidence"]), 100.0)) / 100.0,
                              "merged_columns": crossed if len(crossed) > 1 else []})

        def field_text(field: str) -> str | None:
            values = assignments.get(field, [])
            text = " ".join(cell["text"] for cell in values).strip()
            return text or None

        test_name = field_text("test_name")
        if not test_name or _normalise(test_name) in {"test", "test name", "investigation", "parameter"}:
            continue
        row_index += 1
        value = field_text("result_value")
        unit = field_text("unit")
        reference = field_text("reference_range")
        status = field_text("result_status")
        specimen = field_text("specimen")
        collection_date = field_text("collection_date")
        report_date = field_text("report_date")
        confidence = min((float(word.get("confidence", 100)) for word in line), default=0.0)
        source_text = " | ".join(cell["text"] for cell in cells)
        ambiguous = value is None or confidence < 20
        row_result = {
            "test_name": test_name,
            "result_value": value,
            "result_text": value,
            "unit": unit,
            "reference_range": reference,
            "result_status": status,
            "specimen": specimen,
            "collection_date": collection_date,
            "report_date": report_date,
            "page_number": page_number,
            "table_index": table_index,
            "row_index": row_index,
            "source_text": source_text,
            "column_headers": {key: cell["text"] for key, cell in active_headers.items()},
            "column_bounds": {key: dict(bounds) for key, bounds in active_column_bounds.items()},
            "row_bounds": {"x0": min(float(cell["x0"]) for cell in cells),
                           "y0": min(float(word["y0"]) for word in line),
                           "x1": max(float(cell["x1"]) for cell in cells),
                           "y1": max(float(word["y1"]) for word in line)},
            "cells": row_cells,
            "extraction_confidence": round(max(0.0, min(confidence, 100.0)) / 100.0, 3),
            "ambiguous": ambiguous,
        }
        if active_table_bounds is None:
            active_table_bounds = {"x0": row_result["row_bounds"]["x0"],
                                   "y0": header_y, "x1": row_result["row_bounds"]["x1"],
                                   "y1": row_result["row_bounds"]["y1"]}
        else:
            active_table_bounds["x0"] = min(active_table_bounds["x0"], row_result["row_bounds"]["x0"])
            active_table_bounds["x1"] = max(active_table_bounds["x1"], row_result["row_bounds"]["x1"])
            active_table_bounds["y1"] = max(active_table_bounds["y1"], row_result["row_bounds"]["y1"])
        results.append(row_result)
        active_rows.append(row_result)
    close_table()
    return results


def format_result_row(result: dict[str, Any]) -> str:
    """Create a retrieval-friendly row without changing source values."""
    parts = [f"Test: {result.get('test_name') or 'unreadable'}"]
    parts.append(f"Result: {result.get('result_value') or 'unreadable'}")
    for label, key in (("Unit", "unit"), ("Reference range", "reference_range"),
                       ("Status", "result_status"), ("Specimen", "specimen")):
        if result.get(key):
            parts.append(f"{label}: {result[key]}")
    parts.append(f"Page: {result.get('page_number')}")
    if result.get("source_text"):
        parts.append(f"Source row: {result['source_text']}")
    return " | ".join(parts)
