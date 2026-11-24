package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.ConsultationRecord
import com.example.model.MedicalReport
import com.example.model.RecordsSectionTab
import java.io.File
import com.example.ui.components.VerityGlassCard
import com.example.ui.components.VerityGlassPillButton
import com.example.ui.components.VerityGlassSecondaryPill
import com.example.ui.theme.GlassAlertRed
import com.example.ui.theme.GlassBorderBottom
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.IceBlueText
import com.example.ui.theme.PeriwinkleMist
import com.example.ui.theme.PlatinumWhite
import com.example.ui.theme.SilverText
import com.example.ui.theme.SlateInkDark
import com.example.ui.theme.SoftLavenderText
import kotlin.math.abs
import kotlin.math.sin

@Composable
internal fun RecordsSegmentedTabBar(
    selectedTab: RecordsSectionTab,
    consultationsCount: Int,
    reportsCount: Int,
    onSelectTab: (RecordsSectionTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val outerShape = CircleShape
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(outerShape)
            .background(
                Brush.verticalGradient(listOf(Color(0x2EFFFFFF), Color(0x14FFFFFF))),
                outerShape
            )
            .border(
                1.dp,
                Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderBottom)),
                outerShape
            )
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        RecordsSectionTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            val count = if (tab == RecordsSectionTab.CONSULTATIONS) consultationsCount else reportsCount
            val icon = if (tab == RecordsSectionTab.CONSULTATIONS) {
                Icons.Filled.Description
            } else {
                Icons.Filled.Biotech
            }
            val textColor by animateColorAsState(
                targetValue = if (selected) SlateInkDark else PlatinumWhite,
                animationSpec = tween(200),
                label = "records_tab_text"
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 46.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) {
                            Brush.verticalGradient(listOf(Color(0xFFF7F8FA), Color(0xFFDDE7F6)))
                        } else {
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                        },
                        CircleShape
                    )
                    .clickable { onSelectTab(tab) }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("records_tab_${tab.name.lowercase()}"),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) SlateInkDark else IceBlueText,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = textColor,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (selected) SlateInkDark.copy(alpha = 0.12f) else Color(0x28FFFFFF)
                        )
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) SlateInkDark else SoftLavenderText
                    )
                }
            }
        }
    }
}

