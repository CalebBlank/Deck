package com.hermes.deck.ui.search.providers

import com.hermes.deck.ui.search.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Translation "answer" provider via the free MyMemory API (no key). Patterns:
 *   "translate hello to spanish"  ·  "how do you say good morning in french"  ·  "thank you in german"
 * v1 translates FROM English TO the named language (source detection isn't in the free API).
 */
class TranslationProvider : SearchProvider {
    override val id = "translation"

    private val patterns = listOf(
        Regex("""^translate (.+?) (?:to|into|in) ([a-z ]+)$""", RegexOption.IGNORE_CASE),
        Regex("""^(?:how do (?:you|i) say|say) (.+?) in ([a-z ]+)$""", RegexOption.IGNORE_CASE),
        Regex("""^(.+?) in ([a-z ]+)$""", RegexOption.IGNORE_CASE),
    )

    override suspend fun query(q: String): List<SearchResult> {
        val s = q.trim()
        if (s.length < 4) return emptyList()
        var text: String? = null; var langName: String? = null
        for (p in patterns) {
            val m = p.find(s) ?: continue
            val cand = m.groupValues[2].trim().lowercase(Locale.US)
            if (cand in langs) { text = m.groupValues[1].trim(); langName = cand; break }
        }
        val phrase = text?.takeIf { it.isNotBlank() } ?: return emptyList()
        val tgt = langs[langName] ?: return emptyList()
        if (tgt == "en") return emptyList()   // source detection unsupported; only EN → other
        val translated = translate(phrase, tgt) ?: return emptyList()
        return listOf(
            SearchResult.AnswerResult(
                providerId = "translation",
                value = translated,
                detail = "$phrase → ${langName!!.replaceFirstChar { it.uppercase() }}",
                copyText = translated
            )
        )
    }

    private suspend fun translate(text: String, tgt: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.mymemory.translated.net/get?q=" +
                URLEncoder.encode(text, "UTF-8") + "&langpair=en|$tgt"
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000; readTimeout = 10000
                setRequestProperty("User-Agent", "DeckLauncher/1.0")
            }
            if (conn.responseCode != 200) { conn.disconnect(); return@runCatching null }
            val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            conn.disconnect()
            o.optJSONObject("responseData")?.optString("translatedText")?.trim()?.ifBlank { null }
        }.getOrNull()
    }

    private val langs = mapOf(
        "spanish" to "es", "es" to "es", "french" to "fr", "fr" to "fr", "german" to "de", "de" to "de",
        "italian" to "it", "it" to "it", "portuguese" to "pt", "pt" to "pt", "dutch" to "nl", "nl" to "nl",
        "russian" to "ru", "ru" to "ru", "japanese" to "ja", "ja" to "ja", "chinese" to "zh", "zh" to "zh",
        "korean" to "ko", "ko" to "ko", "arabic" to "ar", "ar" to "ar", "hindi" to "hi", "hi" to "hi",
        "polish" to "pl", "turkish" to "tr", "swedish" to "sv", "norwegian" to "no", "danish" to "da",
        "finnish" to "fi", "greek" to "el", "hebrew" to "he", "thai" to "th", "vietnamese" to "vi",
        "indonesian" to "id", "czech" to "cs", "ukrainian" to "uk", "romanian" to "ro", "hungarian" to "hu",
        "english" to "en",
    )
}
