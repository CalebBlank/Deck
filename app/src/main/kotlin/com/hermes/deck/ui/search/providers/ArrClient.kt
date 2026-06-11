package com.hermes.deck.ui.search.providers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Shared client for Radarr (movies) and Sonarr (TV). Both expose the same v3 API shape, so one client
 * serves both keyed by [service] ("radarr"/"sonarr"), reading per-service URL + API key from prefs.
 *
 * Add flow (per the API): take the lookup result object verbatim and augment it with qualityProfileId
 * + rootFolderPath + monitored + addOptions, then POST it — that guarantees every required field is
 * present. The first quality profile + root folder are used (v1; most setups have one of each).
 */
object ArrClient {
    private const val PREFS = "deck_prefs"

    data class ArrItem(
        val service: String,
        val title: String,
        val year: Int?,
        val overview: String,
        val posterUrl: String?,
        val alreadyAdded: Boolean,   // lookup "id" > 0 → already in Radarr/Sonarr
        val lookupJson: String,      // raw candidate object, POSTed back to add
    )

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun baseUrl(c: Context, service: String) =
        prefs(c).getString("${service}_url", "")?.trim()?.trimEnd('/').orEmpty()
    private fun apiKey(c: Context, service: String) =
        prefs(c).getString("${service}_api_key", "")?.trim().orEmpty()

    fun isConfigured(context: Context, service: String): Boolean =
        baseUrl(context, service).isNotBlank() && apiKey(context, service).isNotBlank()

    private fun endpoint(service: String) = if (service == "sonarr") "series" else "movie"

    /** Search Radarr/Sonarr's lookup (TMDb/TVDb-backed) for titles matching [term]. */
    suspend fun lookup(context: Context, service: String, term: String): List<ArrItem> = withContext(Dispatchers.IO) {
        val base = baseUrl(context, service); val key = apiKey(context, service)
        if (base.isBlank() || key.isBlank()) return@withContext emptyList()
        runCatching {
            val url = "$base/api/v3/${endpoint(service)}/lookup?term=${URLEncoder.encode(term, "UTF-8")}"
            val arr = JSONArray(get(url, key))
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val title = o.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val year = if (o.has("year") && o.optInt("year") > 0) o.optInt("year") else null
                val poster = o.optJSONArray("images")?.let { imgs ->
                    (0 until imgs.length()).firstNotNullOfOrNull { j ->
                        imgs.optJSONObject(j)?.takeIf { it.optString("coverType") == "poster" }
                            ?.let { it.optString("remoteUrl").ifBlank { it.optString("url") } }
                            ?.takeIf { u -> u.isNotBlank() }
                    }
                }
                ArrItem(service, title, year, o.optString("overview"), poster,
                    alreadyAdded = o.optInt("id", 0) > 0, lookupJson = o.toString())
            }
        }.getOrDefault(emptyList())
    }

    /** Add a looked-up item: augment its object with the first quality profile + root folder, POST it,
     *  and trigger a search. Failure message bubbles up for the card. */
    suspend fun add(context: Context, service: String, lookupJson: String): Result<Unit> = withContext(Dispatchers.IO) {
        val base = baseUrl(context, service); val key = apiKey(context, service)
        if (base.isBlank() || key.isBlank()) return@withContext Result.failure(IllegalStateException("Not configured"))
        runCatching {
            val profileId = firstId(get("$base/api/v3/qualityprofile", key))
                ?: error("No quality profile in $service")
            val rootPath = bestRootPath(get("$base/api/v3/rootfolder", key))
                ?: error("No root folder in $service")
            val body = JSONObject(lookupJson).apply {
                put("qualityProfileId", profileId)
                put("rootFolderPath", rootPath)
                put("monitored", true)
                if (service == "sonarr") {
                    put("seasonFolder", true)
                    put("addOptions", JSONObject().put("searchForMissingEpisodes", true).put("monitor", "all"))
                } else {
                    put("minimumAvailability", "released")
                    put("addOptions", JSONObject().put("searchForMovie", true))
                }
            }
            val code = post("$base/api/v3/${endpoint(service)}", key, body.toString())
            if (code !in 200..299) error("$service returned HTTP $code")
        }
    }

    private fun firstId(json: String): Int? =
        runCatching { JSONArray(json).optJSONObject(0)?.optInt("id")?.takeIf { it > 0 } }.getOrNull()
    /** Pick the root folder with the MOST free space (a "use the first" default lands on a full drive
     *  when there are many root folders, like Caleb's JBOD where the first Sonarr folder is 0 GB). */
    private fun bestRootPath(json: String): String? = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            .maxByOrNull { it.optLong("freeSpace", 0L) }
            ?.optString("path")?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun get(url: String, key: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000; readTimeout = 15000
            setRequestProperty("X-Api-Key", key)
            setRequestProperty("Accept", "application/json")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }.also { conn.disconnect() }
    }

    private fun post(url: String, key: String, body: String): Int {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            connectTimeout = 12000; readTimeout = 15000
            setRequestProperty("X-Api-Key", key)
            setRequestProperty("Content-Type", "application/json")
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        return conn.responseCode.also { conn.disconnect() }
    }

    suspend fun fetchPoster(url: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 12000; readTimeout = 12000 }
            conn.inputStream.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }

    /** Normalize a title for cross-checking against Plex: casefold, strip punctuation + a leading
     *  article. Match on normalized title AND exact year so remakes (Dune 1984 vs 2021) aren't merged. */
    fun normalizeTitle(t: String): String =
        t.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("^(the|a|an)\\s+"), "")
            .trim().replace(Regex("\\s+"), " ")
}
