package com.levanyon.meditakip.ui

import androidx.compose.foundation.Image
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
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.levanyon.meditakip.R
import com.levanyon.meditakip.data.LockScreenSettings
import com.levanyon.meditakip.domain.*
import com.levanyon.meditakip.reminders.LockScreenNotifications
import java.time.LocalDate

/** Figma 31:421/480; OS chrome is represented only by a content preview. */
@Composable internal fun LockScreenSettingsContent(value: LockScreenSettings, snapshot: Snapshot,
    change: (LockScreenSettings) -> Unit, back: () -> Unit) {
    val next = NextDose.next(snapshot, LocalDate.now())
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(back, Modifier.size(MediSize.touch)) {
                    Icon(painterResource(R.drawable.ic_p9_back), "Geri", Modifier.size(20.dp))
                }
                Text("Kilit ekranı", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            }
            Text("Sıradaki dozu görünür tut, ayrıntıyı sen seç.", style = phase5Body, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp))
        }
        item {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().heightIn(min = 154.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Önizleme", style = phase5Label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(shape = MaterialTheme.shapes.medium, border = mediSurfaceBorder()) {
                        Row(Modifier.fillMaxWidth().padding(14.dp).semantics(mergeDescendants = true) {},
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                                Icon(painterResource(R.drawable.ic_p9_pill), null, Modifier.padding(8.dp).size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(next?.let { LockScreenNotifications.title(it, value) } ?: "Sıradaki doz yok", style = phase5Label)
                                Text(next?.let { LockScreenNotifications.body(it, value) } ?: "Yaklaşan bir doz olduğunda burada görünür.",
                                    style = phase5Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        item { SettingsSection("Görünürlük") {
            PrivacyToggle("Kilit ekranında göster", "Sıradaki ilacı kilit ekranında hatırlat.", value.visible) { change(value.copy(visible = it)) }
            SettingsDivider()
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Görünüm modu", style = phase5Label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    BoxWithConstraints(Modifier.fillMaxWidth().padding(8.dp).selectableGroup()) {
                        val privateOption: @Composable (Modifier) -> Unit = { modifier ->
                            PrivacyMode("Gizli", "İlaç ve kişi adı yok", R.drawable.ic_p9_eye_off, !value.detailed, modifier) { change(value.copy(detailed = false)) }
                        }
                        val detailedOption: @Composable (Modifier) -> Unit = { modifier ->
                            PrivacyMode("Ayrıntılı", "İlaç ve kişi görünür", R.drawable.ic_p9_eye, value.detailed, modifier) { change(value.copy(detailed = true)) }
                        }
                        if (maxWidth < 290.dp || LocalDensity.current.fontScale > 1.3f) Column {
                            privateOption(Modifier.fillMaxWidth()); detailedOption(Modifier.fillMaxWidth())
                        } else Row { privateOption(Modifier.weight(1f)); detailedOption(Modifier.weight(1f)) }
                    }
                }
            }
        } }
        item { SettingsSection("Sürekli hatırlatma") {
            PrivacyToggle("Sıradaki dozu sabitle", "Bir sonraki dozu bildirimlerde görünür tut.", value.ongoing, R.drawable.ic_p9_pin) { change(value.copy(ongoing = it)) }
        } }
        item { SettingsSection("Hızlı işlemler") {
            PrivacyToggle("Kilit ekranında ‘İçildi’", "İşlem için cihaz kilidi açılsın.", value.takenAction, R.drawable.ic_p9_check) { change(value.copy(takenAction = it)) }
        } }
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
            Text(description, style = phase5Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // The full labelled row is the accessible 48dp+ switch target; artwork is decorative.
        val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        Image(painterResource(when {
            dark && checked -> R.drawable.ic_p9_switch_on_dark
            dark -> R.drawable.ic_p9_switch_off_dark
            checked -> R.drawable.ic_p9_switch_on
            else -> R.drawable.ic_p9_switch_off
        }), null, Modifier.size(46.dp, 28.dp))
    }
}

@Composable private fun PrivacyMode(title: String, description: String, icon: Int, selected: Boolean, modifier: Modifier, choose: () -> Unit) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.small, color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected, role = Role.RadioButton, onClick = choose).padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(icon), null, Modifier.size(18.dp), tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = phase5Label)
                Text(description, style = phase5Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
