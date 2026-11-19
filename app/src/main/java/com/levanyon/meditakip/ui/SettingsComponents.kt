package com.levanyon.meditakip.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.levanyon.meditakip.R

internal val Phase5Padding = PaddingValues(start = MediSpace.page, end = MediSpace.page, top = 28.dp, bottom = MediSpace.extraLarge)
// Scoped Figma type roles inherit Phase 1 fonts and colors, as in People/Medication.
internal val phase5Body @Composable get() = MaterialTheme.typography.bodyLarge
internal val phase5Label @Composable get() = MaterialTheme.typography.labelMedium
internal val phase5Caption @Composable get() = MaterialTheme.typography.labelSmall

@Composable internal fun Phase5Heading(title: String, subtitle: String, action: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MediSpace.medium)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MediSpace.tiny)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Text(subtitle, style = phase5Body, color = careColors.secondaryInk)
        }
        action?.invoke()
    }
}

@Composable internal fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Surface(shape = MaterialTheme.shapes.medium, border = mediSurfaceBorder(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable internal fun SettingsDivider() { HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outlineVariant) }

@Composable internal fun SettingsRow(title: String, subtitle: String, icon: Int, value: String? = null,
    warning: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().heightIn(min = MediSize.touch).semantics { role = Role.Button }) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MediSpace.medium)) {
            Surface(shape = MaterialTheme.shapes.small, color = if (warning) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(painterResource(icon), null, Modifier.size(20.dp), tint = if (warning) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.primary)
                }
            }
            BoxWithConstraints(Modifier.weight(1f)) {
                val stacked = maxWidth < 205.dp || LocalDensity.current.fontScale > 1.3f
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MediSpace.small)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, style = phase5Body)
                        Text(subtitle, style = phase5Caption, color = careColors.secondaryInk)
                        if (stacked && value != null) Text(value, style = phase5Label, color = if (warning) MaterialTheme.colorScheme.onTertiaryContainer else careColors.secondaryInk)
                    }
                    if (!stacked && value != null) Text(value, style = phase5Label, color = if (warning) MaterialTheme.colorScheme.onTertiaryContainer else careColors.secondaryInk)
                }
            }
            Icon(painterResource(R.drawable.ic_phase5_chevron), null, Modifier.size(18.dp), tint = careColors.secondaryInk)
        }
    }
}

@Composable internal fun PermissionAttentionCard(enabled: Boolean, open: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(Modifier.fillMaxWidth().padding(start = MediSpace.regular, end = MediSpace.small, top = MediSpace.small, bottom = MediSpace.small),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MediSpace.medium)) {
            Icon(painterResource(R.drawable.ic_phase5_warning), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.settings_exact_warning), style = phase5Label, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(stringResource(R.string.settings_exact_help), style = phase5Caption, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            TextButton(onClick = open, enabled = enabled, modifier = Modifier.sizeIn(minWidth = MediSize.touch, minHeight = MediSize.touch),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onTertiaryContainer)) { Text(stringResource(R.string.settings_open), style = phase5Label) }
        }
    }
}
