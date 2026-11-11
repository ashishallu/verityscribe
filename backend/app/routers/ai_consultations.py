"""Appointment-bound patient AI consultations and clinician review."""
import logging
import asyncio
import hashlib
import json
import re
import time
import uuid
import base64
from datetime import datetime, timedelta, timezone
from typing import Any
from urllib.parse import urlsplit

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, WebSocket, WebSocketDisconnect
from jose import ExpiredSignatureError, JWTError, jwt
from pydantic import BaseModel, Field, field_validator
from starlette.concurrency import run_in_threadpool

from ..core.security import current_claims, get_current_doctor, get_current_patient, _role_client
from ..core.config import settings
from ..services.ai_consultation import (
    _patient_name_terms, _retrieve_similar_cases, _terms_from_text,
    emergency_warning, generate_reply, generate_summary, patient_context,
    store_approved_case,
)
from ..services.appointment_expiry import ensure_appointment_not_expired
from ..services.gemini_live import gemini_live
router = APIRouter(prefix="/ai-consultations", tags=["AI consultations"])
logger = logging.getLogger(__name__)
AI_TYPES = {"ai_chat", "ai_virtual"}
_STALE_FINALIZATION_AFTER = timedelta(minutes=5)


class _VoiceClientDisconnected(Exception):
    """Internal signal used to stop a Gemini stream after the browser leaves."""


def _voice_ticket_state_rejection(
    ticket_row: dict[str, Any] | None,
    *,
    session_id: str,
    patient_id: str,
    now: datetime | None = None,
) -> str | None:
    """Mirror the RPC predicates for safe diagnostics; the RPC remains atomic."""
    if not ticket_row:
        return "ticket_invalid"
    if str(ticket_row.get("session_id")) != session_id:
        return "ticket_session_mismatch"
    if str(ticket_row.get("patient_id")) != patient_id:
        return "patient_mismatch"
    if ticket_row.get("consumed_at") is not None:
        return "ticket_already_consumed"
    try:
        expires_at = datetime.fromisoformat(
            str(ticket_row["expires_at"]).replace("Z", "+00:00"),
        )
    except (KeyError, TypeError, ValueError):
        return "ticket_invalid"
    if expires_at.tzinfo is None:
        expires_at = expires_at.replace(tzinfo=timezone.utc)
    current = now or datetime.now(timezone.utc)
    if current.tzinfo is None:
        current = current.replace(tzinfo=timezone.utc)
    if expires_at.astimezone(timezone.utc) <= current.astimezone(timezone.utc):
        return "ticket_expired"
    return None


def _rpc_boolean(data: Any) -> bool:
    """Accept PostgREST scalar bool (or a one-row bool list), never truthy values."""
    if isinstance(data, bool):
        return data
    return bool(
        isinstance(data, list)
        and len(data) == 1
        and isinstance(data[0], bool)
        and data[0]
    )


def _postgrest_authorization_role(client: Any) -> str:
    """Read only the JWT role claim from the outgoing server-side header."""
    try:
        header = client.postgrest.session.headers.get("Authorization", "")
        if not header.startswith("Bearer "):
            return "missing"
        parts = header.removeprefix("Bearer ").split(".")
        if len(parts) != 3:
            return "opaque"
        payload = json.loads(base64.urlsafe_b64decode(parts[1] + "==="))
        return str(payload.get("role", "missing"))
    except Exception:
        return "unavailable"


def _voice_origin_allowed(origin: str | None, allowed_origins: set[str]) -> bool:
    if not origin or origin.rstrip("/") in allowed_origins:
        return True
    try:
        return urlsplit(origin).hostname in {"localhost", "127.0.0.1", "::1"}
    except ValueError:
        return False


def db():
    """Return a bounded service-role client for consultation persistence."""
    cfg = settings()
    if not cfg.supabase_url or not cfg.supabase_service_role_key:
        raise HTTPException(status_code=503, detail="Integration service is unavailable")
    return _role_client(cfg.supabase_url, cfg.supabase_service_role_key)


class StartRequest(BaseModel):
    consent: bool
    voice_consent: bool = False
    similar_case_consent: bool = False


class MessageRequest(BaseModel):
    message: str = Field(min_length=1, max_length=4000)
    request_key: str = Field(min_length=1, max_length=80)


class ReviewRequest(BaseModel):
    decision: str = Field(pattern="^(approved|rejected|request_information)$")
    summary: dict[str, Any] | None = None
    note: str | None = Field(default=None, max_length=4000)

    @field_validator("summary")
    @classmethod
    def bound_summary_size(cls, value):
        if value is not None and len(str(value).encode("utf-8")) > 50000:
            raise ValueError("Reviewed summary is too large")
        return value


def _session_or_404(client, session_id: str, patient_id: str | None = None, doctor_id: str | None = None):
    query = client.table("ai_consultation_sessions").select("*").eq("id", session_id)
    if patient_id:
        query = query.eq("patient_id", patient_id)
    if doctor_id:
        query = query.eq("doctor_id", doctor_id)
    row = _maybe_single_data(query)
    if not row:
        raise HTTPException(status_code=404, detail="AI consultation not found")
    return row


def _maybe_single_data(query):
    """Return the row or None; PostgREST's maybe_single execute can return None."""
    response = query.maybe_single().execute()
    return response.data if response is not None else None


def _messages(client, session_id: str):
    return client.table("ai_consultation_messages").select("id,role,content,created_at,request_key").eq("session_id", session_id).order("created_at").limit(500).execute().data or []


def _summary_is_persisted(session: dict[str, Any]) -> bool:
    summary = session.get("summary")
    return bool(
        isinstance(summary, dict)
        and str(summary.get("clinical_summary", "")).strip()
        and str(summary.get("summary_id", "")).strip()
        and summary.get("session_id") == session.get("id")
    )


def _persisted_summary_matches(session: dict[str, Any], summary: dict[str, Any]) -> bool:
    """Confirm a write by reading the durable row when its response is ambiguous."""
    stored = session.get("summary")
    return bool(
        session.get("status") == "awaiting_doctor_review"
        and _summary_is_persisted(session)
        and isinstance(stored, dict)
        and stored.get("summary_id") == summary.get("summary_id")
        and stored.get("session_id") == summary.get("session_id")
    )


def _read_after_ambiguous_summary_write(client, session_id: str, patient_id: str, summary: dict[str, Any]):
    """Return the exact saved draft if PostgREST committed but its response was lost."""
    try:
        latest = _session_or_404(client, session_id, patient_id=patient_id)
    except Exception as exc:
        logger.warning(
            "AI consultation finalization verification unavailable error_type=%s",
            type(exc).__name__,
        )
        return None
    return latest if _persisted_summary_matches(latest, summary) else None


def _finalization_state(session: dict[str, Any]) -> dict[str, Any]:
    status = session.get("status")
    if status == "failed":
        return {"state": "failed", "summary_persisted": _summary_is_persisted(session)}
    if status in {"processing", "finalizing", "pending"}:
        return {"state": "pending", "summary_persisted": False}
    if status == "in_progress":
        return {"state": "in_progress", "summary_persisted": False}
    if status not in {"awaiting_doctor_review", "approved", "rejected", "completed", "incomplete"} or not _summary_is_persisted(session):
        return {"state": "unknown", "summary_persisted": False}
    summary = session["summary"]
    if status == "incomplete" or summary.get("completion_status") in {"incomplete", "ended_early", "summary_failed"} or summary.get("interview_state") == "ended_early" or summary.get("interview_complete") is False:
        state = "incomplete"
    elif status == "completed" or session.get("mode") != "ai_virtual" or (
        summary.get("interview_state") == "complete" and summary.get("interview_complete") is True
    ):
        state = "completed"
    else:
        return {"state": "unknown", "summary_persisted": True}
    return {
        "state": state,
        "summary_persisted": True,
        "summary_id": summary["summary_id"],
        "session_id": session["id"],
    }


