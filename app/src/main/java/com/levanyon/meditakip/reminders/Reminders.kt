package com.levanyon.meditakip.reminders

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import com.levanyon.meditakip.*
import com.levanyon.meditakip.data.DispatchEntity
import com.levanyon.meditakip.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.*

/** Snapshot of everything reminders depend on, for the Hatırlatma Durumu screen and the Bugün banner. */
data class ReminderHealth(
    val remindersOn: Boolean,
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val batteryUnrestricted: Boolean,
    val samsung: Boolean,
    val nextReminderAt: Long?,
    val lastProblem: ReminderLog.Entry?,
) {
    /** Problems that stop reminders from arriving on time. Battery optimization is advice, not a blocker. */
    val working get() = remindersOn && notifications && exactAlarms
}

/**
 * Executes [ReminderPlan] decisions on Android: one alarm-clock alarm for the next reminder (plus an
 * inexact safety alarm), notifications with İçildi / 10 dk ertele actions, and cleanup.
 * See docs/DECISIONS.md D2.1–D2.9.
 */
class Reminders(private val app: MediApp, private val clock: Clock = Clock.systemUTC()) {
    private val lock = Mutex()
    private val alarms = app.getSystemService(AlarmManager::class.java)
    private val notifications = app.getSystemService(NotificationManager::class.java)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun channel() { notifications.createNotificationChannel(NotificationChannel(CHANNEL, app.getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH).apply { description = app.getString(R.string.channel_description); lockscreenVisibility = Notification.VISIBILITY_PRIVATE }) }
    fun exactAllowed() = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
    fun notificationAllowed(): Boolean = notifications.areNotificationsEnabled() && (Build.VERSION.SDK_INT < 33 || app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) && notifications.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    fun batteryUnrestricted(): Boolean = app.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(app.packageName) ?: true
    fun health() = ReminderHealth(app.preferences.state.value.notifications, notificationAllowed(), exactAllowed(), batteryUnrestricted(),
        Build.MANUFACTURER.equals("samsung", ignoreCase = true), app.preferences.nextReminderEvent.takeIf { it > 0 }, app.reminderLog.latest())

