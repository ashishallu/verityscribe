"""Server-side Google ADK Gemini Live session management for AI Virtual."""
from __future__ import annotations

import json
import logging
import os
import threading
import time
from collections.abc import Awaitable, Callable
from typing import Any

import httpx

from ..core.config import settings

logger = logging.getLogger(__name__)
APP_NAME = "verityscribe_ai_virtual"
ClinicalHandler = Callable[[str], Awaitable[str]]
CompletionHandler = Callable[[], Awaitable[None]]
# The post-call summarizer is a separate text-generation model; it is not the
# real-time voice model. Keep the default configurable for deployments.
SUMMARY_MODEL = "gemini-3.8-flash"


def _bind_tool_context_annotation(function: Any, tool_context_type: type) -> None:
    """Bind ADK's injected context type on nested tool callbacks.

    The callback functions are nested in ``initialize`` to keep ADK imports
    lazy. With ``from __future__ import annotations``, ADK 1.15's function
    schema builder later resolves annotations against ``function.__globals__``;
    a function-local ToolContext import is not present there and raises
    ``NameError`` on the first live invocation. Replacing just this annotation
    with the actual class keeps the lazy import and gives ADK a resolvable type.
    """
    annotations = dict(getattr(function, "__annotations__", {}))
    annotations["tool_context"] = tool_context_type
    function.__annotations__ = annotations


def _tool_session_id(tool_context: Any) -> str | None:
    """Read the server-seeded consultation ID from ADK's public tool state.

    ADK 1.15 ToolContext has ``state`` but no ``session`` attribute. Session
    identity is placed in state when the session is created, avoiding reliance
    on ADK's private invocation-context internals.
    """
    state = getattr(tool_context, "state", None)
    if state is None or not hasattr(state, "get"):
        return None
    value = state.get("ai_consultation_session_id")
    return value if isinstance(value, str) and value else None


def _session_state(session_id: str, context: dict[str, Any]) -> dict[str, str]:
    """Build state seeded by the backend for one authorized ADK session."""
    return {
        "clinical_context_json": json.dumps(context, ensure_ascii=False, default=str)[:18000],
        "ai_consultation_session_id": session_id,
    }


