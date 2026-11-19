package com.levanyon.meditakip.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.levanyon.meditakip.R
import com.levanyon.meditakip.data.AppSettings

@Composable fun SettingsScreen(vm: AppViewModel, hasPeople: Boolean = false, openReminderStatus: () -> Unit = {}) {
    val value by vm.settings.collectAsStateWithLifecycle()
    val privacy by vm.lockScreenSettings.collectAsStateWithLifecycle()
    var lockScreen by rememberSaveable { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(lockScreen) { lockScreen = false }
    val access = rememberPermissionAccess(vm)
    val context = LocalContext.current
    val version = remember(context) { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
    if (lockScreen) {
        val snapshot by vm.snapshot.collectAsStateWithLifecycle()
        LockScreenSettingsContent(privacy, snapshot, vm::lockScreenSettings) { lockScreen = false }
    }
    else SettingsContent(value, access, version, vm::settings, openReminderStatus, { lockScreen = true }) { BackupSettings(vm, hasPeople) }
}

/** Existing preferences and permission flows remain the only sources of truth. */
@Composable internal fun SettingsContent(value: AppSettings, access: PermissionAccess, version: String,
    change: (AppSettings) -> Unit, openReminderStatus: () -> Unit, openLockScreen: () -> Unit = {}, backup: @Composable () -> Unit) {
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = Phase5Padding, verticalArrangement = Arrangement.spacedBy(MediSpace.medium)) {
        item { Phase5Heading(stringResource(R.string.settings), stringResource(R.string.settings_subtitle)) }
        if (!access.state.exact) item { PermissionAttentionCard(!access.busy, access.exact) }
        item { SettingsSection(stringResource(R.string.reminder_settings)) {
            SettingsRow(stringResource(R.string.settings_notifications), stringResource(R.string.settings_notifications_help), R.drawable.ic_phase5_bell,
                stringResource(if (!access.state.notifications) R.string.settings_permission_needed else if (value.notifications) R.string.status_on else R.string.status_off),
                warning = !access.state.notifications) { dialog = "notifications" }
            SettingsDivider()
            SettingsRow(stringResource(R.string.status_exact_label), stringResource(R.string.settings_exact_subtitle), R.drawable.ic_phase5_clock,
                stringResource(if (access.state.exact) R.string.status_on else R.string.settings_permission_needed), warning = !access.state.exact, enabled = !access.busy) {
                if (access.state.exact) openReminderStatus() else access.exact()
            }
            SettingsDivider()
            SettingsRow(stringResource(R.string.v3_lock_title), stringResource(R.string.v3_lock_subtitle), R.drawable.ic_phase5_lock, onClick = openLockScreen)
        } }
        item { SectionHeader(stringResource(R.string.v3_data_security)) }
        item { backup() }
        item { SettingsSection(stringResource(R.string.settings_privacy)) {
            SettingsRow(stringResource(R.string.settings_data), stringResource(R.string.settings_data_help), R.drawable.ic_phase5_lock) { dialog = "privacy" }
            SettingsDivider()
            SettingsRow(stringResource(R.string.about_section), stringResource(R.string.settings_about_help), R.drawable.ic_phase5_info, version) { dialog = "about" }
        } }
        item { SettingsSection(stringResource(R.string.appearance)) {
            SettingsRow(stringResource(R.string.settings_theme), stringResource(R.string.settings_theme_help), R.drawable.ic_phase5_moon,
                stringResource(when (value.theme) { "Light" -> R.string.theme_light; "Dark" -> R.string.theme_dark; else -> R.string.theme_system })) { dialog = "theme" }
        } }
    }
    when (dialog) {
        "notifications" -> MediDialog(stringResource(R.string.settings_notifications), { dialog = null }) {
            SettingToggle(stringResource(R.string.enable_notifications), value.notifications) { change(value.copy(notifications = it)) }
            Text(stringResource(if (access.state.notifications) R.string.settings_notification_granted else R.string.settings_notification_missing), style = phase5Body)
            if (!access.state.notifications) SecondaryAction(stringResource(R.string.open_settings), !access.busy, access.notifications)
            SecondaryAction(stringResource(R.string.reminder_status_title)) { dialog = null; openReminderStatus() }
            Text(stringResource(R.string.second_reminder), style = MaterialTheme.typography.titleMedium)
            SettingToggle(stringResource(R.string.retry_enabled), value.retry) { change(value.copy(retry = it)) }
            Text(stringResource(R.string.retry_description), style = phase5Body)
            if (value.retry) Column(Modifier.selectableGroup()) {
                Text(stringResource(R.string.retry_delay), style = MaterialTheme.typography.titleSmall)
                listOf(15, 30, 60, 120).forEach { delay ->
                    RadioSetting(stringResource(R.string.minutes, delay), value.delayMinutes == delay) { change(value.copy(delayMinutes = delay)) }
                }
            }
            CancelAction { dialog = null }
        }
        "theme" -> MediDialog(stringResource(R.string.settings_theme), { dialog = null }) {
            Column(Modifier.selectableGroup()) {
                listOf("System" to R.string.theme_system, "Light" to R.string.theme_light, "Dark" to R.string.theme_dark).forEach { (key, label) ->
                    RadioSetting(stringResource(label), value.theme == key) { change(value.copy(theme = key)); dialog = null }
                }
            }
            CancelAction { dialog = null }
        }
        "privacy" -> MediDialog(stringResource(R.string.settings_data), { dialog = null }) {
            Text(stringResource(R.string.privacy_body), style = phase5Body)
            Text(stringResource(R.string.backup_privacy), style = phase5Body)
            CancelAction { dialog = null }
        }
        "about" -> MediDialog(stringResource(R.string.about_section), { dialog = null }) {
            Text("MediTakip $version", style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.background_info), style = phase5Body)
            Text(stringResource(R.string.no_advice), style = phase5Body)
            CancelAction { dialog = null }
        }
    }
}

@Composable private fun RadioSetting(label: String, selected: Boolean, choose: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = MediSize.primaryAction).selectable(selected, role = Role.RadioButton, onClick = choose), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null)
        Spacer(Modifier.width(MediSpace.medium))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
    }
}
