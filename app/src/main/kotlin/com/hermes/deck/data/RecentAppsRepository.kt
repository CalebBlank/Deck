package com.hermes.deck.data

import android.app.ActivityManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import com.hermes.deck.service.BrowserTabReceiver.Companion.BROWSER_PACKAGE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class RecentAppsRepository(private val context: Context) {

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val packageManager = context.packageManager

    /** Emits a list of recently-used apps ordered by most-recent-first, excluding this launcher. */
    fun getRecentApps(limit: Int = 10): Flow<List<AppInfo>> = flow {
        val end   = System.currentTimeMillis()
        // 48h window: system recents rarely keeps tasks older than this in practice.
        // AOSP TODO: replace with ActivityManager.getRecentTasks() once platform-signed.
        val start = end - 2 * 24 * 60 * 60 * 1000L

        val events = usageStatsManager.queryEvents(start, end)
        val lastForeground = linkedMapOf<String, Long>()
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastForeground[event.packageName] = event.timeStamp
            }
        }

        // Exclude Deck itself unless the user turned "Hide Deck from cards" off (default = hide).
        val hideSelf = context.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE)
            .getBoolean("hide_self_from_cards", true)
        val apps = lastForeground.entries
            .filter { !hideSelf || it.key != context.packageName }
            // The browser is represented by its individual tab cards (broadcast-driven + reconciled
            // against the live tab tasks), never one monolithic app card — that's how Deck stays in
            // sync with native recents, which only shows the tab tasks.
            .filter { it.key != BROWSER_PACKAGE }
            .filter { packageManager.getLaunchIntentForPackage(it.key) != null }
            .sortedByDescending { it.value }
            .take(limit)
            .mapNotNull { (pkg, ts) -> resolveAppInfo(pkg, ts) }

        emit(apps)
    }.flowOn(Dispatchers.IO)

    fun killApp(packageName: String) {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .killBackgroundProcesses(packageName)
    }

    fun hasAccessibilityService(): Boolean {
        val enabled = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabled?.contains(context.packageName) == true
    }

    fun hasUsagePermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    /** Resolve a package name to AppInfo. Called from outside on foreground events. */
    fun resolveApp(packageName: String): AppInfo? = resolveAppInfo(packageName, System.currentTimeMillis())

    fun resolveAppForTask(packageName: String, taskId: Int, lastUsed: Long): AppInfo? =
        resolveAppInfo(packageName, lastUsed)?.copy(taskId = taskId)

    fun isLaunchable(packageName: String): Boolean =
        packageManager.getLaunchIntentForPackage(packageName) != null

    /**
     * Returns the package that most recently moved to the background within [windowMs],
     * excluding [excludePackages]. Used to identify which app opened a browser tab.
     * When Inoreader opens a link, it moves to background < 1 second before the broadcast
     * arrives. When a New Tab is opened from within the browser, no non-browser app has
     * recently moved to background.
     */
    fun findRecentlyBackgroundedApp(windowMs: Long, excludePackages: Set<String>): String? {
        val now = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(now - windowMs, now)
        var result: String? = null
        var latestTime = 0L
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND &&
                event.packageName !in excludePackages &&
                event.timeStamp > latestTime
            ) {
                result = event.packageName
                latestTime = event.timeStamp
            }
        }
        return result
    }

    private fun resolveAppInfo(packageName: String, lastUsed: Long): AppInfo? = try {
        val info = packageManager.getApplicationInfo(packageName, 0)
        AppInfo(
            packageName = packageName,
            label       = packageManager.getApplicationLabel(info).toString(),
            icon        = packageManager.getApplicationIcon(packageName),
            lastUsed    = lastUsed
        )
    } catch (_: PackageManager.NameNotFoundException) { null }
}
