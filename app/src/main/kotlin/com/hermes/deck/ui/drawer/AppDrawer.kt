package com.hermes.deck.ui.drawer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.content.pm.LauncherApps
import com.hermes.deck.ui.common.rememberAppIconBitmap
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.hermes.deck.data.AppInfo
import com.hermes.deck.data.WidgetPinRepository
import com.hermes.deck.ui.search.TagEditorDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AppDrawer(
    onAppLaunch: (AppInfo) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vm: DrawerViewModel = viewModel(factory = DrawerViewModel.factory(context))
    val apps        by vm.filteredApps.collectAsState()
    val letterIndex by vm.letterIndex.collectAsState()
    val gridColumns by vm.gridColumns.collectAsState()
    val viewMode    by vm.viewMode.collectAsState()

    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    Box(
        modifier = modifier
            .padding(top = 20.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp))) {
            // Fade the top/bottom edges by DRAWING the gradient inside the list's own draw pass
            // (drawWithContent) instead of overlaying Boxes — an overlay Box swallows taps on the
            // icons beneath it, whereas a draw-only fade never participates in hit-testing.
            val fadeColor = MaterialTheme.colorScheme.surfaceContainerHigh
            val fadeModifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    val fadePx = 32.dp.toPx()
                    drawRect(
                        brush = Brush.verticalGradient(listOf(fadeColor, Color.Transparent), startY = 0f, endY = fadePx),
                        size  = Size(size.width, fadePx)
                    )
                    drawRect(
                        brush   = Brush.verticalGradient(
                            listOf(Color.Transparent, fadeColor),
                            startY = size.height - fadePx, endY = size.height
                        ),
                        topLeft = Offset(0f, size.height - fadePx),
                        size    = Size(size.width, fadePx)
                    )
                }
            if (viewMode == DrawerViewMode.List) {
                LazyColumn(
                    state          = listState,
                    contentPadding = PaddingValues(
                        start  = 16.dp,
                        end    = 36.dp,
                        top    = 16.dp,
                        bottom = 16.dp
                    ),
                    modifier = fadeModifier
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
                    modifier              = fadeModifier
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

        }
    }
}

