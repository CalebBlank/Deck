package com.hermes.deck.ui.search.providers

import android.content.Context
import android.provider.ContactsContract
import com.hermes.deck.ui.search.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContactSearchProvider(private val context: Context) : SearchProvider {

    override val id = "contacts"

    override suspend fun query(q: String): List<SearchResult> {
        if (q.isBlank() || q.length < 2) return emptyList()
        val out = withContext(Dispatchers.IO) { queryContacts(q) }
        // Confident → rich card: a single match, or one whose name exactly matches the query.
        val exacts = out.filter { it.name.equals(q.trim(), ignoreCase = true) }
        return when {
            out.size == 1    -> listOf(out[0].copy(rich = true))
            exacts.size == 1 -> listOf(exacts[0].copy(rich = true))
            else             -> out
        }
    }

    private fun queryContacts(q: String): List<SearchResult.ContactResult> {
        val results = mutableListOf<SearchResult.ContactResult>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$q%")

        runCatching {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)
                ?.use { cursor ->
                    val nameCol  = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numCol   = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val photoCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                    val seen = mutableSetOf<String>()
                    while (cursor.moveToNext() && results.size < 4) {
                        val name = cursor.getString(nameCol) ?: continue
                        if (!seen.add(name)) continue
                        results.add(SearchResult.ContactResult(
                            name        = name,
                            phoneNumber = cursor.getString(numCol),
                            email       = null,
                            photoUri    = cursor.getString(photoCol)
                        ))
                    }
                }
        }
        return results
    }
}
