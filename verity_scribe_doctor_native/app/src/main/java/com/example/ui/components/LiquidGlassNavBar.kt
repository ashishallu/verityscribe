package com.example.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppointmentStatus
import com.example.model.ClinicalAppointment
import com.example.ui.theme.ClinicalBorder
import com.example.ui.theme.GlacialMistBottom
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.OceanTealDark
import com.example.ui.theme.OceanTealPrimary

data class LiquidGlassNavItem<T>(
    val value: T,
    val labelRes: Int,
    val icon: ImageVector,
    val testTag: String
)

private val GlassCapsuleGradient = listOf(
    Color(0xD9457587),
    Color(0xD423586C),
    Color(0xE3124052)
)

private val GlassBorderBrush = Brush.verticalGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.88f),
        Color.White.copy(alpha = 0.22f),
        Color.White.copy(alpha = 0.62f)
    )
)

private val ActivePillGlassBrush = Brush.verticalGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.36f),
        Color.White.copy(alpha = 0.14f),
        Color.White.copy(alpha = 0.25f)
    )
)

private val ActivePillBorderBrush = Brush.verticalGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.92f),
        Color.White.copy(alpha = 0.26f),
        Color.White.copy(alpha = 0.66f)
    )
)

@Composable
fun <T> LiquidGlassBottomDock(
    items: List<LiquidGlassNavItem<T>>,
    selectedItem: T,
    onSelectItem: (T) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedIndex = items.indexOfFirst { it.value == selectedItem }.coerceAtLeast(0)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Main Floating Liquid-Glass Navigation Capsule
        Box(
            modifier = Modifier
                .weight(1f)
                .height(66.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(999.dp),
                    ambientColor = OceanTealDark.copy(alpha = 0.42f),
                    spotColor = OceanTealDark.copy(alpha = 0.42f)
                )
                .clip(RoundedCornerShape(999.dp))
                .background(brush = Brush.verticalGradient(colors = GlassCapsuleGradient))
                .drawBehind {
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0.0f to Color.White.copy(alpha = 0.24f),
                            0.42f to Color.Transparent,
                            0.78f to Color.Transparent,
                            1.0f to Color.White.copy(alpha = 0.12f)
                        ),
                        cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
                    )
                }
                .border(
                    width = 1.25.dp,
                    brush = GlassBorderBrush,
                    shape = RoundedCornerShape(999.dp)
                )
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 5.dp, vertical = 5.dp)
            ) {
                val tabWidth = maxWidth / items.size.coerceAtLeast(1)
                val indicatorOffset by animateDpAsState(
                    targetValue = tabWidth * selectedIndex,
                    animationSpec = spring(
                        dampingRatio = 0.78f,
                        stiffness = 380f
                    ),
                    label = "glass_nav_indicator_offset"
                )

                // Gliding Frosted Glass Selection Pill
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(tabWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(999.dp))
                        .background(brush = ActivePillGlassBrush)
                        .border(
                            width = 1.1.dp,
                            brush = ActivePillBorderBrush,
                            shape = RoundedCornerShape(999.dp)
                        )
                )

                // Clean Icon-over-Label Navigation Items
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEach { item ->
                        val selected = item.value == selectedItem
                        val label = stringResource(item.labelRes)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .clickable { onSelectItem(item.value) }
                                .testTag(item.testTag),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = label,
                                    tint = if (selected) Color.White else Color.White.copy(alpha = 0.80f),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        letterSpacing = 0.1.sp
                                    ),
                                    color = if (selected) Color.White else Color.White.copy(alpha = 0.82f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Detached Circular Liquid-Glass Quick Search Orb
        Box(
            modifier = Modifier
                .size(62.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = CircleShape,
                    ambientColor = OceanTealDark.copy(alpha = 0.42f),
                    spotColor = OceanTealDark.copy(alpha = 0.42f)
                )
                .clip(CircleShape)
                .background(brush = Brush.verticalGradient(colors = GlassCapsuleGradient))
                .drawBehind {
                    drawCircle(
                        brush = Brush.verticalGradient(
                            0.0f to Color.White.copy(alpha = 0.26f),
                            0.45f to Color.Transparent,
                            0.80f to Color.Transparent,
                            1.0f to Color.White.copy(alpha = 0.14f)
                        )
                    )
                }
                .border(
                    width = 1.25.dp,
                    brush = GlassBorderBrush,
                    shape = CircleShape
                )
                .clickable(onClick = onSearchClick)
                .testTag("nav_search_orb"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = "Quick clinical search",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private enum class QuickSearchScope(val label: String) {
    ALL("All"),
    REVIEW("Awaiting Review"),
    URGENT("Live / Retry")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickClinicalSearchSheet(
    appointments: List<ClinicalAppointment>,
    onDismiss: () -> Unit,
    onSelectAppointment: (ClinicalAppointment) -> Unit,
    onOpenDirectory: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by rememberSaveable { mutableStateOf("") }
    var scope by rememberSaveable { mutableStateOf(QuickSearchScope.ALL) }

    val results = remember(appointments, query, scope) {
        appointments.filter { apt ->
            val scopeMatch = when (scope) {
                QuickSearchScope.ALL -> true
                QuickSearchScope.REVIEW -> apt.status == AppointmentStatus.AWAITING_REVIEW
                QuickSearchScope.URGENT -> {
                    apt.status == AppointmentStatus.IN_PROGRESS ||
                        apt.status == AppointmentStatus.FAILED_SUMMARY
                }
            }
            if (!scopeMatch) return@filter false
            val q = query.trim()
            if (q.isEmpty()) true
            else {
                apt.patientName.contains(q, ignoreCase = true) ||
                    apt.mrn.contains(q, ignoreCase = true) ||
                    apt.chiefComplaint.contains(q, ignoreCase = true) ||
                    apt.consultationType.label.contains(q, ignoreCase = true) ||
                    apt.aiSummary.currentMedications.any { it.contains(q, ignoreCase = true) }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GlacialMistBottom
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Quick Clinical Search",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = InkPrimary
                    )
                    Text(
                        text = "Jump directly to a patient intake or consultation",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("quick_search_close")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close quick search",
                        tint = InkMuted
                    )
                }
            }

            ClinicalSearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = "Search patient, MRN, symptom, or medication...",
                testTag = "quick_search_input"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickSearchScope.entries.forEach { item ->
                    val selected = scope == item
                    FilterChip(
                        selected = selected,
                        onClick = { scope = item },
                        label = {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                )
                            )
                        },
                        shape = RoundedCornerShape(999.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = OceanTealPrimary,
                            selectedLabelColor = Color.White,
                            containerColor = Color.White,
                            labelColor = InkPrimary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = ClinicalBorder,
                            selectedBorderColor = OceanTealPrimary
                        )
                    )
                }
            }

            if (results.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, ClinicalBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "No matching patients found",
                            style = MaterialTheme.typography.titleSmall,
                            color = InkPrimary
                        )
                        Text(
                            text = "Try searching by patient name, MRN, or chief complaint.",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(results, key = { it.id }) { apt ->
                        AppointmentQueueCard(
                            appointment = apt,
                            onClick = {
                                onDismiss()
                                onSelectAppointment(apt)
                            }
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        onDismiss()
                        onOpenDirectory()
                    }
                    .testTag("quick_search_open_directory"),
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, ClinicalBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Open full Patient Directory",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = OceanTealPrimary
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = OceanTealPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
