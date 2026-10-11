package com.levanyon.meditakip.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.levanyon.meditakip.data.*
import com.levanyon.meditakip.domain.Snapshot
import org.junit.*
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import java.io.File
import java.time.LocalDate

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w390dp-h844dp-mdpi")
class Phase9ScreenshotTest(private val dark: Boolean, private val width: Int, private val font: Float) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}-w{1}-font{2}")
        fun cases() = listOf(390 to 1f, 320 to 2f, 1200 to 1f).flatMap { (width, font) -> listOf(false, true).map { arrayOf<Any>(it, width, font) } }
    }
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var qualifiers = ""
    private var originalFont = 1f
    @Before fun setup() {
        qualifiers = RuntimeEnvironment.getQualifiers()
        originalFont = RuntimeEnvironment.getApplication().resources.configuration.fontScale
        RuntimeEnvironment.setQualifiers("w${width}dp-h844dp-mdpi"); RuntimeEnvironment.setFontScale(font)
    }
    @After fun reset() { RuntimeEnvironment.setQualifiers(qualifiers); RuntimeEnvironment.setFontScale(originalFont) }
    @Test fun lightDarkLongTextAndAccessibleControls() {
        val today = LocalDate.now().toEpochDay()
        val suffix = if (font > 1f) " çok uzun bir isim ve açıklama".repeat(3) else ""
        val snapshot = Snapshot(listOf(PersonEntity("p", "Anne$suffix")), listOf(MedicationEntity("m", "p")),
            listOf(MedicationScheduleEntity("s", "m", "old", "Beloc ZOK 50 mg$suffix", "1 tablet", "", "21:00", 127, today, null, true, today)))
        compose.setContent {
            var value by remember { mutableStateOf(LockScreenSettings()) }
            CompositionLocalProvider(LocalDensity provides Density(1f, font)) {
                MediTheme(if (dark) "Dark" else "Light") { Surface { MediContent {
                    LockScreenSettingsContent(value, snapshot, { value = it }, {})
                } } }
            }
        }
        save("private")
        for (label in listOf("Kilit ekranında göster", "Gizli", "Ayrıntılı", "Sıradaki dozu sabitle", "Kilit ekranında ‘İçildi’")) {
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label))
            compose.onNodeWithText(label).assertIsDisplayed().assertHeightIsAtLeast(MediSize.touch)
            checkTargets()
        }
        save("actions")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Ayrıntılı"))
        compose.onNodeWithText("Ayrıntılı").performClick().assertIsSelected()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithText("Beloc ZOK 50 mg$suffix").assertExists()
        save("detailed")
    }
    private fun checkTargets() {
        compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach { node ->
            val label = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty() + node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty()
            assertTrue("Unlabelled control", label.isNotBlank())
            val density = node.layoutInfo.density.density
            assertTrue("Small target $label", node.size.width / density >= 47.5f && node.size.height / density >= 47.5f)
        }
    }
    private fun save(state: String) {
        if (System.getenv("MEDITAKIP_SCREENSHOTS") == null) return
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val output = File("build/screenshots/phase9").apply { mkdirs() }
        File(output, "$state-${if (dark) "dark" else "light"}-$width-$font.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}
