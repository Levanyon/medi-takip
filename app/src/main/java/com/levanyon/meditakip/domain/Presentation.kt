package com.levanyon.meditakip.domain

import com.levanyon.meditakip.data.MedicationScheduleEntity
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

object TurkishFormat {
    val locale: Locale = Locale.forLanguageTag("tr-TR")
    fun date(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
    fun shortDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMM", locale))
    fun weekday(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("EEE", locale))
    fun longWeekdayDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", locale))
    fun time(instant: Long, zone: ZoneId = ZoneId.systemDefault()): String = Instant.ofEpochMilli(instant).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm", locale))
    fun completed(instant: Long, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): String {
        val local = Instant.ofEpochMilli(instant).atZone(zone)
        return if (local.toLocalDate() == date) time(instant, zone) else "${date(local.toLocalDate())} • ${time(instant, zone)}"
    }
}

data class DailyProgress(val total: Int, val completed: Int) {
    val remaining get() = total - completed
    val fraction get() = if (total == 0) 0f else completed.toFloat() / total
    val percent get() = if (total == 0) 0 else completed * 100 / total
    companion object { fun from(items: List<Occurrence>) = DailyProgress(items.size, items.count { it.takenAt != null }) }
}
data class HistoryDay(val date: LocalDate, val items: List<Occurrence>)

object MedicationViews {
    /**
     * The newest revision of a medication: the open one if it exists, otherwise the last of those with the
     * latest `effectiveFrom`. Relies on `snapshot.schedules` being in insertion order (DAO: ORDER BY rowid),
     * which breaks the tie when a medication was edited and archived on the same day.
     */
    fun latestRevision(snapshot: Snapshot, medicationId: String): MedicationScheduleEntity? {
        val list = snapshot.schedules.filter { it.medicationId == medicationId }
        list.lastOrNull { it.effectiveTo == null }?.let { return it }
        val newest = list.maxOfOrNull { it.effectiveFrom } ?: return null
        return list.last { it.effectiveFrom == newest }
    }
    fun current(snapshot: Snapshot): List<MedicationScheduleEntity> {
        val people = snapshot.people.filter { !it.archived }.map { it.id }.toSet()
        val ids = snapshot.medications.filter { !it.archived && it.personId in people }.map { it.id }.toSet()
        return snapshot.schedules.filter { it.medicationId in ids && it.effectiveTo == null }.sortedBy { it.name.lowercase(TurkishFormat.locale) }
    }
    fun history(snapshot: Snapshot, medicationId: String, from: LocalDate, through: LocalDate): List<HistoryDay> {
        if (from > through) return emptyList()
        val schedules = snapshot.schedules.filter { it.medicationId == medicationId }
        val ids = schedules.map { it.id }.toSet()
        val subset = snapshot.copy(medications = snapshot.medications.filter { it.id == medicationId }, schedules = schedules, logs = snapshot.logs.filter { it.scheduleId in ids })
        return generateSequence(through) { it.minusDays(1) }.takeWhile { it >= from }
            .map { HistoryDay(it, Schedule.day(subset, it)) }.filter { it.items.isNotEmpty() }.toList()
    }
}

enum class PermissionAction { NONE, REQUEST, SETTINGS }
object PermissionPolicy {
    fun notifications(api: Int, runtimeGranted: Boolean, notificationsEnabled: Boolean, previouslyAsked: Boolean): PermissionAction = when {
        (api < 33 || runtimeGranted) && notificationsEnabled -> PermissionAction.NONE
        api >= 33 && !runtimeGranted && !previouslyAsked -> PermissionAction.REQUEST
        else -> PermissionAction.SETTINGS
    }
    fun exact(api: Int, allowed: Boolean) = if (api < 31 || allowed) PermissionAction.NONE else PermissionAction.SETTINGS
}

/** A medication at a scheduled time: one column of the person-based history table. */
data class HistoryColumn(val medicationId: String, val minute: Int, val name: String, val dose: String) {
    val key get() = columnKey(medicationId, minute)
    val time: LocalTime get() = LocalTime.of(minute / 60, minute % 60)
}
data class HistoryRow(val date: LocalDate, val cells: Map<String, Occurrence>)
data class HistoryMatrix(val columns: List<HistoryColumn>, val rows: List<HistoryRow>)
private fun columnKey(medicationId: String, minute: Int) = "$medicationId@$minute"

/**
 * Builds the date × (medication, time) matrix purely from existing schedules and logs.
 * Days without any planned or recorded dose for the person are omitted; nothing is persisted.
 */
object HistoryMatrices {
    fun build(snapshot: Snapshot, personId: String, from: LocalDate, through: LocalDate, medicationId: String? = null): HistoryMatrix {
        if (from > through) return HistoryMatrix(emptyList(), emptyList())
        val days = generateSequence(through) { it.minusDays(1) }.takeWhile { it >= from }
            .map { date -> date to Schedule.day(snapshot, date).filter { it.personId == personId && (medicationId == null || it.schedule.medicationId == medicationId) } }
            .filter { it.second.isNotEmpty() }.toList()
        val latest = snapshot.schedules.map { it.medicationId }.distinct().associateWith { MedicationViews.latestRevision(snapshot, it)!! }
        val columns = days.flatMap { it.second }.map { it.schedule.medicationId to it.minute }.distinct().map { (id, minute) ->
            val schedule = latest.getValue(id)
            HistoryColumn(id, minute, schedule.name, schedule.dose)
        }.sortedWith(compareBy({ it.minute }, { it.name.lowercase(TurkishFormat.locale) }))
        return HistoryMatrix(columns, days.map { (date, items) -> HistoryRow(date, items.associateBy { columnKey(it.schedule.medicationId, it.minute) }) })
    }
    /** First day that can hold history for this person (earliest schedule revision). */
    fun earliest(snapshot: Snapshot, personId: String): LocalDate? {
        val ids = snapshot.medications.filter { it.personId == personId }.map { it.id }.toSet()
        return snapshot.schedules.filter { it.medicationId in ids }.minOfOrNull { it.effectiveFrom }?.let(LocalDate::ofEpochDay)
    }
}
