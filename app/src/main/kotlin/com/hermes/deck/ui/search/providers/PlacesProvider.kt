package com.hermes.deck.ui.search.providers

import android.content.Context
import com.hermes.deck.ui.search.SearchResult

/**
 * Google Maps "local places" search provider. Mirrors the tap-to-ask pattern of [ClaudeProvider]:
 * emits a single tap-to-search result for any query WITHOUT a network call — the Places API request
 * and static map fire only when the user taps the card (see PlacesResultCard / [PlacesClient]).
 *
 * Gated on a stored Google Maps Platform API key: with no key there's nothing the card can do, so the
 * provider stays silent until one is set in Settings → Search → Google Maps.
 */
class PlacesProvider(private val context: Context) : SearchProvider {
    override val id = "places"

    override suspend fun query(q: String): List<SearchResult> {
        val trimmed = q.trim()
        if (trimmed.length < 2) return emptyList()
        if (!PlacesClient.isConfigured(context)) return emptyList()
        // Search up front (the card auto-searched anyway → cost-neutral) and only emit a card when the
        // geo-filter leaves real, relevant places — no more empty "no places found" cards.
        val places = PlacesClient.search(context, trimmed).getOrNull().orEmpty()
        if (places.isEmpty()) return emptyList()
        return listOf(SearchResult.PlacesResult(trimmed, places))
    }
}
