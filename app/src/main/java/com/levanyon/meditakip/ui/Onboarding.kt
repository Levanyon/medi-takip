package com.levanyon.meditakip.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.levanyon.meditakip.R

private enum class OnboardingStep { WELCOME, NOTIFICATIONS, EXACT }

/**
 * At most three short steps: what MediTakip does, then only the permissions that are still missing.
 * "How to start" is answered by the Bugün empty state, which leads straight to Kişi Ekle.
 */
@Composable fun Onboarding(vm: AppViewModel) {
    val access = rememberPermissionAccess(vm)
    // Decided once, so granting a permission does not make the step list jump under the user.
    val saved = rememberSaveable { listOfNotNull(OnboardingStep.WELCOME, OnboardingStep.NOTIFICATIONS.takeIf { !access.state.notifications }, OnboardingStep.EXACT.takeIf { !access.state.exact }).joinToString(",") { it.name } }
    val steps = remember(saved) { saved.split(",").map { OnboardingStep.valueOf(it) } }
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = steps[index]
    val last = index == steps.lastIndex
    val scroll = rememberScrollState()
    LaunchedEffect(index) { scroll.scrollTo(0) }
    BackHandler(index > 0) { index-- }
    fun next() { if (last) vm.completeOnboarding() else index++ }
    val nextLabel = stringResource(if (last) R.string.start_using else R.string.continue_label)
    val nextIcon = if (last) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(scroll).padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(MediSpace.large)) {
        AdaptivePair(
            { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary) },
            { if (steps.size > 1) Text(stringResource(R.string.step_count, index + 1, steps.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        )
        Column(Modifier.fillMaxWidth().padding(vertical = MediSpace.large), verticalArrangement = Arrangement.spacedBy(MediSpace.large)) {
            val icon = when (step) { OnboardingStep.WELCOME -> Icons.Default.Spa; OnboardingStep.NOTIFICATIONS -> Icons.Default.NotificationsActive; OnboardingStep.EXACT -> Icons.Default.AlarmOn }
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) { Icon(icon, null, Modifier.padding(MediSpace.large).size(48.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            when (step) {
                OnboardingStep.WELCOME -> {
                    Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineLarge)
                    Text(stringResource(R.string.welcome_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Point(Icons.Default.Today, stringResource(R.string.welcome_point_see))
                    Point(Icons.Default.Notifications, stringResource(R.string.welcome_point_remind))
                    Point(Icons.Default.Lock, stringResource(R.string.welcome_point_private))
                }
                OnboardingStep.NOTIFICATIONS -> {
                    Text(stringResource(R.string.notification_intro_title), style = MaterialTheme.typography.headlineLarge)
                    Text(stringResource(R.string.notification_intro_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (access.state.notifications) StatusChip(stringResource(R.string.permission_granted), true)
                    else if (access.state.previouslyAsked) Text(stringResource(R.string.notification_denied), style = MaterialTheme.typography.bodyMedium)
                }
                OnboardingStep.EXACT -> {
                    Text(stringResource(R.string.exact_intro_title), style = MaterialTheme.typography.headlineLarge)
                    Text(stringResource(R.string.exact_intro_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (access.state.exact) StatusChip(stringResource(R.string.permission_granted), true)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(MediSpace.small)) {
            val needsNotification = step == OnboardingStep.NOTIFICATIONS && !access.state.notifications
            val needsExact = step == OnboardingStep.EXACT && !access.state.exact
            when {
                needsNotification -> PrimaryAction(stringResource(if (access.state.previouslyAsked) R.string.open_settings else R.string.grant_permission), Icons.Default.Notifications, !access.busy, access.notifications)
                needsExact -> PrimaryAction(stringResource(R.string.open_settings), Icons.Default.Alarm, !access.busy, access.exact)
                else -> PrimaryAction(if (step == OnboardingStep.WELCOME && !last) stringResource(R.string.get_started) else nextLabel, nextIcon) { next() }
            }
            if (needsNotification || needsExact) TextButton(onClick = { next() }, enabled = !access.busy, modifier = Modifier.fillMaxWidth().heightIn(min = MediSize.touch)) {
                Text(stringResource(R.string.later), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable private fun Point(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MediSpace.regular)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Start)
    }
}
