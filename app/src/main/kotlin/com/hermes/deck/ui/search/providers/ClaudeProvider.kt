package com.hermes.deck.ui.search.providers

import android.content.Context
import com.hermes.deck.ui.search.SearchResult

/**
 * "Ask Claude" search provider. Emits a single tap-to-ask result for essentially
 * any query WITHOUT making a network call — the Anthropic API request fires only
 * when the user taps the card (see ClaudeResultCard / AnthropicClient).
 *
 * Gated on a stored API key: with no key configured there's nothing the card can
 * do, so the provider stays silent until one is set in Settings → Search → Claude.
 */
class ClaudeProvider(private val context: Context) : SearchProvider {
    override val id = "claude"

    override suspend fun query(q: String): List<SearchResult> {
        val trimmed = q.trim()
        if (trimmed.length < 2) return emptyList()
        val prefs = context.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE)
        val key = prefs.getString("claude_api_key", "")?.trim().orEmpty()
        if (key.isBlank()) return emptyList()
        // Only offer "Ask Claude" for question-shaped queries — a general search shouldn't surface it.
        if (!isQuestion(trimmed)) return emptyList()
        return listOf(SearchResult.ClaudeResult(trimmed))
    }

    private companion object {
        private val QUESTION_STARTERS = listOf(
            "who", "what", "when", "where", "why", "how", "is", "are", "was", "were", "can", "could",
            "should", "would", "do", "does", "did", "will", "which", "whom", "whose",
            "explain", "summarize", "write", "tell me", "give me", "help me")

        fun isQuestion(q: String): Boolean {
            val s = q.trim().lowercase()
            return s.endsWith("?") || QUESTION_STARTERS.any { s == it || s.startsWith("$it ") }
        }
    }
}
