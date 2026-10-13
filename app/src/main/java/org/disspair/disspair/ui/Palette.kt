package org.disspair.disspair.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/** How the app picks light or dark colours. */
enum class ThemeMode(val label: String) {
    SYSTEM("System"), LIGHT("Light"), DARK("Dark");

    fun next(): ThemeMode = entries[(ordinal + 1) % entries.size]
}

/**
 * Semantic colours used across the app.
 *
 * Button container colours (blue, purple, green, red...) are intentionally NOT here:
 * they carry white labels and read well on both light and dark backgrounds.
 */
data class DissColors(
    val isDark: Boolean,
    // Surfaces
    val background: Color,   // main screen
    val overlay: Color,      // full-screen analysis overlays
    val surface: Color,      // dialogs, device cards
    val surfaceAlt: Color,   // characteristic / channel rows
    val console: Color,      // log terminal boxes
    val border: Color,
    val neutral: Color,      // neutral buttons and dividers
    // Text
    val text: Color,
    val textStrong: Color,   // MAC addresses
    val textSecondary: Color,
    val textSubtle: Color,
    val textMuted: Color,
    // Accents used as text / icon colours
    val green: Color,
    val cyan: Color,
    val red: Color,
    val danger: Color,
    val orange: Color,
    val amber: Color,
    val slate: Color,
)

/** The original DissPair look: identical values to the pre-theming hard-coded colours. */
val DarkColors = DissColors(
    isDark = true,
    background = Color(0xFF0A0A0F),
    overlay = Color(0xFA000000),
    surface = Color(0xFF171721),
    surfaceAlt = Color(0xFF0F0F16),
    console = Color(0xFF07070A),
    border = Color(0xFF1A1A24),
    neutral = Color.DarkGray,
    text = Color.White,
    textStrong = Color(0xFFEEEEEE),
    textSecondary = Color.Gray,
    textSubtle = Color(0xFF8A8A9E),
    textMuted = Color(0xFF44445A),
    green = Color(0xFF39FF14),
    cyan = Color(0xFF00E5FF),
    red = Color(0xFFFF2D55),
    danger = Color(0xFFFC1037),
    orange = Color(0xFFFFA500),
    amber = Color(0xFFFFB000),
    slate = Color(0xFF7A7A9A),
)

/** Light variant: neon accents are darkened so they stay readable on white. */
val LightColors = DissColors(
    isDark = false,
    background = Color(0xFFF6F7FB),
    overlay = Color(0xFAF6F7FB),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFECEEF4),
    console = Color(0xFFFFFFFF),
    border = Color(0xFFD5D8E0),
    neutral = Color(0xFF6B6B78),
    text = Color(0xFF15151C),
    textStrong = Color(0xFF2A2A33),
    textSecondary = Color(0xFF5A5A6A),
    textSubtle = Color(0xFF6A6A80),
    textMuted = Color(0xFF6A6A82),
    green = Color(0xFF187009),
    cyan = Color(0xFF006878),
    red = Color(0xFFD0103F),
    danger = Color(0xFFC2002B),
    orange = Color(0xFF9E5200),
    amber = Color(0xFF875800),
    slate = Color(0xFF5E5E78),
)

/**
 * Current palette. Backed by Compose state, so any composable that reads [c]
 * recomposes when the theme changes. Also readable from non-composable helpers.
 */
object Pal {
    var c: DissColors by mutableStateOf(DarkColors)

    // Dark accent -> its light-theme equivalent, for colours that arrive as raw values
    // (e.g. the "39FF14" hex strings used throughout the log calls).
    private val accentMap: Map<Int, (DissColors) -> Color> = mapOf(
        DarkColors.green.toArgb() to { it.green },
        DarkColors.cyan.toArgb() to { it.cyan },
        DarkColors.red.toArgb() to { it.red },
        DarkColors.danger.toArgb() to { it.danger },
        DarkColors.orange.toArgb() to { it.orange },
        DarkColors.amber.toArgb() to { it.amber },
        DarkColors.slate.toArgb() to { it.slate },
    )

    /** Returns the colour to use for an accent written with dark-theme values. */
    fun adapt(color: Color): Color {
        if (c.isDark) return color
        return accentMap[color.toArgb()]?.invoke(c) ?: color
    }
}

/** Persists the user's theme choice. */
object ThemePrefs {
    private const val FILE = "disspair_prefs"
    private const val KEY = "theme_mode"

    fun load(ctx: Context): ThemeMode =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM

    fun save(ctx: Context, mode: ThemeMode) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, mode.name).apply()
    }
}
