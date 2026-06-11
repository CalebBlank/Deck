package com.hermes.deck.ui.search

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hermes.deck.data.AppInfo
import com.hermes.deck.data.InstalledAppsRepository
import com.hermes.deck.data.TagRepository
import com.hermes.deck.plugin.PluginInfo
import com.hermes.deck.plugin.PluginRepository
import com.hermes.deck.ui.search.providers.AiProvider
import com.hermes.deck.ui.search.providers.AnthropicClient
import com.hermes.deck.ui.search.providers.GeminiNanoClassifier
import com.hermes.deck.ui.search.providers.LocalLlmClassifier
import com.hermes.deck.ui.search.providers.YouTubeProvider
import com.hermes.deck.ui.search.providers.TimerProvider
import com.hermes.deck.ui.search.providers.WeatherProvider
import com.hermes.deck.ui.search.providers.WeatherClient
import com.hermes.deck.ui.search.providers.DictionaryProvider
import com.hermes.deck.ui.search.providers.OfflineAnswerProvider
import com.hermes.deck.ui.search.providers.ArrProvider
import com.hermes.deck.ui.search.providers.TodoistProvider
import com.hermes.deck.ui.search.providers.AppSearchProvider
import com.hermes.deck.ui.search.providers.ChatMessage
import com.hermes.deck.ui.search.providers.ChatSession
import com.hermes.deck.ui.search.providers.ClaudeChatState
import com.hermes.deck.ui.search.providers.ClaudeChatStore
import com.hermes.deck.ui.search.providers.ClaudeProvider
import com.hermes.deck.ui.search.providers.ClaudeMemoryStore
import com.hermes.deck.ui.search.providers.ChatBackend
import com.hermes.deck.ui.search.providers.HermesClient
import com.hermes.deck.ui.search.providers.HermesProvider
import com.hermes.deck.ui.search.providers.SharedMemoryClient
import com.hermes.deck.ui.search.providers.ConversationClient
import com.hermes.deck.ui.search.providers.ClaudeNotifications
import com.hermes.deck.ui.search.providers.HomeAssistantProvider
import com.hermes.deck.ui.search.providers.PlacesProvider
import com.hermes.deck.ui.search.providers.WikipediaProvider
import com.hermes.deck.ui.search.providers.UnitConversionProvider
import com.hermes.deck.ui.search.providers.TimezoneProvider
import com.hermes.deck.ui.search.providers.CurrencyProvider
import com.hermes.deck.ui.search.providers.TranslationProvider
import com.hermes.deck.ui.search.providers.GmailProvider
import com.hermes.deck.ui.search.providers.HomeAssistantClient
import com.hermes.deck.ui.search.providers.PlexProvider
import com.hermes.deck.ui.search.providers.TandoorProvider
import com.hermes.deck.ui.search.providers.SymfoniumProvider
import com.hermes.deck.ui.search.providers.TransistorProvider
import com.hermes.deck.ui.search.providers.ClaudePendingAction
import java.util.UUID
import com.hermes.deck.ui.search.providers.BrowserHistorySearchProvider
import com.hermes.deck.ui.search.providers.BrowserSuggestionsProvider
import com.hermes.deck.ui.search.providers.HermesBrowserHistoryProvider
import com.hermes.deck.ui.search.providers.CalculatorProvider
import com.hermes.deck.ui.search.providers.ContactSearchProvider
import com.hermes.deck.ui.search.providers.FileSearchProvider
import com.hermes.deck.data.WidgetPinRepository
import com.hermes.deck.ui.search.providers.DialerProvider
import com.hermes.deck.ui.search.providers.PluginSearchProvider
import com.hermes.deck.ui.search.providers.SettingsSearchProvider
import com.hermes.deck.ui.search.providers.SystemSettingsSearchProvider
import com.hermes.deck.ui.search.providers.WidgetSearchProvider
import com.hermes.deck.ui.search.providers.SearchProvider
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val staticProviders: List<SearchProvider>,
    private val pluginRepository: PluginRepository,
    private val prefs: SharedPreferences,
    private val appCtx: Context
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<SearchResult>>(emptyList())
    val results: StateFlow<List<SearchResult>> = _results.asStateFlow()

    private val _recentClicks = MutableStateFlow<List<SearchResult>>(emptyList())
    val recentClicks: StateFlow<List<SearchResult>> = _recentClicks.asStateFlow()

    // Provider ids in relevance order for the CURRENT query (empty = use the default provider order).
    // Computed from the query itself the moment it settles, so the UI can slot each provider's group
    // into its ranked position as results trickle in — no late reorder of anything already on screen.
    private val _rankedGroupOrder = MutableStateFlow<List<String>>(emptyList())
    val rankedGroupOrder: StateFlow<List<String>> = _rankedGroupOrder.asStateFlow()

    // Content domains the query is "about", from the on-device Gemini Nano classifier (null = off /
    // unavailable / still inferring → ranking uses the heuristic). Arrives LATE — set asynchronously
    // after the fast results so it re-ranks groups into place without blocking the search.
    private val _queryDomains = MutableStateFlow<Set<String>?>(null)
    val queryDomains: StateFlow<Set<String>?> = _queryDomains.asStateFlow()

    // Used to resolve a recent widget's bound appWidgetId so unloadable ones can be hidden.
    private val widgetPinRepo by lazy { WidgetPinRepository(appCtx) }

    // Active Claude conversation. When non-null, the results list renders the chat
    // (one card per message + a reply card) instead of the normal search results.
    private val _activeChat = MutableStateFlow<ClaudeChatState?>(null)
    val activeChat: StateFlow<ClaudeChatState?> = _activeChat.asStateFlow()

    // When on, Claude sends are delegated to the thinking model (extended thinking). Session-level.
    private val _claudeThinking = MutableStateFlow(false)
    val claudeThinking: StateFlow<Boolean> = _claudeThinking.asStateFlow()
    fun toggleClaudeThinking() { _claudeThinking.value = !_claudeThinking.value }

    private val discoveredPlugins = MutableStateFlow<List<PluginInfo>>(emptyList())

    init {
        _recentClicks.value = loadRecentClicks()

        // Track installed plugins; rebuilds list on package install/remove
        viewModelScope.launch {
            pluginRepository.pluginsFlow().collect { plugins ->
                discoveredPlugins.value = plugins
            }
        }

        // Fan out to all providers (static + enabled plugins) in parallel on each debounced query
        viewModelScope.launch {
            var current: kotlinx.coroutines.Job? = null
            // Last results per provider id, carried ACROSS queries. Seeding each new query's slots
            // from this (instead of empty) keeps every group present so it updates in place rather
            // than vanishing and re-appearing — which would re-fire its enter animation each keystroke.
            // Only ever written on the main thread (the fan-out runs on Main.immediate), so the plain
            // LinkedHashMap is safe.
            val carried = LinkedHashMap<String, List<SearchResult>>()
            _query
                .debounce(200L)
                .collect { q ->
                    // Cancel the previous fan-out WITHOUT joining. collectLatest cancels-AND-JOINS,
                    // but a slow provider's blocking HTTP (Plex/HA) isn't interruptible, so the join
                    // stalled the NEXT query until the old search finished — the UI stuck on the
                    // previous query for many seconds. Manual cancel + the ensureActive() guard below
                    // lets the new query show immediately while the old (cancelled) search dies
                    // quietly in the background (its late results are dropped at ensureActive()).
                    current?.cancel()
                    current = launch {
                    if (q.isBlank()) {
                        _results.value = _recentClicks.value
                        carried.clear()   // a cleared box starts fresh; next query's groups animate in
                        _rankedGroupOrder.value = emptyList()
                        _queryDomains.value = null
                        return@launch
                    }
                    val disabledStatic = prefs.getStringSet("disabled_static_providers", emptySet()) ?: emptySet()
                    val disabledPlugins = prefs.getStringSet("disabled_plugins", emptySet()) ?: emptySet()
                    val enabledStatic = staticProviders.filter { it.id !in disabledStatic }
                    val pluginProviders = discoveredPlugins.value
                        .filter { it.authority !in disabledPlugins }
                        .map { PluginSearchProvider(it, pluginRepository) }
                    // Relevance-ranked search (manual drag-reorder removed): the prior is a fixed TIER
                    // order so newer providers (Maps, plugins like Komikku) sit sensibly instead of
                    // sinking last; SearchRanker then reorders by query relevance on top.
                    val allProviders = (enabledStatic + pluginProviders).sortedBy { providerTier(it.id) }
                    // Decide the group order from the QUERY now (before results), so each provider's
                    // group slots into its ranked position as it trickles in — no late reorder. Empty
                    // when the AI re-ranking setting is off → the UI keeps the default provider order.
                    _rankedGroupOrder.value =
                        if (prefs.getBoolean("search_ai_rerank", true))
                            SearchRanker.rank(appCtx, q, allProviders.map { it.id })
                        else emptyList()
                    // On-device Gemini Nano query-intent classifier (opt-in, downloads a model). Runs
                    // as a LATE re-rank: clear the old domains now, then update when inference returns
                    // (~1 s) so groups snap into the smarter order without blocking the fast results.
                    // Child of `current`, so a new keystroke cancels it; ensureActive() drops stale.
                    _queryDomains.value = null
                    if (prefs.getBoolean("search_nano_ranking", false)) {
                        launch {
                            // Prefer AICore Gemini Nano (instant, no download); fall back to the bundled
                            // MediaPipe LLM (Qwen-0.5B, downloaded on demand) on devices where AICore
                            // won't serve Nano (rooted / beta).
                            val domains = GeminiNanoClassifier.classify(q)
                                ?: LocalLlmClassifier.classify(appCtx, q)
                            ensureActive()
                            _queryDomains.value = domains
                        }
                    }
                    // Each provider updates the results the moment it finishes, so fast providers
                    // show immediately and a slow one (e.g. Plex searching a large library) trickles
                    // in when ready — instead of the whole list waiting for the slowest (awaitAll,
                    // which made the results sit blank until Plex returned).
                    // Keep the previous results visible until the new ones trickle in (clearing to
                    // empty here blanked the recents and flashed partial-query results). Each
                    // provider overwrites the whole list (in stable provider order) as it finishes.
                    // Seed each slot with the provider's PREVIOUS results (not empty): the first fast
                    // provider to return for the new query then updates only its OWN slot instead of
                    // collapsing the whole list to itself, so other groups stay put and just refresh.
                    val partial = LinkedHashMap<String, List<SearchResult>>()
                    allProviders.forEach { partial[it.id] = carried[it.id] ?: emptyList() }
                    kotlinx.coroutines.coroutineScope {
                        allProviders.forEach { provider ->
                            launch {
                                val res = runCatching { provider.query(q) }.getOrElse { emptyList() }
                                // A superseded (cancelled) query must NOT write its now-stale results:
                                // runCatching swallows CancellationException, and a slow provider's
                                // blocking HTTP keeps running after cancel, so without this guard the
                                // old query's results land late and clobber the current ones.
                                ensureActive()
                                // Per-provider result cap (0 / absent = unlimited), set in Search settings.
                                // Plex in split-libraries mode caps each library itself (PlexProvider), so
                                // skip the generic combined cap here to avoid truncating the merged list.
                                val plexSplit = provider.id == "plex" &&
                                    prefs.getBoolean("plex_split_libraries", false)
                                val limit = if (plexSplit) 0 else prefs.getInt("provider_limit_${provider.id}", 0)
                                // Content libraries (Plex/Arr) occasionally return loose matches unrelated to
                                // the query (e.g. "steamdeck" surfacing the film "Ned Rifle"). Drop titles that
                                // don't relate to the query; normalized so "steamdeck" still matches "Steam Deck".
                                val relevant =
                                    if (provider.id in CONTENT_MATCH_PROVIDERS) res.filter { titleRelevantToQuery(q, it) }
                                    else res
                                val capped = if (limit > 0) relevant.take(limit) else relevant
                                partial[provider.id] = capped
                                carried[provider.id] = capped   // remember for the next query's seed
                                _results.value = partial.values.flatten()
                            }
                        }
                    }
                    }
                }
        }
    }

    // Editing the search box returns to normal search (the chat stays saved in recents).
    fun onQueryChange(q: String) { if (_activeChat.value != null) _activeChat.value = null; _query.value = q }
    fun clearQuery() { _activeChat.value = null; _query.value = "" }

    /** Start a new Claude conversation seeded with the typed query. */
    fun startClaude(query: String) {
        sendClaude(ClaudeChatState(UUID.randomUUID().toString(), listOf(ChatMessage("user", query.trim())), loading = true))
    }

    /** Start a new Hermes conversation seeded with the typed query. */
    fun startHermes(query: String) {
        sendClaude(ClaudeChatState(
            UUID.randomUUID().toString(), listOf(ChatMessage("user", query.trim())),
            loading = true, backend = ChatBackend.Hermes
        ))
    }

    /** Reopen a saved conversation (with its persisted cards/actions), then refresh HA card state. */
    fun resumeClaude(session: ChatSession) {
        _activeChat.value = ClaudeChatState(
            session.id, session.messages,
            cardsByMessage = session.cardsByMessage, actionsByMessage = session.actionsByMessage,
            remoteTid = session.remoteTid, importCount = session.importCount,
            backend = session.backend
        )
        refreshHaCards(session.id)
    }

    /** After reopening, re-fetch live HA state so persisted device cards aren't stale. */
    private fun refreshHaCards(sessionId: String) {
        if (!HomeAssistantClient.isConfigured(appCtx)) return
        viewModelScope.launch {
            val states = HomeAssistantClient.states(appCtx).getOrNull()?.associateBy { it.entityId } ?: return@launch
            _activeChat.update { st ->
                if (st == null || st.sessionId != sessionId) return@update st
                st.copy(cardsByMessage = st.cardsByMessage.mapValues { (_, list) ->
                    list.map { card ->
                        if (card is SearchResult.HomeAssistantResult) {
                            states[card.entityId]?.let { e ->
                                SearchResult.HomeAssistantResult(e.entityId, e.domain, e.friendlyName,
                                    e.state, e.brightness, e.percentage, e.position, e.attributes)
                            } ?: card
                        } else card
                    }
                })
            }
        }
    }

    /** Send a follow-up in the active conversation. */
    fun replyClaude(text: String) {
        val cur = _activeChat.value ?: return
        if (cur.loading || text.isBlank()) return
        sendClaude(cur.copy(messages = cur.messages + ChatMessage("user", text.trim()), loading = true, error = null))
    }

    /** Close the conversation and return to normal search results. */
    fun endClaude() { _activeChat.value = null }

    private fun sendClaude(state: ClaudeChatState) {
        _activeChat.value = state
        val thinking = _claudeThinking.value
        viewModelScope.launch {
            val pending = mutableListOf<ClaudePendingAction>()
            val cards = mutableListOf<SearchResult>()
            val onText: (String) -> Unit = { partial ->
                // Stream the in-progress answer into the live chat (only if still on this chat).
                if (_activeChat.value?.sessionId == state.sessionId) {
                    _activeChat.value = _activeChat.value?.copy(streamingText = partial)
                }
            }
            val result = if (state.backend == ChatBackend.Hermes)
                // Hermes is its own agent (server-side memory + tools) — no Deck-local tools here.
                HermesClient.ask(appCtx, state.messages, onText = onText)
            else
                AnthropicClient.ask(
                    appCtx, state.messages, thinking = thinking,
                    tools = claudeTools(),
                    executeTool = { name, input -> executeClaudeTool(name, input, pending, cards) },
                    terminalTools = setOf(
                        "home_assistant_control", "show_results",
                        "launch_app", "call_number", "open_setting"
                    ),
                    onText = onText
                )
            result
                .onSuccess { resp ->
                    val msgs = state.messages + ChatMessage("assistant", resp.text)
                    val asstIndex = msgs.size - 1   // the assistant message these belong to
                    val newActions = if (pending.isEmpty()) state.actionsByMessage
                                     else state.actionsByMessage + (asstIndex to pending.toList())
                    val newCards   = if (cards.isEmpty()) state.cardsByMessage
                                     else state.cardsByMessage + (asstIndex to cards.toList())
                    // Persist regardless so a completed turn is never lost…
                    val title = (state.remoteTid?.let { msgs.firstOrNull()?.content } ?: state.messages.first().content).take(60)
                    ClaudeChatStore.save(
                        appCtx,
                        ChatSession(state.sessionId, title, System.currentTimeMillis(), msgs, newCards, newActions,
                            remoteTid = state.remoteTid, importCount = state.importCount, backend = state.backend)
                    )
                    // Conversation handoff: append THIS device's new turns to the shared thread (per-turn,
                    // never overwrites another surface's turns). tid = the resumed thread, else this session.
                    if (ConversationClient.isConfigured(appCtx)) {
                        val tid = state.remoteTid ?: state.sessionId
                        val ownTurns = msgs.drop(state.importCount).map { it.role to it.content }
                        viewModelScope.launch {
                            ConversationClient.appendNew(appCtx, tid, state.sessionId, title, ownTurns)
                        }
                    }
                    // …but only touch the live view if the user is still on this conversation.
                    if (_activeChat.value?.sessionId == state.sessionId) {
                        _activeChat.value = state.copy(
                            messages         = msgs,
                            loading          = false,
                            lastInTokens     = resp.inputTokens,
                            lastOutTokens    = resp.outputTokens,
                            actionsByMessage = newActions,
                            cardsByMessage   = newCards
                        )
                    }
                    // Auto-memory: extract durable facts in the background. Best-effort; never blocks
                    // the answer the user already sees. Runs after the save above so it's complete.
                    if (state.backend == ChatBackend.Claude && ClaudeMemoryStore.isEnabled(appCtx)) {
                        viewModelScope.launch {
                            val facts = AnthropicClient.extractMemories(appCtx, msgs, ClaudeMemoryStore.list(appCtx))
                            if (facts.isNotEmpty()) {
                                ClaudeMemoryStore.addAll(appCtx, facts)
                                if (SharedMemoryClient.isConfigured(appCtx)) SharedMemoryClient.push(appCtx, facts)
                            }
                        }
                    }
                    // Notify when the answer is ready if the user isn't looking at THIS chat — i.e.
                    // the active chat is a different/none, or the launcher is backgrounded.
                    val lookingAtThis = _activeChat.value?.sessionId == state.sessionId &&
                        com.hermes.deck.MainActivity.isInForeground
                    if (!lookingAtThis && ClaudeNotifications.isEnabled(appCtx)) {
                        val q = state.messages.firstOrNull { it.role == "user" }?.content?.take(40)
                        val who = if (state.backend == ChatBackend.Hermes) "Hermes" else "Claude"
                        ClaudeNotifications.notifyAnswer(
                            appCtx, state.sessionId,
                            if (q.isNullOrBlank()) "$who replied" else "$who · $q",
                            resp.text
                        )
                    }
                }
                .onFailure { e ->
                    if (_activeChat.value?.sessionId == state.sessionId) {
                        _activeChat.value = _activeChat.value?.copy(
                            loading = false, error = e.message ?: "Something went wrong", streamingText = null
                        )
                    }
                }
        }
    }

    // --- Claude agent: let Claude read Deck's content via a `search_deck` tool ---

    /** Static providers the user has exposed to Claude (per-provider toggle, default on). Excludes
     *  the AI/Claude providers themselves so the agent can't recurse into itself. */
    private fun exposedProviders(): List<SearchProvider> {
        val disabledStatic = prefs.getStringSet("disabled_static_providers", emptySet()) ?: emptySet()
        return staticProviders.filter {
            it.id !in disabledStatic &&
            it.id != "claude" && it.id != "ai" && it.id != "hermes" &&
            prefs.getBoolean("claude_expose_${it.id}", true)
        }
    }

    /** Tool schemas to pass to Claude — null (no tools) when nothing is exposed. */
    private fun claudeTools(): List<JSONObject>? {
        val tools = mutableListOf<JSONObject>()
        // Memory write tool — let Claude save a durable fact to the shared memory on request,
        // available whenever memory is on and PocketBase is configured (no providers needed).
        if (ClaudeMemoryStore.isEnabled(appCtx) && SharedMemoryClient.isConfigured(appCtx)) {
            tools += JSONObject()
                .put("name", "remember_fact")
                .put("description", "Save a durable long-term fact about the user (a preference, " +
                    "identity detail, device, or goal) to their shared memory, which syncs across all " +
                    "their devices. Use ONLY when the user explicitly asks you to remember something " +
                    "lasting — never transient or one-off details. Confirm briefly after saving.")
                .put("input_schema", JSONObject()
                    .put("type", "object")
                    .put("properties", JSONObject()
                        .put("text", JSONObject().put("type", "string")
                            .put("description", "The fact as a concise third-person statement, e.g. 'Prefers tea over coffee.'"))
                        .put("category", JSONObject().put("type", "string")
                            .put("description", "One of: identity, preference, device, goal, other.")))
                    .put("required", JSONArray().put("text")))
        }
        val exposed = exposedProviders()
        if (exposed.isEmpty()) return tools.ifEmpty { null }
        // Read tool — always available when something is exposed.
        tools += JSONObject()
            .put("name", "search_deck")
            .put("description",
                "Search the user's phone via Deck: installed apps, Home Assistant devices (with " +
                "current state), contacts, phone & Deck settings, files, and browser history. Use it " +
                "to locate anything the user refers to on their device. Returns matching items with " +
                "their exact identifiers (package name, Home Assistant entity_id, phone number, etc.) " +
                "and current state.")
            .put("input_schema", JSONObject()
                .put("type", "object")
                .put("properties", JSONObject()
                    .put("query", JSONObject()
                        .put("type", "string")
                        .put("description", "What to look for, e.g. 'bedroom light', 'chase', 'wifi'")))
                .put("required", JSONArray().put("query")))
        // Presentation tool — render the real interactive result cards inline in the chat.
        tools += JSONObject()
            .put("name", "show_results")
            .put("description",
                "Present interactive result cards to the user in the chat — the real Home Assistant " +
                "device cards (with toggles/sliders), app cards, contacts, etc. Use it to SHOW the " +
                "user what you found so they can see and interact with it directly. Give a search " +
                "query; the matching cards render below your reply. Presentation only — performs no " +
                "action and needs no confirmation.")
            .put("input_schema", JSONObject()
                .put("type", "object")
                .put("properties", JSONObject()
                    .put("query", JSONObject()
                        .put("type", "string")
                        .put("description", "What to show, e.g. 'bedroom lights', 'chase'")))
                .put("required", JSONArray().put("query")))
        // Action tool — Home Assistant control, only when HA is exposed AND configured.
        if (exposed.any { it.id == "home_assistant" } && HomeAssistantClient.isConfigured(appCtx)) {
            tools += JSONObject()
                .put("name", "home_assistant_control")
                .put("description",
                    "Control a Home Assistant entity you found with search_deck. Deck runs this " +
                    "IMMEDIATELY — there is no confirmation step, so phrase your reply as already done " +
                    "(e.g. \"Turned off the bedroom lights\"), never as a request to confirm or as " +
                    "something about to happen. Use the exact entity_id from search_deck; call once per entity.")
                .put("input_schema", JSONObject()
                    .put("type", "object")
                    .put("properties", JSONObject()
                        .put("entity_id", JSONObject().put("type", "string")
                            .put("description", "Exact entity id, e.g. light.bedroom"))
                        .put("service", JSONObject().put("type", "string")
                            .put("description", "Service without domain: turn_on, turn_off, toggle, lock, unlock, open_cover, close_cover, …"))
                        .put("brightness_pct", JSONObject().put("type", "integer")
                            .put("description", "Optional 0–100 brightness for lights"))
                        .put("summary", JSONObject().put("type", "string")
                            .put("description", "Short label for the confirm card, e.g. 'Turn off Bedroom Light'")))
                    .put("required", JSONArray().put("entity_id").put("service").put("summary")))
        }
        // App-leaving actions — these close the search bar / open an app, so each records a PENDING
        // action and the user gets a Confirm card before it runs.
        if (exposed.any { it.id == "apps" }) {
            tools += actionTool("launch_app",
                "Open an installed app you found with search_deck. This LEAVES the launcher, so Deck " +
                "shows the user a Confirm button first — phrase your reply as an offer (e.g. \"I can " +
                "open Chase\"), not as already done.",
                "package", "Exact package name from search_deck, e.g. com.chase.sig.android")
        }
        if (exposed.any { it.id == "contacts" || it.id == "dialer" }) {
            tools += actionTool("call_number",
                "Open the phone dialer for a number (from a contact or dialer result found with " +
                "search_deck). Leaves the launcher, so the user confirms first — phrase it as an offer.",
                "number", "Phone number from a search_deck contact/dialer result")
        }
        if (exposed.any { it.id == "system_settings" }) {
            tools += actionTool("open_setting",
                "Open a phone settings screen found with search_deck (use the action= identifier it " +
                "returned). Leaves the launcher, so the user confirms first — phrase it as an offer.",
                "action", "The action= identifier from a 'Phone setting:' result, e.g. android.settings.WIFI_SETTINGS")
        }
        return tools
    }

    /** Schema for a single-string-param action tool (launch_app / call_number / open_setting). */
    private fun actionTool(name: String, description: String, paramName: String, paramDesc: String): JSONObject =
        JSONObject()
            .put("name", name)
            .put("description", description)
            .put("input_schema", JSONObject()
                .put("type", "object")
                .put("properties", JSONObject()
                    .put(paramName, JSONObject().put("type", "string").put("description", paramDesc))
                    .put("summary", JSONObject().put("type", "string")
                        .put("description", "Short label for the Confirm card, e.g. 'Open Chase'")))
                .put("required", JSONArray().put(paramName).put("summary")))

    private suspend fun executeClaudeTool(
        name: String, input: JSONObject,
        pending: MutableList<ClaudePendingAction>,
        cards: MutableList<SearchResult>
    ): String = when (name) {
        "remember_fact" -> {
            val text = input.optString("text").trim()
            if (text.isBlank()) "Error: nothing to remember."
            else {
                ClaudeMemoryStore.addAll(appCtx, listOf(text))
                val ok = SharedMemoryClient.add(appCtx, text, input.optString("category"))
                if (ok) "Saved to your shared memory (syncs to all devices): \"$text\"."
                else "Saved on this phone, but couldn't reach shared memory right now."
            }
        }
        "search_deck" -> searchForAgent(input.optString("query"))
        "show_results" -> {
            val results = runExposedProviders(input.optString("query")).take(8)
            cards += results
            if (results.isEmpty()) "Nothing matched, so no cards were shown."
            else "Showing ${results.size} card(s) to the user below your reply:\n" + formatForAgent(results)
        }
        "home_assistant_control" -> {
            val entity  = input.optString("entity_id")
            val service = input.optString("service")
            if (entity.isBlank() || service.isBlank()) "Error: entity_id and service are required."
            else {
                val summary = input.optString("summary").ifBlank { "$service ${entity.substringAfter('.')}" }
                val domain  = entity.substringBefore('.')
                val brightnessPct = if (input.has("brightness_pct") && !input.isNull("brightness_pct"))
                    input.optInt("brightness_pct") else null
                val extras = brightnessPct?.let { JSONObject().put("brightness_pct", it) }
                // In-place action — run it immediately (no confirmation). On success, show the live
                // card for the entity Claude touched (reflecting the new state); on failure, a
                // Failed card.
                val r = HomeAssistantClient.callService(appCtx, domain, service, entity, extras)
                if (r.isSuccess) {
                    // Optimistically reflect the action in the cache so the rendered card is current.
                    val newState = when (service) {
                        "turn_on"    -> "on";      "turn_off"    -> "off"
                        "lock"       -> "locked";  "unlock"      -> "unlocked"
                        "open_cover" -> "open";    "close_cover" -> "closed"
                        else         -> null   // toggle / unknown: leave as-is; next fetch corrects it
                    }
                    val brightness255 = brightnessPct?.let { (it * 255 / 100).coerceIn(1, 255) }
                    if (newState != null) HomeAssistantClient.patchCache(entity, newState, brightness255)
                    HomeAssistantClient.states(appCtx).getOrNull()
                        ?.firstOrNull { it.entityId == entity }
                        ?.let { e ->
                            cards += SearchResult.HomeAssistantResult(
                                e.entityId, e.domain, e.friendlyName, e.state,
                                e.brightness, e.percentage, e.position, e.attributes
                            )
                        }
                    "Done: $summary."
                } else {
                    pending += ClaudePendingAction(
                        UUID.randomUUID().toString(), summary, "ha_control",
                        mapOf("entity_id" to entity, "service" to service), status = "failed"
                    )
                    "Failed: $summary — ${r.exceptionOrNull()?.message ?: "service call failed"}."
                }
            }
        }
        "launch_app"   -> proposeAction(pending, "launch_app", "package", input)
        "call_number"  -> proposeAction(pending, "call", "number", input)
        "open_setting" -> proposeAction(pending, "open_setting", "action", input)
        else -> "Unknown tool: $name"
    }

    /** Record an app-leaving action for the user to confirm; returns the tool result for Claude. */
    private fun proposeAction(
        pending: MutableList<ClaudePendingAction>, kind: String, paramName: String, input: JSONObject
    ): String {
        val value = input.optString(paramName)
        if (value.isBlank()) return "Error: $paramName is required."
        val summary = input.optString("summary").ifBlank { value }
        pending += ClaudePendingAction(UUID.randomUUID().toString(), summary, kind, mapOf(paramName to value))
        return "Proposed \"$summary\" — it opens only after the user taps Confirm. Tell them what " +
            "you'll do as an offer, in the same reply."
    }

    /** Run a proposed action after the user taps Confirm; updates its status in the live chat. */
    fun confirmClaudeAction(id: String) {
        val action = _activeChat.value?.actionsByMessage?.values?.flatten()?.firstOrNull { it.id == id } ?: return
        if (action.status != "pending") return
        when (action.kind) {
            "ha_control" -> {
                val entity  = action.params["entity_id"] ?: return
                val service = action.params["service"] ?: return
                val domain  = entity.substringBefore('.')
                val extras  = action.params["brightness_pct"]?.toIntOrNull()
                    ?.let { JSONObject().put("brightness_pct", it) }
                viewModelScope.launch {
                    val r = HomeAssistantClient.callService(appCtx, domain, service, entity, extras)
                    updateAction(id) { it.copy(status = if (r.isSuccess) "done" else "failed") }
                }
            }
            "launch_app" -> {
                val intent = action.params["package"]
                    ?.let { appCtx.packageManager.getLaunchIntentForPackage(it) }
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val ok = intent != null && runCatching { appCtx.startActivity(intent) }.isSuccess
                updateAction(id) { it.copy(status = if (ok) "done" else "failed") }
            }
            "call" -> {
                val number = action.params["number"].orEmpty()
                val ok = number.isNotBlank() && runCatching {
                    appCtx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.isSuccess
                updateAction(id) { it.copy(status = if (ok) "done" else "failed") }
            }
            "open_setting" -> {
                val act = action.params["action"].orEmpty()
                val ok = act.isNotBlank() && runCatching {
                    appCtx.startActivity(Intent(act).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.isSuccess
                updateAction(id) { it.copy(status = if (ok) "done" else "failed") }
            }
            else -> updateAction(id) { it.copy(status = "failed") }
        }
    }

    fun cancelClaudeAction(id: String) = updateAction(id) { it.copy(status = "cancelled") }

    private fun updateAction(id: String, transform: (ClaudePendingAction) -> ClaudePendingAction) {
        _activeChat.update { st ->
            st?.copy(actionsByMessage = st.actionsByMessage.mapValues { (_, list) ->
                list.map { if (it.id == id) transform(it) else it }
            })
        }
    }

    /** Run the exposed providers for [query] in parallel. */
    private suspend fun runExposedProviders(query: String): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        val providers = exposedProviders()
        return kotlinx.coroutines.coroutineScope {
            providers.map { p -> async { runCatching { p.query(query) }.getOrElse { emptyList() } } }.awaitAll()
        }.flatten()
    }

    /** Run the exposed providers and format the matches as text for Claude. */
    private suspend fun searchForAgent(query: String): String {
        if (query.isBlank()) return "No query provided."
        return formatForAgent(runExposedProviders(query))
    }

    private fun formatForAgent(results: List<SearchResult>): String {
        val lines = results.mapNotNull { r ->
            when (r) {
                is SearchResult.AppResult -> "App: ${r.app.label} [package=${r.app.packageName}]"
                is SearchResult.ContactResult -> "Contact: ${r.name}" +
                    (r.phoneNumber?.let { " [phone=$it]" } ?: "") + (r.email?.let { " [email=$it]" } ?: "")
                is SearchResult.DialerResult -> "Dial: ${r.displayText} [number=${r.phoneNumber}]"
                is SearchResult.HomeAssistantResult -> buildString {
                    append("Home Assistant ${r.domain}: ${r.friendlyName} [entity_id=${r.entityId}] state=${r.state}")
                    r.brightness?.let { append(" brightness=${it * 100 / 255}%") }
                    r.percentage?.let { append(" speed=$it%") }
                    r.position?.let { append(" position=$it%") }
                }
                is SearchResult.SettingsResult -> "Deck setting: ${r.title} — ${r.subtitle}"
                is SearchResult.SystemSettingsResult -> "Phone setting: ${r.title} — ${r.subtitle} [action=${r.action}]"
                is SearchResult.FileResult -> "File: ${r.name} [path=${r.path}]"
                is SearchResult.BrowserHistoryResult -> "Web history: ${r.title} (${r.url})"
                is SearchResult.PlexResult -> "Plex ${r.type}: ${r.title} — ${r.subtitle}"
                is SearchResult.PersonResult -> "${r.role.replaceFirstChar { it.uppercase() }}: ${r.name}"
                is SearchResult.TandoorResult -> "Recipe: ${r.name} — ${r.subtitle}"
                is SearchResult.SymfoniumResult -> "${r.type.replaceFirstChar { it.uppercase() }}: ${r.title}" + (r.subtitle?.let { " — $it" } ?: "")
                is SearchResult.TransistorResult -> "Radio station: ${r.title}"
                is SearchResult.PluginResult -> "${r.pluginName}: ${r.title}" + (r.subtitle?.let { " — $it" } ?: "")
                is SearchResult.WikipediaResult -> "Wikipedia: ${r.title}" +
                    (r.description?.let { " ($it)" } ?: "") + (r.extract?.let { " — ${it.take(240)}" } ?: "") + " [${r.url}]"
                is SearchResult.AnswerResult -> r.value + (r.detail?.let { " ($it)" } ?: "")
                is SearchResult.YouTubeResult -> "YouTube: ${r.title} — ${r.channel}"
                is SearchResult.TimerResult -> r.displayText
                is SearchResult.WeatherResult -> "Weather in ${r.location}: ${r.currentTempF}°F, ${WeatherClient.describe(r.currentCode)}"
                is SearchResult.DictionaryResult -> "${r.word}: " + (r.entries.firstOrNull()?.let { "(${it.partOfSpeech}) ${it.definitions.firstOrNull() ?: ""}" } ?: "")
                is SearchResult.AddMediaResult -> "Available to add via ${r.service}: ${r.title}" + (r.year?.let { " ($it)" } ?: "")
                is SearchResult.TodoTaskResult -> "Todoist task: ${r.content}"
                else -> null   // calculator / ai / claude / widget / browser-suggestion: not findable content
            }
        }
        return if (lines.isEmpty()) "No matching items found." else lines.joinToString("\n").take(4000)
    }

    fun recordClick(result: SearchResult) {
        val key = recentKey(result) ?: return
        val current = _recentClicks.value.toMutableList()
        current.removeAll { recentKey(it) == key }
        current.add(0, result)
        val trimmed = current.take(5)
        _recentClicks.value = trimmed
        saveRecentClicks(trimmed)
    }

    fun recordWidgetSeen(result: SearchResult.WidgetPickerResult) {
        val key = recentKey(result) ?: return
        if (recentKey(_recentClicks.value.firstOrNull()) == key) return
        recordClick(result)
    }

    private fun recentKey(result: SearchResult?): String? = when (result) {
        is SearchResult.AppResult             -> "app:${result.app.packageName}"
        is SearchResult.ContactResult         -> "contact:${result.name}"
        is SearchResult.DialerResult          -> "dialer:${result.phoneNumber}"
        is SearchResult.PluginResult          -> "plugin:${result.pluginId}:${result.title}"
        is SearchResult.BrowserHistoryResult  -> "browser:${result.url}"
        is SearchResult.WidgetPickerResult    -> "widget:${result.appPackage}"
        else                                  -> null
    }

    private fun saveRecentClicks(results: List<SearchResult>) {
        val lines = results.mapNotNull { serializeResult(it) }.joinToString("\n")
        prefs.edit().putString("recent_clicks_v2", lines).apply()
    }

    private fun loadRecentClicks(): List<SearchResult> {
        val raw = prefs.getString("recent_clicks_v2", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("\n").mapNotNull { deserializeResult(it) }
    }

    private fun serializeResult(result: SearchResult): String? = when (result) {
        is SearchResult.AppResult ->
            "app\t${result.app.packageName}"
        is SearchResult.ContactResult ->
            "contact\t${result.name}\t${result.phoneNumber.orEmpty()}\t${result.email.orEmpty()}\t${result.photoUri.orEmpty()}"
        is SearchResult.DialerResult ->
            "dialer\t${result.phoneNumber}\t${result.displayText}"
        is SearchResult.PluginResult ->
            "plugin\t${result.pluginId}\t${result.pluginName}\t${result.title}\t${result.subtitle.orEmpty()}\t${result.iconUri.orEmpty()}\t${result.actionUri.orEmpty()}"
        is SearchResult.BrowserHistoryResult ->
            "browser\t${result.url}\t${result.title}\t${result.browserName}"
        is SearchResult.WidgetPickerResult -> {
            val p = result.providers.firstOrNull() ?: return null
            "widget\t${result.appPackage}\t${result.appLabel}\t${p.componentName}\t${p.label}\t${p.packageName}\t${p.minHeightDp}"
        }
        else -> null
    }

    private fun deserializeResult(line: String): SearchResult? {
        val p = line.split("\t")
        return when (p.getOrNull(0)) {
            "app" -> {
                val pkg = p.getOrNull(1) ?: return null
                val pm = appCtx.packageManager
                val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return null
                val label = pm.getApplicationLabel(info).toString()
                val icon = runCatching { pm.getApplicationIcon(pkg) }.getOrNull() ?: return null
                SearchResult.AppResult(AppInfo(pkg, label, icon))
            }
            "contact" -> SearchResult.ContactResult(
                name        = p.getOrNull(1) ?: return null,
                phoneNumber = p.getOrNull(2)?.takeIf { it.isNotEmpty() },
                email       = p.getOrNull(3)?.takeIf { it.isNotEmpty() },
                photoUri    = p.getOrNull(4)?.takeIf { it.isNotEmpty() }
            )
            "dialer" -> SearchResult.DialerResult(
                phoneNumber = p.getOrNull(1) ?: return null,
                displayText = p.getOrNull(2) ?: return null
            )
            "plugin" -> SearchResult.PluginResult(
                pluginId   = p.getOrNull(1) ?: return null,
                pluginName = p.getOrNull(2) ?: return null,
                title      = p.getOrNull(3) ?: return null,
                subtitle   = p.getOrNull(4)?.takeIf { it.isNotEmpty() },
                iconUri    = p.getOrNull(5)?.takeIf { it.isNotEmpty() },
                actionUri  = p.getOrNull(6)?.takeIf { it.isNotEmpty() }
            )
            "browser" -> SearchResult.BrowserHistoryResult(
                url         = p.getOrNull(1) ?: return null,
                title       = p.getOrNull(2) ?: return null,
                browserName = p.getOrNull(3) ?: return null
            )
            "widget" -> {
                val appPackage = p.getOrNull(1) ?: return null
                val appLabel   = p.getOrNull(2) ?: return null
                val compName   = p.getOrNull(3) ?: return null
                val wLabel     = p.getOrNull(4) ?: return null
                val wPkg       = p.getOrNull(5) ?: return null
                val minH       = p.getOrNull(6)?.toIntOrNull() ?: 100
                // Hide recent widgets that aren't fully loaded: only surface those still bound to a
                // live appWidgetId. An unresolved one would render the "long-press to activate"
                // placeholder, so drop it instead of showing it.
                val widgetId   = widgetPinRepo.getPinnedWidgetIdByComponent(compName) ?: return null
                SearchResult.WidgetPickerResult(
                    appPackage = appPackage,
                    appLabel   = appLabel,
                    providers  = listOf(WidgetProviderInfo(compName, wLabel, wPkg, 0, 0, minH)),
                    pinnedComponentName = compName,
                    appWidgetId = widgetId
                )
            }
            else -> null
        }
    }

    companion object {
        /** Fixed prior tier for the relevance ranker (lower = ranks higher by default). Keeps Maps +
         *  plugins (Komikku) up with media instead of dumping them last. */
        private fun providerTier(id: String): Int = when {
            id in setOf("calculator", "unit", "timezone", "currency", "translation", "timer", "weather", "dictionary") -> 0
            id in setOf("apps", "contacts", "dialer", "todoist")                     -> 1
            id in setOf("plex", "tandoor", "symfonium", "transistor", "places", "radarr", "sonarr") || id.startsWith("plugin:") -> 2
            id in setOf("wikipedia", "gmail", "youtube")                             -> 3
            id in setOf("ai", "claude", "hermes", "offline_ai")                      -> 6
            id == "widgets"                                                          -> 5
            else                                                                     -> 4
        }

        /** Content libraries whose results are real titles — gate them by title relevance so a loose
         *  match unrelated to the query (Plex returning the film "Ned Rifle" for "steamdeck") is dropped. */
        private val CONTENT_MATCH_PROVIDERS = setOf("plex", "radarr", "sonarr")

        /** True if [result]'s title is plausibly what [query] is after. Normalized (lowercased,
         *  non-alphanumerics stripped) so a no-space query like "steamdeck" still matches "Steam Deck".
         *  Results with no title are kept — they're judged by their own provider's relevance, not a title. */
        internal fun titleRelevantToQuery(query: String, result: SearchResult): Boolean {
            val title = resultPrimaryText(result) ?: return true
            fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")
            val q = norm(query); val t = norm(title)
            if (q.isEmpty() || t.isEmpty()) return true
            return t.contains(q) || q.contains(t)
        }

        fun factory(context: Context) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val appCtx = context.applicationContext
                val prefs = appCtx.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE)
                val tagRepo = TagRepository(appCtx)
                val staticProviders: List<SearchProvider> = listOf(
                    CalculatorProvider(),
                    UnitConversionProvider(),        // answer card: "5 km to miles"
                    TimezoneProvider(),              // answer card: "time in tokyo" / "3pm EST to PST"
                    CurrencyProvider(),              // answer card: "100 usd to eur" (free ECB rates)
                    TranslationProvider(),           // answer card: "hello in spanish" (free MyMemory)
                    DictionaryProvider(),            // definition card: "define serendipity" (free, no key)
                    TimerProvider(appCtx),           // action card: "5 minute timer" / "alarm 7am" (no backend)
                    DialerProvider(prefs),
                    WidgetSearchProvider(appCtx, WidgetPinRepository(appCtx), tagRepo),
                    AppSearchProvider(InstalledAppsRepository(appCtx), appCtx, tagRepo),
                    ContactSearchProvider(appCtx),
                    SettingsSearchProvider(appCtx),
                    SystemSettingsSearchProvider(appCtx),
                    BrowserHistorySearchProvider(),
                    HermesBrowserHistoryProvider(appCtx),
                    BrowserSuggestionsProvider(),
                    FileSearchProvider(),
                    HomeAssistantProvider(appCtx),   // silent until URL + token are set
                    PlacesProvider(appCtx),          // tap-to-search Google Maps, silent until an API key is set
                    WeatherProvider(appCtx),         // weather card (Open-Meteo, no key); fires only on weather queries
                    WikipediaProvider(appCtx),       // Wikipedia lookup (no key); rich summary when confident, else rows
                    GmailProvider(appCtx),           // tap-to-search Gmail (IMAP app-password), silent until configured
                    YouTubeProvider(appCtx),         // YouTube video search, silent until an API key is set
                    PlexProvider(appCtx),            // silent until server URL + token are set
                    ArrProvider(appCtx, "radarr", "movie"),  // add movies missing from Plex; silent until configured
                    ArrProvider(appCtx, "sonarr", "show"),   // add TV missing from Plex; silent until configured
                    TandoorProvider(appCtx),         // silent until server URL + token are set
                    TodoistProvider(appCtx),         // tasks + quick add; silent until an API token is set
                    SymfoniumProvider(appCtx),       // silent unless the Symfonium app is installed
                    TransistorProvider(appCtx),      // silent unless the Transistor radio app is installed
                    AiProvider(appCtx),   // on-device Gemini Nano, silent no-op until model downloaded
                    OfflineAnswerProvider(appCtx),   // tap-to-ask the bundled on-device model (question queries, AI toggle on)
                    ClaudeProvider(appCtx),   // tap-to-ask Claude, silent until an API key is set
                    HermesProvider(appCtx)    // last: tap-to-ask self-hosted Hermes, silent until URL+password set
                )
                @Suppress("UNCHECKED_CAST")
                return SearchViewModel(staticProviders, PluginRepository(appCtx), prefs, appCtx) as T
            }
        }
    }
}
