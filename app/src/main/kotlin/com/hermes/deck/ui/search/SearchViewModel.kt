package com.hermes.deck.ui.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hermes.deck.data.InstalledAppsRepository
import com.hermes.deck.plugin.PluginRepository
import com.hermes.deck.data.WidgetPinRepository
import com.hermes.deck.ui.search.providers.AiProvider
import com.hermes.deck.ui.search.providers.AppSearchProvider
import com.hermes.deck.ui.search.providers.CalculatorProvider
import com.hermes.deck.ui.search.providers.ContactSearchProvider
import com.hermes.deck.ui.search.providers.PluginSearchProvider
import com.hermes.deck.ui.search.providers.SearchProvider
import com.hermes.deck.ui.search.providers.WidgetSearchProvider
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val staticProviders: List<SearchProvider>,
    private val pluginRepository: PluginRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<SearchResult>>(emptyList())
    val results: StateFlow<List<SearchResult>> = _results.asStateFlow()

    private val pluginProviders = MutableStateFlow<List<SearchProvider>>(emptyList())

    init {
        // Track installed plugins; rebuilds provider list on package install/remove
        viewModelScope.launch {
            pluginRepository.pluginsFlow().collect { plugins ->
                pluginProviders.value = plugins.map { PluginSearchProvider(it, pluginRepository) }
            }
        }

        // Fan out to all providers (static + plugin) in parallel on each debounced query
        viewModelScope.launch {
            _query
                .debounce(200L)
                .collectLatest { q ->
                    if (q.isBlank()) {
                        _results.value = emptyList()
                        return@collectLatest
                    }
                    val allProviders = staticProviders + pluginProviders.value
                    val deferred = allProviders.map { provider ->
                        async { runCatching { provider.query(q) }.getOrElse { emptyList() } }
                    }
                    _results.value = deferred.awaitAll().flatten()
                }
        }
    }

    fun onQueryChange(q: String) { _query.value = q }
    fun clearQuery() { _query.value = "" }

    companion object {
        fun factory(context: Context) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val appCtx = context.applicationContext
                val staticProviders: List<SearchProvider> = listOf(
                    CalculatorProvider(),
                    AppSearchProvider(InstalledAppsRepository(appCtx), appCtx),
                    WidgetSearchProvider(appCtx, WidgetPinRepository(appCtx)),
                    ContactSearchProvider(appCtx),
                    AiProvider(appCtx)   // last: on-device Gemini Nano, silent no-op until model downloaded
                )
                @Suppress("UNCHECKED_CAST")
                return SearchViewModel(staticProviders, PluginRepository(appCtx)) as T
            }
        }
    }
}
