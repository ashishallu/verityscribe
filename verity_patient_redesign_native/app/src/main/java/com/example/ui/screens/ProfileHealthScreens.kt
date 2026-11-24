package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.Bloodtype
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Emergency
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Vaccines
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import com.example.data.GoogleFitHealthConnectManager
import com.example.data.HealthBridgeStatus
import com.example.model.MedicalHistorySection
import com.example.ui.components.UploadReportDialog
import com.example.ui.components.VerityGlassBackdrop
import com.example.ui.components.VerityGlassCard
import com.example.ui.components.VerityGlassChip
import com.example.ui.components.VerityGlassInputField
import com.example.ui.components.VerityGlassPillButton
import com.example.ui.components.VerityGlassSecondaryPill
import com.example.ui.theme.CharcoalBase
import com.example.ui.theme.GlassAlertRed
import com.example.ui.theme.IceBlueText
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.PeriwinkleMist
import com.example.ui.theme.PlatinumWhite
import com.example.ui.theme.SilverText
import com.example.ui.theme.SoftLavenderText
import com.example.viewmodel.VerityUiState

@Composable
internal fun ProfileSubPageTopBar(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    testTagPrefix: String
) {
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
                modifier = Modifier.testTag("${testTagPrefix}_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = PlatinumWhite
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = PlatinumWhite
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = IceBlueText
                    )
                }
            }
        }
        HorizontalDivider(
            color = Color(0x26FFFFFF),
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
internal fun ProfileSecurityBanner(
    text: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0x3D93C5FD), Color(0x30A5B4FC))
                ),
                shape
            )
            .border(1.dp, IceBlueText.copy(alpha = 0.42f), shape)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(IceBlueText.copy(alpha = 0.20f))
                .border(1.dp, IceBlueText.copy(alpha = 0.48f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.VerifiedUser,
                contentDescription = null,
                tint = IceBlueText,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = PlatinumWhite
        )
    }
}

@Composable
fun MedicalHistoryScreen(
    uiState: VerityUiState,
    onBack: () -> Unit,
    onAddNote: (sectionId: String, note: String) -> Unit,
    onUploadReport: (
        uri: Uri,
        title: String,
        labName: String,
        orderedBy: String,
        category: String,
        notes: String
    ) -> Unit,
    onOpenReportsTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedId by remember { mutableStateOf<String?>(null) }
    var editingSection by remember { mutableStateOf<MedicalHistorySection?>(null) }
    var draftNote by remember { mutableStateOf("") }
    var showUploadDialog by remember { mutableStateOf(false) }

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
                    .navigationBarsPadding()
            ) {
                ProfileSubPageTopBar(
                    title = "Medical history",
                    onBack = onBack,
                    testTagPrefix = "medical_history"
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        ProfileSecurityBanner(
                            text = "Your history is securely synced with your care team."
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    items(uiState.medicalHistorySections, key = { it.id }) { section ->
                        val isExpanded = expandedId == section.id
                        MedicalHistoryAccordionCard(
                            section = section,
                            expanded = isExpanded,
                            onToggle = {
                                expandedId = if (isExpanded) null else section.id
                            },
                            onAddEntry = {
                                editingSection = section
                                draftNote = ""
                            },
                            onOpenReportsTab = if (section.id == "mh_documents") onOpenReportsTab else null,
                            onUploadReportClick = if (section.id == "mh_documents") {
                                { showUploadDialog = true }
                            } else {
                                null
                            }
                        )
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
                expandedId = "mh_documents"
            }
        )
    }

    editingSection?.let { target ->
        AlertDialog(
            onDismissRequest = { editingSection = null },
            containerColor = CharcoalBase,
            title = {
                Text(
                    text = "Add to ${target.title}",
                    color = PlatinumWhite
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "This clinical note will stay synced in your Verity medical profile.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SilverText
                    )
                    VerityGlassInputField(
                        value = draftNote,
                        onValueChange = { draftNote = it },
                        placeholder = "Enter diagnosis, procedure, or note...",
                        leadingIcon = Icons.Filled.Add,
                        testTag = "medical_history_note_input"
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAddNote(target.id, draftNote)
                        editingSection = null
                    },
                    enabled = draftNote.isNotBlank()
                ) {
                    Text("Save", color = IceBlueText)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingSection = null }) {
                    Text("Cancel", color = SilverText)
                }
            }
        )
    }
}

