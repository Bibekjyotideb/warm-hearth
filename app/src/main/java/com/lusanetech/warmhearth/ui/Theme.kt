package com.lusanetech.warmhearth.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Design system for WarmHearth.
 *
 * Deliberate choices (see .claude/skills/material-3 and accessibility-compliance):
 *  - No dynamic (wallpaper) colour. Seniors need the same colours every day, and every pair
 *    below is checked for WCAG AA contrast (white text on each tile colour is >= 4.5:1).
 *  - Soft paper background instead of pure white: less glare for ageing eyes.
 *  - Every touch target is at least 72dp (Material minimum is 48dp).
 *  - One spacing scale (8dp grid) and one shape scale, used everywhere.
 */

/** Named colours. Tile colours double as category colours so things are recognised by colour too. */
object Palette {
    val Background = Color(0xFFF6F7F2) // soft paper, low glare
    val Surface = Color(0xFFFFFFFF)
    val SurfaceDim = Color(0xFFE9ECE4)
    val Ink = Color(0xFF14211F)
    val InkSoft = Color(0xFF4A5754)
    val Outline = Color(0xFFCDD3CA)

    val Brand = Color(0xFF0F4C4A)   // dusk teal: the WarmHearth colour
    val Ember = Color(0xFFB35A0F)   // the "hearth" glow: Speak button, highlights
    val Green = Color(0xFF1E7B34)   // call, "I'm OK", success
    val Red = Color(0xFFC62828)     // SOS, danger
    val RedSoft = Color(0xFFFDECEA)
    val Orange = Color(0xFFB85C00)  // medicines, premium
    val Blue = Color(0xFF1B5E8C)    // people
    val Purple = Color(0xFF5E35B1)  // messages
    val Teal = Color(0xFF00695C)    // apps
    val Pink = Color(0xFFAD1457)    // photos
    val Grey = Color(0xFF4E5B62)    // neutral actions, back
    val WhatsApp = Color(0xFF0E7A3C)
}

/** 8dp spacing grid. */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    /** Minimum height of anything tappable. */
    val touch = 72.dp
}

private val colors = lightColorScheme(
    primary = Palette.Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFE6E2),
    onPrimaryContainer = Color(0xFF00201E),
    secondary = Palette.Green,
    onSecondary = Color.White,
    tertiary = Palette.Blue,
    onTertiary = Color.White,
    error = Palette.Red,
    onError = Color.White,
    errorContainer = Palette.RedSoft,
    onErrorContainer = Color(0xFF5F1412),
    background = Palette.Background,
    onBackground = Palette.Ink,
    surface = Palette.Surface,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.SurfaceDim,
    onSurfaceVariant = Palette.InkSoft,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.Surface,
    surfaceContainerHighest = Palette.SurfaceDim,
    outline = Palette.InkSoft,
    outlineVariant = Palette.Outline,
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * [scale] multiplies every text size so the family can pick Large / Extra large / Huge in
 * settings, on top of the phone's own font size. Family mode uses 1.0.
 */
@Composable
fun WarmHearthTheme(scale: Float = 1.25f, content: @Composable () -> Unit) {
    fun s(size: Int, weight: FontWeight = FontWeight.Normal, tracking: Float = 0f) = TextStyle(
        fontSize = (size * scale).sp,
        fontWeight = weight,
        lineHeight = (size * scale * 1.3f).sp,
        letterSpacing = tracking.sp,
    )

    val type = Typography(
        displayLarge = s(56, FontWeight.Bold, -0.5f),
        displayMedium = s(44, FontWeight.Bold),
        displaySmall = s(36, FontWeight.Bold),
        headlineLarge = s(32, FontWeight.Bold),
        headlineMedium = s(26, FontWeight.Bold),
        headlineSmall = s(22, FontWeight.Bold),
        titleLarge = s(22, FontWeight.SemiBold),
        titleMedium = s(19, FontWeight.SemiBold),
        titleSmall = s(16, FontWeight.SemiBold),
        bodyLarge = s(18),
        bodyMedium = s(16),
        bodySmall = s(14),
        labelLarge = s(18, FontWeight.SemiBold),
        labelMedium = s(15, FontWeight.Medium),
        labelSmall = s(13, FontWeight.Medium),
    )
    MaterialTheme(colorScheme = colors, typography = type, shapes = shapes, content = content)
}
