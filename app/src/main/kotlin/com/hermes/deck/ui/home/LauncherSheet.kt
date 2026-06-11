package com.hermes.deck.ui.home

import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Process
import android.net.Uri
import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Widgets
import android.provider.Settings
import com.hermes.deck.ui.search.TagEditorDialog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hermes.deck.data.AppInfo
import com.hermes.deck.data.IconPackInfo
import com.hermes.deck.data.IconPackRepository
import com.hermes.deck.data.IconShape
import com.hermes.deck.ui.common.rememberAppIconBitmap
import android.appwidget.AppWidgetManager
import com.hermes.deck.ui.drawer.AlphabetSlider
import com.hermes.deck.ui.drawer.DrawerViewModel
import com.hermes.deck.ui.drawer.DrawerViewMode
import com.hermes.deck.ui.drawer.WidgetPickerDialog
import com.hermes.deck.ui.search.SearchResult
import com.hermes.deck.ui.search.ResultGroup
import com.hermes.deck.ui.search.SearchResultRow
import com.hermes.deck.ui.search.ClaudeConversation
import com.hermes.deck.ui.search.activateSearchResult
import com.hermes.deck.ui.search.groupResults
import com.hermes.deck.ui.search.providerIdForResult
import com.hermes.deck.ui.search.isAnswerCard
import com.hermes.deck.ui.search.groupScore
import com.hermes.deck.ui.search.SearchViewModel
import com.hermes.deck.ui.search.providers.ClaudeChatStore
import com.hermes.deck.ui.search.providers.ClaudeDeepLink
import com.hermes.deck.ui.search.providers.ClaudeNotifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

private val PLACEHOLDER_WORDS = listOf(
    "groove", "vibe", "fix", "jam", "move",
    "people", "thing", "song", "place", "answer"
)

private val DRAWER_TOP_GAP = 13.dp          // gap below status bar when drawer fully open

/** Fraction of drawerHeight that must be dragged upward to commit to DrawerOpen. */
private const val SWIPE_COMMIT_FRACTION = 0.35f

private const val FLING_OPEN_THRESHOLD  = 500f   // px/s upward → snap open
private const val FLING_CLOSE_THRESHOLD = 100f   // px/s downward → snap closed
private const val MIN_OPEN_DRAG         = 40f    // px of net upward travel a fling must clear to open the
                                                 // drawer — rejects fast thumb-taps (matches HomeScreen's 40px dead-zone)

private val sheetSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness    = Spring.StiffnessMediumLow
)

// ---------------------------------------------------------------------------
// Mode
// ---------------------------------------------------------------------------

/** Focused = keyboard up, pill stays at collapsed height, no text yet. */
private enum class SheetMode { Collapsed, Focused, Searching, DrawerOpen }

// ---------------------------------------------------------------------------
// Public entry point
// ---------------------------------------------------------------------------

/**
 * Two-layer bottom sheet:
 *  - **Drawer box** slides up from the bottom and stops just below the status bar.
 *    Contains the app grid or search results. No search field inside.
 *  - **Floating pill** sits above the nav bar independently (NOT inside the drawer box).
 *    Fades as the drawer opens. Handles drag-up gestures to open the drawer.
 *
 * Modes:
 *  - **Collapsed**  – pill visible, drawer off-screen.
 *  - **Focused**    – keyboard up, active search field in pill, drawer off-screen.
 *  - **Searching**  – drawer open, search results visible; pill shows active field.
 *  - **DrawerOpen** – drawer open, app grid visible; pill faded out.
 */