def _processing_is_stale(session: dict[str, Any], *, now: datetime | None = None) -> bool:
    """Allow recovery only when a previous finalizer stopped making progress."""
    stamp = session.get("updated_at") or session.get("ended_at")
    if not stamp:
        return True
    try:
        parsed = datetime.fromisoformat(str(stamp).replace("Z", "+00:00"))
    except (TypeError, ValueError):
        return True
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    current = now or datetime.now(timezone.utc)
    return current - parsed.astimezone(timezone.utc) >= _STALE_FINALIZATION_AFTER


def _fallback_clinical_summary(messages: list[dict[str, Any]], *, interview_complete: bool) -> dict[str, Any]:
    """Build a concise clinician-readable fallback from question/answer context."""
    reported: list[str] = []
    detail_lines: list[str] = []
    uncertainties: list[str] = []
    categorized: dict[str, list[str]] = {
        "symptoms": [], "onset_duration": [], "severity_frequency": [],
        "associated_symptoms": [], "relevant_history": [], "medications": [],
        "allergies": [], "additional_notes": [],
    }
    previous_question = ""
    for row in messages:
        content = str(row.get("content", "")).strip()[:2000]
        role = row.get("role")
        if not content:
            continue
        if role == "assistant":
            previous_question = content.lower()
            continue
        if role != "patient":
            continue
        reported.append(content)
        q = previous_question
        previous_question = ""
        # Keep the verbatim answer in the raw messages table. Mixed-script
        # fragments from live ASR are not silently transliterated into facts.
        summary_content = re.sub(r"[\u0900-\u0DFF]+", "[unclear speech fragment]", content).strip().rstrip(" .!?;:")
        if summary_content != content and "The transcript contains a speech-recognition fragment in another script; confirm its meaning with the patient." not in uncertainties:
            uncertainties.append("The transcript contains a speech-recognition fragment in another script; confirm its meaning with the patient.")
        if summary_content == "[unclear speech fragment]":
            continue
        if re.search(r"\b(surgery|operation|operated|arthroscopy|replacement)\b", content, re.I):
            categorized["relevant_history"].append(content)
            detail_lines.append(f"Relevant history: the patient reports {summary_content}")
            continue
        if not detail_lines:
            categorized["symptoms"].append(content)
            detail_lines.append(f"Presenting concern: the patient reports {summary_content}")
        elif any(term in q for term in ("which side", "right or left", "one or both", "which joint", "which knee", "where exactly", "where is the pain", "what area")):
            categorized["additional_notes"].append(f"Location/laterality: {content}")
            detail_lines.append(f"Location/laterality: the patient reports {summary_content}")
        elif any(term in q for term in ("1 to 10", "1-10", "1–10", "one to ten", "rate", "severity", "how severe", "pain score")):
            categorized["severity_frequency"].append(content)
            rating = re.fullmatch(r"\s*(\d+(?:\.\d+)?)\s*", summary_content)
            if rating and any(term in q for term in ("1 to 10", "1-10", "1–10", "one to ten", "scale")):
                detail_lines.append(
                    f"Severity: patient rated the symptom {rating.group(1)}/10 when asked for a 1-10 rating"
                )
            else:
                detail_lines.append(f"Severity/detail: the patient's response was '{summary_content}'")
        elif any(term in q for term in ("other symptom", "associated symptom", "anything else", "what else", "along with")):
            categorized["associated_symptoms"].append(content)
            detail_lines.append(f"Additional symptoms: the patient reports '{summary_content}'")
        elif any(term in q for term in ("how long", "when did", "how many days", "since when", "duration", "started", "begin")):
            categorized["onset_duration"].append(content)
            detail_lines.append(f"Onset/duration: the patient reports '{summary_content}'")
        elif any(term in q for term in ("constant", "how often", "frequency", "come and go", "pattern")):
            categorized["severity_frequency"].append(content)
            detail_lines.append(f"Pattern/frequency: the patient reports '{summary_content}'")
        elif any(term in q for term in ("describe", "what does it feel", "what is the pain like", "character", "quality")):
            categorized["symptoms"].append(content)
            detail_lines.append(f"Pain character: the patient describes it as “{summary_content}”")
        elif any(term in q for term in ("medical history", "medical conditions", "past history", "health conditions")):
            categorized["relevant_history"].append(content)
            detail_lines.append(f"Relevant history: the patient reports '{summary_content}'")
        elif any(term in q for term in ("medication", "medicines", "taking any drugs")):
            categorized["medications"].append(content)
            detail_lines.append(f"Current medication: the patient reports '{summary_content}'")
        elif any(term in q for term in ("allerg", "allergic")):
            categorized["allergies"].append(content)
            detail_lines.append(f"Allergies: the patient's response was '{summary_content}'")
        else:
            categorized["additional_notes"].append(content)
            detail_lines.append(f"Additional patient-reported detail: '{summary_content}'")
    completion = "complete" if interview_complete else "ended_early"
    clinical_summary = ". ".join(detail_lines)
    if clinical_summary and not clinical_summary.endswith((".", "!", "?")):
        clinical_summary += "."
    return {
        "clinical_summary": clinical_summary or "No patient statements were captured. Clinician review is required.",
        "chief_complaint": reported[0] if reported else None,
        **categorized,
        "positive_findings": [],
        "negative_findings": [],
        "risk_indicators": [],
        "follow_up_recommendations": [],
        "items_for_doctor_review": ["Review the attributed patient statements; structured summary generation was unavailable."],
        "unanswered_questions": [],
        "uncertainties": uncertainties + ["Clinical fields not explicitly extracted are unknown, not confirmed absent."],
        "draft_prescription": None,
        "review_required": True,
        "interview_complete": interview_complete,
        "interview_state": completion,
        "completion_status": completion,
    }


def _response(client, session: dict[str, Any], *, include_similar_cases: bool = False):
    visible_session = dict(session)
    if not include_similar_cases and isinstance(visible_session.get("summary"), dict):
        summary = dict(visible_session["summary"])
        similar_cases = summary.get("similar_cases")
        if isinstance(similar_cases, dict) and similar_cases.get("matches"):
            summary["similar_cases"] = {"status": "available_to_clinician", "matches": []}
        visible_session["summary"] = summary
    return {
        "session": visible_session,
        "messages": _messages(client, session["id"]),
        "finalization": _finalization_state(session),
    }


