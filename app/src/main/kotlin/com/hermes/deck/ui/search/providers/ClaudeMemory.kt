package com.hermes.deck.ui.search.providers

import android.content.Context

/**
 * Persistent, cross-session memory for the phone Claude (MEMORY.md-style). Durable facts about the
 * user are stored one-per-line in `deck_prefs` and injected into every chat's system prompt. The
 * user can view/edit/clear them in Settings; auto-extraction (separate) appends to them.
 *
 * Off by default. Writes are synchronized so async extraction can't corrupt the list, and the cap
 * never silently evicts older (often more stable) facts — when full it stops adding and signals so
 * the UI can prompt the user to prune.
 */
object ClaudeMemoryStore {
    private const val KEY = "claude_memory"
    private const val ENABLED_KEY = "claude_memory_enabled"
    const val MAX_ENTRIES = 60

    private fun prefs(context: Context) =
        context.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(ENABLED_KEY, false)
    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(ENABLED_KEY, value).apply()
    }

    fun list(context: Context): List<String> =
        (prefs(context).getString(KEY, "") ?: "").split("\n").map { it.trim() }.filter { it.isNotBlank() }

    fun raw(context: Context): String = prefs(context).getString(KEY, "") ?: ""

    /** Replace the whole list (used by the editable Settings field). */
    @Synchronized
    fun setRaw(context: Context, text: String) {
        prefs(context).edit().putString(KEY, text).apply()
    }

    /** Overwrite the cache with a list of facts — used by the PocketBase shared-memory sync. */
    @Synchronized
    fun replaceCache(context: Context, facts: List<String>) {
        prefs(context).edit().putString(KEY, facts.joinToString("\n")).apply()
    }

    fun clear(context: Context) { prefs(context).edit().remove(KEY).apply() }

    fun isFull(context: Context): Boolean = list(context).size >= MAX_ENTRIES

    /**
     * Append genuinely-new facts (case-insensitive dedup). When at [MAX_ENTRIES] it stops adding
     * rather than evicting older facts. Synchronized so concurrent extractions / edits don't clobber.
     * Returns true if the store is now full.
     */
    @Synchronized
    fun addAll(context: Context, newFacts: List<String>): Boolean {
        val current = list(context).toMutableList()
        val seen = current.mapTo(HashSet()) { it.lowercase() }
        var changed = false
        for (f in newFacts) {
            if (current.size >= MAX_ENTRIES) break
            val t = f.trim()
            if (t.isBlank() || t.lowercase() in seen) continue
            current.add(t); seen.add(t.lowercase()); changed = true
        }
        if (changed) prefs(context).edit().putString(KEY, current.joinToString("\n")).apply()
        return current.size >= MAX_ENTRIES
    }

    /** System-prompt block for injection, or null when disabled / empty. */
    fun systemBlock(context: Context): String? {
        if (!isEnabled(context)) return null
        val items = list(context)
        if (items.isEmpty()) return null
        return "Here is what you remember about this user from past conversations (use it to " +
            "personalize answers; don't recite it back unprompted):\n" +
            items.joinToString("\n") { "- $it" }
    }
}
