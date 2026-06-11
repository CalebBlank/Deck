package com.hermes.deck.ui.search.providers

import android.content.Context
import com.hermes.deck.data.AppInfo
import com.hermes.deck.ui.search.SearchResult
import org.json.JSONArray
import org.json.JSONObject

/** One turn in a Claude conversation. role is "user" or "assistant". */
data class ChatMessage(val role: String, val content: String)

/** Which AI backend a chat talks to. Claude = Anthropic API; Hermes = self-hosted agent. */
enum class ChatBackend { Claude, Hermes }

/**
 * An action Claude has proposed (via an action tool) that needs the user's confirmation before it
 * runs — rendered as a Confirm/Cancel card in the chat. [kind] selects the executor; [params] holds
 * the data it needs (e.g. ha_control → entity_id/service/domain).
 */
data class ClaudePendingAction(
    val id: String,
    val summary: String,
    val kind: String,                       // "ha_control" (apps/calls/settings later)
    val params: Map<String, String>,
    val status: String = "pending"          // "pending" | "done" | "failed" | "cancelled"
)

/** A persisted Claude conversation. Cards/actions are keyed by the assistant message index. */
data class ChatSession(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val messages: List<ChatMessage>,
    val cardsByMessage: Map<Int, List<SearchResult>> = emptyMap(),
    val actionsByMessage: Map<Int, List<ClaudePendingAction>> = emptyMap(),
    // Conversation handoff (per-turn shared store): when this session was resumed from a thread
    // that lives on another surface, [remoteTid] is that shared thread id and [importCount] is how
    // many leading messages came from there — so we only ever push this session's OWN later turns.
    val remoteTid: String? = null,
    val importCount: Int = 0,
    val backend: ChatBackend = ChatBackend.Claude
)

/** Live state of an in-progress Claude conversation, rendered as message cards in search. */
data class ClaudeChatState(
    val sessionId: String,
    val messages: List<ChatMessage>,
    val loading: Boolean = false,
    val error: String? = null,
    val lastInTokens: Int = 0,
    val lastOutTokens: Int = 0,
    // Live partial text of the in-progress streamed answer (null when not streaming).
    val streamingText: String? = null,
    // Result cards Claude presented (show_results) and actions it proposed, each keyed by the index
    // of the assistant message it belongs to — so they render interleaved right after that message
    // and accumulate across turns (instead of only the latest turn's, at the bottom).
    val cardsByMessage: Map<Int, List<SearchResult>> = emptyMap(),
    val actionsByMessage: Map<Int, List<ClaudePendingAction>> = emptyMap(),
    // Carried from a resumed [ChatSession] so the per-turn push knows which thread to append to and
    // how many leading messages are inherited (and must not be re-pushed as ours). See ChatSession.
    val remoteTid: String? = null,
    val importCount: Int = 0,
    val backend: ChatBackend = ChatBackend.Claude
)

/**
 * Tiny JSON-in-SharedPreferences store for Claude chat sessions. Keeps the most
 * recently-updated [MAX_SESSIONS]; the idle Claude card surfaces the latest few.
 */
object ClaudeChatStore {
    private const val KEY = "claude_chat_sessions"
    private const val PINNED_KEY = "claude_pinned_sessions"
    private const val MAX_SESSIONS = 20

    fun recent(context: Context, limit: Int, backend: ChatBackend = ChatBackend.Claude): List<ChatSession> =
        load(context).filter { it.backend == backend }.sortedByDescending { it.updatedAt }.take(limit)

