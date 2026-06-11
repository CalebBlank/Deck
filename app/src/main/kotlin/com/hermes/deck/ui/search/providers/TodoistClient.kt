package com.hermes.deck.ui.search.providers

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Todoist client — the new unified **api/v1** (the old rest/v2 returns 410 Gone). Bearer token. Active
 * tasks are fetched once (paginating the `results`/`next_cursor` shape) and cached briefly, then
 * filtered client-side — task lists are small, and it avoids the filter-query syntax. Completing/adding
 * invalidates the cache so the next search reflects the change.
 */
object TodoistClient {
    private const val BASE = "https://api.todoist.com/api/v1"
    private const val KEY = "todoist_token"
    private const val CACHE_MS = 30_000L

    data class Task(val id: String, val content: String)

    @Volatile private var cache: List<Task>? = null
    @Volatile private var cacheTime = 0L

    private fun token(c: Context) =
        c.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE).getString(KEY, "")?.trim().orEmpty()

    fun isConfigured(context: Context): Boolean = token(context).isNotBlank()

    private suspend fun activeTasks(context: Context): List<Task> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        cache?.let { if (now - cacheTime < CACHE_MS) return@withContext it }
        val key = token(context)
        if (key.isBlank()) return@withContext emptyList()
        runCatching {
            // v1 paginates: { results: [...], next_cursor }. Follow the cursor (cap a few pages).
            val out = ArrayList<Task>()
            var cursor: String? = null
            var pages = 0
            do {
                val url = "$BASE/tasks?limit=200" + (cursor?.let { "&cursor=$it" } ?: "")
                val o = JSONObject(get(url, key))
                val results = o.optJSONArray("results") ?: break
                for (i in 0 until results.length()) {
                    val t = results.optJSONObject(i) ?: continue
                    val id = t.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val content = t.optString("content").takeIf { it.isNotBlank() } ?: continue
                    out.add(Task(id, content))
                }
                cursor = if (o.isNull("next_cursor")) null else o.optString("next_cursor").takeIf { it.isNotBlank() }
                pages++
            } while (cursor != null && pages < 5)
            out.also { cache = it; cacheTime = now }
        }.getOrDefault(emptyList())
    }

    suspend fun search(context: Context, query: String): List<Task> =
        activeTasks(context).filter { it.content.contains(query, ignoreCase = true) }

    /** Create a task with [content]. Invalidates the cache. */
    suspend fun add(context: Context, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        val key = token(context)
        if (key.isBlank()) return@withContext Result.failure(IllegalStateException("No Todoist token"))
        runCatching {
            val code = post("$BASE/tasks", key, JSONObject().put("content", content).toString())
            if (code !in 200..299) error("Todoist returned HTTP $code")
            cache = null
        }
    }

    /** Complete (close) a task. Invalidates the cache. */
    suspend fun complete(context: Context, id: String): Boolean = withContext(Dispatchers.IO) {
        val key = token(context)
        if (key.isBlank()) return@withContext false
        runCatching {
            val code = post("$BASE/tasks/$id/close", key, null)
            (code in 200..299).also { if (it) cache = null }
        }.getOrDefault(false)
    }

    private fun get(url: String, key: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000; readTimeout = 12000
            setRequestProperty("Authorization", "Bearer $key")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }.also { conn.disconnect() }
    }

    private fun post(url: String, key: String, body: String?): Int {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12000; readTimeout = 12000
            setRequestProperty("Authorization", "Bearer $key")
            if (body != null) { doOutput = true; setRequestProperty("Content-Type", "application/json") }
        }
        if (body != null) conn.outputStream.use { it.write(body.toByteArray()) } else conn.connect()
        return conn.responseCode.also { conn.disconnect() }
    }
}
