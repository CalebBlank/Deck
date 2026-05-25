package com.hermes.deck.ui.search.providers

import android.appwidget.AppWidgetManager
import android.content.Context
import com.hermes.deck.data.WidgetPinRepository
import com.hermes.deck.ui.search.SearchResult
import com.hermes.deck.ui.search.WidgetProviderInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WidgetSearchProvider(
    private val context: Context,
    private val pinRepo: WidgetPinRepository
) : SearchProvider {

    override val id = "widgets"

    override suspend fun query(q: String): List<SearchResult> = withContext(Dispatchers.IO) {
        if (q.isBlank()) return@withContext emptyList()
        val hidden = context.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE)
            .getString("hidden_apps", "").orEmpty()
            .split(",").filter { it.isNotBlank() }.toSet()
        val manager = AppWidgetManager.getInstance(context)
        val pm = context.packageManager

        runCatching {
            manager.getInstalledProviders()
                .filter { it.provider.packageName !in hidden }
                .groupBy { it.provider.packageName }
                .mapNotNull { (pkg, infos) ->
                    // Only show apps the user has explicitly assigned a widget to
                    val pinnedComp = pinRepo.getPinnedWidget(pkg) ?: return@mapNotNull null
                    val appLabel = runCatching {
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                    }.getOrDefault(pkg)
                    if (!appLabel.contains(q, ignoreCase = true) &&
                        !pkg.contains(q, ignoreCase = true)) return@mapNotNull null
                    val info = infos.find { it.provider.flattenToString() == pinnedComp }
                        ?: return@mapNotNull null
                    SearchResult.WidgetPickerResult(
                        appPackage          = pkg,
                        appLabel            = appLabel,
                        providers           = listOf(
                            WidgetProviderInfo(
                                componentName = info.provider.flattenToString(),
                                label         = runCatching { info.loadLabel(pm) }.getOrDefault("Widget"),
                                packageName   = pkg,
                                previewResId  = info.previewImage,
                                iconResId     = info.icon
                            )
                        ),
                        pinnedComponentName = pinnedComp
                    )
                }
        }.getOrElse { emptyList() }
    }
}
