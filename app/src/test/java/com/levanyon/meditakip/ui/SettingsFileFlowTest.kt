package com.levanyon.meditakip.ui

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.viewmodel.compose.viewModel
import com.levanyon.meditakip.MediApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Verify the existing Settings rows retain local-document contracts and encrypted-export guidance. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = MediApp::class, qualifiers = "w390dp-h844dp-mdpi")
class SettingsFileFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @After fun close() {
        val app = RuntimeEnvironment.getApplication() as MediApp
        runBlocking { app.reminders.scope.coroutineContext[Job]?.cancelAndJoin() }
        app.database.close()
    }
    private fun show() { compose.setContent { MediTheme("Light") { SettingsScreen(viewModel()) } } }
    private fun clickRow(text: String) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
        compose.onNodeWithText(text).performClick()
    }
    @Test fun exportPreservesPrivacyGuidanceAndLocalEncryptedContract() {
        show(); clickRow("Yedek oluştur")
        compose.onNodeWithText("Yedek sağlık bilgilerinizi içerir ve parolanızla şifrelenir.", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Yedek Oluştur").performScrollTo().performClick()
        val intent = shadowOf(compose.activity).nextStartedActivityForResult.intent
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals("application/octet-stream", intent.type)
        assertTrue(intent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false))
        assertTrue(intent.getStringExtra(Intent.EXTRA_TITLE)!!.endsWith(".medibackup"))
    }
    @Test fun restoreOpensExistingLocalPickerWithoutRestoring() {
        show(); clickRow("Yedekten geri yükle")
        val intent = shadowOf(compose.activity).nextStartedActivityForResult.intent
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertTrue(intent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false))
        assertTrue(intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)!!.contains("application/json"))
        compose.onNodeWithText("Geri yükle").assertDoesNotExist()
    }
    @Test fun backupDetailsCanBeCancelledBeforeOpeningPicker() {
        show(); clickRow("Yedek oluştur")
        compose.onNodeWithText("İptal").performScrollTo().performClick()
        assertNull(shadowOf(compose.activity).nextStartedActivityForResult)
    }
}
