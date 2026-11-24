package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.model.DoctorDirectoryEntry
import com.example.ui.components.VerityGlassBackdrop
import com.example.ui.components.VerityGlassCard
import com.example.ui.components.VerityGlassPillButton
import com.example.ui.components.VerityGlassSecondaryPill
import com.example.ui.theme.GlassBorderBottom
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.IceBlueText
import com.example.ui.theme.PlatinumWhite
import com.example.ui.theme.SilverText
import com.example.ui.theme.SlateInkDark
import com.example.ui.theme.SoftLavenderText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Immutable
internal data class BookingDateOption(
    val dayShort: String,
    val dayNumber: String,
    val monthShort: String,
    val fullLabel: String,
    val isToday: Boolean,
    val isoDate: String
)

internal fun generateNext15Days(): List<BookingDateOption> {
    val calendar = Calendar.getInstance()
    val dayShortFmt = SimpleDateFormat("EEE", Locale.getDefault())
    val dayNumFmt = SimpleDateFormat("dd", Locale.getDefault())
    val monthFmt = SimpleDateFormat("MMM", Locale.getDefault())
    val fullFmt = SimpleDateFormat("EEE, dd MMM", Locale.getDefault())
    val isoFmt = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

    return List(15) { index ->
        val date = calendar.time
        val option = BookingDateOption(
            dayShort = if (index == 0) "Today" else dayShortFmt.format(date),
            dayNumber = dayNumFmt.format(date),
            monthShort = monthFmt.format(date),
            fullLabel = fullFmt.format(date),
            isToday = index == 0,
            isoDate = isoFmt.format(date)
        )
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        option
    }
}

internal fun availableBookingSlots(
    slots: List<String>,
    isToday: Boolean,
    currentHour: Int,
    currentMinute: Int
): List<String> {
    if (!isToday) return slots
    val currentTime = currentHour * 60 + currentMinute
    return slots.filter { slot ->
        val match = Regex("^(\\d{1,2}):(\\d{2})\\s*(AM|PM)$", RegexOption.IGNORE_CASE).matchEntire(slot)
            ?: return@filter false
        val hour12 = match.groupValues[1].toIntOrNull() ?: return@filter false
        val minute = match.groupValues[2].toIntOrNull() ?: return@filter false
        val meridiem = match.groupValues[3].uppercase(Locale.ROOT)
        if (hour12 !in 1..12 || minute !in 0..59) return@filter false
        val hour24 = (hour12 % 12) + if (meridiem == "PM") 12 else 0
        hour24 * 60 + minute > currentTime
    }
}

@Composable
internal fun BookingDropdownField(
    icon: ImageVector,
    text: String,
    isPlaceholder: Boolean,
    expanded: Boolean,
    accentTint: Color = IceBlueText,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    val bgBrush = if (expanded) {
        Brush.verticalGradient(listOf(Color(0x3BFFFFFF), Color(0x1EFFFFFF)))
    } else {
        Brush.verticalGradient(listOf(Color(0x28FFFFFF), Color(0x12FFFFFF)))
    }
    val borderBrush = if (expanded) {
        Brush.verticalGradient(listOf(IceBlueText.copy(alpha = 0.85f), Color(0x45FFFFFF)))
    } else {
        Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderBottom))
    }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(220),
        label = "dropdown_arrow_rotation"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 54.dp)
            .clip(shape)
            .background(bgBrush, shape)
            .border(1.dp, borderBrush, shape)
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isPlaceholder && !expanded) SilverText else accentTint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isPlaceholder) FontWeight.Normal else FontWeight.Medium,
                color = if (isPlaceholder) SilverText else PlatinumWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse options" else "Expand options",
                tint = if (expanded) IceBlueText else SilverText,
                modifier = Modifier
                    .size(22.dp)
                    .rotate(arrowRotation)
            )
        }
    }
}

@Composable
internal fun GlassyFloatingDropdown(
    expanded: Boolean,
    anchorWidthPx: Int,
    anchorHeightPx: Int,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!expanded || anchorWidthPx <= 0) return

    val density = LocalDensity.current
    val widthDp = with(density) { anchorWidthPx.toDp() }
    val verticalOffsetPx = anchorHeightPx + with(density) { 8.dp.roundToPx() }
    val shape = RoundedCornerShape(20.dp)

    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(0, verticalOffsetPx),
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true)
    ) {
        Column(
            modifier = Modifier
                .width(widthDp)
                .heightIn(max = 280.dp)
                .shadow(
                    elevation = 20.dp,
                    shape = shape,
                    ambientColor = Color.Black,
                    spotColor = Color.Black
                )
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xEB3B4150), Color(0xF0222630))
                    ),
                    shape
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(Color(0x88FFFFFF), Color(0x33FFFFFF))
                    ),
                    shape = shape
                )
                .verticalScroll(rememberScrollState()),
            content = content
        )
    }
}

