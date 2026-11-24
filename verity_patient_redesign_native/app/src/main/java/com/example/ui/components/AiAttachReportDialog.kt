package com.example.ui.components

import android.net.Uri
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.MedicalReport
import com.example.ui.theme.CharcoalBase
import com.example.ui.theme.IceBlueText
import com.example.ui.theme.PeriwinkleMist
import com.example.ui.theme.PlatinumWhite
import com.example.ui.theme.SilverText
import com.example.ui.theme.SoftLavenderText

@Composable
fun AiAttachReportDialog(
    reports: List<MedicalReport>,
    onDismiss: () -> Unit,
    onSelectExistingReport: (MedicalReport) -> Unit,
    onUploadAndAttachReport: (
        uri: Uri,
        title: String,
        labName: String,
        orderedBy: String,
        category: String,
        notes: String
    ) -> Unit
) {
    var showNewUploadPicker by remember { mutableStateOf(false) }

    if (showNewUploadPicker) {
        UploadReportDialog(
            onDismiss = { showNewUploadPicker = false },
            onConfirmUpload = { uri, title, labName, orderedBy, category, notes ->
                showNewUploadPicker = false
                onUploadAndAttachReport(uri, title, labName, orderedBy, category, notes)
                onDismiss()
            }
        )
        return
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(CharcoalBase.copy(alpha = 0.98f), Color(0xFF171A21))
                        ),
                        RoundedCornerShape(28.dp)
                    )
                    .border(1.dp, Color(0x47FFFFFF), RoundedCornerShape(28.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
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
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(IceBlueText.copy(alpha = 0.16f))
                                .border(1.dp, IceBlueText.copy(alpha = 0.45f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AttachFile,
                                contentDescription = null,
                                tint = IceBlueText,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Upload or attach report",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = PlatinumWhite
                            )
                            Text(
                                text = "Ask Care AI to review your lab reports",
                                style = MaterialTheme.typography.labelSmall,
                                color = IceBlueText
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_ai_attach_report_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = SilverText
                        )
                    }
                }

                VerityGlassPillButton(
                    text = "Upload New Report (PDF / Scan)",
                    leadingIcon = Icons.Filled.UploadFile,
                    onClick = { showNewUploadPicker = true },
                    modifier = Modifier.testTag("ai_upload_new_report_button")
                )

                if (reports.isNotEmpty()) {
                    HorizontalDivider(color = Color(0x21FFFFFF))
                    Text(
                        text = "Or attach a saved report",
                        style = MaterialTheme.typography.labelLarge,
                        color = SoftLavenderText
                    )

                    reports.forEach { report ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0x1CFFFFFF))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                                .clickable {
                                    onSelectExistingReport(report)
                                    onDismiss()
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .testTag("ai_attach_existing_${report.id}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Biotech,
                                contentDescription = null,
                                tint = IceBlueText,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = report.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = PlatinumWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${report.labName} • ${report.dateLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PeriwinkleMist,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Attach",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = IceBlueText
                            )
                        }
                    }
                }
            }
        }
    }
}
