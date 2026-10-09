package com.levanyon.meditakip.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.levanyon.meditakip.data.*
import com.levanyon.meditakip.domain.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
import java.io.File

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w390dp-h844dp-mdpi")
class ScannerScreenshotTest(private val screen: String, private val dark: Boolean, private val width: Int, private val scale: Float) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-dark={1}-w{2}-font{3}")
        fun cases() = listOf("permission", "scanner", "result", "prefill").flatMap { screen -> listOf(false, true).flatMap { dark ->
            listOf(390 to 1f, 320 to 2f, 1200 to 1f).map { (width, scale) -> arrayOf<Any>(screen, dark, width, scale) }
        } }
    }
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var qualifiers: String? = null
    @Before fun setup() {
        Assume.assumeTrue(System.getenv("MEDITAKIP_SCREENSHOTS") != null)
        qualifiers = RuntimeEnvironment.getQualifiers(); RuntimeEnvironment.setQualifiers("w${width}dp-h844dp-mdpi")
    }
    @After fun restore() { qualifiers?.let(RuntimeEnvironment::setQualifiers) }
    @Test fun approvedScannerLayoutsAndReachableActions() {
        val gtin = "08699580090635"
        val catalog = MedicationCatalog { CatalogLookup.Found(CatalogProduct(gtin, "DOLARIT 300 MG 10 FILM TABLET")) }
        var decode: ((String) -> Unit)? = null
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f, scale)) { MediTheme(if (dark) "Dark" else "Light") { Surface {
            ScannerFlow(Snapshot(), PersonEntity("p", "Anne"), catalog,
                CameraPermissionAccess(if (screen == "permission") CameraAccess.RATIONALE else CameraAccess.READY, false) {}, {},
                { _, _, _, _, _, _ -> }, { _, _, _, _, _ -> }, preview = { modifier, _, decoded, flash, ready, _ ->
                    Box(modifier); SideEffect { decode = decoded; flash(true); ready() }
                })
        } } } }
        if (screen == "result" || screen == "prefill") {
            compose.runOnIdle { decode!!("01${gtin}1729123121SERIAL") }
            compose.waitUntil(5000) { compose.onAllNodesWithText("İlaç bulundu").fetchSemanticsNodes().isNotEmpty() }
        }
        if (screen == "prefill") compose.onNodeWithText("İlacı ekle").performScrollTo().performClick()
        save("top")
        val label = when (screen) { "permission" -> "Kamerayı etkinleştir"; "scanner" -> "Barkodu elle gir"; "result" -> "İlacı ekle"; else -> "Kaydet" }
        compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
        save("action")
        compose.onNodeWithContentDescription("Geri").assertIsDisplayed()
    }
    private fun save(part: String) {
        compose.waitForIdle()
        compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach {
            val density = it.layoutInfo.density.density
            assertTrue("touch width", it.size.width / density >= 47.5f)
            assertTrue("touch height", it.size.height / density >= 47.5f)
        }
        val view = compose.activity.window.decorView
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        val dir = File("build/screenshots/phase8").apply { mkdirs() }
        File(dir, "$screen-dark-$dark-w$width-font$scale-$part.png").outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        image.recycle()
    }
}
