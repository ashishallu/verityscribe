package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AssistMessage
import com.example.model.AssistSession
import com.example.model.ClinicalAppointment
import com.example.model.DoctorHistoryRecord
import com.example.ui.theme.ClinicalBorder
import com.example.ui.theme.ClinicalSurface
import com.example.ui.theme.ClinicalSurfaceSubtle
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MedicalTeal
import com.example.ui.theme.OceanMistGradientStops
import com.example.ui.theme.OceanTealPrimary
import kotlinx.coroutines.delay

private val DarkBubbleSlate = OceanTealPrimary
private val EndRedPill = Color(0xFFD64545)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistSessionScreen(
    session: AssistSession,
    patient: ClinicalAppointment?,
    isGenerating: Boolean,
    onBack: () -> Unit,
    onEndSession: () -> Unit,
    onOpenReviewSummary: () -> Unit,
    onSendMessage: (String, String?) -> Unit,
    onSendVoiceNote: () -> Unit,
    modifier: Modifier = Modifier,
    historyRecord: DoctorHistoryRecord? = null
) {
    BackHandler(onBack = onBack)

    var replyText by remember { mutableStateOf("") }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showHistorySnapshotSheet by remember { mutableStateOf(false) }
    var playingMessageId by remember { mutableStateOf<String?>(null) }

    val displayName = patient?.patientName ?: historyRecord?.patientName ?: "Patient"
    val displayAge = patient?.age ?: historyRecord?.age ?: 0
    val displayMrn = patient?.mrn ?: historyRecord?.mrn ?: ""
    val isHistoryOnly = historyRecord != null && patient == null

    val listState = rememberLazyListState()
    LaunchedEffect(session.messages.size, isGenerating) {
        if (session.messages.isNotEmpty()) {
            listState.animateScrollToItem(session.messages.size - 1)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "chrome_knot")
    val knotRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "knot_spin"
    )
    val knotScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "knot_pulse"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = OceanMistGradientStops
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // 1. Top Header on Ocean Teal Top Zone
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack)
                        .testTag("assist_session_back_button"),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.22f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.38f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to Assist",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append("Age: $displayAge | ID: $displayMrn")
                            if (historyRecord != null && isHistoryOnly) {
                                append(" • ${historyRecord.visitDate}")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp
                        ),
                        color = Color.White.copy(alpha = 0.88f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(onClick = onEndSession)
                            .testTag("assist_session_end_button"),
                        shape = RoundedCornerShape(999.dp),
                        color = EndRedPill
                    ) {
                        Text(
                            text = "End",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            ),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable {
                                if (isHistoryOnly) {
                                    showHistorySnapshotSheet = true
                                } else {
                                    onOpenReviewSummary()
                                }
                            }
                            .testTag("assist_session_summary_button"),
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.38f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.AssignmentTurnedIn,
                                contentDescription = if (isHistoryOnly) {
                                    "View historical encounter snapshot"
                                } else {
                                    "Open structured AI draft review"
                                },
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // 2. Chat Stream
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(session.messages, key = { it.id }) { msg ->
                    if (msg.isVoiceNote) {
                        VoiceWaveformBubble(
                            message = msg,
                            isPlaying = playingMessageId == msg.id,
                            onTogglePlay = {
                                playingMessageId = if (playingMessageId == msg.id) null else msg.id
                            }
                        )
                    } else {
                        SessionChatBubble(message = msg)
                    }
                }

                // Centered 3D Metallic Chrome Knot & Draft Summary Status matching Screenshot 2
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_ai_chrome_knot),
                            contentDescription = "Clinical AI synthesis indicator",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(92.dp)
                                .graphicsLayer {
                                    if (isGenerating) {
                                        rotationZ = knotRotation
                                        scaleX = knotScale
                                        scaleY = knotScale
                                    }
                                }
                                .clip(CircleShape)
                                .clickable {
                                    if (isHistoryOnly) {
                                        showHistorySnapshotSheet = true
                                    } else {
                                        onOpenReviewSummary()
                                    }
                                }
                        )
                        Text(
                            text = when {
                                isGenerating && isHistoryOnly -> "Querying historical chart..."
                                isGenerating -> "AI Draft Summary generating..."
                                isHistoryOnly -> "Historical record loaded • Tap emblem for summary"
                                else -> "AI Draft Summary ready • Tap emblem to review"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = InkSecondary
                        )
                    }
                }
            }

            // 3. Frosted Glass Bottom Pill Composer
            val composerShape = RoundedCornerShape(999.dp)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .height(54.dp)
                    .clip(composerShape)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.76f),
                                Color.White.copy(alpha = 0.54f)
                            )
                        ),
                        shape = composerShape
                    )
                    .border(
                        width = 1.2.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.96f),
                                Color.White.copy(alpha = 0.52f)
                            )
                        ),
                        shape = composerShape
                    ),
                shape = composerShape,
                color = Color.Transparent,
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 8.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showAttachmentSheet = true },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("assist_session_attach_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AttachFile,
                            contentDescription = "Attach report",
                            tint = InkMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        if (replyText.isEmpty()) {
                            Text(
                                text = if (isHistoryOnly) {
                                    "Ask about $displayName's history..."
                                } else {
                                    "Reply to $displayName..."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = InkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = MaterialTheme.typography.bodyMedium.fontFamily,
                                fontSize = 14.sp,
                                color = InkPrimary
                            ),
                            cursorBrush = SolidColor(MedicalTeal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("assist_session_reply_input")
                        )
                    }

                    IconButton(
                        onClick = {
                            if (replyText.isNotBlank()) {
                                onSendMessage(replyText, null)
                                replyText = ""
                            }
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("assist_session_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Send,
                            contentDescription = "Send message",
                            tint = if (replyText.isNotBlank()) DarkBubbleSlate else InkMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Surface(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onSendVoiceNote)
                            .testTag("assist_session_mic_button"),
                        shape = CircleShape,
                        color = DarkBubbleSlate
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Mic,
                                contentDescription = "Record voice note",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAttachmentSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAttachmentSheet = false },
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
                Text(
                    text = "Attach Patient Report",
                    style = MaterialTheme.typography.headlineSmall,
                    color = InkPrimary
                )
                val reports = patient?.reports.orEmpty()
                if (reports.isEmpty() && historyRecord != null) {
                    val snapshotTitle = "Encounter Snapshot • ${historyRecord.visitDate}"
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showAttachmentSheet = false
                                onSendMessage("Summarise encounter snapshot for ${historyRecord.patientName}", snapshotTitle)
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = ClinicalSurfaceSubtle,
                        border = BorderStroke(1.dp, ClinicalBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Description,
                                contentDescription = null,
                                tint = MedicalTeal
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(snapshotTitle, style = MaterialTheme.typography.titleSmall, color = InkPrimary)
                                Text(historyRecord.diagnosisSummary, style = MaterialTheme.typography.bodySmall, color = InkMuted)
                            }
                        }
                    }
                } else if (reports.isEmpty()) {
                    Text(
                        text = "No uploaded reports for $displayName yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted
                    )
                } else {
                    reports.forEach { report ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    showAttachmentSheet = false
                                    onSendMessage("Analyze ${report.title}", report.title)
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = ClinicalSurfaceSubtle,
                            border = BorderStroke(1.dp, ClinicalBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Description,
                                    contentDescription = null,
                                    tint = MedicalTeal
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(report.title, style = MaterialTheme.typography.titleSmall, color = InkPrimary)
                                    Text("${report.category} • ${report.date}", style = MaterialTheme.typography.bodySmall, color = InkMuted)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showHistorySnapshotSheet && historyRecord != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showHistorySnapshotSheet = false },
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
                Text(
                    text = "${historyRecord.patientName} • ${historyRecord.visitDate}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = InkPrimary
                )
                Text(
                    text = "${historyRecord.ageLabel} • ${historyRecord.gender} • ${historyRecord.mrn} • ${historyRecord.consultationType.label}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = OceanTealPrimary
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ClinicalSurfaceSubtle,
                    border = BorderStroke(1.dp, ClinicalBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = historyRecord.diagnosisSummary,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = InkPrimary
                        )
                        Text(
                            text = historyRecord.clinicalOutcome,
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSecondary
                        )
                        Text(
                            text = "Vitals: ${historyRecord.vitalsSummary}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = InkMuted
                        )
                        if (historyRecord.medicationsPrescribed.isNotEmpty()) {
                            Text(
                                text = "Regimen: ${historyRecord.medicationsPrescribed.joinToString(" | ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionChatBubble(message: AssistMessage) {
    val isRight = message.isFromDoctor
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isRight) Arrangement.End else Arrangement.Start
    ) {
        val bubbleShape = RoundedCornerShape(
            topStart = 18.dp,
            topEnd = 18.dp,
            bottomStart = if (isRight) 18.dp else 4.dp,
            bottomEnd = if (isRight) 4.dp else 18.dp
        )
        Surface(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .clip(bubbleShape)
                .background(
                    brush = if (isRight) {
                        Brush.verticalGradient(
                            colors = listOf(
                                DarkBubbleSlate.copy(alpha = 0.92f),
                                DarkBubbleSlate.copy(alpha = 0.80f)
                            )
                        )
                    } else {
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.72f),
                                Color.White.copy(alpha = 0.50f)
                            )
                        )
                    },
                    shape = bubbleShape
                )
                .border(
                    width = 1.1.dp,
                    color = if (isRight) Color.White.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.88f),
                    shape = bubbleShape
                ),
            shape = bubbleShape,
            color = Color.Transparent,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (message.attachmentName != null) {
                    Text(
                        text = "Attached: ${message.attachmentName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isRight) Color.White.copy(alpha = 0.78f) else MedicalTeal
                    )
                }
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.5.sp,
                        lineHeight = 20.sp
                    ),
                    color = if (isRight) Color.White else InkPrimary
                )
            }
        }
    }
}

@Composable
private fun VoiceWaveformBubble(
    message: AssistMessage,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit
) {
    val barHeights = remember {
        listOf(8, 16, 12, 22, 14, 10, 26, 18, 11, 24, 16, 19, 28, 14, 10, 22, 16, 12, 24, 18, 13, 20, 11, 15, 8)
    }
    var activeBarIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            for (i in barHeights.indices) {
                activeBarIndex = i
                delay(180)
            }
            activeBarIndex = -1
            onTogglePlay()
        } else {
            activeBarIndex = -1
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 240.dp, max = 295.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onTogglePlay)
                .testTag("voice_waveform_bubble_${message.id}"),
            shape = RoundedCornerShape(16.dp),
            color = DarkBubbleSlate
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = CircleShape,
                    color = Color.White
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause voice note" else "Play voice note",
                            tint = DarkBubbleSlate,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.5.dp)
                ) {
                    barHeights.forEachIndexed { idx, h ->
                        val highlighted = idx <= activeBarIndex
                        Box(
                            modifier = Modifier
                                .width(2.5.dp)
                                .height(h.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (highlighted) Color.White else Color.White.copy(alpha = 0.45f)
                                )
                        )
                    }
                }

                Text(
                    text = message.voiceDuration ?: "0:15",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}
