package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.data.GoogleFitHealthConnectManager
import com.example.data.HealthBridgeStatus
import com.example.model.Appointment
import com.example.model.AppointmentExpiry
import com.example.model.HealthSnapshotMetric
import com.example.ui.components.VerityGlassBackdrop
import com.example.ui.components.VerityGlassCard
import com.example.ui.components.VerityGlassChip
import com.example.ui.components.VerityGlassPillButton
import com.example.ui.components.VerityGlassSecondaryPill
import com.example.ui.theme.GlassAlertRed
import com.example.ui.theme.IceBlueText
import com.example.ui.theme.PeriwinkleMist
import com.example.ui.theme.PlatinumWhite
import com.example.ui.theme.SilverText
import com.example.ui.theme.SoftLavenderText
import com.example.viewmodel.VerityUiState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    uiState: VerityUiState,
    aiSummaryText: String,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
    onAskLumi: (String) -> Unit,
    onBookAppointment: () -> Unit,
    onSelectAppointmentIndex: (Int) -> Unit,
    onViewAppointmentDetails: (Appointment) -> Unit,
    onRescheduleAppointment: (Appointment) -> Unit,
    onJoinAppointmentCall: (Appointment) -> Unit,
    onSyncHealthSnapshot: () -> Unit,
    onAutoRefreshHealthSnapshot: () -> Unit,
    onHealthPermissionsResult: (Set<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        onHealthPermissionsResult(granted)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, uiState.googleFitAutoSync) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && uiState.googleFitAutoSync) {
                onAutoRefreshHealthSnapshot()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    VerityGlassBackdrop(
        isObsidianMode = uiState.darkMode,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(bottom = 116.dp)
            ) {
                // 1. Glass Header (Greeting + Clubbed Profile Avatar)
                item {
                    HomeGlassHeader(
                        firstName = uiState.profile.firstName,
                        profilePhotoPath = uiState.profilePhotoPath,
                        onOpenProfile = onOpenProfile,
                        unreadNotifications = uiState.notifications.count { it.isUnread },
                        onOpenNotifications = onOpenNotifications
                    )
                }

                // 2. Your AI health summary glass card
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    VerityGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .testTag("ai_health_summary_card"),
                        onClick = { onAskLumi("Summarize my upcoming appointments, sleep, and pending medicines.") }
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x2BFFFFFF))
                                        .border(1.dp, Color(0x47FFFFFF), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        tint = SoftLavenderText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Your care overview",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = PlatinumWhite
                                    )
                                    Text(
                                        text = if (uiState.isLiveAiEnabled) {
                                            "Based on your connected record"
                                        } else {
                                            "AI service configuration is missing"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SoftLavenderText
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = aiSummaryText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = PlatinumWhite.copy(alpha = 0.94f)
                            )
                        }
                    }
                }

                // 3. Today at a glance (Stacked Swipeable Card Deck matching Clinical Spotlight)
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    TodayAtAGlanceDeckSection(
                        appointments = uiState.appointments,
                        currentIndex = uiState.currentAppointmentIndex,
                        onSelectIndex = onSelectAppointmentIndex,
                        onViewDetails = onViewAppointmentDetails,
                        onReschedule = onRescheduleAppointment,
                        onJoinCall = onJoinAppointmentCall
                    )
                }

                // 4. Book an appointment metallic glass CTA below Today at a glance
                item {
                    Spacer(modifier = Modifier.height(14.dp))
                    VerityGlassPillButton(
                        text = "Book an appointment",
                        leadingIcon = Icons.Filled.CalendarToday,
                        onClick = onBookAppointment,
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .testTag("home_book_appointment_button")
                    )
                }

                // 5. Health snapshot (2x2 Lively Animated Glass Grid + Live Watch Sync Pill)
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    val fitState = uiState.googleFitState
                    val isLiveConnected = fitState.status == HealthBridgeStatus.CONNECTED
                    val hasAnyLiveValue = fitState.heartRateBpm != null ||
                        fitState.sleepFormatted != null ||
                        fitState.spo2Percent != null ||
                        fitState.stepsToday != null

                    val spinTransition = rememberInfiniteTransition(label = "homeSyncSpin")
                    val spinDegrees by spinTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 850, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "syncSpinAngle"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Health snapshot",
                                style = MaterialTheme.typography.titleLarge,
                                color = PlatinumWhite
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isLiveConnected && hasAnyLiveValue -> Color(0xFF4ADE80)
                                                isLiveConnected -> IceBlueText
                                                else -> SoftLavenderText
                                            }
                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when {
                                        uiState.isSyncingGoogleFit -> "Syncing Google Fit watch vitals..."
                                        isLiveConnected && hasAnyLiveValue ->
                                            "Google Fit live • ${fitState.lastSyncedLabel ?: "Synced"}"
                                        isLiveConnected ->
                                            "Google Fit linked • ${fitState.lastSyncedLabel ?: "Ready"}"
                                        fitState.status == HealthBridgeStatus.PERMISSIONS_NEEDED ->
                                            "Tap Sync to connect watch vitals"
                                        else -> "Health Connect • Tap Sync"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PeriwinkleMist,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 40.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0x36FFFFFF), Color(0x1CFFFFFF))
                                    )
                                )
                                .border(
                                    1.dp,
                                    if (isLiveConnected) IceBlueText.copy(alpha = 0.55f) else Color(0x52FFFFFF),
                                    CircleShape
                                )
                                .clickable(enabled = !uiState.isSyncingGoogleFit) {
                                    if (fitState.status == HealthBridgeStatus.PERMISSIONS_NEEDED &&
                                        fitState.grantedPermissions.isEmpty()
                                    ) {
                                        permissionsLauncher.launch(
                                            GoogleFitHealthConnectManager.requiredPermissions
                                        )
                                    } else {
                                        onSyncHealthSnapshot()
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                                .testTag("home_sync_vitals_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Sync,
                                contentDescription = "Sync watch vitals",
                                tint = IceBlueText,
                                modifier = Modifier
                                    .size(15.dp)
                                    .graphicsLayer {
                                        if (uiState.isSyncingGoogleFit) {
                                            rotationZ = spinDegrees
                                        }
                                    }
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (uiState.isSyncingGoogleFit) "Syncing" else "Sync",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = PlatinumWhite
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    val metrics = uiState.healthSnapshot.associateBy { it.id }
                    // Server-backed accounts may not have synced wearable data yet.
                    // Keep the existing card layout while rendering neutral values
                    // instead of indexing an empty or partially populated list.
                    val heartRate = metrics["hr"]
                        ?: HealthSnapshotMetric("hr", "Heart rate", "—", "bpm")
                    val sleep = metrics["sleep"]
                        ?: HealthSnapshotMetric("sleep", "Sleep", "—")
                    val oxygen = metrics["spo2"]
                        ?: HealthSnapshotMetric("spo2", "SpO2", "—")
                    val steps = metrics["steps"]
                        ?: HealthSnapshotMetric("steps", "Steps", "—")
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            HeartRateSnapshotCard(
                                label = heartRate.label,
                                value = heartRate.value,
                                unit = heartRate.unit,
                                modifier = Modifier.weight(1f)
                            )
                            SleepSnapshotCard(
                                label = sleep.label,
                                value = sleep.value,
                                unit = sleep.unit,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OxygenSnapshotCard(
                                label = "SpO2",
                                value = oxygen.value,
                                unit = oxygen.unit,
                                modifier = Modifier.weight(1f)
                            )
                            StepsSnapshotCard(
                                label = "Steps",
                                value = steps.value,
                                unit = steps.unit,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

            }
        }
    }
}

/**
 * Clinical-Spotlight-style stacked card deck:
 * Cards sit stacked under each other with depth offset and scale, and dragging (or tapping < >)
 * tilts and slides the top card off smoothly to reveal the next card underneath.
 */
@Composable
private fun TodayAtAGlanceDeckSection(
    appointments: List<Appointment>,
    currentIndex: Int,
    onSelectIndex: (Int) -> Unit,
    onViewDetails: (Appointment) -> Unit,
    onReschedule: (Appointment) -> Unit,
    onJoinCall: (Appointment) -> Unit
) {
    var now by remember { mutableStateOf(java.time.LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            now = java.time.LocalDateTime.now()
        }
    }
    val visibleAppointments = appointments.withIndex()
        .filterNot { AppointmentExpiry.isExpired(it.value, now) }
    if (visibleAppointments.isEmpty()) return

    val selectedVisibleIndex = visibleAppointments.indexOfFirst { it.index == currentIndex }
    val safeIndex = selectedVisibleIndex.takeIf { it >= 0 } ?: 0
    val visibleCount = visibleAppointments.size
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 96.dp.toPx() }
    val throwDistancePx = with(density) { 460.dp.toPx() }
    val dragOffsetX = remember { Animatable(0f) }
    var isAnimating by remember { mutableStateOf(false) }

    fun animateAndSwitch(direction: Float, targetIndex: Int) {
        if (isAnimating || visibleCount <= 1) return
        scope.launch {
            isAnimating = true
            dragOffsetX.animateTo(
                targetValue = direction * throwDistancePx,
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
            )
            onSelectIndex(visibleAppointments[targetIndex].index)
            dragOffsetX.snapTo(0f)
            isAnimating = false
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Today at a glance",
                    style = MaterialTheme.typography.titleLarge,
                    color = PlatinumWhite
                )
                Text(
                    text = "${safeIndex + 1} of $visibleCount • Swipe card",
                    style = MaterialTheme.typography.bodySmall,
                    color = PeriwinkleMist
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(
                    onClick = {
                        val prev = (safeIndex - 1).coerceAtLeast(0)
                        animateAndSwitch(-1f, prev)
                    },
                    enabled = safeIndex > 0 && !isAnimating,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("appointment_prev_button")
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0x2BFFFFFF))
                            .border(1.dp, Color(0x4DFFFFFF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous appointment",
                            tint = if (safeIndex > 0) PlatinumWhite else SilverText.copy(alpha = 0.4f)
                        )
                    }
                }
                IconButton(
                    onClick = {
                        val next = (safeIndex + 1).coerceAtMost(visibleCount - 1)
                        animateAndSwitch(1f, next)
                    },
                    enabled = safeIndex < visibleCount - 1 && !isAnimating,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("appointment_next_button")
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0x2BFFFFFF))
                            .border(1.dp, Color(0x4DFFFFFF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next appointment",
                            tint = if (safeIndex < visibleCount - 1) PlatinumWhite else SilverText.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val dragProgress = (abs(dragOffsetX.value) / (swipeThresholdPx * 1.6f)).coerceIn(0f, 1f)
        val visibleLayers = visibleCount.coerceAtMost(3)

        // Stacked deck: back layers drawn underneath the front interactive card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = if (visibleLayers > 1) ((visibleLayers - 1) * 11).dp else 0.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            for (layerOffset in (visibleLayers - 1) downTo 0) {
                val cardIndex = (safeIndex + layerOffset) % visibleCount
                val appointment = visibleAppointments[cardIndex].value

                key(appointment.id, layerOffset) {
                    when (layerOffset) {
                        2 -> {
                            val scale = 0.90f + 0.05f * dragProgress
                            val offsetYPx = with(density) { (22.dp - 11.dp * dragProgress).toPx() }
                            DeckAppointmentCard(
                                appointment = appointment,
                                isFront = false,
                                onViewDetails = {},
                                onReschedule = {},
                                onJoinCall = {},
                                modifier = Modifier
                                    .zIndex(1f)
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        translationY = offsetYPx
                                        alpha = 0.65f + 0.20f * dragProgress
                                    }
                            )
                        }

                        1 -> {
                            val scale = 0.95f + 0.05f * dragProgress
                            val offsetYPx = with(density) { (11.dp - 11.dp * dragProgress).toPx() }
                            DeckAppointmentCard(
                                appointment = appointment,
                                isFront = false,
                                onViewDetails = {},
                                onReschedule = {},
                                onJoinCall = {},
                                modifier = Modifier
                                    .zIndex(2f)
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        translationY = offsetYPx
                                        alpha = 0.85f + 0.15f * dragProgress
                                    }
                            )
                        }

                        else -> {
                            val tiltDegrees = (dragOffsetX.value / 26f).coerceIn(-12f, 12f)
                            val arcLiftY = -abs(dragOffsetX.value) * 0.045f
                            val frontAlpha = (1f - (abs(dragOffsetX.value) / (throwDistancePx * 1.3f)))
                                .coerceIn(0.45f, 1f)

                            DeckAppointmentCard(
                                appointment = appointment,
                                isFront = true,
                                onViewDetails = { onViewDetails(appointment) },
                                onReschedule = { onReschedule(appointment) },
                                onJoinCall = { onJoinCall(appointment) },
                                modifier = Modifier
                                    .zIndex(3f)
                                    .graphicsLayer {
                                        translationX = dragOffsetX.value
                                        translationY = arcLiftY
                                        rotationZ = tiltDegrees
                                        alpha = frontAlpha
                                    }
                                    .pointerInput(safeIndex, visibleCount) {
                                        if (visibleCount <= 1) return@pointerInput
                                        detectHorizontalDragGestures(
                                            onHorizontalDrag = { _, dragAmount ->
                                                if (!isAnimating) {
                                                    scope.launch {
                                                        dragOffsetX.snapTo(dragOffsetX.value + dragAmount)
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                if (isAnimating) return@detectHorizontalDragGestures
                                                if (abs(dragOffsetX.value) > swipeThresholdPx) {
                                                    val dir = if (dragOffsetX.value >= 0f) 1f else -1f
                                                    val nextIndex = (safeIndex + 1) % visibleCount
                                                    animateAndSwitch(dir, nextIndex)
                                                } else {
                                                    scope.launch {
                                                        dragOffsetX.animateTo(
                                                            targetValue = 0f,
                                                            animationSpec = spring(
                                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                                stiffness = Spring.StiffnessMediumLow
                                                            )
                                                        )
                                                    }
                                                }
                                            },
                                            onDragCancel = {
                                                if (!isAnimating) {
                                                    scope.launch {
                                                        dragOffsetX.animateTo(0f)
                                                    }
                                                }
                                            }
                                        )
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeckAppointmentCard(
    appointment: Appointment,
    isFront: Boolean,
    onViewDetails: () -> Unit,
    onReschedule: () -> Unit,
    onJoinCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(24.dp)
    // Frosted slate-glass body opaque enough that stacked cards underneath don't bleed text through
    val cardBrush = Brush.verticalGradient(
        colors = if (isFront) {
            listOf(Color(0xF53E424E), Color(0xF72C3039))
        } else {
            listOf(Color(0xEB343843), Color(0xF0252831))
        }
    )
    val borderBrush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = if (isFront) 0.46f else 0.30f),
            Color.White.copy(alpha = 0.14f)
        )
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isFront) 14.dp else 6.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.55f),
                spotColor = Color.Black.copy(alpha = 0.65f)
            )
            .clip(shape)
            .background(cardBrush, shape)
            .border(1.1.dp, borderBrush, shape)
            .padding(18.dp)
    ) {
        // Doctor header row + compact View Details pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = appointment.avatarRes),
                contentDescription = appointment.doctorName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .border(1.2.dp, Color(0x66FFFFFF), CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appointment.doctorName,
                    style = MaterialTheme.typography.titleMedium,
                    color = PlatinumWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${appointment.specialty} • ${appointment.hospital}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SoftLavenderText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            VerityGlassChip(
                text = "View Details",
                highlighted = true,
                onClick = if (isFront) onViewDetails else null,
                modifier = if (isFront) Modifier.testTag("view_details_${appointment.id}") else Modifier
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Date, time, and consultation mode metadata row
        val modeIcon = when (appointment.consultationType) {
            "AI Virtual" -> Icons.Filled.AutoAwesome
            "AI Chat" -> Icons.Filled.ChatBubbleOutline
            "In person" -> Icons.Filled.LocalHospital
            else -> Icons.Filled.Videocam
        }
        val actionLabel = when (appointment.consultationType) {
            "AI Virtual" -> "Start Virtual"
            "AI Chat" -> "Open AI Chat"
            "In person" -> "Start Consultation"
            else -> "Join Call"
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.CalendarToday,
                        contentDescription = null,
                        tint = IceBlueText,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = appointment.dateLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = PlatinumWhite,
                        maxLines = 1
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.AccessTime,
                        contentDescription = null,
                        tint = IceBlueText,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = appointment.timeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = PlatinumWhite,
                        maxLines = 1
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0x24FFFFFF))
                    .border(1.dp, Color(0x3DFFFFFF), CircleShape)
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = modeIcon,
                    contentDescription = appointment.consultationType,
                    tint = IceBlueText,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = appointment.consultationType,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = IceBlueText,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Side-by-side equal-height glass action pills (matching Clinical Spotlight card buttons)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VerityGlassSecondaryPill(
                text = "Reschedule",
                onClick = { if (isFront) onReschedule() },
                minHeight = 48.dp,
                horizontalPadding = 12.dp,
                modifier = Modifier
                    .weight(1f)
                    .then(if (isFront) Modifier.testTag("reschedule_${appointment.id}") else Modifier)
            )
            VerityGlassPillButton(
                text = actionLabel,
                leadingIcon = modeIcon,
                onClick = { if (isFront) onJoinCall() },
                minHeight = 48.dp,
                horizontalPadding = 12.dp,
                modifier = Modifier
                    .weight(1f)
                    .then(if (isFront) Modifier.testTag("join_call_${appointment.id}") else Modifier)
            )
        }
    }
}

@Composable
private fun HomeGlassHeader(
    firstName: String,
    profilePhotoPath: String?,
    onOpenProfile: () -> Unit,
    unreadNotifications: Int,
    onOpenNotifications: () -> Unit
) {
    val hasPhoto = remember(profilePhotoPath) {
        !profilePhotoPath.isNullOrBlank() && File(profilePhotoPath).exists()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0x29FFFFFF))
                    .border(1.2.dp, Color(0x6BFFFFFF), CircleShape)
                    .clickable(onClick = onOpenProfile)
                    .testTag("home_profile_button"),
                contentAlignment = Alignment.Center
            ) {
                if (hasPhoto) {
                    AsyncImage(
                        model = File(profilePhotoPath!!),
                        contentDescription = "$firstName profile photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = "Open profile",
                        tint = PlatinumWhite,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Good morning, $firstName",
                    style = MaterialTheme.typography.titleMedium,
                    color = PlatinumWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()).format(Date()),
                    style = MaterialTheme.typography.bodySmall,
                    color = IceBlueText.copy(alpha = 0.90f)
                )
            }
            Box(contentAlignment = Alignment.TopEnd) {
                IconButton(onClick = onOpenNotifications, modifier = Modifier.testTag("home_notifications_button")) {
                    Icon(Icons.Filled.Notifications, contentDescription = "Notifications", tint = PlatinumWhite)
                }
                if (unreadNotifications > 0) {
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp, end = 8.dp)
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF87171))
                    )
                }
            }
        }
    }
}

