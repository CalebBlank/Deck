package com.hermes.deck

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/**
 * Read-only provider that exposes the user's Claude credentials (api key + model, from deck_prefs)
 * to signature-matched sibling apps — specifically the Hermes Browser's custom-CSS AI feature, so it
 * reuses Deck's key instead of asking for a second one. Gated by a signature permission, so only an
 * app signed with the same key (the browser) can read it. Single row, columns: claude_api_key,
 * claude_model.
 */
class SettingsProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val prefs = context!!.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE)
        val key = prefs.getString("claude_api_key", "").orEmpty()
        val model = prefs.getString("claude_model", "claude-opus-4-8").orEmpty()
        return MatrixCursor(arrayOf("claude_api_key", "claude_model")).apply {
            addRow(arrayOf<Any?>(key, model))
        }
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.com.hermes.deck.settings"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