    private fun alarmIntent(code: Int, action: String = ACTION_ALARM) = PendingIntent.getBroadcast(app, code, Intent(app, ReminderReceiver::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun openApp() = PendingIntent.getActivity(app, 0, Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Rebuilds reminders in the background; failures are recorded, never swallowed (AUDIT R3). */
    fun refresh() { scope.launch { try { rebuild(true) } catch (e: Exception) { if (e is CancellationException) throw e; app.reminderLog.record("refresh", e) } } }

    suspend fun rebuild(deliver: Boolean) = lock.withLock {
        channel()
        val now = clock.instant()
        val dao = app.database.dao()
        dao.pruneDispatches(now.minus(Duration.ofDays(DISPATCH_RETENTION_DAYS)).toEpochMilli())
        val snapshot = app.repository.snapshot()
        val settings = app.preferences.state.value
        val events = ReminderPlan.events(snapshot, settings, now, ZoneId.systemDefault(), app.preferences.snoozes)
        val renewals = RenewalPlan.events(snapshot, settings, now, ZoneId.systemDefault())
        val outstanding = events.map { it.occurrence.key }.toSet()
        app.preferences.snoozes = ReminderPlan.liveSnoozes(app.preferences.snoozes, outstanding, now)
        val dispatched = dao.dispatches().map { it.key }.toSet()
        if (deliver && notificationAllowed()) {
            val due = ReminderPlan.due(events, dispatched, app.preferences.nextAlarm, now)
            due.forEach { event -> show(event, now); dao.dispatched(DispatchEntity(event.key, now.toEpochMilli())) }
            RenewalPlan.due(renewals, dispatched, app.preferences.nextAlarm, now).forEach { event ->
                showRenewal(event); dao.dispatched(DispatchEntity(event.key, now.toEpochMilli()))
            }
        }
        val renewalKeys = renewals.map { it.key }.toSet()
        notifications.activeNotifications.filter { it.id == RENEWAL_ID && it.tag !in renewalKeys }.forEach { notifications.cancel(it.tag, RENEWAL_ID) }
        // A dose that was marked, skipped, edited or archived loses its visible notification.
        notifications.activeNotifications.filter { it.id == DOSE_ID && it.tag != null && it.tag !in outstanding }.forEach { notifications.cancel(it.tag, DOSE_ID) }
        notifyLowStock(snapshot)
        updatePrivacy(snapshot)
        updateSummaries(snapshot)
        LockScreenNotifications.refresh(app, snapshot, now, events)
        alarms.cancel(alarmIntent(1)); alarms.cancel(alarmIntent(2))
        // Reuse the one existing alarm path for the daily status rollover, never a second scheduler.
        val today = now.atZone(ZoneId.systemDefault()).toLocalDate()
        val rollover = if (app.preferences.lockScreen.value.visible && NextDose.next(snapshot, today)?.date == today)
            now.atZone(ZoneId.systemDefault()).toLocalDate().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant() else null
        val reminderNext = if (notificationAllowed()) listOfNotNull(ReminderPlan.next(events, dispatched, now)?.at, RenewalPlan.next(renewals, dispatched, now)?.at).minOrNull() else null
        app.preferences.nextReminderEvent = if (settings.notifications) reminderNext?.toEpochMilli() ?: 0 else 0
        val statusRollover = rollover?.takeIf { notifications.activeNotifications.any { active -> active.id == LockScreenNotifications.ID } }
        val next = listOfNotNull(reminderNext, statusRollover).minOrNull()
        if (!settings.notifications || next == null) { app.preferences.nextAlarm = 0; return@withLock }
        val timestamp = next.toEpochMilli()
        app.preferences.nextAlarm = timestamp
        schedule(timestamp, alarmIntent(1))
        // Independent inexact safety alarm in case the alarm-clock alarm is ever dropped.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timestamp + SAFETY_DELAY_MILLIS, alarmIntent(2))
    }

    /** Alarm-clock alarm (DECISIONS D2.2); falls back to an inexact alarm if Android refuses it. */
    private fun schedule(timestamp: Long, operation: PendingIntent) {
        if (exactAllowed()) {
            try { alarms.setAlarmClock(AlarmManager.AlarmClockInfo(timestamp, openApp()), operation); return }
            catch (e: SecurityException) { app.reminderLog.record("schedule", e) }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timestamp, operation)
    }

    private fun show(event: ReminderEvent, now: Instant) {
        val o = event.occurrence
        val today = now.atZone(ZoneId.systemDefault()).toLocalDate()
        val time = o.time.toString() + if (o.date != today) " (${TurkishFormat.shortDate(o.date)})" else ""
        val detailed = app.preferences.lockScreen.value.detailed
        val title = if (detailed) app.getString(if (event.kind == ReminderKind.RETRY) R.string.notification_followup_title else R.string.notification_dose_title, o.personName, o.schedule.name) else "İlaç zamanı"
        val text = if (detailed) app.getString(if (event.kind == ReminderKind.RETRY) R.string.notification_followup_text else R.string.notification_dose_text, o.schedule.dose, time) else "Doz saati ${LockScreenNotifications.timing(o)}"
        val public = Notification.Builder(app, CHANNEL).setSmallIcon(R.drawable.ic_medication)
            .setContentTitle(app.getString(R.string.app_name)).setContentText(app.getString(R.string.notification_public_text)).build()
        val builder = Notification.Builder(app, CHANNEL).setSmallIcon(R.drawable.ic_medication)
            .setContentTitle(title).setContentText(text).setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(LockScreenNotifications.open(app, o.key)).setAutoCancel(true)
            .setVisibility(LockScreenNotifications.visibility(app.preferences.lockScreen.value)).setPublicVersion(public)
            .setCategory(Notification.CATEGORY_REMINDER).setWhen(event.at.toEpochMilli()).setShowWhen(true)
            .setGroup(ReminderPlan.groupKey(event)).setSortKey(if (detailed) "${o.personName}\u0000${o.schedule.name}" else o.key)
        if (app.preferences.lockScreen.value.takenAction) builder.addAction(LockScreenNotifications.takenAction(app, o.key))
        builder.addAction(Notification.Action.Builder(Icon.createWithResource(app, R.drawable.ic_medication), app.getString(R.string.action_snooze), action(ACTION_SNOOZE, o.key)).build())
        if (event.kind == ReminderKind.SNOOZE) builder.setSubText(app.getString(R.string.notification_snoozed))
        notifications.notify(o.key, DOSE_ID, builder.build())
    }

    private fun showRenewal(event: RenewalEvent) {
        val intent = Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .setData(Uri.parse("meditakip://renewal/${Uri.encode(event.medicationId)}"))
            .putExtra(EXTRA_MEDICATION_ID, event.medicationId)
        val open = PendingIntent.getActivity(app, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val public = Notification.Builder(app, CHANNEL).setSmallIcon(R.drawable.ic_medication)
            .setContentTitle(app.getString(R.string.app_name)).build()
        val notification = Notification.Builder(app, CHANNEL).setSmallIcon(R.drawable.ic_medication)
            .renewalContent(event)
            .setContentIntent(open).setAutoCancel(true).setVisibility(LockScreenNotifications.visibility(app.preferences.lockScreen.value)).setPublicVersion(public)
            .setCategory(Notification.CATEGORY_REMINDER).setWhen(event.at.toEpochMilli()).build()
        notifications.notify(event.key, RENEWAL_ID, notification)
    }

    /**
     * One notification when a medication becomes low; not repeated until it has recovered (e.g. Stok ekle).
     * A medication only counts as notified once the notification was actually shown.
     */
    private fun notifyLowStock(snapshot: Snapshot) {
        val low = StockPlan.low(snapshot)
        val lowIds = low.map { it.medicationId }.toSet()
        val already = app.preferences.lowStockNotified intersect lowIds
        val canShow = app.preferences.state.value.notifications && notificationAllowed()
        val shown = if (!canShow) emptySet() else low.filter { it.medicationId !in already }.onEach { item ->
            val notification = Notification.Builder(app, CHANNEL).setSmallIcon(R.drawable.ic_medication)
                .lowStockContent(item)
                .setContentIntent(openApp()).setAutoCancel(true).setVisibility(LockScreenNotifications.visibility(app.preferences.lockScreen.value))
                .setPublicVersion(Notification.Builder(app, CHANNEL).setSmallIcon(R.drawable.ic_medication).setContentTitle(app.getString(R.string.app_name)).build())
                .setCategory(Notification.CATEGORY_REMINDER).build()
            notifications.notify(item.medicationId, LOW_STOCK_ID, notification)
        }.map { it.medicationId }.toSet()
        app.preferences.lowStockNotified = already + shown
        notifications.activeNotifications.filter { it.id == LOW_STOCK_ID && it.tag !in lowIds }.forEach { notifications.cancel(it.tag, LOW_STOCK_ID) }
    }

    private fun Notification.Builder.renewalContent(event: RenewalEvent): Notification.Builder {
        val detailed = app.preferences.lockScreen.value.detailed
        return setContentTitle(if (detailed) app.getString(R.string.renewal_notification_title) else "Reçete hatırlatması")
            .setContentText(if (detailed) app.getString(R.string.renewal_notification_text, event.name) else "Yaklaşan bir reçete yenileme hatırlatman var.")
    }

    private fun Notification.Builder.lowStockContent(item: LowStock): Notification.Builder {
        val detailed = app.preferences.lockScreen.value.detailed
        val text = if (!detailed) "Bir ilaç için stok uyarısı var." else item.daysLeft?.let {
            app.getString(R.string.stock_low_notification_text, item.personName, it)
        } ?: app.getString(R.string.stock_low_notification_text_no_days, item.personName, StockPlan.format(item.quantity))
        return setContentTitle(if (detailed) app.getString(R.string.stock_low_notification_title, item.name) else "Stok azalıyor")
            .setContentText(text)
    }

    /** One summary per group of two or more visible doses (DECISIONS D2.3); removed when the group shrinks. */
    private fun updateSummaries(snapshot: Snapshot) {
        val active = notifications.activeNotifications
        val groups = active.filter { it.id == DOSE_ID }.mapNotNull { posted ->
            posted.tag?.let { NextDose.resolve(snapshot, it) }?.let { posted to it }
        }.groupBy { it.first.notification.group }
        active.filter { it.id == SUMMARY_ID && (groups[it.tag]?.size ?: 0) < 2 }.forEach { notifications.cancel(it.tag, SUMMARY_ID) }
        groups.filter { (group, members) -> group != null && members.size >= 2 }.forEach { (group, members) ->
            // Never use cached notification text as medical identity, even after a cancellation.
            val lines = members.map { (posted, item) ->
                val retry = posted.notification.`when` > item.instant(ZoneId.systemDefault()).toEpochMilli() &&
                    posted.notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT) == null
                if (app.preferences.lockScreen.value.detailed) app.getString(
                    if (retry) R.string.notification_followup_title else R.string.notification_dose_title,
                    item.personName, item.schedule.name) else "İlaç zamanı"
            }.sorted()
            val time = members.first().first.notification.`when`.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime().withSecond(0).withNano(0) }
            val title = app.getString(R.string.notification_group_title, members.size, time.toString())
            val style = Notification.InboxStyle().setBigContentTitle(title).also { s -> lines.forEach { s.addLine(it) } }
            val summary = Notification.Builder(app, CHANNEL).setSmallIcon(R.drawable.ic_medication).setContentTitle(title)
                .setContentText(lines.joinToString(", ")).setStyle(style).setGroup(group).setGroupSummary(true)
                .setContentIntent(openApp()).setAutoCancel(true).setVisibility(LockScreenNotifications.visibility(app.preferences.lockScreen.value))
                .setPublicVersion(Notification.Builder(app, CHANNEL).setSmallIcon(R.drawable.ic_medication).setContentTitle(app.getString(R.string.app_name)).setContentText(app.getString(R.string.notification_public_text)).build())
                .setCategory(Notification.CATEGORY_REMINDER).setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN).build()
            notifications.notify(group, SUMMARY_ID, summary)
        }
    }

    private fun action(name: String, key: String): PendingIntent {
        // A distinct data URI per dose and action keeps each PendingIntent separate.
        val intent = Intent(app, ReminderActionReceiver::class.java).setAction(name).setData(Uri.parse("meditakip://dose/${Uri.encode(key)}/$name")).putExtra(EXTRA_KEY, key)
        return PendingIntent.getBroadcast(app, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Apply a privacy change to already-visible reminders without another sound or delivery record. */
    private fun updatePrivacy(snapshot: Snapshot) {
        val privacy = app.preferences.lockScreen.value
        val renewals = RenewalPlan.events(snapshot, app.preferences.state.value, clock.instant(), ZoneId.systemDefault()).associateBy { it.key }
        val lowStock = StockPlan.low(snapshot).associateBy { it.medicationId }
        notifications.activeNotifications.filter { it.notification.channelId == CHANNEL }.forEach { active ->
            // Summaries are rebuilt below exclusively from currently resolvable doses.
            if (active.id == SUMMARY_ID) return@forEach
            val dose = if (active.id == DOSE_ID) active.tag?.let { NextDose.resolve(snapshot, it) } else null
            if (active.id == DOSE_ID && dose == null) {
                notifications.cancel(active.tag, active.id)
                return@forEach
            }
            val builder = Notification.Builder.recoverBuilder(app, active.notification)
                .setVisibility(LockScreenNotifications.visibility(privacy)).setOnlyAlertOnce(true)
                .setPublicVersion(Notification.Builder(app, CHANNEL).setSmallIcon(R.drawable.ic_medication)
                    .setContentTitle(app.getString(R.string.app_name)).setContentText(app.getString(R.string.notification_public_text)).build())
            if (active.id == DOSE_ID && active.tag != null) {
                dose?.let { item ->
                    val retry = active.notification.`when` > item.instant(ZoneId.systemDefault()).toEpochMilli() &&
                        active.notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT) == null
                    val time = item.time.toString() + if (item.date != clock.instant().atZone(ZoneId.systemDefault()).toLocalDate()) " (${TurkishFormat.shortDate(item.date)})" else ""
                    val title = if (privacy.detailed) app.getString(if (retry) R.string.notification_followup_title else R.string.notification_dose_title, item.personName, item.schedule.name) else "İlaç zamanı"
                    val text = if (privacy.detailed) app.getString(if (retry) R.string.notification_followup_text else R.string.notification_dose_text, item.schedule.dose, time) else "Doz saati ${LockScreenNotifications.timing(item)}"
                    builder.setContentTitle(title).setContentText(text).setStyle(Notification.BigTextStyle().bigText(text))
                        .setSortKey(if (privacy.detailed) "${item.personName}\u0000${item.schedule.name}" else item.key)
                }
                builder.setActions()
                if (privacy.takenAction) builder.addAction(LockScreenNotifications.takenAction(app, active.tag))
                builder.addAction(Notification.Action.Builder(Icon.createWithResource(app, R.drawable.ic_medication), app.getString(R.string.action_snooze), action(ACTION_SNOOZE, active.tag)).build())
            }
            if (active.id == RENEWAL_ID) {
                val event = renewals[active.tag]
                if (event == null) { notifications.cancel(active.tag, active.id); return@forEach }
                builder.renewalContent(event)
            }
            if (active.id == LOW_STOCK_ID) {
                val item = lowStock[active.tag]
                if (item == null) { notifications.cancel(active.tag, active.id); return@forEach }
                builder.lowStockContent(item)
            }
            notifications.notify(active.tag, active.id, builder.build())
        }
    }

    /** "10 dk ertele": hides the notification and shows it again after [ReminderPlan.SNOOZE_MINUTES]. */
    suspend fun snooze(key: String, now: Instant = Instant.now()) {
        lock.withLock {
            app.preferences.snoozes = app.preferences.snoozes + (key to now.plus(Duration.ofMinutes(ReminderPlan.SNOOZE_MINUTES)).toEpochMilli())
            notifications.cancel(key, DOSE_ID)
        }
        rebuild(false)
    }

    /** Serialize restore with alarm delivery/actions; never issue a historical reminder backlog. */
    suspend fun restoreData(replace: suspend () -> Unit) {
        lock.withLock {
            replace()
            // The database has committed: a platform cleanup failure must not be reported as rollback.
            try { notifications.cancelAll() } catch (e: Exception) { app.reminderLog.record("restore-notifications", e) }
        }
        try { rebuild(false) } catch (e: Exception) { app.reminderLog.record("restore-reschedule", e) }
    }

    /** Immediate test notification on the real channel. Returns false when notifications are blocked. */
    fun sendTestNotification(scheduled: Boolean = false): Boolean {
        channel()
        if (!notificationAllowed()) return false
        val notification = Notification.Builder(app, CHANNEL).setSmallIcon(R.drawable.ic_medication)
            .setContentTitle(app.getString(if (scheduled) R.string.test_scheduled_title else R.string.test_notification_title))
            .setContentText(app.getString(if (scheduled) R.string.test_scheduled_text else R.string.test_notification_text))
            .setContentIntent(openApp()).setAutoCancel(true).setVisibility(LockScreenNotifications.visibility(app.preferences.lockScreen.value)).setCategory(Notification.CATEGORY_REMINDER).build()
        notifications.notify(TEST_TAG, TEST_ID, notification)
        return true
    }

    /** Test of the whole alarm path: an alarm-clock alarm one minute from now that posts a test notification. */
    fun scheduleTestReminder(delay: Duration = Duration.ofMinutes(1)): Instant {
        val at = Instant.now().plus(delay)
        schedule(at.toEpochMilli(), alarmIntent(3, ACTION_TEST))
        return at
    }

    companion object {
        const val CHANNEL = "medication"
        const val ACTION_ALARM = "com.levanyon.meditakip.ALARM"
        const val ACTION_TEST = "com.levanyon.meditakip.TEST_REMINDER"
        const val ACTION_TAKEN = "com.levanyon.meditakip.action.TAKEN"
        const val ACTION_SNOOZE = "com.levanyon.meditakip.action.SNOOZE"
        const val EXTRA_KEY = "occurrenceKey"
        const val DOSE_ID = 1
        const val SUMMARY_ID = 2
        const val TEST_ID = 3
        const val LOW_STOCK_ID = 4
        const val RENEWAL_ID = 5
        const val EXTRA_MEDICATION_ID = "renewalMedicationId"
        const val TEST_TAG = "test"
        const val DISPATCH_RETENTION_DAYS = 7L
        const val SAFETY_DELAY_MILLIS = 15 * 60_000L
        /** Occurrence keys are "<scheduleId>:<epochDay>:<minute>". */
        fun occurrenceDate(key: String): LocalDate? = key.split(':').getOrNull(1)?.toLongOrNull()?.let(LocalDate::ofEpochDay)
    }
}

/** Alarms, boot, time and permission changes: rebuild and deliver what is due. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val accepted = mutableSetOf(Reminders.ACTION_ALARM, Reminders.ACTION_TEST, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)
        if (Build.VERSION.SDK_INT >= 31) accepted.add(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)
        if (intent.action !in accepted) return
        val app = context.applicationContext as MediApp
        if (intent.action == Reminders.ACTION_TEST) { app.reminders.sendTestNotification(scheduled = true); return }
        val result = goAsync()
        app.reminders.scope.launch {
            try { app.reminders.rebuild(intent.action == Reminders.ACTION_ALARM) }
            catch (e: Exception) { app.reminderLog.record("alarm:${intent.action?.substringAfterLast('.')}", e) }
            finally { result.finish() }
        }
    }
}

/** Only snooze survives as a broadcast action; legacy TAKEN intents never mutate data. */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Reminders.ACTION_SNOOZE) return
        val key = intent.getStringExtra(Reminders.EXTRA_KEY) ?: return
        val app = context.applicationContext as MediApp
        val result = goAsync()
        app.reminders.scope.launch {
            try {
                when (intent.action) {
                    Reminders.ACTION_SNOOZE -> app.reminders.snooze(key)
                }
            } catch (e: Exception) { app.reminderLog.record("action:${intent.action?.substringAfterLast('.')}", e) }
            finally { result.finish() }
        }
    }
}
