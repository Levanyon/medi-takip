package com.levanyon.meditakip.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import com.levanyon.meditakip.R

/**
 * Full-screen add/edit form rendered inside the activity window (not a Dialog window, whose
 * insets were unreliable on Samsung three-button navigation).
 *
 * - The header respects the status bar and cutouts.
 * - The whole form, including the Kaydet action placed at its end, scrolls.
 * - Bottom safe-drawing insets (navigation bar, gesture area and the IME) are applied *inside*
 *   the scroll, so the last action can always be scrolled fully above the system UI or keyboard.
 */
@Composable
fun FormScreen(title: String, dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = dismiss)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        MediContent(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
                Box(Modifier.padding(horizontal = MediSpace.small, vertical = MediSpace.tiny)) { BackHeader(title, dismiss) }
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                        .padding(start = MediSpace.page, end = MediSpace.page, top = MediSpace.small, bottom = MediSpace.large),
                    verticalArrangement = Arrangement.spacedBy(MediSpace.regular), content = content
                )
            }
        }
    }
}

/** Short confirmations and details use the platform dialog frame, which keeps them inside the safe area. */
@Composable
fun MediDialog(title: String, dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = dismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(MediSpace.large), verticalArrangement = Arrangement.spacedBy(MediSpace.regular)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                content()
            }
        }
    }
}

@Composable
fun SecondaryAction(label: String, enabled: Boolean = true, action: () -> Unit) {
    FilledTonalButton(onClick = action, enabled = enabled, shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().heightIn(min = MediSize.primaryAction), contentPadding = PaddingValues(MediSpace.regular)) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun CancelAction(dismiss: () -> Unit) {
    TextButton(onClick = dismiss, modifier = Modifier.fillMaxWidth().heightIn(min = MediSize.touch)) { Text(stringResource(R.string.cancel), style = MaterialTheme.typography.labelLarge) }
}
