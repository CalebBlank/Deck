package com.hermes.deck.ui.search

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ranks provider ids by how relevant each provider is to the query, so the UI can order the result
 * groups (most-relevant provider first). Decided from the QUERY alone — before any results — so a
 * slow-but-relevant provider slots into its position via the group enter animation, not a late jump.
 *
 * Two signals, combined so the strong/confident ones dominate and the user's provider order is the
 * default everything falls back to:
 *  - **lexical** — a query token hitting a provider's keyword set (an unambiguous, literal match);
 *  - **semantic** — cosine(query, provider descriptor) from an on-device sentence-transformer
 *    (all-MiniLM-L6-v2 via ONNX), gated by a threshold so only a confident match promotes. This is
 *    what catches "cake"/"tacos" → Recipes, where there's no literal keyword.
 */
object SearchRanker {
    private const val MODEL = "all-MiniLM-L6-v2.onnx"
    private const val VOCAB = "minilm_vocab.txt"
    private const val LEX_WEIGHT = 100f
    // MiniLM cosines are clean but low-magnitude: a confident match is ~0.1–0.27, non-matches sit at
    // ≤~0.06. Threshold just above that noise floor; weight high enough that a match overcomes the prior.
    private const val SEM_THRESHOLD = 0.08f
    private const val SEM_WEIGHT = 100f

    // Distinctive keywords per provider (narrow on purpose — a keyword win should be confident).
    private val keywords: Map<String, Set<String>> = mapOf(
        "tandoor" to setOf("recipe", "recipes", "cook", "cooking", "bake", "baking", "ingredient",
            "ingredients", "meal", "meals", "dinner", "dish", "cuisine"),
        "plex" to setOf("movie", "movies", "film", "films", "episode", "episodes", "season",
            "seasons", "sitcom", "documentary", "cinema"),
        "home_assistant" to setOf("light", "lights", "lamp", "lamps", "thermostat", "brightness",
            "dim", "unlock", "doorbell", "scene", "heater", "fan", "blinds", "vacuum"),
        "symfonium" to setOf("song", "songs", "music", "track", "tracks", "album", "albums",
            "artist", "artists", "playlist", "listen", "play"),
        "transistor" to setOf("radio", "station", "stations", "fm", "am", "npr", "bbc", "broadcast"),
        "files" to setOf("pdf", "document", "documents", "download", "downloads", "folder"),
        "settings" to setOf("wifi", "bluetooth", "airplane", "hotspot"),
        "system_settings" to setOf("wifi", "bluetooth", "airplane", "hotspot"),
        "places" to setOf("near", "nearby", "restaurant", "restaurants", "coffee", "cafe", "food",
            "gas", "store", "shop", "shops", "bar", "hotel", "pharmacy", "directions", "eat"),
        "wikipedia" to setOf("who", "what", "wiki", "history", "define", "definition", "encyclopedia",
            "biography", "invented", "discovered"),
        "youtube" to setOf("youtube", "video", "videos", "watch", "tutorial", "trailer", "clip",
            "review", "unboxing", "gameplay"),
    )

    // Short natural-language descriptor per provider for the semantic signal. Providers without one
    // (claude/ai "ask" cards, contacts, browser…) get no semantic boost and keep their prior order.
    private val descriptors: Map<String, String> = mapOf(
        "plex" to "movies films tv shows series episodes to watch",
        "tandoor" to "recipes cooking food meals dinner ingredients dishes to make and eat",
        "home_assistant" to "smart home lights switches thermostat temperature locks devices to control",
        "symfonium" to "music songs tracks albums artists playlists to listen to and play",
        "transistor" to "internet radio stations live fm broadcasts npr bbc to listen to",
        "files" to "files documents photos pdfs downloads",
        "settings" to "phone settings wifi bluetooth display brightness volume",
        "system_settings" to "android system settings wifi bluetooth airplane battery",
        "places" to "local businesses nearby restaurants cafes coffee shops stores gas stations bars hotels to visit",
        "wikipedia" to "encyclopedia facts information people history science geography definitions biography",
        "youtube" to "youtube videos to watch tutorials reviews trailers clips gameplay how to",
    )

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    @Volatile private var initStarted = false
    @Volatile private var embedder: MiniLmEmbedder? = null
    private val descriptorEmb = HashMap<String, FloatArray>()

    /** Kick off the embedder load + descriptor embeddings once (non-blocking). */
    private fun preload(context: Context) {
        if (initStarted) return
        initStarted = true
        scope.launch {
            runCatching {
                val e = MiniLmEmbedder.create(context.applicationContext, MODEL, VOCAB)
                descriptors.forEach { (id, desc) -> e.embed(desc)?.let { descriptorEmb[id] = it } }
                embedder = e
            }.onFailure { Log.w("SearchRanker", "embedder init failed (ranking falls back to lexical): ${it.message}") }
        }
    }

    private fun dot(a: FloatArray, b: FloatArray): Float {
        var s = 0f
        val n = minOf(a.size, b.size)
        for (i in 0 until n) s += a[i] * b[i]
        return s
    }

    /**
     * Reorder [providerIdsInOrder] (already the user's default order) by query relevance. Non-blocking
     * w.r.t. the model: until it's loaded, ranks lexical-only. Suspends only for the (millisecond)
     * query embedding, off the main thread.
     */
    suspend fun rank(context: Context, query: String, providerIdsInOrder: List<String>): List<String> {
        preload(context)
        val tokens = query.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 2 }.toSet()
        if (tokens.isEmpty()) return providerIdsInOrder

        val lex = providerIdsInOrder.associateWith { id -> keywords[id]?.count { it in tokens } ?: 0 }

        val e = embedder
        val sem: Map<String, Float> = if (e != null) {
            withContext(Dispatchers.Default) {
                e.embed(query)?.let { q ->
                    providerIdsInOrder.associateWith { id ->
                        descriptorEmb[id]?.let { dot(q, it) } ?: 0f
                    }
                } ?: emptyMap()
            }
        } else emptyMap()

        fun score(id: String, index: Int): Float {
            val lexPart = (lex[id] ?: 0) * LEX_WEIGHT
            val s = sem[id] ?: 0f
            val semPart = if (s >= SEM_THRESHOLD) s * SEM_WEIGHT else 0f
            return lexPart + semPart - index   // prior order is the gentle tiebreaker
        }
        return providerIdsInOrder.withIndex()
            .sortedWith(compareByDescending { (i, id) -> score(id, i) })
            .map { it.value }
    }
}
