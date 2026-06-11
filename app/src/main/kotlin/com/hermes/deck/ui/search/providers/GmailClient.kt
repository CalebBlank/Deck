package com.hermes.deck.ui.search.providers

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.mail.Folder
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.search.FromStringTerm
import javax.mail.search.OrTerm
import javax.mail.search.SubjectTerm

/**
 * Gmail search over IMAP using an app-password (Settings → Search → Gmail). Tap-to-search: an IMAP
 * connect + SELECT is ~2-3s — too slow per keystroke — so the card only fetches when tapped.
 *
 * SETUP: enable 2-Step Verification on the Google account → myaccount.google.com → Security →
 * App passwords → generate one → paste the address + that 16-char password. (Not the normal password.)
 */
object GmailClient {
    private const val PREFS = "deck_prefs"

    data class Mail(val from: String, val subject: String, val date: Long)

    fun email(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("gmail_address", "")?.trim().orEmpty()

    fun appPassword(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("gmail_app_password", "")?.replace(" ", "").orEmpty()

    fun isConfigured(context: Context): Boolean = email(context).isNotBlank() && appPassword(context).isNotBlank()

    suspend fun search(context: Context, query: String): Result<List<Mail>> = withContext(Dispatchers.IO) {
        val addr = email(context); val pw = appPassword(context)
        if (addr.isBlank() || pw.isBlank()) return@withContext Result.failure(IllegalStateException("Gmail not configured"))
        runCatching {
            val props = Properties().apply {
                put("mail.store.protocol", "imaps")
                put("mail.imaps.host", "imap.gmail.com")
                put("mail.imaps.port", "993")
                put("mail.imaps.ssl.enable", "true")
                put("mail.imaps.connectiontimeout", "12000")
                put("mail.imaps.timeout", "15000")
            }
            val store = Session.getInstance(props).getStore("imaps")
            store.connect("imap.gmail.com", 993, addr, pw)
            try {
                val folder = store.getFolder("INBOX")
                folder.open(Folder.READ_ONLY)
                try {
                    val found = folder.search(OrTerm(SubjectTerm(query), FromStringTerm(query)))
                    found.takeLast(12).reversed().map { msg ->
                        val from = (msg.from?.firstOrNull() as? InternetAddress)?.let { it.personal ?: it.address }
                            ?: msg.from?.firstOrNull()?.toString() ?: ""
                        Mail(
                            from = from,
                            subject = msg.subject ?: "(no subject)",
                            date = msg.sentDate?.time ?: msg.receivedDate?.time ?: 0L
                        )
                    }
                } finally { runCatching { folder.close(false) } }
            } finally { runCatching { store.close() } }
        }
    }
}
