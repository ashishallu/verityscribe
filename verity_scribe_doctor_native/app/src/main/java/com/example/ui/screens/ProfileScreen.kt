package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DoctorHistoryRecord
import com.example.model.HistoryRangeFilter
import com.example.model.NotificationPreferences
import com.example.model.PrivacyPreferences
import com.example.ui.components.ClinicalSearchField
import com.example.ui.components.ConsultationTypeBadge
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
import com.example.ui.theme.RoseBg
import com.example.ui.theme.RoseBorder
import com.example.ui.theme.RoseText
import com.example.ui.theme.SlateContainer
import com.example.ui.theme.SlateGunmetalDark
import com.example.ui.theme.SlateSoft
import com.example.viewmodel.VerityScribeUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    uiState: VerityScribeUiState,
    onToggleAvailability: () -> Unit,
    onUpdateNotificationPrefs: (NotificationPreferences) -> Unit,
    onUpdatePrivacyPrefs: (PrivacyPreferences) -> Unit,
    onSelectHistoryRange: (HistoryRangeFilter) -> Unit,
    onHistorySearchQueryChange: (String) -> Unit,
    onOpenAppointment: (String) -> Unit,
    onStartHistoryAiChat: (String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showNotificationsSheet by remember { mutableStateOf(false) }
    var showPrivacySheet by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var selectedHistoryRecord by remember { mutableStateOf<DoctorHistoryRecord?>(null) }

    val profile = uiState.doctorProfile
    val allHistory30Days = remember(uiState.appointments, uiState.doctorHistory) {
        uiState.last30DaysHistory
    }
    val followUpHistoryCount = remember(allHistory30Days) {
        allHistory30Days.count { it.followUpRecommended }
    }
    val filteredModalHistory = remember(
        allHistory30Days,
        uiState.selectedHistoryRange,
        uiState.historySearchQuery
    ) {
        val ranged = when (uiState.selectedHistoryRange) {
            HistoryRangeFilter.LAST_30_DAYS -> allHistory30Days
            HistoryRangeFilter.LAST_7_DAYS -> allHistory30Days.filter { it.daysAgo <= 7 }
            HistoryRangeFilter.FOLLOW_UP -> allHistory30Days.filter { it.followUpRecommended }
        }
        val q = uiState.historySearchQuery.trim().lowercase()
        if (q.isEmpty()) ranged
        else ranged.filter { rec ->
            rec.patientName.lowercase().contains(q) ||
                rec.mrn.lowercase().contains(q) ||
                rec.diagnosisSummary.lowercase().contains(q) ||
                rec.clinicalOutcome.lowercase().contains(q) ||
                rec.medicationsPrescribed.any { it.lowercase().contains(q) }
        }
    }

    val glassCardBrush = Brush.verticalGradient(
        colors = listOf(Color.White.copy(alpha = 0.66f), Color.White.copy(alpha = 0.44f))
    )
    val glassCardBorder = Brush.verticalGradient(
        colors = listOf(Color.White.copy(alpha = 0.94f), Color.White.copy(alpha = 0.44f))
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
        // Ambient luminous glows behind frosted glass cards
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(OceanTealMist.copy(alpha = 0.32f), Color.Transparent),
                    center = Offset(size.width * 0.85f, size.height * 0.16f),
                    radius = size.width * 0.54f
                ),
                radius = size.width * 0.54f,
                center = Offset(size.width * 0.85f, size.height * 0.16f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(OceanTealMid.copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(size.width * 0.14f, size.height * 0.55f),
                    radius = size.width * 0.62f
                ),
                radius = size.width * 0.62f,
                center = Offset(size.width * 0.14f, size.height * 0.55f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(OceanTealMist.copy(alpha = 0.24f), Color.Transparent),
                    center = Offset(size.width * 0.84f, size.height * 0.82f),
                    radius = size.width * 0.50f
                ),
                radius = size.width * 0.50f,
                center = Offset(size.width * 0.84f, size.height * 0.82f)
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Doctor Avatar & Header on Ocean Teal Top Zone
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.22f))
                            .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = "Doctor profile avatar",
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = profile.fullName,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = "${profile.specialtyTitle} • ${profile.hospital}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.88f)
                    )
                }
            }

            // Availability Toggle Glass Card
            item {
                val availShape = RoundedCornerShape(20.dp)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(availShape)
                        .background(
                            brush = if (profile.isAvailable) {
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFFE6FAF2).copy(alpha = 0.86f),
                                        Color(0xFFD1FAE5).copy(alpha = 0.64f)
                                    )
                                )
                            } else {
                                glassCardBrush
                            },
                            shape = availShape
                        )
                        .border(1.2.dp, glassCardBorder, availShape),
                    shape = availShape,
                    color = Color.Transparent,
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (profile.isAvailable) "On Shift • Accepting Consultations" else "Away / Shift Paused",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (profile.isAvailable) EmeraldText else InkPrimary
                            )
                            Text(
                                text = "Real-time AI intake routing & queue visibility",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSecondary
                            )
                        }
                        Switch(
                            checked = profile.isAvailable,
                            onCheckedChange = { onToggleAvailability() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = OceanTealPrimary
                            ),
                            modifier = Modifier.testTag("profile_availability_switch")
                        )
                    }
                }
            }

            // Doctor History (Last 30 Days) Glassy Section
            item {
                val historyCardShape = RoundedCornerShape(22.dp)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(historyCardShape)
                        .background(glassCardBrush, historyCardShape)
                        .border(1.2.dp, glassCardBorder, historyCardShape),
                    shape = historyCardShape,
                    color = Color.Transparent,
                    shadowElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.72f))
                                        .border(1.dp, Color.White, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.History,
                                        contentDescription = null,
                                        tint = OceanTealPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Doctor History",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = InkPrimary
                                    )
                                    Text(
                                        text = "Patients overseen in last 30 days",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = InkSecondary
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(OceanTealPrimary)
                                    .clickable { showHistorySheet = true }
                                    .padding(horizontal = 13.dp, vertical = 8.dp)
                                    .testTag("profile_doctor_history_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "View All (${allHistory30Days.size})",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                        }

                        // 30-Day Quick Stats Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HistoryStatPill(
                                label = "${allHistory30Days.size} Overseen",
                                accent = OceanTealDark,
                                modifier = Modifier.weight(1f)
                            )
                            HistoryStatPill(
                                label = "${allHistory30Days.count { it.daysAgo <= 7 }} Past 7d",
                                accent = OceanTealPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            HistoryStatPill(
                                label = "$followUpHistoryCount Follow-ups",
                                accent = AmberText,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Top 3 recent 30-day overseen patients preview
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            allHistory30Days.take(3).forEach { record ->
                                DoctorHistoryPatientRow(
                                    record = record,
                                    onClick = { selectedHistoryRecord = record },
                                    modifier = Modifier.testTag("profile_history_preview_${record.id}")
                                )
                            }
                        }
                    }
                }
            }

            // Verified Credentials Glass Card
            item {
                val credShape = RoundedCornerShape(20.dp)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(credShape)
                        .background(glassCardBrush, credShape)
                        .border(1.2.dp, glassCardBorder, credShape),
                    shape = credShape,
                    color = Color.Transparent,
                    shadowElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        Text(
                            text = "Clinical Credentials",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = InkPrimary
                        )
                        CredentialCheckRow(
                            label = "Medical license",
                            value = profile.licenseNumber,
                            useMono = true
                        )
                        CredentialCheckRow(
                            label = "Experience",
                            value = profile.experienceYears?.let { "$it years" } ?: "Not recorded"
                        )
                        CredentialCheckRow(
                            label = "Languages",
                            value = profile.languages
                        )
                        CredentialCheckRow(
                            label = "Working hours",
                            value = profile.workingHours
                        )
                    }
                }
            }

            // Settings Glass Group Card
            item {
                val settingsShape = RoundedCornerShape(20.dp)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(settingsShape)
                        .background(glassCardBrush, settingsShape)
                        .border(1.2.dp, glassCardBorder, settingsShape),
                    shape = settingsShape,
                    color = Color.Transparent,
                    shadowElevation = 0.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ProfileActionRow(
                            icon = Icons.Rounded.NotificationsNone,
                            title = "Notification preferences",
                            subtitle = "Urgent labs, AI draft alerts & failed summary pings",
                            onClick = { showNotificationsSheet = true },
                            testTag = "profile_notifications_row"
                        )
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.65f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        ProfileActionRow(
                            icon = Icons.Rounded.Security,
                            title = "Privacy & security",
                            subtitle = "Biometric lock & anonymized case consent rules",
                            onClick = { showPrivacySheet = true },
                            testTag = "profile_privacy_row"
                        )
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.65f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        ProfileActionRow(
                            icon = Icons.Rounded.Info,
                            title = "About VerityScribe Doctor",
                            subtitle = "Clinical Safety Prototype v2.6",
                            onClick = { showAboutDialog = true },
                            testTag = "profile_about_row"
                        )
                    }
                }
            }

            // Glassy Sign Out CTA
            item {
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("profile_sign_out_button"),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.2.dp, RoseBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = RoseBg.copy(alpha = 0.56f),
                        contentColor = RoseText
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out of Session", style = MaterialTheme.typography.labelLarge)
                }
            }
            item { Spacer(modifier = Modifier.height(6.dp).navigationBarsPadding()) }
        }
    }

    // Full 30-Day Doctor History Modal Sheet
    if (showHistorySheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFFEAF3F7)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 640.dp)
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Doctor History • 30 Days",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = InkPrimary
                        )
                        Text(
                            text = "${filteredModalHistory.size} of ${allHistory30Days.size} patient encounters shown",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSecondary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.78f))
                            .border(1.dp, Color.White, RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarMonth,
                                contentDescription = null,
                                tint = OceanTealPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "30d Log",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = OceanTealDark
                            )
                        }
                    }
                }

                ClinicalSearchField(
                    query = uiState.historySearchQuery,
                    onQueryChange = onHistorySearchQueryChange,
                    placeholder = "Search patient, MRN, diagnosis, Rx...",
                    testTag = "history_search_input"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HistoryRangeFilter.entries.forEach { range ->
                        val selected = uiState.selectedHistoryRange == range
                        val chipShape = RoundedCornerShape(999.dp)
                        Box(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(chipShape)
                                .background(
                                    color = if (selected) OceanTealPrimary else Color.White.copy(alpha = 0.72f),
                                    shape = chipShape
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (selected) OceanTealPrimary else Color.White,
                                    shape = chipShape
                                )
                                .clickable { onSelectHistoryRange(range) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("history_filter_${range.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = range.label,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (selected) Color.White else InkPrimary
                            )
                        }
                    }
                }

                if (filteredModalHistory.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, Color.White)
                    ) {
                        Column(
                            modifier = Modifier.padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "No patients seen in this period",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = InkPrimary
                            )
                            Text(
                                text = "Clear the search or switch back to Last 30 Days.",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkMuted
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        items(filteredModalHistory, key = { it.id }) { record ->
                            DoctorHistoryPatientRow(
                                record = record,
                                onClick = { selectedHistoryRecord = record },
                                modifier = Modifier.testTag("history_card_${record.id}")
                            )
                        }
                    }
                }
            }
        }
    }

    // Selected Encounter History Detail Modal Sheet
    selectedHistoryRecord?.let { record ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { selectedHistoryRecord = null },
            sheetState = sheetState,
            containerColor = Color(0xFFF3F8FA)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = record.patientName,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = InkPrimary
                        )
                        Text(
                            text = "${record.ageLabel} • ${record.gender} • ${record.mrn}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = OceanTealDark
                        )
                    }
                    ConsultationTypeBadge(type = record.consultationType)
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, ClinicalBorder)
                    ) {
                        Text(
                            text = if (record.daysAgo == 0) "Today • ${record.visitDate}" else "${record.daysAgo}d ago • ${record.visitDate}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = InkSecondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                    if (record.followUpRecommended) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = AmberBg,
                            border = BorderStroke(1.dp, AmberBorder)
                        ) {
                            Text(
                                text = "Follow-up Recommended",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = AmberText,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                // Diagnosis & Clinical Outcome
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, ClinicalBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "DIAGNOSIS & CLINICAL OUTCOME",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFamily,
                                letterSpacing = 0.6.sp
                            ),
                            color = OceanTealPrimary
                        )
                        Text(
                            text = record.diagnosisSummary,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = InkPrimary
                        )
                        Text(
                            text = record.clinicalOutcome,
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSecondary
                        )
                    }
                }

                // Vitals Snapshot
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, ClinicalBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MonitorHeart,
                            contentDescription = null,
                            tint = OceanTealPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Recorded Vitals",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = InkPrimary
                            )
                            Text(
                                text = record.vitalsSummary,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                                color = InkSecondary
                            )
                        }
                    }
                }

                // Medications Prescribed
                if (record.medicationsPrescribed.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, ClinicalBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Medication,
                                    contentDescription = null,
                                    tint = OceanTealPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Prescribed Regimen",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = InkPrimary
                                )
                            }
                            record.medicationsPrescribed.forEach { med ->
                                Text(
                                    text = "• $med",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkSecondary
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        val historyId = record.id
                        selectedHistoryRecord = null
                        showHistorySheet = false
                        onStartHistoryAiChat(historyId)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("history_start_ai_convo_button"),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OceanTealPrimary)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Start AI Conversation",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }

                if (record.linkedAppointmentId != null) {
                    OutlinedButton(
                        onClick = {
                            val id = record.linkedAppointmentId
                            selectedHistoryRecord = null
                            showHistorySheet = false
                            onOpenAppointment(id)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("history_open_chart_button"),
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.2.dp, OceanTealPrimary.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White.copy(alpha = 0.72f),
                            contentColor = OceanTealDark
                        )
                    ) {
                        Text(
                            text = "Open Full Chart Workspace",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }

    if (showNotificationsSheet) {
        var draftPrefs by remember { mutableStateOf(uiState.notificationPreferences) }
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Notification preferences",
                    style = MaterialTheme.typography.headlineSmall,
                    color = InkPrimary
                )
                TogglePrefRow(
                    title = "Urgent Lab & Cardiac Biomarker Alerts",
                    subtitle = "Immediate high-priority alert for Troponin-I or ECG uploads",
                    checked = draftPrefs.urgentLabAlerts,
                    onCheckedChange = { draftPrefs = draftPrefs.copy(urgentLabAlerts = it) }
                )
                TogglePrefRow(
                    title = "AI Intake Draft Ready",
                    subtitle = "Notify when patient pre-visit summary awaits review",
                    checked = draftPrefs.aiSummaryReadyAlerts,
                    onCheckedChange = { draftPrefs = draftPrefs.copy(aiSummaryReadyAlerts = it) }
                )
                TogglePrefRow(
                    title = "Failed Summary Retry Alerts",
                    subtitle = "Notify immediately if structured intake parsing times out",
                    checked = draftPrefs.failedSummaryAlerts,
                    onCheckedChange = { draftPrefs = draftPrefs.copy(failedSummaryAlerts = it) }
                )
                TogglePrefRow(
                    title = "OPD Queue Arrival Alerts",
                    subtitle = "Alert when next in-person or video patient checks in",
                    checked = draftPrefs.queueArrivalAlerts,
                    onCheckedChange = { draftPrefs = draftPrefs.copy(queueArrivalAlerts = it) }
                )
                Button(
                    onClick = {
                        onUpdateNotificationPrefs(draftPrefs)
                        showNotificationsSheet = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SlateGunmetalDark)
                ) {
                    Text("Save Preferences")
                }
            }
        }
    }

    if (showPrivacySheet) {
        var draftPrivacy by remember { mutableStateOf(uiState.privacyPreferences) }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showPrivacySheet = false },
            sheetState = sheetState,
            containerColor = ClinicalSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Privacy & security",
                    style = MaterialTheme.typography.headlineSmall,
                    color = InkPrimary
                )
                TogglePrefRow(
                    title = "Biometric Session Lock",
                    subtitle = "Require fingerprint/face unlock when returning from background",
                    checked = draftPrivacy.biometricLockEnabled,
                    onCheckedChange = { draftPrivacy = draftPrivacy.copy(biometricLockEnabled = it) }
                )
                TogglePrefRow(
                    title = "Consent-Gated Similar Hospital Cases",
                    subtitle = "Require explicit consent confirmation before viewing cohort comparisons",
                    checked = draftPrivacy.requireConsentForSimilarCases,
                    onCheckedChange = { draftPrivacy = draftPrivacy.copy(requireConsentForSimilarCases = it) }
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SlateSoft,
                    border = BorderStroke(1.dp, SlateContainer)
                ) {
                    Text(
                        text = "Clinical Safety Audit: All AI-generated intake drafts require explicit physician approval before linking to the permanent medical record.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkPrimary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Button(
                    onClick = {
                        onUpdatePrivacyPrefs(draftPrivacy)
                        showPrivacySheet = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SlateGunmetalDark)
                ) {
                    Text("Save Security Settings")
                }
            }
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = ClinicalSurface,
            title = {
                Text("About VerityScribe Doctor", style = MaterialTheme.typography.headlineSmall)
            },
            text = {
                Text(
                    text = "VerityScribe Doctor (UI Prototype v2.6) streamlines pre-visit AI intake review, consent-verified transcript auditing, OCR report inspection, and structured e-prescribing for Verity General Hospital physicians.\n\nAll records displayed in this prototype are local demonstration data.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Close", color = SlateGunmetalDark)
                }
            }
        )
    }
}

