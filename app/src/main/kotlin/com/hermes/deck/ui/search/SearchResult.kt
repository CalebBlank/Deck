package com.hermes.deck.ui.search

import com.hermes.deck.data.AppInfo

sealed class SearchResult {
    data class AppResult(val app: AppInfo, val rich: Boolean = false) : SearchResult()

    data class ContactResult(
        val name: String,
        val phoneNumber: String?,
        val email: String?,
        val photoUri: String?,
        val rich: Boolean = false   // true = the confident single match → expanded card with actions
    ) : SearchResult()

    data class CalculatorResult(
        val expression: String,
        val result: String
    ) : SearchResult()

    /**
     * A computed "answer" card shared by the conversion/lookup providers (unit / currency / timezone
     * / translation). Each fires only when the query parses to its pattern, returning one prominent
     * answer. [providerId] drives grouping/ranking/settings; tapping copies [copyText].
     */
    data class AnswerResult(
        val providerId: String,   // "unit" | "currency" | "timezone" | "translation"
        val value: String,        // the prominent answer, e.g. "3.107 mi", "€92.50", "3:00 PM PST", "hola"
        val detail: String?,      // secondary line echoing the input / source
        val copyText: String,     // copied to clipboard on tap
    ) : SearchResult()

    data class PluginResult(
        val pluginId: String,
        val pluginName: String,
        val title: String,
        val subtitle: String?,
        val iconUri: String?,
        val actionUri: String?,
        val resultType: String? = null
    ) : SearchResult()

    data class AiResult(
        val query: String,
        val answer: String
    ) : SearchResult()

    /**
     * Tap-to-ask Claude (Anthropic API) result. The provider emits this for any
     * query without making a network call; the actual API request fires only
     * when the user taps the card. Carries just the query — the card owns the
     * idle → loading → answer/error state.
     */
    data class ClaudeResult(
        val query: String
    ) : SearchResult()

    /**
     * Tap-to-ask Hermes (self-hosted Nous Research agent via its OpenAI-compatible API). Mirrors
     * [ClaudeResult]: emitted for any query without a network call; the request fires only when the
     * user taps the card. Hermes is its own agent (server-side memory + tools).
     */
    data class HermesResult(
        val query: String
    ) : SearchResult()

    /**
     * Tap-to-search Google Maps local places. Emitted for any query with NO network call (mirrors
     * [ClaudeResult]); the Places API Text Search + static map fire only when the user taps the card,
     * which then owns the idle → loading → results/error state.
     */
    data class PlacesResult(
        val query: String,
        val places: List<com.hermes.deck.ui.search.providers.PlacesClient.Place>,
    ) : SearchResult()

    /**
     * A Wikipedia match. [rich]=true is the confident single result (REST summary resolved the query
     * to one standard article) → render the summary card with thumbnail + description + extract.
     * rich=false is a plain full-text search row (title + snippet). Tapping opens the article [url].
     */
    data class WikipediaResult(
        val title: String,
        val description: String?,
        val extract: String?,
        val thumbnailUrl: String?,
        val url: String,
        val rich: Boolean,
    ) : SearchResult()

    /** Tap-to-search Gmail (IMAP via app-password). Emitted with no network; the inbox search runs
     *  only when the card is tapped (an IMAP connect is too slow per keystroke). */
    data class GmailResult(
        val query: String,
        val mails: List<com.hermes.deck.ui.search.providers.GmailClient.Mail>,
    ) : SearchResult()

    /** A YouTube video (YouTube Data API v3 search). Tapping opens it in the YouTube app / browser.
     *  The provider debounces + caches hard because each API search costs 100 quota units. */
    data class YouTubeResult(
        val videoId: String,
        val title: String,
        val channel: String,
        val thumbnailUrl: String?,
    ) : SearchResult()

    /** A parsed timer or alarm ("5 minute timer", "alarm 7am"). Tapping fires the AlarmClock intent
     *  (set directly, no clock UI). Floats to the top like the other computed-answer cards. */
    data class TimerResult(
        val displayText: String,   // "Set a 5-minute timer" / "Set alarm for 7:00 AM"
        val isAlarm: Boolean,
        val seconds: Int = 0,      // timer length
        val hour: Int = -1,        // alarm hour (24h)
        val minute: Int = 0,       // alarm minute
    ) : SearchResult()

    /** A current-conditions + short forecast card (Open-Meteo, no key). Floats to the top. */
    data class WeatherResult(
        val location: String,
        val currentTempF: Int,
        val currentCode: Int,      // WMO weather code
        val days: List<WeatherDay>,
        val hours: List<WeatherHour> = emptyList(),
    ) : SearchResult()

    /** A dictionary definition (dictionaryapi.dev, no key) for "define X". Floats to the top. */
    data class DictionaryResult(
        val word: String,
        val phonetic: String?,
        val entries: List<DictEntry>,   // part of speech + its definitions
    ) : SearchResult()

    /** Tap-to-ask the ON-DEVICE model (Qwen via [LocalLlmClassifier]) — fully offline, private, free.
     *  Emitted for question-shaped queries when on-device AI is enabled; the model runs only on tap. */
    data class OfflineAnswerResult(
        val query: String
    ) : SearchResult()

    /** A movie/TV title NOT already in Plex, addable via Radarr/Sonarr. The "Add" button posts the
     *  [lookupJson] (augmented) back to the service. Only titles missing from Plex reach here. */
    data class AddMediaResult(
        val service: String,       // "radarr" (movie) | "sonarr" (tv)
        val title: String,
        val year: Int?,
        val overview: String,
        val posterUrl: String?,
        val lookupJson: String,
    ) : SearchResult()

