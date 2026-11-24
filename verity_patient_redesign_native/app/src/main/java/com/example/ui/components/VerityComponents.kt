package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.AlarmOn
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material.icons.outlined.MedicalInformation
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material.icons.rounded.MedicalInformation
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.model.Appointment
import com.example.model.NotificationItem
import com.example.model.VerityTab
import com.example.ui.theme.CharcoalBase
import com.example.ui.theme.GlassBorderBottom
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.GlassButtonCore
import com.example.ui.theme.GlassButtonMid
import com.example.ui.theme.GlassButtonOuter
import com.example.ui.theme.GlassChipActiveBg
import com.example.ui.theme.GlassChipActiveText
import com.example.ui.theme.GlassChipMutedBg
import com.example.ui.theme.GlassFillBottom
import com.example.ui.theme.GlassFillStrongBottom
import com.example.ui.theme.GlassFillStrongTop
import com.example.ui.theme.GlassFillTop
import com.example.ui.theme.IceBlueText
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.PeriwinkleMist
import com.example.ui.theme.PlatinumWhite
import com.example.ui.theme.SilverText
import com.example.ui.theme.SlateInkDark
import com.example.ui.theme.SoftLavenderText
import kotlinx.coroutines.delay

/**
 * Smoky slate-grey & cool silver mist atmospheric backdrop (greyish frosted glass theme).
 */
@Composable
fun VerityGlassBackdrop(
    isObsidianMode: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val topGrey = if (isObsidianMode) Color(0xFF3A3E48) else Color(0xFF4B505C)
    val midGrey = if (isObsidianMode) Color(0xFF282B33) else Color(0xFF353944)
    val bottomGrey = if (isObsidianMode) Color(0xFF1B1D23) else Color(0xFF262932)
    val fogAlpha = if (isObsidianMode) 0.42f else 0.52f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(topGrey, midGrey, bottomGrey)
                )
            )
            .drawBehind {
                // Soft silver-grey atmospheric fog patches across the canvas
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFD2D5DC).copy(alpha = fogAlpha),
                            Color(0xFF8E94A0).copy(alpha = fogAlpha * 0.45f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.14f, size.height * 0.12f),
                        radius = size.width * 0.82f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFD9DCE2).copy(alpha = fogAlpha * 0.85f),
                            Color(0xFF818793).copy(alpha = fogAlpha * 0.42f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.90f, size.height * 0.24f),
                        radius = size.width * 0.78f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFAEB3BD).copy(alpha = fogAlpha * 0.52f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.86f, size.height * 0.72f),
                        radius = size.width * 0.70f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF9EA4B0).copy(alpha = fogAlpha * 0.46f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.14f, size.height * 0.80f),
                        radius = size.width * 0.66f
                    )
                )
            },
        content = content
    )
}

@Composable
fun VerityGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    strong: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val fillBrush = Brush.verticalGradient(
        colors = if (strong) {
            listOf(GlassFillStrongTop, GlassFillStrongBottom)
        } else {
            listOf(GlassFillTop, GlassFillBottom)
        }
    )
    val borderBrush = Brush.verticalGradient(
        colors = listOf(GlassBorderTop, GlassBorderBottom)
    )

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .clip(shape)
            .background(fillBrush, shape)
            .border(width = 1.dp, brush = borderBrush, shape = shape)
            .then(clickableModifier),
        content = content
    )
}

/**
 * Signature metallic-glass pill CTA button matching the "Login" button in the reference screenshot.
 */
