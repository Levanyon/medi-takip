package com.levanyon.meditakip.data

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import com.levanyon.meditakip.MediApp
import com.levanyon.meditakip.domain.*
import com.levanyon.meditakip.reminders.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.*
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=MediApp::class)
class RenewalIntegrationTest {
    private val app get()=RuntimeEnvironment.getApplication() as MediApp
    private val notifications get()=app.getSystemService(NotificationManager::class.java)
    private val alarms get()=shadowOf(app.getSystemService(AlarmManager::class.java))
    private val date=LocalDate.of(2026,10,20)
    private val d=date.toEpochDay()
    private fun engine(at:LocalDateTime)=Reminders(app,Clock.fixed(at.atZone(ZoneId.systemDefault()).toInstant(),ZoneOffset.UTC))
    private suspend fun seed() {
        app.repository.savePerson(PersonEntity("p","Anne"))
        app.database.dao().saveMedication(MedicationEntity("m","p",stockEnabled=true,stockQuantity=20.0,renewalDate=d,renewalReminderEnabled=true,renewalLeadDays=2))
        app.database.dao().saveSchedule(MedicationScheduleEntity("s","m","Anne","Beloc","1 tablet","","08:00",127,d-10,null,true,d-10))
    }
    private suspend fun change(transform:(MedicationEntity)->MedicationEntity) { app.database.dao().saveMedication(transform(app.repository.snapshot().medications.single())) }
    @After fun close() = runBlocking { app.reminders.scope.coroutineContext[Job]?.cancelAndJoin(); app.database.close() }
    @Test fun renewalSharesAlarmPairAndDateChangesOrClearsReplacePendingEvent() = runBlocking {
        seed(); val engine=engine(date.minusDays(3).atTime(23,0))
        // Move doses beyond renewal so the shared next alarm is the renewal itself.
        val dao=app.database.dao(); dao.saveSchedule(dao.schedules().single().copy(start=d+10))
        engine.rebuild(false)
        fun expected(day:Long)=LocalDate.ofEpochDay(day).atTime(9,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(expected(d-2),app.preferences.nextAlarm); assertEquals(2,alarms.scheduledAlarms.size)
        change { it.copy(renewalDate=d+1) }; engine.rebuild(false)
        assertEquals(expected(d-1),app.preferences.nextAlarm)
        change { it.copy(renewalDate=null) }; engine.rebuild(false)
        assertNotEquals(expected(d-1),app.preferences.nextAlarm)
        assertEquals(2,alarms.scheduledAlarms.size)
    }
    @Test fun deliveryIsPrivateUniqueNavigableIdempotentAndNeverChangesDoseHistoryOrStock() = runBlocking {
        seed(); val before=app.repository.snapshot()
        val at=date.minusDays(2).atTime(9,0); val engine=engine(at.plusMinutes(1))
        app.preferences.nextAlarm=at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        engine.rebuild(true); engine.rebuild(true)
        val notification=notifications.activeNotifications.single { it.id==Reminders.RENEWAL_ID }
        assertNotEquals(Reminders.DOSE_ID,notification.id)
        assertTrue(notification.tag.startsWith("renewal:"))
        assertEquals(Notification.VISIBILITY_PRIVATE,notification.notification.visibility)
        assertFalse(notification.notification.publicVersion.extras.toString().contains("Beloc"))
        assertTrue(notification.notification.actions.isNullOrEmpty())
        val intent=shadowOf(notification.notification.contentIntent).savedIntent
        assertEquals("m",intent.getStringExtra(Reminders.EXTRA_MEDICATION_ID))
        assertEquals("renewal",intent.data?.host)
        assertEquals(before.logs,app.repository.snapshot().logs)
        assertEquals(before.medications,app.repository.snapshot().medications)
        assertEquals(1,app.database.dao().dispatches().count { it.key.startsWith("renewal:") })
        change { it.copy(renewalDate=d+10) }; engine.rebuild(true)
        assertTrue(notifications.activeNotifications.none { it.id==Reminders.RENEWAL_ID })
    }
    @Test fun disabledArchivedInactiveAndPastRenewalNeverDeliver() = runBlocking {
        seed(); val engine=engine(date.minusDays(2).atTime(9,1))
        val original=app.repository.snapshot().medications.single()
        listOf(original.copy(renewalReminderEnabled=false),original.copy(archived=true),original.copy(renewalDate=d-5)).forEach {
            app.database.dao().saveMedication(it); app.preferences.nextAlarm=1; engine.rebuild(true)
            assertTrue(notifications.activeNotifications.none { n -> n.id==Reminders.RENEWAL_ID })
        }
        app.database.dao().saveMedication(original)
        app.database.dao().saveSchedule(app.database.dao().schedules().single().copy(active=false))
        engine.rebuild(false); assertEquals(0L,app.preferences.nextAlarm)
    }
    @Test fun rebootUpdateClockAndTimezoneHooksRebuildSharedAlarm() = runBlocking {
        val tomorrow=LocalDate.now().plusDays(2).toEpochDay()
        seed(); change { it.copy(renewalDate=tomorrow,renewalLeadDays=0) }
        app.database.dao().saveSchedule(app.database.dao().schedules().single().copy(start=tomorrow+10))
        val old=TimeZone.getDefault()
        try {
            for(action in listOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_MY_PACKAGE_REPLACED,Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED)) {
                if(action==Intent.ACTION_TIMEZONE_CHANGED) TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"))
                app.preferences.nextAlarm=0
                app.sendBroadcast(Intent(app,ReminderReceiver::class.java).setAction(action))
                shadowOf(android.os.Looper.getMainLooper()).idle()
                withTimeout(5000) { while(app.preferences.nextAlarm==0L) delay(10) }
                app.reminders.scope.coroutineContext[Job]!!.children.toList().joinAll()
                assertEquals(LocalDate.ofEpochDay(tomorrow).atTime(9,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),app.preferences.nextAlarm)
            }
        } finally { TimeZone.setDefault(old) }
    }
}
