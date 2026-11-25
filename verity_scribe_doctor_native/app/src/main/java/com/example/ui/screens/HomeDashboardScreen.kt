package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AppointmentStatus
import com.example.model.ClinicalAppointment
import com.example.model.ConsultationType
import com.example.model.DoctorNotification
import com.example.model.HomeFilterTab
import com.example.model.ShiftPeriod
import com.example.ui.theme.AmberBg
import com.example.ui.theme.AmberBorder
import com.example.ui.theme.AmberText
import com.example.ui.theme.ClinicalBorder
import com.example.ui.theme.ClinicalSurface
import com.example.ui.theme.ClinicalSurfaceSubtle
import com.example.ui.theme.EmeraldBg
import com.example.ui.theme.EmeraldText
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.OceanMistGradientStops
import com.example.ui.theme.OceanTealDark
import com.example.ui.theme.OceanTealMid
import com.example.ui.theme.OceanTealMist
import com.example.ui.theme.OceanTealPrimary
import com.example.ui.theme.OceanTealSoft
import com.example.ui.theme.RoseBg
import com.example.ui.theme.RoseText
import com.example.ui.theme.SlateGunmetalDark
import com.example.ui.theme.SlateGunmetalLight
import com.example.ui.theme.SlateGunmetalMid
import com.example.viewmodel.VerityScribeUiState
import kotlinx.coroutines.launch
import kotlin.math.abs
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDashboardScreen(
    uiState: VerityScribeUiState,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onSelectShift: (ShiftPeriod) -> Unit,
    onNavigateToPatients: (HomeFilterTab) -> Unit,
    onOpenAppointment: (ClinicalAppointment) -> Unit,
    onOpenConsultation: (ClinicalAppointment) -> Unit,
    onRetrySummary: (String) -> Unit,
    onMarkAllNotificationsRead: () -> Unit,
    onNotificationClick: (DoctorNotification) -> Unit,
    modifier: Modifier = Modifier
) {
    var showShiftMenu by remember { mutableStateOf(false) }
    var showNotificationsSheet by remember { mutableStateOf(false) }

    val today = LocalDate.now()
    val priorityQueue = remember(uiState.appointments, today) {
        uiState.appointments.filter { appointment ->
            val appointmentDate = runCatching {
                appointment.appointmentDate?.take(10)?.let(LocalDate::parse)
            }.getOrNull()
            appointmentDate != null && !appointmentDate.isBefore(today) &&
                appointment.status !in setOf(
                    AppointmentStatus.COMPLETED,
                    AppointmentStatus.REJECTED,
                    AppointmentStatus.CANCELLED,
                    AppointmentStatus.INCOMPLETE
                )
        }.sortedBy {
            when (it.status) {
                AppointmentStatus.FAILED_SUMMARY -> 0
                AppointmentStatus.IN_PROGRESS -> 1
                AppointmentStatus.AWAITING_REVIEW -> 2
                AppointmentStatus.WAITING -> 3
                AppointmentStatus.SCHEDULED -> 4
                AppointmentStatus.APPROVED -> 5
                else -> 6
            }
        }
    }

    val nextHeroPatient = remember(priorityQueue, uiState.appointments) {
        priorityQueue.firstOrNull { it.status == AppointmentStatus.AWAITING_REVIEW }
            ?: priorityQueue.firstOrNull()
            ?: uiState.appointments.firstOrNull()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = OceanMistGradientStops
                )
            )
    ) {
        // Subtle ambient glows behind frosted glass cards
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(OceanTealMist.copy(alpha = 0.28f), Color.Transparent),
                    center = Offset(size.width * 0.88f, size.height * 0.22f),
                    radius = size.width * 0.55f
                ),
                radius = size.width * 0.55f,
                center = Offset(size.width * 0.88f, size.height * 0.22f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(OceanTealMid.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(size.width * 0.14f, size.height * 0.68f),
                    radius = size.width * 0.60f
                ),
                radius = size.width * 0.60f,
                center = Offset(size.width * 0.14f, size.height * 0.68f)
            )
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
            // 1. Editorial Clinical Header on Ocean Teal Top Zone
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box {
                            Image(
                                painter = painterResource(id = R.drawable.verityscribe_icon_fg),
                                contentDescription = "VerityScribe Doctor mark",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .border(1.8.dp, Color.White, CircleShape)
                            )
                            if (uiState.doctorProfile.isAvailable) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981))
                                        .border(2.dp, Color.White, CircleShape)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Good morning, ${uiState.doctorProfile.greetingName}",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                ),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${uiState.doctorProfile.department} • ${uiState.doctorProfile.hospital}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.86f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Box {
                        Surface(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable { showNotificationsSheet = true }
                                .testTag("notifications_bell_button"),
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.22f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.38f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Notifications,
                                    contentDescription = "Clinical notifications",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        if (uiState.unreadNotificationsCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-2).dp, y = 2.dp)
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(RoseText)
                                    .border(1.5.dp, Color.White, CircleShape)
                            )
                        }
                    }
                }
            }

            // 2. Signature Gunmetal Telemetry Hero Card
            item {
                ClinicalCommandHeroCard(
                    uiState = uiState,
                    nextHeroPatient = nextHeroPatient,
                    showShiftMenu = showShiftMenu,
                    onToggleShiftMenu = { showShiftMenu = it },
                    onSelectShift = onSelectShift,
                    onOpenAppointment = onOpenAppointment
                )
            }

            // 3. Asymmetric Glassy Bento KPI Layout
            item {
                BentoMetricsGrid(
                    totalAppointments = uiState.totalAppointmentsCount,
                    awaitingReview = uiState.awaitingReviewCount,
                    pendingReports = uiState.pendingReportsCount,
                    completedCount = uiState.completedCount,
                    onTapAwaitingReview = { onNavigateToPatients(HomeFilterTab.AWAITING_REVIEW) },
                    onTapCompleted = { onNavigateToPatients(HomeFilterTab.COMPLETED) },
                    onTapReports = { onNavigateToPatients(HomeFilterTab.ALL) }
                )
            }

            // 4. Stacked Clinical Spotlight Deck ("one below the other" with swipe & arrows)
            if (priorityQueue.isNotEmpty()) {
                item {
                    SpotlightStackedDeck(
                        appointments = priorityQueue,
                        retryingAppointmentId = uiState.retryingSummaryAppointmentId,
                        onViewDetails = onOpenAppointment,
                        onPrimaryAction = { apt ->
                            when {
                                apt.status == AppointmentStatus.FAILED_SUMMARY -> onRetrySummary(apt.id)
                                apt.status == AppointmentStatus.IN_PROGRESS ||
                                    apt.status == AppointmentStatus.APPROVED -> onOpenConsultation(apt)
                                else -> onOpenAppointment(apt)
                            }
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(6.dp).navigationBarsPadding()) }
            }
        }
    }

    if (showNotificationsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showNotificationsSheet = false },
            sheetState = sheetState,
            containerColor = ClinicalSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Clinical Notifications",
                        style = MaterialTheme.typography.headlineSmall,
                        color = InkPrimary
                    )
                    TextButton(onClick = onMarkAllNotificationsRead) {
                        Icon(
                            imageVector = Icons.Rounded.DoneAll,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = SlateGunmetalDark
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Mark all read", color = SlateGunmetalDark)
                    }
                }

                uiState.notifications.forEach { notif ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                onNotificationClick(notif)
                                showNotificationsSheet = false
                            },
                        shape = RoundedCornerShape(14.dp),
                        color = if (!notif.isRead) {
                            if (notif.isUrgent) RoseBg.copy(alpha = 0.5f) else AmberBg.copy(alpha = 0.35f)
                        } else {
                            ClinicalSurfaceSubtle
                        },
                        border = BorderStroke(1.dp, ClinicalBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = notif.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (notif.isUrgent && !notif.isRead) RoseText else InkPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = notif.timeAgo,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = InkMuted
                                )
                            }
                            Text(
                                text = notif.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatSpotlightDate(rawDate: String): String {
    val date = runCatching { LocalDate.parse(rawDate.take(10)) }.getOrNull() ?: return rawDate
    val format = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    return if (date == LocalDate.now()) {
        "Today, ${date.format(format)}"
    } else {
        date.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
    }
}

@Composable
private fun ClinicalCommandHeroCard(
    uiState: VerityScribeUiState,
    nextHeroPatient: ClinicalAppointment?,
    showShiftMenu: Boolean,
    onToggleShiftMenu: (Boolean) -> Unit,
    onSelectShift: (ShiftPeriod) -> Unit,
    onOpenAppointment: (ClinicalAppointment) -> Unit
) {
    val pulseTransition = rememberInfiniteTransition(label = "ecg_pulse")
    val pulsePhase by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ecg_phase"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF074C63), OceanTealPrimary, Color(0xFF1B7692))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(24.dp))
    ) {
        // Architectural ECG Telemetry Waveform Overlay in crisp silver-white
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.14f), Color.Transparent),
                    center = Offset(w * 0.88f, h * 0.20f),
                    radius = w * 0.45f
                ),
                radius = w * 0.45f,
                center = Offset(w * 0.88f, h * 0.20f)
            )

            val baseY = h * 0.40f
            val path = Path().apply {
                moveTo(0f, baseY)
                lineTo(w * 0.42f, baseY)
                lineTo(w * 0.46f, baseY - 10f)
                lineTo(w * 0.49f, baseY + 6f)
                lineTo(w * 0.53f, baseY - 36f)
                lineTo(w * 0.57f, baseY + 22f)
                lineTo(w * 0.61f, baseY - 8f)
                lineTo(w * 0.65f, baseY)
                lineTo(w * 0.78f, baseY)
                lineTo(w * 0.81f, baseY - 6f)
                lineTo(w * 0.84f, baseY - 24f)
                lineTo(w * 0.87f, baseY + 14f)
                lineTo(w * 0.90f, baseY)
                lineTo(w, baseY)
            }
            drawPath(
                path = path,
                color = Color.White.copy(alpha = 0.16f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            val dotX = w * pulsePhase
            drawCircle(
                color = Color.White.copy(alpha = 0.55f),
                radius = 3.5.dp.toPx(),
                center = Offset(dotX, baseY)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF34D399))
                        )
                        Text(
                            text = if (uiState.doctorProfile.isAvailable) "ON SHIFT" else "PAUSED",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.8.sp
                            ),
                            color = Color.White
                        )
                    }
                }

                Box {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { onToggleShiftMenu(true) }
                            .testTag("shift_selector_chip"),
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f))
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarToday,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.92f),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = uiState.selectedShift.displayLabel(),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                            Icon(
                                imageVector = Icons.Rounded.ArrowDropDown,
                                contentDescription = "Change shift",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showShiftMenu,
                        onDismissRequest = { onToggleShiftMenu(false) }
                    ) {
                        ShiftPeriod.entries.forEach { shift ->
                            DropdownMenuItem(
                                text = { Text(shift.label) },
                                onClick = {
                                    onSelectShift(shift)
                                    onToggleShiftMenu(false)
                                }
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = if (uiState.awaitingReviewCount > 0) {
                        "${uiState.awaitingReviewCount} AI drafts ready for review"
                    } else {
                        "All intake drafts reviewed"
                    },
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 21.sp
                    ),
                    color = Color.White
                )
                Text(
                    text = "${uiState.totalAppointmentsCount} consultations • ${uiState.pendingReportsCount} reports • ${uiState.completedCount} completed",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.86f)
                )
            }

            if (nextHeroPatient != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onOpenAppointment(nextHeroPatient) }
                        .testTag("hero_review_next_button"),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "UP NEXT • ${nextHeroPatient.scheduledTime}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    letterSpacing = 0.6.sp
                                ),
                                color = Color(0xFFE2E8F0)
                            )
                            Text(
                                text = "${nextHeroPatient.patientName} — ${nextHeroPatient.chiefComplaint}",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color.White
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Review",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = SlateGunmetalDark
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = null,
                                    tint = SlateGunmetalDark,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BentoMetricsGrid(
    totalAppointments: Int,
    awaitingReview: Int,
    pendingReports: Int,
    completedCount: Int,
    onTapAwaitingReview: () -> Unit,
    onTapCompleted: () -> Unit,
    onTapReports: () -> Unit
) {
    val completionRatio = if (totalAppointments > 0) {
        (completedCount.toFloat() / totalAppointments.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val glassBrush = Brush.verticalGradient(
        colors = listOf(Color.White.copy(alpha = 0.66f), Color.White.copy(alpha = 0.44f))
    )
    val glassBorderBrush = Brush.verticalGradient(
        colors = listOf(Color.White.copy(alpha = 0.94f), Color.White.copy(alpha = 0.44f))
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Left Featured Shift Progress Ring Glass Card
        Surface(
            modifier = Modifier
                .weight(1.05f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(22.dp))
                .background(glassBrush, RoundedCornerShape(22.dp))
                .border(1.2.dp, glassBorderBrush, RoundedCornerShape(22.dp))
                .clickable(onClick = onTapCompleted)
                .testTag("bento_completed_card"),
            shape = RoundedCornerShape(22.dp),
            color = Color.Transparent,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Shift Progress",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = InkPrimary
                    )
                    Icon(
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = null,
                        tint = SlateGunmetalMid,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .size(84.dp)
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokePx = 9.dp.toPx()
                        val inset = strokePx / 2f
                        drawArc(
                            color = Color.White.copy(alpha = 0.68f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - strokePx, size.height - strokePx),
                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                        )
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(SlateGunmetalDark, SlateGunmetalMid, SlateGunmetalLight)
                            ),
                            startAngle = -90f,
                            sweepAngle = 360f * completionRatio.coerceAtLeast(0.08f),
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - strokePx, size.height - strokePx),
                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$completedCount/$totalAppointments",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = InkPrimary
                        )
                        Text(
                            text = "Done",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkMuted
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = EmeraldBg.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f))
                ) {
                    Text(
                        text = "${(completionRatio * 100).toInt()}% cleared",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = EmeraldText,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Right Stacked Glassy Bento KPI Cards
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(glassBrush, RoundedCornerShape(22.dp))
                    .border(1.2.dp, glassBorderBrush, RoundedCornerShape(22.dp))
                    .clickable(onClick = onTapAwaitingReview)
                    .testTag("bento_awaiting_review_card"),
                shape = RoundedCornerShape(22.dp),
                color = Color.Transparent,
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = awaitingReview.toString().padStart(2, '0'),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = InkPrimary
                        )
                        Text(
                            text = "AI Drafts Pending",
                            style = MaterialTheme.typography.labelMedium,
                            color = InkSecondary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(AmberBg.copy(alpha = 0.88f), CircleShape)
                            .border(1.dp, AmberBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.HourglassTop,
                            contentDescription = null,
                            tint = AmberText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(glassBrush, RoundedCornerShape(22.dp))
                    .border(1.2.dp, glassBorderBrush, RoundedCornerShape(22.dp))
                    .clickable(onClick = onTapReports)
                    .testTag("bento_reports_card"),
                shape = RoundedCornerShape(22.dp),
                color = Color.Transparent,
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = pendingReports.toString().padStart(2, '0'),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = InkPrimary
                        )
                        Text(
                            text = "OCR Reports",
                            style = MaterialTheme.typography.labelMedium,
                            color = InkSecondary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.72f), CircleShape)
                            .border(1.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Description,
                            contentDescription = null,
                            tint = OceanTealPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpotlightStackedDeck(
    appointments: List<ClinicalAppointment>,
    retryingAppointmentId: String?,
    onViewDetails: (ClinicalAppointment) -> Unit,
    onPrimaryAction: (ClinicalAppointment) -> Unit
) {
    var currentIndex by remember(appointments.size) { mutableIntStateOf(0) }
    val safeIndex = currentIndex.coerceIn(0, (appointments.size - 1).coerceAtLeast(0))
    val dragOffsetX = remember { Animatable(0f) }
    var deckWidthPx by remember { mutableIntStateOf(900) }
    val scope = rememberCoroutineScope()

    val goToNextImmediate: () -> Unit = {
        if (appointments.size > 1) {
            currentIndex = (safeIndex + 1) % appointments.size
        }
    }
    val animateToNext: () -> Unit = {
        if (appointments.size > 1 && !dragOffsetX.isRunning) {
            scope.launch {
                val exitDistance = (deckWidthPx * 1.15f).coerceAtLeast(540f)
                dragOffsetX.animateTo(-exitDistance, tween(180))
                goToNextImmediate()
                dragOffsetX.snapTo(0f)
            }
        }
    }
    val goToPrevious: () -> Unit = {
        if (appointments.size > 1 && !dragOffsetX.isRunning) {
            currentIndex = if (safeIndex == 0) appointments.lastIndex else safeIndex - 1
        }
    }

    val dragProgress = (abs(dragOffsetX.value) / (deckWidthPx * 0.48f).coerceAtLeast(180f))
        .coerceIn(0f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Header matching reference ("Clinical Spotlight", "1 of X • Swipe card", "< >")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = "Clinical Spotlight",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    color = InkPrimary
                )
                Text(
                    text = "${safeIndex + 1} of ${appointments.size} • Swipe card",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable(enabled = appointments.size > 1, onClick = goToPrevious)
                        .testTag("spotlight_prev_button"),
                    shape = CircleShape,
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFD5DFEA))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                            contentDescription = "Previous spotlight card",
                            tint = InkPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Surface(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable(enabled = appointments.size > 1, onClick = animateToNext)
                        .testTag("spotlight_next_button"),
                    shape = CircleShape,
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFD5DFEA))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = "Next spotlight card",
                            tint = InkPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Stacked Cards: Top active card + ONLY the single next card live underneath
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { if (it.width > 0) deckWidthPx = it.width }
                .padding(bottom = if (appointments.size > 2) 20.dp else if (appointments.size > 1) 12.dp else 0.dp)
                .testTag("clinical_spotlight_stack"),
            contentAlignment = Alignment.TopCenter
        ) {
            // Subtle 3rd depth frosted rim peeking at the very bottom when >= 3 cards exist
            if (appointments.size > 2) {
                val thirdOffsetDp = (20f - 10f * dragProgress).dp
                val thirdScale = 0.89f + (0.055f * dragProgress)
                Surface(
                    modifier = Modifier
                        .matchParentSize()
                        .offset(y = thirdOffsetDp)
                        .graphicsLayer { scaleX = thirdScale },
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White.copy(alpha = 0.60f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.82f))
                ) {}
            }

            // ONLY the real next card loaded underneath, smoothly scaling up as top card slides away
            if (appointments.size > 1) {
                val nextApt = appointments[(safeIndex + 1) % appointments.size]
                val nextOffsetDp = (10f * (1f - dragProgress)).dp
                val nextScale = 0.945f + (0.055f * dragProgress)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = nextOffsetDp)
                        .graphicsLayer {
                            scaleX = nextScale
                            alpha = 0.86f + (0.14f * dragProgress)
                        }
                ) {
                    SpotlightSlateCard(
                        appointment = nextApt,
                        isRetrying = retryingAppointmentId == nextApt.id,
                        onViewDetails = { onViewDetails(nextApt) },
                        onPrimaryAction = { onPrimaryAction(nextApt) }
                    )
                }
            }

            // Top Interactive Swipeable Card (slides left or right with tilt to reveal next card)
            val currentApt = appointments[safeIndex]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(dragOffsetX.value.roundToInt(), 0) }
                    .graphicsLayer {
                        rotationZ = (dragOffsetX.value / 28f).coerceIn(-12f, 12f)
                    }
                    .pointerInput(appointments.size, safeIndex) {
                        if (appointments.size > 1) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    scope.launch {
                                        val threshold = 105f
                                        val exitDistance = (deckWidthPx * 1.15f).coerceAtLeast(540f)
                                        when {
                                            dragOffsetX.value < -threshold -> {
                                                dragOffsetX.animateTo(-exitDistance, tween(170))
                                                goToNextImmediate()
                                                dragOffsetX.snapTo(0f)
                                            }
                                            dragOffsetX.value > threshold -> {
                                                dragOffsetX.animateTo(exitDistance, tween(170))
                                                goToNextImmediate()
                                                dragOffsetX.snapTo(0f)
                                            }
                                            else -> {
                                                dragOffsetX.animateTo(
                                                    0f,
                                                    spring(stiffness = Spring.StiffnessMediumLow)
                                                )
                                            }
                                        }
                                    }
                                },
                                onDragCancel = {
                                    scope.launch {
                                        dragOffsetX.animateTo(0f, spring())
                                    }
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    scope.launch {
                                        dragOffsetX.snapTo(dragOffsetX.value + dragAmount)
                                    }
                                }
                            )
                        }
                    }
            ) {
                SpotlightSlateCard(
                    appointment = currentApt,
                    isRetrying = retryingAppointmentId == currentApt.id,
                    onViewDetails = { onViewDetails(currentApt) },
                    onPrimaryAction = { onPrimaryAction(currentApt) }
                )
            }
        }
    }
}