@Composable
fun VerityGlassPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    minHeight: Dp = 54.dp,
    horizontalPadding: Dp = 20.dp,
    darkCore: Boolean = false
) {
    val shape = RoundedCornerShape(30.dp)
    val baseFill = when {
        !enabled -> Brush.verticalGradient(listOf(Color(0xFF393D47), Color(0xFF2D3038)))
        darkCore -> Brush.verticalGradient(listOf(Color(0xFF1B1D22), Color(0xFF17191E)))
        else -> Brush.verticalGradient(listOf(Color(0xFF5D6371), Color(0xFF434854)))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = minHeight)
            .shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.45f), spotColor = Color.Black.copy(alpha = 0.55f))
            .clip(shape)
            .background(baseFill, shape)
            .drawBehind {
                if (enabled) {
                    val topGlowAlpha = if (darkCore) 0.72f else 0.44f
                    val midGlowAlpha = if (darkCore) 0.24f else 0.16f
                    val bottomGlowAlpha = if (darkCore) 0.56f else 0.30f
                    val sideGlowAlpha = if (darkCore) 0.62f else 0.34f

                    // Top & bottom silver frost sheen
                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color(0xFFF3F4F6).copy(alpha = topGlowAlpha),
                                0.28f to Color(0xFFD1D5DB).copy(alpha = midGlowAlpha),
                                0.48f to Color.Transparent,
                                0.68f to Color.Transparent,
                                0.86f to Color(0xFF9CA3AF).copy(alpha = midGlowAlpha),
                                1.0f to Color(0xFFE5E7EB).copy(alpha = bottomGlowAlpha)
                            )
                        )
                    )
                    // Left & right rounded end-cap silver glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFE5E7EB).copy(alpha = sideGlowAlpha),
                                Color(0xFF9CA3AF).copy(alpha = sideGlowAlpha * 0.3f),
                                Color.Transparent
                            ),
                            center = Offset(size.height * 0.15f, size.height * 0.5f),
                            radius = size.height * 1.15f
                        )
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFE5E7EB).copy(alpha = sideGlowAlpha),
                                Color(0xFF9CA3AF).copy(alpha = sideGlowAlpha * 0.3f),
                                Color.Transparent
                            ),
                            center = Offset(size.width - size.height * 0.15f, size.height * 0.5f),
                            radius = size.height * 1.15f
                        )
                    )
                }
            }
            .border(
                width = 1.1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.76f),
                        Color.White.copy(alpha = 0.26f),
                        Color.White.copy(alpha = 0.44f)
                    )
                ),
                shape = shape
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 12.dp)
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = PlatinumWhite,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                color = if (enabled) PlatinumWhite else SilverText.copy(alpha = 0.55f),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun VerityGlassSecondaryPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    minHeight: Dp = 48.dp,
    horizontalPadding: Dp = 14.dp
) {
    val shape = CircleShape
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = minHeight)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0x2BFFFFFF), Color(0x14FFFFFF))
                ),
                shape
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0x52FFFFFF), Color(0x1AFFFFFF))
                ),
                shape = shape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 12.dp)
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = PlatinumWhite,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = PlatinumWhite,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun VerityGlassInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    testTag: String = "glass_input"
) {
    val shape = RoundedCornerShape(30.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 54.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0x21FFFFFF), Color(0x0FFFFFFF))
                ),
                shape
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0x52FFFFFF), Color(0x1EFFFFFF))
                ),
                shape = shape
            )
            .padding(horizontal = 18.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = PlatinumWhite.copy(alpha = 0.86f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 40.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = SilverText.copy(alpha = 0.78f)
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = PlatinumWhite),
                    cursorBrush = SolidColor(PlatinumWhite),
                    visualTransformation = visualTransformation,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(testTag)
                )
            }
            if (trailingContent != null) {
                trailingContent()
            }
        }
    }
}

@Composable
fun VerityGlassChip(
    text: String,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val shape: Shape = CircleShape
    val bg = if (highlighted) GlassChipActiveBg else GlassChipMutedBg
    val border = if (highlighted) IceBlueText.copy(alpha = 0.55f) else PeriwinkleMist.copy(alpha = 0.25f)
    val clickMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    Box(
        modifier = modifier
            .clip(shape)
            .background(bg, shape)
            .border(1.dp, border, shape)
            .then(clickMod)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (highlighted) GlassChipActiveText else SoftLavenderText
        )
    }
}

