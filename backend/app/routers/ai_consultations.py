"""Appointment-bound patient AI consultations and clinician review."""
import logging
import asyncio
import hashlib
import json
import os
import uuid
from datetime import datetime, timedelta, timezone
from typing import Any
from urllib.parse import urlencode

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, WebSocket, WebSocketDisconnect
from jose import JWTError, jwt
from pydantic import BaseModel, Field, field_validator
from starlette.concurrency import run_in_threadpool

from ..core.security import current_claims, get_current_doctor, get_current_patient
from ..core.config import settings
from ..services.ai_consultation import (
    emergency_warning, generate_reply, generate_summary, store_approved_case,
)
from ..services.ai_service import ai_provider
from .integration import db

router = APIRouter(prefix="/ai-consultations", tags=["AI consultations"])
logger = logging.getLogger(__name__)
AI_TYPES = {"ai_chat", "ai_virtual"}


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
    row = query.maybe_single().execute().data
    if not row:
        raise HTTPException(status_code=404, detail="AI consultation not found")
    return row


def _messages(client, session_id: str):
    return client.table("ai_consultation_messages").select("id,role,content,created_at,request_key").eq("session_id", session_id).order("created_at").limit(500).execute().data or []


def _response(client, session: dict[str, Any], *, include_similar_cases: bool = False):
    visible_session = dict(session)
    if not include_similar_cases and isinstance(visible_session.get("summary"), dict):
        summary = dict(visible_session["summary"])
        similar_cases = summary.get("similar_cases")
        if isinstance(similar_cases, dict) and similar_cases.get("matches"):
            summary["similar_cases"] = {"status": "available_to_clinician", "matches": []}
        visible_session["summary"] = summary
    return {"session": visible_session, "messages": _messages(client, session["id"])}


