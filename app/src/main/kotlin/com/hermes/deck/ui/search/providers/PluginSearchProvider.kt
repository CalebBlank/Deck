package com.hermes.deck.ui.search.providers

import com.hermes.deck.plugin.PluginInfo
import com.hermes.deck.plugin.PluginRepository
import com.hermes.deck.ui.search.SearchResult

class PluginSearchProvider(
    private val plugin: PluginInfo,
    private val repository: PluginRepository
) : SearchProvider {
    override val id = "plugin:${plugin.id}"
    override suspend fun query(q: String): List<SearchResult> = repository.query(plugin, q)
}
