package com.hermes.deck.ui.home

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hermes.deck.data.AppInfo
import com.hermes.deck.data.CardGroup
import com.hermes.deck.data.LivePreviewRepository
import com.hermes.deck.data.RecentAppsRepository
import com.hermes.deck.service.BrowserTabEventBus
import com.hermes.deck.service.BrowserTabReceiver
import com.hermes.deck.service.ForegroundEventBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "DeckStack"

data class HomeUiState(
    val cardGroups: List<CardGroup> = emptyList(),
    val hasUsagePermission: Boolean = true,
    val hasAccessibilityService: Boolean = true,
    val expandedGroup: CardGroup? = null
)

class HomeViewModel(
    private val appContext: Context,
    private val repo: RecentAppsRepository,
    private val prefs: SharedPreferences,
    private val livePreviewRepo: LivePreviewRepository,
    private val selfPackageName: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _cycleEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val cycleEvent: SharedFlow<Unit> = _cycleEvent.asSharedFlow()

    private val _focusEvent = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val focusEvent: SharedFlow<Int> = _focusEvent.asSharedFlow()

    private var lastFocusedIndex = 0
    fun updateFocusedIndex(index: Int) { lastFocusedIndex = index }

    // Package of the app most recently in the foreground (tracked via ForegroundEventBus).
    // A newly-opened tab/app is inserted immediately to the right of THIS app's card, so it
    // lands next to the app it was launched from rather than at the pager's last scroll
    // position. Null until the first foreground event (then we fall back to lastFocusedIndex).
    private var lastUsedPackage: String? = null

    /** Insertion index for a new card: just right of [anchorPkg]'s card, else lastFocusedIndex+1. */
    private fun insertIndexAfter(anchorPkg: String?, groups: List<CardGroup>): Int {
        val idx = if (anchorPkg != null)
            groups.indexOfFirst { g -> g.apps.any { it.packageName == anchorPkg } } else -1
        return if (idx >= 0) (idx + 1).coerceAtMost(groups.size)
               else (lastFocusedIndex + 1).coerceAtMost(groups.size)
    }


    private val _pendingKeyInput = MutableStateFlow("")
    val pendingKeyInput: StateFlow<String> = _pendingKeyInput.asStateFlow()

    fun appendKeyInput(char: Char) { _pendingKeyInput.update { it + char } }
    fun clearKeyInput() { _pendingKeyInput.value = "" }

    private val _backspaceEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val backspaceEvent: SharedFlow<Unit> = _backspaceEvent.asSharedFlow()
    fun backspaceKeyInput() { _backspaceEvent.tryEmit(Unit) }

    private val _drawerCloseEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val drawerCloseEvent: SharedFlow<Unit> = _drawerCloseEvent.asSharedFlow()
    fun requestDrawerClose() { _drawerCloseEvent.tryEmit(Unit) }

    private val _pinnedPackages = MutableStateFlow(
        (prefs.getString("pinned", "") ?: "")
            .split(",")
            .filter { it.isNotEmpty() }
    )
    val pinnedPackages: StateFlow<List<String>> = _pinnedPackages.asStateFlow()

    private val dismissedPackages = mutableMapOf<String, Long>()

    // Set once the browser's live-tab enumeration reply has arrived; lets a slow restore replay
    // refuse to re-add a tab the browser reported dead. @Volatile because events.collect (Default
    // dispatcher) reads them while reconcileBrowserTabs (also a coroutine) writes them.
    @Volatile private var tabsEnumerated = false
    @Volatile private var liveTabIds: Set<Int> = emptySet()

    init {
        refresh()
        refreshPreviews()
        viewModelScope.launch {
            ForegroundEventBus.foregroundPackage.collect { pkg ->
                if (pkg == selfPackageName) return@collect
                if (!repo.isLaunchable(pkg)) return@collect
                // Capture the app we're coming FROM (previous foreground) as the insertion
                // anchor, then record this app as the new most-recently-used.
                val anchorPkg = lastUsedPackage
                lastUsedPackage = pkg
                // Browser tabs are handled via BrowserTabReceiver — skip insertion here.
                if (pkg == BrowserTabReceiver.BROWSER_PACKAGE) return@collect
                val currentGroups = _uiState.value.cardGroups
                val allPkgs = currentGroups.flatMap { it.apps }.map { it.packageName }.toSet()
                if (pkg in allPkgs) return@collect
                if (dismissedPackages.containsKey(pkg)) return@collect
                val appInfo = withContext(Dispatchers.IO) { repo.resolveApp(pkg) } ?: return@collect
                val insertAt = insertIndexAfter(anchorPkg, currentGroups)
                val newList = currentGroups.toMutableList().also {
                    it.add(insertAt, CardGroup.single(appInfo))
                }
                _uiState.update { it.copy(cardGroups = newList) }
                _focusEvent.tryEmit(insertAt)
            }
        }

        viewModelScope.launch {
            BrowserTabEventBus.events.collect { (pkg, taskId, eventParent, isRestore) ->
                Log.d(TAG, "New tab event: pkg=$pkg taskId=$taskId parent=$eventParent restore=$isRestore")
                // A restored tab whose task the browser has since reported dead (via enumeration)
                // must not be re-added. The collect lambda is sequential, so a standalone tab's
                // 2s parent-heuristic delay can let it land AFTER reconcile already ran with an
                // empty live list — this guard catches that race (and skips the wasted delay).
                if (isRestore && tabsEnumerated && taskId !in liveTabIds) {
                    unpersistBrowserTab(taskId)
                    return@collect
                }
                // Prefer the launching app reported by the browser (Activity.getReferrer()) — it's
                // exact. Only fall back to the UsageStats heuristic when the referrer is unknown.
                var parentPackage = eventParent?.takeIf { it != pkg && it != selfPackageName }
                if (parentPackage == null) {
                    // UsageStats MOVE_TO_BACKGROUND events can lag 1–2 s after an app backgrounds.
                    parentPackage = withContext(Dispatchers.IO) {
                        repo.findRecentlyBackgroundedApp(5_000, setOf(pkg, selfPackageName))
                    }
                    Log.d(TAG, "Parent (immediate): $parentPackage")
                    if (parentPackage == null) {
                        delay(2_000L)
                        parentPackage = withContext(Dispatchers.IO) {
                            repo.findRecentlyBackgroundedApp(8_000, setOf(pkg, selfPackageName))
                        }
                        Log.d(TAG, "Parent (after retry): $parentPackage")
                    }
                }
                val resolvedParent = parentPackage ?: pkg
                Log.d(TAG, "Resolved parent: $resolvedParent")
                val appInfo = withContext(Dispatchers.IO) {
                    repo.resolveAppForTask(pkg, taskId, System.currentTimeMillis())
                } ?: return@collect
                // Re-check AFTER the (up-to-2s) parent heuristic: the first slow standalone restore
                // is dequeued before the enumerate reply lands, so the top-of-block guard sees
                // tabsEnumerated=false and waits out the delay. By now the reply may have arrived and
                // reported this task dead — don't add (or re-persist) it. (The top guard still earns
                // its keep: it skips the 2s delay for tabs already known dead when dequeued.)
                if (isRestore && tabsEnumerated && taskId !in liveTabIds) {
                    unpersistBrowserTab(taskId)
                    return@collect
                }
                var focusIdx = -1
                _uiState.update { state ->
                    val groups = state.cardGroups.toMutableList()
                    val isBrowserTab = pkg == BrowserTabReceiver.BROWSER_PACKAGE
                    val idx = if (resolvedParent != pkg) {
                        val parentIdx = groups.indexOfFirst { g -> g.apps.any { it.packageName == resolvedParent } }
                        when {
                            parentIdx >= 0 -> parentIdx
                            // Each browser tab is its own card -- don't merge sibling tabs into one stack
                            // (that made tapping a 2nd+ tab EXPAND the stack instead of opening the tab).
                            isBrowserTab -> -1
                            else -> groups.indexOfFirst { g -> g.apps.any { it.packageName == pkg } }
                        }
                    } else if (isBrowserTab) {
                        -1
                    } else {
                        groups.indexOfFirst { g -> g.apps.any { it.packageName == pkg } }
                    }
                    Log.d(TAG, "Stack idx=$idx groups=${groups.map { g -> "${g.primaryApp.packageName}(${g.apps.size})" }}")
                    if (idx < 0) {
                        // Idempotent: if a card for this exact task already exists, don't duplicate it.
                        // (On resume, reconcile's add-half can emit a NewTabEvent for a tab the restore
                        // replay also emits — without this, the standalone path would add it twice.)
                        val alreadyPresent = taskId != -1 && groups.any { g ->
                            g.apps.any { it.packageName == pkg && it.taskId == taskId }
                        }
                        if (alreadyPresent) return@update state
                        val insertAt = insertIndexAfter(lastUsedPackage, groups)
                        groups.add(insertAt, CardGroup.single(appInfo))
                        focusIdx = insertAt
                        Log.d(TAG, "Added standalone at $insertAt")
                    } else {
                        // Keep origin apps (non-browser) regardless of taskId — they may have
                        // taskId=-1 when root is unavailable and should not be dropped.
                        // For browser entries: drop ghosts (taskId=-1) and dedup the incoming tab.
                        val existing = groups[idx].apps.filter { app ->
                            if (app.packageName == pkg) app.taskId != -1 && app.taskId != taskId
                            else true
                        }
                        val merged = existing + appInfo
                        val updatedGroup = if (merged.size == 1) CardGroup.single(merged.first())
                                           else CardGroup.stack(merged)
                        groups[idx] = updatedGroup
                        // If refresh() ran during the retry delay it may have added a standalone
                        // browser card for this same task. Remove it now that the tab is in a stack.
                        val dupeIdx = groups.indices.firstOrNull { i ->
                            i != idx && groups[i].apps.any { it.packageName == pkg && it.taskId == taskId }
                        }
                        if (dupeIdx != null) groups.removeAt(dupeIdx)
                        // Keep expandedGroup in sync if this stack is currently open.
                        val newExpanded = if (state.expandedGroup?.apps?.any {
                            it.packageName == resolvedParent || it.packageName == pkg
                        } == true) updatedGroup else state.expandedGroup
                        return@update state.copy(cardGroups = groups, expandedGroup = newExpanded)
                    }
                    state.copy(cardGroups = groups)
                }
                if (focusIdx >= 0) _focusEvent.tryEmit(focusIdx)
                // Persist so the card survives a Deck restart (browser tabs are excludeFromRecents,
                // so refresh() can't rediscover them).
                persistBrowserTab(taskId, resolvedParent)
            }
        }

        // Restore browser-tab cards persisted from a previous session. Browser tabs don't appear in
        // UsageStats (excludeFromRecents), so without this they vanish whenever Deck rebuilds its
        // deck. Replayed through the same event path; the persisted parent skips the parent heuristic.
        // After restoring optimistically, ask the browser which tab tasks are actually still live
        // (it's the only process that can see its own excludeFromRecents tasks) and reconcile.
        viewModelScope.launch {
            kotlinx.coroutines.delay(150)
            loadPersistedBrowserTabs().forEach { (taskId, parent) ->
                BrowserTabEventBus.emit(
                    BrowserTabEventBus.NewTabEvent(
                        BrowserTabReceiver.BROWSER_PACKAGE, taskId, parent, isRestore = true,
                    ),
                )
            }
            requestBrowserTabsEnumeration()
        }

        // Reconcile against the browser's authoritative live-tab list. Drop (and unpersist) any
        // browser-tab card whose task is gone; add cards for live tabs Deck didn't know about.
        // If the browser isn't installed or never replies, this simply never fires and the
        // optimistically-restored cards stand — no crash, no loss.
        viewModelScope.launch {
            BrowserTabEventBus.tabsList.collect { liveIds ->
                reconcileBrowserTabs(liveIds)
            }
        }

        // The reopen trampoline reports when a browser tab task is genuinely gone — drop the
        // dead card so it doesn't linger as an un-openable phantom (browser icon, no preview).
        viewModelScope.launch {
            BrowserTabEventBus.tabGone.collect { taskId ->
                unpersistBrowserTab(taskId)
                _uiState.update { state ->
                    val groups = state.cardGroups.mapNotNull { group ->
                        val kept = group.apps.filterNot {
                            it.packageName == BrowserTabReceiver.BROWSER_PACKAGE && it.taskId == taskId
                        }
                        when {
                            kept.isEmpty()        -> null
                            kept.size == group.apps.size -> group
                            kept.size == 1        -> CardGroup.single(kept.first())
                            else                  -> group.copy(apps = kept)
                        }
                    }
                    state.copy(cardGroups = groups)
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val hasPermission = repo.hasUsagePermission()
            val hasA11y = repo.hasAccessibilityService()
            _uiState.update { it.copy(hasUsagePermission = hasPermission, hasAccessibilityService = hasA11y) }
            if (!hasPermission) return@launch
            val hideSelf           = prefs.getBoolean("hide_self_from_cards", true)
            val hideSystemSettings = prefs.getBoolean("hide_system_settings", true)
            val taskMap            = livePreviewRepo.getLiveTasks()
            val livePackages       = taskMap.values.toSet()
            // Never expand Deck into multiple cards — if a stray standard task coexists with the
            // home task, it should still surface as a single Deck card (when not hidden).
            val multiTaskPkgs      = taskMap.values.groupingBy { it }.eachCount()
                .filter { it.value > 1 }.keys - selfPackageName

            repo.getRecentApps().collect { apps ->
                // When root is available, restrict to apps that have a live task in the system
                // recents stack. Falls back to the full UsageStats list only if getLiveTasks()
                // returned nothing (root unavailable or su timed out).
                val liveFiltered = if (livePackages.isNotEmpty())
                    apps.filter { it.packageName in livePackages }
                else
                    apps

                // For packages with multiple concurrent tasks, expand to one AppInfo per task.
                val expanded = mutableListOf<AppInfo>()
                for (app in liveFiltered) {
                    if (app.packageName in multiTaskPkgs) {
                        taskMap.entries
                            .filter { it.value == app.packageName }
                            .mapNotNull { (taskId, pkg) -> repo.resolveAppForTask(pkg, taskId, app.lastUsed) }
                            .forEach { expanded.add(it) }
                    } else {
                        // Resolve taskId even for single-instance apps so moveTaskToFront() works
                        val taskId = taskMap.entries.firstOrNull { it.value == app.packageName }?.key
                        expanded.add(if (taskId != null) app.copy(taskId = taskId) else app)
                    }
                }

                val freshlyRelaunched = mutableSetOf<String>()
                val filtered = expanded.filter { app ->
                    val dismissedAt = dismissedPackages[app.id]
                    // For multi-task apps (taskId != -1) app.lastUsed is a per-package
                    // UsageStats timestamp — using any tab of that app updates it, so it
                    // cannot signal that *this specific task* was relaunched after dismissal.
                    // Only single-task entries (taskId == -1) use the relaunch heuristic.
                    val notDismissed = dismissedAt == null ||
                        (app.taskId == -1 && app.lastUsed > dismissedAt)
                    if (notDismissed && dismissedAt != null) freshlyRelaunched.add(app.id)
                    notDismissed &&
                    !(hideSelf && app.packageName == selfPackageName) &&
                    !(hideSystemSettings && app.packageName == "com.android.settings")
                }
                freshlyRelaunched.forEach { dismissedPackages.remove(it) }

                val currentGroups = _uiState.value.cardGroups
                if (currentGroups.isEmpty()) {
                    val initial = filtered.map { CardGroup.single(it) }
                    _uiState.update { it.copy(cardGroups = initial) }
                    if (initial.isNotEmpty()) _focusEvent.tryEmit(0)
                    return@collect
                }

                // Match existing cards to refreshed data. Exact id match is preferred; fall back to
                // package-name match only when the refreshed entry has taskId=-1 (meaning
                // getLiveTasks() failed or the package is now single-task). This prevents cards
                // from disappearing when a transient root/su failure returns an empty task map.
                val filteredById  = filtered.associateBy { it.id }
                val filteredByPkg = filtered.groupBy { it.packageName }
                val stillPresent = currentGroups.mapNotNull { group ->
                    val remaining = group.apps.mapNotNull { existing ->
                        // Browser tabs use excludeFromRecents="true" so they never appear in
                        // taskMap regardless of root availability. Keep them with their original
                        // taskId; only explicit user dismissal should remove them.
                        if (existing.packageName == BrowserTabReceiver.BROWSER_PACKAGE &&
                                existing.taskId != -1) {
                            existing
                        } else {
                            filteredById[existing.id]
                                ?: filteredByPkg[existing.packageName]?.let { candidates ->
                                    if (existing.packageName !in multiTaskPkgs) {
                                        // Single-instance app: it's the same app regardless of taskId.
                                        // The existing card may carry taskId=-1 (created from a
                                        // foreground event) while refresh() resolves a live taskId —
                                        // an exact-id match would FAIL and drop the card, which splits
                                        // any stack it belongs to (e.g. Inoreader + its browser tabs)
                                        // and then re-spawns it standalone. Match by package instead so
                                        // it keeps its place in the group.
                                        candidates.firstOrNull()
                                    } else {
                                        // Multi-task package: taskId distinguishes instances. Only the
                                        // legacy taskId=-1 fallback carries the old id forward when
                                        // getLiveTasks() failed completely (taskMap empty).
                                        candidates.firstOrNull { it.taskId == -1 }
                                            ?.let { if (taskMap.isEmpty()) it.copy(taskId = existing.taskId) else it }
                                    }
                                }
                                ?: if (taskMap.isEmpty() ||
                                       (existing.taskId != -1 && taskMap.containsKey(existing.taskId)))
                                       existing else null
                        }
                    }
                    when {
                        remaining.isEmpty() -> null
                        remaining.size == 1 -> CardGroup.single(remaining.first())
                        else -> group.copy(apps = remaining)
                    }
                }
                val presentIds      = stillPresent.flatMap { it.apps }.map { it.id }.toSet()
                val presentPackages = stillPresent.flatMap { it.apps }.map { it.packageName }.toSet()
                // For multi-task entries (taskId != -1) check by exact id so new tasks for the
                // same package (e.g. a new browser tab) still appear as new cards.
                val newApps = filtered.filter { app ->
                    if (app.taskId != -1) app.id !in presentIds
                    else app.packageName !in presentPackages
                }
                val newGroups  = newApps.map { CardGroup.single(it) }

                val insertAt = insertIndexAfter(lastUsedPackage, stillPresent)
                val result = if (newGroups.isNotEmpty()) {
                    stillPresent.subList(0, insertAt) + newGroups + stillPresent.subList(insertAt, stillPresent.size)
                } else {
                    stillPresent
                }

                _uiState.update { it.copy(cardGroups = result) }
                if (newGroups.isNotEmpty()) {
                    _focusEvent.tryEmit(insertAt.coerceAtMost(result.size - 1))
                }
            }
        }
    }

    fun dismissGroup(group: CardGroup) {
        group.apps.forEach { app ->
            dismissedPackages[app.id] = System.currentTimeMillis()
            // Browser tabs share a process — killing by package kills ALL tabs.
            // Only kill single-task app instances.
            val isBrowserTab = app.packageName == BrowserTabReceiver.BROWSER_PACKAGE && app.taskId != -1
            if (!isBrowserTab) {
                repo.killApp(app.packageName)
            } else {
                // Close just this tab's task so it also leaves system recents (Deck and native
                // recents stay in sync), and unpersist it so it isn't restored on next Deck launch
                // — and so the resume reconcile doesn't re-add it from the still-live task.
                closeBrowserTab(app.taskId)
                unpersistBrowserTab(app.taskId)
            }
        }
        viewModelScope.launch {
            group.apps.forEach { app ->
                val isBrowserTab = app.packageName == BrowserTabReceiver.BROWSER_PACKAGE && app.taskId != -1
                if (!isBrowserTab) livePreviewRepo.removeTask(app.packageName)
            }
        }
        _uiState.update { state ->
            state.copy(cardGroups = state.cardGroups.filter { it.id != group.id })
        }
    }

    fun reorderGroups(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            val groups = state.cardGroups.toMutableList()
            if (fromIndex !in groups.indices || toIndex !in groups.indices) return@update state
            val moved = groups.removeAt(fromIndex)
            groups.add(toIndex, moved)
            state.copy(cardGroups = groups)
        }
    }

    fun stackGroups(sourceIndex: Int, targetIndex: Int) {
        _uiState.update { state ->
            val groups = state.cardGroups.toMutableList()
            if (sourceIndex !in groups.indices || targetIndex !in groups.indices) return@update state
            if (sourceIndex == targetIndex) return@update state
            val lo     = minOf(sourceIndex, targetIndex)
            val hi     = maxOf(sourceIndex, targetIndex)
            // Target is the "origin" card — keep it on top (front). Source goes below.
            val merged = CardGroup.stack(groups[targetIndex].apps + groups[sourceIndex].apps)
            groups.removeAt(hi)
            groups.removeAt(lo)
            groups.add(lo, merged)
            state.copy(cardGroups = groups)
        }
    }

    fun unstackGroup(groupIndex: Int, cardIndex: Int) {
        _uiState.update { state ->
            val groups = state.cardGroups.toMutableList()
            val group  = groups.getOrNull(groupIndex) ?: return@update state
            if (!group.isStack || cardIndex !in group.apps.indices) return@update state
            val pulled    = group.apps[cardIndex]
            val remaining = group.apps.toMutableList().also { it.removeAt(cardIndex) }
            groups[groupIndex] = if (remaining.size == 1) CardGroup.single(remaining.first())
                                  else CardGroup.stack(remaining)
            groups.add(groupIndex + 1, CardGroup.single(pulled))
            state.copy(cardGroups = groups)
        }
    }

    fun removeFromExpandedStack(appId: String) {
        val expanded = _uiState.value.expandedGroup ?: return
        val app = expanded.apps.firstOrNull { it.id == appId } ?: return
        dismissedPackages[appId] = System.currentTimeMillis()
        // Multi-task apps (taskId != -1) share a process — killing by package name would close
        // every tab/task for that app. Only kill single-task apps.
        if (app.taskId == -1) repo.killApp(app.packageName)
        viewModelScope.launch {
            val isBrowserTab = app.packageName == BrowserTabReceiver.BROWSER_PACKAGE && app.taskId != -1
            if (!isBrowserTab) livePreviewRepo.removeTask(app.packageName)
        }
        val remaining = expanded.apps.filter { it.id != appId }
        _uiState.update { state ->
            val updatedGroups = state.cardGroups.mapNotNull { group ->
                if (group.id != expanded.id) group
                else when {
                    remaining.isEmpty() -> null
                    remaining.size == 1 -> CardGroup.single(remaining.first())
                    else -> CardGroup.stack(remaining)
                }
            }
            val newExpanded = if (remaining.size > 1) CardGroup.stack(remaining) else null
            state.copy(cardGroups = updatedGroups, expandedGroup = newExpanded)
        }
    }

    fun dissolveStack(group: CardGroup) {
        _uiState.update { state ->
            val idx = state.cardGroups.indexOfFirst { it.id == group.id }
            if (idx < 0) return@update state
            val groups = state.cardGroups.toMutableList()
            groups.removeAt(idx)
            group.apps.forEachIndexed { i, app -> groups.add(idx + i, CardGroup.single(app)) }
            state.copy(cardGroups = groups)
        }
    }

    fun expandStack(group: CardGroup) {
        if (!group.isStack) return
        _uiState.update { it.copy(expandedGroup = group) }
        _focusEvent.tryEmit(0)
    }

    fun collapseStack() {
        val group = _uiState.value.expandedGroup ?: return
        _uiState.update { it.copy(expandedGroup = null) }
        val idx = _uiState.value.cardGroups.indexOfFirst { it.id == group.id }
        if (idx >= 0) _focusEvent.tryEmit(idx)
    }

    fun refreshPreviews() {
        viewModelScope.launch {
            livePreviewRepo.refreshAll()
            // WMS writes snapshots asynchronously when an app backgrounds; retry after a short
            // delay to pick up any files that weren't ready on the first pass.
            delay(1500L)
            livePreviewRepo.refreshAll()
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

    // ---- Browser-tab persistence ----------------------------------------------------------------
    // Browser tabs are excludeFromRecents Activity tasks, so Deck's UsageStats refresh() can never
    // rediscover them. We persist each open tab's (taskId, parentPackage) in a StringSet so the
    // cards survive a Deck process restart / deck rebuild. Entries are "taskId|parent" (parent may
    // be empty). The SharedPreferences-returned Set is read-only; always copy before mutating.

    /** Persisted (taskId, parent-or-null) pairs; malformed entries are skipped. */
    private fun loadPersistedBrowserTabs(): List<Pair<Int, String?>> {
        val raw = prefs.getStringSet(PREF_BROWSER_TABS, emptySet()) ?: emptySet()
        return raw.mapNotNull { entry ->
            val parts = entry.split("|", limit = 2)
            val taskId = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val parent = parts.getOrNull(1)?.takeIf { it.isNotEmpty() }
            taskId to parent
        }
    }

    private fun persistBrowserTab(taskId: Int, parent: String?) {
        // Copy the read-only SharedPreferences set before mutating (mutating it directly is UB).
        val updated = (prefs.getStringSet(PREF_BROWSER_TABS, emptySet()) ?: emptySet()).toMutableSet()
        updated.removeAll { it.substringBefore("|").toIntOrNull() == taskId }
        updated.add("$taskId|${parent ?: ""}")
        prefs.edit().putStringSet(PREF_BROWSER_TABS, updated).apply()
    }

    /** Tell the browser to finish a specific tab task so it leaves system recents. No-op-safe if the
     *  browser isn't installed. */
    private fun closeBrowserTab(taskId: Int) {
        runCatching {
            appContext.sendBroadcast(
                Intent(BrowserTabReceiver.ACTION_CLOSE_TAB)
                    .setPackage(BrowserTabReceiver.BROWSER_PACKAGE)
                    .putExtra(BrowserTabReceiver.EXTRA_TASK_ID, taskId)
            )
        }
    }

    private fun unpersistBrowserTab(taskId: Int) {
        val updated = (prefs.getStringSet(PREF_BROWSER_TABS, emptySet()) ?: emptySet()).toMutableSet()
        val changed = updated.removeAll { it.substringBefore("|").toIntOrNull() == taskId }
        if (changed) prefs.edit().putStringSet(PREF_BROWSER_TABS, updated).apply()
    }

    /** Re-sync browser-tab cards with the browser's live tabs on every Deck resume: the enumerate
     *  reply drives [reconcileBrowserTabs], which drops cards for tabs closed/swiped-away in system
     *  recents and adds any new live tabs — so Deck and native recents show the same tab set.
     *
     *  Guarded by [tabsEnumerated]: skip until the startup enumerate has completed once, so a resume
     *  firing during init can't race the 150ms restore replay into duplicate cards (the init path at
     *  construction already does restore-then-enumerate in the right order). */
    fun syncBrowserTabs() {
        if (!tabsEnumerated) return
        requestBrowserTabsEnumeration()
    }

    /** On Home/resume, scroll the carousel to the card for whatever the user was most recently in —
     *  an app (by `lastUsedPackage`) or a specific browser tab (`lastUsedPackage == BROWSER_PACKAGE`,
     *  disambiguated by the last-focused tab task id). No-ops if that card isn't present (e.g. it was
     *  dismissed) — the carousel just keeps its position. */
    fun focusLastUsed() {
        val pkg = lastUsedPackage ?: return
        val groups = _uiState.value.cardGroups
        val idx = if (pkg == BrowserTabReceiver.BROWSER_PACKAGE) {
            val tid = BrowserTabEventBus.currentFocusedTaskId
            groups.indexOfFirst { g -> g.apps.any { it.packageName == pkg && it.taskId == tid } }
                .let { if (it >= 0) it else groups.indexOfFirst { g -> g.apps.any { it.packageName == pkg } } }
        } else {
            groups.indexOfFirst { g -> g.apps.any { it.packageName == pkg } }
        }
        if (idx >= 0) _focusEvent.tryEmit(idx)
    }

    /** Ask the browser to broadcast back the task ids of every live tab task. No-op-safe if the
     *  browser isn't installed (the targeted broadcast simply reaches no receiver). */
    private fun requestBrowserTabsEnumeration() {
        runCatching {
            appContext.sendBroadcast(
                Intent(BrowserTabReceiver.ACTION_ENUMERATE_TABS)
                    .setPackage(BrowserTabReceiver.BROWSER_PACKAGE)
            )
        }
    }

    /** Reconcile Deck's browser-tab cards against the browser's authoritative live-tab list:
     *  drop+unpersist cards whose task is gone, and add cards for live tabs Deck has no card for.
     *  Non-browser cards are never touched. */
    private fun reconcileBrowserTabs(liveIds: IntArray) {
        val live = liveIds.toSet()
        Log.d(TAG, "Reconcile browser tabs against live=$live")
        // Publish the authoritative live set so a slow restore replay (events.collect) can refuse to
        // re-add a tab that's already known-dead. Set BEFORE the drop so the guard sees it.
        liveTabIds = live
        tabsEnumerated = true

        // 1) Drop dead browser-tab cards. Collect their ids first, mutate state, then unpersist
        //    outside the update lambda (MutableStateFlow.update may re-run its lambda on CAS retry).
        val droppedIds = mutableSetOf<Int>()
        _uiState.update { state ->
            val groups = state.cardGroups.mapNotNull { group ->
                val kept = group.apps.filter { app ->
                    val isBrowserTab = app.packageName == BrowserTabReceiver.BROWSER_PACKAGE && app.taskId != -1
                    if (isBrowserTab && app.taskId !in live) {
                        droppedIds.add(app.taskId)
                        false
                    } else true
                }
                when {
                    kept.isEmpty()               -> null
                    kept.size == group.apps.size -> group
                    kept.size == 1               -> CardGroup.single(kept.first())
                    else                         -> group.copy(apps = kept)
                }
            }
            state.copy(cardGroups = groups)
        }
        droppedIds.forEach { unpersistBrowserTab(it) }

        // 2) Add cards for any live tab Deck has no card for. Routed through the same NewTabEvent
        //    path (which persists), using the persisted parent if we have one. isRestore=true so a
        //    racing dead-tab guard treats these consistently (they're all in the live set anyway).
        val knownTaskIds = _uiState.value.cardGroups
            .flatMap { it.apps }
            .filter { it.packageName == BrowserTabReceiver.BROWSER_PACKAGE && it.taskId != -1 }
            .map { it.taskId }
            .toSet()
        val persistedParents = loadPersistedBrowserTabs().associate { (id, parent) -> id to parent }
        live.filter { it !in knownTaskIds }.forEach { taskId ->
            BrowserTabEventBus.emit(
                BrowserTabEventBus.NewTabEvent(
                    BrowserTabReceiver.BROWSER_PACKAGE, taskId, persistedParents[taskId], isRestore = true,
                )
            )
        }

        // If the user came Home from a browser tab, (re)apply the last-used focus now: reconcile runs
        // on resume after the enumerate round-trip, by which point the tab's card + focused-task-id
        // have settled (those broadcasts can land AFTER onResume's first focusLastUsed). Guarded to the
        // browser case so we never double-scroll the app case (which onResume already handled).
        if (lastUsedPackage == BrowserTabReceiver.BROWSER_PACKAGE) focusLastUsed()
    }

    companion object {
        private const val PREF_BROWSER_TABS = "browser_tabs"

        fun factory(context: Context) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return HomeViewModel(
                    context.applicationContext,
                    RecentAppsRepository(context.applicationContext),
                    context.applicationContext.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE),
                    LivePreviewRepository.getInstance(context.applicationContext),
                    context.applicationContext.packageName
                ) as T
            }
        }
    }
}