@router.post("/appointments/{appointment_id}/start")
def start_consultation(appointment_id: str, payload: StartRequest, claims: dict = Depends(current_claims)):
    if claims.get("app_metadata", {}).get("role") != "patient":
        raise HTTPException(status_code=403, detail="Patient access required")
    patient = get_current_patient(claims)
    if not payload.consent:
        raise HTTPException(status_code=422, detail="AI consultation consent is required")
    client = db()
    appointment = _maybe_single_data(
        client.table("appointments").select("*").eq("id", appointment_id).eq("patient_id", patient["id"])
    )
    if not appointment:
        raise HTTPException(status_code=404, detail="Appointment not found")
    ensure_appointment_not_expired(appointment)
    mode = appointment.get("consultation_type")
    if mode not in AI_TYPES:
        raise HTTPException(status_code=422, detail="This appointment is not an AI consultation")
    if mode == "ai_virtual" and not payload.voice_consent:
        raise HTTPException(status_code=422, detail="Voice processing consent is required")
    if appointment.get("status") in {"cancelled", "completed", "no_show"}:
        raise HTTPException(status_code=409, detail="This appointment is no longer available")
    existing = _maybe_single_data(
        client.table("ai_consultation_sessions").select("*").eq("appointment_id", appointment_id)
    )
    if existing:
        if existing["status"] in {"approved", "rejected"}:
            raise HTTPException(status_code=409, detail="This consultation has already been reviewed")
        return {"data": _response(client, existing)}
    now = datetime.now(timezone.utc).isoformat()
    try:
        session = client.table("ai_consultation_sessions").insert({
            "appointment_id": appointment_id, "patient_id": patient["id"], "doctor_id": appointment["doctor_id"],
            "hospital_id": appointment["hospital_id"], "mode": mode, "status": "in_progress",
            "consent_at": now, "voice_consent_at": now if mode == "ai_virtual" else None,
            "similar_case_consent_at": now if payload.similar_case_consent else None,
        }).execute().data[0]
    except Exception:
        # Unique appointment_id makes racing starts idempotent.
        session = _maybe_single_data(
            client.table("ai_consultation_sessions").select("*").eq("appointment_id", appointment_id)
        )
        if not session:
            logger.exception("Unable to create AI consultation session")
            raise HTTPException(status_code=503, detail="Unable to start consultation")
    greeting = "Hello, I’m Verity, an AI intake assistant. I’ll ask a few questions to help prepare a draft for your doctor to review. What would you like help with today?"
    if mode == "ai_chat" and not _messages(client, session["id"]):
        client.table("ai_consultation_messages").insert({"session_id": session["id"], "role": "assistant", "content": greeting}).execute()
    return {"data": _response(client, session)}


@router.get("/{session_id}")
def get_session(session_id: str, claims: dict = Depends(current_claims)):
    role = claims.get("app_metadata", {}).get("role")
    client = db()
    if role == "patient":
        session = _session_or_404(client, session_id, patient_id=claims["sub"])
    elif role == "doctor":
        session = _session_or_404(client, session_id, doctor_id=claims["sub"])
    else:
        raise HTTPException(status_code=403, detail="Insufficient role")
    return {"data": _response(client, session)}


def _send_message(client, session: dict[str, Any], content: str, request_key: str):
    if session["status"] != "in_progress":
        raise HTTPException(status_code=409, detail="This consultation is not accepting messages")
    prior = _messages(client, session["id"])
    request_rows = client.table("ai_consultation_messages").select(
        "id,role,content,created_at,request_key"
    ).eq("session_id", session["id"]).eq("request_key", request_key).order("created_at").execute().data or []
    patient_message = next((row for row in request_rows if row.get("role") == "patient"), None)
    assistant_message = next((row for row in request_rows if row.get("role") == "assistant"), None)
    if patient_message and patient_message.get("content") != content:
        raise HTTPException(status_code=409, detail="Request key was already used for a different message")
    emergency = emergency_warning(content) or any(
        row.get("role") == "patient" and emergency_warning(str(row.get("content", "")))
        for row in prior
    )
    if patient_message and assistant_message:
        return {"messages": [patient_message, assistant_message], "emergency_warning": emergency}
    if not patient_message:
        try:
            patient_message = client.table("ai_consultation_messages").insert({
                "session_id": session["id"], "role": "patient", "content": content,
                "request_key": request_key,
                "created_at": datetime.now(timezone.utc).isoformat(),
            }).execute().data[0]
        except Exception:
            # Concurrent retries with the same key reuse the single saved turn.
            request_rows = client.table("ai_consultation_messages").select(
                "id,role,content,created_at,request_key"
            ).eq("session_id", session["id"]).eq("request_key", request_key).order("created_at").execute().data or []
            patient_message = next((row for row in request_rows if row.get("role") == "patient"), None)
            assistant_message = next((row for row in request_rows if row.get("role") == "assistant"), None)
            if not patient_message:
                raise
            if patient_message.get("content") != content:
                raise HTTPException(status_code=409, detail="Request key was already used for a different message")
            if assistant_message:
                return {"messages": [patient_message, assistant_message], "emergency_warning": emergency}
    history = [row for row in prior if row.get("id") != patient_message.get("id")]
    try:
        reply = generate_reply(
            client, patient_id=session["patient_id"], hospital_id=session["hospital_id"],
            similar_case_consent=bool(session.get("similar_case_consent_at")),
            mode=session["mode"], history=history, message=content,
        )
    except Exception as exc:
        reason = "unknown"
        if isinstance(exc, RuntimeError):
            message = str(exc)
            prefix = "Cloudflare Workers AI request failed (HTTP "
            if message.startswith(prefix) and message.endswith(")"):
                status = message[len(prefix):-1]
                if len(status) == 3 and status.isdigit():
                    reason = f"http_{status}"
            elif message == "Cloudflare Workers AI is not configured":
                reason = "not_configured"
            elif message == "Cloudflare Workers AI returned an unsuccessful response":
                reason = "unsuccessful_response"
            elif message == "Cloudflare Workers AI returned an empty answer":
                reason = "empty_answer"
        logger.warning("AI consultation provider unavailable (%s, reason=%s)", type(exc).__name__, reason)
        raise HTTPException(status_code=503, detail="Your message was saved. The AI reply is temporarily unavailable; you can retry your message.")
    try:
        assistant_message = client.table("ai_consultation_messages").insert({
        "session_id": session["id"], "role": "assistant", "content": reply,
        "request_key": request_key,
        "created_at": (datetime.now(timezone.utc) + timedelta(milliseconds=1)).isoformat(),
        }).execute().data[0]
    except Exception:
        request_rows = client.table("ai_consultation_messages").select(
            "id,role,content,created_at,request_key"
        ).eq("session_id", session["id"]).eq("request_key", request_key).order("created_at").execute().data or []
        assistant_message = next((row for row in request_rows if row.get("role") == "assistant"), None)
        if not assistant_message:
            raise HTTPException(status_code=503, detail="Your message was saved. The AI reply could not be saved; retry your message.")
    return {"messages": [patient_message, assistant_message], "emergency_warning": emergency}


@router.post("/{session_id}/messages")
def send_message(session_id: str, payload: MessageRequest, claims: dict = Depends(current_claims)):
    patient = get_current_patient(claims)
    client = db()
    session = _session_or_404(client, session_id, patient_id=patient["id"])
    if session["mode"] == "ai_virtual":
        raise HTTPException(status_code=409, detail="AI Virtual messages must use the Gemini Live session")
    return {"data": _send_message(client, session, payload.message.strip(), payload.request_key)}


def _voice_ticket_secret() -> str:
    service_key = settings().supabase_service_role_key
    if not service_key:
        raise HTTPException(status_code=503, detail="Voice session security is unavailable")
    return hashlib.sha256((service_key + ":ai-voice-ticket-v1").encode()).hexdigest()


@router.post("/{session_id}/voice-ticket")
def create_voice_ticket(session_id: str, claims: dict = Depends(current_claims)):
    patient = get_current_patient(claims)
    client = db()
    session = _session_or_404(client, session_id, patient_id=patient["id"])
    if session["mode"] != "ai_virtual" or not session.get("voice_consent_at"):
        raise HTTPException(status_code=403, detail="Voice processing was not authorized for this session")
    if session["status"] != "in_progress":
        raise HTTPException(status_code=409, detail="This consultation is not accepting voice")
    appointment = _maybe_single_data(
        client.table("appointments").select("id,appointment_date,appointment_time")
        .eq("id", session["appointment_id"]).eq("patient_id", patient["id"])
    )
    if not appointment:
        raise HTTPException(status_code=404, detail="Appointment not found")
    ensure_appointment_not_expired(appointment)
    ticket_id = str(uuid.uuid4())
    expires = datetime.now(timezone.utc) + timedelta(seconds=90)
    client.table("ai_consultation_voice_tickets").insert({
        "id": ticket_id,
        "session_id": session_id,
        "patient_id": patient["id"],
        "expires_at": expires.isoformat(),
    }).execute()
    token = jwt.encode({
        "sub": patient["id"],
        "session_id": session_id,
        "jti": ticket_id,
        "aud": "verityscribe-ai-voice",
        "exp": expires,
    }, _voice_ticket_secret(), algorithm="HS256")
    return {"data": {"ticket": token, "expires_at": expires.isoformat()}}


