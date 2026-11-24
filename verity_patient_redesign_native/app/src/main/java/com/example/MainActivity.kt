package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.model.Appointment
import com.example.model.ConsultationType
import com.example.model.RecordsSectionTab
import com.example.model.VerityTab
import com.example.ui.components.AppointmentDetailsSheet
import com.example.ui.components.JoinCallSheet
import com.example.ui.components.RescheduleAppointmentDialog
import com.example.ui.components.VerityFloatingBottomBar
import com.example.ui.components.VerityGlassBackdrop
import com.example.ui.components.VoiceConsultationSheet
import com.example.ui.screens.AiScreen
import com.example.ui.screens.AlarmsScreen
import com.example.ui.screens.AppointmentAiChatScreen
import com.example.ui.screens.AppointmentAiVirtualScreen
import com.example.ui.screens.AppointmentInPersonScreen
import com.example.ui.screens.BookAppointmentScreen
import com.example.ui.screens.ConnectedDevicesScreen
import com.example.ui.screens.EmergencyContactsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.InsuranceDashboardScreen
import com.example.ui.screens.LinkedHospitalsScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.MedicalHistoryScreen
import com.example.ui.screens.MedicineDetailScreen
import com.example.ui.screens.MedicinesScreen
import com.example.ui.screens.PrivacySecurityScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.RecordsScreen
import com.example.ui.theme.VerityTheme
import com.example.viewmodel.VerityViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VerityAppRoot()
        }
    }
}

private const val ROUTE_MEDICINE_DETAIL = "medicine_detail/{medicineId}"
private const val ROUTE_BOOK_APPOINTMENT = "book_appointment"
private const val ROUTE_APPOINTMENT_SESSION = "appointment_session/{appointmentId}"
private const val ROUTE_PROFILE_MEDICAL_HISTORY = "profile/medical_history"
private const val ROUTE_PROFILE_EMERGENCY_CONTACTS = "profile/emergency_contacts"
private const val ROUTE_PROFILE_INSURANCE = "profile/insurance"
private const val ROUTE_PROFILE_LINKED_HOSPITALS = "profile/linked_hospitals"
private const val ROUTE_PROFILE_CONNECTED_DEVICES = "profile/connected_devices"
private const val ROUTE_PROFILE_PRIVACY_SECURITY = "profile/privacy_security"

@Composable
fun VerityAppRoot(
    viewModel: VerityViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.statusBannerMessage) {
        val msg = uiState.statusBannerMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    VerityTheme(darkTheme = uiState.darkMode) {
        when {
            !uiState.authInitialized -> {
                VerityGlassBackdrop(isObsidianMode = uiState.darkMode) {}
            }

            !uiState.isLoggedIn -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    LoginScreen(
                        isObsidianMode = uiState.darkMode,
                        authError = uiState.authErrorMessage,
                        onClearError = viewModel::clearAuthError,
                        onLogin = viewModel::loginWithEmail,
                        onRequestPasswordReset = viewModel::requestPasswordReset
                    )
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp)
                    )
                }
            }

            else -> {
                VerityAuthenticatedShell(
                    viewModel = viewModel,
                    snackbarHostState = snackbarHostState
                )
            }
        }
    }
}

