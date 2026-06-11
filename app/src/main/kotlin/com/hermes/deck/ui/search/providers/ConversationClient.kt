package com.hermes.deck.ui.search.providers

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Conversation handoff (v2, per-TURN store) — leave a thread on one device, pick it up on another.
 *
 * Every message is its own immutable PocketBase record, so no surface ever overwrites another's
 * turns (the v1 last-writer-wins data loss). A row's cid is
 *     "<threadId>#<surface>-<contributorSession>#<localIndex6>"
 * which is UNIQUE, so re-posting a turn is a no-op (409/400 → skipped). A thread = all rows whose
 * cid starts with "<threadId>#"; reconstruct by `created` (chronological across surfaces).
 *
 * This device only ever pushes ITS OWN turns: for a resumed thread the first [importCount] messages
 * came from elsewhere, so we push `messages.drop(importCount)` under our own contributor id. A small
 * per-session cursor (in prefs) avoids re-attempting already-stored turns; the unique cid is the
 * real idempotency guarantee. **Auth-gated** (transcripts are sensitive) — reuses the `pb_*` prefs.
 */
object ConversationClient {

    /** A reconstructed shared thread (metadata + ordered messages). */
    data class Thread(
        val tid: String,
        val title: String,
        val surfaces: List<String>,
        val lastSurface: String,
        val updated: String,
        val messages: List<Pair<String, String>>   // (role, content), chronological
    )

    @Volatile private var token: String? = null

    fun isConfigured(context: Context): Boolean = SharedMemoryClient.isConfigured(context)

    /**
     * Append this session's own turns to the shared thread [tid], idempotently. [ownTurns] are the
     * messages produced on THIS device (already excluding any imported prefix). Best-effort.
     */
    suspend fun appendNew(
        context: Context, tid: String, contributorSession: String, title: String,
        ownTurns: List<Pair<String, String>>
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val t = ensureToken(context) ?: return@runCatching false
            val p = prefs(context)
            val curKey = "conv_cur_$contributorSession"
            val contrib = "deck-$contributorSession"
            var i = p.getInt(curKey, 0)
            while (i < ownTurns.size) {
                val (role, content) = ownTurns[i]
                val cid = "%s#%s#%06d".format(tid, contrib, i)
                if (!postTurn(context, cid, title, role, content, t)) break   // network failure → retry next time
                i++
            }
            p.edit().putInt(curKey, i).apply()
            true
        }.getOrDefault(false)
    }

    /** Recent threads, newest first (grouped from per-turn rows). Empty on any failure. */
    suspend fun list(context: Context): List<Thread> = withContext(Dispatchers.IO) {
        runCatching { groupThreads(allRows(context, null)) }.getOrDefault(emptyList())
    }

    /** A single thread by id (full messages), or null. */
    suspend fun get(context: Context, tid: String): Thread? = withContext(Dispatchers.IO) {
        runCatching {
            val flt = "(cid~'" + tid.replace("'", "") + "#')"
            groupThreads(allRows(context, flt)).firstOrNull { it.tid == tid }
        }.getOrNull()
    }

    // --- internals -----------------------------------------------------------------------------

    private fun prefs(context: Context) = context.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE)
    private fun base(context: Context) = (prefs(context).getString("pb_base_url", "") ?: "").trim().trimEnd('/')

    private fun groupThreads(rows: List<JSONObject>): List<Thread> {
        // rows arrive sorted by created,cid (server-side); preserve that order within each group.
        val groups = LinkedHashMap<String, MutableList<JSONObject>>()
        for (r in rows) {
            val cid = r.optString("cid")
            val tid = cid.substringBefore('#')
            groups.getOrPut(tid) { mutableListOf() }.add(r)
        }
        return groups.map { (tid, rs) ->
            val msgs = rs.mapNotNull { r ->
                val m = r.optJSONObject("messages")
                val content = m?.optString("content") ?: ""
                if (m != null && content.isNotEmpty()) m.optString("role") to content else null
            }
            Thread(
                tid = tid,
                title = rs.firstOrNull()?.optString("title") ?: "",
                surfaces = rs.map { it.optString("surface") }.distinct(),
                lastSurface = rs.lastOrNull()?.optString("surface") ?: "",
                updated = rs.lastOrNull()?.optString("created") ?: "",
                messages = msgs
            )
        }.sortedByDescending { it.updated }
    }

    /** Fetch all records (optionally filtered), paginated, sorted chronologically. */
    private fun allRows(context: Context, filter: String?): List<JSONObject> {
        val t = ensureToken(context) ?: return emptyList()
        val out = ArrayList<JSONObject>()
        var page = 1
        while (page <= 60) {
            var url = base(context) +
                "/api/collections/conversations/records?perPage=200&page=$page&sort=created,cid"
            if (filter != null) url += "&filter=" + URLEncoder.encode(filter, "UTF-8")
            val body = get(url, t) ?: break
            val obj = JSONObject(body)
            val items = obj.optJSONArray("items") ?: JSONArray()
            for (i in 0 until items.length()) items.optJSONObject(i)?.let { out.add(it) }
            if (items.length() == 0 || page >= obj.optInt("totalPages", 1)) break
            page++
        }
        return out
    }

    /** POST one turn. Returns true if it's now stored (200) or already was (400 unique-violation);
     *  false only on a network/server failure worth retrying. */
    private fun postTurn(context: Context, cid: String, title: String, role: String, content: String, token: String): Boolean {
        val payload = JSONObject()
            .put("cid", cid).put("title", title.take(80))
            .put("messages", JSONObject().put("role", role).put("content", content))
            .put("surface", "deck").toString()
        val conn = (URL(base(context) + "/api/collections/conversations/records").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 5000; readTimeout = 10000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", token)
        }
        return try {
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            code in 200..299 || code == 400        // 400 = unique cid already exists → treat as stored
        } catch (e: Exception) {
            false
        } finally {
            conn.disconnect()
        }
    }

    private fun ensureToken(context: Context): String? {
        token?.let { return it }
        val payload = JSONObject()
            .put("identity", prefs(context).getString("pb_email", "")?.trim().orEmpty())
            .put("password", prefs(context).getString("pb_password", "")?.trim().orEmpty())
            .toString()
        val resp = send(base(context) + "/api/collections/users/auth-with-password", payload) ?: return null
        token = runCatching { JSONObject(resp).optString("token").takeIf { it.isNotBlank() } }.getOrNull()
        return token
    }

    private fun get(url: String, token: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 5000; readTimeout = 10000
            setRequestProperty("Authorization", token)
        }
        return try {
            if (conn.responseCode in 200..299) conn.inputStream.bufferedReader().use { it.readText() } else null
        } catch (e: Exception) { null } finally { conn.disconnect() }
    }

    private fun send(url: String, body: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 5000; readTimeout = 10000
            setRequestProperty("Content-Type", "application/json")
        }
        return try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode in 200..299) conn.inputStream.bufferedReader().use { it.readText() } else null
        } catch (e: Exception) { null } finally { conn.disconnect() }
    }
}
