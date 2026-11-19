package com.levanyon.meditakip.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
fun FormScreen(title: String, dismiss: () -> Unit, header: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = dismiss)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        MediContent(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
                if (header != null) Box(Modifier.fillMaxWidth().padding(horizontal = MediSpace.page, vertical = MediSpace.regular)) { header() }
                else Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = MediSpace.small, vertical = MediSpace.small)) { BackHeader(title, dismiss) }
                }
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
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = mediSurfaceBorder(), tonalElevation = MediElevation.flat, shadowElevation = MediElevation.raised) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(MediSpace.large), verticalArrangement = Arrangement.spacedBy(MediSpace.regular)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                content()
            }
        }
    }
}

@Composable
fun SecondaryAction(label: String, enabled: Boolean = true, action: () -> Unit) {
    OutlinedButton(onClick = action, enabled = enabled, shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().heightIn(min = MediSize.primaryAction), contentPadding = PaddingValues(MediSpace.regular)) { Text(label, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center) }
}

@Composable
fun CancelAction(dismiss: () -> Unit) {
    QuietAction(stringResource(R.string.cancel), action = dismiss)
}

@Composable
fun DangerAction(label: String, action: () -> Unit) {
    Button(onClick = action, shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer),
        modifier = Modifier.fillMaxWidth().heightIn(min = MediSize.primaryAction)) {
        Text(label, textAlign = TextAlign.Center)
    }
}
