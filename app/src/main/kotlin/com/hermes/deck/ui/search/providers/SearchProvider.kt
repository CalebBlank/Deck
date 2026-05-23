package com.hermes.deck.ui.search.providers

import com.hermes.deck.ui.search.SearchResult

interface SearchProvider {
    val id: String
    suspend fun query(q: String): List<SearchResult>
}
