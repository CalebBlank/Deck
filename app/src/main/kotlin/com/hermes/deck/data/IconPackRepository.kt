package com.hermes.deck.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

data class IconPackInfo(val packageName: String, val label: String, val icon: Drawable)

class IconPackRepository(private val context: Context) {

    fun getInstalledPacks(): List<IconPackInfo> {
        val pm = context.packageManager
        val actions = listOf(
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.teslacoilsw.launcher.THEME",
            "com.gau.go.launcherex.theme",
            "net.oneplus.launcher.icons.ACTION_PICK_ICON",
        )
        return actions
            .flatMap { action -> pm.queryIntentActivities(Intent(action), PackageManager.MATCH_ALL) }
            .distinctBy { it.activityInfo.packageName }
            .mapNotNull { ri ->
                runCatching {
                    IconPackInfo(
                        packageName = ri.activityInfo.packageName,
                        label = ri.loadLabel(pm).toString(),
                        icon = ri.loadIcon(pm)
                    )
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
    }

    // Returns component key -> drawable name AND package -> drawable name (fallback)
    suspend fun loadMappings(packPackageName: String): Map<String, String> = withContext(Dispatchers.IO) {
        val mappings = mutableMapOf<String, String>()
        runCatching {
            val packCtx = context.createPackageContext(packPackageName, 0)
            val resId = packCtx.resources.getIdentifier("appfilter", "xml", packPackageName)
            if (resId == 0) return@runCatching
            val parser = packCtx.resources.getXml(resId)
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                if (parser.name != "item") continue
                val component = parser.getAttributeValue(null, "component") ?: continue
                val drawable = parser.getAttributeValue(null, "drawable") ?: continue
                if (drawable.isBlank()) continue
                val inner = component.removePrefix("ComponentInfo{").removeSuffix("}")
                if (!inner.contains("/")) continue
                val pkg = inner.substringBefore("/")
                mappings[inner] = drawable
                if (!mappings.containsKey(pkg)) mappings[pkg] = drawable
            }
        }
        mappings
    }

    fun getIcon(packPackageName: String, mappings: Map<String, String>,
                packageName: String, activityName: String): Drawable? {
        val componentKey = "$packageName/$activityName"
        val drawableName = mappings[componentKey] ?: mappings[packageName] ?: return null
        return getIconByDrawableName(packPackageName, drawableName)
    }

    fun getIconByDrawableName(packPackageName: String, drawableName: String): Drawable? =
        runCatching {
            val packCtx = context.createPackageContext(packPackageName, 0)
            val resId = packCtx.resources.getIdentifier(drawableName, "drawable", packPackageName)
            if (resId == 0) null else packCtx.resources.getDrawable(resId, packCtx.theme)
        }.getOrNull()

    suspend fun loadAllIconDrawableNames(packPackageName: String): List<String> = withContext(Dispatchers.IO) {
        val names = mutableListOf<String>()
        runCatching {
            val packCtx = context.createPackageContext(packPackageName, 0)
            val resId = packCtx.resources.getIdentifier("appfilter", "xml", packPackageName)
            if (resId == 0) return@runCatching
            val parser = packCtx.resources.getXml(resId)
            val seen = mutableSetOf<String>()
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                if (parser.name != "item") continue
                val drawable = parser.getAttributeValue(null, "drawable") ?: continue
                if (drawable.isBlank() || !seen.add(drawable)) continue
                names.add(drawable)
            }
        }
        names
    }
}
