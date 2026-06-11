package com.hermes.deck.data

import android.content.Context

class WidgetPinRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE)

    fun getPinnedWidget(packageName: String): String? =
        prefs.getString("pinned_widget_$packageName", null)

    fun getPinnedWidgetId(packageName: String): Int? {
        val id = prefs.getInt("pinned_widget_id_$packageName", -1)
        return if (id == -1) null else id
    }

    fun pinWidget(packageName: String, componentName: String) {
        prefs.edit().putString("pinned_widget_$packageName", componentName).apply()
    }

    fun pinWidget(packageName: String, componentName: String, appWidgetId: Int) {
        prefs.edit()
            .putString("pinned_widget_$packageName", componentName)
            .putInt("pinned_widget_id_$packageName", appWidgetId)
            .apply()
    }

    fun unpinWidget(packageName: String) {
        prefs.edit().remove("pinned_widget_$packageName").apply()
    }

    fun getAllPinnedWidgets(): Map<String, String> {
        return prefs.all
            .filter { (key, value) -> key.startsWith("pinned_widget_") && value is String }
            .mapKeys { (key, _) -> key.removePrefix("pinned_widget_") }
            .mapValues { (_, value) -> value as? String ?: "" }
            .filter { (_, v) -> v.isNotEmpty() }
    }

    fun getCustomHeight(packageName: String): Int? {
        val h = prefs.getInt("widget_height_$packageName", -1)
        return if (h == -1) null else h
    }

    fun setCustomHeight(packageName: String, heightDp: Int) {
        prefs.edit().putInt("widget_height_$packageName", heightDp).apply()
    }

    fun getBackgroundStyle(packageName: String): String =
        prefs.getString("widget_bg_$packageName", "default") ?: "default"

    fun setBackgroundStyle(packageName: String, style: String) {
        prefs.edit().putString("widget_bg_$packageName", style).apply()
    }

    // ---- Component-keyed storage (one entry per widget provider) ----

    private fun compKey(comp: String) = comp.replace('/', '_').replace('.', '_')

    fun isPinnedByComponent(comp: String): Boolean =
        prefs.getBoolean("wc_pinned_${compKey(comp)}", false)

    fun getPinnedWidgetIdByComponent(comp: String): Int? {
        val id = prefs.getInt("wc_id_${compKey(comp)}", -1)
        return if (id == -1) null else id
    }

    fun pinWidgetByComponent(comp: String, appWidgetId: Int) {
        prefs.edit()
            .putBoolean("wc_pinned_${compKey(comp)}", true)
            .putInt("wc_id_${compKey(comp)}", appWidgetId)
            .apply()
    }

    fun unpinWidgetByComponent(comp: String) {
        val key = compKey(comp)
        prefs.edit().remove("wc_pinned_$key").remove("wc_id_$key").apply()
    }

    fun getCustomHeightByComponent(comp: String): Int? {
        val h = prefs.getInt("wc_h_${compKey(comp)}", -1)
        return if (h == -1) null else h
    }

    fun setCustomHeightByComponent(comp: String, heightDp: Int) {
        prefs.edit().putInt("wc_h_${compKey(comp)}", heightDp).apply()
    }

    fun getBackgroundStyleByComponent(comp: String): String =
        prefs.getString("wc_bg_${compKey(comp)}", "default") ?: "default"

    fun setBackgroundStyleByComponent(comp: String, style: String) {
        prefs.edit().putString("wc_bg_${compKey(comp)}", style).apply()
    }
}
