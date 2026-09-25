package com.levanyon.meditakip.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.levanyon.meditakip.data.BackupSummary
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w320dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackupUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun previewDoesNotRestoreWithoutExplicitConfirmationAtLargeFont() {
        var confirmed = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MediTheme("Dark") { BackupPreview(BackupSummary(1_800_000_000_000, 2, 3, 0, null, null), {}, { confirmed = true }) }
            }
        }
        assertFalse(confirmed)
        compose.onNodeWithText("2 kişi · 3 ilaç · 0 kayıt").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Geri yükle").performScrollTo().assertIsDisplayed().performClick()
        assertTrue(confirmed)
    }
    @Test fun cancellationDoesNotConfirmRestore() {
        var confirmed = false
        var cancelled = false
        compose.setContent { MediTheme("Light") { BackupPreview(BackupSummary(1_800_000_000_000, 0, 0, 0, null, null), { cancelled = true }, { confirmed = true }) } }
        compose.onNodeWithText("İptal").performScrollTo().performClick()
        assertTrue(cancelled); assertFalse(confirmed)
    }
}
