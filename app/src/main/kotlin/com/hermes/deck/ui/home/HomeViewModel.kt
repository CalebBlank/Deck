package com.hermes.deck.ui.home

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hermes.deck.data.AppInfo
import com.hermes.deck.data.RecentAppsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class HomeUiState(
    val recentApps: List<AppInfo> = emptyList(),
    val hasUsagePermission: Boolean = true
)

class HomeViewModel(
    private val repo: RecentAppsRepository,
    private val prefs: SharedPreferences,
    private val selfPackageName: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _cycleEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val cycleEvent: SharedFlow<Unit> = _cycleEvent.asSharedFlow()

    private val _focusEvent = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val focusEvent: SharedFlow<Int> = _focusEvent.asSharedFlow()

    // Updated by CardStrip whenever the pager page changes; used to place new cards to the right.
    private var lastFocusedIndex = 0
    fun updateFocusedIndex(index: Int) { lastFocusedIndex = index }

    private val _pendingKeyInput = MutableStateFlow("")
    val pendingKeyInput: StateFlow<String> = _pendingKeyInput.asStateFlow()

    fun appendKeyInput(char: Char) { _pendingKeyInput.update { it + char } }
    fun clearKeyInput() { _pendingKeyInput.value = "" }

    private val _backspaceEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val backspaceEvent: SharedFlow<Unit> = _backspaceEvent.asSharedFlow()
    fun backspaceKeyInput() { _backspaceEvent.tryEmit(Unit) }

    // Fired by MainActivity.onResume() and onNewIntent() so the drawer closes when the
    // user presses the home button or uses the system home gesture while Deck is open.
    private val _drawerCloseEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val drawerCloseEvent: SharedFlow<Unit> = _drawerCloseEvent.asSharedFlow()
    fun requestDrawerClose() { _drawerCloseEvent.tryEmit(Unit) }

    private val _pinnedPackages = MutableStateFlow(
        prefs.getString("pinned", "")!!
            .split(",")
            .filter { it.isNotEmpty() }
    )
    val pinnedPackages: StateFlow<List<String>> = _pinnedPackages.asStateFlow()

    private val dismissedPackages = mutableMapOf<String, Long>()

    private val _overviewMode = MutableStateFlow(false)
    val overviewMode: StateFlow<Boolean> = _overviewMode.asStateFlow()

    fun enterOverviewMode() { _overviewMode.value = true }
    fun exitOverviewMode()  { _overviewMode.value = false }

    fun moveCardLeft(app: AppInfo) {
        val apps = _uiState.value.recentApps.toMutableList()
        val idx = apps.indexOf(app)
        if (idx > 0) {
            apps[idx] = apps[idx - 1].also { apps[idx - 1] = apps[idx] }
            _uiState.update { it.copy(recentApps = apps) }
        }
    }

    fun moveCardRight(app: AppInfo) {
        val apps = _uiState.value.recentApps.toMutableList()
        val idx = apps.indexOf(app)
        if (idx in 0 until apps.lastIndex) {
            apps[idx] = apps[idx + 1].also { apps[idx + 1] = apps[idx] }
            _uiState.update { it.copy(recentApps = apps) }
        }
    }

    init {
        refresh()
        viewModelScope.launch {
            while (true) {
                delay(30_000L)
                refresh()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val hasPermission = repo.hasUsagePermission()
            _uiState.update { it.copy(hasUsagePermission = hasPermission) }
            if (!hasPermission) return@launch
            val hideSelf           = prefs.getBoolean("hide_self_from_cards", true)
            val hideSystemSettings = prefs.getBoolean("hide_system_settings", true)
            repo.getRecentApps().collect { apps ->
                val freshlyRelaunched = mutableSetOf<String>()
                val filtered = apps.filter { app ->
                    val dismissedAt = dismissedPackages[app.packageName]
                    val notDismissed = dismissedAt == null || app.lastUsed > dismissedAt
                    if (notDismissed && dismissedAt != null) freshlyRelaunched.add(app.packageName)
                    notDismissed &&
                    !(hideSelf && app.packageName == selfPackageName) &&
                    !(hideSystemSettings && app.packageName == "com.android.settings")
                }
                freshlyRelaunched.forEach { dismissedPackages.remove(it) }
                val currentList = _uiState.value.recentApps
                if (currentList.isEmpty()) {
                    // First load — show in recency order, jump to most recent.
                    _uiState.update { it.copy(recentApps = filtered) }
                    if (filtered.isNotEmpty()) _focusEvent.tryEmit(0)
                    return@collect
                }
                val currentPkgs  = currentList.map { it.packageName }.toSet()
                val filteredPkgs = filtered.map { it.packageName }.toSet()

                // Keep existing cards in their current positions (don't reorder them).
                val stillPresent = currentList.filter { it.packageName in filteredPkgs }

                // Apps that just appeared (weren't in the row before).
                val newApps = filtered.filter { it.packageName !in currentPkgs }

                val result = if (newApps.isNotEmpty()) {
                    // Insert new cards immediately to the right of the focused card.
                    val insertAt = (lastFocusedIndex + 1).coerceAtMost(stillPresent.size)
                    stillPresent.subList(0, insertAt) + newApps + stillPresent.subList(insertAt, stillPresent.size)
                } else {
                    stillPresent
                }

                _uiState.update { it.copy(recentApps = result) }

                if (newApps.isNotEmpty()) {
                    // Scroll to the first newly inserted card.
                    val focusIdx = (lastFocusedIndex + 1).coerceAtMost(result.size - 1)
                    _focusEvent.tryEmit(focusIdx)
                }
                // Existing apps that were re-used keep their position — no focus event.
            }
        }
    }

    fun dismissCard(app: AppInfo) {
        dismissedPackages[app.packageName] = System.currentTimeMillis()
        repo.killApp(app.packageName)
        _uiState.update { state ->
            state.copy(recentApps = state.recentApps.filter { it.packageName != app.packageName })
        }
    }

    fun cycleCard() { _cycleEvent.tryEmit(Unit) }

    fun pinApp(packageName: String) {
        val updated = (_pinnedPackages.value + packageName).distinct().take(4)
        _pinnedPackages.value = updated
        savePin(updated)
    }

    fun unpinApp(packageName: String) {
        val updated = _pinnedPackages.value.filter { it != packageName }
        _pinnedPackages.value = updated
        savePin(updated)
    }

    fun reloadPinned() {
        _pinnedPackages.value = prefs.getString("pinned", "")!!
            .split(",").filter { it.isNotEmpty() }
    }

    private fun savePin(packages: List<String>) {
        prefs.edit().putString("pinned", packages.joinToString(",")).apply()
    }

    companion object {
        fun factory(context: Context) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return HomeViewModel(
                    RecentAppsRepository(context.applicationContext),
                    context.applicationContext.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE),
                    context.applicationContext.packageName
                ) as T
            }
        }
    }
}
