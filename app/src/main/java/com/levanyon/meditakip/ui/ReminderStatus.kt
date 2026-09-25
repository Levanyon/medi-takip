package com.levanyon.meditakip.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.levanyon.meditakip.MediApp
import com.levanyon.meditakip.R
import com.levanyon.meditakip.domain.TurkishFormat
import com.levanyon.meditakip.reminders.ReminderHealth
import kotlinx.coroutines.delay
import java.time.*

/**
 * Live reminder health. System state (permissions, battery) is re-read every few seconds while shown, so
 * returning from system settings updates it; the in-app switch comes from the ViewModel so it updates at once.
 */
@Composable fun rememberReminderHealth(remindersOn: Boolean): ReminderHealth {
    val app = LocalContext.current.applicationContext as MediApp
    var health by remember { mutableStateOf(app.reminders.health()) }
    LaunchedEffect(Unit) { while (true) { health = app.reminders.health(); delay(2_000) } }
    return health.copy(remindersOn = remindersOn)
}

/** "bugün 20:00", "yarın 08:00" or "3 Ekim 2026 08:00". */
@Composable fun reminderTimeText(epochMillis: Long): String {
    val at = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    val today = LocalDate.now()
    val time = at.toLocalTime().withSecond(0).withNano(0).toString()
    return when (at.toLocalDate()) {
        today -> stringResource(R.string.today_at, time)
        today.plusDays(1) -> stringResource(R.string.tomorrow_at, time)
        else -> "${TurkishFormat.date(at.toLocalDate())} $time"
    }
}

/**
 * Ayarlar → Hatırlatma Durumu: every requirement for on-time reminders with a plain status and a
 * Düzelt button that opens the right place, plus test reminders and the last recorded problem.
 */
@Composable fun ReminderStatusScreen(vm: AppViewModel, access: PermissionAccess, back: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as MediApp
    val settings by vm.settings.collectAsStateWithLifecycle()
    val health = rememberReminderHealth(settings.notifications)
    var message by remember { mutableStateOf<String?>(null) }
    val testSent = stringResource(R.string.test_sent)
    val testBlocked = stringResource(R.string.test_blocked)
    fun open(intent: Intent) {
        try { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: Exception) { vm.error.value = context.getString(R.string.error_settings) }
    }
    val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(MediSpace.medium)) {
        item { BackHeader(stringResource(R.string.reminder_status_title), back) }
        item { Text(stringResource(R.string.reminder_status_subtitle), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { OverallStatus(health) }
        item {
            StatusRow(stringResource(R.string.status_reminders_label), health.remindersOn, stringResource(R.string.status_on), stringResource(R.string.status_off), Icons.Default.NotificationsActive) {
                vm.settings(settings.copy(notifications = true))
            }
        }
        item { StatusRow(stringResource(R.string.status_notifications_label), health.notifications, stringResource(R.string.status_granted), stringResource(R.string.status_missing), Icons.Default.Notifications, fix = access.notifications) }
        item { StatusRow(stringResource(R.string.status_exact_label), health.exactAlarms, stringResource(R.string.status_exact_ok), stringResource(R.string.status_exact_missing), Icons.Default.Alarm, fix = access.exact) }
        item {
            StatusRow(stringResource(R.string.status_battery_label), health.batteryUnrestricted, stringResource(R.string.status_battery_ok), stringResource(R.string.status_battery_limited), Icons.Default.BatteryChargingFull,
                help = stringResource(R.string.battery_help), advisory = true) { open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
        if (health.samsung) item {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(MediSpace.page), verticalArrangement = Arrangement.spacedBy(MediSpace.small)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MediSpace.medium)) {
                        Icon(Icons.Default.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.samsung_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    }
                    Text(stringResource(R.string.samsung_help), style = MaterialTheme.typography.bodyLarge)
                    OutlinedButton(onClick = { open(appDetails) }, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().heightIn(min = MediSize.touch)) {
                        Text(stringResource(R.string.open_app_settings), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        item { SectionHeader(stringResource(R.string.test_section)) }
        item { SecondaryAction(stringResource(R.string.send_test)) { message = if (app.reminders.sendTestNotification()) testSent else testBlocked } }
        item {
            SecondaryAction(stringResource(R.string.schedule_test)) {
                message = if (!health.notifications) testBlocked else {
                    val at = app.reminders.scheduleTestReminder().atZone(ZoneId.systemDefault()).toLocalTime().withSecond(0).withNano(0)
                    context.getString(R.string.test_scheduled, at.toString())
                }
            }
        }
        message?.let { text -> item { Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary) } }
        health.lastProblem?.let { problem ->
            item { SectionHeader(stringResource(R.string.last_problem)) }
            item {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(MediSpace.page), verticalArrangement = Arrangement.spacedBy(MediSpace.tiny)) {
                        Text(reminderTimeText(problem.at), style = MaterialTheme.typography.titleSmall)
                        Text("${problem.place} · ${problem.message}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        QuietAction(stringResource(R.string.clear_problems)) { app.reminderLog.clear() }
                    }
                }
            }
        }
    }
}

@Composable private fun OverallStatus(health: ReminderHealth) {
    val ok = health.working
    Surface(shape = MaterialTheme.shapes.extraLarge, color = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = if (ok) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer) {
        Row(Modifier.fillMaxWidth().padding(MediSpace.page), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MediSpace.regular)) {
            Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.Error, null, Modifier.size(32.dp))
            Column(verticalArrangement = Arrangement.spacedBy(MediSpace.tiny)) {
                Text(stringResource(if (ok) R.string.reminder_status_ok else R.string.reminder_status_attention), style = MaterialTheme.typography.titleLarge)
                Text(health.nextReminderAt?.let { stringResource(R.string.next_reminder, reminderTimeText(it)) } ?: stringResource(R.string.no_next_reminder), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/**
 * One requirement: icon + label + status text (never color alone) and, when not satisfied, a Düzelt
 * button. [advisory] items (battery) use a softer warning because reminders still work without them.
 */
@Composable fun StatusRow(label: String, ok: Boolean, okText: String, problemText: String, icon: ImageVector, help: String? = null, advisory: Boolean = false, fix: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.large, color = scheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(MediSpace.regular), verticalArrangement = Arrangement.spacedBy(MediSpace.small)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MediSpace.medium)) {
                Icon(icon, null, tint = scheme.onSurfaceVariant)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(label, style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MediSpace.tiny)) {
                        Icon(if (ok) Icons.Default.CheckCircle else if (advisory) Icons.Default.Info else Icons.Default.Error, null, Modifier.size(18.dp), tint = if (ok) scheme.primary else scheme.tertiary)
                        Text(if (ok) okText else problemText, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    }
                }
            }
            if (!ok) {
                help?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                FilledTonalButton(onClick = fix, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().heightIn(min = MediSize.primaryAction)) {
                    Text(stringResource(R.string.fix), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
