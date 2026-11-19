package com.levanyon.meditakip.reminders

import android.app.*
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import com.levanyon.meditakip.MainActivity
import com.levanyon.meditakip.MediApp
import com.levanyon.meditakip.R
import com.levanyon.meditakip.data.LockScreenSettings
import com.levanyon.meditakip.domain.*
import java.time.*

/** Content and Android policy only. Reminders owns all scheduling and refreshes. */
object LockScreenNotifications {
    const val CHANNEL = "next_dose"
    const val ID = 6
    fun visibility(settings: LockScreenSettings) = when {
        !settings.visible -> Notification.VISIBILITY_SECRET
        settings.detailed -> Notification.VISIBILITY_PUBLIC
        else -> Notification.VISIBILITY_PRIVATE
    }

    // Absolute dates stay truthful between event-driven refreshes, including missed/inexact alarms.
    fun timing(item: Occurrence): String = "${TurkishFormat.shortDate(item.date)} • ${item.time}"
    fun title(item: Occurrence, settings: LockScreenSettings) = if (settings.detailed) item.schedule.name else "Sıradaki ilaç"
    fun body(item: Occurrence, settings: LockScreenSettings) =
        if (settings.detailed) "${item.personName} • Sıradaki doz ${timing(item)}" else "Doz saati ${timing(item)}"

    fun open(app: MediApp, key: String): PendingIntent = PendingIntent.getActivity(app, 0,
        Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .setData(Uri.parse("meditakip://occurrence/${Uri.encode(key)}" )).putExtra(Reminders.EXTRA_KEY, key),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun takenAction(app: MediApp, key: String): Notification.Action = Notification.Action.Builder(
        Icon.createWithResource(app, R.drawable.ic_medication), app.getString(R.string.action_taken), open(app, key))
        .apply { if (Build.VERSION.SDK_INT >= 31) setAuthenticationRequired(true) }.build()

    fun publicVersion(app: MediApp, channel: String, text: String, at: Instant? = null, now: Instant = Instant.now()) = Notification.Builder(app, channel)
        .setSmallIcon(R.drawable.ic_medication).setContentTitle("Sıradaki ilaç").setContentText(text)
        .apply { at?.let { setWhen(it.toEpochMilli()); setShowWhen(true); setUsesChronometer(it > now); setChronometerCountDown(true) } }
        .setVisibility(Notification.VISIBILITY_PRIVATE).build()

    fun refresh(app: MediApp, snapshot: Snapshot, now: Instant,
        events: List<ReminderEvent> = ReminderPlan.events(snapshot, app.preferences.state.value, now, ZoneId.systemDefault(), app.preferences.snoozes)) {
        val manager = app.getSystemService(NotificationManager::class.java)
        val settings = app.preferences.lockScreen.value
        val today = now.atZone(ZoneId.systemDefault()).toLocalDate()
        // Project the existing plan; this status owns no scheduler or independent occurrence rules.
        val item = events.asSequence().filter { it.kind == ReminderKind.FIRST && it.occurrence.date >= today }
            .map { it.occurrence }.firstOrNull { NextDose.resolve(snapshot, it.key) != null }
        val permitted = Build.VERSION.SDK_INT < 33 || app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!settings.visible || !app.preferences.state.value.notifications || !manager.areNotificationsEnabled() || !permitted || item == null) {
            manager.cancel(ID); return
        }
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Sıradaki doz", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Sıradaki dozun sessiz, sürekli görünürlüğü"
            setSound(null, null); enableVibration(false); setShowBadge(false)
        })
        if (manager.getNotificationChannel(CHANNEL).importance == NotificationManager.IMPORTANCE_NONE) { manager.cancel(ID); return }
        val text = body(item, settings)
        val scheduled = app.preferences.snoozes[item.key]?.let(Instant::ofEpochMilli)?.takeIf { it > now }
            ?: item.instant(ZoneId.systemDefault())
        val notification = Notification.Builder(app, CHANNEL).setSmallIcon(R.drawable.ic_medication)
            .setContentTitle(title(item, settings)).setContentText(text).setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open(app, item.key)).setVisibility(visibility(settings))
            .setPublicVersion(publicVersion(app, CHANNEL, body(item, LockScreenSettings()), scheduled, now))
            .setOngoing(settings.ongoing).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_REMINDER)
            .setWhen(scheduled.toEpochMilli()).setShowWhen(true)
            .setUsesChronometer(scheduled > now).setChronometerCountDown(true)
            // If Android delays the midnight refresh, yesterday's status must still disappear.
            .setTimeoutAfter(Duration.between(now, item.date.plusDays(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant()).toMillis().coerceAtLeast(1))
        if (settings.takenAction && item.date <= today) notification.addAction(takenAction(app, item.key))
        // Permission can be revoked between the check and notify.
        try { manager.notify(ID, notification.build()) } catch (_: SecurityException) { manager.cancel(ID) }
    }
}