@Composable
internal fun ConsultationRecordCard(
    record: ConsultationRecord,
    expanded: Boolean,
    isPlaying: Boolean,
    currentSeconds: Int,
    onToggleExpanded: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekPlayback: (Int) -> Unit,
    onDownloadPdf: () -> Unit
) {
    VerityGlassCard(
        cornerRadius = 24.dp,
        strong = expanded,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("consultation_card_${record.id}")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Top Doctor & Visit Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (record.avatarRes != 0) {
                    Image(
                        painter = painterResource(id = record.avatarRes),
                        contentDescription = record.doctorName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, IceBlueText.copy(alpha = 0.65f), CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color(0x2BFFFFFF))
                            .border(1.dp, Color(0x47FFFFFF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Description,
                            contentDescription = null,
                            tint = PlatinumWhite,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.doctorName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = PlatinumWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${record.specialty} • ${record.hospital}",
                        style = MaterialTheme.typography.bodySmall,
                        color = SoftLavenderText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${record.dateLabel} • ${record.timeLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = IceBlueText
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x26FFFFFF))
                        .border(1.dp, Color(0x42FFFFFF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = PlatinumWhite,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Clean full-width Diagnosis & Verification Bento Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x1EFFFFFF))
                    .border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(14.dp))
                    .clickable(onClick = onToggleExpanded)
                    .padding(horizontal = 13.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DIAGNOSIS",
                        style = MaterialTheme.typography.labelSmall,
                        color = SilverText
                    )
                    Text(
                        text = record.diagnosis,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = PlatinumWhite
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0x2693C5FD))
                        .border(1.dp, IceBlueText.copy(alpha = 0.55f), CircleShape)
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = null,
                        tint = IceBlueText,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = record.verifiedLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = IceBlueText
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(240)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(150))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Prescription Bento Container
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0x1AFFFFFF))
                            .border(1.dp, Color(0x30FFFFFF), RoundedCornerShape(18.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Generated prescription",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = PlatinumWhite
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color(0x24FFFFFF))
                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CalendarToday,
                                    contentDescription = null,
                                    tint = SoftLavenderText,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Follow-up ${record.followUpDate}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SoftLavenderText
                                )
                            }
                        }

                        HorizontalDivider(color = Color(0x1CFFFFFF))

                        record.prescriptions.forEach { line ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0x16FFFFFF))
                                    .padding(horizontal = 11.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Medication,
                                    contentDescription = null,
                                    tint = IceBlueText,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(9.dp))
                                Text(
                                    text = line.medicineName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = PlatinumWhite,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = line.instruction,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PeriwinkleMist
                                )
                            }
                        }
                    }

                    // Upgraded Frosted Voice Waveform Player
                    VoiceRecordingPlayerCard(
                        recordId = record.id,
                        isPlaying = isPlaying,
                        currentSeconds = currentSeconds,
                        totalSeconds = record.recordingTotalSeconds,
                        onTogglePlayback = onTogglePlayback,
                        onSeekPlayback = onSeekPlayback
                    )

                    VerityGlassPillButton(
                        text = "Download prescription PDF",
                        leadingIcon = Icons.Filled.Download,
                        onClick = onDownloadPdf,
                        modifier = Modifier.testTag("download_pdf_${record.id}")
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceRecordingPlayerCard(
    recordId: String,
    isPlaying: Boolean,
    currentSeconds: Int,
    totalSeconds: Int,
    onTogglePlayback: () -> Unit,
    onSeekPlayback: (Int) -> Unit
) {
    val safeTotal = totalSeconds.coerceAtLeast(1)
    val progress = (currentSeconds.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f)

    // Deterministic organic speech-like waveform contour per record
    val barAmplitudes = remember(recordId) {
        val seed = recordId.hashCode()
        List(34) { idx ->
            val wave1 = abs(sin((idx + (seed and 0x7)) * 0.48))
            val wave2 = abs(sin((idx * 1.7 + (seed shr 3 and 0xF)) * 0.31))
            (0.24f + 0.76f * ((wave1 * 0.65 + wave2 * 0.35).toFloat())).coerceIn(0.22f, 1f)
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "voice_bar_pulse")
    val livePulse by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "voice_live_pulse"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0x2D3C4454), Color(0x22232833))
                ),
                RoundedCornerShape(18.dp)
            )
            .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = null,
                    tint = IceBlueText,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Consultation audio",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = PlatinumWhite
                )
            }
            Text(
                text = "${formatRecordSeconds(currentSeconds)} / ${formatRecordSeconds(totalSeconds)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = IceBlueText
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isPlaying) {
                            Brush.verticalGradient(listOf(Color(0xFFF7F8FA), Color(0xFFDDE7F6)))
                        } else {
                            Brush.verticalGradient(listOf(Color(0x3DFFFFFF), Color(0x1CFFFFFF)))
                        }
                    )
                    .border(
                        1.dp,
                        if (isPlaying) Color.White else Color(0x59FFFFFF),
                        CircleShape
                    )
                    .clickable(onClick = onTogglePlayback)
                    .testTag("play_recording_$recordId"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause recording" else "Play recording",
                    tint = if (isPlaying) SlateInkDark else PlatinumWhite,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Interactive Scannable Waveform Bar
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .pointerInput(recordId, safeTotal) {
                        detectTapGestures { offset ->
                            val frac = (offset.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                            onSeekPlayback((frac * safeTotal).toInt())
                        }
                    }
                    .pointerInput(recordId, safeTotal) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            val frac = (change.position.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                            onSeekPlayback((frac * safeTotal).toInt())
                        }
                    }
                    .testTag("waveform_seek_$recordId")
            ) {
                val barCount = barAmplitudes.size
                val slotWidth = size.width / barCount
                val barWidth = (slotWidth * 0.54f).coerceAtLeast(2.5.dp.toPx())
                val centerY = size.height / 2f
                val playedIndex = (progress * barCount).toInt()

                for (i in 0 until barCount) {
                    val baseAmp = barAmplitudes[i]
                    val animatedFactor = if (isPlaying && i in (playedIndex - 3)..(playedIndex + 3)) {
                        (0.84f + 0.24f * abs(sin(livePulse + i * 0.8f)))
                    } else {
                        1f
                    }
                    val barHeight = (size.height * 0.84f * baseAmp * animatedFactor)
                        .coerceIn(5.dp.toPx(), size.height * 0.92f)
                    val x = i * slotWidth + (slotWidth - barWidth) / 2f
                    val isPlayed = i.toFloat() / barCount.toFloat() <= progress
                    val isPlayhead = i == playedIndex.coerceIn(0, barCount - 1)

                    val barColor = when {
                        isPlayhead -> PlatinumWhite
                        isPlayed -> IceBlueText
                        else -> Color(0x47FFFFFF)
                    }

                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, centerY - barHeight / 2f),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                }
            }
        }
    }
}

