package com.example.data

import com.example.model.AppointmentStatus
import com.example.model.ClinicalAppointment
import com.example.model.ConsultationType
import com.example.model.DoctorHistoryRecord
import com.example.model.DoctorNotification
import com.example.model.DoctorProfile
import com.example.model.MedicalReport
import com.example.model.SimilarCase
import com.example.model.StructuredAiSummary
import com.example.model.TranscriptSender
import com.example.model.TranscriptTurn
import com.example.model.VitalsSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DoctorWorkspaceData(
    val doctorId: String,
    val profile: DoctorProfile,
    val appointments: List<ClinicalAppointment>,
    val notifications: List<DoctorNotification>,
    val history: List<DoctorHistoryRecord>
)

data class VerifiedDoctorIdentity(
    val doctor: JSONObject,
    val account: JSONObject
) {
    val doctorId: String = doctor.stringOrNull("id")
        ?: throw IllegalStateException("The signed-in account has no doctor profile.")
}

data class ConsultationSummaryRetryResult(
    val summary: StructuredAiSummary,
    val appointmentStatus: AppointmentStatus
)

/** Maps only server-returned data into the supplied Compose UI models. */
class DoctorRepository(private val api: DoctorBackendClient = DoctorBackendClient()) {
    fun signIn(email: String, password: String): DoctorAuthTokens = api.signIn(email, password)
    fun refreshSession(refreshToken: String): DoctorAuthTokens = api.refreshSession(refreshToken)
    fun signOut(accessToken: String) = api.signOut(accessToken)

    /** Verify doctor authorization in one API round-trip via the backend's authoritative route. */
    suspend fun verifyDoctor(token: String): VerifiedDoctorIdentity = withContext(Dispatchers.IO) {
        val doctor = api.get("me/doctor", token).dataObject()
        VerifiedDoctorIdentity(doctor, JSONObject())
    }

    fun profileFor(identity: VerifiedDoctorIdentity): DoctorProfile =
        mapDoctorProfile(identity.doctor, identity.account, null)

    suspend fun loadWorkspace(
        token: String,
        verifiedIdentity: VerifiedDoctorIdentity? = null,
        onInitialWorkspace: ((DoctorWorkspaceData) -> Unit)? = null
    ): DoctorWorkspaceData = withContext(Dispatchers.IO) {
        val identity = verifiedIdentity ?: verifyDoctor(token)
        coroutineScope {
            val appointmentRows = async(Dispatchers.IO) { api.get("me/appointments", token).dataArray().objects() }
            val consultationRows = async(Dispatchers.IO) { runCatching { api.get("me/consultations", token).dataArray().objects() }.getOrDefault(emptyList()) }
            val reportRows = async(Dispatchers.IO) { runCatching { api.get("me/reports", token).dataArray().objects() }.getOrDefault(emptyList()) }
            val notificationRows = async(Dispatchers.IO) { runCatching { api.get("notifications?page=1&page_size=50", token).dataArray().objects() }.getOrDefault(emptyList()) }

            // Paint the schedule as soon as its endpoint responds. Patient demographics,
            // AI reviews, reports, and notification details enrich it below without holding
            // the first useful screen behind every secondary network request.
            val today = LocalDate.now()
            val rawAppointments = appointmentRows.await().filter { appointment ->
                val scheduledDate = (appointment.stringOrNull("appointment_date")
                    ?: appointment.stringOrNull("scheduled_at"))?.take(10)
                val parsedDate = runCatching { scheduledDate?.let(LocalDate::parse) }.getOrNull()
                parsedDate != null && !parsedDate.isBefore(today)
            }
            val profile = mapDoctorProfile(identity.doctor, identity.account, rawAppointments.firstOrNull())
            val initialAppointments = rawAppointments.map { appointment ->
                mapAppointment(appointment, JSONObject(), null, null, emptyList())
            }
            onInitialWorkspace?.invoke(
                DoctorWorkspaceData(
                    identity.doctorId,
                    profile,
                    initialAppointments,
                    emptyList(),
                    buildDoctorHistory(initialAppointments)
                )
            )

            val consultations = consultationRows.await()
            val reports = reportRows.await()
            val rawNotifications = notificationRows.await()

            val appointments = rawAppointments.map { appointment ->
                val patient = appointment.objectOrEmpty("patient")
                val patientId = appointment.stringOrNull("patient_id").orEmpty()
                val consultation = consultations.firstOrNull {
                    it.stringOrNull("appointment_id") == appointment.stringOrNull("id")
                }
                val aiSession = appointment.optJSONObject("ai_session")
                val aiReview = aiSession?.let { JSONObject().put("session", it).put("messages", JSONArray()) }
                val patientReports = reports.filter { it.stringOrNull("patient_id") == patientId }
                mapAppointment(appointment, patient, consultation, aiReview, patientReports)
            }
            val notifications = rawNotifications.map(::mapNotification)
            val history = buildDoctorHistory(appointments)
            DoctorWorkspaceData(identity.doctorId, profile, appointments, notifications, history)
        }
    }

