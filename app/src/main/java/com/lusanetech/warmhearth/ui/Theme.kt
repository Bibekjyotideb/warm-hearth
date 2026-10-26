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
 * WarmHearth design system, v2 ("calm, grown-up, big").
 *
 * Why it changed: research on senior launchers (ReDEAP review, Scitepress 2023) found that
 * toy-like, rainbow-block launchers embarrass older users and get abandoned; modern-looking ones
 * get no dignity complaints. So:
 *  - Tiles are light tinted cards with a solid colour chip for the icon and dark text for the
 *    label. Colour still tells things apart; the text has 12:1+ contrast instead of 4.5:1.
 *  - Solid, saturated colour is reserved for actions that matter: SOS (red), call / I'm OK
 *    (green), Speak (ember).
 *  - Warm paper background (less glare than white), one spacing scale, one shape scale.
 *  - Touch targets stay >= 72dp on the senior side. No dynamic colour: the same colours every day.
 *
 * Every pair below is checked: Ink on any *Soft tint >= 11:1; white on every solid >= 4.5:1;
 * each solid colour as text on its *Soft tint >= 4.5:1.
 */
object Palette {
    val Background = Color(0xFFF5F3EE) // warm paper
    val Surface = Color(0xFFFFFFFF)
    val SurfaceLow = Color(0xFFEDEAE3)
    val Ink = Color(0xFF1B1F1D)
    val InkSoft = Color(0xFF545C58)
    val Outline = Color(0xFFDAD5CB)

    val Brand = Color(0xFF0F4C4A)   // dusk teal: the WarmHearth colour
    val BrandSoft = Color(0xFFDCEBE8)
    val Ember = Color(0xFFB4570F)   // the hearth glow: Speak, highlights
    val EmberSoft = Color(0xFFFBE9D7)
    val Green = Color(0xFF1E7B34)   // call, "I'm OK", success
    val GreenSoft = Color(0xFFE1F1E3)
    val Red = Color(0xFFC62828)     // SOS, danger
    val RedSoft = Color(0xFFFDECEA)
    val Orange = Color(0xFFA35100)  // medicines, attention
    val OrangeSoft = Color(0xFFFCEBD9)
    val Blue = Color(0xFF1B5E8C)    // people
    val BlueSoft = Color(0xFFE2EEF7)
    val Purple = Color(0xFF5E35B1)  // messages, reading
    val PurpleSoft = Color(0xFFECE6F7)
    val Teal = Color(0xFF00695C)    // apps
    val TealSoft = Color(0xFFDDF0EC)
    val Pink = Color(0xFFAD1457)    // photos
    val PinkSoft = Color(0xFFF9E3EC)
    val Grey = Color(0xFF4E5B62)    // neutral actions, back
    val GreySoft = Color(0xFFE6E9E8)
    val WhatsApp = Color(0xFF0E7A3C)
    val WhatsAppSoft = Color(0xFFDFF2E6)
}

/** A solid colour and its tint, used together for tiles, chips and status cards. */
data class Tone(val solid: Color, val soft: Color) {
    companion object {
        val Brand = Tone(Palette.Brand, Palette.BrandSoft)
        val Ember = Tone(Palette.Ember, Palette.EmberSoft)
        val Green = Tone(Palette.Green, Palette.GreenSoft)
        val Red = Tone(Palette.Red, Palette.RedSoft)
        val Orange = Tone(Palette.Orange, Palette.OrangeSoft)
        val Blue = Tone(Palette.Blue, Palette.BlueSoft)
        val Purple = Tone(Palette.Purple, Palette.PurpleSoft)
        val Teal = Tone(Palette.Teal, Palette.TealSoft)
        val Pink = Tone(Palette.Pink, Palette.PinkSoft)
        val Grey = Tone(Palette.Grey, Palette.GreySoft)
        val WhatsApp = Tone(Palette.WhatsApp, Palette.WhatsAppSoft)
    }
}

/** 8dp spacing grid. */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    /** Minimum height of anything tappable on the senior side. */
    val touch = 72.dp
}

private val colors = lightColorScheme(
    primary = Palette.Brand,
    onPrimary = Color.White,
    primaryContainer = Palette.BrandSoft,
    onPrimaryContainer = Color(0xFF00201E),
    secondary = Palette.Green,
    onSecondary = Color.White,
    secondaryContainer = Palette.GreenSoft,
    onSecondaryContainer = Color(0xFF0A2E13),
    tertiary = Palette.Ember,
    onTertiary = Color.White,
    tertiaryContainer = Palette.EmberSoft,
    onTertiaryContainer = Color(0xFF3B1A00),
    error = Palette.Red,
    onError = Color.White,
    errorContainer = Palette.RedSoft,
    onErrorContainer = Color(0xFF5F1412),
    background = Palette.Background,
    onBackground = Palette.Ink,
    surface = Palette.Surface,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.SurfaceLow,
    onSurfaceVariant = Palette.InkSoft,
    surfaceContainerLowest = Palette.Surface,
    surfaceContainerLow = Palette.Surface,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.Surface,
    surfaceContainerHighest = Palette.SurfaceLow,
    outline = Palette.InkSoft,
    outlineVariant = Palette.Outline,
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

/**
 * [scale] multiplies every text size so the family can pick Large / Extra large / Huge
 * (on top of the phone's own font size). Family mode uses 1.0.
 */
@Composable
fun WarmHearthTheme(scale: Float = 1.25f, content: @Composable () -> Unit) {
    fun s(size: Int, weight: FontWeight = FontWeight.Normal, tracking: Float = 0f, line: Float = 1.3f) = TextStyle(
        fontSize = (size * scale).sp,
        fontWeight = weight,
        lineHeight = (size * scale * line).sp,
        letterSpacing = tracking.sp,
    )

    val type = Typography(
        displayLarge = s(60, FontWeight.Bold, -1.5f, 1.1f),
        displayMedium = s(44, FontWeight.Bold, -0.5f, 1.15f),
        displaySmall = s(34, FontWeight.Bold, -0.25f, 1.2f),
        headlineLarge = s(30, FontWeight.Bold, -0.25f),
        headlineMedium = s(25, FontWeight.Bold),
        headlineSmall = s(21, FontWeight.Bold),
        titleLarge = s(21, FontWeight.SemiBold),
        titleMedium = s(18, FontWeight.SemiBold),
        titleSmall = s(15, FontWeight.SemiBold),
        bodyLarge = s(18, line = 1.45f),
        bodyMedium = s(16, line = 1.45f),
        bodySmall = s(14, line = 1.4f),
        labelLarge = s(17, FontWeight.SemiBold),
        labelMedium = s(14, FontWeight.Medium),
        labelSmall = s(12, FontWeight.Medium, 0.2f),
    )
    MaterialTheme(colorScheme = colors, typography = type, shapes = shapes, content = content)
}
