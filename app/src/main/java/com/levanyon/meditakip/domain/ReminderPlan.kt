package com.levanyon.meditakip.domain

import com.levanyon.meditakip.data.AppSettings
import java.time.*

data class ReminderEvent(val occurrence: Occurrence, val at: Instant, val retry: Boolean) {
    val key get() = occurrence.key + if (retry) ":retry" else ":first"
}
object ReminderPlan {
    fun events(snapshot: Snapshot, settings: AppSettings, now: Instant, zone: ZoneId): List<ReminderEvent> {
        if (!settings.notifications) return emptyList()
        val today = now.atZone(zone).toLocalDate()
        val dates = mutableSetOf(today.minusDays(1), today)
        snapshot.schedules.filter { it.active && it.effectiveTo == null }.forEach { s ->
            val start = maxOf(today.toEpochDay(), s.start, s.effectiveFrom)
            (0L..7L).forEach { dates.add(LocalDate.ofEpochDay(start + it)) }
        }
        val current = snapshot.schedules.filter { it.effectiveTo == null && it.active }.map { it.id }.toSet()
        val available = snapshot.medications.filter { !it.archived }.map { it.id }.toSet()
        return dates.flatMap { Schedule.day(snapshot, it) }.filter { it.takenAt == null && it.schedule.id in current && it.schedule.medicationId in available }.flatMap { o ->
            val at = o.instant(zone)
            buildList { add(ReminderEvent(o, at, false)); if (settings.retry) add(ReminderEvent(o, at.plusSeconds(settings.delayMinutes * 60L), true)) }
        }.sortedBy { it.at }
    }
}
