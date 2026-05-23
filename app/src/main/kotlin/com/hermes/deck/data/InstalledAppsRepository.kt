package com.hermes.deck.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class InstalledAppsRepository(private val context: Context) {

    private val packageManager = context.packageManager

    fun getAllApps(): Flow<List<AppInfo>> = flow {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { ri ->
                AppInfo(
                    packageName = ri.activityInfo.packageName,
                    label       = ri.loadLabel(packageManager).toString(),
                    icon        = ri.loadIcon(packageManager)
                )
            }
            .sortedBy { it.label.lowercase() }
        emit(apps)
    }.flowOn(Dispatchers.IO)

    fun getLaunchIntent(packageName: String): Intent? =
        packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
