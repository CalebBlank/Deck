package com.hermes.deck.ui.search

import com.hermes.deck.data.AppInfo

sealed class SearchResult {
    data class AppResult(val app: AppInfo) : SearchResult()

    data class ContactResult(
        val name: String,
        val phoneNumber: String?,
        val email: String?,
        val photoUri: String?
    ) : SearchResult()

    data class CalculatorResult(
        val expression: String,
        val result: String
    ) : SearchResult()

    data class PluginResult(
        val pluginId: String,
        val pluginName: String,
        val title: String,
        val subtitle: String?,
        val iconUri: String?,
        val actionUri: String?
    ) : SearchResult()
}
