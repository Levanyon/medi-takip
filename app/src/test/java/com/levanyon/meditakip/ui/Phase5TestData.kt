package com.levanyon.meditakip.ui

import com.levanyon.meditakip.data.*
import com.levanyon.meditakip.domain.*
import java.time.*

internal object Phase5TestData {
    val today: LocalDate = LocalDate.of(2026, 10, 7)
    val people = listOf(PersonEntity("a", "Anne"), PersonEntity("k", "Kardeşim"), PersonEntity("m", "Mert"))
    val medications = people.map { MedicationEntity(it.id, it.id) }
    val schedules = listOf("Beloc ZOK 50 mg", "D Vitamini", "Levotiron 50 mcg").mapIndexed { index, name ->
        val start = today.minusDays(40).toEpochDay()
        MedicationScheduleEntity(people[index].id, people[index].id, "stale name", name, "1 tablet", "", listOf("08:00", "12:00", "20:00")[index], 127, start, null, true, start)
    }
    fun log(id: String, date: LocalDate, status: LogStatus = LogStatus.TAKEN): MedicationLogEntity {
        val minute = Schedule.minutes(schedules.first { it.id == id }.times).first()
        val time = date.atStartOfDay().plusMinutes(minute.toLong()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return MedicationLogEntity("$id:${date.toEpochDay()}:$minute", id, date.toEpochDay(), minute, time, status)
    }
    val snapshot = Snapshot(people, medications, schedules, listOf(log("a", today), log("k", today), log("m", today, LogStatus.SKIPPED),
        log("a", today.minusDays(1)), log("m", today.minusDays(1)), log("k", today.minusDays(10)), log("a", today.minusDays(35))))
    val longNames = snapshot.copy(people = people.map { it.copy(name = "${it.name} çok uzun aile üyesi adı ve soyadı") },
        schedules = schedules.map { it.copy(name = "${it.name} uzun ilaç adı ve ambalaj açıklaması") })
    fun access(notifications: Boolean = true, exact: Boolean = true, onNotification: () -> Unit = {}, onExact: () -> Unit = {}) =
        PermissionAccess(PermissionState(notifications, exact, if (notifications) PermissionAction.NONE else PermissionAction.REQUEST,
            if (exact) PermissionAction.NONE else PermissionAction.SETTINGS, false), false, onNotification, onExact)
}
