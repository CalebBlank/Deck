package com.hermes.deck.ui.search.providers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One Tandoor recipe match. [imageUrl] is an absolute, token-authed image URL (or null). */
data class TandoorRecipe(
    val id: Int,
    val name: String,
    val subtitle: String,
    val imageUrl: String?
)

/**
 * Minimal raw-HTTP client for a Tandoor Recipes server (https://tandoor.dev). Reads `tandoor_base_url`
 * + `tandoor_token` from `deck_prefs`. Auth is a long-lived API token sent as `Authorization: Bearer`
 * (create one in Tandoor → Settings → API). Mirrors [PlexClient]; recipe search is a light DRF
 * `?query=` call, so it needs none of Plex's auto-discovery / cancellable-HTTP machinery.
 */
object TandoorClient {
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 12_000

    // Small poster cache so thumbnails survive list recomposition/scroll and aren't refetched.
    private val imageCache = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()

    private fun prefs(c: Context) = c.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE)
    private fun baseUrl(c: Context): String? =
        prefs(c).getString("tandoor_base_url", "")?.trim()?.trimEnd('/')?.ifBlank { null }
    private fun token(c: Context): String? =
        prefs(c).getString("tandoor_token", "")?.trim()?.ifBlank { null }

    fun isConfigured(context: Context): Boolean = baseUrl(context) != null && token(context) != null

    /** Host of the configured instance (for the kitshn deep link, which matches on host). */
    fun host(context: Context): String? =
        baseUrl(context)?.let { runCatching { URL(it).host }.getOrNull()?.ifBlank { null } }

    /** Web page for a recipe (fallback open target). */
    fun webUrl(context: Context, id: Int): String? = baseUrl(context)?.let { "$it/view/recipe/$id" }

    /** Search recipes via `/api/recipe/?query=` (DRF paginated; we use page 1). */
    suspend fun search(context: Context, query: String): Result<List<TandoorRecipe>> {
        val base = baseUrl(context) ?: return Result.failure(IllegalStateException("No Tandoor server URL set"))
        val tok  = token(context)   ?: return Result.failure(IllegalStateException("No Tandoor token set"))
        return withContext(Dispatchers.IO) {
            runCatching {
                val q = URLEncoder.encode(query, "UTF-8")
                val body = httpGet("$base/api/recipe/?query=$q&page=1", tok)
                val results = JSONObject(body).optJSONArray("results") ?: return@runCatching emptyList()
                val out = ArrayList<TandoorRecipe>()
                for (i in 0 until results.length()) {
                    val r = results.optJSONObject(i) ?: continue
                    val id = r.optInt("id", -1).takeIf { it >= 0 } ?: continue
                    val name = r.optString("name").takeIf { it.isNotBlank() } ?: continue
                    out.add(TandoorRecipe(id, name, subtitleFor(r), imageUrl(base, r.optString("image"))))
                }
                out
            }
        }
    }

    /** A short supporting line: the description, else total time, else a generic label. */
    private fun subtitleFor(r: JSONObject): String {
        val desc = r.optString("description").takeIf { it.isNotBlank() && it != "null" }
        if (desc != null) return desc
        val total = r.optInt("working_time", 0) + r.optInt("waiting_time", 0)
        return if (total > 0) "$total min" else "Recipe"
    }

    /** Connection test against the recipe endpoint — returns the recipe count for the space. */
    suspend fun ping(context: Context): Result<String> {
        val base = baseUrl(context) ?: return Result.failure(IllegalStateException("No URL set"))
        val tok  = token(context)   ?: return Result.failure(IllegalStateException("No token set"))
        return withContext(Dispatchers.IO) {
            runCatching {
                val count = JSONObject(httpGet("$base/api/recipe/?page=1&page_size=1", tok)).optInt("count", -1)
                if (count >= 0) "$count recipes" else "OK"
            }
        }
    }

    /** Absolute image URL for the recipe's `image` field (absolute as-is, relative → base-prefixed). */
    private fun imageUrl(base: String, image: String?): String? {
        val t = image?.takeIf { it.isNotBlank() && it != "null" } ?: return null
        return if (t.startsWith("http://") || t.startsWith("https://")) t
               else "$base${if (t.startsWith("/")) t else "/$t"}"
    }

    /** Fetch a recipe image (token-authed — Tandoor media requires the Bearer header) + downsample. */
    suspend fun fetchImage(context: Context, url: String): Bitmap? {
        imageCache[url]?.let { return it }
        val tok = token(context) ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $tok")
                    connectTimeout = CONNECT_TIMEOUT_MS; readTimeout = READ_TIMEOUT_MS
                }
                val code = conn.responseCode
                val bmp = if (code in 200..299) {
                    val bytes = conn.inputStream.use { it.readBytes() }
                    decodeSampled(bytes, 300)
                } else null
                conn.disconnect()
                bmp?.also { if (imageCache.size < 80) imageCache[url] = it }
            }.getOrNull()
        }
    }

    /** Decode [bytes] downsampled so its longest side is ≤ [maxPx] (bounds memory + decode time). */
    private fun decodeSampled(bytes: ByteArray, maxPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxPx || bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun httpGet(urlStr: String, token: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        if (code !in 200..299) error(if (code == 401 || code == 403) "unauthorized — check your token" else "HTTP $code")
        return body
    }
}
