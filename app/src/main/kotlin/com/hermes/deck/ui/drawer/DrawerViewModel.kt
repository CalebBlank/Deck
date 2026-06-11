package com.hermes.deck.ui.drawer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hermes.deck.data.AppInfo
import com.hermes.deck.data.IconPackInfo
import com.hermes.deck.data.IconPackRepository
import com.hermes.deck.data.IconShape
import com.hermes.deck.data.InstalledAppsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DrawerViewMode { Grid, List }

class DrawerViewModel(
    installedRepo: InstalledAppsRepository,
    private val prefs: SharedPreferences,
    private val iconPackRepo: IconPackRepository,
    private val context: Context
) : ViewModel() {

    private val allApps: StateFlow<List<AppInfo>> = installedRepo.getAllApps()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _query = MutableStateFlow("")

    private val _selectedIconPack = MutableStateFlow(prefs.getString("icon_pack", "") ?: "")
    val selectedIconPack: StateFlow<String> = _selectedIconPack.asStateFlow()

    private val _resolvedIcons = MutableStateFlow<Map<String, Drawable>>(emptyMap())
    val resolvedIcons: StateFlow<Map<String, Drawable>> = _resolvedIcons.asStateFlow()

    private val _installedPacks = MutableStateFlow<List<IconPackInfo>>(emptyList())
    val installedPacks: StateFlow<List<IconPackInfo>> = _installedPacks.asStateFlow()

    fun setIconPack(packageName: String) {
        _selectedIconPack.value = packageName
        prefs.edit().putString("icon_pack", packageName).apply()
    }

    private fun readIconShape(): IconShape =
        runCatching { IconShape.valueOf(prefs.getString("icon_shape", "CIRCLE") ?: "CIRCLE") }
            .getOrDefault(IconShape.CIRCLE)

    private val _iconShape = MutableStateFlow(readIconShape())
    val iconShape: StateFlow<IconShape> = _iconShape.asStateFlow()

    fun setIconShape(shape: IconShape) {
        _iconShape.value = shape
        prefs.edit().putString("icon_shape", shape.name).apply()
    }

    private fun readHiddenApps(): Set<String> =
        (prefs.getString("hidden_apps", "") ?: "").split(",").filter { it.isNotEmpty() }.toSet()

    private val _hiddenApps = MutableStateFlow(readHiddenApps())
    val hiddenApps: StateFlow<Set<String>> = _hiddenApps.asStateFlow()

    fun hideApp(packageName: String) {
        val updated = _hiddenApps.value + packageName
        _hiddenApps.value = updated
        prefs.edit().putString("hidden_apps", updated.joinToString(",")).apply()
    }

    fun unhideApp(packageName: String) {
        val updated = _hiddenApps.value - packageName
        _hiddenApps.value = updated
        prefs.edit().putString("hidden_apps", updated.joinToString(",")).apply()
    }

    fun refreshFromPrefs() {
        _hiddenApps.value = readHiddenApps()
    }

    private fun readIconOverrides(): Map<String, String> =
        prefs.all.entries
            .filter { it.key.startsWith("icon_override_") && it.value is String && (it.value as String).isNotBlank() }
            .associate { it.key.removePrefix("icon_override_") to it.value as String }

    private val _iconOverrides = MutableStateFlow(readIconOverrides())
    val iconOverrides: StateFlow<Map<String, String>> = _iconOverrides.asStateFlow()

    private val _overrideIcons = MutableStateFlow<Map<String, Drawable>>(emptyMap())
    val overrideIcons: StateFlow<Map<String, Drawable>> = _overrideIcons.asStateFlow()

    private val _wallpaperRevision = MutableStateFlow(0)
    private val wallpaperReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) { _wallpaperRevision.value++ }
    }

    fun setIconOverride(appPkg: String, iconPackPkg: String?) {
        val updated = _iconOverrides.value.toMutableMap()
        if (iconPackPkg == null) {
            updated.remove(appPkg)
            prefs.edit().remove("icon_override_$appPkg").apply()
        } else {
            updated[appPkg] = iconPackPkg
            prefs.edit().putString("icon_override_$appPkg", iconPackPkg).apply()
        }
        _iconOverrides.value = updated
    }

    /** Apps filtered by search query and hidden list, sorted A–Z. */
    val filteredApps: StateFlow<List<AppInfo>> = combine(allApps, _query, _hiddenApps) { apps, q, hidden ->
        apps
            .filter { it.packageName !in hidden }
            .filter { q.isBlank() || it.label.contains(q, ignoreCase = true) }
            .sortedBy { it.label.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Maps each first letter (uppercase) to the flat list index of its first app. */
    val letterIndex: StateFlow<Map<Char, Int>> = filteredApps.map { apps ->
        buildMap {
            apps.forEachIndexed { index, app ->
                val ch = app.label.firstOrNull()?.uppercaseChar() ?: '#'
                val key = if (ch.isLetter()) ch else '#'
                if (!containsKey(key)) put(key, index)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _gridColumns = MutableStateFlow(prefs.getInt("grid_columns", 4))
    val gridColumns: StateFlow<Int> = _gridColumns.asStateFlow()

    private fun readViewMode(): DrawerViewMode =
        if (prefs.getString("drawer_view_mode", "Grid") == "List") DrawerViewMode.List
        else DrawerViewMode.Grid

    private val _viewMode = MutableStateFlow(readViewMode())
    val viewMode: StateFlow<DrawerViewMode> = _viewMode.asStateFlow()

    fun setViewMode(mode: DrawerViewMode) {
        _viewMode.value = mode
        prefs.edit().putString("drawer_view_mode", mode.name).apply()
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "grid_columns"      -> _gridColumns.value = prefs.getInt("grid_columns", 4)
            "hidden_apps"       -> _hiddenApps.value  = readHiddenApps()
            "drawer_view_mode"  -> _viewMode.value    = readViewMode()
            "icon_pack"         -> _selectedIconPack.value = prefs.getString("icon_pack", "") ?: ""
            "icon_shape"        -> _iconShape.value   = readIconShape()
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        @Suppress("DEPRECATION")
        context.registerReceiver(wallpaperReceiver, IntentFilter(Intent.ACTION_WALLPAPER_CHANGED))

        // Load installed icon packs once on a background thread
        viewModelScope.launch(Dispatchers.IO) {
            _installedPacks.value = iconPackRepo.getInstalledPacks()
        }

        // Rebuild the icon cache whenever allApps, selectedIconPack, or wallpaper changes
        viewModelScope.launch(Dispatchers.IO) {
            combine(allApps, _selectedIconPack, _wallpaperRevision) { apps, pack, _ ->
                Pair(apps, pack)
            }.collect { (apps, pack) ->
                if (pack.isBlank()) {
                    _resolvedIcons.value = emptyMap()
                } else {
                    val mappings = iconPackRepo.loadMappings(pack)
                    val icons = mutableMapOf<String, Drawable>()
                    for (app in apps) {
                        val icon = iconPackRepo.getIcon(pack, mappings, app.packageName, app.activityName)
                        if (icon != null) icons[app.packageName] = icon
                    }
                    _resolvedIcons.value = icons
                }
            }
        }

        // Rebuild per-app icon overrides; value format is "packPkg:drawableName"
        viewModelScope.launch(Dispatchers.IO) {
            combine(_iconOverrides, _wallpaperRevision) { overrides, _ -> overrides }
                .collect { overrides ->
                    val icons = mutableMapOf<String, Drawable>()
                    for ((appPkg, overrideValue) in overrides) {
                        val colonIdx = overrideValue.indexOf(':')
                        if (colonIdx < 1) continue
                        val packPkg = overrideValue.substring(0, colonIdx)
                        val drawableName = overrideValue.substring(colonIdx + 1)
                        val icon = iconPackRepo.getIconByDrawableName(packPkg, drawableName)
                        if (icon != null) icons[appPkg] = icon
                    }
                    _overrideIcons.value = icons
                }
        }
    }

    override fun onCleared() {
        context.unregisterReceiver(wallpaperReceiver)
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    companion object {
        fun factory(context: Context) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return DrawerViewModel(
                    InstalledAppsRepository(context.applicationContext),
                    context.applicationContext.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE),
                    IconPackRepository(context.applicationContext),
                    context.applicationContext
                ) as T
            }
        }
    }
}
