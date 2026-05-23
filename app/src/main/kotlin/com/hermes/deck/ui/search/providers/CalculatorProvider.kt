package com.hermes.deck.ui.search.providers

import com.hermes.deck.ui.search.SearchResult

class CalculatorProvider : SearchProvider {

    override val id = "calculator"

    // Matches expressions like "3 + 4 * 2" or "100/5"
    private val exprRegex = Regex("""^\s*-?\d+(\.\d+)?\s*([+\-*/]\s*\d+(\.\d+)?\s*)+$""")

    override suspend fun query(q: String): List<SearchResult> {
        val clean = q.trim()
        if (!exprRegex.matches(clean)) return emptyList()
        val result = evalLeftToRight(clean) ?: return emptyList()
        val formatted = if (result == result.toLong().toDouble()) result.toLong().toString()
                        else "%.6g".format(result)
        return listOf(SearchResult.CalculatorResult(expression = clean, result = formatted))
    }

    /** Simple left-to-right evaluator (no operator precedence). */
    private fun evalLeftToRight(expr: String): Double? = runCatching {
        val tokens = expr.replace(" ", "")
            .split(Regex("(?<=[0-9.])(?=[+\\-*/])|(?<=[+\\-*/])(?=[0-9.\\-])"))
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        var acc = tokens[0].toDouble()
        var i = 1
        while (i < tokens.size - 1) {
            val op  = tokens[i]
            val rhs = tokens[i + 1].toDouble()
            acc = when (op) {
                "+" -> acc + rhs
                "-" -> acc - rhs
                "*" -> acc * rhs
                "/" -> if (rhs == 0.0) return null else acc / rhs
                else -> return null
            }
            i += 2
        }
        acc
    }.getOrNull()
}
