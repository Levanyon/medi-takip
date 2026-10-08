package com.levanyon.meditakip.ui

import android.animation.ValueAnimator
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.levanyon.meditakip.R

internal const val LAUNCH_MILLIS = 800L

internal fun launchMotionEnabled(context: Context): Boolean {
    val accessibility = context.getSystemService(AccessibilityManager::class.java)
    val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    return ValueAnimator.areAnimatorsEnabled() && scale >= 1f && accessibility?.isTouchExplorationEnabled != true
}

internal data class LaunchFrame(val logoAlpha: Float, val logoScale: Float, val textAlpha: Float,
    val textOffset: Float, val settle: Float, val destinationAlpha: Float)

internal fun launchFrame(millis: Long): LaunchFrame {
    fun fraction(start: Long, duration: Long) = ((millis - start).toFloat() / duration).coerceIn(0f, 1f)
    val logo = LinearOutSlowInEasing.transform(fraction(0, 220))
    val text = LinearOutSlowInEasing.transform(fraction(220, 340))
    val end = fraction(560, 240)
    return LaunchFrame(.35f + .65f * logo, .92f + .08f * logo, text, 6f * (1f - text), 1f - .03f * end, end)
}

/** Destination initializes immediately beneath a short decorative overlay. No startup wait or IO. */
@Composable internal fun LaunchTransition(animate: Boolean, content: @Composable () -> Unit) {
    var finished by rememberSaveable { mutableStateOf(!animate) }
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(animate) {
        if (!animate) finished = true
        if (!finished) {
            val start = withFrameNanos { it }
            while (elapsed < LAUNCH_MILLIS) {
                elapsed = withFrameNanos { (it - start) / 1_000_000 }
            }
            finished = true
        }
    }
    val frame = launchFrame(elapsed)
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (finished) 1f else frame.destinationAlpha }) { content() }
        if (!finished) LaunchArtwork(frame, Modifier.graphicsLayer { alpha = 1f - frame.destinationAlpha }
            .pointerInput(Unit) {
                // Do not let a tap on the visible brand activate a still-hidden dose/action.
                awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
            })
    }
}

@Composable internal fun LaunchArtwork(frame: LaunchFrame, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).clearAndSetSemantics { testTag = "launchArtwork" },
        contentAlignment = Alignment.Center) {
        Column(Modifier.graphicsLayer {
            scaleX = frame.settle; scaleY = frame.settle
            // Keep the mark centered before the wordmark's reserved line becomes visible.
            translationY = (22.dp.toPx() * (1f - frame.textAlpha))
        },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            MediMark(88.dp, Modifier.graphicsLayer { alpha = frame.logoAlpha; scaleX = frame.logoScale; scaleY = frame.logoScale })
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp),
                modifier = Modifier.graphicsLayer { alpha = frame.textAlpha; translationY = frame.textOffset.dp.toPx() })
        }
    }
}

@Composable internal fun AppDestination(completed: Boolean, onboarding: @Composable () -> Unit, home: @Composable () -> Unit) {
    if (completed) home() else onboarding()
}

@Composable fun AppLaunch(completed: Boolean, onboarding: @Composable () -> Unit, home: @Composable () -> Unit) {
    val context = LocalContext.current
    LaunchTransition(launchMotionEnabled(context)) { AppDestination(completed, onboarding, home) }
}
