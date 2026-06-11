package com.hermes.deck.ui.search.providers

import com.hermes.deck.ui.search.SearchResult
import kotlin.math.abs

/**
 * Unit conversion "answer" provider — fires only on a "<number> <unit> to <unit>" query (e.g.
 * "5 km to miles", "100f to c", "2 cups in ml"), returning one answer card. Pure-local (no network).
 */
class UnitConversionProvider : SearchProvider {
    override val id = "unit"

    private val regex = Regex(
        """^\s*(-?\d+(?:[.,]\d+)?)\s*([a-zµ°"'/]+)\s+(?:to|in|=|->|>)\s+([a-zµ°"'/]+)\s*$""",
        RegexOption.IGNORE_CASE
    )

    override suspend fun query(q: String): List<SearchResult> {
        val m = regex.find(q.trim()) ?: return emptyList()
        val (numStr, fromRaw, toRaw) = m.destructured
        val num = numStr.replace(',', '.').toDoubleOrNull() ?: return emptyList()
        val from = UnitConverter.canonical(fromRaw) ?: return emptyList()
        val to = UnitConverter.canonical(toRaw) ?: return emptyList()
        val out = UnitConverter.convert(num, from, to) ?: return emptyList()
        return listOf(
            SearchResult.AnswerResult(
                providerId = "unit",
                value = "${fmt(out)} ${UnitConverter.label(to)}",
                detail = "${fmt(num)} ${UnitConverter.label(from)}",
                copyText = fmt(out)
            )
        )
    }

    private fun fmt(v: Double): String {
        if (v == v.toLong().toDouble()) return v.toLong().toString()
        // up to 4 sig-ish decimals, trimmed
        val s = if (abs(v) >= 100) "%.2f".format(v) else "%.4f".format(v)
        return s.trimEnd('0').trimEnd('.')
    }
}

/** Unit table + conversion. Factor = how many BASE units one of this unit is; temperature is special. */
object UnitConverter {
    private data class U(val dim: String, val factor: Double, val label: String)

    private val units = HashMap<String, U>()
    private fun reg(dim: String, factor: Double, label: String, vararg aliases: String) {
        aliases.forEach { units[it] = U(dim, factor, label) }
    }

    init {
        // length (base: meter)
        reg("len", 1.0, "m", "m", "meter", "meters", "metre", "metres")
        reg("len", 1000.0, "km", "km", "kilometer", "kilometers", "kilometre")
        reg("len", 0.01, "cm", "cm", "centimeter", "centimeters")
        reg("len", 0.001, "mm", "mm", "millimeter", "millimeters")
        reg("len", 1609.344, "mi", "mi", "mile", "miles")
        reg("len", 0.9144, "yd", "yd", "yard", "yards")
        reg("len", 0.3048, "ft", "ft", "foot", "feet", "'")
        reg("len", 0.0254, "in", "in", "inch", "inches", "\"")
        reg("len", 1852.0, "nmi", "nmi", "nauticalmile", "nauticalmiles")
        // mass (base: gram)
        reg("mass", 1.0, "g", "g", "gram", "grams")
        reg("mass", 1000.0, "kg", "kg", "kilogram", "kilograms", "kilo", "kilos")
        reg("mass", 0.001, "mg", "mg", "milligram", "milligrams")
        reg("mass", 453.59237, "lb", "lb", "lbs", "pound", "pounds")
        reg("mass", 28.349523, "oz", "oz", "ounce", "ounces")
        reg("mass", 6350.29318, "st", "st", "stone", "stones")
        reg("mass", 1_000_000.0, "t", "t", "tonne", "tonnes", "metricton")
        // volume (base: liter)
        reg("vol", 1.0, "L", "l", "liter", "liters", "litre", "litres")
        reg("vol", 0.001, "mL", "ml", "milliliter", "milliliters", "millilitre")
        reg("vol", 3.785411784, "gal", "gal", "gallon", "gallons")
        reg("vol", 0.946352946, "qt", "qt", "quart", "quarts")
        reg("vol", 0.473176473, "pt", "pt", "pint", "pints")
        reg("vol", 0.2365882365, "cup", "cup", "cups")
        reg("vol", 0.0295735296, "fl oz", "floz", "fluidounce", "fluidounces")
        reg("vol", 0.0147867648, "tbsp", "tbsp", "tablespoon", "tablespoons")
        reg("vol", 0.0049289216, "tsp", "tsp", "teaspoon", "teaspoons")
        // speed (base: m/s)
        reg("spd", 1.0, "m/s", "m/s", "mps", "meterspersecond")
        reg("spd", 0.277777778, "km/h", "km/h", "kmh", "kph", "kmph")
        reg("spd", 0.44704, "mph", "mph", "mileperhour", "milesperhour")
        reg("spd", 0.514444444, "kn", "kn", "knot", "knots")
        // data (base: byte, decimal)
        reg("data", 1.0, "B", "b", "byte", "bytes")
        reg("data", 1e3, "KB", "kb", "kilobyte", "kilobytes")
        reg("data", 1e6, "MB", "mb", "megabyte", "megabytes")
        reg("data", 1e9, "GB", "gb", "gigabyte", "gigabytes")
        reg("data", 1e12, "TB", "tb", "terabyte", "terabytes")
        reg("data", 1024.0, "KiB", "kib")
        reg("data", 1048576.0, "MiB", "mib")
        reg("data", 1073741824.0, "GiB", "gib")
        // time/duration (base: second)
        reg("time", 1.0, "s", "s", "sec", "secs", "second", "seconds")
        reg("time", 60.0, "min", "min", "mins", "minute", "minutes")
        reg("time", 3600.0, "hr", "h", "hr", "hrs", "hour", "hours")
        reg("time", 86400.0, "day", "day", "days")
        reg("time", 604800.0, "week", "week", "weeks")
        // temperature (handled specially; label only, factor unused)
        reg("temp", 0.0, "°C", "c", "celsius", "centigrade", "°c")
        reg("temp", 0.0, "°F", "f", "fahrenheit", "°f")
        reg("temp", 0.0, "K", "k", "kelvin")
    }

    fun canonical(alias: String): String? {
        val a = alias.lowercase().trim()
        return if (units.containsKey(a)) a else null
    }

    fun label(alias: String): String = units[alias.lowercase()]?.label ?: alias

    fun convert(value: Double, fromAlias: String, toAlias: String): Double? {
        val from = units[fromAlias.lowercase()] ?: return null
        val to = units[toAlias.lowercase()] ?: return null
        if (from.dim != to.dim) return null
        if (from.dim == "temp") return convertTemp(value, fromAlias.lowercase(), toAlias.lowercase())
        return value * from.factor / to.factor
    }

    private fun convertTemp(v: Double, from: String, to: String): Double {
        val c = when {
            from in setOf("f", "fahrenheit", "°f") -> (v - 32) * 5.0 / 9.0
            from in setOf("k", "kelvin")           -> v - 273.15
            else                                   -> v // celsius
        }
        return when {
            to in setOf("f", "fahrenheit", "°f") -> c * 9.0 / 5.0 + 32
            to in setOf("k", "kelvin")           -> c + 273.15
            else                                 -> c
        }
    }
}
