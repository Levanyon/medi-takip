package com.levanyon.meditakip.ui

import android.app.Application
import android.app.TimePickerDialog
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.levanyon.meditakip.data.*
import com.levanyon.meditakip.domain.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.time.LocalDate

internal object MedicationTestData {
    val date = LocalDate.of(2026, 10, 7)
    val person = PersonEntity("p", "Deniz")
    val schedule = MedicationScheduleEntity("s", "m", "Deniz", "Test ilacı", "2 kapsül", "Yemekten sonra",
        "08:00, 20:00", 127, date.minusDays(4).toEpochDay(), null, true, date.minusDays(4).toEpochDay())
    val stock = StockSettings(true, 18.0, 2.0, 5.0)
    val snapshot = Snapshot(listOf(person), listOf(MedicationEntity("m", "p", stockEnabled = true,
        stockQuantity = 18.0, stockPerDose = 2.0, stockThreshold = 5.0)), listOf(schedule))
    val longName = "Çok uzun ilaç adı ve uzatılmış salımlı kapsül kullanım bilgisi"
    val longNote = "Kullanım hakkında saklanan uzun açıklama. ".repeat(8)
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w390dp-h844dp-mdpi")
class MedicationRedesignTest {
    @get:Rule val compose = createComposeRule()
    private val data = MedicationTestData
    private fun show(dark: Boolean = false, scale: Float = 1f, body: @Composable () -> Unit) {
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
            MediTheme(if (dark) "Dark" else "Light") { Surface { MediContent { body() } } }
        } }
    }
    @Composable private fun Detail(snapshot: Snapshot = data.snapshot) {
        MedicationDetail(snapshot, "m", data.date, {}, { _, _ -> }, {}, now = data.date.atTime(7, 0))
    }
    private fun reach(text: String) {
        if (compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty())
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
        compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }
    @Test fun normalDetailUsesStoredDataAndActions() {
        var edited = false
        var backed = false
        show { MedicationDetail(data.snapshot, "m", data.date, { backed = true }, { p, s ->
            assertEquals(data.person, p); assertEquals(data.schedule, s); edited = true
        }, {}, now = data.date.atTime(7, 0)) }
        reach("Test ilacı"); reach("08:00 · 2 kapsül")
        reach("Yemekten sonra"); reach("İlacı Düzenle")
        compose.onNodeWithText("İlacı Düzenle").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithContentDescription("Geri").assertHeightIsAtLeast(48.dp).performClick()
        assertTrue(edited && backed)
        compose.onNodeWithText("Azalıyor").assertDoesNotExist()
        compose.onNodeWithText("Reçete yenileme").assertDoesNotExist()
    }
    @Test fun stoppedDetailHasNoInventedNextDose() {
        show { Detail(data.snapshot.copy(schedules = listOf(data.schedule.copy(active = false)))) }
        reach("Yaklaşan doz yok")
    }
    @Test fun lowStockIsOnlyShownWhenDomainSaysLow() {
        show { Detail(data.snapshot.copy(medications = listOf(data.snapshot.medications.single().copy(stockQuantity = 3.0)))) }
        reach("Azalıyor"); reach("Kalan: 3")
    }
    @Test fun overdueDoseIsNamedPending() {
        show { MedicationDetail(data.snapshot, "m", data.date, {}, { _, _ -> }, {}, now = data.date.atTime(12, 0)) }
        reach("Bekleyen doz"); reach("08:00 · 2 kapsül")
    }
    @Test fun emptyFormKeepsActualDefaultsAndAnnouncesValidation() {
        var saved = false
        show { MedicationEditor(data.person, null, {}) { _, _ -> saved = true } }
        reach("08:00"); reach("Kaydet"); compose.onNodeWithText("Kaydet").performClick()
        assertFalse(saved)
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive)).assertExists()
        compose.onNodeWithText("İlaç Adı").performScrollTo().performTextInput("Yeni ilaç")
        compose.onNodeWithText("Doz").performScrollTo().performTextInput("5 ml")
        compose.onNodeWithText("Kaydet").performScrollTo().performClick()
        assertTrue(saved)
    }
    @Test fun editPopulatesAndRetainsScheduleStockAndNotes() {
        var saved: MedicationScheduleEntity? = null
        var stock: StockSettings? = null
        show(true) { MedicationEditor(data.person, data.schedule, {}, data.stock) { s, st -> saved = s; stock = st } }
        compose.onNodeWithText("Test ilacı").assertExists()
        reach("20:00"); reach("Yemekten sonra")
        compose.onNodeWithText("Kaydet").performScrollTo().performClick()
        assertEquals(data.schedule.name, saved?.name)
        assertEquals(data.schedule.dose, saved?.dose)
        assertEquals(data.schedule.note, saved?.note)
        assertEquals(data.schedule.times, saved?.times)
        assertEquals(data.schedule.days, saved?.days)
        assertEquals(data.schedule.start, saved?.start)
        assertEquals(data.schedule.end, saved?.end)
        assertEquals(data.schedule.active, saved?.active)
        assertEquals(data.stock, stock)
    }
    @Test fun editRetainsSingleTimeAndCanAddAnotherUsingPicker() {
        var saved: MedicationScheduleEntity? = null
        show { MedicationEditor(data.person, data.schedule.copy(times = "09:30"), {}) { s, _ -> saved = s } }
        reach("09:30")
        compose.onNodeWithText("Saat Ekle").performScrollTo().performClick()
        compose.runOnIdle {
            val picker = ShadowDialog.getLatestDialog() as TimePickerDialog
            picker.updateTime(21, 15)
            picker.getButton(TimePickerDialog.BUTTON_POSITIVE).performClick()
        }
        reach("21:15")
        compose.onNodeWithText("Kaydet").performScrollTo().performClick()
        assertEquals(listOf(570, 1275), Schedule.minutes(saved!!.times))
    }
    @Test fun removingEveryTimePreventsSave() {
        var saved = false
        show { MedicationEditor(data.person, data.schedule.copy(times = "08:00"), {}) { _, _ -> saved = true } }
        compose.onNodeWithContentDescription("08:00 saatini kaldır").performScrollTo().performClick()
        compose.onNodeWithText("Kaydet").performScrollTo().performClick()
        assertFalse(saved)
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)).assertExists()
    }
    @Test fun weekdaySelectionRetainsTheExistingBitmaskRules() {
        var saved: MedicationScheduleEntity? = null
        show { MedicationEditor(data.person, data.schedule, {}) { s, _ -> saved = s } }
        compose.onNodeWithText("Günler").performScrollTo().performClick()
        compose.onNodeWithText("Pzt").performScrollTo().performClick()
        compose.onNodeWithText("Çar").performScrollTo().performClick()
        compose.onNodeWithText("Kaydet").performScrollTo().performClick()
        assertEquals(5, saved?.days)
    }
    @Test fun endDateAndInactiveStateSurviveAnEdit() {
        var saved: MedicationScheduleEntity? = null
        val schedule = data.schedule.copy(end = LocalDate.now().plusDays(10).toEpochDay(), active = false)
        show { MedicationEditor(data.person, schedule, {}) { s, _ -> saved = s } }
        compose.onNodeWithText("Kaydet").performScrollTo().performClick()
        assertEquals(schedule.end, saved?.end)
        assertEquals(false, saved?.active)
    }
    @Test fun disablingStockRetainsItsStoredAmounts() {
        var stock: StockSettings? = null
        show { MedicationEditor(data.person, data.schedule, {}, data.stock) { _, st -> stock = st } }
        compose.onNodeWithText("Stok takibi").performScrollTo().performClick()
        compose.onNodeWithText("Kaydet").performScrollTo().performClick()
        assertEquals(data.stock.copy(enabled = false), stock)
    }
    @Test @Config(qualifiers = "w320dp-h800dp-mdpi")
    fun emptyAddAtLargeFontKeepsBackAndSaveAccessible() {
        var back = false
        show(false, 2f) { MedicationEditor(data.person, null, { back = true }) { _, _ -> } }
        compose.onNodeWithText("Kaydet").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("Geri").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        assertTrue(back)
    }
    @Test @Config(qualifiers = "w320dp-h800dp-mdpi")
    fun narrowDarkLargeFontFormRetainsLongTextAndSave() {
        var saved: MedicationScheduleEntity? = null
        val schedule = data.schedule.copy(name = data.longName, note = data.longNote)
        show(true, 2f) { MedicationEditor(data.person, schedule, {}, data.stock) { s, _ -> saved = s } }
        compose.onNodeWithText(data.longName).assertExists()
        reach("20:00"); reach("Bu miktara inince haber ver")
        compose.onNodeWithText("Kaydet").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(data.longName, saved?.name); assertEquals(data.longNote, saved?.note)
    }
    @Test @Config(qualifiers = "w320dp-h800dp-mdpi")
    fun narrowDetailWrapsLongNameAndNoteAtLargeFont() {
        show(true, 2f) { Detail(data.snapshot.copy(schedules = listOf(data.schedule.copy(name = data.longName, note = data.longNote)))) }
        reach(data.longName)
        reach(data.longNote)
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText(data.longNote, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        layouts.forEach { assertFalse(it.didOverflowWidth); assertFalse(it.didOverflowHeight) }
        reach("İlacı Düzenle")
    }
}
