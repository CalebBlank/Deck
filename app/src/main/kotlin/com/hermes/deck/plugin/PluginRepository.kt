package com.hermes.deck.plugin

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.hermes.deck.ui.search.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PluginInfo(
    val id: String,
    val name: String,
    val authority: String
)

class PluginRepository(private val context: Context) {

    /** Scans installed packages for providers whose authority starts with AUTHORITY_PREFIX. */
    fun discoverPlugins(): List<PluginInfo> {
        val packages = context.packageManager.getInstalledPackages(PackageManager.GET_PROVIDERS)
        return packages
            .flatMap { pkg -> pkg.providers?.toList() ?: emptyList() }
            .filter { provider ->
                provider.authority?.startsWith(PluginContract.AUTHORITY_PREFIX) == true
            }
            .map { provider ->
                PluginInfo(
                    id        = provider.authority.removePrefix(PluginContract.AUTHORITY_PREFIX),
                    name      = provider.metaData
                        ?.getString(PluginContract.META_PLUGIN_NAME) ?: provider.name,
                    authority = provider.authority
                )
            }
    }

    suspend fun query(plugin: PluginInfo, q: String): List<SearchResult.PluginResult> =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(
                "content://${plugin.authority}/${PluginContract.PATH_SEARCH}" +
                "?${PluginContract.PARAM_QUERY}=${Uri.encode(q)}"
            )
            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    buildList {
                        val iTitle  = cursor.getColumnIndex(PluginContract.COL_TITLE)
                        val iSub    = cursor.getColumnIndex(PluginContract.COL_SUBTITLE)
                        val iIcon   = cursor.getColumnIndex(PluginContract.COL_ICON_URI)
                        val iAction = cursor.getColumnIndex(PluginContract.COL_ACTION_URI)
                        while (cursor.moveToNext()) {
                            add(SearchResult.PluginResult(
                                pluginId   = plugin.id,
                                pluginName = plugin.name,
                                title      = cursor.getString(iTitle) ?: continue,
                                subtitle   = if (iSub >= 0) cursor.getString(iSub) else null,
                                iconUri    = if (iIcon >= 0) cursor.getString(iIcon) else null,
                                actionUri  = if (iAction >= 0) cursor.getString(iAction) else null
                            ))
                        }
                    }
                } ?: emptyList()
            }.getOrElse { emptyList() }
        }
}
