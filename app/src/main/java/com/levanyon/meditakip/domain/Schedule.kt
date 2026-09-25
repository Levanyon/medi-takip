package com.levanyon.meditakip.domain

import com.levanyon.meditakip.data.*
import java.time.*

data class Snapshot(val people: List<PersonEntity> = emptyList(), val medications: List<MedicationEntity> = emptyList(), val schedules: List<MedicationScheduleEntity> = emptyList(), val logs: List<MedicationLogEntity> = emptyList())
data class Occurrence(val schedule: MedicationScheduleEntity, val personId: String, val date: LocalDate, val minute: Int, val takenAt: Long? = null) {
    val key: String get() = "${schedule.id}:${date.toEpochDay()}:$minute"
    val time: LocalTime get() = LocalTime.of(minute / 60, minute % 60)
    fun instant(zone: ZoneId): Instant = date.atTime(time).atZone(zone).toInstant()
}

enum class ScheduleValidation { NAME, DOSE, DAYS, DATES, TIMES }
class ScheduleValidationException(val reason: ScheduleValidation) : IllegalArgumentException()

object Schedule {
    fun minutes(text: String): List<Int> = text.split(',').map { LocalTime.parse(it.trim()).let { t -> t.hour * 60 + t.minute } }.distinct().sorted()
    fun validate(s: MedicationScheduleEntity) {
        if (s.name.isBlank()) throw ScheduleValidationException(ScheduleValidation.NAME)
        if (s.dose.isBlank()) throw ScheduleValidationException(ScheduleValidation.DOSE)
        if (s.days !in 1..127) throw ScheduleValidationException(ScheduleValidation.DAYS)
        if (s.end != null && s.end < s.start) throw ScheduleValidationException(ScheduleValidation.DATES)
        try { if (minutes(s.times).isEmpty()) throw ScheduleValidationException(ScheduleValidation.TIMES) }
        catch (_: java.time.format.DateTimeParseException) { throw ScheduleValidationException(ScheduleValidation.TIMES) }
    }
    fun day(snapshot: Snapshot, date: LocalDate): List<Occurrence> {
        val day = date.toEpochDay()
        val logs = snapshot.logs.associateBy { it.key }
        val meds = snapshot.medications.associateBy { it.id }
        val result = linkedMapOf<String, Occurrence>()
        snapshot.schedules.forEach { s ->
            val personId = meds[s.medicationId]?.personId ?: return@forEach
            if (s.active && day >= s.start && (s.end == null || day <= s.end) && day >= s.effectiveFrom && (s.effectiveTo == null || day <= s.effectiveTo) && s.days and (1 shl (date.dayOfWeek.value - 1)) != 0) {
                minutes(s.times).forEach { minute ->
                    val item = Occurrence(s, personId, date, minute)
                    val completedEarlierRevision = snapshot.logs.any { log -> log.date == day && log.minute == minute && log.scheduleId != s.id && snapshot.schedules.any { old -> old.id == log.scheduleId && old.medicationId == s.medicationId } }
                    if (!completedEarlierRevision) result[item.key] = item.copy(takenAt = logs[item.key]?.takenAt)
                }
            }
        }
        // Completed entries remain visible even when today's schedule is edited or archived.
        snapshot.logs.filter { it.date == day }.forEach { log ->
            val s = snapshot.schedules.find { it.id == log.scheduleId } ?: return@forEach
            val p = meds[s.medicationId]?.personId ?: return@forEach
            result[log.key] = Occurrence(s, p, date, log.minute, log.takenAt)
        }
        return result.values.sortedWith(compareBy({ it.schedule.personName }, { it.minute }, { it.schedule.name }))
    }
}
