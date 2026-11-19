package com.levanyon.meditakip.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.levanyon.meditakip.data.*
import com.levanyon.meditakip.domain.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], application = Application::class, qualifiers = "w390dp-h844dp-mdpi")
class PeopleRedesignTest(private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes() = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
    }
    @get:Rule val compose = createComposeRule()
    private val date = PeopleTestData.date
    private val person = PeopleTestData.person
    private val data = PeopleTestData.snapshot

    private fun show(scale: Float = 1f, body: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                MediTheme(if (dark) "Dark" else "Light") {
                    Scaffold(containerColor = MaterialTheme.colorScheme.background,
                        bottomBar = { Box(Modifier.testTag("navigation")) { MediNavigationBar(listOf("Bugün", "Kişiler", "Geçmiş", "Ayarlar"), List(4) { Icons.Default.Today }, 1) {} } }
                    ) { padding ->
                        MediContent(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                            Box(Modifier.fillMaxWidth().testTag("content")) { body() }
                        }
                    }
                }
            }
        }
    }
    private fun reach(text: String) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
        compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }
    private fun fits(text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        layouts.forEach { result ->
            assertFalse("Vertical clipping: $text", result.didOverflowHeight)
            (0 until result.lineCount).forEach { line ->
                assertFalse(result.isLineEllipsized(line))
                assertTrue("Horizontal clipping: $text", result.getLineLeft(line) >= -0.5f && result.getLineRight(line) <= result.size.width + 0.5f)
            }
        }
    }
    private fun targets() {
        compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach { node ->
            assertTrue(node.size.width >= 48 && node.size.height >= 48)
            assertTrue(node.config.getOrNull(SemanticsProperties.Text)?.isNotEmpty() == true || node.config.getOrNull(SemanticsProperties.ContentDescription)?.isNotEmpty() == true)
        }
    }

    @Test fun emptyPeopleHasOneWorkingCreationAction() {
        var added = false
        show { PeopleScreen(Snapshot(), {}, date) { added = true } }
        reach("Henüz kişi eklenmedi")
        reach("İlaç takibine başlamak için ilk kişiyi ekle.")
        compose.onNodeWithText("Kişi ekle").assertHeightIsAtLeast(48.dp).performClick()
        assertTrue(added)
        compose.onNodeWithText("Ekle").assertDoesNotExist()
        targets()
    }
    @Test fun onePersonOpensAndHeaderAdds() {
        var opened = ""
        var added = false
        show { PeopleScreen(PeopleTestData.single, { opened = it }, date) { added = true } }
        compose.onNodeWithText("Anne").assertHeightIsAtLeast(96.dp).performClick()
        assertEquals("p", opened)
        compose.onNodeWithText("1 aktif ilaç").assertExists()
        compose.onNodeWithText("Ekle").assertContentDescriptionEquals("Kişi ekle").performClick()
        assertTrue(added)
        targets()
    }
    @Test fun multiplePeopleShowTheirOwnCountsAndNextUnhandledDose() {
        show { PeopleScreen(data, {}, date) {} }
        compose.onNode(hasText("Anne") and hasText("3 aktif ilaç") and hasText("Sıradaki 6 Eki · 08:00")).assertExists()
        compose.onNode(hasText("Kardeşim") and hasText("1 aktif ilaç") and hasText("Sıradaki 7 Eki · 09:00")).assertExists()
        compose.onNode(hasText("Mert") and hasText("Sıradaki 6 Eki · 08:00")).assertExists()
    }
    @Test fun noDoseDayDoesNotPretendToBeComplete() {
        show { PeopleScreen(PeopleTestData.single, {}, date.minusDays(10)) {} }
        compose.onNodeWithText("Sıradaki 1 Eki · 08:00").assertExists()
        compose.onNodeWithText("Bugün tamamlandı").assertDoesNotExist()
    }
    @Test fun detailEmptyKeepsAddEditBackWithoutFakeProgress() {
        var added = false
        var edited = false
        var backed = false
        show { PersonDetail(Snapshot(people = listOf(person)), person, { backed = true }, { edited = true }, { added = true }, {}, today = date) }
        reach("Henüz aktif ilaç yok")
        reach("Henüz ilaç eklenmedi")
        compose.onNodeWithText("0 / 0 tamamlandı").assertDoesNotExist()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertDoesNotExist()
        reach("İlaç ekle"); compose.onNodeWithText("İlaç ekle").performClick()
        reach("Kişiyi düzenle"); compose.onNodeWithText("Kişiyi düzenle").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithContentDescription("Geri").assertHeightIsAtLeast(48.dp).performClick()
        assertTrue(added && edited && backed)
        targets()
        compose.onNodeWithText("İlaç tara").assertDoesNotExist()
    }
    @Test fun detailSummaryUsesEarliestUnhandledDoseAndOpensMedication() {
        var opened = ""
        show { PersonDetail(data, person, {}, {}, {}, { opened = it }, today = date) }
        reach("08:00 · Levotiron 50 mcg")
        compose.onNodeWithText("08:00 · Levotiron 50 mcg").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals("c", opened)
        assertEquals(0.6f, DailyProgress.from(Schedule.day(data, date).filter { it.personId == person.id }).fraction)
        reach("Beloc ZOK 50 mg")
        compose.onNodeWithText("Beloc ZOK 50 mg").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals("a", opened)
        compose.onNodeWithText("İçildi").assertDoesNotExist()
        compose.onNodeWithText("İlaç tara").assertDoesNotExist()
        targets()
    }
    @Test fun skippedDoseUsesTodayHandledDefinitionAndSnapshotUpdates() {
        show {
            var state by remember { mutableStateOf(PeopleTestData.single) }
            PersonDetail(state, person, {}, {}, { state = state.copy(logs = listOf(PeopleTestData.log("a", 480), PeopleTestData.log("a", 720, true))) }, {}, today = date)
        }
        reach("08:00 · Beloc ZOK 50 mg")
        reach("İlaç ekle"); compose.onNodeWithText("İlaç ekle").performClick()
        reach("08:00 · Beloc ZOK 50 mg")
        reach(TurkishFormat.date(date.plusDays(1)))
        compose.onNodeWithText("12:00 · Beloc ZOK 50 mg").assertDoesNotExist()
    }
    @Test fun zeroDoseDetailDoesNotHaveProgressBar() {
        show { PersonDetail(PeopleTestData.single, person, {}, {}, {}, {}, today = date.minusDays(10)) }
        reach("08:00 · Beloc ZOK 50 mg")
        reach(TurkishFormat.date(date.minusDays(5)))
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertDoesNotExist()
        reach("Beloc ZOK 50 mg")
    }
    @Test fun completedFinalDayKeepsProgressWhenThereIsNoNextDose() {
        val completed = PeopleTestData.single.copy(
            schedules = PeopleTestData.single.schedules.map { it.copy(end = date.toEpochDay()) },
            logs = listOf(PeopleTestData.log("a", 480), PeopleTestData.log("a", 720, true)))
        show { PersonDetail(completed, person, {}, {}, {}, {}, today = date) }
        reach("2 / 2 tamamlandı")
        reach("Bugün tamamlandı")
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(1f, 0f..1f)))
        compose.onNodeWithText("Sıradaki doz").assertDoesNotExist()
    }
    @Test fun lowStockComesFromCurrentStockLogicAndHasSemantics() {
        show { PersonDetail(PeopleTestData.lowStock, person, {}, {}, {}, {}, today = date) }
        reach("Levotiron 50 mcg")
        compose.onNodeWithText("Levotiron 50 mcg").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Stok az"))
        compose.onNodeWithText("Beloc ZOK 50 mg").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Aktif"))
    }
    @Test fun disabledStockAndZeroThresholdNeverShowLowChip() {
        val stock = PeopleTestData.single.copy(medications = listOf(MedicationEntity("a", "p", stockEnabled = true, stockQuantity = 0.0, stockThreshold = 0.0)))
        show { PersonDetail(stock, person, {}, {}, {}, {}, today = date) }
        reach("Beloc ZOK 50 mg")
        compose.onNodeWithText("Stok az").assertDoesNotExist()
        compose.onNodeWithText("Aktif").assertExists()
    }
    @Test fun stoppedMedicationAndPersonNoteAndReportRemainReachable() {
        var report = false
        var opened = ""
        val noted = person.copy(note = "Kişisel not")
        val stopped = PeopleTestData.single.copy(people = listOf(noted), schedules = PeopleTestData.single.schedules.map { it.copy(active = false) })
        show { PersonDetail(stopped, noted, {}, {}, {}, { opened = it }, report = { report = true }, today = date) }
        reach("Kişisel not"); reach("Henüz aktif ilaç yok"); reach("Durdurulan ilaçlar")
        reach("Beloc ZOK 50 mg"); compose.onNodeWithText("Beloc ZOK 50 mg").performClick()
        assertEquals("a", opened)
        reach("Durduruldu")
        compose.onNodeWithText("Aktif ilaçlar").assertDoesNotExist()
        val reportButton = hasText("Doktor raporu oluştur / paylaş") and hasClickAction()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(reportButton)
        compose.onNode(reportButton).performClick()
        assertTrue(report)
    }
    @Test @Config(qualifiers = "w320dp-h720dp-mdpi")
    fun narrowPeopleLongNameAtLargeFontWrapsAndClearsNavigation() {
        val long = PeopleTestData.single.copy(people = listOf(person.copy(name = PeopleTestData.longName)))
        show(2f) { PeopleScreen(long, {}, date) {} }
        reach(PeopleTestData.longName); fits(PeopleTestData.longName)
        reach("Sıradaki 6 Eki · 08:00"); fits("Sıradaki 6 Eki · 08:00")
        val row = compose.onNodeWithText(PeopleTestData.longName).fetchSemanticsNode().boundsInRoot
        val nav = compose.onNodeWithTag("navigation").fetchSemanticsNode().boundsInRoot
        assertTrue(row.right <= 300f && row.bottom <= nav.top)
        targets()
    }
    @Test @Config(qualifiers = "w320dp-h720dp-mdpi")
    fun narrowDetailLongNamesAndStatusAtLargeFontRemainReachable() {
        val long = PeopleTestData.single.copy(people = listOf(person.copy(name = PeopleTestData.longName)),
            schedules = PeopleTestData.single.schedules.map { it.copy(name = PeopleTestData.longMedication) },
            medications = listOf(MedicationEntity("a", "p", stockEnabled = true, stockQuantity = 1.0, stockThreshold = 3.0)))
        show(2f) { PersonDetail(long, long.people.first(), {}, {}, {}, {}, today = date) }
        reach(PeopleTestData.longName); fits(PeopleTestData.longName)
        reach(PeopleTestData.longMedication); fits(PeopleTestData.longMedication)
        reach("Stok az"); fits("Stok az")
        reach("Kişiyi düzenle"); fits("Kişiyi düzenle")
        val action = compose.onNodeWithText("Kişiyi düzenle").fetchSemanticsNode().boundsInRoot
        val nav = compose.onNodeWithTag("navigation").fetchSemanticsNode().boundsInRoot
        assertTrue(action.bottom <= nav.top)
        targets()
    }
    @Test @Config(qualifiers = "w360dp-h800dp-mdpi")
    fun standardPhoneShowsOneMedicationAndItsSchedule() {
        show { PersonDetail(PeopleTestData.single, person, {}, {}, {}, {}, today = date) }
        compose.onNodeWithText("Beloc ZOK 50 mg").assertIsDisplayed()
        fits("08:00 • 12:00 · 1 tablet")
        targets()
    }
    @Test @Config(qualifiers = "w412dp-h900dp-mdpi")
    fun largerPhoneShowsMultiplePeopleAndHeader() {
        show { PeopleScreen(data, {}, date) {} }
        listOf("Anne", "Kardeşim", "Mert", "Ekle").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        targets()
    }
    @Test @Config(qualifiers = "w1200dp-h900dp-mdpi")
    fun expandedDetailKeepsCenteredReadingWidth() {
        show { PersonDetail(data, person, {}, {}, {}, {}, today = date) }
        compose.onNodeWithTag("content").assertWidthIsEqualTo(MediSize.readingWidth)
        assertEquals(600f, compose.onNodeWithTag("content").fetchSemanticsNode().boundsInRoot.center.x, 1f)
        compose.onNodeWithText("Levotiron 50 mcg").assertIsDisplayed()
    }
}