/**
 * Lively Heart Rate card: double lub-dub beating heart icon + scrolling live ECG waveform trace.
 */
@Composable
private fun HeartRateSnapshotCard(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "heartRateAnim")
    val beatPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 950, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "beatPhase"
    )
    // Lub-dub double pulse envelope
    val pulse = when {
        beatPhase < 0.14f -> sin((beatPhase / 0.14f) * PI).toFloat()
        beatPhase in 0.22f..0.38f -> sin(((beatPhase - 0.22f) / 0.16f) * PI).toFloat() * 0.72f
        else -> 0f
    }
    val heartScale = 1f + pulse * 0.26f

    VerityGlassCard(
        cornerRadius = 20.dp,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = PeriwinkleMist
                )
                Box(
                    modifier = Modifier.size(34.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .graphicsLayer {
                                val ringScale = 0.85f + pulse * 0.35f
                                scaleX = ringScale
                                scaleY = ringScale
                                alpha = 0.28f + pulse * 0.55f
                            }
                            .clip(CircleShape)
                            .background(Color(0xFFFCA5A5).copy(alpha = 0.26f))
                            .border(1.dp, Color(0xFFFCA5A5).copy(alpha = 0.5f), CircleShape)
                    )
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = label,
                        tint = Color(0xFFFCA5A5),
                        modifier = Modifier
                            .size(17.dp)
                            .graphicsLayer {
                                scaleX = heartScale
                                scaleY = heartScale
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Animated ECG pulse waveform
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
            ) {
                val w = size.width
                val h = size.height
                val midY = h * 0.56f
                val sweepX = beatPhase * w

                val ecgPath = Path().apply {
                    moveTo(0f, midY)
                    lineTo(w * 0.18f, midY)
                    lineTo(w * 0.24f, midY - h * 0.18f)
                    lineTo(w * 0.30f, midY + h * 0.14f)
                    lineTo(w * 0.38f, midY - h * 0.44f)
                    lineTo(w * 0.45f, midY + h * 0.32f)
                    lineTo(w * 0.52f, midY)
                    lineTo(w * 0.68f, midY)
                    lineTo(w * 0.74f, midY - h * 0.22f)
                    lineTo(w * 0.80f, midY)
                    lineTo(w, midY)
                }

                drawPath(
                    path = ecgPath,
                    color = Color.White.copy(alpha = 0.16f),
                    style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawPath(
                    path = ecgPath,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            IceBlueText.copy(alpha = 0.55f),
                            Color(0xFFFCA5A5),
                            Color.Transparent
                        ),
                        startX = (sweepX - w * 0.42f),
                        endX = (sweepX + w * 0.18f)
                    ),
                    style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp),
                    color = PlatinumWhite
                )
                if (unit.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.bodySmall,
                        color = SoftLavenderText,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }
        }
    }
}