    fun assistantAnswer(
        token: String,
        appointmentId: String,
        message: String,
        history: List<Pair<String, String>>
    ): String {
        val body = JSONObject()
            .put("message", message)
            .put("history", JSONArray().apply {
                history.takeLast(12).forEach { (role, content) ->
                    put(JSONObject().put("role", role).put("content", content.take(1200)))
                }
            })
        return api.post("doctor/appointments/$appointmentId/assistant", token, body)
            .dataObject().stringOrNull("answer")
            ?: throw IllegalStateException("The clinical assistant returned no answer.")
    }

    fun reviewDecision(token: String, sessionId: String, decision: String, summary: JSONObject?, note: String?) {
        val body = JSONObject().put("decision", decision)
        if (summary != null) body.put("edited_summary", summary)
        if (!note.isNullOrBlank()) body.put("note", note)
        api.post("ai-consultations/doctor/sessions/$sessionId/review", token, body)
    }

    suspend fun retrySessionSummary(
        token: String,
        sessionId: String,
        appointmentId: String
    ): ConsultationSummaryRetryResult = withContext(Dispatchers.IO) {
        val detail = api.post("ai-consultations/doctor/sessions/$sessionId/retry-summary", token, JSONObject()).dataObject()
        val session = detail.objectOrEmpty("session")
        if (session.stringOrNull("id") != sessionId) {
            throw IllegalStateException("The refreshed consultation did not match the retry request.")
        }
        val appointment = detail.objectOrEmpty("appointment")
        ConsultationSummaryRetryResult(
            summary = mapSummary(session.objectOrEmpty("summary"), session),
            appointmentStatus = mapStatus(
                session.stringOrNull("status").orEmpty(),
                appointment.stringOrNull("appointment_date")
            )
        )
    }

    suspend fun loadAppointmentDetail(
        token: String,
        appointmentId: String,
        existing: ClinicalAppointment
    ): ClinicalAppointment = withContext(Dispatchers.IO) {
        val detail = api.get("ai-consultations/doctor/appointments/$appointmentId", token).dataObject()
        val session = detail.objectOrEmpty("session")
        val messages = detail.arrayOrEmpty("messages").objects()
        val rawStatus = session.stringOrNull("status") ?: existing.rawBackendStatus.orEmpty()
        existing.copy(
            aiSummary = mapSummary(session.objectOrEmpty("summary"), session),
            transcript = messages.mapNotNull(::mapTranscriptTurn),
            status = mapStatus(rawStatus, existing.appointmentDate),
            rawBackendStatus = rawStatus,
            aiSessionId = session.stringOrNull("id") ?: existing.aiSessionId
        )
    }

    fun markNotificationRead(token: String, notificationId: String) {
        api.patch("notifications/$notificationId/read", token, JSONObject())
    }

    fun markAllNotificationsRead(token: String) {
        api.post("notifications/read-all", token, JSONObject())
    }

    fun updateAvailability(token: String, doctorId: String, available: Boolean) {
        api.patch("doctors/$doctorId", token, JSONObject().put("is_available", available))
    }

    fun doctorAssistantPayload(summary: StructuredAiSummary): JSONObject = JSONObject()
        .put("clinical_summary", listOf(
            "Problem: ${summary.chiefProblem}",
            "Symptoms and course: ${summary.symptomCourse}",
            "Medications: ${summary.currentMedications.joinToString("; ")}",
            "Relevant history and allergies: ${summary.medicalHistoryAndAllergies}",
            "Clarifications/unknowns: ${summary.clarificationsOrUnknowns.joinToString("; ")}"
        ).filterNot { it.endsWith(": ") }.joinToString("\n"))
        .put("problem", summary.chiefProblem)
        .put("symptom_course", summary.symptomCourse)
        .put("current_medications", JSONArray(summary.currentMedications))
        .put("medical_history_and_allergies", summary.medicalHistoryAndAllergies)
        .put("clarifications_or_unknowns", JSONArray(summary.clarificationsOrUnknowns))
        .put("suggested_medications", JSONArray().apply {
            summary.suggestedMedications.forEach { medication ->
                put(JSONObject()
                    .put("name", medication.medicationName)
                    .put("dosage", medication.dosage)
                    .put("frequency", medication.frequency)
                    .put("duration", medication.duration)
                    .put("instructions", medication.instructions))
            }
        })
        .put("clinical_advice", JSONArray(summary.clinicalAdvice))

