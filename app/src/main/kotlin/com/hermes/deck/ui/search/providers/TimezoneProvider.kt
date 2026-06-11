package com.hermes.deck.ui.search.providers

import com.hermes.deck.ui.search.SearchResult
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Timezone "answer" provider — pure-local via java.time. Handles:
 *   "time in tokyo"  ·  "3pm EST to PST"  ·  "9:30am in london"
 */
class TimezoneProvider : SearchProvider {
    override val id = "timezone"

    private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    override suspend fun query(q: String): List<SearchResult> {
        val s = q.trim()
        val lower = s.lowercase(Locale.US)
        if (lower.length < 4) return emptyList()

        // "time in <zone>" → current time there
        Regex("^(?:what(?:'s| is) the )?(?:current )?time in (.+)$").find(lower)?.let { mr ->
            val zone = parseZone(mr.groupValues[1]) ?: return emptyList()
            val now = ZonedDateTime.now(zone)
            return answer(now, "Current time in ${zoneLabel(zone)}")
        }

        // "<time> <from> to <to>"
        if (" to " in lower) {
            val (before, toStr) = lower.split(" to ", limit = 2)
            val toZone = parseZone(toStr.trim()) ?: return emptyList()
            val (time, fromStr) = splitTimeAndRest(before.trim()) ?: return emptyList()
            val fromZone = parseZone(fromStr) ?: return emptyList()
            val src = ZonedDateTime.now(fromZone).withHour(time.hour).withMinute(time.minute).withSecond(0).withNano(0)
            val dst = src.withZoneSameInstant(toZone)
            return answer(dst, "${timeFmt.format(src)} ${zoneLabel(fromZone)}")
        }

        // "<time> in <zone>" → that local time, shown in <zone>
        if (" in " in lower) {
            val (timeStr, toStr) = lower.split(" in ", limit = 2)
            val time = parseTime(timeStr.trim()) ?: return emptyList()
            val toZone = parseZone(toStr.trim()) ?: return emptyList()
            val src = ZonedDateTime.now().withHour(time.hour).withMinute(time.minute).withSecond(0).withNano(0)
            val dst = src.withZoneSameInstant(toZone)
            return answer(dst, "${timeFmt.format(src)} your time")
        }
        return emptyList()
    }

    private fun answer(dt: ZonedDateTime, detail: String): List<SearchResult> {
        val timeStr = "${timeFmt.format(dt)} ${zoneLabel(dt.zone)}"
        return listOf(SearchResult.AnswerResult("timezone", value = timeStr, detail = detail, copyText = timeStr))
    }

    /** Split "3pm est" → (3pm, "est"); "3:30 pm new york" → (3:30pm, "new york"). Longest time prefix. */
    private fun splitTimeAndRest(s: String): Pair<LocalTime, String>? {
        val tokens = s.split(Regex("\\s+")).filter { it.isNotBlank() }
        for (n in minOf(2, tokens.size) downTo 1) {
            val time = parseTime(tokens.take(n).joinToString(" "))
            if (time != null && tokens.size > n) return time to tokens.drop(n).joinToString(" ")
        }
        return null
    }

    private fun parseTime(raw: String): LocalTime? {
        val t = raw.trim().lowercase(Locale.US).replace(" ", "")
        when (t) { "noon" -> return LocalTime.NOON; "midnight" -> return LocalTime.MIDNIGHT }
        val m = Regex("^(\\d{1,2})(?::(\\d{2}))?(am|pm)?$").find(t) ?: return null
        var h = m.groupValues[1].toIntOrNull() ?: return null
        val min = m.groupValues[2].toIntOrNull() ?: 0
        val ap = m.groupValues[3]
        if (ap == "pm" && h < 12) h += 12
        if (ap == "am" && h == 12) h = 0
        if (h !in 0..23 || min !in 0..59) return null
        return LocalTime.of(h, min)
    }

    private fun parseZone(raw: String): ZoneId? {
        val key = raw.trim().lowercase(Locale.US).removeSuffix(" time").trim()
        abbr[key]?.let { return runCatching { ZoneId.of(it) }.getOrNull() }
        cities[key]?.let { return runCatching { ZoneId.of(it) }.getOrNull() }
        // direct IANA id ("america/new_york") or UTC/GMT offsets
        return runCatching { ZoneId.of(raw.trim().let { if (it.equals("utc", true) || it.equals("gmt", true)) it.uppercase() else it }) }.getOrNull()
    }

    private fun zoneLabel(z: ZoneId): String =
        z.getDisplayName(TextStyle.SHORT, Locale.US).takeIf { it.isNotBlank() && it != z.id } ?: z.id.substringAfterLast('/').replace('_', ' ')

    private val abbr = mapOf(
        "est" to "America/New_York", "edt" to "America/New_York", "et" to "America/New_York",
        "cst" to "America/Chicago", "cdt" to "America/Chicago", "ct" to "America/Chicago",
        "mst" to "America/Denver", "mdt" to "America/Denver", "mt" to "America/Denver",
        "pst" to "America/Los_Angeles", "pdt" to "America/Los_Angeles", "pt" to "America/Los_Angeles",
        "utc" to "UTC", "gmt" to "GMT", "bst" to "Europe/London",
        "cet" to "Europe/Paris", "cest" to "Europe/Paris", "eet" to "Europe/Helsinki",
        "ist" to "Asia/Kolkata", "jst" to "Asia/Tokyo", "kst" to "Asia/Seoul",
        "aest" to "Australia/Sydney", "aedt" to "Australia/Sydney",
    )

    private val cities = mapOf(
        "new york" to "America/New_York", "nyc" to "America/New_York",
        "los angeles" to "America/Los_Angeles", "la" to "America/Los_Angeles", "san francisco" to "America/Los_Angeles",
        "chicago" to "America/Chicago", "denver" to "America/Denver", "seattle" to "America/Los_Angeles",
        "toronto" to "America/Toronto", "mexico city" to "America/Mexico_City",
        "london" to "Europe/London", "paris" to "Europe/Paris", "berlin" to "Europe/Berlin",
        "madrid" to "Europe/Madrid", "rome" to "Europe/Rome", "amsterdam" to "Europe/Amsterdam",
        "moscow" to "Europe/Moscow", "dubai" to "Asia/Dubai", "india" to "Asia/Kolkata",
        "mumbai" to "Asia/Kolkata", "delhi" to "Asia/Kolkata", "tokyo" to "Asia/Tokyo",
        "japan" to "Asia/Tokyo", "beijing" to "Asia/Shanghai", "china" to "Asia/Shanghai",
        "shanghai" to "Asia/Shanghai", "hong kong" to "Asia/Hong_Kong", "singapore" to "Asia/Singapore",
        "seoul" to "Asia/Seoul", "sydney" to "Australia/Sydney", "melbourne" to "Australia/Melbourne",
        "auckland" to "Pacific/Auckland",
    )
}
