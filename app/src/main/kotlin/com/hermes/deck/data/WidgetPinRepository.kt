package com.hermes.deck.data

import android.content.Context

class WidgetPinRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE)

    fun getPinnedWidget(packageName: String): String? =
        prefs.getString("pinned_widget_$packageName", null)

    fun pinWidget(packageName: String, componentName: String) {
        prefs.edit().putString("pinned_widget_$packageName", componentName).apply()
    }

    fun unpinWidget(packageName: String) {
        prefs.edit().remove("pinned_widget_$packageName").apply()
    }
}
