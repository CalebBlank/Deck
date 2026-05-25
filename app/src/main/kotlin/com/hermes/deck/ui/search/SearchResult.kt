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

    data class AiResult(
        val query: String,
        val answer: String
    ) : SearchResult()

    data class WidgetPickerResult(
        val appPackage: String,
        val appLabel: String,
        val providers: List<WidgetProviderInfo>,
        val pinnedComponentName: String?
    ) : SearchResult()
}

data class WidgetProviderInfo(
    val componentName: String,
    val label: String,
    val packageName: String,
    val previewResId: Int,
    val iconResId: Int
)
