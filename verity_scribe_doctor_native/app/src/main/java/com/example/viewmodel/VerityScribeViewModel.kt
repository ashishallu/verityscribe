package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DoctorApiException
import com.example.data.DoctorRepository
import com.example.data.DoctorWorkspaceData
import com.example.data.RememberedSessionStore
import com.example.data.VerifiedDoctorIdentity
import com.example.model.AppointmentStatus
import com.example.model.AssistMessage
import com.example.model.AssistSession
import com.example.model.AssistSessionCategory
import com.example.model.ClinicalAppointment
import com.example.model.DoctorHistoryRecord
import com.example.model.DoctorNotification
import com.example.model.DoctorProfile
import com.example.model.HistoryRangeFilter
import com.example.model.HomeFilterTab
import com.example.model.NotificationPreferences
import com.example.model.PrescriptionItem
import com.example.model.PrivacyPreferences
import com.example.model.ShiftPeriod
import com.example.model.StructuredAiSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class VerityScribeUiState(
    val isRestoringSession: Boolean = true,
    val isSignedIn: Boolean = false,
    val isAuthenticating: Boolean = false,
    val isLoadingWorkspace: Boolean = false,
    val isRefreshing: Boolean = false,
    val isRefreshingDetail: Boolean = false,
    val doctorProfile: DoctorProfile = emptyDoctorProfile(),
    val appointments: List<ClinicalAppointment> = emptyList(),
    val selectedShift: ShiftPeriod = ShiftPeriod.FULL_DAY,
    val selectedHomeFilter: HomeFilterTab = HomeFilterTab.ALL,
    val homeSearchQuery: String = "",
    val directorySearchQuery: String = "",
    val directoryFilter: HomeFilterTab = HomeFilterTab.ALL,
    val notifications: List<DoctorNotification> = emptyList(),
    val assistMessages: List<AssistMessage> = emptyList(),
    val assistSessions: List<AssistSession> = emptyList(),
    val selectedAssistCategory: AssistSessionCategory = AssistSessionCategory.ALL,
    val assistPatientContextId: String = "",
    val isAssistTyping: Boolean = false,
    val retryingSummaryAppointmentId: String? = null,
    val notificationPreferences: NotificationPreferences = NotificationPreferences(),
    val privacyPreferences: PrivacyPreferences = PrivacyPreferences(),
    val doctorHistory: List<DoctorHistoryRecord> = emptyList(),
    val selectedHistoryRange: HistoryRangeFilter = HistoryRangeFilter.LAST_30_DAYS,
    val historySearchQuery: String = "",
    val snackbarMessage: String? = null
) {
    val unreadNotificationsCount: Int get() = notifications.count { !it.isRead }
    val totalAppointmentsCount: Int get() = filteredByShiftAppointments.size
    val awaitingReviewCount: Int get() = filteredByShiftAppointments.count {
        it.status == AppointmentStatus.AWAITING_REVIEW || it.status == AppointmentStatus.FAILED_SUMMARY
    }
    val pendingReportsCount: Int get() = filteredByShiftAppointments.sumOf { it.reports.size }
    val completedCount: Int get() = filteredByShiftAppointments.count { it.status == AppointmentStatus.COMPLETED }
    val filteredByShiftAppointments: List<ClinicalAppointment>
        get() = when (selectedShift) {
            ShiftPeriod.FULL_DAY -> appointments
            ShiftPeriod.MORNING_OPD -> appointments.filter { it.isMorningShift }
            ShiftPeriod.AFTERNOON_ROUNDS -> appointments.filter { !it.isMorningShift }
        }
    val last30DaysHistory: List<DoctorHistoryRecord>
        get() = doctorHistory.filter { it.daysAgo in 0..30 }.sortedBy { it.daysAgo }
}

private fun emptyDoctorProfile() = DoctorProfile(
    fullName = "Clinician",
    greetingName = "Doctor",
    specialtyTitle = "Specialty not recorded",
    department = "Department not recorded",
    hospital = "Hospital not recorded",
    licenseNumber = "Not recorded",
    experienceYears = null,
    languages = "Not recorded",
    workingHours = "Not recorded",
    isAvailable = false
)

