package com.hermes.deck.ui.search.providers

import android.appwidget.AppWidgetManager
import android.content.Context
import com.hermes.deck.data.TagRepository
import com.hermes.deck.data.WidgetPinRepository
import com.hermes.deck.ui.search.SearchResult
import com.hermes.deck.ui.search.WidgetProviderInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WidgetSearchProvider(
    private val context: Context,
    private val pinRepo: WidgetPinRepository,
    private val tagRepo: TagRepository
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
                .filter { pinRepo.isPinnedByComponent(it.provider.flattenToString()) }
                .mapNotNull { info ->
                    val pkg  = info.provider.packageName
                    val comp = info.provider.flattenToString()
                    val widgetLabel = runCatching { info.loadLabel(pm) }.getOrDefault("")
                    val appLabel    = runCatching {
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                    }.getOrDefault(pkg)
                    val tags = tagRepo.getTags(comp)   // tags keyed by component name
                    val displayLabel = widgetLabel.ifEmpty { appLabel }

                    if (!displayLabel.contains(q, ignoreCase = true) &&
                        !appLabel.contains(q, ignoreCase = true) &&
                        !comp.contains(q, ignoreCase = true) &&
                        tags.none { it.contains(q, ignoreCase = true) }
                    ) return@mapNotNull null

                    SearchResult.WidgetPickerResult(
                        appPackage          = pkg,
                        appLabel            = displayLabel,
                        providers           = listOf(WidgetProviderInfo(
                            componentName = comp,
                            label         = widgetLabel,
                            packageName   = pkg,
                            previewResId  = info.previewImage,
                            iconResId     = info.icon,
                            minHeightDp   = info.minHeight.coerceAtLeast(80)
                        )),
                        pinnedComponentName = comp,
                        appWidgetId         = pinRepo.getPinnedWidgetIdByComponent(comp),
                        customHeightDp      = pinRepo.getCustomHeightByComponent(comp),
                        backgroundStyle     = pinRepo.getBackgroundStyleByComponent(comp)
                    )
                }
        }.getOrElse { emptyList() }
    }
}
