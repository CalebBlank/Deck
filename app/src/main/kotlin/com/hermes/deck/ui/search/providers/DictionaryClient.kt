package com.hermes.deck.ui.search.providers

import com.hermes.deck.ui.search.DictEntry
import com.hermes.deck.ui.search.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Free Dictionary API (dictionaryapi.dev) — English definitions, no key. 404 = no such word → null. */
object DictionaryClient {
    private const val BASE = "https://api.dictionaryapi.dev/api/v2/entries/en/"

    suspend fun lookup(word: String): SearchResult.DictionaryResult? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(BASE + URLEncoder.encode(word, "UTF-8")).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000; readTimeout = 10000
            }
            if (conn.responseCode != 200) { conn.disconnect(); return@runCatching null }
            val text = conn.inputStream.bufferedReader().use { it.readText() }.also { conn.disconnect() }
            val arr = JSONArray(text)
            val first = arr.optJSONObject(0) ?: return@runCatching null
            val headword = first.optString("word").ifBlank { word }
            // phonetic may be on the entry or in a phonetics[] item
            val phonetic = first.optString("phonetic").takeIf { it.isNotBlank() }
                ?: (0 until (first.optJSONArray("phonetics")?.length() ?: 0))
                    .firstNotNullOfOrNull { i -> first.optJSONArray("phonetics")?.optJSONObject(i)?.optString("text")?.takeIf { it.isNotBlank() } }
            val meanings = first.optJSONArray("meanings") ?: return@runCatching null
            val entries = (0 until meanings.length()).mapNotNull { i ->
                val m = meanings.optJSONObject(i) ?: return@mapNotNull null
                val pos = m.optString("partOfSpeech").ifBlank { "" }
                val defs = m.optJSONArray("definitions") ?: return@mapNotNull null
                val defList = (0 until defs.length()).mapNotNull { j ->
                    defs.optJSONObject(j)?.optString("definition")?.takeIf { it.isNotBlank() }
                }.take(2)   // a couple per part of speech keeps the card compact
                if (defList.isEmpty()) null else DictEntry(pos, defList)
            }.take(3)
            if (entries.isEmpty()) null
            else SearchResult.DictionaryResult(headword, phonetic, entries)
        }.getOrNull()
    }
}
