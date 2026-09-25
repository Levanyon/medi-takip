package com.levanyon.meditakip.ui

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.viewmodel.compose.viewModel
import com.levanyon.meditakip.MediApp
import com.levanyon.meditakip.data.*
import com.levanyon.meditakip.domain.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import java.time.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], application = MediApp::class, qualifiers = "w320dp-h640dp")
class ScreenLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val date = LocalDate.now()
    private val person = PersonEntity("p", "Ayşe Uzun Türkçe Aile Üyesi Adı")
    private val sibling = PersonEntity("k", "Kardeş")
    private val schedule = MedicationScheduleEntity("s", "m", person.name, "Uzun İlaç Adı", "Programdaki doz bilgisi", "", "08:00,20:00", 127, date.toEpochDay(), null, true, date.toEpochDay())
    private val siblingSchedule = MedicationScheduleEntity("s2", "m2", sibling.name, "Vitamin", "1 kapsül", "", "09:00", 127, date.toEpochDay(), null, true, date.toEpochDay())
    private val fixture = Snapshot(listOf(person), listOf(MedicationEntity("m", "p")), listOf(schedule), emptyList())
    private val family = Snapshot(listOf(person, sibling), listOf(MedicationEntity("m", "p"), MedicationEntity("m2", "k")), listOf(schedule, siblingSchedule), emptyList())
    /** Six medications over three days for one person: more columns than a phone can show. */
    private val crowded: Snapshot = run {
        val start = date.minusDays(2).toEpochDay()
        val schedules = (1..6).map { MedicationScheduleEntity("c$it", "cm$it", person.name, "İlaç Numara $it", "1 tablet", "", "%02d:00".format(6 + it * 2), 127, start, null, true, start) }
        val logs = listOf(MedicationLogEntity("c1:$start:480", "c1", start, 480, LocalDate.ofEpochDay(start).atTime(8, 5).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()))
        Snapshot(listOf(person), schedules.map { MedicationEntity(it.medicationId, "p") }, schedules, logs)
    }
    private fun content(dark: Boolean = false, fontScale: Float = 1.8f, body: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                MediTheme(if (dark) "Dark" else "Light", body)
            }
        }
    }
    private fun lazyScroll(text: String) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
        compose.onNodeWithText(text).assertIsDisplayed()
    }
    @After fun closeResources() {
        val app = RuntimeEnvironment.getApplication() as MediApp
        runBlocking { app.reminders.scope.coroutineContext[Job]?.cancelAndJoin() }
        app.database.close()
    }

    @Test fun todayHighlightsOneDoseAndKeepsEveryDoseActionable() {
        var marked = mutableListOf<Int>()
        var managed: String? = null
        content(true) { TodayScreen(fixture, date.atTime(12, 0), null, {}, viewModel(), { marked += it.minute }, {}, {}, { managed = it }) }
        lazyScroll("0 / 2 tamamlandı")
        lazyScroll("Saati geçti")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("İçildi"))
        val buttons = compose.onAllNodesWithText("İçildi")
        buttons.onFirst().assertIsDisplayed().assertHeightIsAtLeast(MediSize.primaryAction).performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Bekliyor"))
        compose.onAllNodesWithText("İçildi").onLast().performScrollTo().performClick()
        assertEquals(listOf(480, 1200), marked)
        lazyScroll("${person.name} için ilaçları yönet")
        compose.onNodeWithText("${person.name} için ilaçları yönet").performClick()
        assertEquals("p", managed)
    }
    @Test fun personSelectorSwitchesBetweenFamilyMembers() {
        content {
            var selected by remember { mutableStateOf<String?>(null) }
            TodayScreen(family, date.atTime(7, 0), selected, { selected = it }, viewModel(), {}, {}, {}, {})
        }
        lazyScroll("Uzun İlaç Adı")
        compose.onNodeWithText("Vitamin").assertDoesNotExist()
        compose.onNodeWithText("Kardeş").performScrollTo().assertHeightIsAtLeast(MediSize.touch).performClick().assertIsSelected()
        lazyScroll("Vitamin")
        compose.onNodeWithText("Uzun İlaç Adı").assertDoesNotExist()
    }
    @Test fun todayWithoutPeopleLeadsToAddPerson() {
        var added = false
        content { TodayScreen(Snapshot(), date.atTime(9, 0), null, {}, viewModel(), {}, { added = true }, {}, {}) }
        lazyScroll("Henüz kişi eklenmemiş")
        lazyScroll("Kişi Ekle")
        compose.onNodeWithText("Kişi Ekle").performClick()
        assertTrue(added)
    }
    @Test fun peopleScreenShowsAddPersonAndActiveCounts() {
        var opened = ""
        var added = false
        content { PeopleScreen(family, { opened = it }) { added = true } }
        lazyScroll("Kişi Ekle")
        compose.onNodeWithText("Kişi Ekle").performClick()
        assertTrue(added)
        lazyScroll("Kardeş")
        compose.onAllNodesWithText("1 aktif ilaç").assertCountEquals(2)
        compose.onNodeWithText("Kardeş").performClick()
        assertEquals("k", opened)
    }
    @Test fun personDetailIsManagementOnly() {
        var opened = ""
        var edited = false
        var added = false
        content(true) { PersonDetail(fixture, person, {}, { edited = true }, { added = true }, { opened = it }) }
        lazyScroll("Düzenle")
        compose.onNodeWithText("Düzenle").performClick()
        lazyScroll("İlaç Ekle")
        compose.onNodeWithText("İlaç Ekle").performClick()
        lazyScroll("Uzun İlaç Adı")
        compose.onNodeWithText("Uzun İlaç Adı").performClick()
        assertTrue(edited); assertTrue(added); assertEquals("m", opened)
        lazyScroll("Programdaki doz bilgisi")
        compose.onNodeWithText("Bekliyor").assertDoesNotExist()
        compose.onNodeWithText("İçildi").assertDoesNotExist()
    }
    @Test fun medicationDetailSectionsAndDeleteRemainReachable() {
        var deleted = false
        content(true) { MedicationDetail(fixture, "m", date, {}, { _, _ -> }, { deleted = true }) }
        lazyScroll("Kullanımda")
        lazyScroll("İlacı Düzenle")
        lazyScroll("Başlangıç Tarihi")
        lazyScroll("Son 7 gün")
        lazyScroll("İlacı Sil")
        compose.onNodeWithText("İlacı Sil").performClick()
        assertTrue(deleted)
    }
    @Test fun settingsContainOnlySettingsAndKeepPermissionActions() {
        var requested = false
        val access = PermissionAccess(PermissionState(false, false, PermissionAction.REQUEST, PermissionAction.SETTINGS, false), false, { requested = true }, {})
        content { SettingsScreen(viewModel(), access) }
        lazyScroll("İzin Ver")
        compose.onNodeWithText("İzin Ver").performClick()
        assertTrue(requested)
        lazyScroll("Görünüm")
        lazyScroll("Uygulama Hakkında")
        lazyScroll("MediTakip 1.1")
        compose.onNodeWithText("İlaç ve Kişi Yönetimi").assertDoesNotExist()
    }
    @Test fun historySmallTableIsCenteredNotStretched() {
        val single = fixture.copy(schedules = listOf(schedule.copy(times = "08:00")))
        content(fontScale = 1f) { HistoryScreen(single, date, "p", {}, {}) }
        lazyScroll("Tarih")
        val header = compose.onNodeWithText("Tarih").fetchSemanticsNode().boundsInRoot
        // 320dp screen, 20dp page padding: a 1-column table (≈168dp) starts well inside the page, not at its edge.
        assertTrue("Small table must be centered, header starts at ${header.left}", header.left > 50f)
        compose.onNodeWithText("Diğer ilaçları görmek için tabloyu yana kaydırın.").assertDoesNotExist()
    }
    @Test fun historyWideTableScrollsWhileDateColumnStaysVisible() {
        content { HistoryScreen(crowded, date, "p", {}, {}) }
        lazyScroll("Diğer ilaçları görmek için tabloyu yana kaydırın.")
        lazyScroll("Tarih")
        val before = compose.onNodeWithText("Tarih").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("İlaç Numara 6").performScrollTo().assertIsDisplayed()
        val after = compose.onNodeWithText("Tarih").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(before.left, after.left, 0.5f)
        compose.onNodeWithText("İlaç Numara 1").assertIsNotDisplayed()
    }
    @Test fun historyCellOpensDetailForUnmarkedAndTakenDoses() {
        content(fontScale = 1f) { HistoryScreen(crowded, date, "p", {}, {}) }
        lazyScroll("08:05")
        compose.onNodeWithText("08:05").performClick()
        compose.onNodeWithText("Planlanan").assertIsDisplayed()
        compose.onNodeWithText("Tamam").performScrollTo().performClick()
        compose.onAllNodes(hasContentDescription("Alındı olarak işaretlenmedi", substring = true)).onFirst().performScrollTo().performClick()
        compose.onNodeWithText("Alındı olarak işaretlenmedi").assertIsDisplayed()
    }
    @Test fun historyCellDetailShowsTheRevisionValidOnThatDate() {
        // B1: Parol 1 tablet until yesterday, renamed "Parol 500" / 2 tablet from today.
        val from = date.minusDays(2).toEpochDay()
        val old = schedule.copy(id = "r1", name = "Parol", dose = "1 tablet", times = "08:00", start = from, effectiveFrom = from, effectiveTo = date.minusDays(1).toEpochDay())
        val new = old.copy(id = "r2", name = "Parol 500", dose = "2 tablet", effectiveFrom = date.toEpochDay(), effectiveTo = null)
        val taken = LocalDate.ofEpochDay(from).atTime(8, 5).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val revised = fixture.copy(schedules = listOf(old, new), logs = listOf(MedicationLogEntity("r1:$from:480", "r1", from, 480, taken)))
        content(fontScale = 1f) { HistoryScreen(revised, date, "p", {}, {}) }
        lazyScroll("08:05")
        compose.onNodeWithText("08:05").performClick()
        compose.onNodeWithText("1 tablet").assertIsDisplayed()
        compose.onNodeWithText("Parol").assertIsDisplayed()
        compose.onNodeWithText("2 tablet").assertDoesNotExist()
    }
    @Test fun historyWithoutPeopleOrRecordsShowsPolishedEmptyStates() {
        var people = false
        content { HistoryScreen(Snapshot(), date, null, {}) { people = true } }
        lazyScroll("Henüz kişi eklenmemiş")
        lazyScroll("Kişilere Git")
        compose.onNodeWithText("Kişilere Git").performClick()
        assertTrue(people)
    }
    @Test @Config(qualifiers = "w640dp-h320dp")
    fun onboardingActionRemainsReachableInLandscape() {
        content { Onboarding(viewModel()) }
        compose.onNodeWithText("Başla").performScrollTo().assertIsDisplayed()
    }
    @Test @Config(qualifiers = "w640dp-h320dp")
    fun personFormSaveIsInsideScrollableContentInLandscape() {
        var saved: PersonEntity? = null
        content(true) { PersonEditor(person.copy(note = "Uzun kişi notu. ".repeat(20)), {}, {}, { saved = it }) }
        compose.onNodeWithText("Kaydet").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(person.name, saved?.name)
        assertEquals("p", saved?.id)
        compose.onNodeWithText("Kişiyi Sil").performScrollTo().assertIsDisplayed()
    }
    @Test @Config(qualifiers = "w640dp-h320dp")
    fun medicationFormKeepsExistingScheduleWhenSavedInLandscape() {
        var saved: MedicationScheduleEntity? = null
        content(true) { MedicationEditor(person, schedule, {}, { saved = it }) }
        compose.onNodeWithText("Kaydet").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(schedule.name, saved?.name)
        assertEquals(schedule.times, saved?.times)
        assertEquals(schedule.days, saved?.days)
        assertEquals(schedule.start, saved?.start)
        assertEquals(schedule.dose, saved?.dose)
        assertEquals(schedule.active, saved?.active)
    }
    @Test fun medicationFormHidesAdvancedFieldsUntilRequested() {
        content { MedicationEditor(person, null, {}, {}) }
        compose.onNodeWithText("Bitiş Tarihi").assertDoesNotExist()
        compose.onNodeWithText("Diğer seçenekler").performScrollTo().performClick()
        compose.onNodeWithText("Bitiş Tarihi").performScrollTo().assertIsDisplayed()
    }
    @Test fun dateFieldsNeverOfferPastDays() {
        // B5: an existing medication that started 10 days ago keeps showing that start, but the pickers begin today.
        val started = date.minusDays(10)
        content(fontScale = 1f) { MedicationEditor(person, schedule.copy(start = started.toEpochDay()), {}, {}) }
        compose.onNodeWithText("Diğer seçenekler").performScrollTo().performClick()
        compose.onNodeWithText(TurkishFormat.date(started)).performScrollTo().assertIsDisplayed().performClick()
        val todayMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val start = org.robolectric.shadows.ShadowDialog.getLatestDialog() as android.app.DatePickerDialog
        assertEquals(todayMillis, start.datePicker.minDate)
        start.dismiss()
        compose.onNodeWithText("Bitiş tarihi yok").performScrollTo().performClick()
        val end = org.robolectric.shadows.ShadowDialog.getLatestDialog() as android.app.DatePickerDialog
        assertEquals(todayMillis, end.datePicker.minDate)
    }
    @Test fun doseActionsKeepTakenAndUndoCallbacks() {
        content {
            var item by remember { mutableStateOf(Schedule.day(fixture, date).first()) }
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ScheduledDoseRow(item, true,
                    onTaken = { item = item.copy(takenAt = date.atTime(8, 4).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) },
                    onUndo = { item = item.copy(takenAt = null) })
            }
        }
        compose.onNodeWithText("İçildi").performScrollTo().performClick()
        compose.onNodeWithText("✓ İçildi • 08:04").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Geri Al").performScrollTo().assertHeightIsAtLeast(MediSize.touch).performClick()
        compose.onNodeWithText("Bekliyor").performScrollTo().assertIsDisplayed()
    }
    @Test @Config(qualifiers = "w640dp-h320dp")
    fun longConfirmationKeepsCancelReachableWithoutConfirming() {
        var dismissed = false
        var confirmed = false
        content { DeleteConfirmation("Geçmiş kayıtları korunacak. ".repeat(30), { dismissed = true }, { confirmed = true }) }
        compose.onNodeWithText("İptal").performScrollTo().assertIsDisplayed().performClick()
        assertTrue(dismissed)
        assertFalse(confirmed)
    }
}
