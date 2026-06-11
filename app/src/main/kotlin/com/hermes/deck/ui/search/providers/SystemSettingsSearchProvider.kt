package com.hermes.deck.ui.search.providers

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.hermes.deck.ui.search.SearchResult

class SystemSettingsSearchProvider(private val context: Context) : SearchProvider {
    override val id = "system_settings"

    private data class Entry(val title: String, val subtitle: String, val action: String)

    private val entries = listOf(
        // Network & Internet
        Entry("Wi-Fi",                  "Network & Internet",  Settings.ACTION_WIFI_SETTINGS),
        Entry("Hotspot & tethering",    "Network & Internet",  Settings.ACTION_WIRELESS_SETTINGS),
        Entry("Airplane mode",          "Network & Internet",  Settings.ACTION_AIRPLANE_MODE_SETTINGS),
        Entry("Data roaming",           "Network & Internet",  Settings.ACTION_DATA_ROAMING_SETTINGS),
        Entry("Mobile network",         "Network & Internet",  Settings.ACTION_NETWORK_OPERATOR_SETTINGS),
        Entry("VPN",                    "Network & Internet",  Settings.ACTION_VPN_SETTINGS),
        Entry("Wi-Fi calling",          "Network & Internet",  "android.settings.WIFI_CALLING_SETTINGS"),
        Entry("Internet",               "Network & Internet",  Settings.ACTION_WIFI_SETTINGS),
        // Connected Devices
        Entry("Bluetooth",              "Connected devices",   Settings.ACTION_BLUETOOTH_SETTINGS),
        Entry("NFC",                    "Connected devices",   Settings.ACTION_NFC_SETTINGS),
        Entry("Tap & pay",              "Connected devices",   Settings.ACTION_NFC_PAYMENT_SETTINGS),
        Entry("Cast",                   "Connected devices",   Settings.ACTION_CAST_SETTINGS),
        Entry("Printing",               "Connected devices",   Settings.ACTION_PRINT_SETTINGS),
        Entry("USB",                    "Connected devices",   Settings.ACTION_WIRELESS_SETTINGS),
        Entry("Pair new device",        "Connected devices",   Settings.ACTION_BLUETOOTH_SETTINGS),
        // Display
        Entry("Display",                "Display",             Settings.ACTION_DISPLAY_SETTINGS),
        Entry("Screen saver",           "Display",             Settings.ACTION_DREAM_SETTINGS),
        Entry("Night mode",             "Display",             Settings.ACTION_NIGHT_DISPLAY_SETTINGS),
        Entry("Dark theme",             "Display",             Settings.ACTION_DISPLAY_SETTINGS),
        Entry("Adaptive brightness",    "Display",             Settings.ACTION_DISPLAY_SETTINGS),
        Entry("Screen timeout",         "Display",             Settings.ACTION_DISPLAY_SETTINGS),
        Entry("Font size",              "Display",             Settings.ACTION_DISPLAY_SETTINGS),
        Entry("Resolution",             "Display",             Settings.ACTION_DISPLAY_SETTINGS),
        Entry("Refresh rate",           "Display",             Settings.ACTION_DISPLAY_SETTINGS),
        Entry("Auto-rotate",            "Display",             Settings.ACTION_DISPLAY_SETTINGS),
        // Sound & Vibration
        Entry("Sound & vibration",      "Sound",               Settings.ACTION_SOUND_SETTINGS),
        Entry("Volume",                 "Sound",               Settings.ACTION_SOUND_SETTINGS),
        Entry("Ringtone",               "Sound",               Settings.ACTION_SOUND_SETTINGS),
        Entry("Vibration",              "Sound",               Settings.ACTION_SOUND_SETTINGS),
        Entry("Media volume",           "Sound",               Settings.ACTION_SOUND_SETTINGS),
        // Notifications
        Entry("Notifications",          "Notifications",       "android.settings.NOTIFICATION_SETTINGS"),
        Entry("Do Not Disturb",         "Notifications",       Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
        Entry("Notification history",   "Notifications",       "android.settings.NOTIFICATION_HISTORY"),
        Entry("Notification access",    "Notifications",       "android.settings.NOTIFICATION_LISTENER_SETTINGS"),
        // Battery
        Entry("Battery",                "Battery",             Settings.ACTION_BATTERY_SAVER_SETTINGS),
        Entry("Battery saver",          "Battery",             Settings.ACTION_BATTERY_SAVER_SETTINGS),
        Entry("Battery usage",          "Battery",             "android.intent.action.POWER_USAGE_SUMMARY"),
        Entry("Battery optimization",   "Battery",             Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        // Storage
        Entry("Storage",                "Storage",             Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
        Entry("Files",                  "Storage",             Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
        Entry("SD card",                "Storage",             Settings.ACTION_MEMORY_CARD_SETTINGS),
        Entry("USB storage",            "Storage",             Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
        // Privacy & Security
        Entry("Privacy",                "Privacy",             Settings.ACTION_PRIVACY_SETTINGS),
        Entry("Permissions",            "Privacy",             Settings.ACTION_PRIVACY_SETTINGS),
        Entry("Location",               "Privacy",             Settings.ACTION_LOCATION_SOURCE_SETTINGS),
        Entry("Security",               "Security",            Settings.ACTION_SECURITY_SETTINGS),
        Entry("Fingerprint",            "Security",            Settings.ACTION_FINGERPRINT_ENROLL),
        Entry("Screen lock",            "Security",            Settings.ACTION_SECURITY_SETTINGS),
        Entry("PIN",                    "Security",            Settings.ACTION_SECURITY_SETTINGS),
        Entry("Password",               "Security",            Settings.ACTION_SECURITY_SETTINGS),
        Entry("Device admin apps",      "Security",            Settings.ACTION_SECURITY_SETTINGS),
        Entry("Install unknown apps",   "Security",            Settings.ACTION_SECURITY_SETTINGS),
        // Digital Wellbeing
        Entry("Digital Wellbeing",      "Digital Wellbeing",   "android.settings.DIGITAL_WELLBEING_SETTINGS"),
        Entry("Screen time",            "Digital Wellbeing",   "android.settings.DIGITAL_WELLBEING_SETTINGS"),
        Entry("App timers",             "Digital Wellbeing",   "android.settings.DIGITAL_WELLBEING_SETTINGS"),
        Entry("Bedtime mode",           "Digital Wellbeing",   "android.settings.DIGITAL_WELLBEING_SETTINGS"),
        Entry("Focus mode",             "Digital Wellbeing",   "android.settings.DIGITAL_WELLBEING_SETTINGS"),
        Entry("Parental controls",      "Digital Wellbeing",   "android.settings.DIGITAL_WELLBEING_SETTINGS"),
        // Accounts
        Entry("Accounts & sync",        "Accounts",            Settings.ACTION_SYNC_SETTINGS),
        Entry("Add account",            "Accounts",            Settings.ACTION_ADD_ACCOUNT),
        Entry("Google account",         "Accounts",            Settings.ACTION_SYNC_SETTINGS),
        // Accessibility
        Entry("Accessibility",          "Accessibility",       Settings.ACTION_ACCESSIBILITY_SETTINGS),
        Entry("Captions",               "Accessibility",       Settings.ACTION_CAPTIONING_SETTINGS),
        Entry("Magnification",          "Accessibility",       Settings.ACTION_ACCESSIBILITY_SETTINGS),
        Entry("Large text",             "Accessibility",       Settings.ACTION_ACCESSIBILITY_SETTINGS),
        Entry("Text-to-speech",         "Accessibility",       "com.android.settings.TTS_SETTINGS"),
        Entry("Color correction",       "Accessibility",       Settings.ACTION_ACCESSIBILITY_SETTINGS),
        Entry("Hearing aid",            "Accessibility",       "android.settings.HEARING_AID_SETTINGS"),
        // System
        Entry("Date & time",            "System",              Settings.ACTION_DATE_SETTINGS),
        Entry("Language",               "System",              Settings.ACTION_LOCALE_SETTINGS),
        Entry("Language & input",       "System",              Settings.ACTION_LOCALE_SETTINGS),
        Entry("Keyboard",               "System",              Settings.ACTION_INPUT_METHOD_SETTINGS),
        Entry("Spell checker",          "System",              Settings.ACTION_INPUT_METHOD_SETTINGS),
        Entry("Autocomplete",           "System",              Settings.ACTION_INPUT_METHOD_SETTINGS),
        Entry("Gesture navigation",     "System",              "com.android.settings.GESTURE_NAVIGATION_SETTINGS"),
        Entry("System navigation",      "System",              Settings.ACTION_SECURITY_SETTINGS),
        Entry("Backup",                 "System",              "com.android.settings.BACKUP_SETTINGS"),
        Entry("Reset",                  "System",              Settings.ACTION_DEVICE_INFO_SETTINGS),
        Entry("About phone",            "System",              Settings.ACTION_DEVICE_INFO_SETTINGS),
        Entry("Developer options",      "System",              Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
        Entry("Apps",                   "Apps",                Settings.ACTION_APPLICATION_SETTINGS),
        Entry("All apps",               "Apps",                Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS),
        Entry("Default apps",           "Apps",                Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
        Entry("App permissions",        "Apps",                Settings.ACTION_PRIVACY_SETTINGS),
        Entry("Special app access",     "Apps",                Settings.ACTION_USAGE_ACCESS_SETTINGS),
        // Software update
        Entry("System update",          "System",              "android.settings.SYSTEM_UPDATE_SETTINGS"),
        Entry("Software update",        "System",              "android.settings.SYSTEM_UPDATE_SETTINGS"),
    )

    override suspend fun query(q: String): List<SearchResult> {
        if (q.isBlank()) return emptyList()
        val pm = context.packageManager
        return entries
            .filter { it.title.contains(q, ignoreCase = true) || it.subtitle.contains(q, ignoreCase = true) }
            .distinctBy { it.action + it.title }
            .filter { entry ->
                runCatching {
                    Intent(entry.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .resolveActivity(pm) != null
                }.getOrDefault(false)
            }
            .map { SearchResult.SystemSettingsResult(it.title, it.subtitle, it.action) }
    }
}
