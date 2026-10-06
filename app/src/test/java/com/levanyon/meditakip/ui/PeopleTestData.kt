package com.levanyon.meditakip.ui

import com.levanyon.meditakip.data.*
import com.levanyon.meditakip.domain.*
import java.time.*

/** Synthetic fixtures only; no repository or real user data is used. */
internal object PeopleTestData {
    val date: LocalDate = LocalDate.of(2026, 10, 6)
    val person = PersonEntity("p", "Anne")
    const val longName = "Ayşe Uzun Türkçe Aile Üyesi Adı"
    const val longMedication = "Uzun İsimli Tansiyon İlacı Kontrollü Salım 50 mg"
    private fun schedule(id: String, name: String, times: String) = MedicationScheduleEntity(
        id, id, "", name, "1 tablet", "", times, 127, date.minusDays(5).toEpochDay(), null, true, date.minusDays(5).toEpochDay())
    fun log(id: String, minute: Int, skipped: Boolean = false): MedicationLogEntity = MedicationLogEntity(
        "$id:${date.toEpochDay()}:$minute", id, date.toEpochDay(), minute,
        if (skipped) 0 else date.atStartOfDay().plusMinutes(minute.toLong()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        if (skipped) LogStatus.SKIPPED else LogStatus.TAKEN)
    val snapshot = Snapshot(
        people = listOf(person, PersonEntity("k", "Kardeşim"), PersonEntity("m", "Mert")),
        medications = listOf(MedicationEntity("a", "p"), MedicationEntity("b", "p"), MedicationEntity("c", "p"), MedicationEntity("d", "k"), MedicationEntity("e", "m")),
        schedules = listOf(schedule("a", "Beloc ZOK 50 mg", "08:00,12:00"), schedule("b", "D Vitamini", "09:00"),
            schedule("c", "Levotiron 50 mcg", "08:00,20:00"), schedule("d", "Vitamin", "09:00"), schedule("e", "İlaç", "08:00,20:00")),
        logs = listOf(log("a", 480), log("a", 720), log("b", 540), log("d", 540)))
    val lowStock = snapshot.copy(medications = snapshot.medications.map {
        if (it.id == "c") it.copy(stockEnabled = true, stockQuantity = 2.0, stockThreshold = 3.0) else it
    })
    val single = snapshot.copy(people = listOf(person), medications = snapshot.medications.take(1), schedules = snapshot.schedules.take(1), logs = emptyList())
}