@router.post("/appointments/{appointment_id}/start")
def start_consultation(appointment_id: str, payload: StartRequest, claims: dict = Depends(current_claims)):
    if claims.get("app_metadata", {}).get("role") != "patient":
        raise HTTPException(status_code=403, detail="Patient access required")
    patient = get_current_patient(claims)
    if not payload.consent:
        raise HTTPException(status_code=422, detail="AI consultation consent is required")
    client = db()
    appointment = client.table("appointments").select("*").eq("id", appointment_id).eq("patient_id", patient["id"]).maybe_single().execute().data
    if not appointment:
        raise HTTPException(status_code=404, detail="Appointment not found")
    mode = appointment.get("consultation_type")
    if mode not in AI_TYPES:
        raise HTTPException(status_code=422, detail="This appointment is not an AI consultation")
    if mode == "ai_virtual" and not payload.voice_consent:
        raise HTTPException(status_code=422, detail="Voice processing consent is required")
    if appointment.get("status") in {"cancelled", "completed", "no_show"}:
        raise HTTPException(status_code=409, detail="This appointment is no longer available")
    existing = client.table("ai_consultation_sessions").select("*").eq("appointment_id", appointment_id).maybe_single().execute().data
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
        session = client.table("ai_consultation_sessions").select("*").eq("appointment_id", appointment_id).maybe_single().execute().data
        if not session:
            logger.exception("Unable to create AI consultation session")
            raise HTTPException(status_code=503, detail="Unable to start consultation")
    greeting = "Hello, I’m Verity, an AI intake assistant. I’ll ask a few questions to help prepare a draft for your doctor to review. What would you like help with today?"
    if not _messages(client, session["id"]):
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
        logger.warning("AI consultation provider unavailable (%s)", type(exc).__name__)
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
    """Forward live PCM to Cloudflare STT; retain only final transcript/message rows."""
    from websockets.asyncio.client import connect as upstream_connect

    ticket_prefix = "verityscribe-ticket."
    ticket_protocol = next((value for value in websocket.scope.get("subprotocols", [])
                            if value.startswith(ticket_prefix)), None)
    if not ticket_protocol:
        await websocket.close(code=4401, reason="Voice ticket required")
        return
    token = ticket_protocol[len(ticket_prefix):]
    try:
        claims = jwt.decode(token, _voice_ticket_secret(), algorithms=["HS256"], audience="verityscribe-ai-voice")
        patient_id = str(claims["sub"])
        ticket_id = str(uuid.UUID(claims["jti"]))
        if str(uuid.UUID(claims["session_id"])) != session_id:
            raise JWTError("ticket session mismatch")
    except (JWTError, KeyError, ValueError, HTTPException):
        await websocket.close(code=4401, reason="Invalid voice ticket")
        return

    client = db()
    try:
        session = await run_in_threadpool(_session_or_404, client, session_id, patient_id=patient_id)
        if session["mode"] != "ai_virtual" or not session.get("voice_consent_at") or session["status"] != "in_progress":
            await websocket.close(code=4403, reason="Voice session is unavailable")
            return
        consumed_result = await run_in_threadpool(lambda: client.rpc(
            "consume_ai_consultation_voice_ticket", {
                "p_ticket_id": ticket_id,
                "p_session_id": session_id,
                "p_patient_id": patient_id,
            },
        ).execute())
        consumed = getattr(consumed_result, "data", False)
        if consumed is not True and consumed != [True]:
            await websocket.close(code=4401, reason="Voice ticket has expired or was already used")
            return
    except HTTPException:
        await websocket.close(code=4401, reason="Voice session is unavailable")
        return
    except Exception as exc:
        logger.info("AI Virtual session authorization unavailable (%s)", type(exc).__name__)
        await websocket.close(code=1011, reason="Voice session authorization unavailable")
        return

    account_id = os.getenv("CLOUDFLARE_ACCOUNT_ID", "").strip()
    api_token = os.getenv("CLOUDFLARE_API_TOKEN", "").strip()
    if not account_id or not api_token:
        await websocket.close(code=1011, reason="Live speech service is not configured")
        return

    allowed_origins = set(settings().cors_origin_list)
    allowed_origins.add("https://verityscribe-patient-app.onrender.com")
    origin = websocket.headers.get("origin")
    if origin and origin.rstrip("/") not in allowed_origins:
        await websocket.close(code=4403, reason="Origin is not allowed")
        return

    query = urlencode({
        "encoding": "linear16", "sample_rate": "16000", "channels": "1",
        "language": "en-US", "mode": "medical", "punctuate": "true",
        "interim_results": "true", "endpointing": "300",
        "utterance_end_ms": "1000", "vad_events": "true",
    })
    upstream_url = (
        f"wss://api.cloudflare.com/client/v4/accounts/{account_id}/ai/run/"
        f"@cf/deepgram/nova-3?{query}"
    )
    try:
        upstream = await upstream_connect(
            upstream_url,
            additional_headers={"Authorization": f"Bearer {api_token}"},
            open_timeout=12,
            ping_interval=20,
            max_size=1024 * 1024,
        )
    except Exception as exc:
        logger.info("AI Virtual upstream unavailable (%s)", type(exc).__name__)
        await websocket.close(code=1011, reason="Live speech service is unavailable")
        return

    await websocket.accept(subprotocol=ticket_protocol)
    final_segments: list[str] = []
    turn_queue: asyncio.Queue[tuple[str, str]] = asyncio.Queue()
    try:
        async def forward_audio():
            async for message in websocket.iter_bytes():
                if len(message) > 256 * 1024:
                    await websocket.close(code=4400, reason="Audio frame is too large")
                    return
                await upstream.send(message)

        async def forward_transcripts():
            async for raw in upstream:
                if not isinstance(raw, str):
                    continue
                try:
                    event = json.loads(raw)
                except (TypeError, json.JSONDecodeError):
                    continue
                event_type = str(event.get("type", ""))
                if event_type.casefold() in {"speechstarted", "speech_started"}:
                    await websocket.send_json({"type": "speech_started"})
                    continue
                channel = event.get("channel") or {}
                alternatives = channel.get("alternatives") or []
                text = str(alternatives[0].get("transcript", "")).strip() if alternatives else ""
                if text:
                    final = event.get("is_final") is True
                    if final:
                        final_segments.append(text)
                    await websocket.send_json({
                        "type": "transcript", "text": text, "final": final,
                    })
                if event.get("speech_final") is True:
                    transcript = " ".join(final_segments).strip() or text
                    final_segments.clear()
                    if not transcript:
                        continue
                    request_key = str(uuid.uuid4())
                    await websocket.send_json({
                        "type": "turn_started", "request_key": request_key,
                        "transcript": transcript,
                    })
                    await turn_queue.put((request_key, transcript))

        async def process_turns():
            while True:
                request_key, transcript = await turn_queue.get()
                try:
                    result = await run_in_threadpool(
                        _send_message, client, session, transcript, request_key,
                    )
                    await websocket.send_json({
                        "type": "turn", "request_key": request_key,
                        "transcript": transcript, "messages": result["messages"],
                        "emergency_warning": result["emergency_warning"],
                    })
                except HTTPException as exc:
                    await websocket.send_json({
                        "type": "turn_error", "request_key": request_key,
                        "transcript": transcript, "message": exc.detail,
                    })
                except Exception as exc:
                    logger.info("AI Virtual turn failed (%s)", type(exc).__name__)
                    await websocket.send_json({
                        "type": "turn_error", "request_key": request_key,
                        "transcript": transcript,
                        "message": "The reply could not be prepared. Retry the saved transcript.",
                    })
                finally:
                    turn_queue.task_done()

        tasks = {
            asyncio.create_task(forward_audio()),
            asyncio.create_task(forward_transcripts()),
            asyncio.create_task(process_turns()),
        }
        done, pending = await asyncio.wait(tasks, return_when=asyncio.FIRST_COMPLETED)
        for task in pending:
            task.cancel()
        for task in done:
            try:
                task.result()
            except (WebSocketDisconnect, asyncio.CancelledError):
                pass
    except WebSocketDisconnect:
        pass
    except Exception as exc:
        logger.info("AI Virtual stream ended (%s)", type(exc).__name__)
        try:
            await websocket.send_json({"type": "error", "message": "The live voice connection ended."})
        except Exception:
            pass
    finally:
        await upstream.close()