/**
 * Lively Sleep card: cozy bed with breathing blanket & animated rising "Z z z" snoring bubbles.
 */
@Composable
private fun SleepSnapshotCard(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "sleepAnim")
    val snorePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "snorePhase"
    )
    val breath = ((sin(snorePhase * 2f * PI) + 1f) / 2f).toFloat()

    VerityGlassCard(
        cornerRadius = 20.dp,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = PeriwinkleMist
                )
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(SoftLavenderText.copy(alpha = 0.14f))
                        .border(1.dp, SoftLavenderText.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bedtime,
                        contentDescription = label,
                        tint = SoftLavenderText,
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer {
                                rotationZ = -6f + breath * 12f
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Cozy bed + breathing sleeper + 3 staggered rising "Z" snore letters
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Bed frame & headboard
                    drawRoundRect(
                        color = SilverText.copy(alpha = 0.72f),
                        topLeft = Offset(4.dp.toPx(), h * 0.25f),
                        size = Size(3.5.dp.toPx(), h * 0.70f),
                        cornerRadius = CornerRadius(2.dp.toPx())
                    )
                    drawRoundRect(
                        color = SilverText.copy(alpha = 0.65f),
                        topLeft = Offset(4.dp.toPx(), h * 0.72f),
                        size = Size(w * 0.60f, 3.5.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx())
                    )
                    // Pillow
                    drawRoundRect(
                        color = PlatinumWhite.copy(alpha = 0.90f),
                        topLeft = Offset(9.dp.toPx(), h * 0.48f),
                        size = Size(12.dp.toPx(), h * 0.24f),
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )
                    // Sleeping head
                    drawCircle(
                        color = SoftLavenderText,
                        radius = 4.6.dp.toPx(),
                        center = Offset(15.dp.toPx(), h * 0.36f)
                    )
                    // Blanket gently rising & falling with breath
                    val breathLift = breath * 2.4.dp.toPx()
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(PeriwinkleMist, IceBlueText.copy(alpha = 0.85f))
                        ),
                        topLeft = Offset(21.dp.toPx(), h * 0.42f - breathLift),
                        size = Size(w * 0.42f, h * 0.30f + breathLift),
                        cornerRadius = CornerRadius(6.dp.toPx())
                    )

                    // 3 staggered rising "Z" snoring glyphs drifting upwards
                    listOf(0.0f, 0.33f, 0.66f).forEachIndexed { idx, offsetPhase ->
                        val t = (snorePhase + offsetPhase) % 1f
                        val alpha = when {
                            t < 0.2f -> t / 0.2f
                            t > 0.72f -> ((1f - t) / 0.28f).coerceAtLeast(0f)
                            else -> 1f
                        }
                        val zx = w * 0.34f + t * w * 0.32f + idx * 4.dp.toPx()
                        val zy = h * 0.42f - t * h * 0.36f
                        val zSize = (4.5f + t * 3.5f).dp.toPx()
                        drawZGlyph(
                            topLeft = Offset(zx, zy),
                            size = zSize,
                            color = SoftLavenderText.copy(alpha = alpha * 0.95f),
                            strokeWidth = 1.5.dp.toPx()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp),
                    color = PlatinumWhite
                )
                if (unit.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.bodySmall,
                        color = SoftLavenderText,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }
        }
    }
}

