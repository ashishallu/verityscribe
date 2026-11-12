"""Server-side Google ADK Gemini Live session management for AI Virtual."""
from __future__ import annotations

import json
import logging
import os
import threading
from collections.abc import Awaitable, Callable
from typing import Any

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
                        "You are Verity, an AI medical consultation intake assistant, not a licensed clinician. "
                        "Conduct a natural, adaptive, structured patient interview, not a fixed questionnaire. Begin with a "
                        "brief greeting and one open question, then choose each next question from what the patient just said. "
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
                        "questions or repeat details already answered. Clarify ambiguous or incomplete answers. Keep each "
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
        # _messages() already returns the complete durable conversation (up to
        # its API-level 500-row cap). Do not silently summarize only the last
        # 100 turns: earlier symptom onset, surgery, medication, or allergy
        # answers are clinically important.
        safe_transcript = [
            {"role": row.get("role"), "content": str(row.get("content", ""))}
            for row in transcript if row.get("role") in {"patient", "assistant"}
        ]
        prompt = (
            "Create a concise, synthesized clinical intake note for the assigned doctor from the ENTIRE "
            "chronological conversation and verified record context. Do not produce a turn-by-turn retelling "
            "or copy conversational phrasing. Consolidate repeated reports of the same symptom into one clear "
            "clinical statement; preserve meaningful changes or contradictions. Patient messages are evidence "
            "of patient-reported facts. Assistant messages are questions/context only and are NEVER evidence "
            "that the patient has a symptom, condition, medication, allergy, or history. A fact from verified "
            "record context must be identified as record-derived, not patient-reported. "
            "The clinical_summary string must be a concise professional note with these exact labeled sections: "
            "Consultation Summary (2-3 coherent paragraphs); Clinical Assessment (Symptoms, Duration, Severity, "
            "Temperature/Vitals, Relevant Medical History, Current Medications, Allergies); Doctor's Advice & "
            "Treatment Plan; Follow-up; Final Impression; Missing Information / Points Requiring Doctor Confirmation; "
            "Consultation Status. Use concise prose and bullets under the assessment, never a transcript. "
            "Consolidate repetitions and remove filler/acknowledgements. Preserve clinically meaningful chronology. "
            "Identify onset/duration, severity/frequency, "
            "progression and associated symptoms only when stated. Attribute symptoms as patient-reported. "
            "Do not treat an unanswered question as a negative; say not established. Distinguish an explicit "
            "denial from unknown/unreported. Include a prior surgery only if the patient or verified record states it. "
            "Only describe doctor advice if an actual clinician gave it in source material; this is an AI intake, "
            "so label any assistant-provided general guidance as AI-generated and never as a prescription or "
            "doctor-approved plan. If no advice or follow-up was established, state that it was not discussed. "
            "Final Impression must say clinical assessment pending unless the conversation explicitly contains "
            "a clinician's assessment. Never create a diagnosis from symptoms. Do not expose ASR placeholders "
            "such as '[unclear speech fragment]' as findings; move them to Missing Information / Points Requiring "
            "Doctor Confirmation and describe only that wording was unclear. "
            "Every number must keep its meaning and unit/context: never write a bare number such as '7'; "
            "if the assistant asked for pain severity on a 1–10 scale and the patient answered 7, write "
            "'patient-rated severity 7/10'. Do not attach units or labels unless supported by the dialogue. "
            "Return one JSON object with keys: "
            "clinical_summary (string), chief_complaint (string or null), symptoms (array), "
            "onset_duration (array), severity_frequency (array), associated_symptoms (array), "
            "relevant_history (array), medications (array), allergies (array), additional_notes (array), "
            "positive_findings (array), negative_findings (array), risk_indicators (array), "
            "follow_up_recommendations (array), items_for_doctor_review (array), unanswered_questions (array), "
            "uncertainties (array), completion_status (complete or ended_early), "
            "draft_prescription (null), review_required (true), interview_complete (boolean). "
            "Do not infer missing facts. For Telugu, Hindi, code-switched, or other non-English transcript text, "
            "normalize/translate into clear medical English only when the meaning is unambiguous from context. "
            "Do not transliterate a phonetic fragment into a guessed English medical term. Put unclear wording "
            "in uncertainties and retain the original only in the raw transcript. Do not diagnose or prescribe. "
            "For an incomplete interview, "
            "say it ended early and that the transcript needs clinician review; do not imply the interview "
            "was completed. Keep details in the field that explains them (for example, '7/10' belongs in "
            "severity_frequency, not by itself in symptoms). Empty or unknown fields should be null or empty arrays.\n"
            f"interview_complete={str(interview_complete).lower()}\n"
            f"appointment={json.dumps({k: appointment.get(k) for k in ('id', 'consultation_type', 'reason_for_visit', 'appointment_date')}, default=str)}\n"
            f"verified_patient_context={json.dumps(patient_context, ensure_ascii=False, default=str)[:12000]}\n"
            f"transcript={json.dumps(safe_transcript, ensure_ascii=False)}"
        )
        types = self.types
        if types is None:
            from google.genai import types
        response = self.summary_client.models.generate_content(
            model=getattr(self, "summary_model", SUMMARY_MODEL),
            contents=prompt,
            config=types.GenerateContentConfig(
                response_mime_type="application/json",
            ),
        )
        if not response.text:
            raise RuntimeError("Gemini returned an empty consultation summary")
        parsed = json.loads(response.text)
        if not isinstance(parsed, dict) or not isinstance(parsed.get("clinical_summary"), str):
            raise ValueError("Gemini returned an invalid consultation summary")
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
                    prefix_padding_ms=200,
                    silence_duration_ms=max(500, min(self.silence_duration_ms, 2000)),
                )
            ),
        )


gemini_live = GeminiLiveService()
