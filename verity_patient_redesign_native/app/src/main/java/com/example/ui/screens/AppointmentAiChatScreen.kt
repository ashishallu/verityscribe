package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.Appointment
import com.example.model.AiConsultationSnapshot
import com.example.ui.components.VerityGlassCard
import com.example.ui.components.VerityGlassChip
import com.example.ui.components.VerityGlassPillButton
import com.example.ui.components.VerityGlassSecondaryPill
import com.example.ui.theme.IceBlueText
import com.example.ui.theme.PlatinumWhite
import com.example.ui.theme.SilverText
import com.example.ui.theme.SlateInkDark
import com.example.ui.theme.SoftLavenderText
import java.util.UUID

@Immutable
private data class IntakeChatBubble(
    val id: String,
    val isUser: Boolean,
    val text: String
)

private enum class AiChatPhase {
    CHATTING,
    SUMMARY
}

@Composable
fun AppointmentAiChatScreen(
    appointment: Appointment,
    patientFirstName: String,
    onBack: () -> Unit,
    onStartConsultation: (Boolean, (AiConsultationSnapshot?, String?) -> Unit) -> Unit,
    onSendMessage: (String, String, (String?, String?) -> Unit) -> Unit,
    onEndConsultation: ((String?, String?) -> Unit) -> Unit,
    onShareSummaryWithDoctor: (summaryText: String) -> Unit
) {
    val listState = rememberLazyListState()

    val messages = remember(appointment.id) { mutableStateListOf<IntakeChatBubble>() }

    var draft by remember { mutableStateOf("") }
    var isAiTyping by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(AiChatPhase.CHATTING) }
    var sharedWithDoctor by remember { mutableStateOf(false) }
    var finalSummary by remember { mutableStateOf<String?>(null) }
    var finalizationError by remember { mutableStateOf<String?>(null) }
    var isFinalizing by remember { mutableStateOf(false) }
    var pendingRequest by remember { mutableStateOf<Pair<String, String>?>(null) }
    var hasExplicitConsent by remember { mutableStateOf(false) }
    var isStartingSession by remember { mutableStateOf(false) }
    var sessionCanAcceptMessages by remember { mutableStateOf(false) }

    val userTurnCount = messages.count { it.isUser }

    BackHandler {
        if (phase == AiChatPhase.SUMMARY && !sharedWithDoctor) {
            phase = AiChatPhase.CHATTING
        } else {
            onBack()
        }
    }

    LaunchedEffect(messages.size, isAiTyping) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    fun sendMessage(rawText: String) {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty() || isAiTyping || !hasExplicitConsent || !sessionCanAcceptMessages) return
        messages.add(
            IntakeChatBubble(
                id = "u_${UUID.randomUUID()}",
                isUser = true,
                text = trimmed
            )
        )
        draft = ""
        isAiTyping = true

        val requestKey = pendingRequest?.takeIf { it.first == trimmed }?.second ?: UUID.randomUUID().toString()
        pendingRequest = trimmed to requestKey
        onSendMessage(trimmed, requestKey) { reply, error ->
            if (reply != null) pendingRequest = null
            messages.add(IntakeChatBubble(
                id = "a_${UUID.randomUUID()}",
                isUser = false,
                text = reply ?: error ?: "The AI reply is temporarily unavailable. Please try again shortly."
            ))
            isAiTyping = false
        }
    }

    val quickPrompts = remember(userTurnCount) {
        when (userTurnCount) {
            0 -> listOf("I have fever and headache", "Persistent fatigue", "Chest tightness / BP spike")
            1 -> listOf("Since 3 days, highest was 101.8°F", "Started 2 days ago", "Mild but constant")
            else -> listOf("Took paracetamol, mild body ache", "No other symptoms")
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Blurred doctor portrait background
        Image(
            painter = painterResource(id = appointment.avatarRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(26.dp)
        )
        // Smoky frosted slate gradient scrim for crisp readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xC4161920),
                            Color(0xD41D212B),
                            Color(0xEE14171D)
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
                    .imePadding()
            ) {
                // Top frosted bar with Doctor info + End Conversation button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xCC11141B), Color(0x881A1E27))
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
                            onClick = {
                                if (phase == AiChatPhase.SUMMARY && !sharedWithDoctor) {
                                    phase = AiChatPhase.CHATTING
                                } else {
                                    onBack()
                                }
                            },
                            modifier = Modifier.testTag("appt_ai_chat_back")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = PlatinumWhite
                            )
                        }
                        Image(
                            painter = painterResource(id = appointment.avatarRes),
                            contentDescription = appointment.doctorName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .border(1.dp, Color(0x70FFFFFF), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = appointment.doctorName,
                                style = MaterialTheme.typography.titleMedium,
                                color = PlatinumWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "AI Chat • ${appointment.specialty}",
                                style = MaterialTheme.typography.labelSmall,
                                color = IceBlueText,
                                maxLines = 1
                            )
                        }
                        if (phase == AiChatPhase.CHATTING) {
                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color(0x3D93C5FD), Color(0x24C4B5FD))
                                        ),
                                        CircleShape
                                    )
                                    .border(1.dp, Color(0x66FFFFFF), CircleShape)
                                    .clickable(enabled = !isFinalizing && !isAiTyping && sessionCanAcceptMessages) {
                                        isFinalizing = true
                                        onEndConsultation { summaryText, error ->
                                            finalSummary = summaryText
                                            finalizationError = error
                                            isFinalizing = false
                                            phase = AiChatPhase.SUMMARY
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag("appt_ai_chat_end_conversation"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "End Conversation",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PlatinumWhite
                                )
                            }
                        }
                    }
                    HorizontalDivider(
                        color = Color(0x28FFFFFF),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }

                AnimatedContent(
                    targetState = phase,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "ai_chat_phase",
                    modifier = Modifier.weight(1f)
                ) { currentPhase ->
                    when (currentPhase) {
                        AiChatPhase.CHATTING -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                finalizationError?.let { message ->
                                    Text(
                                        text = message,
                                        color = Color(0xFFFFC078),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                                    )
                                }
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(messages, key = { it.id }) { msg ->
                                        IntakeChatBubbleRow(msg)
                                    }
                                    if (isAiTyping) {
                                        item {
                                            IntakeChatBubbleRow(
                                                IntakeChatBubble(
                                                    id = "typing",
                                                    isUser = false,
                                                    text = "Analyzing symptoms..."
                                                )
                                            )
                                        }
                                    }
                                }

                                // Quick reply suggestions
                            if (sessionCanAcceptMessages && userTurnCount < 3) {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    ) {
                                        items(quickPrompts) { prompt ->
                                            VerityGlassChip(
                                                text = prompt,
                                                highlighted = false,
                                                onClick = { sendMessage(prompt) }
                                            )
                                        }
                                    }
                                }

                                // Frosted composer bar
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 52.dp)
                                            .clip(RoundedCornerShape(26.dp))
                                            .background(Color(0x36FFFFFF))
                                            .border(1.dp, Color(0x52FFFFFF), RoundedCornerShape(26.dp))
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            if (draft.isEmpty()) {
                                                Text(
                                                    text = "Describe your symptoms...",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = SilverText.copy(alpha = 0.8f)
                                                )
                                            }
                                            BasicTextField(
                                                value = draft,
                                                onValueChange = { draft = it },
                                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                                    color = PlatinumWhite
                                                ),
                                                cursorBrush = SolidColor(IceBlueText),
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                                keyboardActions = KeyboardActions(onSend = { sendMessage(draft) }),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("appt_ai_chat_input")
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (draft.isNotBlank()) PlatinumWhite else Color(0x2EFFFFFF)
                                            )
                                            .border(1.dp, Color(0x52FFFFFF), CircleShape)
                                            .clickable(enabled = draft.isNotBlank() && sessionCanAcceptMessages) { sendMessage(draft) }
                                            .testTag("appt_ai_chat_send"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Send",
                                            tint = if (draft.isNotBlank() && sessionCanAcceptMessages) SlateInkDark else PlatinumWhite,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        AiChatPhase.SUMMARY -> {
                            val summary = remember(messages.size, finalSummary, finalizationError) {
                                if (finalSummary != null) {
                                    AiIntakeSummary(
                                        chiefComplaint = "See the generated summary below",
                                        durationAndPeak = "See the generated summary below",
                                        additionalNotes = "Clinician review required",
                                        narrative = finalSummary!!
                                    )
                                } else {
                                    AiIntakeSummary(
                                        chiefComplaint = "Not generated",
                                        durationAndPeak = "Not generated",
                                        additionalNotes = "Clinician review required",
                                        narrative = finalizationError ?: "The summary is not available yet."
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 20.dp, vertical = 20.dp),
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
                                                text = "Pre-Visit AI Summary",
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                color = PlatinumWhite
                                            )
                                        }
                                        Text(
                                            text = "Prepared for ${appointment.doctorName} (${appointment.hospital})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SoftLavenderText
                                        )
                                        HorizontalDivider(color = Color(0x24FFFFFF))
                                        SummarySectionRow("Chief Complaint", summary.chiefComplaint)
                                        SummarySectionRow("Timeline & Severity", summary.durationAndPeak)
                                        SummarySectionRow("Additional Notes", summary.additionalNotes)
                                        HorizontalDivider(color = Color(0x1EFFFFFF))
                                        Text(
                                            text = "Concise Clinical Summary",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = IceBlueText
                                        )
                                        Text(
                                            text = summary.narrative,
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
                                            Column {
                                                Text(
                                                    text = "Shared with ${appointment.doctorName}",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = PlatinumWhite
                                                )
                                                Text(
                                                    text = "Your summarized intake notes are synced for your appointment.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = SilverText
                                                )
                                            }
                                        }
                                    }
                                    VerityGlassPillButton(
                                        text = "Done",
                                        onClick = onBack,
                                        modifier = Modifier.testTag("appt_ai_chat_done")
                                    )
                                } else {
                                    VerityGlassPillButton(
                                        text = "Saved for Doctor Review · Done",
                                        leadingIcon = Icons.Filled.Share,
                                        onClick = {
                                            sharedWithDoctor = true
                                            onShareSummaryWithDoctor(summary.narrative)
                                        },
                                        modifier = Modifier.testTag("appt_ai_chat_share_summary")
                                    )
                                    if (finalSummary == null) {
                                        VerityGlassSecondaryPill(
                                            text = if (isFinalizing) "Retrying summary…" else "Retry Summary",
                                            onClick = {
                                                if (!isFinalizing) {
                                                    isFinalizing = true
                                                    onEndConsultation { summaryText, error ->
                                                        finalSummary = summaryText
                                                        finalizationError = error
                                                        isFinalizing = false
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth().testTag("appt_ai_chat_retry_summary")
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

    if (!hasExplicitConsent) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("AI intake consent") },
            text = {
                Text("Verity will process the information you share to prepare an intake draft for ${appointment.doctorName}. The conversation and summary are saved securely for your clinician to review; this is not a diagnosis or treatment plan.")
            },
            confirmButton = {
                TextButton(
                    enabled = !isStartingSession,
                    onClick = {
                        hasExplicitConsent = true
                        isStartingSession = true
                        finalizationError = null
                        onStartConsultation(true) { snapshot, error ->
                            isStartingSession = false
                            if (snapshot == null) {
                                finalizationError = error ?: "The AI intake session could not be started."
                                hasExplicitConsent = false
                            } else {
                                sessionCanAcceptMessages = snapshot.status == "in_progress"
                                messages.clear()
                                messages.addAll(snapshot.messages.filter { it.content.isNotBlank() }.map {
                                    IntakeChatBubble(it.id, it.role == "patient", it.content)
                                })
                                finalSummary = snapshot.summary
                                if (!snapshot.summary.isNullOrBlank()) phase = AiChatPhase.SUMMARY
                                if (!sessionCanAcceptMessages && snapshot.summary.isNullOrBlank()) {
                                    finalizationError = "This intake session is no longer accepting messages. Its saved status is ${snapshot.finalizationState}."
                                }
                            }
                        }
                    }
                ) { Text(if (isStartingSession) "Starting…" else "I consent and continue") }
            },
            dismissButton = {
                TextButton(onClick = onBack, enabled = !isStartingSession) { Text("Not now") }
            }
        )
    }
}

@Composable
private fun IntakeChatBubbleRow(msg: IntakeChatBubble) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
    ) {
        val shape = RoundedCornerShape(
            topStart = 20.dp,
            topEnd = 20.dp,
            bottomStart = if (msg.isUser) 20.dp else 6.dp,
            bottomEnd = if (msg.isUser) 6.dp else 20.dp
        )
        Box(
            modifier = Modifier
                .widthIn(max = 310.dp)
                .clip(shape)
                .background(
                    if (msg.isUser) {
                        Brush.verticalGradient(listOf(Color(0xF5F7F8FA), Color(0xEBDDE7F6)))
                    } else {
                        Brush.verticalGradient(listOf(Color(0x473D4454), Color(0x36242934)))
                    },
                    shape
                )
                .border(
                    1.dp,
                    if (msg.isUser) Color(0xFFFFFFFF) else Color(0x4DFFFFFF),
                    shape
                )
                .padding(horizontal = 15.dp, vertical = 12.dp)
        ) {
            Text(
                text = msg.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (msg.isUser) SlateInkDark else PlatinumWhite
            )
        }
    }
}

@Composable
private fun SummarySectionRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = IceBlueText
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = PlatinumWhite
        )
    }
}

private data class AiIntakeSummary(
    val chiefComplaint: String,
    val durationAndPeak: String,
    val additionalNotes: String,
    val narrative: String
)

