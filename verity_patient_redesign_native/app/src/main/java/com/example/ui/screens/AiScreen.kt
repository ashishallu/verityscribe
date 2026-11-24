package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.model.AiHistoryThread
import com.example.model.ChatMessage
import com.example.model.MedicalReport
import com.example.ui.components.AiAttachReportDialog
import com.example.ui.components.VerityGlassBackdrop
import com.example.ui.components.VerityGlassChip
import com.example.ui.theme.IceBlueText
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.PeriwinkleMist
import com.example.ui.theme.PlatinumWhite
import com.example.ui.theme.SilverText
import com.example.ui.theme.SoftLavenderText
import com.example.viewmodel.VerityUiState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

@Composable
fun AiScreen(
    uiState: VerityUiState,
    onSelectCategory: (String) -> Unit,
    onOpenThread: (AiHistoryThread) -> Unit,
    onStartNewThread: () -> Unit,
    onDeleteThread: (String) -> Unit,
    onUndoDeleteThread: (String) -> Unit,
    onCloseThread: () -> Unit,
    onToggleSavedThread: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSendPrompt: (String) -> Unit,
    onAttachExistingReport: (MedicalReport) -> Unit,
    onUploadAndAttachReport: (
        uri: Uri,
        title: String,
        labName: String,
        orderedBy: String,
        category: String,
        notes: String
    ) -> Unit,
    onClearChat: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onOpenVoice: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAttachReportDialog by remember { mutableStateOf(false) }

    if (uiState.showAiChatThread) {
        BackHandler { onCloseThread() }
    }

    VerityGlassBackdrop(
        isObsidianMode = uiState.darkMode,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            if (!uiState.showAiChatThread) {
                AiHubHistoryView(
                    uiState = uiState,
                    onSelectCategory = onSelectCategory,
                    onOpenThread = onOpenThread,
                    onStartNewThread = onStartNewThread,
                    onOpenAttachReport = { showAttachReportDialog = true },
                    onDeleteThread = onDeleteThread,
                    onUndoDeleteThread = onUndoDeleteThread
                )
            } else {
                AiActiveChatThreadView(
                    uiState = uiState,
                    onBack = onCloseThread,
                    onToggleSaved = onToggleSavedThread,
                    onDraftChange = onDraftChange,
                    onSendPrompt = onSendPrompt,
                    onOpenAttachReport = { showAttachReportDialog = true },
                    onClearChat = onClearChat,
                    onRemoveAttachment = onRemoveAttachment,
                    onOpenVoice = onOpenVoice
                )
            }
        }
    }

    if (showAttachReportDialog) {
        AiAttachReportDialog(
            reports = uiState.reports,
            onDismiss = { showAttachReportDialog = false },
            onSelectExistingReport = onAttachExistingReport,
            onUploadAndAttachReport = onUploadAndAttachReport
        )
    }
}

