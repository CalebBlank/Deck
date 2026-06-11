package com.hermes.deck.ui.search.providers

import android.content.Context
import com.hermes.deck.ui.search.SearchResult

/**
 * Home Assistant search provider. Filters the user's controllable entities (light / switch /
 * input_boolean for v1) by friendly name and returns control cards. Silent until a base URL and
 * long-lived token are set in Settings → Search → Home Assistant. The entity list is fetched once
 * and cached by [HomeAssistantClient], so typing doesn't refetch each keystroke.
 */
class HomeAssistantProvider(private val context: Context) : SearchProvider {
    override val id = "home_assistant"

    override suspend fun query(q: String): List<SearchResult> {
        val trimmed = q.trim()
        if (trimmed.length < 2) return emptyList()
        if (!HomeAssistantClient.isConfigured(context)) return emptyList()

        val entities = HomeAssistantClient.states(context).getOrNull() ?: return emptyList()
        val needle = trimmed.lowercase()

        // Direct name/id matches.
        val byName = entities.filter {
            it.friendlyName.lowercase().contains(needle) || it.entityId.lowercase().contains(needle)
        }
        // Integration-name match: surface every entity whose integration label contains the query
        // (e.g. typing "hue" lists all the Hue devices).
        val integ = HomeAssistantClient.integrationMap(context)
        val byIntegration = if (integ.isEmpty()) emptyList() else {
            val ids = integ.filterValues { it.lowercase().contains(needle) }.keys
            if (ids.isEmpty()) emptyList() else entities.filter { it.entityId in ids }
        }

        return (byName + byIntegration)
            .distinctBy { it.entityId }
            .take(12)
            .map {
                SearchResult.HomeAssistantResult(
                    entityId     = it.entityId,
                    domain       = it.domain,
                    friendlyName = it.friendlyName,
                    state        = it.state,
                    brightness   = it.brightness,
                    percentage   = it.percentage,
                    position     = it.position,
                    attributes   = it.attributes
                )
            }
    }
}
