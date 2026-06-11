package com.hermes.deck.ui.search.providers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Google Places + Static Maps client for [PlacesProvider]. Silent until a Google Maps Platform API
 * key is set in Settings → Search → Google Maps. Uses Places API (New) Text Search, biased to the
 * device's last-known location, plus the Maps Static API for the thumbnail map.
 *
 * KEY RESTRICTION GOTCHA: Static Maps and Places (New) are WEB-SERVICE calls. An Android-app-
 * restricted key (package + SHA-1) 403s them — that restriction only covers the Maps SDK. The key
 * must be unrestricted, or API-restricted (to Places API + Maps Static API) only.
 */
object PlacesClient {
    private const val PREFS = "deck_prefs"
    private const val KEY = "maps_api_key"
    private const val SEARCH_URL = "https://places.googleapis.com/v1/places:searchText"
    // Places API (New) REQUIRES a field mask; omitting it is a 400. Keep it to exactly what we render.
    private const val FIELD_MASK =
        "places.id,places.displayName,places.formattedAddress,places.location,places.rating,places.userRatingCount,places.currentOpeningHours.openNow"

    // Geographic-relevance filter: keep places that are nearby OR prominent enough to be a landmark.
    private const val NEAR_KM = 100.0           // within this distance of the user counts as "local"
    private const val LANDMARK_RATINGS = 5000   // a far place needs at least this many reviews to be a "landmark"

    private fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1); val dLng = Math.toRadians(lng2 - lng1)
        val sLat = Math.sin(dLat / 2); val sLng = Math.sin(dLng / 2)
        val a = sLat * sLat + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * sLng * sLng
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    data class Place(
        val id: String,
        val name: String,
        val address: String,
        val lat: Double,
        val lng: Double,
        val rating: Double?,
        val userRatingCount: Int?,
        val openNow: Boolean?,
    )

    fun apiKey(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "")?.trim().orEmpty()

    fun isConfigured(context: Context): Boolean = apiKey(context).isNotBlank()

    /** Result cap from the provider's generic "Result limit" slider (provider_limit_places): a value
     *  of 1–8, or Unlimited (0) → 9, the Static Maps numbered-pin ceiling (labels are single chars). */
    fun maxResults(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("provider_limit_places", 0)
            .let { if (it in 1..8) it else 9 }

    /** Device last-known location (lat,lng) via LocationManager, or null without permission / a fix. */
    fun lastLocation(context: Context): Pair<Double, Double>? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        @Suppress("MissingPermission")
        val loc = runCatching { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
            ?: runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
            ?: return null
        return loc.latitude to loc.longitude
    }

    suspend fun search(context: Context, query: String): Result<List<Place>> = withContext(Dispatchers.IO) {
        val key = apiKey(context)
        if (key.isBlank()) return@withContext Result.failure(IllegalStateException("No Google Maps API key set"))
        runCatching {
            val body = JSONObject().apply {
                put("textQuery", query)
                put("maxResultCount", maxResults(context))   // honors the provider's Result-limit slider (≤9)
                lastLocation(context)?.let { (lat, lng) ->
                    put("locationBias", JSONObject().put("circle", JSONObject()
                        .put("center", JSONObject().put("latitude", lat).put("longitude", lng))
                        .put("radius", 20000.0)))
                }
            }.toString()
            val conn = (URL(SEARCH_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12000; readTimeout = 12000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("X-Goog-Api-Key", key)
                setRequestProperty("X-Goog-FieldMask", FIELD_MASK)
            }
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(text).optJSONObject("error")?.optString("message") }.getOrNull()
                throw RuntimeException(msg?.ifBlank { null } ?: "Places request failed (HTTP $code)")
            }
            val arr = JSONObject(text).optJSONArray("places") ?: return@runCatching emptyList()
            val parsed = (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val loc = o.optJSONObject("location") ?: return@mapNotNull null
                Place(
                    id = o.optString("id"),
                    name = o.optJSONObject("displayName")?.optString("text").orEmpty().ifBlank { "(unnamed)" },
                    address = o.optString("formattedAddress"),
                    lat = loc.optDouble("latitude"),
                    lng = loc.optDouble("longitude"),
                    rating = if (o.has("rating")) o.optDouble("rating") else null,
                    userRatingCount = if (o.has("userRatingCount")) o.optInt("userRatingCount") else null,
                    openNow = o.optJSONObject("currentOpeningHours")?.let {
                        if (it.has("openNow")) it.optBoolean("openNow") else null
                    },
                )
            }
            // Geographic relevance: a strong NAME match can surface a place on the other side of the world
            // (a store literally named "SteamDeck" in Vietnam). Keep places that are nearby OR prominent
            // enough to be a landmark you'd intentionally look up (Eiffel Tower). Without a location fix we
            // can't judge distance, so keep everything.
            val here = lastLocation(context)
            if (here == null) parsed
            else parsed.filter { p ->
                haversineKm(here.first, here.second, p.lat, p.lng) <= NEAR_KM ||
                    (p.userRatingCount ?: 0) >= LANDMARK_RATINGS
            }
        }
    }

    /** Maps Static API URL with numbered markers (labels are single alphanumeric chars → 1..9). */
    fun staticMapUrl(context: Context, places: List<Place>, widthPx: Int, heightPx: Int): String? {
        val key = apiKey(context)
        if (key.isBlank() || places.isEmpty()) return null
        val w = widthPx.coerceIn(120, 640); val h = heightPx.coerceIn(80, 640)
        val sb = StringBuilder("https://maps.googleapis.com/maps/api/staticmap?size=${w}x${h}&scale=2")
        places.take(9).forEachIndexed { i, p ->
            sb.append("&markers=").append(URLEncoder.encode("color:red|label:${i + 1}|${p.lat},${p.lng}", "UTF-8"))
        }
        sb.append("&key=").append(key)
        return sb.toString()
    }

    suspend fun fetchBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12000; readTimeout = 12000
            }
            conn.inputStream.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }
}