@Composable
internal fun AlphabetSlider(
    letters: List<Char>,
    letterIndex: Map<Char, Int>,
    scrollToIndex: suspend (Int) -> Unit,
    modifier: Modifier = Modifier,
    fullHeightPx: Int = 0
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var activeLetter by remember { mutableStateOf<Char?>(null) }
    var activeY      by remember { mutableFloatStateOf(0f) }

    fun letterAt(yPx: Float, heightPx: Float): Char? {
        if (letters.isEmpty()) return null
        val fraction = (yPx / heightPx).coerceIn(0f, 1f)
        return letters[(fraction * letters.size).toInt().coerceIn(0, letters.lastIndex)]
    }

    fun scrollTo(yPx: Float, heightPx: Float) {
        val ch = letterAt(yPx, heightPx) ?: return
        activeLetter = ch
        activeY      = yPx
        letterIndex[ch]?.let { itemIdx -> scope.launch { scrollToIndex(itemIdx) } }
    }

    Box(
        modifier = modifier.pointerInput(letters, letterIndex) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                scrollTo(down.position.y, size.height.toFloat())
                var event = awaitPointerEvent()
                while (event.changes.any { it.pressed }) {
                    event.changes.forEach { change ->
                        change.consume()
                        scrollTo(change.position.y, size.height.toFloat())
                    }
                    event = awaitPointerEvent()
                }
                activeLetter = null
            }
        }
    ) {
        val indicatorSize = 72.dp
        activeLetter?.let { ch ->
            val yOffsetDp = with(density) { activeY.toDp() } - indicatorSize / 2
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = -(indicatorSize + 8.dp), y = yOffsetDp.coerceAtLeast(0.dp))
                    .requiredSize(indicatorSize)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text       = ch.toString(),
                    color      = MaterialTheme.colorScheme.onPrimary,
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        val sliderFadeColor = MaterialTheme.colorScheme.surfaceContainerHigh
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .drawWithContent {
                    drawContent()
                    val fadePx = 48.dp.toPx()
                    drawRect(
                        brush   = Brush.verticalGradient(
                            listOf(Color.Transparent, sliderFadeColor),
                            startY = size.height - fadePx, endY = size.height
                        ),
                        topLeft = Offset(0f, size.height - fadePx),
                        size    = Size(size.width, fadePx)
                    )
                }
        ) {
            val usedHeight = if (fullHeightPx > 0) fullHeightPx else constraints.maxHeight
            val itemHeightPx = if (letters.isNotEmpty() && usedHeight > 0) usedHeight / letters.size else 0
            letters.forEachIndexed { i, ch ->
                Text(
                    text       = ch.toString(),
                    fontSize   = 9.sp,
                    lineHeight = 9.sp,
                    textAlign  = TextAlign.Center,
                    color      = if (ch == activeLetter) MaterialTheme.colorScheme.primary
                                 else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (ch == activeLetter) FontWeight.Bold
                                 else FontWeight.Normal,
                    modifier   = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(0, itemHeightPx * i) }
                        .height(with(density) { itemHeightPx.toDp() })
                        .wrapContentHeight(Alignment.CenterVertically)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppGridItem(app: AppInfo, onClick: () -> Unit, onHide: () -> Unit = {}) {
    val context = LocalContext.current
    val iconBitmap = rememberAppIconBitmap(key = app.packageName, drawable = app.icon, size = 192)

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

    var showShortcuts    by remember { mutableStateOf(false) }
    var showWidgetPicker by remember { mutableStateOf(false) }
    var showTagEditor    by remember { mutableStateOf(false) }

    Box {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
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
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (showShortcuts) {
            ShortcutMenuDialog(
                app          = app,
                iconBitmap   = iconBitmap,
                iconCorner   = 12.dp,
                shortcuts    = shortcuts,
                hasWidgets   = widgetProviders.isNotEmpty(),
                onDismiss    = { showShortcuts = false },
                onPickWidget = { showShortcuts = false; showWidgetPicker = true },
                onEditTags   = { showShortcuts = false; showTagEditor = true },
                onHide       = { showShortcuts = false; onHide() }
            )
        }

        if (showWidgetPicker) {
            WidgetPickerDialog(
                app             = app,
                widgetProviders = widgetProviders,
                onDismiss       = { showWidgetPicker = false }
            )
        }
        if (showTagEditor) {
            TagEditorDialog(
                packageName = app.packageName,
                title       = app.label,
                onDismiss   = { showTagEditor = false }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppListItem(app: AppInfo, onClick: () -> Unit, onHide: () -> Unit = {}) {
    val context = LocalContext.current
    val iconBitmap = rememberAppIconBitmap(key = app.packageName, drawable = app.icon, size = 128)

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

    var showShortcuts    by remember { mutableStateOf(false) }
    var showWidgetPicker by remember { mutableStateOf(false) }
    var showTagEditor    by remember { mutableStateOf(false) }

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

        if (showShortcuts) {
            ShortcutMenuDialog(
                app          = app,
                iconBitmap   = iconBitmap,
                iconCorner   = 8.dp,
                shortcuts    = shortcuts,
                hasWidgets   = widgetProviders.isNotEmpty(),
                onDismiss    = { showShortcuts = false },
                onPickWidget = { showShortcuts = false; showWidgetPicker = true },
                onEditTags   = { showShortcuts = false; showTagEditor = true },
                onHide       = { showShortcuts = false; onHide() }
            )
        }

        if (showWidgetPicker) {
            WidgetPickerDialog(
                app             = app,
                widgetProviders = widgetProviders,
                onDismiss       = { showWidgetPicker = false }
            )
        }
        if (showTagEditor) {
            TagEditorDialog(
                packageName = app.packageName,
                title       = app.label,
                onDismiss   = { showTagEditor = false }
            )
        }
    }
}

@Composable
private fun ShortcutMenuDialog(
    app: AppInfo,
    iconBitmap: Bitmap?,
    iconCorner: Dp,
    shortcuts: List<android.content.pm.ShortcutInfo>,
    hasWidgets: Boolean,
    onDismiss: () -> Unit,
    onPickWidget: () -> Unit,
    onEditTags: () -> Unit,
    onHide: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape            = RoundedCornerShape(28.dp),
        title            = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (iconBitmap != null) {
                    Image(
                        bitmap             = iconBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier           = Modifier.size(48.dp).clip(RoundedCornerShape(iconCorner))
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(app.label, style = MaterialTheme.typography.titleMedium)
            }
        },
        text             = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Actions
                Row(
                    modifier          = Modifier.fillMaxWidth().clickable(onClick = onEditTags).padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) { Text("Edit tags", style = MaterialTheme.typography.bodyMedium) }
                HorizontalDivider()
                if (hasWidgets) {
                    Row(
                        modifier          = Modifier.fillMaxWidth().clickable(onClick = onPickWidget).padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) { Text("Select widget", style = MaterialTheme.typography.bodyMedium) }
                    HorizontalDivider()
                }
                Row(
                    modifier          = Modifier.fillMaxWidth().clickable(onClick = onHide).padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) { Text("Hide app", style = MaterialTheme.typography.bodyMedium) }
                HorizontalDivider()
                Row(
                    modifier          = Modifier.fillMaxWidth()
                        .clickable {
                            onDismiss()
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", app.packageName, null)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) { Text("App info", style = MaterialTheme.typography.bodyMedium) }

                // Shortcuts
                if (shortcuts.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
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
                }
            }
        },
        confirmButton  = {},
        dismissButton  = null
    )
}

@Composable
internal fun WidgetPickerDialog(
    app: AppInfo,
    widgetProviders: List<android.appwidget.AppWidgetProviderInfo>,
    onDismiss: () -> Unit
) {
    val context  = LocalContext.current
    val pinRepo  = remember { WidgetPinRepository(context) }

    val initiallyPinned = remember {
        widgetProviders
            .map { it.provider.flattenToString() }
            .filter { pinRepo.isPinnedByComponent(it) }
            .toSet()
    }
    var selected by remember { mutableStateOf(initiallyPinned) }

    // Queue of component names still needing the system bind dialog
    var bindQueue      by remember { mutableStateOf(emptyList<String>()) }
    var pendingId      by remember { mutableIntStateOf(-1) }
    var pendingComp    by remember { mutableStateOf<String?>(null) }
    var nextBindIntent by remember { mutableStateOf<android.content.Intent?>(null) }
    var dismissPending by remember { mutableStateOf(false) }

    val widgetHost = remember { android.appwidget.AppWidgetHost(context.applicationContext, 1027).also { it.startListening() } }
    DisposableEffect(Unit) { onDispose { widgetHost.stopListening() } }

    val bindLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        val id   = pendingId
        val comp = pendingComp
        if (id != -1 && comp != null) {
            val manager2 = AppWidgetManager.getInstance(context)
            val bound = manager2.getAppWidgetInfo(id) != null
            if (bound) {
                pinRepo.pinWidgetByComponent(comp, id)
                val info2 = manager2.getAppWidgetInfo(id)
                if (info2 != null) {
                    val configComp2 = info2.configure
                    if (configComp2 != null) {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                                    component = configComp2
                                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        }
                    } else {
                        context.sendBroadcast(
                            android.content.Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                                setComponent(info2.provider)
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(id))
                            }
                        )
                    }
                }
            } else {
                runCatching { widgetHost.deleteAppWidgetId(id) }
            }
        }
        pendingId   = -1
        pendingComp = null
        val next = bindQueue.firstOrNull()
        if (next != null) {
            bindQueue = bindQueue.drop(1)
            val newId     = widgetHost.allocateAppWidgetId()
            val component = android.content.ComponentName.unflattenFromString(next)!!
            pendingId   = newId
            pendingComp = next
            nextBindIntent = android.content.Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, newId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, component)
            }
        } else {
            dismissPending = true
        }
    }

    LaunchedEffect(nextBindIntent) {
        nextBindIntent?.let { intent ->
            nextBindIntent = null
            bindLauncher.launch(intent)
        }
    }

    LaunchedEffect(dismissPending) {
        if (dismissPending) onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text("Widgets for ${app.label}") },
        text             = {
            LazyColumn {
                items(widgetProviders) { info ->
                    val label = runCatching { info.loadLabel(context.packageManager) }.getOrDefault("Widget")
                    val comp  = info.provider.flattenToString()
                    val checked = comp in selected
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
                            .clickable { selected = if (checked) selected - comp else selected + comp }
                            .padding(vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = checked, onCheckedChange = {
                                selected = if (checked) selected - comp else selected + comp
                            })
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
                val manager = AppWidgetManager.getInstance(context)
                // Unpin deselected widgets
                (initiallyPinned - selected).forEach { comp ->
                    pinRepo.getPinnedWidgetIdByComponent(comp)?.let { runCatching { widgetHost.deleteAppWidgetId(it) } }
                    pinRepo.unpinWidgetByComponent(comp)
                }
                // Bind newly selected widgets — try silent first, queue rest for system dialog
                val needsDialog = mutableListOf<String>()
                (selected - initiallyPinned).forEach { comp ->
                    val newId     = widgetHost.allocateAppWidgetId()
                    val component = android.content.ComponentName.unflattenFromString(comp)!!
                    if (manager.bindAppWidgetIdIfAllowed(newId, component)) {
                        pinRepo.pinWidgetByComponent(comp, newId)
                        manager.getAppWidgetInfo(newId)?.let { info ->
                            val configComp = info.configure
                            if (configComp != null) {
                                // Widget requires configuration — launch it; the Activity
                                // is responsible for calling updateAppWidget when done.
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                                            this.component = configComp
                                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, newId)
                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                }
                            } else {
                                // No configure Activity — kick the provider to send initial content.
                                context.sendBroadcast(
                                    android.content.Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                                        setComponent(info.provider)
                                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(newId))
                                    }
                                )
                            }
                        }
                    } else {
                        widgetHost.deleteAppWidgetId(newId)
                        needsDialog += comp
                    }
                }
                if (needsDialog.isEmpty()) {
                    onDismiss()
                } else {
                    val first = needsDialog.first()
                    val queue = needsDialog.drop(1)
                    val newId     = widgetHost.allocateAppWidgetId()
                    val component = android.content.ComponentName.unflattenFromString(first)!!
                    pendingId   = newId
                    pendingComp = first
                    bindQueue   = queue
                    bindLauncher.launch(
                        android.content.Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, newId)
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, component)
                        }
                    )
                }
            }) { Text("Save") }
        },
        dismissButton    = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
