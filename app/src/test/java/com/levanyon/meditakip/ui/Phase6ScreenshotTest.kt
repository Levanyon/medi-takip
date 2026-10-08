package com.levanyon.meditakip.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import org.junit.*
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
import java.io.File

/** Native renders plus target/heading/progress checks. Output is deliberately git-ignored. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w390dp-h844dp-mdpi")
class Phase6ScreenshotTest(private val screen: String, private val dark: Boolean, private val width: Int, private val font: Float) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-dark={1}-w{2}-font{3}")
        fun cases(): List<Array<Any>> {
            val screens = listOf("splash1", "splash2", "splash3", "welcome", "notifications", "exact", "ready", "notificationDenied", "exactDenied")
            val normal = screens.flatMap { screen -> listOf(false, true).map { arrayOf<Any>(screen, it, 390, 1f) } }
            val responsive = listOf("welcome", "notifications", "exact", "ready", "notificationDenied", "exactDenied").flatMap { screen ->
                listOf(320 to 1f, 320 to 2f, 360 to 1f, 412 to 1f, 1200 to 1f).flatMap { (width, font) ->
                    listOf(false, true).map { arrayOf<Any>(screen, it, width, font) }
                }
            }
            return normal + responsive
        }
    }
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var originalQualifiers: String? = null
    private var originalFontScale = 1f
    @Before fun configure() {
        Assume.assumeTrue(System.getenv("MEDITAKIP_SCREENSHOTS") != null)
        originalQualifiers = RuntimeEnvironment.getQualifiers()
        originalFontScale = RuntimeEnvironment.getApplication().resources.configuration.fontScale
        RuntimeEnvironment.setQualifiers("w" + width + "dp-h844dp-mdpi")
        RuntimeEnvironment.setFontScale(font)
    }
    @After fun reset() {
        // Both operations update global Android resources, beyond the local Compose density.
        originalQualifiers?.let { RuntimeEnvironment.setQualifiers(it); RuntimeEnvironment.setFontScale(originalFontScale) }
    }
    @Test fun renderAndVerify() {
        val step = when (screen) {
            "notifications", "notificationDenied" -> OnboardingStep.NOTIFICATIONS
            "exact", "exactDenied" -> OnboardingStep.EXACT
            "ready" -> OnboardingStep.READY
            else -> OnboardingStep.WELCOME
        }
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, font)) {
                MediTheme(if (dark) "Dark" else "Light") { Surface {
                    if (screen.startsWith("splash")) LaunchArtwork(launchFrame(when (screen) { "splash1" -> 0; "splash2" -> 440; else -> 560 }))
                    else OnboardingPage(step, step.ordinal, 4, screen.endsWith("Denied"), primary = {}, secondary = {})
                } }
            }
        }
        save("")
        if (!screen.startsWith("splash")) {
            compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertExists()
            compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertExists()
            val action = when {
                screen.endsWith("Denied") -> "Ayarları aç"
                screen == "welcome" -> "Başla"
                screen == "notifications" -> "Bildirimleri etkinleştir"
                screen == "exact" -> "Tam zamanında hatırlatmayı aç"
                else -> "İlk kişiyi ekle"
            }
            compose.onNodeWithText(action).performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(MediSize.touch)
            save("-actions")
            if (screen != "welcome") {
                compose.onNodeWithText(if (screen == "ready") "Daha sonra" else "Şimdilik geç").performScrollTo().assertIsDisplayed()
            }
        }
        compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach { node ->
            val label = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty() +
                node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty()
            assertTrue("Unlabelled control", label.isNotBlank())
            val density = node.layoutInfo.density.density
            assertTrue("Small target: " + label, node.size.width / density >= 47.5f && node.size.height / density >= 47.5f)
        }
    }
    private fun save(suffix: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        assertTrue(view.width > 0 && view.height > 0)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val output = File("build/screenshots/phase6").apply { mkdirs() }
        val name = screen + "-" + (if (dark) "dark" else "light") + "-" + width + "-" + font + suffix + ".png"
        File(output, name).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}
