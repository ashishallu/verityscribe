package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.example.ui.components.VerityGlassBackdrop
import com.example.ui.components.VerityGlassCard
import com.example.ui.components.VerityGlassPillButton
import com.example.ui.components.VerityGlassSecondaryPill
import com.example.ui.theme.GlassAlertRed
import com.example.ui.theme.IceBlueText
import com.example.ui.theme.PlatinumWhite
import com.example.ui.theme.SilverText
import com.example.ui.theme.SoftLavenderText
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

private enum class InPersonPhase {
    RECORDING,
    SUMMARIZING,
    SUMMARY
}

@Composable
fun AppointmentInPersonScreen(
    appointment: Appointment,
    isDark: Boolean,
    onBack: () -> Unit,
    onUploadRecording: (filePath: String, onResult: (String?, String?, String?) -> Unit) -> Unit,
    onSaveTranscriptCorrection: (transcriptId: String, text: String, onResult: (String?) -> Unit) -> Unit
) {
    val context = LocalContext.current

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var phase by remember { mutableStateOf(InPersonPhase.RECORDING) }
    var isRecording by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var showConsentDialog by remember { mutableStateOf(true) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var transcript by remember { mutableStateOf("") }
    var transcriptId by remember { mutableStateOf<String?>(null) }
    var recordingStatus by remember { mutableStateOf<String?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var isSavingCorrection by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun beginRecording() {
        runCatching {
            val file = File(context.cacheDir, "appointment-${appointment.id}-${System.currentTimeMillis()}.m4a")
            val activeRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            activeRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            activeRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            activeRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            activeRecorder.setAudioSamplingRate(16_000)
            activeRecorder.setAudioEncodingBitRate(64_000)
            activeRecorder.setOutputFile(file.absolutePath)
            activeRecorder.prepare()
            activeRecorder.start()
            recorder = activeRecorder
            recordingFile = file
            isRecording = true
            errorMessage = null
        }.onFailure { error ->
            runCatching { recorder?.release() }
            recorder = null
            isRecording = false
            errorMessage = "Microphone recording could not start: ${error.localizedMessage ?: "check microphone access"}"
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) beginRecording() else errorMessage = "Microphone permission is required to record this consultation."
    }

    fun stopAndUploadRecording() {
        if (!hasMicPermission || recordingFile == null) {
            errorMessage = "No consultation audio was recorded."
            return
        }
        val file = recordingFile ?: return
        runCatching { recorder?.stop() }.onFailure { error ->
            errorMessage = "The recording could not be finalized: ${error.localizedMessage ?: "try recording again"}"
        }
        runCatching { recorder?.release() }
        recorder = null
        isRecording = false
        phase = InPersonPhase.SUMMARIZING
        isUploading = true
        onUploadRecording(file.absolutePath) { recognizedText, id, status ->
            isUploading = false
            if (recognizedText.isNullOrBlank()) {
                errorMessage = status?.takeIf { it.contains("failed", true) || it.contains("could not", true) }
                    ?: "No transcript was returned. The recording is retained locally; please retry or contact your care team."
                phase = InPersonPhase.SUMMARY
            } else {
                transcript = recognizedText
                transcriptId = id
                recordingStatus = status
                errorMessage = null
                phase = InPersonPhase.SUMMARY
            }
        }
    }

    BackHandler {
        onBack()
    }

    LaunchedEffect(phase, isRecording) {
        while (phase == InPersonPhase.RECORDING && isRecording) {
            delay(1000L)
            elapsedSeconds += 1
        }
    }

    val latestRecorder by rememberUpdatedState(recorder)
    DisposableEffect(Unit) {
        onDispose {
            runCatching { latestRecorder?.stop() }
            runCatching { latestRecorder?.release() }
        }
    }

    if (showConsentDialog) {
        AlertDialog(
            onDismissRequest = { showConsentDialog = false; onBack() },
            title = { Text("Consent to record") },
            text = { Text("This in-person consultation audio will be uploaded securely for transcription and clinician review. The transcript may contain recognition errors. Recording starts only after microphone permission is granted.") },
            confirmButton = {
                TextButton(onClick = {
                    showConsentDialog = false
                    if (hasMicPermission) beginRecording()
                    else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text("I consent") }
            },
            dismissButton = {
                TextButton(onClick = { showConsentDialog = false; onBack() }) { Text("Not now") }
            }
        )
    }

    val liveTransition = rememberInfiniteTransition(label = "inperson_ambient_recorder")
    val wavePhase by liveTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "inperson_wave_phase"
    )
    val rippleProgress by liveTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "inperson_ripple"
    )
    val dotPulse by liveTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "inperson_rec_dot"
    )

    val mins = elapsedSeconds / 60
    val secs = elapsedSeconds % 60
    val formattedTimer = String.format("%02d:%02d", mins, secs)

    VerityGlassBackdrop(isObsidianMode = isDark) {
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
                // Translucent frosted top bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xD90E1015), Color(0x99171A21))
                            )
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("inperson_back")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = PlatinumWhite
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "In-Person Consultation",
                                style = MaterialTheme.typography.titleLarge,
                                color = PlatinumWhite
                            )
                            Text(
                                text = "${appointment.doctorName} • ${appointment.hospital}",
                                style = MaterialTheme.typography.bodySmall,
                                color = IceBlueText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0x28FFFFFF))
                                .border(1.dp, Color(0x45FFFFFF), CircleShape)
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isRecording && phase == InPersonPhase.RECORDING) {
                                            GlassAlertRed.copy(alpha = dotPulse)
                                        } else {
                                            SilverText
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isRecording && phase == InPersonPhase.RECORDING) "REC" else "PAUSED",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isRecording && phase == InPersonPhase.RECORDING) PlatinumWhite else SilverText
                            )
                        }
                    }
                    HorizontalDivider(
                        color = Color(0x26FFFFFF),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }

                AnimatedContent(
                    targetState = phase,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "inperson_phase",
                    modifier = Modifier.weight(1f)
                ) { currentPhase ->
                    when (currentPhase) {
                        InPersonPhase.RECORDING -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Hero Animated Acoustic Orb Stage
                                VerityGlassCard(
                                    cornerRadius = 28.dp,
                                    strong = true,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 22.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        // Top live mode badge inside card
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(Color(0x24FFFFFF))
                                                .border(1.dp, Color(0x3DFFFFFF), CircleShape)
                                                .padding(horizontal = 12.dp, vertical = 5.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.GraphicEq,
                                                contentDescription = null,
                                                tint = if (isRecording) IceBlueText else SilverText,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (isRecording) "AMBIENT AUDIO ACTIVE" else "RECORDING PAUSED",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isRecording) IceBlueText else SilverText
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))

                                        // Centered Mic Orb + Mathematically Concentric Ripple Rings
                                        Box(
                                            modifier = Modifier.size(204.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                val center = Offset(size.width / 2f, size.height / 2f)
                                                val baseRadius = 52.dp.toPx()
                                                val maxRadius = (size.minDimension / 2f) - 4.dp.toPx()

                                                if (isRecording) {
                                                    for (ringIdx in 0 until 3) {
                                                        val p = (rippleProgress + ringIdx / 3f) % 1f
                                                        val r = baseRadius + (maxRadius - baseRadius) * p
                                                        val alpha = (1f - p) * 0.55f
                                                        val ringColor = if (ringIdx % 2 == 0) IceBlueText else SoftLavenderText
                                                        drawCircle(
                                                            color = ringColor.copy(alpha = alpha),
                                                            radius = r,
                                                            center = center,
                                                            style = Stroke(width = 2.dp.toPx())
                                                        )
                                                    }
                                                } else {
                                                    drawCircle(
                                                        color = SilverText.copy(alpha = 0.20f),
                                                        radius = baseRadius + 16.dp.toPx(),
                                                        center = center,
                                                        style = Stroke(width = 1.5.dp.toPx())
                                                    )
                                                }
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .size(104.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        Brush.radialGradient(
                                                            listOf(
                                                                if (isRecording) IceBlueText.copy(alpha = 0.30f)
                                                                else Color(0x24FFFFFF),
                                                                Color(0xD9242934)
                                                            )
                                                        )
                                                    )
                                                    .border(
                                                        width = 1.6.dp,
                                                        brush = Brush.verticalGradient(
                                                            listOf(
                                                                if (isRecording) IceBlueText else Color(0x52FFFFFF),
                                                                if (isRecording) SoftLavenderText else Color(0x28FFFFFF)
                                                            )
                                                        ),
                                                        shape = CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (isRecording) Icons.Filled.Mic else Icons.Filled.MicOff,
                                                    contentDescription = if (isRecording) "Recording audio" else "Recording paused",
                                                    tint = if (isRecording) PlatinumWhite else SilverText,
                                                    modifier = Modifier.size(40.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        Text(
                                            text = formattedTimer,
                                            style = MaterialTheme.typography.displaySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = PlatinumWhite
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Text(
                                            text = if (isRecording) {
                                                "Recording consultation audio"
                                            } else {
                                                "Consultation recording paused"
                                            },
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (isRecording) IceBlueText else SilverText,
                                            textAlign = TextAlign.Center
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Text(
                                            text = if (hasMicPermission) {
                                                "Audio is encrypted and summarized once you tap Stop"
                                            } else {
                                                "Tap Resume to grant microphone access"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SilverText,
                                            textAlign = TextAlign.Center
                                        )

                                        Spacer(modifier = Modifier.height(18.dp))

                                        // Frosted Inset Acoustic Spectrum Bar
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(18.dp))
                                                .background(Color(0x1AFFFFFF))
                                                .border(1.dp, Color(0x30FFFFFF), RoundedCornerShape(18.dp))
                                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                        ) {
                                            LivelyAcousticSpectrum(
                                                isRecording = isRecording,
                                                phase = wavePhase,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(54.dp)
                                            )
                                        }
                                    }
                                }

                                // Visit Context & Capabilities Bento Strip
                                VerityGlassCard(
                                    cornerRadius = 22.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Image(
                                                painter = painterResource(id = appointment.avatarRes),
                                                contentDescription = appointment.doctorName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .clip(CircleShape)
                                                    .border(1.dp, IceBlueText.copy(alpha = 0.65f), CircleShape)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "${appointment.doctorName} • ${appointment.specialty}",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = PlatinumWhite,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = appointment.notes,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = SilverText,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            AmbientFeaturePill(
                                                icon = Icons.Filled.GraphicEq,
                                                label = "Ambient Audio",
                                                tint = IceBlueText,
                                                modifier = Modifier.weight(1f)
                                            )
                                            AmbientFeaturePill(
                                                icon = Icons.Filled.Lock,
                                                label = "Private",
                                                tint = SoftLavenderText,
                                                modifier = Modifier.weight(0.85f)
                                            )
                                            AmbientFeaturePill(
                                                icon = Icons.Filled.AutoAwesome,
                                                label = "Auto Summary",
                                                tint = IceBlueText,
                                                modifier = Modifier.weight(1.05f)
                                            )
                                        }
                                    }
                                }

                                errorMessage?.let { message ->
                                    Text(message, color = GlassAlertRed, style = MaterialTheme.typography.bodySmall)
                                }
                                recordingStatus?.let { status ->
                                    Text("Transcription status: $status", color = SoftLavenderText, style = MaterialTheme.typography.bodySmall)
                                }

                                // Pause/resume and upload controls remain visually unchanged.
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(0.9f)
                                            .defaultMinSize(minHeight = 54.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(Color(0x36FFFFFF), Color(0x18FFFFFF))
                                                ),
                                                CircleShape
                                            )
                                            .border(1.dp, Color(0x52FFFFFF), CircleShape)
                                            .clickable {
                                                if (isRecording) {
                                                    runCatching { recorder?.pause() }
                                                        .onSuccess { isRecording = false }
                                                        .onFailure { errorMessage = "Recording could not be paused." }
                                                } else if (recorder != null) {
                                                    runCatching { recorder?.resume() }
                                                        .onSuccess { isRecording = true }
                                                        .onFailure { errorMessage = "Recording could not be resumed." }
                                                } else if (hasMicPermission) {
                                                    beginRecording()
                                                } else {
                                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                }
                                            }
                                            .padding(horizontal = 14.dp)
                                            .testTag("inperson_pause_resume"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (isRecording) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                                contentDescription = if (isRecording) "Pause recording" else "Resume recording",
                                                tint = PlatinumWhite,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (isRecording) "Pause" else "Resume",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = PlatinumWhite
                                            )
                                        }
                                    }

                                    VerityGlassPillButton(
                                        text = if (isUploading) "Uploading & Transcribing…" else "Stop & Transcribe",
                                        leadingIcon = Icons.Filled.Stop,
                                        minHeight = 54.dp,
                                        onClick = { if (!isUploading) stopAndUploadRecording() },
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .testTag("inperson_stop_summarize")
                                    )
                                }
                            }
                        }

                        InPersonPhase.SUMMARIZING -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                VerityGlassCard(
                                    cornerRadius = 26.dp,
                                    strong = true,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(28.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            color = IceBlueText,
                                            strokeWidth = 3.dp,
                                            modifier = Modifier.size(38.dp)
                                        )
                                        Text(
                                            text = if (isUploading) "Transcribing Consultation…" else "Preparing transcript…",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = PlatinumWhite
                                        )
                                        Text(
                                            text = "The recording is being processed by the consultation transcription service. No clinical summary is inferred from the audio here.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SilverText,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        InPersonPhase.SUMMARY -> {
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
                                                text = "Consultation Transcript",
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                color = PlatinumWhite
                                            )
                                        }
                                        Text(
                                            text = "${appointment.doctorName} • ${appointment.hospital} • $formattedTimer recorded",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SoftLavenderText
                                        )
                                        HorizontalDivider(color = Color(0x24FFFFFF))
                                        HorizontalDivider(color = Color(0x1EFFFFFF))
                                        Text(
                                            text = "Speech recognition draft — verify before relying on it",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = IceBlueText
                                        )
                                        if (transcript.isNotBlank()) {
                                            BasicTextField(
                                                value = transcript,
                                                onValueChange = { transcript = it },
                                                modifier = Modifier.fillMaxWidth().testTag("inperson_transcript_editor"),
                                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = PlatinumWhite),
                                                decorationBox = { inner ->
                                                    Box {
                                                        if (transcript.isBlank()) Text("Transcript", color = SilverText)
                                                        inner()
                                                    }
                                                }
                                            )
                                        } else {
                                            Text(
                                                text = errorMessage ?: "A transcript was not returned. No clinical findings have been generated.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = GlassAlertRed
                                            )
                                        }
                                        Text(
                                            text = "The recording/transcript is submitted for clinician review; this screen does not create a diagnosis or treatment plan.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SoftLavenderText
                                        )
                                    }
                                }

                                if (transcriptId != null && transcript.isNotBlank()) {
                                    VerityGlassCard(
                                        cornerRadius = 20.dp,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Lock,
                                                contentDescription = null,
                                                tint = IceBlueText,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = "Saved for clinician review",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = PlatinumWhite
                                                )
                                                Text(
                                                    text = "This is a draft transcript, not an approved medical record.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = SilverText
                                                )
                                            }
                                        }
                                    }
                                    VerityGlassPillButton(
                                        text = if (isSavingCorrection) "Saving correction…" else "Save transcript correction",
                                        onClick = {
                                            if (!isSavingCorrection) {
                                                isSavingCorrection = true
                                                onSaveTranscriptCorrection(transcriptId!!, transcript) { error ->
                                                    isSavingCorrection = false
                                                    errorMessage = error ?: "Transcript correction saved for clinician review."
                                                }
                                            }
                                        },
                                        modifier = Modifier.testTag("inperson_save_correction")
                                    )
                                    VerityGlassPillButton(
                                        text = "Done",
                                        onClick = onBack,
                                        modifier = Modifier.testTag("inperson_done")
                                    )
                                } else {
                                    VerityGlassSecondaryPill(
                                        text = "Back to consultation",
                                        onClick = { phase = InPersonPhase.RECORDING },
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
}

@Composable
private fun LivelyAcousticSpectrum(
    isRecording: Boolean,
    phase: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val barCount = 34
        val spacing = size.width / barCount
        val barWidth = (spacing * 0.50f).coerceAtLeast(3.dp.toPx())
        val centerY = size.height / 2f

        for (i in 0 until barCount) {
            val edgeFade = 1f - abs((i - (barCount - 1) / 2f) / (barCount / 2f)) * 0.45f
            val amp = if (isRecording) {
                val w1 = abs(sin(phase * 1.4f + i * 0.42f))
                val w2 = abs(cos(phase * 0.9f - i * 0.27f))
                (0.20f + 0.80f * (w1 * 0.6f + w2 * 0.4f)) * edgeFade
            } else {
                0.14f * edgeFade
            }
            val barH = (size.height * amp).coerceIn(4.dp.toPx(), size.height * 0.92f)
            val x = i * spacing + (spacing - barWidth) / 2f
            val tint = if (i % 3 == 0) SoftLavenderText else IceBlueText
            drawRoundRect(
                color = tint.copy(alpha = if (isRecording) 0.90f else 0.35f),
                topLeft = Offset(x, centerY - barH / 2f),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
private fun AmbientFeaturePill(
    icon: ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0x1CFFFFFF))
            .border(1.dp, Color(0x30FFFFFF), CircleShape)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = PlatinumWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun InPersonSummaryBlock(label: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = IceBlueText
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = PlatinumWhite
        )
    }
}