@Composable
private fun AiHubHistoryView(
    uiState: VerityUiState,
    onSelectCategory: (String) -> Unit,
    onOpenThread: (AiHistoryThread) -> Unit,
    onStartNewThread: () -> Unit,
    onOpenAttachReport: () -> Unit,
    onDeleteThread: (String) -> Unit,
    onUndoDeleteThread: (String) -> Unit
) {
    val categories = listOf("All", "Medications", "Symptoms", "Reports")
    var showAllHistory by remember { mutableStateOf(false) }

    val filteredThreads = remember(uiState.aiHistoryThreads, uiState.selectedAiCategory, showAllHistory) {
        val matching = if (uiState.selectedAiCategory == "All") {
            uiState.aiHistoryThreads
        } else {
            uiState.aiHistoryThreads.filter {
                it.category.equals(uiState.selectedAiCategory, ignoreCase = true)
            }
        }
        if (showAllHistory) matching else matching.take(4)
    }

    LazyColumn(
        modifier = Modifier
            .widthIn(max = 600.dp)
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 114.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. Greeting Header + Quick Report Upload
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hi, ${uiState.profile.firstName}",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 23.sp
                        ),
                        color = PlatinumWhite
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "How may I help you today?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PeriwinkleMist
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 42.dp)
                        .clip(CircleShape)
                        .background(Color(0x29FFFFFF))
                        .border(1.dp, IceBlueText.copy(alpha = 0.50f), CircleShape)
                        .clickable(onClick = onOpenAttachReport)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("ai_hub_upload_report_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.UploadFile,
                        contentDescription = "Upload report to AI",
                        tint = IceBlueText,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Upload Report",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = PlatinumWhite
                    )
                }
            }
        }

        // 2. Hero Frosted Slate Card with Overflowing Photorealistic 3D Chrome Ribbon
        item {
            val heroShape = RoundedCornerShape(24.dp)
            val chromeRibbonBitmap = rememberTransparentChromeBitmap(
                resId = R.drawable.img_ai_chrome_ribbon,
                targetSizePx = 440
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_hero_card")
            ) {
                // Card body offset downward by 12.dp so the 3D chrome knot breaks above the top edge
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .height(182.dp)
                        .shadow(
                            elevation = 14.dp,
                            shape = heroShape,
                            ambientColor = Color.Black.copy(alpha = 0.45f),
                            spotColor = Color.Black.copy(alpha = 0.55f)
                        )
                        .clip(heroShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF4F5563),
                                    Color(0xFF393E4A),
                                    Color(0xFF252932)
                                )
                            ),
                            heroShape
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                listOf(Color(0x5EFFFFFF), Color(0x21FFFFFF))
                            ),
                            shape = heroShape
                        )
                        .clickable(onClick = onStartNewThread)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.fillMaxWidth(0.58f)) {
                            Text(
                                text = "Verity Care AI",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 23.sp
                                ),
                                color = PlatinumWhite
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (uiState.isLiveAiEnabled) {
                                    "Clinical AI for prescriptions and health records."
                                } else {
                                    "AI service configuration is missing. Sign in after reconnecting the service."
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    lineHeight = 17.sp,
                                    fontSize = 12.5.sp
                                ),
                                color = SoftLavenderText.copy(alpha = 0.95f)
                            )
                        }

                        // Crisp white pill CTA matching "Get Pro" in reference
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minHeight = 42.dp)
                                .shadow(6.dp, CircleShape)
                                .clip(CircleShape)
                                .background(PlatinumWhite)
                                .clickable(onClick = onStartNewThread)
                                .padding(horizontal = 24.dp, vertical = 10.dp)
                                .testTag("ai_hero_cta_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Ask Verity",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = ObsidianBlack
                            )
                        }
                    }
                }

                // 3D Chrome Ribbon floating on the right side and breaking above the top border
                if (chromeRibbonBitmap != null) {
                    Image(
                        bitmap = chromeRibbonBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 8.dp, y = (-4).dp)
                            .size(width = 186.dp, height = 202.dp)
                    )
                }
            }
        }

        // 3. History Header + Filter Chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "History",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 21.sp
                        ),
                        color = PlatinumWhite
                    )
                    Text(
                        text = if (showAllHistory) "Show less" else "View all",
                        style = MaterialTheme.typography.bodyMedium,
                        color = IceBlueText,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showAllHistory = !showAllHistory
                                onSelectCategory("All")
                            }
                            .padding(horizontal = 8.dp, vertical = 12.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        val selected = uiState.selectedAiCategory == cat
                        val chipShape = RoundedCornerShape(12.dp)
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minHeight = 40.dp)
                                .clip(chipShape)
                                .background(
                                    if (selected) Color(0xFF1D2028) else Color(0x2BFFFFFF),
                                    chipShape
                                )
                                .border(
                                    width = 0.9.dp,
                                    color = if (selected) Color(0x3DFFFFFF) else Color(0x45FFFFFF),
                                    shape = chipShape
                                )
                                .clickable { onSelectCategory(cat) }
                                .padding(horizontal = 16.dp, vertical = 9.dp)
                                .testTag("ai_category_${cat.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = cat,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                ),
                                color = if (selected) PlatinumWhite else PlatinumWhite.copy(alpha = 0.92f)
                            )
                        }
                    }
                }
            }
        }

        // 4. Clean Divider-Separated History List (transparent/normal at rest, slide-to-delete + Undo)
        item {
            if (filteredThreads.isEmpty()) {
                val emptyShape = RoundedCornerShape(22.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(emptyShape)
                        .background(Color(0x1EFFFFFF), emptyShape)
                        .border(0.9.dp, Color(0x33FFFFFF), emptyShape)
                        .padding(horizontal = 20.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No conversations in this category",
                        style = MaterialTheme.typography.titleMedium,
                        color = PlatinumWhite
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Start a new chat with Verity Care AI above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PeriwinkleMist,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    filteredThreads.forEachIndexed { index, thread ->
                        val isPendingDelete = thread.id in uiState.pendingDeletedAiThreadIds
                        key(thread.id) {
                            SwipeToDeleteHistoryRow(
                                thread = thread,
                                isPendingDelete = isPendingDelete,
                                showDivider = index < filteredThreads.lastIndex && !isPendingDelete,
                                onOpen = { onOpenThread(thread) },
                                onDelete = { onDeleteThread(thread.id) },
                                onUndo = { onUndoDeleteThread(thread.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteHistoryRow(
    thread: AiHistoryThread,
    isPendingDelete: Boolean,
    showDivider: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(22.dp)

    AnimatedContent(
        targetState = isPendingDelete,
        transitionSpec = {
            fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(160))
        },
        label = "historyDeleteTransition",
        modifier = modifier.fillMaxWidth()
    ) { deleted ->
        if (deleted) {
            // Coral-red "Deleted" + "Undo" pill card when swiped
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .defaultMinSize(minHeight = 72.dp)
                    .shadow(
                        elevation = 6.dp,
                        shape = cardShape,
                        ambientColor = Color.Black.copy(alpha = 0.35f),
                        spotColor = Color.Black.copy(alpha = 0.4f)
                    )
                    .clip(cardShape)
                    .background(Color(0xFFDD4B3E), cardShape)
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .testTag("ai_history_deleted_banner_${thread.id}")
            ) {
                Text(
                    text = "Deleted",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    ),
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onUndo)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("ai_history_undo_${thread.id}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo delete ${thread.title}",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Undo",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.5.sp
                        ),
                        color = Color.White
                    )
                }
            }
        } else {
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value != SwipeToDismissBoxValue.Settled) {
                        onDelete()
                        true
                    } else {
                        false
                    }
                },
                positionalThreshold = { totalDistance -> totalDistance * 0.35f }
            )
            val isSliding = dismissState.dismissDirection != SwipeToDismissBoxValue.Settled

            Column(modifier = Modifier.fillMaxWidth()) {
                SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromStartToEnd = true,
                    enableDismissFromEndToStart = true,
                    backgroundContent = {
                        if (isSliding) {
                            val isSwipingRight = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
                            val armed = dismissState.targetValue != SwipeToDismissBoxValue.Settled
                            val bgColor by animateColorAsState(
                                targetValue = if (armed) Color(0xFFDD4B3E) else Color(0xFF9C3E36),
                                animationSpec = tween(150),
                                label = "swipeDeleteBg"
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(cardShape)
                                    .background(bgColor, cardShape)
                                    .padding(horizontal = 24.dp),
                                contentAlignment = if (isSwipingRight) Alignment.CenterStart else Alignment.CenterEnd
                            ) {
                                Text(
                                    text = "Deleted",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.5.sp
                                    ),
                                    color = Color.White.copy(alpha = if (armed) 1f else 0.88f)
                                )
                            }
                        }
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(if (isSliding) 22.dp else 12.dp))
                            .background(
                                if (isSliding) Color(0xF02B2F39) else Color.Transparent
                            )
                            .clickable(onClick = onOpen)
                            .padding(
                                vertical = 13.dp,
                                horizontal = if (isSliding) 12.dp else 2.dp
                            )
                            .testTag("ai_history_item_${thread.id}")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = thread.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 15.5.sp
                                ),
                                color = PlatinumWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color(0x1E93C5FD))
                                    .border(0.8.dp, IceBlueText.copy(alpha = 0.45f), CircleShape)
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = thread.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = IceBlueText
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = thread.preview,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                            color = SilverText.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (showDivider && !isSliding) {
                    HorizontalDivider(thickness = 0.8.dp, color = Color(0x21FFFFFF))
                }
            }
        }
    }
}