@router.websocket("/{session_id}/voice-stream")
async def voice_stream(websocket: WebSocket, session_id: str):
    """Authenticated native bidirectional Gemini Live audio session."""
    correlation_id = uuid.uuid4().hex
    stage = "voice_ticket_received"
    ticket_prefix = "verityscribe-ticket."
    ticket_protocol = next((value for value in websocket.scope.get("subprotocols", [])
                            if value.startswith(ticket_prefix)), None)
    if not ticket_protocol:
        logger.warning(
            "AI Virtual handshake correlation_id=%s stage=voice_ticket_received outcome=rejected reason=ticket_missing",
            correlation_id,
        )
        await websocket.close(code=4401, reason="Voice ticket required")
        return
    token = ticket_protocol[len(ticket_prefix):]
    logger.info(
        "AI Virtual handshake correlation_id=%s stage=voice_ticket_received ticket_present=true",
        correlation_id,
    )
    try:
        stage = "voice_ticket_validation"
        claims = jwt.decode(token, _voice_ticket_secret(), algorithms=["HS256"], audience="verityscribe-ai-voice")
        patient_id = str(claims["sub"])
        ticket_id = str(uuid.UUID(claims["jti"]))
        ticket_session_id = str(uuid.UUID(claims["session_id"]))
        requested_session_id = str(uuid.UUID(session_id))
        if ticket_session_id != requested_session_id:
            logger.warning(
                "AI Virtual handshake correlation_id=%s stage=voice_ticket_validation outcome=rejected reason=ticket_session_mismatch",
                correlation_id,
            )
            await websocket.close(code=4401, reason="Invalid voice ticket")
            return
        session_id = requested_session_id
    except ExpiredSignatureError:
        logger.warning(
            "AI Virtual handshake correlation_id=%s stage=voice_ticket_validation outcome=rejected reason=ticket_expired",
            correlation_id,
        )
        await websocket.close(code=4401, reason="Voice ticket expired")
        return
    except (JWTError, KeyError, ValueError, HTTPException):
        logger.warning(
            "AI Virtual handshake correlation_id=%s stage=voice_ticket_validation outcome=rejected reason=ticket_invalid",
            correlation_id,
        )
        await websocket.close(code=4401, reason="Invalid voice ticket")
        return

    logger.info(
        "AI Virtual handshake correlation_id=%s stage=voice_ticket_validation outcome=ok",
        correlation_id,
    )

    # Validate origin before consuming the single-use ticket. Otherwise a
    # rejected browser origin burns a valid ticket and makes retry diagnosis
    # misleading. No consultation data is sent before authorization.
    stage = "voice_session_authorization"
    allowed_origins = set(settings().cors_origin_list)
    allowed_origins.add("https://verityscribe-patient-app.onrender.com")
    origin = websocket.headers.get("origin")
    if not _voice_origin_allowed(origin, allowed_origins):
        logger.warning(
            "AI Virtual handshake correlation_id=%s stage=voice_session_authorization outcome=rejected reason=origin_not_allowed",
            correlation_id,
        )
        await websocket.close(code=4403, reason="Origin is not allowed")
        return

    try:
        client = db()
    except Exception as exc:
        logger.exception(
            "AI Virtual handshake correlation_id=%s stage=voice_session_authorization outcome=error reason=service_client_unavailable error_type=%s",
            correlation_id, type(exc).__name__,
        )
        await websocket.close(code=1011, reason="Voice authorization unavailable")
        return

    # Decode only the role claim from the configured PostgREST Authorization
    # header. Never log the header, key, or JWT. This detects a shared client
    # accidentally carrying a patient token before the ticket RPC is called.
    postgrest_role = _postgrest_authorization_role(client)
    logger.info(
        "AI Virtual handshake correlation_id=%s stage=voice_session_authorization service_client_authorization_role=%s",
        correlation_id, postgrest_role,
    )

    try:
        stage = "voice_session_validation"
        session = await run_in_threadpool(_session_or_404, client, session_id)
        if session.get("patient_id") != patient_id:
            logger.warning(
                "AI Virtual handshake correlation_id=%s stage=voice_session_validation outcome=rejected reason=patient_mismatch",
                correlation_id,
            )
            await websocket.close(code=4403, reason="Voice session is unavailable")
            return
        if session["mode"] != "ai_virtual" or not session.get("voice_consent_at") or session["status"] != "in_progress":
            logger.warning(
                "AI Virtual handshake correlation_id=%s stage=voice_session_validation outcome=rejected reason=session_not_active",
                correlation_id,
            )
            await websocket.close(code=4403, reason="Voice session is unavailable")
            return
        logger.info(
            "AI Virtual handshake correlation_id=%s stage=voice_session_validation outcome=ok",
            correlation_id,
        )

        stage = "appointment_validation"
        appointment = await run_in_threadpool(lambda: _maybe_single_data(
            client.table("appointments").select("id,appointment_date,appointment_time")
            .eq("id", session["appointment_id"]).eq("patient_id", patient_id)
        ))
        if not appointment:
            logger.warning(
                "AI Virtual handshake correlation_id=%s stage=appointment_validation outcome=rejected reason=appointment_not_found",
                correlation_id,
            )
            await websocket.close(code=4403, reason="Appointment is unavailable")
            return
        try:
            ensure_appointment_not_expired(appointment)
        except HTTPException as exc:
            reason = "appointment_expired" if exc.status_code == 410 else "appointment_expiry_unverified"
            logger.warning(
                "AI Virtual handshake correlation_id=%s stage=appointment_validation outcome=rejected reason=%s",
                correlation_id, reason,
            )
            await websocket.close(code=4403, reason="Appointment is unavailable")
            return
        logger.info(
            "AI Virtual handshake correlation_id=%s stage=appointment_validation outcome=ok",
            correlation_id,
        )

        stage = "voice_ticket_state_validation"
        ticket_row = await run_in_threadpool(lambda: _maybe_single_data(
            client.table("ai_consultation_voice_tickets")
            .select("session_id,patient_id,expires_at,consumed_at")
            .eq("id", ticket_id)
        ))
        reason = _voice_ticket_state_rejection(
            ticket_row, session_id=session_id, patient_id=patient_id,
        )
        if reason:
            logger.warning(
                "AI Virtual handshake correlation_id=%s stage=voice_ticket_state_validation outcome=rejected reason=%s",
                correlation_id, reason,
            )
            await websocket.close(code=4401, reason="Voice ticket is invalid, expired, or already used")
            return

        stage = "voice_ticket_consumption_result"
        consumed_result = await run_in_threadpool(lambda: client.rpc(
            "consume_ai_consultation_voice_ticket", {
                "p_ticket_id": ticket_id,
                "p_session_id": session_id,
                "p_patient_id": patient_id,
            },
        ).execute())
        consumed_data = getattr(consumed_result, "data", False)
        consumed = _rpc_boolean(consumed_data)
        logger.info(
            "AI Virtual handshake correlation_id=%s stage=voice_ticket_consumption_result ticket_consumed=%s service_client_authorization_role=%s",
            correlation_id, str(consumed).lower(), postgrest_role,
        )
        if not consumed:
            logger.warning(
                "AI Virtual handshake correlation_id=%s stage=voice_ticket_consumption_result outcome=rejected reason=rpc_returned_false",
                correlation_id,
            )
            await websocket.close(code=4401, reason="Voice ticket is invalid, expired, or already used")
            return
        logger.info(
            "AI Virtual handshake correlation_id=%s stage=voice_ticket_consumption_result outcome=ok",
            correlation_id,
        )
    except HTTPException as exc:
        reason = "session_not_found" if exc.status_code == 404 else "authorization_validation_failed"
        logger.warning(
            "AI Virtual handshake correlation_id=%s stage=%s outcome=rejected reason=%s status=%d",
            correlation_id, stage, reason, exc.status_code,
        )
        await websocket.close(code=4401, reason="Voice session is unavailable")
        return
    except Exception as exc:
        logger.exception(
            "AI Virtual handshake correlation_id=%s stage=%s outcome=error error_type=%s",
            correlation_id, stage, type(exc).__name__,
        )
        await websocket.close(code=1011, reason="Voice session authorization unavailable")
        return

    await websocket.accept(subprotocol=ticket_protocol)
    logger.info(
        "AI Virtual handshake correlation_id=%s stage=websocket_accepted outcome=ok",
        correlation_id,
    )
    voice_started_at = time.perf_counter()
    first_patient_audio_logged = False
    first_gemini_audio_logged = False
    # ADK's imports can scan installed entry points; initialize off the event
    # loop and only after the patient has authenticated and opened the socket.
    logger.info(
        "AI Virtual handshake correlation_id=%s stage=gemini_initialization_started",
        correlation_id,
    )
    initialized = await run_in_threadpool(gemini_live.initialize)
    logger.info("AI Virtual latency stage=backend_initialize elapsed_ms=%d", round((time.perf_counter() - voice_started_at) * 1000))
    if not initialized:
        logger.warning("AI Virtual startup failed stage=initialize error_type=%s",
                       gemini_live.initialization_error or "unknown")
        try:
            await websocket.send_json({"type": "error", "message": "Live consultation is temporarily unavailable."})
        except (WebSocketDisconnect, RuntimeError):
            pass
        try:
            await websocket.close(code=1011, reason="Gemini Live is not configured")
        except (WebSocketDisconnect, RuntimeError):
            pass
        return
    queue = gemini_live.request_queue_type()
    types = gemini_live.types
    user_id = patient_id
    context = None
    live_iterator = None
    first_event = None
    startup_messages: list[dict[str, Any]] = []
    session_created = False
    ended_before_ready = False
    input_partial = ""
    assistant_partial = ""
    current_turn_key = f"live-{uuid.uuid4()}"
    pending_text_turns: list[tuple[str | None, str]] = []

    async def send_json(event: dict[str, Any]) -> None:
        try:
            await websocket.send_json(event)
        except (WebSocketDisconnect, RuntimeError) as exc:
            raise _VoiceClientDisconnected from exc

    async def send_bytes(data: bytes) -> None:
        try:
            await websocket.send_bytes(data)
        except (WebSocketDisconnect, RuntimeError) as exc:
            raise _VoiceClientDisconnected from exc

    async def close_socket(code: int, reason: str) -> None:
        try:
            await websocket.close(code=code, reason=reason)
        except (WebSocketDisconnect, RuntimeError):
            # Starlette rejects a close frame after it has already observed a
            # disconnect. The session cleanup still runs in the outer finally.
            pass

    async def persist_turn(request_key: str, patient_text: str, assistant_text: str) -> None:
        for role, text in (("patient", patient_text.strip()), ("assistant", assistant_text.strip())):
            if not text:
                continue
            try:
                def save_if_missing():
                    existing = client.table("ai_consultation_messages").select("id").eq(
                        "session_id", session_id).eq("request_key", request_key).eq(
                        "role", role).limit(1).execute()
                    if getattr(existing, "data", None):
                        return
                    client.table("ai_consultation_messages").insert({
                        "session_id": session_id, "role": role, "content": text[:12000],
                        "request_key": request_key,
                    }).execute()
                await run_in_threadpool(save_if_missing)
            except Exception as exc:
                logger.warning("AI Virtual transcript persistence failed (%s)", type(exc).__name__)
                raise

    async def mark_interview_complete() -> None:
        # This is a state marker only. The consultation remains in_progress
        # until the patient ends the call and the final summary is persisted.
        def save_completion_marker():
            result = client.table("ai_consultation_sessions").update({
                "summary": {"interview_complete": True},
            }).eq("id", session_id).eq("patient_id", patient_id).eq(
                "status", "in_progress").select("id").execute()
            if not getattr(result, "data", None):
                raise RuntimeError("Consultation is no longer active")
        await run_in_threadpool(save_completion_marker)

    async def cleanup_live_session() -> None:
        queue.close()
        if live_iterator is not None:
            try:
                await live_iterator.aclose()
            except Exception as exc:
                logger.info("AI Virtual upstream cleanup failed (%s)", type(exc).__name__)
        if session_created:
            await gemini_live.delete_session(user_id=user_id, session_id=session_id)

    async def clinical_handler(question: str) -> str:
        rows = await run_in_threadpool(_messages, client, session_id)
        context = await run_in_threadpool(patient_context, client, patient_id)
        case_matches = []
        if session.get("similar_case_consent_at"):
            patient_names = await run_in_threadpool(_patient_name_terms, client, patient_id)
            if patient_names is not None:
                terms = _terms_from_text(question, excluded_terms=patient_names)
                case_matches = await run_in_threadpool(
                    _retrieve_similar_cases, client,
                    hospital_id=session["hospital_id"], patient_id=patient_id,
                    terms=terms, consent=True,
                ) or []
        # Gemini is the sole conversational model in AI Virtual. Return only
        # server-authorized facts as tool evidence; never chain another LLM.
        evidence = {
            "verified_patient_context": context,
            "recent_consultation_transcript": rows[-16:],
            "deidentified_hospital_patterns": case_matches,
            "note": "Similar-case patterns are not facts about this patient.",
        }
        return json.dumps(evidence, ensure_ascii=False, default=str)[:18000]

    try:
        history = await run_in_threadpool(_messages, client, session_id)
        context = {"appointment_id": session["appointment_id"], "hospital_id": session["hospital_id"],
                   "consultation_mode": "ai_virtual", "recent_transcript": history[-16:]}
        # delete_session also clears the per-session tool handlers if ADK's
        # create_session partially succeeds and then raises.
        session_created = True
        await gemini_live.create_session(
            user_id=user_id, session_id=session_id, context=context,
            handler=clinical_handler, completion_handler=mark_interview_complete,
        )
        live = gemini_live.runner.run_live(user_id=user_id, session_id=session_id,
                                           live_request_queue=queue, run_config=gemini_live.run_config())
        live_iterator = live.__aiter__()
        # Native-audio Live models wait for an input turn before speaking. Give
        # the agent a one-time kickoff after explicit patient consent so Start
        # Call produces an audible greeting without routing through TTS.
        queue.send_content(types.Content(
            role="user",
            parts=[types.Part(text="Start the consultation with a brief, warm greeting and ask what the patient would like help with today.")],
        ))
        # run_live() is a lazy async generator: constructing it does not start
        # the provider connection. Wait for its first real event before telling
        # Flutter that the call is ready.
        next_event_task = asyncio.create_task(live_iterator.__anext__())
        disconnect_task = asyncio.create_task(websocket.receive())
        startup_deadline = asyncio.get_running_loop().time() + 35
        while True:
            remaining = startup_deadline - asyncio.get_running_loop().time()
            if remaining <= 0:
                raise TimeoutError("Gemini Live produced no startup event")
            done, _ = await asyncio.wait(
                {next_event_task, disconnect_task}, timeout=remaining,
                return_when=asyncio.FIRST_COMPLETED,
            )
            if not done:
                raise TimeoutError("Gemini Live produced no startup event")
            if disconnect_task in done:
                startup_message = disconnect_task.result()
                if startup_message.get("type") == "websocket.disconnect":
                    raise _VoiceClientDisconnected
                startup_text = startup_message.get("text")
                if startup_text:
                    try:
                        startup_command = json.loads(startup_text)
                    except (TypeError, json.JSONDecodeError):
                        startup_command = {}
                    if isinstance(startup_command, dict) and startup_command.get("type") == "end":
                        # Hang Up is valid before Gemini's greeting. Do not
                        # keep the browser waiting for the provider's first
                        # event after it has explicitly ended the call.
                        ended_before_ready = True
                        break
                if len(startup_messages) >= 16:
                    raise ValueError("Too many client frames arrived before startup")
                startup_messages.append(startup_message)
                disconnect_task = asyncio.create_task(websocket.receive())
            if next_event_task in done:
                first_event = next_event_task.result()
                break
        if ended_before_ready:
            if not next_event_task.done():
                next_event_task.cancel()
                await asyncio.gather(next_event_task, return_exceptions=True)
            if not disconnect_task.done():
                disconnect_task.cancel()
                await asyncio.gather(disconnect_task, return_exceptions=True)
            await persist_turn(current_turn_key, "", "")
            await send_json({"type": "session_saved"})
            await cleanup_live_session()
            return
        logger.info("AI Virtual latency stage=gemini_first_event elapsed_ms=%d", round((time.perf_counter() - voice_started_at) * 1000))
        disconnect_task.cancel()
        await asyncio.gather(disconnect_task, return_exceptions=True)
        await send_json({"type": "ready"})
        logger.info(
            "AI Virtual handshake correlation_id=%s stage=gemini_ready outcome=ok",
            correlation_id,
        )
    except Exception as exc:
        if 'next_event_task' in locals() and not next_event_task.done():
            next_event_task.cancel()
            await asyncio.gather(next_event_task, return_exceptions=True)
        if 'disconnect_task' in locals() and not disconnect_task.done():
            disconnect_task.cancel()
            await asyncio.gather(disconnect_task, return_exceptions=True)
        if not isinstance(exc, _VoiceClientDisconnected):
            # This startup phase runs before any patient audio is consumed.
            # Logging the traceback identifies SDK/runtime failures while
            # avoiding request payloads, clinical text, or credential values.
            logger.exception(
                "AI Virtual startup failed stage=adk_first_event error_type=%s",
                type(exc).__name__,
            )
            try:
                await send_json({
                    "type": "error",
                    "code": "live_start_failed",
                    "message": "Gemini Live could not start securely. Please try again.",
                })
            except _VoiceClientDisconnected:
                pass
            await close_socket(1011, "Gemini Live session could not start")
        await cleanup_live_session()
        return

    async def browser_to_gemini():
        nonlocal first_patient_audio_logged
        startup_index = 0
        while True:
            if startup_index < len(startup_messages):
                message = startup_messages[startup_index]
                startup_index += 1
            else:
                message = await websocket.receive()
            if message.get("type") == "websocket.disconnect":
                return
            frame = message.get("bytes")
            if frame is not None:
                if not first_patient_audio_logged:
                    first_patient_audio_logged = True
                    logger.info(
                        "AI Virtual latency stage=first_patient_audio_received elapsed_ms=%d frame_bytes=%d",
                        round((time.perf_counter() - voice_started_at) * 1000), len(frame),
                    )
                if len(frame) > 256 * 1024:
                    await close_socket(4400, "Audio frame too large")
                    return
                queue.send_realtime(types.Blob(data=frame, mime_type="audio/pcm;rate=16000"))
                continue
            text = message.get("text")
            if text:
                try:
                    command = json.loads(text)
                except (TypeError, json.JSONDecodeError):
                    continue
                if command.get("type") == "end":
                    return
                if command.get("type") == "text" and isinstance(command.get("text"), str):
                    prompt = command["text"].strip()[:4000]
                    if prompt:
                        request_key = command.get("request_key")
                        pending_text_turns.append((
                            str(request_key)[:80] if request_key else None, prompt,
                        ))
                        queue.send_content(types.Content(
                            role="user", parts=[types.Part(text=prompt)],
                        ))

    async def gemini_to_browser():
        nonlocal input_partial, assistant_partial, current_turn_key, first_gemini_audio_logged
        async def events():
            if first_event is not None:
                yield first_event
            if live_iterator is not None:
                async for event in live_iterator:
                    yield event

        async for event in events():
            if event.interrupted:
                interrupted_turn = current_turn_key
                await send_json({"type": "interrupted", "turn_id": interrupted_turn})
                assistant_partial = ""
                current_turn_key = f"live-{uuid.uuid4()}"
            content = event.content
            if content and content.parts:
                for part in content.parts:
                    if part.inline_data and part.inline_data.data:
                        if not first_gemini_audio_logged:
                            first_gemini_audio_logged = True
                            logger.info("AI Virtual latency stage=first_gemini_audio elapsed_ms=%d", round((time.perf_counter() - voice_started_at) * 1000))
                        # Forward native Gemini PCM chunks immediately; Flutter
                        # feeds these into its PCM stream player without waiting
                        # for the end of the assistant turn.
                        await send_bytes(part.inline_data.data)
            if event.input_transcription and event.input_transcription.text:
                if not input_partial:
                    await send_json({"type": "speech_started"})
                input_partial += event.input_transcription.text
                logger.info(
                    "AI Virtual event=input_transcription chars=%d final=%s",
                    len(event.input_transcription.text),
                    bool(event.input_transcription.finished),
                )
                await send_json({"type": "caption", "role": "patient", "text": event.input_transcription.text,
                                 "final": bool(event.input_transcription.finished),
                                 "turn_id": current_turn_key})
            if event.output_transcription and event.output_transcription.text:
                assistant_partial += event.output_transcription.text
                logger.info(
                    "AI Virtual event=output_transcription chars=%d final=%s",
                    len(event.output_transcription.text),
                    bool(event.output_transcription.finished),
                )
                await send_json({"type": "caption", "role": "assistant", "text": event.output_transcription.text,
                                 "final": bool(event.output_transcription.finished),
                                 "turn_id": current_turn_key})
            if event.turn_complete:
                typed_turn = pending_text_turns.pop(0) if pending_text_turns else (None, "")
                input_text = input_partial.strip() or typed_turn[1]
                request_key = typed_turn[0] or current_turn_key
                current_turn_key = request_key
                assistant_text = assistant_partial.strip()
                await persist_turn(request_key, input_text, assistant_text)
                marker = await run_in_threadpool(_session_or_404, client, session_id, patient_id)
                marker_summary = marker.get("summary")
                completed = bool(
                    marker_summary.get("interview_complete")
                    if isinstance(marker_summary, dict) else False
                )
                # Reset before sending the UI event. If the browser has left,
                # the finalizer must not persist the same complete turn under
                # a second request key.
                input_partial = assistant_partial = ""
                current_turn_key = f"live-{uuid.uuid4()}"
                await send_json({"type": "turn_complete", "request_key": request_key,
                                 "input_text": input_text,
                                 "output_text": assistant_text,
                                 "emergency_warning": emergency_warning(input_text),
                                 "consultation_complete": completed})

        # A normal iterator close is unexpected during an active call. Surface
        # it instead of leaving Flutter with a dead socket that looks as if it
        # is still listening.
        logger.error("AI Virtual provider event stream closed while session active")
        await send_json({
            "type": "error",
            "code": "live_stream_closed",
            "message": "Gemini Live closed the voice stream. Reconnect before continuing.",
        })

    try:
        tasks = {
            asyncio.create_task(browser_to_gemini()),
            asyncio.create_task(gemini_to_browser()),
        }
        done, pending = await asyncio.wait(tasks, return_when=asyncio.FIRST_COMPLETED)
        for task in pending:
            task.cancel()
        if pending:
            await asyncio.gather(*pending, return_exceptions=True)
        for task in done:
            try:
                task.result()
            except (WebSocketDisconnect, asyncio.CancelledError, _VoiceClientDisconnected):
                pass
    except (WebSocketDisconnect, _VoiceClientDisconnected):
        pass
    except Exception as exc:
        logger.exception("AI Virtual stream failed error_type=%s", type(exc).__name__)
        try:
            await send_json({"type": "error", "message": "The live voice connection ended."})
        except _VoiceClientDisconnected:
            pass
    finally:
        # Persist partial speech/captions even if the patient ends the call or
        # the socket drops before Gemini emits turn_complete.
        partial_key, partial_input = current_turn_key, input_partial
        if pending_text_turns:
            typed_key, typed_text = pending_text_turns[0]
            partial_key = typed_key or partial_key
            partial_input = partial_input or typed_text
        try:
            await persist_turn(partial_key, partial_input, assistant_partial)
            try:
                await send_json({"type": "session_saved"})
            except _VoiceClientDisconnected:
                pass
        except Exception as exc:
            logger.error("AI Virtual final transcript save failed error_type=%s", type(exc).__name__)
        finally:
            await cleanup_live_session()


