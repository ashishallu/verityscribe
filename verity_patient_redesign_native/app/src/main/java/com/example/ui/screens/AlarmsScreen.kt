package com.example.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.DosePeriod
import com.example.model.MedicationScheduleItem
import com.example.ui.components.VerityGlassBackdrop
import com.example.ui.components.VerityGlassCard
import com.example.ui.components.VerityGlassChip
import com.example.ui.components.VerityPillIconBadge
import com.example.ui.theme.CharcoalBase
import com.example.ui.theme.GlassProgressActive
import com.example.ui.theme.GlassProgressTrack
import com.example.ui.theme.IceBlueText
import com.example.ui.theme.PeriwinkleMist
import com.example.ui.theme.PlatinumWhite
import com.example.ui.theme.SilverText
import com.example.ui.theme.SoftLavenderText
import com.example.viewmodel.VerityUiState

@Composable
fun AlarmsScreen(
    uiState: VerityUiState,
    onToggleDose: (String) -> Unit,
    onOpenMedicineDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showScheduleTip by remember { mutableStateOf(false) }

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
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top bar
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Medication planner",
                            style = MaterialTheme.typography.headlineMedium,
                            color = PlatinumWhite
                        )
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0x2BFFFFFF), Color(0x12FFFFFF))
                                    )
                                )
                                .border(1.dp, Color(0x47FFFFFF), CircleShape)
                                .clickable { showScheduleTip = true }
                                .testTag("planner_scanner_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DocumentScanner,
                                contentDescription = "Scan schedule",
                                tint = PlatinumWhite
                            )
                        }
                    }
                }

                // Today's progress glass card
                item {
                    val total = uiState.totalTodayCount.coerceAtLeast(1)
                    val taken = uiState.takenTodayCount
                    val fraction = (taken.toFloat() / total.toFloat()).coerceIn(0f, 1f)

                    VerityGlassCard(
                        cornerRadius = 22.dp,
                        strong = true,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(62.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val stroke = 6.dp.toPx()
                                    drawArc(
                                        color = GlassProgressTrack,
                                        startAngle = 0f,
                                        sweepAngle = 360f,
                                        useCenter = false,
                                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                                    )
                                    drawArc(
                                        color = GlassProgressActive,
                                        startAngle = -90f,
                                        sweepAngle = 360f * fraction,
                                        useCenter = false,
                                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                                    )
                                }
                                Text(
                                    text = "$taken/$total",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = PlatinumWhite
                                )
                            }

                            Spacer(modifier = Modifier.width(18.dp))

                            Column {
                                Text(
                                    text = "Today's progress",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PlatinumWhite
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "$taken medicines taken • ${uiState.pendingTodayCount} remaining",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PeriwinkleMist
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                VerityGlassChip(
                                    text = "NEXT • 08:00 PM",
                                    highlighted = true
                                )
                            }
                        }
                    }
                }

                // Period sections
                DosePeriod.entries.forEach { period ->
                    item(key = "period_${period.name}") {
                        val periodItems = uiState.scheduleItems.filter { it.period == period }
                        val dotColor = when (period) {
                            DosePeriod.MORNING -> IceBlueText
                            DosePeriod.AFTERNOON -> PeriwinkleMist
                            DosePeriod.EVENING -> SoftLavenderText
                            DosePeriod.NIGHT -> Color(0xFFA78BFA)
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(dotColor)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = period.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = PlatinumWhite
                                    )
                                }
                                Text(
                                    text = period.defaultTime,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = IceBlueText
                                )
                            }

                            if (periodItems.isEmpty()) {
                                VerityGlassCard(
                                    cornerRadius = 18.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "No reminders scheduled",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = SilverText,
                                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
                                    )
                                }
                            } else {
                                periodItems.forEach { item ->
                                    ScheduleDoseCard(
                                        item = item,
                                        isTaken = item.id in uiState.takenScheduleIds,
                                        onToggle = { onToggleDose(item.id) },
                                        onOpenMedicine = { onOpenMedicineDetail(item.medicineId) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showScheduleTip) {
        AlertDialog(
            onDismissRequest = { showScheduleTip = false },
            containerColor = CharcoalBase,
            title = { Text("Sync Medication Reminders", color = PlatinumWhite) },
            text = {
                Text(
                    "Tap TAKEN or PENDING on any scheduled medicine to update today's adherence log.",
                    color = SilverText
                )
            },
            confirmButton = {
                TextButton(onClick = { showScheduleTip = false }) {
                    Text("Close", color = PlatinumWhite)
                }
            }
        )
    }
}

@Composable
private fun ScheduleDoseCard(
    item: MedicationScheduleItem,
    isTaken: Boolean,
    onToggle: () -> Unit,
    onOpenMedicine: () -> Unit
) {
    VerityGlassCard(
        cornerRadius = 20.dp,
        onClick = onOpenMedicine,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VerityPillIconBadge(
                size = 46.dp,
                cornerRadius = 14.dp,
                containerColor = if (item.isGreenAccent) Color(0x2EFFFFFF) else Color(0x1EFFFFFF),
                iconTint = PlatinumWhite
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.medicineName,
                    style = MaterialTheme.typography.titleMedium,
                    color = PlatinumWhite
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = PeriwinkleMist
                )
                Text(
                    text = item.prescriberNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = SoftLavenderText.copy(alpha = 0.88f)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggle)
                    .testTag("alarm_toggle_${item.id}"),
                contentAlignment = Alignment.Center
            ) {
                VerityGlassChip(
                    text = if (isTaken) "TAKEN" else "PENDING",
                    highlighted = isTaken
                )
            }
        }
    }
}