    fun addConsultation(
        token: String,
        appointment: ClinicalAppointment,
        clinicalNote: String,
        primaryDiagnosis: String?
    ): String {
        val payload = JSONObject()
            .put("appointment_id", appointment.id)
            .put("consultation_type", consultationTypeValue(appointment.consultationType))
            .put("symptoms", appointment.chiefComplaint.takeIf(String::isNotBlank) ?: JSONObject.NULL)
            .put("diagnosis", primaryDiagnosis?.takeIf(String::isNotBlank) ?: JSONObject.NULL)
            .put("treatment_plan", clinicalNote.takeIf(String::isNotBlank) ?: JSONObject.NULL)
        return api.post("consultations", token, payload).dataObject().stringOrNull("id")
            ?: throw IllegalStateException("The consultation was not created by the backend.")
    }

    fun completeAppointment(
        token: String,
        appointmentId: String,
        clinicalNote: String,
        diagnoses: List<String>,
        prescriptions: List<com.example.model.PrescriptionItem>
    ) {
        val medicationRows = JSONArray().apply {
            prescriptions.forEach { item ->
                put(JSONObject()
                    .put("name", item.medicationName)
                    .put("dosage", item.dosage)
                    .put("frequency", item.frequency)
                    .put("duration", item.duration)
                    .put("instructions", item.instructions))
            }
        }
        val payload = JSONObject()
            .put("clinical_note", clinicalNote)
            .put("diagnoses", JSONArray(diagnoses.filter(String::isNotBlank)))
            .put("prescriptions", medicationRows)
            .put("mark_completed", true)
        api.post("doctor/appointments/$appointmentId/complete", token, payload)
    }

    fun addNote(token: String, consultationId: String, note: String) {
        api.post("consultations/$consultationId/notes", token, JSONObject().put("note_text", note))
    }

    fun addDiagnosis(token: String, consultationId: String, diagnosis: String) {
        api.post("consultations/$consultationId/diagnoses", token, JSONObject().put("diagnosis_name", diagnosis))
    }

    private fun mapDoctorProfile(doctor: JSONObject, account: JSONObject, appointment: JSONObject?): DoctorProfile {
        val profile = account
        val appointmentDoctor = appointment?.objectOrEmpty("doctor") ?: JSONObject()
        val linkedProfile = appointmentDoctor.objectOrEmpty("profiles")
        val department = appointmentDoctor.objectOrEmpty("departments")
        val hospital = appointmentDoctor.objectOrEmpty("hospitals")
        val first = profile.stringOrNull("first_name") ?: linkedProfile.stringOrNull("first_name")
        val last = profile.stringOrNull("last_name") ?: linkedProfile.stringOrNull("last_name")
        val fullName = listOfNotNull(first, last).joinToString(" ").ifBlank { "Clinician" }
        return DoctorProfile(
            fullName = if (fullName.startsWith("Dr.", ignoreCase = true)) fullName else "Dr. $fullName",
            greetingName = if (last.isNullOrBlank()) fullName else "Dr. $last",
            specialtyTitle = doctor.stringOrNull("specialty")
                ?: doctor.stringOrNull("specialization")
                ?: department.stringOrNull("name")
                ?: "Clinician",
            department = department.stringOrNull("name") ?: "Department not recorded",
            hospital = hospital.stringOrNull("name") ?: "Hospital not recorded",
            licenseNumber = doctor.stringOrNull("license_number") ?: "Not recorded",
            experienceYears = doctor.optIntOrNull("experience_years"),
            languages = doctor.stringOrNull("languages") ?: "Not recorded",
            workingHours = doctor.stringOrNull("working_hours") ?: "Not recorded",
            isAvailable = doctor.optBoolean("is_available", false)
        )
    }

