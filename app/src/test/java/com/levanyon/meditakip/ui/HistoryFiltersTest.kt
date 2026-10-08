package com.levanyon.meditakip.ui

import com.levanyon.meditakip.data.LogStatus
import com.levanyon.meditakip.domain.*
import org.junit.Assert.*
import org.junit.Test

class HistoryFiltersTest {
    private val data = Phase5TestData
    @Test fun emptyLogsNeverBecomeFakeHistory() {
        assertTrue(historyRecords(data.snapshot.copy(logs = emptyList()), data.today, null, HistoryFilter()).isEmpty())
    }
    @Test fun groupsAreNewestFirstWithRealPeopleAndScheduledTimes() {
        val rows = historyRecords(data.snapshot, data.today, null, HistoryFilter())
        assertEquals(listOf(data.today, data.today.minusDays(1)), rows.map { it.date }.distinct())
        assertEquals(listOf("Anne", "Kardeşim", "Mert"), rows.take(3).map { it.personName })
        assertEquals(listOf(480, 720, 1200), rows.take(3).map { it.minute })
    }
    @Test fun personFilterUsesIdsAndPreservesLongNames() {
        val rows = historyRecords(data.longNames, data.today, "k", HistoryFilter())
        assertEquals(1, rows.size)
        assertEquals(data.longNames.people[1].name, rows.single().personName)
    }
    @Test fun dateRangesAreInclusiveAndAllIncludesOldRecords() {
        val edge = data.snapshot.copy(logs = data.snapshot.logs + data.log("a", data.today.minusDays(6)) + data.log("a", data.today.minusDays(7)))
        assertEquals(6, historyRecords(edge, data.today, null, HistoryFilter()).size)
        assertEquals(8, historyRecords(edge, data.today, null, HistoryFilter(30)).size)
        assertEquals(9, historyRecords(edge, data.today, null, HistoryFilter(null)).size)
    }
    @Test fun takenAndSkippedFiltersUseStoredStatesWithoutMutation() {
        val before = data.snapshot.copy()
        val skipped = historyRecords(data.snapshot, data.today, null, HistoryFilter(status = LogStatus.SKIPPED))
        assertEquals(1, skipped.size); assertTrue(skipped.single().skipped); assertNull(skipped.single().takenAt)
        assertEquals(4, historyRecords(data.snapshot, data.today, null, HistoryFilter(status = LogStatus.TAKEN)).size)
        assertEquals(before, data.snapshot)
    }
    @Test fun summaryKeepsExistingHandledMetricIncludingSkipped() {
        val actual = historyProgress(data.snapshot, data.today, null)
        assertEquals(DailyProgress.from((0L..6).flatMap { Schedule.day(data.snapshot, data.today.minusDays(it)) }), actual)
        assertEquals(21, actual.total); assertEquals(5, actual.completed)
        assertEquals(7, historyProgress(data.snapshot, data.today, "a").total)
    }
    @Test fun archivedAndNeverEffectiveRevisionsRetainTheirLoggedNames() {
        val archived = data.snapshot.copy(people = data.people.map { it.copy(archived = true) },
            medications = data.medications.map { it.copy(archived = true) },
            schedules = data.schedules.map { it.copy(effectiveTo = it.effectiveFrom - 1) })
        assertEquals(5, historyRecords(archived, data.today, null, HistoryFilter()).size)
        assertEquals("Beloc ZOK 50 mg", historyRecords(archived, data.today, "a", HistoryFilter()).first().schedule.name)
    }
    @Test fun futureRecordsAreNotPresentedAsPastHistory() {
        val future = data.snapshot.copy(logs = listOf(data.log("a", data.today.plusDays(1))))
        assertTrue(historyRecords(future, data.today, null, HistoryFilter(null)).isEmpty())
    }
}
