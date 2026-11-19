package com.levanyon.meditakip.domain

import java.time.LocalDate

/** Same earliest-unhandled-dose rule as Today and Medication Detail, including today's overdue dose. */
object NextDose {
    fun forMedication(snapshot: Snapshot, medicationId: String, today: LocalDate): Occurrence? {
        val medication = snapshot.medications.find { it.id == medicationId && !it.archived } ?: return null
        if (snapshot.people.none { it.id == medication.personId && !it.archived }) return null
        val current = MedicationViews.latestRevision(snapshot, medicationId) ?: return null
        if (!current.active || current.effectiveTo != null) return null
        val first = maxOf(today.toEpochDay(), current.start, current.effectiveFrom)
        return (0L..7L).asSequence().map { LocalDate.ofEpochDay(first + it) }
            .flatMap { Schedule.day(snapshot, it).asSequence() }
            .firstOrNull { it.schedule.id == current.id && !it.handled }
    }

    fun next(snapshot: Snapshot, today: LocalDate): Occurrence? = snapshot.medications
        .mapNotNull { forMedication(snapshot, it.id, today) }
        .minWithOrNull(compareBy({ it.date }, { it.minute }, { it.personName }, { it.schedule.name }, { it.key }))

    /** Resolve a notification against current data, never trust identity or history from Intent extras. */
    fun resolve(snapshot: Snapshot, key: String): Occurrence? {
        // IDs from supported backups may contain colons; only the final two fields are numeric.
        val dateAndId = key.substringBeforeLast(':', "")
        val date = runCatching { LocalDate.ofEpochDay(dateAndId.substringAfterLast(':', "").toLong()) }.getOrNull() ?: return null
        return Schedule.day(snapshot, date).firstOrNull { item ->
            item.key == key && !item.handled && item.schedule.active && item.schedule.effectiveTo == null &&
                snapshot.medications.any { it.id == item.schedule.medicationId && !it.archived } &&
                snapshot.people.any { it.id == item.personId && !it.archived }
        }
    }
}
