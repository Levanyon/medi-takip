package com.levanyon.meditakip.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.view.ContextThemeWrapper

@Composable
fun rememberPickerContext(): Context {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return remember(context, dark) { ContextThemeWrapper(context, if (dark) android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert) }
}

private val Light = lightColorScheme(
    primary = Color(0xFF20695D), onPrimary = Color.White,
    primaryContainer = Color(0xFFD9EEE6), onPrimaryContainer = Color(0xFF154B40),
    secondary = Color(0xFF536B61), secondaryContainer = Color(0xFFE4EEE5), onSecondaryContainer = Color(0xFF2C473A),
    tertiary = Color(0xFF795B31), tertiaryContainer = Color(0xFFF7E9D3), onTertiaryContainer = Color(0xFF584223),
    background = Color(0xFFF7F8F4), onBackground = Color(0xFF222E29),
    surface = Color(0xFFFCFDF9), onSurface = Color(0xFF222E29),
    surfaceContainer = Color(0xFFEDF1EB), surfaceContainerLow = Color(0xFFF2F5EF),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFE6ECE4), surfaceContainerHighest = Color(0xFFDFE6DC),
    surfaceVariant = Color(0xFFE4EAE2), onSurfaceVariant = Color(0xFF4D5D54), outlineVariant = Color(0xFFCDD8CE)
)
private val Dark = darkColorScheme(
    primary = Color(0xFF9DD6C2), onPrimary = Color(0xFF063B2D),
    primaryContainer = Color(0xFF234E40), onPrimaryContainer = Color(0xFFD6F2E4),
    secondary = Color(0xFFB5CCBE), secondaryContainer = Color(0xFF30483B), onSecondaryContainer = Color(0xFFD7ECDD),
    tertiary = Color(0xFFE7C494), tertiaryContainer = Color(0xFF514128), onTertiaryContainer = Color(0xFFFFE8C7),
    background = Color(0xFF121B17), onBackground = Color(0xFFE2EBE1),
    surface = Color(0xFF17211B), onSurface = Color(0xFFE2EBE1),
    surfaceContainer = Color(0xFF243027), surfaceContainerLow = Color(0xFF1C271F),
    surfaceContainerLowest = Color(0xFF0D1510), surfaceContainerHigh = Color(0xFF2B382E), surfaceContainerHighest = Color(0xFF344137),
    surfaceVariant = Color(0xFF354339), onSurfaceVariant = Color(0xFFBDCCBF), outlineVariant = Color(0xFF435447)
)

@Composable
fun MediTheme(theme: String, content: @Composable () -> Unit) {
    val dark = theme == "Dark" || (theme == "System" && isSystemInDarkTheme())
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(36.dp)),
        typography = Typography(
            headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
            headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
            headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
            titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 26.sp),
            titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
            titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 28.sp),
            bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 25.sp),
            bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
            labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
            labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
            labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp)
        ), content = content
    )
}