@Composable
private fun AiActiveChatThreadView(
    uiState: VerityUiState,
    onBack: () -> Unit,
    onToggleSaved: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSendPrompt: (String) -> Unit,
    onOpenAttachReport: () -> Unit,
    onClearChat: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onOpenVoice: () -> Unit
) {
    var showQuickPrompts by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val isSaved = (uiState.activeAiThreadId ?: "thread_new") in uiState.savedAiThreadIds

    LaunchedEffect(uiState.chatMessages.size, uiState.isAiGenerating) {
        if (uiState.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.chatMessages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .widthIn(max = 600.dp)
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // 1. Top Bar: Frosted Back Circle • Centered Thread Title • Frosted Heart Circle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0x2EFFFFFF))
                    .border(0.9.dp, Color(0x4DFFFFFF), CircleShape)
                    .clickable(onClick = onBack)
                    .testTag("ai_thread_back_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to AI history",
                    tint = PlatinumWhite,
                    modifier = Modifier.size(19.dp)
                )
            }

            Text(
                text = uiState.activeAiThreadTitle,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp
                ),
                color = PlatinumWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            )

            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0x2EFFFFFF))
                    .border(0.9.dp, Color(0x4DFFFFFF), CircleShape)
                    .clickable(onClick = onToggleSaved)
                    .testTag("ai_thread_favorite_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSaved) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isSaved) "Remove favorite" else "Save thread",
                    tint = PlatinumWhite,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        // 2. Chat Message Stream + Centered 3D Chrome Clover Emblem
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(uiState.chatMessages, key = { it.id }) { msg ->
                if (msg.isVoiceNote) {
                    VoiceNoteWaveformBubble(durationLabel = msg.voiceDurationLabel)
                } else {
                    ReferenceChatBubble(message = msg, onRemoveAttachment = onRemoveAttachment)
                }
            }

            item(key = "chrome_clover_presence") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ChromeCloverSculpture(
                        animate = uiState.isAiGenerating,
                        modifier = Modifier.size(86.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when {
                            uiState.isAiGenerating -> "Reviewing your care plan..."
                            uiState.isLiveAiEnabled -> "AI service configured • Ready for your question"
                            else -> "AI service unavailable"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                        color = SoftLavenderText
                    )
                }
            }
        }

        // 3. Expandable Quick Clinical Prompts Strip
        AnimatedVisibility(visible = showQuickPrompts) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val quickPrompts = buildList {
                    add("Upload report")
                    uiState.medicines.firstOrNull()?.let { add("Explain my ${it.name} schedule") }
                    add("Check my vitals")
                    uiState.appointments.firstOrNull()?.let { add("Summarize my upcoming appointment with ${it.doctorName}") }
                    add("Reset chat")
                }
                quickPrompts.forEach { prompt ->
                    VerityGlassChip(
                        text = prompt,
                        highlighted = true,
                        onClick = {
                            showQuickPrompts = false
                            when (prompt) {
                                "Upload report" -> onOpenAttachReport()
                                "Reset chat" -> onClearChat()
                                else -> onSendPrompt(prompt)
                            }
                        }
                    )
                }
            }
        }

        // 4. Frosted Pearl-Silver Composer Capsule matching the right reference screen
        val composerShape = RoundedCornerShape(32.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp)
                .defaultMinSize(minHeight = 58.dp)
                .shadow(
                    elevation = 14.dp,
                    shape = composerShape,
                    ambientColor = Color.Black.copy(alpha = 0.40f),
                    spotColor = Color.Black.copy(alpha = 0.50f)
                )
                .clip(composerShape)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xF2F3F5F9), Color(0xE0E1E5ED))
                    ),
                    composerShape
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.85f),
                    shape = composerShape
                )
                .padding(start = 6.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onOpenAttachReport,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("ai_attach_report_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.AttachFile,
                    contentDescription = "Upload or attach report",
                    tint = Color(0xFF4C5361),
                    modifier = Modifier
                        .size(21.dp)
                        .rotate(35f)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (uiState.aiDraftInput.isEmpty()) {
                    Text(
                        text = "Message",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF6B7280)
                    )
                }
                BasicTextField(
                    value = uiState.aiDraftInput,
                    onValueChange = onDraftChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = Color(0xFF1A1E26)
                    ),
                    cursorBrush = SolidColor(Color(0xFF1A1E26)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSendPrompt(uiState.aiDraftInput) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_chat_input")
                )
            }

            // Context counter badge matching "✦ 45" in reference
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { showQuickPrompts = !showQuickPrompts }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFF1E222B),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${uiState.medicines.size}",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color(0xFF1E222B)
                )
            }

            // Dark circular Mic / Send action button inside pill
            val hasDraft = uiState.aiDraftInput.isNotBlank()
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1F242D))
                    .clickable {
                        if (hasDraft) {
                            onSendPrompt(uiState.aiDraftInput)
                        } else {
                            onOpenVoice()
                        }
                    }
                    .testTag(if (hasDraft) "ai_send_button" else "ai_mic_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (hasDraft) Icons.AutoMirrored.Filled.Send else Icons.Filled.Mic,
                    contentDescription = if (hasDraft) "Send message" else "Voice input",
                    tint = PlatinumWhite,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ReferenceChatBubble(message: ChatMessage, onRemoveAttachment: (String) -> Unit) {
    // Sleek asymmetric tail corner matching image_30fb990b.png
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (message.isUser) 18.dp else 4.dp,
        bottomEnd = if (message.isUser) 4.dp else 18.dp
    )

    val fill = if (message.isUser) {
        Brush.verticalGradient(listOf(Color(0xA83E4B63), Color(0x8C252D3B)))
    } else {
        Brush.verticalGradient(listOf(Color(0xBCEEF3FC), Color(0xA8DCE4F2)))
    }

    val borderColor = if (message.isUser) {
        IceBlueText.copy(alpha = 0.42f)
    } else {
        Color.White.copy(alpha = 0.62f)
    }

    val textColor = if (message.isUser) {
        PlatinumWhite
    } else {
        Color(0xFF1A1E26)
    }

    val secondaryColor = if (message.isUser) {
        SilverText
    } else {
        Color(0xFF5A6270)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 304.dp)
                .shadow(
                    elevation = 4.dp,
                    shape = shape,
                    ambientColor = Color.Black.copy(alpha = 0.25f),
                    spotColor = Color.Black.copy(alpha = 0.30f)
                )
                .clip(shape)
                .background(fill, shape)
                .border(0.7.dp, borderColor, shape)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!message.attachmentTitle.isNullOrBlank()) {
                    val file = remember(message.attachmentPath) {
                        message.attachmentPath?.let { File(it) }?.takeIf { it.exists() }
                    }
                    val isImg = message.attachmentMimeType?.startsWith("image/", ignoreCase = true) == true
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x1E93C5FD))
                            .border(1.dp, IceBlueText.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (file != null && isImg) {
                            AsyncImage(
                                model = file,
                                contentDescription = message.attachmentTitle,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.UploadFile,
                                contentDescription = null,
                                tint = IceBlueText,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = message.attachmentTitle,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!message.attachmentSubtitle.isNullOrBlank()) {
                                Text(
                                    text = message.attachmentSubtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = secondaryColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        message.backendDocumentId?.let { documentId ->
                            IconButton(onClick = { onRemoveAttachment(documentId) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove report from this chat", tint = secondaryColor)
                            }
                        }
                    }
                }
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.5.sp,
                        lineHeight = 20.sp
                    ),
                    color = textColor
                )
                if (message.isStreaming) {
                    Text(
                        text = "Typing...",
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryColor
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceNoteWaveformBubble(durationLabel: String) {
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0.82f) }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            delay(140L)
            progress = (progress + 0.04f).let { if (it > 1f) 0f else it }
        }
    }

    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = 18.dp,
        bottomEnd = 4.dp
    )

    val barHeights = remember {
        listOf(
            0.28f, 0.55f, 0.85f, 0.42f, 0.70f, 0.96f, 0.50f, 0.35f, 0.78f, 0.92f,
            0.48f, 0.66f, 0.32f, 0.82f, 0.54f, 0.95f, 0.60f, 0.38f, 0.74f, 0.88f,
            0.44f, 0.68f, 0.90f, 0.52f, 0.36f, 0.76f, 0.84f, 0.40f, 0.62f, 0.88f,
            0.46f, 0.72f, 0.30f, 0.60f, 0.42f, 0.28f
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Row(
            modifier = Modifier
                .widthIn(min = 258.dp, max = 296.dp)
                .shadow(4.dp, shape)
                .clip(shape)
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF212630), Color(0xFF1B1F27))),
                    shape
                )
                .border(0.7.dp, Color(0x18FFFFFF), shape)
                .padding(start = 10.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 40.dp, minHeight = 40.dp)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(PlatinumWhite)
                    .clickable { isPlaying = !isPlaying }
                    .testTag("ai_voice_note_play"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause voice note" else "Play voice note",
                    tint = Color(0xFF1B1F27),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Fine high-density waveform + vertical playhead line matching reference image
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
            ) {
                val count = barHeights.size
                val barW = 1.6.dp.toPx()
                val gap = ((size.width - count * barW) / (count - 1).coerceAtLeast(1))
                    .coerceAtLeast(1.2.dp.toPx())
                val centerY = size.height / 2f

                barHeights.forEachIndexed { idx, frac ->
                    val x = idx * (barW + gap)
                    if (x + barW <= size.width) {
                        val h = (size.height * 0.82f * frac).coerceAtLeast(4.dp.toPx())
                        val ratio = idx.toFloat() / count.toFloat()
                        val color = if (ratio <= progress) {
                            PlatinumWhite.copy(alpha = 0.92f)
                        } else {
                            SilverText.copy(alpha = 0.42f)
                        }
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(x, centerY - h / 2f),
                            size = Size(barW, h),
                            cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                        )
                    }
                }

                // Crisp tall playhead scrubber line
                val scrubberX = (size.width * progress).coerceIn(2.dp.toPx(), size.width - 2.dp.toPx())
                drawRoundRect(
                    color = PlatinumWhite,
                    topLeft = Offset(scrubberX, 0f),
                    size = Size(1.8.dp.toPx(), size.height),
                    cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                )
            }

            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = durationLabel,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Normal,
                    fontFeatureSettings = "tnum"
                ),
                color = PlatinumWhite.copy(alpha = 0.92f)
            )
        }
    }
}