/** State and actions for the supplied Compose UI; all clinical data comes from authenticated APIs. */
class VerityScribeViewModel : ViewModel() {
    private val repository = DoctorRepository()
    private val _uiState = MutableStateFlow(VerityScribeUiState())
    val uiState: StateFlow<VerityScribeUiState> = _uiState.asStateFlow()
    private var accessToken: String? = null
    private var doctorId: String? = null
    private var sessionRestoreStarted = false
    private val editedSummaries = mutableMapOf<String, StructuredAiSummary>()

    fun restoreRememberedSession(sessionStore: RememberedSessionStore) {
        if (sessionRestoreStarted) return
        sessionRestoreStarted = true
        val savedRefreshToken = sessionStore.readRefreshToken()
        if (savedRefreshToken.isNullOrBlank()) {
            _uiState.update { it.copy(isRestoringSession = false) }
            return
        }
        _uiState.update { it.copy(isRestoringSession = true, isAuthenticating = true) }
        viewModelScope.launch {
            runCatching {
                val tokens = try {
                    withContext(Dispatchers.IO) { repository.refreshSession(savedRefreshToken) }
                } catch (error: Throwable) {
                    if (error is DoctorApiException && error.statusCode in 400..499) sessionStore.clear()
                    throw error
                }
                // Supabase refresh tokens rotate on use. Save the replacement before loading
                // the workspace so process death cannot lose the newly issued token.
                withContext(Dispatchers.IO) { sessionStore.saveRefreshToken(tokens.refreshToken) }
                val identity = repository.verifyDoctor(tokens.accessToken)
                tokens.accessToken to identity
            }.onSuccess { (token, identity) ->
                activateVerifiedIdentity(token, identity, "Welcome back, ${repository.profileFor(identity).fullName}")
                loadWorkspaceInBackground(token, identity)
            }.onFailure { error ->
                accessToken = null
                doctorId = null
                _uiState.update {
                    it.copy(
                        isRestoringSession = false,
                        isAuthenticating = false,
                        snackbarMessage = if (error is DoctorApiException && error.statusCode in 400..499) {
                            "Your saved sign-in has expired. Please sign in again."
                        } else {
                            error.message?.takeIf(String::isNotBlank)
                                ?: "Could not restore your saved sign-in. Please try again."
                        }
                    )
                }
            }
        }
    }

