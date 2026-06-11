package com.hermes.deck.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import kotlin.math.roundToInt
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.hermes.deck.ui.search.providers.LocalLlmClassifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.hermes.deck.data.IconPackRepository
import com.hermes.deck.data.IconShape
import com.hermes.deck.plugin.PluginRepository
import com.hermes.deck.ui.search.WidgetManagementScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.hermes.deck.ui.search.providers.HomeAssistantClient
import com.hermes.deck.ui.search.providers.PlexClient
import com.hermes.deck.ui.search.providers.PlexLibrary
import com.hermes.deck.ui.search.providers.TandoorClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, initialSection: String? = null, initialProvider: String? = null) {
    var showAppearance by remember { mutableStateOf(initialSection == "appearance") }
    var showCards      by remember { mutableStateOf(initialSection == "cards") }
    var showSearch     by remember { mutableStateOf(initialSection == "search") }
    var showAbout      by remember { mutableStateOf(initialSection == "about") }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            ListItem(
                headlineContent   = { Text("Appearance") },
                supportingContent = { Text("Theme, wallpaper, icons") },
                trailingContent   = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                modifier          = Modifier.clickable { showAppearance = true }
            )
            HorizontalDivider()
            ListItem(
                headlineContent   = { Text("Cards & Drawer") },
                supportingContent = { Text("Hidden apps, grid columns, drawer style") },
                trailingContent   = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                modifier          = Modifier.clickable { showCards = true }
            )
            HorizontalDivider()
            ListItem(
                headlineContent   = { Text("Search") },
                supportingContent = { Text("Providers, widgets, number keys, plugins") },
                trailingContent   = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                modifier          = Modifier.clickable { showSearch = true }
            )
            HorizontalDivider()
            ListItem(
                headlineContent   = { Text("About") },
                supportingContent = { Text("Version, reset, clear pins") },
                trailingContent   = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                modifier          = Modifier.clickable { showAbout = true }
            )
        }
    }

    if (showAppearance) {
        AppearanceSettingsScreen(onBack = { showAppearance = false })
    }
    if (showCards) {
        CardsSettingsScreen(onBack = { showCards = false })
    }
    if (showSearch) {
        SearchSettingsScreen(onBack = { showSearch = false }, initialProvider = initialProvider)
    }
    if (showAbout) {
        AboutSettingsScreen(onBack = { showAbout = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE) }

    var themeMode       by remember { mutableStateOf(prefs.getString("theme_mode", "system") ?: "system") }
    var materialYou     by remember { mutableStateOf(prefs.getBoolean("material_you", true)) }
    var wallpaperDim    by remember { mutableFloatStateOf(prefs.getFloat("wallpaper_dim", 0f)) }
    var wallpaperBlur   by remember { mutableFloatStateOf(prefs.getFloat("wallpaper_blur", 0f)) }
    var seedColorArgb   by remember { mutableIntStateOf(prefs.getInt("seed_color", 0)) }
    var selectedIconPack by remember { mutableStateOf(prefs.getString("icon_pack", "") ?: "") }
    var iconShape       by remember { mutableStateOf(
        runCatching { IconShape.valueOf(prefs.getString("icon_shape", "NONE") ?: "NONE") }.getOrDefault(IconShape.NONE)
    ) }
    var showIconPackPicker by remember { mutableStateOf(false) }
    var showShapePicker    by remember { mutableStateOf(false) }

    val iconPackRepo   = remember { IconPackRepository(context) }
    val installedPacks = remember { iconPackRepo.getInstalledPacks() }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            ListItem(
                headlineContent   = { Text("Theme") },
                supportingContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(when (themeMode) {
                            "dark"  -> "Always dark"
                            "light" -> "Always light"
                            else    -> "Follow system"
                        })
                        val themeOptions = listOf("system" to "System", "dark" to "Dark", "light" to "Light")
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            themeOptions.forEachIndexed { index, (value, label) ->
                                SegmentedButton(
                                    selected = themeMode == value,
                                    onClick  = {
                                        themeMode = value
                                        prefs.edit().putString("theme_mode", value).apply()
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = themeOptions.size)
                                ) { Text(label) }
                            }
                        }
                    }
                }
            )
            HorizontalDivider()
            ListItem(
                headlineContent   = { Text("Material You colors") },
                supportingContent = { Text(if (materialYou) "Using wallpaper colors" else "Using default palette") },
                trailingContent   = {
                    Switch(
                        checked         = materialYou,
                        onCheckedChange = { checked ->
                            materialYou = checked
                            prefs.edit().putBoolean("material_you", checked).apply()
                        }
                    )
                }
            )
            if (!materialYou) {
                HorizontalDivider()
                ListItem(
                    headlineContent   = { Text("Accent color") },
                    supportingContent = {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement   = Arrangement.spacedBy(8.dp),
                            modifier              = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp)
                        ) {
                            staticSeedColors.forEach { (name, color) ->
                                val selected = seedColorArgb == color.toArgb()
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.outline, CircleShape) else Modifier)
                                        .clickable {
                                            seedColorArgb = color.toArgb()
                                            prefs.edit().putInt("seed_color", color.toArgb()).apply()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selected) {
                                        Icon(
                                            imageVector        = Icons.Default.Check,
                                            contentDescription = name,
                                            tint               = Color.White,
                                            modifier           = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                )
            }
            HorizontalDivider()
            ListItem(
                headlineContent   = { Text("Wallpaper blur") },
                supportingContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${(wallpaperBlur * 100).toInt()}%")
                        Slider(
                            value          = wallpaperBlur,
                            onValueChange  = {
                                wallpaperBlur = it
                                prefs.edit().putFloat("wallpaper_blur", it).apply()
                            },
                            valueRange     = 0f..1f,
                            modifier       = Modifier.fillMaxWidth()
                        )
                    }
                }
            )
            HorizontalDivider()
            ListItem(
                headlineContent   = { Text("Wallpaper dim") },
                supportingContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${(wallpaperDim * 100).toInt()}%")
                        Slider(
                            value          = wallpaperDim,
                            onValueChange  = {
                                wallpaperDim = it
                                prefs.edit().putFloat("wallpaper_dim", it).apply()
                            },
                            valueRange     = 0f..0.7f,
                            modifier       = Modifier.fillMaxWidth()
                        )
                    }
                }
            )
            HorizontalDivider()
            SettingsItem(
                title    = "Change wallpaper",
                subtitle = "Open system wallpaper picker",
                onClick  = {
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_SET_WALLPAPER)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            )
            HorizontalDivider()
            SettingsItem(
                title    = "Icon pack",
                subtitle = if (selectedIconPack.isBlank()) "None"
                           else installedPacks.firstOrNull { it.packageName == selectedIconPack }?.label ?: selectedIconPack,
                onClick  = { showIconPackPicker = true }
            )
            HorizontalDivider()
            SettingsItem(
                title    = "Icon shape",
                subtitle = iconShape.label,
                onClick  = { showShapePicker = true }
            )
        }
    }

    if (showIconPackPicker) {
        AlertDialog(
            onDismissRequest = { showIconPackPicker = false },
            title = { Text("Icon pack") },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text("None") },
                        leadingContent  = {
                            RadioButton(
                                selected = selectedIconPack.isBlank(),
                                onClick  = {
                                    selectedIconPack = ""
                                    prefs.edit().putString("icon_pack", "").apply()
                                    showIconPackPicker = false
                                }
                            )
                        },
                        modifier = Modifier.clickable {
                            selectedIconPack = ""
                            prefs.edit().putString("icon_pack", "").apply()
                            showIconPackPicker = false
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    installedPacks.forEach { pack ->
                        ListItem(
                            headlineContent = { Text(pack.label) },
                            leadingContent  = {
                                RadioButton(
                                    selected = selectedIconPack == pack.packageName,
                                    onClick  = {
                                        selectedIconPack = pack.packageName
                                        prefs.edit().putString("icon_pack", pack.packageName).apply()
                                        showIconPackPicker = false
                                    }
                                )
                            },
                            modifier = Modifier.clickable {
                                selectedIconPack = pack.packageName
                                prefs.edit().putString("icon_pack", pack.packageName).apply()
                                showIconPackPicker = false
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showShapePicker) {
        AlertDialog(
            onDismissRequest = { showShapePicker = false },
            title = { Text("Icon shape") },
            text = {
                Column {
                    IconShape.entries.forEach { shape ->
                        ListItem(
                            headlineContent = { Text(shape.label) },
                            leadingContent  = {
                                RadioButton(
                                    selected = iconShape == shape,
                                    onClick  = {
                                        iconShape = shape
                                        prefs.edit().putString("icon_shape", shape.name).apply()
                                        showShapePicker = false
                                    }
                                )
                            },
                            modifier = Modifier.clickable {
                                iconShape = shape
                                prefs.edit().putString("icon_shape", shape.name).apply()
                                showShapePicker = false
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardsSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE) }

    var hideSelfCards         by remember { mutableStateOf(prefs.getBoolean("hide_self_from_cards", true)) }
    var hideSystemSettings    by remember { mutableStateOf(prefs.getBoolean("hide_system_settings", true)) }
    var disableRecentsGesture by remember { mutableStateOf(prefs.getBoolean("disable_recents_gesture", false)) }
    var gridColumns           by remember { mutableIntStateOf(prefs.getInt("grid_columns", 4)) }
    var drawerStyle           by remember { mutableStateOf(prefs.getString("drawer_view_mode", "Grid") ?: "Grid") }
    var hiddenApps            by remember {
        mutableStateOf(
            prefs.getString("hidden_apps", "")!!.split(",").filter { it.isNotEmpty() }.toSet()
        )
    }
    var showHiddenApps by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cards & Drawer") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            ListItem(
                headlineContent   = { Text("Hide Deck from cards") },
                supportingContent = { Text(if (hideSelfCards) "Deck won't appear in recent cards" else "Deck appears in recent cards") },
                trailingContent   = {
                    Switch(
                        checked         = hideSelfCards,
                        onCheckedChange = { checked ->
                            hideSelfCards = checked
                            prefs.edit().putBoolean("hide_self_from_cards", checked).apply()
                        }
                    )
                }
            )
            HorizontalDivider()
            ListItem(
                headlineContent   = { Text("Hide Settings from cards") },
                supportingContent = { Text(if (hideSystemSettings) "System Settings won't appear in recent cards" else "System Settings appears in recent cards") },
                trailingContent   = {
                    Switch(
                        checked         = hideSystemSettings,
                        onCheckedChange = { checked ->
                            hideSystemSettings = checked
                            prefs.edit().putBoolean("hide_system_settings", checked).apply()
                        }
                    )
                }
            )
            HorizontalDivider()
            ListItem(
                headlineContent   = { Text("Disable Android recents gesture") },
                supportingContent = { Text(if (disableRecentsGesture) "Switched to 3-button navigation (requires root)" else "Gesture navigation active") },
                trailingContent   = {
                    Switch(
                        checked         = disableRecentsGesture,
                        onCheckedChange = { checked ->
                            disableRecentsGesture = checked
                            prefs.edit().putBoolean("disable_recents_gesture", checked).apply()
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    val su = listOf("/debug_ramdisk/su", "/su/bin/su", "/sbin/su")
                                        .firstOrNull { java.io.File(it).canExecute() } ?: return@withContext
                                    val mode = if (checked) "0" else "2"
                                    // Full path avoids $PATH issues under su; am crash restarts
                                    // SystemUI so the new navigation_mode takes effect immediately.
                                    ProcessBuilder(su, "-c",
                                        "/system/bin/settings put secure navigation_mode $mode && am crash com.android.systemui"
                                    ).start().waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
                                }
                            }
                        }
                    )
                }
            )
            HorizontalDivider()
            ListItem(
                headlineContent   = { Text("App drawer style") },
                supportingContent = {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        listOf("Grid", "List").forEachIndexed { index, style ->
                            SegmentedButton(
                                selected = drawerStyle == style,
                                onClick  = {
                                    drawerStyle = style
                                    prefs.edit().putString("drawer_view_mode", style).apply()
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = 2)
                            ) { Text(style) }
                        }
                    }
                }
            )
            if (drawerStyle == "Grid") {
                HorizontalDivider()
                ListItem(
                    headlineContent   = { Text("Grid columns") },
                    supportingContent = {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            listOf(3, 4, 5).forEachIndexed { index, n ->
                                SegmentedButton(
                                    selected = gridColumns == n,
                                    onClick  = {
                                        gridColumns = n
                                        prefs.edit().putInt("grid_columns", n).apply()
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 3)
                                ) { Text("$n") }
                            }
                        }
                    }
                )
            }
            HorizontalDivider()
            SettingsItem(
                title    = "Hidden apps",
                subtitle = if (hiddenApps.isEmpty()) "None hidden"
                           else "${hiddenApps.size} app${if (hiddenApps.size == 1) "" else "s"} hidden",
                onClick  = { showHiddenApps = true }
            )
        }
    }

    if (showHiddenApps) {
        HiddenAppsScreen(
            hiddenApps = hiddenApps,
            onSave     = { updated ->
                hiddenApps = updated
                prefs.edit().putString("hidden_apps", updated.joinToString(",")).apply()
                showHiddenApps = false
            },
            onBack     = { showHiddenApps = false }
        )
    }
}

private data class SearchProviderMeta(
    val limitKey: String,     // == SearchProvider.id; used for "provider_limit_<key>"
    val enableKey: String,    // static id (in disabled_static_providers) or plugin authority (disabled_plugins)
    val label: String,
    val description: String,
    val group: String,
    val isPlugin: Boolean = false
)

private val BUILTIN_SEARCH_PROVIDERS = listOf(
    SearchProviderMeta("apps", "apps", "Apps", "Find installed apps by name.", "System"),
    SearchProviderMeta("contacts", "contacts", "Contacts", "Search your contacts by name or number.", "System"),
    SearchProviderMeta("system_settings", "system_settings", "System Settings", "Jump straight to Android settings screens.", "System"),
    SearchProviderMeta("browser_history", "browser_history", "Browser History", "Search history from installed browsers. Requires root.", "Root"),
    SearchProviderMeta("hermes_browser_history", "hermes_browser_history", "Hermes Browser History", "Search history from the Hermes browser.", "Built-In"),
    SearchProviderMeta("browser_suggestions", "browser_suggestions", "Web Suggestions", "Show web search suggestions as you type.", "Built-In"),
    SearchProviderMeta("files", "files", "Files", "Search files in device storage. Requires root.", "Root"),
    SearchProviderMeta("calculator", "calculator", "Calculator", "Evaluate math expressions inline.", "Built-In"),
    SearchProviderMeta("timer", "timer", "Timer & Alarm", "Set a timer or alarm from search — \"5 minute timer\", \"timer 1h30m\", \"alarm 7am\". No setup.", "Built-In"),
    SearchProviderMeta("dictionary", "dictionary", "Dictionary", "Define words from search — \"define serendipity\", \"what does ephemeral mean\". Free (dictionaryapi.dev), no key.", "Built-In"),
    SearchProviderMeta("dialer", "dialer", "Dialer", "Type a number — or T9 letters — to place a call.", "Built-In"),
    SearchProviderMeta("widgets", "widgets", "Widgets", "Show a pinned app widget in results.", "Built-In"),
    SearchProviderMeta("settings", "settings", "Settings", "Search Deck's own settings.", "Built-In"),
    SearchProviderMeta("ai", "ai", "Gemini", "On-device Gemini Nano answers, when the model is available.", "Built-In"),
    SearchProviderMeta("offline_ai", "offline_ai", "On-device AI Answer", "Answer questions with the bundled on-device model — offline, private, free, no quota. Appears for question-shaped queries when On-device AI ranking is enabled; the model runs only when you tap.", "Built-In"),
    SearchProviderMeta("claude", "claude", "Claude", "Ask Claude (Anthropic) — appears for any query and fires only when you tap the result. Requires an API key.", "Built-In"),
    SearchProviderMeta("hermes", "hermes", "Hermes", "Ask your self-hosted Hermes agent (Nous Research) — appears for any query, fires when you tap. Requires its URL and access password.", "Built-In"),
    SearchProviderMeta("home_assistant", "home_assistant", "Home Assistant", "Control your Home Assistant devices from search. Requires a URL and access token.", "Built-In"),
    SearchProviderMeta("plex", "plex", "Plex", "Search your Plex media library. Requires a server URL and token.", "Built-In"),
    SearchProviderMeta("radarr", "radarr", "Radarr", "Add movies you don't already have in Plex — search a movie, tap Add to send it to Radarr. Hides anything already in Plex. Requires Radarr's URL + API key.", "Built-In"),
    SearchProviderMeta("sonarr", "sonarr", "Sonarr", "Add TV shows you don't already have in Plex — search a show, tap Add to send it to Sonarr. Hides anything already in Plex. Requires Sonarr's URL + API key.", "Built-In"),
    SearchProviderMeta("tandoor", "tandoor", "Tandoor", "Search your Tandoor recipe collection. Requires a server URL and API token.", "Built-In"),
    SearchProviderMeta("todoist", "todoist", "Todoist", "Find tasks (tap the checkbox to complete one) and add new tasks from the search bar. Requires a Todoist API token.", "Built-In"),
    SearchProviderMeta("symfonium", "symfonium", "Symfonium", "Search songs in Symfonium and tap to play. Requires the Symfonium app — no login needed.", "Built-In"),
    SearchProviderMeta("transistor", "transistor", "Transistor", "Find your saved Transistor radio stations and tap to play. Requires the Transistor app — no login needed.", "Built-In"),
    SearchProviderMeta("places", "places", "Google Maps", "Search nearby businesses on Google Maps with a map — appears for any query, fires when you tap. Requires a Google Maps Platform API key.", "Built-In"),
    SearchProviderMeta("weather", "weather", "Weather", "Current conditions + a 5-day forecast — \"weather\", \"weather in chicago\". Free (Open-Meteo), no key. \"weather\" on its own uses your location (grant location permission).", "Built-In"),
    SearchProviderMeta("wikipedia", "wikipedia", "Wikipedia", "Look up Wikipedia articles. A confident match shows a summary with a thumbnail; otherwise a few results (capped by the limit below). No account needed.", "Built-In"),
    SearchProviderMeta("unit", "unit", "Unit Conversion", "Convert units inline — e.g. \"5 km to miles\", \"100f to c\", \"2 cups in ml\". Local, no account.", "Built-In"),
    SearchProviderMeta("timezone", "timezone", "Timezone", "Convert times across zones — \"time in tokyo\", \"3pm EST to PST\". Local, no account.", "Built-In"),
    SearchProviderMeta("currency", "currency", "Currency", "Convert currencies — \"100 usd to eur\". Free ECB daily rates (frankfurter.app), no key.", "Built-In"),
    SearchProviderMeta("translation", "translation", "Translation", "Translate phrases — \"translate hello to spanish\", \"hello in french\". Free MyMemory API.", "Built-In"),
    SearchProviderMeta("gmail", "gmail", "Gmail", "Search your inbox over IMAP; tap a result to open Gmail. Requires your address + a Google App Password (2-Step Verification → App passwords), not your normal password.", "Built-In"),
    SearchProviderMeta("youtube", "youtube", "YouTube", "Search YouTube videos and tap to open. Requires a free YouTube Data API v3 key. Heads-up: each search costs 100 of the 10,000 free daily quota units (~100 searches/day), so it fires only after you pause typing.", "Built-In"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSettingsScreen(onBack: () -> Unit, initialProvider: String? = null) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE) }
    val pluginRepo        = remember { PluginRepository(context) }
    val discoveredPlugins = remember { pluginRepo.discoverPlugins() }

    var disabledStaticProviders by remember {
        mutableStateOf(prefs.getStringSet("disabled_static_providers", emptySet())?.toSet() ?: emptySet())
    }
    var disabledPlugins by remember {
        mutableStateOf(prefs.getStringSet("disabled_plugins", emptySet())?.toSet() ?: emptySet())
    }
    // Bumped when returning from a detail page so the row summaries (on/off, limit) recompute.
    var refreshKey by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf(initialProvider?.let { ip -> BUILTIN_SEARCH_PROVIDERS.find { it.limitKey == ip } }) }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val pluginMetas = discoveredPlugins.map {
            SearchProviderMeta(
                limitKey    = "plugin:${it.id}",
                enableKey   = it.authority,
                label       = it.name,
                description = it.id,
                group       = "External",
                isPlugin    = true
            )
        }
        val allMetas = BUILTIN_SEARCH_PROVIDERS + pluginMetas
        val metaByKey = remember(allMetas) { allMetas.associateBy { it.limitKey } }
        // Static list — manual drag-reorder removed; ranking is now relevance-driven (SearchViewModel
        // uses a fixed tier prior + on-device relevance). Built-ins first, then plugins.
        val keys = allMetas.map { it.limitKey }
        val listState = rememberLazyListState()

        LazyColumn(
            state    = listState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "header") {
                Column {
                    var aiRerank by remember { mutableStateOf(prefs.getBoolean("search_ai_rerank", true)) }
                    ListItem(
                        headlineContent   = { Text("Smart result ordering") },
                        supportingContent = { Text("Reorder result groups by how well they match your query, on-device. Experimental.") },
                        trailingContent   = {
                            Switch(checked = aiRerank, onCheckedChange = {
                                aiRerank = it
                                prefs.edit().putBoolean("search_ai_rerank", it).apply()
                            })
                        }
                    )
                    HorizontalDivider()
                    var nanoRanking by remember { mutableStateOf(prefs.getBoolean("search_nano_ranking", false)) }
                    val llmState by LocalLlmClassifier.state.collectAsState()
                    val aiSubtitle = if (!nanoRanking) {
                        "Understands what a query is about (e.g. \"batman\" → comic/movie, not the song) to rank results, fully on-device. Prefers your phone's built-in Gemini Nano; if that's unavailable it downloads a ~550 MB model (Qwen) on first use."
                    } else when (val s = llmState) {
                        is LocalLlmClassifier.ModelState.Downloading -> "Downloading on-device model… ${s.percent}%"
                        LocalLlmClassifier.ModelState.Loading        -> "Loading on-device model…"
                        LocalLlmClassifier.ModelState.Ready          -> "On-device model ready."
                        is LocalLlmClassifier.ModelState.Failed      -> "Model unavailable (${s.message}); using fast heuristic ranking."
                        LocalLlmClassifier.ModelState.Idle           -> "On — uses Gemini Nano if available, otherwise downloads a model on your first search."
                    }
                    ListItem(
                        headlineContent   = { Text("On-device AI ranking") },
                        supportingContent = { Text(aiSubtitle) },
                        trailingContent   = {
                            Switch(checked = nanoRanking, onCheckedChange = {
                                nanoRanking = it
                                prefs.edit().putBoolean("search_nano_ranking", it).apply()
                            })
                        }
                    )
                    HorizontalDivider()
                    Text(
                        "Results are ordered automatically by how well each provider matches your query. Tap a provider for its settings.",
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                    HorizontalDivider()
                }
            }
            items(keys, key = { it }) { key ->
                val meta = metaByKey[key] ?: return@items
                @Suppress("UNUSED_EXPRESSION") refreshKey  // recompose trigger
                val enabled = if (meta.isPlugin) meta.enableKey !in disabledPlugins
                              else               meta.enableKey !in disabledStaticProviders
                val limit = prefs.getInt("provider_limit_${meta.limitKey}", 0)
                Column(modifier = Modifier.animateItem()) {
                    ListItem(
                        headlineContent   = { Text(meta.label) },
                        supportingContent = {
                            Text(
                                (if (enabled) "On" else "Off") +
                                    (if (limit > 0) "  ·  max $limit result${if (limit == 1) "" else "s"}" else "")
                            )
                        },
                        colors            = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier          = Modifier.clickable { selected = meta }
                    )
                    HorizontalDivider()
                }
            }
            if (pluginMetas.isEmpty()) {
                item(key = "no_plugins") {
                    ListItem(
                        headlineContent   = { Text("No plugins installed") },
                        supportingContent = { Text("Install a Deck plugin to add more search providers") }
                    )
                }
            }
        }
    }

    selected?.let { meta ->
        SearchProviderDetailScreen(
            meta   = meta,
            onBack = {
                disabledStaticProviders = prefs.getStringSet("disabled_static_providers", emptySet())?.toSet() ?: emptySet()
                disabledPlugins = prefs.getStringSet("disabled_plugins", emptySet())?.toSet() ?: emptySet()
                refreshKey++
                selected = null
            }
        )
    }
}