@Composable
private fun ChromeCloverSculpture(
    animate: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "chromeClover")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cloverRotate"
    )

    val cloverBitmap = rememberTransparentChromeBitmap(
        resId = R.drawable.img_ai_chrome_clover,
        targetSizePx = 320
    )

    if (cloverBitmap != null) {
        Image(
            bitmap = cloverBitmap,
            contentDescription = "Verity AI emblem",
            contentScale = ContentScale.Fit,
            modifier = modifier.graphicsLayer {
                if (animate) {
                    rotationZ = angle
                }
            }
        )
    } else {
        Spacer(modifier = modifier)
    }
}

private object ChromeBitmapCache {
    private val cache = mutableMapOf<Int, ImageBitmap>()

    fun get(resId: Int): ImageBitmap? = synchronized(cache) { cache[resId] }

    fun put(resId: Int, bitmap: ImageBitmap) = synchronized(cache) {
        cache[resId] = bitmap
    }
}

@Composable
private fun rememberTransparentChromeBitmap(
    @DrawableRes resId: Int,
    targetSizePx: Int
): ImageBitmap? {
    val context = LocalContext.current
    val cached = ChromeBitmapCache.get(resId)
    val state by produceState<ImageBitmap?>(initialValue = cached, key1 = resId) {
        if (value == null) {
            val loaded = withContext(Dispatchers.Default) {
                decodeAndKnockoutWhiteBackground(context, resId, targetSizePx)
            }
            if (loaded != null) {
                ChromeBitmapCache.put(resId, loaded)
                value = loaded
            }
        }
    }
    return state
}

