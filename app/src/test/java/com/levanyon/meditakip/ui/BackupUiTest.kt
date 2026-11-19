package com.levanyon.meditakip.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
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
        compose.onNodeWithText("Vazgeç").performScrollTo().performClick()
        assertTrue(cancelled); assertFalse(confirmed)
    }
    @Test fun exportRejectsShortAndMismatchedPasswordsThenAcceptsUnicodeSpaces() {
        var submitted: CharArray? = null
        compose.setContent { MediTheme("Light") { BackupPasswordDialog(true, {}, { submitted = it }) } }
        fun submit() = compose.onNode(hasText("Yedeği şifrele") and hasClickAction()).performScrollTo().performClick()
        submit(); assertNull(submitted)
        compose.onNodeWithText("Parola 12–1024 karakter olmalı.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Yedek parolası").performScrollTo().performTextInput("uzun parola İstanbul")
        compose.onNodeWithText("Parolayı tekrar gir").performScrollTo().performTextInput("farklı uzun parola")
        submit(); assertNull(submitted)
        compose.onNodeWithText("Parolalar eşleşmiyor.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Parolayı tekrar gir").performScrollTo().performTextReplacement("uzun parola İstanbul")
        submit(); assertArrayEquals("uzun parola İstanbul".toCharArray(), submitted)
        submitted!!.fill('\u0000')
    }
    @Test fun restorePasswordCanBeShownHiddenAndCancelledWithoutSubmission() {
        var submitted = false; var cancelled = false
        compose.setContent { MediTheme("Dark") { BackupPasswordDialog(false, { cancelled = true }, { submitted = true; it.fill('\u0000') }) } }
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("test password")
        compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        compose.onNodeWithText("Parolayı göster").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Password))
        compose.onNodeWithText("Parolayı gizle").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        compose.onNodeWithText("İptal").performScrollTo().performClick()
        assertTrue(cancelled); assertFalse(submitted)
    }
    @Test fun restoreRequestsOnlyOnePasswordAndDoesNotImposeNewExportLengthRule() {
        var submitted: CharArray? = null
        compose.setContent { MediTheme("Light") { BackupPasswordDialog(false, {}, { submitted = it }) } }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        compose.onNodeWithText("Bu yedek şifreli.", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("wrong")
        compose.onNodeWithText("Yedeği aç").performScrollTo().performClick()
        assertArrayEquals("wrong".toCharArray(), submitted); submitted!!.fill('\u0000')
    }
    @Test fun legacyWarningIsShownBeforeConfirmation() {
        compose.setContent { MediTheme("Light") { BackupPreview(BackupSummary(1_800_000_000_000, 1, 1, 0, null, null), {}, {}, legacyUnencrypted = true) } }
        compose.onNodeWithText("Bu eski yedek şifrelenmemiş.", substring = true).performScrollTo().assertIsDisplayed()
    }
}