    private suspend fun mapAppointment(
        raw: JSONObject,
        patientRecord: JSONObject,
        consultation: JSONObject?,
        aiReview: JSONObject?,
        reports: List<JSONObject>
    ): ClinicalAppointment {
        val patient = raw.objectOrEmpty("patient")
        val patientName = patient.stringOrNull("full_name")
            ?: listOfNotNull(patient.stringOrNull("first_name"), patient.stringOrNull("last_name"))
                .joinToString(" ").ifBlank { "Patient name not recorded" }
        val appointmentId = raw.stringOrNull("id").orEmpty()
        val session = aiReview?.objectOrEmpty("session") ?: JSONObject()
        val summaryJson = session.optJSONObject("summary") ?: JSONObject()
        val messageRows = aiReview?.optJSONArray("messages")?.objects().orEmpty()
        val appointmentDate = raw.stringOrNull("appointment_date") ?: raw.stringOrNull("scheduled_at")?.take(10)
        val appointmentTime = raw.stringOrNull("appointment_time")
            ?: raw.stringOrNull("scheduled_at")?.let(::formatTime)
            ?: "Time not recorded"
        val aiSummary = mapSummary(summaryJson, session)
        val appointmentStatus = raw.stringOrNull("status").orEmpty()
        val rawStatus = if (appointmentStatus.equals("completed", ignoreCase = true)) {
            appointmentStatus
        } else {
            session.stringOrNull("status") ?: appointmentStatus
        }
        val status = mapStatus(rawStatus, appointmentDate)
        val noteRows = consultation?.optJSONArray("notes")?.objects().orEmpty()
        val noteText = noteRows.mapNotNull { it.stringOrNull("note_text") }.joinToString("\n\n")
            .ifBlank { consultation?.stringOrNull("treatment_plan") ?: "" }
        val diagnoses = consultation?.optJSONArray("diagnoses")?.objects()?.mapNotNull {
            it.stringOrNull("diagnosis_name")
        }.orEmpty().ifEmpty {
            listOfNotNull(consultation?.stringOrNull("diagnosis"))
        }
        val patientAge = patient.optIntOrNull("age")
            ?: patient.stringOrNull("date_of_birth")?.let(::ageFromBirthDate)
            ?: patientRecord.stringOrNull("date_of_birth")?.let(::ageFromBirthDate)
            ?: 0
        val medicalId = patientRecord.stringOrNull("medical_id") ?: patient.stringOrNull("id") ?: "Not recorded"
        val reportsMapped = reports.map(::mapReport)
        return ClinicalAppointment(
            id = appointmentId,
            patientName = patientName,
            age = patientAge,
            gender = patient.stringOrNull("gender") ?: patientRecord.stringOrNull("gender") ?: "Not recorded",
            mrn = medicalId,
            bloodGroup = patientRecord.stringOrNull("blood_group") ?: "Not recorded",
            allergies = listOf("Not documented"),
            vitals = VitalsSnapshot("Not recorded", "Not recorded", "Not recorded", "Not recorded"),
            scheduledTime = appointmentTime,
            isMorningShift = appointmentTime.take(2).toIntOrNull()?.let { it < 13 } ?: true,
            consultationType = mapConsultationType(raw.stringOrNull("consultation_type")),
            chiefComplaint = raw.stringOrNull("reason_for_visit") ?: raw.stringOrNull("reason") ?: raw.stringOrNull("notes") ?: "Reason for visit not recorded",
            status = status,
            consentVerifiedAt = session.stringOrNull("consent_at") ?: "Consent record not available",
            aiSummary = aiSummary,
            transcript = messageRows.mapNotNull(::mapTranscriptTurn),
            reports = reportsMapped,
            similarCases = mapSimilarCases(summaryJson),
            clinicalNote = noteText,
            diagnoses = diagnoses,
            prescriptions = emptyList(),
            rejectionReason = session.stringOrNull("review_note"),
            aiSessionId = session.stringOrNull("id"),
            consultationId = consultation?.stringOrNull("id"),
            appointmentDate = appointmentDate,
            rawBackendStatus = rawStatus,
            patientId = raw.stringOrNull("patient_id").orEmpty()
        )
    }