@Composable
fun VerityFloatingBottomBar(
    currentTab: VerityTab,
    onSelectTab: (VerityTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val navTabs = remember {
        listOf(
            VerityTab.MEDICINES,
            VerityTab.ALARMS,
            VerityTab.HOME,
            VerityTab.AI,
            VerityTab.RECORDS
        )
    }
    val haptic = LocalHapticFeedback.current
    val selectedIndex = navTabs.indexOf(currentTab).let { if (it >= 0) it else 2 }

    // Living ambient shimmer & breathing aura across the floating glass dock
    val infiniteTransition = rememberInfiniteTransition(label = "navDockLive")
    val shimmerProgress by infiniteTransition.animateFloat(
        initialValue = -0.35f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "navShimmer"
    )
    val auraPulse by infiniteTransition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "navAuraPulse"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .fillMaxWidth()
        ) {
            val dockPadding = 5.dp
            val slotGap = 4.dp
            val selectedWeight = 2.45f
            val unselectedWeight = 1f
            val totalWeight = selectedWeight + unselectedWeight * (navTabs.size - 1)
            val usableWidth = (maxWidth - dockPadding * 2 - slotGap * (navTabs.size - 1)).coerceAtLeast(0.dp)
            val unselectedWidth = usableWidth * (unselectedWeight / totalWeight)
            val selectedWidth = usableWidth * (selectedWeight / totalWeight)
            val targetIndicatorX = (unselectedWidth + slotGap) * selectedIndex

            val animatedIndicatorX by animateDpAsState(
                targetValue = targetIndicatorX,
                animationSpec = spring(
                    dampingRatio = 0.70f,
                    stiffness = 380f
                ),
                label = "navPillX"
            )
            val animatedGlowFraction by animateFloatAsState(
                targetValue = (selectedIndex + 0.5f) / navTabs.size.toFloat(),
                animationSpec = spring(
                    dampingRatio = 0.76f,
                    stiffness = 340f
                ),
                label = "navGlowX"
            )

            // Levitating glass dock with dynamic aurora under-glow & deep drop shadow
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val glowX = size.width * animatedGlowFraction
                        val glowY = size.height * 0.68f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    IceBlueText.copy(alpha = 0.28f * auraPulse),
                                    SoftLavenderText.copy(alpha = 0.14f * auraPulse),
                                    Color.Transparent
                                ),
                                center = Offset(glowX, glowY),
                                radius = size.width * 0.42f
                            ),
                            center = Offset(glowX, glowY),
                            radius = size.width * 0.42f
                        )
                    }
                    .shadow(
                        elevation = 24.dp,
                        shape = CircleShape,
                        ambientColor = Color.Black.copy(alpha = 0.65f),
                        spotColor = Color(0xFF0A0E17).copy(alpha = 0.78f)
                    )
                    .clip(CircleShape)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xD9525A6D),
                                Color(0xD42F3441),
                                Color(0xDE222630)
                            )
                        ),
                        shape = CircleShape
                    )
                    .drawBehind {
                        // Subtle sweeping optical glass sheen across the dock
                        val startX = size.width * (shimmerProgress - 0.25f)
                        val endX = size.width * (shimmerProgress + 0.25f)
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.10f),
                                    IceBlueText.copy(alpha = 0.08f),
                                    Color.Transparent
                                ),
                                start = Offset(startX, 0f),
                                end = Offset(endX, size.height)
                            )
                        )
                    }
                    .border(
                        width = 1.25.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.80f),
                                Color.White.copy(alpha = 0.18f),
                                IceBlueText.copy(alpha = 0.42f)
                            )
                        ),
                        shape = CircleShape
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(dockPadding)
                ) {
                    // Gliding pearlescent active capsule indicator
                    Box(
                        modifier = Modifier
                            .offset(x = animatedIndicatorX)
                            .width(selectedWidth)
                            .height(48.dp)
                            .shadow(
                                elevation = 8.dp,
                                shape = CircleShape,
                                ambientColor = IceBlueText.copy(alpha = 0.45f),
                                spotColor = IceBlueText.copy(alpha = 0.55f)
                            )
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFFFAFBFD),
                                        Color(0xFFE7F0FF),
                                        Color(0xFFF3F6FC)
                                    )
                                ),
                                shape = CircleShape
                            )
                            .drawBehind {
                                val sweepStart = size.width * (shimmerProgress - 0.35f)
                                val sweepEnd = size.width * (shimmerProgress + 0.25f)
                                drawRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.White.copy(alpha = 0.72f),
                                            Color.Transparent
                                        ),
                                        start = Offset(sweepStart, 0f),
                                        end = Offset(sweepEnd, size.height)
                                    )
                                )
                            }
                            .border(
                                width = 1.dp,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White,
                                        IceBlueText.copy(alpha = 0.75f)
                                    )
                                ),
                                shape = CircleShape
                            )
                    )

                    // Interactive floating tab slots
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(slotGap),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        navTabs.forEach { tab ->
                            val selected = tab == currentTab
                            val isCenterHome = tab == VerityTab.HOME
                            val animatedWeight by animateFloatAsState(
                                targetValue = if (selected) selectedWeight else unselectedWeight,
                                animationSpec = spring(
                                    dampingRatio = 0.74f,
                                    stiffness = 380f
                                ),
                                label = "tabWeight"
                            )
                            val iconScale by animateFloatAsState(
                                targetValue = if (selected) 1.12f else 0.96f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                ),
                                label = "tabIconScale"
                            )
                            val contentTint by animateColorAsState(
                                targetValue = when {
                                    selected -> SlateInkDark
                                    isCenterHome -> IceBlueText.copy(alpha = 0.96f)
                                    else -> PlatinumWhite.copy(alpha = 0.86f)
                                },
                                animationSpec = tween(180),
                                label = "tabTint"
                            )

                            val centerAnchorModifier = if (isCenterHome && !selected) {
                                Modifier
                                    .background(Color(0x1F93C5FD), CircleShape)
                                    .border(0.9.dp, IceBlueText.copy(alpha = 0.32f), CircleShape)
                            } else {
                                Modifier
                            }

                            Box(
                                modifier = Modifier
                                    .weight(animatedWeight)
                                    .height(48.dp)
                                    .clip(CircleShape)
                                    .then(centerAnchorModifier)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        role = Role.Tab,
                                        onClickLabel = tab.label
                                    ) {
                                        if (!selected) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        onSelectTab(tab)
                                    }
                                    .testTag("nav_tab_${tab.route}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(horizontal = if (selected) 8.dp else 0.dp)
                                ) {
                                    Icon(
                                        imageVector = tabIcon(tab, selected),
                                        contentDescription = tab.label,
                                        tint = contentTint,
                                        modifier = Modifier
                                            .size(if (isCenterHome) 21.dp else 19.dp)
                                            .graphicsLayer {
                                                scaleX = iconScale
                                                scaleY = iconScale
                                                translationY = if (selected) -0.5.dp.toPx() else 0f
                                            }
                                    )
                                    AnimatedVisibility(
                                        visible = selected,
                                        enter = fadeIn(tween(170)) + expandHorizontally(
                                            animationSpec = spring(
                                                dampingRatio = 0.80f,
                                                stiffness = 420f
                                            )
                                        ),
                                        exit = fadeOut(tween(110)) + shrinkHorizontally(tween(130))
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = tab.label,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    letterSpacing = 0.1.sp
                                                ),
                                                color = contentTint,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Clip
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun tabIcon(tab: VerityTab, selected: Boolean): ImageVector {
    return when (tab) {
        VerityTab.MEDICINES -> if (selected) Icons.Rounded.LocalPharmacy else Icons.Outlined.LocalPharmacy
        VerityTab.ALARMS -> if (selected) Icons.Rounded.AlarmOn else Icons.Outlined.AlarmOn
        VerityTab.HOME -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
        VerityTab.AI -> if (selected) Icons.Rounded.AutoAwesome else Icons.Outlined.AutoAwesome
        VerityTab.RECORDS -> if (selected) Icons.Rounded.MedicalInformation else Icons.Outlined.MedicalInformation
        VerityTab.PROFILE -> if (selected) Icons.Rounded.Person else Icons.Outlined.Person
    }
}

@Composable
fun VerityBrandBadge(
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF343842), Color(0xFF15171D))
                )
            )
            .border(1.dp, Color(0x47FFFFFF), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val heights = listOf(8.dp, 14.dp, 20.dp, 14.dp, 8.dp)
            heights.forEach { barHeight ->
                Box(
                    modifier = Modifier
                        .width(2.5.dp)
                        .height(barHeight)
                        .clip(CircleShape)
                        .background(PlatinumWhite)
                )
            }
        }
    }
}

