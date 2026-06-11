package com.hermes.deck.ui.search.providers

import android.content.Context
import com.hermes.deck.ui.search.SearchResult
import kotlinx.coroutines.delay

/**
 * Weather card via Open-Meteo (free, no key). Fires only when the query mentions weather, so it
 * doesn't spend a network call per keystroke. "weather" / "weather today" → device location (needs
 * location permission); "weather in chicago" / "tokyo forecast" → geocodes that place.
 */
class WeatherProvider(private val context: Context) : SearchProvider {
    override val id = "weather"

    private val trigger = Regex("\\b(weather|forecast|temperature|temp)\\b")
    private val fillers = Regex(
        "\\b(weather|forecast|temperature|temp|in|at|the|today|tomorrow|current|currently|" +
        "now|whats|what|hows|how|is|for|outside|like|right)\\b"
    )

    override suspend fun query(q: String): List<SearchResult> {
        val low = q.trim().lowercase()
        if (!trigger.containsMatchIn(low)) return emptyList()
        delay(350)   // settle before the network call

        val city = low.replace("'", "").replace(fillers, " ")
            .replace(Regex("[^a-z ]"), " ").trim().replace(Regex("\\s+"), " ")
        val geo = if (city.length >= 2) {
            WeatherClient.geocode(city) ?: return emptyList()
        } else {
            val loc = PlacesClient.lastLocation(context) ?: return emptyList()  // needs location permission
            WeatherClient.Geo(loc.first, loc.second, "Your location")
        }
        val fc = WeatherClient.forecast(geo.lat, geo.lng, geo.name) ?: return emptyList()
        return listOf(SearchResult.WeatherResult(fc.location, fc.currentTempF, fc.currentCode, fc.days, fc.hours))
    }
}
