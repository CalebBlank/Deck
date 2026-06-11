package com.hermes.deck.ui.search.providers

import android.content.Context
import com.hermes.deck.data.InstalledAppsRepository
import com.hermes.deck.data.TagRepository
import com.hermes.deck.ui.search.SearchResult
import kotlinx.coroutines.flow.first

class AppSearchProvider(
    private val repo: InstalledAppsRepository,
    private val context: Context,
    private val tagRepo: TagRepository
) : SearchProvider {

    override val id = "apps"

    override suspend fun query(q: String): List<SearchResult> {
        if (q.isBlank()) return emptyList()
        val prefs = context.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE)
        val visibleOnly = prefs.getBoolean("app_search_visible_only", true)
        val hidden = if (visibleOnly) hiddenPackages() else emptySet()
        val matches = repo.getAllApps().first()
            .filter {
                (it.label.contains(q, ignoreCase = true) ||
                 tagRepo.getTags(it.packageName).any { tag -> tag.contains(q, ignoreCase = true) }) &&
                it.packageName !in hidden
            }
        // Confident → rich app card: a single match, or one whose label exactly matches the query.
        val exacts = matches.filter { it.label.equals(q.trim(), ignoreCase = true) }
        return when {
            matches.size == 1 -> listOf(SearchResult.AppResult(matches[0], rich = true))
            exacts.size == 1  -> listOf(SearchResult.AppResult(exacts[0], rich = true))
            else              -> matches.take(5).map { SearchResult.AppResult(it) }
        }
    }

    private fun hiddenPackages(): Set<String> =
        context.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE)
            .getString("hidden_apps", "")
            .orEmpty()
            .split(",")
            .filter { it.isNotBlank() }
            .toSet()
}
