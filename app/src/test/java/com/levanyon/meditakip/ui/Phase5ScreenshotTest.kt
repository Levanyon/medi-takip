package com.levanyon.meditakip.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.levanyon.meditakip.data.*
import org.junit.*
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowDialog
import java.io.File

/** Real native Compose render artifacts and semantics checks, not pixel-baseline assertions. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w390dp-h844dp-mdpi")
class Phase5ScreenshotTest(private val screen: String, private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-dark={1}")
        fun cases() = listOf("history", "historyEmpty", "historyLongNames", "historyNarrow", "historyLargeFont", "filter", "filterLargeFont",
            "settings", "settingsAttention", "settingsNarrow", "settingsLargeFont", "restore", "restoreLargeFont",
            "history360", "history412", "historyTablet", "settings360", "settings412", "settingsTablet",
            "passwordExport", "passwordExportLargeFont", "passwordRestore", "passwordRestoreLargeFont",
            "restoreLegacy", "restoreLegacyLargeFont")
            .flatMap { listOf(arrayOf<Any>(it, false), arrayOf<Any>(it, true)) }
    }
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun prepare() {
        Assume.assumeTrue(System.getenv("MEDITAKIP_SCREENSHOTS") != null)
        RuntimeEnvironment.setFontScale(if (screen.endsWith("LargeFont")) 2f else 1f)
        when {
            screen.endsWith("Narrow") || screen.endsWith("LargeFont") -> RuntimeEnvironment.setQualifiers("w320dp-h800dp-mdpi")
            screen.endsWith("360") -> RuntimeEnvironment.setQualifiers("w360dp-h800dp-mdpi")
            screen.endsWith("412") -> RuntimeEnvironment.setQualifiers("w412dp-h900dp-mdpi")
            screen.endsWith("Tablet") -> RuntimeEnvironment.setQualifiers("w1200dp-h900dp-mdpi")
        }
    }
    @After fun restoreFontScale() {
        // RuntimeEnvironment also changes system resources; do not leak the render configuration
        // into later tests that perform pointer input using their own window configuration.
        RuntimeEnvironment.setFontScale(1f)
    }
    @Test fun renderAndCheckAccessibility() {
        val data = Phase5TestData
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, if (screen.endsWith("LargeFont")) 2f else 1f)) {
                MediTheme(if (dark) "Dark" else "Light") {
                    Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = {
                        MediNavigationBar(listOf("Bugün", "Kişiler", "Geçmiş", "Ayarlar"),
                            listOf(Icons.Default.Today, Icons.Default.People, Icons.Default.CalendarMonth, Icons.Default.Settings),
                            if (screen.startsWith("settings") || screen.startsWith("restore") || screen.startsWith("password")) 3 else 2) {}
                    }) { padding ->
                        MediContent(Modifier.fillMaxSize().padding(padding)) {
                            when {
                                screen.startsWith("settings") || screen.startsWith("restore") || screen.startsWith("password") -> SettingsContent(AppSettings(), data.access(exact = screen != "settingsAttention"),
                                    "1.2-debug", {}, {}) { BackupRows(false, {}, {}) }
                                else -> HistoryScreen(when (screen) {
                                    "historyEmpty" -> data.snapshot.copy(logs = emptyList())
                                    "historyLongNames", "historyLargeFont" -> data.longNames
                                    else -> data.snapshot
                                }, data.today, null, {}, {})
                            }
                        }
                        if (screen.startsWith("filter")) HistoryFilterSheet(HistoryFilter(), {}, {})
                        if (screen.startsWith("restore")) BackupPreview(BackupSummary(1_800_000_000_000, 2, 3, 5, data.today.minusDays(5), data.today), {}, {}, screen.startsWith("restoreLegacy"))
                        if (screen.startsWith("password")) BackupPasswordDialog(screen.startsWith("passwordExport"), {}, { it.fill('\u0000') })
                    }
                }
            }
        }
        save(screen)
        if (screen.startsWith("settings")) {
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("MediTakip hakkında"))
            compose.onNodeWithText("MediTakip hakkında").performScrollTo().assertIsDisplayed()
            save("$screen-info")
        }
        if (screen.startsWith("restore")) {
            compose.onNodeWithText("Geri yükle").performScrollTo().assertIsDisplayed()
            save("$screen-actions")
            compose.onNodeWithText("Vazgeç").performScrollTo().assertIsDisplayed()
        }
        if (screen.startsWith("filter")) {
            compose.onNodeWithText("Filtreyi uygula").performScrollTo().assertIsDisplayed()
            save("$screen-apply")
        }
        if (screen.startsWith("history")) {
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Planlanan dozlar"))
            compose.onNodeWithText("Planlanan dozlar").assertIsDisplayed()
            save("$screen-bottom")
        }
        if (screen.startsWith("password")) {
            compose.onNodeWithText("İptal").performScrollTo().assertIsDisplayed()
            save("$screen-actions")
        }
        if (!screen.startsWith("filter") && !screen.startsWith("restore") && !screen.startsWith("password")) {
            compose.onNode(hasText("Ayarlar") and isSelectable()).performScrollTo().assertIsDisplayed()
        }
    }
    private fun save(name: String) {
        compose.waitForIdle()
        compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach { node ->
            val label = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty() +
                node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty()
            assertTrue("Unlabelled control: ${node.boundsInRoot}", label.isNotBlank())
            val density = node.layoutInfo.density.density
            assertTrue("Touch width: $label", node.size.width / density >= 47.5f)
            assertTrue("Touch height: $label", node.size.height / density >= 47.5f)
        }
        val dialog = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }
        val view = compose.activity.window.decorView
        assertTrue(view.width > 0 && view.height > 0)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        dialog?.window?.decorView?.let { overlay ->
            // Dialog windows are separate native roots. Composite them over the activity, including
            // the platform window dim amount, rather than exporting transparent pixels as black.
            val amount = dialog.window?.attributes?.dimAmount ?: 0f
            canvas.drawColor(android.graphics.Color.argb((amount * 255).toInt(), 0, 0, 0))
            canvas.save()
            canvas.translate((view.width - overlay.width) / 2f, (view.height - overlay.height) / 2f)
            overlay.draw(canvas)
            canvas.restore()
        }
        val output = File("build/screenshots/phase5").apply { mkdirs() }
        File(output, "$name-${if (dark) "dark" else "light"}.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}
