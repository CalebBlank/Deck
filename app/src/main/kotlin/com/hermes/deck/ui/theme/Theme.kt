package com.hermes.deck.ui.theme

import android.app.WallpaperManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.rememberDynamicColorScheme

@Composable
fun DeckTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    seedColor: Color? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var wallpaperToken by remember { mutableIntStateOf(0) }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        DisposableEffect(Unit) {
            val wm = WallpaperManager.getInstance(context)
            val listener = WallpaperManager.OnColorsChangedListener { _, which ->
                if (which and WallpaperManager.FLAG_SYSTEM != 0) wallpaperToken++
            }
            wm.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
            onDispose { wm.removeOnColorsChangedListener(listener) }
        }
    }

    // rememberDynamicColorScheme must be called unconditionally (composable rules).
    // We always generate a seed scheme; it is only actually used when dynamicColor=false
    // and a seedColor was chosen by the user.
    val effectiveSeed = seedColor ?: Color(0xFF6750A4) // M3 baseline purple fallback
    val seedScheme = rememberDynamicColorScheme(seedColor = effectiveSeed, isDark = darkTheme, isAmoled = false)

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            remember(darkTheme, wallpaperToken) {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
        seedColor != null -> seedScheme
        darkTheme         -> darkColorScheme()
        else              -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content     = content
    )
}
