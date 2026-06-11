package com.hermes.deck.ui.search.providers

import android.content.Context
import com.hermes.deck.ui.search.SearchResult

/**
 * Tap-to-ask the on-device model (the same Qwen used for ranking). Fully offline, private, free — no
 * network, no key, no quota. Emitted only for QUESTION-shaped queries (so it isn't bottom-of-list
 * clutter on every search) and only when on-device AI is enabled (shares the model with ranking).
 * The model runs only when the card is tapped — no inference per keystroke.
 */
class OfflineAnswerProvider(private val context: Context) : SearchProvider {
    override val id = "offline_ai"

    private val questionStart = Regex(
        "^(what|whats|who|whos|how|why|when|where|which|whose|is|are|was|were|do|does|did|can|could|should|would|will)\\b",
        RegexOption.IGNORE_CASE
    )

    override suspend fun query(q: String): List<SearchResult> {
        val raw = q.trim()
        if (raw.length < 6) return emptyList()
        val prefs = context.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("search_nano_ranking", false)) return emptyList()
        val looksLikeQuestion = raw.endsWith("?") || questionStart.containsMatchIn(raw)
        if (!looksLikeQuestion) return emptyList()
        return listOf(SearchResult.OfflineAnswerResult(raw))
    }
}
