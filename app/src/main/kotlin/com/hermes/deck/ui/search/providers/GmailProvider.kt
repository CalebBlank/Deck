package com.hermes.deck.ui.search.providers

import android.content.Context
import com.hermes.deck.ui.search.SearchResult

/**
 * Gmail search provider. Tap-to-search (IMAP is too slow per keystroke): emits one card for any query
 * with no network; the inbox search runs only when the card is tapped. Silent until an address + app
 * password are set in Settings → Search → Gmail.
 */
class GmailProvider(private val context: Context) : SearchProvider {
    override val id = "gmail"

    override suspend fun query(q: String): List<SearchResult> {
        val trimmed = q.trim()
        if (trimmed.length < 3 || !GmailClient.isConfigured(context)) return emptyList()
        // Actually search the inbox (IMAP, ~2-3s) and only emit a card when there are matching messages —
        // no placeholder "search Gmail" card for queries with no results.
        val mails = GmailClient.search(context, trimmed).getOrNull().orEmpty()
        if (mails.isEmpty()) return emptyList()
        // Honor the per-provider "Max results" slider (provider_limit_gmail; 0 = unlimited). The generic
        // SearchViewModel cap only limits the number of result CARDS (Gmail emits a single card), so the
        // message list inside the card has to be capped here.
        val limit = context.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE).getInt("provider_limit_gmail", 0)
        val shown = if (limit > 0) mails.take(limit) else mails
        return listOf(SearchResult.GmailResult(trimmed, shown))
    }
}
