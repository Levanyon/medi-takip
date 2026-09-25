package com.levanyon.meditakip.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.levanyon.meditakip.R
import com.levanyon.meditakip.domain.*

/** Ayarlar holds only application settings: reminders, appearance and a short about section. */
@Composable fun SettingsScreen(vm: AppViewModel, access: PermissionAccess) {
    val value by vm.settings.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(MediSpace.medium)) {
        item { ScreenHeading(stringResource(R.string.settings), stringResource(R.string.settings_subtitle)) }
        item { SectionHeader(stringResource(R.string.reminder_settings)) }
        item { SettingsSurface {
            SettingToggle(stringResource(R.string.enable_notifications), value.notifications) { vm.settings(value.copy(notifications = it)) }
            PermissionStatusRow(stringResource(R.string.notifications), access.state.notifications, access.busy, access.state.notificationAction == PermissionAction.REQUEST, access.notifications, stringResource(R.string.permission_ok_notifications))
            PermissionStatusRow(stringResource(R.string.exact_alarms), access.state.exact, access.busy, false, access.exact, stringResource(R.string.permission_ok_exact))
            if (!access.state.exact) Text(stringResource(R.string.exact_fallback), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        item { SettingsSurface {
            Text(stringResource(R.string.second_reminder), style = MaterialTheme.typography.titleMedium)
            SettingToggle(stringResource(R.string.retry_enabled), value.retry) { vm.settings(value.copy(retry = it)) }
            Text(stringResource(R.string.retry_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (value.retry) {
                Text(stringResource(R.string.retry_delay), style = MaterialTheme.typography.titleSmall)
                Column(Modifier.selectableGroup()) { listOf(15, 30, 60, 120).forEach { delay -> RadioSetting(stringResource(R.string.minutes, delay), value.delayMinutes == delay) { vm.settings(value.copy(delayMinutes = delay)) } } }
            }
        } }
        item { SectionHeader(stringResource(R.string.appearance)) }
        item { SettingsSurface {
            Column(Modifier.selectableGroup()) {
                listOf("System" to R.string.theme_system, "Light" to R.string.theme_light, "Dark" to R.string.theme_dark).forEach { (key, label) -> RadioSetting(stringResource(label), value.theme == key) { vm.settings(value.copy(theme = key)) } }
            }
        } }
        // Plain reading text, not a box: each paragraph is its own item so long text scrolls naturally.
        item { SectionHeader(stringResource(R.string.about_section)) }
        item { Text(stringResource(R.string.privacy_body), style = MaterialTheme.typography.bodyMedium) }
        listOf(R.string.backup_body, R.string.background_info, R.string.no_advice).forEach { text ->
            item { Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        item { Text(stringResource(R.string.version_label), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = MediSpace.small)) }
    }
}
/** Granted permissions collapse to one reassuring line; only missing ones show an action. */
@Composable fun PermissionStatusRow(label: String, granted: Boolean, busy: Boolean, request: Boolean, action: () -> Unit, grantedText: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(MediSpace.small)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MediSpace.medium)) {
            Icon(if (granted) Icons.Default.CheckCircle else Icons.Default.ErrorOutline, null, tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary)
            Column {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(if (granted) grantedText ?: stringResource(R.string.permission_granted) else stringResource(R.string.permission_required),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!granted) FilledTonalButton(shape = MaterialTheme.shapes.large, onClick = action, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = MediSize.primaryAction)) {
            Text(stringResource(if (request) R.string.grant_permission else R.string.open_settings), style = MaterialTheme.typography.labelLarge)
        }
    }
}
@Composable private fun SettingsSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(MediSpace.page), verticalArrangement = Arrangement.spacedBy(MediSpace.regular), content = content)
    }
}
@Composable private fun RadioSetting(label: String, selected: Boolean, choose: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = MediSize.primaryAction).selectable(selected, role = Role.RadioButton, onClick = choose), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null); Spacer(Modifier.width(MediSpace.medium)); Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
    }
}
