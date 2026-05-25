package com.hermes.deck.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class DeckNotificationService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val title = sbn.notification.extras
            .getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = sbn.notification.extras
            .getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
        NotificationStore.post(sbn.packageName, title, text)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        NotificationStore.remove(sbn.packageName)
    }

    override fun onListenerDisconnected() {
        NotificationStore.clear()
    }
}