@Composable
private fun VerityAuthenticatedShell(
    viewModel: VerityViewModel,
    snackbarHostState: SnackbarHostState
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val context = LocalContext.current

    var showVoiceSheet by remember { mutableStateOf(false) }
    var detailAppointment by remember { mutableStateOf<Appointment?>(null) }
    var rescheduleAppointment by remember { mutableStateOf<Appointment?>(null) }
    var callAppointment by remember { mutableStateOf<Appointment?>(null) }
    var showNotifications by remember { mutableStateOf(false) }

    val currentTab = VerityTab.entries.find { it.route == currentRoute }
    val showBottomBar = currentTab != null &&
        currentTab != VerityTab.PROFILE &&
        !(currentTab == VerityTab.AI && uiState.showAiChatThread)

    val navigateToTab: (VerityTab) -> Unit = { tab ->
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = VerityTab.HOME.route,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            modifier = Modifier.fillMaxSize()
        ) {
            composable(VerityTab.HOME.route) {
                HomeScreen(
                    uiState = uiState,
                    aiSummaryText = viewModel.getAiHealthSummaryText(),
                    onOpenProfile = {
                        navController.navigate(VerityTab.PROFILE.route) {
                            launchSingleTop = true
                        }
                    },
                    onOpenNotifications = { showNotifications = true },
                    onAskLumi = { prompt ->
                        navigateToTab(VerityTab.AI)
                        viewModel.sendAiPrompt(prompt)
                    },
                    onBookAppointment = { navController.navigate(ROUTE_BOOK_APPOINTMENT) },
                    onSelectAppointmentIndex = viewModel::setAppointmentIndex,
                    onViewAppointmentDetails = { detailAppointment = it },
                    onRescheduleAppointment = { rescheduleAppointment = it },
                    onJoinAppointmentCall = { appt ->
                        if (appt.consultationType == ConsultationType.VIDEO.label) {
                            callAppointment = appt
                        } else {
                            navController.navigate("appointment_session/${appt.id}")
                        }
                    },
                    onSyncHealthSnapshot = { viewModel.syncGoogleFit(showBanner = true) },
                    onAutoRefreshHealthSnapshot = { viewModel.syncGoogleFit(showBanner = false) },
                    onHealthPermissionsResult = viewModel::onHealthPermissionsResult
                )
            }

            composable(ROUTE_BOOK_APPOINTMENT) {
                BookAppointmentScreen(
                    isDark = uiState.darkMode,
                    onBack = { navController.popBackStack() },
                    hospitals = uiState.hospitals,
                    doctors = uiState.doctorsDirectory,
                    onConfirmBooking = { doctorName, specialty, hospital, dateLabel, timeLabel, consultationType, reason, notes, avatarRes ->
                        viewModel.bookAppointment(
                            doctorName = doctorName,
                            specialty = specialty,
                            hospital = hospital,
                            dateLabel = dateLabel,
                            timeLabel = timeLabel,
                            consultationType = consultationType,
                            reason = reason,
                            notes = notes,
                            avatarRes = avatarRes,
                            onComplete = { succeeded -> if (succeeded) navController.popBackStack() }
                        )
                    }
                )
            }

            composable(VerityTab.AI.route) {
                AiScreen(
                    uiState = uiState,
                    onSelectCategory = viewModel::selectAiCategory,
                    onOpenThread = viewModel::openAiThread,
                    onStartNewThread = viewModel::startNewAiThread,
                    onDeleteThread = viewModel::markAiThreadDeleted,
                    onUndoDeleteThread = viewModel::undoDeleteAiThread,
                    onCloseThread = viewModel::closeAiThread,
                    onToggleSavedThread = viewModel::toggleSavedActiveThread,
                    onDraftChange = viewModel::updateAiDraft,
                    onSendPrompt = viewModel::sendAiPrompt,
                    onAttachExistingReport = viewModel::attachExistingReportToAiChat,
                    onUploadAndAttachReport = viewModel::uploadAndAttachReportToAiChat,
                    onClearChat = viewModel::clearChat,
                    onRemoveAttachment = viewModel::removeAiChatAttachment,
                    onOpenVoice = { showVoiceSheet = true }
                )
            }

            composable(VerityTab.MEDICINES.route) {
                MedicinesScreen(
                    uiState = uiState,
                    onSearchQueryChange = viewModel::updateMedicineSearch,
                    onOpenMedicineDetail = { medId ->
                        navController.navigate("medicine_detail/$medId")
                    }
                )
            }

            composable(VerityTab.ALARMS.route) {
                AlarmsScreen(
                    uiState = uiState,
                    onToggleDose = viewModel::toggleScheduleDose,
                    onOpenMedicineDetail = { medId ->
                        navController.navigate("medicine_detail/$medId")
                    }
                )
            }

            composable(VerityTab.RECORDS.route) {
                RecordsScreen(
                    uiState = uiState,
                    onSelectRecordsTab = viewModel::selectRecordsTab,
                    onToggleExpanded = viewModel::toggleConsultationExpanded,
                    onTogglePlayback = viewModel::toggleRecordingPlayback,
                    onSeekPlayback = viewModel::seekRecording,
                    onDownloadPdf = viewModel::downloadPrescriptionPdf,
                    onSelectReportCategory = viewModel::selectReportCategory,
                    onToggleReportExpanded = viewModel::toggleReportExpanded,
                    onShareReport = viewModel::shareReportWithDoctor,
                    onDownloadReport = viewModel::downloadReport,
                    onUploadReport = viewModel::uploadMedicalReport
                )
            }

            composable(VerityTab.PROFILE.route) {
                ProfileScreen(
                    uiState = uiState,
                    onBack = { navController.popBackStack() },
                    onUploadPhoto = viewModel::updateProfilePhoto,
                    onRemovePhoto = viewModel::removeProfilePhoto,
                    onToggleDarkMode = viewModel::setDarkMode,
                    onOpenMedicalHistory = {
                        navController.navigate(ROUTE_PROFILE_MEDICAL_HISTORY) { launchSingleTop = true }
                    },
                    onOpenEmergencyContacts = {
                        navController.navigate(ROUTE_PROFILE_EMERGENCY_CONTACTS) { launchSingleTop = true }
                    },
                    onOpenInsurance = {
                        navController.navigate(ROUTE_PROFILE_INSURANCE) { launchSingleTop = true }
                    },
                    onOpenLinkedHospitals = {
                        navController.navigate(ROUTE_PROFILE_LINKED_HOSPITALS) { launchSingleTop = true }
                    },
                    onOpenConnectedDevices = {
                        navController.navigate(ROUTE_PROFILE_CONNECTED_DEVICES) { launchSingleTop = true }
                    },
                    onOpenPrivacySecurity = {
                        navController.navigate(ROUTE_PROFILE_PRIVACY_SECURITY) { launchSingleTop = true }
                    },
                    onLogout = viewModel::logout
                )
            }

            composable(ROUTE_PROFILE_MEDICAL_HISTORY) {
                MedicalHistoryScreen(
                    uiState = uiState,
                    onBack = { navController.popBackStack() },
                    onAddNote = viewModel::addMedicalHistoryNote,
                    onUploadReport = viewModel::uploadMedicalReport,
                    onOpenReportsTab = {
                        viewModel.selectRecordsTab(RecordsSectionTab.REPORTS)
                        navController.popBackStack(VerityTab.HOME.route, inclusive = false)
                        navigateToTab(VerityTab.RECORDS)
                    }
                )
            }

            composable(ROUTE_PROFILE_EMERGENCY_CONTACTS) {
                EmergencyContactsScreen(
                    uiState = uiState,
                    onBack = { navController.popBackStack() },
                    onCallContact = { phone ->
                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                        runCatching { context.startActivity(dialIntent) }
                            .onFailure { viewModel.showStatusMessage("Dial: $phone") }
                    }
                )
            }

            composable(ROUTE_PROFILE_INSURANCE) {
                InsuranceDashboardScreen(
                    uiState = uiState,
                    onBack = { navController.popBackStack() },
                    onCallSupport = { phone ->
                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                        runCatching { context.startActivity(dialIntent) }
                            .onFailure { viewModel.showStatusMessage("Insurance Support: $phone") }
                    }
                )
            }

            composable(ROUTE_PROFILE_LINKED_HOSPITALS) {
                LinkedHospitalsScreen(
                    uiState = uiState,
                    onBack = { navController.popBackStack() },
                    onBookVisit = { navController.navigate(ROUTE_BOOK_APPOINTMENT) }
                )
            }

            composable(ROUTE_PROFILE_CONNECTED_DEVICES) {
                ConnectedDevicesScreen(
                    uiState = uiState,
                    onBack = { navController.popBackStack() },
                    onSyncGoogleFit = { viewModel.syncGoogleFit(showBanner = true) },
                    onHealthPermissionsResult = viewModel::onHealthPermissionsResult,
                    onToggleAutoSync = viewModel::toggleGoogleFitAutoSync,
                    onOpenGoogleFitApp = viewModel::openGoogleFitApp,
                    onOpenHealthConnectSettings = viewModel::openHealthConnectSettings
                )
            }

            composable(ROUTE_PROFILE_PRIVACY_SECURITY) {
                PrivacySecurityScreen(
                    uiState = uiState,
                    onBack = { navController.popBackStack() },
                    onOpenHealthConnectSettings = viewModel::openHealthConnectSettings
                )
            }

            composable(
                route = ROUTE_MEDICINE_DETAIL,
                arguments = listOf(navArgument("medicineId") { type = NavType.StringType })
            ) { entry ->
                val medId = entry.arguments?.getString("medicineId").orEmpty()
                val medicine = viewModel.findMedicine(medId) ?: uiState.medicines.first()
                MedicineDetailScreen(
                    medicine = medicine,
                    isDark = uiState.darkMode,
                    onBack = { navController.popBackStack() },
                    onSelectPharmacy = { price ->
                        viewModel.showStatusMessage(
                            "Selected ${price.vendorName} (₹${price.priceRupees} • ${price.etaInfo})"
                        )
                    },
                    onCallStore = { store ->
                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${store.phoneNumber}"))
                        runCatching { context.startActivity(dialIntent) }
                            .onFailure {
                                viewModel.showStatusMessage("${store.name}: ${store.phoneNumber}")
                            }
                    }
                )
            }

            composable(
                route = ROUTE_APPOINTMENT_SESSION,
                arguments = listOf(navArgument("appointmentId") { type = NavType.StringType })
            ) { entry ->
                val apptId = entry.arguments?.getString("appointmentId").orEmpty()
                val appt = uiState.appointments.find { it.id == apptId }
                if (appt == null) {
                    LaunchedEffect(apptId) { navController.popBackStack() }
                } else when (appt.consultationType) {
                    ConsultationType.AI_CHAT.label -> {
                        AppointmentAiChatScreen(
                            appointment = appt,
                            patientFirstName = uiState.profile.firstName,
                            onBack = { navController.popBackStack() },
                            onStartConsultation = { consentAccepted, result ->
                                viewModel.prepareAppointmentAiChat(appt, consentAccepted, result)
                            },
                            onSendMessage = { text, requestKey, result -> viewModel.sendAppointmentAiMessage(appt, text, requestKey, result) },
                            onEndConsultation = { result -> viewModel.endAppointmentAiConsultation(appt, result) },
                            onShareSummaryWithDoctor = {
                                viewModel.showStatusMessage("AI Chat summary saved for ${appt.doctorName} to review")
                            }
                        )
                    }
                    ConsultationType.AI_VIRTUAL.label -> {
                        AppointmentAiVirtualScreen(
                            appointment = appt,
                            patientFirstName = uiState.profile.firstName,
                            onBack = { navController.popBackStack() },
                            onStartConsultation = { consent, voiceConsent ->
                                viewModel.startAppointmentAiVirtual(appt, consent, voiceConsent)
                            },
                            onPersistTurn = { sessionId, requestKey, input, output ->
                                viewModel.persistAppointmentAiVirtualTurn(sessionId, requestKey, input, output)
                            },
                            onEndConsultation = { sessionId ->
                                viewModel.endAppointmentAiVirtual(sessionId)
                            },
                            onShareSummaryWithDoctor = {
                                viewModel.showStatusMessage("AI Virtual summary saved for ${appt.doctorName} to review")
                            }
                        )
                    }
                    else -> {
                        AppointmentInPersonScreen(
                            appointment = appt,
                            isDark = uiState.darkMode,
                            onBack = { navController.popBackStack() },
                            onUploadRecording = { filePath, callback ->
                                viewModel.uploadAppointmentVoiceDraft(appt.id, filePath, callback)
                            },
                            onSaveTranscriptCorrection = { transcriptId, text, callback ->
                                viewModel.correctAppointmentVoiceTranscript(transcriptId, text, callback)
                            }
                        )
                    }
                }
            }
        }

        if (showBottomBar) {
            VerityFloatingBottomBar(
                currentTab = currentTab ?: VerityTab.HOME,
                onSelectTab = navigateToTab,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (showBottomBar) 96.dp else 16.dp)
        )
    }

    if (showVoiceSheet) {
        VoiceConsultationSheet(
            doctorName = uiState.appointments.firstOrNull()?.doctorName ?: "your care team",
            onDismiss = { showVoiceSheet = false },
            onAskAiWithTranscript = { prompt ->
                showVoiceSheet = false
                navigateToTab(VerityTab.AI)
                viewModel.sendAiPrompt(prompt)
            }
        )
    }

    detailAppointment?.let { appt ->
        AppointmentDetailsSheet(
            appointment = appt,
            onDismiss = { detailAppointment = null },
            onReschedule = {
                detailAppointment = null
                rescheduleAppointment = appt
            },
            onJoinCall = {
                detailAppointment = null
                if (appt.consultationType == ConsultationType.VIDEO.label) {
                    callAppointment = appt
                } else {
                    navController.navigate("appointment_session/${appt.id}")
                }
            }
        )
    }

    rescheduleAppointment?.let { appt ->
        RescheduleAppointmentDialog(
            appointment = appt,
            onDismiss = { rescheduleAppointment = null },
            onConfirmSlot = { date, time ->
                viewModel.rescheduleAppointment(appt.id, date, time)
                rescheduleAppointment = null
            }
        )
    }

    callAppointment?.let { appt ->
        JoinCallSheet(
            appointment = appt,
            onDismiss = { callAppointment = null }
        )
    }

    if (showNotifications) {
        AlertDialog(
            onDismissRequest = { showNotifications = false },
            title = {
                Column {
                    Text("Notifications", fontWeight = FontWeight.SemiBold)
                    if (uiState.notifications.any { it.isUnread }) {
                        TextButton(onClick = viewModel::markNotificationsRead) { Text("Mark all read") }
                    }
                }
            },
            text = {
                if (uiState.notifications.isEmpty()) {
                    Text("No notifications are available.")
                } else {
                    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                        uiState.notifications.take(30).forEachIndexed { index, item ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = item.isUnread) { viewModel.markNotificationRead(item.id) }
                                    .padding(vertical = 10.dp, horizontal = 6.dp)
                            ) {
                                Text(item.title, fontWeight = if (item.isUnread) FontWeight.SemiBold else FontWeight.Normal)
                                Text(item.body, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                                if (item.timeAgo.isNotBlank()) Text(item.timeAgo.take(16), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            }
                            if (index != uiState.notifications.take(30).lastIndex) HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showNotifications = false }) { Text("Close") } }
        )
    }
}
