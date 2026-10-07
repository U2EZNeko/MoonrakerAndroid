package com.moonraker.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.moonraker.android.data.settings.ThemeMode
import kotlin.math.max
import kotlin.math.min

data class AccentChoice(
    val name: String,
    val color: Color,
)

/** Single accent palette (named swatches). */
val AccentChoices = listOf(
    AccentChoice("Ember", Color(0xFFFF6B4E)),
    AccentChoice("Red", Color(0xFFE53935)),
    AccentChoice("Orange", Color(0xFFFB8C00)),
    AccentChoice("Amber", Color(0xFFFFB300)),
    AccentChoice("Yellow", Color(0xFFFDD835)),
    AccentChoice("Forest", Color(0xFF43A047)),
    AccentChoice("Teal", Color(0xFF00897B)),
    AccentChoice("Cyan", Color(0xFF00ACC1)),
    AccentChoice("Ocean", Color(0xFF1E88E5)),
    AccentChoice("Violet", Color(0xFF7E57C2)),
    AccentChoice("Purple", Color(0xFF5E35B1)),
    AccentChoice("Rose", Color(0xFFD81B60)),
    AccentChoice("Brown", Color(0xFF6D4C41)),
    AccentChoice("Slate", Color(0xFF78909C)),
    AccentChoice("White", Color(0xFFFFFFFF)),
)

private fun Color.harmonizeSurface(dark: Boolean, oled: Boolean): ColorScheme {
    val accent = this
    val onAccent = if (accent.luminance() > 0.65f) Color(0xFF121212) else Color.White
    return if (dark || oled) {
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accent.copy(alpha = 0.24f),
            onPrimaryContainer = Color.White,
            secondary = accent.copy(alpha = 0.85f),
            tertiary = accent.copy(alpha = 0.7f),
            background = if (oled) Color.Black else Color(0xFF121212),
            surface = if (oled) Color(0xFF050505) else Color(0xFF1E1E1E),
            surfaceVariant = if (oled) Color(0xFF121212) else Color(0xFF2A2A2A),
            onBackground = Color(0xFFEDEDED),
            onSurface = Color(0xFFEDEDED),
            onSurfaceVariant = Color(0xFFBDBDBD),
            outline = Color(0xFF444444),
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accent.copy(alpha = 0.16f),
            onPrimaryContainer = Color(0xFF1A1A1A),
            secondary = accent.copy(alpha = 0.85f),
            tertiary = accent.copy(alpha = 0.7f),
            background = Color(0xFFF7F4F1),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFEFEAE6),
            onBackground = Color(0xFF1A1A1A),
            onSurface = Color(0xFF1A1A1A),
            onSurfaceVariant = Color(0xFF5C5C5C),
            outline = Color(0xFFC9C2BB),
        )
    }
}

private fun Color.luminance(): Float =
    0.2126f * red + 0.7152f * green + 0.0722f * blue

fun Long.toComposeColor(): Color = Color(this.toInt())

fun Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

@Composable
fun MoonrakerTheme(
    themeMode: ThemeMode,
    accentArgb: Long,
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.OLED -> true
    }
    val oled = themeMode == ThemeMode.OLED
    val accent = remember(accentArgb) { accentArgb.toComposeColor() }
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme = remember(dark, oled, accent, useDynamicColor, dynamicAvailable) {
        if (useDynamicColor && dynamicAvailable) {
            val dynamic = if (dark || oled) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
            if (oled) {
                dynamic.copy(
                    background = Color.Black,
                    surface = Color(0xFF050505),
                    surfaceVariant = Color(0xFF121212),
                )
            } else {
                dynamic
            }
        } else {
            accent.harmonizeSurface(dark, oled)
        }
    }

    MaterialTheme(
        colorScheme = scheme,
        content = content,
    )
}

fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)
    val c = v * s
    val x = c * (1 - kotlin.math.abs((h / 60f) % 2 - 1))
    val m = v - c
    val (r1, g1, b1) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        red = (r1 + m).coerceIn(0f, 1f),
        green = (g1 + m).coerceIn(0f, 1f),
        blue = (b1 + m).coerceIn(0f, 1f),
    )
}

fun colorToHsv(color: Color): FloatArray {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = max(r, max(g, b))
    val min = min(r, min(g, b))
    val delta = max - min
    val hue = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0) it + 360f else it }
    val sat = if (max == 0f) 0f else delta / max
    return floatArrayOf(hue, sat, max)
}
