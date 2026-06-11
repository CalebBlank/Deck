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

/** One controllable Home Assistant entity. Common controls use the typed fields; richer domains
 *  (climate/media) read extra values out of [attributes] (raw HA attributes, stringified; arrays
 *  are kept as their JSON text). */
data class HaEntity(
    val entityId: String,
    val domain: String,
    val friendlyName: String,
    val state: String,        // "on"/"off"/"locked"/"open"/"closed"/hvac-mode/"playing"/…
    val brightness: Int?,     // light: 0-255 when on
    val percentage: Int?,     // fan: 0-100
    val position: Int?,       // cover: current_position 0-100
    val attributes: Map<String, String> = emptyMap()
)

/**
 * Minimal raw-HTTPS client for the Home Assistant REST API (no SDK). Reads the base URL and a
 * long-lived access token from `deck_prefs` (`ha_base_url`, `ha_token`). The entity list is
 * cached briefly so typing in search doesn't refetch every keystroke; after a successful service
 * call we patch that cache so a stale refresh can't flip a just-toggled entity back.
 */
object HomeAssistantClient {
    // Controllable domains. Expand as more controls are added.
    private val DOMAINS = setOf(
        "light", "switch", "input_boolean", "fan", "cover", "lock", "scene", "script",
        "climate", "media_player", "button", "input_button",
        "input_number", "number", "input_select", "select", "vacuum", "humidifier",
        "alarm_control_panel", "sensor", "binary_sensor"
    )
    private const val CACHE_TTL_MS = 10_000L
    private const val TIMEOUT_MS = 8_000

    @Volatile private var cache: List<HaEntity> = emptyList()
    @Volatile private var cacheAt: Long = 0L
    // entity_id -> integration label (config-entry title, else domain). Registries change rarely.
    private const val INTEGRATION_TTL_MS = 5 * 60_000L
    @Volatile private var integrationCache: Map<String, String> = emptyMap()
    @Volatile private var integrationCacheAt: Long = 0L

