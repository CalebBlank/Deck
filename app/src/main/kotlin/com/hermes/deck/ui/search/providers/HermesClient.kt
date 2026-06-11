package com.hermes.deck.ui.search.providers

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Minimal raw-HTTP client for a self-hosted Hermes agent (Nous Research) via its
 * OpenAI-compatible API (`POST /v1/chat/completions`, streamed).
 *
 * Unlike [AnthropicClient] there are NO Deck-local tools and NO memory extraction: Hermes is
 * itself a persistent agent with server-side memory + tools, so Deck just streams a chat to it
 * and renders the reply. Reuses [ClaudeResponse] for the answer + token usage.
 *
 * The endpoint is the user's Hermes base URL (e.g. `https://192.168.0.31:8443`), typically with
 * a self-signed cert — so this client installs a trust-all TLS factory SCOPED to its own
 * connections only (it must never touch the app-wide default, which the Anthropic/HA/Plex
 * clients rely on for real cert validation).
 */
object HermesClient {
    const val DEFAULT_MODEL = "hermes-agent"

    /** True once the user has set both a base URL and a password in Settings → Search → Hermes. */
    fun isConfigured(context: Context): Boolean {
        val p = context.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE)
        return !p.getString("hermes_base_url", "").isNullOrBlank() &&
               !p.getString("hermes_password", "").isNullOrBlank()
    }

    /** Normalize the configured base into a chat endpoint: strip trailing '/' and a trailing '/v1'. */
    private fun chatEndpoint(base: String): String {
        var b = base.trim().trimEnd('/')
        if (b.endsWith("/v1")) b = b.dropLast(3)
        return "$b/v1/chat/completions"
    }

    /**
     * Streams a chat completion. [onText] receives the running answer text as SSE deltas arrive
     * (drives the live "streaming" bubble, same as the Claude path). Returns the final text plus
     * token usage on success, or a failure carrying a user-facing message.
     */
    suspend fun ask(
        context: Context,
        messages: List<ChatMessage>,
        onText: ((String) -> Unit)? = null
    ): Result<ClaudeResponse> = withContext(Dispatchers.IO) {
        val p = context.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE)
        val base = p.getString("hermes_base_url", "")?.trim().orEmpty()
        val password = p.getString("hermes_password", "")?.trim().orEmpty()
        val model = p.getString("hermes_model", DEFAULT_MODEL)?.trim()?.ifBlank { DEFAULT_MODEL } ?: DEFAULT_MODEL
        if (base.isBlank() || password.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Hermes isn't set up. Add its URL and password in Settings → Search → Hermes.")
            )
        }
        runCatching {
            val body = JSONObject()
                .put("model", model)
                .put("stream", true)
                .put("messages", JSONArray().apply {
                    messages.forEach { put(JSONObject().put("role", it.role).put("content", it.content)) }
                })
                .toString()
            streamChat(chatEndpoint(base), password, body, onText)
        }
    }

    /** POST a streaming chat request and accumulate the OpenAI SSE `delta.content` chunks. */
    private fun streamChat(
        endpoint: String, password: String, body: String, onText: ((String) -> Unit)?
    ): ClaudeResponse {
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            // Self-signed local cert: trust-all factory on THIS connection only (never the default).
            if (this is HttpsURLConnection) {
                sslSocketFactory = insecureFactory
                hostnameVerifier = HostnameVerifier { _, _ -> true }
            }
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            // Hermes is an agent doing 40k-token context (+ possible server-side tool work), so it
            // can be slow — a short read timeout would guillotine long answers mid-stream.
            readTimeout = 180_000
            setRequestProperty("content-type", "application/json")
            setRequestProperty("authorization", "Bearer $password")
            setRequestProperty("accept", "text/event-stream")
        }
        return try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw RuntimeException(parseError(err, code))
            }
            val text = StringBuilder()
            var inTok = 0
            var outTok = 0
            conn.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (!line.startsWith("data:")) continue
                    val data = line.substring(5).trim()
                    if (data.isEmpty() || data == "[DONE]") continue
                    val ev = runCatching { JSONObject(data) }.getOrNull() ?: continue
                    ev.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")
                        ?.optString("content")?.takeIf { it.isNotEmpty() }?.let {
                            text.append(it)
                            onText?.invoke(text.toString())
                        }
                    ev.optJSONObject("usage")?.let {
                        inTok = it.optInt("prompt_tokens", inTok)
                        outTok = it.optInt("completion_tokens", outTok)
                    }
                }
            }
            val finalText = text.toString().trim()
            if (finalText.isBlank()) throw RuntimeException("Hermes returned an empty response.")
            ClaudeResponse(finalText, inTok, outTok)
        } finally {
            conn.disconnect()
        }
    }

    private fun parseError(json: String, code: Int): String {
        val msg = runCatching { JSONObject(json).optJSONObject("error")?.optString("message") }.getOrNull()
        return when {
            !msg.isNullOrBlank() -> msg
            code == 401          -> "Hermes rejected the password (401). Check it in Settings → Search → Hermes."
            else                 -> "Hermes request failed (HTTP $code)"
        }
    }

    /** Trust-all socket factory, built once, used ONLY on this client's own connections. */
    private val insecureFactory: SSLSocketFactory by lazy {
        val tm = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(tm), SecureRandom()) }.socketFactory
    }
}
