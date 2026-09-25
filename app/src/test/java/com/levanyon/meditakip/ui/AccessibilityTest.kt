package com.levanyon.meditakip.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.viewmodel.compose.viewModel
import com.levanyon.meditakip.MediApp
import com.levanyon.meditakip.data.*
import com.levanyon.meditakip.domain.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.*

/**
 * Phase 5 accessibility matrix: every main screen at font scales 1.0, 1.3, 1.5 and 2.0 on a small
 * (320 dp) phone. The primary action must be reachable and every visible tappable element must have a
 * label (text or TalkBack description) and a touch target of at least 48 × 48 dp.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], application = MediApp::class, qualifiers = "w320dp-h640dp")
class AccessibilityTest(private val scale: Float) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "fontScale={0}")
        fun scales() = listOf(1.0f, 1.3f, 1.5f, 2.0f).map { arrayOf<Any>(it) }
    }
    @get:Rule val compose = createComposeRule()
    private val date = LocalDate.now()
    private val anne = PersonEntity("p", "Ayşe Uzun Türkçe Aile Üyesi Adı")
    private val schedule = MedicationScheduleEntity("s", "m", anne.name, "Uzun İsimli Tansiyon İlacı", "Programdaki doz bilgisi", "", "08:00,20:00", 127, date.minusDays(3).toEpochDay(), null, true, date.minusDays(3).toEpochDay())
    private val taken = MedicationLogEntity("s:${date.minusDays(1).toEpochDay()}:480", "s", date.minusDays(1).toEpochDay(), 480, date.minusDays(1).atTime(8, 5).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
    private val snapshot = Snapshot(listOf(anne, PersonEntity("k", "Kardeş")), listOf(MedicationEntity("m", "p")), listOf(schedule), listOf(taken))
    private val access = PermissionAccess(PermissionState(false, false, PermissionAction.REQUEST, PermissionAction.SETTINGS, false), false, {}, {})

    @After fun close() {
        val app = RuntimeEnvironment.getApplication() as MediApp
        runBlocking { app.reminders.scope.coroutineContext[Job]?.cancelAndJoin() }
        app.database.close()
    }
    private fun show(body: @Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalDensity provides Density(1f, scale)) { MediTheme("Light") { MediContent { body() } } }
    }
    /** Brings [text] into view (lazy or plain scrolling) and checks it is visible and tall enough. */
    private fun reachable(text: String) {
        val scrollable = compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes()
        if (scrollable.isNotEmpty()) compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
        else compose.onNodeWithText(text).performScrollTo()
        // Also scroll a nested horizontal container (e.g. the person selector), as a user would swipe it.
        runCatching { compose.onAllNodesWithText(text).onFirst().performScrollTo() }
        compose.onAllNodesWithText(text).onFirst().assertIsDisplayed()
        assertAccessibleTargets()
    }
    private fun assertAccessibleTargets() {
        compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach { node ->
            val label = listOfNotNull(node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }, node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(),
                node.config.getOrNull(SemanticsProperties.EditableText)?.text).joinToString(" ").trim()
            assertTrue("Tappable element without a label at ${node.boundsInRoot} (fontScale $scale)", label.isNotEmpty() || node.config.getOrNull(SemanticsProperties.Role) == androidx.compose.ui.semantics.Role.Switch)
            // Layout size, not the visible part: an element half scrolled out of a list is still full size
            // once the user scrolls to it. (Material's minimum interactive size is part of the layout size.)
            val density = node.layoutInfo.density.density
            val width = node.size.width / density
            val height = node.size.height / density
            assertTrue("Touch target too small for '$label': $width x $height dp (fontScale $scale)", width >= 47.5f && height >= 47.5f)
        }
    }

    @Test fun today() { show { TodayScreen(snapshot, date.atTime(12, 0), "p", {}, viewModel(), {}, {}, {}, {}, warning = com.levanyon.meditakip.R.string.reminders_need_attention) }; reachable("İçildi"); reachable("Kardeş") }
    @Test fun people() { show { PeopleScreen(snapshot, {}, {}) }; reachable("Kişi Ekle"); reachable("Kardeş") }
    @Test fun personDetail() { show { PersonDetail(snapshot, anne, {}, {}, {}, {}) }; reachable("Düzenle"); reachable("İlaç Ekle") }
    @Test fun medicationDetail() { show { MedicationDetail(snapshot, "m", date, {}, { _, _ -> }, {}) }; reachable("İlacı Düzenle"); reachable("İlacı Arşivle") }
    @Test fun history() { show { HistoryScreen(snapshot, date, "p", {}, {}) }; reachable("Tarih"); reachable("08:05") }
    @Test fun settings() { show { SettingsScreen(viewModel()) }; reachable("Hatırlatma Durumu"); reachable("Koyu") }
    @Test fun reminderStatus() { show { ReminderStatusScreen(viewModel(), access) {} }; reachable("Pil kısıtlaması"); reachable("Test bildirimi gönder") }
    @Test fun personForm() { show { PersonEditor(anne, {}, {}) {} }; reachable("Kaydet"); reachable("Kişiyi Arşivle") }
    @Test fun medicationForm() { show { MedicationEditor(anne, schedule, {}) {} }; reachable("Saat Ekle"); reachable("Kaydet") }
    @Test fun onboarding() { show { Onboarding(viewModel()) }; reachable("İlk kişiyi ekle"); reachable("Daha sonra") }
}