@router.post("/{session_id}/voice-turn")
async def voice_turn(session_id: str, audio: UploadFile = File(...), request_key: str = Form(..., min_length=1, max_length=80), claims: dict = Depends(current_claims)):
    patient = get_current_patient(claims)
    client = db()
    session = _session_or_404(client, session_id, patient_id=patient["id"])
    if session["mode"] != "ai_virtual" or not session.get("voice_consent_at"):
        raise HTTPException(status_code=403, detail="Voice processing was not authorized for this session")
    content = await audio.read(10 * 1024 * 1024 + 1)
    if len(content) > 10 * 1024 * 1024:
        raise HTTPException(status_code=413, detail="Audio must be 10 MB or smaller")
    if not content:
        raise HTTPException(status_code=422, detail="Audio is empty")
    try:
        result = await run_in_threadpool(ai_provider.transcribe_consensus, content, audio.filename or "ai-virtual.wav")
        transcript = result.final_text.strip()
    except Exception as exc:
        logger.warning("AI Virtual transcription unavailable (%s)", type(exc).__name__)
        raise HTTPException(status_code=503, detail="Speech recognition is temporarily unavailable. Please retry.")
    finally:
        del content
    if not transcript:
        raise HTTPException(status_code=422, detail="No clear speech was detected; please try again")
    response = await run_in_threadpool(_send_message, client, session, transcript, request_key)
    return {"data": {**response, "transcript": transcript}}


@router.post("/{session_id}/end")
def end_consultation(session_id: str, claims: dict = Depends(current_claims)):
    patient = get_current_patient(claims)
    client = db()
    session = _session_or_404(client, session_id, patient_id=patient["id"])
    if session["status"] in {"awaiting_doctor_review", "approved", "rejected"}:
        return {"data": _response(client, session)}
    messages = _messages(client, session_id)
    if not any(row.get("role") == "patient" for row in messages):
        raise HTTPException(status_code=409, detail="Add a message before ending the consultation")
    client.table("ai_consultation_sessions").update({"status": "processing", "ended_at": datetime.now(timezone.utc).isoformat()}).eq("id", session_id).eq("patient_id", patient["id"]).execute()
    appointment = client.table("appointments").select("*").eq("id", session["appointment_id"]).single().execute().data
    try:
        summary = generate_summary(
            client, patient_id=patient["id"], hospital_id=session["hospital_id"],
            mode=session["mode"], appointment=appointment, messages=messages,
            similar_case_consent=bool(session.get("similar_case_consent_at")),
        )
    except Exception as exc:
        logger.warning("AI summary generation unavailable (%s)", type(exc).__name__)
        client.table("ai_consultation_sessions").update({"status": "failed"}).eq("id", session_id).execute()
        raise HTTPException(status_code=503, detail="Summary generation failed. Retry ending the consultation.")
    session = client.table("ai_consultation_sessions").update({"status": "awaiting_doctor_review", "summary": summary, "ended_at": datetime.now(timezone.utc).isoformat(), "updated_at": datetime.now(timezone.utc).isoformat()}).eq("id", session_id).eq("patient_id", patient["id"]).execute().data[0]
    return {"data": _response(client, session)}


@router.get("/doctor/appointments/{appointment_id}")
def doctor_review_detail(appointment_id: str, doctor: dict = Depends(get_current_doctor)):
    client = db()
    session = client.table("ai_consultation_sessions").select("*").eq("appointment_id", appointment_id).eq("doctor_id", doctor["id"]).maybe_single().execute().data
    appointment = client.table("appointments").select("*").eq("id", appointment_id).eq("doctor_id", doctor["id"]).maybe_single().execute().data
    if not appointment:
        raise HTTPException(status_code=404, detail="Appointment not found")
    if appointment.get("consultation_type") not in AI_TYPES:
        raise HTTPException(status_code=422, detail="This is not an AI consultation appointment")
    if not session:
        patient = client.table("profiles").select("id,first_name,last_name").eq("id", appointment["patient_id"]).maybe_single().execute().data
        return {"data": {"session": None, "messages": [], "appointment": appointment, "patient": patient}}
    patient = client.table("profiles").select("id,first_name,last_name").eq("id", session["patient_id"]).maybe_single().execute().data
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
