package com.hermes.deck.ui.search

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.hermes.deck.data.IconShape
import com.hermes.deck.data.TagRepository
import com.hermes.deck.data.WidgetPinRepository
import kotlin.math.roundToInt
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Window
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.InputChip
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hermes.deck.ui.common.rememberAppIconBitmap
import com.hermes.deck.ui.drawer.DrawerViewModel
import com.hermes.deck.ui.drawer.WidgetPickerDialog
import com.hermes.deck.ui.home.AppContextMenu
import com.hermes.deck.ui.home.AppContextMenuItem
import com.hermes.deck.ui.home.IconPickerDialog
import com.hermes.deck.ui.search.providers.AnthropicClient
import com.hermes.deck.ui.search.providers.ChatMessage
import com.hermes.deck.ui.search.providers.ChatSession
import com.hermes.deck.ui.search.providers.HomeAssistantClient
import com.hermes.deck.ui.search.providers.PlexClient
import com.hermes.deck.ui.search.providers.PlexItem
import com.hermes.deck.ui.search.providers.TandoorClient
import com.hermes.deck.ui.search.providers.SymfoniumClient
import com.hermes.deck.ui.search.providers.TransistorClient
import com.hermes.deck.ui.search.providers.PlacesClient
import com.hermes.deck.ui.search.providers.WikipediaClient
import com.hermes.deck.ui.search.providers.GmailClient
import com.hermes.deck.ui.search.providers.YouTubeClient
import com.hermes.deck.ui.search.providers.WeatherClient
import com.hermes.deck.ui.search.providers.LocalLlmClassifier
import com.hermes.deck.ui.search.providers.ArrClient
import com.hermes.deck.ui.search.providers.TodoistClient
import androidx.compose.material3.Checkbox
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Shape
import kotlin.math.roundToInt
import com.hermes.deck.ui.home.rememberAppShortcuts
import com.hermes.deck.ui.home.shortcutDrawableToBitmap
import androidx.compose.material.icons.filled.Restaurant
import com.hermes.deck.ui.search.providers.ClaudePendingAction
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.CoroutineScope
import com.hermes.deck.ui.search.providers.ClaudeChatState
import com.hermes.deck.ui.search.providers.ClaudeChatStore
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.History
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import java.time.OffsetDateTime
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing

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
            // Enter activates the top result (same as tapping it). Claude starts a chat.
            val top = savedResults.firstOrNull()
            when {
                top is SearchResult.ClaudeResult                 -> vm.startClaude(top.query)
                top is SearchResult.HermesResult                 -> vm.startHermes(top.query)
                top != null && activateSearchResult(context, top) -> vm.clearQuery()
                else                                              -> { /* nothing to open */ }
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
        val activeChat by vm.activeChat.collectAsState()
        val claudeThinking by vm.claudeThinking.collectAsState()
        val plexSplit = LocalContext.current.getSharedPreferences("deck_prefs", android.content.Context.MODE_PRIVATE)
            .getBoolean("plex_split_libraries", false)
        val grouped = remember(results, plexSplit) { groupResults(results, plexSplit) }
        val chat = activeChat
        if (chat != null) {
            ClaudeConversation(
                state            = chat,
                onSend           = { vm.replyClaude(it) },
                thinking         = claudeThinking,
                onToggleThinking = { vm.toggleClaudeThinking() },
                onConfirmAction  = { vm.confirmClaudeAction(it) },
                onCancelAction   = { vm.cancelClaudeAction(it) },
                modifier         = Modifier.height(maxListHeight)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier       = Modifier.heightIn(max = maxListHeight)
            ) {
                grouped.forEach { (label, groupItems) ->
                    if (label == "Widgets") {
                        groupItems.forEach { result ->
                            val comp = (result as? SearchResult.WidgetPickerResult)?.providers?.firstOrNull()?.componentName
                                ?: (result as? SearchResult.WidgetPickerResult)?.appPackage
                                ?: return@forEach
                            item(key = "widget:$comp") {
                                SearchResultRow(result = result, onDismiss = vm::clearQuery, retryKey = manualExpanded)
                            }
                        }
                    } else {
                        item(key = "group:$label") {
                            ResultGroup(
                                label          = label,
                                results        = groupItems,
                                onDismiss      = vm::clearQuery,
                                retryKey       = manualExpanded,
                                onManage       = null,
                                onClaudeStart  = { vm.startClaude(it) },
                                onClaudeResume = { vm.resumeClaude(it) },
                                onHermesStart  = { vm.startHermes(it) },
                            )
                        }
                    }
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
}

/**
 * Performs the default "open" action for a result — the same thing tapping its card does.
 * Returns true if it launched something. Used by the search box's Enter key to activate the
 * top result. Calculator/Widget/Ai/Claude have no generic launch (handled by the caller).
 */
internal fun activateSearchResult(context: android.content.Context, result: SearchResult): Boolean = when (result) {
    is SearchResult.AppResult -> {
        val intent = context.packageManager.getLaunchIntentForPackage(result.app.packageName)
        if (intent != null) { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } else false
    }
    is SearchResult.ContactResult -> {
        val num = result.phoneNumber
        if (num != null) {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
        } else false
    }
    is SearchResult.DialerResult ->
        runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${result.phoneNumber}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    is SearchResult.PluginResult -> {
        val uri = result.actionUri
        if (uri != null) runCatching { context.startActivity(Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess else false
    }
    is SearchResult.BrowserHistoryResult ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    is SearchResult.BrowserSuggestionResult ->
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com/?q=${Uri.encode(result.suggestion)}"))
                    .setPackage("com.hermes.browser").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
    is SearchResult.SettingsResult ->
        runCatching {
            context.startActivity(
                Intent(context, com.hermes.deck.ui.settings.SettingsActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); putExtra("section", result.section)
                }
            )
        }.isSuccess
    is SearchResult.SystemSettingsResult ->
        runCatching { context.startActivity(Intent(result.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    is SearchResult.FileResult ->
        runCatching {
            val file = java.io.File(result.path)
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, result.mimeType ?: "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }.isSuccess
    is SearchResult.PlexResult -> { openPlexItem(context, result.ratingKey, result.type); true }
    is SearchResult.TandoorResult -> { openTandoorItem(context, result.id); true }
    is SearchResult.SymfoniumResult -> { SymfoniumClient.play(context, result.mediaId); true }
    is SearchResult.TransistorResult -> { TransistorClient.play(context, result.mediaId); true }
    is SearchResult.PlacesResult -> { openMapsSearch(context, result.query); true }
    is SearchResult.WikipediaResult -> { openUrl(context, result.url); true }
    is SearchResult.AnswerResult -> { copyToClipboard(context, result.copyText); true }
    is SearchResult.GmailResult -> { openGmailApp(context); true }
    is SearchResult.YouTubeResult -> { openYouTube(context, result.videoId); true }
    is SearchResult.TimerResult -> { fireTimer(context, result); true }
    else -> false
}

@Composable
internal fun SearchResultRow(result: SearchResult, onDismiss: () -> Unit, retryKey: Any = Unit, resolvedIcon: android.graphics.drawable.Drawable? = null, iconShape: IconShape = IconShape.NONE, onResultSelected: ((SearchResult) -> Unit)? = null, onClaudeStart: ((String) -> Unit)? = null, onClaudeResume: ((ChatSession) -> Unit)? = null, onHermesStart: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    var showConfig by remember { mutableStateOf(false) }
    val providerId = providerIdForResult(result)
    // For cards that don't already have their own long-press menu, long-press opens a "Search
    // Configuration" popup. App/Widget add the same item to their existing menus instead.
    val onConfigure: (() -> Unit)? = if (providerId != null) ({ showConfig = true }) else null
    Box {
        when (result) {
            is SearchResult.AppResult          -> AppResultCard(result, onDismiss, resolvedIcon, iconShape, onResultSelected)
            is SearchResult.ContactResult      -> ContactResultCard(result, onDismiss, onResultSelected, onConfigure)
            is SearchResult.CalculatorResult   -> CalculatorResultCard(result, onConfigure)
            is SearchResult.PluginResult       -> PluginResultCard(result, onDismiss, onResultSelected)
            is SearchResult.AiResult           -> AiResultCard(result, onConfigure)
            is SearchResult.ClaudeResult       -> ClaudeResultCard(result, onClaudeStart, onClaudeResume, onConfigure)
            is SearchResult.HermesResult       -> HermesResultCard(result, onHermesStart, onClaudeResume, onConfigure)
            is SearchResult.DialerResult       -> DialerResultCard(result, onDismiss, onResultSelected, onConfigure)
            is SearchResult.WidgetPickerResult -> WidgetPickerCard(result, retryKey)
            is SearchResult.SettingsResult       -> SettingsResultCard(result, onConfigure)
            is SearchResult.SystemSettingsResult -> SystemSettingsResultCard(result, onConfigure)
            is SearchResult.BrowserHistoryResult  -> BrowserHistoryResultCard(result, onDismiss, onResultSelected, onConfigure)
            is SearchResult.FileResult             -> FileResultCard(result, onDismiss, onConfigure)
            is SearchResult.BrowserSuggestionResult -> BrowserSuggestionResultCard(result, onDismiss, onConfigure)
            is SearchResult.HomeAssistantResult     -> HomeAssistantCard(result, onConfigure)
            is SearchResult.PlexResult              -> PlexResultCard(result, onDismiss, onConfigure)
            is SearchResult.PersonResult            -> PersonResultCard(result, onDismiss, onConfigure)
            is SearchResult.TandoorResult           -> TandoorResultCard(result, onDismiss, onConfigure)
            is SearchResult.SymfoniumResult         -> SymfoniumResultCard(result, onDismiss, onConfigure)
            is SearchResult.TransistorResult        -> TransistorResultCard(result, onDismiss, onConfigure)
            is SearchResult.PlacesResult            -> PlacesResultCard(result, onConfigure)
            is SearchResult.WikipediaResult         -> WikipediaResultCard(result, onConfigure)
            is SearchResult.AnswerResult            -> AnswerResultCard(result, onConfigure)
            is SearchResult.GmailResult             -> GmailResultCard(result, onConfigure)
            is SearchResult.YouTubeResult           -> YouTubeResultCard(result, onDismiss, onConfigure)
            is SearchResult.TimerResult             -> TimerResultCard(result, onDismiss, onConfigure)
            is SearchResult.WeatherResult           -> WeatherResultCard(result, onConfigure)
            is SearchResult.DictionaryResult        -> DictionaryResultCard(result, onConfigure)
            is SearchResult.OfflineAnswerResult     -> OfflineAnswerCard(result, onConfigure)
            is SearchResult.AddMediaResult          -> AddMediaCard(result, onConfigure)
            is SearchResult.TodoTaskResult          -> TodoTaskCard(result, onConfigure)
            is SearchResult.TodoAddResult           -> TodoAddCard(result, onConfigure)
        }
        if (providerId != null) {
            AppContextMenu(
                expanded         = showConfig,
                onDismissRequest = { showConfig = false },
                hasShortcuts     = false,
                shortcuts        = {},
                actions          = {
                    AppContextMenuItem("Search Configuration", Icons.Default.Settings) {
                        showConfig = false
                        openSearchProviderSettings(context, providerId)
                    }
                }
            )
        }
    }
}

/**
 * Home Assistant entity card — dispatches by domain to the right controls. Every card keys its
 * optimistic state on entityId (so a stale-cache refresh can't reset it) and, on a successful
 * service call, patches the client cache via [haCall] so the two never diverge.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeAssistantCard(result: SearchResult.HomeAssistantResult, onConfigure: (() -> Unit)? = null) {
    // Long-press anywhere on the card body opens "Search Configuration". The interactive
    // controls (Switch / Slider) are deeper nodes and hit-test first, so toggling/dragging
    // them still works; only a long-press on the non-control area reaches this wrapper.
    Box(
        modifier = if (onConfigure != null) Modifier.combinedClickable(onLongClick = onConfigure) {}
                   else Modifier
    ) {
        when (result.domain) {
            "light"                   -> HaDimmableCard(result, Icons.Default.Lightbulb)
            "fan"                     -> HaDimmableCard(result, Icons.Default.Air)
            "switch", "input_boolean" -> HaToggleCard(result, Icons.Default.ToggleOn)
            "lock"                    -> HaLockCard(result)
            "cover"                   -> HaCoverCard(result)
            "climate"                 -> HaClimateCard(result)
            "media_player"            -> HaMediaCard(result)
            "input_number", "number"  -> HaNumberCard(result)
            "input_select", "select"  -> HaSelectCard(result)
            "vacuum"                  -> HaVacuumCard(result)
            "humidifier"              -> HaHumidifierCard(result)
            "alarm_control_panel"     -> HaAlarmCard(result)
            "sensor", "binary_sensor" -> HaSensorCard(result)
            "scene", "script", "button", "input_button" -> HaActivateCard(result)
            else                      -> HaControlRow(
                Icons.Default.Lightbulb, result.friendlyName,
                result.state.replaceFirstChar { it.uppercase() }, active = false
            ) {}
        }
    }
}

private fun parseStringList(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())
}

private fun fmtTemp(t: Float): String = if (t % 1f == 0f) t.toInt().toString() else "%.1f".format(t)

/** Parse a stringified JSON number array (e.g. rgb_color "[255, 100, 50]", hs_color "[210, 80]"). */
private fun parseFloatList(json: String?): List<Float> {
    if (json.isNullOrBlank() || json == "null") return emptyList()
    return runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.optDouble(it).toFloat() }
    }.getOrDefault(emptyList())
}

/** Seconds → "m:ss" (or "h:mm:ss" past an hour) for media progress. */
private fun fmtTime(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** Rough warm↔cool tint for a colour-temperature value, just for the bulb icon. */
private fun kelvinToColor(kelvin: Int, minK: Int, maxK: Int): Color {
    val warm = Color(1f, 0.78f, 0.55f)
    val cool = Color(0.79f, 0.89f, 1f)
    val frac = ((kelvin - minK).toFloat() / (maxK - minK).coerceAtLeast(1)).coerceIn(0f, 1f)
    return Color(
        red   = warm.red   + (cool.red   - warm.red)   * frac,
        green = warm.green + (cool.green - warm.green) * frac,
        blue  = warm.blue  + (cool.blue  - warm.blue)  * frac
    )
}

/** Fire a service call off the UI; on success patch the cache (null attrs keep their cached value). */
private fun haCall(
    scope: CoroutineScope,
    context: android.content.Context,
    entityId: String,
    domain: String,
    service: String,
    extras: JSONObject? = null,
    patchState: String? = null,
    brightness: Int? = null,
    percentage: Int? = null,
    position: Int? = null,
    onFailure: () -> Unit = {}
) {
    scope.launch {
        val r = HomeAssistantClient.callService(context, domain, service, entityId, extras)
        if (r.isSuccess) {
            if (patchState != null) HomeAssistantClient.patchCache(entityId, patchState, brightness, percentage, position)
        } else onFailure()
    }
}

/** Shared name + state + trailing-control row used by every HA card. */
@Composable
private fun HaControlRow(
    icon: ImageVector,
    name: String,
    stateLabel: String,
    active: Boolean,
    iconTint: Color? = null,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(start = 8.dp, top = 6.dp, end = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = iconTint
                                 ?: if (active) MaterialTheme.colorScheme.primary
                                    else        MaterialTheme.colorScheme.onSurfaceVariant,
            modifier           = Modifier.padding(start = 8.dp, end = 16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(name, maxLines = 1, style = MaterialTheme.typography.bodyLarge)
            Text(stateLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

/** switch / input_boolean — plain on/off toggle. */
@Composable
private fun HaToggleCard(result: SearchResult.HomeAssistantResult, icon: ImageVector) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val unavailable = result.state == "unavailable"
    var on by remember(result.entityId) { mutableStateOf(result.state == "on") }
    HaControlRow(icon, result.friendlyName, if (unavailable) "Unavailable" else if (on) "On" else "Off", on) {
        Switch(checked = on, enabled = !unavailable, onCheckedChange = { v ->
            on = v
            haCall(scope, context, result.entityId, result.domain, if (v) "turn_on" else "turn_off",
                patchState = if (v) "on" else "off", onFailure = { on = !v })
        })
    }
}

/** lock — lock/unlock toggle. */
@Composable
private fun HaLockCard(result: SearchResult.HomeAssistantResult) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val unavailable = result.state == "unavailable"
    var locked by remember(result.entityId) { mutableStateOf(result.state == "locked") }
    HaControlRow(Icons.Default.Lock, result.friendlyName,
        if (unavailable) "Unavailable" else if (locked) "Locked" else "Unlocked", locked) {
        Switch(checked = locked, enabled = !unavailable, onCheckedChange = { v ->
            locked = v
            haCall(scope, context, result.entityId, "lock", if (v) "lock" else "unlock",
                patchState = if (v) "locked" else "unlocked", onFailure = { locked = !v })
        })
    }
}

/** light / fan — on/off toggle plus a level slider (brightness % or fan speed %). Lights that
 *  support colour also get a hue bar; lights that support colour temperature get a warm↔cool bar.
 *  The picked colour is held locally (patchCache can't carry colour through a cache refresh). */
@Composable
private fun HaDimmableCard(result: SearchResult.HomeAssistantResult, icon: ImageVector) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isLight = result.domain == "light"
    val unavailable = result.state == "unavailable"
    val attrs = result.attributes
    var on by remember(result.entityId) { mutableStateOf(result.state == "on") }
    var pct by remember(result.entityId) {
        mutableStateOf(
            if (isLight) ((result.brightness ?: 255) * 100f / 255f).coerceIn(1f, 100f)
            else         (result.percentage ?: 100).toFloat().coerceIn(1f, 100f)
        )
    }

    // --- Colour capabilities (lights only) ---
    val colorModes = remember(result.entityId) {
        parseStringList(attrs["supported_color_modes"]).map { it.lowercase() }
    }
    val supportsColor = isLight && colorModes.any { it in setOf("hs", "rgb", "rgbw", "rgbww", "xy") }
    val supportsCt    = isLight && colorModes.any { it == "color_temp" }
    val ctMinK = attrs["min_color_temp_kelvin"]?.toIntOrNull() ?: 2000
    val ctMaxK = (attrs["max_color_temp_kelvin"]?.toIntOrNull() ?: 6500).coerceAtLeast(ctMinK + 1)
    // Local colour state so a 10s cache refresh can't snap the swatch back.
    var hue by remember(result.entityId) {
        mutableStateOf(parseFloatList(attrs["hs_color"]).firstOrNull() ?: 0f)
    }
    var kelvin by remember(result.entityId) {
        mutableStateOf(attrs["color_temp_kelvin"]?.toIntOrNull() ?: ((ctMinK + ctMaxK) / 2))
    }
    var swatch by remember(result.entityId) {
        mutableStateOf(
            parseFloatList(attrs["rgb_color"]).takeIf { it.size == 3 }
                ?.let { Color(it[0] / 255f, it[1] / 255f, it[2] / 255f) }
        )
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        HaControlRow(icon, result.friendlyName,
            if (unavailable) "Unavailable" else if (on) "On · ${pct.toInt()}%" else "Off", on,
            iconTint = if (on && !unavailable) swatch else null) {
            Switch(checked = on, enabled = !unavailable, onCheckedChange = { v ->
                on = v
                haCall(scope, context, result.entityId, result.domain, if (v) "turn_on" else "turn_off",
                    patchState = if (v) "on" else "off", onFailure = { on = !v })
            })
        }
        if (!unavailable) {
            // Brightness/speed shows even when off — dragging it turns the light on to that level.
            Slider(
                value                 = pct,
                onValueChange         = { pct = it },
                onValueChangeFinished = {
                    on = true
                    val p = pct.toInt().coerceIn(1, 100)
                    if (isLight) haCall(scope, context, result.entityId, "light", "turn_on",
                        JSONObject().put("brightness_pct", p), patchState = "on", brightness = p * 255 / 100)
                    else haCall(scope, context, result.entityId, "fan", "set_percentage",
                        JSONObject().put("percentage", p), patchState = "on", percentage = p)
                },
                valueRange = 1f..100f,
                modifier   = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            )
            if (on && supportsColor) {
                Spacer(Modifier.height(12.dp))
                HueBar(
                    hue         = hue,
                    onHueChange = { h -> hue = h; swatch = Color.hsv(h, 1f, 1f) },
                    onCommit    = {
                        haCall(scope, context, result.entityId, "light", "turn_on",
                            JSONObject().put("hs_color", JSONArray().put(hue.toDouble()).put(100.0)),
                            patchState = "on")
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                )
            }
            if (on && supportsCt) {
                Spacer(Modifier.height(12.dp))
                ColorTempBar(
                    kelvin = kelvin, minK = ctMinK, maxK = ctMaxK,
                    onKelvinChange = { k -> kelvin = k; swatch = kelvinToColor(k, ctMinK, ctMaxK) },
                    onCommit       = {
                        haCall(scope, context, result.entityId, "light", "turn_on",
                            JSONObject().put("color_temp_kelvin", kelvin), patchState = "on")
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                )
            }
        }
    }
}

/** Rainbow hue picker, styled as a Material slider (same as the brightness slider) with a gradient
 *  track. [onHueChange] fires live; [onCommit] on release. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HueBar(hue: Float, onHueChange: (Float) -> Unit, onCommit: () -> Unit, modifier: Modifier = Modifier) {
    val spectrum = remember { (0..360 step 60).map { Color.hsv(it.toFloat(), 1f, 1f) } }
    Slider(
        value                 = hue,
        onValueChange         = onHueChange,
        onValueChangeFinished = onCommit,
        valueRange            = 0f..360f,
        modifier              = modifier,
        colors                = SliderDefaults.colors(thumbColor = Color.hsv(hue.coerceIn(0f, 360f), 1f, 1f)),
        track                 = { _ -> GradientTrack(frac = hue.coerceIn(0f, 360f) / 360f, colors = spectrum) }
    )
}

/** Warm↔cool colour-temperature picker, styled as a Material slider with a gradient track. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorTempBar(
    kelvin: Int, minK: Int, maxK: Int,
    onKelvinChange: (Int) -> Unit, onCommit: () -> Unit, modifier: Modifier = Modifier
) {
    val warmCool = remember { listOf(Color(1f, 0.78f, 0.55f), Color.White, Color(0.79f, 0.89f, 1f)) }
    val span = (maxK - minK).coerceAtLeast(1)
    Slider(
        value                 = kelvin.toFloat(),
        onValueChange         = { onKelvinChange(it.toInt()) },
        onValueChangeFinished = onCommit,
        valueRange            = minK.toFloat()..maxK.toFloat().coerceAtLeast(minK + 1f),
        modifier              = modifier,
        colors                = SliderDefaults.colors(thumbColor = kelvinToColor(kelvin, minK, maxK)),
        track                 = { _ -> GradientTrack(frac = (kelvin - minK).toFloat() / span, colors = warmCool) }
    )
}

/** A Material-slider track (16dp, matching the default) filled with a horizontal gradient, split by
 *  a gap around the thumb. Outer ends are fully rounded; the inner ends (at the gap) use the small
 *  2dp `TrackInsideCornerSize` so it matches the brightness slider's track exactly. */
@Composable
private fun GradientTrack(frac: Float, colors: List<Color>) {
    Canvas(Modifier.fillMaxWidth().height(16.dp)) {
        val w = size.width
        val h = size.height
        val outer = CornerRadius(h / 2f, h / 2f)              // fully-rounded outer ends
        val inner = CornerRadius(2.dp.toPx(), 2.dp.toPx())    // M3 TrackInsideCornerSize
        val brush = Brush.horizontalGradient(colors, startX = 0f, endX = w)
        val cut = 8.dp.toPx()                                 // thumb half-width (2dp) + 6dp gap each side
        val thumbX = frac.coerceIn(0f, 1f) * w

        val leftEnd = (thumbX - cut).coerceAtLeast(0f)
        if (leftEnd > 0f) {
            drawPath(Path().apply {
                addRoundRect(RoundRect(Rect(0f, 0f, leftEnd, h),
                    topLeft = outer, topRight = inner, bottomRight = inner, bottomLeft = outer))
            }, brush)
        }
        val rightStart = (thumbX + cut).coerceAtMost(w)
        if (rightStart < w) {
            drawPath(Path().apply {
                addRoundRect(RoundRect(Rect(rightStart, 0f, w, h),
                    topLeft = inner, topRight = outer, bottomRight = outer, bottomLeft = inner))
            }, brush)
        }
    }
}

/** cover — open / stop / close buttons plus a position slider. */
@Composable
private fun HaCoverCard(result: SearchResult.HomeAssistantResult) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val unavailable = result.state == "unavailable"
    var pos by remember(result.entityId) { mutableStateOf((result.position ?: 0).toFloat()) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        HaControlRow(Icons.Default.Window, result.friendlyName,
            if (unavailable) "Unavailable" else result.state.replaceFirstChar { it.uppercase() }, pos > 0f) {
            Row {
                IconButton(enabled = !unavailable, onClick = {
                    pos = 100f
                    haCall(scope, context, result.entityId, "cover", "open_cover", patchState = "open", position = 100)
                }) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Open") }
                IconButton(enabled = !unavailable, onClick = {
                    haCall(scope, context, result.entityId, "cover", "stop_cover")
                }) { Icon(Icons.Default.Stop, contentDescription = "Stop") }
                IconButton(enabled = !unavailable, onClick = {
                    pos = 0f
                    haCall(scope, context, result.entityId, "cover", "close_cover", patchState = "closed", position = 0)
                }) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close") }
            }
        }
        if (!unavailable) {
            Slider(
                value                 = pos,
                onValueChange         = { pos = it },
                onValueChangeFinished = {
                    val p = pos.toInt().coerceIn(0, 100)
                    haCall(scope, context, result.entityId, "cover", "set_cover_position",
                        JSONObject().put("position", p),
                        patchState = if (p > 0) "open" else "closed", position = p)
                },
                valueRange = 0f..100f,
                modifier   = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            )
        }
    }
}

/** scene / script / button — a single activate/run/press button (no persistent state to track). */
@Composable
private fun HaActivateCard(result: SearchResult.HomeAssistantResult) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isButton = result.domain == "button" || result.domain == "input_button"
    val isScript = result.domain == "script"
    val service = if (isButton) "press" else "turn_on"
    HaControlRow(
        Icons.Default.PlayArrow, result.friendlyName,
        when { isButton -> "Button"; isScript -> "Script"; else -> "Scene" }, active = false
    ) {
        FilledTonalButton(onClick = { haCall(scope, context, result.entityId, result.domain, service) }) {
            Text(when { isButton -> "Press"; isScript -> "Run"; else -> "Activate" })
        }
    }
}

/** climate — current/target temp with +/- stepper and a row of HVAC-mode chips. */
@Composable
private fun HaClimateCard(result: SearchResult.HomeAssistantResult) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val attrs = result.attributes
    val unavailable = result.state == "unavailable"
    val current = attrs["current_temperature"]?.toFloatOrNull()
    val currentHumidity = attrs["current_humidity"]?.toFloatOrNull()
    val minT = attrs["min_temp"]?.toFloatOrNull() ?: 7f
    val maxT = attrs["max_temp"]?.toFloatOrNull() ?: 35f
    val step = attrs["target_temp_step"]?.toFloatOrNull() ?: 0.5f
    val modes = remember(result.entityId) { parseStringList(attrs["hvac_modes"]) }
    var target by remember(result.entityId) { mutableStateOf(attrs["temperature"]?.toFloatOrNull() ?: 20f) }
    var mode by remember(result.entityId) { mutableStateOf(result.state) }

    fun pushTarget() {
        haCall(scope, context, result.entityId, "climate", "set_temperature",
            JSONObject().put("temperature", target))
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Thermostat, contentDescription = null,
                tint = if (mode != "off") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, end = 16.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(result.friendlyName, maxLines = 1, style = MaterialTheme.typography.bodyLarge)
                Text(
                    buildString {
                        append(mode.replaceFirstChar { it.uppercase() })
                        if (current != null) append(" · now ${fmtTemp(current)}°")
                        if (currentHumidity != null) append(" · ${currentHumidity.toInt()}% RH")
                    },
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(enabled = !unavailable, onClick = {
                target = (target - step).coerceIn(minT, maxT); pushTarget()
            }) { Icon(Icons.Default.Remove, contentDescription = "Lower") }
            Text("${fmtTemp(target)}°", style = MaterialTheme.typography.titleMedium)
            IconButton(enabled = !unavailable, onClick = {
                target = (target + step).coerceIn(minT, maxT); pushTarget()
            }) { Icon(Icons.Default.Add, contentDescription = "Raise") }
        }
        if (modes.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                modes.forEach { m ->
                    FilterChip(
                        selected = m == mode,
                        enabled  = !unavailable,
                        onClick  = {
                            mode = m
                            haCall(scope, context, result.entityId, "climate", "set_hvac_mode",
                                JSONObject().put("hvac_mode", m), patchState = m)
                        },
                        label = { Text(m.replace('_', ' ').replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
        }
    }
}

/** A Material-3-Expressive-style wavy/squiggly progress bar. (The native
 *  `LinearWavyProgressIndicator` is alpha-only in material3 1.5; this draws the same look on the
 *  stable line.) Sine wave over the elapsed portion, a flat track for the remainder; the wave
 *  scrolls while [animate] is true. */
@Composable
private fun WavyProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    wavelength: Dp = 40.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
) {
    val phase = if (animate) {
        rememberInfiniteTransition(label = "wavy").animateFloat(
            initialValue  = 0f,
            targetValue   = (2.0 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
            label         = "wavyPhase"
        ).value
    } else 0f

    // Material 3 linear wavy progress spec (thin): wavelength 40, amplitude 3, stroke 4,
    // 4dp gap between active indicator and track, 4dp stop dot at the end, total height 10.
    Canvas(modifier = modifier.height(10.dp)) {
        val p     = progress.coerceIn(0f, 1f)
        val w     = size.width
        val cy    = size.height / 2f
        val amp   = 3.dp.toPx()
        val sw    = 4.dp.toPx()
        val gap   = 4.dp.toPx()
        val stopR = 2.dp.toPx()                                  // 4dp stop indicator
        val k     = (2.0 * Math.PI).toFloat() / wavelength.toPx()
        val head  = (w * p).coerceIn(0f, w)

        // Remaining track — flat line after the wave, stopping short of the end dot. Offset by the
        // stroke width too so the round caps on both ends don't swallow the 4dp visible gap.
        val trackStart = (head + gap + sw).coerceAtMost(w)
        val trackEnd   = w - stopR * 2f
        if (trackStart < trackEnd) {
            drawLine(trackColor, Offset(trackStart, cy), Offset(trackEnd, cy),
                strokeWidth = sw, cap = StrokeCap.Round)
        }
        // Stop indicator dot at the far end.
        drawCircle(color, radius = stopR, center = Offset(w - stopR, cy))

        // Active wavy indicator.
        if (head > 0f) {
            val path = Path().apply {
                moveTo(0f, cy + amp * kotlin.math.sin(phase))
                var x = 0f
                while (x <= head) {
                    lineTo(x, cy + amp * kotlin.math.sin(k * x + phase))
                    x += 2f
                }
            }
            drawPath(path, color, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Seekable wavy bar — the [WavyProgressIndicator] look plus a draggable pill handle at the
 *  current position. Tap or drag to scrub: [onValueChange] fires live with a 0..1 fraction,
 *  [onValueChangeFinished] on release. */
@Composable
private fun WavySeekBar(
    progress: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    wavelength: Dp = 40.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
) {
    val phase = if (animate) {
        rememberInfiniteTransition(label = "seekwavy").animateFloat(
            initialValue  = 0f,
            targetValue   = (2.0 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
            label         = "seekPhase"
        ).value
    } else 0f

    Canvas(
        modifier = modifier
            .height(24.dp)
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    if (size.width > 0) { onValueChange((o.x / size.width).coerceIn(0f, 1f)); onValueChangeFinished() }
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart  = { o -> if (size.width > 0) onValueChange((o.x / size.width).coerceIn(0f, 1f)) },
                    onDragEnd    = { onValueChangeFinished() },
                    onDragCancel = { onValueChangeFinished() }
                ) { change, _ -> if (size.width > 0) onValueChange((change.position.x / size.width).coerceIn(0f, 1f)) }
            }
    ) {
        val w    = size.width
        val cy   = size.height / 2f
        val amp  = 3.dp.toPx()
        val sw   = 4.dp.toPx()
        val k    = (2.0 * Math.PI).toFloat() / wavelength.toPx()
        val head = (w * progress.coerceIn(0f, 1f))
        val hw   = 4.dp.toPx()    // handle width
        val hh   = 16.dp.toPx()   // handle height
        val hr   = 2.dp.toPx()

        val waveEnd    = (head - hw / 2f).coerceAtLeast(0f)
        val trackStart = head + hw / 2f
        if (trackStart < w) {
            drawLine(trackColor, Offset(trackStart, cy), Offset(w, cy), strokeWidth = sw, cap = StrokeCap.Round)
        }
        if (waveEnd > 0f) {
            val path = Path().apply {
                moveTo(0f, cy + amp * kotlin.math.sin(phase))
                var x = 0f
                while (x <= waveEnd) { lineTo(x, cy + amp * kotlin.math.sin(k * x + phase)); x += 2f }
            }
            drawPath(path, color, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        // Handle — vertical pill at the current position.
        drawRoundRect(
            color        = color,
            topLeft      = Offset(head - hw / 2f, cy - hh / 2f),
            size         = Size(hw, hh),
            cornerRadius = CornerRadius(hr, hr)
        )
    }
}

/** media_player — large artwork, title + series + device, a wavy progress bar, and a prominent
 *  prev / play-pause / next transport. */
@Composable
private fun HaMediaCard(result: SearchResult.HomeAssistantResult) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val attrs = result.attributes
    // Drive icon + progress ticker from a LOCAL var — result.state is a snapshot that never
    // refetches, so reading it directly would freeze the play icon and progress on tap.
    var playing by remember(result.entityId) { mutableStateOf(result.state == "playing") }
    val title  = attrs["media_title"]?.takeIf { it.isNotBlank() && it != "null" }
    val series = (attrs["media_series_title"] ?: attrs["media_artist"] ?: attrs["media_album_name"])
        ?.takeIf { it.isNotBlank() && it != "null" }
    val canSeek  = (attrs["supported_features"]?.toIntOrNull() ?: 0) and 2 != 0   // SUPPORT_SEEK
    val duration = attrs["media_duration"]?.toFloatOrNull()?.takeIf { it > 0f }

    // Live elapsed: seed from media_position PLUS the time HA says has passed since it recorded
    // that position (media_position_updated_at). Many players only push media_position on
    // play/seek, so the bare snapshot reads stale (often 0:00) — adding the drift gives the true
    // current position. Then tick locally each second while playing.
    var elapsed by remember(result.entityId, attrs["media_position"], attrs["media_position_updated_at"]) {
        val basePos = attrs["media_position"]?.toFloatOrNull() ?: 0f
        val drift = if (result.state == "playing") {
            attrs["media_position_updated_at"]?.let { ts ->
                runCatching {
                    val updatedMs = OffsetDateTime.parse(ts).toInstant().toEpochMilli()
                    ((System.currentTimeMillis() - updatedMs) / 1000f).coerceAtLeast(0f)
                }.getOrNull()
            } ?: 0f
        } else 0f
        val start = basePos + drift
        mutableStateOf(if (duration != null) start.coerceAtMost(duration) else start)
    }
    var seeking by remember(result.entityId) { mutableStateOf(false) }
    LaunchedEffect(playing, duration, seeking) {
        if (!playing || duration == null || seeking) return@LaunchedEffect
        while (true) { delay(1000); elapsed = (elapsed + 1f).coerceAtMost(duration) }
    }

    // Artwork (loaded manually — no Coil in this project).
    val artUrl = remember(result.entityId, attrs["entity_picture"]) {
        HomeAssistantClient.imageUrl(context, attrs["entity_picture"])
    }
    val art by produceState<android.graphics.Bitmap?>(null, artUrl) {
        value = artUrl?.let { HomeAssistantClient.fetchImage(context, it) }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        // Artwork + metadata
        Row(verticalAlignment = Alignment.CenterVertically) {
            val artBmp = art
            Box(
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (artBmp != null) {
                    Image(
                        bitmap = artBmp.asImageBitmap(), contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Default.MusicNote, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title ?: result.state.replaceFirstChar { it.uppercase() },
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold
                )
                if (series != null) Text(
                    series, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    result.friendlyName, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Progress (only when a real duration is known — radio/live streams often have none)
        if (duration != null) {
            Spacer(Modifier.height(12.dp))
            if (canSeek) {
                WavySeekBar(
                    progress              = (elapsed / duration).coerceIn(0f, 1f),
                    animate               = playing && !seeking,
                    onValueChange         = { frac -> seeking = true; elapsed = frac * duration },
                    onValueChangeFinished = {
                        seeking = false
                        haCall(scope, context, result.entityId, "media_player", "media_seek",
                            JSONObject().put("seek_position", elapsed.toInt()))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                WavyProgressIndicator(
                    progress = (elapsed / duration).coerceIn(0f, 1f),
                    animate  = playing,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmtTime(elapsed.toInt()), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fmtTime(duration.toInt()), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Prominent transport
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Volume down — flanks the transport (HA volume_down steps the level by one).
            IconButton(
                onClick = { haCall(scope, context, result.entityId, "media_player", "volume_down") },
                modifier = Modifier.size(40.dp)
            ) { Icon(Icons.Default.VolumeDown, "Volume down", modifier = Modifier.size(26.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { haCall(scope, context, result.entityId, "media_player", "media_previous_track") },
                modifier = Modifier.size(48.dp)
            ) { Icon(Icons.Default.SkipPrevious, "Previous", modifier = Modifier.size(34.dp)) }
            Spacer(Modifier.width(18.dp))
            FilledIconButton(
                onClick = {
                    playing = !playing
                    haCall(scope, context, result.entityId, "media_player", "media_play_pause",
                        patchState = if (playing) "playing" else "paused")
                },
                modifier = Modifier.size(60.dp)
            ) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause",
                    modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.width(18.dp))
            IconButton(
                onClick = { haCall(scope, context, result.entityId, "media_player", "media_next_track") },
                modifier = Modifier.size(48.dp)
            ) { Icon(Icons.Default.SkipNext, "Next", modifier = Modifier.size(34.dp)) }
            Spacer(Modifier.width(8.dp))
            // Volume up — flanks the transport (HA volume_up steps the level by one).
            IconButton(
                onClick = { haCall(scope, context, result.entityId, "media_player", "volume_up") },
                modifier = Modifier.size(40.dp)
            ) { Icon(Icons.Default.VolumeUp, "Volume up", modifier = Modifier.size(26.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** input_number / number — a value slider across the entity's min..max (step-snapped). */
@Composable
private fun HaNumberCard(result: SearchResult.HomeAssistantResult) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val attrs = result.attributes
    val unavailable = result.state == "unavailable"
    val minV = attrs["min"]?.toFloatOrNull() ?: 0f
    val maxV = (attrs["max"]?.toFloatOrNull() ?: 100f).coerceAtLeast(minV + 1f)
    val stepV = attrs["step"]?.toFloatOrNull()?.takeIf { it > 0f } ?: 1f
    val steps = (((maxV - minV) / stepV).toInt() - 1).coerceIn(0, 100)
    var value by remember(result.entityId) { mutableStateOf((result.state.toFloatOrNull() ?: minV).coerceIn(minV, maxV)) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
        HaControlRow(Icons.Default.Tune, result.friendlyName,
            if (unavailable) "Unavailable" else fmtTemp(value), active = !unavailable) {}
        if (!unavailable) {
            Slider(
                value                 = value,
                onValueChange         = { value = it },
                onValueChangeFinished = {
                    haCall(scope, context, result.entityId, result.domain, "set_value",
                        JSONObject().put("value", value.toDouble()))
                },
                valueRange = minV..maxV,
                steps      = steps,
                modifier   = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            )
        }
    }
}

/** input_select / select — a dropdown of the entity's options. */
@Composable
private fun HaSelectCard(result: SearchResult.HomeAssistantResult) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val options = remember(result.entityId) { parseStringList(result.attributes["options"]) }
    var current by remember(result.entityId) { mutableStateOf(result.state) }
    var menu by remember { mutableStateOf(false) }
    Box {
        HaControlRow(Icons.AutoMirrored.Filled.List, result.friendlyName,
            current.replace('_', ' ').replaceFirstChar { it.uppercase() }, active = false) {
            TextButton(onClick = { menu = true }, enabled = options.isNotEmpty()) {
                Text("Change")
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text    = { Text(opt) },
                    onClick = {
                        menu = false; current = opt
                        haCall(scope, context, result.entityId, result.domain, "select_option",
                            JSONObject().put("option", opt), patchState = opt)
                    }
                )
            }
        }
    }
}

/** vacuum — start / stop / return-to-base. */
@Composable
private fun HaVacuumCard(result: SearchResult.HomeAssistantResult) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val unavailable = result.state == "unavailable"
    val cleaning = result.state == "cleaning"
    val battery = result.attributes["battery_level"]?.toFloatOrNull()?.toInt()
    val vacLabel = if (unavailable) "Unavailable" else buildString {
        append(result.state.replace('_', ' ').replaceFirstChar { it.uppercase() })
        if (battery != null) append(" · $battery%")
    }
    HaControlRow(Icons.Default.CleaningServices, result.friendlyName, vacLabel, cleaning) {
        Row {
            IconButton(enabled = !unavailable, onClick = {
                haCall(scope, context, result.entityId, "vacuum", "start", patchState = "cleaning")
            }) { Icon(Icons.Default.PlayArrow, contentDescription = "Start") }
            IconButton(enabled = !unavailable, onClick = {
                haCall(scope, context, result.entityId, "vacuum", "stop")
            }) { Icon(Icons.Default.Stop, contentDescription = "Stop") }
            IconButton(enabled = !unavailable, onClick = {
                haCall(scope, context, result.entityId, "vacuum", "return_to_base", patchState = "returning")
            }) { Icon(Icons.Default.Home, contentDescription = "Return to base") }
        }
    }
}

/** humidifier — on/off toggle plus a target-humidity slider. */
@Composable
private fun HaHumidifierCard(result: SearchResult.HomeAssistantResult) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val attrs = result.attributes
    val unavailable = result.state == "unavailable"
    val minH = attrs["min_humidity"]?.toFloatOrNull() ?: 0f
    val maxH = (attrs["max_humidity"]?.toFloatOrNull() ?: 100f).coerceAtLeast(minH + 1f)
    val currentHum = attrs["current_humidity"]?.toFloatOrNull()?.toInt()
    var on by remember(result.entityId) { mutableStateOf(result.state == "on") }
    var hum by remember(result.entityId) { mutableStateOf((attrs["humidity"]?.toFloatOrNull() ?: 50f).coerceIn(minH, maxH)) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        HaControlRow(Icons.Default.WaterDrop, result.friendlyName,
            if (unavailable) "Unavailable"
            else if (on) "On · ${hum.toInt()}%" + (currentHum?.let { " (now $it%)" } ?: "")
            else "Off", on) {
            Switch(checked = on, enabled = !unavailable, onCheckedChange = { v ->
                on = v
                haCall(scope, context, result.entityId, "humidifier", if (v) "turn_on" else "turn_off",
                    patchState = if (v) "on" else "off", onFailure = { on = !v })
            })
        }
        if (on && !unavailable) {
            Slider(
                value                 = hum,
                onValueChange         = { hum = it },
                onValueChangeFinished = {
                    haCall(scope, context, result.entityId, "humidifier", "set_humidity",
                        JSONObject().put("humidity", hum.toInt()))
                },
                valueRange = minH..maxH,
                modifier   = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            )
        }
    }
}

/** alarm_control_panel — arm/disarm with an optional PIN field; arm modes from supported_features. */
@Composable
private fun HaAlarmCard(result: SearchResult.HomeAssistantResult) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val attrs = result.attributes
    val unavailable = result.state == "unavailable"
    // supported_features bits: ARM_HOME=1, ARM_AWAY=2, ARM_NIGHT=4. Default to home|away if absent.
    val feat = (attrs["supported_features"]?.toIntOrNull() ?: 0).let { if (it == 0) 3 else it }
    val codeFormat = attrs["code_format"]?.takeIf { it.isNotBlank() && it != "null" }   // "number" / "text"
    val codeArmRequired = attrs["code_arm_required"]?.toBoolean() ?: true
    var code by remember(result.entityId) { mutableStateOf("") }
    var stateText by remember(result.entityId) { mutableStateOf(result.state) }
    val armed = stateText != "disarmed"
    val needCodeToDisarm = codeFormat != null
    val needCodeToArm = codeFormat != null && codeArmRequired

    fun act(service: String, target: String, needsCode: Boolean) {
        if (needsCode && code.isBlank()) return
        val extras = if (needsCode) JSONObject().put("code", code) else null
        stateText = target
        haCall(scope, context, result.entityId, "alarm_control_panel", service, extras, patchState = target)
        code = ""
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
        HaControlRow(Icons.Default.Security, result.friendlyName,
            if (unavailable) "Unavailable" else stateText.replace('_', ' ').replaceFirstChar { it.uppercase() },
            active = armed && !unavailable) {}
        if (codeFormat != null && !unavailable) {
            OutlinedTextField(
                value                = code,
                onValueChange        = { code = it },
                label                = { Text("Code") },
                singleLine           = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions      = KeyboardOptions(
                    keyboardType = if (codeFormat == "number") KeyboardType.NumberPassword else KeyboardType.Password
                ),
                modifier             = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        if (!unavailable) {
            Row(
                modifier              = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (armed) {
                    FilledTonalButton(
                        enabled = !needCodeToDisarm || code.isNotBlank(),
                        onClick = { act("alarm_disarm", "disarmed", needCodeToDisarm) }
                    ) { Text("Disarm") }
                } else {
                    if (feat and 1 != 0) FilledTonalButton(
                        enabled = !needCodeToArm || code.isNotBlank(),
                        onClick = { act("alarm_arm_home", "armed_home", needCodeToArm) }
                    ) { Text("Arm Home") }
                    if (feat and 2 != 0) FilledTonalButton(
                        enabled = !needCodeToArm || code.isNotBlank(),
                        onClick = { act("alarm_arm_away", "armed_away", needCodeToArm) }
                    ) { Text("Arm Away") }
                    if (feat and 4 != 0) FilledTonalButton(
                        enabled = !needCodeToArm || code.isNotBlank(),
                        onClick = { act("alarm_arm_night", "armed_night", needCodeToArm) }
                    ) { Text("Arm Night") }
                }
            }
        }
    }
}

/** sensor / binary_sensor — read-only value display (no controls). */
@Composable
private fun HaSensorCard(result: SearchResult.HomeAssistantResult) {
    val unit = result.attributes["unit_of_measurement"]?.takeIf { it.isNotBlank() && it != "null" }
    val value = when {
        result.state == "unavailable" -> "Unavailable"
        unit != null                  -> "${result.state} $unit"
        else                          -> result.state.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
    HaControlRow(Icons.Default.Sensors, result.friendlyName, value, active = false) {}
}

internal fun groupResults(
    results: List<SearchResult>,
    plexSplitLibraries: Boolean = false
): List<Pair<String, List<SearchResult>>> {
    val map = LinkedHashMap<String, MutableList<SearchResult>>()
    for (result in results) {
        val key = when (result) {
            is SearchResult.AppResult          -> "Apps"
            is SearchResult.ContactResult      -> "Contacts"
            is SearchResult.CalculatorResult   -> "Calculator"
            is SearchResult.PluginResult       -> result.resultType?.takeIf { it.isNotBlank() } ?: result.pluginName
            is SearchResult.AiResult           -> "Gemini"
            is SearchResult.ClaudeResult       -> "Claude"
            is SearchResult.HermesResult       -> "Hermes"
            is SearchResult.DialerResult       -> "Phone"
            is SearchResult.WidgetPickerResult -> "Widgets"
            is SearchResult.SettingsResult       -> "Settings"
            is SearchResult.SystemSettingsResult -> "System Settings"
            is SearchResult.BrowserHistoryResult   -> "Browser"
            is SearchResult.FileResult             -> "Files"
            is SearchResult.BrowserSuggestionResult -> "Web Suggestions"
            is SearchResult.HomeAssistantResult      -> "Home Assistant"
            is SearchResult.PlexResult               ->
                if (plexSplitLibraries) result.library?.takeIf { it.isNotBlank() } ?: "Plex" else "Plex"
            is SearchResult.PersonResult             -> "Cast & Crew"
            is SearchResult.TandoorResult            -> "Recipes"
            is SearchResult.SymfoniumResult          -> "Music"
            is SearchResult.TransistorResult         -> "Radio"
            is SearchResult.PlacesResult             -> "Places"
            is SearchResult.WikipediaResult          -> "Wikipedia"
            is SearchResult.AnswerResult             -> answerGroupLabel(result.providerId)
            is SearchResult.GmailResult              -> "Mail"
            is SearchResult.YouTubeResult            -> "YouTube"
            is SearchResult.TimerResult              -> "Timer"
            is SearchResult.WeatherResult            -> "Weather"
            is SearchResult.DictionaryResult         -> "Dictionary"
            is SearchResult.OfflineAnswerResult      -> "On-device AI"
            is SearchResult.AddMediaResult           -> if (result.service == "sonarr") "Add to Sonarr" else "Add to Radarr"
            is SearchResult.TodoTaskResult           -> "Todoist"
            is SearchResult.TodoAddResult            -> "Todoist"
        }
        map.getOrPut(key) { mutableListOf() }.add(result)
    }
    return map.entries.map { it.key to (it.value as List<SearchResult>) }
}

/** Maps a result to its provider id (== SearchProvider.id / the settings limitKey), or null for
 *  results without a settings page. Used by the long-press "Search Configuration" action. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlexResultCard(result: SearchResult.PlexResult, onDismiss: () -> Unit, onConfigure: (() -> Unit)? = null) {
    val context = LocalContext.current
    val poster by produceState<android.graphics.Bitmap?>(null, result.thumbUrl) {
        value = result.thumbUrl?.let { PlexClient.fetchImage(context, it) }
    }
    if (result.rich) {
        RichMediaCard(
            art = poster, fallbackIcon = Icons.Default.Movie, title = result.title, subtitle = result.subtitle,
            primaryIcon = Icons.Default.PlayArrow, primaryLabel = "Play",
            onPrimary = { openPlexItem(context, result.ratingKey, result.type); onDismiss() },
            onLongClick = onConfigure,
            showVolume = result.type in setOf("track", "album", "artist"),   // music only — no volume on Movies/TV
            posterArt  = result.type in setOf("movie", "show", "season", "episode"),
            extra = if (result.type in setOf("movie", "show", "season", "episode")) ({ PlexMetaSection(result.ratingKey) }) else null
        )
        return
    }
    ListItem(
        headlineContent   = { Text(result.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(result.subtitle, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent    = {
            val p = poster
            Box(
                modifier = Modifier.size(width = 38.dp, height = 56.dp).clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (p != null) Image(p.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(Icons.Default.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        colors   = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.combinedClickable(onLongClick = onConfigure) {
            openPlexItem(context, result.ratingKey, result.type)
            onDismiss()
        }
    )
}

/** Open a Plex result. Neither the official Plex app (React-Native; only season/episode + watch.plex.tv
 *  routes) nor plezy exposes a deep link to an item's INFO page, so instead we hand directly-playable
 *  items (movie/episode) to plezy to PLAY via its `plezy://play?content_id=plezy_{machineId}_{ratingKey}`
 *  scheme. That filter isn't in the current plezy release yet, so this is forward-compatible: it throws
 *  today and we fall back to opening plezy / the Plex app / the web app, and starts playing on tap
 *  automatically once plezy ships the deep link. Synchronous so the search box closing can't cancel it. */
private fun openPlexItem(context: android.content.Context, ratingKey: String, type: String) {
    val mid = PlexClient.cachedMachineId()
    // 1) Hand the item to plezy to play it. Only directly-playable types: a show/season ratingKey has
    //    no media file, so plezy's player shows "File information not available" (verified). Those fall
    //    through to just opening the app.
    if (mid != null && (type == "movie" || type == "episode")) {
        val contentId = "plezy_${mid}_$ratingKey"
        val opened = runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("plezy://play?content_id=$contentId"))
                    .setPackage("com.edde746.plezy")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
        if (opened) return
    }
    // 2) Fall back to opening plezy, then the official Plex app.
    for (pkg in listOf("com.edde746.plezy", "com.plexapp.android")) {
        context.packageManager.getLaunchIntentForPackage(pkg)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?.let { if (runCatching { context.startActivity(it) }.isSuccess) return }
    }
    // 3) Last resort: the web app (item detail in a browser).
    if (mid != null) {
        val key = java.net.URLEncoder.encode("/library/metadata/$ratingKey", "UTF-8")
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://app.plex.tv/desktop/#!/server/$mid/details?key=$key"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

/** Rich actor/director card: circular photo + role + a Wikipedia bio + a horizontally-scrollable
 *  filmography (fetched lazily from Plex). Each film opens in Plex on tap. Always rich (a matched
 *  person is, by construction, a confident single result). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PersonResultCard(result: SearchResult.PersonResult, onDismiss: () -> Unit, onConfigure: (() -> Unit)? = null) {
    val context = LocalContext.current
    // Person photo: Plex person thumbs are ABSOLUTE (metadata-static.plex.tv) → fetch direct;
    // a server-relative path (rare) goes through the token-authed transcoder.
    val photo by produceState<android.graphics.Bitmap?>(null, result.thumbUrl) {
        value = result.thumbUrl?.let { t ->
            val url = if (t.startsWith("http", ignoreCase = true)) t else PlexClient.imageUrl(context, t)
            url?.let { PlexClient.fetchImage(context, it) }
        }
    }
    val bio by produceState<String?>(null, result.name) {
        value = WikipediaClient.summary(result.name)?.takeIf { it.type == "standard" }?.extract
    }
    val films by produceState<List<PlexItem>?>(null, result.id) {
        value = PlexClient.filmography(context, result.filmographyKeys)
    }
    Column(
        Modifier.fillMaxWidth()
            .then(if (onConfigure != null) Modifier.combinedClickable(onLongClick = onConfigure) {} else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val b = photo
                if (b != null) Image(b.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(result.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(result.role.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        bio?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        val list = films
        if (list != null && list.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(list) { film ->
                    FilmographyChip(film) { openPlexItem(context, film.ratingKey, film.type); onDismiss() }
                }
            }
        }
    }
}

@Composable
private fun FilmographyChip(item: PlexItem, onClick: () -> Unit) {
    val context = LocalContext.current
    val poster by produceState<android.graphics.Bitmap?>(null, item.thumb) {
        value = item.thumb?.let { t -> PlexClient.imageUrl(context, t)?.let { url -> PlexClient.fetchImage(context, url) } }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(88.dp).clip(RoundedCornerShape(8.dp)).clickable { onClick() }.padding(4.dp)
    ) {
        Box(
            Modifier.width(80.dp).height(120.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val b = poster
            if (b != null) Image(b.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(Icons.Default.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(item.title, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
        item.year?.let {
            Text(it.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TandoorResultCard(result: SearchResult.TandoorResult, onDismiss: () -> Unit, onConfigure: (() -> Unit)? = null) {
    val context = LocalContext.current
    val image by produceState<android.graphics.Bitmap?>(null, result.imageUrl) {
        value = result.imageUrl?.let { TandoorClient.fetchImage(context, it) }
    }
    if (result.rich) {
        RichMediaCard(
            art = image, fallbackIcon = Icons.Default.Restaurant, title = result.name, subtitle = result.subtitle,
            primaryIcon = Icons.Default.OpenInNew, primaryLabel = "Open recipe",
            onPrimary = { openTandoorItem(context, result.id); onDismiss() },
            onLongClick = onConfigure
        )
        return
    }
    ListItem(
        headlineContent   = { Text(result.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(result.subtitle, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        leadingContent    = {
            val p = image
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (p != null) Image(p.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(Icons.Default.Restaurant, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        colors   = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.combinedClickable(onLongClick = onConfigure) {
            openTandoorItem(context, result.id)
            onDismiss()
        }
    )
}

/** Open a Tandoor recipe. Prefers the user's kitshn client via its deep link
 *  `kitshn://<instanceHost>/recipe/<id>` — kitshn matches on HOST, so Deck's Tandoor URL host must
 *  match the instance kitshn is signed into (else kitshn shows "inaccessible instance"). Falls back to
 *  the recipe's web page, then opening kitshn. Synchronous so the search box closing can't cancel it. */
private fun openTandoorItem(context: android.content.Context, id: Int) {
    TandoorClient.host(context)?.let { host ->
        val opened = runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("kitshn://$host/recipe/$id"))
                    .setPackage("de.kitshn.android")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
        if (opened) return
    }
    // Fallback: the recipe's web page (works without kitshn installed).
    TandoorClient.webUrl(context, id)?.let { web ->
        if (runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(web)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess) return
    }
    // Last resort: open kitshn (home).
    context.packageManager.getLaunchIntentForPackage("de.kitshn.android")
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ?.let { runCatching { context.startActivity(it) } }
}

@Composable
private fun SymfoniumResultCard(result: SearchResult.SymfoniumResult, onDismiss: () -> Unit, onConfigure: (() -> Unit)? = null) {
    val context = LocalContext.current
    val art by produceState<android.graphics.Bitmap?>(null, result.artUri) {
        value = result.artUri?.let { SymfoniumClient.fetchArt(context, it) }
    }
    // Songs keep Symfonium's own subtitle (artist · album); containers get a type label so they read
    // as "tap to play this whole album/artist" rather than a single track.
    val sub = when (result.type) {
        "album"  -> "Album" + (result.subtitle?.let { " · $it" } ?: "")
        "artist" -> "Artist"
        else     -> result.subtitle
    }
    val fallbackIcon = when (result.type) {
        "album"  -> Icons.Default.Album
        "artist" -> Icons.Default.Person
        else     -> Icons.Default.MusicNote
    }
    val shape = if (result.type == "artist") CircleShape else RoundedCornerShape(8.dp)
    if (result.rich) {
        RichMediaCard(
            art = art, fallbackIcon = fallbackIcon, title = result.title, subtitle = sub,
            primaryIcon = Icons.Default.PlayArrow, primaryLabel = "Play",
            onPrimary = { SymfoniumClient.play(context, result.mediaId); onDismiss() },
            onLongClick = onConfigure, artShape = shape, showVolume = false,
            extra = when (result.type) {
                "album"  -> ({ SymfoniumAlbumTracks(result.mediaId) })
                "artist" -> ({ SymfoniumArtistSection(result.mediaId, result.title) })
                else     -> null
            }
        )
        return
    }
    ListItem(
        headlineContent   = { Text(result.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (sub != null) {
            { Text(sub, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else null,
        leadingContent    = {
            val p = art
            Box(
                modifier = Modifier.size(48.dp).clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (p != null) Image(p.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(fallbackIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        colors   = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.combinedClickable(onLongClick = onConfigure) {
            SymfoniumClient.play(context, result.mediaId)
            onDismiss()
        }
    )
}

@Composable
private fun TransistorResultCard(result: SearchResult.TransistorResult, onDismiss: () -> Unit, onConfigure: (() -> Unit)? = null) {
    val context = LocalContext.current
    ListItem(
        headlineContent   = { Text(result.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text("Radio station", style = MaterialTheme.typography.labelSmall) },
        leadingContent    = {
            val p = result.art
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (p != null) Image(p.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(Icons.Default.Radio, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        colors   = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.combinedClickable(onLongClick = onConfigure) {
            TransistorClient.play(context, result.mediaId)
            onDismiss()
        }
    )
}

internal fun providerIdForResult(result: SearchResult): String? = when (result) {
    is SearchResult.AppResult               -> "apps"
    is SearchResult.ContactResult           -> "contacts"
    is SearchResult.CalculatorResult        -> "calculator"
    is SearchResult.PluginResult            -> null
    is SearchResult.AiResult                -> "ai"
    is SearchResult.ClaudeResult            -> "claude"
    is SearchResult.HermesResult            -> "hermes"
    is SearchResult.DialerResult            -> "dialer"
    is SearchResult.WidgetPickerResult      -> "widgets"
    is SearchResult.SettingsResult          -> "settings"
    is SearchResult.SystemSettingsResult    -> "system_settings"
    is SearchResult.BrowserHistoryResult    -> "browser_history"
    is SearchResult.FileResult              -> "files"
    is SearchResult.BrowserSuggestionResult -> "browser_suggestions"
    is SearchResult.HomeAssistantResult     -> "home_assistant"
    is SearchResult.PlexResult              -> "plex"
    is SearchResult.PersonResult            -> "plex"
    is SearchResult.TandoorResult           -> "tandoor"
    is SearchResult.SymfoniumResult         -> "symfonium"
    is SearchResult.TransistorResult        -> "transistor"
    is SearchResult.PlacesResult            -> "places"
    is SearchResult.WikipediaResult         -> "wikipedia"
    is SearchResult.AnswerResult            -> result.providerId
    is SearchResult.GmailResult             -> "gmail"
    is SearchResult.YouTubeResult           -> "youtube"
    is SearchResult.TimerResult             -> "timer"
    is SearchResult.WeatherResult           -> "weather"
    is SearchResult.DictionaryResult        -> "dictionary"
    is SearchResult.OfflineAnswerResult     -> "offline_ai"
    is SearchResult.AddMediaResult          -> result.service
    is SearchResult.TodoTaskResult          -> "todoist"
    is SearchResult.TodoAddResult           -> "todoist"
}

/** Group-section label for an answer card, keyed by its provider id. */
internal fun answerGroupLabel(providerId: String): String = when (providerId) {
    "unit"        -> "Unit Conversion"
    "currency"    -> "Currency"
    "timezone"    -> "Time"
    "translation" -> "Translation"
    else          -> "Answer"
}

/** True when a result is a confident "answer" card that should float its group to the TOP of the
 *  results — an exact/rich match, or a single computed answer (calculator). Future answer providers
 *  (unit / currency / timezone / translation) should add themselves here. */
internal fun isConfidentResult(result: SearchResult): Boolean = when (result) {
    is SearchResult.AppResult        -> result.rich
    is SearchResult.ContactResult    -> result.rich
    is SearchResult.PlexResult       -> result.rich
    is SearchResult.PersonResult     -> true   // a matched actor/director floats to the top
    is SearchResult.TandoorResult    -> result.rich
    is SearchResult.SymfoniumResult  -> result.rich
    is SearchResult.WikipediaResult  -> result.rich
    is SearchResult.CalculatorResult -> true
    is SearchResult.AnswerResult     -> true
    else                             -> false
}

/** A computed-answer card (calculator + unit/currency/timezone/translation) that should always lead
 *  the results for the exact query it answers, regardless of title matching. */
internal fun isAnswerCard(result: SearchResult): Boolean =
    result is SearchResult.CalculatorResult || result is SearchResult.AnswerResult ||
    result is SearchResult.TimerResult || result is SearchResult.WeatherResult ||
    result is SearchResult.DictionaryResult

/** The user-facing primary text (name/title) of a result, used for query-relevance ranking. Null for
 *  cards with no title to match against (computed answers, ask-cards, widgets, suggestions). */
internal fun resultPrimaryText(result: SearchResult): String? = when (result) {
    is SearchResult.AppResult            -> result.app.label
    is SearchResult.ContactResult        -> result.name
    is SearchResult.PlexResult           -> result.title
    is SearchResult.PersonResult         -> result.name
    is SearchResult.TandoorResult        -> result.name
    is SearchResult.SymfoniumResult      -> result.title
    is SearchResult.TransistorResult     -> result.title
    is SearchResult.PluginResult         -> result.title
    is SearchResult.WikipediaResult      -> result.title
    is SearchResult.DialerResult         -> result.displayText
    is SearchResult.BrowserHistoryResult -> result.title
    is SearchResult.FileResult           -> result.name
    is SearchResult.SettingsResult       -> result.title
    is SearchResult.SystemSettingsResult -> result.title
    is SearchResult.HomeAssistantResult  -> result.friendlyName
    is SearchResult.YouTubeResult        -> result.title
    is SearchResult.AddMediaResult       -> result.title
    is SearchResult.TodoTaskResult       -> result.content
    else -> null
}

/** How well a result title matches the query, for ranking result GROUPS by relevance:
 *  3 = exact, 2 = whole-word or prefix, 1 = substring, 0 = no match. This is what makes an exact-title
 *  hit (the comic / movie "Batman") outrank a loose one (a music track that merely contains the word),
 *  independent of which provider it came from. */
internal fun titleMatchScore(title: String?, query: String): Int {
    val t = title?.lowercase()?.trim() ?: return 0
    val q = query.lowercase().trim()
    if (q.isEmpty() || t.isEmpty()) return 0
    return when {
        t == q -> 3
        t.startsWith("$q ") || t.endsWith(" $q") || t.contains(" $q ") -> 2   // whole word
        q.length >= 3 && t.startsWith(q) -> 2                                  // prefix
        q.length >= 3 && t.contains(q)   -> 1                                  // substring
        else -> 0
    }
}

/** Best title-match score across a group's results (groups are never empty). */
internal fun groupTitleScore(items: List<SearchResult>, query: String): Int =
    items.maxOf { titleMatchScore(resultPrimaryText(it), query) }

/** A music-domain group (Plex track/album/artist, Symfonium, radio). Such a group is demoted below
 *  non-music title matches in [groupRelevanceRank]: music that merely shares a name with a non-music
 *  search (e.g. The Who's track "Batman" vs. the film/comic) shouldn't outrank what was searched for.
 *  A genuine music search is unaffected — nothing non-music ties it, so there's nothing to sink below. */
internal fun isMusicGroup(items: List<SearchResult>): Boolean = when (val r = items.first()) {
    is SearchResult.PlexResult       -> r.type in setOf("track", "album", "artist")
    is SearchResult.SymfoniumResult  -> true
    is SearchResult.TransistorResult -> true
    else -> false
}

/** The content domain a result group represents, in the [GeminiNanoClassifier] vocabulary (movie / tv
 *  / music / comic / book / person / app / contact / recipe / place / web), or null if it has none.
 *  Used to match a group against the Nano-classified query domains in [groupScore]. */
internal fun groupDomain(items: List<SearchResult>): String? = when (val r = items.first()) {
    is SearchResult.PlexResult -> when (r.type) {
        "movie" -> "movie"
        "show", "season", "episode" -> "tv"
        "track", "album", "artist" -> "music"
        else -> null
    }
    is SearchResult.SymfoniumResult  -> "music"
    is SearchResult.TransistorResult -> "music"
    is SearchResult.PersonResult     -> "person"
    is SearchResult.AppResult        -> "app"
    is SearchResult.ContactResult    -> "contact"
    is SearchResult.DialerResult     -> "contact"
    is SearchResult.TandoorResult    -> "recipe"
    is SearchResult.PlacesResult     -> "place"
    is SearchResult.WikipediaResult  -> "web"
    is SearchResult.YouTubeResult    -> "web"
    is SearchResult.AddMediaResult   -> if (r.service == "sonarr") "tv" else "movie"
    is SearchResult.PluginResult     -> {
        val s = ((r.resultType ?: "") + " " + r.pluginName).lowercase()
        when {
            listOf("comic", "manga", "komikku").any { it in s } -> "comic"
            "book" in s -> "book"
            else -> null
        }
    }
    else -> null
}

/** Group ranking score (HIGHER = more relevant). Combines title match with domain knowledge:
 *  base = best title match (0/10/20/30); no title match sinks to the bottom. With Gemini-Nano query
 *  domains available, a group whose domain the model deems relevant is boosted and an incidental
 *  non-music-query music match is demoted; with no domains (model off / unavailable / still inferring)
 *  it falls back to the heuristic — demote a same-named music match (The Who's "Batman") below the
 *  film/comic the user actually searched for. */
internal fun groupScore(items: List<SearchResult>, query: String, aiDomains: Set<String>?): Int {
    val title = groupTitleScore(items, query)
    if (title == 0) return -100                                  // no title match → bottom
    var s = title * 10
    val music = isMusicGroup(items)
    if (aiDomains.isNullOrEmpty()) {
        if (music) s -= 15                                       // heuristic fallback
    } else {
        val domain = groupDomain(items)
        // Strong boost (>1 title level) so a domain the model deems relevant leads even on a weaker
        // title match — e.g. "batman" → comic puts the "Batman - Hush" comic (prefix match) above an
        // exact-title "Batman" movie, which is what the user wants for a comic character.
        if (domain != null && domain in aiDomains) s += 15       // model says this domain is relevant
        if (music && "music" !in aiDomains) s -= 15              // music isn't what they meant
    }
    // A Wikipedia group with several ambiguous articles (no confident summary) shouldn't rank high on a
    // title match alone — only a single strong/rich result deserves the top. Demote the multi-row case.
    val wikiAmbiguous = items.size > 1 &&
        items.all { it is SearchResult.WikipediaResult } &&
        items.none { it is SearchResult.WikipediaResult && it.rich }
    if (wikiAmbiguous) s -= 15
    // "Add to Radarr/Sonarr" is a secondary action (you don't own it yet) — rank it BELOW the Plex
    // content you already have, even when the title matches equally well.
    if (items.firstOrNull() is SearchResult.AddMediaResult) s -= 15
    return s
}

/** Open Settings → Search → <provider> directly for the given provider id. */
internal fun openSearchProviderSettings(context: android.content.Context, providerId: String) {
    context.startActivity(
        Intent(context, com.hermes.deck.ui.settings.SettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("section", "search")
            putExtra("search_provider", providerId)
        }
    )
}

@Composable
internal fun ResultGroup(
    label: String,
    results: List<SearchResult>,
    onDismiss: () -> Unit,
    retryKey: Any = Unit,
    resolvedIcon: (SearchResult) -> Drawable? = { null },
    iconShape: IconShape = IconShape.NONE,
    onManage: (() -> Unit)? = null,
    showLabel: Boolean = true,
    onResultSelected: ((SearchResult) -> Unit)? = null,
    onClaudeStart: ((String) -> Unit)? = null,
    onClaudeResume: ((ChatSession) -> Unit)? = null,
    onHermesStart: ((String) -> Unit)? = null
) {
    Surface(
        shape    = RoundedCornerShape(16.dp),
        color    = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        // Bottom padding INSIDE the card so the last row isn't flush against the card's bottom edge.
        Column(Modifier.padding(bottom = 8.dp)) {
            if (showLabel) Text(
                text     = label,
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 2.dp)
            )
            results.forEachIndexed { index, result ->
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 8.dp))
                SearchResultRow(
                    result           = result,
                    onDismiss        = onDismiss,
                    retryKey         = retryKey,
                    resolvedIcon     = resolvedIcon(result),
                    iconShape        = iconShape,
                    onResultSelected = onResultSelected,
                    onClaudeStart    = onClaudeStart,
                    onClaudeResume   = onClaudeResume,
                    onHermesStart    = onHermesStart
                )
            }
            if (onManage != null) {
                HorizontalDivider(Modifier.padding(horizontal = 8.dp))
                ListItem(
                    headlineContent = { Text("Manage all widgets") },
                    trailingContent = { Text("→", style = MaterialTheme.typography.bodyMedium) },
                    colors          = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier        = Modifier.clickable { onManage() }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppResultCard(result: SearchResult.AppResult, onDismiss: () -> Unit, resolvedIcon: android.graphics.drawable.Drawable? = null, iconShape: IconShape = IconShape.NONE, onResultSelected: ((SearchResult) -> Unit)? = null) {
    val context = LocalContext.current
    val drawerVm: DrawerViewModel = viewModel(factory = DrawerViewModel.factory(context))
    val installedPacks by drawerVm.installedPacks.collectAsState()
    val iconOverrides  by drawerVm.iconOverrides.collectAsState()
    val haptic = LocalHapticFeedback.current
    var showMenu         by remember { mutableStateOf(false) }
    var showTagEditor    by remember { mutableStateOf(false) }
    var showIconPicker   by remember { mutableStateOf(false) }
    var showWidgetPicker by remember { mutableStateOf(false) }
    val widgetProviders by produceState<List<android.appwidget.AppWidgetProviderInfo>>(emptyList(), result.app.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                android.appwidget.AppWidgetManager.getInstance(context)
                    .getInstalledProviders()
                    .filter { it.provider.packageName == result.app.packageName }
            }.getOrDefault(emptyList())
        }
    }

    val iconBitmap = rememberAppIconBitmap(
        key       = result.app.packageName,
        drawable  = resolvedIcon ?: result.app.icon,
        iconShape = iconShape,
        size      = 128
    )
    Box {
        fun launchApp() {
            context.packageManager.getLaunchIntentForPackage(result.app.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { context.startActivity(it) }
            onResultSelected?.invoke(result); onDismiss()
        }
        if (result.rich) {
            val (launcherApps, shortcuts) = rememberAppShortcuts(result.app.packageName, load = true)
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).combinedClickable(
                        onClick     = { launchApp() },
                        onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); showMenu = true }
                    )
                ) {
                    iconBitmap?.let { Image(it.asImageBitmap(), null, modifier = Modifier.size(56.dp)) } ?: Spacer(Modifier.size(56.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(result.app.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (result.app.category.isNotEmpty())
                            Text(result.app.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ContactActionButton(Icons.Default.OpenInNew, "Open", Modifier.weight(1f)) { launchApp() }
                    ContactActionButton(Icons.Default.Info, "App info", Modifier.weight(1f)) {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", result.app.packageName, null); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        }
                        onDismiss()
                    }
                }
                if (shortcuts.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    shortcuts.forEach { sc -> AppShortcutRow(launcherApps, sc) { onResultSelected?.invoke(result); onDismiss() } }
                }
            }
        } else {
        ListItem(
            headlineContent   = { Text(result.app.label, fontWeight = FontWeight.SemiBold) },
            supportingContent = if (result.app.category.isNotEmpty()) {
                { Text(result.app.category, style = MaterialTheme.typography.labelSmall) }
            } else null,
            leadingContent    = {
                iconBitmap?.let {
                    Image(
                        bitmap             = it.asImageBitmap(),
                        contentDescription = null,
                        modifier           = Modifier.size(40.dp)
                    )
                } ?: Spacer(Modifier.size(40.dp))
            },
            colors   = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.combinedClickable(
                onClick = { launchApp() },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showMenu = true
                }
            )
        )
        }
        AppContextMenu(
            expanded         = showMenu,
            onDismissRequest = { showMenu = false },
            hasShortcuts     = false,
            shortcuts        = {},
            actions          = {
            AppContextMenuItem("Edit tags", Icons.Default.Label) { showMenu = false; showTagEditor = true }
            AppContextMenuItem("Hide", Icons.Default.VisibilityOff) { showMenu = false; drawerVm.hideApp(result.app.packageName) }
            AppContextMenuItem("Change icon", Icons.Default.Palette) { showMenu = false; showIconPicker = true }
            AppContextMenuItem("Uninstall", Icons.Default.Delete) {
                showMenu = false
                context.startActivity(
                    Intent(Intent.ACTION_DELETE, Uri.parse("package:${result.app.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            if (widgetProviders.isNotEmpty()) {
                AppContextMenuItem("Select widget", Icons.Default.Widgets) { showMenu = false; showWidgetPicker = true }
            }
            AppContextMenuItem("App info", Icons.Default.Info) {
                showMenu = false
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", result.app.packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
            AppContextMenuItem("Search Configuration", Icons.Default.Search) {
                showMenu = false
                openSearchProviderSettings(context, "apps")
            }
            }
        )
        if (showTagEditor) {
            TagEditorDialog(packageName = result.app.packageName, title = result.app.label, onDismiss = { showTagEditor = false })
        }
        if (showIconPicker) {
            IconPickerDialog(
                app             = result.app,
                installedPacks  = installedPacks,
                currentOverride = iconOverrides[result.app.packageName],
                onPick          = { packPkg -> drawerVm.setIconOverride(result.app.packageName, packPkg) },
                onDismiss       = { showIconPicker = false }
            )
        }
        if (showWidgetPicker) {
            WidgetPickerDialog(
                app             = result.app,
                widgetProviders = widgetProviders,
                onDismiss       = { showWidgetPicker = false }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactResultCard(result: SearchResult.ContactResult, onDismiss: () -> Unit, onResultSelected: ((SearchResult) -> Unit)? = null, onConfigure: (() -> Unit)? = null) {
    val context = LocalContext.current
    fun act(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        onResultSelected?.invoke(result); onDismiss()
    }
    if (!result.rich) {
        ListItem(
            headlineContent   = { Text(result.name, fontWeight = FontWeight.SemiBold) },
            supportingContent = result.phoneNumber?.let { num -> { Text(num) } },
            leadingContent    = { InitialsAvatar(result.name) },
            colors            = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier          = Modifier.combinedClickable(onLongClick = onConfigure) {
                result.phoneNumber?.let { act(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$it"))) } ?: run { onResultSelected?.invoke(result); onDismiss() }
            }
        )
        return
    }
    // Rich card — the confident single match: photo, name, and inline Call / Message / Email actions.
    val photo by produceState<android.graphics.Bitmap?>(null, result.photoUri) {
        value = result.photoUri?.let { uri ->
            withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) } }.getOrNull()
            }
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth()
            .combinedClickable(onLongClick = onConfigure) {
                result.phoneNumber?.let { act(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$it"))) }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                val p = photo
                if (p != null) Image(p.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                else InitialsAvatar(result.name)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(result.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                result.phoneNumber?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            result.phoneNumber?.let { num ->
                ContactActionButton(Icons.Default.Call, "Call", Modifier.weight(1f)) { act(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num"))) }
                ContactActionButton(Icons.Default.Sms, "Message", Modifier.weight(1f)) { act(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$num"))) }
            }
            result.email?.let { mail ->
                ContactActionButton(Icons.Default.Email, "Email", Modifier.weight(1f)) { act(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$mail"))) }
            }
        }
    }
}

@Composable
private fun ContactActionButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(vertical = 10.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun AppShortcutRow(launcherApps: android.content.pm.LauncherApps, shortcut: android.content.pm.ShortcutInfo, onLaunched: () -> Unit) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.densityDpi
    val icon by produceState<android.graphics.Bitmap?>(null, shortcut.id) {
        value = withContext(Dispatchers.IO) {
            runCatching { launcherApps.getShortcutIconDrawable(shortcut, density)?.let { shortcutDrawableToBitmap(it) } }.getOrNull()
        }
    }
    val label = (shortcut.shortLabel ?: shortcut.longLabel ?: "").toString()
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .clickable { runCatching { launcherApps.startShortcut(shortcut, null, null) }; onLaunched() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        icon?.let { Image(it.asImageBitmap(), null, modifier = Modifier.size(20.dp)) } ?: Spacer(Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Shared rich card for the confident single media match (Plex / Tandoor / Symfonium): large art +
 *  title/subtitle + a primary Play/Open button, plus an optional device media-volume slider. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RichMediaCard(
    art: android.graphics.Bitmap?,
    fallbackIcon: ImageVector,
    title: String,
    subtitle: String?,
    primaryIcon: ImageVector,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    artShape: Shape = RoundedCornerShape(8.dp),
    showVolume: Boolean = false,
    posterArt: Boolean = false,   // true → full 2:3 movie/TV poster (taller, uncropped, top-aligned)
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxWidth()
            .combinedClickable(onLongClick = onLongClick) { onPrimary() }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = if (posterArt) Alignment.Top else Alignment.CenterVertically) {
            Box(
                modifier = (if (posterArt) Modifier.width(72.dp).height(108.dp) else Modifier.size(72.dp))
                    .clip(artShape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (art != null) Image(art.asImageBitmap(), null,
                    contentScale = if (posterArt) ContentScale.Fit else ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(fallbackIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
        extra?.invoke(this)
        Spacer(Modifier.height(12.dp))
        ContactActionButton(primaryIcon, primaryLabel, Modifier.fillMaxWidth()) { onPrimary() }
        if (showVolume) {
            Spacer(Modifier.height(8.dp))
            MediaVolumeSlider()
        }
    }
}

@Composable
private fun MediaVolumeSlider() {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }
    val max = remember { audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var vol by remember { mutableStateOf(audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.VolumeUp, contentDescription = "Media volume", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Slider(
            value         = vol,
            onValueChange = { vol = it; runCatching { audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, it.roundToInt(), 0) } },
            valueRange    = 0f..max.toFloat(),
            steps         = (max - 1).coerceAtLeast(0),
            modifier      = Modifier.weight(1f)
        )
    }
}

/** Plex rich-card extra: plot summary + a horizontally-scrollable cast row (fetched lazily). */
@Composable
private fun ColumnScope.PlexMetaSection(ratingKey: String) {
    val context = LocalContext.current
    val meta by produceState<PlexClient.PlexMeta?>(null, ratingKey) {
        value = PlexClient.metadata(context, ratingKey)
    }
    val m = meta ?: return
    m.summary?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
    if (m.cast.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(m.cast) { c -> CastChip(c) }
        }
    }
}

@Composable
private fun CastChip(cast: PlexClient.PlexCast) {
    val context = LocalContext.current
    val img by produceState<android.graphics.Bitmap?>(null, cast.thumb) {
        value = cast.thumb?.let { t ->
            // Plex cast thumbs are usually ABSOLUTE (metadata-static.plex.tv) — fetch those directly;
            // only server-relative paths go through the token-authed photo transcoder.
            val url = if (t.startsWith("http", ignoreCase = true)) t else PlexClient.imageUrl(context, t)
            url?.let { PlexClient.fetchImage(context, it) }
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            val b = img
            if (b != null) Image(b.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(cast.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
        cast.role?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

/** Rich album extra: the album's songs, each tappable to play in Symfonium. */
@Composable
private fun ColumnScope.SymfoniumAlbumTracks(albumMediaId: String) {
    val context = LocalContext.current
    val tracks by produceState<List<SymfoniumClient.SymfoniumItem>?>(null, albumMediaId) {
        value = SymfoniumClient.children(context, albumMediaId)
    }
    val list = tracks ?: return
    if (list.isEmpty()) return
    Spacer(Modifier.height(8.dp))
    list.take(25).forEachIndexed { i, t ->
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .clickable { SymfoniumClient.play(context, t.mediaId) }
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(20.dp))
            // Symfonium prefixes the title with its own "N • " track number — strip it (we number the row).
            Text(
                t.title.replace(symfTrackNumberPrefix, ""),
                style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            t.subtitle?.takeIf { it.isNotBlank() }?.let {   // duration, e.g. "05:28"
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Symfonium prefixes album-track titles with "N • " (its own numbering); we render our own index, so
 *  strip a leading number + bullet/dot separator to avoid showing the number twice. */
private val symfTrackNumberPrefix = Regex("^\\d+\\s*[•·.]\\s*")

/** Rich artist extra: a Wikipedia bio (Symfonium has none) + a scrollable row of the artist's albums. */
@Composable
private fun ColumnScope.SymfoniumArtistSection(artistMediaId: String, artistName: String) {
    val context = LocalContext.current
    val bio by produceState<String?>(null, artistName) {
        value = WikipediaClient.summary(artistName)?.takeIf { it.type == "standard" }?.extract
    }
    val albums by produceState<List<SymfoniumClient.SymfoniumItem>?>(null, artistMediaId) {
        value = SymfoniumClient.children(context, artistMediaId)
    }
    bio?.takeIf { it.isNotBlank() }?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
    val al = albums ?: return
    if (al.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(al) { album -> SymfoniumAlbumChip(album) }
        }
    }
}

@Composable
private fun SymfoniumAlbumChip(album: SymfoniumClient.SymfoniumItem) {
    val context = LocalContext.current
    val art by produceState<android.graphics.Bitmap?>(null, album.artUri) {
        value = album.artUri?.let { SymfoniumClient.fetchArt(context, it) }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp).clip(RoundedCornerShape(8.dp))
            .clickable { SymfoniumClient.play(context, album.mediaId) }.padding(4.dp)
    ) {
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            val b = art
            if (b != null) Image(b.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(Icons.Default.Album, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(album.title, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CalculatorResultCard(result: SearchResult.CalculatorResult, onConfigure: (() -> Unit)? = null) {
    ListItem(
        headlineContent   = {
            Text(
                text       = result.result,
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        },
        supportingContent = { Text(result.expression) },
        leadingContent    = { Icon(Icons.Default.Calculate, contentDescription = null) },
        colors            = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier          = if (onConfigure != null) Modifier.combinedClickable(onLongClick = onConfigure) {}
                            else Modifier
    )
}

@Composable
private fun PluginResultCard(result: SearchResult.PluginResult, onDismiss: () -> Unit, onResultSelected: ((SearchResult) -> Unit)? = null) {
    val context = LocalContext.current
    val iconBitmap by produceState<ImageBitmap?>(null, result.iconUri) {
        // Clear any bitmap left over from a recycled row before (re)loading, so a slow/failed fetch
        // never leaves the previous result's cover showing on this one.
        value = null
        value = result.iconUri?.let { uriStr ->
            withContext(Dispatchers.IO) {
                runCatching {
                    val uri = Uri.parse(uriStr)
                    when (uri.scheme?.lowercase()) {
                        // Plugins can hand us a plain http(s) icon URL (e.g. a favicon). Deck has no
                        // image loader, so fetch it manually. content:// (the old path) still works.
                        "http", "https" -> {
                            val conn = (java.net.URL(uriStr).openConnection() as java.net.HttpURLConnection).apply {
                                connectTimeout = 6_000
                                readTimeout = 6_000
                                setRequestProperty("User-Agent", "Deck/1.0")
                            }
                            try {
                                conn.inputStream.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                            } finally {
                                conn.disconnect()
                            }
                        }
                        else -> context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                            BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor)?.asImageBitmap()
                        }
                    }
                }.getOrNull()
            }
        }
    }
    // Custom Row (not ListItem) so the favicon top-aligns with the title's first line instead of
    // centering against a 2-line title.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                result.actionUri?.let { uri ->
                    runCatching {
                        context.startActivity(
                            Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
                onResultSelected?.invoke(result)
                onDismiss()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
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
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                result.title,
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 2,
                overflow   = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            result.subtitle?.let { sub ->
                Text(
                    sub,
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AiResultCard(result: SearchResult.AiResult, onConfigure: (() -> Unit)? = null) {
    ListItem(
        headlineContent   = { Text(result.answer) },
        supportingContent = { Text("Gemini Nano", style = MaterialTheme.typography.labelSmall) },
        leadingContent    = {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.tertiary
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (onConfigure != null) Modifier.combinedClickable(onLongClick = onConfigure) {}
                   else Modifier
    )
}

/**
 * Idle Claude entry in the results: an "Ask Claude about …" row that starts a new
 * conversation, plus up to 3 recent chat sessions to resume. The live conversation
 * itself is rendered separately as one card per message (see claudeConversationItems),
 * driven by SearchViewModel.activeChat.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClaudeResultCard(
    result: SearchResult.ClaudeResult,
    onStart: ((String) -> Unit)? = null,
    onResume: ((ChatSession) -> Unit)? = null,
    onConfigure: (() -> Unit)? = null
) {
    val context = LocalContext.current
    // Bumped whenever a pin toggles, to re-read the store without changing the query.
    var pinVersion by remember { mutableStateOf(0) }
    val pinned    = remember(result.query, pinVersion) { ClaudeChatStore.pinnedSessions(context) }
    val pinnedIds = pinned.map { it.id }.toSet()
    val recents   = remember(result.query, pinVersion) {
        ClaudeChatStore.relevant(context, result.query, 3).filterNot { it.id in pinnedIds }
    }
    // Conversation handoff: threads last worked on your OTHER devices (desktop/voice), tappable to
    // resume here. Resuming gives this device a FRESH session whose later turns append back to the
    // same shared thread (remoteTid) as our own contributor — so nothing overwrites another surface.
    var remote by remember { mutableStateOf<List<ChatSession>>(emptyList()) }
    var hiddenLocalIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(result.query, pinVersion) {
        if (!com.hermes.deck.ui.search.providers.ConversationClient.isConfigured(context)) {
            remote = emptyList(); hiddenLocalIds = emptySet(); return@LaunchedEffect
        }
        val threads = com.hermes.deck.ui.search.providers.ConversationClient.list(context)
            .filter { th -> th.surfaces.any { it != "deck" } }
        val tidSet = threads.map { it.tid }.toSet()
        val locals = ClaudeChatStore.recent(context, 100)
        // Purge stale v1 artifacts: an old-resume local session that reused a shared thread's id with
        // no remoteTid. It duplicates the remote thread and would re-inject its turns if resumed. Safe:
        // a legit deck-only thread's id is never a mixed-surface tid (deck-only threads are filtered out).
        val artifacts = locals.filter { it.remoteTid == null && it.id in tidSet }
        artifacts.forEach { ClaudeChatStore.delete(context, it.id) }
        hiddenLocalIds = artifacts.map { it.id }.toSet()
        // Don't show a remote thread we've already resumed locally (avoids re-creating the two-row dupe).
        val resumedTids = locals.mapNotNull { it.remoteTid }.toSet()
        remote = threads.filter { it.tid !in resumedTids }.take(3).map { th ->
            val imported = th.messages.map { ChatMessage(it.first, it.second) }
            val from = th.surfaces.filter { it != "deck" }.joinToString("+").ifBlank { th.lastSurface }
            ChatSession(
                id = java.util.UUID.randomUUID().toString(),
                title = th.title.ifBlank { "(conversation)" } + " (from " + from + ")",
                updatedAt = System.currentTimeMillis(),
                messages = imported,
                remoteTid = th.tid,
                importCount = imported.size,
                backend = com.hermes.deck.ui.search.providers.ChatBackend.Claude
            )
        }
    }
    Column {
        ListItem(
            headlineContent   = { Text("Ask Claude about \"${result.query}\"") },
            supportingContent = { Text("Anthropic's AI assistant · tap to start a chat", style = MaterialTheme.typography.labelSmall) },
            leadingContent    = {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            colors   = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.combinedClickable(
                onClick     = { onStart?.invoke(result.query) },
                onLongClick = onConfigure
            )
        )
        pinned.filterNot { it.id in hiddenLocalIds }.forEach { session ->
            HorizontalDivider(Modifier.padding(horizontal = 8.dp))
            ClaudeSessionRow(
                session     = session,
                pinned      = true,
                onResume    = { onResume?.invoke(session) },
                onTogglePin = { ClaudeChatStore.setPinned(context, session.id, false); pinVersion++ },
                onDelete    = { ClaudeChatStore.delete(context, session.id); pinVersion++ }
            )
        }
        recents.filterNot { it.id in hiddenLocalIds }.forEach { session ->
            HorizontalDivider(Modifier.padding(horizontal = 8.dp))
            ClaudeSessionRow(
                session     = session,
                pinned      = false,
                onResume    = { onResume?.invoke(session) },
                onTogglePin = { ClaudeChatStore.setPinned(context, session.id, true); pinVersion++ },
                onDelete    = { ClaudeChatStore.delete(context, session.id); pinVersion++ }
            )
        }
        remote.forEach { session ->
            HorizontalDivider(Modifier.padding(horizontal = 8.dp))
            ClaudeSessionRow(
                session     = session,
                pinned      = false,
                onResume    = { onResume?.invoke(session) },
                onTogglePin = { }
            )
        }
    }
}

/**
 * Tap-to-ask Hermes card. Mirrors [ClaudeResultCard] but talks to the self-hosted Hermes agent;
 * below the ask row it surfaces this backend's own recent/pinned threads (kept separate from
 * Claude's via [com.hermes.deck.ui.search.providers.ChatBackend]).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HermesResultCard(
    result: SearchResult.HermesResult,
    onStart: ((String) -> Unit)? = null,
    onResume: ((ChatSession) -> Unit)? = null,
    onConfigure: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var pinVersion by remember { mutableStateOf(0) }
    val backend = com.hermes.deck.ui.search.providers.ChatBackend.Hermes
    val pinned    = remember(result.query, pinVersion) { ClaudeChatStore.pinnedSessions(context, backend = backend) }
    val pinnedIds = pinned.map { it.id }.toSet()
    val recents   = remember(result.query, pinVersion) {
        ClaudeChatStore.relevant(context, result.query, 3, backend).filterNot { it.id in pinnedIds }
    }
    Column {
        ListItem(
            headlineContent   = { Text("Ask Hermes about \"${result.query}\"") },
            supportingContent = { Text("Your self-hosted Hermes agent · tap to start a chat", style = MaterialTheme.typography.labelSmall) },
            leadingContent    = {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            },
            colors   = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.combinedClickable(
                onClick     = { onStart?.invoke(result.query) },
                onLongClick = onConfigure
            )
        )
        pinned.forEach { session ->
            HorizontalDivider(Modifier.padding(horizontal = 8.dp))
            ClaudeSessionRow(
                session     = session,
                pinned      = true,
                onResume    = { onResume?.invoke(session) },
                onTogglePin = { ClaudeChatStore.setPinned(context, session.id, false); pinVersion++ }
            )
        }
        recents.forEach { session ->
            HorizontalDivider(Modifier.padding(horizontal = 8.dp))
            ClaudeSessionRow(
                session     = session,
                pinned      = false,
                onResume    = { onResume?.invoke(session) },
                onTogglePin = { ClaudeChatStore.setPinned(context, session.id, true); pinVersion++ }
            )
        }
    }
}

/** A recent/pinned Claude session row. Tap resumes it; long-press toggles its pin. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClaudeSessionRow(
    session: ChatSession,
    pinned: Boolean,
    onResume: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        ListItem(
            headlineContent   = { Text(session.title, maxLines = 1) },
            supportingContent = {
                val n = session.messages.count { it.role == "user" }
                Text(
                    "$n message${if (n == 1) "" else "s"} · " +
                        android.text.format.DateUtils.getRelativeTimeSpanString(session.updatedAt),
                    style = MaterialTheme.typography.labelSmall
                )
            },
            leadingContent = {
                Icon(if (pinned) Icons.Default.PushPin else Icons.Default.History, contentDescription = null)
            },
            colors   = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.combinedClickable(onClick = onResume, onLongClick = { menu = true })
        )
        AppContextMenu(
            expanded         = menu,
            onDismissRequest = { menu = false },
            hasShortcuts     = false,
            shortcuts        = {},
            actions          = {
                AppContextMenuItem(if (pinned) "Unpin thread" else "Pin thread", Icons.Default.PushPin) {
                    menu = false
                    onTogglePin()
                }
                if (onDelete != null) {
                    AppContextMenuItem("Delete thread", Icons.Default.Delete) {
                        menu = false
                        onDelete()
                    }
                }
            }
        )
    }
}

/**
 * An active Claude conversation: messages scroll in a list that FILLS a definite-height Box,
 * with the reply bar pinned (overlaid) at the bottom and lifted above the keyboard by imeBottom.
 * Why a Box + fillMaxSize and not a Column + weight: the search-bar content passes an unbounded
 * height, so a weighted child collapses to 0 (that was the "reply at top, no messages" bug). A
 * fillMaxSize LazyColumn inside a *definite-height* Box renders correctly.
 */
@Composable
internal fun ClaudeConversation(
    state: ClaudeChatState,
    onSend: (String) -> Unit,
    bottomInset: Dp = 0.dp,
    thinking: Boolean = false,
    onToggleThinking: () -> Unit = {},
    onConfirmAction: (String) -> Unit = {},
    onCancelAction: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val context = LocalContext.current
    // Long-pressing any message pins/unpins the whole active thread. Bumped to re-read the store.
    var pinVersion by remember { mutableStateOf(0) }
    val threadPinned = remember(state.sessionId, pinVersion) { ClaudeChatStore.isPinned(context, state.sessionId) }
    // Measure the reply bar so the list reserves exactly its height + a small 8dp gap (not a
    // guessed constant). bottomInset is 0 on surfaces that already inset above the keyboard.
    var replyBarHeightPx by remember { mutableStateOf(0) }
    val replyClearance = with(density) { replyBarHeightPx.toDp() } + 8.dp + bottomInset
    val msgCount = state.messages.size + (if (state.loading) 1 else 0) +
        (if (state.error != null) 1 else 0) +
        state.cardsByMessage.values.sumOf { it.size } + state.actionsByMessage.values.sumOf { it.size }
    LaunchedEffect(msgCount, replyClearance, state.streamingText) {
        if (msgCount > 0) runCatching { listState.animateScrollToItem(msgCount - 1) }
    }
    Box(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            state          = listState,
            modifier       = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = replyClearance)
        ) {
            state.messages.forEachIndexed { i, msg ->
                item(key = "claudemsg:$i") {
                    ClaudeMessageCard(
                        message     = msg,
                        pinned      = threadPinned,
                        onTogglePin = { ClaudeChatStore.setPinned(context, state.sessionId, !threadPinned); pinVersion++ }
                    )
                }
                // Result cards Claude presented with this assistant turn (interleaved; the real
                // cards in a Surface for a card background + correct content colour — a raw item
                // would inherit the default black LocalContentColor).
                state.cardsByMessage[i]?.let { cards ->
                    items(cards.size, key = { idx -> "claudecard:$i:$idx" }) { idx ->
                        Surface(
                            shape    = RoundedCornerShape(16.dp),
                            color    = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            SearchResultRow(result = cards[idx], onDismiss = {})
                        }
                    }
                }
                // Actions Claude proposed with this turn.
                state.actionsByMessage[i]?.let { actions ->
                    items(actions.size, key = { idx -> "claudeaction:${actions[idx].id}" }) { idx ->
                        val action = actions[idx]
                        ClaudeActionCard(
                            action    = action,
                            onConfirm = { onConfirmAction(action.id) },
                            onCancel  = { onCancelAction(action.id) }
                        )
                    }
                }
            }
            if (state.loading) {
                val streaming = state.streamingText
                if (!streaming.isNullOrEmpty()) {
                    // The answer streaming in — render it as the live assistant bubble.
                    item(key = "claude_streaming") {
                        ClaudeMessageCard(
                            message     = ChatMessage("assistant", streaming),
                            pinned      = threadPinned,
                            onTogglePin = { ClaudeChatStore.setPinned(context, state.sessionId, !threadPinned); pinVersion++ }
                        )
                    }
                } else {
                    // Before the first token / while a tool runs — show the spinner.
                    item(key = "claude_thinking") { ClaudeThinkingCard() }
                }
            }
            state.error?.let { err -> item(key = "claude_error") { ClaudeErrorCard(err) } }
        }
        ClaudeReplyBar(
            state, onSend,
            thinking = thinking,
            onToggleThinking = onToggleThinking,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = bottomInset)
                .onSizeChanged { replyBarHeightPx = it.height }
        )
    }
}

/** A Confirm/Cancel card for an action Claude proposed (turn-ending model — nothing runs until
 *  the user taps Confirm). Shows status (done/failed/cancelled) once resolved. */
@Composable
private fun ClaudeActionCard(action: ClaudePendingAction, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val (icon, tint) = when (action.status) {
        "done"      -> Icons.Default.CheckCircle to cs.primary
        "failed"    -> Icons.Default.ErrorOutline to cs.error
        "cancelled" -> Icons.Default.Cancel to cs.onSurfaceVariant
        else        -> Icons.Default.PlayArrow to cs.primary
    }
    Surface(
        color    = cs.surfaceContainerHigh,
        shape    = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            modifier          = Modifier.padding(start = 12.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(end = 12.dp))
            Column(Modifier.weight(1f)) {
                Text(action.summary, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    when (action.status) {
                        "done"      -> "Done"
                        "failed"    -> "Failed"
                        "cancelled" -> "Cancelled"
                        else        -> "Confirm to run"
                    },
                    style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant
                )
            }
            if (action.status == "pending") {
                TextButton(onClick = onCancel) { Text("Cancel") }
                Spacer(Modifier.width(4.dp))
                FilledTonalButton(onClick = onConfirm) { Text("Confirm") }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClaudeMessageCard(
    message: ChatMessage,
    pinned: Boolean = false,
    onTogglePin: (() -> Unit)? = null
) {
    val isUser = message.role == "user"
    var menu by remember { mutableStateOf(false) }
    Box {
        Surface(
            shape    = RoundedCornerShape(16.dp),
            color    = if (isUser) MaterialTheme.colorScheme.primaryContainer
                       else        MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                .then(
                    if (onTogglePin != null)
                        Modifier.combinedClickable(onClick = {}, onLongClick = { menu = true })
                    else Modifier
                )
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector        = if (isUser) Icons.Default.Search else Icons.Default.AutoAwesome,
                    contentDescription = if (isUser) "You" else "Claude",
                    tint               = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                                         else        MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.padding(end = 10.dp).size(20.dp)
                )
                Text(
                    text  = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                            else        MaterialTheme.colorScheme.onSurface
                )
            }
        }
        if (onTogglePin != null) {
            AppContextMenu(
                expanded         = menu,
                onDismissRequest = { menu = false },
                hasShortcuts     = false,
                shortcuts        = {},
                actions          = {
                    AppContextMenuItem(if (pinned) "Unpin thread" else "Pin thread", Icons.Default.PushPin) {
                        menu = false
                        onTogglePin()
                    }
                }
            )
        }
    }
}

@Composable
private fun ClaudeThinkingCard() {
    Surface(
        shape    = RoundedCornerShape(16.dp),
        color    = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("Thinking…", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ClaudeErrorCard(err: String) {
    Surface(
        shape    = RoundedCornerShape(16.dp),
        color    = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(
            err,
            color    = MaterialTheme.colorScheme.onErrorContainer,
            style    = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun ClaudeReplyBar(
    state: ClaudeChatState,
    onSend: (String) -> Unit,
    thinking: Boolean = false,
    onToggleThinking: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var reply by remember(state.sessionId) { mutableStateOf("") }
    fun submit() {
        val t = reply.trim()
        if (t.isNotBlank() && !state.loading) { reply = ""; onSend(t) }
    }
    Surface(
        shape    = RoundedCornerShape(16.dp),
        // Distinct from the message bubbles (surfaceContainerHighest) via a darker fill (no border).
        color    = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            // Usage bar for the most recent response (output tokens of the max-output budget).
            val progress = (state.lastOutTokens.toFloat() / AnthropicClient.MAX_TOKENS).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconToggleButton(checked = thinking, onCheckedChange = { onToggleThinking() }) {
                    Icon(
                        Icons.Default.Psychology,
                        contentDescription = if (thinking) "Thinking model on" else "Delegate to thinking model",
                        tint = if (thinking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextField(
                    value           = reply,
                    onValueChange   = { reply = it },
                    placeholder     = { Text("Reply to Claude…") },
                    enabled         = !state.loading,
                    singleLine      = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor   = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor  = Color.Transparent,
                        focusedIndicatorColor   = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor  = Color.Transparent
                    ),
                    modifier        = Modifier.weight(1f)
                )
                IconButton(onClick = { submit() }, enabled = !state.loading && reply.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialerResultCard(result: SearchResult.DialerResult, onDismiss: () -> Unit, onResultSelected: ((SearchResult) -> Unit)? = null, onConfigure: (() -> Unit)? = null) {
    val context = LocalContext.current
    ListItem(
        headlineContent   = { Text(formatPhoneNumber(result.phoneNumber)) },
        supportingContent = if (result.displayText != result.phoneNumber) {
            { Text("\"${result.displayText}\"", style = MaterialTheme.typography.bodySmall) }
        } else null,
        leadingContent    = {
            Icon(Icons.Outlined.Phone, contentDescription = "Dial")
        },
        colors   = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.combinedClickable(onLongClick = onConfigure) {
            context.startActivity(
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:${result.phoneNumber}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            onResultSelected?.invoke(result)
            onDismiss()
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingsResultCard(result: SearchResult.SettingsResult, onConfigure: (() -> Unit)? = null) {
    val context = LocalContext.current
    ListItem(
        headlineContent   = { Text(result.title) },
        supportingContent = { Text(result.subtitle, style = MaterialTheme.typography.bodySmall) },
        leadingContent    = {
            Box(Modifier.size(40.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Icon(
                    imageVector        = Icons.Default.Settings,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.primary
                )
            }
        },
        colors   = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onLongClick = onConfigure) {
                context.startActivity(
                    android.content.Intent(context, com.hermes.deck.ui.settings.SettingsActivity::class.java).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra("section", result.section)
                    }
                )
            }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SystemSettingsResultCard(result: SearchResult.SystemSettingsResult, onConfigure: (() -> Unit)? = null) {
    val context = LocalContext.current
    ListItem(
        headlineContent   = { Text(result.title) },
        supportingContent = { Text(result.subtitle, style = MaterialTheme.typography.bodySmall) },
        leadingContent    = {
            Box(Modifier.size(40.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Icon(
                    imageVector        = Icons.Default.Settings,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.secondary
                )
            }
        },
        colors   = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onLongClick = onConfigure) {
                context.startActivity(
                    android.content.Intent(result.action)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BrowserHistoryResultCard(result: SearchResult.BrowserHistoryResult, onDismiss: () -> Unit, onResultSelected: ((SearchResult) -> Unit)? = null, onConfigure: (() -> Unit)? = null) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    ListItem(
        headlineContent   = {
            Text(
                text       = result.title.ifBlank { result.url },
                fontWeight = FontWeight.SemiBold,
                maxLines   = 1,
                overflow   = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text     = result.url,
                style    = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        },
        leadingContent    = {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Language, contentDescription = null, tint = primary)
            }
        },
        colors   = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.combinedClickable(onLongClick = onConfigure) {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(result.url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            onResultSelected?.invoke(result)
            onDismiss()
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileResultCard(result: SearchResult.FileResult, onDismiss: () -> Unit, onConfigure: (() -> Unit)? = null) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val icon = when {
        result.mimeType?.startsWith("image/") == true   -> Icons.Default.Image
        result.mimeType?.startsWith("video/") == true   -> Icons.Default.Movie
        result.mimeType?.startsWith("audio/") == true   -> Icons.Default.MusicNote
        result.mimeType?.startsWith("text/") == true    -> Icons.Default.Description
        result.mimeType == "application/pdf"             -> Icons.Default.Description
        else                                             -> Icons.Default.InsertDriveFile
    }
    ListItem(
        headlineContent   = {
            Text(
                text       = result.name,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 1,
                overflow   = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text     = result.path,
                style    = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        },
        leadingContent    = {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = primary)
            }
        },
        colors   = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.combinedClickable(onLongClick = onConfigure) {
            try {
                val file = java.io.File(result.path)
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                context.startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, result.mimeType ?: "*/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            } catch (_: Exception) { }
            onDismiss()
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BrowserSuggestionResultCard(result: SearchResult.BrowserSuggestionResult, onDismiss: () -> Unit, onConfigure: (() -> Unit)? = null) {
    val context = LocalContext.current
    ListItem(
        headlineContent = { Text(result.suggestion) },
        leadingContent  = {
            Icon(
                imageVector        = Icons.Default.Search,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        colors   = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.combinedClickable(onLongClick = onConfigure) {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com/?q=${Uri.encode(result.suggestion)}"))
                        .setPackage("com.hermes.browser")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            onDismiss()
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WidgetPickerCard(result: SearchResult.WidgetPickerResult, retryKey: Any = Unit) {
    val provider    = result.providers.firstOrNull() ?: return
    val comp        = provider.componentName
    val context     = LocalContext.current
    val pinRepo     = remember { WidgetPinRepository(context) }
    // Recent (deserialized) widget results carry no appWidgetId; resolve the bound one by
    // component so a pinned widget renders live instead of the "long-press to activate" placeholder.
    val appWidgetId = result.appWidgetId ?: remember(comp) { pinRepo.getPinnedWidgetIdByComponent(comp) }
    val haptic = LocalHapticFeedback.current
    var showMenu     by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    // Fetch AppWidgetProviderInfo to check for configure activity
    val providerInfo by produceState<AppWidgetProviderInfo?>(null, comp) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val cn = ComponentName.unflattenFromString(comp)!!
                AppWidgetManager.getInstance(context).getInstalledProviders().find { it.provider == cn }
            }.getOrNull()
        }
    }

    // Mirror the persisted settings so changes from WidgetSettingsScreen are reflected
    // without requiring a new search query. SharedPreferences listener triggers recomposition.
    val prefs = remember { context.getSharedPreferences("deck_prefs", android.content.Context.MODE_PRIVATE) }
    var customHeightDp  by remember { mutableStateOf<Int?>(pinRepo.getCustomHeightByComponent(comp)) }
    var backgroundStyle by remember { mutableStateOf(pinRepo.getBackgroundStyleByComponent(comp)) }
    val compKey = remember(comp) { comp.replace('/', '_').replace('.', '_') }
    DisposableEffect(comp) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            when (key) {
                "wc_h_$compKey" -> {
                    val h = sp.getInt(key, -1)
                    customHeightDp = if (h == -1) null else h
                }
                "wc_bg_$compKey" ->
                    backgroundStyle = sp.getString(key, "default") ?: "default"
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    Box {
        if (appWidgetId != null) {
            val widgetHost = remember { AppWidgetHost(context.applicationContext, 1027) }
            DisposableEffect(Unit) {
                widgetHost.startListening()
                onDispose { widgetHost.stopListening() }
            }
            LiveWidgetCard(
                provider        = provider,
                appWidgetId     = appWidgetId,
                widgetHost      = widgetHost,
                customHeightDp  = customHeightDp,
                backgroundStyle = backgroundStyle,
                modifier        = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
            // Transparent overlay so long-press is captured before the
            // AndroidView widget (e.g. Maps) can steal the touch event.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showMenu = true
                        }
                    )
            )
        } else {
            Box(
                modifier         = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .height(120.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(16.dp)
                    )
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showMenu = true
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text      = "Long-press this app in the drawer to activate the widget",
                    style     = MaterialTheme.typography.bodySmall,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier  = Modifier.padding(16.dp)
                )
            }
        }
        AppContextMenu(
            expanded         = showMenu,
            onDismissRequest = { showMenu = false },
            hasShortcuts     = false,
            shortcuts        = {},
            actions          = {
            AppContextMenuItem("App info", Icons.Default.Info) {
                showMenu = false
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", result.appPackage, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
            AppContextMenuItem("Search Configuration", Icons.Default.Search) {
                showMenu = false
                openSearchProviderSettings(context, "widgets")
            }
            if (appWidgetId != null) {
                AppContextMenuItem("Widget settings", Icons.Default.Settings) {
                    showMenu = false
                    showSettings = true
                }
            }
            val configActivity = providerInfo?.configure
            val widgetId = appWidgetId
            if (configActivity != null && widgetId != null) {
                AppContextMenuItem("Configure widget", Icons.Default.Settings) {
                    showMenu = false
                    runCatching {
                        context.startActivity(
                            Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                                component = configActivity
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    }
                }
            }
            }
        )

    }

    if (showSettings && appWidgetId != null) {
        Dialog(
            onDismissRequest = { showSettings = false },
            properties = DialogProperties(
                usePlatformDefaultWidth  = false,
                decorFitsSystemWindows   = false
            )
        ) {
            WidgetSettingsScreen(
                packageName = result.appPackage,
                appLabel    = result.appLabel,
                appWidgetId = appWidgetId,
                provider    = provider,
                onDismiss   = { showSettings = false }
            )
        }
    }
}

@Composable
private fun LiveWidgetCard(
    provider: WidgetProviderInfo,
    appWidgetId: Int,
    widgetHost: AppWidgetHost,
    customHeightDp: Int? = null,
    backgroundStyle: String = "default",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val providerInfo by produceState<AppWidgetProviderInfo?>(null, provider.componentName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val cn = ComponentName.unflattenFromString(provider.componentName)!!
                AppWidgetManager.getInstance(context).getInstalledProviders().find { it.provider == cn }
            }.getOrNull()
        }
    }
    val info = providerInfo
    val widgetHeight = (customHeightDp ?: info?.minHeight ?: provider.minHeightDp).dp.coerceAtLeast(80.dp)
    if (info != null) {
        val bgColor = if (backgroundStyle == "transparent") Color.Transparent
                      else MaterialTheme.colorScheme.surfaceContainerHighest
        // BoxWithConstraints gives us the actual dp size so we can notify adaptive widgets
        // of their container dimensions via updateAppWidgetOptions.
        androidx.compose.foundation.layout.BoxWithConstraints(
            modifier = modifier
                .height(widgetHeight)
                .clip(RoundedCornerShape(16.dp))
                .background(bgColor)
        ) {
            val widthDp     = maxWidth.value.toInt()
            val heightDpInt = widgetHeight.value.toInt()
            // key(appWidgetId) forces AndroidView recreation when the widget changes.
            // Without it, AndroidView's factory (called only once) would keep showing the
            // previous widget because produceState doesn't reset to null on key change.
            key(appWidgetId) {
                AndroidView(
                    factory  = { ctx ->
                        runCatching {
                            val view = widgetHost.createView(ctx, appWidgetId, info)
                            view.layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            (view.parent as? android.view.ViewGroup)?.removeView(view)
                            view
                        }.getOrElse { android.view.View(ctx) }
                    },
                    update   = { view ->
                        view.setPadding(0, 0, 0, 0)
                        // Tell the widget provider its current container size so adaptive
                        // widgets (Maps, etc.) can re-layout to fit.
                        AppWidgetManager.getInstance(context).updateAppWidgetOptions(
                            appWidgetId,
                            android.os.Bundle().apply {
                                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,  widthDp)
                                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,  widthDp)
                                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDpInt)
                                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDpInt)
                            }
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    } else {
        Box(
            modifier = modifier
                .height(widgetHeight)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        )
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
            RoundedCornerShape(16.dp)
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


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun WidgetSettingsScreen(
    packageName: String,
    appLabel: String,
    appWidgetId: Int?,
    provider: WidgetProviderInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val pinRepo = remember { WidgetPinRepository(context) }
    val comp = provider.componentName

    val providerInfo by produceState<AppWidgetProviderInfo?>(null, comp) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val cn = ComponentName.unflattenFromString(comp)!!
                AppWidgetManager.getInstance(context).getInstalledProviders().find { it.provider == cn }
            }.getOrNull()
        }
    }

    var heightDp   by remember { mutableIntStateOf(pinRepo.getCustomHeightByComponent(comp) ?: 80) }
    var bgStyle    by remember { mutableStateOf(pinRepo.getBackgroundStyleByComponent(comp)) }
    var showChange by remember { mutableStateOf(false) }

    LaunchedEffect(providerInfo) {
        val info = providerInfo ?: return@LaunchedEffect
        if (pinRepo.getCustomHeightByComponent(comp) == null) {
            heightDp = info.minHeight.coerceIn(80, 600)
        }
    }

    val widgetHost = remember { AppWidgetHost(context.applicationContext, 1027) }
    DisposableEffect(Unit) {
        widgetHost.startListening()
        onDispose { widgetHost.stopListening() }
    }

    fun save() {
        pinRepo.setCustomHeightByComponent(comp, heightDp)
        pinRepo.setBackgroundStyleByComponent(comp, bgStyle)
    }

    BackHandler { save(); onDismiss() }
    Scaffold(
        topBar = {
            TopAppBar(
                title          = { Text(appLabel) },
                navigationIcon = {
                    IconButton(onClick = { save(); onDismiss() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier            = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
                    // Live preview — updates as height/bg sliders change
                    if (appWidgetId != null) {
                        LiveWidgetCard(
                            provider        = provider,
                            appWidgetId     = appWidgetId,
                            widgetHost      = widgetHost,
                            customHeightDp  = heightDp,
                            backgroundStyle = bgStyle,
                            modifier        = Modifier.fillMaxWidth()
                        )
                    } else {
                        StaticWidgetPreview(
                            provider = provider,
                            modifier = Modifier.fillMaxWidth().height(120.dp)
                        )
                    }

                    // Height slider
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Height: ${heightDp}dp",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value         = heightDp.toFloat(),
                            onValueChange = { heightDp = it.roundToInt() },
                            valueRange    = 80f..600f
                        )
                    }

                    // Background selector
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Background",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        listOf("default" to "Default", "transparent" to "Transparent").forEach { (value, label) ->
                            Row(
                                modifier          = Modifier
                                    .fillMaxWidth()
                                    .clickable { bgStyle = value }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = bgStyle == value, onClick = { bgStyle = value })
                                Text(label, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }

                    // Tags (keyed by component name so each widget provider has independent tags)
                    val tagRepo = remember { TagRepository(context) }
                    var tags          by remember { mutableStateOf(tagRepo.getTags(comp)) }
                    var showTagEditor by remember { mutableStateOf(false) }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Tags",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (tags.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement   = Arrangement.spacedBy(4.dp)
                            ) {
                                tags.sorted().forEach { tag ->
                                    InputChip(
                                        selected = false,
                                        onClick  = {},
                                        label    = { Text(tag) }
                                    )
                                }
                            }
                        }
                        OutlinedButton(
                            onClick  = { showTagEditor = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (tags.isEmpty()) "Add Tags" else "Edit Tags") }
                    }
                    if (showTagEditor) {
                        TagEditorDialog(
                            packageName = comp,
                            title       = appLabel,
                            onDismiss   = {
                                showTagEditor = false
                                tags = tagRepo.getTags(comp)
                            }
                        )
                    }

                    // Native configure — only shown if the widget declares a config activity
                    val info = providerInfo
                    if (info?.configure != null && appWidgetId != null) {
                        OutlinedButton(
                            onClick  = {
                                runCatching {
                                    context.startActivity(
                                        Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                                            component = info.configure
                                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Configure Widget")
                        }
                    }

                    // Remove / swap
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick  = {
                                appWidgetId?.let { runCatching { widgetHost.deleteAppWidgetId(it) } }
                                pinRepo.unpinWidgetByComponent(comp)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            colors   = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) { Text("Remove") }
                        OutlinedButton(
                            onClick  = { showChange = true },
                            modifier = Modifier.weight(1f)
                        ) { Text("Swap Widget") }
                    }
        }
    }

    if (showChange) {
        WidgetPickerDialog(
            packageName = packageName,
            appLabel    = appLabel,
            onDismiss   = {
                showChange = false
                save()
                onDismiss()
            }
        )
    }
}

@Composable
private fun WidgetPickerDialog(packageName: String, appLabel: String, onDismiss: () -> Unit) {
    val context  = LocalContext.current
    val pinRepo  = remember { WidgetPinRepository(context) }
    val pm       = context.packageManager

    val widgetProviders by produceState<List<AppWidgetProviderInfo>>(emptyList(), packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                AppWidgetManager.getInstance(context)
                    .getInstalledProviders()
                    .filter { it.provider.packageName == packageName }
            }.getOrDefault(emptyList())
        }
    }

    var selectedComp by remember { mutableStateOf<String?>(null) }
    var pendingId    by remember { mutableIntStateOf(-1) }
    var pendingComp  by remember { mutableStateOf<String?>(null) }
    val widgetHost   = remember { AppWidgetHost(context.applicationContext, 1027) }

    val bindLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        val id   = pendingId
        val comp = pendingComp
        if (id != -1 && comp != null) {
            // Don't trust resultCode — some OEMs return RESULT_CANCELED even on success.
            val manager2 = AppWidgetManager.getInstance(context)
            val bound = manager2.getAppWidgetInfo(id) != null
            if (bound) {
                pinRepo.pinWidgetByComponent(comp, id)
                val info2 = manager2.getAppWidgetInfo(id)
                if (info2 != null) {
                    val configComp = info2.configure
                    if (configComp != null) {
                        runCatching {
                            context.startActivity(
                                Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                                    component = configComp
                                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        }
                    } else {
                        context.sendBroadcast(
                            Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
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
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text("Widget for $appLabel") },
        text             = {
            LazyColumn {
                item {
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .clickable { selectedComp = null }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedComp == null, onClick = { selectedComp = null })
                        Text("None", modifier = Modifier.padding(start = 8.dp))
                    }
                }
                items(widgetProviders) { info ->
                    val label = runCatching { info.loadLabel(pm) }.getOrDefault("Widget")
                    val comp  = info.provider.flattenToString()
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .clickable { selectedComp = comp }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedComp == comp, onClick = { selectedComp = comp })
                        Text(label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val comp = selectedComp
                if (comp == null) {
                    onDismiss()
                } else {
                    val newId     = widgetHost.allocateAppWidgetId()
                    val component = ComponentName.unflattenFromString(comp)!!
                    val manager = AppWidgetManager.getInstance(context)
                    val silentBound = manager.bindAppWidgetIdIfAllowed(newId, component)
                    if (silentBound) {
                        pinRepo.pinWidgetByComponent(comp, newId)
                        val info = manager.getAppWidgetInfo(newId)
                        if (info != null) {
                            val configComp = info.configure
                            if (configComp != null) {
                                runCatching {
                                    context.startActivity(
                                        Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                                            this.component = configComp
                                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, newId)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                }
                            } else {
                                context.sendBroadcast(
                                    Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                                        setComponent(info.provider)
                                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(newId))
                                    }
                                )
                            }
                        }
                        onDismiss()
                    } else {
                        pendingId   = newId
                        pendingComp = comp
                        val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, newId)
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, component)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        bindLauncher.launch(bindIntent)
                    }
                }
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun formatPhoneNumber(digits: String): String = when (digits.length) {
    10   -> "(${digits.substring(0, 3)}) ${digits.substring(3, 6)}-${digits.substring(6)}"
    7    -> "${digits.substring(0, 3)}-${digits.substring(3)}"
    else -> digits
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WidgetManagementScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val pinRepo = remember { WidgetPinRepository(context) }
    val pm      = context.packageManager

    fun loadPinnedComps(): List<String> =
        AppWidgetManager.getInstance(context).getInstalledProviders()
            .map { it.provider.flattenToString() }
            .filter { pinRepo.isPinnedByComponent(it) }

    var pinnedComps  by remember { mutableStateOf(loadPinnedComps()) }
    var settingsComp by remember { mutableStateOf<String?>(null) }
    var showAddWidget by remember { mutableStateOf(false) }
    // Which widget previews are expanded. Empty = all collapsed (compact list).
    var expandedComps by remember { mutableStateOf(setOf<String>()) }

    val widgetHost = remember { AppWidgetHost(context.applicationContext, 1027) }
    DisposableEffect(Unit) {
        widgetHost.startListening()
        onDispose { widgetHost.stopListening() }
    }

    BackHandler(onBack = onDismiss)
    Scaffold(
        topBar = {
            TopAppBar(
                title          = { Text("Widgets") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddWidget = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add widget")
            }
        }
    ) { padding ->
        if (pinnedComps.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No widgets configured.\nLong-press an app in the drawer to add one.",
                    style     = MaterialTheme.typography.bodyMedium,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier  = Modifier.padding(32.dp)
                )
            }
        } else {
            LazyColumn(
                contentPadding      = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier            = Modifier.padding(padding).fillMaxSize()
            ) {
                items(pinnedComps, key = { it }) { comp ->
                    val cn  = remember(comp) { ComponentName.unflattenFromString(comp) }
                    val pkg = cn?.packageName ?: comp
                    val appLabel = remember(pkg) {
                        runCatching {
                            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                        }.getOrDefault(pkg)
                    }
                    val providerInfo = remember(comp) {
                        runCatching {
                            AppWidgetManager.getInstance(context).getInstalledProviders()
                                .find { it.provider == cn }
                        }.getOrNull()
                    }
                    val widgetId = remember(comp) { pinRepo.getPinnedWidgetIdByComponent(comp) }
                    val expanded = comp in expandedComps
                    Surface(
                        shape    = RoundedCornerShape(16.dp),
                        color    = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(bottom = 8.dp)) {
                            // Tappable header: collapse/expand the preview. Settings lives here
                            // too, so it's reachable without expanding (or scrolling) the widget.
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedComps = if (expanded) expandedComps - comp else expandedComps + comp
                                    }
                                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                            ) {
                                Text(
                                    text     = appLabel,
                                    style    = MaterialTheme.typography.labelMedium,
                                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { settingsComp = comp }) { Text("Settings") }
                                Icon(
                                    imageVector        = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (expanded) "Collapse widget" else "Expand widget"
                                )
                            }
                            AnimatedVisibility(visible = expanded) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    if (providerInfo != null && widgetId != null) {
                                        val wInfo = WidgetProviderInfo(
                                            componentName = comp,
                                            label         = runCatching { providerInfo.loadLabel(pm) }.getOrDefault("Widget"),
                                            packageName   = pkg,
                                            previewResId  = providerInfo.previewImage,
                                            iconResId     = providerInfo.icon,
                                            minHeightDp   = providerInfo.minHeight.coerceAtLeast(80)
                                        )
                                        LiveWidgetCard(
                                            provider        = wInfo,
                                            appWidgetId     = widgetId,
                                            widgetHost      = widgetHost,
                                            customHeightDp  = pinRepo.getCustomHeightByComponent(comp),
                                            backgroundStyle = pinRepo.getBackgroundStyleByComponent(comp),
                                            modifier        = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                        )
                                    } else if (widgetId == null) {
                                        Text(
                                            "Not yet activated. Long-press the app in the drawer.",
                                            style    = MaterialTheme.typography.bodySmall,
                                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    settingsComp?.let { comp ->
        val cn  = ComponentName.unflattenFromString(comp) ?: return@let
        val pkg = cn.packageName
        val appLabel = runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
        val pInfo = runCatching {
            AppWidgetManager.getInstance(context).getInstalledProviders().find { it.provider == cn }
        }.getOrNull() ?: return@let
        val wInfo = WidgetProviderInfo(
            componentName = comp,
            label         = runCatching { pInfo.loadLabel(pm) }.getOrDefault("Widget"),
            packageName   = pkg,
            previewResId  = pInfo.previewImage,
            iconResId     = pInfo.icon,
            minHeightDp   = pInfo.minHeight.coerceAtLeast(80)
        )
        WidgetSettingsScreen(
            packageName = pkg,
            appLabel    = appLabel,
            appWidgetId = pinRepo.getPinnedWidgetIdByComponent(comp),
            provider    = wInfo,
            onDismiss   = {
                settingsComp = null
                pinnedComps  = loadPinnedComps()
            }
        )
    }

    if (showAddWidget) {
        AddWidgetPicker(
            onDismiss = {
                showAddWidget = false
                pinnedComps  = loadPinnedComps()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddWidgetPicker(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val pinRepo = remember { WidgetPinRepository(context) }

    var selectedPkg by remember { mutableStateOf<Pair<String, String>?>(null) } // packageName to appLabel

    val appsWithWidgets by produceState<List<Pair<String, String>>>(emptyList()) {
        value = withContext(Dispatchers.IO) {
            val manager = AppWidgetManager.getInstance(context)
            val pm = context.packageManager
            runCatching {
                manager.getInstalledProviders()
                    .groupBy { it.provider.packageName }
                    .mapNotNull { (pkg, _) ->
                        runCatching {
                            val label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                            label to pkg
                        }.getOrNull()
                    }
                    .sortedBy { it.first.lowercase() }
            }.getOrElse { emptyList() }
        }
    }

    BackHandler(onBack = onDismiss)
    Scaffold(
        topBar = {
            TopAppBar(
                title          = { Text("Add Widget") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (appsWithWidgets.isEmpty()) {
            Box(
                modifier         = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier       = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(appsWithWidgets, key = { it.second }) { (label, pkg) ->
                    val iconBitmap by produceState<android.graphics.Bitmap?>(null, pkg) {
                        value = withContext(Dispatchers.IO) {
                            runCatching {
                                val d = context.packageManager.getApplicationIcon(pkg)
                                val w = d.intrinsicWidth.coerceIn(1, 96)
                                val h = d.intrinsicHeight.coerceIn(1, 96)
                                android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888).also { bmp ->
                                    d.setBounds(0, 0, w, h)
                                    d.draw(android.graphics.Canvas(bmp))
                                }
                            }.getOrNull()
                        }
                    }
                    ListItem(
                        headlineContent = { Text(label) },
                        leadingContent  = {
                            if (iconBitmap != null) {
                                androidx.compose.foundation.Image(
                                    bitmap             = iconBitmap!!.asImageBitmap(),
                                    contentDescription = null,
                                    modifier           = Modifier.size(40.dp)
                                )
                            } else {
                                Spacer(Modifier.size(40.dp))
                            }
                        },
                        modifier        = Modifier
                            .fillMaxWidth()
                            .clickable { selectedPkg = label to pkg }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    selectedPkg?.let { (label, pkg) ->
        WidgetPickerDialog(
            packageName = pkg,
            appLabel    = label,
            onDismiss   = {
                selectedPkg = null
                onDismiss()
            }
        )
    }
}

/**
 * Auto-searching Google Maps card. Runs a Places Text Search (biased to the device location) BY
 * DEFAULT whenever it appears for a query — no tap needed — renders a Static Maps thumbnail with
 * numbered pins, and lists the matches (count capped by the provider's "Result limit" slider).
 * Tapping a row opens that place in Google Maps; tapping the header/map opens the search there.
 * Self-contained — no VM/persisted state, unlike the Claude/Hermes chat cards.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlacesResultCard(result: SearchResult.PlacesResult, onConfigure: (() -> Unit)? = null) {
    val context = LocalContext.current
    // The provider already searched + geo-filtered (and only emits this card when there are real places),
    // so just render result.places and fetch the static map for them — no search/loading state here.
    val loading = false
    val error: String? = null
    val places: List<PlacesClient.Place>? = result.places
    var mapBitmap by remember(result.query) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(result.query) {
        mapBitmap = null
        if (result.places.isNotEmpty()) {
            PlacesClient.staticMapUrl(context, result.places, 600, 300)?.let { mapBitmap = PlacesClient.fetchBitmap(it) }
        }
    }

    Column {
        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Finding places nearby…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        error?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        val pl = places
        if (pl != null) {
            mapBitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Map of nearby results",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .combinedClickable(onLongClick = onConfigure) { openMapsSearch(context, result.query) }
                )
            }
            if (pl.isEmpty()) {
                Text("No places found", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            pl.take(9).forEachIndexed { i, p ->
                ListItem(
                    headlineContent   = { Text("${i + 1}. ${p.name}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        val bits = buildList {
                            if (p.address.isNotBlank()) add(p.address)
                            p.rating?.let { add("★ %.1f".format(it)) }
                            p.openNow?.let { add(if (it) "Open" else "Closed") }
                        }
                        if (bits.isNotEmpty()) Text(bits.joinToString(" · "), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    colors   = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { openMapsPlace(context, p) }
                )
            }
        }
    }
}

private fun openMapsSearch(context: android.content.Context, query: String) {
    val opened = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess
    if (!opened) runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(query)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun openMapsPlace(context: android.content.Context, place: PlacesClient.Place) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:${place.lat},${place.lng}?q=" + Uri.encode("${place.name}, ${place.address}")))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/**
 * Wikipedia card. [rich]=true (the confident single match) renders a summary: thumbnail + title +
 * short description + a capped (4-line) extract. rich=false renders a plain row (title + snippet).
 * Either taps through to the article in the browser.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WikipediaResultCard(result: SearchResult.WikipediaResult, onConfigure: (() -> Unit)? = null) {
    val context = LocalContext.current
    if (result.rich) {
        val thumb by produceState<android.graphics.Bitmap?>(null, result.thumbnailUrl) {
            value = result.thumbnailUrl?.let { WikipediaClient.fetchBitmap(it) }
        }
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onLongClick = onConfigure) { openUrl(context, result.url) }
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            val t = thumb
            Box(
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (t != null) Image(t.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(Icons.Default.Article, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(result.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                result.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                result.extract?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
                Text("Wikipedia", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
            }
        }
    } else {
        val desc = result.description?.takeIf { it.isNotBlank() }
        ListItem(
            headlineContent   = { Text(result.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = if (desc != null) ({
                Text(desc, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }) else null,
            leadingContent    = { Icon(Icons.Default.Article, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            colors   = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.combinedClickable(onLongClick = onConfigure) { openUrl(context, result.url) }
        )
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Shared card for the conversion/lookup "answer" providers (unit / currency / timezone / translation):
 *  a prominent value + an input echo; tap copies the value to the clipboard. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AnswerResultCard(result: SearchResult.AnswerResult, onConfigure: (() -> Unit)? = null) {
    val context = LocalContext.current
    ListItem(
        headlineContent   = { Text(result.value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = result.detail?.let { d -> { Text(d, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        leadingContent    = { Icon(answerIcon(result.providerId), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent   = { Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) },
        colors   = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.combinedClickable(onLongClick = onConfigure) { copyToClipboard(context, result.copyText) }
    )
}

private fun answerIcon(providerId: String): ImageVector = when (providerId) {
    "unit"        -> Icons.Default.SwapHoriz
    "currency"    -> Icons.Default.AttachMoney
    "timezone"    -> Icons.Default.Schedule
    "translation" -> Icons.Default.Translate
    else          -> Icons.Default.Calculate
}

private fun copyToClipboard(context: android.content.Context, text: String) {
    (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager)
        ?.setPrimaryClip(android.content.ClipData.newPlainText("Deck", text))
    android.widget.Toast.makeText(context, "Copied", android.widget.Toast.LENGTH_SHORT).show()
}

/** Tap-to-search Gmail card: idle until tapped, then runs the IMAP inbox search and lists messages.
 *  Tapping the card or a row opens the Gmail app (IMAP has no per-message deep link). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GmailResultCard(result: SearchResult.GmailResult, onConfigure: (() -> Unit)? = null) {
    val context = LocalContext.current
    Column {
        // No "Search Gmail for …" header row — the "Gmail" group label + the messages are enough.
        result.mails.forEach { mail ->
            ListItem(
                headlineContent   = { Text(mail.subject, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    val date = if (mail.date > 0) " · " + android.text.format.DateUtils.getRelativeTimeSpanString(mail.date) else ""
                    Text(mail.from + date, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                leadingContent    = { Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) },
                colors   = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.combinedClickable(onLongClick = onConfigure) { openGmailApp(context) }
            )
        }
    }
}

private fun openGmailApp(context: android.content.Context) {
    val opened = context.packageManager.getLaunchIntentForPackage("com.google.android.gm")
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { runCatching { context.startActivity(it) }.isSuccess } ?: false
    if (!opened) runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://mail.google.com/")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** A YouTube video result: 16:9 thumbnail + title + channel; tap opens the video in the YouTube app. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun YouTubeResultCard(result: SearchResult.YouTubeResult, onDismiss: () -> Unit, onConfigure: (() -> Unit)? = null) {
    val context = LocalContext.current
    val thumb by produceState<android.graphics.Bitmap?>(null, result.thumbnailUrl) {
        value = result.thumbnailUrl?.let { YouTubeClient.fetchBitmap(it) }
    }
    ListItem(
        headlineContent   = { Text(result.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(result.channel, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent    = {
            Box(
                modifier = Modifier.size(width = 88.dp, height = 50.dp).clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val b = thumb
                if (b != null) Image(b.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        colors   = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.combinedClickable(onLongClick = onConfigure) {
            openYouTube(context, result.videoId)
            onDismiss()
        }
    )
}

/** Open a video in the YouTube app (vnd.youtube:<id>), falling back to the watch URL in a browser. */
private fun openYouTube(context: android.content.Context, videoId: String) {
    val app = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(app) }.isFailure) runCatching { context.startActivity(web) }
}

/** A timer/alarm action card. Tap fires the standard AlarmClock intent. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimerResultCard(result: SearchResult.TimerResult, onDismiss: () -> Unit, onConfigure: (() -> Unit)? = null) {
    val context = LocalContext.current
    ListItem(
        headlineContent   = { Text(result.displayText, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(if (result.isAlarm) "Tap to set the alarm" else "Tap to start the timer", style = MaterialTheme.typography.labelSmall) },
        leadingContent    = { Icon(if (result.isAlarm) Icons.Default.Alarm else Icons.Default.Timer, contentDescription = null) },
        colors            = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier          = Modifier.combinedClickable(onLongClick = onConfigure) {
            fireTimer(context, result)
            onDismiss()
        }
    )
}

/** WMO weather code → a monochrome OUTLINED Material icon (line-drawing style), tinted by the caller. */
private fun weatherIcon(code: Int): ImageVector = when {
    code == 0 || code == 1 -> Icons.Outlined.WbSunny
    code in 95..99 -> Icons.Outlined.Thunderstorm
    code in 71..77 || code == 85 || code == 86 -> Icons.Outlined.AcUnit   // snow
    code in 51..82 -> Icons.Outlined.Grain                                // drizzle / rain / showers
    else -> Icons.Outlined.Cloud                                          // partly cloudy / overcast / fog
}

/** Current conditions + a Daily/Hourly forecast (Open-Meteo). Informational (no tap action). */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun WeatherResultCard(result: SearchResult.WeatherResult, onConfigure: (() -> Unit)? = null) {
    var hourly by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth()
            .then(if (onConfigure != null) Modifier.combinedClickable(onLongClick = onConfigure) {} else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(weatherIcon(result.currentCode), contentDescription = WeatherClient.describe(result.currentCode),
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${result.currentTempF}°", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(WeatherClient.describe(result.currentCode), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(result.location, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
        }
        if (result.days.isNotEmpty() || result.hours.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !hourly,
                    onClick = { hourly = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("Daily") }
                SegmentedButton(
                    selected = hourly,
                    onClick = { hourly = true },
                    enabled = result.hours.isNotEmpty(),
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("Hourly") }
            }
            Spacer(Modifier.height(10.dp))
            if (hourly) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    result.hours.forEach { h ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(h.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            Icon(weatherIcon(h.code), contentDescription = WeatherClient.describe(h.code),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp).padding(vertical = 3.dp))
                            Text("${h.tempF}°", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    result.days.take(5).forEach { d ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(d.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Icon(weatherIcon(d.code), contentDescription = WeatherClient.describe(d.code),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp).padding(vertical = 3.dp))
                            Text("${d.hiF}°", style = MaterialTheme.typography.labelLarge)
                            Text("${d.loF}°", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** A dictionary entry: headword + phonetic + definitions grouped by part of speech. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DictionaryResultCard(result: SearchResult.DictionaryResult, onConfigure: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth()
            .then(if (onConfigure != null) Modifier.combinedClickable(onLongClick = onConfigure) {} else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(result.word, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            result.phonetic?.let {
                Spacer(Modifier.width(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        result.entries.forEach { e ->
            Spacer(Modifier.height(8.dp))
            if (e.partOfSpeech.isNotBlank()) Text(
                e.partOfSpeech,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            e.definitions.forEachIndexed { i, d ->
                Text(
                    "${i + 1}. $d",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }
    }
}

/** A Todoist task with a checkbox — checking it completes the task in place (optimistic: tick now,
 *  call the API, untick on failure). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TodoTaskCard(result: SearchResult.TodoTaskResult, onConfigure: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var done by remember(result.id) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth()
            .then(if (onConfigure != null) Modifier.combinedClickable(onLongClick = onConfigure) {} else Modifier)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = done,
            onCheckedChange = { checked ->
                if (checked && !done) {
                    done = true
                    scope.launch { if (!TodoistClient.complete(context, result.id)) done = false }   // revert on failure
                }
            }
        )
        Text(
            result.content,
            style = MaterialTheme.typography.bodyMedium,
            textDecoration = if (done) TextDecoration.LineThrough else null,
            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/** "Add '<query>' to Todoist" — quick task capture from the search bar. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TodoAddCard(result: SearchResult.TodoAddResult, onConfigure: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember(result.query) { mutableStateOf<String?>(null) }
    ListItem(
        headlineContent = { Text(status ?: "Add \"${result.query}\" to Todoist", fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        leadingContent  = { Icon(Icons.Default.Add, contentDescription = null) },
        colors          = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier        = Modifier.combinedClickable(onLongClick = onConfigure) {
            if (status == null || status == "Failed — tap to retry") {
                status = "Adding…"
                scope.launch {
                    val r = TodoistClient.add(context, result.query)
                    status = if (r.isSuccess) "Added ✓" else "Failed — tap to retry"
                }
            }
        }
    )
}

/** A movie/TV title missing from Plex, with an explicit "Add to Radarr/Sonarr" button (an add starts
 *  a download, so it's a deliberate button — not a whole-card tap). Idle → Adding… → Added ✓. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AddMediaCard(result: SearchResult.AddMediaResult, onConfigure: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val poster by produceState<android.graphics.Bitmap?>(null, result.posterUrl) {
        value = result.posterUrl?.let { ArrClient.fetchPoster(it) }
    }
    var status by remember(result.title, result.year) { mutableStateOf<String?>(null) }
    val label = if (result.service == "sonarr") "Add to Sonarr" else "Add to Radarr"
    Column(
        Modifier.fillMaxWidth()
            .then(if (onConfigure != null) Modifier.combinedClickable(onLongClick = onConfigure) {} else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.width(56.dp).height(84.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val b = poster
                if (b != null) Image(b.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(Icons.Default.Movie, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(result.title + (result.year?.let { " ($it)" } ?: ""), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (result.overview.isNotBlank()) Text(result.overview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(10.dp))
        ContactActionButton(Icons.Default.Add, status ?: label, Modifier.fillMaxWidth()) {
            if (status == null || status == "Failed — tap to retry") {
                status = "Adding…"
                scope.launch {
                    val r = ArrClient.add(context, result.service, result.lookupJson)
                    status = if (r.isSuccess) "Added ✓" else "Failed — tap to retry"
                }
            }
        }
    }
}

/** Tap-to-ask the on-device model — fully offline/private/free. Idle → tap → thinking → answer.
 *  Reuses LocalLlmClassifier's single engine + mutex (the model runs only on tap). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OfflineAnswerCard(result: SearchResult.OfflineAnswerResult, onConfigure: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember(result.query) { mutableStateOf(false) }
    var answer by remember(result.query) { mutableStateOf<String?>(null) }
    var asked by remember(result.query) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth()
            .combinedClickable(onLongClick = onConfigure) {
                if (!asked) {
                    asked = true; loading = true
                    scope.launch {
                        val a = LocalLlmClassifier.generate(context,
                            "<|im_start|>system\nYou are a helpful assistant. Answer the question concisely in 1-3 sentences.<|im_end|>\n" +
                            "<|im_start|>user\n${result.query}<|im_end|>\n<|im_start|>assistant\n")
                        answer = a ?: "Couldn't answer — enable on-device AI in Settings → Search and let the model finish downloading."
                        loading = false
                    }
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("On-device AI", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("offline · private", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        when {
            answer != null -> Text(answer!!, style = MaterialTheme.typography.bodyMedium)
            loading        -> Text("Thinking…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else           -> Text("Tap to answer this with the on-device model — no internet, no account.",
                                   style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Fire the AlarmClock intent: a timer starts directly (no clock UI); an alarm opens the clock so it
 *  can be confirmed/adjusted. */
private fun fireTimer(context: android.content.Context, result: SearchResult.TimerResult) {
    val intent = if (result.isAlarm) {
        Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(android.provider.AlarmClock.EXTRA_HOUR, result.hour)
            putExtra(android.provider.AlarmClock.EXTRA_MINUTES, result.minute)
        }
    } else {
        Intent(android.provider.AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(android.provider.AlarmClock.EXTRA_LENGTH, result.seconds)
            putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
            putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, "Deck timer")
        }
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun resultKey(result: SearchResult): Any = when (result) {
    is SearchResult.AppResult          -> "app:${result.app.packageName}"
    is SearchResult.ContactResult      -> "contact:${result.name}"
    is SearchResult.CalculatorResult   -> "calc:${result.expression}"
    is SearchResult.PluginResult       -> "plugin:${result.pluginId}:${result.title}"
    is SearchResult.AiResult           -> "ai:${result.query}"
    is SearchResult.ClaudeResult       -> "claude:${result.query}"
    is SearchResult.HermesResult       -> "hermes:${result.query}"
    is SearchResult.DialerResult       -> "dialer:${result.phoneNumber}"
    is SearchResult.WidgetPickerResult -> "widgets:${result.providers.firstOrNull()?.componentName ?: result.appPackage}"
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
