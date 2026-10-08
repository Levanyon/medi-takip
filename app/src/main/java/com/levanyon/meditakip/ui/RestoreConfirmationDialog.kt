package com.levanyon.meditakip.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.levanyon.meditakip.R

/** Called only with the existing validated backup preview. No restore is triggered on dismissal. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun RestoreConfirmationDialog(dismiss: () -> Unit, confirm: () -> Unit, details: @Composable ColumnScope.() -> Unit = {}) {
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 30.dp, vertical = MediSpace.regular), contentAlignment = Alignment.Center) {
        Surface(modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(), shape = MaterialTheme.shapes.large, border = mediSurfaceBorder(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(MediSpace.page), verticalArrangement = Arrangement.spacedBy(MediSpace.regular)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_phase5_restore), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer) }
                }
                Text(stringResource(R.string.backup_restore_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.restore_v2_body), style = phase5Body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                details()
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Text(stringResource(R.string.restore_v2_warning), style = phase5Label, color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.fillMaxWidth().padding(MediSpace.medium))
                }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MediSpace.small), verticalArrangement = Arrangement.spacedBy(MediSpace.small)) {
                    FilledTonalButton(onClick = dismiss, shape = MaterialTheme.shapes.small, modifier = Modifier.widthIn(min = 128.dp).heightIn(min = MediSize.touch),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Text(stringResource(R.string.restore_v2_cancel), style = phase5Label)
                    }
                    FilledTonalButton(onClick = confirm, shape = MaterialTheme.shapes.small, modifier = Modifier.widthIn(min = 128.dp).heightIn(min = MediSize.touch),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer)) {
                        Text(stringResource(R.string.backup_restore_confirm), style = phase5Label)
                    }
                }
            }
        }
        }
    }
}