@Composable
internal fun GlassyDropdownOptionRow(
    title: String,
    subtitle: String? = null,
    trailingBadge: String? = null,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val rowBg by animateColorAsState(
        targetValue = if (selected) Color(0x2B93C5FD) else Color.Transparent,
        animationSpec = tween(180),
        label = "dropdown_row_bg"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .background(rowBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) IceBlueText else PlatinumWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) SilverText else SilverText.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (!trailingBadge.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = trailingBadge,
                style = MaterialTheme.typography.labelMedium,
                color = SoftLavenderText
            )
        }
        if (selected) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Selected",
                tint = IceBlueText,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
internal fun GlassyDoctorOptionRow(
    doctor: DoctorDirectoryEntry,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val rowBg by animateColorAsState(
        targetValue = if (selected) Color(0x2B93C5FD) else Color.Transparent,
        animationSpec = tween(180),
        label = "doctor_row_bg"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 60.dp)
            .background(rowBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(doctor.avatarRes),
            contentDescription = doctor.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .border(
                    1.dp,
                    if (selected) IceBlueText else Color(0x55FFFFFF),
                    CircleShape
                )
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = doctor.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) IceBlueText else PlatinumWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${doctor.department} • ${doctor.hospital}",
                style = MaterialTheme.typography.bodySmall,
                color = SilverText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color(0x24FFFFFF))
                .border(1.dp, Color(0x33FFFFFF), CircleShape)
                .padding(horizontal = 9.dp, vertical = 4.dp)
        ) {
            Text(
                text = "₹${doctor.feeRupees}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = SoftLavenderText
            )
        }
        if (selected) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Selected",
                tint = IceBlueText,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
internal fun BookingDatePill(
    option: BookingDateOption,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val shape = RoundedCornerShape(20.dp)
    val fillBrush = if (selected) {
        Brush.verticalGradient(listOf(Color(0xFFF7F8FA), Color(0xFFDDE7F6)))
    } else {
        Brush.verticalGradient(listOf(Color(0x2EFFFFFF), Color(0x12FFFFFF)))
    }
    val borderBrush = if (selected) {
        Brush.verticalGradient(listOf(Color(0xFFFFFFFF), IceBlueText))
    } else {
        Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderBottom))
    }

    Column(
        modifier = Modifier
            .width(68.dp)
            .defaultMinSize(minHeight = 86.dp)
            .clip(shape)
            .background(fillBrush, shape)
            .border(1.dp, borderBrush, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = option.dayShort,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (option.isToday || selected) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                selected -> SlateInkDark.copy(alpha = 0.85f)
                option.isToday -> IceBlueText
                else -> SilverText
            },
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = option.dayNumber,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (selected) SlateInkDark else PlatinumWhite
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = option.monthShort,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) SlateInkDark.copy(alpha = 0.78f) else SoftLavenderText,
            maxLines = 1
        )
    }
}

@Composable
internal fun BookingTimeSlotPill(
    slot: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    val fillBrush = if (selected) {
        Brush.verticalGradient(listOf(Color(0xFFF7F8FA), Color(0xFFDDE7F6)))
    } else {
        Brush.verticalGradient(listOf(Color(0x2BFFFFFF), Color(0x10FFFFFF)))
    }
    val borderBrush = if (selected) {
        Brush.verticalGradient(listOf(Color(0xFFFFFFFF), IceBlueText))
    } else {
        Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderBottom))
    }

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(shape)
            .background(fillBrush, shape)
            .border(1.dp, borderBrush, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = slot,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) SlateInkDark else PlatinumWhite,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