@Composable
private fun MedicalHistoryAccordionCard(
    section: MedicalHistorySection,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAddEntry: () -> Unit,
    onOpenReportsTab: (() -> Unit)?,
    onUploadReportClick: (() -> Unit)?
) {
    val iconTint = if (section.isAlert) GlassAlertRed else IceBlueText
    VerityGlassCard(
        cornerRadius = 20.dp,
        strong = expanded,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("medical_history_row_${section.id}")
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 60.dp)
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.14f))
                        .border(1.dp, iconTint.copy(alpha = 0.34f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = medicalHistoryIcon(section.id),
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = PlatinumWhite
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = section.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = PeriwinkleMist,
                        maxLines = if (expanded) 3 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = SilverText
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = Color(0x21FFFFFF))
                    section.details.forEach { line ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(iconTint)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall,
                                color = PlatinumWhite.copy(alpha = 0.92f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Updated ${section.lastUpdated}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SilverText
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (onUploadReportClick != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 40.dp)
                                        .clip(CircleShape)
                                        .background(IceBlueText.copy(alpha = 0.20f))
                                        .border(1.dp, IceBlueText.copy(alpha = 0.55f), CircleShape)
                                        .clickable(onClick = onUploadReportClick)
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                        .testTag("medical_history_upload_report")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.UploadFile,
                                        contentDescription = "Upload report",
                                        tint = IceBlueText,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Upload Report",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PlatinumWhite
                                    )
                                }
                            }
                            if (onOpenReportsTab != null) {
                                VerityGlassChip(
                                    text = "View All",
                                    highlighted = true,
                                    onClick = onOpenReportsTab
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x24FFFFFF))
                                    .border(1.dp, Color(0x3DFFFFFF), CircleShape)
                                    .clickable(onClick = onAddEntry)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Add note",
                                    tint = IceBlueText,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Add note",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = PlatinumWhite
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun medicalHistoryIcon(sectionId: String): ImageVector {
    return when (sectionId) {
        "mh_past" -> Icons.Outlined.MedicalServices
        "mh_surgical" -> Icons.Outlined.ContentCut
        "mh_allergies" -> Icons.Outlined.WarningAmber
        "mh_chronic" -> Icons.Outlined.MonitorHeart
        "mh_family" -> Icons.Outlined.Groups
        "mh_lifestyle" -> Icons.Outlined.SelfImprovement
        "mh_blood" -> Icons.Outlined.Bloodtype
        "mh_documents" -> Icons.Outlined.FolderOpen
        "mh_vaccination" -> Icons.Outlined.Vaccines
        else -> Icons.Outlined.Emergency
    }
}

