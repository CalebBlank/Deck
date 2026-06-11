package com.hermes.deck.ui.search.providers

import android.content.Context
import com.hermes.deck.ui.search.SearchResult

/**
 * Tandoor Recipes search provider. Queries the user's Tandoor server via [TandoorClient] and returns
 * recipe cards. Silent until a server URL + API token are set in Settings → Search → Tandoor.
 */
class TandoorProvider(private val context: Context) : SearchProvider {
    override val id = "tandoor"

    override suspend fun query(q: String): List<SearchResult> {
        val trimmed = q.trim()
        if (trimmed.length < 2 || !TandoorClient.isConfigured(context)) return emptyList()
        val out = TandoorClient.search(context, trimmed).getOrDefault(emptyList()).map {
            SearchResult.TandoorResult(
                id       = it.id,
                name     = it.name,
                subtitle = it.subtitle,
                imageUrl = it.imageUrl
            )
        }
        // Confident → rich: a single match, or one whose name exactly matches the query.
        val exacts = out.filter { it.name.equals(trimmed, ignoreCase = true) }
        return when {
            out.size == 1    -> listOf(out[0].copy(rich = true))
            exacts.size == 1 -> listOf(exacts[0].copy(rich = true))
            else             -> out
        }
    }
}