@Composable
fun VerityPillIconBadge(
    size: Dp = 48.dp,
    cornerRadius: Dp = 14.dp,
    containerColor: Color = Color(0x21FFFFFF),
    iconTint: Color = PlatinumWhite,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(containerColor)
            .border(1.dp, Color(0x33FFFFFF), shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Medication,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(size * 0.48f)
        )
    }
}

@Composable
fun PastelMicButton(
    onClick: () -> Unit,
    contentDescription: String,
    size: Dp = 42.dp,
    testTag: String = "pastel_mic_button",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFFE5E7EB), Color(0xFF9CA3AF))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = contentDescription,
            tint = ObsidianBlack,
            modifier = Modifier.size(20.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceConsultationSheet(
    doctorName: String = "your care team",
    onDismiss: () -> Unit,
    onAskAiWithTranscript: (String) -> Unit
) {
    val context = LocalContext.current
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(hasMicPermission) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        permissionDenied = !granted
        if (granted) {
            isRecording = true
        }
    }

    LaunchedEffect(isRecording) {
        while (isRecording) {
            delay(1000L)
            elapsedSeconds += 1
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(550),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CharcoalBase
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            VerityGlassChip(
                text = if (isRecording) "SECURELY RECORDING" else "VOICE INPUT",
                highlighted = true
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = doctorName,
                style = MaterialTheme.typography.titleLarge,
                color = PlatinumWhite
            )
            Text(
                text = "Encrypted voice capture",
                style = MaterialTheme.typography.bodyMedium,
                color = SilverText
            )
            Spacer(modifier = Modifier.height(22.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(56.dp)
            ) {
                val baseFractions = listOf(0.35f, 0.7f, 0.5f, 1.0f, 0.65f, 0.85f, 0.45f, 0.75f, 0.4f)
                baseFractions.forEachIndexed { idx, frac ->
                    val animatedHeight = if (isRecording) {
                        val mod = if (idx % 2 == 0) pulse else (1.4f - pulse).coerceIn(0.35f, 1f)
                        (48.dp * frac * mod).coerceAtLeast(10.dp)
                    } else {
                        12.dp
                    }
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .height(animatedHeight)
                            .clip(CircleShape)
                            .background(PlatinumWhite)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            val mins = elapsedSeconds / 60
            val secs = elapsedSeconds % 60
            Text(
                text = String.format("%02d:%02d", mins, secs),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = PlatinumWhite
            )

            if (permissionDenied) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Microphone permission is needed for live audio capture. You can still send a symptom summary to Verity AI.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SilverText
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (!hasMicPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            isRecording = !isRecording
                        }
                    },
                    border = BorderStroke(1.dp, Color(0x47FFFFFF)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PlatinumWhite),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("voice_record_toggle_button"),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isRecording) "Pause" else if (hasMicPermission) "Record" else "Allow Mic")
                }

                VerityGlassPillButton(
                    text = "Send to Verity",
                    onClick = {
                        isRecording = false
                        onAskAiWithTranscript("Summarize my check-in notes for $doctorName before my appointment.")
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("voice_send_ai_button")
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsSheet(
    notifications: List<NotificationItem>,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CharcoalBase
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = null,
                        tint = PlatinumWhite
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Notifications",
                        style = MaterialTheme.typography.titleLarge,
                        color = PlatinumWhite
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = PlatinumWhite)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            notifications.forEachIndexed { idx, item ->
                Column(modifier = Modifier.padding(vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = PlatinumWhite,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = item.timeAgo,
                            style = MaterialTheme.typography.bodySmall,
                            color = SilverText
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SilverText
                    )
                }
                if (idx < notifications.lastIndex) {
                    HorizontalDivider(color = Color(0x21FFFFFF))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppointmentDetailsSheet(
    appointment: Appointment,
    onDismiss: () -> Unit,
    onReschedule: () -> Unit,
    onJoinCall: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CharcoalBase
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 10.dp)
                .navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = appointment.avatarRes),
                    contentDescription = appointment.doctorName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = appointment.doctorName,
                        style = MaterialTheme.typography.titleLarge,
                        color = PlatinumWhite
                    )
                    Text(
                        text = "${appointment.specialty} • ${appointment.hospital}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SilverText
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.CalendarToday,
                        contentDescription = null,
                        tint = IceBlueText,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(appointment.dateLabel, style = MaterialTheme.typography.bodyMedium, color = PlatinumWhite)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.AccessTime,
                        contentDescription = null,
                        tint = IceBlueText,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(appointment.timeLabel, style = MaterialTheme.typography.bodyMedium, color = PlatinumWhite)
                }
                VerityGlassChip(
                    text = appointment.consultationType,
                    highlighted = true
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            VerityGlassCard(
                cornerRadius = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Consultation Focus • ${appointment.consultationType}",
                        style = MaterialTheme.typography.labelMedium,
                        color = SoftLavenderText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = appointment.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PlatinumWhite
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            val detailActionLabel = when (appointment.consultationType) {
                "AI Virtual" -> "Start Virtual"
                "AI Chat" -> "Open AI Chat"
                "In person" -> "Start Consultation"
                else -> "Join Call"
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                VerityGlassSecondaryPill(
                    text = "Reschedule",
                    onClick = onReschedule,
                    modifier = Modifier.weight(1f)
                )
                VerityGlassPillButton(
                    text = detailActionLabel,
                    leadingIcon = Icons.Filled.Videocam,
                    onClick = onJoinCall,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun RescheduleAppointmentDialog(
    appointment: Appointment,
    onDismiss: () -> Unit,
    onConfirmSlot: (String, String) -> Unit
) {
    val slots = listOf(
        "Today, 23 Nov" to "04:30 PM",
        "Tue, 24 Nov" to "11:00 AM",
        "Wed, 25 Nov" to "09:30 AM"
    )
    var selectedSlotIndex by remember { mutableIntStateOf(1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CharcoalBase,
        title = { Text("Reschedule ${appointment.doctorName}", color = PlatinumWhite) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Select an available consultation slot:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SilverText
                )
                slots.forEachIndexed { idx, (date, time) ->
                    val selected = idx == selectedSlotIndex
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) Color(0x2EFFFFFF) else Color(0x14FFFFFF),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (selected) Color(0x73FFFFFF) else Color(0x21FFFFFF)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { selectedSlotIndex = idx }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(date, style = MaterialTheme.typography.titleSmall, color = PlatinumWhite)
                            Text(
                                text = time,
                                style = MaterialTheme.typography.labelLarge,
                                color = PlatinumWhite
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val (date, time) = slots[selectedSlotIndex]
                    onConfirmSlot(date, time)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = PlatinumWhite,
                    contentColor = ObsidianBlack
                )
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SilverText)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinCallSheet(
    appointment: Appointment,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CharcoalBase
    ) {
        val readyChipLabel = when (appointment.consultationType) {
            "AI Virtual" -> "AI VIRTUAL SESSION READY"
            "AI Chat" -> "AI CHAT SESSION READY"
            "In person" -> "CLINIC CHECK-IN PASS"
            else -> "WAITING ROOM READY"
        }
        val leaveButtonLabel = when (appointment.consultationType) {
            "In person" -> "Close Check-In Pass"
            "AI Chat", "AI Virtual" -> "End Session"
            else -> "Leave Waiting Room"
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            VerityGlassChip(text = readyChipLabel, highlighted = true)
            Spacer(modifier = Modifier.height(16.dp))
            Image(
                painter = painterResource(id = appointment.avatarRes),
                contentDescription = appointment.doctorName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, Color(0x52FFFFFF), CircleShape)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = appointment.doctorName,
                style = MaterialTheme.typography.titleLarge,
                color = PlatinumWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${appointment.specialty} • ${appointment.consultationType} • ${appointment.timeLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = IceBlueText
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.LocalHospital,
                    contentDescription = null,
                    tint = SilverText,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = appointment.hospital,
                    style = MaterialTheme.typography.bodySmall,
                    color = SilverText
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFDC2626),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(leaveButtonLabel)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
