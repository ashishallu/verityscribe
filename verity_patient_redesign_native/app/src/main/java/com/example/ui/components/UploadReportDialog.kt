package com.example.ui.components

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.UploadFile
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.ui.theme.CharcoalBase
import com.example.ui.theme.IceBlueText
import com.example.ui.theme.PeriwinkleMist
import com.example.ui.theme.PlatinumWhite
import com.example.ui.theme.SilverText
import com.example.ui.theme.SoftLavenderText

private val reportUploadCategories = listOf(
    "Blood Work",
    "Metabolic",
    "Cardiology",
    "Imaging",
    "General"
)

@Composable
fun UploadReportDialog(
    onDismiss: () -> Unit,
    onConfirmUpload: (
        uri: Uri,
        title: String,
        labName: String,
        orderedBy: String,
        category: String,
        notes: String
    ) -> Unit
) {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var selectedMimeType by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var labName by remember { mutableStateOf("") }
    var orderedBy by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Blood Work") }
    var notes by remember { mutableStateOf("") }

    fun handlePickedUri(uri: Uri?) {
        if (uri == null) return
        selectedUri = uri
        val resolver = context.contentResolver
        val mime = resolver.getType(uri).orEmpty()
        selectedMimeType = mime
        val resolvedName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) c.getString(idx) else null
            } else {
                null
            }
        } ?: if (mime.startsWith("image/")) "Scanned Report.jpg" else "Lab Report.pdf"
        selectedFileName = resolvedName
        if (title.isBlank()) {
            title = resolvedName.substringBeforeLast('.').replace('_', ' ').replace('-', ' ').trim()
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        handlePickedUri(uri)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        handlePickedUri(uri)
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
                                imageVector = Icons.Filled.UploadFile,
                                contentDescription = null,
                                tint = IceBlueText,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Upload new report",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = PlatinumWhite
                            )
                            Text(
                                text = "Add PDF or report scan to medical history",
                                style = MaterialTheme.typography.labelSmall,
                                color = IceBlueText
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_upload_report_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = SilverText
                        )
                    }
                }

                // Zero-permission Pickers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    VerityGlassSecondaryPill(
                        text = "PDF / File",
                        leadingIcon = Icons.Filled.AttachFile,
                        onClick = {
                            documentPickerLauncher.launch(arrayOf("application/pdf", "image/*"))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_report_document_button")
                    )
                    VerityGlassSecondaryPill(
                        text = "Scan / Photo",
                        leadingIcon = Icons.Filled.Image,
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_report_photo_button")
                    )
                }

                // Selected Attachment Preview Pill
                if (selectedUri != null) {
                    val isImage = selectedMimeType.startsWith("image/", ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(IceBlueText.copy(alpha = 0.14f))
                            .border(1.dp, IceBlueText.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isImage) {
                            AsyncImage(
                                model = selectedUri,
                                contentDescription = "Selected report preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, Color(0x47FFFFFF), RoundedCornerShape(10.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0x26FFFFFF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Description,
                                    contentDescription = null,
                                    tint = IceBlueText,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedFileName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = PlatinumWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isImage) "Image scan ready" else "Document ready to upload",
                                style = MaterialTheme.typography.labelSmall,
                                color = IceBlueText
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "File attached",
                            tint = IceBlueText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x14FFFFFF))
                            .border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(16.dp))
                            .clickable {
                                documentPickerLauncher.launch(arrayOf("application/pdf", "image/*"))
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "Tap PDF / File or Scan / Photo above to select a lab report.",
                            style = MaterialTheme.typography.bodySmall,
                            color = PeriwinkleMist
                        )
                    }
                }

                VerityGlassInputField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = "Report title (e.g. Complete Blood Count)",
                    leadingIcon = Icons.Filled.Description,
                    testTag = "upload_report_title_input"
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Category",
                        style = MaterialTheme.typography.labelLarge,
                        color = SoftLavenderText
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(end = 4.dp)
                    ) {
                        items(reportUploadCategories) { item ->
                            VerityGlassChip(
                                text = item,
                                highlighted = category == item,
                                onClick = { category = item }
                            )
                        }
                    }
                }

                VerityGlassInputField(
                    value = labName,
                    onValueChange = { labName = it },
                    placeholder = "Lab or clinic name (optional)",
                    leadingIcon = Icons.Filled.LocalHospital,
                    testTag = "upload_report_lab_input"
                )

                VerityGlassInputField(
                    value = orderedBy,
                    onValueChange = { orderedBy = it },
                    placeholder = "Ordered by doctor (optional)",
                    leadingIcon = Icons.Filled.Person,
                    testTag = "upload_report_doctor_input"
                )

                VerityGlassInputField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = "Notes or key takeaway (optional)",
                    leadingIcon = Icons.Filled.Notes,
                    testTag = "upload_report_notes_input"
                )

                Spacer(modifier = Modifier.height(2.dp))

                VerityGlassPillButton(
                    text = "Save & Upload Report",
                    leadingIcon = Icons.Filled.UploadFile,
                    enabled = selectedUri != null && title.isNotBlank(),
                    onClick = {
                        val uri = selectedUri ?: return@VerityGlassPillButton
                        onConfirmUpload(uri, title, labName, orderedBy, category, notes)
                    },
                    modifier = Modifier.testTag("confirm_upload_report_button")
                )
            }
        }
    }
}
