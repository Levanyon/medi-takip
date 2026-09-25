package com.levanyon.meditakip.data

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import com.levanyon.meditakip.MediApp
import com.levanyon.meditakip.domain.Schedule
import com.levanyon.meditakip.reminders.ReminderActionReceiver
import com.levanyon.meditakip.reminders.ReminderLog
import com.levanyon.meditakip.reminders.Reminders
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = MediApp::class)
class ReminderIntegrationTest {
    private lateinit var app: MediApp
    private val notifications get() = app.getSystemService(NotificationManager::class.java)
    @Before fun setup() {
        app = RuntimeEnvironment.getApplication() as MediApp
        runBlocking(kotlinx.coroutines.Dispatchers.IO) { app.database.clearAllTables() }
        app.preferences.save(AppSettings()); app.preferences.snoozes = emptyMap(); app.reminderLog.clear()
    }
    @After fun close() { app.database.close() }
    private suspend fun add(time: LocalDateTime, person: PersonEntity = PersonEntity("p", "Mother"), name: String = "A") {
        app.repository.savePerson(person)
        app.repository.saveMedication(person, null, MedicationScheduleEntity("", "", person.name, name, "1 tablet", "", time.toLocalTime().toString(), 127, time.toLocalDate().toEpochDay(), null, true, LocalDate.now().toEpochDay()))
    }
    /** Pretends the alarm for [time] fired now: delivers everything due since then. */
    private suspend fun fire(time: LocalDateTime) {
        app.preferences.nextAlarm = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        app.reminders.rebuild(true)
    }
    private fun doseNotifications() = notifications.activeNotifications.filter { it.id == Reminders.DOSE_ID }
    private fun now() = LocalDateTime.now().withSecond(0).withNano(0)