@router.post("/{session_id}/voice-turn")
async def voice_turn(session_id: str, audio: UploadFile = File(...), request_key: str = Form(..., min_length=1, max_length=80), claims: dict = Depends(current_claims)):
    raise HTTPException(status_code=410, detail="Upload-based voice turns were removed; use the authenticated Gemini Live stream.")


@router.post("/{session_id}/end")
def end_consultation(session_id: str, claims: dict = Depends(current_claims)):
    patient = get_current_patient(claims)
    client = db()
    session = _session_or_404(client, session_id, patient_id=patient["id"])
    if session["status"] in {"awaiting_doctor_review", "approved", "rejected", "completed", "incomplete"}:
        # Preserve idempotency, and backfill persistence identifiers for older
        # summaries when the patient retries after a lost acknowledgement.
        if not _summary_is_persisted(session) and isinstance(session.get("summary"), dict):
            summary = dict(session["summary"])
            if str(summary.get("clinical_summary", "")).strip():
                summary.setdefault("summary_id", str(uuid.uuid4()))
                summary["session_id"] = session_id
                try:
                    saved = client.table("ai_consultation_sessions").update({
                        "summary": summary,
                        "updated_at": datetime.now(timezone.utc).isoformat(),
                    }).eq("id", session_id).eq("patient_id", patient["id"]).eq(
                        "status", session["status"]).execute().data or []
                    if saved:
                        session = saved[0]
                except Exception as exc:
                    logger.error("AI consultation summary marker persistence failed error_type=%s", type(exc).__name__)
                    raise HTTPException(status_code=503, detail="The saved summary could not be verified. Retry finalization.")
        return {"data": _response(client, session)}
    if session["status"] == "processing":
        if not _processing_is_stale(session):
            raise HTTPException(status_code=409, detail="This consultation is already being finalized; retry shortly")
        # A prior request may have died after claiming finalization. Reclaim
        # with a compare-and-set so two patient retries cannot both take over.
        previous_updated_at = session.get("updated_at")
        reclaim = client.table("ai_consultation_sessions").update({
            "status": "in_progress",
            "updated_at": datetime.now(timezone.utc).isoformat(),
        }).eq("id", session_id).eq("patient_id", patient["id"]).eq(
            "status", "processing")
        if previous_updated_at is not None:
            reclaim = reclaim.eq("updated_at", previous_updated_at)
        reclaimed = reclaim.execute().data or []
        if not reclaimed:
            raise HTTPException(status_code=409, detail="Finalization recovery was claimed by another request; retry shortly")
        session = _session_or_404(client, session_id, patient_id=patient["id"])
    messages = _messages(client, session_id)
    if not messages:
        if session["mode"] == "ai_virtual":
            # A patient can hang up before Gemini's first greeting or before
            # any speech is transcribed. Keep that as an explicit incomplete
            # clinician-review draft instead of trapping the patient in-call
            # or inventing a transcript/summary.
            ended_at = datetime.now(timezone.utc).isoformat()
            summary = {
                "summary_id": str(uuid.uuid4()),
                "session_id": session_id,
                "clinical_summary": "The consultation ended before a transcript was captured. No clinical information was recorded; clinician review is required.",
                "interview_complete": False,
                "interview_state": "ended_early",
                "chief_complaint": None,
                "symptoms": [],
                "relevant_history": [],
                "medications": [],
                "allergies": [],
                "positive_findings": [],
                "negative_findings": [],
                "risk_indicators": [],
                "follow_up_recommendations": [],
                "items_for_doctor_review": ["No transcript was captured before the patient ended the call."],
                "uncertainties": ["The interview did not capture patient statements."],
                "draft_prescription": None,
                "review_required": True,
                "completion_status": "ended_early",
            }
            ended = client.table("ai_consultation_sessions").update({
                "status": "awaiting_doctor_review",
                "summary": summary,
                "ended_at": ended_at,
                "updated_at": ended_at,
            }).eq("id", session_id).eq("patient_id", patient["id"]).eq(
                "status", session["status"]).execute().data or []
            if ended:
                return {"data": _response(client, ended[0])}
            latest = _session_or_404(client, session_id, patient_id=patient["id"])
            if latest["status"] in {"awaiting_doctor_review", "approved", "rejected"}:
                return {"data": _response(client, latest)}
            raise HTTPException(status_code=409, detail="This consultation changed while it was being ended")
        raise HTTPException(status_code=409, detail="Add a message before ending the consultation")
    summary_state = session.get("summary")
    interview_complete = bool(
        summary_state.get("interview_complete")
        if isinstance(summary_state, dict) else False
    ) if session["mode"] == "ai_virtual" else True
    processing_result = client.table("ai_consultation_sessions").update({
        "status": "processing",
        "ended_at": datetime.now(timezone.utc).isoformat(),
        "updated_at": datetime.now(timezone.utc).isoformat(),
    }).eq("id", session_id).eq("patient_id", patient["id"]).eq(
        "status", session["status"]).select("id").execute()
    if not getattr(processing_result, "data", None):
        raise HTTPException(status_code=409, detail="This consultation changed while it was being finalized")
    try:
        appointment = client.table("appointments").select("*").eq("id", session["appointment_id"]).single().execute().data
        if session["mode"] == "ai_virtual":
            verified_context = patient_context(client, patient["id"])
            summary = gemini_live.generate_consultation_summary(
                transcript=messages,
                patient_context=verified_context,
                appointment=appointment,
                interview_complete=interview_complete,
            )
            summary["interview_state"] = "complete" if interview_complete else "ended_early"
            summary["completion_status"] = summary["interview_state"]
        else:
            summary = generate_summary(
                client, patient_id=patient["id"], hospital_id=session["hospital_id"],
                mode=session["mode"], appointment=appointment, messages=messages,
                similar_case_consent=bool(session.get("similar_case_consent_at")),
            )
    except Exception as exc:
        logger.exception(
            "AI consultation summary generation failed mode=%s error_type=%s",
            session["mode"], type(exc).__name__,
        )
        fallback = _fallback_clinical_summary(messages, interview_complete=interview_complete)
        fallback.update({"summary_id": str(uuid.uuid4()), "session_id": session_id,
                         "interview_state": "ended_early" if not interview_complete else "summary_fallback",
                         "summary_generation_status": "failed",
                         "uncertainties": fallback["uncertainties"] + ["Some transcript wording may need confirmation against the original conversation."],
                         "items_for_doctor_review": fallback["items_for_doctor_review"] + ["Confirm the location, side, and prior procedure details with the patient."],
                         "completion_status": "incomplete"})
        try:
            saved_fallback = client.table("ai_consultation_sessions").update({
                "status": "awaiting_doctor_review", "summary": fallback,
                "ended_at": datetime.now(timezone.utc).isoformat(),
                "updated_at": datetime.now(timezone.utc).isoformat(),
            }).eq("id", session_id).eq("patient_id", patient["id"]).execute().data or []
            if not saved_fallback:
                raise RuntimeError("Factual incomplete draft was not persisted")
        except Exception as persist_exc:
            # A timeout can occur after PostgREST committed the update. Verify
            # by session + summary ID before labeling the durable session failed.
            verified = _read_after_ambiguous_summary_write(
                client, session_id, patient["id"], fallback,
            )
            if verified:
                logger.warning("AI consultation fallback write verified after ambiguous response")
                return {"data": _response(client, verified)}
            logger.exception(
                "AI consultation fallback persistence failed error_type=%s",
                type(persist_exc).__name__,
            )
            try:
                client.table("ai_consultation_sessions").update({
                    "status": "failed", "updated_at": datetime.now(timezone.utc).isoformat(),
                }).eq("id", session_id).eq("patient_id", patient["id"]).eq(
                    "status", "processing").execute()
            except Exception as failed_state_exc:
                logger.error("AI consultation failed-state persistence failed error_type=%s", type(failed_state_exc).__name__)
            raise HTTPException(status_code=503, detail="The factual draft could not be persisted. The transcript remains available for retry.")
        return {"data": _response(client, saved_fallback[0])}
    summary["summary_id"] = str(uuid.uuid4())
    summary["session_id"] = session_id
    try:
        persisted = client.table("ai_consultation_sessions").update({
            "status": "awaiting_doctor_review", "summary": summary,
            "ended_at": datetime.now(timezone.utc).isoformat(),
            "updated_at": datetime.now(timezone.utc).isoformat(),
        }).eq("id", session_id).eq("patient_id", patient["id"]).eq(
            "status", "processing").execute().data or []
        if not persisted:
            raise RuntimeError("Consultation summary update returned no row")
        session = persisted[0]
    except Exception as exc:
        # Do not convert a committed-but-unacknowledged write into a failed
        # session. Read back the exact draft; unrelated/old summaries do not
        # satisfy the check because summary_id and session_id must match.
        verified = _read_after_ambiguous_summary_write(
            client, session_id, patient["id"], summary,
        )
        if verified:
            logger.warning("AI consultation summary write verified after ambiguous response")
            return {"data": _response(client, verified)}
        logger.exception(
            "AI consultation summary persistence failed error_type=%s",
            type(exc).__name__,
        )
        fallback = _fallback_clinical_summary(messages, interview_complete=interview_complete)
        fallback.update({
            "summary_id": str(uuid.uuid4()),
            "session_id": session_id,
            "interview_state": "summary_persistence_failed",
            "completion_status": "summary_persistence_failed",
            "uncertainties": fallback["uncertainties"] + ["The structured summary could not be persisted; clinician review is required."],
        })
        try:
            failed = client.table("ai_consultation_sessions").update({
                "status": "failed", "summary": fallback,
                "updated_at": datetime.now(timezone.utc).isoformat(),
            }).eq("id", session_id).eq("patient_id", patient["id"]).eq(
                "status", "processing").execute().data or []
            if not failed:
                logger.error("AI consultation failed state could not be persisted after summary write failure")
        except Exception as persist_exc:
            logger.error("AI consultation failed-state persistence failed error_type=%s", type(persist_exc).__name__)
        raise HTTPException(status_code=503, detail="The transcript is saved, but the consultation summary could not be persisted. Retry ending the consultation.")
    return {"data": _response(client, session)}


