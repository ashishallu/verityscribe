"""Validation and compatibility helpers for physician-reviewed AI drafts."""

from __future__ import annotations

import json
from typing import Any


FREE_TEXT_PRESCRIPTION_FORMAT = "verityscribe_free_text_prescription_v1"


def normalize_suggested_medications(value: Any, *, limit: int = 30) -> list[dict[str, str]]:
    """Keep only bounded, named medication suggestions for clinician review."""
    if not isinstance(value, list):
        return []
    normalized: list[dict[str, str]] = []
    for item in value[:limit]:
        if not isinstance(item, dict):
            continue
        name = str(item.get("name") or item.get("medicine_name") or "").strip()[:200]
        if not name:
            continue
        normalized.append({
            "name": name,
            "dosage": str(item.get("dosage") or "").strip()[:200],
            "frequency": str(item.get("frequency") or "").strip()[:200],
            "duration": str(item.get("duration") or "").strip()[:200],
            "instructions": str(item.get("instructions") or "").strip()[:1000],
        })
    return normalized


def normalize_clinical_advice(value: Any, *, limit: int = 30) -> list[str]:
    if not isinstance(value, list):
        return []
    return [
        str(item).strip()[:1000]
        for item in value[:limit]
        if isinstance(item, str) and item.strip()
    ]


def encode_free_text_prescription_notes(
    *,
    items: list[dict[str, str]],
    clinical_advice: list[str],
    source: str,
    source_id: str,
    existing_notes: str | None = None,
) -> str:
    """Encode free-text items in the existing notes column; schema has no name column."""
    payload: dict[str, Any] = {
        "format": FREE_TEXT_PRESCRIPTION_FORMAT,
        "source": source,
        "source_id": source_id,
        "free_text_items": items,
        "clinical_advice": clinical_advice,
    }
    if existing_notes:
        try:
            prior = json.loads(existing_notes)
        except (TypeError, ValueError):
            prior = None
        if isinstance(prior, dict) and prior.get("format") == FREE_TEXT_PRESCRIPTION_FORMAT:
            payload["notes"] = prior.get("notes") or ""
        else:
            payload["notes"] = existing_notes[:4000]
    return json.dumps(payload, ensure_ascii=False, separators=(",", ":"))


def decode_free_text_prescription_notes(value: Any) -> dict[str, Any] | None:
    try:
        payload = json.loads(str(value or ""))
    except (TypeError, ValueError):
        return None
    if not isinstance(payload, dict) or payload.get("format") != FREE_TEXT_PRESCRIPTION_FORMAT:
        return None
    payload["free_text_items"] = normalize_suggested_medications(payload.get("free_text_items"))
    payload["clinical_advice"] = normalize_clinical_advice(payload.get("clinical_advice"))
    return payload


def format_treatment_plan(
    clinical_note: str | None,
    clinical_advice: list[str],
    approved_medications: list[dict[str, str]],
) -> str | None:
    sections: list[str] = []
    note = str(clinical_note or "").strip()
    if note:
        sections.append(note[:10000])
    if clinical_advice:
        sections.append("Clinician-reviewed advice:\n" + "\n".join(f"- {item}" for item in clinical_advice))
    if approved_medications:
        lines = []
        for item in approved_medications:
            details = "; ".join(
                f"{label}: {item[key]}" for key, label in (
                    ("dosage", "dosage"), ("frequency", "frequency"),
                    ("duration", "duration"), ("instructions", "instructions"),
                ) if item.get(key)
            )
            lines.append(f"- {item['name']}" + (f" ({details})" if details else ""))
        sections.append("Clinician-approved medication entries:\n" + "\n".join(lines))
    return "\n\n".join(sections)[:12000] or None
