package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Clinical Precision Light-First Palette
val ClinicalBackground = Color(0xFFF8FAFC)
val ClinicalSurface = Color(0xFFFFFFFF)
val ClinicalSurfaceSubtle = Color(0xFFF1F5F9)
val ClinicalBorder = Color(0xFFE2E8F0)
val ClinicalBorderStrong = Color(0xFFCBD5E1)

val InkPrimary = Color(0xFF0F172A)
val InkSecondary = Color(0xFF475569)
val InkMuted = Color(0xFF64748B)
val InkSubtle = Color(0xFF94A3B8)

// Consistent Ocean Teal -> Glacial Mist Clinical Palette
val OceanTealTop = Color(0xFF0A5E7A)
val OceanTealMid = Color(0xFF28829D)
val OceanTealMist = Color(0xFF8EC0CF)
val GlacialMistMid = Color(0xFFD5E8EE)
val GlacialMistBottom = Color(0xFFF3F8FA)

val MistTop = OceanTealTop
val MistMid = GlacialMistMid
val MistBottom = GlacialMistBottom

val OceanTealPrimary = Color(0xFF0B627E)
val OceanTealDark = Color(0xFF084B61)
val OceanTealContainer = Color(0xFFCDE7EF)
val OceanTealSoft = Color(0xFFE8F4F8)

val SlateGunmetalDark = OceanTealPrimary
val SlateGunmetalMid = OceanTealMid
val SlateGunmetalLight = Color(0xFF4B9BB2)
val SlateLayerBack1 = Color(0xFFE6F2F6)
val SlateLayerBack2 = Color(0xFFD2E6EE)
val SlateContainer = OceanTealContainer
val SlateSoft = OceanTealSoft

// Accents aligned to Ocean Teal Clinical Theme
val MedicalTeal = OceanTealPrimary
val MedicalTealDark = OceanTealDark
val MedicalTealContainer = OceanTealContainer
val MedicalTealSoft = OceanTealSoft

val ClinicalBlue = Color(0xFF1A7A99)
val ClinicalBlueDark = OceanTealDark
val ClinicalBlueContainer = OceanTealContainer
val ClinicalIndigo = OceanTealPrimary

val OceanMistGradientStops = listOf(
    OceanTealTop,
    OceanTealMid,
    Color(0xFF9AC9D7),
    GlacialMistMid,
    GlacialMistBottom
)

// Clinical Status Semantic Tokens
val AmberText = Color(0xFFB45309)
val AmberBg = Color(0xFFFEF3C7)
val AmberBorder = Color(0xFFFDE68A)

val EmeraldText = Color(0xFF047857)
val EmeraldBg = Color(0xFFD1FAE5)
val EmeraldBorder = Color(0xFFA7F3D0)

val SkyText = Color(0xFF0369A1)
val SkyBg = Color(0xFFE0F2FE)
val SkyBorder = Color(0xFFBAE6FD)

val RoseText = Color(0xFFBE123C)
val RoseBg = Color(0xFFFFE4E6)
val RoseBorder = Color(0xFFFECDD3)

val NeutralText = Color(0xFF475569)
val NeutralBg = Color(0xFFF1F5F9)
val NeutralBorder = Color(0xFFE2E8F0)
