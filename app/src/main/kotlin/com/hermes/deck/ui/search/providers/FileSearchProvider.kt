package com.hermes.deck.ui.search.providers

import android.webkit.MimeTypeMap
import com.hermes.deck.ui.search.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FileSearchProvider : SearchProvider {
    override val id = "files"

    private val suPath: String? = listOf("/debug_ramdisk/su", "/su/bin/su", "/sbin/su")
        .firstOrNull { java.io.File(it).canExecute() }

    private fun runSu(su: String, cmd: String): String = try {
        val proc = ProcessBuilder(su, "-c", cmd).redirectErrorStream(false).start()
        val out = proc.inputStream.bufferedReader().readText()
        proc.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
        proc.destroy()
        out
    } catch (_: Exception) { "" }

    override suspend fun query(q: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val su = suPath ?: return@withContext emptyList()
        if (q.length < 3) return@withContext emptyList()

        // Strip dangerous shell characters
        val safe = q.replace("'", "").replace("\"", "").replace("`", "")
            .replace("$", "").replace("\\", "")

        if (safe.length < 3) return@withContext emptyList()

        val cmd = "find /sdcard -maxdepth 8 -iname \"*$safe*\" -type f 2>/dev/null | head -10"
        val output = runSu(su, cmd)

        output.lines()
            .filter { it.isNotBlank() }
            .map { path ->
                val name = path.substringAfterLast('/')
                val ext  = name.substringAfterLast('.', "").lowercase()
                val mime = if (ext.isNotEmpty()) MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) else null
                SearchResult.FileResult(path = path, name = name, mimeType = mime)
            }
    }
}
