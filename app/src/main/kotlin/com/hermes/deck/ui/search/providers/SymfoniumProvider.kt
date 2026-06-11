package com.hermes.deck.ui.search.providers

import android.content.Context
import com.hermes.deck.ui.search.SearchResult

/**
 * Symfonium music search provider. Connects to Symfonium's MediaBrowserService and returns songs;
 * tapping a result plays it in Symfonium. Silent unless the Symfonium app is installed.
 */
class SymfoniumProvider(private val context: Context) : SearchProvider {
    override val id = "symfonium"

    override suspend fun query(q: String): List<SearchResult> {
        val trimmed = q.trim()
        if (trimmed.length < 2 || !SymfoniumClient.isInstalled(context)) return emptyList()
        val out = SymfoniumClient.search(context, trimmed).map {
            SearchResult.SymfoniumResult(
                mediaId  = it.mediaId,
                title    = it.title,
                subtitle = it.subtitle,
                artUri   = it.artUri,
                type     = it.type
            )
        }
        // Confident → rich, but ONLY for albums/artists — those have expandable content (track list /
        // albums row + bio). A lone song has nothing extra to show, so it stays a plain row even when
        // it's the single result.
        val exacts = out.filter { it.title.equals(trimmed, ignoreCase = true) }
        return when {
            out.size == 1 && out[0].type != "song"      -> listOf(out[0].copy(rich = true))
            exacts.size == 1 && exacts[0].type != "song" -> listOf(exacts[0].copy(rich = true))
            else                                         -> out
        }
    }
}
