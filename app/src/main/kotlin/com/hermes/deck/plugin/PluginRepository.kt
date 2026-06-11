package com.hermes.deck.plugin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import com.hermes.deck.ui.search.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

data class PluginInfo(
    val id: String,
    val name: String,
    val authority: String
)

class PluginRepository(private val context: Context) {

    /** Hot-ish flow: emits current plugin list immediately, then re-emits on package install/remove. */
    fun pluginsFlow(): Flow<List<PluginInfo>> = callbackFlow {
        trySend(discoverPlugins())

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                trySend(discoverPlugins())
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        context.registerReceiver(receiver, filter)
        awaitClose { context.unregisterReceiver(receiver) }
    }

    /** Scans installed packages for providers whose authority starts with AUTHORITY_PREFIX. */
    fun discoverPlugins(): List<PluginInfo> {
        val packages = context.packageManager
            .getInstalledPackages(PackageManager.GET_PROVIDERS or PackageManager.GET_META_DATA)
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
                        val iType   = cursor.getColumnIndex(PluginContract.COL_RESULT_TYPE)
                        if (iTitle < 0) return@use emptyList<SearchResult.PluginResult>()
                        while (cursor.moveToNext()) {
                            add(SearchResult.PluginResult(
                                pluginId   = plugin.id,
                                pluginName = plugin.name,
                                title      = cursor.getString(iTitle) ?: continue,
                                subtitle   = if (iSub >= 0) cursor.getString(iSub) else null,
                                iconUri    = if (iIcon >= 0) cursor.getString(iIcon) else null,
                                actionUri  = if (iAction >= 0) cursor.getString(iAction) else null,
                                resultType = if (iType >= 0) cursor.getString(iType) else null
                            ))
                        }
                    }
                } ?: emptyList()
            }.getOrElse { emptyList() }
        }
}
