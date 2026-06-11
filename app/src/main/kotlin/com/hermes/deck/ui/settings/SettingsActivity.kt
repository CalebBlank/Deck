package com.hermes.deck.ui.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.hermes.deck.ui.theme.DeckTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs = remember { getSharedPreferences("deck_prefs", Context.MODE_PRIVATE) }
            var themeMode      by remember { mutableStateOf(prefs.getString("theme_mode", "system") ?: "system") }
            var materialYou    by remember { mutableStateOf(prefs.getBoolean("material_you", true)) }
            var seedColorArgb  by remember { mutableIntStateOf(prefs.getInt("seed_color", 0)) }

            DisposableEffect(Unit) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    when (key) {
                        "theme_mode"   -> themeMode     = prefs.getString("theme_mode", "system") ?: "system"
                        "material_you" -> materialYou   = prefs.getBoolean("material_you", true)
                        "seed_color"   -> seedColorArgb = prefs.getInt("seed_color", 0)
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }

            val systemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                "dark"  -> true
                "light" -> false
                else    -> systemDark
            }
            val seedColor: Color? = if (materialYou || seedColorArgb == 0) null else Color(seedColorArgb)

            DeckTheme(darkTheme = isDark, dynamicColor = materialYou, seedColor = seedColor) {
                SettingsScreen(
                    onBack          = ::finish,
                    initialSection  = intent.getStringExtra("section"),
                    initialProvider = intent.getStringExtra("search_provider")
                )
            }
        }
    }
}
