package com.hermes.deck.ui.search.providers

import com.hermes.deck.ui.search.SearchResult
import kotlinx.coroutines.delay

/**
 * Dictionary definitions via the free Dictionary API (no key). Fires only on a "define"-style query
 * so it doesn't look up every word typed: "define serendipity", "definition of apropos",
 * "what does ephemeral mean", "ubiquitous definition".
 */
class DictionaryProvider : SearchProvider {
    override val id = "dictionary"

    private val patterns = listOf(
        Regex("^define\\s+(.+)$"),
        Regex("^definition\\s+of\\s+(.+)$"),
        Regex("^definition\\s+(.+)$"),
        Regex("^meaning\\s+of\\s+(.+)$"),
        Regex("^what\\s+does\\s+(.+?)\\s+mean\\??$"),
        Regex("^what\\s+is\\s+the\\s+(?:definition|meaning)\\s+of\\s+(.+)$"),
        Regex("^(.+?)\\s+definition$"),
        Regex("^(.+?)\\s+meaning$"),
    )

    override suspend fun query(q: String): List<SearchResult> {
        val low = q.trim().lowercase()
        val word = patterns.firstNotNullOfOrNull { it.find(low)?.groupValues?.get(1)?.trim() }
            ?.takeIf { it.isNotBlank() } ?: return emptyList()
        delay(300)
        return DictionaryClient.lookup(word)?.let { listOf(it) } ?: emptyList()
    }
}
