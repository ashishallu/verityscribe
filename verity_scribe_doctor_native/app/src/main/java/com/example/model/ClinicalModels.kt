package com.example.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class AppointmentStatus(val label: String) {
    AWAITING_REVIEW("Awaiting Review"),
    APPROVED("Approved"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed"),
    FAILED_SUMMARY("Failed Summary"),
    REJECTED("Rejected"),
    SCHEDULED("Scheduled"),
    WAITING("Waiting"),
    CANCELLED("Cancelled"),
    INCOMPLETE("Incomplete")
}

enum class ConsultationType(val label: String) {
    AI_CHAT("AI Chat"),
    AI_VIRTUAL("AI Virtual"),
    IN_PERSON("In-Person"),
    VIDEO("Video"),
    PHONE("Phone"),
    CHAT("Chat")
}

enum class HomeFilterTab(val label: String) {
    ALL("All"),
    AWAITING_REVIEW("Awaiting Review"),
    UPCOMING("Upcoming"),
    COMPLETED("Completed")
}

enum class ShiftPeriod(val label: String, val hours: String?) {
    FULL_DAY("Full Day", null),
    MORNING_OPD("Morning OPD", "08:00–13:00"),
    AFTERNOON_ROUNDS("Afternoon rounds", "13:00–17:00");

    fun displayLabel(date: LocalDate = LocalDate.now()): String {
        val formattedDate = date.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
        return listOfNotNull(label, hours, formattedDate).joinToString(" • ")
    }
}

enum class TranscriptSender {
    AI_INTERVIEWER,
    PATIENT
}

data class TranscriptTurn(
    val id: String,
    val sender: TranscriptSender,
    val text: String,
    val timestamp: String
)

data class StructuredAiSummary(
    val chiefProblem: String,
    val symptomCourse: String,
    val currentMedications: List<String>,
    val medicalHistoryAndAllergies: String,
    val clarificationsOrUnknowns: List<String>,
    val providerName: String,
    val modelVersion: String,
    val generatedAt: String,
    val generationLatency: String,
    val generationFailed: Boolean = false,
    val failureReason: String? = null,
    val suggestedMedications: List<PrescriptionItem> = emptyList(),
    val clinicalAdvice: List<String> = emptyList()
)

data class MedicalReport(
    val id: String,
    val title: String,
    val category: String,
    val date: String,
    val facility: String,
    val flaggedFindings: List<String>,
    val ocrExtractedText: String
)

data class SimilarCase(
    val id: String,
    val caseCode: String,
    val demographics: String,
    val presentation: String,
    val intervention: String,
    val outcome: String,
    val matchPercent: Int
)

data class PrescriptionItem(
    val id: String,
    val medicationName: String,
    val dosage: String,
    val frequency: String,
    val duration: String,
    val instructions: String
)

data class VitalsSnapshot(
    val bp: String,
    val hr: String,
    val spo2: String,
    val temp: String
)

data class ClinicalAppointment(
    val id: String,
    val patientName: String,
    val age: Int,
    val gender: String,
    val mrn: String,
    val bloodGroup: String,
    val allergies: List<String>,
    val vitals: VitalsSnapshot,
    val scheduledTime: String,
    val isMorningShift: Boolean,
    val consultationType: ConsultationType,
    val chiefComplaint: String,
    val status: AppointmentStatus,
    val consentVerifiedAt: String,
    val aiSummary: StructuredAiSummary,
    val transcript: List<TranscriptTurn>,
    val reports: List<MedicalReport>,
    val similarCases: List<SimilarCase>,
    val clinicalNote: String,
    val diagnoses: List<String>,
    val prescriptions: List<PrescriptionItem>,
    val rejectionReason: String? = null,
    val aiSessionId: String? = null,
    val consultationId: String? = null,
    val appointmentDate: String? = null,
    val rawBackendStatus: String? = null,
    val patientId: String = ""
) {
    val ageLabel: String get() = age.takeIf { it > 0 }?.let { "$it yrs" } ?: "Age not recorded"
}

enum class AssistSessionCategory(val label: String) {
    ALL("All"),
    INTAKE_DRAFTS("Intake Drafts"),
    RECORDS("Records"),
    SUMMARIES("Summaries")
}

data class AssistMessage(
    val id: String,
    val isFromDoctor: Boolean,
    val text: String,
    val timestamp: String,
    val attachmentName: String? = null,
    val isVoiceNote: Boolean = false,
    val voiceDuration: String? = null
)

data class AssistSession(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: AssistSessionCategory,
    val linkedAppointmentId: String,
    val updatedAt: String,
    val messages: List<AssistMessage>,
    val linkedHistoryRecordId: String? = null,
    val isHistoryOnly: Boolean = false
)

data class DoctorNotification(
    val id: String,
    val title: String,
    val description: String,
    val timeAgo: String,
    val isUrgent: Boolean,
    val isRead: Boolean,
    val linkedAppointmentId: String? = null
)

data class DoctorProfile(
    val fullName: String,
    val greetingName: String,
    val specialtyTitle: String,
    val department: String,
    val hospital: String,
    val licenseNumber: String,
    val experienceYears: Int?,
    val languages: String,
    val workingHours: String,
    val isAvailable: Boolean
)

data class NotificationPreferences(
    val urgentLabAlerts: Boolean = true,
    val aiSummaryReadyAlerts: Boolean = true,
    val queueArrivalAlerts: Boolean = true,
    val failedSummaryAlerts: Boolean = true
)

data class PrivacyPreferences(
    val biometricLockEnabled: Boolean = true,
    val autoLockMinutes: Int = 5,
    val requireConsentForSimilarCases: Boolean = true
)

enum class HistoryRangeFilter(val label: String, val maxDaysAgo: Int) {
    LAST_30_DAYS("Last 30 Days", 30),
    LAST_7_DAYS("Last 7 Days", 7),
    FOLLOW_UP("Follow-up Flagged", 30)
}

data class DoctorHistoryRecord(
    val id: String,
    val patientName: String,
    val age: Int,
    val gender: String,
    val mrn: String,
    val visitDate: String,
    val daysAgo: Int,
    val consultationType: ConsultationType,
    val diagnosisSummary: String,
    val clinicalOutcome: String,
    val medicationsPrescribed: List<String>,
    val vitalsSummary: String,
    val followUpRecommended: Boolean = false,
    val linkedAppointmentId: String? = null
) {
    val ageLabel: String get() = age.takeIf { it > 0 }?.let { "$it yrs" } ?: "Age not recorded"
}
