package com.levanyon.meditakip.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.levanyon.meditakip.domain.Snapshot
import org.junit.*
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Native rendering artifacts for Figma review; pixel-baseline comparison is not claimed. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w390dp-h844dp-mdpi")
class PeopleScreenshotTest(private val screen: String, private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-dark={1}")
        fun cases() = listOf("peopleMultiple", "peopleEmpty", "peopleNarrow", "peopleLargeFont", "people360", "people412", "peopleExpanded",
            "personDetail", "personEmpty", "personLowStock", "personLongMedication", "personLargeFont", "personNarrow", "personExpanded")
            .flatMap { listOf(arrayOf<Any>(it, false), arrayOf<Any>(it, true)) }
    }
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun gate() {
        Assume.assumeTrue(System.getenv("MEDITAKIP_SCREENSHOTS") != null)
        when {
            screen.endsWith("Narrow") || screen.endsWith("LargeFont") -> RuntimeEnvironment.setQualifiers("w320dp-h800dp-mdpi")
            screen.endsWith("360") -> RuntimeEnvironment.setQualifiers("w360dp-h800dp-mdpi")
            screen.endsWith("412") -> RuntimeEnvironment.setQualifiers("w412dp-h900dp-mdpi")
            screen.endsWith("Expanded") -> RuntimeEnvironment.setQualifiers("w1200dp-h900dp-mdpi")
        }
    }
    @Test fun render() {
        val person = PeopleTestData.person
        val data = when (screen) {
            "peopleEmpty" -> Snapshot()
            "personEmpty" -> Snapshot(people = listOf(person))
            "personLowStock" -> PeopleTestData.lowStock
            "personLongMedication", "personLargeFont" -> PeopleTestData.lowStock.copy(
                schedules = PeopleTestData.lowStock.schedules.map { if (it.id == "a") it.copy(name = PeopleTestData.longMedication) else it })
            "peopleLargeFont", "peopleNarrow" -> PeopleTestData.snapshot.copy(people = PeopleTestData.snapshot.people.map {
                if (it.id == "p") it.copy(name = PeopleTestData.longName) else it })
            else -> PeopleTestData.snapshot
        }
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (screen.endsWith("LargeFont")) 2f else 1f)) {
                MediTheme(if (dark) "Dark" else "Light") {
                    Scaffold(containerColor = MaterialTheme.colorScheme.background,
                        bottomBar = { MediNavigationBar(listOf("Bugün", "Kişiler", "Geçmiş", "Ayarlar"),
                            listOf(Icons.Outlined.Today, Icons.Outlined.People, Icons.Outlined.CalendarMonth, Icons.Outlined.Settings), 1,
                            listOf(Icons.Filled.Today, Icons.Filled.People, Icons.Filled.CalendarMonth, Icons.Filled.Settings)) {} }
                    ) { padding ->
                        MediContent(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                            if (screen.startsWith("people")) PeopleScreen(data, {}, PeopleTestData.date) {}
                            else PersonDetail(data, person, {}, {}, {}, {}, today = PeopleTestData.date)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        save(screen)
        if (screen == "personLargeFont") {
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(PeopleTestData.longMedication))
            compose.onNodeWithText(PeopleTestData.longMedication).performScrollTo().assertIsDisplayed()
            save("personLargeFont-medications")
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Kişiyi düzenle"))
            save("personLargeFont-bottom")
        }
    }
    private fun save(name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        assertTrue(view.width > 0 && view.height > 0)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val output = File("build/screenshots/phase3").apply { mkdirs() }
        File(output, "$name-${if (dark) "dark" else "light"}.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
}
