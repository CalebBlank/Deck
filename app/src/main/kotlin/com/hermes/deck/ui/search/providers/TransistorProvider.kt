package com.hermes.deck.ui.search.providers

import android.content.Context
import com.hermes.deck.ui.search.SearchResult

/**
 * Transistor internet-radio search provider. Filters the user's saved Transistor stations by name and
 * returns station cards; tapping plays the station in Transistor. Silent unless Transistor is installed.
 */
class TransistorProvider(private val context: Context) : SearchProvider {
    override val id = "transistor"

    override suspend fun query(q: String): List<SearchResult> {
        val trimmed = q.trim()
        if (trimmed.length < 2 || !TransistorClient.isInstalled(context)) return emptyList()
        return TransistorClient.search(context, trimmed).map {
            SearchResult.TransistorResult(
                mediaId = it.mediaId,
                title   = it.title,
                art     = it.art
            )
        }
    }
}
