package com.hermes.deck.ui.search.providers

import android.content.Context
import com.hermes.deck.ui.search.SearchResult
import kotlinx.coroutines.delay

/**
 * Radarr (movies) / Sonarr (TV) "add it" provider — one class, two instances. Looks up titles via the
 * service, then shows only those NOT already in Plex (and not already in Radarr/Sonarr), so the user
 * never sees a Plex/Radarr duplicate. The Plex check runs IN this provider (not at a later combine
 * stage) so a suppressed title never flashes on screen before Plex's results arrive.
 *
 * [id] = "radarr"/"sonarr"; [plexType] = the Plex item type to dedup against ("movie"/"show").
 */
class ArrProvider(
    private val context: Context,
    override val id: String,
    private val plexType: String,
) : SearchProvider {

    override suspend fun query(q: String): List<SearchResult> {
        val raw = q.trim()
        if (raw.length < 3 || !ArrClient.isConfigured(context, id)) return emptyList()
        delay(500)   // settle before the lookup (it hits TMDb/TVDb)
        val candidates = ArrClient.lookup(context, id, raw).filter { !it.alreadyAdded }
        if (candidates.isEmpty()) return emptyList()

        // What the user already has in Plex (normalized title + exact year, so remakes aren't merged).
        val plexKeys = PlexClient.search(context, raw).getOrNull()?.items
            ?.filter { it.type == plexType && it.year != null }
            ?.map { ArrClient.normalizeTitle(it.title) to it.year }
            ?.toSet() ?: emptySet()

        return candidates
            .filterNot { c -> c.year != null && (ArrClient.normalizeTitle(c.title) to c.year) in plexKeys }
            .take(5)
            .map { SearchResult.AddMediaResult(it.service, it.title, it.year, it.overview, it.posterUrl, it.lookupJson) }
    }
}
