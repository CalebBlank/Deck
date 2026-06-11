package com.hermes.deck.ui.search.providers

import android.content.Context
import com.hermes.deck.ui.search.SearchResult

/**
 * "Ask Hermes" search provider — the self-hosted Nous Research agent. Mirrors [ClaudeProvider]:
 * emits a single tap-to-ask result for any query WITHOUT a network call; the request fires only
 * when the user taps the card (see HermesResultCard / HermesClient).
 *
 * Stays silent until the Hermes URL + password are set in Settings → Search → Hermes.
 */
class HermesProvider(private val context: Context) : SearchProvider {
    override val id = "hermes"

    override suspend fun query(q: String): List<SearchResult> {
        val trimmed = q.trim()
        if (trimmed.length < 2) return emptyList()
        if (!HermesClient.isConfigured(context)) return emptyList()
        return listOf(SearchResult.HermesResult(trimmed))
    }
}
