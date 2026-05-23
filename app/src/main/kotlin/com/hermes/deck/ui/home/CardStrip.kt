package com.hermes.deck.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.hermes.deck.data.AppInfo
import kotlinx.coroutines.launch

private val PEEK_HORIZONTAL = 36.dp
private val PAGE_SPACING     = 12.dp

/** webOS-style card strip: center card dominant, partials peek from each side. */
@Composable
fun CardStrip(
    cards: List<AppInfo>,
    onCardTap: (AppInfo) -> Unit,
    onCardDismiss: (AppInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    if (cards.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text  = "No recent apps",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
        }
        return
    }

    val pagerState = rememberPagerState { cards.size }

    HorizontalPager(
        state               = pagerState,
        contentPadding      = PaddingValues(horizontal = PEEK_HORIZONTAL),
        pageSpacing         = PAGE_SPACING,
        beyondViewportPageCount = 1,
        modifier            = modifier
    ) { page ->
        val app = cards[page]
        GesturableCard(
            app       = app,
            onTap     = { onCardTap(app) },
            onDismiss = { onCardDismiss(app) }
        )
    }
}

@Composable
private fun GesturableCard(
    app: AppInfo,
    onTap: () -> Unit,
    onDismiss: () -> Unit
) {
    val scope       = rememberCoroutineScope()
    var offsetY     by remember { mutableFloatStateOf(0f) }
    var revealed    by remember { mutableStateOf(false) }
    var dismissed   by remember { mutableStateOf(false) }

    // α for action row: fades in as card is dragged down
    val actionsAlpha = when {
        revealed -> 1f
        offsetY > 0f -> (offsetY / ACTION_REVEAL_THRESHOLD).coerceIn(0f, 1f)
        else -> 0f
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Action row slides in above the card
        if (actionsAlpha > 0f) {
            CardActions(
                app      = app,
                onHide   = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer { alpha = actionsAlpha }
            )
        }

        AppCard(
            app   = app,
            onTap = if (revealed) {
                // Tap card body while revealed → collapse actions
                { scope.launch { snapBack(setOffset = { offsetY = it }, setRevealed = { revealed = it }) } }
            } else onTap,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = offsetY }
                .pointerInput(revealed) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                when {
                                    // Swipe up hard enough → dismiss
                                    offsetY < -DISMISS_THRESHOLD && !revealed -> {
                                        dismissed = true
                                        animate(offsetY, -2000f, animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness    = Spring.StiffnessMedium
                                        )) { v, _ -> offsetY = v }
                                        onDismiss()
                                    }
                                    // Drag down far enough → snap to revealed state
                                    offsetY > ACTION_REVEAL_THRESHOLD && !revealed -> {
                                        revealed = true
                                        animate(offsetY, ACTION_SNAP_Y, animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy
                                        )) { v, _ -> offsetY = v }
                                    }
                                    // Otherwise spring back to 0
                                    else -> {
                                        val from = offsetY
                                        revealed = false
                                        animate(from, 0f, animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy
                                        )) { v, _ -> offsetY = v }
                                    }
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                val from = offsetY
                                revealed = false
                                animate(from, 0f, animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy
                                )) { v, _ -> offsetY = v }
                            }
                        },
                        onVerticalDrag = { _, delta ->
                            if (!dismissed) {
                                val next = offsetY + delta
                                // Allow free drag up; resist drag down past snap point while revealed
                                offsetY = if (revealed) next.coerceAtLeast(0f) else next
                            }
                        }
                    )
                }
        )
    }
}

private const val DISMISS_THRESHOLD       = 120f
private const val ACTION_REVEAL_THRESHOLD = 60f
private const val ACTION_SNAP_Y           = 80f
