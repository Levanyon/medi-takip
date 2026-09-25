package com.levanyon.meditakip.data

import android.app.AlarmManager
import android.app.NotificationManager
import com.levanyon.meditakip.MediApp
import com.levanyon.meditakip.domain.Schedule
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = MediApp::class)
class ReminderIntegrationTest {
    private lateinit var app: MediApp
    @Before fun setup() { app = RuntimeEnvironment.getApplication() as MediApp; runBlocking(kotlinx.coroutines.Dispatchers.IO) { app.database.clearAllTables() }; app.preferences.save(AppSettings()) }
    @After fun close() { app.database.close() }
    private suspend fun add(time: LocalDateTime) {
        val person = PersonEntity("p", "Mother")
        app.repository.savePerson(person)
        app.repository.saveMedication(person, null, MedicationScheduleEntity("", "", "Mother", "A", "text", "", time.toLocalTime().toString(), 127, time.toLocalDate().toEpochDay(), null, true, LocalDate.now().toEpochDay()))
    }
    @Test fun exactAndSafetyAlarmRestoreAndCancel() = runBlocking {
        add(LocalDate.now().plusDays(1).atTime(8, 0))
        val alarms = shadowOf(app.getSystemService(AlarmManager::class.java))
        app.reminders.rebuild(false)
        assertEquals(2, alarms.scheduledAlarms.size)
        val first = app.preferences.nextAlarm
        app.reminders.rebuild(false)
        assertEquals(2, alarms.scheduledAlarms.size)
        assertEquals(first, app.preferences.nextAlarm)
        app.preferences.save(AppSettings(notifications = false))
        app.reminders.rebuild(false)
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }
    @Test fun dueNotificationIsIdempotentAndTakenCancelsIt() = runBlocking {
        val time = LocalDateTime.now().withSecond(0).withNano(0)
        add(time)
        app.preferences.nextAlarm = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        app.reminders.rebuild(true)
        assertEquals(1, app.database.dao().dispatches().size)
        app.reminders.rebuild(true)
        assertEquals(1, app.database.dao().dispatches().size)
        val notificationManager = app.getSystemService(NotificationManager::class.java)
        assertEquals(1, notificationManager.activeNotifications.size)
        assertEquals("İlaç zamanı 💊", notificationManager.activeNotifications.single().notification.extras.getString(android.app.Notification.EXTRA_TITLE))
        assertEquals("İlaç hatırlatmaları", notificationManager.getNotificationChannel("medication").name.toString())
        val item = Schedule.day(app.repository.snapshot(), LocalDate.now()).first()
        app.repository.completions.taken(item)
        app.reminders.rebuild(false)
        assertEquals(0, notificationManager.activeNotifications.size)
    }
    @Test fun notificationUsesCurrentPersonNameAfterRename() = runBlocking {
        // B2: the schedule revision still stores "Mother"; the notification must use the people table.
        val time = LocalDateTime.now().withSecond(0).withNano(0)
        add(time)
        app.repository.savePerson(PersonEntity("p", "Annem"))
        app.preferences.nextAlarm = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        app.reminders.rebuild(true)
        val text = app.getSystemService(NotificationManager::class.java).activeNotifications.single().notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString()
        assertTrue("Notification should name the renamed person: $text", text.startsWith("Annem • A"))
        assertFalse(text.contains("Mother"))
    }
}
