package com.levanyon.meditakip.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG 2.1 AA contrast for the colour pairs the app actually uses (both themes):
 * 4.5:1 for text, 3:1 for meaningful non-text marks (status icons).
 */
class ContrastTest {
    private fun ratio(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance().toDouble(), b.luminance().toDouble()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }
    private fun ink(c: ColorScheme) = if (c == LightColors) LightCareColors.secondaryInk else DarkCareColors.secondaryInk
    private fun textPairs(c: ColorScheme) = mapOf(
        "onBackground/background" to (c.onBackground to c.background),
        "onSurface/surfaceContainerLow (rows, cards)" to (c.onSurface to c.surfaceContainerLow),
        "onSurfaceVariant/background (subtitles)" to (ink(c) to c.background),
        "onSurfaceVariant/surfaceContainerLow (secondary text in rows)" to (ink(c) to c.surfaceContainerLow),
        "onSurfaceVariant/surfaceContainerHigh (chips, selector)" to (ink(c) to c.surfaceContainerHigh),
        "onSurface/surfaceContainerHigh (selector labels)" to (c.onSurface to c.surfaceContainerHigh),
        "primary/background (text buttons, links)" to (c.primary to c.background),
        "primary/surfaceContainerLow (quiet actions in rows)" to (c.primary to c.surfaceContainerLow),
        "primary/surfaceContainer (selected nav label)" to (c.primary to c.surfaceContainer),
        "onSurfaceVariant/surfaceContainer (nav labels)" to (ink(c) to c.surfaceContainer),
        "onPrimary/primary (filled buttons)" to (c.onPrimary to c.primary),
        "onPrimaryContainer/primaryContainer (highlighted dose, chips)" to (c.onPrimaryContainer to c.primaryContainer),
        "onSecondaryContainer/secondaryContainer (tonal buttons)" to (c.onSecondaryContainer to c.secondaryContainer),
        "onTertiaryContainer/tertiaryContainer (warnings)" to (c.onTertiaryContainer to c.tertiaryContainer),
        "onTertiaryContainer/surfaceContainerLow (permission status text)" to (c.onTertiaryContainer to c.surfaceContainerLow),
        "error/background" to (c.error to c.background),
        "error/surfaceContainerLow (dialog errors)" to (c.error to c.surfaceContainerLow),
        "onError/error" to (c.onError to c.error),
        "onErrorContainer/errorContainer" to (c.onErrorContainer to c.errorContainer),
        "onSecondary/secondary" to (c.onSecondary to c.secondary),
        "onTertiary/tertiary" to (c.onTertiary to c.tertiary),
        "onSurface/surface" to (c.onSurface to c.surface),
        "onSurface/surfaceContainerHighest" to (c.onSurface to c.surfaceContainerHighest),
        "onSurfaceVariant/primaryContainer (highlighted notes)" to (ink(c) to c.primaryContainer),
        "onSurfaceVariant/surfaceContainerLowest" to (ink(c) to c.surfaceContainerLowest),
        "inverseOnSurface/inverseSurface (snackbar)" to (c.inverseOnSurface to c.inverseSurface),
        "inversePrimary/inverseSurface (snackbar action)" to (c.inversePrimary to c.inverseSurface),
    )
    private fun iconPairs(c: ColorScheme) = mapOf(
        "secondaryInk/surfaceContainerLow (pending marker)" to (ink(c) to c.surfaceContainerLow),
        "secondaryInk/background (pending marker)" to (ink(c) to c.background),
        "tertiary/background (overdue marker)" to (c.tertiary to c.background),
        "primary/background (taken marker)" to (c.primary to c.background),
    )
    private fun check(name: String, scheme: ColorScheme) {
        val failures = textPairs(scheme).mapNotNull { (label, pair) -> ratio(pair.first, pair.second).takeIf { it < 4.5 }?.let { "$label = %.2f".format(it) } } +
            iconPairs(scheme).mapNotNull { (label, pair) -> ratio(pair.first, pair.second).takeIf { it < 3.0 }?.let { "$label = %.2f".format(it) } }
        assertTrue("$name theme below WCAG AA: $failures", failures.isEmpty())
    }
    @Test fun lightThemeMeetsWcagAa() = check("Light", LightColors)
    @Test fun darkThemeMeetsWcagAa() = check("Dark", DarkColors)
}