    private fun mapSummary(summary: JSONObject, session: JSONObject): StructuredAiSummary {
        val text = summary.stringOrNull("clinical_summary").orEmpty()
        val labeled = text.lineSequence().mapNotNull { line ->
            val idx = line.indexOf(':')
            if (idx <= 0) null else line.substring(0, idx).trim().lowercase() to line.substring(idx + 1).trim()
        }.toMap()
        val sections = summary.optJSONObject("sections") ?: JSONObject()
        val failed = summary.stringOrNull("summary_generation_status") == "failed" || session.stringOrNull("status") == "failed"
        val meds = summary.optJSONArray("current_medications")?.toStringList()
            ?: summary.optJSONArray("medications")?.toStringList()
            ?: listOfNotNull(summary.stringOrNull("medications") ?: labeled["medications"])
                .flatMap { it.split(';', '\n').map(String::trim).filter(String::isNotBlank) }
        val unknowns = summary.optJSONArray("clarifications_or_unknowns")?.toStringList()
            ?: summary.optJSONArray("clarifications_needed")?.toStringList()
            ?: listOfNotNull(summary.stringOrNull("clarifications_needed") ?: labeled["clarifications needed"])
                .flatMap { it.split(';', '\n').map(String::trim).filter(String::isNotBlank) }
        val suggestedMedications = summary.optJSONArray("suggested_medications")?.objects()?.mapIndexedNotNull { index, item ->
            val name = item.stringOrNull("name") ?: item.stringOrNull("medicine_name") ?: return@mapIndexedNotNull null
            com.example.model.PrescriptionItem(
                id = "suggested-${session.stringOrNull("id") ?: "summary"}-$index",
                medicationName = name,
                dosage = item.stringOrNull("dosage").orEmpty(),
                frequency = item.stringOrNull("frequency").orEmpty(),
                duration = item.stringOrNull("duration").orEmpty(),
                instructions = item.stringOrNull("instructions").orEmpty()
            )
        }.orEmpty()
        val clinicalAdvice = summary.optJSONArray("clinical_advice")?.toStringList().orEmpty()
        return StructuredAiSummary(
            chiefProblem = summary.stringOrNull("problem")
                ?: summary.stringOrNull("chief_problem")
                ?: sections.stringOrNull("problem")
                ?: labeled["problem"].orEmpty(),
            symptomCourse = summary.stringOrNull("symptom_course")
                ?: summary.stringOrNull("symptoms_and_course")
                ?: sections.stringOrNull("symptoms")
                ?: labeled["symptoms and course"].orEmpty(),
            currentMedications = meds,
            medicalHistoryAndAllergies = summary.stringOrNull("medical_history_and_allergies")
                ?: sections.stringOrNull("medical_history")
                ?: labeled["relevant history and allergies"].orEmpty(),
            clarificationsOrUnknowns = unknowns,
            providerName = summary.stringOrNull("provider_name") ?: summary.stringOrNull("summary_generation_provider") ?: summary.stringOrNull("provider") ?: "Not reported by provider",
            modelVersion = summary.stringOrNull("model_version") ?: summary.stringOrNull("summary_generation_model") ?: summary.stringOrNull("model") ?: "Not reported by provider",
            generatedAt = summary.stringOrNull("generated_at") ?: session.stringOrNull("updated_at") ?: "Not recorded",
            generationLatency = summary.stringOrNull("generation_latency") ?: "Not reported",
            generationFailed = failed,
            failureReason = friendlySummaryFailureReason(
                summary.stringOrNull("summary_error_reason")
                    ?: summary.stringOrNull("failure_reason")
                    ?: session.stringOrNull("summary_error_reason")
                    ?: session.stringOrNull("summary_error")
            ),
            suggestedMedications = suggestedMedications,
            clinicalAdvice = clinicalAdvice
        )
    }

    private fun mapTranscriptTurn(row: JSONObject): TranscriptTurn? {
        val text = row.stringOrNull("content") ?: row.stringOrNull("text") ?: return null
        val role = row.stringOrNull("role")?.lowercase().orEmpty()
        if (role !in setOf("patient", "assistant", "ai", "system")) return null
        if (role == "system") return null
        val created = row.stringOrNull("created_at")
        return TranscriptTurn(
            id = row.stringOrNull("id") ?: "turn-${text.hashCode()}",
            sender = if (role == "patient") TranscriptSender.PATIENT else TranscriptSender.AI_INTERVIEWER,
            text = text,
            timestamp = created?.let(::formatTime) ?: "Time not recorded"
        )
    }