@Composable
private fun HistoryStatPill(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.56f))
            .border(1.dp, Color.White.copy(alpha = 0.84f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.5.sp
            ),
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DoctorHistoryPatientRow(
    record: DoctorHistoryRecord,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rowShape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(Color.White.copy(alpha = 0.54f), rowShape)
            .border(1.dp, Color.White.copy(alpha = 0.85f), rowShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(OceanTealPrimary.copy(alpha = 0.14f))
                    .border(1.dp, OceanTealPrimary.copy(alpha = 0.28f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = record.patientName.firstOrNull()?.uppercaseChar()?.toString() ?: "P",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = OceanTealDark
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = record.patientName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = InkPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (record.daysAgo == 0) "Today" else "${record.daysAgo}d ago • ${record.visitDate.substringBeforeLast(" ")}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.5.sp
                        ),
                        color = OceanTealPrimary
                    )
                }

                Text(
                    text = record.diagnosisSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = record.mrn,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp
                        ),
                        color = InkMuted
                    )
                    if (record.followUpRecommended) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(AmberBg.copy(alpha = 0.85f))
                                .border(1.dp, AmberBorder, RoundedCornerShape(999.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Follow-up",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = AmberText
                            )
                        }
                    }
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "Inspect encounter details",
                tint = OceanTealPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun CredentialCheckRow(
    label: String,
    value: String,
    useMono: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.CheckCircleOutline,
            contentDescription = null,
            tint = OceanTealPrimary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = "$label • ",
            style = MaterialTheme.typography.bodyMedium,
            color = InkSecondary
        )
        Text(
            text = value,
            style = if (useMono) {
                MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Medium
                )
            } else {
                MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
            },
            color = InkPrimary
        )
    }
}

@Composable
private fun ProfileActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = OceanTealDark,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = InkPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = InkSecondary
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = OceanTealPrimary
        )
    }
}

@Composable
private fun TogglePrefRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = InkPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = SlateGunmetalDark
            )
        )
    }
}
