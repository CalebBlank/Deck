package com.hermes.deck.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScreenshotEntry(val bitmap: Bitmap, val capturedAt: Long)

/**
 * Process-singleton bitmap cache shared between ScreenshotAccessibilityService and the UI.
 * LRU eviction keeps memory bounded. [revision] increments on every write so Compose
 * can observe it with collectAsState() to trigger recomposition when new screenshots arrive.
 *
 * Keys are either plain package names (single-task apps) or "$packageName:$taskId"
 * (multi-task apps such as browser tabs). Callers use [AppInfo.id] as the key.
 *
 * Screenshots are ALSO persisted to disk (cache dir) so that cards restored after a Deck process
 * restart — notably browser-tab cards, which are excludeFromRecents and can't be rediscovered —
 * show their last preview instead of a blank frame. Disk IO happens off the main thread; the
 * UI-facing get()/getEntry() are pure in-memory reads. Call [init] once (idempotent) to enable
 * disk persistence and kick off the one-time preload of any on-disk screenshots into memory.
 */
object ScreenshotCache {

    private const val MAX_ENTRIES = 20
    private const val MAX_DISK_FILES = 20
    private const val DIR_NAME = "screenshots"

    private val cache = object : LinkedHashMap<String, ScreenshotEntry>(MAX_ENTRIES + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, ScreenshotEntry>): Boolean =
            size > MAX_ENTRIES
    }

    private val _revision = MutableStateFlow(0)

    /** Increments each time a screenshot is written. Observe in Compose to trigger recomposition. */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    @Volatile private var diskDir: File? = null
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * One-time, idempotent setup. There's no Application subclass, so this is called from both
     * MainActivity.onCreate and ScreenshotAccessibilityService.onCreate (whichever runs first wins).
     * Enables disk persistence and preloads any on-disk screenshots into the memory LRU off the
     * main thread, bumping [revision] so already-composed cards pick them up.
     */
    @Synchronized
    fun init(appContext: Context) {
        if (diskDir != null) return
        val dir = File(appContext.applicationContext.cacheDir, DIR_NAME).apply { mkdirs() }
        diskDir = dir
        ioScope.launch { preloadFromDisk(dir) }
    }

    private fun preloadFromDisk(dir: File) {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        var loadedAny = false
        for (file in files) {
            val key = keyFromFilename(file.name) ?: continue
            // Decode OUTSIDE the lock — get()/getEntry() share this monitor and run on the main
            // thread during composition, so holding it across 20 decodes could stall a frame.
            synchronized(this) { if (cache.containsKey(key)) null else key } ?: continue
            val bmp = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull() ?: continue
            synchronized(this) {
                // Re-check under the lock: a live capture may have written this key meanwhile —
                // don't clobber a fresher in-memory entry with the stale disk one.
                if (!cache.containsKey(key)) {
                    cache[key] = ScreenshotEntry(bmp, file.lastModified())
                    loadedAny = true
                }
            }
        }
        if (loadedAny) _revision.update { it + 1 }
    }

    @Synchronized
    fun put(key: String, bitmap: Bitmap) {
        cache[key] = ScreenshotEntry(bitmap, System.currentTimeMillis())
        _revision.update { it + 1 }
        val dir = diskDir
        if (dir != null) {
            ioScope.launch { writeToDisk(dir, key, bitmap) }
        }
    }

    /** Returns the bitmap for [key], or null if not cached. Pure in-memory read (no disk IO). */
    @Synchronized
    fun get(key: String): Bitmap? = cache[key]?.bitmap

    /** Returns the full [ScreenshotEntry] for [key]. Pure in-memory read (no disk IO). */
    @Synchronized
    fun getEntry(key: String): ScreenshotEntry? = cache[key]

    @Synchronized
    fun remove(key: String) {
        cache.remove(key)
        val dir = diskDir
        if (dir != null) {
            ioScope.launch { runCatching { File(dir, filenameFor(key)).delete() } }
        }
    }

    @Synchronized
    fun clear() {
        cache.clear()
        val dir = diskDir
        if (dir != null) {
            ioScope.launch { dir.listFiles()?.forEach { runCatching { it.delete() } } }
        }
    }

    // ---- disk helpers (IO thread only) ----------------------------------------------------------

    private fun writeToDisk(dir: File, key: String, bitmap: Bitmap) {
        runCatching {
            val file = File(dir, filenameFor(key))
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            file.setLastModified(System.currentTimeMillis())
            pruneDisk(dir)
        }
    }

    /** Evict the oldest files so the on-disk set stays bounded (by lastModified). */
    private fun pruneDisk(dir: File) {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        if (files.size <= MAX_DISK_FILES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_DISK_FILES)
            .forEach { runCatching { it.delete() } }
    }

    // Reversible sanitize: ':' (the only special char a key can contain — package:taskId) maps to
    // '~', which can't appear in a package name or a numeric taskId, so filenames round-trip back
    // to keys for the preload. A lossy substitution (e.g. '_') would break, since package names
    // legitimately contain underscores.
    private fun filenameFor(key: String): String = key.replace(':', '~') + ".png"

    private fun keyFromFilename(name: String): String? {
        if (!name.endsWith(".png")) return null
        return name.removeSuffix(".png").replace('~', ':')
    }
}
