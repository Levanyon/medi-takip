package com.levanyon.meditakip.reminders

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import com.levanyon.meditakip.*
import com.levanyon.meditakip.data.DispatchEntity
import com.levanyon.meditakip.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.*

class Reminders(private val app: MediApp) {
    private val lock = Mutex()
    private val alarms = app.getSystemService(AlarmManager::class.java)
    private val notifications = app.getSystemService(NotificationManager::class.java)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    fun channel() { notifications.createNotificationChannel(NotificationChannel("medication", app.getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH).apply { description = app.getString(R.string.channel_description); lockscreenVisibility = Notification.VISIBILITY_PRIVATE }) }
    fun exactAllowed() = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
    fun notificationAllowed(): Boolean = notifications.areNotificationsEnabled() && (Build.VERSION.SDK_INT < 33 || app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) && notifications.getNotificationChannel("medication")?.importance != NotificationManager.IMPORTANCE_NONE
    private fun pending(code: Int) = PendingIntent.getBroadcast(app, code, Intent(app, ReminderReceiver::class.java).setAction("com.levanyon.meditakip.ALARM"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun refresh() { scope.launch { runCatching { rebuild(true) } } }
    suspend fun rebuild(deliver: Boolean) = lock.withLock {
        channel()
        val now = Instant.now()
        val snapshot = app.repository.snapshot()
        val settings = app.preferences.state.value
        val events = ReminderPlan.events(snapshot, settings, now, ZoneId.systemDefault())
        val dispatched = app.database.dao().dispatches().map { it.key }.toSet()
        val lower = app.preferences.nextAlarm
        if (deliver && lower > 0 && notificationAllowed()) {
            events.filter { it.key !in dispatched && it.at.toEpochMilli() >= lower && !it.at.isAfter(now) && it.at >= now.minusSeconds(86400) }.forEach { event ->
                show(event)
                app.database.dao().dispatched(DispatchEntity(event.key, now.toEpochMilli()))
            }
        }
        // Removing or completing an occurrence also removes its visible notification.
        val outstanding = events.map { it.occurrence.key }.toSet()
        notifications.activeNotifications.filter { it.tag != null && it.tag !in outstanding }.forEach { notifications.cancel(it.tag, 1) }
        alarms.cancel(pending(1)); alarms.cancel(pending(2))
        val next = events.firstOrNull { it.at > now && it.key !in dispatched }
        if (!settings.notifications || !notificationAllowed() || next == null) { app.preferences.nextAlarm = 0; return@withLock }
        val timestamp = next.at.toEpochMilli()
        app.preferences.nextAlarm = timestamp
        if (exactAllowed()) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timestamp, pending(1))
                // Independent inexact backup remains if Android later cancels exact alarms.
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timestamp + 15 * 60_000, pending(2))
            } catch (_: SecurityException) { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timestamp, pending(2)) }
        } else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timestamp, pending(2))
    }
    private fun show(event: ReminderEvent) {
        val o = event.occurrence
        val summary = app.getString(R.string.notification_person_medication, o.schedule.personName, o.schedule.name)
        val time = app.getString(R.string.notification_time, o.time.toString())
        val content = "$summary\n${TurkishFormat.date(o.date)} • $time" + if (event.retry) "\n" + app.getString(R.string.notification_retry_body, o.schedule.name) else ""
        val open = PendingIntent.getActivity(app, 0, Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(app, "medication").setSmallIcon(R.drawable.ic_medication).setContentTitle(app.getString(if (event.retry) R.string.notification_retry_title else R.string.notification_title)).setContentText("$summary • $time").setStyle(Notification.BigTextStyle().bigText(content)).setContentIntent(open).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).setCategory(Notification.CATEGORY_REMINDER).build()
        notifications.notify(o.key, 1, notification)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val accepted = mutableSetOf("com.levanyon.meditakip.ALARM", Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)
        if (Build.VERSION.SDK_INT >= 31) accepted.add(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)
        if (intent.action !in accepted) return
        val result = goAsync()
        val app = context.applicationContext as MediApp
        app.reminders.scope.launch {
            try { app.reminders.rebuild(intent.action == "com.levanyon.meditakip.ALARM") }
            catch (_: Exception) { android.util.Log.e("MediTakip", "Reminder recovery failed; reopening the app retries scheduling.") }
            finally { result.finish() }
        }
    }
}
