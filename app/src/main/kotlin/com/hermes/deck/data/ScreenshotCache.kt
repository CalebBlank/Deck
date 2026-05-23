package com.hermes.deck.data

import android.graphics.Bitmap

/**
 * Process-singleton bitmap cache shared between ScreenshotAccessibilityService and the UI.
 * LRU eviction keeps memory bounded.
 */
object ScreenshotCache {

    private const val MAX_ENTRIES = 12
    private val cache = object : LinkedHashMap<String, Bitmap>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Bitmap>): Boolean {
            if (size > MAX_ENTRIES) {
                eldest.value.recycle()
                return true
            }
            return false
        }
    }

    @Synchronized
    fun put(packageName: String, bitmap: Bitmap) {
        cache[packageName]?.recycle()
        cache[packageName] = bitmap
    }

    @Synchronized
    fun get(packageName: String): Bitmap? = cache[packageName]

    @Synchronized
    fun remove(packageName: String) {
        cache.remove(packageName)?.recycle()
    }

    @Synchronized
    fun clear() {
        cache.values.forEach { it.recycle() }
        cache.clear()
    }
}