@Composable
fun ConnectedDevicesScreen(
    uiState: VerityUiState,
    onBack: () -> Unit,
    onSyncGoogleFit: () -> Unit,
    onHealthPermissionsResult: (Set<String>) -> Unit,
    onToggleAutoSync: (Boolean) -> Unit,
    onOpenGoogleFitApp: () -> Unit,
    onOpenHealthConnectSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        onHealthPermissionsResult(granted)
    }

    LaunchedEffect(Unit) {
        onSyncGoogleFit()
    }

    val fitState = uiState.googleFitState
    val isConnected = fitState.status == HealthBridgeStatus.CONNECTED
    val needsPermissions = fitState.status == HealthBridgeStatus.PERMISSIONS_NEEDED
    val statusColor = when (fitState.status) {
        HealthBridgeStatus.CONNECTED -> IceBlueText
        HealthBridgeStatus.PERMISSIONS_NEEDED -> SoftLavenderText
        HealthBridgeStatus.UPDATE_REQUIRED, HealthBridgeStatus.UNAVAILABLE -> GlassAlertRed
    }

    val hrPerm = HealthPermission.getReadPermission(HeartRateRecord::class)
    val sleepPerm = HealthPermission.getReadPermission(SleepSessionRecord::class)
    val spo2Perm = HealthPermission.getReadPermission(OxygenSaturationRecord::class)
    val stepsPerm = HealthPermission.getReadPermission(StepsRecord::class)

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
                    .navigationBarsPadding()
            ) {
                ProfileSubPageTopBar(
                    title = "Connected devices",
                    subtitle = "Google Fit via Android Health Connect",
                    onBack = onBack,
                    testTagPrefix = "connected_devices"
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Google Fit Bridge Hero Card
                    item {
                        VerityGlassCard(
                            strong = true,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(CircleShape)
                                                .background(statusColor.copy(alpha = 0.16f))
                                                .border(1.dp, statusColor.copy(alpha = 0.45f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Watch,
                                                contentDescription = null,
                                                tint = statusColor,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = "Google Fit Sync",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = PlatinumWhite
                                            )
                                            Text(
                                                text = if (fitState.isGoogleFitInstalled) {
                                                    "Google Fit app installed"
                                                } else {
                                                    "Health Connect bridge ready"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = PeriwinkleMist
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(statusColor.copy(alpha = 0.16f))
                                            .border(1.dp, statusColor.copy(alpha = 0.45f), CircleShape)
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = statusColor,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = if (uiState.isSyncingGoogleFit) "Syncing..." else fitState.status.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = statusColor
                                        )
                                    }
                                }

                                Text(
                                    text = fitState.statusDetail,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SilverText
                                )

                                // Primary Connect / Sync CTA
                                when {
                                    needsPermissions -> {
                                        VerityGlassPillButton(
                                            text = if (fitState.grantedCount == 0) {
                                                "Connect Google Fit"
                                            } else {
                                                "Grant Remaining Permissions (${fitState.grantedCount}/4)"
                                            },
                                            leadingIcon = Icons.Filled.Sync,
                                            onClick = {
                                                permissionsLauncher.launch(
                                                    GoogleFitHealthConnectManager.requiredPermissions
                                                )
                                            },
                                            modifier = Modifier.testTag("connect_google_fit_button")
                                        )
                                    }

                                    isConnected -> {
                                        VerityGlassPillButton(
                                            text = if (uiState.isSyncingGoogleFit) "Syncing Google Fit..." else "Sync Google Fit Now",
                                            leadingIcon = Icons.Filled.Sync,
                                            enabled = !uiState.isSyncingGoogleFit,
                                            onClick = onSyncGoogleFit,
                                            modifier = Modifier.testTag("sync_google_fit_button")
                                        )
                                    }

                                    else -> {
                                        VerityGlassPillButton(
                                            text = "Set Up Health Connect",
                                            leadingIcon = Icons.Filled.Settings,
                                            onClick = onOpenHealthConnectSettings,
                                            modifier = Modifier.testTag("setup_health_connect_button")
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    VerityGlassSecondaryPill(
                                        text = "Open Google Fit",
                                        leadingIcon = Icons.Filled.OpenInNew,
                                        onClick = onOpenGoogleFitApp,
                                        minHeight = 48.dp,
                                        horizontalPadding = 12.dp,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("open_google_fit_app_button")
                                    )
                                    VerityGlassSecondaryPill(
                                        text = "Health Connect",
                                        leadingIcon = Icons.Filled.Settings,
                                        onClick = onOpenHealthConnectSettings,
                                        minHeight = 48.dp,
                                        horizontalPadding = 12.dp,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("open_health_connect_settings_button")
                                    )
                                }
                            }
                        }
                    }

                    // Live Health Snapshot Feed Mapping
                    item {
                        Text(
                            text = "Live Health Snapshot Feed",
                            style = MaterialTheme.typography.titleMedium,
                            color = PlatinumWhite
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                GoogleFitMetricCard(
                                    icon = Icons.Filled.Favorite,
                                    title = "Heart rate",
                                    liveValue = fitState.heartRateBpm?.let { "$it bpm" },
                                    authorized = hrPerm in fitState.grantedPermissions,
                                    accent = Color(0xFFFCA5A5),
                                    modifier = Modifier.weight(1f)
                                )
                                GoogleFitMetricCard(
                                    icon = Icons.Filled.Bedtime,
                                    title = "Sleep",
                                    liveValue = fitState.sleepFormatted,
                                    authorized = sleepPerm in fitState.grantedPermissions,
                                    accent = SoftLavenderText,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                GoogleFitMetricCard(
                                    icon = Icons.Filled.Air,
                                    title = "Blood oxygen",
                                    liveValue = fitState.spo2Percent?.let { "$it%" },
                                    authorized = spo2Perm in fitState.grantedPermissions,
                                    accent = IceBlueText,
                                    modifier = Modifier.weight(1f)
                                )
                                GoogleFitMetricCard(
                                    icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                                    title = "Steps today",
                                    liveValue = fitState.stepsToday?.toString(),
                                    authorized = stepsPerm in fitState.grantedPermissions,
                                    accent = PeriwinkleMist,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Auto-sync & Companion Info
                    item {
                        VerityGlassCard(
                            cornerRadius = 22.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 52.dp)
                                        .clickable { onToggleAutoSync(!uiState.googleFitAutoSync) },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Auto-sync Health Snapshot",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = PlatinumWhite
                                        )
                                        Text(
                                            text = "Refresh Home vitals from Google Fit on launch",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SilverText
                                        )
                                    }
                                    Switch(
                                        checked = uiState.googleFitAutoSync,
                                        onCheckedChange = onToggleAutoSync,
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = ObsidianBlack,
                                            checkedTrackColor = PlatinumWhite,
                                            uncheckedThumbColor = PlatinumWhite,
                                            uncheckedTrackColor = Color(0x33FFFFFF),
                                            uncheckedBorderColor = Color(0x52FFFFFF)
                                        ),
                                        modifier = Modifier.testTag("google_fit_autosync_switch")
                                    )
                                }
                                HorizontalDivider(
                                    color = Color(0x21FFFFFF),
                                    modifier = Modifier.padding(vertical = 10.dp)
                                )
                                Text(
                                    text = "How Google Fit connects on Android: Google Fit syncs step counts, sleep sessions, heart rate, and SpO2 to Android Health Connect on your device. Verity reads those records directly to keep your Home Health Snapshot live.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PeriwinkleMist
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
private fun GoogleFitMetricCard(
    icon: ImageVector,
    title: String,
    liveValue: String?,
    authorized: Boolean,
    accent: Color,
    modifier: Modifier = Modifier
) {
    VerityGlassCard(
        cornerRadius = 18.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (authorized) "Authorized" else "Locked",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (authorized) IceBlueText else SilverText
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = PeriwinkleMist
            )
            Text(
                text = liveValue ?: if (authorized) "No records yet" else "Connect Fit",
                style = MaterialTheme.typography.titleMedium,
                color = PlatinumWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
