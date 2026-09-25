package com.levanyon.meditakip.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.*

/**
 * Flows that close a dialog containing a text field (the Atla note). Deliberately WITHOUT a screen-size
 * qualifier: with one, Robolectric freezes when such a dialog closes (robolectric/robolectric#8460).
 * Layout at small sizes is covered elsewhere (ScreenLayoutTest, AccessibilityTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = MediApp::class)
class SkipDialogFlowTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.now()
    private val start = today.minusDays(3).toEpochDay()
    private val yesterday = today.minusDays(1)
    private val schedule = MedicationScheduleEntity("s", "m", "Anne", "Parol", "1 tablet", "", "08:00", 127, start, null, true, start)
    private val snapshot = Snapshot(listOf(PersonEntity("p", "Anne")), listOf(MedicationEntity("m", "p")), listOf(schedule), emptyList())
    private val calls = mutableListOf<String>()
    private val recorder = object : DoseCorrections {
        override fun taken(item: Occurrence, takenAt: Long?) { calls += "taken ${item.date}" }
        override fun skip(item: Occurrence, note: String?) { calls += "skip ${item.date} $note" }
        override fun undo(item: Occurrence) { calls += "undo ${item.date}" }
        override fun setTakenTime(item: Occurrence, takenAt: Long) { calls += "time ${item.date}" }
    }
    @After fun close() {
        val app = RuntimeEnvironment.getApplication() as MediApp
        runBlocking { app.reminders.scope.coroutineContext[Job]?.cancelAndJoin() }
        app.database.close()
    }
    private fun openYesterday() {
        compose.setContent { MediTheme("Light") { HistoryScreen(snapshot, today, "p", {}, goToPeople = {}, corrections = recorder) } }
        val description = "${TurkishFormat.date(yesterday)}: Alındı olarak işaretlenmedi"
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription(description, substring = true))
        compose.onNode(hasContentDescription(description, substring = true)).performClick()
        compose.onNodeWithText("Atla").performScrollTo().performClick()
    }

    @Test fun skippingAPastDoseNamesTheDateAndCanBeCancelled() {
        openYesterday()
        // The skip dialog itself names the date and the change: it is the confirmation for a past day.
        compose.onNodeWithText("Atlandı olarak işaretlenecek", substring = true).assertIsDisplayed()
        compose.onNodeWithText("İptal").performScrollTo().performClick()
        assertTrue(calls.isEmpty())
    }
    @Test fun skippingAPastDoseRecordsItOnceWithTheNote() {
        openYesterday()
        compose.onNodeWithText("Not (isteğe bağlı)").performTextInput("Hastanedeydik")
        compose.onAllNodesWithText("Atla").filterToOne(hasClickAction()).performScrollTo().performClick()
        assertEquals(listOf("skip $yesterday Hastanedeydik"), calls)
    }
    @Test fun addingStockAcceptsTurkishDecimalsOnly() {
        var added: Double? = null
        compose.setContent { MediTheme("Light") { AddStockDialog({}) { added = it } } }
        compose.onAllNodesWithText("Stok ekle").filterToOne(hasClickAction()).assertIsNotEnabled()
        compose.onNodeWithText("Eklenecek miktar").performTextInput("abc")
        compose.onAllNodesWithText("Stok ekle").filterToOne(hasClickAction()).assertIsNotEnabled()
        compose.onNodeWithText("Eklenecek miktar").performTextInput("20,5")
        compose.onAllNodesWithText("Stok ekle").filterToOne(hasClickAction()).performClick()
        assertEquals(20.5, added!!, 1e-9)
    }
    @Test fun skippingOnBugunShowsAnUndoSnackbar() {
        compose.setContent { MediTheme("Light") { Home(snapshot, viewModel()) } }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Atla"))
        compose.onAllNodesWithText("Atla").onFirst().performClick()
        compose.onNodeWithText("Bu doz atlansın mı?").assertIsDisplayed()
        compose.onNodeWithText("Not (isteğe bağlı)").performTextInput("Dışarıdaydım")
        compose.onAllNodesWithText("Atla").filterToOne(hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Parol atlandı olarak kaydedildi").fetchSemanticsNodes().isNotEmpty() }
    }
}