@Composable
fun LauncherSheet(
    pendingKeyInput: StateFlow<String>,
    onKeyInputConsumed: () -> Unit,
    backspaceEvent: SharedFlow<Unit>,
    openEvent: SharedFlow<Unit>? = null,
    closeEvent: SharedFlow<Unit>? = null,
    modifier: Modifier = Modifier
) {
    val context      = LocalContext.current
    val density      = LocalDensity.current
    val scope        = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // ViewModels
    val searchVm: SearchViewModel = viewModel(factory = SearchViewModel.factory(context))
    val drawerVm: DrawerViewModel = viewModel(factory = DrawerViewModel.factory(context))

    // Observable state
    val query         by searchVm.query.collectAsState()
    val results       by searchVm.results.collectAsState()
    val recentClicks  by searchVm.recentClicks.collectAsState()
    val apps          by drawerVm.filteredApps.collectAsState()
    val gridColumns   by drawerVm.gridColumns.collectAsState()
    val viewMode      by drawerVm.viewMode.collectAsState()
    val letterIndex   by drawerVm.letterIndex.collectAsState()
    val resolvedIcons  by drawerVm.resolvedIcons.collectAsState()
    val iconShape      by drawerVm.iconShape.collectAsState()
    val overrideIcons  by drawerVm.overrideIcons.collectAsState()
    val iconOverrides  by drawerVm.iconOverrides.collectAsState()
    val installedPacks by drawerVm.installedPacks.collectAsState()

    // Sheet mode
    var mode by remember { mutableStateOf(SheetMode.Collapsed) }

    // Placeholder word cycling on ON_RESUME
    var wordIndex by remember { mutableIntStateOf(PLACEHOLDER_WORDS.indices.random()) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                wordIndex = (wordIndex + 1) % PLACEHOLDER_WORDS.size
                drawerVm.refreshFromPrefs()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // Hardware key forwarding
    val pendingInput by pendingKeyInput.collectAsState()
    LaunchedEffect(pendingInput) {
        if (pendingInput.isNotEmpty()) {
            if (mode == SheetMode.Collapsed || mode == SheetMode.DrawerOpen) {
                mode = SheetMode.Searching
            }
            searchVm.onQueryChange(query + pendingInput)
            onKeyInputConsumed()
        }
    }
    LaunchedEffect(backspaceEvent) {
        backspaceEvent.collect {
            if (query.isNotEmpty()) searchVm.onQueryChange(query.dropLast(1))
        }
    }
    // Going home (home gesture / home key while Deck is already foregrounded) collapses the sheet —
    // closes an open search box / chat / drawer back to the resting pill.
    LaunchedEffect(closeEvent) {
        closeEvent?.collect {
            if (mode != SheetMode.Collapsed) {
                searchVm.clearQuery()
                focusManager.clearFocus()
                mode = SheetMode.Collapsed
            }
        }
    }

    // Drive mode from query changes
    LaunchedEffect(query) {
        if (query.isNotBlank() && mode !in setOf(SheetMode.Searching, SheetMode.DrawerOpen)) {
            mode = SheetMode.Searching
        }
        // Clearing text keeps the search overlay open so the user can keep typing.
    }

    // Back handlers — LIFO: last registered = highest priority.
    // DrawerOpen + Searching predictive back are registered inside BoxWithConstraints (need drawerHeightAnim).
    BackHandler(enabled = mode == SheetMode.Focused) {
        mode = SheetMode.Collapsed
    }

    // A tapped "Claude replied" notification (MainActivity → ClaudeDeepLink) reopens that chat.
    LaunchedEffect(Unit) {
        ClaudeDeepLink.pendingSessionId.collect { id ->
            if (id != null) {
                ClaudeChatStore.get(context, id)?.let { session ->
                    searchVm.resumeClaude(session)
                    mode = SheetMode.Searching
                }
                ClaudeNotifications.cancel(context, id)
                ClaudeDeepLink.pendingSessionId.value = null
            }
        }
    }

    // Focus requester for the active search field
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(mode) {
        if (mode == SheetMode.Searching || mode == SheetMode.Focused) {
            kotlinx.coroutines.delay(80)
            runCatching { focusRequester.requestFocus() }
        } else if (mode == SheetMode.Collapsed) {
            focusManager.clearFocus()
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val fullHeightPx    = constraints.maxHeight.toFloat()
        val statusBarPx     = WindowInsets.statusBars.getTop(density).toFloat()
        val navBarPx        = WindowInsets.navigationBars.getBottom(density).toFloat()
        val imeBottomPx     = WindowInsets.ime.getBottom(density).toFloat()
        // 5dp above the nav bar normally; a roomier 16dp above the keyboard. RAMP smoothly between
        // them over the last 16dp of IME inset instead of STEPPING when it crosses the nav bar — a
        // step there makes the (IME-tracking) search-box top edge jump down-and-back as it closes.
        val bottomGapPx     = with(density) {
            val t = ((imeBottomPx - navBarPx) / 16.dp.toPx()).coerceIn(0f, 1f)
            5.dp.toPx() + (16.dp.toPx() - 5.dp.toPx()) * t
        }
        val topGapPx        = with(density) { DRAWER_TOP_GAP.toPx() }
        val pillHeightPx    = with(density) { 56.dp.toPx() }

        // pillBottomY accounts for keyboard so the drawer bottom matches the pill position
        val pillBottomY     = fullHeightPx - maxOf(navBarPx, imeBottomPx) - bottomGapPx

        // maxDrawerHeight = distance from pill bottom up to statusBar + gap
        val maxDrawerHeight = (pillBottomY - statusBarPx - topGapPx).coerceAtLeast(0f)

        // Animatable height: 0f = drawer invisible, maxDrawerHeight = fully open.
        // The drawer's bottom edge is always pinned at pillBottomY.
        val drawerHeightAnim = remember { Animatable(0f) }

        // Animate the height on mode change. BUT when the panel is already settled-open and only the
        // keyboard (IME) size changes, SNAP-track maxDrawerHeight instead of springing: the spring
        // lags the IME inset (which drives the bottom edge directly), so the top edge dips down and
        // bounces back up as the keyboard closes. Snapping keeps the height in lockstep with the IME,
        // pinning the top edge so the box just grows/shrinks downward.
        var prevMode by remember { mutableStateOf(mode) }
        var prevMax  by remember { mutableStateOf(maxDrawerHeight) }
        LaunchedEffect(mode, maxDrawerHeight) {
            val open = mode == SheetMode.Searching || mode == SheetMode.DrawerOpen
            // settled-open (height already at the old max) + same mode = a pure keyboard resize.
            val imeResizeWhileOpen = open && mode == prevMode && drawerHeightAnim.value >= prevMax - 1f
            prevMode = mode
            prevMax  = maxDrawerHeight
            val target = if (open) maxDrawerHeight else 0f
            if (drawerHeightAnim.value > maxDrawerHeight) drawerHeightAnim.snapTo(maxDrawerHeight)
            if (imeResizeWhileOpen) drawerHeightAnim.snapTo(target)
            else drawerHeightAnim.animateTo(target, sheetSpring)
        }

        // Predictive back for DrawerOpen: animate drawer proportionally to gesture progress,
        // spring closed on commit, spring open on cancel.
        PredictiveBackHandler(enabled = mode == SheetMode.DrawerOpen) { progress ->
            try {
                progress.collect { backEvent ->
                    drawerHeightAnim.snapTo(maxDrawerHeight * (1f - backEvent.progress))
                }
                drawerHeightAnim.animateTo(0f, sheetSpring)
                mode = SheetMode.Collapsed
            } catch (e: kotlinx.coroutines.CancellationException) {
                drawerHeightAnim.animateTo(maxDrawerHeight, sheetSpring)
                throw e
            }
        }

        // Predictive back for the search panel: shrink it proportionally to the gesture, commit to
        // Collapsed (clearing the query) or spring back open on cancel — mirrors the drawer above.
        PredictiveBackHandler(enabled = mode == SheetMode.Searching) { progress ->
            try {
                progress.collect { backEvent ->
                    drawerHeightAnim.snapTo(maxDrawerHeight * (1f - backEvent.progress))
                }
                drawerHeightAnim.animateTo(0f, sheetSpring)
                searchVm.clearQuery()
                mode = SheetMode.Collapsed
            } catch (e: kotlinx.coroutines.CancellationException) {
                drawerHeightAnim.animateTo(maxDrawerHeight, sheetSpring)
                throw e
            }
        }

        // External open trigger (e.g. swipe from dead zone in HomeScreen)
        if (openEvent != null) {
            LaunchedEffect(openEvent) {
                openEvent.collect {
                    if (mode != SheetMode.DrawerOpen) {
                        mode = SheetMode.DrawerOpen
                        drawerHeightAnim.animateTo(maxDrawerHeight, sheetSpring)
                    }
                }
            }
        }

        // Hoisted so onPreFling can check scroll position before deciding to close.
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        val gridState = rememberLazyGridState()

        // NestedScrollConnection: swipe down in the grid/results collapses the drawer.
        // onPreFling handles BOTH velocity-fling AND slow-drag releases (position check).
        val drawerNestedScroll = remember(maxDrawerHeight) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    // Upward scroll while drawer is partially closed → re-open drawer
                    // before the grid gets a chance to scroll its content.
                    if (mode == SheetMode.DrawerOpen && available.y < 0f &&
                        drawerHeightAnim.value < maxDrawerHeight &&
                        source == NestedScrollSource.Drag
                    ) {
                        scope.launch {
                            drawerHeightAnim.snapTo(
                                (drawerHeightAnim.value - available.y).coerceAtMost(maxDrawerHeight)
                            )
                        }
                        return Offset(0f, available.y)
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    if (mode == SheetMode.DrawerOpen && available.y > 0f &&
                        source == NestedScrollSource.Drag
                    ) {
                        scope.launch {
                            drawerHeightAnim.snapTo(
                                (drawerHeightAnim.value - available.y).coerceAtLeast(0f)
                            )
                        }
                        return Offset(0f, available.y)
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (mode != SheetMode.DrawerOpen) return Velocity.Zero
                    val atTop = if (viewMode == DrawerViewMode.List)
                        listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                    else
                        gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
                    val fastDownFling    = available.y > FLING_CLOSE_THRESHOLD && atTop
                    val partiallyOpen   = drawerHeightAnim.value > 1f && drawerHeightAnim.value < maxDrawerHeight
                    if (fastDownFling || partiallyOpen) {
                        val snapClosed = fastDownFling ||
                            drawerHeightAnim.value < maxDrawerHeight * (1f - SWIPE_COMMIT_FRACTION)
                        if (snapClosed) {
                            drawerHeightAnim.animateTo(0f, sheetSpring)
                            mode = SheetMode.Collapsed
                        } else {
                            drawerHeightAnim.animateTo(maxDrawerHeight, sheetSpring)
                        }
                        return available
                    }
                    return Velocity.Zero
                }
            }
        }

        // openProgress: 0..1 fraction of drawer open (ungated so drag-then-release animates correctly)
        val openProgress = if (maxDrawerHeight > 0f)
            (drawerHeightAnim.value / maxDrawerHeight).coerceIn(0f, 1f) else 0f
        // Pill fades out — fully gone at 30% drawer height
        // Searching uses a full-screen overlay; pill is hidden so it doesn't peek behind it.
        val pillAlpha = if (mode == SheetMode.Focused) 1f
                        else (1f - openProgress / 0.3f).coerceIn(0f, 1f)

        val pillSearchActive = mode == SheetMode.Focused || mode == SheetMode.Searching
        val pillHPad by animateDpAsState(
            targetValue  = if (pillSearchActive) 12.dp else 24.dp,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness    = Spring.StiffnessMedium
            ),
            label = "pillHPad"
        )

        // Absorbs all pointer events when drawer is open, blocking the CardStrip below.
        // Must be first (lowest Z-order) so the drawer box above it still handles its own gestures.
        if (mode == SheetMode.DrawerOpen || mode == SheetMode.Searching) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(mode) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false).consume()
                            var event = awaitPointerEvent()
                            while (event.changes.any { it.pressed }) {
                                event.changes.forEach { it.consume() }
                                event = awaitPointerEvent()
                            }
                        }
                    }
            )
        }

        // ── Layer 1: Drawer box (behind pill) ────────────────────────────────
        // Bottom edge pinned at pillBottomY. Height grows upward as drawer opens.
        // When height = 0 the box is invisible. When height = maxDrawerHeight the
        // top sits at statusBarPx + DRAWER_TOP_GAP.
        // Never render smaller than the pill so the drawer starts at pill size on swipe.
        // When the panel is settled fully-open (not animating, height already at max) and only the
        // keyboard is resizing, read maxDrawerHeight DIRECTLY — in lockstep with pillBottomY in the
        // same frame — instead of the height animatable, whose IME snap runs a frame late and made
        // the top edge stutter on keyboard close. During a real drag the value is well below max →
        // follow it; during any spring isRunning is true → animate normally. (Reading .value below
        // keeps this recomposing on every frame.) No gesture code touched.
        // Log confirmed: the height animatable's IME snap lands ONE FRAME late, so `anim` lags
        // `maxDrawerHeight` all the way down a keyboard close → waiting for `anim >= max` never engages.
        // Instead detect that `maxDrawerHeight` is CHANGING this frame (= the keyboard is animating,
        // not a finger drag — a drag keeps max constant while anim drops) and read max directly, in
        // lockstep with pillBottomY. So: pinned if fully-open & not springing & (max moving OR settled).
        var prevFrameMax by remember { mutableStateOf(maxDrawerHeight) }
        val maxChanging = kotlin.math.abs(maxDrawerHeight - prevFrameMax) > 1f
        val pinnedOpen = (mode == SheetMode.Searching || mode == SheetMode.DrawerOpen) &&
            !drawerHeightAnim.isRunning &&
            (maxChanging || drawerHeightAnim.value >= maxDrawerHeight - 1f)
        val renderedHeightPx = if (pinnedOpen) maxDrawerHeight.coerceAtLeast(pillHeightPx)
                               else drawerHeightAnim.value.coerceAtLeast(pillHeightPx)
        SideEffect { prevFrameMax = maxDrawerHeight }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { renderedHeightPx.toDp() })
                .offset { IntOffset(0, (pillBottomY - renderedHeightPx).roundToInt()) }
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(with(density) {
                    (renderedHeightPx / 2f).coerceAtMost(28.dp.toPx()).toDp()
                }))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                // Slow-drag release snap: pointerInput fires after any press-release,
                // including slow drags that don't generate a fling velocity.
                .pointerInput(maxDrawerHeight) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        waitForUpOrCancellation()
                        if (mode == SheetMode.DrawerOpen &&
                            drawerHeightAnim.value > 0f &&
                            drawerHeightAnim.value < maxDrawerHeight
                        ) {
                            scope.launch {
                                val snapClosed =
                                    drawerHeightAnim.value < maxDrawerHeight * (1f - SWIPE_COMMIT_FRACTION)
                                if (snapClosed) {
                                    drawerHeightAnim.animateTo(0f, sheetSpring)
                                    mode = SheetMode.Collapsed
                                } else {
                                    drawerHeightAnim.animateTo(maxDrawerHeight, sheetSpring)
                                }
                            }
                        }
                    }
                }
        ) {
            val showDrawerGrid = mode == SheetMode.DrawerOpen ||
                (mode == SheetMode.Collapsed && drawerHeightAnim.value > 0f)
            when {
                showDrawerGrid -> {
                    val borderColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    val border = 16.dp
                    val fade = 24.dp
                    val totalFade = border + fade
                    val solidFraction = border / totalFade
                    // Draw the top/bottom border+fade INSIDE the list's own draw pass instead of
                    // overlaying Boxes. An overlay Box swallows taps on the bottom-row icons (e.g.
                    // "Deck Settings") when the list is scrolled to the top — a draw-only fade can't.
                    val fadeModifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()
                            val fadePx = totalFade.toPx()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0f to borderColor, solidFraction to borderColor, 1f to Color.Transparent,
                                    startY = 0f, endY = fadePx
                                ),
                                size = Size(size.width, fadePx)
                            )
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0f to Color.Transparent, (1f - solidFraction) to borderColor, 1f to borderColor,
                                    startY = size.height - fadePx, endY = size.height
                                ),
                                topLeft = Offset(0f, size.height - fadePx),
                                size = Size(size.width, fadePx)
                            )
                        }
                    Box(Modifier.fillMaxSize()) {
                        if (viewMode == DrawerViewMode.List) {
                            LazyColumn(
                                state          = listState,
                                contentPadding = PaddingValues(
                                    start  = border,
                                    end    = border + 20.dp,
                                    top    = totalFade,
                                    bottom = totalFade
                                ),
                                modifier       = fadeModifier
                                    .nestedScroll(drawerNestedScroll)
                            ) {
                                items(apps, key = { it.packageName }) { app ->
                                    SheetAppListItem(
                                        app                 = app,
                                        resolvedIcon        = overrideIcons[app.packageName] ?: resolvedIcons[app.packageName],
                                        iconShape           = iconShape,
                                        installedPacks      = installedPacks,
                                        currentIconOverride = iconOverrides[app.packageName],
                                        onClick             = {
                                            context.packageManager
                                                .getLaunchIntentForPackage(app.packageName)
                                                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                ?.let { context.startActivity(it) }
                                            mode = SheetMode.Collapsed
                                        },
                                        onHide             = { drawerVm.hideApp(app.packageName) },
                                        onUninstall        = {
                                            context.startActivity(
                                                Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        },
                                        onIconOverride     = { packPkg -> drawerVm.setIconOverride(app.packageName, packPkg) }
                                    )
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                state                 = gridState,
                                columns               = GridCells.Fixed(gridColumns),
                                contentPadding        = PaddingValues(
                                    start  = border,
                                    end    = border + 20.dp,
                                    top    = totalFade,
                                    bottom = totalFade
                                ),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement   = Arrangement.spacedBy(20.dp),
                                modifier              = fadeModifier
                                    .nestedScroll(drawerNestedScroll)
                            ) {
                                items(apps, key = { it.packageName }) { app ->
                                    SheetAppGridItem(
                                        app                 = app,
                                        resolvedIcon        = overrideIcons[app.packageName] ?: resolvedIcons[app.packageName],
                                        iconShape           = iconShape,
                                        installedPacks      = installedPacks,
                                        currentIconOverride = iconOverrides[app.packageName],
                                        onClick             = {
                                            context.packageManager
                                                .getLaunchIntentForPackage(app.packageName)
                                                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                ?.let { context.startActivity(it) }
                                            mode = SheetMode.Collapsed
                                        },
                                        onHide             = { drawerVm.hideApp(app.packageName) },
                                        onUninstall        = {
                                            context.startActivity(
                                                Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        },
                                        onIconOverride     = { packPkg -> drawerVm.setIconOverride(app.packageName, packPkg) }
                                    )
                                }
                            }
                        }
                        // Invisible top drag-strip (the fade itself is now drawn by fadeModifier above).
                        Box(
                            Modifier.fillMaxWidth().height(totalFade).align(Alignment.TopCenter)
                                .pointerInput(maxDrawerHeight) {
                                    awaitEachGesture {
                                        val vt = VelocityTracker()
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        vt.addPointerInputChange(down)
                                        var dragging = false
                                        dragLoop@ while (true) {
                                            val event = awaitPointerEvent()
                                            for (change in event.changes) {
                                                vt.addPointerInputChange(change)
                                                if (!change.pressed) break@dragLoop
                                                val dy = change.position.y - change.previousPosition.y
                                                if (dy > 0f) {
                                                    dragging = true
                                                    change.consume()
                                                    scope.launch {
                                                        drawerHeightAnim.snapTo(
                                                            (drawerHeightAnim.value - dy).coerceAtLeast(0f)
                                                        )
                                                    }
                                                } else if (dragging && dy < 0f) {
                                                    change.consume()
                                                    scope.launch {
                                                        drawerHeightAnim.snapTo(
                                                            (drawerHeightAnim.value - dy).coerceAtMost(maxDrawerHeight)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        if (dragging) {
                                            val vel = vt.calculateVelocity()
                                            scope.launch {
                                                val snapClosed = vel.y > FLING_CLOSE_THRESHOLD ||
                                                    drawerHeightAnim.value < maxDrawerHeight * (1f - SWIPE_COMMIT_FRACTION)
                                                if (snapClosed) {
                                                    drawerHeightAnim.animateTo(0f, sheetSpring)
                                                    mode = SheetMode.Collapsed
                                                } else {
                                                    drawerHeightAnim.animateTo(maxDrawerHeight, sheetSpring)
                                                }
                                            }
                                        }
                                    }
                                }
                        )
                        Box(Modifier.width(border).fillMaxHeight().align(Alignment.CenterStart).background(borderColor))
                        // Drawn last so it renders above the border/gradient overlays
                        AlphabetSlider(
                            letters       = letterIndex.keys.sorted(),
                            letterIndex   = letterIndex,
                            scrollToIndex = { idx ->
                                if (viewMode == DrawerViewMode.List) listState.scrollToItem(idx)
                                else gridState.scrollToItem(idx)
                            },
                            fullHeightPx  = maxDrawerHeight.toInt(),
                            modifier      = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = border - 8.dp, top = totalFade, bottom = totalFade)
                                .width(32.dp)
                                .fillMaxHeight()
                        )
                    }
                }

                mode == SheetMode.Searching -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Field row, with the invisible drag-to-close handle OVERLAYING its top edge
                        // (added after the Row, below). As an overlay it adds no layout height, so the
                        // field keeps the same gap above its text as the collapsed pill.
                        Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            // end=8 (not 16) so the search icon lands 40dp from the edge — matching
                            // the collapsed pill (pillHPad 24 + its own 16 padding), not 8dp further left.
                            modifier          = Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                // In a chat, Back returns to the search results; otherwise it closes search.
                                if (searchVm.activeChat.value != null) searchVm.endClaude()
                                else { searchVm.clearQuery(); mode = SheetMode.Collapsed }
                            }) {
                                Icon(
                                    imageVector        = Icons.Default.ArrowBack,
                                    contentDescription = "Back",
                                    tint               = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            val searchCtx = context
                            ActiveSearchField(
                                query          = query,
                                wordIndex      = wordIndex,
                                focusRequester = focusRequester,
                                onQueryChange  = searchVm::onQueryChange,
                                onSearch       = {
                                    // Enter activates the top result; Claude starts a chat;
                                    // falls back to a web search when there are no results.
                                    val top = results.firstOrNull()
                                    when {
                                        top is SearchResult.ClaudeResult -> searchVm.startClaude(top.query)
                                        top is SearchResult.HermesResult -> searchVm.startHermes(top.query)
                                        top != null && activateSearchResult(searchCtx, top) -> {
                                            searchVm.clearQuery(); mode = SheetMode.Collapsed
                                        }
                                        results.isEmpty() && query.isNotBlank() -> {
                                            launchWebSearch(searchCtx, query)
                                            searchVm.clearQuery()
                                            mode = SheetMode.Collapsed
                                        }
                                    }
                                },
                                modifier       = Modifier.weight(1f)
                            )
                        }
                            // Invisible handle over the field's top edge — drag down to close.
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .height(20.dp)
                                    .pointerInput(maxDrawerHeight) {
                                        detectVerticalDragGestures(
                                            onVerticalDrag = { change, dy ->
                                                change.consume()
                                                scope.launch {
                                                    drawerHeightAnim.snapTo(
                                                        (drawerHeightAnim.value - dy).coerceIn(0f, maxDrawerHeight)
                                                    )
                                                }
                                            },
                                            onDragEnd = {
                                                scope.launch {
                                                    if (drawerHeightAnim.value < maxDrawerHeight * (1f - SWIPE_COMMIT_FRACTION)) {
                                                        drawerHeightAnim.animateTo(0f, sheetSpring)
                                                        searchVm.clearQuery(); mode = SheetMode.Collapsed
                                                    } else {
                                                        drawerHeightAnim.animateTo(maxDrawerHeight, sheetSpring)
                                                    }
                                                }
                                            }
                                        )
                                    }
                            )
                        }
                        LaunchedEffect(results) {
                            val widget = results.filterIsInstance<SearchResult.WidgetPickerResult>().firstOrNull()
                            if (widget != null && query.isNotBlank()) {
                                searchVm.recordWidgetSeen(widget)
                            }
                        }
                        val activeChat by searchVm.activeChat.collectAsState()
                        val claudeThinking by searchVm.claudeThinking.collectAsState()
                        val rankedOrder by searchVm.rankedGroupOrder.collectAsState()
                        val aiDomains by searchVm.queryDomains.collectAsState()
                        // Order the groups by the query-relevance ranking (empty = default order).
                        // Stable sort + a fixed ranking means a group that trickles in just slots into
                        // its place via the enter animation — no reorder of groups already on screen.
                        val plexSplit = context.getSharedPreferences("deck_prefs", android.content.Context.MODE_PRIVATE)
                            .getBoolean("plex_split_libraries", false)
                        val grouped = remember(results, rankedOrder, plexSplit, query, aiDomains) {
                            val g = groupResults(results, plexSplit)
                            // Group ranking, most-relevant first:
                            //  1. computed-answer cards (calculator / unit / currency / …) always lead;
                            //  2. then by relevance score (groupScore): title match + content domain.
                            //     With Gemini-Nano query domains (opt-in) it boosts the domains the model
                            //     says the query means and demotes the rest; without them it falls back to
                            //     the heuristic (an incidental same-named music track ranks below the
                            //     film/comic). aiDomains arrives LATE, re-running this sort to snap groups
                            //     into the smarter order;
                            //  3. then the query→provider relevance ranking as a tiebreaker (default order
                            //     when re-rank is off). Stable sort, so a late-arriving group slots into
                            //     place via its enter animation rather than reordering what's on screen.
                            g.sortedWith(compareBy(
                                { (_, items) -> if (items.any { isAnswerCard(it) }) 0 else 1 },
                                { (_, items) -> -groupScore(items, query, aiDomains) },
                                { (_, items) ->
                                    if (rankedOrder.isEmpty()) 0
                                    else {
                                        val pid = items.firstOrNull()?.let { providerIdForResult(it) }
                                        rankedOrder.indexOf(pid).let { if (it < 0) Int.MAX_VALUE else it }
                                    }
                                }
                            ))
                        }
                        val chat = activeChat
                        if (chat != null) {
                            ClaudeConversation(
                                state            = chat,
                                onSend           = { searchVm.replyClaude(it) },
                                bottomInset      = 16.dp,   // gap below the reply bar, matching the 16dp sides
                                thinking         = claudeThinking,
                                onToggleThinking = { searchVm.toggleClaudeThinking() },
                                onConfirmAction  = { searchVm.confirmClaudeAction(it) },
                                onCancelAction   = { searchVm.cancelClaudeAction(it) },
                                modifier         = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                            )
                        } else {
                            // A plain scrolling Column (not LazyColumn) so a group that trickles in
                            // near the TOP (e.g. Plex, which loads late but ranks high) is revealed in
                            // place instead of being prepended above the viewport — LazyColumn anchors
                            // the old top item, pushing the new group off-screen and forcing a scroll
                            // up. Search result sets are short, so eager composition is fine.
                            val scrollState = rememberScrollState()
                            // New query → back to the top so the highest-priority results are visible.
                            LaunchedEffect(query) { scrollState.scrollTo(0) }
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState)
                                    .padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
                            ) {
                                if (query.isBlank() && results.isNotEmpty()) {
                                    Text(
                                        text     = "Recent",
                                        style    = MaterialTheme.typography.labelMedium,
                                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
                                    )
                                }
                                // Slots that have already appeared this query — a slot animates in only
                                // on its genuine first appearance, never again on a reorder/recompose.
                                val seenSlots = remember(query) { mutableSetOf<String>() }
                                grouped.forEach { (label, groupItems) ->
                                    if (label == "Widgets") {
                                        groupItems.forEach { result ->
                                            val comp = (result as? SearchResult.WidgetPickerResult)?.providers?.firstOrNull()?.componentName
                                                ?: (result as? SearchResult.WidgetPickerResult)?.appPackage
                                                ?: return@forEach
                                            key("widget:$comp") {
                                                val firstTime = remember { "widget:$comp" !in seenSlots }
                                                SideEffect { seenSlots.add("widget:$comp") }
                                                EnterAnimated(animate = firstTime) {
                                                    SearchResultRow(
                                                        result           = result,
                                                        onDismiss        = { searchVm.clearQuery(); mode = SheetMode.Collapsed },
                                                        retryKey         = true,
                                                        onResultSelected = searchVm::recordClick
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        key("group:$label") {
                                            val firstTime = remember { "group:$label" !in seenSlots }
                                            SideEffect { seenSlots.add("group:$label") }
                                            EnterAnimated(animate = firstTime) {
                                                ResultGroup(
                                                    label            = label,
                                                    results          = groupItems,
                                                    onDismiss        = {
                                                        searchVm.clearQuery()
                                                        mode = SheetMode.Collapsed
                                                    },
                                                    retryKey         = true,
                                                    resolvedIcon     = { result ->
                                                        (result as? SearchResult.AppResult)?.let {
                                                            overrideIcons[it.app.packageName] ?: resolvedIcons[it.app.packageName]
                                                        }
                                                    },
                                                    iconShape        = iconShape,
                                                    onManage         = null,
                                                    onResultSelected = searchVm::recordClick,
                                                    onClaudeStart    = { searchVm.startClaude(it) },
                                                    onClaudeResume   = { searchVm.resumeClaude(it) },
                                                    onHermesStart    = { searchVm.startHermes(it) },
                                                )
                                            }
                                        }
                                    }
                                }
                                if (query.isNotBlank()) {
                                    val ctx = LocalContext.current
                                    ListItem(
                                        headlineContent = { Text("Search the web for \"$query\"") },
                                        leadingContent  = { Icon(Icons.Default.Search, contentDescription = null) },
                                        colors          = ListItemDefaults.colors(containerColor = Color.Transparent),
                                        modifier        = Modifier.clickable {
                                            launchWebSearch(ctx, query)
                                            searchVm.clearQuery()
                                            mode = SheetMode.Collapsed
                                        }
                                    )
                                }
                            }
                        }

                    }
                }

            }
        }

        // ── Tap-to-dismiss overlay (between drawer and pill) ──────────────────
        // Visible only when Focused: tapping anywhere outside the pill clears focus.
        if (mode == SheetMode.Focused) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {
                        focusManager.clearFocus()
                        mode = SheetMode.Collapsed
                    }
            )
        }

        // ── Layer 2: Floating pill (in front, independent of drawer) ─────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(start = pillHPad, end = pillHPad, bottom = 5.dp)
                .graphicsLayer {
                    alpha = pillAlpha
                }
                .drawWithContent {
                    drawContent()
                    if (openProgress > 0f && mode != SheetMode.Searching && mode != SheetMode.Focused) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                startY = 0f,
                                endY   = size.height * openProgress,
                                colors = listOf(Color.Transparent, Color.Black)
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
                }
                // Don't even APPLY the pill's drag pointerInput when it's invisible/irrelevant
                // (Searching or DrawerOpen): a present-but-idle pointer node still intercepts taps on
                // the drawer's bottom rows beneath the invisible pill (e.g. "Deck Settings"). A
                // conditional .then removes the node entirely instead of early-returning inside it.
                .then(
                    if (mode == SheetMode.Searching || mode == SheetMode.DrawerOpen) Modifier
                    else Modifier.pointerInput(mode, maxDrawerHeight) {
                    awaitEachGesture {
                        val vt   = VelocityTracker()
                        val down = awaitFirstDown(requireUnconsumed = false)
                        vt.addPointerInputChange(down)
                        // Use the system touch slop (not a hard 8px) so a normal tap — which always
                        // drifts a few px — is NOT mistaken for a drag. A tap must fall through to the
                        // "open search" branch below even while a close spring is still running.
                        val slop = viewConfiguration.touchSlop
                        var cumDelta = 0f   // NET vertical drag (− = up); drives the open/close decision
                        var tracking = false
                        dragLoop@ while (true) {
                            val event = awaitPointerEvent()
                            for (change in event.changes) {
                                vt.addPointerInputChange(change)
                                if (!change.pressed) break@dragLoop
                                val dy = change.position.y - change.previousPosition.y
                                cumDelta += dy
                                if (!tracking && cumDelta < -slop) tracking = true
                                if (tracking) {
                                    // The first snapTo cancels any in-flight close spring, so the
                                    // drawer follows the finger cleanly from wherever it now sits.
                                    change.consume()
                                    scope.launch {
                                        drawerHeightAnim.snapTo(
                                            (drawerHeightAnim.value - dy).coerceIn(0f, maxDrawerHeight)
                                        )
                                    }
                                }
                            }
                        }
                        if (tracking) {
                            val vel  = vt.calculateVelocity()
                            val drag = -cumDelta   // net upward travel in px (+ = up)
                            android.util.Log.d("DeckSheet", "pill release: drag=$drag velY=${vel.y} mode=$mode")
                            // A real open needs actual travel: a FAR drag, OR a fast flick that still
                            // cleared MIN_OPEN_DRAG. A quick thumb-tap drifts just past touch-slop and
                            // releases fast (high velocity, tiny travel) — that velocity-only path used to
                            // false-open the drawer. Decide on NET upward drag (cumDelta), not the animated
                            // height, which is contaminated by a still-running close spring.
                            val snapOpen = drag > maxDrawerHeight * SWIPE_COMMIT_FRACTION ||
                                (vel.y < -FLING_OPEN_THRESHOLD && drag > MIN_OPEN_DRAG)
                            if (snapOpen) {
                                scope.launch {
                                    mode = SheetMode.DrawerOpen
                                    drawerHeightAnim.animateTo(maxDrawerHeight, sheetSpring)
                                }
                            } else if (drag < MIN_OPEN_DRAG && mode == SheetMode.Collapsed) {
                                // Tap-sized travel: the finger barely moved → treat it AS a tap and open
                                // search (rather than leaving a fast tap doing nothing). The mode change
                                // drives drawerHeightAnim up via LaunchedEffect(mode).
                                mode = if (recentClicks.isNotEmpty()) SheetMode.Searching else SheetMode.Focused
                            } else {
                                // Aborted partial drag (real travel, but not committed) → settle closed.
                                scope.launch { drawerHeightAnim.animateTo(0f, sheetSpring) }
                            }
                        } else if (mode == SheetMode.Collapsed) {
                            mode = if (recentClicks.isNotEmpty()) SheetMode.Searching else SheetMode.Focused
                        }
                    }
                }
                )
        ) {
            // Only compose the pill content while it's actually visible. When the drawer is open
            // (>30% open → pillAlpha 0) the invisible SearchPill's background Row would otherwise sit
            // over the drawer's bottom rows and swallow taps on them (e.g. "Deck Settings"). It still
            // fades in correctly during the close animation, since that's when pillAlpha rises above 0.
            if (pillAlpha > 0f) {
                if (mode == SheetMode.Focused || mode == SheetMode.Searching) {
                    ActiveSearchField(
                        query          = query,
                        wordIndex      = wordIndex,
                        focusRequester = focusRequester,
                        onQueryChange  = searchVm::onQueryChange,
                        modifier       = Modifier.fillMaxWidth()
                    )
                } else {
                    SearchPill(wordIndex = wordIndex, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// EnterAnimated — expand + fade a search-result group into place the first time
// it appears (e.g. a slow provider like Plex finishing). Enter-only; the
// transition state is remembered per call site (wrap with key(...) so a new
// group animates once and trickle updates to an existing group don't re-fire).
// ---------------------------------------------------------------------------

@Composable
private fun EnterAnimated(animate: Boolean = true, content: @Composable () -> Unit) {
    // animate=false → start already-visible (no enter transition). Used for a slot that has already
    // appeared this query, so a later reorder/recompose can't re-fire its enter animation.
    val visible = remember { MutableTransitionState(!animate).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn(tween(420)) + expandVertically(tween(420)),
        exit  = ExitTransition.None
    ) { content() }
}

// ---------------------------------------------------------------------------
// SearchPill — visual display of the collapsed pill (no gesture logic)
// ---------------------------------------------------------------------------

@Composable
private fun SearchPill(wordIndex: Int, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier              = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            AnimatedPlaceholder(wordIndex = wordIndex)
        }
        Icon(
            imageVector        = Icons.Default.Search,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier           = Modifier.size(28.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// ActiveSearchField — Searching mode; hosts BasicTextField + IME
// ---------------------------------------------------------------------------

@Composable
private fun ActiveSearchField(
    query: String,
    wordIndex: Int,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onSearch: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(query, TextRange(query.length))) }
    LaunchedEffect(query) {
        if (fieldValue.text != query) {
            fieldValue = TextFieldValue(query, TextRange(query.length))
        }
    }
    BasicTextField(
        value           = fieldValue,
        onValueChange   = { fieldValue = it; onQueryChange(it.text) },
        singleLine      = true,
        textStyle       = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface
        ),
        cursorBrush     = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
        modifier        = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .focusRequester(focusRequester),
        decorationBox = { innerTextField ->
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier              = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        AnimatedPlaceholder(wordIndex = wordIndex)
                    }
                    innerTextField()
                }
                Icon(
                    imageVector        = Icons.Default.Search,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier           = Modifier.size(28.dp)
                )
            }
        }
    )
}

