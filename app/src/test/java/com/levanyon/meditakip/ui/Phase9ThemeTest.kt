package com.levanyon.meditakip.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class Phase9ThemeTest {
    @Test fun exactApprovedLightRoles() {
        val c = LightColors
        val actual = listOf(c.background, c.surface, c.surfaceVariant, c.primary, c.primaryContainer, c.secondary, c.secondaryContainer,
            c.onSurface, c.onSurfaceVariant, c.outline, LightCareColors.success, LightCareColors.successContainer,
            c.tertiary, c.tertiaryContainer, c.error, c.errorContainer, c.onPrimary, c.surfaceContainer, LightCareColors.progressTrack, LightCareColors.accentSoft)
        val expected = listOf(0xFFF7F4F0,0xFFFFFFFF,0xFFECE7E1,0xFF5E4AE3,0xFFEAE6FF,0xFFE86F51,0xFFFFE7DF,
            0xFF1A1720,0xFF706A79,0xFFD8D0D9,0xFF2E8B67,0xFFDDF4EA,0xFFB36E12,0xFFFFF0D7,0xFFB44747,0xFFFFE2E1,
            0xFFFFFFFF,0xFFF0ECE7,0xFFDDD6E2,0xFFF3EEFF).map { Color(it) }
        assertEquals(expected, actual)
    }
    @Test fun exactApprovedDarkRoles() {
        val c = DarkColors
        val actual = listOf(c.background, c.surface, c.surfaceVariant, c.primary, c.primaryContainer, c.secondary, c.secondaryContainer,
            c.onSurface, c.onSurfaceVariant, c.outline, DarkCareColors.success, DarkCareColors.successContainer,
            c.tertiary, c.tertiaryContainer, c.error, c.errorContainer, c.onPrimary, c.surfaceContainer, DarkCareColors.progressTrack, DarkCareColors.accentSoft)
        val expected = listOf(0xFF121017,0xFF1B1822,0xFF24212B,0xFFB7A7FF,0xFF342C63,0xFFFFAB93,0xFF5A2D23,
            0xFFF6F2F8,0xFFAEA7B4,0xFF413A48,0xFF76D4A8,0xFF173C30,0xFFF1BC6D,0xFF4A351A,0xFFFFA0A0,0xFF512626,
            0xFF1E143E,0xFF1A1720,0xFF3A3440,0xFF29233D).map { Color(it) }
        assertEquals(expected, actual)
    }
    @Test fun successTextHasAaContrastInBothThemes() {
        for (c in listOf(LightCareColors, DarkCareColors)) {
            val light = maxOf(c.successInk.luminance(), c.successContainer.luminance())
            val dark = minOf(c.successInk.luminance(), c.successContainer.luminance())
            assertTrue((light + .05) / (dark + .05) >= 4.5)
        }
    }
}
