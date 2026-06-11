package com.hermes.deck.ui.search.providers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal raw-HTTP client for the Anthropic Messages API (no SDK).
 *
 * One-shot use: sends the typed query as a single user message and returns the
 * text response. The API key and model are read from "deck_prefs". Prompt
 * caching is intentionally NOT used — each query is a unique, tiny prefix far
 * below the cacheable minimum, so a cache breakpoint would only add cost.
 */
/** Answer text plus the token usage the API reported for that one request. */
data class ClaudeResponse(
    val text: String,
    val inputTokens: Int,
    val outputTokens: Int
)

object AnthropicClient {
    private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
    private const val ANTHROPIC_VERSION = "2023-06-01"
    const val DEFAULT_MODEL = "claude-opus-4-8"
    const val MAX_TOKENS = 1024
    // Thinking tokens count against max_tokens, so give a thinking request much more headroom.
    const val THINKING_MAX_TOKENS = 8192
    // Safety cap on the agentic tool-use loop (each iteration is one round-trip).
    const val MAX_TOOL_ITERATIONS = 6

    /** Built-in default system prompt, used when the user hasn't set a custom one in Settings. */
    fun defaultSystemPrompt(model: String): String =
        "You are Claude, an AI assistant made by Anthropic, running as the \"$model\" model. " +
        "You are embedded in the search bar of Deck, a phone launcher on the user's Android device — " +
        "they reach you by typing into the launcher and tapping an \"Ask Claude\" result, so treat " +
        "the query as a quick question asked on the go. Answer it directly and concisely in plain " +
        "text (no markdown formatting). Respond with only your final answer; do not include any " +
        "reasoning, preamble, or follow-up questions."

