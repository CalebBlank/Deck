package com.hermes.deck.ui.search.providers

import android.content.Context
import com.hermes.deck.ui.search.SearchResult
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Plex search provider. Queries the user's Plex Media Server via [PlexClient] and returns media
 * cards (movies / shows / episodes / music). Silent until a server URL + token are set in
 * Settings → Search → Plex. Warms the machineIdentifier cache alongside the search so that opening
 * a result (a deep link) is synchronous — the result card's coroutine scope is cancelled the moment
 * the search box closes on tap, so the open must not depend on a network call.
 */
class PlexProvider(private val context: Context) : SearchProvider {
    override val id = "plex"

    override suspend fun query(q: String): List<SearchResult> {
        val raw = q.trim()
        // Skip 1–2 char queries: they match the whole library and the server search is slow.
        if (raw.length < 3 || !PlexClient.isConfigured(context)) return emptyList()
        // A trailing 4-digit year ("Superman 2025") — Plex's search ignores the year as a title token
        // and returns nothing, so search WITHOUT it and filter the results by that year instead.
        val ym = Regex("""^(.+?)\s+((?:19|20)\d{2})$""").find(raw)
        val searchQuery = ym?.groupValues?.get(1)?.trim()?.takeIf { it.length >= 2 } ?: raw
        val year = ym?.groupValues?.get(2)?.toIntOrNull()
        // Plex-only extra debounce. Each search opens a socket to the remote server; a fast
        // type-through used to open one per keystroke, and the burst of overlapping connections
        // stalled the live query's connect past the timeout ("Plex shows nothing"). This delay is
        // cancellable, so if the user types again it's dropped and no search hits the server —
        // only a settled query does. Fast local providers stay on the search bar's 200 ms debounce.
        kotlinx.coroutines.delay(450)
        val search = coroutineScope {
            launch { PlexClient.machineIdentifier(context) }   // warm the cache for deep-link opens
            PlexClient.search(context, searchQuery).getOrDefault(PlexSearch(emptyList(), emptyList()))
        }
        val results = search.items.map {
            SearchResult.PlexResult(
                ratingKey = it.ratingKey,
                title     = it.title,
                subtitle  = it.subtitle,
                type      = it.type,
                thumbUrl  = PlexClient.imageUrl(context, it.thumb),
                library   = it.library
            )
        }
        // Filter by the requested year (movies carry the year in their subtitle, e.g. "Movie · 2025").
        val byYear = if (year == null) results else results.filter { it.subtitle.contains(year.toString()) }
        // Episode noise filter. (1) If a SHOW itself is in the results, drop episodes OF that show
        // (don't list a show + its episodes). (2) When the query NAMES a show/movie (a show/movie title
        // contains it), also drop episodes of UNRELATED shows whose only tie is a title collision — e.g.
        // searching "Daredevil" returns a "Daredevil" episode of The Red Green Show; the user wants the
        // Daredevil series, not that. An episode's [subtitle] is "<parent show> · SxEy", so a parent
        // that doesn't contain the query marks it unrelated. (When the query matches no show/movie — i.e.
        // the user searched an episode title directly — episodes are kept.)
        val showTitles = byYear.filter { it.type == "show" }.map { it.title }
        val namesShowOrMovie = byYear.any {
            it.type in setOf("movie", "show") && it.title.contains(searchQuery, ignoreCase = true)
        }
        val deduped = byYear.filter { r ->
            if (r.type != "episode") return@filter true
            if (showTitles.any { r.subtitle.startsWith("$it ·") }) return@filter false
            if (namesShowOrMovie && !r.subtitle.contains(searchQuery, ignoreCase = true)) return@filter false
            true
        }
        // When libraries are split into their own cards, each gets its own result cap
        // (plex_limit_<library>, 0/absent = unlimited). Keyed on the same label groupResults uses.
        // (When not split, the single combined cap is applied by SearchViewModel instead.)
        val prefs = context.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE)
        val splitLibs = prefs.getBoolean("plex_split_libraries", false)
        val finalList = if (!splitLibs) deduped else {
            val counts = HashMap<String, Int>()
            deduped.filter { r ->
                val lib = r.library?.takeIf { it.isNotBlank() } ?: "Plex"
                val limit = prefs.getInt("plex_limit_$lib", 0)
                if (limit <= 0) return@filter true
                val n = (counts[lib] ?: 0) + 1
                counts[lib] = n
                n <= limit
            }
        }
        // Confidence is evaluated PER GROUP. With split libraries, Movies / TV Shows / … are separate
        // cards, so a lone result IN a library is confident even when another library also returned one
        // (e.g. 1 movie + 1 show → BOTH rich). Otherwise the whole list is one group.
        fun libOf(r: SearchResult.PlexResult) = r.library?.takeIf { it.isNotBlank() } ?: "Plex"
        // A group is "confident" only when it has a SINGLE clear match: one result, or exactly ONE
        // whose title equals the query. Then show just that one rich (no mix). Multiple exact titles
        // (e.g. two movies both called "Superman") = ambiguous → plain rows. Evaluated per library.
        fun pickRich(group: List<SearchResult.PlexResult>): List<SearchResult.PlexResult> {
            // An EPISODE whose title coincidentally equals the query (e.g. a "Daredevil" episode of an
            // unrelated show) must NOT count as the confident match — that used to win and DROP the
            // actual Daredevil shows. Only a movie/show/season/etc. can be the rich pick.
            val exacts = group.filter { it.title.equals(searchQuery, ignoreCase = true) && it.type != "episode" }
            return when {
                group.size == 1  -> listOf(group[0].copy(rich = true))
                exacts.size == 1 -> {
                    val pick = exacts[0]
                    // Rich the clear match, but KEEP siblings that also genuinely match the query (a
                    // second "Daredevil" series) as rows — only incidental noise (no title match) drops.
                    val others = group.filter { it !== pick && it.title.contains(searchQuery, ignoreCase = true) }
                    listOf(pick.copy(rich = true)) + others
                }
                else             -> group
            }
        }
        val media = if (splitLibs) finalList.groupBy { libOf(it) }.values.flatMap { pickRich(it) }
                    else pickRich(finalList)
        // Actor/Director person card (rich, photo + Wikipedia bio + filmography). The card has no
        // compact form (a person has no item to open), so show it ONLY when confident in a SINGLE
        // person — mirrors the media pickRich rule. Candidates = people whose name contains every query
        // token (guards an incidental surname match in an unrelated search). Pick when there's exactly
        // one candidate, or exactly one whose name equals the query; otherwise show no person card.
        val qTokens = searchQuery.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val candidates = search.people.filter { p -> qTokens.all { p.name.lowercase().contains(it) } }
        val person = if (candidates.size == 1) candidates.first()
                     else candidates.singleOrNull { it.name.equals(searchQuery, ignoreCase = true) }
        val people = listOfNotNull(person).map { p ->
            SearchResult.PersonResult(
                id = p.id, name = p.name, role = p.role,
                thumbUrl = p.thumb, filmographyKeys = p.filmographyKeys
            )
        }
        return people + media
    }
}
