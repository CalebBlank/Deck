package com.hermes.deck.ui.search.providers

import android.content.SharedPreferences
import com.hermes.deck.ui.search.SearchResult

class DialerProvider(private val prefs: SharedPreferences) : SearchProvider {
    override val id = "dialer"

    override suspend fun query(q: String): List<SearchResult> {
        val map = buildLetterToDigitMap()
        val digits = convertToDigits(q, map) ?: return emptyList()
        if (digits.length < 3) return emptyList()
        return listOf(SearchResult.DialerResult(phoneNumber = digits, displayText = q))
    }

    private fun buildLetterToDigitMap(): Map<Char, Char> {
        val keyMap = prefs.getString("number_key_map", "") ?: return emptyMap()
        if (keyMap.length < 10) return emptyMap()
        return keyMap.mapIndexed { i, c ->
            c.lowercaseChar() to if (i < 9) '1' + i else '0'
        }.toMap()
    }

    private fun convertToDigits(q: String, map: Map<Char, Char>): String? {
        val sb = StringBuilder()
        for (c in q) {
            when {
                c.isDigit()                       -> sb.append(c)
                c.lowercaseChar() in map          -> sb.append(map[c.lowercaseChar()])
                c in listOf(' ', '-', '(', ')', '+', '.') -> { /* skip formatting */ }
                else                              -> return null  // unmapped letter → not a phone query
            }
        }
        return sb.toString().takeIf { it.isNotEmpty() }
    }
}
