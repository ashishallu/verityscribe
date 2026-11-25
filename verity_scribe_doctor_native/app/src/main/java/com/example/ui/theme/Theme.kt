package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ClinicalLightColorScheme = lightColorScheme(
    primary = OceanTealPrimary,
    onPrimary = Color.White,
    primaryContainer = OceanTealContainer,
    onPrimaryContainer = OceanTealDark,
    secondary = OceanTealMid,
    onSecondary = Color.White,
    secondaryContainer = OceanTealSoft,
    onSecondaryContainer = OceanTealDark,
    tertiary = ClinicalBlue,
    onTertiary = Color.White,
    background = GlacialMistBottom,
    onBackground = InkPrimary,
    surface = ClinicalSurface,
    onSurface = InkPrimary,
    surfaceVariant = ClinicalSurfaceSubtle,
    onSurfaceVariant = InkMuted,
    outline = ClinicalBorder,
    outlineVariant = ClinicalBorderStrong,
    error = RoseText,
    onError = Color.White,
    errorContainer = RoseBg,
    onErrorContainer = RoseText
)

val ClinicalShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun VerityScribeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ClinicalLightColorScheme,
        typography = Typography,
        shapes = ClinicalShapes,
        content = content
    )
}
