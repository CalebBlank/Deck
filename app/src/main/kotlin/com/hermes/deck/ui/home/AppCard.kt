package com.hermes.deck.ui.home

import android.graphics.Bitmap
import android.graphics.Canvas
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
    modifier: Modifier = Modifier
) {
    val cacheRevision by ScreenshotCache.revision.collectAsState()
    val screenshot: Bitmap? = remember(app.packageName, cacheRevision) {
        ScreenshotCache.get(app.packageName)
    }
    val iconBitmap: Bitmap = remember(app.packageName) {
        val d = app.icon
        val w = d.intrinsicWidth.coerceIn(1, 192)
        val h = d.intrinsicHeight.coerceIn(1, 192)
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
            d.setBounds(0, 0, w, h)
            d.draw(Canvas(bmp))
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
