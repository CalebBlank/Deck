package com.hermes.deck.ui.search.providers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow

/** Carries a tapped "Claude replied" notification's session id from MainActivity to the search
 *  surface, which observes it to reopen that conversation. */
object ClaudeDeepLink {
    const val EXTRA_SESSION = "claude_open_session"
    val pendingSessionId = MutableStateFlow<String?>(null)
}

/**
 * Posts a notification when a Claude answer arrives while the user isn't looking at the chat.
 * Tapping it deep-links back into that conversation (via [ClaudeDeepLink] → MainActivity → the
 * search surface). Best-effort: gated on the `claude_notify` pref + POST_NOTIFICATIONS, and the
 * request's coroutine must outlive the chat view (it does — the SearchViewModel is activity-scoped).
 */
object ClaudeNotifications {
    private const val CHANNEL_ID = "claude_replies"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE).getBoolean("claude_notify", false)

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Claude replies", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Shown when a Claude answer is ready and you're not looking at the chat"
                }
            )
        }
    }

    fun notifyAnswer(context: Context, sessionId: String, title: String, body: String) {
        if (!isEnabled(context) || !hasPermission(context)) return
        ensureChannel(context)
        val intent = Intent(context, com.hermes.deck.MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(ClaudeDeepLink.EXTRA_SESSION, sessionId)
        }
        val pi = PendingIntent.getActivity(
            context, sessionId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(sessionId.hashCode(), notif) }
    }

    fun cancel(context: Context, sessionId: String) {
        runCatching { NotificationManagerCompat.from(context).cancel(sessionId.hashCode()) }
    }
}
