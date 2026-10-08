package com.levanyon.meditakip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.levanyon.meditakip.R

/** Figma mark: native rounded capsule and the locally converted check asset. */
@Composable internal fun MediMark(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(size * .3125f))) {
        // Coordinates are proportional to the approved mark, not an illustration bitmap.
        Box(Modifier.offset(size * .05f, size * .513f).size(size * .46f, size * .24f)
            .rotate(42f).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(size * .12f)))
        Icon(painterResource(R.drawable.ic_phase6_mark_check), null,
            Modifier.offset(size * .48f, size * .38f).size(size * .34f), tint = MaterialTheme.colorScheme.onPrimary)
    }
}

@Composable internal fun OnboardingPage(step: OnboardingStep, index: Int, count: Int, recovery: Boolean = false,
    busy: Boolean = false, primary: () -> Unit, secondary: () -> Unit) {
    val title = when {
        recovery && step == OnboardingStep.NOTIFICATIONS -> R.string.onboarding_notification_denied_title
        recovery -> R.string.onboarding_exact_denied_title
        step == OnboardingStep.WELCOME -> R.string.onboarding_welcome_title
        step == OnboardingStep.NOTIFICATIONS -> R.string.onboarding_notifications_title
        step == OnboardingStep.EXACT -> R.string.onboarding_exact_title
        else -> R.string.onboarding_ready_title
    }
    val body = when {
        recovery && step == OnboardingStep.NOTIFICATIONS -> R.string.onboarding_notification_denied_body
        recovery -> R.string.onboarding_exact_denied_body
        step == OnboardingStep.WELCOME -> R.string.onboarding_welcome_body
        step == OnboardingStep.NOTIFICATIONS -> R.string.onboarding_notifications_body
        step == OnboardingStep.EXACT -> R.string.onboarding_exact_body
        else -> R.string.onboarding_ready_body
    }
    val action = when {
        recovery -> R.string.onboarding_settings
        step == OnboardingStep.WELCOME -> R.string.onboarding_start
        step == OnboardingStep.NOTIFICATIONS -> R.string.onboarding_notifications_action
        step == OnboardingStep.EXACT -> R.string.onboarding_exact_action
        else -> R.string.setup_add_first_person
    }
    val scroll = rememberScrollState()
    LaunchedEffect(step, recovery) { scroll.scrollTo(0) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        // Intrinsic height gives whitespace on tall devices and a single scroll surface on short ones.
        BoxWithConstraints(Modifier.widthIn(max = 560.dp).fillMaxSize()) {
            val height = maxHeight
            Column(Modifier.verticalScroll(scroll).heightIn(min = height).height(IntrinsicSize.Min)
                .padding(start = MediSpace.page, end = MediSpace.page, top = 28.dp, bottom = MediSpace.page),
                horizontalAlignment = Alignment.CenterHorizontally) {
                OnboardingProgress(index, count, visible = !recovery)
                Spacer(Modifier.height(if (height >= 700.dp) 108.dp else MediSpace.large))
                Column(Modifier.weight(1f).fillMaxWidth().padding(vertical = MediSpace.regular),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically)) {
                    if (step == OnboardingStep.WELCOME) MediMark(92.dp)
                    else Surface(shape = MaterialTheme.shapes.extraLarge,
                        color = if (recovery) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer) {
                        Box(Modifier.size(if (recovery) 82.dp else 88.dp), contentAlignment = Alignment.Center) {
                            Icon(painterResource(when {
                                recovery -> R.drawable.ic_phase6_warning
                                step == OnboardingStep.NOTIFICATIONS -> R.drawable.ic_phase6_bell
                                step == OnboardingStep.EXACT -> R.drawable.ic_phase6_clock
                                else -> R.drawable.ic_phase6_check
                            }), null, Modifier.size(if (recovery) 34.dp else 36.dp),
                                tint = if (recovery) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text(stringResource(title), Modifier.widthIn(max = 320.dp).semantics { heading() },
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp), textAlign = TextAlign.Center)
                    Text(stringResource(body), Modifier.widthIn(max = 316.dp), style = phase5Body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    if (recovery) RecoveryHint()
                    else if (step != OnboardingStep.READY) PrivacyHint(stringResource(when (step) {
                        OnboardingStep.WELCOME -> R.string.onboarding_privacy
                        OnboardingStep.NOTIFICATIONS -> R.string.onboarding_notifications_hint
                        else -> R.string.onboarding_exact_hint
                    }))
                }
                Spacer(Modifier.height(18.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = primary, enabled = !busy, shape = MaterialTheme.shapes.small,
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = MediSize.touch)) {
                        Text(stringResource(action), Modifier.weight(1f, fill = false), style = phase5Label, textAlign = TextAlign.Center)
                        Spacer(Modifier.width(MediSpace.small))
                        Icon(painterResource(if (recovery) R.drawable.ic_phase6_settings_action else R.drawable.ic_phase6_arrow), null, Modifier.size(18.dp))
                    }
                    if (step == OnboardingStep.NOTIFICATIONS || step == OnboardingStep.EXACT) {
                        FilledTonalButton(onClick = secondary, enabled = !busy, shape = MaterialTheme.shapes.small,
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth().heightIn(min = MediSize.touch)) {
                            Text(stringResource(R.string.onboarding_skip), style = phase5Label)
                        }
                    } else if (step == OnboardingStep.READY) {
                        // Retain the existing optional exit from guided person -> medication setup.
                        QuietAction(stringResource(R.string.setup_later), action = secondary)
                    }
                }
            }
        }
    }
}

@Composable private fun OnboardingProgress(index: Int, count: Int, visible: Boolean) {
    val label = stringResource(R.string.step_count, index + 1, count)
    Row(Modifier.height(7.dp).semantics {
        contentDescription = label
        progressBarRangeInfo = ProgressBarRangeInfo((index + 1).toFloat(), 0f..count.toFloat(), count - 1)
    }, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        if (visible) repeat(count) { position ->
            Box(Modifier.size(if (position == index) 20.dp else 7.dp, 7.dp)
                .background(if (position == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50)))
        }
    }
}

@Composable private fun PrivacyHint(text: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MediSpace.small)) {
            Icon(painterResource(R.drawable.ic_phase6_shield), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(text, Modifier.weight(1f, fill = false), style = phase5Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun RecoveryHint() {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(painterResource(R.drawable.ic_phase6_settings), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.onboarding_recovery_hint), style = phase5Label)
                Text(stringResource(R.string.onboarding_recovery_path), style = phase5Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