/**
 * Lively Blood Oxygen card: pulsing respiratory wave + rising oxygen micro-bubbles.
 */
@Composable
private fun OxygenSnapshotCard(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "o2Anim")
    val o2Phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "o2Phase"
    )
    val breathScale = 1f + 0.14f * ((sin(o2Phase * 2f * PI) + 1f) / 2f).toFloat()

    VerityGlassCard(
        cornerRadius = 20.dp,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = PeriwinkleMist
                )
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(IceBlueText.copy(alpha = 0.14f))
                        .border(1.dp, IceBlueText.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Air,
                        contentDescription = label,
                        tint = IceBlueText,
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer {
                                scaleX = breathScale
                                scaleY = breathScale
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
            ) {
                val w = size.width
                val h = size.height
                val wavePath = Path().apply {
                    val steps = 28
                    for (i in 0..steps) {
                        val x = (i / steps.toFloat()) * w
                        val y = h * 0.56f + sin((i / steps.toFloat()) * 4f * PI + o2Phase * 2f * PI).toFloat() * h * 0.22f
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
                drawPath(
                    path = wavePath,
                    brush = Brush.horizontalGradient(
                        listOf(IceBlueText.copy(alpha = 0.3f), IceBlueText, SoftLavenderText)
                    ),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )

                // Rising oxygen micro-bubbles
                listOf(0.18f to 0.0f, 0.52f to 0.35f, 0.82f to 0.68f).forEach { (xFrac, offset) ->
                    val t = (o2Phase + offset) % 1f
                    val alpha = (1f - abs(t - 0.5f) * 2f).coerceIn(0f, 1f)
                    drawCircle(
                        color = IceBlueText.copy(alpha = alpha * 0.85f),
                        radius = (2.2f + (1f - t) * 1.4f).dp.toPx(),
                        center = Offset(w * xFrac, h * (0.85f - t * 0.7f)),
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp),
                    color = PlatinumWhite
                )
                if (unit.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.bodySmall,
                        color = SoftLavenderText,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }
        }
    }
}

/**
 * Lively Steps card: animated walking footsteps taking 3 steps forward (Left -> Right -> Left),
 * fading cleanly, and restarting from step 1 repeatedly.
 */
@Composable
private fun StepsSnapshotCard(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "stepsAnim")
    val walkCycle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "walkCycle"
    )
    // Subtle walker tilt when a foot lands
    val stepBounce = when {
        walkCycle < 0.24f -> -10f
        walkCycle < 0.48f -> 10f
        walkCycle < 0.72f -> -10f
        else -> 0f
    }

    VerityGlassCard(
        cornerRadius = 20.dp,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = PeriwinkleMist
                )
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(IceBlueText.copy(alpha = 0.14f))
                        .border(1.dp, IceBlueText.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                        contentDescription = label,
                        tint = IceBlueText,
                        modifier = Modifier
                            .size(17.dp)
                            .graphicsLayer {
                                rotationZ = stepBounce
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 3 sequential footsteps walking across and restarting
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
            ) {
                val w = size.width
                val h = size.height

                // Fade all 3 steps together at end of cycle before restarting
                val globalFade = if (walkCycle > 0.80f) {
                    ((1f - walkCycle) / 0.20f).coerceIn(0f, 1f)
                } else {
                    1f
                }

                val stepSpecs = listOf(
                    Triple(0.04f, Offset(w * 0.16f, h * 0.34f), -12f), // Step 1 (Left)
                    Triple(0.28f, Offset(w * 0.46f, h * 0.68f), 10f),  // Step 2 (Right)
                    Triple(0.52f, Offset(w * 0.76f, h * 0.34f), -10f)  // Step 3 (Left)
                )

                stepSpecs.forEachIndexed { idx, (appearAt, center, angleDeg) ->
                    val stepAppear = ((walkCycle - appearAt) / 0.12f).coerceIn(0f, 1f)
                    val alpha = stepAppear * globalFade
                    if (alpha > 0.02f) {
                        val color = if (idx == 1) SoftLavenderText else IceBlueText
                        drawFootprintStamp(
                            center = center,
                            angleDeg = angleDeg,
                            color = color.copy(alpha = alpha)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp),
                    color = PlatinumWhite
                )
                if (unit.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.bodySmall,
                        color = SoftLavenderText,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawZGlyph(
    topLeft: Offset,
    size: Float,
    color: Color,
    strokeWidth: Float
) {
    val path = Path().apply {
        moveTo(topLeft.x, topLeft.y)
        lineTo(topLeft.x + size, topLeft.y)
        lineTo(topLeft.x, topLeft.y + size)
        lineTo(topLeft.x + size, topLeft.y + size)
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun DrawScope.drawFootprintStamp(
    center: Offset,
    angleDeg: Float,
    color: Color
) {
    rotate(degrees = angleDeg, pivot = center) {
        val soleW = 10.dp.toPx()
        val soleH = 6.2.dp.toPx()
        // Forefoot pad
        drawOval(
            color = color,
            topLeft = Offset(center.x - soleW * 0.1f, center.y - soleH * 0.5f),
            size = Size(soleW * 0.65f, soleH)
        )
        // Heel pad
        drawOval(
            color = color,
            topLeft = Offset(center.x - soleW * 0.55f, center.y - soleH * 0.36f),
            size = Size(soleW * 0.36f, soleH * 0.72f)
        )
    }
}
