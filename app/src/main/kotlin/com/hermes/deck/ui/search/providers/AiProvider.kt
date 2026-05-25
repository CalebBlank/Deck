package com.hermes.deck.ui.search.providers

import android.content.Context
import com.hermes.deck.ui.search.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiProvider(private val context: Context) : SearchProvider {
    override val id = "ai"

    private var sessionAvailable: Boolean? = null

    override suspend fun query(q: String): List<SearchResult> = withContext(Dispatchers.Default) {
        if (sessionAvailable == false) return@withContext emptyList()
        runCatching {
            val answer = runInference(q) ?: return@withContext emptyList()
            sessionAvailable = true
            listOf(SearchResult.AiResult(query = q, answer = answer))
        }.getOrElse { sessionAvailable = false; emptyList() }
    }

    @Suppress("UNUSED_PARAMETER")
    private suspend fun runInference(q: String): String? = null
}