    private fun prefs(context: Context) =
        context.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE)

    private fun baseUrl(context: Context): String? =
        prefs(context).getString("ha_base_url", "")?.trim()?.trimEnd('/')?.ifBlank { null }

    private fun token(context: Context): String? =
        prefs(context).getString("ha_token", "")?.trim()?.ifBlank { null }

    fun isConfigured(context: Context): Boolean =
        baseUrl(context) != null && token(context) != null

    /** Cached list of controllable entities (light/switch/input_boolean), name-sorted. */
    suspend fun states(context: Context, force: Boolean = false): Result<List<HaEntity>> {
        val base = baseUrl(context) ?: return Result.failure(IllegalStateException("No Home Assistant URL set"))
        val tok  = token(context)   ?: return Result.failure(IllegalStateException("No Home Assistant token set"))

        val now = System.currentTimeMillis()
        if (!force && cache.isNotEmpty() && now - cacheAt < CACHE_TTL_MS) {
            return Result.success(cache)
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                val body = httpGet("$base/api/states", tok)
                val arr = JSONArray(body)
                val out = ArrayList<HaEntity>(arr.length())
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("entity_id")
                    val domain = id.substringBefore('.', "")
                    if (domain !in DOMAINS) continue
                    val attrs = o.optJSONObject("attributes") ?: JSONObject()
                    val name = attrs.optString("friendly_name").ifBlank { id }
                    val attrMap = HashMap<String, String>()
                    attrs.keys().forEach { k -> attrMap[k] = attrs.get(k).toString() }
                    out.add(HaEntity(
                        entityId     = id,
                        domain       = domain,
                        friendlyName = name,
                        state        = o.optString("state"),
                        brightness   = attrs.intOrNull("brightness"),
                        percentage   = attrs.intOrNull("percentage"),
                        position     = attrs.intOrNull("current_position"),
                        attributes   = attrMap
                    ))
                }
                out.sortedBy { it.friendlyName.lowercase() }
            }.onSuccess {
                cache = it
                cacheAt = System.currentTimeMillis()
            }
        }
    }

    /** entity_id -> integration label (config-entry title, falling back to its domain). Cached.
     *  Uses the REST template endpoint; returns empty if the HA version lacks config_entry_attr. */
    suspend fun integrationMap(context: Context): Map<String, String> {
        val base = baseUrl(context) ?: return emptyMap()
        val tok  = token(context)   ?: return emptyMap()
        val now = System.currentTimeMillis()
        if (integrationCacheAt > 0L && now - integrationCacheAt < INTEGRATION_TTL_MS) return integrationCache

        val template =
            "{% set ns = namespace(items=[]) %}{% for s in states %}" +
            "{% set cid = config_entry_id(s.entity_id) %}{% if cid %}" +
            "{% set ns.items = ns.items + [{'e': s.entity_id, 'i': config_entry_attr(cid, 'title'), 'd': config_entry_attr(cid, 'domain')}] %}" +
            "{% endif %}{% endfor %}{{ ns.items | tojson }}"

        val result: Map<String, String> = withContext(Dispatchers.IO) {
            runCatching {
                val body = httpPost("$base/api/template", tok, JSONObject().put("template", template).toString())
                val arr = JSONArray(body)
                val map = HashMap<String, String>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val e = o.optString("e")
                    if (e.isBlank()) continue
                    val title = o.optString("i").takeIf { it.isNotBlank() && it != "None" && it != "null" }
                    val domain = o.optString("d").takeIf { it.isNotBlank() && it != "None" }
                    map[e] = title ?: domain ?: continue
                }
                map as Map<String, String>
            }.getOrDefault(emptyMap())
        }
        integrationCache = result
        integrationCacheAt = System.currentTimeMillis()  // cache failures too, so we don't retry every keystroke
        return result
    }

    /** Patch one cached entity after a successful service call so cache and card stay in sync.
     *  Null attribute args keep the existing cached value. */
    fun patchCache(entityId: String, state: String, brightness: Int? = null, percentage: Int? = null, position: Int? = null) {
        cache = cache.map {
            if (it.entityId == entityId) it.copy(
                state      = state,
                brightness = brightness ?: it.brightness,
                percentage = percentage ?: it.percentage,
                position   = position ?: it.position
            ) else it
        }
    }

    private fun JSONObject.intOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    /** Call a service, e.g. callService(ctx, "light", "turn_on", "light.kitchen", {"brightness_pct":50}). */
    suspend fun callService(
        context: Context,
        domain: String,
        service: String,
        entityId: String,
        extras: JSONObject? = null
    ): Result<Unit> {
        val base = baseUrl(context) ?: return Result.failure(IllegalStateException("No Home Assistant URL set"))
        val tok  = token(context)   ?: return Result.failure(IllegalStateException("No Home Assistant token set"))
        return withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject().put("entity_id", entityId)
                extras?.keys()?.forEach { k -> payload.put(k, extras.get(k)) }
                httpPost("$base/api/services/$domain/$service", tok, payload.toString())
                Unit
            }
        }
    }

    /** Lightweight connection test against /api/ (returns the status message). */
    suspend fun ping(context: Context): Result<String> {
        val base = baseUrl(context) ?: return Result.failure(IllegalStateException("No URL set"))
        val tok  = token(context)   ?: return Result.failure(IllegalStateException("No token set"))
        return withContext(Dispatchers.IO) {
            runCatching {
                val body = httpGet("$base/api/", tok)
                runCatching { JSONObject(body).optString("message").ifBlank { "API running." } }
                    .getOrDefault("API running.")
            }
        }
    }

    /** Absolute URL for an `entity_picture` (e.g. media artwork); relative paths get the base URL. */
    fun imageUrl(context: Context, entityPicture: String?): String? {
        val pic = entityPicture?.takeIf { it.isNotBlank() && it != "null" } ?: return null
        if (pic.startsWith("http://") || pic.startsWith("https://")) return pic
        val base = baseUrl(context) ?: return null
        return if (pic.startsWith("/")) "$base$pic" else "$base/$pic"
    }

    /** Fetch + decode an image (media artwork). Adds the bearer token for same-origin HA URLs. */
    suspend fun fetchImage(context: Context, url: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val base = baseUrl(context)
            val tok = token(context)
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                if (tok != null && base != null && url.startsWith(base)) {
                    setRequestProperty("Authorization", "Bearer $tok")
                }
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            val bmp = if (conn.responseCode in 200..299) conn.inputStream.use { BitmapFactory.decodeStream(it) } else null
            conn.disconnect()
            bmp
        }.getOrNull()
    }

    private fun httpGet(urlStr: String, token: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }
        return readResponse(conn)
    }

    private fun httpPost(urlStr: String, token: String, json: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }
        conn.outputStream.use { it.write(json.toByteArray()) }
        return readResponse(conn)
    }

    private fun readResponse(conn: HttpURLConnection): String {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        if (code !in 200..299) {
            val detail = if (code == 401) "unauthorized — check your token" else "HTTP $code"
            error(detail)
        }
        return body
    }
}
