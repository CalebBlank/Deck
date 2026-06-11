package com.hermes.deck.ui.search.providers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.text.HtmlCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * YouTube Data API v3 client for [YouTubeProvider]. Silent until an API key is set in
 * Settings → Search → YouTube.
 *
 * QUOTA: a `search.list` call costs **100 units** and the default daily quota is 10,000 → only ~100
 * searches/day. So the provider debounces hard and we cache results per query here (re-typing or
 * re-ranking the same query costs nothing). Thumbnails are public i.ytimg.com URLs — fetched directly,
 * no key needed.
 */
object YouTubeClient {
    private const val PREFS = "deck_prefs"
    private const val KEY = "youtube_api_key"
    private const val SEARCH_URL = "https://www.googleapis.com/youtube/v3/search"

    data class Video(val videoId: String, val title: String, val channel: String, val thumbnailUrl: String?)

    // Per-query result cache (LRU) — guards the 100-unit/search quota against repeats + re-ranks.
    private val cache = object : LinkedHashMap<String, List<Video>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Video>>?) = size > 32
    }

    fun apiKey(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "")?.trim().orEmpty()

    fun isConfigured(context: Context): Boolean = apiKey(context).isNotBlank()

    /** Result cap from the provider's "Result limit" slider (provider_limit_youtube): 1–10, else 6. */
    private fun maxResults(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("provider_limit_youtube", 0)
            .let { if (it in 1..10) it else 6 }

    suspend fun search(context: Context, query: String): Result<List<Video>> = withContext(Dispatchers.IO) {
        val key = apiKey(context)
        if (key.isBlank()) return@withContext Result.failure(IllegalStateException("No YouTube API key set"))
        val cacheKey = "${maxResults(context)}:${query.lowercase()}"
        synchronized(cache) { cache[cacheKey] }?.let { return@withContext Result.success(it) }
        runCatching {
            val q = URLEncoder.encode(query, "UTF-8")
            val url = "$SEARCH_URL?part=snippet&type=video&maxResults=${maxResults(context)}&q=$q&key=$key"
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12000; readTimeout = 12000
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(text).optJSONObject("error")?.optString("message") }.getOrNull()
                throw RuntimeException(msg?.ifBlank { null } ?: "YouTube request failed (HTTP $code)")
            }
            val arr = JSONObject(text).optJSONArray("items") ?: return@runCatching emptyList()
            val out = (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val vid = o.optJSONObject("id")?.optString("videoId")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val sn = o.optJSONObject("snippet") ?: return@mapNotNull null
                // YouTube titles come HTML-escaped (&amp;, &#39;) — decode for display.
                val title = HtmlCompat.fromHtml(sn.optString("title"), HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
                val channel = sn.optString("channelTitle")
                val thumb = sn.optJSONObject("thumbnails")?.let {
                    it.optJSONObject("medium") ?: it.optJSONObject("high") ?: it.optJSONObject("default")
                }?.optString("url")?.takeIf { u -> u.isNotBlank() }
                Video(vid, title.ifBlank { "(untitled)" }, channel, thumb)
            }
            synchronized(cache) { cache[cacheKey] = out }
            out
        }
    }

    suspend fun fetchBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12000; readTimeout = 12000
            }
            conn.inputStream.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }
}