    @Test fun alarmClockAndSafetyAlarmRestoreAndCancel() = runBlocking {
        add(LocalDate.now().plusDays(1).atTime(8, 0))
        val alarms = shadowOf(app.getSystemService(AlarmManager::class.java))
        app.reminders.rebuild(false)
        assertEquals(2, alarms.scheduledAlarms.size)
        assertNotNull("The next reminder is an alarm-clock alarm", alarms.scheduledAlarms.firstOrNull { it.alarmClockInfo != null })
        val first = app.preferences.nextAlarm
        app.reminders.rebuild(false)
        assertEquals(2, alarms.scheduledAlarms.size)
        assertEquals(first, app.preferences.nextAlarm)
        app.preferences.save(AppSettings(notifications = false))
        app.reminders.rebuild(false)
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }
    @Test fun dueNotificationShowsPersonFirstIsIdempotentAndTakenCancelsIt() = runBlocking {
        val time = now()
        add(time)
        fire(time)
        assertEquals(1, app.database.dao().dispatches().size)
        app.reminders.rebuild(true)
        assertEquals(1, app.database.dao().dispatches().size)
        val shown = doseNotifications().single().notification
        assertEquals("Mother · A", shown.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("1 tablet · ${time.toLocalTime()}", shown.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(listOf("İçildi", "10 dk ertele"), shown.actions.map { it.title.toString() })
        assertEquals("İlaç zamanı", shown.publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals("İlaç hatırlatmaları", notifications.getNotificationChannel("medication").name.toString())
        val item = Schedule.day(app.repository.snapshot(), LocalDate.now()).first()
        app.repository.completions.taken(item)
        app.reminders.rebuild(false)
        assertEquals(0, doseNotifications().size)
    }
    @Test fun notificationUsesCurrentPersonNameAfterRename() = runBlocking {
        // B2: the schedule revision still stores "Mother"; the notification must use the people table.
        val time = now()
        add(time)
        app.repository.savePerson(PersonEntity("p", "Annem"))
        fire(time)
        assertEquals("Annem · A", doseNotifications().single().notification.extras.getString(Notification.EXTRA_TITLE))
    }
    @Test fun takenActionFromNotificationRecordsTheDoseWithoutOpeningTheApp() = runBlocking {
        val time = now()
        add(time)
        fire(time)
        val key = doseNotifications().single().tag
        val intent = Intent(app, ReminderActionReceiver::class.java).setAction(Reminders.ACTION_TAKEN).putExtra(Reminders.EXTRA_KEY, key)
        app.reminders.markTakenFromNotification(intent.getStringExtra(Reminders.EXTRA_KEY)!!)
        val log = app.repository.snapshot().logs.single()
        assertEquals(key, log.key)
        assertEquals(0, doseNotifications().size)
        // Repeating the action (e.g. double tap) never creates a second record.
        app.reminders.markTakenFromNotification(key)
        assertEquals(1, app.repository.snapshot().logs.size)
    }
    @Test fun snoozeHidesTheNotificationAndSchedulesItTenMinutesLater() = runBlocking {
        val time = now()
        add(time)
        fire(time)
        val key = doseNotifications().single().tag
        val pressed = Instant.now()
        app.reminders.snooze(key, pressed)
        assertEquals(0, doseNotifications().size)
        val snoozedAt = app.preferences.snoozes.getValue(key)
        assertEquals(pressed.plus(Duration.ofMinutes(10)).toEpochMilli(), snoozedAt)
        assertEquals("Next alarm is the snooze (before the 30 min follow-up)", snoozedAt, app.preferences.nextAlarm)
        // When the snooze alarm fires, the reminder comes back marked as snoozed.
        app.reminders.rebuild(false)
        app.preferences.nextAlarm = snoozedAt
        val later = LocalDateTime.ofInstant(Instant.ofEpochMilli(snoozedAt), ZoneId.systemDefault())
        assertTrue(later.isAfter(time))
        // Marking the dose clears the snooze.
        app.repository.completions.taken(Schedule.day(app.repository.snapshot(), LocalDate.now()).first())
        app.reminders.rebuild(false)
        assertTrue(app.preferences.snoozes.isEmpty())
    }
    @Test fun sameTimeDosesForTwoPeopleGetOneSummary() = runBlocking {
        val time = now()
        add(time, PersonEntity("p", "Anne"), "Parol")
        add(time, PersonEntity("k", "Kardeş"), "Vitamin")
        fire(time)
        assertEquals(2, doseNotifications().size)
        val summary = notifications.activeNotifications.single { it.id == Reminders.SUMMARY_ID }.notification
        assertTrue(summary.extras.getString(Notification.EXTRA_TITLE)!!.startsWith("2 ilaç zamanı"))
        assertEquals(1, doseNotifications().map { it.notification.group }.distinct().size)
    }
    @Test fun oldDispatchRecordsArePruned() = runBlocking {
        val dao = app.database.dao()
        dao.dispatched(DispatchEntity("old", Instant.now().minus(Duration.ofDays(30)).toEpochMilli()))
        dao.dispatched(DispatchEntity("recent", Instant.now().minus(Duration.ofDays(1)).toEpochMilli()))
        app.reminders.rebuild(false)
        assertEquals(listOf("recent"), dao.dispatches().map { it.key })
    }
    @Test fun testNotificationUsesTheReminderChannel() {
        assertTrue(app.reminders.sendTestNotification())
        val test = notifications.activeNotifications.single { it.id == Reminders.TEST_ID }.notification
        assertEquals("MediTakip test bildirimi", test.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("medication", test.channelId)
        val at = app.reminders.scheduleTestReminder()
        assertTrue(shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms.any { it.triggerAtTime == at.toEpochMilli() })
    }
    @Test fun reminderLogKeepsTheLatestFiftyProblems() {
        val log = ReminderLog(File(app.filesDir, "log-test.txt")).also { it.clear() }
        repeat(60) { log.record("alarm", IllegalStateException("problem $it")) }
        assertEquals(50, log.entries().size)
        assertEquals("IllegalStateException: problem 59", log.latest()!!.message)
        assertEquals("alarm", log.latest()!!.place)
    }
    @Test fun occurrenceDateIsReadFromTheKey() {
        assertEquals(LocalDate.ofEpochDay(20352), Reminders.occurrenceDate("abc-123:20352:480"))
        assertNull(Reminders.occurrenceDate("broken"))
    }
}
