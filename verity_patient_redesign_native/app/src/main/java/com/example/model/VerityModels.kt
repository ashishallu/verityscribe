package com.example.model

import androidx.annotation.DrawableRes

enum class VerityTab(val route: String, val label: String) {
    HOME("home", "Home"),
    AI("ai", "Care AI"),
    MEDICINES("medicines", "Pharmacy"),
    ALARMS("alarms", "Schedule"),
    RECORDS("records", "History"),
    PROFILE("profile", "Profile")
}

data class Appointment(
    val id: String,
    val doctorName: String,
    val specialty: String,
    val hospital: String,
    val dateLabel: String,
    val timeLabel: String,
    val consultationType: String = ConsultationType.VIDEO.label,
    val notes: String,
    @DrawableRes val avatarRes: Int
)

data class PharmacyPrice(
    val id: String,
    val vendorName: String,
    val discountInfo: String,
    val etaInfo: String,
    val priceRupees: Int,
    val isBestValue: Boolean = false
)

data class NearbyStore(
    val id: String,
    val name: String,
    val distanceKm: String,
    val stockStatus: String,
    val openUntil: String,
    val phoneNumber: String
)

data class Medicine(
    val id: String,
    val name: String,
    val dosage: String,
    val category: String,
    val scheduleText: String,
    val shortSchedule: String,
    val initialQuantity: Int,
    val remainingQuantity: Int,
    val dailyUsage: Int,
    val finishesInDays: Int,
    val reorderLabel: String,
    val needsReorder: Boolean,
    val prescribedBy: String,
    val purpose: String,
    val howToTake: String,
    val foodInstructions: String,
    val sideEffects: String,
    val doctorNotes: String,
    val prices: List<PharmacyPrice>,
    val nearbyStores: List<NearbyStore>,
    val inventoryTracked: Boolean = true
)

enum class DosePeriod(val title: String, val defaultTime: String) {
    MORNING("Morning", "08:00 AM"),
    AFTERNOON("Afternoon", "12:30 PM"),
    EVENING("Evening", "06:30 PM"),
    NIGHT("Night", "08:00 PM")
}

data class MedicationScheduleItem(
    val id: String,
    val medicineId: String,
    val medicineName: String,
    val subtitle: String,
    val prescriberNote: String,
    val period: DosePeriod,
    val isGreenAccent: Boolean = false
)

data class HealthSnapshotMetric(
    val id: String,
    val label: String,
    val value: String,
    val unit: String = ""
)

data class RecentActivityItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val type: ActivityType
)

enum class ActivityType {
    PRESCRIPTION,
    REMINDER,
    LAB_REPORT
}

data class PrescriptionLine(
    val medicineName: String,
    val instruction: String
)

data class ConsultationRecord(
    val id: String,
    val doctorName: String,
    val specialty: String = "General Medicine",
    val hospital: String,
    val dateLabel: String,
    val timeLabel: String,
    val diagnosis: String,
    val status: String,
    val verifiedLabel: String,
    val patientName: String,
    val prescriptions: List<PrescriptionLine>,
    val followUpDate: String,
    val recordingElapsedSeconds: Int,
    val recordingTotalSeconds: Int,
    @DrawableRes val avatarRes: Int = 0
)

enum class RecordsSectionTab(val label: String) {
    CONSULTATIONS("Consultations"),
    REPORTS("Reports")
}

data class ReportMetric(
    val name: String,
    val value: String,
    val referenceRange: String,
    val isFlagged: Boolean = false
)

data class MedicalReport(
    val id: String,
    val title: String,
    val labName: String,
    val orderedBy: String,
    val dateLabel: String,
    val category: String,
    val status: String,
    val summaryNote: String,
    val metrics: List<ReportMetric>,
    val attachmentPath: String? = null,
    val attachmentName: String? = null,
    val attachmentMimeType: String? = null,
    val isUserUploaded: Boolean = false,
    val duplicateFingerprint: String? = null
)

data class ChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val timestamp: String,
    val isStreaming: Boolean = false,
    val isVoiceNote: Boolean = false,
    val voiceDurationLabel: String = "",
    val attachmentTitle: String? = null,
    val attachmentSubtitle: String? = null,
    val attachmentPath: String? = null,
    val attachmentMimeType: String? = null,
    val backendDocumentId: String? = null
)

data class AiHistoryThread(
    val id: String,
    val title: String,
    val category: String,
    val preview: String,
    val messages: List<ChatMessage>
)

data class AiConsultationTurn(
    val id: String,
    val role: String,
    val content: String,
    val requestKey: String? = null
)

data class AiConsultationSnapshot(
    val sessionId: String,
    val status: String,
    val finalizationState: String,
    val messages: List<AiConsultationTurn>,
    val summary: String? = null
)

data class NotificationItem(
    val id: String,
    val title: String,
    val body: String,
    val timeAgo: String,
    val isUnread: Boolean = true
)

data class PatientProfile(
    val name: String,
    val firstName: String,
    val verityId: String,
    val avatarInitial: String,
    val clinicalContext: String,
    val emergencyContactsCount: Int,
    val insurancePlan: String,
    val linkedHospitalsSummary: String,
    val connectedDeviceSummary: String
)

data class HospitalOption(
    val id: String,
    val name: String,
    val departments: List<String>
)

data class DoctorDirectoryEntry(
    val id: String,
    val name: String,
    val department: String,
    val hospital: String,
    val feeRupees: Int,
    @DrawableRes val avatarRes: Int
)

enum class ConsultationType(val label: String) {
    AI_CHAT("AI Chat"),
    AI_VIRTUAL("AI Virtual"),
    IN_PERSON("In person"),
    VIDEO("Video")
}

data class MedicalHistorySection(
    val id: String,
    val title: String,
    val summary: String,
    val details: List<String>,
    val lastUpdated: String,
    val isAlert: Boolean = false
)

data class EmergencyContact(
    val id: String,
    val name: String,
    val relation: String,
    val phoneNumber: String,
    val isPrimary: Boolean = false
)

data class InsuranceInfo(
    val planName: String,
    val policyNumber: String,
    val memberId: String,
    val annualCover: String,
    val availableCover: String,
    val utilizationFraction: Float,
    val renewalAmount: String,
    val renewalDueDate: String,
    val agentName: String,
    val agentPhone: String,
    val cashlessEligible: Boolean = true
)

data class LinkedHospital(
    val id: String,
    val name: String,
    val address: String,
    val distanceLabel: String,
    val departments: List<String>,
    val isPrimary: Boolean = false,
    val isCashlessEnabled: Boolean = true,
    val lastVisitLabel: String,
    val syncStatus: String = "Records synced"
)

