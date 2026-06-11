package com.hermes.deck.ui.search.providers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.text.HtmlCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Wikipedia client for [WikipediaProvider]. No API key (open API), but Wikimedia returns 403 for
 * generic/absent User-Agents, so every request carries a contactful UA. Two endpoints:
 *  - REST summary (/api/rest_v1/page/summary/<title>) → the "confident single result" rich card.
 *  - Action API search (w/api.php?action=query&list=search) → the fallback list of rows.
 */
object WikipediaClient {
    private const val UA = "DeckLauncher/1.0 (+https://github.com/CalebBlank/deck; personal launcher)"
    private const val REST = "https://en.wikipedia.org/api/rest_v1/page/summary/"
    private const val API = "https://en.wikipedia.org/w/api.php"

    data class Summary(
        val title: String,
        val description: String?,
        val extract: String,
        val thumbnailUrl: String?,
        val url: String,
        val type: String,
    )

    data class Hit(val title: String, val snippet: String, val url: String)

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000; readTimeout = 10000
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "application/json")
        }

    private fun articleUrl(title: String): String =
        "https://en.wikipedia.org/wiki/" + Uri.encode(title.replace(' ', '_'))

    /** REST page summary. The title goes in the URL PATH, so it MUST be Uri.encode'd — URLEncoder
     *  turns spaces into '+', and '+' in a path segment 404s (would make "confident" never fire on
     *  multi-word queries). Returns null on 404 / network error. */
    suspend fun summary(query: String): Summary? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = open(REST + Uri.encode(query.trim()))
            val code = conn.responseCode
            if (code != 200) { conn.disconnect(); return@runCatching null }
            val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            conn.disconnect()
            val title = o.optString("title").ifBlank { return@runCatching null }
            val page = o.optJSONObject("content_urls")?.optJSONObject("desktop")?.optString("page")?.ifBlank { null }
                ?: o.optJSONObject("content_urls")?.optJSONObject("mobile")?.optString("page")?.ifBlank { null }
                ?: articleUrl(title)
            Summary(
                title        = title,
                description  = o.optString("description").ifBlank { null },
                extract      = o.optString("extract").trim(),
                thumbnailUrl = o.optJSONObject("thumbnail")?.optString("source")?.ifBlank { null },
                url          = page,
                type         = o.optString("type").ifBlank { "standard" },
            )
        }.getOrNull()
    }

    /** Action-API full-text search → up to [limit] rows. srsearch is a query PARAM (URLEncoder is
     *  correct here). The snippet is HTML + entities → HtmlCompat to plain text. */
    suspend fun search(query: String, limit: Int): List<Hit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$API?action=query&format=json&list=search&srprop=snippet&srlimit=$limit" +
                "&srsearch=" + URLEncoder.encode(query.trim(), "UTF-8")
            val conn = open(url)
            if (conn.responseCode != 200) { conn.disconnect(); return@runCatching emptyList() }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val arr = JSONObject(text).optJSONObject("query")?.optJSONArray("search") ?: return@runCatching emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val title = obj.optString("title").ifBlank { return@mapNotNull null }
                val snippet = HtmlCompat.fromHtml(obj.optString("snippet"), HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
                Hit(title, snippet, articleUrl(title))
            }
        }.getOrDefault(emptyList())
    }

    suspend fun fetchBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { open(url).inputStream.use { BitmapFactory.decodeStream(it) } }.getOrNull()
    }
}
