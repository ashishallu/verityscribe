package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BackendAiRepository
import com.example.data.GoogleFitHealthConnectManager
import com.example.data.GoogleFitSyncSnapshot
import com.example.data.HealthBridgeStatus
import com.example.data.PrescriptionPdfExporter
import com.example.data.PatientBackendClient
import com.example.data.DirectGeminiSession
import com.example.data.VerityPreferences
import com.example.data.VeritySeedData
import com.example.model.ActivityType
import com.example.model.AiHistoryThread
import com.example.model.AiConsultationSnapshot
import com.example.model.AiConsultationTurn
import com.example.model.Appointment
import com.example.model.ChatMessage
import com.example.model.ConsultationRecord
import com.example.model.EmergencyContact
import com.example.model.HealthSnapshotMetric
import com.example.model.InsuranceInfo
import com.example.model.LinkedHospital
import com.example.model.MedicalHistorySection
import com.example.model.MedicalReport
import com.example.model.deduplicateMedicalReports
import com.example.model.MedicationScheduleItem
import com.example.model.Medicine
import com.example.model.NearbyStore
import com.example.model.NotificationItem
import com.example.model.PatientProfile
import com.example.model.HospitalOption
import com.example.model.DoctorDirectoryEntry
import com.example.model.RecentActivityItem
import com.example.model.RecordsSectionTab
import com.example.model.ReportMetric
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class VerityUiState(
    val profile: PatientProfile = PatientProfile("Patient", "Patient", "", "P", "", 0, "", "", ""),
    val appointments: List<Appointment> = emptyList(),
    val hospitals: List<HospitalOption> = emptyList(),
    val doctorsDirectory: List<DoctorDirectoryEntry> = emptyList(),
    val currentAppointmentIndex: Int = 0,
    val medicines: List<Medicine> = emptyList(),
    val medicineSearchQuery: String = "",
    val scheduleItems: List<MedicationScheduleItem> = emptyList(),
    val takenScheduleIds: Set<String> = emptySet(),
    val healthSnapshot: List<HealthSnapshotMetric> = emptyList(),
    val recentActivity: List<RecentActivityItem> = emptyList(),
    val consultations: List<ConsultationRecord> = emptyList(),
    val expandedConsultationId: String? = null,
    val playingConsultationId: String? = null,
    val playbackSecondsByRecord: Map<String, Int> = emptyMap(),
    val selectedRecordsTab: RecordsSectionTab = RecordsSectionTab.CONSULTATIONS,
    val reports: List<MedicalReport> = emptyList(),
    val selectedReportCategory: String = "All",
    val expandedReportId: String? = "rep_lipid",
    val aiHistoryThreads: List<AiHistoryThread> = emptyList(),
    val selectedAiCategory: String = "All",
    val activeAiThreadId: String? = null,
    val activeAiThreadTitle: String = "Verity Care AI",
    val savedAiThreadIds: Set<String> = emptySet(),
    val pendingDeletedAiThreadIds: Set<String> = emptySet(),
    val showAiChatThread: Boolean = false,
    val chatMessages: List<ChatMessage> = emptyList(),
    val aiDraftInput: String = "",
    val isAiGenerating: Boolean = false,
    val isLiveAiEnabled: Boolean = false,
    val notifications: List<NotificationItem> = emptyList(),
    val darkMode: Boolean = true,
    val isLoggedIn: Boolean = false,
    val authInitialized: Boolean = false,
    val authErrorMessage: String? = null,
    val loggedInEmail: String = "",
    val profilePhotoPath: String? = null,
    val googleFitState: GoogleFitSyncSnapshot = GoogleFitSyncSnapshot(),
    val isSyncingGoogleFit: Boolean = false,
    val googleFitAutoSync: Boolean = true,
    val medicalHistorySections: List<MedicalHistorySection> = emptyList(),
    val emergencyContacts: List<EmergencyContact> = emptyList(),
    val insuranceInfo: InsuranceInfo = InsuranceInfo("", "", "", "", "", 0f, "", "", "", "", false),
    val linkedHospitals: List<LinkedHospital> = emptyList(),
    val statusBannerMessage: String? = null
) {
    val morningScheduleItems: List<MedicationScheduleItem>
        get() = scheduleItems.filter { it.period == com.example.model.DosePeriod.MORNING }

    val takenTodayCount: Int
        get() = morningScheduleItems.count { it.id in takenScheduleIds }

    val totalTodayCount: Int
        get() = morningScheduleItems.size

    val pendingTodayCount: Int
        get() = (totalTodayCount - takenTodayCount).coerceAtLeast(0)

    val filteredMedicines: List<Medicine>
        get() {
            val query = medicineSearchQuery.trim()
            if (query.isEmpty()) return medicines
            return medicines.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.category.contains(query, ignoreCase = true) ||
                    it.dosage.contains(query, ignoreCase = true)
            }
        }
}

class VerityViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = VerityPreferences(application.applicationContext)
    private val googleFitManager = GoogleFitHealthConnectManager(application.applicationContext)
    private val backend = PatientBackendClient { access, refresh ->
        viewModelScope.launch { runCatching { preferences.updateRememberedTokens(access, refresh) } }
    }
    private val aiRepository = BackendAiRepository(backend)

    private val _uiState = MutableStateFlow(
        VerityUiState(
            isLiveAiEnabled = aiRepository.isLiveAiEnabled,
        )
    )
    val uiState: StateFlow<VerityUiState> = _uiState.asStateFlow()

    private var aiStreamJob: Job? = null
    private var playbackJob: Job? = null
    private val pendingDeleteJobs = mutableMapOf<String, Job>()
    private val aiConsultationSessions = mutableMapOf<String, String>()

    init {
        viewModelScope.launch {
            preferences.darkModeFlow.collect { enabled ->
                _uiState.update { it.copy(darkMode = enabled) }
            }
        }
        viewModelScope.launch {
            preferences.takenDoseIdsFlow.collect { ids ->
                _uiState.update { it.copy(takenScheduleIds = ids) }
            }
        }
        viewModelScope.launch {
            val session = preferences.authSessionFlow.first()
            if (session.isLoggedIn && !session.accessToken.isNullOrBlank()) {
                backend.restoreAccessToken(session.accessToken, session.refreshToken)
                val first = session.displayName.substringBefore(' ').ifBlank { "Patient" }
                _uiState.update { it.copy(isLoggedIn = true, authInitialized = true, loggedInEmail = session.email,
                    profile = it.profile.copy(name = session.displayName, firstName = first,
                        avatarInitial = first.firstOrNull()?.uppercase() ?: "P"), appointments = emptyList()) }
                runCatching { refreshServerData(session.displayName, session.email) }
                    .onFailure { _uiState.update { state -> state.copy(statusBannerMessage = it.localizedMessage ?: "Signed in; health data could not be refreshed yet.") } }
            } else {
                _uiState.update { it.copy(authInitialized = true) }
            }
        }
        viewModelScope.launch {
            preferences.profilePhotoPathFlow.collect { path ->
                val validPath = path?.takeIf { File(it).exists() }
                _uiState.update { it.copy(profilePhotoPath = validPath) }
            }
        }
        viewModelScope.launch {
            preferences.googleFitAutoSyncFlow.collect { enabled ->
                _uiState.update { it.copy(googleFitAutoSync = enabled) }
                if (enabled) {
                    syncGoogleFit(showBanner = false)
                }
            }
        }
        viewModelScope.launch {
            preferences.uploadedReportsJsonFlow.collect { json ->
                val current = _uiState.value
                if (!current.isLoggedIn || current.loggedInEmail.isBlank()) return@collect
                val savedReports = parseUploadedReportsJson(json, current.loggedInEmail)
                if (savedReports.isNotEmpty()) {
                    _uiState.update { state ->
                        val mergedReports = deduplicateMedicalReports(savedReports + state.reports)
                        val updatedSections = syncDocumentsSection(
                            sections = state.medicalHistorySections,
                            reports = mergedReports,
                            consultationsCount = state.consultations.size
                        )
                        state.copy(
                            reports = mergedReports,
                            medicalHistorySections = updatedSections
                        )
                    }
                }
            }
        }
    }

    fun loginWithEmail(email: String, password: String, rememberMe: Boolean) {
        viewModelScope.launch {
            runCatching {
                val identity = backend.signIn(email, password)
                preferences.saveAuthenticatedSession(identity.displayName, identity.email, backend.currentAccessToken().orEmpty(), backend.currentRefreshToken(), rememberMe)
                val first = identity.displayName.substringBefore(' ').ifBlank { "Patient" }
                _uiState.update { it.copy(isLoggedIn = true, loggedInEmail = identity.email, authErrorMessage = null,
                    appointments = emptyList(), profile = it.profile.copy(name = identity.displayName, firstName = first,
                        avatarInitial = first.firstOrNull()?.uppercase() ?: "P")) }
                runCatching { refreshServerData(identity.displayName, identity.email) }
                    .onFailure { _uiState.update { state -> state.copy(statusBannerMessage = it.localizedMessage ?: "Signed in; health data could not be refreshed yet.") } }
            }.onFailure { err ->
                backend.clearSession()
                _uiState.update { it.copy(authErrorMessage = err.localizedMessage ?: "Sign in failed. Check the connection and try again.") }
            }
        }
    }

    fun requestPasswordReset(email: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            runCatching { backend.requestPasswordReset(email) }
                .onSuccess { onResult(null) }
                .onFailure { error -> onResult(error.localizedMessage ?: "Password reset could not be requested.") }
        }
    }

    fun registerWithEmail(name: String, email: String, password: String, rememberMe: Boolean) {
        viewModelScope.launch {
            runCatching { backend.signUp(name, email, password) }
                .onSuccess { signedIn ->
                    if (!signedIn) _uiState.update { it.copy(authErrorMessage = "Account created. Verify your email, then sign in.") }
                    else {
                        val identity = runCatching { backend.profile() }.getOrNull()?.optJSONObject("data")
                        val display = identity?.let { listOf(it.optString("first_name"), it.optString("last_name")).filter(String::isNotBlank).joinToString(" ") }.orEmpty().ifBlank { name }
                        val profile = runCatching { refreshServerData(display, email) }.getOrElse { _uiState.value.profile.copy(name = display, firstName = display.substringBefore(" ")) }
                        preferences.saveAuthenticatedSession(display, email, backend.currentAccessToken().orEmpty(), backend.currentRefreshToken(), rememberMe)
                        _uiState.update { it.copy(isLoggedIn = true, loggedInEmail = email, profile = profile, authErrorMessage = null) }
                    }
                }
                .onFailure { err -> _uiState.update { it.copy(authErrorMessage = err.localizedMessage ?: "Account creation failed.") } }
        }
    }

    fun logout() {
        viewModelScope.launch {
            runCatching { aiRepository.endAllSessions() }
            aiRepository.forgetSessions()
            preferences.logout()
            backend.clearSession()
            aiConsultationSessions.clear()
            _uiState.update { it.copy(
                isLoggedIn = false, authErrorMessage = null, appointments = emptyList(),
                consultations = emptyList(), reports = emptyList(), medicines = emptyList(),
                scheduleItems = emptyList(), medicalHistorySections = emptyList(), notifications = emptyList(),
                emergencyContacts = emptyList(), linkedHospitals = emptyList(), healthSnapshot = emptyList(),
                insuranceInfo = com.example.model.InsuranceInfo("", "", "", "", "", 0f, "", "", "", "", false),
                chatMessages = emptyList(), aiHistoryThreads = emptyList(), activeAiThreadId = null,
                showAiChatThread = false, profilePhotoPath = null
            ) }
        }
    }

    private suspend fun refreshServerData(displayName: String, email: String): PatientProfile {
        val (profileResponse, patientResponse, remoteAppointments) = coroutineScope {
            val profile = async { backend.profile() }
            val patient = async { backend.patient() }
            val appointments = async { backend.appointments() }
            Triple(profile.await(), patient.await(), appointments.await())
        }
        val profileJson = profileResponse.optJSONObject("data") ?: JSONObject()
        val patientJson = patientResponse.optJSONObject("data") ?: JSONObject()
        val name = listOf(profileJson.optString("first_name"), profileJson.optString("last_name"))
            .filter(String::isNotBlank).joinToString(" ").ifBlank { displayName }.ifBlank { email.substringBefore('@') }
        val first = name.substringBefore(' ').ifBlank { "Patient" }
        _uiState.update { state -> state.copy(
            appointments = remoteAppointments,
            currentAppointmentIndex = 0,
            healthSnapshot = emptyList(), consultations = emptyList(), reports = emptyList(),
            medicines = emptyList(), scheduleItems = emptyList(), medicalHistorySections = emptyList(),
            notifications = emptyList(), recentActivity = emptyList(), emergencyContacts = emptyList(),
            linkedHospitals = emptyList(), aiHistoryThreads = emptyList(),
            chatMessages = emptyList(), activeAiThreadId = null, showAiChatThread = false,
            insuranceInfo = com.example.model.InsuranceInfo("", "", "", "", "", 0f, "", "", "", "", false),
            profile = state.profile.copy(
                name = name, firstName = first, avatarInitial = first.firstOrNull()?.uppercase() ?: "P",
                verityId = patientJson.optString("id").ifBlank { profileJson.optString("id") }.ifBlank { "" },
                clinicalContext = "Connected VerityScribe health record",
                emergencyContactsCount = patientJson.optJSONArray("emergency_contacts")?.length() ?: 0,
                insurancePlan = "",
                linkedHospitalsSummary = "No linked hospitals returned"
            )
        ) }
        viewModelScope.launch { loadPatientExtras(remoteAppointments, name, patientJson) }
        viewModelScope.launch {
            val cached = parseUploadedReportsJson(preferences.uploadedReportsJsonFlow.first(), email)
            if (cached.isNotEmpty()) _uiState.update { state ->
                if (!state.isLoggedIn || !state.loggedInEmail.equals(email, ignoreCase = true)) return@update state
                val merged = deduplicateMedicalReports(cached + state.reports)
                state.copy(reports = merged)
            }
        }
        viewModelScope.launch {
            runCatching { backend.hospitalsAndDoctors() }.onSuccess { directory ->
                _uiState.update { it.copy(hospitals = directory.first, doctorsDirectory = directory.second) }
            }.onFailure {
                _uiState.update { state -> state.copy(statusBannerMessage = "Care directory could not be loaded. Retry when connected.") }
            }
        }
        return _uiState.value.profile
    }

    private suspend fun loadPatientExtras(remoteAppointments: List<Appointment>, name: String, patientJson: JSONObject) {
        val extras = coroutineScope {
            val record = async { runCatching { backend.medicalRecord() } }
            val reports = async { runCatching { backend.reports() } }
            val insurance = async { runCatching { backend.insurance() } }
            val consultations = async { runCatching { backend.consultations() } }
            val notifications = async { runCatching { backend.notifications() } }
            val prescriptions = async { runCatching { backend.prescriptions() } }
            val schedules = async { runCatching { backend.medicationSchedules() } }
            val timeline = async { runCatching { backend.timeline() } }
            listOf(record.await(), reports.await(), insurance.await(), consultations.await(), notifications.await(), prescriptions.await(), schedules.await(), timeline.await())
        }
        val optionalDataIncomplete = extras.any { it.isFailure }
        val recordResponse = extras[0].getOrDefault(JSONObject())
        val reportResponse = extras[1].getOrDefault(JSONObject())
        val insuranceResponse = extras[2].getOrDefault(JSONObject())
        val consultationResponse = extras[3].getOrDefault(JSONObject())
        val notificationResponse = extras[4].getOrDefault(JSONObject())
        val prescriptionResponse = extras[5].getOrDefault(JSONObject())
        val scheduleResponse = extras[6].getOrDefault(JSONObject())
        val timelineResponse = extras[7].getOrDefault(JSONObject())
        val reportRows = deduplicateMedicalReports(reportResponse.optJSONArray("data").toObjects().map(::toMedicalReport))
        val prescriptionRows = prescriptionResponse.optJSONArray("data").toObjects()
        val scheduledRows = scheduleResponse.optJSONArray("data").toObjects()
        val medicines = prescriptionRows.flatMap { prescription ->
            prescription.optJSONArray("items").toObjects().map { item ->
                val medicine = item.optJSONObject("medicine") ?: JSONObject()
                val name = medicine.optString("name").ifBlank { medicine.optString("generic_name") }
                    .ifBlank { item.optString("medicine_name") }
                val dosage = listOf(medicine.optString("strength"), item.optString("dosage"))
                    .filter(String::isNotBlank).distinct().joinToString(" · ").ifBlank { "Dose not listed" }
                val frequency = item.optString("frequency").ifBlank { "Schedule not listed" }
                val itemSchedules = scheduledRows.filter { it.optString("prescription_item_id") == item.optString("id") }
                val scheduleText = itemSchedules.mapNotNull { schedule ->
                    listOf("scheduled_time", "time", "time_of_day").firstNotNullOfOrNull { key ->
                        schedule.optString(key).takeIf(String::isNotBlank)
                    }
                }.distinct().joinToString(" • ").ifBlank { frequency }
                com.example.model.Medicine(
                    id = item.optString("id").ifBlank { medicine.optString("id") }.ifBlank { UUID.randomUUID().toString() },
                    name = name.ifBlank { "Prescribed medicine" }, dosage = dosage,
                    category = "Prescription", scheduleText = scheduleText, shortSchedule = frequency,
                    initialQuantity = 0, remainingQuantity = 0, dailyUsage = 0, finishesInDays = 0,
                    reorderLabel = "Not tracked", needsReorder = false,
                    prescribedBy = "Prescription dated ${prescription.optString("prescription_date").ifBlank { "not listed" }}",
                    purpose = prescription.optString("notes").ifBlank { "Not specified in the prescription." },
                    howToTake = item.optString("instructions").ifBlank { frequency },
                    foodInstructions = "Not specified in the prescription.",
                    sideEffects = "Not listed in the prescription.",
                    doctorNotes = prescription.optString("notes").ifBlank { "No additional prescription notes." },
                    prices = emptyList(), nearbyStores = emptyList(), inventoryTracked = false
                )
            }
        }.distinctBy { it.id }
        val medicineByItemId = medicines.associateBy { it.id }
        val scheduleItems = scheduledRows.mapNotNull { schedule ->
            val itemId = schedule.optString("prescription_item_id")
            val medicine = medicineByItemId[itemId] ?: return@mapNotNull null
            val rawTime = listOf("scheduled_time", "time", "time_of_day").firstNotNullOfOrNull { key ->
                schedule.optString(key).takeIf(String::isNotBlank)
            }.orEmpty()
            val parsedHour = Regex("^(\\d{1,2})").find(rawTime)?.groupValues?.getOrNull(1)?.toIntOrNull()
            val hour = when {
                parsedHour == null -> null
                rawTime.contains("PM", ignoreCase = true) -> parsedHour % 12 + 12
                rawTime.contains("AM", ignoreCase = true) -> parsedHour % 12
                else -> parsedHour
            }
            val period = when (hour) {
                in 5..11 -> com.example.model.DosePeriod.MORNING
                in 12..16 -> com.example.model.DosePeriod.AFTERNOON
                in 17..19 -> com.example.model.DosePeriod.EVENING
                in 20..23, in 0..4 -> com.example.model.DosePeriod.NIGHT
                else -> return@mapNotNull null
            }
            com.example.model.MedicationScheduleItem(
                id = schedule.optString("id").ifBlank { "$itemId-${period.name}" },
                medicineId = medicine.id, medicineName = medicine.name,
                subtitle = listOf(medicine.dosage, medicine.shortSchedule).filter(String::isNotBlank).joinToString(" · "),
                prescriberNote = schedule.optString("notes"), period = period
            )
        }
        val consultationRows = consultationResponse.optJSONArray("data").toObjects().map { row ->
            val doctor = row.optJSONObject("doctor") ?: JSONObject()
            val doctorName = listOf(doctor.optString("first_name"), doctor.optString("last_name"))
                .filter(String::isNotBlank).joinToString(" ").ifBlank { "Care team" }
            val appointment = remoteAppointments.firstOrNull { it.id == row.optString("appointment_id") }
            val prescriptionLines = prescriptionRows
                .filter { it.optString("consultation_id") == row.optString("id") }
                .flatMap { prescription -> prescription.optJSONArray("items").toObjects().map { item ->
                    val medicine = item.optJSONObject("medicine") ?: JSONObject()
                    val name = medicine.optString("name").ifBlank { medicine.optString("generic_name") }
                        .ifBlank { item.optString("medicine_name", "Prescribed medicine") }
                    val instruction = listOf(item.optString("dosage"), item.optString("frequency"), item.optString("instructions"))
                        .filter(String::isNotBlank).joinToString(" · ")
                    com.example.model.PrescriptionLine(name, instruction.ifBlank { "Instructions not listed in record" })
                } }
            com.example.model.ConsultationRecord(
                id = row.optString("id"), doctorName = if (doctorName.startsWith("Dr.")) doctorName else "Dr. $doctorName",
                specialty = row.optString("consultation_type").ifBlank { appointment?.specialty ?: "Consultation" },
                hospital = appointment?.hospital.orEmpty(),
                dateLabel = row.optString("consultation_date").ifBlank { row.optString("created_at") }.take(10),
                timeLabel = appointment?.timeLabel.orEmpty(),
                diagnosis = row.optString("diagnosis").ifBlank { row.optString("summary") },
                status = if (row.optString("approved_at").isNotBlank()) "Approved" else "Awaiting review",
                verifiedLabel = if (row.optString("approved_at").isNotBlank()) "Clinician reviewed" else "Clinician review required",
                patientName = name, prescriptions = prescriptionLines, followUpDate = row.optString("follow_up_date"),
                recordingElapsedSeconds = 0, recordingTotalSeconds = 0
            )
        }
        val historySections = toMedicalHistory(recordResponse.optJSONObject("data") ?: JSONObject())
        val policy = insuranceResponse.optJSONObject("data")?.optJSONArray("policies")?.optJSONObject(0)
        val provider = policy?.optJSONObject("provider") ?: JSONObject()
        val activeInsurance = policy?.let {
            com.example.model.InsuranceInfo(
                planName = provider.optString("name").ifBlank { "Insurance policy" },
                policyNumber = it.optString("policy_number").ifBlank { "Not listed" },
                memberId = it.optString("member_id").ifBlank { "Not listed" },
                annualCover = it.optString("coverage_amount").ifBlank { "Not listed" },
                availableCover = it.optString("available_cover").ifBlank { "Not listed" },
                utilizationFraction = 0f,
                renewalAmount = it.optString("renewal_amount").ifBlank { "Not listed" },
                renewalDueDate = it.optString("renewal_date").ifBlank { "Not listed" },
                agentName = "", agentPhone = provider.optString("phone"),
                cashlessEligible = it.optBoolean("cashless_eligible", false)
            )
        }
        val serverNotifications = notificationResponse.optJSONArray("data").toObjects().map { row ->
            com.example.model.NotificationItem(
                id = row.optString("id"), title = row.optString("title").ifBlank { "Notification" },
                body = row.optString("body").ifBlank { row.optString("message") },
                timeAgo = row.optString("created_at"), isUnread = !row.has("is_read") || row.optBoolean("is_read") == false
            )
        }
        val emergencyContacts = patientJson.optJSONArray("emergency_contacts").toObjects().mapNotNull { row ->
            val contactId = row.optString("id")
            val contactName = row.optString("name").ifBlank {
                listOf(row.optString("first_name"), row.optString("last_name"))
                    .filter(String::isNotBlank).joinToString(" ")
            }
            if (contactName.isBlank()) null else EmergencyContact(
                id = contactId.ifBlank { "contact_${contactName.hashCode()}" },
                name = contactName,
                relation = row.optString("relationship").ifBlank { row.optString("relation", "Not specified") },
                phoneNumber = row.optString("phone_number").ifBlank { row.optString("phone") },
                isPrimary = row.optBoolean("is_primary")
            )
        }
        val recentActivity = timelineResponse.optJSONArray("data").toObjects().mapIndexedNotNull { index, event ->
            val type = event.optString("type")
            val record = event.optJSONObject("record") ?: JSONObject()
            val description = when (type) {
                "appointment" -> record.optString("reason_for_visit").ifBlank { record.optString("status") }
                "consultation" -> record.optString("diagnosis").ifBlank { record.optString("summary") }
                "prescription" -> record.optString("notes").ifBlank { "Prescription recorded" }
                "report" -> record.optString("report_type").ifBlank { record.optString("title", "Report added") }
                "vital" -> record.optString("vital_type").ifBlank { "Vital recorded" }
                "diagnosis" -> record.optString("diagnosis", "Diagnosis recorded")
                else -> "Clinical record updated"
            }
            val activityType = when (type) {
                "prescription" -> ActivityType.PRESCRIPTION
                "report" -> ActivityType.LAB_REPORT
                else -> ActivityType.REMINDER
            }
            RecentActivityItem(
                id = "timeline_${event.optString("date")}_${record.optString("id", index.toString())}",
                title = type.replaceFirstChar { it.uppercase() },
                subtitle = listOf(event.optString("date").take(10), description)
                    .filter(String::isNotBlank).joinToString(" • "),
                type = activityType
            )
        }
        _uiState.update { state -> state.copy(
            consultations = consultationRows,
            reports = reportRows,
            medicines = medicines,
            scheduleItems = scheduleItems,
            medicalHistorySections = historySections,
            notifications = serverNotifications,
            recentActivity = recentActivity,
            emergencyContacts = emergencyContacts,
            insuranceInfo = activeInsurance ?: com.example.model.InsuranceInfo(
                "", "", "", "", "", 0f, "", "", "", "", false
            ),
            profile = state.profile.copy(
                insurancePlan = activeInsurance?.planName.orEmpty(),
                linkedHospitalsSummary = remoteAppointments.map { it.hospital }.filter(String::isNotBlank).distinct().joinToString().ifBlank { "No linked hospitals returned" },
                clinicalContext = buildString {
                    val conditions = recordResponse.optJSONObject("data")?.optJSONArray("chronic_conditions").toObjects()
                        .mapNotNull { it.optString("condition_name").takeIf(String::isNotBlank) }
                    val allergies = recordResponse.optJSONObject("data")?.optJSONArray("allergies").toObjects()
                        .mapNotNull { it.optString("allergen").takeIf(String::isNotBlank) }
                    if (conditions.isNotEmpty()) append("Patient-recorded conditions: ${conditions.joinToString()}. ")
                    if (allergies.isNotEmpty()) append("Recorded allergies: ${allergies.joinToString()}.")
                    if (isEmpty()) append("Connected VerityScribe health record; no additional history returned.")
                },
            )
        ).let { updated ->
            if (optionalDataIncomplete) updated.copy(statusBannerMessage = "Some record sections could not be loaded. Check your connection and refresh.") else updated
        } }
    }

    private fun JSONArray?.toObjects(): List<JSONObject> = this?.let { array ->
        (0 until array.length()).mapNotNull(array::optJSONObject)
    }.orEmpty()

    private fun toMedicalReport(row: JSONObject): MedicalReport {
        val type = row.optString("report_type").ifBlank { "Medical report" }
        val date = row.optString("report_date").ifBlank { row.optString("created_at") }.take(10)
        return MedicalReport(
            id = row.optString("id"), title = type.replace('_', ' '),
            labName = "", orderedBy = "", dateLabel = date, category = type.replace('_', ' '),
            status = row.optString("review_status").ifBlank { "Available" },
            summaryNote = listOf(row.optString("findings"), row.optString("recommendations"))
                .filter(String::isNotBlank).joinToString("\n"),
            metrics = emptyList(), attachmentPath = row.optString("report_file_url").takeIf(String::isNotBlank),
            isUserUploaded = row.optString("source_type") == "private_chat_upload",
            duplicateFingerprint = row.optString("report_file_url").takeIf(String::isNotBlank)?.let { "url:$it" }
        )
    }

    private fun toMedicalHistory(record: JSONObject): List<MedicalHistorySection> {
        val sections = listOf(
            "medical_history" to "Medical history", "allergies" to "Allergies",
            "chronic_conditions" to "Chronic conditions", "surgeries" to "Surgeries",
            "vaccinations" to "Vaccinations", "family_history" to "Family history",
            "lifestyle" to "Lifestyle", "disabilities" to "Disabilities"
        )
        return sections.mapNotNull { (key, title) ->
            val entries = record.optJSONArray(key).toObjects()
            if (entries.isEmpty()) return@mapNotNull null
            val details = entries.mapNotNull { entry ->
                listOf("condition_name", "allergen", "name", "description", "notes", "value")
                    .firstNotNullOfOrNull { field -> entry.optString(field).takeIf(String::isNotBlank) }
            }
            if (details.isEmpty()) return@mapNotNull null
            com.example.model.MedicalHistorySection(
                id = key, title = title, summary = "${details.size} patient-recorded entr${if (details.size == 1) "y" else "ies"}",
                details = details, lastUpdated = entries.firstOrNull()?.optString("updated_at").orEmpty().take(10)
            )
        }
    }

    fun clearAuthError() {
        _uiState.update { it.copy(authErrorMessage = null) }
    }

    fun getAiHealthSummaryText(): String {
        val state = _uiState.value
        val pendingHomeMeds = state.morningScheduleItems.count { it.id !in state.takenScheduleIds }
        val sleepValue = state.healthSnapshot.find { it.id == "sleep" }?.value ?: "not synced"
        return aiRepository.buildHomeSummary(
            upcomingCount = state.appointments.size,
            pendingMedicinesCount = pendingHomeMeds,
            sleepDuration = sleepValue
        )
    }

    fun setAppointmentIndex(index: Int) {
        val maxIndex = (_uiState.value.appointments.size - 1).coerceAtLeast(0)
        _uiState.update { it.copy(currentAppointmentIndex = index.coerceIn(0, maxIndex)) }
    }

    fun rescheduleAppointment(appointmentId: String, newDate: String, newTime: String) {
        _uiState.update { it.copy(statusBannerMessage = "Appointment rescheduling is not available from the connected service yet.") }
    }

    fun bookAppointment(
        doctorName: String,
        specialty: String,
        hospital: String,
        dateLabel: String,
        timeLabel: String,
        consultationType: String,
        reason: String,
        notes: String,
        avatarRes: Int,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val state = _uiState.value
            val selectedDoctor = state.doctorsDirectory.firstOrNull { it.name == doctorName && it.hospital == hospital }
            val selectedHospital = state.hospitals.firstOrNull { it.name == hospital }
            if (selectedDoctor == null || selectedHospital == null) {
                _uiState.update { it.copy(statusBannerMessage = "The selected doctor is unavailable. Refresh the directory and try again.") }
                onComplete?.invoke(false)
                return@launch
            }
            runCatching {
                backend.bookAppointment(selectedDoctor.id, selectedHospital.id, dateLabel, timeLabel, consultationType, reason, notes)
            }.onSuccess { appointment ->
                _uiState.update { current -> current.copy(
                    appointments = listOf(appointment) + current.appointments,
                    currentAppointmentIndex = 0,
                    statusBannerMessage = "Appointment booked with ${appointment.doctorName}"
                ) }
                onComplete?.invoke(true)
            }.onFailure { error ->
                _uiState.update { it.copy(statusBannerMessage = error.localizedMessage ?: "Could not book appointment.") }
                onComplete?.invoke(false)
            }
        }
    }

    fun sendAppointmentAiMessage(appointment: Appointment, message: String, requestKey: String, onResult: (String?, String?) -> Unit) {
        viewModelScope.launch {
            val sessionId = aiConsultationSessions[appointment.id]
            if (sessionId.isNullOrBlank()) {
                onResult(null, "Accept the AI intake consent before sending a message.")
                return@launch
            }
            try {
                val reply = backend.sendAiConsultationMessage(sessionId, message, requestKey)
                onResult(reply, null)
            } catch (sendError: Exception) {
                // A timeout can happen after the server saved the patient turn. Reload first;
                // only return a reply if the same idempotency-keyed turn is confirmed.
                val recovered = runCatching { readAiConsultationSnapshot(sessionId) }.getOrNull()
                val messages = recovered?.messages.orEmpty()
                val patientIndex = messages.indexOfLast {
                    it.role == "patient" && (it.requestKey == requestKey || it.content == message)
                }
                val recoveredReply = messages.drop(patientIndex + 1)
                    .firstOrNull { it.role == "assistant" }?.content
                if (patientIndex >= 0 && !recoveredReply.isNullOrBlank()) onResult(recoveredReply, null)
                else onResult(null, sendError.localizedMessage ?: "AI consultation is unavailable.")
            }
        }
    }

    fun prepareAppointmentAiChat(appointment: Appointment, explicitConsent: Boolean, onResult: (AiConsultationSnapshot?, String?) -> Unit) {
        viewModelScope.launch {
            if (!explicitConsent) {
                onResult(null, "AI consultation consent is required.")
                return@launch
            }
            runCatching {
                val sessionId = aiConsultationSessions[appointment.id]
                    ?: preferences.aiConsultationSession(appointment.id)
                    ?: backend.startAiConsultation(appointment.id, "ai_chat", consent = true, voiceConsent = false)
                        .also {
                            aiConsultationSessions[appointment.id] = it
                            preferences.saveAiConsultationSession(appointment.id, it)
                        }
                aiConsultationSessions[appointment.id] = sessionId
                readAiConsultationSnapshot(sessionId)
            }.onSuccess { onResult(it, null) }
                .onFailure { onResult(null, it.localizedMessage ?: "The AI intake session could not be loaded.") }
        }
    }

    private suspend fun readAiConsultationSnapshot(sessionId: String): AiConsultationSnapshot {
        val data = backend.getAiConsultation(sessionId)
        val session = data.optJSONObject("session") ?: JSONObject()
        val messages = data.optJSONArray("messages").toObjects().mapIndexed { index, row ->
            AiConsultationTurn(
                id = row.optString("id").ifBlank { "turn_$index" },
                role = row.optString("role"),
                content = row.optString("content"),
                requestKey = row.optString("request_key").takeIf(String::isNotBlank)
            )
        }
        val finalization = data.optJSONObject("finalization") ?: JSONObject()
        val summary = session.optJSONObject("summary")?.optString("clinical_summary")?.takeIf(String::isNotBlank)
        return AiConsultationSnapshot(
            sessionId = session.optString("id", sessionId),
            status = session.optString("status"),
            finalizationState = finalization.optString("state", session.optString("status")),
            messages = messages,
            summary = summary
        )
    }

    fun endAppointmentAiConsultation(appointment: Appointment, onResult: (String?, String?) -> Unit) {
        viewModelScope.launch {
            val sessionId = aiConsultationSessions[appointment.id]
            if (sessionId.isNullOrBlank()) {
                onResult(null, "Accept the AI intake consent and start the conversation before ending it.")
                return@launch
            }
            var lastError: String? = null
            repeat(8) { attempt ->
                if (attempt == 0) runCatching { backend.endAiConsultation(sessionId) }
                    .onFailure { lastError = it.localizedMessage }
                val snapshot = runCatching { readAiConsultationSnapshot(sessionId) }.getOrNull()
                val summary = snapshot?.summary
                if (!summary.isNullOrBlank()) {
                    onResult(summary, null)
                    return@launch
                }
                val state = snapshot?.finalizationState.orEmpty()
                if (state in setOf("failed_summary", "incomplete")) {
                    lastError = "The conversation is saved, but summary generation needs a retry ($state)."
                    return@repeat
                }
                if (attempt < 7) delay(750L)
            }
            onResult(null, lastError ?: "The conversation is saved, but the summary is still being finalized. Retry shortly.")
        }
    }

    suspend fun startAppointmentAiVirtual(appointment: Appointment, explicitConsent: Boolean, explicitVoiceConsent: Boolean): DirectGeminiSession {
        require(explicitConsent && explicitVoiceConsent) { "Explicit AI Virtual and microphone consent is required." }
        val sessionId = aiConsultationSessions[appointment.id]
            ?: preferences.aiConsultationSession(appointment.id)
        if (sessionId != null) {
            aiConsultationSessions[appointment.id] = sessionId
            val credential = backend.issueLiveCredential(sessionId)
            val token = credential.optString("credential")
            val model = credential.optString("model")
            val config = credential.optJSONObject("config")
            if (token.isBlank() || model.isBlank() || config == null) throw IOException("Gemini Live setup was incomplete.")
            return DirectGeminiSession(sessionId, token, model, config)
        }
        val live = backend.startAiVirtual(appointment.id, consent = true, voiceConsent = true)
        aiConsultationSessions[appointment.id] = live.sessionId
        preferences.saveAiConsultationSession(appointment.id, live.sessionId)
        return live
    }

    suspend fun persistAppointmentAiVirtualTurn(sessionId: String, requestKey: String, input: String, output: String) {
        backend.persistDirectTurn(sessionId, requestKey, input, output)
    }

    suspend fun endAppointmentAiVirtual(sessionId: String): String = backend.endAiConsultation(sessionId)

    fun toggleScheduleDose(scheduleId: String) {
        viewModelScope.launch {
            val current = _uiState.value.takenScheduleIds
            val updated = if (scheduleId in current) current - scheduleId else current + scheduleId
            _uiState.update { it.copy(takenScheduleIds = updated) }
            preferences.setTakenDoseIds(updated)
        }
    }

    fun isMedicineTakenToday(medicineId: String): Boolean {
        val taken = _uiState.value.takenScheduleIds
        return when (medicineId) {
            "med_metformin" -> "sched_metformin_morning" in taken
            "med_atorvastatin" -> "sched_atorvastatin_night" in taken || "sched_atorvastatin_morning" in taken
            "med_vitamind3" -> "sched_vitd_morning" in taken
            else -> false
        }
    }

    fun toggleMedicineHomeStatus(medicineId: String) {
        val scheduleId = when (medicineId) {
            "med_metformin" -> "sched_metformin_morning"
            "med_atorvastatin" -> "sched_atorvastatin_night"
            "med_vitamind3" -> "sched_vitd_morning"
            else -> return
        }
        toggleScheduleDose(scheduleId)
    }

    fun updateMedicineSearch(query: String) {
        _uiState.update { it.copy(medicineSearchQuery = query) }
    }

    fun findMedicine(medicineId: String): Medicine? {
        return _uiState.value.medicines.find { it.id == medicineId }
    }

    fun toggleConsultationExpanded(recordId: String) {
        _uiState.update { state ->
            val next = if (state.expandedConsultationId == recordId) null else recordId
            state.copy(expandedConsultationId = next)
        }
    }

    fun toggleRecordingPlayback(recordId: String) {
        val currentlyPlaying = _uiState.value.playingConsultationId
        if (currentlyPlaying == recordId) {
            playbackJob?.cancel()
            _uiState.update { it.copy(playingConsultationId = null) }
            return
        }

        val record = _uiState.value.consultations.find { it.id == recordId } ?: return
        playbackJob?.cancel()
        _uiState.update { it.copy(playingConsultationId = recordId) }
        playbackJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val currentSec = _uiState.value.playbackSecondsByRecord[recordId] ?: record.recordingElapsedSeconds
                if (currentSec >= record.recordingTotalSeconds) {
                    _uiState.update { state ->
                        state.copy(
                            playingConsultationId = null,
                            playbackSecondsByRecord = state.playbackSecondsByRecord + (recordId to 0)
                        )
                    }
                    break
                } else {
                    _uiState.update { state ->
                        state.copy(
                            playbackSecondsByRecord = state.playbackSecondsByRecord + (recordId to (currentSec + 1))
                        )
                    }
                }
            }
        }
    }

    fun seekRecording(recordId: String, seconds: Int) {
        val record = _uiState.value.consultations.find { it.id == recordId } ?: return
        val clamped = seconds.coerceIn(0, record.recordingTotalSeconds)
        _uiState.update { state ->
            state.copy(playbackSecondsByRecord = state.playbackSecondsByRecord + (recordId to clamped))
        }
    }

    fun downloadPrescriptionPdf(record: ConsultationRecord) {
        viewModelScope.launch {
            val result = PrescriptionPdfExporter.exportPrescriptionPdf(getApplication(), record)
            val message = result.fold(
                onSuccess = { file -> "Saved ${file.name} (${record.doctorName})" },
                onFailure = { err -> "Could not save PDF: ${err.localizedMessage ?: "Unknown error"}" }
            )
            _uiState.update { it.copy(statusBannerMessage = message) }
        }
    }

    fun selectRecordsTab(tab: RecordsSectionTab) {
        _uiState.update { it.copy(selectedRecordsTab = tab) }
    }

    fun selectReportCategory(category: String) {
        _uiState.update { it.copy(selectedReportCategory = category) }
    }

    fun toggleReportExpanded(reportId: String) {
        _uiState.update { state ->
            val next = if (state.expandedReportId == reportId) null else reportId
            state.copy(expandedReportId = next)
        }
    }

    fun shareReportWithDoctor(report: MedicalReport) {
        _uiState.update {
            it.copy(statusBannerMessage = "Shared ${report.title} with ${report.orderedBy}")
        }
    }

    fun downloadReport(report: MedicalReport) {
        val path = report.attachmentPath
        if (!path.isNullOrBlank() && File(path).exists()) {
            openReportAttachment(report)
            return
        }
        _uiState.update {
            it.copy(statusBannerMessage = "Saved ${report.title} (${report.dateLabel})")
        }
    }

    fun openReportAttachment(report: MedicalReport) {
        val path = report.attachmentPath ?: return
        val file = File(path)
        if (!file.exists()) {
            showStatusMessage("Attached file is no longer available.")
            return
        }
        val app = getApplication<Application>()
        runCatching {
            val contentUri = FileProvider.getUriForFile(
                app,
                "${app.packageName}.fileprovider",
                file
            )
            val mimeType = report.attachmentMimeType ?: "*/*"
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(viewIntent)
        }.onFailure {
            showStatusMessage("Saved locally as ${report.attachmentName ?: file.name}")
        }
    }

    fun uploadMedicalReport(
        uri: Uri,
        title: String,
        labName: String,
        orderedBy: String,
        category: String,
        notes: String
    ) {
        viewModelScope.launch {
            persistUploadedReport(uri, title, labName, orderedBy, category, notes, chatThreadId = null)
        }
    }

    fun uploadAndAttachReportToAiChat(
        uri: Uri,
        title: String,
        labName: String,
        orderedBy: String,
        category: String,
        notes: String
    ) {
        viewModelScope.launch {
            val threadId = _uiState.value.activeAiThreadId?.takeIf { it.isNotBlank() && it != "thread_new" }
                ?: "thread_${UUID.randomUUID()}"
            _uiState.update { state -> state.copy(
                activeAiThreadId = threadId,
                activeAiThreadTitle = if (state.chatMessages.isEmpty()) "Medical report review" else state.activeAiThreadTitle,
                showAiChatThread = true
            ) }
            val created = persistUploadedReport(uri, title, labName, orderedBy, category, notes, chatThreadId = threadId)
            if (created != null) {
                attachExistingReportToAiChat(created, alreadyAttachedToCurrentThread = true)
            }
        }
    }

    private suspend fun persistUploadedReport(
        uri: Uri,
        title: String,
        labName: String,
        orderedBy: String,
        category: String,
        notes: String,
        chatThreadId: String?
    ): MedicalReport? {
        val copiedAttachment = withContext(Dispatchers.IO) {
            runCatching {
                val app = getApplication<Application>()
                val resolver = app.contentResolver
                val suppliedMime = resolver.getType(uri)
                val displayName = resolveDisplayName(uri) ?: buildDefaultAttachmentName(suppliedMime ?: "application/octet-stream")
                val mimeType = suppliedMime?.takeIf { it in setOf("application/pdf", "image/png", "image/jpeg", "image/jpg", "image/webp") }
                    ?: when (displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
                        "pdf" -> "application/pdf"
                        "png" -> "image/png"
                        "jpg", "jpeg" -> "image/jpeg"
                        "webp" -> "image/webp"
                        else -> suppliedMime ?: "application/octet-stream"
                    }
                val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val reportsDir = File(app.filesDir, "uploaded_reports").apply { mkdirs() }
                val destFile = File(reportsDir, "${System.currentTimeMillis()}_$safeName")

                resolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: return@runCatching null

                if (destFile.exists() && destFile.length() > 0L) {
                    Triple(destFile.absolutePath, displayName, mimeType)
                } else {
                    null
                }
            }.getOrNull()
        }

        if (copiedAttachment == null) {
            showStatusMessage("Unable to read the selected report file.")
            return null
        }

        val (savedPath, displayName, mimeType) = copiedAttachment
        val duplicateFingerprint = withContext(Dispatchers.IO) {
            runCatching { sha256(File(savedPath)) }.getOrNull()
        }?.let { "sha256:$it" }
        val dateLabel = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
        val resolvedTitle = title.trim().ifBlank {
            displayName.substringBeforeLast('.').ifBlank { "Uploaded Lab Report" }
        }
        val resolvedLab = labName.trim().ifBlank { "Personal Upload" }
        val resolvedDoctor = orderedBy.trim().ifBlank { "Self-uploaded" }
        val sessionId = runCatching { aiRepository.sessionForReportUpload(chatThreadId ?: "report_uploads") }.getOrElse { error ->
            File(savedPath).delete()
            showStatusMessage(error.localizedMessage ?: "A secure chat session could not be prepared for report upload.")
            return null
        }
        val uploadResponse = runCatching {
            backend.uploadChatReport(sessionId, File(savedPath), displayName, mimeType, normalizeReportType(category, mimeType))
        }.getOrElse { error ->
            File(savedPath).delete()
            showStatusMessage(error.localizedMessage ?: "Secure report upload failed. The report was not added.")
            return null
        }
        val uploadData = uploadResponse.optJSONObject("data") ?: JSONObject()
        val serverDocument = uploadData.optJSONObject("document") ?: JSONObject()
        val documentId = serverDocument.optString("id")
        val extractedText = uploadData.optString("extracted_text").trim()
        if (documentId.isBlank() || extractedText.isBlank()) {
            File(savedPath).delete()
            showStatusMessage("The server stored no readable report text. Try a clearer PDF or image.")
            return null
        }
        val formatBadge = when {
            mimeType.contains("pdf", ignoreCase = true) -> "PDF Document"
            mimeType.startsWith("image/", ignoreCase = true) -> "Report Image"
            else -> "Clinical File"
        }

        val newReport = MedicalReport(
            id = documentId,
            title = resolvedTitle,
            labName = resolvedLab,
            orderedBy = resolvedDoctor,
            dateLabel = dateLabel,
            category = uploadData.optString("report_type").ifBlank { normalizeReportType(category, mimeType) },
            status = "Saved for clinician review",
            summaryNote = listOf(
                notes.trim(),
                "Unverified OCR text — confirm against the original report before clinical use:\n${extractedText.take(6000)}"
            ).filter(String::isNotBlank).joinToString("\n\n"),
            metrics = listOf(
                ReportMetric("Attachment", displayName, formatBadge, isFlagged = false),
                ReportMetric("Uploaded On", dateLabel, "Private report stored; clinician review required", isFlagged = false)
            ),
            attachmentPath = savedPath,
            attachmentName = displayName,
            attachmentMimeType = mimeType,
            isUserUploaded = true,
            duplicateFingerprint = duplicateFingerprint
        )

        var uploadedListToSave: List<MedicalReport> = emptyList()
        _uiState.update { state ->
            val updatedReports = deduplicateMedicalReports(listOf(newReport) + state.reports)
            val visibleReport = updatedReports.firstOrNull { it.id == newReport.id }
                ?: updatedReports.first()
            uploadedListToSave = updatedReports.filter { it.isUserUploaded }
            val updatedSections = syncDocumentsSection(
                sections = state.medicalHistorySections,
                reports = updatedReports,
                consultationsCount = state.consultations.size
            )
            val newActivity = RecentActivityItem(
                id = "act_${UUID.randomUUID()}",
                title = "Report uploaded",
                subtitle = "$resolvedTitle • $dateLabel",
                type = ActivityType.LAB_REPORT
            )
            state.copy(
                reports = updatedReports,
                selectedReportCategory = "All",
                expandedReportId = visibleReport.id,
                medicalHistorySections = updatedSections,
                recentActivity = listOf(newActivity) + state.recentActivity,
                statusBannerMessage = "Uploaded $resolvedTitle securely; OCR text is available for review"
            )
        }
        preferences.setUploadedReportsJson(encodeUploadedReportsJson(uploadedListToSave, _uiState.value.loggedInEmail))
        return newReport
    }

    private fun normalizeReportType(category: String, mimeType: String): String {
        val value = category.lowercase(Locale.ROOT)
        return when {
            value.contains("prescription") || value.contains("medicine") -> "prescription"
            value.contains("blood") -> "blood_report"
            value.contains("x-ray") || value.contains("xray") -> "x_ray"
            value.contains("mri") -> "mri"
            value.contains("ecg") || value.contains("ekg") -> "ecg"
            value.contains("lab") -> "lab_report"
            mimeType == "application/pdf" -> "pdf"
            else -> "medicine_photo"
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        val resolver = getApplication<Application>().contentResolver
        return resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else {
                null
            }
        }
    }

    private fun buildDefaultAttachmentName(mimeType: String): String {
        val ext = when {
            mimeType.contains("pdf", ignoreCase = true) -> "pdf"
            mimeType.contains("png", ignoreCase = true) -> "png"
            mimeType.startsWith("image/", ignoreCase = true) -> "jpg"
            else -> "dat"
        }
        return "medical_report.$ext"
    }

    private fun syncDocumentsSection(
        sections: List<MedicalHistorySection>,
        reports: List<MedicalReport>,
        consultationsCount: Int
    ): List<MedicalHistorySection> {
        val latestDate = reports.firstOrNull()?.dateLabel ?: "Today"
        val details = reports.map { rep ->
            val tag = if (rep.isUserUploaded) "Uploaded" else rep.labName
            "${rep.title} — ${rep.dateLabel} ($tag)"
        }
        return sections.map { section ->
            if (section.id == "mh_documents") {
                section.copy(
                    summary = "${reports.size} lab reports & $consultationsCount consultation summaries synced",
                    details = details,
                    lastUpdated = latestDate
                )
            } else {
                section
            }
        }
    }

    private fun encodeUploadedReportsJson(reports: List<MedicalReport>, ownerEmail: String): String {
        val arr = JSONArray()
        reports.forEach { rep ->
            val obj = JSONObject().apply {
                put("id", rep.id)
                put("title", rep.title)
                put("labName", rep.labName)
                put("orderedBy", rep.orderedBy)
                put("dateLabel", rep.dateLabel)
                put("category", rep.category)
                put("status", rep.status)
                put("summaryNote", rep.summaryNote)
                put("attachmentPath", rep.attachmentPath ?: "")
                put("attachmentName", rep.attachmentName ?: "")
                put("attachmentMimeType", rep.attachmentMimeType ?: "")
                put("duplicateFingerprint", rep.duplicateFingerprint ?: "")
            }
            arr.put(obj)
        }
        return JSONObject().put("owner_email", ownerEmail.trim().lowercase(Locale.ROOT))
            .put("reports", arr).toString()
    }

    private fun parseUploadedReportsJson(json: String?, ownerEmail: String): List<MedicalReport> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val root = JSONObject(json)
            if (root.optString("owner_email").trim().lowercase(Locale.ROOT) != ownerEmail.trim().lowercase(Locale.ROOT)) {
                return@runCatching emptyList()
            }
            val arr = root.optJSONArray("reports") ?: JSONArray()
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val path = obj.optString("attachmentPath").takeIf { it.isNotBlank() && File(it).exists() }
                    val name = obj.optString("attachmentName").takeIf { it.isNotBlank() } ?: "Uploaded report"
                    val mime = obj.optString("attachmentMimeType").takeIf { it.isNotBlank() } ?: "application/pdf"
                    val dateLabel = obj.optString("dateLabel").ifBlank { "Recent" }
                    val formatBadge = when {
                        mime.contains("pdf", ignoreCase = true) -> "PDF Document"
                        mime.startsWith("image/", ignoreCase = true) -> "Report Image"
                        else -> "Clinical File"
                    }
                    add(
                        MedicalReport(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            labName = obj.optString("labName", "Personal Upload"),
                            orderedBy = obj.optString("orderedBy", "Self-uploaded"),
                            dateLabel = dateLabel,
                            category = obj.optString("category", "General"),
                            status = obj.optString("status", "UPLOADED"),
                            summaryNote = obj.optString("summaryNote"),
                            metrics = listOf(
                                ReportMetric("Attachment", name, formatBadge, isFlagged = false),
                                ReportMetric("Uploaded On", dateLabel, "Synced to Medical History", isFlagged = false)
                            ),
                            attachmentPath = path,
                            attachmentName = name,
                            attachmentMimeType = mime,
                            isUserUploaded = true,
                            duplicateFingerprint = obj.optString("duplicateFingerprint").takeIf(String::isNotBlank)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun selectAiCategory(category: String) {
        _uiState.update { it.copy(selectedAiCategory = category) }
    }

    fun openAiThread(thread: AiHistoryThread) {
        aiStreamJob?.cancel()
        _uiState.update {
            it.copy(
                activeAiThreadId = thread.id,
                activeAiThreadTitle = thread.title,
                chatMessages = thread.messages,
                showAiChatThread = true,
                isAiGenerating = false
            )
        }
    }

    fun startNewAiThread() {
        aiStreamJob?.cancel()
        val threadId = "thread_${UUID.randomUUID()}"
        _uiState.update {
            it.copy(
                activeAiThreadId = threadId,
                activeAiThreadTitle = "New chat",
                chatMessages = listOf(newAiWelcomeMessage(threadId)),
                showAiChatThread = true,
                isAiGenerating = false
            )
        }
    }

    fun closeAiThread() {
        _uiState.update { it.copy(showAiChatThread = false) }
    }

    fun toggleSavedActiveThread() {
        val currentId = _uiState.value.activeAiThreadId ?: "thread_new"
        _uiState.update { state ->
            val updated = if (currentId in state.savedAiThreadIds) {
                state.savedAiThreadIds - currentId
            } else {
                state.savedAiThreadIds + currentId
            }
            state.copy(savedAiThreadIds = updated)
        }
    }

    fun markAiThreadDeleted(threadId: String) {
        pendingDeleteJobs[threadId]?.cancel()
        _uiState.update { state ->
            state.copy(
                pendingDeletedAiThreadIds = state.pendingDeletedAiThreadIds + threadId
            )
        }
        pendingDeleteJobs[threadId] = viewModelScope.launch {
            delay(4500L)
            _uiState.update { state ->
                val remaining = state.aiHistoryThreads.filterNot { it.id == threadId }
                val clearedActive = if (state.activeAiThreadId == threadId) null else state.activeAiThreadId
                state.copy(
                    aiHistoryThreads = remaining,
                    pendingDeletedAiThreadIds = state.pendingDeletedAiThreadIds - threadId,
                    activeAiThreadId = clearedActive
                )
            }
            pendingDeleteJobs.remove(threadId)
        }
    }

    fun undoDeleteAiThread(threadId: String) {
        pendingDeleteJobs.remove(threadId)?.cancel()
        _uiState.update { state ->
            state.copy(
                pendingDeletedAiThreadIds = state.pendingDeletedAiThreadIds - threadId
            )
        }
    }

    fun updateAiDraft(text: String) {
        _uiState.update { it.copy(aiDraftInput = text) }
    }

    fun sendAiPrompt(promptText: String) {
        val trimmed = promptText.trim()
        if (trimmed.isEmpty() || _uiState.value.isAiGenerating) return
        val before = _uiState.value
        val threadId = before.activeAiThreadId?.takeIf { it.isNotBlank() && it != "thread_new" }
            ?: "thread_${UUID.randomUUID()}"

        val nowLabel = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val userMsg = ChatMessage(
            id = "user_${UUID.randomUUID()}",
            isUser = true,
            text = trimmed,
            timestamp = nowLabel
        )
        val assistantMsgId = "ai_${UUID.randomUUID()}"
        val placeholderMsg = ChatMessage(
            id = assistantMsgId,
            isUser = false,
            text = "...",
            timestamp = nowLabel,
            isStreaming = true
        )

        val requestHistory = before.chatMessages + userMsg
        _uiState.update { state ->
            val resolvedTitle = if (state.activeAiThreadId == null || state.activeAiThreadId == "thread_new" ||
                state.activeAiThreadId != threadId || state.activeAiThreadTitle == "New chat") {
                trimmed.take(28).let { if (trimmed.length > 28) "$it..." else it }
            } else {
                state.activeAiThreadTitle
            }
            state.copy(
                activeAiThreadId = threadId,
                showAiChatThread = true,
                activeAiThreadTitle = resolvedTitle,
                chatMessages = state.chatMessages + userMsg + placeholderMsg,
                aiDraftInput = "",
                isAiGenerating = true
            )
        }

        aiStreamJob?.cancel()
        aiStreamJob = viewModelScope.launch {
            try {
                aiRepository.streamClinicalReply(
                    userPrompt = trimmed,
                    clinicalContext = _uiState.value.profile.clinicalContext,
                    threadId = threadId,
                    history = requestHistory
                ).collect { partial ->
                    _uiState.update { state ->
                        state.copy(
                            chatMessages = state.chatMessages.map { msg ->
                                if (msg.id == assistantMsgId) {
                                    msg.copy(text = partial, isStreaming = true)
                                } else {
                                    msg
                                }
                            }
                        )
                    }
                }
                _uiState.update { state ->
                    val complete = state.copy(
                        isAiGenerating = false,
                        chatMessages = state.chatMessages.map { msg ->
                            if (msg.id == assistantMsgId) msg.copy(isStreaming = false) else msg
                        }
                    )
                    snapshotActiveAiThread(complete, threadId)
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    val failed = state.copy(
                        isAiGenerating = false,
                        chatMessages = state.chatMessages.map { msg ->
                            if (msg.id == assistantMsgId) {
                                msg.copy(
                                    text = "Unable to generate response: ${e.localizedMessage ?: "Error"}",
                                    isStreaming = false
                                )
                            } else {
                                msg
                            }
                        }
                    )
                    snapshotActiveAiThread(failed, threadId)
                }
            }
        }
    }

    fun attachExistingReportToAiChat(report: MedicalReport, alreadyAttachedToCurrentThread: Boolean = false) {
        if (_uiState.value.isAiGenerating) return
        val before = _uiState.value
        val threadId = before.activeAiThreadId?.takeIf { it.isNotBlank() && it != "thread_new" }
            ?: "thread_${UUID.randomUUID()}"
        val nowLabel = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val userMsg = ChatMessage(
            id = "user_rep_${UUID.randomUUID()}",
            isUser = true,
            text = "Please review this ${report.category.lowercase()} report: ${report.title}",
            timestamp = nowLabel,
            attachmentTitle = report.title,
            attachmentSubtitle = "${report.labName} • ${report.dateLabel}",
            attachmentPath = report.attachmentPath,
            attachmentMimeType = report.attachmentMimeType,
            backendDocumentId = if (alreadyAttachedToCurrentThread) report.id else null
        )
        val assistantMsgId = "ai_rep_${UUID.randomUUID()}"
        val placeholderMsg = ChatMessage(
            id = assistantMsgId,
            isUser = false,
            text = "...",
            timestamp = nowLabel,
            isStreaming = true
        )

        val requestHistory = before.chatMessages + userMsg
        _uiState.update { state ->
            val resolvedTitle = if (state.activeAiThreadId == null || state.activeAiThreadId == "thread_new" || state.activeAiThreadId != threadId) {
                report.title.take(28).let { if (report.title.length > 28) "$it..." else it }
            } else {
                state.activeAiThreadTitle
            }
            state.copy(
                activeAiThreadId = threadId,
                showAiChatThread = true,
                activeAiThreadTitle = resolvedTitle,
                chatMessages = state.chatMessages + userMsg + placeholderMsg,
                isAiGenerating = true
            )
        }

        val metricsSummary = report.metrics.take(3).joinToString("; ") { "${it.name}: ${it.value}" }
        val promptForAi = "Analyze uploaded report '${report.title}' (${report.category}, ${report.labName}, ${report.dateLabel}). Metrics: $metricsSummary. Notes: ${report.summaryNote}"

        aiStreamJob?.cancel()
        aiStreamJob = viewModelScope.launch {
            try {
                var backendDocumentId: String? = if (alreadyAttachedToCurrentThread) report.id else null
                val localFile = report.attachmentPath?.let(::File)?.takeIf { it.isFile && it.length() > 0L }
                if (!alreadyAttachedToCurrentThread && localFile != null) {
                    val sessionId = aiRepository.sessionForReportUpload(threadId)
                    val upload = backend.uploadChatReport(
                        sessionId, localFile, report.attachmentName ?: localFile.name,
                        report.attachmentMimeType ?: "application/octet-stream",
                        normalizeReportType(report.category, report.attachmentMimeType ?: "application/octet-stream")
                    )
                    val uploadData = upload.optJSONObject("data") ?: JSONObject()
                    backendDocumentId = uploadData.optJSONObject("document")?.optString("id")
                        ?.takeIf(String::isNotBlank)
                        ?: throw IOException("The report was uploaded but no document ID was returned.")
                    _uiState.update { state -> state.copy(chatMessages = state.chatMessages.map { item ->
                        if (item.id == userMsg.id) item.copy(backendDocumentId = backendDocumentId) else item
                    }) }
                }
                aiRepository.streamClinicalReply(
                    userPrompt = promptForAi,
                    clinicalContext = _uiState.value.profile.clinicalContext,
                    threadId = threadId,
                    history = requestHistory,
                    attachmentText = buildString {
                        append("Report: ${report.title}; type: ${report.category}; lab: ${report.labName}; date: ${report.dateLabel}.")
                        if (metricsSummary.isNotBlank()) append(" Extracted metrics: $metricsSummary.")
                        if (report.summaryNote.isNotBlank()) append(" Extracted report text: ${report.summaryNote}")
                    }
                ).collect { partial ->
                    _uiState.update { state ->
                        state.copy(
                            chatMessages = state.chatMessages.map { msg ->
                                if (msg.id == assistantMsgId) {
                                    msg.copy(text = partial, isStreaming = true)
                                } else {
                                    msg
                                }
                            }
                        )
                    }
                }
                _uiState.update { state ->
                    val complete = state.copy(
                        isAiGenerating = false,
                        chatMessages = state.chatMessages.map { msg ->
                            if (msg.id == assistantMsgId) {
                                val enriched = buildString {
                                    append("Report reviewed: ${report.title} (${report.dateLabel}). ")
                                    if (metricsSummary.isNotBlank()) {
                                        append("Key details: $metricsSummary. ")
                                    }
                                    append(msg.text)
                                }
                                msg.copy(text = enriched, isStreaming = false)
                            } else {
                                msg
                            }
                        }
                    )
                    snapshotActiveAiThread(complete, threadId)
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    val failed = state.copy(
                        isAiGenerating = false,
                        chatMessages = state.chatMessages.map { msg ->
                            if (msg.id == assistantMsgId) {
                                msg.copy(
                                    text = "Unable to review report: ${e.localizedMessage ?: "Error"}",
                                    isStreaming = false
                                )
                            } else {
                                msg
                            }
                        }
                    )
                    snapshotActiveAiThread(failed, threadId)
                }
            }
        }
    }

    fun clearChat() {
        aiStreamJob?.cancel()
        val oldThread = _uiState.value.activeAiThreadId
        if (!oldThread.isNullOrBlank()) viewModelScope.launch { runCatching { aiRepository.endThread(oldThread) } }
        val nextThread = "thread_${UUID.randomUUID()}"
        _uiState.update {
            it.copy(
                activeAiThreadId = nextThread,
                activeAiThreadTitle = "New chat",
                chatMessages = listOf(newAiWelcomeMessage(nextThread)),
                isAiGenerating = false
            )
        }
    }

    private fun newAiWelcomeMessage(threadId: String): ChatMessage = ChatMessage(
        id = "welcome_$threadId",
        isUser = false,
        text = "Hello, I'm Verity. I can help explain information in your health record, reports, and medicines, or help you find an appointment. What would you like to know?",
        timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    )

    fun removeAiChatAttachment(documentId: String) {
        val threadId = _uiState.value.activeAiThreadId ?: return
        viewModelScope.launch {
            runCatching { aiRepository.removeDocument(threadId, documentId) }
                .onSuccess {
                    _uiState.update { state -> state.copy(
                        chatMessages = state.chatMessages.map { message ->
                            if (message.backendDocumentId == documentId) message.copy(backendDocumentId = null) else message
                        },
                        statusBannerMessage = "Report removed from this AI chat. It remains in Medical Records."
                    ) }
                }
                .onFailure { error -> _uiState.update { it.copy(
                    statusBannerMessage = error.localizedMessage ?: "Report could not be removed from this chat."
                ) } }
        }
    }

    private fun snapshotActiveAiThread(state: VerityUiState, threadId: String): VerityUiState {
        val visible = state.chatMessages.filterNot { it.isStreaming || it.text == "..." }
        if (visible.isEmpty()) return state
        val title = state.activeAiThreadTitle.ifBlank {
            visible.firstOrNull { it.isUser }?.text?.take(28) ?: "Verity AI chat"
        }
        val preview = visible.lastOrNull()?.text.orEmpty().take(120)
        val thread = AiHistoryThread(
            id = threadId,
            title = title,
            category = "Consultation",
            preview = preview,
            messages = visible
        )
        return state.copy(aiHistoryThreads = listOf(thread) + state.aiHistoryThreads.filterNot { it.id == threadId })
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(darkMode = enabled) }
            preferences.setDarkMode(enabled)
        }
    }

    fun markNotificationsRead() {
        viewModelScope.launch {
            runCatching { backend.markAllNotificationsRead() }
                .onSuccess {
                    _uiState.update { state -> state.copy(notifications = state.notifications.map { it.copy(isUnread = false) }) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(statusBannerMessage = error.localizedMessage ?: "Notifications could not be marked read.") }
                }
        }
    }

    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch {
            runCatching { backend.markNotificationRead(notificationId) }
                .onSuccess {
                    _uiState.update { state -> state.copy(
                        notifications = state.notifications.map { item ->
                            if (item.id == notificationId) item.copy(isUnread = false) else item
                        }
                    ) }
                }
                .onFailure { error -> _uiState.update { it.copy(
                    statusBannerMessage = error.localizedMessage ?: "Notification could not be marked read."
                ) } }
        }
    }

    fun uploadAppointmentVoiceDraft(appointmentId: String, filePath: String, onResult: (String?, String?, String?) -> Unit) {
        viewModelScope.launch {
            runCatching { backend.uploadAppointmentVoice(appointmentId, File(filePath)) }
                .onSuccess { response ->
                    val data = response.optJSONObject("data") ?: JSONObject()
                    val transcript = data.optJSONObject("transcript") ?: JSONObject()
                    val text = transcript.optString("transcript_text").takeIf(String::isNotBlank)
                    val transcriptId = transcript.optString("id").takeIf(String::isNotBlank)
                    val status = data.optString("status").ifBlank { data.optJSONObject("asr")?.optString("status").orEmpty() }
                    onResult(text, transcriptId, status)
                }
                .onFailure { error -> onResult(null, null, error.localizedMessage ?: "Voice note transcription failed.") }
        }
    }

    fun correctAppointmentVoiceTranscript(transcriptId: String, text: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            runCatching { backend.saveVoiceTranscriptCorrection(transcriptId, text) }
                .onSuccess { onResult(null) }
                .onFailure { error -> onResult(error.localizedMessage ?: "Transcript correction could not be saved.") }
        }
    }

    fun updateProfilePhoto(uri: Uri) {
        viewModelScope.launch {
            val savedPath = withContext(Dispatchers.IO) {
                runCatching {
                    val app = getApplication<Application>()
                    val dir = app.filesDir
                    dir.listFiles { file ->
                        file.name.startsWith("profile_avatar_") && file.name.endsWith(".jpg")
                    }?.forEach { it.delete() }

                    val destFile = File(dir, "profile_avatar_${System.currentTimeMillis()}.jpg")
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    } ?: return@runCatching null

                    if (destFile.exists() && destFile.length() > 0L) destFile.absolutePath else null
                }.getOrNull()
            }

            if (savedPath != null) {
                preferences.setProfilePhotoPath(savedPath)
                _uiState.update {
                    it.copy(
                        profilePhotoPath = savedPath,
                        statusBannerMessage = "Profile photo updated"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(statusBannerMessage = "Unable to load selected photo")
                }
            }
        }
    }

    fun removeProfilePhoto() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val app = getApplication<Application>()
                app.filesDir.listFiles { file ->
                    file.name.startsWith("profile_avatar_") && file.name.endsWith(".jpg")
                }?.forEach { it.delete() }
            }
            preferences.setProfilePhotoPath(null)
            _uiState.update {
                it.copy(
                    profilePhotoPath = null,
                    statusBannerMessage = "Profile photo removed"
                )
            }
        }
    }

    fun syncGoogleFit(showBanner: Boolean = true) {
        if (_uiState.value.isSyncingGoogleFit) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingGoogleFit = true) }
            val snapshot = withContext(Dispatchers.IO) {
                googleFitManager.inspectAndSync()
            }
            _uiState.update { state ->
                val updatedMetrics = buildList {
                    snapshot.heartRateBpm?.let { add(HealthSnapshotMetric("hr", "Heart rate", it.toString(), "bpm")) }
                    snapshot.sleepFormatted?.let { add(HealthSnapshotMetric("sleep", "Sleep", it)) }
                    snapshot.spo2Percent?.let { add(HealthSnapshotMetric("spo2", "Blood oxygen", "$it%")) }
                    snapshot.stepsToday?.let { add(HealthSnapshotMetric("steps", "Steps today", googleFitManager.formatSteps(it))) }
                }
                val deviceSummary = when (snapshot.status) {
                    HealthBridgeStatus.CONNECTED -> {
                        val stamp = snapshot.lastSyncedLabel ?: "Just now"
                        "Google Fit • Synced ($stamp)"
                    }
                    HealthBridgeStatus.PERMISSIONS_NEEDED ->
                        if (snapshot.grantedCount > 0) {
                            "Google Fit • ${snapshot.grantedCount}/4 authorized"
                        } else {
                            "Google Fit • Tap to connect"
                        }
                    HealthBridgeStatus.UPDATE_REQUIRED -> "Health Connect update required"
                    HealthBridgeStatus.UNAVAILABLE -> "Google Fit • Setup Health Connect"
                }
                state.copy(
                    googleFitState = snapshot,
                    isSyncingGoogleFit = false,
                    healthSnapshot = updatedMetrics,
                    profile = state.profile.copy(connectedDeviceSummary = deviceSummary),
                    statusBannerMessage = if (showBanner) snapshot.statusDetail else state.statusBannerMessage
                )
            }
        }
    }

    fun onHealthPermissionsResult(grantedPermissions: Set<String>) {
        if (grantedPermissions.isNotEmpty()) {
            syncGoogleFit(showBanner = true)
        } else {
            viewModelScope.launch {
                val snapshot = withContext(Dispatchers.IO) {
                    googleFitManager.inspectAndSync()
                }
                _uiState.update {
                    it.copy(
                        googleFitState = snapshot,
                        statusBannerMessage = "Health Connect permissions were not granted."
                    )
                }
            }
        }
    }

    fun toggleGoogleFitAutoSync(enabled: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(googleFitAutoSync = enabled) }
            preferences.setGoogleFitAutoSync(enabled)
            if (enabled) {
                syncGoogleFit(showBanner = true)
            }
        }
    }

    fun openGoogleFitApp() {
        val opened = googleFitManager.openGoogleFitOrStore()
        if (!opened) {
            showStatusMessage("Google Fit is not installed on this device.")
        }
    }

    fun openHealthConnectSettings() {
        val opened = googleFitManager.openHealthConnectSettings()
        if (!opened) {
            showStatusMessage("Health Connect settings unavailable on this device.")
        }
    }

    fun addMedicalHistoryNote(sectionId: String, note: String) {
        val trimmed = note.trim()
        if (trimmed.isEmpty()) return
        val today = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
        _uiState.update { state ->
            val updated = state.medicalHistorySections.map { section ->
                if (section.id == sectionId) {
                    section.copy(
                        details = section.details + trimmed,
                        lastUpdated = today
                    )
                } else {
                    section
                }
            }
            state.copy(
                medicalHistorySections = updated,
                statusBannerMessage = "Updated medical history"
            )
        }
    }

    fun showStatusMessage(message: String) {
        _uiState.update { it.copy(statusBannerMessage = message) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusBannerMessage = null) }
    }
}
