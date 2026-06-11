package com.hermes.deck.ui.search.providers

import android.content.Context
import com.hermes.deck.ui.search.SearchResult
import kotlinx.coroutines.delay

/**
 * YouTube video search. Returns real video results (thumbnail + title + channel) via the YouTube Data
 * API v3; tapping opens the video in the YouTube app. Silent until an API key is set in
 * Settings → Search → YouTube.
 *
 * QUOTA-AWARE: each API search costs 100 of the 10,000 default daily units (~100/day), so this fires
 * only on a SETTLED query — a heavy, cancellable debounce means fast typing spends nothing, and
 * [YouTubeClient] caches per query so repeats/re-ranks are free.
 */
class YouTubeProvider(private val context: Context) : SearchProvider {
    override val id = "youtube"

    override suspend fun query(q: String): List<SearchResult> {
        val raw = q.trim()
        if (raw.length < 3 || !YouTubeClient.isConfigured(context)) return emptyList()
        // Spend a quota unit only once the query settles. Cancellable: a new keystroke drops this
        // before any request is made, so a fast type-through costs nothing.
        delay(700)
        return YouTubeClient.search(context, raw).getOrDefault(emptyList()).map {
            SearchResult.YouTubeResult(it.videoId, it.title, it.channel, it.thumbnailUrl)
        }
    }
}
