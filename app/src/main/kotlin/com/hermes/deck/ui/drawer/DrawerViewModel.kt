package com.hermes.deck.ui.drawer

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hermes.deck.data.AppInfo
import com.hermes.deck.data.InstalledAppsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class DrawerViewMode { Grid, List }

class DrawerViewModel(
    installedRepo: InstalledAppsRepository,
    private val prefs: SharedPreferences
) : ViewModel() {

    private val allApps: StateFlow<List<AppInfo>> = installedRepo.getAllApps()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _query = MutableStateFlow("")

    private fun readHiddenApps(): Set<String> =
        prefs.getString("hidden_apps", "")!!.split(",").filter { it.isNotEmpty() }.toSet()

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
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    companion object {
        fun factory(context: Context) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return DrawerViewModel(
                    InstalledAppsRepository(context.applicationContext),
                    context.applicationContext.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE)
                ) as T
            }
        }
    }
}
