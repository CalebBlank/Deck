package com.hermes.deck.ui.search.providers

import com.hermes.deck.ui.search.WeatherDay
import com.hermes.deck.ui.search.WeatherHour
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Open-Meteo weather client — free, no API key. Geocodes a place name and fetches current conditions
 * plus a short daily forecast (Fahrenheit; the user is US-based).
 */
object WeatherClient {
    private const val GEOCODE_URL = "https://geocoding-api.open-meteo.com/v1/search"
    private const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast"

    data class Geo(val lat: Double, val lng: Double, val name: String)
    data class Forecast(
        val location: String,
        val currentTempF: Int,
        val currentCode: Int,
        val days: List<WeatherDay>,
        val hours: List<WeatherHour>,
    )

    /** Resolve a place name → coordinates + a display label ("Chicago, Illinois"). */
    suspend fun geocode(name: String): Geo? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$GEOCODE_URL?name=${URLEncoder.encode(name, "UTF-8")}&count=1&language=en&format=json"
            val o = JSONObject(httpGet(url))
            val r = o.optJSONArray("results")?.optJSONObject(0) ?: return@runCatching null
            val label = listOfNotNull(
                r.optString("name").takeIf { it.isNotBlank() },
                r.optString("admin1").takeIf { it.isNotBlank() } ?: r.optString("country").takeIf { it.isNotBlank() }
            ).joinToString(", ")
            Geo(r.optDouble("latitude"), r.optDouble("longitude"), label)
        }.getOrNull()
    }

    suspend fun forecast(lat: Double, lng: Double, location: String): Forecast? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$FORECAST_URL?latitude=$lat&longitude=$lng" +
                "&current=temperature_2m,weather_code" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                "&hourly=temperature_2m,weather_code" +
                "&temperature_unit=fahrenheit&timezone=auto&forecast_days=5"
            val o = JSONObject(httpGet(url))
            val cur = o.optJSONObject("current") ?: return@runCatching null
            val daily = o.optJSONObject("daily") ?: return@runCatching null
            val times = daily.optJSONArray("time") ?: return@runCatching null
            val codes = daily.optJSONArray("weather_code")
            val maxs = daily.optJSONArray("temperature_2m_max")
            val mins = daily.optJSONArray("temperature_2m_min")
            val days = (0 until times.length()).map { i ->
                WeatherDay(
                    label = dayLabel(times.optString(i), i),
                    code  = codes?.optInt(i) ?: 0,
                    hiF   = (maxs?.optDouble(i) ?: 0.0).toInt(),
                    loF   = (mins?.optDouble(i) ?: 0.0).toInt(),
                )
            }
            // Hourly: the next ~12 hours starting from the location's current hour.
            val hours = parseHours(o.optJSONObject("hourly"), cur.optString("time"))
            Forecast(location, cur.optDouble("temperature_2m").toInt(), cur.optInt("weather_code"), days, hours)
        }.getOrNull()
    }

    /** The next ~12 hourly entries starting at the location's current hour ("Now"). */
    private fun parseHours(hourly: JSONObject?, currentTime: String): List<WeatherHour> {
        hourly ?: return emptyList()
        val times = hourly.optJSONArray("time") ?: return emptyList()
        val temps = hourly.optJSONArray("temperature_2m")
        val codes = hourly.optJSONArray("weather_code")
        // ISO strings compare lexically; match to the current HOUR (ignore minutes).
        val curHour = currentTime.take(13)   // "2026-06-11T15"
        var start = 0
        for (i in 0 until times.length()) {
            if (times.optString(i).take(13) >= curHour) { start = i; break }
        }
        return (start until minOf(start + 12, times.length())).map { i ->
            WeatherHour(
                label = hourLabel(times.optString(i), i == start),
                code  = codes?.optInt(i) ?: 0,
                tempF = (temps?.optDouble(i) ?: 0.0).toInt(),
            )
        }
    }

    private fun hourLabel(iso: String, isNow: Boolean): String {
        if (isNow) return "Now"
        return runCatching {
            LocalDateTime.parse(iso).format(DateTimeFormatter.ofPattern("h a"))
        }.getOrDefault(iso)
    }

    private fun dayLabel(iso: String, index: Int): String {
        if (index == 0) return "Today"
        return runCatching {
            LocalDate.parse(iso).dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
        }.getOrDefault(iso)
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000; readTimeout = 10000
        }
        return conn.inputStream.bufferedReader().use { it.readText() }.also { conn.disconnect() }
    }

    /** WMO weather-code → short description. */
    fun describe(code: Int): String = when (code) {
        0 -> "Clear sky"
        1 -> "Mainly clear"; 2 -> "Partly cloudy"; 3 -> "Overcast"
        45, 48 -> "Fog"
        51 -> "Light drizzle"; 53 -> "Drizzle"; 55 -> "Heavy drizzle"
        56, 57 -> "Freezing drizzle"
        61 -> "Light rain"; 63 -> "Rain"; 65 -> "Heavy rain"
        66, 67 -> "Freezing rain"
        71 -> "Light snow"; 73 -> "Snow"; 75 -> "Heavy snow"; 77 -> "Snow grains"
        80 -> "Light showers"; 81 -> "Showers"; 82 -> "Violent showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"; 96, 99 -> "Thunderstorm with hail"
        else -> "—"
    }

    /** WMO weather-code → emoji glyph (rendered as text, no icon assets). */
    fun emoji(code: Int): String = when (code) {
        0, 1 -> "☀️"
        2 -> "⛅"; 3 -> "☁️"
        45, 48 -> "🌫️"
        51, 53, 55, 56, 57 -> "🌦️"
        61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️"
        71, 73, 75, 77, 85, 86 -> "🌨️"
        95, 96, 99 -> "⛈️"
        else -> "🌡️"
    }
}
