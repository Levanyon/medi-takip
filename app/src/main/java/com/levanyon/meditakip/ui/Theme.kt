package com.levanyon.meditakip.ui

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
 * MediTakip V2 — Quiet Luxury Healthcare.
 *
 * Warm neutral surfaces keep the app calm while deep petrol and jade accents make
 * important actions feel intentional. The palette deliberately avoids bright
 * hospital-blue tones and keeps contrast high for everyday use.
 */
internal val LightColors = lightColorScheme(
    primary = Color(0xFF0B5A4C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6EEE6),
    onPrimaryContainer = Color(0xFF073C33),

    secondary = Color(0xFF52665E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3ECE7),
    onSecondaryContainer = Color(0xFF2D443B),

    tertiary = Color(0xFF8A6338),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF4E7D4),
    onTertiaryContainer = Color(0xFF59401F),

    background = Color(0xFFF5F7F3),
    onBackground = Color(0xFF18231F),
    surface = Color(0xFFFAFBF8),
    onSurface = Color(0xFF18231F),

    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F4EF),
    surfaceContainer = Color(0xFFE9EEE9),
    surfaceContainerHigh = Color(0xFFE2E8E3),
    surfaceContainerHighest = Color(0xFFDBE3DD),

    surfaceVariant = Color(0xFFE3E9E5),
    onSurfaceVariant = Color(0xFF53615B),
    outline = Color(0xFF7A8882),
    outlineVariant = Color(0xFFC9D2CD),

    error = Color(0xFFBA4B4B),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD8),
    onErrorContainer = Color(0xFF410006)
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD7BE),
    onPrimary = Color(0xFF053A30),
    primaryContainer = Color(0xFF174A3F),
    onPrimaryContainer = Color(0xFFD5F3E7),

    secondary = Color(0xFFB4CAC0),
    onSecondary = Color(0xFF20362E),
    secondaryContainer = Color(0xFF2A4037),
    onSecondaryContainer = Color(0xFFD6E9E0),

    tertiary = Color(0xFFE4C18F),
    onTertiary = Color(0xFF402D12),
    tertiaryContainer = Color(0xFF503D25),
    onTertiaryContainer = Color(0xFFFFE6C3),

    background = Color(0xFF0D1512),
    onBackground = Color(0xFFE0E9E4),
    surface = Color(0xFF111B17),
    onSurface = Color(0xFFE0E9E4),

    surfaceContainerLowest = Color(0xFF09100D),
    surfaceContainerLow = Color(0xFF17211D),
    surfaceContainer = Color(0xFF1D2924),
    surfaceContainerHigh = Color(0xFF25322C),
    surfaceContainerHighest = Color(0xFF2D3B34),

    surfaceVariant = Color(0xFF33413A),
    onSurfaceVariant = Color(0xFFBCCAC3),
    outline = Color(0xFF87968E),
    outlineVariant = Color(0xFF405149),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun MediTheme(theme: String, content: @Composable () -> Unit) {
    val dark = theme == "Dark" || (theme == "System" && isSystemInDarkTheme())
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(10.dp),
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(26.dp),
            extraLarge = RoundedCornerShape(32.dp)
        ),
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
                fontSize = 28.sp,
                lineHeight = 34.sp,
                letterSpacing = (-0.25).sp
            ),
            headlineSmall = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 23.sp,
                lineHeight = 29.sp
            ),
            titleLarge = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                lineHeight = 26.sp
            ),
            titleMedium = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                lineHeight = 24.sp
            ),
            titleSmall = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                lineHeight = 22.sp
            ),
            bodyLarge = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 16.sp,
                lineHeight = 24.sp
            ),
            bodyMedium = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 15.sp,
                lineHeight = 22.sp
            ),
            bodySmall = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 13.sp,
                lineHeight = 19.sp
            ),
            labelLarge = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                lineHeight = 20.sp
            ),
            labelMedium = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                lineHeight = 18.sp
            ),
            labelSmall = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        ),
        content = content
    )
}
