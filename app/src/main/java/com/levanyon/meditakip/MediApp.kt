package com.levanyon.meditakip

import android.app.Application
import androidx.room.Room
import com.levanyon.meditakip.data.*

class MediApp : Application() {
    val database by lazy { Room.databaseBuilder(this, MediDatabase::class.java, "meditakip.db").build() }
    val repository by lazy { Repository(database) }
    val preferences by lazy { Preferences(this) }
    val reminders by lazy { com.levanyon.meditakip.reminders.Reminders(this) }
    override fun onCreate() { super.onCreate(); reminders.channel() }
}
