package com.hermes.deck.data

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.hermes.deck.service.BrowserTabReceiver.Companion.BROWSER_PACKAGE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

private const val TAG = "LivePreview"

class LivePreviewRepository(private val context: Context) {

    val suPath: String? = listOf("/debug_ramdisk/su", "/su/bin/su", "/sbin/su")
        .firstOrNull { File(it).canExecute() }

    val isRootAvailable: Boolean get() = suPath != null

    /**
     * Reads WMS task snapshots for all recent tasks into ScreenshotCache.
     * Uses root to:
     *  1. Discover the snapshots UUID directory
     *  2. Parse dumpsys activity recents for taskId → packageName
     *  3. Batch-copy JPEG snapshots to our files dir
     *  4. Decode and put into ScreenshotCache, then delete temp copies
     */
    suspend fun refreshAll(): Unit = withContext(Dispatchers.IO) {
        val su = suPath ?: return@withContext

        // 1. Find the UUID subdir inside /data/system_ce/0/snapshots/
        val uuid = runSu(su, "ls /data/system_ce/0/snapshots/ 2>/dev/null")
            .trim().lines().firstOrNull()?.trim()
        if (uuid.isNullOrBlank()) {
            Log.w(TAG, "No snapshots UUID directory found")
            return@withContext
        }
        val snapshotsDir = "/data/system_ce/0/snapshots/$uuid"

        // 2. Parse recent tasks (taskId → packageName), most-recent task wins per package
        val taskMap = parseRecentTasks(su)
        if (taskMap.isEmpty()) {
            Log.w(TAG, "No recent tasks parsed")
            return@withContext
        }

        // 3. Batch-copy all snapshot JPEGs to app's files dir in one su invocation
        val destDir = File(context.filesDir, "snap_tmp").also { it.mkdirs() }
        val cpScript = taskMap.entries.joinToString("; ") { (taskId, _) ->
            "cp '$snapshotsDir/$taskId.jpg' '${destDir.absolutePath}/$taskId.jpg' 2>/dev/null; chmod 644 '${destDir.absolutePath}/$taskId.jpg' 2>/dev/null"
        }
        runSu(su, cpScript)

        // 4. Decode and cache each one, clean up
        val multiTaskPkgs = taskMap.values.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
        for ((taskId, pkg) in taskMap) {
            // The browser is always per-task: each tab is its own Activity/task, but
            // excludeFromRecents="true" hides all but the foreground tab from
            // `dumpsys activity recents`, so the multiTaskPkgs heuristic sees only one
            // browser task and would store under the bare package key. That bare key then
            // gets shown for EVERY browser card via AppCard's package fallback (all tabs
            // look identical). Force the per-task key for the browser.
            val perTask = pkg in multiTaskPkgs || pkg == BROWSER_PACKAGE
            val cacheKey = if (perTask) "$pkg:$taskId" else pkg
            val f = File(destDir, "$taskId.jpg")
            if (f.exists() && f.length() > 0L) {
                BitmapFactory.decodeFile(f.path)?.let { bmp ->
                    ScreenshotCache.put(cacheKey, bmp)
                    Log.d(TAG, "Loaded snapshot for $cacheKey")
                }
                f.delete()
            }
        }
    }

    /**
     * Force-stops [packageName] and removes its task from Android's recents stack.
     * No-op if root is unavailable.
     *
     * Two-step approach:
     *  1. `am force-stop` — finishes all activities and kills all processes. More reliable than
     *     killBackgroundProcesses() which only works when the app is already in the background.
     *  2. `am task remove` — removes the visual entry from the recents list. Requires a taskId
     *     lookup from dumpsys; silently skipped if the task isn't found (e.g. already gone).
     */
    suspend fun removeTask(packageName: String) = withContext(Dispatchers.IO) {
        val su = suPath ?: return@withContext

        // Step 1: force-stop — no taskId needed, works regardless of app state
        runSu(su, "am force-stop $packageName")
        Log.d(TAG, "Force-stopped $packageName")

        // Step 2: remove from recents list
        val taskMap = parseRecentTasks(su)
        val taskId = taskMap.entries.firstOrNull { it.value == packageName }?.key
        if (taskId != null) {
            runSu(su, "am stack remove $taskId")
            Log.d(TAG, "Removed task $taskId ($packageName) from system recents")
        } else {
            Log.d(TAG, "No task entry found for $packageName in recents (already gone or never opened)")
        }
    }

    private fun runSu(su: String, cmd: String): String = try {
        val proc = ProcessBuilder(su, "-c", cmd).redirectErrorStream(false).start()
        val out = proc.inputStream.bufferedReader().readText()
        proc.waitFor(5, TimeUnit.SECONDS)
        proc.destroy()
        out
    } catch (_: Exception) { "" }

    private fun parseRecentTasks(su: String): Map<Int, String> {
        val recents = runSu(su, "dumpsys activity recents 2>/dev/null")
        val result = mutableMapOf<Int, String>()
        val taskIdRegex = Regex("taskId=(\\d+)")
        val componentRegex = Regex("mActivityComponent=(\\S+)")
        // The launcher's own home activity is never a useful card. Deck's *other* screens (e.g. the
        // Settings activity opened as its own task) appear as cards unless "Hide Deck from cards" is on.
        val hideSelf = context.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE)
            .getBoolean("hide_self_from_cards", true)
        for (block in recents.split(Regex("\\* Recent #\\d+:"))) {
            val taskId = taskIdRegex.find(block)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            val component = componentRegex.find(block)?.groupValues?.get(1)?.trim() ?: continue
            val pkg = component.substringBefore('/')
            if (pkg.isBlank()) continue
            if (pkg == context.packageName && (hideSelf || component.endsWith("MainActivity"))) continue
            // The browser's reopen trampoline (ReopenTabActivity) shares the browser package, so
            // its component would otherwise be counted as a tab — manufacturing phantom cards.
            if (component.contains("ReopenTabActivity")) continue
            result[taskId] = pkg
        }
        return result
    }

    /** Returns the set of package names that currently have a task in Android's recents stack. */
    suspend fun getLiveTaskPackages(): Set<String> = getLiveTasks().values.toSet()

    /** Returns all live tasks as taskId → packageName (may have multiple entries for same package). */
    suspend fun getLiveTasks(): Map<Int, String> = withContext(Dispatchers.IO) {
        val su = suPath ?: return@withContext emptyMap()
        parseRecentTasks(su)
    }

    companion object {
        @Volatile private var _instance: LivePreviewRepository? = null
        fun getInstance(ctx: Context): LivePreviewRepository = _instance ?: synchronized(this) {
            _instance ?: LivePreviewRepository(ctx.applicationContext).also { _instance = it }
        }
    }
}
