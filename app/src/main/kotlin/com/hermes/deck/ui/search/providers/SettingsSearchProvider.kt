package com.hermes.deck.ui.search.providers

import android.content.Context
import com.hermes.deck.ui.search.SearchResult

class SettingsSearchProvider(private val context: Context) : SearchProvider {

    override val id = "settings"

    private val entries: List<Pair<String, SearchResult.SettingsResult>> = listOf(
        "appearance" to SearchResult.SettingsResult("Theme",             "Light, dark, or follow system",           "appearance"),
        "appearance" to SearchResult.SettingsResult("Material You",      "Dynamic colors from your wallpaper",      "appearance"),
        "appearance" to SearchResult.SettingsResult("Accent color",      "Custom color when Material You is off",   "appearance"),
        "appearance" to SearchResult.SettingsResult("Wallpaper",         "Dim level and wallpaper picker",          "appearance"),
        "appearance" to SearchResult.SettingsResult("Icon pack",         "Apply a custom icon pack",                "appearance"),
        "appearance" to SearchResult.SettingsResult("Icon shape",        "Change the shape of app icons",           "appearance"),
        "cards"      to SearchResult.SettingsResult("App drawer style",  "Grid or list view",                       "cards"),
        "cards"      to SearchResult.SettingsResult("Grid columns",      "Number of columns in the app drawer",     "cards"),
        "cards"      to SearchResult.SettingsResult("Hidden apps",       "Apps hidden from the drawer",             "cards"),
        "cards"      to SearchResult.SettingsResult("Hide Deck from cards",     "Stop Deck appearing in recent cards",     "cards"),
        "cards"      to SearchResult.SettingsResult("Hide Settings from cards", "Stop Settings appearing in recent cards", "cards"),
        "search"     to SearchResult.SettingsResult("Search providers",  "Enable or disable search result types",   "search"),
        "search"     to SearchResult.SettingsResult("Visible apps only", "Exclude hidden apps from search",         "search"),
        "search"     to SearchResult.SettingsResult("Manage widgets",    "Configure pinned widget appearance",      "search"),
        "search"     to SearchResult.SettingsResult("Number key layout", "Map number keys to letters for search",   "search"),
        "about"      to SearchResult.SettingsResult("Version",           "Current app version",                     "about"),
        "about"      to SearchResult.SettingsResult("Reset onboarding",  "Show permission setup on next launch",    "about"),
        "about"      to SearchResult.SettingsResult("Clear pinned apps", "Remove all apps from the dock",           "about"),
    )

    override suspend fun query(q: String): List<SearchResult> {
        if (q.isBlank()) return emptyList()
        return entries
            .filter { (_, r) ->
                r.title.contains(q, ignoreCase = true) ||
                r.subtitle.contains(q, ignoreCase = true)
            }
            .map { it.second }
    }
}
