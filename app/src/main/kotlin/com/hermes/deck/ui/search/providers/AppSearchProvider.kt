package com.hermes.deck.ui.search.providers

import android.content.Context
import com.hermes.deck.data.InstalledAppsRepository
import com.hermes.deck.ui.search.SearchResult
import kotlinx.coroutines.flow.first

class AppSearchProvider(
    private val repo: InstalledAppsRepository,
    private val context: Context
) : SearchProvider {

    override val id = "apps"

    override suspend fun query(q: String): List<SearchResult> {
        if (q.isBlank()) return emptyList()
        val hidden = hiddenPackages()
        return repo.getAllApps().first()
            .filter { it.label.contains(q, ignoreCase = true) && it.packageName !in hidden }
            .take(5)
            .map { SearchResult.AppResult(it) }
    }

    private fun hiddenPackages(): Set<String> =
        context.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE)
            .getString("hidden_apps", "")
            .orEmpty()
            .split(",")
            .filter { it.isNotBlank() }
            .toSet()
}
