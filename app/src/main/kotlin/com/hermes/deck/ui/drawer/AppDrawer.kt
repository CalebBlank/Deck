package com.hermes.deck.ui.drawer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.content.pm.LauncherApps
import android.util.Log
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.appwidget.AppWidgetManager
import com.hermes.deck.data.AppInfo
import com.hermes.deck.data.WidgetPinRepository
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class DrawerValue { Closed, Open }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawer(
    onClose: () -> Unit,
    onAppLaunch: (AppInfo) -> Unit,
    openSignal: SharedFlow<Unit>? = null,
    closeSignal: SharedFlow<Unit>? = null,
    dragDeltaFlow: SharedFlow<Float>? = null,
    settleFlow: SharedFlow<Float>? = null,
    onOpen: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val drawerScope = rememberCoroutineScope()
    val vm: DrawerViewModel = viewModel(factory = DrawerViewModel.factory(context))
    val apps        by vm.filteredApps.collectAsState()
    val letterIndex by vm.letterIndex.collectAsState()
    val gridColumns by vm.gridColumns.collectAsState()
    val viewMode    by vm.viewMode.collectAsState()

    // Scroll states declared first so LaunchedEffect can reference them safely
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val isSettlingClosed = remember { mutableStateOf(false) }
    val state = remember {
        AnchoredDraggableState(
            initialValue        = DrawerValue.Closed,
            positionalThreshold = { totalDistance -> totalDistance * 0.3f },
            velocityThreshold   = { with(density) { 80.dp.toPx() } },
            animationSpec       = spring(dampingRatio = Spring.DampingRatioNoBouncy)
        )
    }

    // Merged signal handler: open, close, and settle all use collectLatest so that
    // a close signal always cancels any in-progress open settle (and vice versa).
    // settleFlow is merged here rather than collected separately so stale open-direction
    // velocities cannot execute after a close signal has already settled the drawer.
    LaunchedEffect(openSignal, closeSignal, settleFlow) {
        merge(
            (openSignal  ?: emptyFlow<Unit>()).map { Float.NEGATIVE_INFINITY },
            (closeSignal ?: emptyFlow<Unit>()).map { Float.POSITIVE_INFINITY },
            (settleFlow  ?: emptyFlow<Float>()).map { it }
        ).collectLatest { velocity ->
            Log.d("DeckDrawer", "signal velocity=$velocity (close=${velocity == Float.POSITIVE_INFINITY}, open=${velocity == Float.NEGATIVE_INFINITY})")
            when {
                velocity == Float.POSITIVE_INFINITY -> {
                    isSettlingClosed.value = true
                    state.settle(1_000_000f)
                }
                velocity == Float.NEGATIVE_INFINITY -> {
                    if (state.anchors.hasAnchorFor(DrawerValue.Open) && state.targetValue != DrawerValue.Open) {
                        state.settle(-1_000_000f)
                    }
                }
                else -> state.settle(velocity)
            }
        }
    }

    // Watch targetValue for both directions:
    // - Open:   onOpen() fires as soon as settle targets Open (not waiting for completion,
    //           since collectLatest can cancel settle and currentValue never reaches Open)
    // - Closed: onClose() fires as soon as settle targets Closed (not waiting for completion
    //           either, so drawerIsOpen resets even if settle was cancelled mid-open and
    //           currentValue was already Closed — which would never re-emit)
    LaunchedEffect(Unit) {
        snapshotFlow { state.targetValue }
            .drop(1)
            .distinctUntilChanged()
            .collect { target ->
                Log.d("DeckDrawer", "targetValue → $target")
                when (target) {
                    DrawerValue.Open -> {
                        onOpen()
                        gridState.scrollToItem(0)
                        listState.scrollToItem(0)
                    }
                    DrawerValue.Closed -> {
                        isSettlingClosed.value = false
                        onClose()
                    }
                }
            }
    }

    // Follow finger during drag from handle
    LaunchedEffect(dragDeltaFlow) {
        dragDeltaFlow?.collect { delta -> state.dispatchRawDelta(delta) }
    }

    // Back button closes the drawer based on actual physics state, not the external drawerIsOpen flag.
    BackHandler(enabled = state.targetValue == DrawerValue.Open) {
        drawerScope.launch {
            isSettlingClosed.value = true
            state.settle(1_000_000f)
        }
    }

    val nestedScrollConnection = remember(state) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                return if (source == NestedScrollSource.Drag && available.y > 0f) {
                    state.dispatchRawDelta(available.y)
                    Offset(0f, available.y)
                } else Offset.Zero
            }

            // Intercept downward flings before the list consumes them, when the drawer
            // has already been pulled down. Without this, the list's fling handler eats
            // the velocity and onPostFling receives ~0, preventing snap-to-close.
            override suspend fun onPreFling(available: Velocity): Velocity {
                val offset = runCatching { state.requireOffset() }.getOrNull() ?: return Velocity.Zero
                return if (offset > 1f && available.y > 50f && !isSettlingClosed.value) {
                    state.settle(1_000_000f)
                    available
                } else Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val decisive = when {
                    available.y > 50f  ->  1_000_000f   // flung downward → snap Closed
                    available.y < -50f -> -1_000_000f   // flung upward   → snap Open
                    else               ->  available.y  // slow release — let position threshold decide
                }
                Log.d("DeckDrawer", "onPostFling: available.y=${available.y}, isSettlingClosed=${isSettlingClosed.value}, decisive=$decisive")
                if (!isSettlingClosed.value || decisive > 0f) {
                    state.settle(decisive)
                }
                return Velocity.Zero
            }
        }
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp, top = statusBarTop + 8.dp)
                .align(Alignment.BottomCenter)
        ) {
            val fullHeight = constraints.maxHeight.toFloat()
            val closedAnchorPx = fullHeight + with(density) { 64.dp.toPx() }
            val prevClosedAnchor = remember { mutableFloatStateOf(Float.NaN) }
            SideEffect {
                if (prevClosedAnchor.floatValue != closedAnchorPx) {
                    prevClosedAnchor.floatValue = closedAnchorPx
                    state.updateAnchors(DraggableAnchors {
                        DrawerValue.Closed at closedAnchorPx
                        DrawerValue.Open   at 0f
                    })
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, if (state.anchors.size > 0) state.requireOffset().roundToInt() else constraints.maxHeight) }
                    .anchoredDraggable(state, Orientation.Vertical)
                    .clip(RoundedCornerShape(44.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(top = 20.dp)
                        .nestedScroll(nestedScrollConnection)
                ) {
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(28.dp))) {
                        if (viewMode == DrawerViewMode.List) {
                            LazyColumn(
                                state          = listState,
                                contentPadding = PaddingValues(
                                    start  = 16.dp,
                                    end    = 36.dp,
                                    top    = 16.dp,
                                    bottom = 16.dp
                                ),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(apps, key = { it.packageName }) { app ->
                                    AppListItem(
                                        app     = app,
                                        onClick = { onAppLaunch(app) },
                                        onHide  = { vm.hideApp(app.packageName) }
                                    )
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                state                 = gridState,
                                columns               = GridCells.Fixed(gridColumns),
                                contentPadding        = PaddingValues(
                                    start  = 16.dp,
                                    end    = 36.dp,
                                    top    = 16.dp,
                                    bottom = 16.dp
                                ),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement   = Arrangement.spacedBy(20.dp),
                                modifier              = Modifier.fillMaxSize()
                            ) {
                                items(apps, key = { it.packageName }) { app ->
                                    AppGridItem(
                                        app     = app,
                                        onClick = { onAppLaunch(app) },
                                        onHide  = { vm.hideApp(app.packageName) }
                                    )
                                }
                            }
                        }

                        AlphabetSlider(
                            letters       = letterIndex.keys.sorted(),
                            letterIndex   = letterIndex,
                            scrollToIndex = { idx ->
                                if (viewMode == DrawerViewMode.List) listState.scrollToItem(idx)
                                else gridState.scrollToItem(idx)
                            },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 8.dp)
                                .width(20.dp)
                                .fillMaxHeight()
                        )
                        val fadeColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .height(32.dp)
                                .background(Brush.verticalGradient(listOf(fadeColor, Color.Transparent)))
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(32.dp)
                                .background(Brush.verticalGradient(listOf(Color.Transparent, fadeColor)))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlphabetSlider(
    letters: List<Char>,
    letterIndex: Map<Char, Int>,
    scrollToIndex: suspend (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var activeLetter by remember { mutableStateOf<Char?>(null) }

    fun letterAt(yPx: Float, heightPx: Float): Char? {
        if (letters.isEmpty()) return null
        val fraction = (yPx / heightPx).coerceIn(0f, 1f)
        return letters[(fraction * letters.size).toInt().coerceIn(0, letters.lastIndex)]
    }

    fun scrollTo(yPx: Float, heightPx: Float) {
        val ch = letterAt(yPx, heightPx) ?: return
        activeLetter = ch
        letterIndex[ch]?.let { itemIdx -> scope.launch { scrollToIndex(itemIdx) } }
    }

    Box(
        modifier = modifier.pointerInput(letters, letterIndex) {
            detectVerticalDragGestures(
                onDragStart  = { offset -> scrollTo(offset.y, size.height.toFloat()) },
                onVerticalDrag = { change, _ ->
                    change.consume()
                    scrollTo(change.position.y, size.height.toFloat())
                },
                onDragEnd    = { activeLetter = null },
                onDragCancel = { activeLetter = null }
            )
        }
    ) {
        // Bubble indicator floats to the left when dragging
        activeLetter?.let { ch ->
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = (-44).dp)
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text       = ch.toString(),
                    color      = MaterialTheme.colorScheme.onPrimary,
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(
            modifier            = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            letters.forEach { ch ->
                Text(
                    text       = ch.toString(),
                    fontSize   = 9.sp,
                    textAlign  = TextAlign.Center,
                    color      = if (ch == activeLetter) MaterialTheme.colorScheme.primary
                                 else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (ch == activeLetter) androidx.compose.ui.text.font.FontWeight.Bold
                                 else androidx.compose.ui.text.font.FontWeight.Normal,
                    modifier   = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppGridItem(app: AppInfo, onClick: () -> Unit, onHide: () -> Unit = {}) {
    val context = LocalContext.current
    val iconBitmap by produceState<Bitmap?>(null, app.packageName) {
        value = withContext(Dispatchers.Default) {
            val d = app.icon
            val w = d.intrinsicWidth.coerceIn(1, 192)
            val h = d.intrinsicHeight.coerceIn(1, 192)
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                d.setBounds(0, 0, w, h)
                d.draw(Canvas(bmp))
            }
        }
    }

    val shortcuts by produceState<List<android.content.pm.ShortcutInfo>>(emptyList(), app.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val la = context.getSystemService(LauncherApps::class.java)
                val query = LauncherApps.ShortcutQuery().apply {
                    setQueryFlags(
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
                    )
                    setPackage(app.packageName)
                }
                la.getShortcuts(query, android.os.Process.myUserHandle()) ?: emptyList()
            }.getOrDefault(emptyList())
        }
    }

    val widgetProviders by produceState<List<android.appwidget.AppWidgetProviderInfo>>(emptyList(), app.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                AppWidgetManager.getInstance(context)
                    .getInstalledProviders()
                    .filter { it.provider.packageName == app.packageName }
            }.getOrDefault(emptyList())
        }
    }

    var showShortcuts by remember { mutableStateOf(false) }
    var showWidgetPicker by remember { mutableStateOf(false) }

    Box {
        Column(
            modifier            = Modifier
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick     = onClick,
                    onLongClick = { showShortcuts = true }
                )
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            iconBitmap?.let {
                Image(
                    bitmap             = it.asImageBitmap(),
                    contentDescription = app.label,
                    modifier           = Modifier.size(52.dp)
                )
            } ?: Spacer(Modifier.size(52.dp))
            Text(
                text     = app.label,
                style    = MaterialTheme.typography.labelMedium,
                color    = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (showShortcuts && iconBitmap != null) {
            ShortcutMenuDialog(
                app             = app,
                iconBitmap      = iconBitmap!!,
                iconCorner      = 12.dp,
                shortcuts       = shortcuts,
                widgetProviders = widgetProviders,
                onDismiss       = { showShortcuts = false },
                onPickWidget    = { showShortcuts = false; showWidgetPicker = true },
                onHide          = { showShortcuts = false; onHide() }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppListItem(app: AppInfo, onClick: () -> Unit, onHide: () -> Unit = {}) {
    val context = LocalContext.current
    val iconBitmap by produceState<Bitmap?>(null, app.packageName) {
        value = withContext(Dispatchers.Default) {
            val d = app.icon
            val w = d.intrinsicWidth.coerceIn(1, 128)
            val h = d.intrinsicHeight.coerceIn(1, 128)
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                d.setBounds(0, 0, w, h)
                d.draw(Canvas(bmp))
            }
        }
    }

    val shortcuts by produceState<List<android.content.pm.ShortcutInfo>>(emptyList(), app.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val la = context.getSystemService(LauncherApps::class.java)
                val query = LauncherApps.ShortcutQuery().apply {
                    setQueryFlags(
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
                    )
                    setPackage(app.packageName)
                }
                la.getShortcuts(query, android.os.Process.myUserHandle()) ?: emptyList()
            }.getOrDefault(emptyList())
        }
    }

    val widgetProviders by produceState<List<android.appwidget.AppWidgetProviderInfo>>(emptyList(), app.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                AppWidgetManager.getInstance(context)
                    .getInstalledProviders()
                    .filter { it.provider.packageName == app.packageName }
            }.getOrDefault(emptyList())
        }
    }

    var showShortcuts by remember { mutableStateOf(false) }
    var showWidgetPicker by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .combinedClickable(
                    onClick     = onClick,
                    onLongClick = { showShortcuts = true }
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            iconBitmap?.let {
                Image(
                    bitmap             = it.asImageBitmap(),
                    contentDescription = app.label,
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

        if (showShortcuts && iconBitmap != null) {
            ShortcutMenuDialog(
                app             = app,
                iconBitmap      = iconBitmap!!,
                iconCorner      = 8.dp,
                shortcuts       = shortcuts,
                widgetProviders = widgetProviders,
                onDismiss       = { showShortcuts = false },
                onPickWidget    = { showShortcuts = false; showWidgetPicker = true },
                onHide          = { showShortcuts = false; onHide() }
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

@Composable
private fun ShortcutMenuDialog(
    app: AppInfo,
    iconBitmap: Bitmap,
    iconCorner: Dp,
    shortcuts: List<android.content.pm.ShortcutInfo>,
    widgetProviders: List<android.appwidget.AppWidgetProviderInfo>,
    onDismiss: () -> Unit,
    onPickWidget: () -> Unit,
    onHide: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton    = {},
        shape            = RoundedCornerShape(28.dp),
        text             = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier          = Modifier.padding(bottom = 12.dp)
                ) {
                    Image(
                        bitmap             = iconBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier           = Modifier.size(48.dp).clip(RoundedCornerShape(iconCorner))
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(app.label, style = MaterialTheme.typography.titleMedium)
                }
                HorizontalDivider()
                shortcuts.forEach { shortcut ->
                    val shortcutIcon: Bitmap? = remember(shortcut.id) {
                        runCatching {
                            val d = context.getSystemService(LauncherApps::class.java)
                                .getShortcutIconDrawable(shortcut, 0) ?: return@runCatching null
                            val w = d.intrinsicWidth.coerceIn(1, 64)
                            val h = d.intrinsicHeight.coerceIn(1, 64)
                            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                                d.setBounds(0, 0, w, h)
                                d.draw(Canvas(bmp))
                            }
                        }.getOrNull()
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                runCatching {
                                    context.getSystemService(LauncherApps::class.java)
                                        .startShortcut(shortcut, null, null)
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (shortcutIcon != null) {
                            Image(
                                bitmap             = shortcutIcon.asImageBitmap(),
                                contentDescription = null,
                                modifier           = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(
                            text  = shortcut.shortLabel?.toString() ?: shortcut.id,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                if (shortcuts.isNotEmpty()) HorizontalDivider()
                if (widgetProviders.isNotEmpty()) {
                    TextButton(
                        onClick  = onPickWidget,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Select widget", modifier = Modifier.fillMaxWidth()) }
                }
                TextButton(
                    onClick  = onHide,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Hide", modifier = Modifier.fillMaxWidth()) }
            }
        }
    )
}

@Composable
private fun WidgetPickerDialog(
    app: AppInfo,
    widgetProviders: List<android.appwidget.AppWidgetProviderInfo>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val pinRepo = remember { WidgetPinRepository(context) }
    var selected by remember { mutableStateOf(pinRepo.getPinnedWidget(app.packageName)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text("Select widget for ${app.label}") },
        text             = {
            LazyColumn {
                item {
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .clickable { selected = null }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected == null, onClick = { selected = null })
                        Text("None", modifier = Modifier.padding(start = 8.dp))
                    }
                }
                items(widgetProviders) { info ->
                    val label = runCatching { info.loadLabel(context.packageManager) }.getOrDefault("Widget")
                    val comp  = info.provider.flattenToString()
                    val previewBitmap by produceState<ImageBitmap?>(null, info.provider) {
                        value = withContext(Dispatchers.IO) {
                            runCatching {
                                val res = context.packageManager.getResourcesForApplication(info.provider.packageName)
                                val resId = if (info.previewImage != 0) info.previewImage else info.icon
                                if (resId == 0) return@runCatching null
                                val drawable = res.getDrawable(resId, null)
                                val w = drawable.intrinsicWidth.coerceIn(1, 600)
                                val h = drawable.intrinsicHeight.coerceIn(1, 600)
                                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                drawable.setBounds(0, 0, w, h)
                                drawable.draw(Canvas(bmp))
                                bmp.asImageBitmap()
                            }.getOrNull()
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = comp }
                            .padding(vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selected == comp, onClick = { selected = comp })
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                        if (previewBitmap != null) {
                            Image(
                                bitmap             = previewBitmap!!,
                                contentDescription = label,
                                contentScale       = ContentScale.Fit,
                                modifier           = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .padding(horizontal = 8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }
                    }
                }
            }
        },
        confirmButton    = {
            TextButton(onClick = {
                if (selected != null) pinRepo.pinWidget(app.packageName, selected!!)
                else pinRepo.unpinWidget(app.packageName)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton    = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