class GeminiLiveService:
    def __init__(self) -> None:
        cfg = settings()
        self.model = cfg.gemini_live_model
        self.voice = cfg.gemini_live_voice
        self.silence_duration_ms = cfg.gemini_live_silence_duration_ms
        self.summary_model = cfg.gemini_summary_model
        self.summary_fallback_model = cfg.gemini_summary_fallback_model
        self.summary_primary_timeout_seconds = cfg.gemini_summary_primary_timeout_seconds
        self.summary_fallback_timeout_seconds = cfg.gemini_summary_fallback_timeout_seconds
        self.google_api_key = cfg.google_api_key
        self.agent = None
        self.runner = None
        self.sessions = None
        self.types = None
        self.request_queue_type = None
        self.handlers: dict[str, ClinicalHandler] = {}
        self.completion_handlers: dict[str, CompletionHandler] = {}
        self.summary_client = None
        self.initialization_error: str | None = None
        self._init_lock = threading.Lock()

    def initialize_client(self) -> bool:
        """Initialize the shared GenAI client without loading ADK.

        Direct browser-to-Gemini Live sessions only need server-side token
        issuance and post-call text generation. Keeping those operations
        independent from ADK means the live audio path does not initialize or
        proxy through an ADK runner; ADK remains available for the fallback
        WebSocket implementation.
        """
        if self.summary_client is not None and self.types is not None:
            return True
        if not self.google_api_key:
            self.initialization_error = "missing_api_key"
            return False
        with self._init_lock:
            if self.summary_client is not None and self.types is not None:
                return True
            try:
                from google import genai
                from google.genai import types

                # ADK reads this server-side environment variable. Never return or log it.
                os.environ["GOOGLE_API_KEY"] = self.google_api_key
                self.types = types
                self.summary_client = genai.Client(
                    api_key=self.google_api_key,
                    http_options=types.HttpOptions(timeout=30_000),
                )
                self.initialization_error = None
                return True
            except Exception as exc:
                self.initialization_error = type(exc).__name__
                logger.exception(
                    "AI Virtual GenAI client initialization failed error_type=%s",
                    type(exc).__name__,
                )
                return False

    def initialize(self) -> bool:
        """Load ADK/GenAI lazily so unrelated API startup does not scan SDK entry points."""
        if self.configured:
            return True
        if not self.google_api_key:
            self.initialization_error = "missing_api_key"
            return False
        if not self.initialize_client():
            return False
        with self._init_lock:
            if self.configured:
                return True
            try:
                from google.adk.agents import Agent
                from google.adk.runners import Runner
                from google.adk.sessions import InMemorySessionService
                from google.adk.tools.tool_context import ToolContext
                from google.adk.agents.live_request_queue import LiveRequestQueue
                from google.genai import types

                self.sessions = InMemorySessionService()
                self.types = types
                self.request_queue_type = LiveRequestQueue

                def instruction(ctx: Any) -> str:
                    context = ctx.state.get("clinical_context_json", "{}")
                    return (
                        "You are Verity, a transparent AI health-intake assistant preparing a draft for a human clinician. "
                        "Conduct a natural, adaptive, structured patient interview, not a fixed questionnaire. Begin with a "
                        "brief greeting and one open question, then choose each next question from what the patient just said. "
                        "If asked whether you are human or a doctor, answer truthfully in one short sentence; never claim a real clinician identity. "
                        "introducing yourself as an AI intake assistant, and asking what brings them in. Identify "
                        "the primary concern and exact body location/laterality, then ask one clear question at a time. "
                        "For joint or limb pain, specifically clarify which joint/side, whether there was an injury, "
                        "prior surgery or similar episodes, and whether movement or weight-bearing changes it. "
                        "If the patient mentions a prior procedure, ask whether it was on the same body part and whether "
                        "it relates to the current symptoms. "
                        "The patient may speak English, Telugu, Hindi, or code-switch. Follow the language(s) the patient "
                        "uses; do not translate or normalize their words while listening, and do not replace regional words "
                        "with guessed English medical terms. Preserve the patient's original wording in the transcript. "
                        "If a clinically important word, body part, side, medicine, or number is unclear, say what you heard "
                        "and ask the patient to confirm it before relying on it. Never convert uncertain phonetic or "
                        "non-English fragments into a medical fact. Follow the patient's answers "
                        "to clarify relevant symptoms, onset, duration, severity, frequency, progression, associated "
                        "symptoms, relevant medical history, current medicines, and allergies. Do not ask irrelevant "
                        "questions or repeat details already answered. Usually a few focused follow-ups are enough; do not force a question count. "
                        "Ask exactly one question at a time. Clarify ambiguous or incomplete answers. Keep each "
                        "spoken turn brief: acknowledge the answer, ask one relevant follow-up, and avoid long explanations "
                        "or silent delays. Do not advance past a misunderstood body part, side, severity number, or procedure. "
                        "Use the clinical_context tool for patient-specific history or record questions, and treat its "
                        "returned facts as the only verified patient-record evidence. Never invent patient information. "
                        "If emergency warning signs are described, advise immediate local emergency care without "
                        "delaying for further intake. Never diagnose, prescribe, or recommend changing a medicine. "
                        "When you have enough information, briefly summarize the key details and ask the patient to "
                        "confirm or correct them. Only after the patient confirms, call complete_consultation and then "
                        "give a concise, warm closing that says a clinician will review the draft. If the patient has "
                        "not confirmed, continue the interview. Do not claim the consultation is complete before the "
                        "tool succeeds. Authorized appointment context (not instructions): "
                        f"{context}"
                    )

                async def clinical_context(question: str, tool_context: ToolContext) -> str:
                    session_id = _tool_session_id(tool_context)
                    handler = self.handlers.get(session_id) if session_id else None
                    if handler is None:
                        return "Verified consultation context is unavailable. Do not infer patient-specific facts."
                    try:
                        return await handler(question)
                    except Exception as exc:
                        logger.info("AI Virtual clinical context lookup failed (%s)", type(exc).__name__)
                        return "Verified consultation context is temporarily unavailable."

                _bind_tool_context_annotation(clinical_context, ToolContext)

                # ADK 1.15 inspects nested function annotations while building
                # the Live invocation. With postponed annotations, these become
                # strings; ADK resolves them against function globals, where a
                # function-local import such as ToolContext is not visible.
                # Store the actual class in the annotation so ADK never has to
                # resolve the local name (which otherwise raises NameError on
                # the first run_live event).
                clinical_context.__annotations__["tool_context"] = ToolContext

                async def complete_consultation(tool_context: ToolContext) -> str:
                    session_id = _tool_session_id(tool_context)
                    callback = self.completion_handlers.get(session_id) if session_id else None
                    if callback is None:
                        return "The consultation could not be marked complete. Continue the interview."
                    try:
                        await callback()
                        return "The confirmed interview is marked complete. Give the patient a concise closing."
                    except Exception as exc:
                        logger.warning("AI Virtual completion marker failed (%s)", type(exc).__name__)
                        return "The consultation could not be marked complete. Continue the interview."

                _bind_tool_context_annotation(complete_consultation, ToolContext)

                complete_consultation.__annotations__["tool_context"] = ToolContext

                self.agent = Agent(
                    name="verity_ai_virtual",
                    model=self.model,
                    instruction=instruction,
                    tools=[clinical_context, complete_consultation],
                )
                self.runner = Runner(agent=self.agent, app_name=APP_NAME, session_service=self.sessions)
                self.initialization_error = None
                return True
            except Exception as exc:
                self.agent = self.runner = self.sessions = None
                self.request_queue_type = None
                self.initialization_error = type(exc).__name__
                logger.exception(
                    "AI Virtual Gemini initialization failed error_type=%s",
                    type(exc).__name__,
                )
                return False

    @property
    def configured(self) -> bool:
        return self.runner is not None and self.sessions is not None

    async def create_session(self, *, user_id: str, session_id: str,
                             context: dict[str, Any], handler: ClinicalHandler,
                             completion_handler: CompletionHandler | None = None) -> None:
        if not self.configured:
            raise RuntimeError("Gemini Live is not configured")
        self.handlers[session_id] = handler
        if completion_handler is not None:
            self.completion_handlers[session_id] = completion_handler
        await self.sessions.create_session(
            app_name=APP_NAME, user_id=user_id, session_id=session_id,
            state=_session_state(session_id, context),
        )

    async def delete_session(self, *, user_id: str, session_id: str) -> None:
        self.handlers.pop(session_id, None)
        self.completion_handlers.pop(session_id, None)
        if self.sessions:
            try:
                await self.sessions.delete_session(app_name=APP_NAME, user_id=user_id, session_id=session_id)
            except Exception as exc:
                logger.info("AI Virtual session cleanup failed (%s)", type(exc).__name__)

    def generate_consultation_summary(
        self, *, transcript: list[dict[str, Any]], patient_context: dict[str, Any],
        appointment: dict[str, Any], interview_complete: bool,
    ) -> dict[str, Any]:
        """Generate a doctor-review draft using Gemini, never the AI Chat provider."""
        if self.summary_client is None and not self.initialize_client():
            raise RuntimeError("Gemini summary service is not configured")
        if self.summary_client is None:
            raise RuntimeError("Gemini summary service is not configured")
        # Keep the complete raw transcript in Supabase; send a compact, clearly
        # speaker-labeled view to the summarizer. Assistant speech is context,
        # never evidence. Limit unrelated record fields so the summary request
        # does not become another copy of the patient's whole chart.
        safe_transcript = [
            {"speaker": "Patient", "text": str(row.get("content", "")).strip()}
            if row.get("role") == "patient" else
            {"speaker": "Interviewer (question/context only)", "text": str(row.get("content", "")).strip()}
            for row in transcript
            if row.get("role") in {"patient", "assistant"} and str(row.get("content", "")).strip()
        ]
        context_fields = (
            "allergies", "chronic_conditions", "surgeries", "vitals",
            "current_prescriptions", "patient_profile", "reports", "consultations",
        )
        compact_context = {
            key: patient_context[key]
            for key in context_fields
            if isinstance(patient_context, dict) and key in patient_context
        }
        transcript_json = json.dumps(safe_transcript, ensure_ascii=False, separators=(",", ":"))
        context_json = json.dumps(compact_context, ensure_ascii=False, default=str, separators=(",", ":"))[:5000]
        summary_model = getattr(self, "summary_model", SUMMARY_MODEL)
        logger.info(
            "AI Virtual text summary request model=%s conversation_turns=%d context_fields=%d",
            summary_model, len(safe_transcript), len(compact_context),
        )
        prompt = (
            "Create a concise, organized clinical intake summary for a physician from the chronological dialogue "
            "and the small verified-record context. This is a summary, NOT a transcript: remove greetings, repeated "
            "questions, acknowledgements, filler, and duplicate details. Patient turns are the only conversation "
            "evidence. Interviewer turns are question/context only, never patient facts. Clearly mark record-derived "
            "facts as from the chart. Do not infer a diagnosis or recommend treatment. "
            "Set clinical_summary to a brief professional summary with these headings and content: "
            "Problem; Symptoms and course (location, onset/duration, severity/frequency, associated symptoms only "
            "when stated); Medications; Relevant history and allergies; Clarifications needed; Status. "
            "Use compact bullets and plain language, ideally under 180 words. Put each number with its supported "
            "meaning and unit/context (for example, a 7 given in answer to a 1–10 pain question is 7/10). "
            "Never fill gaps: distinguish an explicit patient denial from unknown, unreported, or unanswered. "
            "Preserve contradictions as needing clarification. Do not use assistant questions as evidence. Do not "
            "translate or guess unclear Telugu, Hindi, code-switched, or other-language words; retain meaning only "
            "when unambiguous, otherwise say the transcript needs confirmation. The complete original transcript "
            "is stored separately and must not be rewritten. No diagnosis, prescription, dosage, examination finding, "
            "or clinician advice may be invented. Physician review is mandatory. "
            "Return one JSON object with keys: "
            "clinical_summary (string), chief_complaint (string or null), symptoms (array), "
            "onset_duration (array), severity_frequency (array), associated_symptoms (array), "
            "relevant_history (array), medications (array), allergies (array), additional_notes (array), "
            "positive_findings (array), negative_findings (array), risk_indicators (array), "
            "follow_up_recommendations (array), items_for_doctor_review (array), unanswered_questions (array), "
            "uncertainties (array), completion_status (complete or ended_early), "
            "draft_prescription (null), review_required (true), interview_complete (boolean). "
            "Do not infer missing facts. For incomplete interviews say ended early and require clinician review. "
            "Empty or unknown fields should be null or empty arrays.\n"
            f"interview_complete={str(interview_complete).lower()}\n"
            f"appointment={json.dumps({k: appointment.get(k) for k in ('id', 'consultation_type', 'reason_for_visit', 'appointment_date')}, default=str)}\n"
            f"verified_patient_context={context_json}\n"
            f"conversation={transcript_json}"
        )
        # Gemini is the preferred text summarizer. If its endpoint times out
        # or returns unusable output, the existing Cloudflare Workers AI text
        # service is a secondary summary provider; Live voice remains Gemini.
        response_data = None
        gemini_error: Exception | None = None
        for attempt in range(2):
            started = time.perf_counter()
            try:
                # google-genai 1.36 (the project's pinned SDK) does not expose
                # Gemini 3.8's thinking_level field. Send the documented
                # GenerateContent REST shape so Flash can use low reasoning;
                # auth stays in a header and is never part of the URL/logs.
                endpoint_model = summary_model.replace("/", "%2F")
                response = httpx.post(
                    f"https://generativelanguage.googleapis.com/v1beta/models/{endpoint_model}:generateContent",
                    headers={"x-goog-api-key": self.google_api_key},
                    json={
                        "contents": [{"role": "user", "parts": [{"text": prompt}]}],
                        "generationConfig": {
                            "responseMimeType": "application/json",
                            # The summary schema is deliberately compact. A
                            # smaller generation budget reduces reserved
                            # capacity and first-token latency under load.
                            "maxOutputTokens": 800,
                            "thinkingConfig": {"thinkingLevel": "LOW"},
                        },
                    },
                    timeout=httpx.Timeout(
                        max(5.0, min(30.0, float(getattr(self, "summary_primary_timeout_seconds", 15.0)))),
                        connect=10.0,
                    ),
                )
                response.raise_for_status()
                response_data = response.json()
                logger.info(
                    "AI Virtual text summary response model=%s status=%d elapsed_ms=%d",
                    summary_model, response.status_code,
                    round((time.perf_counter() - started) * 1000),
                )
                break
            except Exception as exc:
                status_code = (
                    getattr(getattr(exc, "response", None), "status_code", None)
                    or getattr(exc, "code", None)
                    or getattr(exc, "status_code", None)
                )
                provider_status = None
                retry_after = None
                response = getattr(exc, "response", None)
                if response is not None:
                    retry_after = response.headers.get("Retry-After")
                    try:
                        provider_error = response.json().get("error", {})
                        provider_status = provider_error.get("status")
                        if not retry_after:
                            for detail in provider_error.get("details", []):
                                if str(detail.get("@type", "")).endswith("RetryInfo"):
                                    retry_after = detail.get("retryDelay")
                                    break
                    except (AttributeError, ValueError):
                        pass
                logger.warning(
                    "AI Virtual clinical summary request failed model=%s status=%s provider_status=%s attempt=%d elapsed_ms=%d error_type=%s",
                    summary_model, status_code, provider_status, attempt + 1,
                    round((time.perf_counter() - started) * 1000), type(exc).__name__,
                )
                retryable = (
                    status_code in {429, 500, 502, 503, 504}
                    or isinstance(exc, (httpx.TimeoutException, httpx.TransportError))
                )
                gemini_error = exc
                if attempt == 0 and retryable:
                    # A non-streaming GenerateContent response can take a while
                    # to produce its first byte during provider load. Retrying
                    # immediately after a client read timeout caused a second
                    # request to hit 429. Honor Google's Retry-After when sent;
                    # otherwise use a short bounded backoff.
                    try:
                        provider_delay = (
                            float(str(retry_after).removesuffix("s"))
                            if retry_after else None
                        )
                    except (TypeError, ValueError):
                        provider_delay = None
                    # A long RetryInfo (for example a daily quota reset) is
                    # not transient at request timescale. Do not hammer the
                    # exhausted Gemini quota; proceed to the configured text
                    # fallback instead.
                    if provider_delay is not None and provider_delay > 30.0:
                        logger.warning(
                            "AI Virtual Gemini retry skipped model=%s provider_status=%s retry_after_s=%.0f reason=retry_window_exceeded",
                            summary_model, provider_status, provider_delay,
                        )
                        # A project-wide quota cannot be relieved by retrying
                        # the same model. Continue to the distinct fallback.
                        break
                    fallback_model = getattr(
                        self, "summary_fallback_model", "gemini-3.5-flash-lite"
                    )
                    if fallback_model and fallback_model != summary_model:
                        logger.info(
                            "AI Virtual Gemini summary switching to Gemini fallback model=%s after primary_status=%s",
                            fallback_model, provider_status or status_code or "transport_error",
                        )
                        break
                    retry_delay = max(0.0, provider_delay) if provider_delay is not None else 5.0
                    logger.info(
                        "AI Virtual clinical summary retry scheduled model=%s attempt=%d delay_s=%.1f",
                        summary_model, attempt + 2, retry_delay,
                    )
                    if retry_delay:
                        time.sleep(retry_delay)
                    continue
                break

        def parse_summary(response_text: str) -> dict[str, Any]:
            text = response_text.strip()
            # Tolerate a short preamble or fenced JSON from a compatible
            # fallback model without ever accepting a non-object response.
            start = text.find("{")
            if start < 0:
                raise ValueError("Summary response contains no JSON object")
            parsed_value, _ = json.JSONDecoder().raw_decode(text[start:])
            if not isinstance(parsed_value, dict) or not isinstance(
                parsed_value.get("clinical_summary"), str
            ) or not parsed_value["clinical_summary"].strip():
                raise ValueError("Summary response has no clinical summary")
            # A syntactically valid outer JSON response may still wrap a
            # second JSON object/fence in clinical_summary. Treat that as an
            # unusable model result and let the configured summary provider
            # recover it instead of displaying raw JSON after the voice call.
            if parsed_value["clinical_summary"].lstrip().startswith(("{", "```")):
                raise ValueError("Summary response contains non-readable nested JSON")
            return parsed_value

        parsed: dict[str, Any] | None = None
        summary_provider = "gemini"
        if response_data is not None:
            try:
                candidates = response_data.get("candidates") or []
                parts = (candidates[0].get("content") or {}).get("parts") if candidates else None
                response_text = "".join(
                    str(part.get("text", ""))
                    for part in (parts or [])
                    if isinstance(part, dict) and not part.get("thought")
                ).strip()
                parsed = parse_summary(response_text)
            except Exception as exc:
                gemini_error = exc

        fallback_model = getattr(
            self, "summary_fallback_model", "gemini-3.5-flash-lite"
        )
        summary_error_status = (
            getattr(getattr(gemini_error, "response", None), "status_code", None)
            or getattr(gemini_error, "code", None)
            or getattr(gemini_error, "status_code", None)
        )
        summary_error_retryable = (
            summary_error_status in {429, 500, 502, 503, 504}
            or isinstance(gemini_error, (httpx.TimeoutException, httpx.TransportError))
        )
        if (
            parsed is None
            and summary_error_retryable
            and fallback_model
            and fallback_model != summary_model
        ):
            fallback_started = time.perf_counter()
            try:
                fallback_response = httpx.post(
                    "https://generativelanguage.googleapis.com/v1beta/models/"
                    f"{fallback_model.replace('/', '%2F')}:generateContent",
                    headers={"x-goog-api-key": self.google_api_key},
                    json={
                        "contents": [{"role": "user", "parts": [{"text": prompt}]}],
                        "generationConfig": {
                            "responseMimeType": "application/json",
                            "maxOutputTokens": 512,
                            "thinkingConfig": {"thinkingLevel": "MINIMAL"},
                        },
                    },
                    timeout=httpx.Timeout(
                        max(5.0, min(30.0, float(getattr(self, "summary_fallback_timeout_seconds", 20.0)))),
                        connect=10.0,
                    ),
                )
                fallback_response.raise_for_status()
                fallback_data = fallback_response.json()
                fallback_candidates = fallback_data.get("candidates") or []
                fallback_parts = (
                    (fallback_candidates[0].get("content") or {}).get("parts")
                    if fallback_candidates else None
                )
                fallback_text = "".join(
                    str(part.get("text", ""))
                    for part in (fallback_parts or [])
                    if isinstance(part, dict) and not part.get("thought")
                ).strip()
                parsed = parse_summary(fallback_text)
                summary_provider = "gemini_fallback"
                logger.info(
                    "AI Virtual text summary response provider=gemini_fallback model=%s status=%d elapsed_ms=%d",
                    fallback_model, fallback_response.status_code,
                    round((time.perf_counter() - fallback_started) * 1000),
                )
            except Exception as fallback_error:
                fallback_status = (
                    getattr(getattr(fallback_error, "response", None), "status_code", None)
                    or getattr(fallback_error, "code", None)
                    or getattr(fallback_error, "status_code", None)
                )
                logger.warning(
                    "AI Virtual Gemini summary fallback failed model=%s status=%s error_type=%s",
                    fallback_model, fallback_status or "unknown", type(fallback_error).__name__,
                )

        if parsed is None:
            logger.warning(
                "AI Virtual summary switching provider primary=gemini secondary=cloudflare error_type=%s",
                type(gemini_error).__name__ if gemini_error else "empty_response",
            )
            try:
                from .ai_service import ai_provider

                response_text = ai_provider.summarize_consultation(prompt)
                parsed = parse_summary(response_text)
                summary_provider = "cloudflare"
                logger.info(
                    "AI Virtual text summary response provider=cloudflare model=%s",
                    os.getenv("CLOUDFLARE_SUMMARY_MODEL", ai_provider.SUMMARY_MODEL),
                )
            except Exception as fallback_error:
                logger.warning(
                    "AI Virtual secondary summary provider failed provider=cloudflare error_type=%s",
                    type(fallback_error).__name__,
                )
                raise RuntimeError(
                    "Gemini and Cloudflare could not generate a structured consultation summary"
                ) from fallback_error

        parsed["summary_generation_provider"] = summary_provider
        parsed["draft_prescription"] = None
        parsed["review_required"] = True
        parsed["interview_complete"] = interview_complete
        for field in (
            "symptoms", "onset_duration", "severity_frequency", "associated_symptoms",
            "relevant_history", "medications", "allergies", "additional_notes",
            "positive_findings", "negative_findings", "risk_indicators",
            "follow_up_recommendations", "items_for_doctor_review",
            "unanswered_questions", "uncertainties",
        ):
            value = parsed.get(field)
            if value is None:
                parsed[field] = []
            elif not isinstance(value, list):
                parsed[field] = [str(value)]
        parsed["completion_status"] = "complete" if interview_complete else "ended_early"
        return parsed

    def run_config(self):
        from google.adk.agents.run_config import RunConfig, StreamingMode
        from google.genai import types
        return RunConfig(
            streaming_mode=StreamingMode.BIDI,
            response_modalities=[types.Modality.AUDIO],
            speech_config=types.SpeechConfig(voice_config=types.VoiceConfig(
                prebuilt_voice_config=types.PrebuiltVoiceConfig(voice_name=self.voice))),
            # Empty/default language hints intentionally leave Gemini's native
            # transcription language detection enabled for multilingual and
            # code-switched speech. google-genai 1.36 exposes no language hint
            # fields for this Live-agent transcription config.
            input_audio_transcription=types.AudioTranscriptionConfig(),
            output_audio_transcription=types.AudioTranscriptionConfig(),
            realtime_input_config=types.RealtimeInputConfig(
                automatic_activity_detection=types.AutomaticActivityDetection(
                    disabled=False,
                    start_of_speech_sensitivity=types.StartSensitivity.START_SENSITIVITY_LOW,
                    end_of_speech_sensitivity=types.EndSensitivity.END_SENSITIVITY_HIGH,
                    prefix_padding_ms=40,
                    silence_duration_ms=500,
                )
            ),
        )


gemini_live = GeminiLiveService()
