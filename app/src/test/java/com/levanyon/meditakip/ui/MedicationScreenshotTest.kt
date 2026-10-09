package com.levanyon.meditakip.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import org.junit.*
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Review artifacts from native Compose rendering; no pixel-baseline assertion is claimed. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w390dp-h844dp-mdpi")
class MedicationScreenshotTest(private val screen: String, private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-dark={1}")
        fun cases() = listOf("detail", "detailLowStock", "detailLongName", "detailNoNext", "detailLargeFont",
            "add", "edit", "addNarrow", "addLargeFont", "editLargeFont", "add360", "edit412", "editTablet")
            .flatMap { listOf(arrayOf<Any>(it, false), arrayOf<Any>(it, true)) }
    }
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun gate() {
        Assume.assumeTrue(System.getenv("MEDITAKIP_SCREENSHOTS") != null)
        when {
            screen.endsWith("Narrow") || screen.endsWith("LargeFont") -> RuntimeEnvironment.setQualifiers("w320dp-h800dp-mdpi")
            screen.endsWith("360") -> RuntimeEnvironment.setQualifiers("w360dp-h800dp-mdpi")
            screen.endsWith("412") -> RuntimeEnvironment.setQualifiers("w412dp-h900dp-mdpi")
            screen.endsWith("Tablet") -> RuntimeEnvironment.setQualifiers("w1200dp-h900dp-mdpi")
        }
    }
    @Test fun render() {
        val data = MedicationTestData
        val schedule = when (screen) {
            "detailLongName", "detailLargeFont", "editLargeFont" -> data.schedule.copy(name = data.longName, note = data.longNote)
            "detailNoNext" -> data.schedule.copy(active = false)
            else -> data.schedule
        }
        val snapshot = data.snapshot.copy(schedules = listOf(schedule), medications = data.snapshot.medications.map {
            if (screen == "detailLowStock") it.copy(stockQuantity = 3.0) else it
        })
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, if (screen.endsWith("LargeFont")) 2f else 1f)) {
                MediTheme(if (dark) "Dark" else "Light") {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        MediContent(Modifier.fillMaxSize()) {
                            if (screen.startsWith("detail")) MedicationDetail(snapshot, "m", data.date, {}, { _, _ -> }, {}, now = data.date.atTime(7, 0))
                            else MedicationEditor(data.person, if (screen.startsWith("edit")) schedule else null, {},
                                if (screen.startsWith("edit")) data.stock else null) { _, _, _ -> }
                        }
                    }
                }
            }
        }
        save(screen)
        if (!screen.startsWith("detail")) {
            compose.onNodeWithText("Tedavi dönemi").performScrollTo().assertIsDisplayed()
            save("$screen-period")
            compose.onNodeWithText("Kaydet").performScrollTo().assertIsDisplayed()
            save("$screen-save")
        }
    }
    private fun save(name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        assertTrue(view.width > 0 && view.height > 0)
        compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach { node ->
            val density = node.layoutInfo.density.density
            assertTrue("Touch target narrower than 48dp", node.size.width / density >= 47.5f)
            assertTrue("Touch target shorter than 48dp", node.size.height / density >= 47.5f)
            assertTrue("Control outside screen: ${node.boundsInRoot}", node.boundsInRoot.left >= 0 && node.boundsInRoot.right <= view.width)
        }
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val output = File("build/screenshots/phase4").apply { mkdirs() }
        File(output, "$name-${if (dark) "dark" else "light"}.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
}
