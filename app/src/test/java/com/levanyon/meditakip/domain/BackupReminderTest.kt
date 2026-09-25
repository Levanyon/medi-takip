package com.levanyon.meditakip.domain

import org.junit.Test
import org.junit.Assert.*
import java.time.Duration

class BackupReminderTest {
    private val start = 1_800_000_000_000L
    private val period = Duration.ofDays(30).toMillis()
    @Test fun firstReminderWaitsThirtyDaysAndNeedsData() {
        assertFalse(BackupReminder.due(true, 0, start, start + period - 1))
        assertTrue(BackupReminder.due(true, 0, start, start + period))
        assertFalse(BackupReminder.due(false, 0, start, start + period))
    }
    @Test fun successfulBackupRestartsPeriodAndClockRollbackDoesNotNag() {
        assertFalse(BackupReminder.due(true, start + period, start, start + period + 1))
        assertTrue(BackupReminder.due(true, start + period, start, start + 2 * period))
        assertFalse(BackupReminder.due(true, start, start, start - 1))
    }
}