@Composable
private fun SpotlightSlateCard(
    appointment: ClinicalAppointment,
    isRetrying: Boolean,
    onViewDetails: () -> Unit,
    onPrimaryAction: () -> Unit
) {
    val isFailed = appointment.status == AppointmentStatus.FAILED_SUMMARY
    val isLiveVideo = appointment.consultationType.label.contains("Video", ignoreCase = true) ||
        appointment.consultationType.label.contains("Virtual", ignoreCase = true)

    val subtitle = when (appointment.status) {
        AppointmentStatus.FAILED_SUMMARY -> "${appointment.chiefComplaint} • Summary Failed"
        AppointmentStatus.IN_PROGRESS -> "${appointment.chiefComplaint} • Live Now"
        else -> "${appointment.chiefComplaint} • ${appointment.consultationType.label}"
    }

    val primaryCtaLabel = when {
        isFailed -> if (isRetrying) "Retrying..." else "Retry AI"
        isLiveVideo -> "Join Call"
        else -> "Start Consult"
    }

    val primaryIcon = when {
        isFailed -> Icons.Rounded.Refresh
        isLiveVideo -> Icons.Outlined.Videocam
        else -> Icons.Rounded.MedicalServices
    }

    val initials = appointment.patientName
        .split(" ")
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .take(2)
        .joinToString("")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.96f),
        border = BorderStroke(1.2.dp, Color.White),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Row: Avatar + Patient Name & Subtitle + View Details Link
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(OceanTealSoft)
                        .border(1.2.dp, OceanTealPrimary.copy(alpha = 0.22f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = OceanTealDark
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appointment.patientName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.5.sp
                        ),
                        color = InkPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isFailed) RoseText else InkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "View Details",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp
                    ),
                    color = OceanTealPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onViewDetails)
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                        .testTag("spotlight_view_details_${appointment.id}")
                )
            }

            // Middle Metadata Row: Calendar + Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarToday,
                        contentDescription = null,
                        tint = OceanTealPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${appointment.appointmentDate?.let(::formatSpotlightDate) ?: LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))} • ${appointment.mrn}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = InkSecondary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = OceanTealPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = appointment.scheduledTime,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = InkSecondary
                    )
                }
            }

            // Bottom Dual Pill Action Buttons matching reference (Ice Pill + Deep Ocean Teal CTA)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Left Secondary Pill (Glacial Ice White)
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(onClick = onViewDetails)
                        .testTag("spotlight_review_draft_${appointment.id}"),
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFFEAF3F6),
                    border = BorderStroke(1.dp, Color(0xFFD2E5EC))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Review Draft",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = OceanTealDark
                        )
                    }
                }

                // Right Primary Pill (Deep Ocean Teal or Rose on Failure)
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(enabled = !isRetrying, onClick = onPrimaryAction)
                        .testTag("spotlight_primary_action_${appointment.id}"),
                    shape = RoundedCornerShape(999.dp),
                    color = if (isFailed) RoseText else OceanTealPrimary,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isRetrying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                imageVector = primaryIcon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = primaryCtaLabel,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
