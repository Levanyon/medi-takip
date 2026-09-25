package com.levanyon.meditakip.domain

import com.levanyon.meditakip.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.util.Locale

class PresentationTest {
    private val date = LocalDate.of(2026, 9, 21)
    private val old = MedicationScheduleEntity("old", "m", "Anne", "A", "1", "", "08:00,20:00", 127, date.toEpochDay(), null, true, date.toEpochDay(), date.toEpochDay())
    private val current = old.copy(id = "new", name = "B", effectiveFrom = date.plusDays(1).toEpochDay(), effectiveTo = null)
    private val snapshot = Snapshot(listOf(PersonEntity("p", "Anne")), listOf(MedicationEntity("m", "p")), listOf(old, current))
    @Test fun turkishDateIgnoresDeviceLanguage() {
        val previous = Locale.getDefault()
        try { Locale.setDefault(Locale.US); assertEquals("21 Eylül 2026", TurkishFormat.date(date)) } finally { Locale.setDefault(previous) }
    }
    @Test fun completionShowsRealLocalTime() { assertEquals("08:04", TurkishFormat.time(Instant.parse("2026-09-21T05:04:00Z").toEpochMilli(), ZoneId.of("Europe/Istanbul"))) }
    @Test fun completionOnAnotherDateIsExplicit() { assertEquals("22 Eylül 2026 • 00:04", TurkishFormat.completed(Instant.parse("2026-09-21T21:04:00Z").toEpochMilli(), date, ZoneId.of("Europe/Istanbul"))) }
    @Test fun historyIncludesRevisionsAndUnmarkedDoses() {
        val matrix = HistoryMatrices.build(snapshot, "p", date, date.plusDays(1), medicationId = "m")
        assertEquals(listOf("B", "A"), matrix.rows.map { row -> row.cells.values.first().schedule.name })
        assertEquals(4, matrix.rows.sumOf { it.cells.size })
        assertTrue(matrix.rows.all { row -> row.cells.values.all { it.takenAt == null } })
    }
    @Test fun historyUsesExistingCompletionTimestampAndFiltersOtherMedications() {
        val item = Schedule.day(snapshot, date).first()
        val state = snapshot.copy(logs = listOf(MedicationLogEntity(item.key, "old", date.toEpochDay(), 480, 1234)))
        assertEquals(1234L, HistoryMatrices.build(state, "p", date, date, medicationId = "m").rows.single().cells.values.first { it.minute == 480 }.takenAt)
        assertTrue(HistoryMatrices.build(state, "p", date, date, medicationId = "other").rows.isEmpty())
    }
    @Test fun progressCountsOccurrencesNotMedicationNames() {
        val items = Schedule.day(snapshot, date)
        val progress = DailyProgress.from(items.mapIndexed { index, item -> if (index == 0) item.copy(takenAt = 1234) else item })
        assertEquals(DailyProgress(2, 1), progress); assertEquals(1, progress.remaining); assertEquals(50, progress.percent)
        assertEquals(0, DailyProgress.from(emptyList()).percent)
    }
    @Test fun permissionPolicyNeverRequestsGrantedPermissions() {
        assertEquals(PermissionAction.NONE, PermissionPolicy.notifications(33, true, true, false))
        assertEquals(PermissionAction.NONE, PermissionPolicy.notifications(28, false, true, false))
        assertEquals(PermissionAction.NONE, PermissionPolicy.exact(30, false))
        assertEquals(PermissionAction.NONE, PermissionPolicy.exact(33, true))
    }
    @Test fun deniedOrBlockedPermissionHasSafeSettingsRoute() {
        assertEquals(PermissionAction.REQUEST, PermissionPolicy.notifications(33, false, false, false))
        assertEquals(PermissionAction.SETTINGS, PermissionPolicy.notifications(33, false, false, true))
        assertEquals(PermissionAction.SETTINGS, PermissionPolicy.notifications(33, true, false, true))
        assertEquals(PermissionAction.SETTINGS, PermissionPolicy.notifications(28, false, false, false))
        assertEquals(PermissionAction.SETTINGS, PermissionPolicy.exact(33, false))
    }
}
