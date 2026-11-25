package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.ActiveConsultationScreen
import com.example.ui.screens.AiAssistScreen
import com.example.ui.screens.AiAssistSessionScreen
import com.example.ui.screens.AiReviewWorkspaceScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.PatientsDirectoryScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SignInScreen
import com.example.data.RememberedSessionStore
import com.example.ui.theme.ClinicalSurface
import com.example.ui.theme.GlacialMistBottom
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.OceanTealContainer
import com.example.ui.theme.OceanTealDark
import com.example.ui.theme.OceanTealPrimary
import com.example.ui.theme.VerityScribeTheme
import com.example.viewmodel.VerityScribeUiState
import com.example.viewmodel.VerityScribeViewModel

object Routes {
    const val RESTORING = "restoring_session"
    const val SIGN_IN = "sign_in"
    const val MAIN = "main"
    const val REVIEW = "review/{appointmentId}"
    const val CONSULTATION = "consultation/{appointmentId}"
    const val ASSIST_SESSION = "assist_session/{sessionId}"

    fun reviewRoute(appointmentId: String) = "review/$appointmentId"
    fun consultationRoute(appointmentId: String) = "consultation/$appointmentId"
    fun assistSessionRoute(sessionId: String) = "assist_session/$sessionId"
}

enum class MainTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME(R.string.nav_home, Icons.Rounded.GridView, Icons.Outlined.GridView, "nav_tab_home"),
    PATIENTS(R.string.nav_patients, Icons.Rounded.Groups, Icons.Outlined.Groups, "nav_tab_patients"),
    ASSIST(R.string.nav_assist, Icons.Rounded.AutoAwesome, Icons.Outlined.AutoAwesome, "nav_tab_assist"),
    PROFILE(R.string.nav_profile, Icons.Rounded.Person, Icons.Outlined.Person, "nav_tab_profile")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            VerityScribeTheme {
                VerityScribeApp(
                    onUpdateSystemBars = { isSignedIn ->
                        if (isSignedIn) {
                            enableEdgeToEdge(
                                statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                                navigationBarStyle = SystemBarStyle.light(
                                    GlacialMistBottom.toArgb(),
                                    GlacialMistBottom.toArgb()
                                )
                            )
                        } else {
                            enableEdgeToEdge(
                                statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                                navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun VerityScribeApp(
    viewModel: VerityScribeViewModel = viewModel(),
    onUpdateSystemBars: (Boolean) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val appContext = LocalContext.current.applicationContext
    val rememberedSessionStore = remember(appContext) { RememberedSessionStore(appContext) }

    DisposableEffect(lifecycleOwner, uiState.isSignedIn) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && uiState.isSignedIn) {
                viewModel.refreshWorkspace()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel, rememberedSessionStore) {
        viewModel.restoreRememberedSession(rememberedSessionStore)
    }

    LaunchedEffect(uiState.isRestoringSession, uiState.isSignedIn) {
        if (uiState.isRestoringSession) return@LaunchedEffect
        val destination = if (uiState.isSignedIn) Routes.MAIN else Routes.SIGN_IN
        if (navController.currentDestination?.route != destination) {
            navController.navigate(destination) {
                popUpTo(Routes.RESTORING) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(uiState.isSignedIn) {
        onUpdateSystemBars(uiState.isSignedIn)
    }

    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearSnackbar()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GlacialMistBottom)
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.RESTORING
        ) {
            composable(Routes.RESTORING) {
                SessionRestoreScreen()
            }
            composable(Routes.SIGN_IN) {
                SignInScreen(
                    onSignIn = { email, password, rememberMe ->
                        viewModel.signInWithCredentials(email, password, rememberMe, rememberedSessionStore) {
                            navController.navigate(Routes.MAIN) {
                                popUpTo(Routes.SIGN_IN) { inclusive = true }
                            }
                        }
                    }
                )
            }

            composable(Routes.MAIN) {
                MainWorkspaceScaffold(
                    uiState = uiState,
                    viewModel = viewModel,
                    onOpenAppointment = { appointmentId ->
                        navController.navigate(Routes.reviewRoute(appointmentId))
                    },
                    onOpenConsultation = { appointmentId ->
                        navController.navigate(Routes.consultationRoute(appointmentId))
                    },
                    onOpenAssistSession = { sessionId ->
                        navController.navigate(Routes.assistSessionRoute(sessionId))
                    },
                    onSignOut = {
                        viewModel.signOut(rememberedSessionStore)
                        navController.navigate(Routes.SIGN_IN) {
                            popUpTo(Routes.MAIN) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route = Routes.REVIEW,
                arguments = listOf(navArgument("appointmentId") { type = NavType.StringType })
            ) { backStackEntry ->
                val appointmentId = backStackEntry.arguments?.getString("appointmentId").orEmpty()
                val appointment = uiState.appointments.find { it.id == appointmentId }
                if (appointment != null) {
                    LaunchedEffect(appointment.id) {
                        viewModel.refreshAppointmentDetail(appointment.id)
                    }
                    AiReviewWorkspaceScreen(
                        appointment = appointment,
                        isRetryingSummary = uiState.retryingSummaryAppointmentId == appointment.id,
                        isRefreshing = uiState.isRefreshingDetail,
                        onRefresh = { viewModel.refreshAppointmentDetail(appointment.id, userInitiated = true) },
                        onBack = { navController.popBackStack() },
                        onApproveDraft = { viewModel.approveDraft(appointment.id) },
                        onRejectDraft = { reason -> viewModel.rejectDraft(appointment.id, reason) },
                        onUpdateDraftSummary = { updated ->
                            viewModel.updateDraftSummary(appointment.id, updated)
                        },
                        onRetrySummary = { viewModel.retryFailedSummary(appointment.id) },
                        onOpenConsultation = {
                            navController.navigate(Routes.consultationRoute(appointment.id))
                        }
                    )
                }
            }

            composable(
                route = Routes.CONSULTATION,
                arguments = listOf(navArgument("appointmentId") { type = NavType.StringType })
            ) { backStackEntry ->
                val appointmentId = backStackEntry.arguments?.getString("appointmentId").orEmpty()
                val appointment = uiState.appointments.find { it.id == appointmentId }
                if (appointment != null) {
                    ActiveConsultationScreen(
                        appointment = appointment,
                        requireConsentForSimilarCases = uiState.privacyPreferences.requireConsentForSimilarCases,
                        onBack = { navController.popBackStack() },
                        onSaveConsultation = { note, dx, rx, markCompleted ->
                            viewModel.saveConsultationDocumentation(
                                appointmentId = appointment.id,
                                clinicalNote = note,
                                diagnoses = dx,
                                prescriptions = rx,
                                markCompleted = markCompleted
                            )
                        }
                    )
                }
            }

            composable(
                route = Routes.ASSIST_SESSION,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getString("sessionId").orEmpty()
                val session = uiState.assistSessions.find { it.id == sessionId }
                if (session != null) {
                    val historyRecord = session.linkedHistoryRecordId?.let { histId ->
                        uiState.last30DaysHistory.find { it.id == histId }
                    }
                    val patient = if (session.linkedAppointmentId.isNotBlank()) {
                        uiState.appointments.find { it.id == session.linkedAppointmentId }
                    } else if (historyRecord == null) {
                        uiState.appointments.first()
                    } else {
                        null
                    }
                    AiAssistSessionScreen(
                        session = session,
                        patient = patient,
                        historyRecord = historyRecord,
                        isGenerating = uiState.isAssistTyping,
                        onBack = { navController.popBackStack() },
                        onEndSession = {
                            viewModel.endAssistSession(session.id)
                            navController.popBackStack()
                        },
                        onOpenReviewSummary = {
                            patient?.let { navController.navigate(Routes.reviewRoute(it.id)) }
                        },
                        onSendMessage = { query, attachment ->
                            viewModel.sendSessionMessage(session.id, query, attachment)
                        },
                        onSendVoiceNote = {
                            viewModel.sendSessionVoiceNote(session.id)
                        }
                    )
                }
            }
        }

        if (uiState.isSignedIn && uiState.isLoadingWorkspace) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 10.dp, start = 16.dp, end = 16.dp)
                    .widthIn(max = 420.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                shadowElevation = 5.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(17.dp),
                        strokeWidth = 2.dp,
                        color = OceanTealPrimary
                    )
                    Text(
                        text = "Loading clinical workspace…",
                        color = InkPrimary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 92.dp, start = 16.dp, end = 16.dp)
        ) { data ->
            Snackbar(
                shape = RoundedCornerShape(12.dp),
                containerColor = OceanTealDark,
                contentColor = Color.White
            ) {
                Text(text = data.visuals.message, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SessionRestoreScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GlacialMistBottom),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = OceanTealPrimary)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Checking saved sign-in…", color = InkMuted)
        }
    }
}

@Composable
private fun MainWorkspaceScaffold(
    uiState: VerityScribeUiState,
    viewModel: VerityScribeViewModel,
    onOpenAppointment: (String) -> Unit,
    onOpenConsultation: (String) -> Unit,
    onOpenAssistSession: (String) -> Unit,
    onSignOut: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.HOME) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        if (useRail) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(GlacialMistBottom)
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                NavigationRail(
                    containerColor = ClinicalSurface,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    MainTab.entries.forEach { tab ->
                        val selected = selectedTab == tab
                        NavigationRailItem(
                            selected = selected,
                            onClick = { selectedTab = tab },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = stringResource(tab.labelRes)
                                )
                            },
                            label = { Text(stringResource(tab.labelRes)) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = OceanTealPrimary,
                                selectedTextColor = OceanTealDark,
                                indicatorColor = OceanTealContainer,
                                unselectedIconColor = InkMuted,
                                unselectedTextColor = InkMuted
                            ),
                            modifier = Modifier.testTag(tab.testTag)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    MainTabContent(
                        selectedTab = selectedTab,
                        uiState = uiState,
                        viewModel = viewModel,
                        onNavigateToPatients = { filter ->
                            viewModel.selectDirectoryFilter(filter)
                            selectedTab = MainTab.PATIENTS
                        },
                        onOpenAppointment = onOpenAppointment,
                        onOpenConsultation = onOpenConsultation,
                        onOpenAssistSession = onOpenAssistSession,
                        onSignOut = onSignOut,
                        modifier = Modifier.widthIn(max = 680.dp)
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(GlacialMistBottom)
            ) {
                MainTabContent(
                    selectedTab = selectedTab,
                    uiState = uiState,
                    viewModel = viewModel,
                    onNavigateToPatients = { filter ->
                        viewModel.selectDirectoryFilter(filter)
                        selectedTab = MainTab.PATIENTS
                    },
                    onOpenAppointment = onOpenAppointment,
                    onOpenConsultation = onOpenConsultation,
                    onOpenAssistSession = onOpenAssistSession,
                    onSignOut = onSignOut,
                    modifier = Modifier.fillMaxSize()
                )

                // Floating frosted-glass capsule navigation bar
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFFD2E5EC).copy(alpha = 0.90f),
                    border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.88f)),
                    shadowElevation = 10.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MainTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            Surface(
                                modifier = Modifier
                                    .weight(if (selected) 1.45f else 1f)
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable { selectedTab = tab }
                                    .testTag(tab.testTag),
                                shape = RoundedCornerShape(999.dp),
                                color = if (selected) Color.White else Color.White.copy(alpha = 0.42f),
                                shadowElevation = if (selected) 3.dp else 0.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes),
                                        tint = if (selected) OceanTealPrimary else Color(0xFF285566),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    if (selected) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(tab.labelRes),
                                            color = OceanTealDark,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.5.sp
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainTabContent(
    selectedTab: MainTab,
    uiState: VerityScribeUiState,
    viewModel: VerityScribeViewModel,
    onNavigateToPatients: (com.example.model.HomeFilterTab) -> Unit,
    onOpenAppointment: (String) -> Unit,
    onOpenConsultation: (String) -> Unit,
    onOpenAssistSession: (String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (selectedTab) {
        MainTab.HOME -> HomeDashboardScreen(
            uiState = uiState,
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refreshWorkspace(userInitiated = true) },
            onSelectShift = viewModel::selectShift,
            onNavigateToPatients = onNavigateToPatients,
            onOpenAppointment = { apt -> onOpenAppointment(apt.id) },
            onOpenConsultation = { apt -> onOpenConsultation(apt.id) },
            onRetrySummary = viewModel::retryFailedSummary,
            onMarkAllNotificationsRead = viewModel::markAllNotificationsRead,
            onNotificationClick = { notif ->
                viewModel.markNotificationRead(notif.id)
                notif.linkedAppointmentId?.let(onOpenAppointment)
            },
            modifier = modifier
        )

        MainTab.PATIENTS -> PatientsDirectoryScreen(
            uiState = uiState,
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refreshWorkspace(userInitiated = true) },
            onSearchQueryChange = viewModel::updateDirectorySearchQuery,
            onSelectFilter = viewModel::selectDirectoryFilter,
            onOpenAppointment = { apt -> onOpenAppointment(apt.id) },
            modifier = modifier
        )

        MainTab.ASSIST -> AiAssistScreen(
            uiState = uiState,
            onSelectCategory = viewModel::selectAssistCategory,
            onOpenSession = onOpenAssistSession,
            onStartNewSession = { appointmentId, initialPrompt, attachmentTitle ->
                val newId = viewModel.startNewAssistSession(
                    appointmentId = appointmentId,
                    initialPrompt = initialPrompt,
                    attachmentReportTitle = attachmentTitle
                )
                if (newId.isNotBlank()) onOpenAssistSession(newId)
            },
            onMarkAllNotificationsRead = viewModel::markAllNotificationsRead,
            onNotificationClick = { notif ->
                viewModel.markNotificationRead(notif.id)
                notif.linkedAppointmentId?.let(onOpenAppointment)
            },
            modifier = modifier
        )

        MainTab.PROFILE -> ProfileScreen(
            uiState = uiState,
            onToggleAvailability = viewModel::toggleAvailability,
            onUpdateNotificationPrefs = viewModel::updateNotificationPreferences,
            onUpdatePrivacyPrefs = viewModel::updatePrivacyPreferences,
            onSelectHistoryRange = viewModel::selectHistoryRange,
            onHistorySearchQueryChange = viewModel::updateHistorySearchQuery,
            onOpenAppointment = onOpenAppointment,
            onStartHistoryAiChat = { historyRecordId ->
                val sessionId = viewModel.startHistoryAiConversation(historyRecordId)
                if (sessionId.isNotBlank()) onOpenAssistSession(sessionId)
            },
            onSignOut = onSignOut,
            modifier = modifier
        )
    }
}
