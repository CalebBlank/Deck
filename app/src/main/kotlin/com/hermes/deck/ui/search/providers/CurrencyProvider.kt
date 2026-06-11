package com.hermes.deck.ui.search.providers

import com.hermes.deck.ui.search.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

/**
 * Currency conversion "answer" provider — fires on "<number> <cur> to <cur>" (e.g. "100 usd to eur",
 * "50 dollars in pounds"). Free ECB daily rates via frankfurter.app, no key. ~30 major currencies.
 */
class CurrencyProvider : SearchProvider {
    override val id = "currency"

    private val regex = Regex(
        """^\s*(-?\d+(?:[.,]\d+)?)\s*([a-zA-Z$€£¥]{1,12})\s+(?:to|in|=|->)\s+([a-zA-Z$€£¥]{1,12})\s*$""",
        RegexOption.IGNORE_CASE
    )

    override suspend fun query(q: String): List<SearchResult> {
        val m = regex.find(q.trim()) ?: return emptyList()
        val (numStr, fromRaw, toRaw) = m.destructured
        val amount = numStr.replace(',', '.').toDoubleOrNull() ?: return emptyList()
        val from = code(fromRaw) ?: return emptyList()
        val to = code(toRaw) ?: return emptyList()
        if (from == to) return emptyList()
        val converted = convert(amount, from, to) ?: return emptyList()
        return listOf(
            SearchResult.AnswerResult(
                providerId = "currency",
                value = "${fmt(converted)} $to",
                detail = "${fmt(amount)} $from · ECB daily rate",
                copyText = fmt(converted)
            )
        )
    }

    private suspend fun convert(amount: Double, from: String, to: String): Double? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.frankfurter.app/latest?amount=$amount&from=$from&to=$to"
            val conn = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 10000; readTimeout = 10000 }
            if (conn.responseCode != 200) { conn.disconnect(); return@runCatching null }
            val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            conn.disconnect()
            o.optJSONObject("rates")?.let { if (it.has(to)) it.optDouble(to).takeIf { d -> !d.isNaN() } else null }
        }.getOrNull()
    }

    private fun fmt(v: Double): String = if (abs(v) < 1) "%.4f".format(v).trimEnd('0').trimEnd('.') else "%,.2f".format(v)

    private val codes = setOf("AUD","BGN","BRL","CAD","CHF","CNY","CZK","DKK","EUR","GBP","HKD","HUF",
        "IDR","ILS","INR","ISK","JPY","KRW","MXN","MYR","NOK","NZD","PHP","PLN","RON","SEK","SGD","THB","TRY","USD","ZAR")

    private val aliases = mapOf(
        "$" to "USD", "dollar" to "USD", "dollars" to "USD", "usd" to "USD", "us" to "USD",
        "€" to "EUR", "euro" to "EUR", "euros" to "EUR",
        "£" to "GBP", "pound" to "GBP", "pounds" to "GBP", "quid" to "GBP", "sterling" to "GBP",
        "¥" to "JPY", "yen" to "JPY",
        "yuan" to "CNY", "rmb" to "CNY", "renminbi" to "CNY",
        "rupee" to "INR", "rupees" to "INR",
        "won" to "KRW", "peso" to "MXN", "pesos" to "MXN", "real" to "BRL", "reais" to "BRL",
        "franc" to "CHF", "francs" to "CHF", "rand" to "ZAR", "lira" to "TRY",
        "aud" to "AUD", "cad" to "CAD", "chf" to "CHF", "cny" to "CNY", "nzd" to "NZD",
        "sek" to "SEK", "nok" to "NOK", "dkk" to "DKK", "sgd" to "SGD", "hkd" to "HKD",
    )

    private fun code(raw: String): String? {
        val r = raw.trim().lowercase()
        aliases[r]?.let { return it }
        val up = raw.trim().uppercase()
        return if (up in codes) up else null
    }
}
