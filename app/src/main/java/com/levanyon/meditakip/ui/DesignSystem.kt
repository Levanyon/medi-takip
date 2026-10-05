package com.levanyon.meditakip.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp

/** Shared dimensions describe roles, never a particular phone's width. */
object MediSpace {
    val tiny = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val regular = 16.dp
    val page = 20.dp
    val large = 24.dp
    val extraLarge = 32.dp
    val hero = 40.dp
}

object MediSize {
    val touch = 48.dp
    val primaryAction = 56.dp
    val icon = 24.dp
    val readingWidth = 840.dp
}

object MediMotion {
    const val feedbackMillis = 160
    const val progressMillis = 240
}

object MediElevation {
    val flat = 0.dp
    val card = 1.dp
    val raised = 3.dp
    val navigation = 6.dp
}

/** A fluid reading column; the outer surface still paints edge to edge. */
@Composable
fun MediContent(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = MediSize.readingWidth).fillMaxWidth()) { content() }
    }
}

/** Reflows based on measured content, including Android font scaling and translations. */
@Composable
fun AdaptivePair(leading: @Composable () -> Unit, trailing: @Composable () -> Unit) {
    Layout(
        content = { Box { leading() }; Box { trailing() } },
        modifier = Modifier.fillMaxWidth()
    ) { measurables, constraints ->
        val gap = MediSpace.medium.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val first = measurables[0].measure(loose)
        val second = measurables[1].measure(loose)
        val width = constraints.maxWidth
        val stacked = first.width + second.width + gap > width
        val height = if (stacked) first.height + gap + second.height else maxOf(first.height, second.height)
        layout(width, constraints.constrainHeight(height)) {
            first.placeRelative(0, if (stacked) 0 else (height - first.height) / 2)
            second.placeRelative(
                if (stacked) 0 else width - second.width,
                if (stacked) first.height + gap else (height - second.height) / 2
            )
        }
    }
}

/**
 * Floating navigation shell used throughout MediTakip V2.
 *
 * The outer spacing separates navigation from the content and device edge;
 * the low-contrast border keeps it crisp in both light and dark themes.
 */
@Composable
fun MediNavigationBar(
    labels: List<String>,
    icons: List<ImageVector>,
    selected: Int,
    selectedIcons: List<ImageVector> = icons,
    onSelect: (Int) -> Unit
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.labelMedium
    val labelWidth = labels.maxOf { textMeasurer.measure(it, style = labelStyle).size.width }
    val itemWidth = maxOf(MediSize.touch, with(density) { labelWidth.toDp() } + MediSpace.large)

    MediContent(
        Modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(horizontal = MediSpace.medium, vertical = MediSpace.small)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
            shadowElevation = MediElevation.navigation
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val barWidth = maxOf(maxWidth, itemWidth * labels.size)
                Box(Modifier.horizontalScroll(rememberScrollState())) {
                    NavigationBar(
                        modifier = Modifier.width(barWidth),
                        containerColor = Color.Transparent,
                        tonalElevation = MediElevation.flat,
                        windowInsets = WindowInsets(0, 0, 0, 0)
                    ) {
                        labels.forEachIndexed { index, label ->
                            NavigationBarItem(
                                selected = selected == index,
                                onClick = { onSelect(index) },
                                icon = {
                                    Icon(
                                        if (selected == index) selectedIcons[index] else icons[index],
                                        null
                                    )
                                },
                                label = { Text(label, style = labelStyle) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
