package com.hermes.deck.ui.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hermes.deck.data.InstalledAppsRepository
import com.hermes.deck.plugin.PluginRepository
import com.hermes.deck.ui.search.providers.AppSearchProvider
import com.hermes.deck.ui.search.providers.CalculatorProvider
import com.hermes.deck.ui.search.providers.ContactSearchProvider
import com.hermes.deck.ui.search.providers.SearchProvider
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val providers: List<SearchProvider>
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<SearchResult>>(emptyList())
    val results: StateFlow<List<SearchResult>> = _results.asStateFlow()

    init {
        viewModelScope.launch {
            _query
                .debounce(200L)
                .collectLatest { q ->
                    if (q.isBlank()) {
                        _results.value = emptyList()
                        return@collectLatest
                    }
                    // Fan out to all providers in parallel, merge results
                    val deferred = providers.map { provider ->
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
                val providers: List<SearchProvider> = listOf(
                    CalculatorProvider(),
                    AppSearchProvider(InstalledAppsRepository(appCtx)),
                    ContactSearchProvider(appCtx)
                    // PluginProviders are discovered and added at runtime via PluginRepository
                )
                @Suppress("UNCHECKED_CAST")
                return SearchViewModel(providers) as T
            }
        }
    }
}