private fun decodeAndKnockoutWhiteBackground(
    context: Context,
    @DrawableRes resId: Int,
    targetSizePx: Int
): ImageBitmap? {
    val raw = BitmapFactory.decodeResource(context.resources, resId) ?: return null
    val aspect = raw.width.toFloat() / raw.height.toFloat().coerceAtLeast(1f)
    val w = if (aspect >= 1f) targetSizePx else (targetSizePx * aspect).toInt().coerceAtLeast(1)
    val h = if (aspect >= 1f) (targetSizePx / aspect).toInt().coerceAtLeast(1) else targetSizePx

    val scaled = Bitmap.createScaledBitmap(raw, w, h, true)
    if (scaled !== raw) {
        raw.recycle()
    }
    val argb = if (scaled.config == Bitmap.Config.ARGB_8888) {
        scaled
    } else {
        scaled.copy(Bitmap.Config.ARGB_8888, true).also { scaled.recycle() }
    }

    val total = w * h
    val pixels = IntArray(total)
    argb.getPixels(pixels, 0, w, 0, 0, w, h)

    val bgMask = BooleanArray(total)
    val visited = BooleanArray(total)
    val queue = IntArray(total)

    fun isNearWhiteNeutral(pixel: Int, minChannelThreshold: Int, maxChromaDiff: Int): Boolean {
        val r = (pixel ushr 16) and 0xFF
        val g = (pixel ushr 8) and 0xFF
        val b = pixel and 0xFF
        val minC = min(r, min(g, b))
        val maxC = max(r, max(g, b))
        return minC >= minChannelThreshold && (maxC - minC) <= maxChromaDiff
    }

    // 1. Exterior BFS from all 4 borders
    var head = 0
    var tail = 0
    fun tryPushExterior(idx: Int) {
        if (!visited[idx] && isNearWhiteNeutral(pixels[idx], 234, 14)) {
            visited[idx] = true
            bgMask[idx] = true
            queue[tail++] = idx
        }
    }

    for (x in 0 until w) {
        tryPushExterior(x)
        tryPushExterior((h - 1) * w + x)
    }
    for (y in 0 until h) {
        tryPushExterior(y * w)
        tryPushExterior(y * w + (w - 1))
    }

    while (head < tail) {
        val curr = queue[head++]
        val cx = curr % w
        val cy = curr / w
        if (cx > 0) tryPushExterior(curr - 1)
        if (cx + 1 < w) tryPushExterior(curr + 1)
        if (cy > 0) tryPushExterior(curr - w)
        if (cy + 1 < h) tryPushExterior(curr + w)
    }

    // 2. Interior enclosed background holes inside chrome loops (large flat near-white regions)
    val componentIndices = IntArray(total)
    for (i in 0 until total) {
        if (!visited[i] && isNearWhiteNeutral(pixels[i], 244, 8)) {
            var compHead = 0
            var compTail = 0
            visited[i] = true
            componentIndices[compTail++] = i

            while (compHead < compTail) {
                val curr = componentIndices[compHead++]
                val cx = curr % w
                val cy = curr / w
                val neighbors = intArrayOf(
                    if (cx > 0) curr - 1 else -1,
                    if (cx + 1 < w) curr + 1 else -1,
                    if (cy > 0) curr - w else -1,
                    if (cy + 1 < h) curr + w else -1
                )
                for (n in neighbors) {
                    if (n >= 0 && !visited[n] && isNearWhiteNeutral(pixels[n], 242, 10)) {
                        visited[n] = true
                        componentIndices[compTail++] = n
                    }
                }
            }

            if (compTail >= 75) {
                for (k in 0 until compTail) {
                    bgMask[componentIndices[k]] = true
                }
            }
        }
    }

    // 3. Apply clean transparency + 1-pixel anti-aliased fringe feathering
    val outPixels = pixels.copyOf()
    for (y in 0 until h) {
        for (x in 0 until w) {
            val idx = y * w + x
            if (bgMask[idx]) {
                outPixels[idx] = 0x00000000
            } else {
                var touchesBg = false
                for (dy in -1..1) {
                    val ny = y + dy
                    if (ny in 0 until h) {
                        for (dx in -1..1) {
                            val nx = x + dx
                            if (nx in 0 until w && bgMask[ny * w + nx]) {
                                touchesBg = true
                                break
                            }
                        }
                    }
                    if (touchesBg) break
                }

                if (touchesBg) {
                    val p = pixels[idx]
                    val r = (p ushr 16) and 0xFF
                    val g = (p ushr 8) and 0xFF
                    val b = p and 0xFF
                    val luma = (r + g + b) / 3
                    if (luma > 185) {
                        val alphaFrac = ((250f - luma) / 65f).coerceIn(0.12f, 1f)
                        val a = (alphaFrac * 255f).toInt().coerceIn(0, 255)
                        outPixels[idx] = (a shl 24) or (r shl 16) or (g shl 8) or b
                    }
                }
            }
        }
    }

    argb.setPixels(outPixels, 0, w, 0, 0, w, h)
    return argb.asImageBitmap()
}