    /** Sessions whose title or message content matches [query], best match first (then most recent).
     *  Empty when nothing matches — so the idle card falls back to just the "Ask …" prompt. */
    fun relevant(context: Context, query: String, limit: Int, backend: ChatBackend = ChatBackend.Claude): List<ChatSession> {
        val tokens = query.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 2 }
        if (tokens.isEmpty()) return emptyList()
        return load(context).filter { it.backend == backend }
            .mapNotNull { s ->
                val hay = (s.title + " " + s.messages.joinToString(" ") { it.content }).lowercase()
                val score = tokens.count { hay.contains(it) }
                if (score > 0) s to score else null
            }
            .sortedWith(compareByDescending<Pair<ChatSession, Int>> { it.second }.thenByDescending { it.first.updatedAt })
            .take(limit)
            .map { it.first }
    }

    fun get(context: Context, id: String): ChatSession? = load(context).find { it.id == id }

    /** Forget a saved session (and its pin). */
    fun delete(context: Context, id: String) {
        persist(context, load(context).filterNot { it.id == id })
        setPinned(context, id, false)
    }

    fun save(context: Context, session: ChatSession) {
        val merged = load(context).filter { it.id != session.id } + session
        // Pinned threads are exempt from the cap so they can't silently fall out of storage
        // once MAX_SESSIONS newer sessions exist.
        val pinned = pinnedIds(context)
        val (keep, rest) = merged.partition { it.id in pinned }
        val trimmed = keep + rest.sortedByDescending { it.updatedAt }.take(MAX_SESSIONS)
        persist(context, trimmed)
    }

    /** Ids of pinned sessions (kept regardless of recency, always surfaced in the idle card). */
    fun pinnedIds(context: Context): Set<String> {
        val raw = prefs(context).getString(PINNED_KEY, "") ?: ""
        if (raw.isBlank()) return emptySet()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        }.getOrDefault(emptySet())
    }

    fun isPinned(context: Context, id: String): Boolean = id in pinnedIds(context)

    fun setPinned(context: Context, id: String, pinned: Boolean) {
        val ids = pinnedIds(context).toMutableSet()
        if (pinned) ids.add(id) else ids.remove(id)
        val arr = JSONArray()
        ids.forEach { arr.put(it) }
        prefs(context).edit().putString(PINNED_KEY, arr.toString()).apply()
    }

    /** Pinned sessions that still exist in storage, most-recent first. */
    fun pinnedSessions(context: Context, limit: Int = Int.MAX_VALUE, backend: ChatBackend = ChatBackend.Claude): List<ChatSession> {
        val ids = pinnedIds(context)
        if (ids.isEmpty()) return emptyList()
        return load(context).filter { it.id in ids && it.backend == backend }.sortedByDescending { it.updatedAt }.take(limit)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE)

    private fun load(context: Context): List<ChatSession> {
        val raw = prefs(context).getString(KEY, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val msgs = o.getJSONArray("messages")
                ChatSession(
                    id        = o.getString("id"),
                    title     = o.getString("title"),
                    updatedAt = o.getLong("updatedAt"),
                    messages  = (0 until msgs.length()).map { j ->
                        val m = msgs.getJSONObject(j)
                        ChatMessage(m.getString("role"), m.getString("content"))
                    },
                    cardsByMessage   = deserializeCardMap(context, o.optJSONObject("cards")),
                    actionsByMessage = deserializeActionMap(o.optJSONObject("actions")),
                    remoteTid        = o.optString("remoteTid").takeIf { it.isNotBlank() },
                    importCount      = o.optInt("importCount", 0),
                    backend          = runCatching { ChatBackend.valueOf(o.optString("backend", "Claude")) }
                        .getOrDefault(ChatBackend.Claude)
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun persist(context: Context, sessions: List<ChatSession>) {
        val arr = JSONArray()
        sessions.forEach { s ->
            val msgs = JSONArray()
            s.messages.forEach { m ->
                msgs.put(JSONObject().put("role", m.role).put("content", m.content))
            }
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("title", s.title)
                    .put("updatedAt", s.updatedAt)
                    .put("messages", msgs)
                    .put("cards", serializeCardMap(s.cardsByMessage))
                    .put("actions", serializeActionMap(s.actionsByMessage))
                    .put("remoteTid", s.remoteTid ?: JSONObject.NULL)
                    .put("importCount", s.importCount)
                    .put("backend", s.backend.name)
            )
        }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }

    // --- card / action persistence (cards/actions keyed by assistant-message index) ---

    private fun serializeCardMap(map: Map<Int, List<SearchResult>>): JSONObject {
        val o = JSONObject()
        map.forEach { (idx, cards) ->
            val arr = JSONArray()
            cards.forEach { c -> serializeChatCard(c)?.let { arr.put(it) } }
            if (arr.length() > 0) o.put(idx.toString(), arr)
        }
        return o
    }

    private fun deserializeCardMap(context: Context, o: JSONObject?): Map<Int, List<SearchResult>> {
        if (o == null) return emptyMap()
        val map = HashMap<Int, List<SearchResult>>()
        o.keys().forEach { k ->
            val idx = k.toIntOrNull() ?: return@forEach
            val arr = o.optJSONArray(k) ?: return@forEach
            val list = (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { deserializeChatCard(context, it) }
            }
            if (list.isNotEmpty()) map[idx] = list
        }
        return map
    }

    private fun serializeActionMap(map: Map<Int, List<ClaudePendingAction>>): JSONObject {
        val o = JSONObject()
        map.forEach { (idx, actions) ->
            val arr = JSONArray()
            actions.forEach { a ->
                val params = JSONObject()
                a.params.forEach { (k, v) -> params.put(k, v) }
                arr.put(JSONObject().put("id", a.id).put("summary", a.summary)
                    .put("kind", a.kind).put("params", params).put("status", a.status))
            }
            if (arr.length() > 0) o.put(idx.toString(), arr)
        }
        return o
    }

    private fun deserializeActionMap(o: JSONObject?): Map<Int, List<ClaudePendingAction>> {
        if (o == null) return emptyMap()
        val map = HashMap<Int, List<ClaudePendingAction>>()
        o.keys().forEach { k ->
            val idx = k.toIntOrNull() ?: return@forEach
            val arr = o.optJSONArray(k) ?: return@forEach
            map[idx] = (0 until arr.length()).mapNotNull { i ->
                val a = arr.optJSONObject(i) ?: return@mapNotNull null
                val p = a.optJSONObject("params") ?: JSONObject()
                val pmap = p.keys().asSequence().associateWith { p.optString(it) }
                ClaudePendingAction(a.optString("id"), a.optString("summary"), a.optString("kind"),
                    pmap, a.optString("status").ifBlank { "pending" })
            }
        }
        return map
    }

    /** Serialize the subset of result cards Claude presents; null for types we won't reconstruct. */
    private fun serializeChatCard(r: SearchResult): JSONObject? = when (r) {
        is SearchResult.HomeAssistantResult -> JSONObject().apply {
            put("t", "ha"); put("entityId", r.entityId); put("domain", r.domain)
            put("name", r.friendlyName); put("state", r.state)
            r.brightness?.let { put("brightness", it) }
            r.percentage?.let { put("percentage", it) }
            r.position?.let { put("position", it) }
            val attrs = JSONObject(); r.attributes.forEach { (k, v) -> attrs.put(k, v) }
            put("attrs", attrs)
        }
        is SearchResult.AppResult -> JSONObject().put("t", "app").put("pkg", r.app.packageName)
        is SearchResult.ContactResult -> JSONObject().apply {
            put("t", "contact"); put("name", r.name)
            r.phoneNumber?.let { put("phone", it) }; r.email?.let { put("email", it) }; r.photoUri?.let { put("photo", it) }
        }
        is SearchResult.DialerResult -> JSONObject().put("t", "dialer").put("number", r.phoneNumber).put("display", r.displayText)
        is SearchResult.SettingsResult -> JSONObject().put("t", "settings").put("title", r.title).put("subtitle", r.subtitle).put("section", r.section)
        is SearchResult.SystemSettingsResult -> JSONObject().put("t", "sys").put("title", r.title).put("subtitle", r.subtitle).put("action", r.action)
        is SearchResult.FileResult -> JSONObject().apply { put("t", "file"); put("path", r.path); put("name", r.name); r.mimeType?.let { put("mime", it) } }
        is SearchResult.BrowserHistoryResult -> JSONObject().put("t", "browser").put("url", r.url).put("title", r.title).put("browser", r.browserName)
        else -> null
    }

    private fun deserializeChatCard(context: Context, o: JSONObject): SearchResult? {
        fun intOrNull(key: String) = if (o.has(key) && !o.isNull(key)) o.optInt(key) else null
        fun strOrNull(key: String) = o.optString(key).takeIf { it.isNotBlank() }
        return when (o.optString("t")) {
            "ha" -> {
                val a = o.optJSONObject("attrs")
                val attrs = a?.keys()?.asSequence()?.associateWith { a.optString(it) } ?: emptyMap()
                SearchResult.HomeAssistantResult(
                    o.optString("entityId"), o.optString("domain"), o.optString("name"),
                    o.optString("state"), intOrNull("brightness"), intOrNull("percentage"),
                    intOrNull("position"), attrs
                )
            }
            "app" -> runCatching {
                val pkg = o.optString("pkg"); val pm = context.packageManager
                val info = pm.getApplicationInfo(pkg, 0)
                SearchResult.AppResult(AppInfo(pkg, pm.getApplicationLabel(info).toString(), pm.getApplicationIcon(pkg)))
            }.getOrNull()
            "contact" -> SearchResult.ContactResult(o.optString("name"), strOrNull("phone"), strOrNull("email"), strOrNull("photo"))
            "dialer" -> SearchResult.DialerResult(o.optString("number"), o.optString("display"))
            "settings" -> SearchResult.SettingsResult(o.optString("title"), o.optString("subtitle"), o.optString("section"))
            "sys" -> SearchResult.SystemSettingsResult(o.optString("title"), o.optString("subtitle"), o.optString("action"))
            "file" -> SearchResult.FileResult(o.optString("path"), o.optString("name"), strOrNull("mime"))
            "browser" -> SearchResult.BrowserHistoryResult(o.optString("url"), o.optString("title"), o.optString("browser"))
            else -> null
        }
    }
}
