package com.hermes.deck.ui.search.providers

import android.content.Context
import com.hermes.deck.ui.search.SearchResult

/**
 * Wikipedia search provider — no API key. Summary-first: if the query resolves to a single clear
 * article (REST summary type=="standard" with an extract — riding Wikipedia's own primary-topic +
 * redirect resolution) it returns ONE rich result (summary card); otherwise it returns a few plain
 * rows from full-text search. The plain rows are capped by the provider's "Result limit" slider
 * (provider_limit_wikipedia), applied by the framework's res.take(limit) — no per-card pref-reading
 * needed because Wikipedia returns N discrete rows.
 */
class WikipediaProvider(private val context: Context) : SearchProvider {
    override val id = "wikipedia"

    override suspend fun query(q: String): List<SearchResult> {
        val trimmed = q.trim()
        if (trimmed.length < 3) return emptyList()

        WikipediaClient.summary(trimmed)?.let { s ->
            if (s.type == "standard" && s.extract.isNotBlank()) {
                return listOf(
                    SearchResult.WikipediaResult(
                        title = s.title, description = s.description, extract = s.extract,
                        thumbnailUrl = s.thumbnailUrl, url = s.url, rich = true
                    )
                )
            }
        }
        // Ambiguous / no direct match → a few plain rows (framework caps to the slider).
        return WikipediaClient.search(trimmed, 8).map { hit ->
            SearchResult.WikipediaResult(
                title = hit.title, description = hit.snippet, extract = null,
                thumbnailUrl = null, url = hit.url, rich = false
            )
        }
    }
}