    fun signInWithCredentials(
        email: String,
        password: String,
        rememberMe: Boolean,
        sessionStore: RememberedSessionStore,
        onSuccess: () -> Unit = {}
    ) {
        if (_uiState.value.isAuthenticating) return
        if (email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(snackbarMessage = "Enter your clinical email and password.") }
            return
        }
        sessionRestoreStarted = true
        _uiState.update { it.copy(isAuthenticating = true, snackbarMessage = null) }
        viewModelScope.launch {
            runCatching {
                val tokens = withContext(Dispatchers.IO) { repository.signIn(email.trim(), password) }
                // Authenticate and verify the server-authoritative doctor role before entry.
                // Appointment history, reports, and notifications load after the first screen.
                val identity = repository.verifyDoctor(tokens.accessToken)
                withContext(Dispatchers.IO) {
                    if (rememberMe) sessionStore.saveRefreshToken(tokens.refreshToken) else sessionStore.clear()
                }
                tokens.accessToken to identity
            }.onSuccess { (token, identity) ->
                activateVerifiedIdentity(token, identity, "Signed in as ${repository.profileFor(identity).fullName}")
                loadWorkspaceInBackground(token, identity)
                onSuccess()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isAuthenticating = false,
                        snackbarMessage = error.message?.takeIf(String::isNotBlank)
                            ?: "Sign-in failed. Check the account and try again."
                    )
                }
            }
        }
    }

    private fun activateVerifiedIdentity(
        token: String,
        identity: VerifiedDoctorIdentity,
        message: String
    ) {
        accessToken = token
        doctorId = identity.doctorId
        _uiState.update {
            it.copy(
                isRestoringSession = false,
                isSignedIn = true,
                isAuthenticating = false,
                isLoadingWorkspace = true,
                doctorProfile = repository.profileFor(identity),
                snackbarMessage = message
            )
        }
    }

    private fun loadWorkspaceInBackground(token: String, identity: VerifiedDoctorIdentity) {
        viewModelScope.launch {
            runCatching {
                repository.loadWorkspace(token, identity) { initialWorkspace ->
                    applyWorkspace(initialWorkspace, keepLoading = true)
                }
            }
                .onSuccess { applyWorkspace(it) }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingWorkspace = false,
                            snackbarMessage = error.message ?: "Signed in, but clinical workspace data could not be loaded."
                        )
                    }
                }
        }
    }

    private fun applyWorkspace(
        workspace: DoctorWorkspaceData,
        message: String? = null,
        keepLoading: Boolean = false
    ) {
        _uiState.update { old ->
            old.copy(
                isRestoringSession = false,
                isSignedIn = true,
                isAuthenticating = false,
                isLoadingWorkspace = keepLoading,
                doctorProfile = workspace.profile,
                appointments = workspace.appointments,
                notifications = workspace.notifications,
                doctorHistory = workspace.history,
                assistPatientContextId = old.assistPatientContextId.takeIf { id ->
                    workspace.appointments.any { it.id == id }
                } ?: workspace.appointments.firstOrNull()?.id.orEmpty(),
                snackbarMessage = message
            )
        }
    }

    fun refreshWorkspace(message: String? = null, userInitiated: Boolean = false) {
        val token = accessToken ?: return
        _uiState.update { it.copy(isRefreshing = userInitiated) }
        viewModelScope.launch {
            try {
                val workspace = withContext(Dispatchers.IO) { repository.loadWorkspace(token) }
                applyWorkspace(workspace, message)
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(snackbarMessage = error.message ?: "Could not refresh clinical data.")
                }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun refreshAppointmentDetail(appointmentId: String, userInitiated: Boolean = false) {
        val existing = getAppointmentById(appointmentId) ?: return
        val token = accessToken ?: return
        _uiState.update { it.copy(isRefreshingDetail = userInitiated) }
        viewModelScope.launch {
            try {
                val refreshed = withContext(Dispatchers.IO) {
                    repository.loadAppointmentDetail(token, appointmentId, existing)
                }
                _uiState.update { state ->
                    state.copy(appointments = state.appointments.map { current ->
                        if (current.id == appointmentId) refreshed else current
                    })
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(snackbarMessage = error.message ?: "Could not refresh consultation details.")
                }
            } finally {
                _uiState.update { it.copy(isRefreshingDetail = false) }
            }
        }
    }

    fun signOut(sessionStore: RememberedSessionStore) {
        val tokenToRevoke = accessToken
        sessionStore.clear()
        accessToken = null
        doctorId = null
        editedSummaries.clear()
        _uiState.value = VerityScribeUiState(isRestoringSession = false)
        tokenToRevoke?.let { token ->
            viewModelScope.launch {
                runCatching { withContext(Dispatchers.IO) { repository.signOut(token) } }
            }
        }
    }

    fun clearSnackbar() { _uiState.update { it.copy(snackbarMessage = null) } }
    fun selectShift(shift: ShiftPeriod) { _uiState.update { it.copy(selectedShift = shift) } }
    fun selectHomeFilter(filter: HomeFilterTab) { _uiState.update { it.copy(selectedHomeFilter = filter) } }
    fun updateHomeSearchQuery(query: String) { _uiState.update { it.copy(homeSearchQuery = query) } }
    fun updateDirectorySearchQuery(query: String) { _uiState.update { it.copy(directorySearchQuery = query) } }
    fun selectDirectoryFilter(filter: HomeFilterTab) { _uiState.update { it.copy(directoryFilter = filter) } }
    fun selectHistoryRange(range: HistoryRangeFilter) { _uiState.update { it.copy(selectedHistoryRange = range) } }
    fun updateHistorySearchQuery(query: String) { _uiState.update { it.copy(historySearchQuery = query) } }
    fun getAppointmentById(id: String): ClinicalAppointment? = _uiState.value.appointments.find { it.id == id }
    fun getAssistSessionById(sessionId: String): AssistSession? = _uiState.value.assistSessions.find { it.id == sessionId }
    fun getHistoryRecordById(id: String): DoctorHistoryRecord? = _uiState.value.last30DaysHistory.find { it.id == id }

    fun approveDraft(appointmentId: String) = reviewDraft(appointmentId, "approved", null)
    fun rejectDraft(appointmentId: String, reason: String) = reviewDraft(appointmentId, "rejected", reason.trim().ifBlank { "Clinician rejected this draft." })

    private fun reviewDraft(appointmentId: String, decision: String, note: String?) {
        val token = accessToken ?: return showError("Sign in again to review this consultation.")
        val appointment = getAppointmentById(appointmentId) ?: return showError("Appointment is no longer available.")
        val sessionId = appointment.aiSessionId ?: return showError("This appointment has no AI consultation draft to review.")
        val summary = editedSummaries[appointmentId] ?: appointment.aiSummary
        if (decision == "approved" && summary.chiefProblem.isBlank() && summary.symptomCourse.isBlank()) {
            return showError("The consultation summary is empty and cannot be approved.")
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.reviewDecision(token, sessionId, decision, repository.doctorAssistantPayload(summary), note)
                }
                repository.loadWorkspace(token)
            }.onSuccess {
                editedSummaries.remove(appointmentId)
                applyWorkspace(it, if (decision == "approved") "Draft approved and linked to the patient record." else "Draft rejected and recorded for clinician follow-up.")
            }.onFailure { error -> showError(error.message ?: "Review action failed; no changes were confirmed.") }
        }
    }

    fun updateDraftSummary(appointmentId: String, newSummary: StructuredAiSummary) {
        editedSummaries[appointmentId] = newSummary
        _uiState.update { state ->
            state.copy(appointments = state.appointments.map { if (it.id == appointmentId) it.copy(aiSummary = newSummary) else it }, snackbarMessage = "Draft edits are held until a review decision is submitted.")
        }
    }

    fun retryFailedSummary(appointmentId: String) {
        val appointment = getAppointmentById(appointmentId) ?: return
        if (appointment.aiSessionId == null) return showError("No AI summary session is available for retry.")
        val token = accessToken ?: return showError("Sign in again to retry this AI summary.")
        if (_uiState.value.retryingSummaryAppointmentId != null) return
        _uiState.update { it.copy(retryingSummaryAppointmentId = appointmentId, snackbarMessage = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.retrySessionSummary(token, appointment.aiSessionId, appointmentId)
                }
            }.onSuccess { retry ->
                _uiState.update { state ->
                    state.copy(
                        appointments = state.appointments.map { current ->
                            if (current.id == appointmentId) current.copy(
                                aiSummary = retry.summary,
                                status = retry.appointmentStatus,
                                rawBackendStatus = "awaiting_doctor_review"
                            ) else current
                        },
                        retryingSummaryAppointmentId = null,
                        snackbarMessage = if (retry.summary.generationFailed) {
                            retry.summary.failureReason ?: "Summary generation failed. The transcript remains available."
                        } else {
                            "AI summary regenerated. Verify it against the preserved transcript before review."
                        }
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        retryingSummaryAppointmentId = null,
                        snackbarMessage = error.message ?: "Summary retry failed. The transcript remains available."
                    )
                }
            }
        }
    }

    fun saveConsultationDocumentation(
        appointmentId: String,
        clinicalNote: String,
        diagnoses: List<String>,
        prescriptions: List<PrescriptionItem>,
        markCompleted: Boolean
    ) {
        val token = accessToken ?: return showError("Sign in again to save consultation documentation.")
        val appointment = getAppointmentById(appointmentId) ?: return showError("Appointment is no longer available.")
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    if (markCompleted) {
                        repository.completeAppointment(token, appointmentId, clinicalNote, diagnoses, prescriptions)
                    } else {
                        var consultationId = appointment.consultationId
                        if (consultationId == null) {
                            consultationId = repository.addConsultation(token, appointment, clinicalNote, diagnoses.firstOrNull())
                        } else if (clinicalNote.isNotBlank()) {
                            repository.addNote(token, consultationId, clinicalNote)
                        }
                        diagnoses.drop(if (appointment.consultationId == null) 1 else 0)
                            .filter(String::isNotBlank).forEach { repository.addDiagnosis(token, consultationId, it) }
                    }
                }
                repository.loadWorkspace(token)
            }.onSuccess {
                applyWorkspace(it, if (markCompleted) {
                    "Consultation completed. Diagnosis, notes, and medication entries were saved to the patient record."
                } else {
                    "Clinical documentation saved."
                })
            }.onFailure { error -> showError(error.message ?: "Documentation could not be saved.") }
        }
    }

    fun markAllNotificationsRead() {
        val token = accessToken ?: return showError("Sign in again to update notifications.")
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.markAllNotificationsRead(token) }; repository.loadWorkspace(token) }
                .onSuccess { applyWorkspace(it, "Notifications marked as read.") }
                .onFailure { showError(it.message ?: "Notifications could not be updated.") }
        }
    }

    fun markNotificationRead(notificationId: String) {
        val token = accessToken ?: return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.markNotificationRead(token, notificationId) }; repository.loadWorkspace(token) }
                .onSuccess { applyWorkspace(it) }
                .onFailure { showError(it.message ?: "Notification could not be updated.") }
        }
    }

    fun selectAssistCategory(category: AssistSessionCategory) { _uiState.update { it.copy(selectedAssistCategory = category) } }

    fun startHistoryAiConversation(historyRecordId: String): String {
        val record = getHistoryRecordById(historyRecordId) ?: return showErrorAndReturn("Historical encounter is not available.")
        val appointmentId = record.linkedAppointmentId ?: return showErrorAndReturn("This history record is not linked to an accessible appointment.")
        return createAssistSession(appointmentId, record.patientName, record.diagnosisSummary, historyRecordId, true, null)
    }

    fun startNewAssistSession(appointmentId: String, initialPrompt: String? = null, attachmentReportTitle: String? = null): String {
        val patient = getAppointmentById(appointmentId) ?: return showErrorAndReturn("Choose an appointment from this doctor's assigned patients first.")
        return createAssistSession(patient.id, patient.patientName, patient.chiefComplaint, null, false, initialPrompt to attachmentReportTitle)
    }

    private fun createAssistSession(
        appointmentId: String,
        patientName: String,
        titleDetail: String,
        historyId: String?,
        historyOnly: Boolean,
        initial: Pair<String?, String?>?
    ): String {
        val sessionId = "assist-${UUID.randomUUID()}"
        val prompt = initial?.first?.trim().orEmpty()
        val attachment = initial?.second
        val messages = if (prompt.isBlank() && attachment == null) {
            listOf(AssistMessage("welcome-$sessionId", false, "Clinical assistant ready for $patientName. Ask a question about the assigned patient's documented chart.", "Now"))
        } else {
            listOf(AssistMessage("user-$sessionId", true, prompt.ifBlank { "Review report: $attachment" }, "Now", attachmentName = attachment))
        }
        val session = AssistSession(
            id = sessionId, title = "$patientName — $titleDetail", subtitle = "Clinical chart assistant · not a saved conversation", 
            category = if (historyOnly) AssistSessionCategory.SUMMARIES else AssistSessionCategory.INTAKE_DRAFTS,
            linkedAppointmentId = appointmentId, updatedAt = "Now", messages = messages,
            linkedHistoryRecordId = historyId, isHistoryOnly = historyOnly
        )
        _uiState.update { it.copy(assistPatientContextId = appointmentId, assistSessions = listOf(session) + it.assistSessions, isAssistTyping = prompt.isNotBlank() || attachment != null) }
        if (prompt.isNotBlank() || attachment != null) {
            val question = prompt.ifBlank { "Summarize the documented chart and the selected report '$attachment'; distinguish unknown from negative." }
            requestAssistantReply(sessionId, appointmentId, question, emptyList())
        }
        return sessionId
    }

    fun sendSessionMessage(sessionId: String, query: String, attachmentReportTitle: String? = null) {
        val session = getAssistSessionById(sessionId) ?: return
        val clean = query.trim()
        if (clean.isBlank() && attachmentReportTitle == null) return
        val question = clean.ifBlank { "Review the documented chart and report '$attachmentReportTitle'." }
        val history = session.messages.mapNotNull { message ->
            if (message.text.isBlank() || message.text.startsWith("Clinical assistant ready")) null
            else (if (message.isFromDoctor) "user" else "assistant") to message.text
        }.takeLast(12)
        val patientMessage = AssistMessage("user-${UUID.randomUUID()}", true, question, "Now", attachmentName = attachmentReportTitle)
        _uiState.update { state -> state.copy(
            assistSessions = state.assistSessions.map { if (it.id == sessionId) it.copy(subtitle = question, updatedAt = "Now", messages = it.messages + patientMessage) else it },
            isAssistTyping = true
        ) }
        requestAssistantReply(sessionId, session.linkedAppointmentId, question, history)
    }

    fun sendSessionVoiceNote(sessionId: String) {
        showError("Voice-note capture is not connected in this app build. Use the text field for a chart question.")
    }

    private fun requestAssistantReply(sessionId: String, appointmentId: String, question: String, history: List<Pair<String, String>>) {
        val token = accessToken
        if (token == null) {
            showError("Sign in again to use the clinical assistant.")
            _uiState.update { it.copy(isAssistTyping = false) }
            return
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repository.assistantAnswer(token, appointmentId, question, history) }
            }.onSuccess { answer ->
                appendAssistantMessage(sessionId, answer)
            }.onFailure { error ->
                _uiState.update { it.copy(isAssistTyping = false, snackbarMessage = error.message ?: "Clinical assistant is temporarily unavailable.") }
            }
        }
    }

    private fun appendAssistantMessage(sessionId: String, answer: String) {
        val reply = AssistMessage("assistant-${UUID.randomUUID()}", false, answer, "Now")
        _uiState.update { state -> state.copy(
            assistSessions = state.assistSessions.map { if (it.id == sessionId) it.copy(subtitle = answer, updatedAt = "Now", messages = it.messages + reply) else it },
            assistMessages = state.assistMessages + reply,
            isAssistTyping = false
        ) }
    }

    fun endAssistSession(sessionId: String) {
        val session = getAssistSessionById(sessionId)
        _uiState.update { it.copy(isAssistTyping = false, snackbarMessage = if (session?.isHistoryOnly == true) "Historical chart conversation closed. The chat transcript is held only in this app session." else "Assistant conversation closed. Chat messages are held only in this app session.") }
    }

    fun selectAssistPatientContext(appointmentId: String) {
        if (getAppointmentById(appointmentId) == null) return showError("That appointment is not in the assigned patient list.")
        _uiState.update { it.copy(assistPatientContextId = appointmentId, snackbarMessage = "Patient context selected.") }
    }

    fun sendAssistQuery(query: String, attachmentReportTitle: String? = null) {
        val clean = query.trim()
        if (clean.isBlank() && attachmentReportTitle == null) return
        val appointmentId = _uiState.value.assistPatientContextId
        val patient = getAppointmentById(appointmentId) ?: return showError("Select an assigned appointment before asking about a patient chart.")
        val question = clean.ifBlank { "Review report '$attachmentReportTitle' in the documented chart." }
        val history = _uiState.value.assistMessages.map { (if (it.isFromDoctor) "user" else "assistant") to it.text }.takeLast(12)
        val userMessage = AssistMessage("doctor-${UUID.randomUUID()}", true, question, "Now", attachmentName = attachmentReportTitle)
        _uiState.update { it.copy(assistMessages = it.assistMessages + userMessage, isAssistTyping = true) }
        requestStandaloneAssistantReply(patient.id, question, history)
    }

    private fun requestStandaloneAssistantReply(appointmentId: String, question: String, history: List<Pair<String, String>>) {
        val token = accessToken ?: return showError("Sign in again to use the clinical assistant.")
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.assistantAnswer(token, appointmentId, question, history) } }
                .onSuccess { answer -> appendAssistantMessageToGeneralChat(answer) }
                .onFailure { error -> _uiState.update { it.copy(isAssistTyping = false, snackbarMessage = error.message ?: "Clinical assistant is temporarily unavailable.") } }
        }
    }

    private fun appendAssistantMessageToGeneralChat(answer: String) {
        val reply = AssistMessage("assistant-${UUID.randomUUID()}", false, answer, "Now")
        _uiState.update { it.copy(assistMessages = it.assistMessages + reply, isAssistTyping = false) }
    }

    fun toggleAvailability() {
        val token = accessToken ?: return showError("Sign in again to update availability.")
        val id = doctorId ?: return showError("Doctor profile is not available.")
        val next = !_uiState.value.doctorProfile.isAvailable
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.updateAvailability(token, id, next) }; repository.loadWorkspace(token) }
                .onSuccess { applyWorkspace(it, "Availability updated.") }
                .onFailure { showError(it.message ?: "Availability could not be updated.") }
        }
    }

    fun updateNotificationPreferences(prefs: NotificationPreferences) {
        _uiState.update { it.copy(notificationPreferences = prefs, snackbarMessage = "Preference changes are local only; this backend has no notification-preferences update endpoint.") }
    }

    fun updatePrivacyPreferences(prefs: PrivacyPreferences) {
        _uiState.update { it.copy(privacyPreferences = prefs, snackbarMessage = "Privacy preferences updated on this device only.") }
    }

    private fun showError(message: String) { _uiState.update { it.copy(snackbarMessage = message) } }
    private fun showErrorAndReturn(message: String): String { showError(message); return "" }
}
