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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.levanyon.meditakip.R
import com.levanyon.meditakip.domain.*

/** Ayarlar holds only application settings: reminders, appearance and a short about section. */
@Composable fun SettingsScreen(vm: AppViewModel, hasPeople: Boolean = false, openReminderStatus: () -> Unit = {}) {
    val value by vm.settings.collectAsStateWithLifecycle()
    val health = rememberReminderHealth(value.notifications)
    LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(MediSpace.medium)) {
        item { ScreenHeading(stringResource(R.string.settings), stringResource(R.string.settings_subtitle)) }
        item { SectionHeader(stringResource(R.string.reminder_settings)) }
        item { SettingsSurface { SettingToggle(stringResource(R.string.enable_notifications), value.notifications) { vm.settings(value.copy(notifications = it)) } } }
        // Permissions, battery, Samsung guidance and tests live on one dedicated screen (Phase 2.3).
        item {
            val ok = health.working
            Surface(onClick = openReminderStatus, shape = MaterialTheme.shapes.large, color = if (ok) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth().heightIn(min = MediSize.primaryAction)) {
                Row(Modifier.padding(MediSpace.page), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MediSpace.regular)) {
                    Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.Error, null, tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onTertiaryContainer)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.reminder_status_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(if (ok) R.string.reminder_status_ok else R.string.reminder_status_attention), style = MaterialTheme.typography.bodyMedium)
                    }
                    Icon(Icons.Default.ChevronRight, null)
                }
            }
        }
        item { SettingsSurface {
            Text(stringResource(R.string.second_reminder), style = MaterialTheme.typography.titleMedium)
            SettingToggle(stringResource(R.string.retry_enabled), value.retry) { vm.settings(value.copy(retry = it)) }
            Text(stringResource(R.string.retry_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (value.retry) {
                Text(stringResource(R.string.retry_delay), style = MaterialTheme.typography.titleSmall)
                Column(Modifier.selectableGroup()) { listOf(15, 30, 60, 120).forEach { delay -> RadioSetting(stringResource(R.string.minutes, delay), value.delayMinutes == delay) { vm.settings(value.copy(delayMinutes = delay)) } } }
            }
        } }
        item { BackupSettings(vm, hasPeople) }
        item { SectionHeader(stringResource(R.string.appearance)) }
        item { SettingsSurface {
            Column(Modifier.selectableGroup()) {
                listOf("System" to R.string.theme_system, "Light" to R.string.theme_light, "Dark" to R.string.theme_dark).forEach { (key, label) -> RadioSetting(stringResource(label), value.theme == key) { vm.settings(value.copy(theme = key)) } }
            }
        } }
        // Plain reading text, not a box: each paragraph is its own item so long text scrolls naturally.
        item { SectionHeader(stringResource(R.string.about_section)) }
        item { Text(stringResource(R.string.privacy_body), style = MaterialTheme.typography.bodyMedium) }
        listOf(R.string.background_info, R.string.no_advice).forEach { text ->
            item { Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        item { Text(stringResource(R.string.version_label), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = MediSpace.small)) }
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
