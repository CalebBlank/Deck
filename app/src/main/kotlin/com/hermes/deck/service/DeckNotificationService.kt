package com.hermes.deck.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class DeckNotificationService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        NotificationStore.post(sbn.packageName)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        NotificationStore.remove(sbn.packageName)
    }

    override fun onListenerDisconnected() {
        NotificationStore.clear()
    }
}
