package com.hermes.deck.ui.search.providers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One Plex search match. [thumb] is a server-relative path (needs base URL + token to load). */
data class PlexItem(
    val ratingKey: String,
    val title: String,
    val type: String,        // movie / show / season / episode / artist / album / track / …
    val year: Int?,
    val thumb: String?,
    val subtitle: String,
    val library: String?     // librarySectionTitle, e.g. "Movies" / "TV Shows"
)

/** A Plex library section (Movies / TV Shows / Music / Photos / …). */
data class PlexLibrary(val id: String, val title: String, val type: String)

/** A person (actor or director) surfaced by `/hubs/search`. [thumb] is an ABSOLUTE
 *  metadata-static.plex.tv URL (fetch direct, like cast). [filmographyKeys] are the section
 *  queries (`/library/sections/13/all?actor=<id>`) that list their titles — one per library the
 *  person appears in; query+merge them for the full filmography. */
data class PlexPerson(
    val id: String,
    val name: String,
    val role: String,            // "actor" | "director"
    val thumb: String?,
    val filmographyKeys: List<String>
)

/** A search response: media items plus any people (cast/crew) hubs matched. */
data class PlexSearch(val items: List<PlexItem>, val people: List<PlexPerson>)

/**
 * Minimal raw-HTTP client for a Plex Media Server (no SDK). Reads `plex_base_url` + `plex_token`
 * from `deck_prefs`. Auth is the X-Plex-Token; JSON is requested via the Accept header (Plex
 * defaults to XML). Mirrors [HomeAssistantClient].
 */
object PlexClient {
    private const val TIMEOUT_MS = 8_000
    // Auto-discovery picks a reachable URL (resolveBase), so the search connect is normally fast
    // (~130 ms). 8 s only bounds how long a STALE cached URL (e.g. the local IP after leaving home)
    // blocks before search() re-races and retries on the other URL.
    private const val CONNECT_TIMEOUT_MS = 8_000
    // Short timeout for the reachability race — a dead/unreachable URL must drop out quickly so the
    // good one wins without a long wait.
    private const val PROBE_TIMEOUT_MS = 4_000
    // Library search on a big (remote) library can take a while; allow a generous read timeout so the
    // first search completes and trickles in, instead of timing out (which left it needing a retype).
    private const val SEARCH_READ_TIMEOUT_MS = 25_000
    private val MEDIA_TYPES = setOf(
        "movie", "show", "season", "episode", "artist", "album", "track", "clip", "collection"
    )

    @Volatile private var machineId: String? = null
    // The configured URL last found reachable (the auto-discovery winner). Used by every request and
    // the synchronous imageUrl(); cleared on a genuine connect failure so the next search re-races
    // (handles moving between home/away, where which URL works flips).
    @Volatile private var activeBase: String? = null
    // Small poster cache so images survive list recomposition/scroll and aren't refetched.
    private val imageCache = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()

