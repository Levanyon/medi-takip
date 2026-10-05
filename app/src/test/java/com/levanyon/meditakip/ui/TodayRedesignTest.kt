package com.levanyon.meditakip.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.levanyon.meditakip.MediApp
import com.levanyon.meditakip.data.*
import com.levanyon.meditakip.domain.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.*

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], application = MediApp::class, qualifiers = "w360dp-h800dp-mdpi")
class TodayRedesignTest(private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes() = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
    }
    @get:Rule val compose = createComposeRule()
    private val date = LocalDate.now()
    private val person = PersonEntity("p", "Anne")
    private val schedule = MedicationScheduleEntity("s", "m", "", "Beloc ZOK 50 mg", "1 tablet", "Tok karnına", "08:00,19:30", 127,
        date.toEpochDay(), null, true, date.toEpochDay())
    private val fixture = Snapshot(listOf(person), listOf(MedicationEntity("m", "p")), listOf(schedule))
    private fun log(minute: Int, skipped: Boolean = false) = MedicationLogEntity("s:${date.toEpochDay()}:$minute", "s", date.toEpochDay(), minute,
        if (skipped) 0 else date.atStartOfDay().plusMinutes(minute + 3L).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        if (skipped) LogStatus.SKIPPED else LogStatus.TAKEN, note = if (skipped) "Dışarıdaydım" else null)

    @After fun close() {
        val app = RuntimeEnvironment.getApplication() as MediApp
        runBlocking { app.reminders.scope.coroutineContext[Job]?.cancelAndJoin() }
        app.database.close()
    }

    private fun show(scale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                MediTheme(if (dark) "Dark" else "Light") {
                    Scaffold(containerColor = MaterialTheme.colorScheme.background,
                        bottomBar = { Box(Modifier.testTag("navigation")) { MediNavigationBar(listOf("Bugün", "Kişiler", "Geçmiş", "Ayarlar"), List(4) { Icons.Default.Today }, 0) {} } }
                    ) { padding ->
                        MediContent(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                            Box(Modifier.fillMaxWidth().testTag("content")) { content() }
                        }
                    }
                }
            }
        }
    }
    private fun reach(text: String) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
        compose.onNodeWithText(text).assertIsDisplayed()
    }
    private fun textFits(text: String) {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertTrue(results.isNotEmpty())
        results.forEach { result ->
            assertFalse(result.didOverflowHeight)
            (0 until result.lineCount).forEach { line ->
                assertFalse(result.isLineEllipsized(line))
                assertTrue(result.getLineLeft(line) >= -0.5f && result.getLineRight(line) <= result.size.width + 0.5f)
            }
        }
    }

    @Test fun noPeopleOffersWorkingAddAction() {
        var added = false
        show { TodayScreen(Snapshot(), date.atTime(12, 0), null, {}, viewModel(), {}, { added = true }, {}, {}) }
        reach("Henüz kişi eklenmemiş")
        reach("Kişi Ekle")
        compose.onNodeWithText("Kişi Ekle").performClick()
        assertTrue(added)
        compose.onNodeWithText("Sıradaki").assertDoesNotExist()
    }

    @Test fun noMedicationOffersCreationForSelectedPerson() {
        var target: String? = null
        show { TodayScreen(Snapshot(people = listOf(person)), date.atTime(12, 0), "p", {}, viewModel(), {}, {}, { target = it }, {}) }
        reach("Henüz ilaç eklenmemiş")
        reach("Anne için ilaç ekle")
        compose.onNodeWithText("Anne için ilaç ekle").performClick()
        assertEquals("p", target)
        compose.onNodeWithText("Bugün tamamlandı").assertDoesNotExist()
    }

    @Test fun existingMedicationWithoutTodaysDoseIsNotCalledComplete() {
        val tomorrow = fixture.copy(schedules = listOf(schedule.copy(start = date.plusDays(1).toEpochDay(), effectiveFrom = date.plusDays(1).toEpochDay())))
        show { TodayScreen(tomorrow, date.atTime(12, 0), "p", {}, viewModel(), {}, {}, {}, {}) }
        reach("Bugün için planlanan ilaç yok.")
        compose.onNodeWithText("Henüz ilaç eklenmemiş").assertDoesNotExist()
        compose.onNodeWithText("Bugün tamamlandı").assertDoesNotExist()
        compose.onNodeWithText("Sıradaki").assertDoesNotExist()
    }

    @Test fun upcomingHeroPrecedesCompletedTimelineAndKeepsNoteAndTimeChange() {
        var changed: Occurrence? = null
        show { TodayScreen(fixture.copy(logs = listOf(log(480))), date.atTime(12, 0), "p", {}, viewModel(), {}, {}, {}, {}, changeTime = { changed = it }) }
        reach("Sıradaki")
        compose.onNodeWithText("Sıradaki").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithText("19:30").assertIsDisplayed()
        textFits("19:30")
        reach("Tok karnına")
        reach("✓ İçildi • 08:03")
        reach("Saati değiştir")
        compose.onNodeWithText("Saati değiştir").performClick()
        assertEquals(480, changed?.minute)
    }

    @Test fun overdueDoseKeepsPriorityOverLaterDose() {
        var marked: Occurrence? = null
        show { TodayScreen(fixture, date.atTime(12, 0), "p", {}, viewModel(), { marked = it }, {}, {}, {}) }
        reach("Saati geçti")
        compose.onNodeWithText("08:00").assertIsDisplayed()
        val overdueAction = hasText("İçildi") and hasContentDescription("08:00", substring = true)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(overdueAction)
        compose.onNode(overdueAction).assertIsDisplayed().performClick()
        assertEquals(480, marked?.minute)
    }

    @Test fun singleHeroIsNotDuplicatedAndSkipRevealsCompletion() {
        val single = fixture.copy(schedules = listOf(schedule.copy(times = "19:30")))
        var skipped: Occurrence? = null
        show {
            var state by remember { mutableStateOf(single) }
            TodayScreen(state, date.atTime(12, 0), "p", {}, viewModel(), {}, {}, {}, {}, skip = {
                skipped = it
                state = single.copy(logs = listOf(log(it.minute, skipped = true)))
            })
        }
        reach("19:30")
        compose.onAllNodesWithText("19:30").assertCountEquals(1)
        compose.onNodeWithText("Günün planı").assertDoesNotExist()
        reach("Atla")
        compose.onNodeWithText("Atla").performClick()
        assertEquals(1170, skipped?.minute)
        reach("Bugün tamamlandı")
        compose.onNodeWithText("Sıradaki").assertDoesNotExist()
        reach("Atlandı")
        reach("Not: Dışarıdaydım")
        reach("Geri Al")
        compose.onNodeWithText("Saati değiştir").assertDoesNotExist()
    }

    @Test fun allTakenShowsCalmCompletionWithoutHero() {
        show { TodayScreen(fixture.copy(logs = listOf(log(480), log(1170))), date.atTime(21, 0), "p", {}, viewModel(), {}, {}, {}, {}) }
        reach("2 / 2 tamamlandı")
        reach("Bugün tamamlandı")
        reach("Planlanan tüm ilaçlar işlendi.")
        compose.onNodeWithText("Sıradaki").assertDoesNotExist()
        compose.onNodeWithText("İçildi").assertDoesNotExist()
        reach("✓ İçildi • 19:33")
    }

    @Test fun selectedPersonControlsHeroAndProgress() {
        val other = PersonEntity("k", "Kardeş")
        val family = fixture.copy(people = listOf(person, other), medications = fixture.medications + MedicationEntity("v", "k"),
            schedules = fixture.schedules + schedule.copy(id = "v", medicationId = "v", name = "Vitamin D", times = "09:00"))
        show {
            var selected by remember { mutableStateOf("p") }
            TodayScreen(family, date.atTime(7, 0), selected, { selected = it }, viewModel(), {}, {}, {}, {})
        }
        compose.onNode(hasText("Kardeş") and isSelectable()).performScrollTo().performClick().assertIsSelected()
        reach("0 / 1 tamamlandı")
        reach("Vitamin D")
        compose.onNodeWithText("Beloc ZOK 50 mg").assertDoesNotExist()
    }

    @Test fun existingLowStockAlertOpensItsMedication() {
        var opened: String? = null
        val stocked = fixture.copy(medications = listOf(MedicationEntity("m", "p", stockEnabled = true, stockQuantity = 2.0, stockThreshold = 4.0)))
        show { TodayScreen(stocked, date.atTime(12, 0), "p", {}, viewModel(), {}, {}, {}, {}, openMedication = { opened = it }) }
        val alert = hasText("Beloc ZOK 50 mg azalıyor", substring = true) and hasClickAction()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(alert)
        compose.onNode(alert).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals("m", opened)
    }

    @Test @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun narrowLargeTextWrapsAndActionsClearNavigation() {
        val longName = "Ayşe Uzun Türkçe Aile Üyesi Adı"
        val longMedication = "Uzun İsimli Tansiyon İlacı 50 mg"
        val data = fixture.copy(people = listOf(person.copy(name = longName)), schedules = listOf(schedule.copy(name = longMedication, times = "19:30")))
        show(scale = 2f) { TodayScreen(data, date.atTime(12, 0), "p", {}, viewModel(), {}, {}, {}, {}) }
        compose.onNode(hasText(longName) and isSelectable()).performScrollTo().assertIsSelected().assertHeightIsAtLeast(48.dp)
        textFits(longName)
        reach("19:30")
        textFits("19:30")
        reach(longMedication)
        textFits(longMedication)
        reach("İçildi")
        compose.onNodeWithText("İçildi").performScrollTo().assertHeightIsAtLeast(56.dp)
        textFits("İçildi")
        val action = compose.onNodeWithText("İçildi").fetchSemanticsNode().boundsInRoot
        val nav = compose.onNodeWithTag("navigation").fetchSemanticsNode().boundsInRoot
        assertTrue("Dose action must clear navigation", action.bottom <= nav.top)
        reach("Atla")
        compose.onNodeWithText("Atla").assertHeightIsAtLeast(48.dp)
        textFits("Atla")
    }

    @Test @Config(qualifiers = "w412dp-h900dp-mdpi")
    fun largerPhoneShowsNextActionWithoutScrolling() {
        show { TodayScreen(fixture.copy(logs = listOf(log(480))), date.atTime(12, 0), "p", {}, viewModel(), {}, {}, {}, {}) }
        compose.onNodeWithText("19:30").assertIsDisplayed()
        compose.onNodeWithText("İçildi").assertIsDisplayed()
        textFits("19:30")
    }

    @Test @Config(qualifiers = "w1200dp-h900dp-mdpi")
    fun expandedScreenUsesCenteredReadingColumn() {
        show { TodayScreen(fixture, date.atTime(7, 0), "p", {}, viewModel(), {}, {}, {}, {}) }
        compose.onNodeWithTag("content").assertWidthIsEqualTo(MediSize.readingWidth)
        val content = compose.onNodeWithTag("content").fetchSemanticsNode().boundsInRoot
        assertEquals(600f, content.center.x, 1f)
        compose.onNodeWithText("08:00").assertIsDisplayed()
    }
}