/** The selectable Claude models (id → friendly label). */
private val CLAUDE_MODELS = listOf(
    "claude-opus-4-8"   to "Claude Opus 4.8",
    "claude-opus-4-7"   to "Claude Opus 4.7",
    "claude-opus-4-6"   to "Claude Opus 4.6",
    "claude-sonnet-4-6" to "Claude Sonnet 4.6",
    "claude-haiku-4-5"  to "Claude Haiku 4.5"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClaudeModelDropdown(
    label: String,
    selectedId: String,
    includeSameAsModel: Boolean,   // thinking model: blank means "Same as Model"; main: blank means default Opus 4.8
    supporting: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val current = when {
        selectedId.isBlank() && includeSameAsModel -> "Same as Model"
        selectedId.isBlank()                       -> "Claude Opus 4.8"
        else -> CLAUDE_MODELS.firstOrNull { it.first == selectedId }?.second ?: selectedId
    }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value          = current,
            onValueChange  = {},
            readOnly       = true,
            label          = { Text(label) },
            trailingIcon   = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            supportingText = { Text(supporting) },
            modifier       = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (includeSameAsModel) {
                DropdownMenuItem(text = { Text("Same as Model") }, onClick = { expanded = false; onSelect("") })
            }
            CLAUDE_MODELS.forEach { (id, lbl) ->
                DropdownMenuItem(text = { Text(lbl) }, onClick = { expanded = false; onSelect(id) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchProviderDetailScreen(meta: SearchProviderMeta, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE) }
    BackHandler(onBack = onBack)

    val disabledKey = if (meta.isPlugin) "disabled_plugins" else "disabled_static_providers"
    var enabled by remember {
        mutableStateOf(meta.enableKey !in (prefs.getStringSet(disabledKey, emptySet()) ?: emptySet()))
    }
    var limit by remember { mutableIntStateOf(prefs.getInt("provider_limit_${meta.limitKey}", 0)) }

    // Apps-specific
    var appSearchVisibleOnly by remember { mutableStateOf(prefs.getBoolean("app_search_visible_only", true)) }
    // Hermes-specific
    var hermesBaseUrl  by remember { mutableStateOf(prefs.getString("hermes_base_url", "") ?: "") }
    var hermesPassword by remember { mutableStateOf(prefs.getString("hermes_password", "") ?: "") }
    var hermesModel    by remember { mutableStateOf(prefs.getString("hermes_model", "") ?: "") }
    var hermesPwShown  by remember { mutableStateOf(false) }
    // Shared-memory (PocketBase) creds — sync the Claude memory across devices
    var pbBaseUrl  by remember { mutableStateOf(prefs.getString("pb_base_url", "") ?: "") }
    var pbEmail    by remember { mutableStateOf(prefs.getString("pb_email", "") ?: "") }
    var pbPassword by remember { mutableStateOf(prefs.getString("pb_password", "") ?: "") }
    var pbPwShown  by remember { mutableStateOf(false) }
    // Claude-specific
    var claudeApiKey      by remember { mutableStateOf(prefs.getString("claude_api_key", "") ?: "") }
    var claudeModel       by remember { mutableStateOf(prefs.getString("claude_model", "") ?: "") }
    var claudeThinkingModel by remember { mutableStateOf(prefs.getString("claude_thinking_model", "") ?: "") }
    var claudeKeyShown    by remember { mutableStateOf(false) }
    var claudeSystemPrompt by remember { mutableStateOf(prefs.getString("claude_system_prompt", "") ?: "") }
    var claudeUseLocation by remember { mutableStateOf(prefs.getBoolean("claude_use_location", false)) }
    val locationPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        claudeUseLocation = granted
        prefs.edit().putBoolean("claude_use_location", granted).apply()
    }
    var claudeMemoryEnabled by remember { mutableStateOf(prefs.getBoolean("claude_memory_enabled", false)) }
    var claudeMemoryText    by remember { mutableStateOf(prefs.getString("claude_memory", "") ?: "") }
    var claudeNotify by remember { mutableStateOf(prefs.getBoolean("claude_notify", false)) }
    val notifPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        claudeNotify = granted
        prefs.edit().putBoolean("claude_notify", granted).apply()
    }
    var haUrl        by remember { mutableStateOf(prefs.getString("ha_base_url", "") ?: "") }
    var haToken      by remember { mutableStateOf(prefs.getString("ha_token", "") ?: "") }
    var haTokenShown by remember { mutableStateOf(false) }
    // Plex-specific
    var plexUrl        by remember { mutableStateOf(prefs.getString("plex_base_url", "") ?: "") }
    var plexUrlAlt     by remember { mutableStateOf(prefs.getString("plex_base_url_alt", "") ?: "") }
    var plexToken      by remember { mutableStateOf(prefs.getString("plex_token", "") ?: "") }
    var plexTokenShown by remember { mutableStateOf(false) }
    // Lifted out of the plex branch so the shared "Result limit" slider below can hide itself when
    // libraries are split (each library then carries its own limit instead of one combined cap).
    var plexSplitLibs  by remember { mutableStateOf(prefs.getBoolean("plex_split_libraries", false)) }
    var tandoorUrl        by remember { mutableStateOf(prefs.getString("tandoor_base_url", "") ?: "") }
    var tandoorToken      by remember { mutableStateOf(prefs.getString("tandoor_token", "") ?: "") }
    var tandoorTokenShown by remember { mutableStateOf(false) }
    // Widgets-specific
    var showWidgetManager by remember { mutableStateOf(false) }
    // Dialer-specific
    var showKeyConfig  by remember { mutableStateOf(false) }
    var keyConfigIndex by remember { mutableIntStateOf(0) }
    var keyInput       by remember { mutableStateOf("") }
    val keyConfigDigits = listOf('1','2','3','4','5','6','7','8','9','0')
    val tempKeyMap = remember {
        val saved = prefs.getString("number_key_map", "")
        mutableStateListOf<Char>().also { list ->
            if (saved != null && saved.length == 10) list.addAll(saved.toList())
            else repeat(10) { list.add(' ') }
        }
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        val cur = (prefs.getStringSet(disabledKey, emptySet()) ?: emptySet()).toMutableSet()
        if (value) cur.remove(meta.enableKey) else cur.add(meta.enableKey)
        prefs.edit().putStringSet(disabledKey, cur).apply()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(meta.label) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                meta.description,
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Show in search") },
                trailingContent = { Switch(checked = enabled, onCheckedChange = { setEnabled(it) }) }
            )
            HorizontalDivider()
            // Plex in split-libraries mode uses per-library caps (below) instead of one combined limit.
            if (meta.limitKey != "plex" || !plexSplitLibs) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text("Result limit", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text  = if (limit in 1..8) "$limit" else "Unlimited",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Discrete steps: 1,2,3,4,5,6,7,8, then Unlimited (stored as 0).
                    Slider(
                        value                 = if (limit in 1..8) (limit - 1).toFloat() else 8f,
                        onValueChange         = { v -> val idx = v.roundToInt(); limit = if (idx >= 8) 0 else idx + 1 },
                        onValueChangeFinished = { prefs.edit().putInt("provider_limit_${meta.limitKey}", limit).apply() },
                        valueRange            = 0f..8f,
                        steps                 = 7,
                        enabled               = enabled
                    )
                }
                HorizontalDivider()
            }

            // Expose this provider's results to the Claude/AI agent (it reads them via search_deck
            // to answer and act on requests). Hidden for the AI providers themselves.
            if (meta.limitKey != "claude" && meta.limitKey != "ai") {
                var exposeToClaude by remember {
                    mutableStateOf(prefs.getBoolean("claude_expose_${meta.limitKey}", true))
                }
                ListItem(
                    headlineContent   = { Text("Expose to Claude/AI") },
                    supportingContent = { Text("Let the Claude assistant read this provider's results to answer and act on your requests.") },
                    trailingContent   = {
                        Switch(checked = exposeToClaude, onCheckedChange = { v ->
                            exposeToClaude = v
                            prefs.edit().putBoolean("claude_expose_${meta.limitKey}", v).apply()
                        })
                    }
                )
                HorizontalDivider()
            }

            when (meta.limitKey) {
                "apps" -> {
                    ListItem(
                        headlineContent   = { Text("Visible apps only") },
                        supportingContent = { Text("Exclude hidden apps from search results") },
                        trailingContent   = {
                            Switch(
                                checked         = appSearchVisibleOnly,
                                enabled         = enabled,
                                onCheckedChange = {
                                    appSearchVisibleOnly = it
                                    prefs.edit().putBoolean("app_search_visible_only", it).apply()
                                }
                            )
                        }
                    )
                    HorizontalDivider()
                }
                "widgets" -> {
                    ListItem(
                        headlineContent   = { Text("Manage widgets") },
                        supportingContent = { Text("Change height, background, configure or swap pinned widgets") },
                        trailingContent   = { TextButton(onClick = { showWidgetManager = true }, enabled = enabled) { Text("Manage") } }
                    )
                    HorizontalDivider()
                }
                "dialer" -> {
                    ListItem(
                        headlineContent   = { Text("Number key layout") },
                        supportingContent = {
                            val saved = prefs.getString("number_key_map", "")
                            if (saved != null && saved.length == 10) Text(saved.uppercase()) else Text("Not configured")
                        },
                        trailingContent   = {
                            TextButton(onClick = {
                                val saved = prefs.getString("number_key_map", "")
                                tempKeyMap.clear()
                                if (saved != null && saved.length == 10) tempKeyMap.addAll(saved.toList())
                                else repeat(10) { tempKeyMap.add(' ') }
                                keyConfigIndex = 0
                                showKeyConfig = true
                            }) { Text("Configure") }
                        }
                    )
                    HorizontalDivider()
                }
                "claude" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value                = claudeApiKey,
                            onValueChange        = {
                                claudeApiKey = it.trim()
                                prefs.edit().putString("claude_api_key", claudeApiKey).apply()
                            },
                            label                = { Text("API key") },
                            placeholder          = { Text("sk-ant-…") },
                            singleLine           = true,
                            visualTransformation = if (claudeKeyShown) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon         = {
                                IconButton(onClick = { claudeKeyShown = !claudeKeyShown }) {
                                    Icon(
                                        imageVector        = if (claudeKeyShown) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (claudeKeyShown) "Hide API key" else "Show API key"
                                    )
                                }
                            },
                            supportingText       = { Text("Stored on-device. Used only when you tap an \"Ask Claude\" result.") },
                            modifier             = Modifier.fillMaxWidth()
                        )
                        ClaudeModelDropdown(
                            label              = "Model",
                            selectedId         = claudeModel,
                            includeSameAsModel = false,
                            supporting         = "The model used for Claude answers.",
                            onSelect           = {
                                claudeModel = it
                                prefs.edit().putString("claude_model", it).apply()
                            }
                        )
                        ClaudeModelDropdown(
                            label              = "Thinking model",
                            selectedId         = claudeThinkingModel,
                            includeSameAsModel = true,
                            supporting         = "Used when the brain toggle in the chat is on (extended thinking).",
                            onSelect           = {
                                claudeThinkingModel = it
                                prefs.edit().putString("claude_thinking_model", it).apply()
                            }
                        )
                        OutlinedTextField(
                            value          = claudeSystemPrompt,
                            onValueChange  = {
                                claudeSystemPrompt = it
                                prefs.edit().putString("claude_system_prompt", it).apply()
                            },
                            label          = { Text("System prompt") },
                            placeholder    = { Text("Leave blank for the built-in default") },
                            minLines       = 3,
                            supportingText = { Text("Custom instructions that shape how Claude responds. Blank uses the default (concise launcher answers).") },
                            modifier       = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text("Location-aware answers", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Shares your approximate location and lets Claude search the web for weather, nearby places, and current info. Web search has a small per-query cost.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked         = claudeUseLocation,
                                onCheckedChange = { on ->
                                    if (!on) {
                                        claudeUseLocation = false
                                        prefs.edit().putBoolean("claude_use_location", false).apply()
                                    } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                        claudeUseLocation = true
                                        prefs.edit().putBoolean("claude_use_location", true).apply()
                                    } else {
                                        locationPermLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                    }
                                }
                            )
                        }
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text("Memory", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Remember durable facts about you across chats and include them in every conversation. View, edit, or delete them below.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked         = claudeMemoryEnabled,
                                onCheckedChange = {
                                    claudeMemoryEnabled = it
                                    prefs.edit().putBoolean("claude_memory_enabled", it).apply()
                                }
                            )
                        }
                        if (claudeMemoryEnabled) {
                            OutlinedTextField(
                                value          = claudeMemoryText,
                                onValueChange  = {
                                    claudeMemoryText = it
                                    prefs.edit().putString("claude_memory", it).apply()
                                },
                                label          = { Text("Remembered facts (one per line)") },
                                minLines       = 3,
                                supportingText = { Text("Claude reads these at the start of every chat, and adds to them automatically as it learns. Edit or delete any line.") },
                                modifier       = Modifier.fillMaxWidth()
                            )
                            TextButton(onClick = {
                                claudeMemoryText = ""
                                prefs.edit().putString("claude_memory", "").apply()
                            }) { Text("Clear memory") }
                        }
                        // Shared memory across devices (PocketBase on the HA Yellow). When set, the phone
                        // Claude reads + writes the same about-me core as the desktop agent.
                        Text("Shared memory (PocketBase)", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Sync memory with your other devices via PocketBase. Leave blank to keep memory on this phone only.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value         = pbBaseUrl,
                            onValueChange = { pbBaseUrl = it.trim(); prefs.edit().putString("pb_base_url", pbBaseUrl).apply() },
                            label         = { Text("PocketBase URL") },
                            placeholder   = { Text("http://192.168.0.31:8090") },
                            singleLine    = true,
                            modifier      = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value         = pbEmail,
                            onValueChange = { pbEmail = it.trim(); prefs.edit().putString("pb_email", pbEmail).apply() },
                            label         = { Text("Service account email") },
                            placeholder   = { Text("agent@deck.local") },
                            singleLine    = true,
                            modifier      = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value                = pbPassword,
                            onValueChange        = { pbPassword = it; prefs.edit().putString("pb_password", pbPassword).apply() },
                            label                = { Text("Service account password") },
                            singleLine           = true,
                            visualTransformation = if (pbPwShown) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon         = {
                                IconButton(onClick = { pbPwShown = !pbPwShown }) {
                                    Icon(
                                        imageVector        = if (pbPwShown) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (pbPwShown) "Hide password" else "Show password"
                                    )
                                }
                            },
                            supportingText       = { Text("Low-privilege PocketBase account. Stored on-device.") },
                            modifier             = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text("Notify when ready", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Get a notification when an answer arrives while you're not looking at the chat. Tap it to jump back to the conversation.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked         = claudeNotify,
                                onCheckedChange = { on ->
                                    if (!on) {
                                        claudeNotify = false
                                        prefs.edit().putBoolean("claude_notify", false).apply()
                                    } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                                            PackageManager.PERMISSION_GRANTED) {
                                        claudeNotify = true
                                        prefs.edit().putBoolean("claude_notify", true).apply()
                                    } else {
                                        notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                            )
                        }
                    }
                    HorizontalDivider()
                }
                "hermes" -> {
                    Column(
                        modifier            = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value          = hermesBaseUrl,
                            onValueChange  = {
                                hermesBaseUrl = it.trim()
                                prefs.edit().putString("hermes_base_url", hermesBaseUrl).apply()
                            },
                            label          = { Text("Base URL") },
                            placeholder    = { Text("https://192.168.0.31:8443") },
                            singleLine     = true,
                            supportingText = { Text("Your Hermes add-on's API address — the OpenAI-compatible port (8443/8080), not :8123.") },
                            modifier       = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value                = hermesPassword,
                            onValueChange        = {
                                hermesPassword = it
                                prefs.edit().putString("hermes_password", hermesPassword).apply()
                            },
                            label                = { Text("Access password") },
                            placeholder          = { Text("Hermes add-on access_password") },
                            singleLine           = true,
                            visualTransformation = if (hermesPwShown) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon         = {
                                IconButton(onClick = { hermesPwShown = !hermesPwShown }) {
                                    Icon(
                                        imageVector        = if (hermesPwShown) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (hermesPwShown) "Hide password" else "Show password"
                                    )
                                }
                            },
                            supportingText       = { Text("Sent as a Bearer token. Stored on-device; used only when you tap an \"Ask Hermes\" result.") },
                            modifier             = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value          = hermesModel,
                            onValueChange  = {
                                hermesModel = it.trim()
                                prefs.edit().putString("hermes_model", hermesModel).apply()
                            },
                            label          = { Text("Model") },
                            placeholder    = { Text("hermes-agent") },
                            singleLine     = true,
                            supportingText = { Text("Model id Hermes exposes. Blank uses \"hermes-agent\".") },
                            modifier       = Modifier.fillMaxWidth()
                        )
                    }
                    HorizontalDivider()
                }
                "home_assistant" -> {
                    val haScope = rememberCoroutineScope()
                    var haTesting by remember { mutableStateOf(false) }
                    var haTestMsg by remember { mutableStateOf<String?>(null) }
                    Column(
                        modifier            = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value          = haUrl,
                            onValueChange  = {
                                haUrl = it.trim()
                                prefs.edit().putString("ha_base_url", haUrl).apply()
                                haTestMsg = null
                            },
                            label          = { Text("Base URL") },
                            placeholder    = { Text("https://xxxxx.ui.nabu.casa") },
                            singleLine     = true,
                            supportingText = { Text("Your Home Assistant address (Nabu Casa or any https URL).") },
                            modifier       = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value                = haToken,
                            onValueChange        = {
                                haToken = it.trim()
                                prefs.edit().putString("ha_token", haToken).apply()
                                haTestMsg = null
                            },
                            label                = { Text("Long-lived access token") },
                            placeholder          = { Text("eyJ…") },
                            singleLine           = true,
                            visualTransformation = if (haTokenShown) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon         = {
                                IconButton(onClick = { haTokenShown = !haTokenShown }) {
                                    Icon(
                                        imageVector        = if (haTokenShown) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (haTokenShown) "Hide token" else "Show token"
                                    )
                                }
                            },
                            supportingText       = { Text("HA → your profile → Security → Long-lived access tokens → Create token.") },
                            modifier             = Modifier.fillMaxWidth()
                        )
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TextButton(
                                enabled = enabled && !haTesting && haUrl.isNotBlank() && haToken.isNotBlank(),
                                onClick = {
                                    haTesting = true; haTestMsg = null
                                    haScope.launch {
                                        val r = HomeAssistantClient.ping(context)
                                        haTesting = false
                                        haTestMsg = if (r.isSuccess) "Connected ✓"
                                                    else "Failed: ${r.exceptionOrNull()?.message ?: "unknown error"}"
                                    }
                                }
                            ) { Text(if (haTesting) "Testing…" else "Test connection") }
                            haTestMsg?.let {
                                Text(
                                    text  = it,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (it.startsWith("Connected")) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                }
                "gmail" -> {
                    var gAddr by remember { mutableStateOf(prefs.getString("gmail_address", "") ?: "") }
                    var gPass by remember { mutableStateOf(prefs.getString("gmail_app_password", "") ?: "") }
                    var gPassShown by remember { mutableStateOf(false) }
                    Column(
                        modifier            = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value         = gAddr,
                            onValueChange = { gAddr = it.trim(); prefs.edit().putString("gmail_address", gAddr).apply() },
                            label         = { Text("Gmail address") },
                            placeholder   = { Text("you@gmail.com") },
                            singleLine    = true,
                            modifier      = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value                = gPass,
                            onValueChange        = { gPass = it; prefs.edit().putString("gmail_app_password", gPass).apply() },
                            label                = { Text("App password") },
                            placeholder          = { Text("16-character app password") },
                            singleLine           = true,
                            visualTransformation = if (gPassShown) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon         = {
                                IconButton(onClick = { gPassShown = !gPassShown }) {
                                    Icon(if (gPassShown) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = if (gPassShown) "Hide" else "Show")
                                }
                            },
                            supportingText       = { Text("NOT your normal password. Turn on 2-Step Verification, then Google Account → Security → App passwords → generate one for \"Mail\" and paste it here. Searches your inbox over IMAP; tap a result to open the Gmail app.") },
                            modifier             = Modifier.fillMaxWidth()
                        )
                    }
                    HorizontalDivider()
                }
                "places" -> {
                    var mapsKey by remember { mutableStateOf(prefs.getString("maps_api_key", "") ?: "") }
                    var mapsKeyShown by remember { mutableStateOf(false) }
                    Column(
                        modifier            = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value                = mapsKey,
                            onValueChange        = {
                                mapsKey = it.trim()
                                prefs.edit().putString("maps_api_key", mapsKey).apply()
                            },
                            label                = { Text("Google Maps API key") },
                            placeholder          = { Text("AIza…") },
                            singleLine           = true,
                            visualTransformation = if (mapsKeyShown) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon         = {
                                IconButton(onClick = { mapsKeyShown = !mapsKeyShown }) {
                                    Icon(
                                        imageVector        = if (mapsKeyShown) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (mapsKeyShown) "Hide key" else "Show key"
                                    )
                                }
                            },
                            supportingText       = { Text("Google Cloud Console → enable Places API (New) + Maps Static API → Credentials → create an API key. It must be UNRESTRICTED or API-restricted (NOT Android-app-restricted) — these are web-service calls and an app-restricted key returns 403. Results bias to your location (grant location permission).") },
                            modifier             = Modifier.fillMaxWidth()
                        )
                    }
                    HorizontalDivider()
                }
                "youtube" -> {
                    var ytKey by remember { mutableStateOf(prefs.getString("youtube_api_key", "") ?: "") }
                    var ytKeyShown by remember { mutableStateOf(false) }
                    Column(
                        modifier            = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value                = ytKey,
                            onValueChange        = {
                                ytKey = it.trim()
                                prefs.edit().putString("youtube_api_key", ytKey).apply()
                            },
                            label                = { Text("YouTube Data API key") },
                            placeholder          = { Text("AIza…") },
                            singleLine           = true,
                            visualTransformation = if (ytKeyShown) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon         = {
                                IconButton(onClick = { ytKeyShown = !ytKeyShown }) {
                                    Icon(
                                        imageVector        = if (ytKeyShown) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (ytKeyShown) "Hide key" else "Show key"
                                    )
                                }
                            },
                            supportingText       = { Text("Google Cloud Console → enable \"YouTube Data API v3\" → Credentials → create an API key. Free; the default 10,000-units/day quota allows ~100 searches/day (each search = 100 units), so results load only after you stop typing. Use the limit slider below to cap results per search.") },
                            modifier             = Modifier.fillMaxWidth()
                        )
                    }
                    HorizontalDivider()
                }
                "radarr", "sonarr" -> {
                    val svc = meta.limitKey
                    val svcLabel = if (svc == "sonarr") "Sonarr" else "Radarr"
                    var arrUrl by remember(svc) { mutableStateOf(prefs.getString("${svc}_url", "") ?: "") }
                    var arrKey by remember(svc) { mutableStateOf(prefs.getString("${svc}_api_key", "") ?: "") }
                    var arrKeyShown by remember(svc) { mutableStateOf(false) }
                    Column(
                        modifier            = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value          = arrUrl,
                            onValueChange  = { arrUrl = it.trim(); prefs.edit().putString("${svc}_url", arrUrl).apply() },
                            label          = { Text("$svcLabel URL") },
                            placeholder    = { Text(if (svc == "sonarr") "http://192.168.1.50:8989" else "http://192.168.1.50:7878") },
                            singleLine     = true,
                            supportingText = { Text("Your $svcLabel server — the same address you open in a browser.") },
                            modifier       = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value                = arrKey,
                            onValueChange        = { arrKey = it.trim(); prefs.edit().putString("${svc}_api_key", arrKey).apply() },
                            label                = { Text("$svcLabel API key") },
                            singleLine           = true,
                            visualTransformation = if (arrKeyShown) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon         = {
                                IconButton(onClick = { arrKeyShown = !arrKeyShown }) {
                                    Icon(
                                        imageVector        = if (arrKeyShown) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (arrKeyShown) "Hide key" else "Show key"
                                    )
                                }
                            },
                            supportingText       = { Text("$svcLabel → Settings → General → API Key. Added titles use your first quality profile + root folder, are monitored, and start searching automatically. Anything already in Plex is hidden from results.") },
                            modifier             = Modifier.fillMaxWidth()
                        )
                    }
                    HorizontalDivider()
                }
                "todoist" -> {
                    var todoToken by remember { mutableStateOf(prefs.getString("todoist_token", "") ?: "") }
                    var todoTokenShown by remember { mutableStateOf(false) }
                    Column(
                        modifier            = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value                = todoToken,
                            onValueChange        = { todoToken = it.trim(); prefs.edit().putString("todoist_token", todoToken).apply() },
                            label                = { Text("Todoist API token") },
                            singleLine           = true,
                            visualTransformation = if (todoTokenShown) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon         = {
                                IconButton(onClick = { todoTokenShown = !todoTokenShown }) {
                                    Icon(
                                        imageVector        = if (todoTokenShown) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (todoTokenShown) "Hide token" else "Show token"
                                    )
                                }
                            },
                            supportingText       = { Text("Todoist → Settings → Integrations → Developer → API token. Tasks matching your search appear with a checkbox to complete; an \"Add to Todoist\" card lets the search bar double as quick task entry.") },
                            modifier             = Modifier.fillMaxWidth()
                        )
                    }
                    HorizontalDivider()
                }
                "plex" -> {
                    val plexScope = rememberCoroutineScope()
                    var plexTesting by remember { mutableStateOf(false) }
                    var plexTestMsg by remember { mutableStateOf<String?>(null) }
                    var plexLibraries by remember { mutableStateOf<List<PlexLibrary>?>(null) }
                    var plexDisabledLibs by remember {
                        mutableStateOf(prefs.getStringSet("plex_disabled_libraries", emptySet())?.toSet() ?: emptySet())
                    }
                    // Per-library result caps (plex_limit_<title>, 0 = All), used only in split mode.
                    val plexLibLimits = remember { mutableStateMapOf<String, Int>() }
                    LaunchedEffect(Unit) {
                        if (PlexClient.isConfigured(context)) plexLibraries = PlexClient.libraries(context).getOrNull()
                    }
                    Column(
                        modifier            = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value          = plexUrl,
                            onValueChange  = {
                                plexUrl = it.trim()
                                prefs.edit().putString("plex_base_url", plexUrl).apply()
                                plexTestMsg = null
                            },
                            label          = { Text("Server URL") },
                            placeholder    = { Text("http://192.168.1.50:32400") },
                            singleLine     = true,
                            supportingText = { Text("Your Plex server. Add both a local and a remote URL below — Deck auto-picks whichever connects.") },
                            modifier       = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value          = plexUrlAlt,
                            onValueChange  = {
                                plexUrlAlt = it.trim()
                                prefs.edit().putString("plex_base_url_alt", plexUrlAlt).apply()
                                plexTestMsg = null
                            },
                            label          = { Text("Alternate URL (optional)") },
                            placeholder    = { Text("https://1-2-3-4.abcd.plex.direct:32400") },
                            singleLine     = true,
                            supportingText = { Text("The other address — e.g. your plex.direct HTTPS URL for when you're away. Deck races both and uses the one that responds.") },
                            modifier       = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value                = plexToken,
                            onValueChange        = {
                                plexToken = it.trim()
                                prefs.edit().putString("plex_token", plexToken).apply()
                                plexTestMsg = null
                            },
                            label                = { Text("X-Plex-Token") },
                            placeholder          = { Text("xxxxxxxxxxxxxxxxxxxx") },
                            singleLine           = true,
                            visualTransformation = if (plexTokenShown) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon         = {
                                IconButton(onClick = { plexTokenShown = !plexTokenShown }) {
                                    Icon(
                                        imageVector        = if (plexTokenShown) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (plexTokenShown) "Hide token" else "Show token"
                                    )
                                }
                            },
                            supportingText       = { Text("Plex Web → open any item → ⋯ → Get Info → View XML; copy the X-Plex-Token from the URL.") },
                            modifier             = Modifier.fillMaxWidth()
                        )
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TextButton(
                                enabled = enabled && !plexTesting && (plexUrl.isNotBlank() || plexUrlAlt.isNotBlank()) && plexToken.isNotBlank(),
                                onClick = {
                                    plexTesting = true; plexTestMsg = null
                                    plexScope.launch {
                                        val r = PlexClient.ping(context)
                                        plexTesting = false
                                        plexTestMsg = if (r.isSuccess) "Connected ✓ (${r.getOrNull()})"
                                                      else "Failed: ${r.exceptionOrNull()?.message ?: "unknown error"}"
                                        if (r.isSuccess) plexLibraries = PlexClient.libraries(context).getOrNull()
                                    }
                                }
                            ) { Text(if (plexTesting) "Testing…" else "Test connection") }
                            plexTestMsg?.let {
                                Text(
                                    text  = it,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (it.startsWith("Connected")) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Separate libraries into cards")
                                Text(
                                    "Show each Plex library as its own result card, labeled by library.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = plexSplitLibs, onCheckedChange = {
                                plexSplitLibs = it
                                prefs.edit().putBoolean("plex_split_libraries", it).apply()
                            })
                        }
                        plexLibraries?.let { libs ->
                            if (libs.isNotEmpty()) {
                                Text(
                                    "Libraries",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                libs.forEach { lib ->
                                    val on = lib.title !in plexDisabledLibs
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(lib.title)
                                                Text(
                                                    lib.type.replaceFirstChar { it.uppercase() },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Switch(checked = on, onCheckedChange = { checked ->
                                                plexDisabledLibs = if (checked) plexDisabledLibs - lib.title else plexDisabledLibs + lib.title
                                                prefs.edit().putStringSet("plex_disabled_libraries", plexDisabledLibs).apply()
                                            })
                                        }
                                        // When split, each library gets its own result cap (0 = All),
                                        // shown as a slider matching the combined "Result limit" control.
                                        if (plexSplitLibs && on) {
                                            val lim = plexLibLimits[lib.title] ?: prefs.getInt("plex_limit_${lib.title}", 0)
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "Show up to",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text  = if (lim in 1..8) "$lim" else "All",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Slider(
                                                value                 = if (lim in 1..8) (lim - 1).toFloat() else 8f,
                                                onValueChange         = { v -> val idx = v.roundToInt(); plexLibLimits[lib.title] = if (idx >= 8) 0 else idx + 1 },
                                                onValueChangeFinished = { prefs.edit().putInt("plex_limit_${lib.title}", plexLibLimits[lib.title] ?: 0).apply() },
                                                valueRange            = 0f..8f,
                                                steps                 = 7
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                }

                "tandoor" -> {
                    val tandoorScope = rememberCoroutineScope()
                    var tandoorTesting by remember { mutableStateOf(false) }
                    var tandoorTestMsg by remember { mutableStateOf<String?>(null) }
                    Column(
                        modifier            = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value          = tandoorUrl,
                            onValueChange  = {
                                tandoorUrl = it.trim()
                                prefs.edit().putString("tandoor_base_url", tandoorUrl).apply()
                                tandoorTestMsg = null
                            },
                            label          = { Text("Server URL") },
                            placeholder    = { Text("https://recipes.example.com") },
                            singleLine     = true,
                            supportingText = { Text("Your Tandoor server address. To open recipes in kitshn, this host must match the instance kitshn is signed into.") },
                            modifier       = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value                = tandoorToken,
                            onValueChange        = {
                                tandoorToken = it.trim()
                                prefs.edit().putString("tandoor_token", tandoorToken).apply()
                                tandoorTestMsg = null
                            },
                            label                = { Text("API token") },
                            placeholder          = { Text("tda_…") },
                            singleLine           = true,
                            visualTransformation = if (tandoorTokenShown) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon         = {
                                IconButton(onClick = { tandoorTokenShown = !tandoorTokenShown }) {
                                    Icon(
                                        imageVector        = if (tandoorTokenShown) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (tandoorTokenShown) "Hide token" else "Show token"
                                    )
                                }
                            },
                            supportingText       = { Text("Tandoor → Settings → API → create an API token, then paste it here.") },
                            modifier             = Modifier.fillMaxWidth()
                        )
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TextButton(
                                enabled = enabled && !tandoorTesting && tandoorUrl.isNotBlank() && tandoorToken.isNotBlank(),
                                onClick = {
                                    tandoorTesting = true; tandoorTestMsg = null
                                    tandoorScope.launch {
                                        val r = TandoorClient.ping(context)
                                        tandoorTesting = false
                                        tandoorTestMsg = if (r.isSuccess) "Connected ✓ (${r.getOrNull()})"
                                                         else "Failed: ${r.exceptionOrNull()?.message ?: "unknown error"}"
                                    }
                                }
                            ) { Text(if (tandoorTesting) "Testing…" else "Test connection") }
                            tandoorTestMsg?.let {
                                Text(
                                    text  = it,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (it.startsWith("Connected")) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (showWidgetManager) {
        WidgetManagementScreen(onDismiss = { showWidgetManager = false })
    }

    if (showKeyConfig) {
        val focusRequester = remember { FocusRequester() }
        AlertDialog(
            onDismissRequest = { showKeyConfig = false; keyConfigIndex = 0 },
            title = { Text("Number Key Layout") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Press the key on your keyboard that you use for ${keyConfigDigits[keyConfigIndex]} (${keyConfigIndex + 1} of 10)")
                    if (keyConfigIndex > 0) {
                        Text(
                            text = keyConfigDigits.take(keyConfigIndex)
                                .mapIndexed { i, d -> "$d→${tempKeyMap.getOrElse(i) { '?' }.uppercaseChar()}" }
                                .joinToString("  "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedTextField(
                        value         = keyInput,
                        onValueChange = { newText ->
                            val char = newText.lastOrNull()
                            if (char != null && char.isLetter()) {
                                tempKeyMap[keyConfigIndex] = char.lowercaseChar()
                                if (keyConfigIndex < 9) {
                                    keyConfigIndex++
                                } else {
                                    prefs.edit()
                                        .putString("number_key_map", String(tempKeyMap.toCharArray()))
                                        .apply()
                                    showKeyConfig = false
                                    keyConfigIndex = 0
                                }
                            }
                            keyInput = ""
                        },
                        placeholder = { Text("Press a key…") },
                        singleLine  = true,
                        modifier    = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showKeyConfig = false; keyConfigIndex = 0 }) {
                    Text("Cancel")
                }
            }
        )
        LaunchedEffect(keyConfigIndex) {
            focusRequester.requestFocus()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE) }

    var showResetConfirm    by remember { mutableStateOf(false) }
    var showClearPinConfirm by remember { mutableStateOf(false) }

    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrDefault("—")
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            SettingsItem(
                title    = "Version",
                subtitle = versionName ?: "—",
                onClick  = null
            )
            HorizontalDivider()
            SettingsItem(
                title    = "Reset onboarding",
                subtitle = "Show permission setup screens on next launch"
            ) {
                showResetConfirm = true
            }
            HorizontalDivider()
            SettingsItem(
                title    = "Clear pinned apps",
                subtitle = "Remove all apps from the dock bar"
            ) {
                showClearPinConfirm = true
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset onboarding?") },
            text  = { Text("The permission setup screens will appear on next launch.") },
            confirmButton = {
                TextButton(onClick = {
                    prefs.edit().remove("onboarding_done").apply()
                    showResetConfirm = false
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showClearPinConfirm) {
        AlertDialog(
            onDismissRequest = { showClearPinConfirm = false },
            title = { Text("Clear dock?") },
            text  = { Text("All pinned apps will be removed from the dock bar.") },
            confirmButton = {
                TextButton(onClick = {
                    prefs.edit().remove("pinned").apply()
                    showClearPinConfirm = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearPinConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

private val staticSeedColors = listOf(
    "Red"         to Color(0xFFE53935),
    "Pink"        to Color(0xFFD81B60),
    "Purple"      to Color(0xFF8E24AA),
    "Deep Purple" to Color(0xFF5E35B1),
    "Indigo"      to Color(0xFF3949AB),
    "Blue"        to Color(0xFF1E88E5),
    "Cyan"        to Color(0xFF00ACC1),
    "Teal"        to Color(0xFF00897B),
    "Green"       to Color(0xFF43A047),
    "Lime"        to Color(0xFF7CB342),
    "Amber"       to Color(0xFFFFB300),
    "Orange"      to Color(0xFFFB8C00),
    "Deep Orange" to Color(0xFFE64A19),
)

@Composable
private fun SectionHeader(title: String) {
    Text(
        text     = title,
        style    = MaterialTheme.typography.labelMedium,
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null
) {
    ListItem(
        headlineContent   = { Text(title) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        modifier          = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HiddenAppsScreen(
    hiddenApps: Set<String>,
    onSave: (Set<String>) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var filterQuery by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf(hiddenApps) }
    BackHandler {
        onSave(pending)
    }

    val allApps: List<Pair<String, String>> = remember {
        val pm = context.packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .mapNotNull { ri ->
                runCatching { ri.loadLabel(pm).toString() to ri.activityInfo.packageName }.getOrNull()
            }
            .sortedBy { it.first.lowercase() }
            .distinctBy { it.second }
    }

    val filtered = remember(filterQuery, allApps) {
        if (filterQuery.isBlank()) allApps
        else allApps.filter { it.first.contains(filterQuery, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hidden apps") },
                navigationIcon = {
                    IconButton(onClick = { onSave(pending) }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value         = filterQuery,
                onValueChange = { filterQuery = it },
                placeholder   = { Text("Search apps") },
                singleLine    = true,
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filtered, key = { it.second }) { (label, pkg) ->
                    val checked = pkg in pending
                    val iconBitmap by produceState<Bitmap?>(null, pkg) {
                        value = withContext(Dispatchers.IO) {
                            runCatching {
                                val d = context.packageManager.getApplicationIcon(pkg)
                                val w = d.intrinsicWidth.coerceIn(1, 96)
                                val h = d.intrinsicHeight.coerceIn(1, 96)
                                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                                    d.setBounds(0, 0, w, h)
                                    d.draw(Canvas(bmp))
                                }
                            }.getOrNull()
                        }
                    }
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .clickable {
                                pending = if (checked) pending - pkg else pending + pkg
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (iconBitmap != null) {
                            Image(
                                bitmap             = iconBitmap!!.asImageBitmap(),
                                contentDescription = null,
                                modifier           = Modifier.size(40.dp)
                            )
                        } else {
                            Spacer(Modifier.size(40.dp))
                        }
                        Text(
                            text     = label,
                            style    = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Checkbox(
                            checked         = checked,
                            onCheckedChange = { isChecked ->
                                pending = if (isChecked) pending + pkg else pending - pkg
                            }
                        )
                    }
                }
            }
        }
    }
}