@Composable
internal fun MedicalReportCard(
    report: MedicalReport,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onShareWithDoctor: () -> Unit,
    onDownloadReport: () -> Unit
) {
    val categoryIcon = when (report.category) {
        "Cardiology" -> Icons.Filled.MonitorHeart
        "Metabolic" -> Icons.Filled.Science
        "Imaging" -> Icons.Filled.Image
        "General" -> Icons.Filled.Description
        else -> Icons.Filled.Biotech
    }
    val statusTint = when (report.status) {
        "ATTENTION" -> GlassAlertRed
        "OPTIMAL", "UPLOADED" -> IceBlueText
        else -> SoftLavenderText
    }
    val attachedFile = remember(report.attachmentPath) {
        report.attachmentPath?.let { File(it) }?.takeIf { it.exists() }
    }
    val isImageAttachment = report.attachmentMimeType?.startsWith("image/", ignoreCase = true) == true

    VerityGlassCard(
        cornerRadius = 24.dp,
        strong = expanded,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("report_card_${report.id}")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0x30FFFFFF), Color(0x16FFFFFF))
                            )
                        )
                        .border(1.dp, Color(0x47FFFFFF), RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = null,
                        tint = statusTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = report.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = PlatinumWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${report.labName} • ${report.dateLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = IceBlueText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(statusTint.copy(alpha = 0.18f))
                                .border(1.dp, statusTint.copy(alpha = 0.55f), CircleShape)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = report.status,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = statusTint
                            )
                        }
                        Text(
                            text = "Ordered by ${report.orderedBy}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SilverText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x26FFFFFF))
                        .border(1.dp, Color(0x42FFFFFF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = PlatinumWhite,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(240)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(150))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Biomarker Metrics Bento Container
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0x1AFFFFFF))
                            .border(1.dp, Color(0x30FFFFFF), RoundedCornerShape(18.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Key Biomarkers",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = PlatinumWhite
                        )
                        HorizontalDivider(color = Color(0x1CFFFFFF))
                        report.metrics.forEach { metric ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0x15FFFFFF))
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = metric.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = PlatinumWhite
                                    )
                                    Text(
                                        text = "Ref: ${metric.referenceRange}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SilverText
                                    )
                                }
                                Text(
                                    text = metric.value,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (metric.isFlagged) GlassAlertRed else IceBlueText
                                )
                            }
                        }
                    }

                    // Uploaded Image Preview or Attachment Pill
                    if (attachedFile != null && isImageAttachment) {
                        AsyncImage(
                            model = attachedFile,
                            contentDescription = "${report.title} preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, Color(0x3DFFFFFF), RoundedCornerShape(16.dp))
                                .clickable(onClick = onDownloadReport)
                        )
                    } else if (attachedFile != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(IceBlueText.copy(alpha = 0.14f))
                                .border(1.dp, IceBlueText.copy(alpha = 0.42f), RoundedCornerShape(14.dp))
                                .clickable(onClick = onDownloadReport)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.UploadFile,
                                contentDescription = null,
                                tint = IceBlueText,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = report.attachmentName ?: attachedFile.name,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = PlatinumWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Filled.OpenInNew,
                                contentDescription = "Open file",
                                tint = IceBlueText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // AI Clinical Takeaway
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x1EFFFFFF))
                            .border(1.dp, Color(0x30FFFFFF), RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = SoftLavenderText,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = report.summaryNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = PlatinumWhite.copy(alpha = 0.94f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        VerityGlassSecondaryPill(
                            text = "Share",
                            onClick = onShareWithDoctor,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("share_report_${report.id}")
                        )
                        VerityGlassPillButton(
                            text = if (attachedFile != null) "Open File" else "Download",
                            leadingIcon = if (attachedFile != null) Icons.Filled.OpenInNew else Icons.Filled.Download,
                            onClick = onDownloadReport,
                            minHeight = 48.dp,
                            horizontalPadding = 14.dp,
                            modifier = Modifier
                                .weight(1.25f)
                                .testTag("download_report_${report.id}")
                        )
                    }
                }
            }
        }
    }
}

private fun formatRecordSeconds(totalSeconds: Int): String {
    val mins = totalSeconds / 60
    val secs = totalSeconds % 60
    return String.format("%02d:%02d", mins, secs)
}