    private fun mapReport(row: JSONObject): MedicalReport {
        val findings = listOfNotNull(row.stringOrNull("findings"), row.stringOrNull("recommendations"))
        return MedicalReport(
            id = row.stringOrNull("id") ?: "report-${row.hashCode()}",
            title = row.stringOrNull("title") ?: row.stringOrNull("report_type") ?: "Medical report",
            category = row.stringOrNull("report_type") ?: "Report",
            date = row.stringOrNull("report_date") ?: "Date not recorded",
            facility = row.stringOrNull("facility") ?: "Facility not recorded",
            flaggedFindings = findings,
            ocrExtractedText = findings.joinToString("\n").ifBlank { "OCR text is not available in the report record." }
        )
    }

    private fun mapNotification(row: JSONObject): DoctorNotification = DoctorNotification(
        id = row.stringOrNull("id") ?: "notification-${row.hashCode()}",
        title = row.stringOrNull("title") ?: row.stringOrNull("notification_type") ?: "Clinical update",
        description = row.stringOrNull("message") ?: row.stringOrNull("description") ?: "",
        timeAgo = row.stringOrNull("created_at")?.let(::formatTime) ?: "Time not recorded",
        isUrgent = row.optBoolean("is_urgent", false),
        isRead = row.optBoolean("is_read", false),
        linkedAppointmentId = row.stringOrNull("appointment_id")
    )

    private fun mapSimilarCases(summary: JSONObject): List<SimilarCase> =
        summary.objectOrEmpty("similar_cases").optJSONArray("matches")?.objects()?.mapIndexed { index, item ->
            SimilarCase(
                id = item.stringOrNull("id") ?: "case-$index",
                caseCode = item.stringOrNull("case_code") ?: "De-identified reference",
                demographics = item.stringOrNull("demographics") ?: "Not provided",
                presentation = item.stringOrNull("presentation") ?: "Not provided",
                intervention = item.stringOrNull("intervention") ?: "Not provided",
                outcome = item.stringOrNull("outcome") ?: "Not provided",
                matchPercent = item.optInt("match_percent", 0)
            )
        }.orEmpty()

    private fun mapConsultationType(value: String?): ConsultationType = when (value?.lowercase()) {
        "ai_virtual", "ai-virtual" -> ConsultationType.AI_VIRTUAL
        "ai_chat", "ai-chat" -> ConsultationType.AI_CHAT
        "video" -> ConsultationType.VIDEO
        "phone" -> ConsultationType.PHONE
        "chat" -> ConsultationType.CHAT
        else -> ConsultationType.IN_PERSON
    }

    private fun JSONObject.isAiConsultation(): Boolean =
        stringOrNull("consultation_type")?.lowercase() in setOf("ai_virtual", "ai_chat")

    private fun mapStatus(sessionStatus: String, date: String?): AppointmentStatus = when (sessionStatus.lowercase()) {
        "awaiting_doctor_review" -> AppointmentStatus.AWAITING_REVIEW
        "approved" -> AppointmentStatus.APPROVED
        "in_progress", "processing" -> AppointmentStatus.IN_PROGRESS
        "failed" -> AppointmentStatus.FAILED_SUMMARY
        "rejected" -> AppointmentStatus.REJECTED
        "completed" -> AppointmentStatus.COMPLETED
        "waiting" -> AppointmentStatus.WAITING
        "incomplete" -> AppointmentStatus.INCOMPLETE
        "cancelled", "canceled", "no_show", "expired" -> AppointmentStatus.CANCELLED
        "scheduled", "pending", "booked" -> AppointmentStatus.SCHEDULED
        else -> AppointmentStatus.SCHEDULED
    }

    private fun ageFromBirthDate(value: String): Int? = runCatching {
        java.time.Period.between(LocalDate.parse(value.take(10)), LocalDate.now()).years.takeIf { it in 0..125 }
    }.getOrNull()

    private fun formatTime(value: String): String = runCatching {
        OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault()))
    }.getOrElse { value }

    private fun consultationTypeValue(type: ConsultationType): String = when (type) {
        ConsultationType.AI_VIRTUAL -> "ai_virtual"
        ConsultationType.AI_CHAT -> "ai_chat"
        ConsultationType.IN_PERSON -> "in_person"
        ConsultationType.VIDEO -> "video"
        ConsultationType.PHONE -> "phone"
        ConsultationType.CHAT -> "chat"
    }
}

