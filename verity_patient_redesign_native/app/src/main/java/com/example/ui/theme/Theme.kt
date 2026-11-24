package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val VeritySlateGlassScheme = darkColorScheme(
    primary = PlatinumWhite,
    onPrimary = ObsidianBlack,
    primaryContainer = Color(0x36FFFFFF),
    onPrimaryContainer = PlatinumWhite,
    secondary = IceBlueText,
    onSecondary = ObsidianBlack,
    secondaryContainer = Color(0x24A5B4FC),
    onSecondaryContainer = PlatinumWhite,
    tertiary = SoftLavenderText,
    onTertiary = ObsidianBlack,
    background = Color(0xFF262932),
    onBackground = PlatinumWhite,
    surface = CharcoalBase,
    onSurface = PlatinumWhite,
    surfaceVariant = GraphiteSurface,
    onSurfaceVariant = SilverText,
    outline = Color(0x4DFFFFFF),
    outlineVariant = Color(0x26FFFFFF)
)

private val VerityMistGlassScheme = darkColorScheme(
    primary = PlatinumWhite,
    onPrimary = ObsidianBlack,
    primaryContainer = Color(0x40FFFFFF),
    onPrimaryContainer = PlatinumWhite,
    secondary = IceBlueText,
    onSecondary = ObsidianBlack,
    secondaryContainer = Color(0x2BA5B4FC),
    onSecondaryContainer = PlatinumWhite,
    tertiary = SoftLavenderText,
    onTertiary = ObsidianBlack,
    background = Color(0xFF343842),
    onBackground = PlatinumWhite,
    surface = Color(0xFF3A3E49),
    onSurface = PlatinumWhite,
    surfaceVariant = Color(0xFF474C59),
    onSurfaceVariant = Color(0xFFE2E5EB),
    outline = Color(0x59FFFFFF),
    outlineVariant = Color(0x2EFFFFFF)
)

@Composable
fun VerityTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) VeritySlateGlassScheme else VerityMistGlassScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VerityTypography,
        content = content
    )
}
