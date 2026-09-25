package com.levanyon.meditakip

import android.app.Application
import androidx.room.Room
import com.levanyon.meditakip.data.*

class MediApp : Application() {
    // Explicit migrations only: an unknown version makes Room fail loudly instead of wiping data.
    val database by lazy { Room.databaseBuilder(this, MediDatabase::class.java, "meditakip.db").addMigrations(*ALL_MIGRATIONS).build() }
    val repository by lazy { Repository(database) }
    val preferences by lazy { Preferences(this) }
    val reminderLog by lazy { com.levanyon.meditakip.reminders.ReminderLog(java.io.File(filesDir, "reminder-log.txt")) }
    val reminders by lazy { com.levanyon.meditakip.reminders.Reminders(this) }
    val backups by lazy { Backups(repository, preferences, filesDir, packageManager.getPackageInfo(packageName, 0).versionName ?: "") }
    override fun onCreate() { super.onCreate(); reminders.channel() }
}
