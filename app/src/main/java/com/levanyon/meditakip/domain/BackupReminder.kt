package com.levanyon.meditakip.domain

import java.time.Duration

object BackupReminder {
    fun due(hasPeople: Boolean, lastBackupAt: Long, startedAt: Long, now: Long): Boolean {
        val since = lastBackupAt.takeIf { it > 0 } ?: startedAt
        return hasPeople && since > 0 && now >= since && now - since >= Duration.ofDays(30).toMillis()
    }
}
