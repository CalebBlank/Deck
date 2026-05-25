package com.hermes.deck.ui.search

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

// Process-level cache: maps widget provider componentName → allocated AppWidget ID.
// Survives composable lifecycle so the system's per-ID BIND permission is not lost
// when the search bar collapses (e.g. the BIND permission dialog launches with
// FLAG_ACTIVITY_NEW_TASK, causing the composable to leave the composition).
// IDs are intentionally never deleted: removing an ID would revoke the system's
// per-ID BIND permission, causing the approval dialog to reappear on the next
// composition. The ID is reused from this cache when the search bar reopens.
private val widgetIdCache = HashMap<String, Int>()

private val PLACEHOLDER_WORDS = listOf(
    "groove", "vibe", "fix", "jam", "move",
    "people", "thing", "song", "place", "answer"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun LauncherSearchBar(
    pendingKeyInput: StateFlow<String>? = null,
    onKeyInputConsumed: () -> Unit = {},
    backspaceEvent: SharedFlow<Unit>? = null,
    onSearchActiveChange: ((Boolean) -> Unit)? = null,
    dismissSignal: SharedFlow<Unit>? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vm: SearchViewModel = viewModel(factory = SearchViewModel.factory(context))
    val query   by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    var manualExpanded by remember { mutableStateOf(false) }
    val expanded = query.isNotEmpty() || manualExpanded

    BackHandler(enabled = expanded) {
        vm.clearQuery()
        manualExpanded = false
        onSearchActiveChange?.invoke(false)
    }

    LaunchedEffect(dismissSignal) {
        dismissSignal?.collect {
            vm.clearQuery()
            manualExpanded = false
            onSearchActiveChange?.invoke(false)
        }
    }

    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    LaunchedEffect(imeBottom) {
        // Only act when the keyboard is fully hidden AND the bar is still considered
        // expanded. Guarding on `manualExpanded` (rather than the derived `expanded`)
        // prevents a double-fire: `onActiveChange` already sets manualExpanded = false
        // and calls clearQuery() when the bar collapses normally. If that callback has
        // already run, manualExpanded is false here and we skip the redundant updates,
        // eliminating the stutter/jump during the collapse animation.
        if (imeBottom == 0.dp && manualExpanded) {
            manualExpanded = false
            vm.clearQuery()
            onSearchActiveChange?.invoke(false)
        }
    }

    // Consume hardware key presses forwarded from MainActivity
    val pendingInput by (pendingKeyInput ?: remember { MutableStateFlow("") }).collectAsState()
    LaunchedEffect(pendingInput) {
        if (pendingInput.isNotEmpty()) {
            if (!expanded) {
                manualExpanded = true
                onSearchActiveChange?.invoke(true)
            }
            vm.onQueryChange(query + pendingInput)
            onKeyInputConsumed()
        }
    }

    LaunchedEffect(backspaceEvent) {
        backspaceEvent?.collect {
            if (query.isNotEmpty()) vm.onQueryChange(query.dropLast(1))
        }
    }

    // Snapshot of the last non-empty results so that onSearch can launch the top result
    // even after onActiveChange fires first and clears the query (which empties `results`).
    var savedResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }

    // Track whether the bar has ever been expanded so the initial-composition false state
    // doesn't trigger the collapse side-effect.
    var wasExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(manualExpanded) {
        if (manualExpanded) {
            savedResults = emptyList()
            wasExpanded = true
        } else if (wasExpanded) {
            // Bar is collapsing: wait 300ms so DockedSearchBar's internal collapse
            // animation has completed before external layout changes fire. This
            // prevents the recomposition stutter from concurrent internal + external state
            // updates in the same pass.
            delay(300)
            onSearchActiveChange?.invoke(false)
        }
    }
    LaunchedEffect(results) {
        if (results.isNotEmpty()) {
            Log.d("DeckSearch", "results updated (${results.size} items), saving")
            savedResults = results
        }
    }
    LaunchedEffect(query) {
        if (query.isNotBlank()) {
            Log.d("DeckSearch", "query changed to '$query', clearing savedResults")
            savedResults = emptyList()
        }
    }

    var wordIndex by remember { mutableIntStateOf((PLACEHOLDER_WORDS.indices).random()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                wordIndex = (wordIndex + 1) % PLACEHOLDER_WORDS.size
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    DockedSearchBar(
        query            = query,
        onQueryChange    = vm::onQueryChange,
        onSearch         = { _ ->
            Log.d("DeckSearch", "onSearch: query='$query', savedResults.size=${savedResults.size}, top=${savedResults.firstOrNull()?.javaClass?.simpleName}")
            val top = savedResults.firstOrNull() ?: return@DockedSearchBar
            when (top) {
                is SearchResult.AppResult -> {
                    context.packageManager.getLaunchIntentForPackage(top.app.packageName)
                        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ?.let { context.startActivity(it) }
                    vm.clearQuery()
                }
                is SearchResult.ContactResult -> {
                    top.phoneNumber?.let { num ->
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("tel:$num"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                    vm.clearQuery()
                }
                else -> {
                    // Calculator, widget, plugin, AI — just dismiss
                    vm.clearQuery()
                }
            }
        },
        active           = expanded,
        onActiveChange = { active ->
            if (!active) {
                vm.clearQuery()
                onSearchActiveChange?.invoke(false)
            }
            manualExpanded = active
            // When expanding, notify the parent immediately.
            // When collapsing, the LaunchedEffect(manualExpanded) handles the notification
            // after a one-frame delay to avoid stutter from concurrent state updates.
            if (active) onSearchActiveChange?.invoke(true)
        },
        modifier         = modifier,
        trailingIcon     = { Icon(Icons.Default.Search, contentDescription = null) },
        placeholder      = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Find your… ", style = MaterialTheme.typography.bodyLarge)
                AnimatedContent(
                    targetState  = PLACEHOLDER_WORDS[wordIndex],
                    transitionSpec = {
                        fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
                    },
                    label        = "placeholder_word",
                ) { word ->
                    Text(
                        text       = word,
                        fontStyle  = FontStyle.Italic,
                        fontWeight = FontWeight.SemiBold,
                        style      = MaterialTheme.typography.bodyLarge,
                        modifier   = Modifier.paddingFromBaseline(bottom = 12.dp)
                    )
                }
            }
        },
    ) {
        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        // 56dp DockedSearchBar + 24dp vertical padding (2×12dp from HomeScreen);
        // 16dp top gap matches the drawer's top padding so they visually align.
        val maxListHeight = screenHeight - statusBarTop - navBarBottom - 80.dp - 16.dp
        LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier       = Modifier.heightIn(max = maxListHeight)
        ) {
            items(results, key = { resultKey(it) }) { result ->
                SearchResultRow(result = result, onDismiss = vm::clearQuery, retryKey = manualExpanded)
            }
            if (results.isEmpty() && query.isNotBlank()) {
                item {
                    val ctx = LocalContext.current
                    ListItem(
                        headlineContent   = { Text("Search the web for \"$query\"") },
                        leadingContent    = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier          = Modifier.clickable {
                            val uri = android.net.Uri.parse("https://www.google.com/search?q=${android.net.Uri.encode(query)}")
                            ctx.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                            vm.clearQuery()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(result: SearchResult, onDismiss: () -> Unit, retryKey: Any = Unit) {
    when (result) {
        is SearchResult.AppResult          -> AppResultCard(result, onDismiss)
        is SearchResult.ContactResult      -> ContactResultCard(result, onDismiss)
        is SearchResult.CalculatorResult   -> CalculatorResultCard(result)
        is SearchResult.PluginResult       -> PluginResultCard(result, onDismiss)
        is SearchResult.AiResult           -> AiResultCard(result)
        is SearchResult.WidgetPickerResult -> WidgetPickerCard(result, retryKey = retryKey)
    }
}

@Composable
private fun AppResultCard(result: SearchResult.AppResult, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val painter = rememberDrawablePainter(result.app.icon)
    ListItem(
        headlineContent   = { Text(result.app.label, fontWeight = FontWeight.SemiBold) },
        supportingContent = if (result.app.category.isNotEmpty()) {
            { Text(result.app.category, style = MaterialTheme.typography.labelSmall) }
        } else null,
        leadingContent    = {
            Image(
                painter            = painter,
                contentDescription = null,
                modifier           = Modifier.size(40.dp)
            )
        },
        modifier = Modifier.clickable {
            context.packageManager.getLaunchIntentForPackage(result.app.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ?.let { context.startActivity(it) }
            onDismiss()
        }
    )
}

@Composable
private fun ContactResultCard(result: SearchResult.ContactResult, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ListItem(
        headlineContent   = { Text(result.name, fontWeight = FontWeight.SemiBold) },
        supportingContent = result.phoneNumber?.let { num -> { Text(num) } },
        leadingContent    = { InitialsAvatar(result.name) },
        modifier          = Modifier.clickable {
            result.phoneNumber?.let { num ->
                context.startActivity(
                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            onDismiss()
        }
    )
}

@Composable
private fun CalculatorResultCard(result: SearchResult.CalculatorResult) {
    ListItem(
        headlineContent   = {
            Text(
                text       = result.result,
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        },
        supportingContent = { Text(result.expression) },
        leadingContent    = { Icon(Icons.Default.Calculate, contentDescription = null) }
    )
}

@Composable
private fun PluginResultCard(result: SearchResult.PluginResult, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val iconBitmap by produceState<ImageBitmap?>(null, result.iconUri) {
        value = result.iconUri?.let { uriStr ->
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(uriStr))?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        }
    }
    ListItem(
        headlineContent   = { Text(result.title, fontWeight = FontWeight.SemiBold) },
        supportingContent = result.subtitle?.let { sub -> { Text(sub) } },
        leadingContent    = {
            if (iconBitmap != null) {
                Image(
                    bitmap             = iconBitmap!!,
                    contentDescription = null,
                    modifier           = Modifier.size(40.dp)
                )
            } else {
                Icon(
                    Icons.Default.Extension,
                    contentDescription = null,
                    modifier           = Modifier.size(40.dp)
                )
            }
        },
        modifier = Modifier.clickable {
            result.actionUri?.let { uri ->
                runCatching {
                    context.startActivity(
                        Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            onDismiss()
        }
    )
}

@Composable
private fun InitialsAvatar(name: String, modifier: Modifier = Modifier) {
    val initials = name.trim().split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
    Box(
        modifier           = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondary),
        contentAlignment   = Alignment.Center
    ) {
        Text(
            text       = initials,
            color      = MaterialTheme.colorScheme.onSecondary,
            style      = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AiResultCard(result: SearchResult.AiResult) {
    ListItem(
        headlineContent   = { Text(result.answer) },
        supportingContent = { Text("Gemini Nano", style = MaterialTheme.typography.labelSmall) },
        leadingContent    = {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.tertiary
            )
        }
    )
}

@Composable
private fun WidgetPickerCard(result: SearchResult.WidgetPickerResult, retryKey: Any = Unit) {
    val provider = result.providers.firstOrNull() ?: return
    val context = LocalContext.current
    // Use a stable host ID so the same host instance survives recompositions within
    // this search session. ID 1027 is arbitrary and outside the system reserved range.
    val widgetHost = remember {
        AppWidgetHost(context.applicationContext, 1027)
    }
    DisposableEffect(Unit) {
        widgetHost.startListening()
        onDispose { widgetHost.stopListening() }
    }
    LiveWidgetCard(
        provider    = provider,
        widgetHost  = widgetHost,
        retryKey    = retryKey,
        modifier    = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

@Composable
private fun LiveWidgetCard(
    provider: WidgetProviderInfo,
    widgetHost: AppWidgetHost,
    retryKey: Any = Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appWidgetManager = remember { AppWidgetManager.getInstance(context.applicationContext) }

    val providerInfo by produceState<AppWidgetProviderInfo?>(null, provider.componentName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val component = ComponentName.unflattenFromString(provider.componentName)!!
                appWidgetManager.getInstalledProviders().find { it.provider == component }
            }.getOrNull()
        }
    }

    // Allocation is deferred to a coroutine so that any exception thrown by
    // allocateAppWidgetId() is caught rather than crashing the composition.
    var appWidgetId by remember(provider.componentName) { mutableStateOf(widgetIdCache[provider.componentName] ?: -1) }
    var bound by remember(provider.componentName) { mutableStateOf(false) }
    var showFallback by remember(provider.componentName) { mutableStateOf(false) }
    var resumeCount by remember(provider.componentName) { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeCount++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(provider.componentName, retryKey, resumeCount) {
        try {
            val isDefaultLauncher = run {
                val homeIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                    addCategory(android.content.Intent.CATEGORY_HOME)
                }
                val resolved = context.packageManager.resolveActivity(homeIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                resolved?.activityInfo?.packageName == context.packageName
            }
            Log.d("DeckWidget", "isDefaultLauncher=$isDefaultLauncher, resumeCount=$resumeCount, appWidgetId=$appWidgetId")

            val component = ComponentName.unflattenFromString(provider.componentName)
                ?: run { showFallback = true; return@LaunchedEffect }

            val id = if (appWidgetId != -1) appWidgetId else {
                val newId = widgetHost.allocateAppWidgetId()
                widgetIdCache[provider.componentName] = newId
                appWidgetId = newId
                newId
            }

            val bindResult = appWidgetManager.bindAppWidgetIdIfAllowed(id, component)
            Log.d("DeckWidget", "bindAppWidgetIdIfAllowed=$bindResult, id=$id, component=$component")
            if (bindResult) {
                bound = true
                context.sendBroadcast(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                        setComponent(component)
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(id))
                    }
                )
                return@LaunchedEffect
            }

            if (resumeCount == 0) {
                Log.d("DeckWidget", "Launching BIND intent, resumeCount=$resumeCount, isDefaultLauncher=$isDefaultLauncher")
                context.startActivity(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, component)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
        } catch (e: Exception) {
            Log.e("DeckWidget", "Exception in widget binding", e)
            showFallback = true
        }
    }

    if (!showFallback && bound && appWidgetId != -1) {
        val info = providerInfo
        AndroidView(
            factory  = { ctx ->
                runCatching {
                    if (info != null) widgetHost.createView(ctx, appWidgetId, info)
                    else android.view.View(ctx)
                }.getOrElse { android.view.View(ctx) }
            },
            modifier = modifier.clip(RoundedCornerShape(12.dp))
        )
    } else {
        StaticWidgetPreview(provider, modifier)
    }
}

@Composable
private fun StaticWidgetPreview(provider: WidgetProviderInfo, modifier: Modifier = Modifier) {
    Log.d("DeckWidget", "showing StaticWidgetPreview for ${provider.componentName}")
    val context = LocalContext.current
    val previewBitmap by produceState<ImageBitmap?>(null, provider.componentName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val res = context.packageManager.getResourcesForApplication(provider.packageName)
                val resId = if (provider.previewResId != 0) provider.previewResId else provider.iconResId
                if (resId != 0) {
                    val drawable = res.getDrawable(resId, null)
                    val w = drawable.intrinsicWidth.coerceIn(1, 800)
                    val h = drawable.intrinsicHeight.coerceIn(1, 800)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    drawable.setBounds(0, 0, w, h)
                    drawable.draw(android.graphics.Canvas(bmp))
                    bmp.asImageBitmap()
                } else null
            }.getOrNull()
        }
    }

    Box(
        modifier         = modifier.background(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            RoundedCornerShape(12.dp)
        ),
        contentAlignment = Alignment.Center
    ) {
        if (previewBitmap != null) {
            Image(
                bitmap             = previewBitmap!!,
                contentDescription = provider.label,
                contentScale       = ContentScale.Fit,
                modifier           = Modifier.fillMaxSize().padding(8.dp)
            )
        } else {
            Icon(
                imageVector        = Icons.Default.Widgets,
                contentDescription = null,
                modifier           = Modifier.size(40.dp),
                tint               = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun rememberDrawablePainter(drawable: Drawable): BitmapPainter {
    val imageBitmap = remember(drawable) {
        val bmp = Bitmap.createBitmap(
            drawable.intrinsicWidth.coerceAtLeast(1),
            drawable.intrinsicHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bmp.asImageBitmap()
    }
    return BitmapPainter(imageBitmap)
}

private fun resultKey(result: SearchResult): Any = when (result) {
    is SearchResult.AppResult          -> "app:${result.app.packageName}"
    is SearchResult.ContactResult      -> "contact:${result.name}"
    is SearchResult.CalculatorResult   -> "calc:${result.expression}"
    is SearchResult.PluginResult       -> "plugin:${result.pluginId}:${result.title}"
    is SearchResult.AiResult           -> "ai:${result.query}"
    is SearchResult.WidgetPickerResult -> "widgets:${result.appPackage}"
}