// ---------------------------------------------------------------------------
// AnimatedPlaceholder
// ---------------------------------------------------------------------------

@Composable
private fun AnimatedPlaceholder(wordIndex: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text  = "Find your… ",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AnimatedContent(
            targetState   = PLACEHOLDER_WORDS[wordIndex],
            transitionSpec = {
                fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
            },
            label         = "placeholder_word"
        ) { word ->
            Text(
                text       = word,
                fontStyle  = FontStyle.Italic,
                fontWeight = FontWeight.SemiBold,
                style      = MaterialTheme.typography.bodyLarge,
                color      = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------------------
// SheetAppGridItem
// ---------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SheetAppGridItem(
    app: AppInfo,
    onClick: () -> Unit,
    onHide: () -> Unit,
    onUninstall: () -> Unit,
    onIconOverride: (String?) -> Unit,
    installedPacks: List<IconPackInfo> = emptyList(),
    currentIconOverride: String? = null,
    resolvedIcon: Drawable? = null,
    iconShape: IconShape = IconShape.NONE
) {
    val context = LocalContext.current
    val iconBitmap = rememberAppIconBitmap(
        key       = app.packageName,
        drawable  = resolvedIcon ?: app.icon,
        iconShape = iconShape,
        size      = 192
    )

    val widgetProviders by produceState<List<android.appwidget.AppWidgetProviderInfo>>(emptyList(), app.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                AppWidgetManager.getInstance(context)
                    .getInstalledProviders()
                    .filter { it.provider.packageName == app.packageName }
            }.getOrDefault(emptyList())
        }
    }

    var showMenu         by remember { mutableStateOf(false) }
    var showIconPicker   by remember { mutableStateOf(false) }
    var showWidgetPicker by remember { mutableStateOf(false) }
    var showTagEditor    by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val (launcherApps, shortcuts) = rememberAppShortcuts(app.packageName, showMenu)

    Box {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick     = onClick,
                    onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); showMenu = true }
                )
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            iconBitmap?.let {
                Image(
                    bitmap             = it.asImageBitmap(),
                    contentDescription = app.label,
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier.size(52.dp)
                )
            } ?: Spacer(Modifier.size(52.dp))
            Text(
                text      = app.label,
                style     = MaterialTheme.typography.labelMedium,
                color     = MaterialTheme.colorScheme.onSurface,
                maxLines  = 2,
                minLines  = 2,
                overflow  = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier  = Modifier.fillMaxWidth()
            )
        }
        AppContextMenu(
            expanded         = showMenu,
            onDismissRequest = { showMenu = false },
            hasShortcuts     = shortcuts.isNotEmpty(),
            shortcuts        = {
                shortcuts.forEach { sc -> AppShortcutItem(launcherApps, sc) { showMenu = false } }
            },
            actions          = {
                AppContextMenuItem("Edit tags", Icons.Default.Label) { showMenu = false; showTagEditor = true }
                AppContextMenuItem("Hide", Icons.Default.VisibilityOff) { showMenu = false; onHide() }
                AppContextMenuItem("Change icon", Icons.Default.Palette) { showMenu = false; showIconPicker = true }
                AppContextMenuItem("Uninstall", Icons.Default.Delete) { showMenu = false; onUninstall() }
                if (widgetProviders.isNotEmpty()) AppContextMenuItem("Select widget", Icons.Default.Widgets) { showMenu = false; showWidgetPicker = true }
                AppContextMenuItem("App info", Icons.Default.Info) {
                    showMenu = false
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", app.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }
            }
        )
        if (showTagEditor) {
            TagEditorDialog(
                packageName = app.packageName,
                title       = app.label,
                onDismiss   = { showTagEditor = false }
            )
        }
        if (showIconPicker) {
            IconPickerDialog(
                app             = app,
                installedPacks  = installedPacks,
                currentOverride = currentIconOverride,
                onPick          = onIconOverride,
                onDismiss       = { showIconPicker = false }
            )
        }
        if (showWidgetPicker) {
            WidgetPickerDialog(
                app             = app,
                widgetProviders = widgetProviders,
                onDismiss       = { showWidgetPicker = false }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// SheetAppListItem
// ---------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SheetAppListItem(
    app: AppInfo,
    onClick: () -> Unit,
    onHide: () -> Unit,
    onUninstall: () -> Unit,
    onIconOverride: (String?) -> Unit,
    installedPacks: List<IconPackInfo> = emptyList(),
    currentIconOverride: String? = null,
    resolvedIcon: Drawable? = null,
    iconShape: IconShape = IconShape.NONE
) {
    val context = LocalContext.current
    val iconBitmap = rememberAppIconBitmap(
        key       = app.packageName,
        drawable  = resolvedIcon ?: app.icon,
        iconShape = iconShape,
        size      = 128
    )

    val widgetProviders by produceState<List<android.appwidget.AppWidgetProviderInfo>>(emptyList(), app.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                AppWidgetManager.getInstance(context)
                    .getInstalledProviders()
                    .filter { it.provider.packageName == app.packageName }
            }.getOrDefault(emptyList())
        }
    }

    var showMenu         by remember { mutableStateOf(false) }
    var showIconPicker   by remember { mutableStateOf(false) }
    var showWidgetPicker by remember { mutableStateOf(false) }
    var showTagEditor    by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val (launcherApps, shortcuts) = rememberAppShortcuts(app.packageName, showMenu)

    Box {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .combinedClickable(
                    onClick     = onClick,
                    onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); showMenu = true }
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            iconBitmap?.let {
                Image(
                    bitmap             = it.asImageBitmap(),
                    contentDescription = app.label,
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier.size(40.dp)
                )
            } ?: Spacer(Modifier.size(40.dp))
            Text(
                text     = app.label,
                style    = MaterialTheme.typography.bodyLarge,
                color    = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        AppContextMenu(
            expanded         = showMenu,
            onDismissRequest = { showMenu = false },
            hasShortcuts     = shortcuts.isNotEmpty(),
            shortcuts        = {
                shortcuts.forEach { sc -> AppShortcutItem(launcherApps, sc) { showMenu = false } }
            },
            actions          = {
                AppContextMenuItem("Edit tags", Icons.Default.Label) { showMenu = false; showTagEditor = true }
                AppContextMenuItem("Hide", Icons.Default.VisibilityOff) { showMenu = false; onHide() }
                AppContextMenuItem("Change icon", Icons.Default.Palette) { showMenu = false; showIconPicker = true }
                AppContextMenuItem("Uninstall", Icons.Default.Delete) { showMenu = false; onUninstall() }
                if (widgetProviders.isNotEmpty()) AppContextMenuItem("Select widget", Icons.Default.Widgets) { showMenu = false; showWidgetPicker = true }
                AppContextMenuItem("App info", Icons.Default.Info) {
                    showMenu = false
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", app.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }
            }
        )
        if (showTagEditor) {
            TagEditorDialog(
                packageName = app.packageName,
                title       = app.label,
                onDismiss   = { showTagEditor = false }
            )
        }
        if (showIconPicker) {
            IconPickerDialog(
                app             = app,
                installedPacks  = installedPacks,
                currentOverride = currentIconOverride,
                onPick          = onIconOverride,
                onDismiss       = { showIconPicker = false }
            )
        }
        if (showWidgetPicker) {
            WidgetPickerDialog(
                app             = app,
                widgetProviders = widgetProviders,
                onDismiss       = { showWidgetPicker = false }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// AppContextMenu — rounded floating panel replacing DropdownMenu
// ---------------------------------------------------------------------------

@Composable
internal fun AppContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    hasShortcuts: Boolean,
    shortcuts: @Composable ColumnScope.() -> Unit,
    actions: @Composable ColumnScope.() -> Unit
) {
    val expandedState = remember { MutableTransitionState(false) }
    expandedState.targetState = expanded

    if (expandedState.currentState || expandedState.targetState) {
        Popup(
            alignment        = Alignment.TopStart,
            onDismissRequest = onDismissRequest,
            properties       = PopupProperties(focusable = expanded)
        ) {
            AnimatedVisibility(
                visibleState = expandedState,
                enter = scaleIn(
                    animationSpec   = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness    = Spring.StiffnessMedium
                    ),
                    transformOrigin = TransformOrigin(0f, 0f)
                ) + fadeIn(tween(150)),
                exit  = scaleOut(
                    animationSpec   = tween(120),
                    targetScale     = 0.85f,
                    transformOrigin = TransformOrigin(0f, 0f)
                ) + fadeOut(tween(120))
            ) {
                // Two separate boxes with a small gap: shortcuts on top, app actions below.
                // The actions box starts collapsed behind "More options" when shortcuts exist
                // (when there are none, it opens expanded since it's the only content).
                var actionsExpanded by remember(expanded, hasShortcuts) { mutableStateOf(!hasShortcuts) }
                Column(
                    modifier            = Modifier.width(IntrinsicSize.Max),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (hasShortcuts) {
                        AppContextMenuBox { shortcuts() }
                    }
                    AppContextMenuBox(
                        modifier = Modifier.animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness    = Spring.StiffnessMedium
                            )
                        )
                    ) {
                        if (actionsExpanded) {
                            actions()
                        } else {
                            AppContextMenuItem("More options", Icons.Default.MoreHoriz) { actionsExpanded = true }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppContextMenuBox(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape           = RoundedCornerShape(24.dp),
        color           = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation  = 3.dp,
        shadowElevation = 8.dp,
        modifier        = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier            = modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content             = content
        )
    }
}

@Composable
internal fun AppContextMenuItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            modifier           = Modifier.size(20.dp),
            tint               = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

// ---------------------------------------------------------------------------
// App shortcuts (Android static/dynamic/pinned shortcuts) in the long-press menu
// ---------------------------------------------------------------------------

/** Loads an app's launcher shortcuts. Requires Deck to be the default launcher — otherwise
 *  getShortcuts throws SecurityException and we just show none. */
private fun loadAppShortcuts(launcherApps: LauncherApps, packageName: String): List<ShortcutInfo> =
    runCatching {
        val query = LauncherApps.ShortcutQuery().apply {
            setPackage(packageName)
            setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
        }
        launcherApps.getShortcuts(query, Process.myUserHandle())
            ?.filter { it.isEnabled }
            ?.sortedBy { it.rank }
            ?.take(5)
            ?: emptyList()
    }.getOrDefault(emptyList())

internal fun shortcutDrawableToBitmap(d: Drawable): Bitmap {
    (d as? BitmapDrawable)?.bitmap?.let { return it }
    val w = d.intrinsicWidth.coerceIn(1, 144)
    val h = d.intrinsicHeight.coerceIn(1, 144)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    d.setBounds(0, 0, w, h)
    d.draw(Canvas(bmp))
    return bmp
}

/** Returns (LauncherApps, shortcuts). Shortcuts are loaded only while [load] is true (menu open). */
@Composable
internal fun rememberAppShortcuts(packageName: String, load: Boolean): Pair<LauncherApps, List<ShortcutInfo>> {
    val context = LocalContext.current
    val launcherApps = remember { context.getSystemService(android.content.Context.LAUNCHER_APPS_SERVICE) as LauncherApps }
    val shortcuts by produceState<List<ShortcutInfo>>(emptyList(), packageName, load) {
        value = if (load) withContext(Dispatchers.IO) { loadAppShortcuts(launcherApps, packageName) }
                else emptyList()
    }
    return launcherApps to shortcuts
}

@Composable
private fun AppShortcutItem(
    launcherApps: LauncherApps,
    shortcut: ShortcutInfo,
    onLaunched: () -> Unit
) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.densityDpi
    val iconBitmap by produceState<Bitmap?>(null, shortcut.id) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                launcherApps.getShortcutIconDrawable(shortcut, density)?.let { shortcutDrawableToBitmap(it) }
            }.getOrNull()
        }
    }
    val label = (shortcut.shortLabel ?: shortcut.longLabel ?: "").toString()
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable {
                runCatching { launcherApps.startShortcut(shortcut, null, null) }
                onLaunched()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap             = iconBitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier           = Modifier.size(20.dp)
            )
        } else {
            Spacer(Modifier.size(20.dp))
        }
        Text(
            text     = label,
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------------------------------------------------------------------------
// IconPickerDialog — two-level: pack list → icon browser
// ---------------------------------------------------------------------------

private sealed class PickerScreen {
    object PackList : PickerScreen()
    data class IconBrowser(val pack: IconPackInfo) : PickerScreen()
}

@Composable
internal fun IconPickerDialog(
    app: AppInfo,
    installedPacks: List<IconPackInfo>,
    currentOverride: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(currentOverride) }
    var screen   by remember { mutableStateOf<PickerScreen>(PickerScreen.PackList) }

    // Android back navigates within the dialog before dismissing
    BackHandler(enabled = screen is PickerScreen.IconBrowser) {
        screen = PickerScreen.PackList
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            when (val s = screen) {
                is PickerScreen.PackList -> Text("Change icon")
                is PickerScreen.IconBrowser -> Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { screen = PickerScreen.PackList }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text     = s.pack.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        text = {
            when (val s = screen) {
                is PickerScreen.PackList -> {
                    if (installedPacks.isEmpty()) {
                        Text(
                            "No icon packs installed. Install an icon pack to use custom icons.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier            = Modifier.heightIn(max = 360.dp)
                        ) {
                            item(key = "default") {
                                PackRow(
                                    label    = "Default",
                                    icon     = app.icon,
                                    selected = selected == null,
                                    onClick  = { selected = null }
                                )
                            }
                            items(installedPacks, key = { it.packageName }) { pack ->
                                PackRow(
                                    label    = pack.label,
                                    icon     = pack.icon,
                                    selected = selected?.startsWith("${pack.packageName}:") == true,
                                    onClick  = { screen = PickerScreen.IconBrowser(pack) },
                                    showArrow = true
                                )
                            }
                        }
                    }
                }
                is PickerScreen.IconBrowser -> IconBrowserContent(
                    pack          = s.pack,
                    currentOverride = selected,
                    onPickDrawable = { drawableName ->
                        selected = "${s.pack.packageName}:$drawableName"
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(selected); onDismiss() }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun PackRow(
    label: String,
    icon: Drawable,
    selected: Boolean,
    onClick: () -> Unit,
    showArrow: Boolean = false
) {
    val bitmap by produceState<Bitmap?>(null, icon) {
        value = withContext(Dispatchers.Default) {
            val size = 96
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bmp ->
                icon.setBounds(0, 0, size, size)
                icon.draw(Canvas(bmp))
            }
        }
    }
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        bitmap?.let {
            Image(it.asImageBitmap(), contentDescription = null,
                contentScale = ContentScale.Fit, modifier = Modifier.size(40.dp))
        } ?: Spacer(Modifier.size(40.dp))
        Text(
            text     = label,
            style    = MaterialTheme.typography.bodyMedium,
            color    = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                       else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (showArrow) {
            Icon(
                imageVector        = Icons.Default.ArrowBack,
                contentDescription = null,
                modifier           = Modifier.size(16.dp).rotate(180f),
                tint               = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun IconBrowserContent(
    pack: IconPackInfo,
    currentOverride: String?,
    onPickDrawable: (String) -> Unit
) {
    val context = LocalContext.current
    val repo    = remember(context) { IconPackRepository(context) }
    var searchQuery by remember { mutableStateOf("") }

    val drawableNames by produceState<List<String>?>(null, pack.packageName) {
        value = withContext(Dispatchers.IO) { repo.loadAllIconDrawableNames(pack.packageName) }
    }

    val names = drawableNames
    if (names == null) {
        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (names.isEmpty()) {
        Text("No icons found in this pack.", style = MaterialTheme.typography.bodyMedium)
        return
    }

    val filteredNames = remember(names, searchQuery) {
        if (searchQuery.isBlank()) names
        else names.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    val currentDrawable = currentOverride
        ?.takeIf { it.startsWith("${pack.packageName}:") }
        ?.removePrefix("${pack.packageName}:")

    Column {
        OutlinedTextField(
            value         = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder   = { Text("Search icons") },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )
        LazyVerticalGrid(
            columns               = GridCells.Fixed(4),
            verticalArrangement   = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier              = Modifier.heightIn(max = 300.dp)
        ) {
            items(filteredNames, key = { it }) { drawableName ->
                val drawable by produceState<Drawable?>(null, pack.packageName, drawableName) {
                    value = withContext(Dispatchers.IO) {
                        repo.getIconByDrawableName(pack.packageName, drawableName)
                    }
                }
                val bmp by produceState<Bitmap?>(null, drawable) {
                    val d = drawable ?: return@produceState
                    value = withContext(Dispatchers.Default) {
                        val size = 96
                        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { b ->
                            d.setBounds(0, 0, size, size); d.draw(Canvas(b))
                        }
                    }
                }
                val isSelected = currentDrawable == drawableName
                Box(
                    modifier         = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                            else Color.Transparent
                        )
                        .clickable { onPickDrawable(drawableName) }
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    bmp?.let {
                        Image(it.asImageBitmap(), contentDescription = drawableName,
                            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    } ?: CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun launchWebSearch(context: android.content.Context, query: String) {
    context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com/?q=${Uri.encode(query)}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private fun resultKey(result: SearchResult): Any = when (result) {
    is SearchResult.AppResult          -> "app:${result.app.packageName}"
    is SearchResult.ContactResult      -> "contact:${result.name}:${result.phoneNumber}"
    is SearchResult.CalculatorResult   -> "calc:${result.expression}"
    is SearchResult.PluginResult       -> "plugin:${result.pluginId}:${result.title}"
    is SearchResult.AiResult           -> "ai:${result.query}"
    is SearchResult.ClaudeResult       -> "claude:${result.query}"
    is SearchResult.HermesResult       -> "hermes:${result.query}"
    is SearchResult.WidgetPickerResult -> "widget:${result.providers.firstOrNull()?.componentName ?: result.appPackage}"
    is SearchResult.DialerResult       -> "dialer:${result.phoneNumber}"
    is SearchResult.SettingsResult       -> "settings:${result.section}:${result.title}"
    is SearchResult.SystemSettingsResult -> "system_settings:${result.action}"
    is SearchResult.BrowserHistoryResult   -> "browser:${result.url}"
    is SearchResult.FileResult             -> "file:${result.path}"
    is SearchResult.BrowserSuggestionResult -> "suggestion:${result.suggestion}"
    is SearchResult.HomeAssistantResult     -> "ha:${result.entityId}"
    is SearchResult.PlexResult              -> "plex:${result.ratingKey}"
    is SearchResult.PersonResult            -> "person:${result.id}"
    is SearchResult.TandoorResult           -> "tandoor:${result.id}"
    is SearchResult.SymfoniumResult         -> "symfonium:${result.mediaId}"
    is SearchResult.TransistorResult        -> "transistor:${result.mediaId}"
    is SearchResult.PlacesResult            -> "places:${result.query}"
    is SearchResult.WikipediaResult         -> "wiki:${result.url}"
    is SearchResult.AnswerResult            -> "answer:${result.providerId}:${result.value}"
    is SearchResult.GmailResult             -> "gmail:${result.query}"
    is SearchResult.YouTubeResult           -> "youtube:${result.videoId}"
    is SearchResult.TimerResult             -> "timer:${result.displayText}"
    is SearchResult.WeatherResult           -> "weather:${result.location}"
    is SearchResult.DictionaryResult        -> "dict:${result.word}"
    is SearchResult.OfflineAnswerResult     -> "offline:${result.query}"
    is SearchResult.AddMediaResult          -> "${result.service}:${result.title}:${result.year}"
    is SearchResult.TodoTaskResult          -> "todo:${result.id}"
    is SearchResult.TodoAddResult           -> "todoadd:${result.query}"
}
