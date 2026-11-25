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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AppointmentStatus
import com.example.model.ClinicalAppointment
import com.example.model.HomeFilterTab
import com.example.ui.components.AppointmentQueueCard
import com.example.ui.components.ClinicalSearchField
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.OceanMistGradientStops
import com.example.ui.theme.OceanTealDark
import com.example.ui.theme.OceanTealMid
import com.example.ui.theme.OceanTealMist
import com.example.viewmodel.VerityScribeUiState
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientsDirectoryScreen(
    uiState: VerityScribeUiState,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit,
    onSelectFilter: (HomeFilterTab) -> Unit,
    onOpenAppointment: (ClinicalAppointment) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now().toString()
    val filteredPatients = remember(
        uiState.appointments,
        uiState.directorySearchQuery,
        uiState.directoryFilter,
        today
    ) {
        val todaysAppointments = uiState.appointments.filter {
            val appointmentDate = runCatching {
                it.appointmentDate?.take(10)?.let(LocalDate::parse)
            }.getOrNull()
            it.status != AppointmentStatus.REJECTED &&
                appointmentDate != null && !appointmentDate.isBefore(LocalDate.parse(today))
        }
        val statusFiltered = when (uiState.directoryFilter) {
            HomeFilterTab.ALL -> todaysAppointments
            HomeFilterTab.AWAITING_REVIEW -> todaysAppointments.filter {
                it.status == AppointmentStatus.AWAITING_REVIEW || it.status == AppointmentStatus.FAILED_SUMMARY
            }
            HomeFilterTab.UPCOMING -> todaysAppointments.filter {
                it.status == AppointmentStatus.SCHEDULED ||
                    it.status == AppointmentStatus.WAITING ||
                    it.status == AppointmentStatus.APPROVED ||
                    it.status == AppointmentStatus.IN_PROGRESS
            }
            HomeFilterTab.COMPLETED -> todaysAppointments.filter {
                it.status == AppointmentStatus.COMPLETED
            }
        }
        val q = uiState.directorySearchQuery.trim().lowercase()
        if (q.isEmpty()) statusFiltered
        else statusFiltered.filter { apt ->
            apt.patientName.lowercase().contains(q) ||
                apt.mrn.lowercase().contains(q) ||
                apt.chiefComplaint.lowercase().contains(q) ||
                apt.diagnoses.any { it.lowercase().contains(q) } ||
                apt.aiSummary.currentMedications.any { it.lowercase().contains(q) } ||
                apt.prescriptions.any { it.medicationName.lowercase().contains(q) }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = OceanMistGradientStops
                )
            )
    ) {
        // Subtle luminous ambient glows behind frosted glass patient cards
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        OceanTealMist.copy(alpha = 0.34f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.88f, size.height * 0.14f),
                    radius = size.width * 0.58f
                ),
                radius = size.width * 0.58f,
                center = Offset(size.width * 0.88f, size.height * 0.14f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        OceanTealMid.copy(alpha = 0.18f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.14f, size.height * 0.52f),
                    radius = size.width * 0.62f
                ),
                radius = size.width * 0.62f,
                center = Offset(size.width * 0.14f, size.height * 0.52f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        OceanTealMist.copy(alpha = 0.24f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.82f, size.height * 0.78f),
                    radius = size.width * 0.50f
                ),
                radius = size.width * 0.50f,
                center = Offset(size.width * 0.82f, size.height * 0.78f)
            )
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.directory_title),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = "${filteredPatients.size} patients scheduled today",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.86f)
                        )
                    }

                    // Frosted translucent glass badge in header
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.24f),
                                        Color.White.copy(alpha = 0.12f)
                                    )
                                )
                            )
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(999.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF34D399))
                            )
                            Text(
                                text = "${filteredPatients.size} Today",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color.White
                            )
                        }
                    }
                }
            }

            item {
                ClinicalSearchField(
                    query = uiState.directorySearchQuery,
                    onQueryChange = onSearchQueryChange,
                    placeholder = stringResource(R.string.directory_search_hint),
                    testTag = "directory_search_input",
                    glassy = true
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HomeFilterTab.entries.forEach { tab ->
                        val selected = uiState.directoryFilter == tab
                        val chipShape = RoundedCornerShape(999.dp)
                        Box(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(chipShape)
                                .background(
                                    brush = if (selected) {
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.96f),
                                                Color.White.copy(alpha = 0.84f)
                                            )
                                        )
                                    } else {
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.26f),
                                                Color.White.copy(alpha = 0.13f)
                                            )
                                        )
                                    },
                                    shape = chipShape
                                )
                                .border(
                                    width = 1.1.dp,
                                    color = if (selected) Color.White else Color.White.copy(alpha = 0.48f),
                                    shape = chipShape
                                )
                                .clickable { onSelectFilter(tab) }
                                .padding(horizontal = 16.dp, vertical = 9.dp)
                                .testTag("directory_filter_${tab.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                                ),
                                color = if (selected) OceanTealDark else Color.White
                            )
                        }
                    }
                }
            }

            if (filteredPatients.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.62f),
                                        Color.White.copy(alpha = 0.42f)
                                    )
                                )
                            ),
                        shape = RoundedCornerShape(22.dp),
                        color = Color.Transparent,
                        border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.82f))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (uiState.directorySearchQuery.isBlank()) {
                                    "No patients scheduled today"
                                } else {
                                    "No matching patients found"
                                },
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = InkPrimary
                            )
                            Text(
                                text = if (uiState.directorySearchQuery.isBlank()) {
                                    "Appointments scheduled today or in the coming days will appear here."
                                } else {
                                    "Try searching by MRN, diagnosis, or medication."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = InkMuted
                            )
                        }
                    }
                }
            } else {
                items(filteredPatients, key = { it.id }) { appointment ->
                    AppointmentQueueCard(
                        appointment = appointment,
                        onClick = { onOpenAppointment(appointment) },
                        glassy = true
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp).navigationBarsPadding()) }
            }
        }
    }
}
