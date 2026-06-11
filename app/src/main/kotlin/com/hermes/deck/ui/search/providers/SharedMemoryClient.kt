package com.hermes.deck.ui.search.providers

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client for Caleb's shared "about-me core" memory, stored in PocketBase on the HA Yellow.
 *
 * Lets the phone Claude read and write the SAME memory the desktop (Claude Code) uses — one brain
 * across surfaces. Plain HTTP on the LAN; authenticates as a low-privilege service account
 * (agent@deck.local), not the superadmin. Best-effort: every call returns null/does nothing on
 * failure so the local cache ([ClaudeMemoryStore]) keeps working off-LAN or when PocketBase is down.
 *
 * Prefs (deck_prefs): pb_base_url, pb_email, pb_password.
 */
object SharedMemoryClient {
    @Volatile private var token: String? = null

    fun isConfigured(context: Context): Boolean {
        val p = prefs(context)
        return !p.getString("pb_base_url", "").isNullOrBlank() &&
               !p.getString("pb_email", "").isNullOrBlank() &&
               !p.getString("pb_password", "").isNullOrBlank()
    }

    /** Pull the shared memory fact texts, or null on any failure (caller keeps its cache). */
    suspend fun pull(context: Context): List<String>? = withContext(Dispatchers.IO) {
        runCatching {
            // The memory collection is public-read on the LAN, so reads need NO auth — this keeps the
            // phone "knowing you" working even if the service-account login is unavailable.
            val body = get(base(context) + "/api/collections/memory/records?perPage=200&sort=category,key", null)
                ?: return@runCatching null
            val items = JSONObject(body).optJSONArray("items") ?: JSONArray()
            (0 until items.length()).mapNotNull {
                items.optJSONObject(it)?.optString("text")?.takeIf { s -> s.isNotBlank() }
            }
        }.getOrNull()
    }

    /** Save a single fact with an explicit category. Returns true on success. */
    suspend fun add(context: Context, text: String, category: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val t = ensureToken(context) ?: return@runCatching false
            val payload = JSONObject()
                .put("text", text.trim())
                .put("category", category.ifBlank { "other" })
                .put("source_platform", "deck")
                .toString()
            post(base(context) + "/api/collections/memory/records", payload, t) != null
        }.getOrDefault(false)
    }

    /** Push new durable facts to the shared store (source_platform=deck). Best-effort. */
    suspend fun push(context: Context, facts: List<String>) {
        withContext(Dispatchers.IO) {
            runCatching {
                val t = ensureToken(context) ?: return@runCatching
                for (f in facts) {
                    val payload = JSONObject()
                        .put("text", f.trim())
                        .put("category", "preference")
                        .put("source_platform", "deck")
                        .toString()
                    post(base(context) + "/api/collections/memory/records", payload, t)
                }
            }
        }
    }

    // --- internals ---

    private fun prefs(context: Context) =
        context.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE)

    private fun base(context: Context) =
        (prefs(context).getString("pb_base_url", "") ?: "").trim().trimEnd('/')

    private fun ensureToken(context: Context): String? {
        token?.let { return it }
        val payload = JSONObject()
            .put("identity", prefs(context).getString("pb_email", "")?.trim().orEmpty())
            .put("password", prefs(context).getString("pb_password", "")?.trim().orEmpty())
            .toString()
        val resp = post(base(context) + "/api/collections/users/auth-with-password", payload, null) ?: return null
        token = runCatching { JSONObject(resp).optString("token").takeIf { it.isNotBlank() } }.getOrNull()
        return token
    }

    private fun get(url: String, token: String?): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 5000; readTimeout = 8000
            if (token != null) setRequestProperty("Authorization", token)
        }
        return try {
            if (conn.responseCode in 200..299) conn.inputStream.bufferedReader().use { it.readText() } else null
        } catch (e: Exception) { null } finally { conn.disconnect() }
    }

    private fun post(url: String, body: String, token: String?): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 5000; readTimeout = 8000
            setRequestProperty("Content-Type", "application/json")
            if (token != null) setRequestProperty("Authorization", token)
        }
        return try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode in 200..299) conn.inputStream.bufferedReader().use { it.readText() }
            else { conn.errorStream?.bufferedReader()?.use { it.readText() }; null }
        } catch (e: Exception) { null } finally { conn.disconnect() }
    }
}
