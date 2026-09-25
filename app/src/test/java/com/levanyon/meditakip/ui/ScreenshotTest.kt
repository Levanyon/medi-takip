package com.levanyon.meditakip.ui

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.activity.ComponentActivity
import android.graphics.Canvas
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.lifecycle.viewmodel.compose.viewModel
import com.levanyon.meditakip.MediApp
import com.levanyon.meditakip.data.*
import com.levanyon.meditakip.domain.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.*

/**
 * Development aid, not a regression test: renders the main screens to PNG files in
 * app/build/screenshots so the visual design can be reviewed without a device.
 * Runs only when the environment variable MEDITAKIP_SCREENSHOTS is set.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = MediApp::class, qualifiers = "w360dp-h800dp-xhdpi")
class ScreenshotTest(private val screen: String, private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-dark={1}")
        fun params() = listOf("today", "people", "person", "medication", "history", "settings", "status", "onboarding", "personForm", "medicationForm", "empty", "brand")
            .flatMap { listOf(arrayOf<Any>(it, false), arrayOf<Any>(it, true)) }
    }
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val date = LocalDate.now()
    private val anne = PersonEntity("p", "Anne")
    private val kardes = PersonEntity("k", "Kardeş")
    private fun rev(id: String, med: String, name: String, dose: String, times: String) =
        MedicationScheduleEntity(id, med, "", name, dose, "", times, 127, date.minusDays(10).toEpochDay(), null, true, date.minusDays(10).toEpochDay())
    private val schedules = listOf(rev("s1", "m1", "Tansiyon İlacı", "1 tablet", "08:00"), rev("s2", "m2", "Parol", "1 tablet", "13:00,20:00"), rev("s3", "m3", "D Vitamini", "1 damla", "09:00"))
    private fun taken(s: String, day: LocalDate, minute: Int, late: Int) =
        MedicationLogEntity("$s:${day.toEpochDay()}:$minute", s, day.toEpochDay(), minute, day.atStartOfDay().plusMinutes(minute + late.toLong()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
    private val snapshot = Snapshot(listOf(anne, kardes), listOf(MedicationEntity("m1", "p"), MedicationEntity("m2", "p", stockEnabled = true, stockQuantity = 7.0, stockThreshold = 8.0), MedicationEntity("m3", "k")), schedules,
        (1L..8L).flatMap { d -> val day = date.minusDays(d); listOfNotNull(taken("s1", day, 480, 4), if (d % 3 != 0L) taken("s2", day, 780, 2) else null, if (d % 2 == 0L) taken("s2", day, 1200, 9) else null) } + taken("s1", date, 480, 4) + MedicationLogEntity("s2:${date.minusDays(3).toEpochDay()}:1200", "s2", date.minusDays(3).toEpochDay(), 1200, 0, LogStatus.SKIPPED, note = "Dışarıdaydık"))

    @Before fun gate() { Assume.assumeTrue(System.getenv("MEDITAKIP_SCREENSHOTS") != null) }
    @After fun close() {
        val app = RuntimeEnvironment.getApplication() as MediApp
        runBlocking { app.reminders.scope.coroutineContext[Job]?.cancelAndJoin() }
        app.database.close()
    }

    @Test fun render() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1f)) {
                MediTheme(if (dark) "Dark" else "Light") {
                    androidx.compose.material3.Surface {
                        val vm: AppViewModel = viewModel()
                        val access = PermissionAccess(PermissionState(true, true, PermissionAction.NONE, PermissionAction.NONE, false), false, {}, {})
                        MediContent {
                            when (screen) {
                                "today" -> TodayScreen(snapshot, date.atTime(14, 10), "p", {}, vm, {}, {}, {}, {})
                                "people" -> PeopleScreen(snapshot, {}, {})
                                "person" -> PersonDetail(snapshot, anne, {}, {}, {}, {})
                                "medication" -> MedicationDetail(snapshot, "m2", date, {}, { _, _ -> }, {})
                                "history" -> HistoryScreen(snapshot, date, "p", {}, {})
                                "settings" -> SettingsScreen(vm)
                                "status" -> ReminderStatusScreen(vm, access) {}
                                "onboarding" -> Onboarding(vm)
                                "personForm" -> PersonEditor(null, {}) {}
                                "medicationForm" -> MedicationEditor(anne, null, {}) { _, _ -> }
                                "empty" -> TodayScreen(Snapshot(), date.atTime(9, 0), null, {}, vm, {}, {}, {}, {})
                                "brand" -> BrandPreview()
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val out = File("build/screenshots").apply { mkdirs() }
        File(out, "$screen-${if (dark) "dark" else "light"}.png").outputStream().use {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}

/** Launcher icon as Android masks it (circle and squircle) plus the themed-icon monochrome variant. */
@androidx.compose.runtime.Composable
private fun BrandPreview() {
    androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(24.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(24.dp)) {
        listOf(androidx.compose.foundation.shape.CircleShape, androidx.compose.foundation.shape.RoundedCornerShape(36.dp)).forEach { shape ->
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.size(160.dp).clip(shape)) {
                androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.levanyon.meditakip.R.drawable.ic_launcher_background), null, androidx.compose.ui.Modifier.matchParentSize())
                androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.levanyon.meditakip.R.drawable.ic_launcher_foreground), null, androidx.compose.ui.Modifier.matchParentSize())
            }
        }
        androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.CircleShape, color = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer) {
            androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(com.levanyon.meditakip.R.drawable.ic_launcher_monochrome), null, androidx.compose.ui.Modifier.size(160.dp), tint = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}
