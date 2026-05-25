package com.hermes.deck.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import android.graphics.Point
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.hermes.deck.data.AppInfo
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private val PEEK_HORIZONTAL = 80.dp
private val PAGE_SPACING     = 1.5.dp

/** webOS-style card strip: center card dominant, partials peek from each side. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CardStrip(
    cards: List<AppInfo>,
    onCardTap: (AppInfo) -> Unit,
    onCardDismiss: (AppInfo) -> Unit,
    onCardMoveLeft: ((AppInfo) -> Unit)? = null,
    onCardMoveRight: ((AppInfo) -> Unit)? = null,
    overviewMode: Boolean = false,
    onEnterOverview: () -> Unit = {},
    onExitOverview: () -> Unit = {},
    cycleEvent: SharedFlow<Unit>? = null,
    focusEvent: SharedFlow<Int>? = null,
    onPageChange: ((Int) -> Unit)? = null,
    onCardRevealed: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (cards.isEmpty()) {
        EmptyCardsScreen(modifier = modifier.fillMaxSize())
        return
    }

    val pagerState = rememberPagerState { cards.size }

    LaunchedEffect(cycleEvent) {
        cycleEvent?.collect {
            val next = (pagerState.currentPage + 1) % cards.size
            pagerState.animateScrollToPage(next)
        }
    }

    LaunchedEffect(focusEvent) {
        focusEvent?.collect { page ->
            if (page < cards.size) pagerState.animateScrollToPage(page)
        }
    }

    val peekHorizontal by animateDpAsState(
        targetValue   = if (overviewMode) 40.dp else PEEK_HORIZONTAL,
        animationSpec = tween(300),
        label         = "peek"
    )
    val pageSpacing by animateDpAsState(
        targetValue   = if (overviewMode) 0.dp else PAGE_SPACING,
        animationSpec = tween(300),
        label         = "spacing"
    )

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            onPageChange?.invoke(page)
        }
    }

    var postDismissOffsetFraction by remember { mutableFloatStateOf(0f) }
    var dismissAnimTrigger by remember { mutableIntStateOf(0) }
    var dismissedAtPage by remember { mutableIntStateOf(-1) }
    LaunchedEffect(dismissAnimTrigger) {
        if (dismissAnimTrigger > 0) {
            animate(
                initialValue  = postDismissOffsetFraction,
                targetValue   = 0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
            ) { v, _ -> postDismissOffsetFraction = v }
        }
    }

    Box(modifier = modifier) {
        HorizontalPager(
            state                = pagerState,
            contentPadding       = PaddingValues(horizontal = peekHorizontal),
            pageSpacing          = pageSpacing,
            beyondBoundsPageCount = if (overviewMode) 4 else 1,
            flingBehavior        = if (overviewMode) PagerDefaults.flingBehavior(
                state             = pagerState,
                pagerSnapDistance = PagerSnapDistance.atMost(cards.size)
            ) else PagerDefaults.flingBehavior(state = pagerState),
            modifier             = Modifier.fillMaxSize()
        ) { page ->
            val app = cards.getOrNull(page) ?: return@HorizontalPager

            val signedPageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val pageOffset = signedPageOffset.absoluteValue.coerceIn(0f, 1f)
            val scope = rememberCoroutineScope()
            var dragOffsetX by remember(app.packageName) { mutableFloatStateOf(0f) }
            var isDragging  by remember(app.packageName) { mutableStateOf(false) }

            // Scale animates smoothly between normal and overview values
            val maxScale by animateFloatAsState(
                targetValue   = if (overviewMode) 0.48f else 1.0f,
                animationSpec = cardSpring,
                label         = "maxScale"
            )
            val minScale by animateFloatAsState(
                targetValue   = if (overviewMode) 0.40f else 0.90f,
                animationSpec = cardSpring,
                label         = "minScale"
            )
            val baseScale = lerp(minScale, maxScale, 1f - pageOffset)
            val scale     = baseScale * if (isDragging && overviewMode) 1.06f else 1f

            // Swing rotates proportional to drag displacement; springs back via dragOffsetX animation
            val swingRotation = if (overviewMode) (dragOffsetX / 400f * 12f).coerceIn(-12f, 12f) else 0f

            val dimAlpha         = if (overviewMode) lerp(0.75f, 1f, 1f - pageOffset)
                                   else lerp(0.60f, 1f, 1f - pageOffset)
            val rawElevation     = lerp(2f, 10f, 1f - pageOffset).dp
            val elevation by animateDpAsState(
                targetValue   = rawElevation,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label         = "elevation"
            )

            // Outer box fills pager slot for gesture hit-testing; card height leaves 90dp snap buffer
            BoxWithConstraints(
                modifier         = Modifier
                    .fillMaxSize()
                    .graphicsLayer { clip = false },   // allows card and shadow to render past slot bounds
                contentAlignment = Alignment.Center
            ) {
                @Suppress("DEPRECATION")
                val realSize = Point().also { LocalView.current.display.getRealSize(it) }
                val screenAspect = if (realSize.y >= realSize.x) realSize.y.toFloat() / realSize.x
                                   else realSize.x.toFloat() / realSize.y

                val cardHeightDp: Dp
                val cardWidthDp: Dp
                with(LocalDensity.current) {
                    val slotHeightPx = constraints.maxHeight.toFloat()
                    val slotWidthPx  = constraints.maxWidth.toFloat()
                    val heightPx = (slotWidthPx * screenAspect).coerceAtMost(slotHeightPx * 0.88f)
                    val widthPx  = (heightPx / screenAspect).coerceAtMost(slotWidthPx)
                    cardHeightDp = heightPx.toDp()
                    cardWidthDp  = widthPx.toDp()
                }
                val maxWidthPx = constraints.maxWidth.toFloat()

                Box(
                    modifier = Modifier
                        .width(cardWidthDp)
                        .height(cardHeightDp)
                        .graphicsLayer {
                            scaleX = scale; scaleY = scale
                            val dismissOffset = when {
                                postDismissOffsetFraction > 0f && page >= dismissedAtPage -> postDismissOffsetFraction * maxWidthPx
                                postDismissOffsetFraction < 0f && page < dismissedAtPage  -> postDismissOffsetFraction * maxWidthPx
                                else -> 0f
                            }
                            translationX = dragOffsetX + dismissOffset + if (overviewMode) {
                                val intDist = (pagerState.currentPage - page).toFloat()
                                val fracContrib = if (intDist.absoluteValue <= 1f) pagerState.currentPageOffsetFraction else 0f
                                (intDist + fracContrib) * maxWidthPx * (1f - baseScale) / 1.03f
                            } else 0f
                            rotationZ    = swingRotation
                        }
                        .pointerInput(app.packageName, overviewMode) {
                            if (overviewMode) {
                                var cumulativeDragX = 0f
                                val threshold = 120.dp.roundToPx().toFloat()
                                val springBack: () -> Unit = {
                                    isDragging = false
                                    val from = dragOffsetX
                                    scope.launch {
                                        animate(from, 0f, animationSpec = cardSpring) { v, _ -> dragOffsetX = v }
                                    }
                                }
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        isDragging = true
                                        cumulativeDragX = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        cumulativeDragX += dragAmount.x
                                        dragOffsetX = cumulativeDragX
                                        when {
                                            cumulativeDragX < -threshold && page > 0 -> {
                                                onCardMoveLeft?.invoke(app)
                                                cumulativeDragX = 0f
                                                dragOffsetX = 0f
                                            }
                                            cumulativeDragX > threshold && page < cards.lastIndex -> {
                                                onCardMoveRight?.invoke(app)
                                                cumulativeDragX = 0f
                                                dragOffsetX = 0f
                                            }
                                        }
                                    },
                                    onDragEnd = springBack,
                                    onDragCancel = springBack
                                )
                            }
                        }
                ) {
                    GesturableCard(
                        app             = app,
                        isCurrentPage   = (page == pagerState.currentPage),
                        overviewMode    = overviewMode,
                        elevation       = elevation,
                        dimAlpha        = dimAlpha,
                        onTap           = {
                            if (overviewMode) onExitOverview() else onCardTap(app)
                        },
                        onLongPress     = { if (!overviewMode) onEnterOverview() },
                        onDismiss       = {
                            dismissedAtPage = page
                            postDismissOffsetFraction = if (page >= cards.lastIndex) -1f else 1f
                            dismissAnimTrigger++
                            onCardDismiss(app)
                        },
                        onRevealedChange = onCardRevealed
                    )

                }
            }
        }
    }
}

@Composable
private fun GesturableCard(
    app: AppInfo,
    onTap: () -> Unit,
    onDismiss: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    onRevealedChange: ((Boolean) -> Unit)? = null,
    overviewMode: Boolean = false,
    isCurrentPage: Boolean = true,
    elevation: Dp = 8.dp,
    dimAlpha: Float = 1f,
    modifier: Modifier = Modifier
) {
    val scope            = rememberCoroutineScope()
    val density = LocalDensity.current
    val dismissThresholdPx      = with(density) { 120.dp.toPx() }
    val actionRevealThresholdPx = with(density) { 60.dp.toPx() }
    val actionSnapYPx           = with(density) { 90.dp.toPx() }
    var offsetY          by remember(app.packageName) { mutableFloatStateOf(0f) }
    var revealed         by remember(app.packageName) { mutableStateOf(false) }
    var dismissed        by remember(app.packageName) { mutableStateOf(false) }
    val currentRevealed  by rememberUpdatedState(revealed)
    val currentDismissed by rememberUpdatedState(dismissed)

    val actionsAlpha = when {
        revealed     -> 1f
        offsetY > 0f -> (offsetY / actionRevealThresholdPx).coerceIn(0f, 1f)
        else         -> 0f
    }

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage && revealed) {
            val from = offsetY
            revealed = false
            animate(from, 0f, animationSpec = cardSpring) { v, _ -> offsetY = v }
        }
    }
    LaunchedEffect(revealed) { onRevealedChange?.invoke(revealed) }

    fun snapBack() {
        val from = offsetY
        scope.launch { animate(from, 0f, animationSpec = cardSpring) { v, _ -> offsetY = v } }
    }

    fun handleDragEnd() {
        scope.launch {
            when {
                offsetY < -dismissThresholdPx && !currentRevealed -> {
                    dismissed = true
                    animate(offsetY, -2000f, animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness    = Spring.StiffnessHigh
                    )) { v, _ -> offsetY = v }
                    onDismiss()
                }
                offsetY > actionRevealThresholdPx && !currentRevealed -> {
                    revealed = true
                    animate(offsetY, actionSnapYPx, animationSpec = cardSpring) { v, _ -> offsetY = v }
                }
                currentRevealed -> {
                    if (offsetY < actionSnapYPx / 2f) {
                        val from = offsetY
                        revealed = false
                        animate(from, 0f, animationSpec = cardSpring) { v, _ -> offsetY = v }
                    } else {
                        animate(offsetY, actionSnapYPx, animationSpec = cardSpring) { v, _ -> offsetY = v }
                    }
                }
                else -> snapBack()
            }
        }
    }

    val draggableState = rememberDraggableState { delta ->
        if (!currentDismissed) {
            val next = offsetY + delta
            offsetY = if (currentRevealed) next.coerceAtLeast(0f) else next
        }
    }

    val verticalDragModifier = if (!overviewMode && isCurrentPage) {
        Modifier.draggable(
            orientation   = Orientation.Vertical,
            state         = draggableState,
            onDragStopped = { handleDragEnd() }
        )
    } else Modifier

    Box(modifier = modifier.fillMaxSize().then(verticalDragModifier)) {
        if (actionsAlpha > 0f) {
            CardActions(
                app      = app,
                onHide   = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer { alpha = actionsAlpha }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = offsetY
                }
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp, vertical = 16.dp)
                    .shadow(elevation, RoundedCornerShape(CARD_CORNER), clip = false)
                    .fillMaxSize()
            ) {
                AppCard(
                    app         = app,
                    onTap       = if (!isCurrentPage && !overviewMode) {{}} else if (revealed) {
                        {
                            scope.launch {
                                val from = offsetY
                                revealed = false
                                animate(from, 0f, animationSpec = cardSpring) { v, _ -> offsetY = v }
                            }
                            Unit
                        }
                    } else onTap,
                    onLongPress = if (!isCurrentPage && !overviewMode) null else onLongPress,
                    modifier    = Modifier.fillMaxSize()
                )
                if (dimAlpha < 1f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(CARD_CORNER))
                            .background(Color.Black.copy(alpha = 1f - dimAlpha))
                    )
                }
            }
        }
    }
}

private val cardSpring = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy)
