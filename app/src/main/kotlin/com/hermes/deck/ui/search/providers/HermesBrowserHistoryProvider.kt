package com.hermes.deck.ui.search.providers

import android.content.Context
import android.net.Uri
import com.hermes.deck.ui.search.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HermesBrowserHistoryProvider(private val context: Context) : SearchProvider {
    override val id = "hermes_browser_history"

    override suspend fun query(q: String): List<SearchResult> = withContext(Dispatchers.IO) {
        if (q.length < 2) return@withContext emptyList()
        try {
            val uri = Uri.Builder()
                .scheme("content")
                .authority("com.hermes.browser.history")
                .appendQueryParameter("q", q)
                .build()
            val cursor = context.contentResolver.query(uri, null, null, null, null)
                ?: return@withContext emptyList()
            cursor.use {
                val urlCol   = it.getColumnIndexOrThrow("url")
                val titleCol = it.getColumnIndexOrThrow("title")
                buildList {
                    while (it.moveToNext()) {
                        add(SearchResult.BrowserHistoryResult(
                            url         = it.getString(urlCol),
                            title       = it.getString(titleCol),
                            browserName = "Hermes"
                        ))
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
