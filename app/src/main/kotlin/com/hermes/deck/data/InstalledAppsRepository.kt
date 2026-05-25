package com.hermes.deck.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InstalledAppsRepository(private val context: Context) {

    private val packageManager = context.packageManager

    fun getAllApps(): Flow<List<AppInfo>> = callbackFlow {
        suspend fun query(): List<AppInfo> = withContext(Dispatchers.IO) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            packageManager
                .queryIntentActivities(intent, PackageManager.MATCH_ALL)
                .map { ri ->
                    AppInfo(
                        packageName = ri.activityInfo.packageName,
                        label       = ri.loadLabel(packageManager).toString(),
                        icon        = ri.loadIcon(packageManager),
                        category    = ri.activityInfo.applicationInfo.category.toCategoryLabel()
                    )
                }
                .sortedBy { it.label.lowercase() }
        }

        trySend(query())

        val scope = this
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                scope.launch { scope.trySend(query()) }
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

    fun getLaunchIntent(packageName: String): Intent? =
        packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

private fun Int.toCategoryLabel(): String = when (this) {
    ApplicationInfo.CATEGORY_GAME          -> "Game"
    ApplicationInfo.CATEGORY_AUDIO         -> "Audio"
    ApplicationInfo.CATEGORY_VIDEO         -> "Video"
    ApplicationInfo.CATEGORY_IMAGE         -> "Image"
    ApplicationInfo.CATEGORY_SOCIAL        -> "Social"
    ApplicationInfo.CATEGORY_NEWS          -> "News"
    ApplicationInfo.CATEGORY_MAPS          -> "Maps"
    ApplicationInfo.CATEGORY_PRODUCTIVITY  -> "Productivity"
    ApplicationInfo.CATEGORY_ACCESSIBILITY -> "Accessibility"
    else -> ""
}
