package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderShared
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ClinicalAppointment
import com.example.model.PrescriptionItem
import com.example.ui.components.StatusBadge
import com.example.ui.theme.ClinicalBlue
import com.example.ui.theme.ClinicalBlueContainer
import com.example.ui.theme.ClinicalBlueDark
import com.example.ui.theme.ClinicalBorder
import com.example.ui.theme.ClinicalSurface
import com.example.ui.theme.ClinicalSurfaceSubtle
import com.example.ui.theme.EmeraldBg
import com.example.ui.theme.EmeraldBorder
import com.example.ui.theme.EmeraldText
import com.example.ui.theme.GlacialMistBottom
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MedicalTeal
import com.example.ui.theme.MedicalTealDark
import com.example.ui.theme.MedicalTealSoft
import com.example.ui.theme.OceanTealMid
import com.example.ui.theme.OceanTealTop
import com.example.ui.theme.RoseBg
import com.example.ui.theme.RoseBorder
import com.example.ui.theme.RoseText
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ActiveConsultationScreen(
    appointment: ClinicalAppointment,
    requireConsentForSimilarCases: Boolean,
    onBack: () -> Unit,
    onSaveConsultation: (
        clinicalNote: String,
        diagnoses: List<String>,
        prescriptions: List<PrescriptionItem>,
        markCompleted: Boolean
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var clinicalNote by remember(appointment.id) { mutableStateOf(appointment.clinicalNote) }
    val diagnoses = remember(appointment.id) {
        mutableStateListOf<String>().apply { addAll(appointment.diagnoses) }
    }
    var newDiagnosisInput by remember { mutableStateOf("") }

    val prescriptions = remember(appointment.id) {
        mutableStateListOf<PrescriptionItem>().apply { addAll(appointment.prescriptions) }
    }

    var showReportsSheet by remember { mutableStateOf(false) }
    var showSimilarCasesSheet by remember { mutableStateOf(false) }
    var showAddRxSheet by remember { mutableStateOf(false) }
    var similarCasesConsentGranted by remember { mutableStateOf(!requireConsentForSimilarCases) }

    val quickPhrases = remember {
        listOf(
            "No acute cardiopulmonary distress.",
            "Lungs clear bilaterally; S1/S2 normal.",
            "Reinforced DASH sodium restriction.",
            "Follow-up in 2 weeks with repeat panel."
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = GlacialMistBottom,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(OceanTealTop, OceanTealMid)
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("consult_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = appointment.patientName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${appointment.ageLabel} • ${appointment.gender} • ${appointment.mrn} • Blood ${appointment.bloodGroup}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily, fontSize = 11.sp),
                                color = Color.White.copy(alpha = 0.88f)
                            )
                        }
                        StatusBadge(status = appointment.status)
                    }

                    // Vitals Strip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VitalPill("BP", appointment.vitals.bp)
                        VitalPill("HR", appointment.vitals.hr)
                        VitalPill("SpO2", appointment.vitals.spo2)
                        VitalPill("Temp", appointment.vitals.temp)
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = ClinicalSurface,
                border = BorderStroke(1.dp, ClinicalBorder),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onSaveConsultation(
                                clinicalNote,
                                diagnoses.toList(),
                                prescriptions.toList(),
                                false
                            )
                        },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("consult_save_draft_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ClinicalBorder)
                    ) {
                        Text("Save Note", color = InkPrimary)
                    }
                    Button(
                        onClick = {
                            onSaveConsultation(
                                clinicalNote,
                                diagnoses.toList(),
                                prescriptions.toList(),
                                true
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("consult_complete_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MedicalTeal)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Complete Consultation", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Allergy Alert Strip
            item {
                val hasSevereAllergy = appointment.allergies.any { !it.equals("NKDA", ignoreCase = true) }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = if (hasSevereAllergy) RoseBg else EmeraldBg.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, if (hasSevereAllergy) RoseBorder else EmeraldBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (hasSevereAllergy) Icons.Rounded.WarningAmber else Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = if (hasSevereAllergy) RoseText else EmeraldText,
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            text = "Allergies: ${appointment.allergies.joinToString(", ")}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (hasSevereAllergy) RoseText else EmeraldText
                        )
                    }
                }
            }

            // Quick Access: Medical Records (OCR) & Consent-Gated Similar Cases
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { showReportsSheet = true }
                            .testTag("consult_reports_button"),
                        shape = RoundedCornerShape(14.dp),
                        color = ClinicalSurface,
                        border = BorderStroke(1.dp, ClinicalBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Science,
                                contentDescription = null,
                                tint = ClinicalBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Records & OCR",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = InkPrimary
                                )
                                Text(
                                    text = "${appointment.reports.size} uploaded",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = InkMuted
                                )
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { showSimilarCasesSheet = true }
                            .testTag("consult_similar_cases_button"),
                        shape = RoundedCornerShape(14.dp),
                        color = ClinicalSurface,
                        border = BorderStroke(1.dp, ClinicalBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FolderShared,
                                contentDescription = null,
                                tint = MedicalTeal,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Similar Cases",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = InkPrimary
                                )
                                Text(
                                    text = if (similarCasesConsentGranted) "Consent active" else "Consent-gated",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = InkMuted
                                )
                            }
                        }
                    }
                }
            }

            // Clinical Note Editor
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = ClinicalSurface,
                    border = BorderStroke(1.dp, ClinicalBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Clinical Note & Assessment",
                            style = MaterialTheme.typography.titleSmall,
                            color = InkPrimary
                        )
                        OutlinedTextField(
                            value = clinicalNote,
                            onValueChange = { clinicalNote = it },
                            placeholder = { Text("Document physical exam, clinical reasoning, and follow-up plan...") },
                            minLines = 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("consult_note_input"),
                            shape = RoundedCornerShape(12.dp)
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            quickPhrases.forEach { phrase ->
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            clinicalNote = if (clinicalNote.isBlank()) phrase else "$clinicalNote $phrase"
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    color = ClinicalSurfaceSubtle,
                                    border = BorderStroke(1.dp, ClinicalBorder)
                                ) {
                                    Text(
                                        text = "+ $phrase",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = InkSecondary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Diagnosis Entry
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = ClinicalSurface,
                    border = BorderStroke(1.dp, ClinicalBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Diagnoses (ICD-10)",
                            style = MaterialTheme.typography.titleSmall,
                            color = InkPrimary
                        )
                        if (diagnoses.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                diagnoses.forEach { dx ->
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = MedicalTealSoft,
                                        border = BorderStroke(1.dp, MedicalTeal.copy(alpha = 0.35f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = dx,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MedicalTealDark
                                            )
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "Remove diagnosis",
                                                tint = MedicalTealDark,
                                                modifier = Modifier
                                                    .size(15.dp)
                                                    .clickable { diagnoses.remove(dx) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newDiagnosisInput,
                                onValueChange = { newDiagnosisInput = it },
                                placeholder = { Text("Add diagnosis (e.g. Hyperlipidemia E78.5)") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("consult_diagnosis_input"),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Button(
                                onClick = {
                                    val clean = newDiagnosisInput.trim()
                                    if (clean.isNotEmpty() && !diagnoses.contains(clean)) {
                                        diagnoses.add(clean)
                                        newDiagnosisInput = ""
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = InkPrimary),
                                modifier = Modifier
                                    .height(52.dp)
                                    .testTag("consult_add_diagnosis_button")
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = "Add diagnosis")
                            }
                        }
                    }
                }
            }

            // Prescription Builder
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = ClinicalSurface,
                    border = BorderStroke(1.dp, ClinicalBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Medication,
                                    contentDescription = null,
                                    tint = MedicalTeal,
                                    modifier = Modifier.size(19.dp)
                                )
                                Text(
                                    text = "Prescription Builder (${prescriptions.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = InkPrimary
                                )
                            }
                            OutlinedButton(
                                onClick = { showAddRxSheet = true },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MedicalTeal),
                                modifier = Modifier.testTag("consult_add_rx_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    tint = MedicalTeal,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Rx", color = MedicalTeal)
                            }
                        }

                        if (prescriptions.isEmpty()) {
                            Text(
                                text = "No medications added yet. Tap '+ Add Rx' to build e-prescription.",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkMuted
                            )
                        } else {
                            prescriptions.forEach { rx ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = ClinicalSurfaceSubtle,
                                    border = BorderStroke(1.dp, ClinicalBorder)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${rx.medicationName} • ${rx.dosage}",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = InkPrimary
                                            )
                                            Text(
                                                text = "${rx.frequency} • ${rx.duration}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MedicalTealDark
                                            )
                                            if (rx.instructions.isNotBlank()) {
                                                Text(
                                                    text = rx.instructions,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = InkMuted
                                                )
                                            }
                                        }
                                        IconButton(onClick = { prescriptions.remove(rx) }) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = "Remove medication",
                                                tint = RoseText
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }

    // Medical Records & OCR Sheet
    if (showReportsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showReportsSheet = false },
            sheetState = sheetState,
            containerColor = ClinicalSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Uploaded Medical Records & OCR",
                    style = MaterialTheme.typography.headlineSmall,
                    color = InkPrimary
                )
                if (appointment.reports.isEmpty()) {
                    Text(
                        text = "No uploaded laboratory or imaging reports for ${appointment.patientName}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkMuted
                    )
                } else {
                    appointment.reports.forEach { report ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = ClinicalSurfaceSubtle,
                            border = BorderStroke(1.dp, ClinicalBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Description,
                                        contentDescription = null,
                                        tint = ClinicalBlue
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = report.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = InkPrimary
                                        )
                                        Text(
                                            text = "${report.category} • ${report.facility} • ${report.date}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = InkMuted
                                        )
                                    }
                                }
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    report.flaggedFindings.forEach { finding ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = ClinicalBlueContainer
                                        ) {
                                            Text(
                                                text = finding,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ClinicalBlueDark,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(color = ClinicalBorder)
                                Text(
                                    text = "OCR EXTRACTED TEXT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = InkMuted
                                )
                                Text(
                                    text = report.ocrExtractedText,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp
                                    ),
                                    color = InkPrimary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(ClinicalSurface, RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Consent-Gated Similar Hospital Cases Drawer/Sheet
    if (showSimilarCasesSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSimilarCasesSheet = false },
            sheetState = sheetState,
            containerColor = ClinicalSurface
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MedicalTeal
                    )
                    Text(
                        text = "Similar Hospital Cases (Consent-Gated)",
                        style = MaterialTheme.typography.headlineSmall,
                        color = InkPrimary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MedicalTealSoft,
                    border = BorderStroke(1.dp, MedicalTeal.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { similarCasesConsentGranted = !similarCasesConsentGranted }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = similarCasesConsentGranted,
                            onCheckedChange = { similarCasesConsentGranted = it },
                            colors = CheckboxDefaults.colors(checkedColor = MedicalTeal),
                            modifier = Modifier.testTag("similar_cases_consent_checkbox")
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Confirm anonymized cohort comparison consent",
                                style = MaterialTheme.typography.labelLarge,
                                color = InkPrimary
                            )
                            Text(
                                text = "Patient consent verified (${appointment.consentVerifiedAt}) for de-identified clinical decision support.",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSecondary
                            )
                        }
                    }
                }

                if (!similarCasesConsentGranted) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = ClinicalSurfaceSubtle,
                        border = BorderStroke(1.dp, ClinicalBorder)
                    ) {
                        Text(
                            text = "Cohort case comparison is locked until consent confirmation above is checked.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkMuted,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else if (appointment.similarCases.isEmpty()) {
                    Text(
                        text = "No high-similarity anonymized hospital cases matched for this specific visit profile.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkMuted
                    )
                } else {
                    appointment.similarCases.forEach { sim ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = ClinicalSurfaceSubtle,
                            border = BorderStroke(1.dp, ClinicalBorder)
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
                                    Text(
                                        text = sim.caseCode,
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                                        color = MedicalTealDark
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = EmeraldBg
                                    ) {
                                        Text(
                                            text = "${sim.matchPercent}% match",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EmeraldText,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = sim.demographics,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = InkPrimary
                                )
                                Text(
                                    text = "Presentation: ${sim.presentation}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkSecondary
                                )
                                Text(
                                    text = "Action: ${sim.intervention}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkPrimary
                                )
                                Text(
                                    text = "Outcome: ${sim.outcome}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = EmeraldText
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Prescription Modal Sheet
    if (showAddRxSheet) {
        var medName by remember { mutableStateOf("") }
        var dosage by remember { mutableStateOf("") }
        var frequency by remember { mutableStateOf("Once daily") }
        var duration by remember { mutableStateOf("30 days") }
        var instructions by remember { mutableStateOf("") }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { showAddRxSheet = false },
            sheetState = sheetState,
            containerColor = ClinicalSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Add Prescription Item",
                    style = MaterialTheme.typography.headlineSmall,
                    color = InkPrimary
                )
                OutlinedTextField(
                    value = medName,
                    onValueChange = { medName = it },
                    label = { Text("Medication Name") },
                    placeholder = { Text("e.g. Amlodipine") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rx_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = dosage,
                        onValueChange = { dosage = it },
                        label = { Text("Dosage") },
                        placeholder = { Text("e.g. 5 mg") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("rx_dosage_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = duration,
                        onValueChange = { duration = it },
                        label = { Text("Duration") },
                        placeholder = { Text("e.g. 30 days") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("rx_duration_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                OutlinedTextField(
                    value = frequency,
                    onValueChange = { frequency = it },
                    label = { Text("Frequency") },
                    placeholder = { Text("e.g. Once daily / Twice daily") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rx_frequency_input"),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text("Patient Instructions") },
                    placeholder = { Text("e.g. Take after morning meal") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rx_instructions_input"),
                    shape = RoundedCornerShape(12.dp)
                )
                Button(
                    onClick = {
                        if (medName.isNotBlank() && dosage.isNotBlank()) {
                            prescriptions.add(
                                PrescriptionItem(
                                    id = "rx-${UUID.randomUUID()}",
                                    medicationName = medName.trim(),
                                    dosage = dosage.trim(),
                                    frequency = frequency.trim().ifEmpty { "Once daily" },
                                    duration = duration.trim().ifEmpty { "30 days" },
                                    instructions = instructions.trim()
                                )
                            )
                            showAddRxSheet = false
                        }
                    },
                    enabled = medName.isNotBlank() && dosage.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("rx_save_item_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MedicalTeal)
                ) {
                    Text("Add to Prescription")
                }
            }
        }
    }
}

@Composable
private fun VitalPill(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.20f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.34f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.SemiBold
                ),
                color = Color.White
            )
        }
    }
}
