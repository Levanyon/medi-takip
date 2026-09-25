package com.levanyon.meditakip.domain

import com.levanyon.meditakip.data.*
import org.junit.Assert.*
import org.junit.Test

/** Stock math (Phase 3, DECISIONS D3.7). */
class StockTest {
    private val daily = MedicationScheduleEntity("s", "m", "", "Parol", "1 tablet", "", "08:00,20:00", 127, 0, null, true, 0)
    private fun med(quantity: Double, perDose: Double = 1.0, threshold: Double = 0.0, enabled: Boolean = true) =
        MedicationEntity("m", "p", stockEnabled = enabled, stockQuantity = quantity, stockPerDose = perDose, stockThreshold = threshold)

    @Test fun dosesPerDayAveragesOverTheWeek() {
        assertEquals(2.0, StockPlan.dosesPerDay(daily), 1e-9)
        assertEquals(2 * 5 / 7.0, StockPlan.dosesPerDay(daily.copy(days = 0b0011111)), 1e-9)
        assertEquals(0.0, StockPlan.dosesPerDay(daily.copy(active = false)), 1e-9)
    }
    @Test fun daysLeftRoundsDown() {
        assertEquals(5, StockPlan.daysLeft(med(11.0), daily))           // 11 tablets, 2 a day
        assertEquals(10, StockPlan.daysLeft(med(10.0, perDose = 0.5), daily))
        assertEquals(0, StockPlan.daysLeft(med(-2.0), daily))           // below zero shows as 0
        assertNull(StockPlan.daysLeft(med(10.0, enabled = false), daily))
        assertNull(StockPlan.daysLeft(med(10.0), daily.copy(active = false)))
    }
    @Test fun lowNeedsAThreshold() {
        assertFalse("No threshold, no warning", StockPlan.isLow(med(1.0)))
        assertTrue(StockPlan.isLow(med(6.0, threshold = 6.0)))
        assertFalse(StockPlan.isLow(med(6.5, threshold = 6.0)))
        assertFalse(StockPlan.isLow(med(1.0, threshold = 6.0, enabled = false)))
    }
    @Test fun lowListsOnlyActivePeopleAndMedications() {
        val snapshot = Snapshot(
            listOf(PersonEntity("p", "Anne"), PersonEntity("x", "Eski", archived = true)),
            listOf(med(4.0, threshold = 6.0), MedicationEntity("a", "p", archived = true, stockEnabled = true, stockQuantity = 1.0, stockThreshold = 5.0),
                MedicationEntity("z", "x", stockEnabled = true, stockQuantity = 1.0, stockThreshold = 5.0)),
            listOf(daily, daily.copy(id = "s2", medicationId = "a"), daily.copy(id = "s3", medicationId = "z")))
        val low = StockPlan.low(snapshot)
        assertEquals(listOf("m"), low.map { it.medicationId })
        assertEquals(LowStock("m", "p", "Anne", "Parol", 4.0, 2), low.single())
        assertTrue(StockPlan.low(snapshot, personId = "x").isEmpty())
    }
    @Test fun turkishNumbers() {
        assertEquals("12", StockPlan.format(12.0))
        assertEquals("11,5", StockPlan.format(11.5))
        assertEquals("0,25", StockPlan.format(0.25))
        assertEquals("0", StockPlan.format(-1.0))
        assertEquals(11.5, StockPlan.parse("11,5")!!, 1e-9)
        assertEquals(11.5, StockPlan.parse(" 11.5 ")!!, 1e-9)
        assertNull(StockPlan.parse("on iki"))
    }
    @Test fun settingsValidation() {
        assertTrue(StockSettings(false, -1.0, 0.0, -1.0).valid)
        assertTrue(StockSettings(true, 0.0, 0.5, 0.0).valid)
        assertFalse(StockSettings(true, 10.0, 0.0, 2.0).valid)
        assertFalse(StockSettings(true, -1.0, 1.0, 2.0).valid)
    }
}
