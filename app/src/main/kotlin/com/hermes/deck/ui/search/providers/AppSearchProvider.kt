package com.hermes.deck.ui.search.providers

import com.hermes.deck.data.InstalledAppsRepository
import com.hermes.deck.ui.search.SearchResult
import kotlinx.coroutines.flow.first

class AppSearchProvider(private val repo: InstalledAppsRepository) : SearchProvider {

    override val id = "apps"

    override suspend fun query(q: String): List<SearchResult> {
        if (q.isBlank()) return emptyList()
        return repo.getAllApps().first()
            .filter { it.label.contains(q, ignoreCase = true) }
            .take(5)
            .map { SearchResult.AppResult(it) }
    }
}
