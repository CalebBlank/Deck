package com.hermes.deck.data

import android.app.ActivityManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
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
        val start = end - 7 * 24 * 60 * 60 * 1000L

        val events = usageStatsManager.queryEvents(start, end)
        val lastForeground = linkedMapOf<String, Long>()
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastForeground[event.packageName] = event.timeStamp
            }
        }

        val apps = lastForeground.entries
            .filter { it.key != context.packageName }
            .sortedByDescending { it.value }
            .take(limit)
            .mapNotNull { (pkg, ts) -> resolveAppInfo(pkg, ts) }

        emit(apps)
    }.flowOn(Dispatchers.IO)

    fun killApp(packageName: String) {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .killBackgroundProcesses(packageName)
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
