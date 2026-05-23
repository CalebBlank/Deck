package com.hermes.deck.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.hermes.deck.data.AppInfo

/** Horizontal card strip with swipe-up-to-dismiss, mirroring the webOS card metaphor. */
@Composable
fun CardStrip(
    cards: List<AppInfo>,
    onCardTap: (AppInfo) -> Unit,
    onCardDismiss: (AppInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (cards.isEmpty()) {
            Text(
                text  = "No recent apps",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
        } else {
            LazyRow(
                state               = rememberLazyListState(),
                contentPadding      = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment   = Alignment.CenterVertically,
                modifier            = Modifier.fillMaxWidth()
            ) {
                items(cards, key = { it.packageName }) { app ->
                    DismissableCard(
                        app       = app,
                        onTap     = { onCardTap(app) },
                        onDismiss = { onCardDismiss(app) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DismissableCard(
    app: AppInfo,
    onTap: () -> Unit,
    onDismiss: () -> Unit
) {
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dismissed  by remember { mutableStateOf(false) }

    val alpha by animateFloatAsState(
        targetValue      = if (dismissed) 0f else 1f,
        animationSpec    = tween(durationMillis = 180),
        finishedListener = { if (dismissed) onDismiss() },
        label            = "card_alpha"
    )
    val translationY by animateFloatAsState(
        targetValue   = if (dismissed) -500f else dragOffsetY,
        animationSpec = tween(durationMillis = if (dismissed) 180 else 0),
        label         = "card_ty"
    )

    AppCard(
        app = app,
        onTap = onTap,
        modifier = Modifier
            .graphicsLayer {
                this.alpha       = alpha
                this.translationY = translationY
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragOffsetY < -100f) dismissed = true
                        else dragOffsetY = 0f
                    },
                    onDragCancel = { dragOffsetY = 0f },
                    onVerticalDrag = { _, delta ->
                        // Only allow upward drag
                        if (delta < 0) dragOffsetY = (dragOffsetY + delta).coerceAtMost(0f)
                    }
                )
            }
    )
}
