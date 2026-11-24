package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.model.Appointment
import com.example.data.DirectGeminiSession
import com.example.data.GeminiLiveVoiceClient
import com.example.ui.components.VerityGlassCard
import com.example.ui.components.VerityGlassChip
import com.example.ui.components.VerityGlassPillButton
import com.example.ui.components.VerityGlassSecondaryPill
import com.example.ui.theme.IceBlueText
import com.example.ui.theme.PlatinumWhite
import com.example.ui.theme.SilverText
import com.example.ui.theme.SoftLavenderText
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class VirtualCallPhase {
    ACTIVE_CALL,
    POST_CALL_SUMMARY
}

@Composable
fun AppointmentAiVirtualScreen(
    appointment: Appointment,
    patientFirstName: String,
    onBack: () -> Unit,
    onStartConsultation: suspend (consent: Boolean, voiceConsent: Boolean) -> DirectGeminiSession,
    onPersistTurn: suspend (sessionId: String, requestKey: String, input: String, output: String) -> Unit,
    onEndConsultation: suspend (sessionId: String) -> String,
    onShareSummaryWithDoctor: (summaryText: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var voiceConsentAccepted by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(VirtualCallPhase.ACTIVE_CALL) }
    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(true) }
    var showCaptions by remember { mutableStateOf(true) }
    var isAiSpeaking by remember { mutableStateOf(false) }
    var isPatientSpeaking by remember { mutableStateOf(false) }
    var connectionStatus by remember { mutableStateOf("Requesting microphone permission…") }
    var connectionError by remember { mutableStateOf<String?>(null) }
    var generatedSummary by remember { mutableStateOf<String?>(null) }
    var isFinalizing by remember { mutableStateOf(false) }
    var isStartingSession by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var connectionAttempt by remember { mutableIntStateOf(0) }
    var sharedWithDoctor by remember { mutableStateOf(false) }
    var consultationSessionId by remember { mutableStateOf<String?>(null) }
    var liveClient by remember { mutableStateOf<GeminiLiveVoiceClient?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            connectionError = null
            connectionStatus = "Microphone permission granted · connecting"
        } else {
            connectionStatus = "Microphone permission required"
            connectionError = "Microphone permission is required to start AI Virtual. Allow it and retry."
        }
    }

    val voiceTranscript = remember(appointment.id) { mutableStateListOf<String>() }
    var currentCaption by remember { mutableStateOf("Connecting securely to Gemini Live…") }
    var assistantCaptionBuffer by remember { mutableStateOf("") }

    BackHandler {
        if (phase == VirtualCallPhase.POST_CALL_SUMMARY && !sharedWithDoctor) {
            onBack()
        } else {
            onBack()
        }
    }

    LaunchedEffect(appointment.id, hasMicPermission, voiceConsentAccepted) {
        if (voiceConsentAccepted && !hasMicPermission) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(appointment.id, hasMicPermission, phase, voiceConsentAccepted, connectionAttempt) {
        if (!voiceConsentAccepted || !hasMicPermission || phase != VirtualCallPhase.ACTIVE_CALL || liveClient != null || isStartingSession) return@LaunchedEffect
        isStartingSession = true
        connectionStatus = "Connecting securely to Gemini Live…"
        connectionError = null
        try {
            val session = onStartConsultation(true, true)
            consultationSessionId = session.sessionId
            val client = GeminiLiveVoiceClient(
                credential = session.credential,
                model = session.model,
                config = session.config,
                onCaption = { speaker, text, final ->
                    scope.launch(Dispatchers.Main.immediate) {
                        if (text.isNotBlank()) {
                            if (speaker == "assistant") {
                                if (final) {
                                    assistantCaptionBuffer = ""
                                    currentCaption = text
                                    voiceTranscript.add("AI Agent: $text")
                                    isAiSpeaking = false
                                } else {
                                    assistantCaptionBuffer += text
                                    currentCaption = assistantCaptionBuffer
                                    isAiSpeaking = true
                                }
                            } else {
                                currentCaption = text
                                isPatientSpeaking = !final
                                if (final) voiceTranscript.add("$patientFirstName: $text")
                            }
                        }
                    }
                },
                onReady = {
                    scope.launch(Dispatchers.Main.immediate) {
                        connectionStatus = "Connected · listening"
                        connectionError = null
                    }
                },
                onError = { message ->
                    scope.launch(Dispatchers.Main.immediate) {
                        connectionError = message
                        connectionStatus = "Connection unavailable"
                    }
                },
                persistTurn = { requestKey, input, output ->
                    onPersistTurn(session.sessionId, requestKey, input, output)
                },
                onVoiceActivity = { active ->
                    scope.launch(Dispatchers.Main.immediate) { isPatientSpeaking = active }
                }
            )
            liveClient = client
            client.connect()
        } catch (error: Exception) {
            liveClient?.close()
            liveClient = null
            connectionError = error.localizedMessage?.takeIf(String::isNotBlank)
                ?: "Could not connect to Gemini Live. Check the connection and retry."
            connectionStatus = "Connection unavailable"
        } finally {
            isStartingSession = false
        }
    }

    LaunchedEffect(phase) {
        while (phase == VirtualCallPhase.ACTIVE_CALL) {
            delay(1000L)
            elapsedSeconds += 1
        }
    }

    // The live client changes from null -> connecting -> connected while the
    // WebSocket handshake is still in progress. Keep cleanup keyed only to the
    // owning appointment/screen lifetime so those state updates cannot dispose
    // the client mid-handshake. Explicit hang-up and retry still close it below.
    DisposableEffect(appointment.id) {
        onDispose { liveClient?.close() }
    }

    val pulseTransition = rememberInfiniteTransition(label = "virtual_voice_aura")
    val auraPulse by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aura_pulse"
    )
    val wavePhase by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val mins = elapsedSeconds / 60
    val secs = elapsedSeconds % 60
    val formattedTimer = String.format("%02d:%02d", mins, secs)

    Box(modifier = Modifier.fillMaxSize()) {
        // Blurred ambient doctor background for immersive video-call stage
        Image(
            painter = painterResource(id = appointment.avatarRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(32.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xCC161922),
                            Color(0xD91F2430),
                            Color(0xF212151B)
                        )
                    )
                )
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .navigationBarsPadding()
            ) {
                // Top Video Call Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("appt_ai_virtual_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PlatinumWhite
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0x36FFFFFF))
                            .border(1.dp, Color(0x52FFFFFF), CircleShape)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (phase == VirtualCallPhase.ACTIVE_CALL) IceBlueText else SoftLavenderText
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (phase == VirtualCallPhase.ACTIVE_CALL) {
                                "AI VIRTUAL • $formattedTimer"
                            } else {
                                "SESSION SUMMARY"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = PlatinumWhite
                        )
                    }

                    Spacer(modifier = Modifier.width(48.dp))
                }

                AnimatedContent(
                    targetState = phase,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "virtual_phase",
                    modifier = Modifier.weight(1f)
                ) { currentPhase ->
                    when (currentPhase) {
                        VirtualCallPhase.ACTIVE_CALL -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Spacer(modifier = Modifier.height(6.dp))

                                // Video Feed Card featuring Doctor + AI Voice Agent Ring
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    VerityGlassCard(
                                        cornerRadius = 30.dp,
                                        strong = true,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            // Doctor video background inside stage
                                            Image(
                                                painter = painterResource(id = appointment.avatarRes),
                                                contentDescription = appointment.doctorName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .blur(10.dp)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        Brush.verticalGradient(
                                                            listOf(
                                                                Color(0x55181C24),
                                                                Color(0x99191D26),
                                                                Color(0xDD14171F)
                                                            )
                                                        )
                                                    )
                                            )

                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center,
                                                modifier = Modifier.padding(horizontal = 20.dp)
                                            ) {
                                                // Animated Voice Agent Ring around crisp Doctor Avatar
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier.size(164.dp)
                                                ) {
                                                    val ringScale = if (isAiSpeaking) 1f + 0.14f * auraPulse else 1f + 0.04f * auraPulse
                                                    Box(
                                                        modifier = Modifier
                                                            .size((146 * ringScale).dp)
                                                            .clip(CircleShape)
                                                            .background(
                                                                Brush.radialGradient(
                                                                    listOf(
                                                                        if (isAiSpeaking) IceBlueText.copy(alpha = 0.36f)
                                                                        else SoftLavenderText.copy(alpha = 0.22f),
                                                                        Color.Transparent
                                                                    )
                                                                )
                                                            )
                                                    )
                                                    Image(
                                                        painter = painterResource(id = appointment.avatarRes),
                                                        contentDescription = appointment.doctorName,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier
                                                            .size(122.dp)
                                                            .clip(CircleShape)
                                                            .border(
                                                                width = 2.dp,
                                                                brush = Brush.verticalGradient(
                                                                    listOf(
                                                                        if (isAiSpeaking) IceBlueText else SoftLavenderText,
                                                                        Color(0x70FFFFFF)
                                                                    )
                                                                ),
                                                                shape = CircleShape
                                                            )
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(12.dp))
                                                Text(
                                                    text = appointment.doctorName,
                                                    style = MaterialTheme.typography.headlineSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = PlatinumWhite,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${appointment.specialty} • AI Voice Agent",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = IceBlueText
                                                )
                                                Spacer(modifier = Modifier.height(14.dp))

                                                // Voice Agent Spectrum Visualizer
                                                VoiceAgentEqualizer(
                                                    isActive = isAiSpeaking || !isMuted,
                                                    isAiSpeaking = isAiSpeaking,
                                                    phase = wavePhase,
                                                    modifier = Modifier
                                                        .width(190.dp)
                                                        .height(34.dp)
                                                )

                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = when {
                                                        connectionError != null -> "Connection needs attention"
                                                        isMuted -> "Microphone muted"
                                                        isAiSpeaking -> "AI Agent speaking…"
                                                        isPatientSpeaking -> "Listening to you…"
                                                        else -> connectionStatus
                                                    },
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = if (isMuted) SilverText else SoftLavenderText
                                                )
                                            }

                                            // Live Captions Overlay inside Video Stage
                                            if (showCaptions) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomCenter)
                                                        .fillMaxWidth()
                                                        .padding(14.dp)
                                                        .clip(RoundedCornerShape(18.dp))
                                                        .background(Color(0xAA171B24))
                                                        .border(1.dp, Color(0x45FFFFFF), RoundedCornerShape(18.dp))
                                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                                ) {
                                                    Text(
                                            text = connectionError?.let { "$it\n$currentCaption" } ?: currentCaption,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = PlatinumWhite,
                                                        textAlign = TextAlign.Center,
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (connectionError != null && !isFinalizing && phase == VirtualCallPhase.ACTIVE_CALL) {
                                    TextButton(
                                        onClick = {
                                            liveClient?.close()
                                            liveClient = null
                                            connectionError = null
                                            if (!hasMicPermission) {
                                                connectionStatus = "Requesting microphone permission…"
                                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            } else {
                                                connectionAttempt += 1
                                            }
                                        },
                                        modifier = Modifier.align(Alignment.CenterHorizontally).testTag("virtual_call_retry_connection")
                                    ) { Text("Retry Gemini Live connection") }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Interactive Voice Conversation Prompts
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val voicePrompts = listOf(
                                        "I've had fever & headache for 2 days",
                                        "Check my morning BP readings",
                                        "Ask about medication timing"
                                    )
                                    items(voicePrompts) { prompt ->
                                        VerityGlassChip(
                                            text = prompt,
                                            highlighted = false,
                                            onClick = {
                                                liveClient?.sendText(prompt)
                                            }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Video Call Control Dock
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 18.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color(0x40FFFFFF), Color(0x20FFFFFF))
                                            ),
                                            CircleShape
                                        )
                                        .border(1.dp, Color(0x52FFFFFF), CircleShape)
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CallControlCircleButton(
                                        icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                                        label = if (isMuted) "Unmute" else "Mute",
                                        active = !isMuted,
                                        onClick = {
                                            if (!hasMicPermission) {
                                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                            isMuted = !isMuted
                                            liveClient?.setMuted(isMuted)
                                        },
                                        testTag = "virtual_call_mute"
                                    )
                                    CallControlCircleButton(
                                        icon = if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                        label = "Speaker",
                                        active = isSpeakerOn,
                                        onClick = {
                                            isSpeakerOn = !isSpeakerOn
                                            liveClient?.setPlaybackMuted(!isSpeakerOn)
                                        },
                                        testTag = "virtual_call_speaker"
                                    )
                                    CallControlCircleButton(
                                        icon = Icons.Filled.ClosedCaption,
                                        label = "Captions",
                                        active = showCaptions,
                                        onClick = { showCaptions = !showCaptions },
                                        testTag = "virtual_call_captions"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .defaultMinSize(minWidth = 56.dp, minHeight = 52.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFDC2626))
                                            .clickable {
                                                if (!isFinalizing) {
                                                    isFinalizing = true
                                                    liveClient?.close()
                                                    liveClient = null
                                                    scope.launch {
                                                        try {
                                                            val sessionId = consultationSessionId
                                                                ?: throw IllegalStateException("No active voice session was started.")
                                                            generatedSummary = onEndConsultation(sessionId)
                                                            sharedWithDoctor = true
                                                            onShareSummaryWithDoctor(generatedSummary.orEmpty())
                                                            phase = VirtualCallPhase.POST_CALL_SUMMARY
                                                        } catch (error: Exception) {
                                                            connectionError = error.localizedMessage
                                                                ?: "The transcript is saved, but summary finalization failed."
                                                            isFinalizing = false
                                                        }
                                                    }
                                                }
                                            }
                                            .padding(horizontal = 18.dp, vertical = 12.dp)
                                            .testTag("virtual_call_end"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CallEnd,
                                            contentDescription = "End Virtual Call",
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }

                        VirtualCallPhase.POST_CALL_SUMMARY -> {
                            val summaryText = generatedSummary ?: "The server has not returned a saved clinical summary yet. Retry ending the consultation."

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 20.dp, vertical = 18.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                VerityGlassCard(
                                    cornerRadius = 24.dp,
                                    strong = true,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Filled.AutoAwesome,
                                                contentDescription = null,
                                                tint = IceBlueText,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "AI Virtual Session Summary",
                                                style = MaterialTheme.typography.titleLarge,
                                                color = PlatinumWhite
                                            )
                                        }
                                        Text(
                                            text = "Duration: $formattedTimer • ${appointment.doctorName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SoftLavenderText
                                        )
                                        HorizontalDivider(color = Color(0x24FFFFFF))
                                        Text(
                                            text = summaryText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = PlatinumWhite
                                        )
                                    }
                                }

                                if (sharedWithDoctor) {
                                    VerityGlassCard(
                                        cornerRadius = 20.dp,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.CheckCircle,
                                                contentDescription = null,
                                                tint = IceBlueText,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = "Consultation summary saved for ${appointment.doctorName} to review.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = PlatinumWhite
                                            )
                                        }
                                    }
                                    VerityGlassPillButton(
                                        text = "Done",
                                        onClick = onBack,
                                        modifier = Modifier.testTag("virtual_call_done")
                                    )
                                } else {
                                    VerityGlassPillButton(
                                        text = if (isFinalizing) "Saving consultation…" else "Share Summary with Doctor",
                                        leadingIcon = Icons.Filled.Share,
                                        onClick = {
                                            if (!isFinalizing && generatedSummary != null) {
                                                sharedWithDoctor = true
                                                onShareSummaryWithDoctor(summaryText)
                                            }
                                        },
                                        modifier = Modifier.testTag("virtual_call_share_summary")
                                    )
                                    VerityGlassSecondaryPill(
                                        text = "Close",
                                        onClick = onBack,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (!voiceConsentAccepted) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("AI Virtual voice consent") },
            text = {
                Text("Your microphone audio and live transcript will be sent securely to the configured AI voice service to prepare a consultation draft for ${appointment.doctorName}. The transcript and summary are saved for clinician review. You can mute or end the call at any time.")
            },
            confirmButton = {
                TextButton(onClick = { voiceConsentAccepted = true }) { Text("I consent and continue") }
            },
            dismissButton = {
                TextButton(onClick = onBack) { Text("Not now") }
            }
        )
    }
}

