package com.levanyon.meditakip.ui

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Additional semantic roles absent from Material's ColorScheme; ink roles preserve text contrast. */
data class CareColors(val success: Color, val successContainer: Color, val successInk: Color,
    val progressTrack: Color, val accentSoft: Color, val secondaryInk: Color)
internal val LightCareColors = CareColors(Color(0xFF2E8B67), Color(0xFFDDF4EA), Color(0xFF206348), Color(0xFFDDD6E2), Color(0xFFF3EEFF), Color(0xFF625C69))
internal val DarkCareColors = CareColors(Color(0xFF76D4A8), Color(0xFF173C30), Color(0xFF76D4A8), Color(0xFF3A3440), Color(0xFF29233D), Color(0xFFAEA7B4))
val careColors: CareColors
    @Composable get() = if (MaterialTheme.colorScheme.background.luminance() < .5f) DarkCareColors else LightCareColors

@Composable
fun rememberPickerContext(): Context {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return remember(context, dark) {
        ContextThemeWrapper(
            context,
            if (dark) android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert
        )
    }
}

/**
 * MediTakip V3 — Human Care. Wallpaper colors never override these approved roles.
 */
internal val LightColors = lightColorScheme(
    primary = Color(0xFF5E4AE3),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEAE6FF),
    onPrimaryContainer = Color(0xFF342C63),

    secondary = Color(0xFFE86F51),
    onSecondary = Color(0xFF1A1720),
    secondaryContainer = Color(0xFFFFE7DF),
    onSecondaryContainer = Color(0xFF5A2D23),

    tertiary = Color(0xFFB36E12),
    onTertiary = Color(0xFF121017),
    tertiaryContainer = Color(0xFFFFF0D7),
    onTertiaryContainer = Color(0xFF704000),

    background = Color(0xFFF7F4F0),
    onBackground = Color(0xFF1A1720),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1720),

    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF0ECE7),
    surfaceContainerHigh = Color(0xFFECE7E1),
    surfaceContainerHighest = Color(0xFFECE7E1),

    surfaceVariant = Color(0xFFECE7E1),
    onSurfaceVariant = Color(0xFF706A79),
    outline = Color(0xFFD8D0D9),
    outlineVariant = Color(0xFFD8D0D9),

    surfaceTint = Color(0xFF5E4AE3),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFECE7E1),
    inverseSurface = Color(0xFF24212B),
    inverseOnSurface = Color(0xFFF6F2F8),
    inversePrimary = Color(0xFFB7A7FF),
    scrim = Color(0xFF000000),

    error = Color(0xFFB44747),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE2E1),
    onErrorContainer = Color(0xFF512626)
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFB7A7FF),
    onPrimary = Color(0xFF1E143E),
    primaryContainer = Color(0xFF342C63),
    onPrimaryContainer = Color(0xFFEAE6FF),

    secondary = Color(0xFFFFAB93),
    onSecondary = Color(0xFF1A1720),
    secondaryContainer = Color(0xFF5A2D23),
    onSecondaryContainer = Color(0xFFFFE7DF),

    tertiary = Color(0xFFF1BC6D),
    onTertiary = Color(0xFF1A1720),
    tertiaryContainer = Color(0xFF4A351A),
    onTertiaryContainer = Color(0xFFF1BC6D),

    background = Color(0xFF121017),
    onBackground = Color(0xFFF6F2F8),
    surface = Color(0xFF1B1822),
    onSurface = Color(0xFFF6F2F8),

    surfaceContainerLowest = Color(0xFF121017),
    surfaceContainerLow = Color(0xFF1B1822),
    surfaceContainer = Color(0xFF1A1720),
    surfaceContainerHigh = Color(0xFF24212B),
    surfaceContainerHighest = Color(0xFF24212B),

    surfaceVariant = Color(0xFF24212B),
    onSurfaceVariant = Color(0xFFAEA7B4),
    outline = Color(0xFF413A48),
    outlineVariant = Color(0xFF413A48),

    surfaceTint = Color(0xFFB7A7FF),
    surfaceBright = Color(0xFF24212B),
    surfaceDim = Color(0xFF121017),
    inverseSurface = Color(0xFFF6F2F8),
    inverseOnSurface = Color(0xFF1A1720),
    inversePrimary = Color(0xFF5E4AE3),
    scrim = Color(0xFF000000),

    error = Color(0xFFFFA0A0),
    onError = Color(0xFF512626),
    errorContainer = Color(0xFF512626),
    onErrorContainer = Color(0xFFFFE2E1)
)

@Composable
fun MediTheme(theme: String, content: @Composable () -> Unit) {
    val dark = theme == "Dark" || (theme == "System" && isSystemInDarkTheme())
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        shapes = MediShapes,
        typography = Typography(
            headlineLarge = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                lineHeight = 38.sp,
                letterSpacing = (-0.4).sp
            ),
            headlineMedium = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                lineHeight = 32.sp,
                letterSpacing = (-0.25).sp
            ),
            headlineSmall = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                lineHeight = 26.sp,
                // Scheduled times use headlineSmall: stable digit widths aid scanning.
                fontFeatureSettings = "tnum"
            ),
            titleLarge = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                lineHeight = 26.sp
            ),
            titleMedium = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 24.sp
            ),
            titleSmall = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 24.sp
            ),
            bodyLarge = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 16.sp,
                lineHeight = 24.sp
            ),
            bodyMedium = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 16.sp,
                lineHeight = 24.sp
            ),
            bodySmall = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 14.sp,
                lineHeight = 20.sp
            ),
            labelLarge = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp
            ),
            labelMedium = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp
            ),
            labelSmall = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        ),
        content = content
    )
}