internal fun AllTimeSlotsScreen(
    isDark: Boolean,
    selectedDateLabel: String,
    selectedTimeLabel: String?,
    allSlots: List<String>,
    onSelectSlotAndReturn: (String) -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)

    val morningSlots = allSlots.filter { it.endsWith("AM") }
    val afternoonSlots = allSlots.filter { slot ->
        slot.endsWith("PM") && (
            slot.startsWith("12:") || slot.startsWith("01:") ||
                slot.startsWith("02:") || slot.startsWith("03:") || slot.startsWith("04:")
            )
    }
    val eveningSlots = allSlots.filter { slot ->
        slot.endsWith("PM") && (
            slot.startsWith("05:") || slot.startsWith("06:") ||
                slot.startsWith("07:") || slot.startsWith("08:")
            )
    }

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
                            onClick = onClose,
                            modifier = Modifier.testTag("booking_all_slots_back")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to appointment form",
                                tint = PlatinumWhite
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "All time slots",
                                style = MaterialTheme.typography.titleLarge,
                                color = PlatinumWhite
                            )
                            Text(
                                text = "$selectedDateLabel • 30-min consultations",
                                style = MaterialTheme.typography.bodySmall,
                                color = IceBlueText
                            )
                        }
                    }
                    HorizontalDivider(
                        color = Color(0x26FFFFFF),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        TimeSlotGroupCard(
                            title = "Morning",
                            subtitle = "09:00 AM – 11:30 AM",
                            icon = Icons.Filled.WbSunny,
                            iconTint = IceBlueText,
                            slots = morningSlots,
                            selectedSlot = selectedTimeLabel,
                            onSelect = onSelectSlotAndReturn
                        )
                    }
                    item {
                        TimeSlotGroupCard(
                            title = "Afternoon",
                            subtitle = "12:00 PM – 04:30 PM",
                            icon = Icons.Filled.WbTwilight,
                            iconTint = SoftLavenderText,
                            slots = afternoonSlots,
                            selectedSlot = selectedTimeLabel,
                            onSelect = onSelectSlotAndReturn
                        )
                    }
                    item {
                        TimeSlotGroupCard(
                            title = "Evening",
                            subtitle = "05:00 PM – 08:00 PM",
                            icon = Icons.Filled.NightsStay,
                            iconTint = IceBlueText,
                            slots = eveningSlots,
                            selectedSlot = selectedTimeLabel,
                            onSelect = onSelectSlotAndReturn
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun TimeSlotGroupCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    slots: List<String>,
    selectedSlot: String?,
    onSelect: (String) -> Unit
) {
    VerityGlassCard(
        cornerRadius = 24.dp,
        strong = true,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0x24FFFFFF))
                        .border(1.dp, Color(0x3DFFFFFF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = PlatinumWhite
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = SilverText
                    )
                }
                Text(
                    text = "${slots.size} slots",
                    style = MaterialTheme.typography.labelMedium,
                    color = SoftLavenderText
                )
            }

            slots.chunked(3).forEach { rowSlots ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowSlots.forEach { slot ->
                        BookingTimeSlotPill(
                            slot = slot,
                            selected = slot == selectedSlot,
                            onClick = { onSelect(slot) },
                            testTag = "booking_all_slot_${slot.replace(" ", "_").replace(":", "")}",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(3 - rowSlots.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
internal fun GlassyBookingConfirmDialog(
    doctor: DoctorDirectoryEntry,
    dateLabel: String,
    timeLabel: String,
    consultationTypeLabel: String,
    reason: String,
    onDismiss: () -> Unit,
    onConfirmPay: () -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xF0323743), Color(0xF51F232C))
                ),
                shape
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderBottom)),
                shape = shape
            )
            .padding(22.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.AccessTime,
                    contentDescription = null,
                    tint = IceBlueText,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Review & Confirm",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = PlatinumWhite
                )
            }

            Column {
                Text(
                    text = doctor.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PlatinumWhite
                )
                Text(
                    text = "${doctor.department} • ${doctor.hospital}",
                    style = MaterialTheme.typography.bodySmall,
                    color = IceBlueText
                )
            }

            HorizontalDivider(color = Color(0x26FFFFFF))

            SummaryLine("When", "$dateLabel • $timeLabel")
            SummaryLine("Type", consultationTypeLabel)
            SummaryLine("Reason", reason)

            VerityGlassCard(
                cornerRadius = 16.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Consultation Fee",
                            style = MaterialTheme.typography.labelLarge,
                            color = PlatinumWhite
                        )
                        Text(
                            text = "Aster Health Platinum applied",
                            style = MaterialTheme.typography.bodySmall,
                            color = SoftLavenderText
                        )
                    }
                    Text(
                        text = "₹${doctor.feeRupees}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = IceBlueText
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            VerityGlassPillButton(
                text = "Pay ₹${doctor.feeRupees} & Book",
                onClick = onConfirmPay,
                modifier = Modifier.testTag("booking_confirm_pay_button")
            )
            VerityGlassSecondaryPill(
                text = "Edit details",
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("booking_edit_details_button")
            )
        }
    }
}

@Composable
internal fun SummaryLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = SilverText
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = PlatinumWhite,
            textAlign = TextAlign.End
        )
    }
}
