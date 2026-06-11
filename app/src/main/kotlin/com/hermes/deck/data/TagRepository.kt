package com.hermes.deck.data

import android.content.Context

class TagRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE)

    fun getTags(packageName: String): Set<String> =
        prefs.getStringSet("tags_$packageName", emptySet())?.toSet() ?: emptySet()

    fun setTags(packageName: String, tags: Set<String>) {
        prefs.edit().putStringSet("tags_$packageName", tags.toHashSet()).apply()
    }
}
