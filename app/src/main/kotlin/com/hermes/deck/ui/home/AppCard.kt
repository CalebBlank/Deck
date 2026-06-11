package com.hermes.deck.ui.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.hermes.deck.data.AppInfo
import com.hermes.deck.data.ScreenshotCache

val CARD_CORNER = 40.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCard(
    app: AppInfo,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    resolvedIcon: Drawable? = null,
    modifier: Modifier = Modifier
) {
    val cacheRevision by ScreenshotCache.revision.collectAsState()
    // Look up by exact card id first (packageName, or packageName:taskId for multi-task
    // apps), then fall back to the bare package key. The fallback is required for native
    // apps: HomeViewModel resolves a real taskId for them (so moveTaskToFront works), making
    // app.id = "pkg:taskId", but the screenshot service captures by bare package name.
    // This fallback is safe for browser tabs now that the bare `com.hermes.browser` key is
    // never written (LivePreviewRepository forces per-task keys for the browser) — a browser
    // tab without its own capture falls back to a non-existent key and shows its icon.
    val screenshot: Bitmap? = remember(app.id, cacheRevision) {
        ScreenshotCache.get(app.id) ?: ScreenshotCache.get(app.packageName)
    }
    val iconBitmap: Bitmap = remember(app.packageName, resolvedIcon) {
        val d = resolvedIcon ?: app.icon
        val size = 192
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bmp ->
            val canvas = Canvas(bmp)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && d is AdaptiveIconDrawable) {
                // Foreground only — card background shows through the transparent areas.
                val extra = (size * 0.25f).toInt()
                d.foreground?.let { it.setBounds(-extra, -extra, size + extra, size + extra); it.draw(canvas) }
            } else {
                d.setBounds(0, 0, size, size)
                d.draw(canvas)
            }
        }
    }
    Box(
        modifier         = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(CARD_CORNER))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
        contentAlignment = Alignment.Center
    ) {
        if (screenshot != null) {
            Image(
                bitmap             = screenshot.asImageBitmap(),
                contentDescription = app.label,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )
        } else {
            Image(
                bitmap             = iconBitmap.asImageBitmap(),
                contentDescription = app.label,
                modifier           = Modifier.size(80.dp)
            )
        }
    }
}