    /** A matching Todoist task. Its checkbox completes the task in place (optimistic + API). */
    data class TodoTaskResult(
        val id: String,
        val content: String,
    ) : SearchResult()

    /** "Add '<query>' to Todoist" — creates the typed text as a new task on tap. */
    data class TodoAddResult(
        val query: String
    ) : SearchResult()

    data class DialerResult(
        val phoneNumber: String,   // digits only, e.g. "5551234"
        val displayText: String    // what the user typed, e.g. "jenny" or "555-1234"
    ) : SearchResult()

    data class WidgetPickerResult(
        val appPackage: String,
        val appLabel: String,
        val providers: List<WidgetProviderInfo>,
        val pinnedComponentName: String?,
        val appWidgetId: Int? = null,
        val customHeightDp: Int? = null,
        val backgroundStyle: String = "default"
    ) : SearchResult()

    data class SettingsResult(
        val title: String,
        val subtitle: String,
        val section: String   // "appearance", "cards", "search", or "about"
    ) : SearchResult()

    data class SystemSettingsResult(
        val title: String,
        val subtitle: String,
        val action: String    // Settings.ACTION_* intent action
    ) : SearchResult()

    data class BrowserHistoryResult(
        val url: String,
        val title: String,
        val browserName: String
    ) : SearchResult()

    data class FileResult(
        val path: String,
        val name: String,
        val mimeType: String?
    ) : SearchResult()

    data class BrowserSuggestionResult(
        val suggestion: String
    ) : SearchResult()

    /**
     * A controllable Home Assistant entity (v1: light / switch / input_boolean). The card
     * renders domain-appropriate controls and calls HA services. Carries the snapshot the
     * provider had; the card keys its own optimistic state on entityId.
     */
    /** A Plex Media Server search match (movie / show / episode / music). Tapping opens it in Plex. */
    data class PlexResult(
        val ratingKey: String,
        val title: String,
        val subtitle: String,    // e.g. "Movie · 2010" or "Breaking Bad · S1E1"
        val type: String,        // movie / show / season / episode / artist / album / track / …
        val thumbUrl: String?,   // absolute, token-authed poster URL
        val library: String?,    // librarySectionTitle, e.g. "Movies" / "TV Shows" (for split grouping)
        val rich: Boolean = false // confident single match → large poster + Play + volume
    ) : SearchResult()

    /** A person (actor / director) matched in Plex. Always a rich card: photo + Wikipedia bio + a
     *  scrollable filmography row (fetched from [filmographyKeys] when the card mounts). */
    data class PersonResult(
        val id: String,
        val name: String,
        val role: String,                 // "actor" | "director"
        val thumbUrl: String?,            // absolute metadata-static.plex.tv photo (fetched direct)
        val filmographyKeys: List<String> // Plex section queries listing their titles
    ) : SearchResult()

    /** A Tandoor Recipes search match. Tapping opens the recipe in kitshn (or the web page). */
    data class TandoorResult(
        val id: Int,
        val name: String,
        val subtitle: String,    // description, total time, or "Recipe"
        val imageUrl: String?,   // absolute, token-authed image URL
        val rich: Boolean = false // confident single match → large image + Open recipe
    ) : SearchResult()

    /** A Symfonium music-library match (song, album, or artist). Tapping plays it in Symfonium. */
    data class SymfoniumResult(
        val mediaId: String,     // Symfonium media id, e.g. "song/216043"
        val title: String,
        val subtitle: String?,   // artist · album
        val artUri: String?,     // art uri (content://), may be unreadable → type icon fallback
        val type: String,        // "song" | "album" | "artist"
        val rich: Boolean = false // confident single match → large art + Play + volume
    ) : SearchResult()

    /** A saved Transistor radio station. Tapping plays it and opens Transistor's now-playing screen. */
    data class TransistorResult(
        val mediaId: String,     // station UUID
        val title: String,
        val art: android.graphics.Bitmap?   // station favicon (MediaItem.iconBitmap); null → radio icon fallback
    ) : SearchResult()

    data class HomeAssistantResult(
        val entityId: String,      // e.g. "light.kitchen"
        val domain: String,        // light/switch/fan/cover/lock/scene/script/climate/media_player/…
        val friendlyName: String,
        val state: String,         // "on"/"off"/"locked"/"open"/hvac-mode/"playing"/…
        val brightness: Int?,      // light: 0-255 when on
        val percentage: Int?,      // fan: 0-100
        val position: Int?,        // cover: current_position 0-100
        val attributes: Map<String, String> = emptyMap()  // richer domains (climate/media) read extras here
    ) : SearchResult()
}

/** One part-of-speech block of a dictionary entry. */
data class DictEntry(
    val partOfSpeech: String,
    val definitions: List<String>,
)

/** One day of the weather forecast. */
data class WeatherDay(
    val label: String,   // "Today" / "Tue"
    val code: Int,       // WMO weather code
    val hiF: Int,
    val loF: Int,
)

/** One hour of the weather forecast. */
data class WeatherHour(
    val label: String,   // "Now" / "3 PM"
    val code: Int,       // WMO weather code
    val tempF: Int,
)

data class WidgetProviderInfo(
    val componentName: String,
    val label: String,
    val packageName: String,
    val previewResId: Int,
    val iconResId: Int,
    val minHeightDp: Int = 100
)
