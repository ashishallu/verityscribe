package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material.icons.rounded.Summarize
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AppointmentStatus
import com.example.model.ClinicalAppointment
import com.example.model.StructuredAiSummary
import com.example.model.TranscriptSender
import com.example.ui.components.ClinicalSafetyBanner
import com.example.ui.components.ConsentVerifiedBadge
import com.example.ui.components.ConsultationTypeBadge
import com.example.ui.components.StatusBadge
import com.example.ui.theme.AmberBg
import com.example.ui.theme.AmberBorder
import com.example.ui.theme.AmberText
import com.example.ui.theme.ClinicalBlue
import com.example.ui.theme.ClinicalBorder
import com.example.ui.theme.ClinicalSurface
import com.example.ui.theme.ClinicalSurfaceSubtle
import com.example.ui.theme.GlacialMistBottom
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MedicalTeal
import com.example.ui.theme.MedicalTealDark
import com.example.ui.theme.MedicalTealSoft
import com.example.ui.theme.OceanTealMid
import com.example.ui.theme.OceanTealTop
import com.example.ui.theme.RoseBg
import com.example.ui.theme.RoseBorder
import com.example.ui.theme.RoseText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiReviewWorkspaceScreen(
    appointment: ClinicalAppointment,
    isRetryingSummary: Boolean,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onBack: () -> Unit,
    onApproveDraft: () -> Unit,
    onRejectDraft: (String) -> Unit,
    onUpdateDraftSummary: (StructuredAiSummary) -> Unit,
    onRetrySummary: () -> Unit,
    onOpenConsultation: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var selectedTabIndex by remember { mutableIntStateOf(0) } // 0 = Summary, 1 = Transcript
    var showEditSheet by remember { mutableStateOf(false) }
    var showRejectDialog by remember { mutableStateOf(false) }
    var metadataExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = GlacialMistBottom,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(OceanTealTop, OceanTealMid)
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("review_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = appointment.patientName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${appointment.ageLabel} • ${appointment.gender}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.86f)
                            )
                        }
                        StatusBadge(status = appointment.status)
                    }

                    // Frosted Segmented Tabs on Ocean Teal Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.20f), RoundedCornerShape(12.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SegmentedReviewTab(
                            title = "Structured AI Summary",
                            icon = Icons.Rounded.Summarize,
                            selected = selectedTabIndex == 0,
                            onClick = { selectedTabIndex = 0 },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("review_tab_summary")
                        )
                        SegmentedReviewTab(
                            title = "Preserved Transcript",
                            icon = Icons.Rounded.Forum,
                            selected = selectedTabIndex == 1,
                            onClick = { selectedTabIndex = 1 },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("review_tab_transcript")
                        )
                    }
                }
            }
        },
        bottomBar = {
            // Sticky Clinical Decision Bar
            Surface(
                color = ClinicalSurface,
                border = BorderStroke(1.dp, ClinicalBorder),
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (appointment.status == AppointmentStatus.APPROVED ||
                        appointment.status == AppointmentStatus.IN_PROGRESS ||
                        appointment.status == AppointmentStatus.COMPLETED
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showEditSheet = true },
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("review_edit_button"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, ClinicalBorder)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = null,
                                    tint = InkPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Edit", color = InkPrimary)
                            }
                            Button(
                                onClick = onOpenConsultation,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("open_active_consultation_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MedicalTeal)
                            ) {
                                Text(
                                    text = if (appointment.status == AppointmentStatus.COMPLETED) {
                                        "View Clinical Chart"
                                    } else {
                                        "Open Clinical Consultation"
                                    },
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showEditSheet = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("review_edit_button"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, ClinicalBorder)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = null,
                                    tint = InkPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = stringResource(R.string.edit_draft_button),
                                    color = InkPrimary,
                                    maxLines = 1
                                )
                            }
                            OutlinedButton(
                                onClick = { showRejectDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("review_reject_button"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, RoseBorder),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseText)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = stringResource(R.string.reject_draft_button),
                                    maxLines = 1
                                )
                            }
                        }

                        Button(
                            onClick = onApproveDraft,
                            enabled = !appointment.aiSummary.generationFailed,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("review_approve_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MedicalTeal,
                                disabledContainerColor = ClinicalSurfaceSubtle
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.approve_and_link_button),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
        if (selectedTabIndex == 0) {
            // TAB A: Structured AI Summary
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    ClinicalSafetyBanner(
                        status = appointment.status,
                        rejectionReason = appointment.rejectionReason
                    )
                }

                // Failed Summary State with Simulated Retry
                if (appointment.aiSummary.generationFailed) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = RoseBg,
                            border = BorderStroke(1.dp, RoseBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ErrorOutline,
                                        contentDescription = null,
                                        tint = RoseText
                                    )
                                    Text(
                                        text = "Summary Generation Failed — Needs Retry",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = RoseText
                                    )
                                }
                                Text(
                                    text = appointment.aiSummary.failureReason
                                        ?: "Automated structuring failed. Preserved intake transcript remains intact.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkPrimary
                                )
                                Button(
                                    onClick = onRetrySummary,
                                    enabled = !isRetryingSummary,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = RoseText),
                                    modifier = Modifier.testTag("retry_summary_button")
                                ) {
                                    if (isRetryingSummary) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Regenerating Summary...")
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.retry_summary_button))
                                    }
                                }
                            }
                        }
                    }
                }

                // Meta Strip
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ConsultationTypeBadge(type = appointment.consultationType)
                        Text(
                            text = "Scheduled ${appointment.scheduledTime}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = InkMuted
                        )
                    }
                }

                item {
                    SummarySectionCard(
                        icon = Icons.Rounded.ReportProblem,
                        title = "Chief Problem",
                        body = appointment.aiSummary.chiefProblem,
                        accent = MedicalTeal
                    )
                }

                item {
                    SummarySectionCard(
                        icon = Icons.Rounded.Timeline,
                        title = "Symptom Course",
                        body = appointment.aiSummary.symptomCourse,
                        accent = ClinicalBlue
                    )
                }

                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = ClinicalSurface,
                        border = BorderStroke(1.dp, ClinicalBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Medication,
                                    contentDescription = null,
                                    tint = MedicalTeal,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Current Medications",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = InkPrimary
                                )
                            }
                            appointment.aiSummary.currentMedications.forEach { med ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    color = ClinicalSurfaceSubtle
                                ) {
                                    Text(
                                        text = "• $med",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = InkPrimary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (appointment.aiSummary.suggestedMedications.isNotEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = AmberBg.copy(alpha = 0.34f),
                            border = BorderStroke(1.dp, AmberBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "AI-Drafted Suggested Medications · Doctor verification required",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = AmberText
                                )
                                appointment.aiSummary.suggestedMedications.forEach { medication ->
                                    val details = listOf(
                                        medication.dosage,
                                        medication.frequency,
                                        medication.duration,
                                        medication.instructions
                                    ).filter(String::isNotBlank).joinToString(" · ")
                                    Text(
                                        text = listOf(medication.medicationName, details).filter(String::isNotBlank).joinToString(" — "),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = InkPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                if (appointment.aiSummary.clinicalAdvice.isNotEmpty()) {
                    item {
                        SummarySectionCard(
                            icon = Icons.Rounded.HistoryEdu,
                            title = "AI-Drafted Clinical Advice · Review before use",
                            body = appointment.aiSummary.clinicalAdvice.joinToString("\n") { "• $it" },
                            accent = ClinicalBlue
                        )
                    }
                }

                item {
                    SummarySectionCard(
                        icon = Icons.Rounded.HistoryEdu,
                        title = "Medical History & Allergies",
                        body = appointment.aiSummary.medicalHistoryAndAllergies,
                        accent = InkPrimary
                    )
                }

                if (appointment.aiSummary.clarificationsOrUnknowns.isNotEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = AmberBg.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, AmberBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                                        contentDescription = null,
                                        tint = AmberText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Clarifications / Unknowns",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = AmberText
                                    )
                                }
                                appointment.aiSummary.clarificationsOrUnknowns.forEach { item ->
                                    Text(
                                        text = "• $item",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = InkPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                // Generation Status & Provider Metadata (Progressive Disclosure)
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { metadataExpanded = !metadataExpanded },
                        shape = RoundedCornerShape(16.dp),
                        color = ClinicalSurface,
                        border = BorderStroke(1.dp, ClinicalBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Generation Status & Provider Metadata",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = InkSecondary
                                )
                                Icon(
                                    imageVector = if (metadataExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = "Toggle metadata",
                                    tint = InkMuted
                                )
                            }
                            if (metadataExpanded) {
                                HorizontalDivider(color = ClinicalBorder)
                                MetadataRow("Provider", appointment.aiSummary.providerName)
                                MetadataRow("Model", appointment.aiSummary.modelVersion)
                                MetadataRow("Generated", appointment.aiSummary.generatedAt)
                                MetadataRow("Latency", appointment.aiSummary.generationLatency)
                                appointment.aiSummary.failureReason?.let {
                                    MetadataRow("Generation issue", it)
                                }
                                MetadataRow("Consent", appointment.consentVerifiedAt)
                            }
                        }
                    }
                }
            }
        } else {
            // TAB B: Full Preserved Transcript
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ConsentVerifiedBadge(timestamp = appointment.consentVerifiedAt)
                        Text(
                            text = "Verbatim preserved intake dialogue. Unedited source record.",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted
                        )
                    }
                }

                items(appointment.transcript, key = { it.id }) { turn ->
                    val isPatient = turn.sender == TranscriptSender.PATIENT
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isPatient) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            modifier = Modifier.widthIn(max = 310.dp),
                            shape = RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (isPatient) 16.dp else 4.dp,
                                bottomEnd = if (isPatient) 4.dp else 16.dp
                            ),
                            color = if (isPatient) MedicalTealSoft else ClinicalSurface,
                            border = BorderStroke(
                                1.dp,
                                if (isPatient) MedicalTeal.copy(alpha = 0.35f) else ClinicalBorder
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isPatient) Icons.Rounded.Person else Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            tint = if (isPatient) MedicalTealDark else ClinicalBlue,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = if (isPatient) appointment.patientName else "AI Intake Interviewer",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = if (isPatient) MedicalTealDark else ClinicalBlue
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = turn.timestamp,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 10.sp
                                        ),
                                        color = InkMuted
                                    )
                                }
                                Text(
                                    text = turn.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = InkPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }

    // Edit Draft Sheet
    if (showEditSheet) {
        var chiefProblem by remember { mutableStateOf(appointment.aiSummary.chiefProblem) }
        var symptomCourse by remember { mutableStateOf(appointment.aiSummary.symptomCourse) }
        var medsText by remember { mutableStateOf(appointment.aiSummary.currentMedications.joinToString("\n")) }
        var historyText by remember { mutableStateOf(appointment.aiSummary.medicalHistoryAndAllergies) }
        var unknownsText by remember { mutableStateOf(appointment.aiSummary.clarificationsOrUnknowns.joinToString("\n")) }
        var suggestedMedicationsText by remember {
            mutableStateOf(appointment.aiSummary.suggestedMedications.joinToString("\n") { medication ->
                listOf(medication.medicationName, medication.dosage, medication.frequency, medication.duration, medication.instructions)
                    .joinToString(" | ")
            })
        }
        var clinicalAdviceText by remember { mutableStateOf(appointment.aiSummary.clinicalAdvice.joinToString("\n")) }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { showEditSheet = false },
            sheetState = sheetState,
            containerColor = ClinicalSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Edit Structured AI Draft",
                    style = MaterialTheme.typography.headlineSmall,
                    color = InkPrimary
                )
                OutlinedTextField(
                    value = chiefProblem,
                    onValueChange = { chiefProblem = it },
                    label = { Text("Chief Problem") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = symptomCourse,
                    onValueChange = { symptomCourse = it },
                    label = { Text("Symptom Course") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = medsText,
                    onValueChange = { medsText = it },
                    label = { Text("Current Medications (one per line)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = historyText,
                    onValueChange = { historyText = it },
                    label = { Text("Medical History & Allergies") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = unknownsText,
                    onValueChange = { unknownsText = it },
                    label = { Text("Clarifications / Unknowns (one per line)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = suggestedMedicationsText,
                    onValueChange = { suggestedMedicationsText = it },
                    label = { Text("Suggested Medications — one per line") },
                    supportingText = { Text("Format: name | dosage | frequency | duration | instructions") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = clinicalAdviceText,
                    onValueChange = { clinicalAdviceText = it },
                    label = { Text("Clinical Advice — one item per line") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = RoundedCornerShape(12.dp)
                )
                Button(
                    onClick = {
                        onUpdateDraftSummary(
                            appointment.aiSummary.copy(
                                chiefProblem = chiefProblem.trim(),
                                symptomCourse = symptomCourse.trim(),
                                currentMedications = medsText.lines().map { it.trim() }.filter { it.isNotEmpty() },
                                medicalHistoryAndAllergies = historyText.trim(),
                                clarificationsOrUnknowns = unknownsText.lines().map { it.trim() }.filter { it.isNotEmpty() },
                                suggestedMedications = suggestedMedicationsText.lines().mapIndexedNotNull { index, line ->
                                    val fields = line.split('|', limit = 5).map(String::trim)
                                    val name = fields.firstOrNull().orEmpty()
                                    if (name.isBlank()) null else com.example.model.PrescriptionItem(
                                        id = "edited-suggested-$index",
                                        medicationName = name,
                                        dosage = fields.getOrElse(1) { "" },
                                        frequency = fields.getOrElse(2) { "" },
                                        duration = fields.getOrElse(3) { "" },
                                        instructions = fields.getOrElse(4) { "" }
                                    )
                                },
                                clinicalAdvice = clinicalAdviceText.lines().map(String::trim).filter(String::isNotBlank),
                                generationFailed = false,
                                failureReason = null
                            )
                        )
                        showEditSheet = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_edited_draft_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MedicalTeal)
                ) {
                    Text("Save Draft Changes")
                }
            }
        }
    }

    // Reject Draft Reason Dialog
    if (showRejectDialog) {
        var reason by remember { mutableStateOf("") }
        val presets = listOf(
            "Inaccurate symptom timeline",
            "Incomplete medication reconciliation",
            "Requires direct physician re-intake"
        )
        AlertDialog(
            onDismissRequest = { showRejectDialog = false },
            containerColor = ClinicalSurface,
            title = { Text("Reject AI Intake Draft", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Specify clinical audit reason before discarding AI draft from chart linkage:",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted
                    )
                    presets.forEach { preset ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { reason = preset },
                            shape = RoundedCornerShape(10.dp),
                            color = if (reason == preset) RoseBg else ClinicalSurfaceSubtle,
                            border = BorderStroke(1.dp, if (reason == preset) RoseBorder else ClinicalBorder)
                        ) {
                            Text(
                                text = preset,
                                style = MaterialTheme.typography.labelMedium,
                                color = InkPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Reason") },
                        placeholder = { Text("Enter clinical reason...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reject_reason_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRejectDraft(reason)
                        showRejectDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseText),
                    modifier = Modifier.testTag("confirm_reject_button")
                ) {
                    Text("Confirm Reject")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRejectDialog = false }) {
                    Text("Cancel", color = InkMuted)
                }
            }
        )
    }
}

@Composable
private fun SegmentedReviewTab(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(9.dp),
        color = if (selected) Color.White else Color.Transparent,
        shadowElevation = if (selected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MedicalTeal else Color.White.copy(alpha = 0.90f),
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = if (selected) InkPrimary else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SummarySectionCard(
    icon: ImageVector,
    title: String,
    body: String,
    accent: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = ClinicalSurface,
        border = BorderStroke(1.dp, ClinicalBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = InkPrimary
                )
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = InkSecondary
            )
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = InkMuted)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp
            ),
            color = InkPrimary
        )
    }
}
