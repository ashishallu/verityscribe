package com.example.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.ConsultationRecord
import com.example.model.MedicalReport
import com.example.model.RecordsSectionTab
import com.example.ui.components.UploadReportDialog
import com.example.ui.components.VerityGlassBackdrop
import com.example.ui.components.VerityGlassCard
import com.example.ui.components.VerityGlassChip
import com.example.ui.theme.IceBlueText
import com.example.ui.theme.PeriwinkleMist
import com.example.ui.theme.PlatinumWhite
import com.example.viewmodel.VerityUiState

@Composable
fun RecordsScreen(
    uiState: VerityUiState,
    onSelectRecordsTab: (RecordsSectionTab) -> Unit,
    onToggleExpanded: (String) -> Unit,
    onTogglePlayback: (String) -> Unit,
    onSeekPlayback: (String, Int) -> Unit,
    onDownloadPdf: (ConsultationRecord) -> Unit,
    onSelectReportCategory: (String) -> Unit,
    onToggleReportExpanded: (String) -> Unit,
    onShareReport: (MedicalReport) -> Unit,
    onDownloadReport: (MedicalReport) -> Unit,
    onUploadReport: (
        uri: Uri,
        title: String,
        labName: String,
        orderedBy: String,
        category: String,
        notes: String
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    var showUploadDialog by remember { mutableStateOf(false) }

    val reportCategories = remember(uiState.reports) {
        listOf("All") + uiState.reports.map { it.category }.distinct()
    }
    val filteredReports = remember(uiState.reports, uiState.selectedReportCategory) {
        if (uiState.selectedReportCategory == "All") {
            uiState.reports
        } else {
            uiState.reports.filter { it.category == uiState.selectedReportCategory }
        }
    }

    VerityGlassBackdrop(
        isObsidianMode = uiState.darkMode,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // Header + Segmented Consultations / Reports Tab Switcher
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Medical history",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = PlatinumWhite
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Consultations, audio notes & diagnostic reports",
                                style = MaterialTheme.typography.bodyMedium,
                                color = IceBlueText
                            )
                        }
                        AnimatedVisibility(
                            visible = uiState.selectedRecordsTab == RecordsSectionTab.REPORTS
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color(0x4093C5FD), Color(0x28A5B4FC))
                                        )
                                    )
                                    .border(1.dp, IceBlueText.copy(alpha = 0.55f), CircleShape)
                                    .clickable { showUploadDialog = true }
                                    .padding(horizontal = 14.dp, vertical = 9.dp)
                                    .testTag("records_header_upload_report")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Upload report",
                                    tint = PlatinumWhite,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Upload",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PlatinumWhite
                                )
                            }
                        }
                    }

                    RecordsSegmentedTabBar(
                        selectedTab = uiState.selectedRecordsTab,
                        consultationsCount = uiState.consultations.size,
                        reportsCount = uiState.reports.size,
                        onSelectTab = onSelectRecordsTab
                    )
                }

                AnimatedContent(
                    targetState = uiState.selectedRecordsTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "records_tab_content",
                    modifier = Modifier.weight(1f)
                ) { activeTab ->
                    when (activeTab) {
                        RecordsSectionTab.CONSULTATIONS -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 20.dp,
                                    end = 20.dp,
                                    top = 6.dp,
                                    bottom = 116.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(uiState.consultations, key = { it.id }) { record ->
                                    val expanded = uiState.expandedConsultationId == record.id
                                    val isPlaying = uiState.playingConsultationId == record.id
                                    val currentSeconds = uiState.playbackSecondsByRecord[record.id]
                                        ?: record.recordingElapsedSeconds

                                    ConsultationRecordCard(
                                        record = record,
                                        expanded = expanded,
                                        isPlaying = isPlaying,
                                        currentSeconds = currentSeconds,
                                        onToggleExpanded = { onToggleExpanded(record.id) },
                                        onTogglePlayback = { onTogglePlayback(record.id) },
                                        onSeekPlayback = { sec -> onSeekPlayback(record.id, sec) },
                                        onDownloadPdf = { onDownloadPdf(record) }
                                    )
                                }
                            }
                        }

                        RecordsSectionTab.REPORTS -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 20.dp,
                                    end = 20.dp,
                                    top = 6.dp,
                                    bottom = 116.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                item {
                                    VerityGlassCard(
                                        cornerRadius = 22.dp,
                                        strong = true,
                                        onClick = { showUploadDialog = true },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("upload_new_report_card")
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(CircleShape)
                                                    .background(IceBlueText.copy(alpha = 0.18f))
                                                    .border(1.dp, IceBlueText.copy(alpha = 0.50f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.UploadFile,
                                                    contentDescription = "Upload new report",
                                                    tint = IceBlueText,
                                                    modifier = Modifier.size(21.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Upload new report",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = PlatinumWhite
                                                )
                                                Text(
                                                    text = "Attach a PDF lab result or scan photo",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = PeriwinkleMist
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(Color(0x28FFFFFF))
                                                    .border(1.dp, Color(0x47FFFFFF), CircleShape)
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = "+ Add",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = IceBlueText
                                                )
                                            }
                                        }
                                    }
                                }

                                item {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(reportCategories) { category ->
                                            VerityGlassChip(
                                                text = category,
                                                highlighted = uiState.selectedReportCategory == category,
                                                onClick = { onSelectReportCategory(category) }
                                            )
                                        }
                                    }
                                }

                                items(filteredReports, key = { it.id }) { report ->
                                    MedicalReportCard(
                                        report = report,
                                        expanded = uiState.expandedReportId == report.id,
                                        onToggleExpanded = { onToggleReportExpanded(report.id) },
                                        onShareWithDoctor = { onShareReport(report) },
                                        onDownloadReport = { onDownloadReport(report) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showUploadDialog) {
        UploadReportDialog(
            onDismiss = { showUploadDialog = false },
            onConfirmUpload = { uri, title, labName, orderedBy, category, notes ->
                onUploadReport(uri, title, labName, orderedBy, category, notes)
                showUploadDialog = false
            }
        )
    }
}

