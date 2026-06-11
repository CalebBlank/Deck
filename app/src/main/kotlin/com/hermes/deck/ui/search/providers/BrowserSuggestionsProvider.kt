package com.hermes.deck.ui.search.providers

import com.hermes.deck.ui.search.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class BrowserSuggestionsProvider : SearchProvider {
    override val id = "browser_suggestions"

    override suspend fun query(q: String): List<SearchResult> = withContext(Dispatchers.IO) {
        if (q.length < 2) return@withContext emptyList()
        try {
            val encoded = URLEncoder.encode(q, "UTF-8")
            val conn = (URL("https://duckduckgo.com/ac/?q=$encoded&type=list").openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout    = 3000
                requestMethod  = "GET"
                setRequestProperty("User-Agent", "Deck Launcher")
            }
            val body = conn.inputStream.bufferedReader().readText().also { conn.disconnect() }
            // Response format: ["query", ["suggestion1", "suggestion2", ...]]
            val suggestions = JSONArray(body).optJSONArray(1) ?: return@withContext emptyList()
            (0 until suggestions.length())
                .mapNotNull { suggestions.optString(it).takeIf { s -> s.isNotBlank() } }
                .take(5)
                .map { SearchResult.BrowserSuggestionResult(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
