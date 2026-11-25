package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AssistSessionCategory
import com.example.model.DoctorNotification
import com.example.ui.theme.AmberBg
import com.example.ui.theme.ClinicalBorder
import com.example.ui.theme.ClinicalSurface
import com.example.ui.theme.ClinicalSurfaceSubtle
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MedicalTeal
import com.example.ui.theme.OceanMistGradientStops
import com.example.ui.theme.OceanTealDark
import com.example.ui.theme.OceanTealMid
import com.example.ui.theme.OceanTealMist
import com.example.ui.theme.OceanTealPrimary
import com.example.ui.theme.RoseBg
import com.example.ui.theme.RoseText
import com.example.viewmodel.VerityScribeUiState

private val AssistSlateDark = OceanTealPrimary
private val AssistHeroTop = Color(0xCC074A61)
private val AssistHeroBottom = Color(0x9E1A7895)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistScreen(
    uiState: VerityScribeUiState,
    onSelectCategory: (AssistSessionCategory) -> Unit,
    onOpenSession: (String) -> Unit,
    onStartNewSession: (appointmentId: String, initialPrompt: String?, attachmentTitle: String?) -> Unit,
    onMarkAllNotificationsRead: () -> Unit,
    onNotificationClick: (DoctorNotification) -> Unit,
    modifier: Modifier = Modifier
) {
    var inputQuery by remember { mutableStateOf("") }
    var showAllSessions by remember { mutableStateOf(false) }
    var showNewSessionSheet by remember { mutableStateOf(false) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showNotificationsSheet by remember { mutableStateOf(false) }

    val filteredSessions = remember(uiState.assistSessions, uiState.selectedAssistCategory) {
        val todaysSessions = uiState.assistSessions.filter { !it.isHistoryOnly }
        if (uiState.selectedAssistCategory == AssistSessionCategory.ALL) {
            todaysSessions
        } else {
            todaysSessions.filter { it.category == uiState.selectedAssistCategory }
        }
    }

    val visibleSessions = remember(filteredSessions, showAllSessions) {
        if (showAllSessions) filteredSessions else filteredSessions.take(3)
    }

    val activePatient = remember(uiState.appointments, uiState.assistPatientContextId) {
        uiState.appointments.find { it.id == uiState.assistPatientContextId }
            ?: uiState.appointments.firstOrNull()
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
        // Subtle luminous ambient glows behind frosted glass cards
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(OceanTealMist.copy(alpha = 0.34f), Color.Transparent),
                    center = Offset(size.width * 0.86f, size.height * 0.16f),
                    radius = size.width * 0.56f
                ),
                radius = size.width * 0.56f,
                center = Offset(size.width * 0.86f, size.height * 0.16f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(OceanTealMid.copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(size.width * 0.14f, size.height * 0.58f),
                    radius = size.width * 0.62f
                ),
                radius = size.width * 0.62f,
                center = Offset(size.width * 0.14f, size.height * 0.58f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(OceanTealMist.copy(alpha = 0.26f), Color.Transparent),
                    center = Offset(size.width * 0.84f, size.height * 0.80f),
                    radius = size.width * 0.50f
                ),
                radius = size.width * 0.50f,
                center = Offset(size.width * 0.84f, size.height * 0.80f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(top = 12.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Header on Ocean Teal Top Zone
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "Hi, ${uiState.doctorProfile.greetingName}",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp
                                ),
                                color = Color.White
                            )
                            Text(
                                text = "How can I assist with patient care today?",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.86f)
                            )
                        }

                        Box {
                            Surface(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .clickable { showNotificationsSheet = true }
                                    .testTag("assist_notifications_button"),
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.22f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.38f))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.Notifications,
                                        contentDescription = "Clinical notifications",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            if (uiState.unreadNotificationsCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = (-2).dp, y = 2.dp)
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(RoseText)
                                )
                            }
                        }
                    }
                }

                // 2. Frosted Glass Hero Card ("Clinical AI Intake" + 3D Metallic Ribbon)
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(AssistHeroTop, AssistHeroBottom)
                                )
                            )
                            .border(
                                width = 1.2.dp,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.56f),
                                        Color.White.copy(alpha = 0.18f)
                                    )
                                ),
                                shape = RoundedCornerShape(24.dp)
                            )
                    ) {
                        // Overflowing 3D chrome ribbon on right
                        Image(
                            painter = painterResource(id = R.drawable.img_ai_chrome_ribbon),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(176.dp)
                                .align(Alignment.CenterEnd)
                                .offset(x = 26.dp, y = 4.dp)
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth(0.68f)
                                .padding(horizontal = 20.dp, vertical = 22.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Clinical AI Intake",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 21.sp
                                ),
                                color = Color.White
                            )
                            Text(
                                text = "Review structured drafts, patient records, and symptom summaries.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                ),
                                color = Color.White.copy(alpha = 0.88f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.96f),
                                                Color.White.copy(alpha = 0.84f)
                                            )
                                        )
                                    )
                                    .border(1.dp, Color.White, RoundedCornerShape(999.dp))
                                    .clickable { showNewSessionSheet = true }
                                    .padding(horizontal = 20.dp, vertical = 10.dp)
                                    .testTag("assist_new_session_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "New Session",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = OceanTealDark
                                )
                            }
                        }
                    }
                }

                // 3. Recent Sessions Header + Glassy Filter Pills
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recent Sessions",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                ),
                                color = InkPrimary
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.60f),
                                                Color.White.copy(alpha = 0.36f)
                                            )
                                        )
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = Color.White.copy(alpha = 0.85f),
                                        shape = RoundedCornerShape(999.dp)
                                    )
                                    .clickable { showAllSessions = !showAllSessions }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .testTag("assist_view_all_button")
                            ) {
                                Text(
                                    text = if (showAllSessions) "Show less" else "View all",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = OceanTealDark
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AssistSessionCategory.entries.forEach { category ->
                                val selected = uiState.selectedAssistCategory == category
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
                                                        Color.White.copy(alpha = 0.44f),
                                                        Color.White.copy(alpha = 0.24f)
                                                    )
                                                )
                                            },
                                            shape = chipShape
                                        )
                                        .border(
                                            width = 1.1.dp,
                                            color = if (selected) Color.White else Color.White.copy(alpha = 0.72f),
                                            shape = chipShape
                                        )
                                        .clickable { onSelectCategory(category) }
                                        .padding(horizontal = 15.dp, vertical = 9.dp)
                                        .testTag("assist_category_${category.name.lowercase()}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = category.label,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                                        ),
                                        color = if (selected) OceanTealDark else InkPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Glassy Frosted Recent Session Cards
                items(visibleSessions, key = { it.id }) { session ->
                    val cardShape = RoundedCornerShape(20.dp)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(cardShape)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.64f),
                                        Color.White.copy(alpha = 0.42f)
                                    )
                                ),
                                shape = cardShape
                            )
                            .border(
                                width = 1.2.dp,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.94f),
                                        Color.White.copy(alpha = 0.44f)
                                    )
                                ),
                                shape = cardShape
                            )
                            .clickable { onOpenSession(session.id) }
                            .testTag("assist_session_row_${session.id}"),
                        shape = cardShape,
                        color = Color.Transparent,
                        shadowElevation = 0.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = session.title,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    ),
                                    color = InkPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Color.White.copy(alpha = 0.56f))
                                        .border(1.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(999.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = session.category.label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = OceanTealDark
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.48f))
                                    .border(1.dp, Color.White.copy(alpha = 0.72f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 11.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = session.subtitle,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                    color = InkSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = session.updatedAt,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 10.5.sp
                                    ),
                                    color = OceanTealPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.72f))
                                        .border(1.dp, Color.White, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = "Open AI session",
                                        tint = OceanTealPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Floating Glassy Translucent Pill Composer sitting cleanly above Floating Glass Navbar
            val composerShape = RoundedCornerShape(999.dp)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 88.dp)
                    .height(54.dp)
                    .clip(composerShape)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.76f),
                                Color.White.copy(alpha = 0.54f)
                            )
                        ),
                        shape = composerShape
                    )
                    .border(
                        width = 1.2.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.96f),
                                Color.White.copy(alpha = 0.52f)
                            )
                        ),
                        shape = composerShape
                    ),
                shape = composerShape,
                color = Color.Transparent,
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 8.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showAttachmentSheet = true },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("assist_hub_attach_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AttachFile,
                            contentDescription = "Attach report",
                            tint = OceanTealDark,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        if (inputQuery.isEmpty()) {
                            Text(
                                text = "Ask Clinical AI or start a consultation...",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                                color = InkSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = inputQuery,
                            onValueChange = { inputQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = MaterialTheme.typography.bodyMedium.fontFamily,
                                fontSize = 14.sp,
                                color = InkPrimary
                            ),
                            cursorBrush = SolidColor(MedicalTeal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("assist_query_input")
                        )
                    }

                    IconButton(
                        onClick = {
                            onStartNewSession(
                                activePatient?.id.orEmpty(),
                                "Start a clinical assistant session for the selected appointment",
                                null
                            )
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("assist_hub_mic_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Mic,
                            contentDescription = "Start voice session",
                            tint = OceanTealDark,
                            modifier = Modifier.size(21.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Surface(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable {
                                val prompt = inputQuery.trim()
                                inputQuery = ""
                                onStartNewSession(
                                    activePatient?.id.orEmpty(),
                                    prompt.ifEmpty { "Summarise the selected patient's documented intake and alerts" },
                                    null
                                )
                            }
                            .testTag("assist_send_button"),
                        shape = CircleShape,
                        color = AssistSlateDark
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowUpward,
                                contentDescription = "Start Clinical AI session",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // New Session Patient Picker Sheet
    if (showNewSessionSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showNewSessionSheet = false },
            sheetState = sheetState,
            containerColor = ClinicalSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Start New Clinical AI Session",
                    style = MaterialTheme.typography.headlineSmall,
                    color = InkPrimary
                )
                Text(
                    text = "Select a patient chart to launch an interactive AI consultation & draft session:",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted
                )
                uiState.appointments.forEach { apt ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                showNewSessionSheet = false
                                onStartNewSession(apt.id, null, null)
                            },
                        shape = RoundedCornerShape(14.dp),
                        color = ClinicalSurfaceSubtle,
                        border = BorderStroke(1.dp, ClinicalBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = MedicalTeal
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${apt.patientName} • ${apt.ageLabel}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = InkPrimary
                                )
                                Text(
                                    text = "${apt.mrn} • ${apt.chiefComplaint}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Attach Report Sheet
    if (showAttachmentSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAttachmentSheet = false },
            sheetState = sheetState,
            containerColor = ClinicalSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Attach Synchronized Report",
                    style = MaterialTheme.typography.headlineSmall,
                    color = InkPrimary
                )
                Text(
                    text = "Analyze an uploaded lab or imaging report in a new AI session:",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted
                )
                activePatient?.reports.orEmpty().forEach { report ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showAttachmentSheet = false
                                onStartNewSession(activePatient?.id.orEmpty(), "Analyze ${report.title}", report.title)
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = ClinicalSurfaceSubtle,
                        border = BorderStroke(1.dp, ClinicalBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Description,
                                contentDescription = null,
                                tint = MedicalTeal
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(report.title, style = MaterialTheme.typography.titleSmall, color = InkPrimary)
                                Text("${report.category} • ${report.date}", style = MaterialTheme.typography.bodySmall, color = InkMuted)
                            }
                        }
                    }
                }
            }
        }
    }

    // Notifications Sheet
    if (showNotificationsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showNotificationsSheet = false },
            sheetState = sheetState,
            containerColor = ClinicalSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Clinical Notifications",
                        style = MaterialTheme.typography.headlineSmall,
                        color = InkPrimary
                    )
                    TextButton(onClick = onMarkAllNotificationsRead) {
                        Icon(
                            imageVector = Icons.Rounded.DoneAll,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MedicalTeal
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Mark all read", color = MedicalTeal)
                    }
                }

                uiState.notifications.forEach { notif ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                onNotificationClick(notif)
                                showNotificationsSheet = false
                            },
                        shape = RoundedCornerShape(14.dp),
                        color = if (!notif.isRead) {
                            if (notif.isUrgent) RoseBg.copy(alpha = 0.5f) else AmberBg.copy(alpha = 0.35f)
                        } else {
                            ClinicalSurfaceSubtle
                        },
                        border = BorderStroke(1.dp, ClinicalBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = notif.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (notif.isUrgent && !notif.isRead) RoseText else InkPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = notif.timeAgo,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                    color = InkMuted
                                )
                            }
                            Text(
                                text = notif.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
