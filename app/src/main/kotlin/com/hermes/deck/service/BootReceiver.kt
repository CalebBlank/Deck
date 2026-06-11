package com.hermes.deck.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.io.File
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("disable_recents_gesture", false)) return

        val su = listOf("/debug_ramdisk/su", "/su/bin/su", "/sbin/su")
            .firstOrNull { File(it).canExecute() } ?: return
        try {
            ProcessBuilder(su, "-c", "/system/bin/settings put secure navigation_mode 0")
                .start()
                .waitFor(5, TimeUnit.SECONDS)
        } catch (_: Exception) {}
    }
}