@Composable
private fun VoiceAgentEqualizer(
    isActive: Boolean,
    isAiSpeaking: Boolean,
    phase: Float,
    modifier: Modifier = Modifier
) {
    val barTint = if (isAiSpeaking) IceBlueText else SoftLavenderText
    Canvas(modifier = modifier) {
        val barCount = 15
        val spacing = size.width / barCount
        val barWidth = (spacing * 0.48f).coerceAtLeast(3.dp.toPx())
        val centerY = size.height / 2f

        for (i in 0 until barCount) {
            val env = 1f - abs((i - (barCount - 1) / 2f) / (barCount / 2f)) * 0.55f
            val wave = if (isActive) {
                (0.28f + 0.72f * abs(sin(phase + i * 0.65f))) * env
            } else {
                0.18f
            }
            val barH = (size.height * wave).coerceAtLeast(4.dp.toPx())
            val x = i * spacing + (spacing - barWidth) / 2f
            drawRoundRect(
                color = barTint.copy(alpha = if (isActive) 0.92f else 0.42f),
                topLeft = Offset(x, centerY - barH / 2f),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
private fun CallControlCircleButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (active) Color(0x38FFFFFF) else Color(0x16FFFFFF))
                .border(
                    1.dp,
                    if (active) IceBlueText.copy(alpha = 0.7f) else Color(0x33FFFFFF),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) PlatinumWhite else SilverText,
                modifier = Modifier.size(21.dp)
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) PlatinumWhite else SilverText
        )
    }
}