@router.get("/doctor/appointments/{appointment_id}")
def doctor_review_detail(appointment_id: str, doctor: dict = Depends(get_current_doctor)):
    client = db()
    session = _maybe_single_data(
        client.table("ai_consultation_sessions").select("*").eq("appointment_id", appointment_id).eq("doctor_id", doctor["id"])
    )
    appointment = _maybe_single_data(
        client.table("appointments").select("*").eq("id", appointment_id).eq("doctor_id", doctor["id"])
    )
    if not appointment:
        raise HTTPException(status_code=404, detail="Appointment not found")
    if appointment.get("consultation_type") not in AI_TYPES:
        raise HTTPException(status_code=422, detail="This is not an AI consultation appointment")
    if not session:
        patient = _maybe_single_data(
            client.table("profiles").select("id,first_name,last_name").eq("id", appointment["patient_id"])
        )
        return {"data": {"session": None, "messages": [], "appointment": appointment, "patient": patient}}
    patient = _maybe_single_data(
        client.table("profiles").select("id,first_name,last_name").eq("id", session["patient_id"])
    )
    return {"data": {**_response(client, session, include_similar_cases=True), "appointment": appointment, "patient": patient}}


@router.post("/doctor/sessions/{session_id}/review")
def review_consultation(session_id: str, payload: ReviewRequest, doctor: dict = Depends(get_current_doctor)):
    client = db()
    session = _session_or_404(client, session_id, doctor_id=doctor["id"])
    if session["status"] in {"approved", "rejected"}:
        raise HTTPException(status_code=409, detail="This consultation has already been reviewed")
    if session["status"] not in {"awaiting_doctor_review", "failed"}:
        raise HTTPException(status_code=409, detail="This consultation is not ready for review")
    before = session.get("summary")
    after = payload.summary if payload.summary is not None else before
    if payload.decision == "approved":
        if not isinstance(after, dict) or not str(after.get("clinical_summary", "")).strip():
            raise HTTPException(status_code=422, detail="A reviewed clinical summary is required")
        existing = client.table("consultations").select("id").eq("appointment_id", session["appointment_id"]).limit(1).execute().data or []
        if existing:
            consultation = existing[0]
        else:
            consultation = client.table("consultations").insert({
                "appointment_id": session["appointment_id"], "patient_id": session["patient_id"],
                "doctor_id": doctor["id"], "consultation_date": datetime.now(timezone.utc).isoformat(),
                "consultation_type": session["mode"], "symptoms": after.get("clinical_summary"),
                "diagnosis": None, "treatment_plan": None,
            }).execute().data[0]
        session = client.table("ai_consultation_sessions").update({
            "status": "approved", "summary": after, "consultation_id": consultation["id"],
            "reviewed_by": doctor["id"], "reviewed_at": datetime.now(timezone.utc).isoformat(), "review_note": payload.note,
        }).eq("id", session_id).eq("doctor_id", doctor["id"]).execute().data[0]
        store_approved_case(client, session=session, summary=after)
    elif payload.decision == "rejected":
        session = client.table("ai_consultation_sessions").update({
            "status": "rejected", "summary": after, "reviewed_by": doctor["id"],
            "reviewed_at": datetime.now(timezone.utc).isoformat(), "review_note": payload.note,
        }).eq("id", session_id).eq("doctor_id", doctor["id"]).execute().data[0]
    else:
        if not payload.note or not payload.note.strip():
            raise HTTPException(status_code=422, detail="Explain what additional information is needed")
        session = client.table("ai_consultation_sessions").update({
            "status": "in_progress", "summary": after, "reviewed_by": doctor["id"],
            "reviewed_at": datetime.now(timezone.utc).isoformat(), "review_note": payload.note,
        }).eq("id", session_id).eq("doctor_id", doctor["id"]).execute().data[0]
        client.table("ai_consultation_messages").insert({
            "session_id": session_id, "role": "system",
            "content": payload.note.strip(),
        }).execute()
    client.table("ai_consultation_review_events").insert({
        "session_id": session_id, "doctor_id": doctor["id"], "action": payload.decision,
        "before_summary": before, "after_summary": after, "note": payload.note,
    }).execute()
    return {"data": session}
