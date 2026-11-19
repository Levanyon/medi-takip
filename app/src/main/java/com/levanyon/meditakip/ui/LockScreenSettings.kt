package com.levanyon.meditakip.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.levanyon.meditakip.R
import com.levanyon.meditakip.data.LockScreenSettings
import com.levanyon.meditakip.domain.*
import com.levanyon.meditakip.reminders.LockScreenNotifications
import java.time.LocalDate

/** V3 39:139: content preview only; Android owns the actual lock-screen chrome. */
@Composable internal fun LockScreenSettingsContent(value: LockScreenSettings, snapshot: Snapshot,
    change: (LockScreenSettings) -> Unit, back: () -> Unit) {
    val next = NextDose.next(snapshot, LocalDate.now())
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(back, Modifier.size(MediSize.touch)) {
                    Icon(painterResource(R.drawable.ic_p9_back), stringResource(R.string.back), Modifier.size(20.dp))
                }
                Text(stringResource(R.string.v3_lock_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            }
            Text(stringResource(R.string.v3_lock_subtitle), style = phase5Body, color = careColors.secondaryInk,
                modifier = Modifier.padding(top = 16.dp))
        }
        item {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().heightIn(min = 154.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.v3_preview), style = phase5Label, color = careColors.secondaryInk)
                    Surface(shape = MaterialTheme.shapes.medium, border = mediSurfaceBorder()) {
                        Row(Modifier.fillMaxWidth().padding(14.dp).semantics(mergeDescendants = true) {},
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                                Icon(painterResource(R.drawable.ic_p9_pill), null, Modifier.padding(8.dp).size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(next?.let { LockScreenNotifications.title(it, value) } ?: stringResource(R.string.v3_no_next), style = phase5Label)
                                Text(next?.let { LockScreenNotifications.body(it, value) } ?: stringResource(R.string.v3_no_next_help),
                                    style = phase5Caption, color = careColors.secondaryInk)
                            }
                        }
                    }
                }
            }
        }
        item { SettingsSection(stringResource(R.string.v3_visibility)) {
            PrivacyToggle(stringResource(R.string.v3_show_lock), stringResource(R.string.v3_show_lock_help), value.visible) { change(value.copy(visible = it)) }
            SettingsDivider()
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.v3_mode), style = phase5Label, color = careColors.secondaryInk)
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    BoxWithConstraints(Modifier.fillMaxWidth().padding(8.dp).selectableGroup()) {
                        val privateOption: @Composable (Modifier) -> Unit = { modifier ->
                            PrivacyMode(stringResource(R.string.v3_private), stringResource(R.string.v3_private_help), R.drawable.ic_p9_eye_off, !value.detailed, modifier) { change(value.copy(detailed = false)) }
                        }
                        val detailedOption: @Composable (Modifier) -> Unit = { modifier ->
                            PrivacyMode(stringResource(R.string.v3_detailed), stringResource(R.string.v3_detailed_help), R.drawable.ic_p9_eye, value.detailed, modifier) { change(value.copy(detailed = true)) }
                        }
                        if (maxWidth < 290.dp || LocalDensity.current.fontScale > 1.3f) Column {
                            privateOption(Modifier.fillMaxWidth()); detailedOption(Modifier.fillMaxWidth())
                        } else Row { privateOption(Modifier.weight(1f)); detailedOption(Modifier.weight(1f)) }
                    }
                }
            }
        } }
        item { SettingsSection(stringResource(R.string.v3_persistent)) {
            PrivacyToggle(stringResource(R.string.v3_pin), stringResource(R.string.v3_pin_help), value.ongoing, R.drawable.ic_p9_pin) { change(value.copy(ongoing = it)) }
        } }
        item { SettingsSection(stringResource(R.string.v3_quick_actions)) {
            PrivacyToggle(stringResource(R.string.v3_quick_taken), stringResource(R.string.v3_unlock_required), value.takenAction, R.drawable.ic_p9_check) { change(value.copy(takenAction = it)) }
        } }
        item { Text(stringResource(R.string.v3_private_default), style = MaterialTheme.typography.bodySmall, color = careColors.secondaryInk) }
    }
}

@Composable private fun PrivacyToggle(title: String, description: String, checked: Boolean, icon: Int? = null, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).toggleable(checked, role = Role.Switch, onValueChange = change)
        .padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        icon?.let { Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(painterResource(it), null, Modifier.padding(10.dp).size(20.dp), tint = MaterialTheme.colorScheme.primary)
        } }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = phase5Caption, color = careColors.secondaryInk)
        }
        Switch(checked, onCheckedChange = null)
    }
}

@Composable private fun PrivacyMode(title: String, description: String, icon: Int, selected: Boolean, modifier: Modifier, choose: () -> Unit) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.small, color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected, role = Role.RadioButton, onClick = choose).padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(icon), null, Modifier.size(18.dp), tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = phase5Label)
                Text(description, style = phase5Caption, color = careColors.secondaryInk)
            }
        }
    }
}
