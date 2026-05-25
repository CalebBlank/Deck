package com.hermes.deck.data

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScreenshotEntry(val bitmap: Bitmap, val capturedAt: Long)

/**
 * Process-singleton bitmap cache shared between ScreenshotAccessibilityService and the UI.
 * LRU eviction keeps memory bounded. [revision] increments on every write so Compose
 * can observe it with collectAsState() to trigger recomposition when new screenshots arrive.
 */
object ScreenshotCache {

    private const val MAX_ENTRIES = 12
    private val cache = object : LinkedHashMap<String, ScreenshotEntry>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, ScreenshotEntry>): Boolean {
            if (size > MAX_ENTRIES) {
                eldest.value.bitmap.recycle()
                return true
            }
            return false
        }
    }

    private val _revision = MutableStateFlow(0)

    /** Increments each time a screenshot is written. Observe in Compose to trigger recomposition. */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    @Synchronized
    fun put(packageName: String, bitmap: Bitmap) {
        cache[packageName]?.bitmap?.recycle()
        cache[packageName] = ScreenshotEntry(bitmap, System.currentTimeMillis())
        _revision.value++
    }

    /** Returns the raw bitmap, or null if not cached. Use [getEntry] to also check the timestamp. */
    @Synchronized
    fun get(packageName: String): Bitmap? = cache[packageName]?.bitmap

    @Synchronized
    fun getEntry(packageName: String): ScreenshotEntry? = cache[packageName]

    @Synchronized
    fun remove(packageName: String) {
        cache.remove(packageName)?.bitmap?.recycle()
    }

    @Synchronized
    fun clear() {
        cache.values.forEach { it.bitmap.recycle() }
        cache.clear()
    }
}
