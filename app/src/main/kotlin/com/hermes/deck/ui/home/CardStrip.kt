package com.hermes.deck.ui.home

import android.graphics.Point
import android.graphics.drawable.Drawable
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.hermes.deck.data.CardGroup
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.hypot
import kotlin.math.roundToInt

private val PEEK_HORIZONTAL = 80.dp
private val PAGE_SPACING     = 1.dp

private data class DragInfo(
    val groupIndex: Int,
    val groupId: String = "",
    // Absolute pointer position in viewport coords (accumulated from drag deltas)
    val fingerX: Float = 0f,
    val fingerY: Float = 0f,
    // Where inside the card the finger landed at drag start (pixels from card left/top)
    val fingerCardX: Float = 0f,
    val fingerCardY: Float = 0f,
    // Accumulated visual translation from drag origin (layout-independent)
    val dragTransX: Float = 0f,
    val dragTransY: Float = 0f,
    val velX: Float = 0f,
    val velY: Float = 0f,
    val pivotX: Float = 0.5f,
    val pivotY: Float = 0.5f,
    val hasMoved: Boolean = false,
    // Non-null only during snap-back animation after drag end
    val snapX: Float? = null,
    val snapY: Float? = null,
    val stackTargetIndex: Int? = null,
    val neighborScrollTarget: Int? = null
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CardStrip(
    cardGroups: List<CardGroup>,
    onGroupTap: (CardGroup) -> Unit,
    onGroupDismiss: (CardGroup) -> Unit,
    onGroupUnstack: ((CardGroup) -> Unit)? = null,
    onStack: (sourceIndex: Int, targetIndex: Int) -> Unit = { _, _ -> },
    onReorderGroup: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    isExpandedStack: Boolean = false,
    isCollapsing: Boolean = false,
    isExpanding: Boolean = false,
    expandPivotIndex: Int = -1,
    cycleEvent: SharedFlow<Unit>? = null,
    focusEvent: SharedFlow<Int>? = null,
    onPageChange: ((Int) -> Unit)? = null,
    onCardRevealed: ((Boolean) -> Unit)? = null,
    overrideIcons: Map<String, Drawable> = emptyMap(),
    resolvedIcons: Map<String, Drawable> = emptyMap(),
    bottomReservedDp: Dp = 0.dp,
    deadZoneHeightDp: Dp = 0.dp,
    onEmptyLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (cardGroups.isEmpty()) return

    val density = LocalDensity.current
    val haptic  = LocalHapticFeedback.current
    val scope   = rememberCoroutineScope()

    val lazyListState = rememberLazyListState()
    val snapBehavior  = rememberSnapFlingBehavior(lazyListState)

    var dragInfo by remember { mutableStateOf<DragInfo?>(null) }
    val latestCardGroups = rememberUpdatedState(cardGroups)

    // Hover-reorder state: after holding B over a peek card for 1s, C slides to the other side.
    val hoverJobRef      = remember { object { var job: Job? = null } }
    var hoverCommitted   by remember { mutableStateOf(false) }
    val hoverCOffsetAnim = remember { Animatable(0f) }  // peek card crossing to opposite side
    var hoverCGroupId    by remember { mutableStateOf<String?>(null) }
    val hoverAOffsetAnim = remember { Animatable(0f) }  // exit card (opposite side from target)
    var hoverAGroupId    by remember { mutableStateOf<String?>(null) }
    val hoverDOffsetAnim = remember { Animatable(0f) }  // enter card (same side as target, off-screen)
    var hoverDGroupId    by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(cycleEvent) {
        cycleEvent?.collect {
            val next = (lazyListState.firstVisibleItemIndex + 1) % cardGroups.size
            lazyListState.animateScrollToItem(next)
        }
    }
    LaunchedEffect(focusEvent) {
        focusEvent?.collect { groupIdx ->
            if (groupIdx in cardGroups.indices) lazyListState.animateScrollToItem(groupIdx)
        }
    }
    LaunchedEffect(lazyListState) {
        var first = true
        snapshotFlow {
            val idx = lazyListState.firstVisibleItemIndex
            val off = lazyListState.firstVisibleItemScrollOffset
            val sz  = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.toFloat() ?: 1f
            (idx + off / sz).roundToInt()
        }.distinctUntilChanged().collect { page ->
            if (!first) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            first = false
            onPageChange?.invoke(page)
        }
    }

    // Tracks which expanded-view group IDs have already played the expand-in animation.
    // Stored here (not per-item) so it survives LazyRow virtualization unmounts/remounts.
    val expandAnimatedIds = remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(isExpandedStack) {
        // Clear on every transition so isInitialBatch is reliable
        expandAnimatedIds.value = emptySet()
    }

    Box(
        modifier = modifier
            .graphicsLayer { clip = false }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val groups = latestCardGroups.value
                        val layout = lazyListState.layoutInfo
                        val vs     = layout.viewportStartOffset
                        val hit    = layout.visibleItemsInfo.firstOrNull { info ->
                            val s = info.offset - vs
                            offset.x >= s && offset.x < s + info.size
                        }
                        if (hit == null || hit.index !in groups.indices) {
                            onEmptyLongPress?.invoke()
                            return@detectDragGesturesAfterLongPress
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        // Freeze any in-progress snap animation so the peeking card
                        // opposite the drag direction isn't scrolled out of composition.
                        val snapIdx = lazyListState.firstVisibleItemIndex
                        val snapOff = lazyListState.firstVisibleItemScrollOffset
                        scope.launch { lazyListState.scrollToItem(snapIdx, snapOff) }
                        hoverJobRef.job?.cancel()
                        hoverJobRef.job = null
                        hoverCommitted = false
                        scope.launch {
                            hoverCOffsetAnim.snapTo(0f)
                            hoverAOffsetAnim.snapTo(0f)
                            hoverDOffsetAnim.snapTo(0f)
                        }
                        hoverCGroupId = null
                        hoverAGroupId = null
                        hoverDGroupId = null
                        val itemX  = offset.x - (hit.offset - vs).toFloat()
                        val pivotX = (itemX / hit.size.toFloat()).coerceIn(0f, 1f)
                        val pivotY = (offset.y / layout.viewportSize.height.toFloat()).coerceIn(0f, 1f)
                        dragInfo = DragInfo(
                            groupIndex  = hit.index,
                            groupId     = groups[hit.index].id,
                            fingerX     = offset.x,
                            fingerY     = offset.y,
                            fingerCardX = itemX,
                            fingerCardY = offset.y,
                            pivotX      = pivotX,
                            pivotY      = pivotY
                        )
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val cur        = dragInfo ?: return@detectDragGesturesAfterLongPress
                        val newFingerX = cur.fingerX + dragAmount.x
                        val newFingerY = cur.fingerY + dragAmount.y
                        val layout     = lazyListState.layoutInfo
                        val vs         = layout.viewportStartOffset.toFloat()
                        val visible    = layout.visibleItemsInfo
                        val draggedVis = visible.firstOrNull { it.index == cur.groupIndex }
                        var newStackTarget: Int? = null
                        var neighborScroll: Int? = null
                        if (draggedVis != null) {
                            val cw  = draggedVis.size.toFloat()
                            // Card's current visual center from the absolute finger position
                            val cx  = newFingerX - cur.fingerCardX + cw / 2f
                            val centeredIdx = lazyListState.firstVisibleItemIndex
                            for (info in visible) {
                                if (info.index == cur.groupIndex) continue
                                val s       = info.offset - vs
                                val e       = s + info.size
                                val overlap = minOf(cx + cw / 2f, e) - maxOf(cx - cw / 2f, s)
                                if (overlap <= cw * 0.20f) continue
                                if (info.index == centeredIdx && overlap > cw * 0.50f) {
                                    newStackTarget = info.index
                                } else if (info.index != centeredIdx) {
                                    neighborScroll = info.index
                                }
                            }
                        }
                        // Haptic when the drag first overlaps a card (neighbor or stack target)
                        val newOverlap = newStackTarget ?: neighborScroll
                        val oldOverlap = cur.stackTargetIndex ?: cur.neighborScrollTarget
                        if (newOverlap != null && newOverlap != oldOverlap) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        // Hover-reorder: start/cancel 1-second timer when B enters/leaves a peek card
                        if (!hoverCommitted && neighborScroll != cur.neighborScrollTarget) {
                            hoverJobRef.job?.cancel()
                            hoverJobRef.job = null
                            if (neighborScroll != null) {
                                val capturedTarget   = neighborScroll
                                val capturedGroupId  = cur.groupId
                                val capturedCGroupId = latestCardGroups.value.getOrNull(capturedTarget)?.id
                                if (capturedCGroupId != null) {
                                    hoverJobRef.job = scope.launch {
                                        delay(1000L)
                                        val di = dragInfo ?: return@launch
                                        if (di.groupId != capturedGroupId) return@launch
                                        val groups  = latestCardGroups.value
                                        val dragged = di.groupIndex
                                        val target  = capturedTarget.coerceIn(0, groups.size - 1)
                                        if (target == dragged) return@launch
                                        val dir      = if (target > dragged) -1 else 1
                                        val newIndex = (target + dir).coerceIn(0, groups.size - 1)
                                        val sign     = if (target > dragged) 1f else -1f
                                        val slotPx   = (lazyListState.layoutInfo.visibleItemsInfo
                                            .firstOrNull()?.size?.toFloat() ?: return@launch) +
                                            with(density) { PAGE_SPACING.toPx() }
                                        val exitGroupId  = groups.getOrNull(if (target > dragged) dragged - 1 else dragged + 1)?.id
                                        val enterGroupId = groups.getOrNull(if (target > dragged) target + 1 else target - 1)?.id
                                        val animSpec = spring<Float>(
                                            stiffness    = Spring.StiffnessMediumLow,
                                            dampingRatio = Spring.DampingRatioNoBouncy
                                        )
                                        // A (exit card) must start animating BEFORE the reorder fires because
                                        // the anchor-key fvi adjustment unmounts A immediately when onReorderGroup
                                        // is called. Pre-animating with a 50ms head start lets A slide off-screen
                                        // before its layout position jumps to fvi-2 and LazyRow unmounts it.
                                        var aAnimJob: Job? = null
                                        try {
                                            hoverCommitted = true
                                            hoverAGroupId = exitGroupId
                                            if (exitGroupId != null) {
                                                hoverAOffsetAnim.snapTo(0f)
                                                aAnimJob = scope.launch {
                                                    hoverAOffsetAnim.animateTo(-sign * slotPx, animSpec)
                                                }
                                                delay(50L)
                                            }
                                            hoverCGroupId  = capturedCGroupId
                                            hoverDGroupId  = enterGroupId
                                            hoverCOffsetAnim.snapTo(sign * 2f * slotPx)
                                            hoverDOffsetAnim.snapTo(sign * slotPx)
                                            onReorderGroup(target, newIndex)
                                            dragInfo = dragInfo?.copy(groupIndex = target)
                                            withFrameNanos { }
                                            withFrameNanos { }
                                            coroutineScope {
                                                launch { hoverCOffsetAnim.animateTo(0f, animSpec) }
                                                launch {
                                                    delay(150L)
                                                    hoverDOffsetAnim.animateTo(0f, animSpec)
                                                }
                                            }
                                        } finally {
                                            aAnimJob?.cancel()
                                            hoverCommitted = false
                                            hoverCGroupId  = null
                                            hoverAGroupId  = null
                                            hoverDGroupId  = null
                                            hoverCOffsetAnim.snapTo(0f)
                                            hoverAOffsetAnim.snapTo(0f)
                                            hoverDOffsetAnim.snapTo(0f)
                                        }
                                        dragInfo = dragInfo?.copy(neighborScrollTarget = null)
                                        hoverJobRef.job = null
                                    }
                                }
                            }
                        }
                        dragInfo = cur.copy(
                            fingerX              = newFingerX,
                            fingerY              = newFingerY,
                            dragTransX           = cur.dragTransX + dragAmount.x,
                            dragTransY           = cur.dragTransY + dragAmount.y,
                            velX                 = cur.velX * 0.72f + dragAmount.x * 0.28f,
                            velY                 = cur.velY * 0.72f + dragAmount.y * 0.28f,
                            hasMoved             = true,
                            stackTargetIndex     = newStackTarget,
                            neighborScrollTarget = neighborScroll
                        )
                    },
                    onDragEnd = {
                        hoverJobRef.job?.cancel()
                        hoverJobRef.job = null
                        hoverCommitted = false
                        scope.launch {
                            hoverCOffsetAnim.snapTo(0f)
                            hoverAOffsetAnim.snapTo(0f)
                            hoverDOffsetAnim.snapTo(0f)
                        }
                        hoverCGroupId = null
                        hoverAGroupId = null
                        hoverDGroupId = null
                        val cur = dragInfo ?: return@detectDragGesturesAfterLongPress
                        if (!cur.hasMoved) { dragInfo = null; return@detectDragGesturesAfterLongPress }
                        // neighborScrollTarget = quick release over peek card → stack.
                        val stackTarget = cur.stackTargetIndex ?: cur.neighborScrollTarget
                        if (stackTarget != null) {
                            dragInfo = null
                            onStack(cur.groupIndex, stackTarget)
                        } else {
                            scope.launch {
                                // Compute fromX from the card's actual visual position (key lookup)
                                // so snap-back starts exactly where the card was rendered, regardless
                                // of any dragTransX adjustment that happened during a reorder.
                                val vs = lazyListState.layoutInfo.viewportStartOffset.toFloat()
                                val ci = lazyListState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.key == cur.groupId }
                                val fromX = if (ci != null)
                                    cur.fingerX - cur.fingerCardX - (ci.offset.toFloat() - vs)
                                else cur.dragTransX
                                val fromY    = cur.dragTransY
                                val fromVelX = cur.velX
                                val fromVelY = cur.velY
                                animate(
                                    initialValue  = 1f,
                                    targetValue   = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness    = Spring.StiffnessMedium
                                    )
                                ) { p, _ ->
                                    dragInfo = cur.copy(
                                        snapX            = fromX * p,
                                        snapY            = fromY * p,
                                        velX             = fromVelX * p,
                                        velY             = fromVelY * p,
                                        stackTargetIndex = null
                                    )
                                }
                                dragInfo = null
                            }
                        }
                    },
                    onDragCancel = {
                        hoverJobRef.job?.cancel()
                        hoverJobRef.job = null
                        hoverCommitted = false
                        scope.launch {
                            hoverCOffsetAnim.snapTo(0f)
                            hoverAOffsetAnim.snapTo(0f)
                            hoverDOffsetAnim.snapTo(0f)
                        }
                        hoverCGroupId = null
                        hoverAGroupId = null
                        hoverDGroupId = null
                        dragInfo = null
                    }
                )
            }
    ) {
        LazyRow(
            state                 = lazyListState,
            flingBehavior         = snapBehavior,
            contentPadding        = PaddingValues(horizontal = PEEK_HORIZONTAL),
            horizontalArrangement = Arrangement.spacedBy(PAGE_SPACING),
            userScrollEnabled     = dragInfo == null && !isCollapsing && !isExpanding,
            modifier              = Modifier.fillMaxSize().graphicsLayer { clip = false }
        ) {
            itemsIndexed(cardGroups, key = { _, group -> group.id }) { displayIdx, group ->
                val firstIdx = lazyListState.firstVisibleItemIndex
                val firstOff = lazyListState.firstVisibleItemScrollOffset
                val itemSz   = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.toFloat() ?: 1f
                val currentF = firstIdx + firstOff / itemSz
                val pageOff  = (currentF - displayIdx).absoluteValue.coerceIn(0f, 1f)

                val isDragged        = dragInfo?.let { it.groupId == group.id && it.hasMoved } ?: false
                val isAnyDragging   = dragInfo?.hasMoved ?: false
                val isStackTarget   = dragInfo?.stackTargetIndex == displayIdx
                val isNeighborTarget = dragInfo?.neighborScrollTarget == displayIdx
                val isCurrentPage   = displayIdx == lazyListState.firstVisibleItemIndex

                // Full foreground at pageOff = 0.5: stretch the ease-out curve over the first half
                val t        = ((1f - pageOff) * 2f).coerceIn(0f, 1f)
                val pageFrac = 2f * t - t * t

                val targetScale = when {
                    isDragged     -> 0.60f
                    isAnyDragging -> 0.55f
                    else          -> lerp(0.92f, 1.00f, pageFrac)
                }
                val baseScale by animateFloatAsState(
                    targetValue   = targetScale,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label         = "scale"
                )
                // Animate pivot to the inner edge so peeking cards zoom inward rather than
                // off-screen (their layout centre is off-screen, so centre-pivot scale pushes
                // the visible edge further off).
                val dragPivotXTarget = when {
                    !isAnyDragging || isDragged -> 0.5f
                    displayIdx < lazyListState.firstVisibleItemIndex -> 1f
                    displayIdx > lazyListState.firstVisibleItemIndex -> 0f
                    else -> 0.5f
                }
                val animatedPivotX by animateFloatAsState(
                    targetValue   = dragPivotXTarget,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label         = "pivotX"
                )
                // Inward nudge: inner edge slides toward centre so the motion is visible.
                // With inner-edge pivot the nudge and pivot change add up (~33dp total) instead
                // of cancelling each other out as they did with an outward nudge.
                val peekNudgePx   = with(density) { 24.dp.toPx() }
                val peekNudgeTarget = when {
                    !isAnyDragging || isDragged -> 0f
                    displayIdx < lazyListState.firstVisibleItemIndex ->  peekNudgePx
                    displayIdx > lazyListState.firstVisibleItemIndex -> -peekNudgePx
                    else -> 0f
                }
                val peekNudge by animateFloatAsState(
                    targetValue   = peekNudgeTarget,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label         = "peekNudge"
                )
                val dimAlpha = lerp(0.60f, 1f, pageFrac)

                val elevation by animateDpAsState(
                    targetValue   = when { isDragged -> 18.dp; isCurrentPage -> 10.dp; else -> 2.dp },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label         = "elev"
                )

                val px = (dragInfo?.pivotX ?: 0.5f) - 0.5f
                val py = (dragInfo?.pivotY ?: 0.5f) - 0.5f
                val distFraction  = (hypot(px, py) / 0.707f).coerceIn(0f, 1f)
                val rotAmplifier  = lerp(1f, 5f, distFraction)
                val maxAngle      = lerp(5f, 20f, distFraction)
                val targetRotation = if (isDragged) {
                    val vx = dragInfo?.velX ?: 0f
                    val ySign = if ((dragInfo?.pivotY ?: 0.5f) >= 0.5f) -1f else 1f
                    vx * 0.4f * rotAmplifier * ySign
                } else 0f
                val animRotation by animateFloatAsState(
                    targetValue   = targetRotation,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                    label         = "rotation"
                )


                // --- Expand animation: cards fly from fan positions to row ---
                // isInitialBatch: true only during the composition that first shows the expanded view.
                // expandAnimatedIds is empty until the first LaunchedEffect runs, so all items in the
                // same composition pass see isEmpty() == true. Items that enter later (lazy scroll)
                // see a non-empty set and initialize to 0 instead of the fan position.
                val alreadyExpanded = group.id in expandAnimatedIds.value
                val isInitialBatch  = isExpandedStack && expandAnimatedIds.value.isEmpty()

                val expandTransAnim = remember(group.id) {
                    val viewW = lazyListState.layoutInfo.viewportSize.width.toFloat()
                    val initX = if (isInitialBatch && displayIdx > 0 && viewW > 0f)
                                    -displayIdx.toFloat() * viewW else 0f
                    Animatable(initX)
                }
                val expandRotAnim = remember(group.id) {
                    val initRot = if (isInitialBatch) {
                        val bc = (cardGroups.size - 1).coerceAtMost(4)
                        val ps = if (bc > 0) 16f / bc else 0f
                        -8f + displayIdx.coerceAtMost(bc) * ps
                    } else 0f
                    Animatable(initRot)
                }
                LaunchedEffect(Unit) {
                    if (!isExpandedStack) return@LaunchedEffect
                    // Mark done first so any concurrent remount sees alreadyExpanded = true.
                    expandAnimatedIds.value = expandAnimatedIds.value + group.id
                    if (!isInitialBatch || alreadyExpanded) return@LaunchedEffect
                    delay(displayIdx * 35L)
                    launch {
                        expandTransAnim.animateTo(
                            0f, animationSpec = spring(
                                stiffness    = Spring.StiffnessMediumLow,
                                dampingRatio = Spring.DampingRatioMediumBouncy
                            )
                        )
                    }
                    expandRotAnim.animateTo(
                        0f, animationSpec = spring(
                            stiffness    = Spring.StiffnessMediumLow,
                            dampingRatio = Spring.DampingRatioMediumBouncy
                        )
                    )
                }

                // --- Collapse animation: cards return to fan positions ---
                val collapseTransAnim = remember(group.id) { Animatable(0f) }
                val collapseRotAnim   = remember(group.id) { Animatable(0f) }
                LaunchedEffect(isCollapsing) {
                    if (!isCollapsing || !isExpandedStack) {
                        collapseTransAnim.snapTo(0f)
                        collapseRotAnim.snapTo(0f)
                        return@LaunchedEffect
                    }
                    val totalCards    = cardGroups.size
                    val backCountCol  = (totalCards - 1).coerceAtMost(4)
                    val perStepCol    = if (backCountCol > 0) 16f / backCountCol else 0f
                    val baseRotCol    = -8f
                    val indivAngleCol = displayIdx.coerceAtMost(backCountCol) * perStepCol
                    val targetRot      = baseRotCol + indivAngleCol
                    val itemW = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.toFloat()
                    launch {
                        if (displayIdx > 0 && itemW != null && itemW > 0f) {
                            collapseTransAnim.animateTo(
                                -displayIdx * itemW, animationSpec = spring(
                                    stiffness    = Spring.StiffnessMediumLow,
                                    dampingRatio = Spring.DampingRatioNoBouncy
                                )
                            )
                        }
                    }
                    collapseRotAnim.animateTo(
                        targetRot, animationSpec = spring(
                            stiffness    = Spring.StiffnessMediumLow,
                            dampingRatio = Spring.DampingRatioNoBouncy
                        )
                    )
                }

                val expandOffset   = expandTransAnim.value
                val collapseOffset = collapseTransAnim.value
                val expandRot      = expandRotAnim.value
                val collapseRot    = collapseRotAnim.value

                // Slide non-pivot cards out on expand, back in on collapse return
                val slideAnim = remember(group.id) { Animatable(0f) }
                LaunchedEffect(isExpanding) {
                    if (isExpandedStack) return@LaunchedEffect
                    if (!isExpanding) {
                        slideAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                        return@LaunchedEffect
                    }
                    if (expandPivotIndex < 0 || displayIdx == expandPivotIndex) return@LaunchedEffect
                    val dir      = if (displayIdx < expandPivotIndex) -1f else 1f
                    val viewport = lazyListState.layoutInfo.viewportSize.width.toFloat()
                    slideAnim.animateTo(
                        dir * viewport,
                        spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy)
                    )
                }
                LaunchedEffect("slideIn") {
                    if (isExpandedStack || !isCollapsing || expandPivotIndex < 0) return@LaunchedEffect
                    if (displayIdx == expandPivotIndex) return@LaunchedEffect
                    val dir      = if (displayIdx < expandPivotIndex) -1f else 1f
                    val viewport = lazyListState.layoutInfo.viewportSize.width.toFloat()
                    slideAnim.snapTo(-dir * viewport)
                    delay((displayIdx - expandPivotIndex).absoluteValue * 30L)
                    slideAnim.animateTo(
                        0f,
                        spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy)
                    )
                }

                // Look up by key (group.id) not by displayIdx: after a reorder the stale layoutInfo
                // still contains the dragged card at its OLD slot position under the correct key,
                // so fingerX - fingerCardX - naturalLeft always yields the right translationX.
                val dragTransX: Float
                val dragTransY: Float
                if (isDragged && dragInfo != null) {
                    val di = dragInfo!!
                    if (di.snapX != null) {
                        dragTransX = di.snapX
                        dragTransY = di.snapY ?: 0f
                    } else {
                        val vs       = lazyListState.layoutInfo.viewportStartOffset.toFloat()
                        val cardInfo = lazyListState.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.key == group.id }
                        if (cardInfo != null) {
                            val naturalLeft = cardInfo.offset.toFloat() - vs
                            dragTransX = di.fingerX - di.fingerCardX - naturalLeft
                            dragTransY = di.fingerY - di.fingerCardY
                        } else {
                            dragTransX = di.dragTransX
                            dragTransY = di.dragTransY
                        }
                    }
                } else {
                    dragTransX = 0f
                    dragTransY = 0f
                }

                // Push neighboring items outward from any nearby stack.
                // sPageFrac is a smoothstep over the full scroll range (1 at center, 0 one page away)
                // so the gap grows and shrinks continuously during every swipe frame.
                val closestStackEntry = cardGroups.withIndex()
                    .filter { (idx, g) -> g.isStack && idx != displayIdx }
                    .minByOrNull { (idx, _) -> (idx - displayIdx).absoluteValue }
                val nearestStackIdx = closestStackEntry?.index ?: -1
                val stackPushTranslationX = if (closestStackEntry != null) {
                    val stackPageOff = (currentF - nearestStackIdx).absoluteValue.coerceIn(0f, 1f)
                    val x = (1f - stackPageOff).coerceIn(0f, 1f)
                    val sPageFrac = x * x * (3f - 2f * x)   // smoothstep: 1 at center, 0 one page away
                    val fraction = (closestStackEntry.value.apps.size.coerceIn(2, 5) - 2) / 3f
                    val maxInset = 8.dp + 20.dp * fraction + 16.dp   // base + size scale + focus bonus
                    with(density) { (maxInset * sPageFrac).toPx() } *
                        if (displayIdx < nearestStackIdx) -1f else 1f
                } else 0f

                BoxWithConstraints(
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .fillParentMaxHeight()
                        .zIndex(when {
                            isDragged     -> 2f
                            group.isStack -> 1f
                            isCurrentPage -> 0.5f
                            else          -> 0f
                        })
                        .let { if (!isDragged && !isCollapsing && !isExpanding) it.animateItem(
                            fadeInSpec    = if (isAnyDragging) null else tween(120),
                            placementSpec = if (isAnyDragging) null else tween(durationMillis = 600, easing = FastOutSlowInEasing),
                            fadeOutSpec   = if (isAnyDragging) null else tween(120)
                        ) else it }
                        .graphicsLayer {
                            clip   = false
                            scaleX = baseScale
                            scaleY = baseScale
                            if (isDragged) {
                                translationX    = dragTransX
                                translationY    = dragTransY
                                rotationZ       = animRotation
                                transformOrigin = TransformOrigin(
                                    dragInfo?.pivotX ?: 0.5f,
                                    dragInfo?.pivotY ?: 0.5f
                                )
                            } else {
                                translationX    = expandOffset + collapseOffset + slideAnim.value + stackPushTranslationX + peekNudge +
                                                  (if (group.id == hoverCGroupId && !isDragged) hoverCOffsetAnim.value else 0f) +
                                                  (if (group.id == hoverAGroupId && !isDragged) hoverAOffsetAnim.value else 0f) +
                                                  (if (group.id == hoverDGroupId && !isDragged) hoverDOffsetAnim.value else 0f)
                                rotationZ       = expandRot + collapseRot
                                transformOrigin = TransformOrigin(animatedPivotX, 0.5f)
                            }
                        },
                    contentAlignment = Alignment.TopCenter
                ) {
                    @Suppress("DEPRECATION")
                    val realSize = Point().also { LocalView.current.display.getRealSize(it) }
                    val screenAspect = if (realSize.y >= realSize.x) realSize.y.toFloat() / realSize.x
                                      else realSize.x.toFloat() / realSize.y

                    val cardHeightDp: Dp
                    val cardWidthDp: Dp
                    val effectiveSlotDp: Dp
                    var cardToSearchBarPx = 0f
                    with(density) {
                        val slotH = constraints.maxHeight.toFloat()
                        val slotW = constraints.maxWidth.toFloat()
                        val effH  = (slotH - bottomReservedDp.toPx()).coerceAtLeast(0f)
                        val h     = (slotW * screenAspect).coerceAtMost(effH * 0.88f)
                        val w     = (h / screenAspect).coerceAtMost(slotW)
                        cardHeightDp      = h.toDp()
                        cardWidthDp       = w.toDp()
                        effectiveSlotDp   = effH.toDp()
                        // Distance from resting card bottom to the search bar top
                        cardToSearchBarPx = (effH / 2f - h / 2f) + bottomReservedDp.toPx() + deadZoneHeightDp.toPx()
                    }

                    // Always compute fanOpen (Compose hook must not be conditional)
                    // While dragging a stack horizontally, modulate the splay:
                    // drag right → more open, drag left → less open
                    val dragFanBias = if (isDragged && group.isStack) {
                        val cardW = lazyListState.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.index == displayIdx }?.size?.toFloat() ?: 1f
                        (-dragTransX / (cardW * 0.5f)).coerceIn(-1f, 1f)
                    } else 0f
                    val fanBase = if (group.isStack && group.apps.size > 1) {
                        val t = (1f - pageOff).coerceIn(0f, 1f)
                        val t2 = t * t; t2 * t2 * t2   // sextic ease-in: extremely slow at edges, very fast near center
                    } else 0f
                    val fanTarget  = (fanBase + dragFanBias).coerceIn(0f, 1f)
                    val fanOpen by animateFloatAsState(
                        targetValue   = fanTarget,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label         = "fanOpen"
                    )
                    val maxBackVisible = 4
                    val backCount      = (group.apps.size - 1).coerceAtMost(maxBackVisible)
                    // Fixed total spread: more cards = smaller per-step angle, fan stays symmetric at ±8°
                    val totalFanSpread = 16f
                    val perStep        = if (backCount > 0) totalFanSpread / backCount else 0f
                    val stackBaseRotation = lerp(0f, -totalFanSpread / 2f, fanOpen)
                    // Hoist bx so the centering offset can use the same value as the fan layers
                    val bx = with(density) { lerp(5.dp.toPx(), 18.dp.toPx(), fanOpen) }
                    val by = with(density) { lerp(3.dp.toPx(), 10.dp.toPx(), fanOpen) }
                    // Shift the container left by half the total fan spread to keep equal margins
                    val fanCenteringX = if (group.isStack && backCount > 0) -(bx * backCount) / 2f else 0f
                    // Hoisted for stacks so the whole fan container tracks vertical drag
                    val stackOffsetY = remember(group.id) { mutableFloatStateOf(0f) }

                    Box(
                        modifier         = Modifier.fillMaxWidth().height(effectiveSlotDp),
                        contentAlignment = Alignment.Center
                    ) {
                        val containerWidthDp = cardWidthDp

                        // "Release to unstack" hint + dotted line — sibling of the fan
                        // container so no rotation/translation ever affects it.
                        if (group.isStack) {
                            val halfwayPx       = cardToSearchBarPx / 2f
                            val labelAlpha      = (stackOffsetY.floatValue / halfwayPx).coerceIn(0f, 1f)
                            val cardTopInSlotDp = (effectiveSlotDp - cardHeightDp) / 2
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { alpha = labelAlpha },
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier            = Modifier.padding(top = cardTopInSlotDp + 16.dp)
                                ) {
                                    Text(
                                        text  = "Release to unstack",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    val lineHeightDp = with(density) {
                                        (stackOffsetY.floatValue - 32.dp.toPx()).coerceAtLeast(0f).toDp()
                                    }
                                    if (lineHeightDp > 0.dp) {
                                        Spacer(Modifier.height(4.dp))
                                        Canvas(modifier = Modifier.fillMaxWidth().height(lineHeightDp)) {
                                            val progress = (stackOffsetY.floatValue / halfwayPx).coerceIn(0f, 1f)
                                            val dash = lerp(2.dp.toPx(), 36.dp.toPx(), progress)
                                            val gap  = lerp(2.dp.toPx(), 10.dp.toPx(), progress)
                                            val cx   = size.width / 2f
                                            drawLine(
                                                color       = Color.White.copy(alpha = 0.6f),
                                                start       = Offset(cx, 0f),
                                                end         = Offset(cx, size.height),
                                                strokeWidth = 2.dp.toPx(),
                                                pathEffect  = PathEffect.dashPathEffect(floatArrayOf(dash, gap))
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .width(containerWidthDp)
                                .height(cardHeightDp)
                                .offset { IntOffset(0, if (group.isStack) stackOffsetY.floatValue.roundToInt() else 0) }
                                .graphicsLayer {
                                    clip         = false
                                    rotationZ    = stackBaseRotation
                                    translationX = fanCenteringX
                                }
                        ) {
                            // Fan back-card layers for stacked groups (back-most drawn first)
                            if (group.isStack && group.apps.size > 1) {
                                for (i in backCount downTo 1) {
                                    val angle = perStep * i
                                    val app   = group.apps[i]
                                    Box(modifier = Modifier.fillMaxSize().graphicsLayer {
                                        translationX = bx * i
                                        translationY = -by * i
                                        rotationZ    = lerp(angle * 0.25f, angle, fanOpen)
                                        alpha        = (0.85f - (i - 1) * 0.10f).coerceAtLeast(0.45f)
                                    }) {
                                        BackCardLayer(
                                            app          = app,
                                            resolvedIcon = overrideIcons[app.packageName]
                                                           ?: resolvedIcons[app.packageName]
                                        )
                                    }
                                }
                            }

                            // Front card
                            GesturableCard(
                                app              = group.primaryApp,
                                resolvedIcon     = overrideIcons[group.primaryApp.packageName]
                                                   ?: resolvedIcons[group.primaryApp.packageName],
                                isCurrentPage    = isCurrentPage,
                                isDragging       = isAnyDragging,
                                isStack              = group.isStack,
                                stackOffsetY         = if (group.isStack) stackOffsetY else null,
                                downDismissTrackPx   = cardToSearchBarPx,
                                elevation            = elevation,
                                dimAlpha         = dimAlpha,
                                onTap            = {
                                    if (!isCurrentPage) scope.launch { lazyListState.animateScrollToItem(displayIdx) }
                                    else onGroupTap(group)
                                },
                                onDismiss        = { onGroupDismiss(group) },
                                onUnstack        = if (group.isStack) { { onGroupUnstack?.invoke(group) } } else null,
                                onRevealedChange = onCardRevealed
                            )

                            // Stack target highlight overlay
                            if (isStackTarget) {
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp, vertical = 16.dp)
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(CARD_CORNER))
                                        .background(Color.White.copy(alpha = 0.20f))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackCardLayer(
    app: com.hermes.deck.data.AppInfo,
    resolvedIcon: Drawable?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(horizontal = 4.dp, vertical = 16.dp)
            .shadow(4.dp, RoundedCornerShape(CARD_CORNER), clip = false)
            .clip(RoundedCornerShape(CARD_CORNER))
            .fillMaxSize()
    ) {
        AppCard(
            app          = app,
            resolvedIcon = resolvedIcon,
            onTap        = {},
            modifier     = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun GesturableCard(
    app: com.hermes.deck.data.AppInfo,
    onTap: () -> Unit,
    onDismiss: () -> Unit,
    onUnstack: (() -> Unit)? = null,
    onRevealedChange: ((Boolean) -> Unit)? = null,
    isCurrentPage: Boolean = true,
    isDragging: Boolean = false,
    isStack: Boolean = false,
    stackOffsetY: MutableFloatState? = null,
    downDismissTrackPx: Float = 0f,
    elevation: Dp = 8.dp,
    dimAlpha: Float = 1f,
    resolvedIcon: Drawable? = null,
    modifier: Modifier = Modifier
) {
    val scope   = rememberCoroutineScope()
    val density = LocalDensity.current
    val view                    = LocalView.current
    val dismissThresholdPx      = with(density) { 120.dp.toPx() }
    // Halfway between card bottom and search bar — both the haptic peak and the dismiss point
    val halfwayPx               = if (downDismissTrackPx > 0f) downDismissTrackPx / 2f
                                  else with(density) { 90.dp.toPx() }
    val stackDismissThresholdPx = halfwayPx
    val actionRevealThresholdPx = with(density) {  60.dp.toPx() }
    val actionSnapYPx           = with(density) {  90.dp.toPx() }
    // Spring haptic: intervals shrink from maxInterval → minInterval as drag approaches halfwayPx
    val maxIntervalPx           = with(density) { 28.dp.toPx() }
    val minIntervalPx           = with(density) {  5.dp.toPx() }
    val hapticProgress          = remember { floatArrayOf(0f) }  // next threshold to fire
    // Single haptic for non-stack cards when drag crosses the reveal threshold
    val revealHapticFired       = remember { booleanArrayOf(false) }

    // For stacks the offsetY is owned by the parent and applied to the container, not just the front card.
    val internalOffsetY = remember(app.packageName) { mutableFloatStateOf(0f) }
    val offsetYState    = stackOffsetY ?: internalOffsetY
    var offsetY by offsetYState
    var revealed  by remember(app.packageName) { mutableStateOf(false) }
    var dismissed by remember(app.packageName) { mutableStateOf(false) }
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

    fun handleDragEnd() {
        scope.launch {
            when {
                offsetY < -dismissThresholdPx && !currentRevealed -> {
                    dismissed = true
                    animate(
                        offsetY, -2000f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
                    ) { v, _ -> offsetY = v }
                    onDismiss()
                }
                isStack && offsetY > stackDismissThresholdPx -> {
                    animate(offsetY, 0f, animationSpec = spring(stiffness = Spring.StiffnessHigh)) { v, _ -> offsetY = v }
                    onUnstack?.invoke()
                }
                offsetY > actionRevealThresholdPx && !currentRevealed && !isStack -> {
                    revealed = true
                    animate(offsetY, actionSnapYPx, animationSpec = cardSpring) { v, _ -> offsetY = v }
                }
                currentRevealed -> {
                    if (offsetY < actionSnapYPx / 2f || offsetY > actionSnapYPx) {
                        val from = offsetY
                        revealed = false
                        animate(from, 0f, animationSpec = cardSpring) { v, _ -> offsetY = v }
                    } else {
                        animate(offsetY, actionSnapYPx, animationSpec = cardSpring) { v, _ -> offsetY = v }
                    }
                }
                isStack -> {
                    val from = offsetY
                    animate(
                        from, 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness    = Spring.StiffnessMediumLow
                        )
                    ) { v, _ -> offsetY = v }
                }
                else -> {
                    val from = offsetY
                    animate(from, 0f, animationSpec = cardSpring) { v, _ -> offsetY = v }
                }
            }
        }
    }

    val draggableState = rememberDraggableState { delta ->
        if (!currentDismissed) {
            val next = offsetY + delta
            offsetY = if (currentRevealed) next.coerceAtLeast(0f) else next
            // Spring haptic: fire when past threshold, then set next threshold progressively closer
            if (!isStack && !currentRevealed && offsetY >= actionRevealThresholdPx && !revealHapticFired[0]) {
                    revealHapticFired[0] = true
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
            if (isStack && offsetY > 0f && offsetY >= hapticProgress[0]) {
                val progress = (offsetY / halfwayPx).coerceIn(0f, 1f)
                val hapticConst = when {
                    progress < 0.50f -> HapticFeedbackConstants.CLOCK_TICK
                    progress < 0.85f -> HapticFeedbackConstants.VIRTUAL_KEY
                    else             -> HapticFeedbackConstants.LONG_PRESS
                }
                view.performHapticFeedback(hapticConst)
                // Quadratic compression: intervals shrink faster near the halfway point
                val nextInterval = lerp(maxIntervalPx, minIntervalPx, progress * progress)
                hapticProgress[0] = offsetY + nextInterval
            }
        }
    }

    val verticalDragModifier = if (isCurrentPage && !isDragging) {
        Modifier.draggable(
            orientation   = Orientation.Vertical,
            state         = draggableState,
            onDragStarted = { hapticProgress[0] = maxIntervalPx / 2f; revealHapticFired[0] = false },
            onDragStopped = { handleDragEnd() }
        )
    } else Modifier

    Box(modifier = modifier.fillMaxSize().graphicsLayer { clip = false }.then(verticalDragModifier)) {
        // Drawn first so the card overlays it — card slides down to reveal actions from behind
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
                .offset { IntOffset(0, if (stackOffsetY == null) offsetY.roundToInt() else 0) }
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp, vertical = 16.dp)
                    .shadow(elevation, RoundedCornerShape(CARD_CORNER), clip = false)
                    .fillMaxSize()
            ) {
                AppCard(
                    app          = app,
                    resolvedIcon = resolvedIcon,
                    onTap        = if (revealed) {
                        {
                            scope.launch {
                                val from = offsetY
                                revealed = false
                                animate(from, 0f, animationSpec = cardSpring) { v, _ -> offsetY = v }
                            }
                            Unit
                        }
                    } else onTap,
                    onLongPress  = null,
                    modifier     = Modifier.fillMaxSize()
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

private val cardSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
