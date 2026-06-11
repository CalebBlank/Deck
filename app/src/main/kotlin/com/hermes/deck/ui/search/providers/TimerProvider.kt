package com.hermes.deck.ui.search.providers

import android.content.Context
import com.hermes.deck.ui.search.SearchResult

/**
 * Parses "timer"/"alarm" queries into a one-tap action card — no backend. "5 minute timer",
 * "timer for 1h30m", "set a timer 90 seconds", "alarm 7am", "alarm 6:30 pm". Tapping fires the
 * standard AlarmClock intent (set directly, no clock UI).
 */
class TimerProvider(private val context: Context) : SearchProvider {
    override val id = "timer"

    override suspend fun query(q: String): List<SearchResult> {
        val raw = q.trim().lowercase()
        if (raw.length < 4) return emptyList()

        // ALARM: needs the word "alarm" (or "wake me") + a clock time.
        if (Regex("\\b(alarm|wake me)\\b").containsMatchIn(raw)) {
            parseTime(raw)?.let { (h, m) ->
                return listOf(SearchResult.TimerResult("Set alarm for ${formatClock(h, m)}", isAlarm = true, hour = h, minute = m))
            }
        }

        // TIMER: needs the word "timer" + a duration.
        if (Regex("\\btimer\\b").containsMatchIn(raw)) {
            val secs = parseDuration(raw)
            if (secs in 1..86_400) {
                return listOf(SearchResult.TimerResult("Set a ${formatDuration(secs)} timer", isAlarm = false, seconds = secs))
            }
        }
        return emptyList()
    }

    /** Sum hours/minutes/seconds anywhere in the text; a bare number with no unit is taken as minutes. */
    private fun parseDuration(q: String): Int {
        var total = 0
        Regex("(\\d+)\\s*(hours|hour|hrs|hr|h)\\b").findAll(q).forEach { total += it.groupValues[1].toInt() * 3600 }
        Regex("(\\d+)\\s*(minutes|minute|mins|min|m)\\b").findAll(q).forEach { total += it.groupValues[1].toInt() * 60 }
        Regex("(\\d+)\\s*(seconds|second|secs|sec|s)\\b").findAll(q).forEach { total += it.groupValues[1].toInt() }
        if (total == 0) Regex("\\b(\\d+)\\b").find(q)?.let { total = it.groupValues[1].toInt() * 60 }
        return total
    }

    /** Parse a clock time: "7", "7am", "7:30 pm", "06:45". 24h when no am/pm. */
    private fun parseTime(q: String): Pair<Int, Int>? {
        val m = Regex("\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b").findAll(q)
            .firstOrNull { it.groupValues[1].toInt() <= 23 } ?: return null
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].ifBlank { "0" }.toInt()
        when (m.groupValues[3]) {
            "pm" -> if (h < 12) h += 12
            "am" -> if (h == 12) h = 0
        }
        if (h !in 0..23 || min !in 0..59) return null
        return h to min
    }

    private fun formatDuration(secs: Int): String {
        val h = secs / 3600; val m = (secs % 3600) / 60; val s = secs % 60
        val parts = buildList {
            if (h > 0) add("$h hour" + if (h > 1) "s" else "")
            if (m > 0) add("$m minute" + if (m > 1) "s" else "")
            if (s > 0) add("$s second" + if (s > 1) "s" else "")
        }
        return parts.joinToString(" ")
    }

    private fun formatClock(h: Int, m: Int): String {
        val ampm = if (h < 12) "AM" else "PM"
        val h12 = when { h == 0 -> 12; h > 12 -> h - 12; else -> h }
        return "%d:%02d %s".format(h12, m, ampm)
    }
}
