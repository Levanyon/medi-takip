package com.levanyon.meditakip.ui

import android.app.TimePickerDialog
import android.content.DialogInterface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.levanyon.meditakip.MediApp
import com.levanyon.meditakip.data.*
import com.levanyon.meditakip.domain.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.time.*

/** Correcting history from a Geçmiş cell (Phase 3, DECISIONS D3.5/D3.6). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = MediApp::class, qualifiers = "w360dp-h800dp")
class HistoryCorrectionTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.now()
    private val start = today.minusDays(3).toEpochDay()
    private val schedule = MedicationScheduleEntity("s", "m", "Anne", "Parol", "1 tablet", "", "08:00", 127, start, null, true, start)
    private val yesterday = today.minusDays(1)
    private fun millis(day: LocalDate, h: Int, m: Int) = day.atTime(h, m).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val snapshot = Snapshot(listOf(PersonEntity("p", "Anne")), listOf(MedicationEntity("m", "p")), listOf(schedule), listOf(
        MedicationLogEntity("s:${today.toEpochDay()}:480", "s", today.toEpochDay(), 480, millis(today, 8, 3)),
        MedicationLogEntity("s:${today.minusDays(2).toEpochDay()}:480", "s", today.minusDays(2).toEpochDay(), 480, millis(today.minusDays(2), 8, 0), LogStatus.SKIPPED, note = "Dışarıdaydım"),
    ))
    private class Recorder : DoseCorrections {
        val calls = mutableListOf<String>()
        override fun taken(item: Occurrence, takenAt: Long?) { calls += "taken ${item.date} ${takenAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime() }}" }
        override fun skip(item: Occurrence, note: String?) { calls += "skip ${item.date} $note" }
        override fun undo(item: Occurrence) { calls += "undo ${item.date}" }
        override fun setTakenTime(item: Occurrence, takenAt: Long) { calls += "time ${item.date}" }
    }
    private val recorder = Recorder()
    @After fun close() {
        val app = RuntimeEnvironment.getApplication() as MediApp
        runBlocking { app.reminders.scope.coroutineContext[Job]?.cancelAndJoin() }
        app.database.close()
    }
    private fun open(description: String) {
        compose.setContent { MediTheme("Light") { HistoryScreen(snapshot, today, "p", {}, goToPeople = {}, corrections = recorder) } }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription(description, substring = true))
        compose.onNode(hasContentDescription(description, substring = true)).performClick()
    }
    private fun confirmTimePicker() {
        val picker = ShadowDialog.getLatestDialog() as TimePickerDialog
        picker.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        compose.waitForIdle()
    }

    @Test fun markingAPastDoseAsksForTheTimeThenConfirms() {
        open("${TurkishFormat.date(yesterday)}: Alındı olarak işaretlenmedi")
        compose.onNodeWithText("İçildi olarak işaretle").performScrollTo().performClick()
        confirmTimePicker()
        compose.onNodeWithText("Geçmiş kayıt değiştirilsin mi?").assertIsDisplayed()
        compose.onNodeWithText("İçildi olarak işaretlenecek (saat 08:00)", substring = true).assertIsDisplayed()
        assertTrue("Nothing is written before confirming", recorder.calls.isEmpty())
        compose.onNodeWithText("Değiştir").performScrollTo().performClick()
        assertEquals(listOf("taken $yesterday 08:00"), recorder.calls)
    }
    @Test fun todaysRecordIsRemovedWithoutConfirmation() {
        open("${TurkishFormat.date(today)}: İçildi")
        compose.onNodeWithText("Saati değiştir").assertIsDisplayed()
        compose.onNodeWithText("Kaydı kaldır").performScrollTo().performClick()
        assertEquals(listOf("undo $today"), recorder.calls)
        compose.onNodeWithText("Geçmiş kayıt değiştirilsin mi?").assertDoesNotExist()
    }
    @Test fun aSkippedPastDoseCanBeCleared() {
        open("${TurkishFormat.date(today.minusDays(2))}: Atlandı")
        compose.onNodeWithText("Atlandı · Dışarıdaydım").assertIsDisplayed()
        compose.onNodeWithText("Saati değiştir").assertDoesNotExist()
        compose.onNodeWithText("Kaydı kaldır").performScrollTo().performClick()
        compose.onNodeWithText("Değiştir").performScrollTo().performClick()
        assertEquals(listOf("undo ${today.minusDays(2)}"), recorder.calls)
    }
    @Test fun withoutCorrectionsTheDetailIsReadOnly() {
        compose.setContent { MediTheme("Light") { HistoryScreen(snapshot, today, "p", {}, goToPeople = {}) } }
        val description = "${TurkishFormat.date(yesterday)}: Alındı olarak işaretlenmedi"
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription(description, substring = true))
        compose.onNode(hasContentDescription(description, substring = true)).performClick()
        compose.onNodeWithText("Tamam").assertIsDisplayed()
        compose.onNodeWithText("İçildi olarak işaretle").assertDoesNotExist()
    }
}
