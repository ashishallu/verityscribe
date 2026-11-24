package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.Medicine
import com.example.ui.components.VerityGlassBackdrop
import com.example.ui.components.VerityGlassCard
import com.example.ui.components.VerityGlassChip
import com.example.ui.components.VerityGlassInputField
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
fun MedicinesScreen(
    uiState: VerityUiState,
    onSearchQueryChange: (String) -> Unit,
    onOpenMedicineDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showScannerInfo by remember { mutableStateOf(false) }

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
                            text = "Medicines",
                            style = MaterialTheme.typography.headlineMedium,
                            color = PlatinumWhite
                        )
                        IconButton(
                            onClick = { showScannerInfo = true },
                            modifier = Modifier.testTag("scan_prescription_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DocumentScanner,
                                contentDescription = "Scan prescription",
                                tint = PlatinumWhite
                            )
                        }
                    }
                }

                // Glass search bar
                item {
                    VerityGlassInputField(
                        value = uiState.medicineSearchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = "Search medicines",
                        leadingIcon = Icons.Filled.Search,
                        trailingContent = if (uiState.medicineSearchQuery.isNotEmpty()) {
                            {
                                IconButton(
                                    onClick = { onSearchQueryChange("") },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Clear search",
                                        tint = SilverText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        } else {
                            null
                        },
                        testTag = "medicines_search_input"
                    )
                }

                // Section header
                item {
                    Text(
                        text = "Medication dashboard",
                        style = MaterialTheme.typography.titleLarge,
                        color = PlatinumWhite,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                val itemsList = uiState.filteredMedicines
                if (itemsList.isEmpty()) {
                    item {
                        VerityGlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (uiState.medicineSearchQuery.isBlank()) "No prescriptions are currently available" else "No medicines match \"${uiState.medicineSearchQuery}\"",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PlatinumWhite
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Try searching by medicine name, dosage, or condition.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SilverText
                                )
                            }
                        }
                    }
                } else {
                    items(itemsList, key = { it.id }) { medicine ->
                        MedicineGlassDashboardCard(
                            medicine = medicine,
                            onClick = { onOpenMedicineDetail(medicine.id) }
                        )
                    }
                }
            }
        }
    }

    if (showScannerInfo) {
        AlertDialog(
            onDismissRequest = { showScannerInfo = false },
            containerColor = CharcoalBase,
            title = { Text("Prescription Scanner", color = PlatinumWhite) },
            text = {
                Text(
                    "Prescription details are loaded from the patient record. Stock, pharmacy prices, and store availability are shown only when provided by a connected service.",
                    color = SilverText
                )
            },
            confirmButton = {
                TextButton(onClick = { showScannerInfo = false }) {
                    Text("Got it", color = PlatinumWhite)
                }
            }
        )
    }
}

@Composable
private fun MedicineGlassDashboardCard(
    medicine: Medicine,
    onClick: () -> Unit
) {
    val progress = if (medicine.inventoryTracked && medicine.initialQuantity > 0) {
        (medicine.remainingQuantity.toFloat() / medicine.initialQuantity.toFloat()).coerceIn(0f, 1f)
    } else 0f

    VerityGlassCard(
        cornerRadius = 22.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("medicine_card_${medicine.id}"),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                VerityPillIconBadge(size = 52.dp)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = medicine.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = PlatinumWhite
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${medicine.dosage} • ${medicine.category}",
                        style = MaterialTheme.typography.bodySmall,
                        color = SoftLavenderText
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = medicine.scheduleText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = IceBlueText
                    )
                }
                if (medicine.needsReorder) {
                    VerityGlassChip(
                        text = "REORDER",
                        highlighted = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (medicine.inventoryTracked) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (medicine.needsReorder) Color(0xFFD1D5DB) else GlassProgressActive,
                    trackColor = GlassProgressTrack,
                    strokeCap = StrokeCap.Round
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (medicine.inventoryTracked) "${medicine.remainingQuantity} of ${medicine.initialQuantity} tablets remaining" else "Inventory quantity not tracked",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = PlatinumWhite
                )
                Text(
                    text = medicine.prescribedBy,
                    style = MaterialTheme.typography.bodySmall,
                    color = PeriwinkleMist
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color(0x1FFFFFFF))
            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                FooterGlassMetric(
                    label = "Schedule",
                    value = medicine.shortSchedule,
                    modifier = Modifier.weight(1f)
                )
                FooterGlassMetric(
                    label = "Supply",
                    value = if (medicine.inventoryTracked) "${medicine.finishesInDays} days" else "Not tracked",
                    modifier = Modifier.weight(1f)
                )
                FooterGlassMetric(
                    label = "Inventory",
                    value = if (medicine.inventoryTracked) medicine.reorderLabel else "Unavailable",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun FooterGlassMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = PeriwinkleMist
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = PlatinumWhite
        )
    }
}
