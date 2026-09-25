package com.levanyon.meditakip.domain

import com.levanyon.meditakip.data.AppSettings
import java.time.*

/** FIRST = at the scheduled time, RETRY = follow-up if still not marked, SNOOZE = user chose "10 dk ertele". */
enum class ReminderKind { FIRST, RETRY, SNOOZE }

data class ReminderEvent(val occurrence: Occurrence, val at: Instant, val kind: ReminderKind) {
    val retry get() = kind == ReminderKind.RETRY
    /** Unique per delivery; used by the dispatch table so a reminder is never shown twice. */
    val key get() = occurrence.key + when (kind) {
        ReminderKind.FIRST -> ":first"
        ReminderKind.RETRY -> ":retry"
        ReminderKind.SNOOZE -> ":snooze@${at.toEpochMilli()}"
    }
}

/** Pure reminder planning; Android scheduling in `reminders/Reminders.kt` only executes these decisions. */
object ReminderPlan {
    const val SNOOZE_MINUTES = 10L
    /** Reminders are never delivered for events older than this (no backlog after reboot / time change). */
    val DELIVERY_WINDOW: Duration = Duration.ofHours(24)

    /**
     * Every reminder event for yesterday … +8 days of outstanding (not yet marked) doses of current,
     * active, non-archived revisions, plus follow-ups (if enabled) and snoozes, sorted by time.
     */
    fun events(snapshot: Snapshot, settings: AppSettings, now: Instant, zone: ZoneId, snoozes: Map<String, Long> = emptyMap()): List<ReminderEvent> {
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
            buildList {
                add(ReminderEvent(o, at, ReminderKind.FIRST))
                if (settings.retry) add(ReminderEvent(o, at.plusSeconds(settings.delayMinutes * 60L), ReminderKind.RETRY))
                snoozes[o.key]?.let { add(ReminderEvent(o, Instant.ofEpochMilli(it), ReminderKind.SNOOZE)) }
            }
        }.sortedWith(compareBy({ it.at }, { it.occurrence.personName }, { it.occurrence.schedule.name }))
    }

    /**
     * Events to show now: not yet dispatched, due between the alarm that was scheduled ([lowerMillis])
     * and [now], and not older than [DELIVERY_WINDOW]. `lowerMillis == 0` means no alarm was pending,
     * so nothing is delivered (e.g. right after install or after reminders were switched off).
     */
    fun due(events: List<ReminderEvent>, dispatched: Set<String>, lowerMillis: Long, now: Instant): List<ReminderEvent> {
        if (lowerMillis <= 0) return emptyList()
        return events.filter { it.key !in dispatched && it.at.toEpochMilli() >= lowerMillis && !it.at.isAfter(now) && it.at >= now.minus(DELIVERY_WINDOW) }
    }

    /** The next future event to schedule an alarm for. */
    fun next(events: List<ReminderEvent>, dispatched: Set<String>, now: Instant): ReminderEvent? =
        events.firstOrNull { it.at > now && it.key !in dispatched }

    /** Keeps only snoozes that still point at an outstanding dose and are not older than the delivery window. */
    fun liveSnoozes(snoozes: Map<String, Long>, outstanding: Set<String>, now: Instant): Map<String, Long> =
        snoozes.filter { (key, at) -> key in outstanding && at >= now.minus(DELIVERY_WINDOW).toEpochMilli() }

    /** Doses due at the same moment share a notification group, so Android bundles them under one summary. */
    fun groupKey(event: ReminderEvent): String = "due@${event.at.toEpochMilli()}"
}
