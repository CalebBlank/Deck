package com.hermes.deck.ui.search.providers

import android.util.Log
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Classifies a search query into content DOMAINS using on-device Gemini Nano (ML Kit GenAI Prompt
 * API, backed by Android's AICore). The search ranker uses the returned domains to surface the
 * results the user actually meant — e.g. "batman" → {comic, movie, tv} so the film/comic outrank a
 * same-named music track — knowledge the bundled MiniLM embedder can't supply (the titles are the
 * identical string).
 *
 * Fully best-effort: every SDK call is guarded, and ANY failure (model unavailable, not yet
 * downloaded, inference error, timeout) returns null so ranking falls back to the heuristic. Nothing
 * here blocks search — the caller runs this as a late re-rank, like the Plex result trickle.
 *
 * The model is large and AICore downloads it on demand, so this is gated by a Settings toggle in the
 * caller; the first use after enabling may return null while the download completes in the background.
 */
object GeminiNanoClassifier {
    private const val TAG = "GeminiNano"
    private const val INFERENCE_TIMEOUT_MS = 5_000L

    /** The closed vocabulary the model must choose from — kept in lockstep with `groupDomain()` in
     *  LauncherSearchBar so a returned label actually maps to a result group. */
    val DOMAINS = listOf(
        "movie", "tv", "music", "comic", "book", "person",
        "app", "contact", "recipe", "place", "game", "web"
    )

    @Volatile private var model: GenerativeModel? = null
    @Volatile private var unavailable = false      // a definitive UNAVAILABLE — stop trying this session
    @Volatile private var downloadKicked = false
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Small LRU cache so re-typing / re-ranking the same query doesn't re-run inference.
    private val cache = object : LinkedHashMap<String, Set<String>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Set<String>>?) = size > 64
    }

    private fun client(): GenerativeModel? {
        if (unavailable) return null
        model?.let { return it }
        return runCatching { Generation.getClient() }.getOrNull()?.also { model = it }
    }

    /** True only when the model is present and ready. If it's merely downloadable, kicks a one-time
     *  background download and returns false — search keeps using the heuristic until it's ready. */
    private suspend fun ready(): Boolean {
        val m = client() ?: run { Log.i(TAG, "ready: getClient() returned null"); return false }
        // A thrown checkStatus (e.g. AICore 606 FEATURE_NOT_FOUND — the Prompt feature isn't provisioned
        // on this device/build) won't recover this session, so latch it off to avoid hammering AICore on
        // every query.
        val status = runCatching { m.checkStatus() }
            .onFailure { Log.w(TAG, "checkStatus threw: ${it.message}"); unavailable = true }
            .getOrNull() ?: return false
        Log.i(TAG, "checkStatus=$status (AVAILABLE=${FeatureStatus.AVAILABLE} DOWNLOADABLE=${FeatureStatus.DOWNLOADABLE} DOWNLOADING=${FeatureStatus.DOWNLOADING} UNAVAILABLE=${FeatureStatus.UNAVAILABLE})")
        return when (status) {
            FeatureStatus.AVAILABLE   -> true
            FeatureStatus.DOWNLOADABLE -> { kickDownload(m); false }
            FeatureStatus.DOWNLOADING -> false
            else                      -> { unavailable = true; false }   // UNAVAILABLE
        }
    }

    private fun kickDownload(m: GenerativeModel) {
        if (downloadKicked) return
        downloadKicked = true
        scope.launch { runCatching { m.download().collect { } } }   // AICore manages it; result ignored
    }

    /**
     * Classify [query] into a subset of [DOMAINS]. Returns null when the model is unavailable / not
     * yet downloaded / errored / timed out (→ heuristic fallback). Cached per query.
     */
    suspend fun classify(query: String): Set<String>? {
        val q = query.trim().lowercase()
        if (q.length < 2) return null
        synchronized(cache) { cache[q] }?.let { return it }
        if (!ready()) return null
        val m = model ?: return null
        val raw = withTimeoutOrNull(INFERENCE_TIMEOUT_MS) {
            runCatching { m.generateContent(buildPrompt(q)).candidates.firstOrNull()?.text }
                .onFailure { Log.w(TAG, "inference failed: ${it.message}") }
                .getOrNull()
        } ?: return null
        val domains = parse(raw)
        Log.i(TAG, "classify '$q' -> raw='${raw.take(100)}' domains=$domains")
        if (domains.isNotEmpty()) synchronized(cache) { cache[q] = domains }
        return domains.ifEmpty { null }
    }

    private fun buildPrompt(q: String): String =
        "Classify what a phone search query is about. Choose only from this exact list of categories: " +
        DOMAINS.joinToString(", ") + ". " +
        "Reply with ONLY the matching categories as a comma-separated list, most relevant first, no other words. " +
        "Pick every category the query could plausibly mean (e.g. a superhero name is comic, movie, tv). " +
        "If none fit, reply \"none\".\n" +
        "Query: \"" + q + "\"\nCategories:"

    /** Keep only tokens that are real domains; tolerant of extra prose / punctuation in the reply. */
    private fun parse(raw: String): Set<String> =
        raw.lowercase().split(Regex("[^a-z]+")).filter { it in DOMAINS }.toSet()
}
