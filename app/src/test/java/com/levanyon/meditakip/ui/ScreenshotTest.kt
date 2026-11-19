package com.levanyon.meditakip.ui

import android.graphics.Bitmap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Today
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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
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
        fun params() = listOf("today", "people", "person", "medication", "history", "settings", "status", "onboarding", "personForm", "medicationForm", "empty", "brand", "designSystem",
            "todayUpcoming", "todayCompleted", "todayNoMedication", "todayNoDose", "todaySkipped", "todayLargeFont", "todayNarrow", "todayLarger", "todayExpanded")
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

    @Before fun gate() {
        Assume.assumeTrue(System.getenv("MEDITAKIP_SCREENSHOTS") != null)
        when (screen) {
            "todayNarrow" -> RuntimeEnvironment.setQualifiers("w320dp-h720dp-xhdpi")
            "todayLarger" -> RuntimeEnvironment.setQualifiers("w412dp-h900dp-xhdpi")
            "todayExpanded" -> RuntimeEnvironment.setQualifiers("w1200dp-h900dp-mdpi")
        }
    }
    @After fun close() {
        val app = RuntimeEnvironment.getApplication() as MediApp
        runBlocking { app.reminders.scope.coroutineContext[Job]?.cancelAndJoin() }
        app.database.close()
    }

    @Test fun render() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (screen == "todayLargeFont") 2f else 1f)) {
                MediTheme(if (dark) "Dark" else "Light") {
                    androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                        val vm: AppViewModel = viewModel()
                        val access = PermissionAccess(PermissionState(true, true, PermissionAction.NONE, PermissionAction.NONE, false), false, {}, {})
                        MediContent {
                            when (screen) {
                                "today", "todayUpcoming", "todayCompleted", "todayNoMedication", "todayNoDose", "todaySkipped", "todayLargeFont", "todayNarrow", "todayLarger", "todayExpanded" -> {
                                    val data = when (screen) {
                                        "todayCompleted" -> snapshot.copy(logs = snapshot.logs + taken("s2", date, 780, 2) + taken("s2", date, 1200, 3))
                                        "todayNoMedication" -> Snapshot(people = listOf(anne, kardes))
                                        "todayNoDose" -> snapshot.copy(schedules = schedules.map { it.copy(start = date.plusDays(1).toEpochDay(), effectiveFrom = date.plusDays(1).toEpochDay()) }, logs = emptyList())
                                        "todaySkipped" -> snapshot.copy(logs = snapshot.logs + MedicationLogEntity("s2:${date.toEpochDay()}:780", "s2", date.toEpochDay(), 780, 0, LogStatus.SKIPPED, note = "Dışarıdaydık"))
                                        else -> snapshot
                                    }
                                    TodayPreview(data, date.atTime(if (screen == "today") 14 else 12, 10), vm)
                                }
                                "people" -> PeopleScreen(snapshot, {}, {})
                                "person" -> PersonDetail(snapshot, anne, {}, {}, {}, {})
                                "medication" -> MedicationDetail(snapshot, "m2", date, {}, { _, _ -> }, {})
                                "history" -> HistoryScreen(snapshot, date, "p", {}, {})
                                "settings" -> SettingsScreen(vm)
                                "status" -> ReminderStatusScreen(vm, access) {}
                                "onboarding" -> Onboarding(vm)
                                "personForm" -> PersonEditor(null, {}) {}
                                "medicationForm" -> MedicationEditor(anne, null, {}) { _, _, _ -> }
                                "empty" -> TodayScreen(Snapshot(), date.atTime(9, 0), null, {}, vm, {}, {}, {}, {})
                                "brand" -> BrandPreview()
                                "designSystem" -> DesignSystemPreview()
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        saveScreenshot(screen)
        if (screen == "todayLargeFont") {
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Sıradaki doz"))
            compose.waitForIdle()
            saveScreenshot("todayLargeFont-hero")
        }
        if (screen == "todaySkipped") {
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Not: Dışarıdaydık"))
            compose.waitForIdle()
            saveScreenshot("todaySkipped-timeline")
        }
    }

    private fun saveScreenshot(name: String) {
        val out = File("build/screenshots").apply { mkdirs() }
        File(out, "$name-${if (dark) "dark" else "light"}.png").outputStream().use {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}

/** Real Phase 1 navigation and scaffold padding, with the existing four destinations. */
@Composable
private fun TodayPreview(snapshot: Snapshot, now: LocalDateTime, vm: AppViewModel) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { MediNavigationBar(listOf("Bugün", "Kişiler", "Geçmiş", "Ayarlar"),
            listOf(Icons.Default.Today, Icons.Default.People, Icons.Default.CalendarMonth, Icons.Default.Settings), 0) {} }
    ) { padding ->
        MediContent(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            TodayScreen(snapshot, now, "p", {}, vm, {}, {}, {}, {})
        }
    }
}

/** Shared-component review sheet; no production screen or data changes. */
@Composable
private fun DesignSystemPreview() {
    val labels = listOf("Bugün", "İlaçlar", "Kişiler", "Geçmiş", "Ayarlar")
    androidx.compose.material3.Scaffold(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        bottomBar = { MediNavigationBar(labels, List(5) { androidx.compose.material.icons.Icons.Default.Today }, 0) {} }
    ) { padding ->
        androidx.compose.foundation.lazy.LazyColumn(
            androidx.compose.ui.Modifier.padding(padding), contentPadding = ScreenPadding,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(MediSpace.regular)
        ) {
            item { ScreenHeading("MediTakip", "Sakin, anlaşılır, her gün yanınızda.") }
            item { SectionHeader("Günlük bakım", "Ortak bileşenler") }
            item { BackHeader("Kişi bilgileri") {} }
            item { PrimaryAction("İlaç Ekle") {} }
            item { SecondaryAction("Bilgileri düzenle") {} }
            item { QuietAction("Daha sonra") {} }
            item { AdaptivePair({ Avatar("Ayşe") }, { StatusChip("İçildi • 08:00", completed = true) }) }
            item { EmptyState("Henüz kişi eklenmemiş", "İlk kişiyi ekleyerek başlayın.") }
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