private fun friendlySummaryFailureReason(code: String?): String? {
    if (code.isNullOrBlank()) return null
    val normalized = code.lowercase(Locale.ROOT)
    return when {
        "no_saved_messages" in normalized -> "No saved transcript was available to summarize. Verify the conversation before retrying."
        "json_parse_error" in normalized || "invalid_output" in normalized -> "The AI provider returned an unreadable summary. The transcript is preserved; retry generation."
        "unconfigured" in normalized || "missing_credential" in normalized -> "A summary provider is not configured. Contact the system administrator."
        "401" in normalized || "403" in normalized || "unauthorized" in normalized -> "A summary provider rejected its credentials. Contact the system administrator."
        "429" in normalized || "rate_limit" in normalized || "quota" in normalized -> "The summary provider is temporarily rate-limited. Retry shortly."
        "404" in normalized || "model_not_found" in normalized -> "The configured summary model is unavailable. Contact the system administrator."
        "timeout" in normalized || "timed_out" in normalized -> "Summary generation timed out. The transcript is preserved; retry generation."
        "503" in normalized || "502" in normalized || "504" in normalized || "provider_unavailable" in normalized -> "The summary provider is temporarily unavailable. Retry shortly."
        else -> "Summary generation failed. The transcript is preserved; retry generation."
    }
}

/** One recent history entry per patient, backed by that patient's completed visits. */
internal fun buildDoctorHistory(
    appointments: List<ClinicalAppointment>,
    today: LocalDate = LocalDate.now()
): List<DoctorHistoryRecord> {
    data class RecentVisit(val appointment: ClinicalAppointment, val date: LocalDate, val daysAgo: Int)

    val visitsByPatient = appointments.mapNotNull { appointment ->
        if (appointment.status != AppointmentStatus.COMPLETED) return@mapNotNull null
        val visitDate = runCatching {
            appointment.appointmentDate?.take(10)?.let(LocalDate::parse)
        }.getOrNull() ?: return@mapNotNull null
        val daysAgo = java.time.temporal.ChronoUnit.DAYS.between(visitDate, today).toInt()
        if (daysAgo !in 0..30) return@mapNotNull null

        val patientKey = appointment.patientId.trim().takeIf(String::isNotEmpty)?.let { "patient:$it" }
            ?: appointment.mrn.trim().takeIf {
                it.isNotEmpty() && !it.equals("Not recorded", ignoreCase = true)
            }?.let { "mrn:$it" }
            ?: "name:${appointment.patientName.trim().lowercase(Locale.ROOT)}"
        patientKey to RecentVisit(appointment, visitDate, daysAgo)
    }.groupBy({ it.first }, { it.second })

    return visitsByPatient.map { (patientKey, visits) ->
        val latest = visits.maxBy { it.date }
        val appointment = latest.appointment
        val diagnoses = visits.sortedByDescending { it.date }
            .flatMap { it.appointment.diagnoses }
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
        val medications = visits.sortedByDescending { it.date }
            .flatMap { it.appointment.prescriptions }
            .map { prescription ->
                listOf(prescription.medicationName, prescription.dosage, prescription.frequency)
                    .filter(String::isNotBlank)
                    .joinToString(" · ")
            }
            .filter(String::isNotEmpty)
            .distinct()

        DoctorHistoryRecord(
            id = "history-$patientKey",
            patientName = appointment.patientName,
            age = appointment.age,
            gender = appointment.gender,
            mrn = appointment.mrn,
            visitDate = appointment.appointmentDate ?: latest.date.toString(),
            daysAgo = latest.daysAgo,
            consultationType = appointment.consultationType,
            diagnosisSummary = diagnoses.joinToString(" • ").ifBlank { "Not documented" },
            clinicalOutcome = appointment.clinicalNote.ifBlank { "No consultation outcome recorded." },
            medicationsPrescribed = medications,
            vitalsSummary = "BP ${appointment.vitals.bp} · HR ${appointment.vitals.hr} · SpO₂ ${appointment.vitals.spo2}",
            followUpRecommended = visits.any { it.appointment.aiSummary.clarificationsOrUnknowns.isNotEmpty() },
            linkedAppointmentId = appointment.id
        )
    }.sortedBy { it.daysAgo }
}

private fun JSONArray.toStringList(): List<String> = (0 until length()).mapNotNull { index ->
    optString(index).takeIf(String::isNotBlank)
}

private fun JSONObject.optIntOrNull(key: String): Int? = if (hasValue(key)) optInt(key) else null
