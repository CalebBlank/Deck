package com.hermes.deck.ui.search.providers

import com.hermes.deck.ui.search.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BrowserHistorySearchProvider : SearchProvider {
    override val id = "browser_history"

    private val suPath: String? = listOf("/debug_ramdisk/su", "/su/bin/su", "/sbin/su")
        .firstOrNull { java.io.File(it).canExecute() }

    private fun runSu(su: String, cmd: String): String = try {
        val proc = ProcessBuilder(su, "-c", cmd).redirectErrorStream(false).start()
        val out = proc.inputStream.bufferedReader().readText()
        proc.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
        proc.destroy()
        out
    } catch (_: Exception) { "" }

    data class BrowserSpec(
        val name: String,
        val pkg: String,
        val dbPath: String,
        val isFirefox: Boolean = false
    )

    private val browsers = listOf(
        BrowserSpec("Chrome",           "com.android.chrome",            "app_chrome/Default/History"),
        BrowserSpec("Brave",            "com.brave.browser",             "app_chrome/Default/History"),
        BrowserSpec("Edge",             "com.microsoft.emmx",            "app_chrome/Default/History"),
        BrowserSpec("Samsung Internet", "com.sec.android.app.sbrowser",  "app_sbrowser/Default/History"),
        BrowserSpec("Firefox",          "org.mozilla.firefox",           "files/mozilla", isFirefox = true),
    )

    override suspend fun query(q: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val su = suPath ?: return@withContext emptyList()
        if (q.length < 2) return@withContext emptyList()

        // Sanitize for SQL injection: escape single and double quotes
        val safe = q.replace("'", "''").replace("\"", "\"\"")

        val results = mutableListOf<SearchResult.BrowserHistoryResult>()

        for (browser in browsers) {
            val dataDir = "/data/data/${browser.pkg}"

            // Check if data dir exists by trying to list it
            val check = runSu(su, "ls '$dataDir' 2>/dev/null && echo EXISTS")
            if (!check.contains("EXISTS")) continue

            if (browser.isFirefox) {
                // Discover profile dir
                val profilesDir = "$dataDir/files/mozilla"
                val lsOut = runSu(su, "ls '$profilesDir' 2>/dev/null")
                val profileDir = lsOut.lines()
                    .map { it.trim() }
                    .firstOrNull { it.endsWith(".default") || it.endsWith(".default-release") }
                    ?: continue

                val dbFile = "$profilesDir/$profileDir/places.sqlite"
                val sql = "SELECT url || \"|||||\" || COALESCE(title,\"\") FROM moz_places WHERE (url LIKE \"%$safe%\" OR title LIKE \"%$safe%\") ORDER BY last_visit_date DESC LIMIT 5"
                val output = runSu(su, "/system/bin/sqlite3 '$dbFile' '$sql'")
                output.lines().filter { it.isNotBlank() }.forEach { line ->
                    val sep = line.indexOf("|||||")
                    if (sep >= 0) {
                        val url   = line.substring(0, sep)
                        val title = line.substring(sep + 5)
                        if (url.isNotBlank()) {
                            results += SearchResult.BrowserHistoryResult(url, title, browser.name)
                        }
                    }
                }
            } else {
                val dbFile = "$dataDir/${browser.dbPath}"
                val sql = "SELECT url || \"|||||\" || COALESCE(title,\"\") FROM urls WHERE (url LIKE \"%$safe%\" OR title LIKE \"%$safe%\") AND hidden=0 ORDER BY last_visit_time DESC LIMIT 5"
                val output = runSu(su, "/system/bin/sqlite3 '$dbFile' '$sql'")
                output.lines().filter { it.isNotBlank() }.forEach { line ->
                    val sep = line.indexOf("|||||")
                    if (sep >= 0) {
                        val url   = line.substring(0, sep)
                        val title = line.substring(sep + 5)
                        if (url.isNotBlank()) {
                            results += SearchResult.BrowserHistoryResult(url, title, browser.name)
                        }
                    }
                }
            }
        }

        results
    }
}
