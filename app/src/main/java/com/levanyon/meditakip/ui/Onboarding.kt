package com.levanyon.meditakip.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable

internal enum class OnboardingStep { WELCOME, NOTIFICATIONS, EXACT, READY }

/** Presentation state only. Permissions and completion still belong to their existing owners. */
@Composable fun Onboarding(vm: AppViewModel) {
    OnboardingFlow(rememberPermissionAccess(vm), vm::completeOnboarding)
}

@Composable internal fun OnboardingFlow(access: PermissionAccess, complete: (Boolean) -> Unit) {
    // Freeze the initial route across permission dialogs and activity/process recreation.
    var route by rememberSaveable { mutableStateOf(listOfNotNull(OnboardingStep.WELCOME,
        OnboardingStep.NOTIFICATIONS.takeIf { !access.state.notifications },
        OnboardingStep.EXACT.takeIf { !access.state.exact }, OnboardingStep.READY).joinToString(",") { it.name }) }
    val steps = remember(route) { route.split(",").map(OnboardingStep::valueOf) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var notificationAttempted by rememberSaveable { mutableStateOf(false) }
    var exactAttempted by rememberSaveable { mutableStateOf(false) }
    val step = steps[index]
    // Retained click/effect callbacks must observe a settings return's newest permission snapshot.
    val currentPermission by rememberUpdatedState(access.state)
    fun granted(candidate: OnboardingStep) = when (candidate) {
        OnboardingStep.NOTIFICATIONS -> currentPermission.notifications
        OnboardingStep.EXACT -> currentPermission.exact
        else -> false
    }
    fun next() {
        // Remove newly satisfied future requirements; retain visited steps for coherent progress.
        val remaining = steps.take(index + 1) + steps.drop(index + 1).filterNot(::granted)
        route = remaining.joinToString(",") { it.name }
        index = (index + 1).coerceAtMost(remaining.lastIndex)
    }
    BackHandler(index > 0 && !access.busy) {
        index = (index - 1 downTo 0).first { !granted(steps[it]) }
    }
    // The shared handler refreshes on result and ON_RESUME. Never request automatically.
    LaunchedEffect(step, access.state.notifications, access.state.exact, access.busy) {
        if (!access.busy && granted(step)) next()
    }
    val recovery = !access.busy && when (step) {
        OnboardingStep.NOTIFICATIONS -> !access.state.notifications && (notificationAttempted || access.state.previouslyAsked)
        OnboardingStep.EXACT -> !access.state.exact && exactAttempted
        else -> false
    }
    OnboardingPage(step, index, steps.size, recovery, access.busy, primary = {
        when (step) {
            OnboardingStep.WELCOME -> next()
            OnboardingStep.NOTIFICATIONS -> { notificationAttempted = true; access.notifications() }
            OnboardingStep.EXACT -> { exactAttempted = true; access.exact() }
            OnboardingStep.READY -> complete(true)
        }
    }, secondary = { if (step == OnboardingStep.READY) complete(false) else next() })
}
