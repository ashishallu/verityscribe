package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AppointmentStatus
import com.example.model.ClinicalAppointment
import com.example.model.ConsultationType
import com.example.ui.theme.AmberBg
import com.example.ui.theme.AmberBorder
import com.example.ui.theme.AmberText
import com.example.ui.theme.ClinicalBorder
import com.example.ui.theme.ClinicalSurface
import com.example.ui.theme.ClinicalSurfaceSubtle
import com.example.ui.theme.EmeraldBg
import com.example.ui.theme.EmeraldBorder
import com.example.ui.theme.EmeraldText
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.InkSubtle
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.NeutralBg
import com.example.ui.theme.NeutralBorder
import com.example.ui.theme.NeutralText
import com.example.ui.theme.OceanTealDark
import com.example.ui.theme.OceanTealPrimary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.example.ui.theme.RoseBg
import com.example.ui.theme.RoseBorder
import com.example.ui.theme.RoseText
import com.example.ui.theme.SkyBg
import com.example.ui.theme.SkyBorder
import com.example.ui.theme.SkyText
import com.example.ui.theme.SlateContainer
import com.example.ui.theme.SlateGunmetalDark
import com.example.ui.theme.SlateGunmetalLight
import com.example.ui.theme.SlateGunmetalMid
import com.example.ui.theme.SlateSoft

@Composable
fun StatusBadge(
    status: AppointmentStatus,
    modifier: Modifier = Modifier
) {
    val (bg, border, textColor, icon) = when (status) {
        AppointmentStatus.AWAITING_REVIEW -> Quadruple(AmberBg, AmberBorder, AmberText, Icons.Rounded.Schedule)
        AppointmentStatus.APPROVED -> Quadruple(EmeraldBg, EmeraldBorder, EmeraldText, Icons.Rounded.CheckCircle)
        AppointmentStatus.IN_PROGRESS -> Quadruple(SkyBg, SkyBorder, SkyText, Icons.Rounded.AutoAwesome)
        AppointmentStatus.COMPLETED -> Quadruple(EmeraldBg, EmeraldBorder, EmeraldText, Icons.Rounded.CheckCircle)
        AppointmentStatus.FAILED_SUMMARY -> Quadruple(RoseBg, RoseBorder, RoseText, Icons.Rounded.ErrorOutline)
        AppointmentStatus.REJECTED -> Quadruple(NeutralBg, NeutralBorder, NeutralText, Icons.Rounded.Block)
        AppointmentStatus.SCHEDULED -> Quadruple(SkyBg, SkyBorder, SkyText, Icons.Rounded.Schedule)
        AppointmentStatus.WAITING -> Quadruple(AmberBg, AmberBorder, AmberText, Icons.Rounded.Schedule)
        AppointmentStatus.CANCELLED -> Quadruple(NeutralBg, NeutralBorder, NeutralText, Icons.Rounded.Block)
        AppointmentStatus.INCOMPLETE -> Quadruple(NeutralBg, NeutralBorder, NeutralText, Icons.Rounded.Block)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = bg,
        border = BorderStroke(1.dp, border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = status.label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = textColor,
                maxLines = 1
            )
        }
    }
}

@Composable
fun ConsultationTypeBadge(
    type: ConsultationType,
    modifier: Modifier = Modifier
) {
    val icon: ImageVector = when (type) {
        ConsultationType.AI_CHAT -> Icons.Rounded.AutoAwesome
        ConsultationType.AI_VIRTUAL -> Icons.Rounded.AutoAwesome
        ConsultationType.IN_PERSON -> Icons.Rounded.LocalHospital
        ConsultationType.VIDEO -> Icons.Rounded.Videocam
        ConsultationType.PHONE -> Icons.Rounded.Call
        ConsultationType.CHAT -> Icons.AutoMirrored.Rounded.Chat
    }

    val isAi = type == ConsultationType.AI_CHAT || type == ConsultationType.AI_VIRTUAL
    val bgColor = if (isAi) SlateSoft else ClinicalSurfaceSubtle
    val borderColor = if (isAi) SlateGunmetalLight.copy(alpha = 0.35f) else ClinicalBorder
    val contentColor = if (isAi) SlateGunmetalDark else InkSecondary

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = type.label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

@Composable
fun PatientInitialAvatar(
    name: String,
    modifier: Modifier = Modifier,
    highlightColor: Color = SlateGunmetalDark
) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "P"
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(highlightColor.copy(alpha = 0.12f))
            .border(1.dp, highlightColor.copy(alpha = 0.24f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = highlightColor
        )
    }
}