    /**
     * Returns the answer + token usage on success, or a failure carrying a user-facing message.
     *
     * When [tools] + [executeTool] are provided, runs an agentic tool-use loop: if Claude responds
     * with `tool_use`, every tool is executed via [executeTool] and the results are fed back, up to
     * [MAX_TOOL_ITERATIONS] rounds, until Claude returns a final text answer. Token usage is summed
     * across all rounds.
     */
    suspend fun ask(
        context: Context,
        messages: List<ChatMessage>,
        thinking: Boolean = false,
        tools: List<JSONObject>? = null,
        executeTool: (suspend (name: String, input: JSONObject) -> String)? = null,
        terminalTools: Set<String> = emptySet(),
        onText: ((String) -> Unit)? = null
    ): Result<ClaudeResponse> = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("claude_api_key", "")?.trim().orEmpty()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("No Claude API key set. Add one in Settings → Search → Claude.")
            )
        }
        val model = prefs.getString("claude_model", DEFAULT_MODEL)
            ?.trim()?.ifBlank { DEFAULT_MODEL } ?: DEFAULT_MODEL
        // When delegating to "thinking", use the thinking model if set, else fall back to the main model.
        val effModel = if (thinking)
            prefs.getString("claude_thinking_model", "")?.trim()?.ifBlank { model } ?: model
        else model
        val customPrompt = prefs.getString("claude_system_prompt", "")?.trim().orEmpty()
        val baseSystem = customPrompt.ifBlank { defaultSystemPrompt(effModel) }
        val useLocation = prefs.getBoolean("claude_use_location", false)
        val locationNote = if (useLocation) locationContext(context) else null
        // Shared memory: refresh the local cache from PocketBase (one brain across devices). On any
        // failure (off-LAN / PocketBase down) the existing cache is kept, so it's never worse than local.
        if (ClaudeMemoryStore.isEnabled(context) && SharedMemoryClient.isConfigured(context)) {
            SharedMemoryClient.pull(context)?.let { ClaudeMemoryStore.replaceCache(context, it) }
        }
        val memoryBlock = ClaudeMemoryStore.systemBlock(context)
        val toolNote = if (!tools.isNullOrEmpty())
            "You have tools to look at what is on the user's phone via Deck — installed apps, Home " +
            "Assistant devices (with live state), contacts, settings and files. When the user refers " +
            "to something on their device (e.g. \"the bedroom lights\", \"my bank app\"), call the " +
            "search tool to find it (by exact identifier) before answering. You may call tools more " +
            "than once. Keep your final reply concise and in plain text. Device controls " +
            "(home_assistant_control) run IMMEDIATELY — phrase those as already done. App-leaving " +
            "actions (launch_app, call_number, open_setting) need the user to tap Confirm first — " +
            "phrase those as an offer (e.g. \"I can open Chase for you\"). Either way, give your " +
            "one-sentence reply in the SAME turn as the tool call."
        else null
        val system = listOfNotNull(baseSystem, toolNote, memoryBlock, locationNote).joinToString("\n\n")

        runCatching {
            val msgArray = JSONArray()
            messages.forEach { m ->
                msgArray.put(JSONObject().put("role", m.role).put("content", m.content))
            }
            var totalIn = 0
            var totalOut = 0
            var finalText = ""
            var iterations = 0
            // Stream only when thinking is OFF: a thinking turn carries `thinking`+`signature` blocks
            // that must be echoed verbatim on a tool continuation, which the delta-reconstruction
            // path can't reproduce. Thinking is the slow path we're not optimizing anyway.
            val streaming = onText != null && !thinking
            while (true) {
                iterations++
                val body = JSONObject().apply {
                    put("model", effModel)
                    put("max_tokens", if (thinking) THINKING_MAX_TOKENS else MAX_TOKENS)
                    put("system", system)
                    put("messages", msgArray)
                    if (thinking) put("thinking", JSONObject().put("type", "adaptive"))
                    if (streaming) put("stream", true)
                    val toolArr = JSONArray()
                    // Server-side web search so Claude can answer with live data (weather, nearby).
                    if (useLocation) toolArr.put(
                        JSONObject().put("type", "web_search_20260209").put("name", "web_search")
                    )
                    tools?.forEach { toolArr.put(it) }
                    if (toolArr.length() > 0) put("tools", toolArr)
                }.toString()

                val root = if (streaming) streamMessages(body, apiKey, onText!!)
                           else JSONObject(postMessages(body, apiKey))
                root.optJSONObject("usage")?.let {
                    totalIn += it.optInt("input_tokens"); totalOut += it.optInt("output_tokens")
                }

                val content = root.optJSONArray("content") ?: JSONArray()
                val stop = root.optString("stop_reason")
                if (tools != null && executeTool != null && stop == "tool_use" && iterations < MAX_TOOL_ITERATIONS) {
                    // Run every tool. Build ALL tool_results (matching tool_use_id) up front — returning
                    // a partial set 400s.
                    var terminal = false
                    val toolResults = JSONArray()
                    for (i in 0 until content.length()) {
                        val block = content.optJSONObject(i) ?: continue
                        if (block.optString("type") != "tool_use") continue
                        val toolName = block.optString("name")
                        val result = runCatching {
                            executeTool(toolName, block.optJSONObject("input") ?: JSONObject())
                        }.getOrElse { "Error running tool: ${it.message}" }
                        toolResults.put(
                            JSONObject()
                                .put("type", "tool_result")
                                .put("tool_use_id", block.optString("id"))
                                .put("content", result)
                        )
                        if (toolName in terminalTools) terminal = true
                    }
                    if (terminal) {
                        // A terminal (action) tool ends the turn: Claude already replied alongside the
                        // proposal, and nothing executes until the user confirms — so skip the extra
                        // continuation round-trip entirely.
                        finalText = parseAnswer(root).ifBlank { "Done." }
                        break
                    }
                    // Read tool(s): echo the assistant turn verbatim, return its tool_results, continue.
                    msgArray.put(JSONObject().put("role", "assistant").put("content", content))
                    msgArray.put(JSONObject().put("role", "user").put("content", toolResults))
                } else {
                    finalText = parseAnswer(root)
                    break
                }
            }
            if (finalText.isBlank()) throw RuntimeException("Claude returned an empty response.")
            ClaudeResponse(text = finalText, inputTokens = totalIn, outputTokens = totalOut)
        }
    }

    /** POST a Messages request body, returning the response text or throwing a user-facing error. */
    private fun postMessages(body: String, apiKey: String): String {
        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("content-type", "application/json")
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", ANTHROPIC_VERSION)
        }
        return try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw RuntimeException(parseError(text, code))
            text
        } finally {
            conn.disconnect()
        }
    }

    /** Accumulates one streamed content block (text or tool_use) across its deltas. */
    private class BlockBuilder(val type: String, val id: String = "", val name: String = "") {
        val text = StringBuilder()
        val json = StringBuilder()   // tool_use input arrives as input_json_delta fragments
    }

    /**
     * POST a streaming (`stream:true`) Messages request and parse the SSE event stream. [onText] is
     * called with the running text of THIS round as it arrives. Returns a reconstructed root JSON
     * ({content, stop_reason, usage}) shaped like the non-streaming response so the loop is uniform.
     */
    private fun streamMessages(body: String, apiKey: String, onText: (String) -> Unit): JSONObject {
        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("content-type", "application/json")
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", ANTHROPIC_VERSION)
            setRequestProperty("accept", "text/event-stream")
        }
        return try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            // A non-2xx with stream:true is a normal JSON error body, NOT an SSE stream.
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw RuntimeException(parseError(err, code))
            }

            val blocks = sortedMapOf<Int, BlockBuilder>()
            val roundText = StringBuilder()
            var stopReason = ""
            var inputTokens = 0
            var outputTokens = 0

            conn.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (!line.startsWith("data:")) continue
                    val data = line.substring(5).trim()
                    if (data.isEmpty()) continue
                    val ev = runCatching { JSONObject(data) }.getOrNull() ?: continue
                    // Switch on the payload's own `type`, not the `event:` line (more resilient).
                    when (ev.optString("type")) {
                        "message_start" ->
                            ev.optJSONObject("message")?.optJSONObject("usage")?.let {
                                inputTokens = it.optInt("input_tokens", inputTokens)
                            }
                        "content_block_start" -> {
                            val cb = ev.optJSONObject("content_block") ?: JSONObject()
                            blocks[ev.optInt("index")] =
                                BlockBuilder(cb.optString("type"), cb.optString("id"), cb.optString("name"))
                        }
                        "content_block_delta" -> {
                            val b = blocks[ev.optInt("index")] ?: continue
                            val d = ev.optJSONObject("delta") ?: continue
                            when (d.optString("type")) {
                                "text_delta" -> {
                                    val t = d.optString("text")
                                    b.text.append(t)
                                    roundText.append(t)
                                    onText(roundText.toString())
                                }
                                "input_json_delta" -> b.json.append(d.optString("partial_json"))
                            }
                        }
                        "message_delta" -> {
                            ev.optJSONObject("delta")?.optString("stop_reason")
                                ?.takeIf { it.isNotEmpty() }?.let { stopReason = it }
                            ev.optJSONObject("usage")?.let { outputTokens = it.optInt("output_tokens", outputTokens) }
                        }
                        "error" -> throw RuntimeException(
                            ev.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                                ?: "Claude streaming error"
                        )
                        // content_block_stop / message_stop / ping: nothing to do
                    }
                }
            }

            val content = JSONArray()
            blocks.values.forEach { b ->
                when (b.type) {
                    "text" -> content.put(JSONObject().put("type", "text").put("text", b.text.toString()))
                    "tool_use" -> {
                        val input = runCatching { JSONObject(b.json.toString().ifBlank { "{}" }) }
                            .getOrDefault(JSONObject())
                        content.put(JSONObject().put("type", "tool_use")
                            .put("id", b.id).put("name", b.name).put("input", input))
                    }
                }
            }
            JSONObject()
                .put("content", content)
                .put("stop_reason", stopReason)
                .put("usage", JSONObject().put("input_tokens", inputTokens).put("output_tokens", outputTokens))
        } finally {
            conn.disconnect()
        }
    }

    /** Concatenate every text block in `content[]` (with web search there can be several). */
    private fun parseAnswer(root: JSONObject): String {
        val content = root.optJSONArray("content") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until content.length()) {
            val block = content.optJSONObject(i) ?: continue
            if (block.optString("type") == "text") {
                val t = block.optString("text")
                if (t.isNotEmpty()) {
                    if (sb.isNotEmpty()) sb.append("\n")
                    sb.append(t)
                }
            }
        }
        return sb.toString().trim()
    }

    /**
     * Auto-memory: given the recent conversation and the existing memory, return ONLY genuinely-new
     * durable facts about the user (deduped/curated by a cheap model). Best-effort — returns empty on
     * any failure and never throws. The caller appends the result to [ClaudeMemoryStore].
     */
    suspend fun extractMemories(
        context: Context,
        messages: List<ChatMessage>,
        existing: List<String>
    ): List<String> = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("claude_api_key", "")?.trim().orEmpty()
        if (apiKey.isBlank()) return@withContext emptyList()
        runCatching {
            val convo = messages.takeLast(6).joinToString("\n") { "${it.role}: ${it.content}" }
            val sys =
                "You curate a long-term memory of durable facts about a user for a phone assistant. " +
                "From the recent conversation, extract ONLY genuinely new, durable facts worth remembering " +
                "in future chats — stable preferences, personal/biographical details, ongoing projects, " +
                "relationships, recurring needs. EXCLUDE anything already covered by the existing memory, " +
                "one-off or transient questions, general world facts, and the assistant's own statements. " +
                "Be conservative; prefer fewer. Return ONLY a JSON array of short third-person statements " +
                "(e.g. [\"Lives in Denver\",\"Drives a Polestar 2\"]). Return [] if there is nothing new."
            val userMsg = "Existing memory:\n" +
                (existing.joinToString("\n") { "- $it" }.ifBlank { "(none)" }) +
                "\n\nRecent conversation:\n$convo"
            val body = JSONObject()
                .put("model", "claude-haiku-4-5")
                .put("max_tokens", 400)
                .put("system", sys)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", userMsg)))
                .toString()
            val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true
                connectTimeout = 15_000; readTimeout = 30_000
                setRequestProperty("content-type", "application/json")
                setRequestProperty("x-api-key", apiKey)
                setRequestProperty("anthropic-version", ANTHROPIC_VERSION)
            }
            val responseText = try {
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) return@runCatching emptyList<String>()
                text
            } finally { conn.disconnect() }
            parseFactArray(parseAnswer(JSONObject(responseText)))
        }.getOrDefault(emptyList())
    }

    /** Pull a JSON array of strings out of the model's reply (tolerates code fences / stray text). */
    private fun parseFactArray(text: String): List<String> {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        return runCatching {
            val arr = JSONArray(text.substring(start, end + 1))
            (0 until arr.length()).map { arr.optString(it).trim() }.filter { it.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    /** Approximate location note for the system prompt, or null if unavailable / no permission. */
    private fun locationContext(context: Context): String? = runCatching {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        @Suppress("MissingPermission")
        val loc = runCatching { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
            ?: runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
            ?: return null
        val place = runCatching {
            @Suppress("DEPRECATION")
            Geocoder(context).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
        }.getOrNull()
        val where = place?.let {
            listOfNotNull(it.locality, it.adminArea, it.countryName).joinToString(", ").ifBlank { null }
        }
        val coords = "%.3f, %.3f".format(loc.latitude, loc.longitude)
        "The user's approximate current location is ${where ?: coords} (coordinates: $coords). " +
        "Use it for location-relevant questions (weather, nearby places, etc.). A web_search tool is " +
        "available — use it for anything needing current information such as weather or what's nearby."
    }.getOrNull()

    private fun parseError(json: String, code: Int): String {
        val msg = runCatching {
            JSONObject(json).optJSONObject("error")?.optString("message")
        }.getOrNull()
        return msg?.takeIf { it.isNotBlank() } ?: "Claude request failed (HTTP $code)"
    }
}