    private fun prefs(c: Context) = c.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE)
    /** All configured server URLs (primary + alternate), trimmed, de-duped, blanks dropped. */
    private fun configuredBases(c: Context): List<String> {
        val p = prefs(c)
        return listOf(p.getString("plex_base_url", ""), p.getString("plex_base_url_alt", ""))
            .mapNotNull { it?.trim()?.trimEnd('/')?.ifBlank { null } }
            .distinct()
    }
    /** Resolved winner if known, else the first configured URL — for synchronous callers (imageUrl,
     *  which runs right after a search has already resolved [activeBase]). */
    private fun baseUrl(c: Context): String? = activeBase ?: configuredBases(c).firstOrNull()
    private fun token(c: Context): String? =
        prefs(c).getString("plex_token", "")?.trim()?.ifBlank { null }

    fun isConfigured(context: Context): Boolean = configuredBases(context).isNotEmpty() && token(context) != null

    /** Library section titles the user has switched OFF — their results are dropped from search. */
    private fun disabledLibraries(c: Context): Set<String> =
        prefs(c).getStringSet("plex_disabled_libraries", emptySet()) ?: emptySet()

    /** List the server's library sections (for the per-library enable/disable settings). */
    suspend fun libraries(context: Context): Result<List<PlexLibrary>> {
        val tok = token(context) ?: return Result.failure(IllegalStateException("No Plex token set"))
        return withContext(Dispatchers.IO) {
            runCatching {
                val base = resolveBase(context) ?: error("No reachable Plex server")
                val dirs = JSONObject(httpGet("$base/library/sections", tok))
                    .optJSONObject("MediaContainer")?.optJSONArray("Directory")
                val out = ArrayList<PlexLibrary>()
                if (dirs != null) for (i in 0 until dirs.length()) {
                    val d = dirs.optJSONObject(i) ?: continue
                    val title = d.optString("title").takeIf { it.isNotBlank() } ?: continue
                    out.add(PlexLibrary(d.optString("key"), title, d.optString("type")))
                }
                out
            }
        }
    }

    /** Pick a reachable base URL by RACING `GET /` (token-authed — the exact connect+auth path a
     *  search uses) against every configured URL; the first to answer 200 wins and is cached in
     *  [activeBase]. Returns null only if none respond. Losing probes are cancelled (socket
     *  disconnected) so they don't linger as hairpin connections. */
    private suspend fun resolveBase(context: Context): String? {
        activeBase?.let { return it }
        val tok = token(context) ?: return null
        val bases = configuredBases(context)
        if (bases.isEmpty()) return null
        if (bases.size == 1) return bases[0].also { activeBase = it }
        val winner = coroutineScope {
            val deferred = CompletableDeferred<String?>()
            val probes = bases.map { base -> launch { if (probe(base, tok)) deferred.complete(base) } }
            launch { probes.joinAll(); deferred.complete(null) }   // all failed → resolve to null
            deferred.await().also { probes.forEach { it.cancel() } }
        }
        return winner?.also { activeBase = it }
    }

    /** Reachability probe: GET / on [base] with the token, short timeout, cancellable (aborts its
     *  socket if it loses the race so it doesn't keep a connection open). */
    private suspend fun probe(base: String, tok: String): Boolean = withContext(Dispatchers.IO) {
        val conn = (URL("$base/").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("X-Plex-Token", tok)
            setRequestProperty("Accept", "application/json")
            connectTimeout = PROBE_TIMEOUT_MS
            readTimeout = PROBE_TIMEOUT_MS
        }
        val watcher = launch { try { awaitCancellation() } finally { runCatching { conn.disconnect() } } }
        runCatching { conn.responseCode in 200..299 }
            .getOrDefault(false)
            .also { watcher.cancel(); runCatching { conn.disconnect() } }
    }

    /** Search the library via the unified `/hubs/search` endpoint. Auto-discovers a reachable server
     *  URL; on a genuine (still-active) connect failure it drops the stale winner, re-races the URLs
     *  and retries ONCE — so moving between home/away costs a slower search, not an empty one. */
    suspend fun search(context: Context, query: String): Result<PlexSearch> {
        if (token(context) == null) return Result.failure(IllegalStateException("No Plex token set"))
        if (configuredBases(context).isEmpty()) return Result.failure(IllegalStateException("No Plex server URL set"))
        val first = searchOnce(context, query)
        // Retry only on a genuine failure that's STILL the live query. A superseded (cancelled) search
        // surfaces as SocketException("Socket closed") — NOT CancellationException — so discriminate on
        // isActive, not the exception type, or every keystroke would clear the cache and re-race.
        if (first.isSuccess || !currentCoroutineContext().isActive) return first
        activeBase = null   // drop the stale winner so searchOnce re-races onto the other URL
        return searchOnce(context, query)
    }

    private suspend fun searchOnce(context: Context, query: String): Result<PlexSearch> {
        val base = resolveBase(context) ?: return Result.failure(IllegalStateException("No reachable Plex server URL"))
        val tok  = token(context) ?: return Result.failure(IllegalStateException("No Plex token set"))
        return withContext(Dispatchers.IO) {
            runCatching {
                val q = URLEncoder.encode(query, "UTF-8")
                // cancellableGet (not httpGet): a superseded search is cancelled the instant the user
                // types the next char; this aborts its socket so it stops competing for the connection.
                val body = cancellableGet("$base/hubs/search?query=$q&limit=15", tok, SEARCH_READ_TIMEOUT_MS)
                val container = JSONObject(body).optJSONObject("MediaContainer")
                    ?: return@runCatching PlexSearch(emptyList(), emptyList())
                val hubs = container.optJSONArray("Hub")
                    ?: return@runCatching PlexSearch(emptyList(), emptyList())
                val out = ArrayList<PlexItem>()
                // Cast/crew hubs ("Actors"/"Directors") carry people as Directory tags, not Metadata.
                // Merge by name (a person appears once per library, and as both actor+director) so a
                // search yields one card per person with all their filmography keys combined.
                val people = LinkedHashMap<String, PlexPerson>()
                val disabled = disabledLibraries(context)
                for (i in 0 until hubs.length()) {
                    val hub = hubs.optJSONObject(i) ?: continue
                    val hubType = hub.optString("type")
                    if (hubType == "actor" || hubType == "director") {
                        val dir = hub.optJSONArray("Directory") ?: continue
                        for (j in 0 until dir.length()) {
                            val p = dir.optJSONObject(j) ?: continue
                            val id = p.optString("id").takeIf { it.isNotBlank() } ?: continue
                            val name = p.optString("tag").takeIf { it.isNotBlank() } ?: continue
                            val key = p.optString("key").takeIf { it.isNotBlank() }
                            val thumb = p.optString("thumb").takeIf { it.isNotBlank() }
                            val nameKey = name.lowercase()
                            val existing = people[nameKey]
                            people[nameKey] = if (existing == null) {
                                PlexPerson(id, name, hubType, thumb, listOfNotNull(key))
                            } else {
                                existing.copy(
                                    thumb = existing.thumb ?: thumb,
                                    filmographyKeys = (existing.filmographyKeys + listOfNotNull(key)).distinct()
                                )
                            }
                        }
                        continue
                    }
                    val meta = hub.optJSONArray("Metadata") ?: continue
                    for (j in 0 until meta.length()) {
                        val m = meta.optJSONObject(j) ?: continue
                        val type = m.optString("type")
                        if (type !in MEDIA_TYPES) continue
                        val rk = m.optString("ratingKey")
                        val title = m.optString("title")
                        if (rk.isBlank() || title.isBlank()) continue
                        val year = if (m.has("year") && !m.isNull("year")) m.optInt("year") else null
                        val thumb = m.optString("thumb").takeIf { it.isNotBlank() }
                            ?: m.optString("grandparentThumb").takeIf { it.isNotBlank() }
                        val library = m.optString("librarySectionTitle").takeIf { it.isNotBlank() }
                        if (library != null && library in disabled) continue   // user switched this library off
                        out.add(PlexItem(rk, title, type, year, thumb, subtitleFor(m, type, year), library))
                    }
                }
                PlexSearch(out, people.values.toList())
            }
        }
    }

    /** Fetch a person's filmography by querying each of their section keys
     *  (`/library/sections/13/all?actor=<id>`) and merging by ratingKey (a title can appear in
     *  several keys). Newest first. Empty on failure — the card just hides the row. */
    suspend fun filmography(context: Context, keys: List<String>): List<PlexItem> {
        val base = resolveBase(context) ?: return emptyList()
        val tok  = token(context) ?: return emptyList()
        val disabled = disabledLibraries(context)
        return withContext(Dispatchers.IO) {
            val merged = LinkedHashMap<String, PlexItem>()
            for (key in keys.take(4)) {
                runCatching {
                    val sep = if (key.contains('?')) "&" else "?"
                    val body = httpGet("$base$key${sep}sort=year:desc", tok)
                    val meta = JSONObject(body).optJSONObject("MediaContainer")?.optJSONArray("Metadata")
                        ?: return@runCatching
                    for (j in 0 until meta.length()) {
                        val m = meta.optJSONObject(j) ?: continue
                        val type = m.optString("type")
                        if (type !in MEDIA_TYPES) continue
                        val rk = m.optString("ratingKey")
                        val title = m.optString("title")
                        if (rk.isBlank() || title.isBlank() || merged.containsKey(rk)) continue
                        val year = if (m.has("year") && !m.isNull("year")) m.optInt("year") else null
                        val thumb = m.optString("thumb").takeIf { it.isNotBlank() }
                            ?: m.optString("grandparentThumb").takeIf { it.isNotBlank() }
                        val library = m.optString("librarySectionTitle").takeIf { it.isNotBlank() }
                        if (library != null && library in disabled) continue
                        merged[rk] = PlexItem(rk, title, type, year, thumb, subtitleFor(m, type, year), library)
                    }
                }
            }
            merged.values.toList()
        }
    }

    private fun subtitleFor(m: JSONObject, type: String, year: Int?): String = when (type) {
        "movie" -> listOfNotNull("Movie", year?.toString()).joinToString(" · ")
        "show"  -> listOfNotNull("TV Show", year?.toString()).joinToString(" · ")
        "season" -> m.optString("parentTitle").ifBlank { "Season" }
        "episode" -> {
            val show = m.optString("grandparentTitle").takeIf { it.isNotBlank() }
            val s = m.optInt("parentIndex", -1); val e = m.optInt("index", -1)
            val se = if (s > 0 && e > 0) "S${s}E$e" else null
            listOfNotNull(show, se).joinToString(" · ").ifBlank { "Episode" }
        }
        "artist" -> "Artist"
        "album"  -> listOfNotNull(m.optString("parentTitle").takeIf { it.isNotBlank() }, "Album").joinToString(" · ")
        "track"  -> listOfNotNull(m.optString("grandparentTitle").takeIf { it.isNotBlank() }, "Track").joinToString(" · ")
        "collection" -> "Collection"
        else -> type.replaceFirstChar { it.uppercase() }
    }

    /** Connection test: RACE all configured URLs, then read the server's friendly name from the
     *  winner and report which URL connected. Forces a fresh race (ignores any cached winner). */
    suspend fun ping(context: Context): Result<String> {
        val tok = token(context) ?: return Result.failure(IllegalStateException("No token set"))
        if (configuredBases(context).isEmpty()) return Result.failure(IllegalStateException("No URL set"))
        activeBase = null
        val winner = resolveBase(context)
            ?: return Result.failure(IllegalStateException("no configured URL is reachable"))
        return withContext(Dispatchers.IO) {
            runCatching {
                val c = JSONObject(httpGet("$winner/", tok)).optJSONObject("MediaContainer")
                val name = c?.optString("friendlyName")?.ifBlank { "Connected." } ?: "Connected."
                "$name — using $winner"
            }
        }
    }

    /** Cached machineIdentifier (null until [machineIdentifier] has run) — for synchronous deep-links. */
    fun cachedMachineId(): String? = machineId

    /** Server machineIdentifier (for deep links), cached. */
    suspend fun machineIdentifier(context: Context): String? {
        machineId?.let { return it }
        val base = resolveBase(context) ?: return null
        val tok  = token(context)   ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                JSONObject(httpGet("$base/", tok)).optJSONObject("MediaContainer")
                    ?.optString("machineIdentifier")?.takeIf { it.isNotBlank() }
            }.getOrNull().also { machineId = it }
        }
    }

    data class PlexCast(val name: String, val role: String?, val thumb: String?)
    data class PlexMeta(val summary: String?, val cast: List<PlexCast>)

    /** Full metadata for a ratingKey — plot summary + cast (Role tags). For the rich result card. */
    suspend fun metadata(context: Context, ratingKey: String): PlexMeta? {
        val base = resolveBase(context) ?: return null
        val tok  = token(context) ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val body = httpGet("$base/library/metadata/$ratingKey", tok)
                val m = JSONObject(body).optJSONObject("MediaContainer")
                    ?.optJSONArray("Metadata")?.optJSONObject(0) ?: return@runCatching null
                val summary = m.optString("summary").takeIf { it.isNotBlank() }
                val roles = m.optJSONArray("Role")
                val cast = if (roles == null) emptyList() else (0 until roles.length()).mapNotNull { i ->
                    val r = roles.optJSONObject(i) ?: return@mapNotNull null
                    val name = r.optString("tag").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    PlexCast(name, r.optString("role").takeIf { it.isNotBlank() }, r.optString("thumb").takeIf { it.isNotBlank() })
                }.take(20)
                PlexMeta(summary, cast)
            }.getOrNull()
        }
    }

    /** Token-authed thumbnail URL for a server-relative thumb path, routed through Plex's photo
     *  TRANSCODER so the server downscales the poster to thumbnail size. The card is 38×56dp, so
     *  ~150×225 is plenty. Fetching the raw poster (often 2000×3000, ~1 MB each) over a remote
     *  cleartext link was slow and memory-heavy, and only one finished before the rest were
     *  cancelled by recomposition — that was the "only one poster loads" bug. */
    fun imageUrl(context: Context, thumb: String?): String? {
        val t = thumb?.takeIf { it.isNotBlank() } ?: return null
        val base = baseUrl(context) ?: return null
        val tok  = token(context)   ?: return null
        val path = if (t.startsWith("/")) t else "/$t"
        val inner = URLEncoder.encode("$path?X-Plex-Token=$tok", "UTF-8")
        return "$base/photo/:/transcode?width=150&height=225&minSize=1&upscale=0&url=$inner&X-Plex-Token=$tok"
    }

    suspend fun fetchImage(context: Context, url: String): Bitmap? {
        imageCache[url]?.let { return it }
        return withContext(Dispatchers.IO) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"; connectTimeout = TIMEOUT_MS; readTimeout = TIMEOUT_MS
            }
            // Abort the socket if this fetch is cancelled (card disposed by recomposition), so it
            // stops competing for the connection — same starvation issue as the search.
            val watcher = launch { try { awaitCancellation() } finally { runCatching { conn.disconnect() } } }
            runCatching {
                val code = conn.responseCode
                val bmp = if (code in 200..299) {
                    val bytes = conn.inputStream.use { it.readBytes() }
                    decodeSampled(bytes, 300)
                } else null
                bmp?.also { if (imageCache.size < 80) imageCache[url] = it }
            }.getOrNull().also {
                watcher.cancel()
                runCatching { conn.disconnect() }
            }
        }
    }

    /** Decode [bytes] downsampled so its longest side is ≤ [maxPx]. Defensive: if a server ignores
     *  the transcode request and returns a full-res poster, a direct 2000×3000 decode is ~24 MB —
     *  inSampleSize keeps memory (and decode time) bounded. */
    private fun decodeSampled(bytes: ByteArray, maxPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxPx || bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun httpGet(urlStr: String, token: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("X-Plex-Token", token)
            setRequestProperty("Accept", "application/json")
            connectTimeout = TIMEOUT_MS
            readTimeout = SEARCH_READ_TIMEOUT_MS
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        if (code !in 200..299) error(if (code == 401) "unauthorized — check your token" else "HTTP $code")
        return body
    }

    /** GET that ABORTS its socket (disconnect → the blocking read throws) the moment the calling
     *  coroutine is cancelled — so a superseded search stops immediately instead of running to
     *  completion in the background and starving the next query's connection. A watcher coroutine
     *  (separate IO thread) waits for cancellation and disconnects, which unblocks the read here. */
    private suspend fun cancellableGet(urlStr: String, token: String, readTimeoutMs: Int): String =
        withContext(Dispatchers.IO) {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("X-Plex-Token", token)
                setRequestProperty("Accept", "application/json")
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = readTimeoutMs
            }
            val watcher = launch { try { awaitCancellation() } finally { runCatching { conn.disconnect() } } }
            try {
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
                if (code !in 200..299) error(if (code == 401) "unauthorized — check your token" else "HTTP $code")
                body
            } finally {
                watcher.cancel()
                runCatching { conn.disconnect() }
            }
        }
}