@Composable
fun AppointmentQueueCard(
    appointment: ClinicalAppointment,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glassy: Boolean = false
) {
    val avatarColor = when (appointment.status) {
        AppointmentStatus.FAILED_SUMMARY -> RoseText
        AppointmentStatus.IN_PROGRESS -> SkyText
        AppointmentStatus.COMPLETED, AppointmentStatus.APPROVED -> EmeraldText
        AppointmentStatus.AWAITING_REVIEW -> SlateGunmetalMid
        AppointmentStatus.REJECTED -> NeutralText
        AppointmentStatus.SCHEDULED -> SkyText
        AppointmentStatus.WAITING -> AmberText
        AppointmentStatus.CANCELLED -> NeutralText
        AppointmentStatus.INCOMPLETE -> NeutralText
    }

    val cardShape = RoundedCornerShape(if (glassy) 22.dp else 16.dp)
    val cardModifier = if (glassy) {
        modifier
            .fillMaxWidth()
            .testTag("appointment_card_${appointment.id}")
            .clip(cardShape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.64f),
                        Color.White.copy(alpha = 0.44f)
                    )
                ),
                shape = cardShape
            )
            .border(
                width = 1.2.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.94f),
                        Color.White.copy(alpha = 0.42f)
                    )
                ),
                shape = cardShape
            )
            .clickable(onClick = onClick)
    } else {
        modifier
            .fillMaxWidth()
            .testTag("appointment_card_${appointment.id}")
            .clip(cardShape)
            .clickable(onClick = onClick)
    }

    Surface(
        modifier = cardModifier,
        shape = cardShape,
        color = if (glassy) Color.Transparent else ClinicalSurface,
        border = if (glassy) null else BorderStroke(1.dp, ClinicalBorder),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                PatientInitialAvatar(
                    name = appointment.patientName,
                    highlightColor = avatarColor
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = appointment.patientName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = InkPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusBadge(status = appointment.status)
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "${appointment.ageLabel} • ${appointment.gender}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (glassy) InkSecondary else InkMuted
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = listOfNotNull(
                            appointment.appointmentDate?.take(10)?.let { rawDate ->
                                runCatching {
                                    LocalDate.parse(rawDate).format(
                                        DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())
                                    )
                                }.getOrNull()
                            },
                            appointment.scheduledTime.takeIf(String::isNotBlank)
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = InkPrimary
                    )
                    ConsultationTypeBadge(type = appointment.consultationType)
                }

                if (appointment.reports.isNotEmpty()) {
                    Row(
                        modifier = if (glassy) {
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.White.copy(alpha = 0.52f))
                                .border(1.dp, Color.White.copy(alpha = 0.72f), RoundedCornerShape(999.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        } else {
                            Modifier
                        },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Description,
                            contentDescription = null,
                            tint = if (glassy) OceanTealPrimary else InkMuted,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "${appointment.reports.size}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (glassy) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = if (glassy) OceanTealDark else InkMuted
                        )
                    }
                }
            }

            val complaintContainerModifier = if (glassy) {
                Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.52f), RoundedCornerShape(14.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.78f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            } else {
                Modifier
                    .fillMaxWidth()
                    .background(ClinicalSurfaceSubtle, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            }

            Row(
                modifier = complaintContainerModifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = appointment.chiefComplaint,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = InkPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = if (glassy) {
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.72f))
                            .border(1.dp, Color.White, CircleShape)
                    } else {
                        Modifier
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Open consultation",
                        tint = if (glassy) OceanTealPrimary else InkMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ClinicalSafetyBanner(
    status: AppointmentStatus,
    rejectionReason: String? = null,
    modifier: Modifier = Modifier
) {
    val (bg, border, iconTint, titleText, icon) = when (status) {
        AppointmentStatus.APPROVED, AppointmentStatus.COMPLETED -> Quintuple(
            EmeraldBg,
            EmeraldBorder,
            EmeraldText,
            "Physician Approved — Linked to Patient Chart",
            Icons.Rounded.VerifiedUser
        )
        AppointmentStatus.REJECTED -> Quintuple(
            RoseBg,
            RoseBorder,
            RoseText,
            "Draft Rejected: ${rejectionReason ?: "Clinician requested manual intake"}",
            Icons.Rounded.Block
        )
        AppointmentStatus.FAILED_SUMMARY -> Quintuple(
            RoseBg,
            RoseBorder,
            RoseText,
            "Automated Summary Failed — Raw Transcript Preserved Below",
            Icons.Rounded.ErrorOutline
        )
        else -> Quintuple(
            AmberBg,
            AmberBorder,
            AmberText,
            stringResource(R.string.ai_draft_warning_banner),
            Icons.Rounded.WarningAmber
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = bg,
        border = BorderStroke(1.dp, border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = titleText,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = iconTint,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun ConsentVerifiedBadge(
    timestamp: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = SlateSoft,
        border = BorderStroke(1.dp, SlateContainer)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = SlateGunmetalDark,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "Consent Verified • $timestamp",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = SlateGunmetalDark
            )
        }
    }
}

@Composable
fun ClinicalSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    testTag: String,
    modifier: Modifier = Modifier,
    glassy: Boolean = false
) {
    val fieldShape = RoundedCornerShape(if (glassy) 20.dp else 16.dp)
    val surfaceModifier = if (glassy) {
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 50.dp)
            .testTag(testTag)
            .clip(fieldShape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.28f),
                        Color.White.copy(alpha = 0.14f)
                    )
                ),
                shape = fieldShape
            )
            .border(
                width = 1.2.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.68f),
                        Color.White.copy(alpha = 0.24f)
                    )
                ),
                shape = fieldShape
            )
    } else {
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag(testTag)
    }

    Surface(
        modifier = surfaceModifier,
        shape = fieldShape,
        color = if (glassy) Color.Transparent else Color.White.copy(alpha = 0.94f),
        border = if (glassy) null else BorderStroke(1.dp, Color.White),
        shadowElevation = if (glassy) 0.dp else 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = "Search",
                tint = if (glassy) Color.White.copy(alpha = 0.92f) else InkMuted,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (glassy) Color.White.copy(alpha = 0.78f) else InkSubtle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = MaterialTheme.typography.bodyMedium.fontFamily,
                        fontSize = 14.sp,
                        color = if (glassy) Color.White else InkPrimary
                    ),
                    cursorBrush = SolidColor(if (glassy) Color.White else SlateGunmetalDark),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Clear search",
                        tint = if (glassy) Color.White.copy(alpha = 0.90f) else InkMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
private data class Quintuple<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)
