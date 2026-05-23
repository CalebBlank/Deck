package com.hermes.deck.ui.home

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hermes.deck.data.AppInfo
import com.hermes.deck.data.ScreenshotCache

val CARD_CORNER = 24.dp

@Composable
fun AppCard(
    app: AppInfo,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val screenshot: Bitmap? = remember(app.packageName) {
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
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(CARD_CORNER))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onTap)
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
                modifier           = Modifier
                    .size(80.dp)
                    .align(Alignment.Center)
            )
        }

        // App label pinned to bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                    RoundedCornerShape(bottomStart = CARD_CORNER, bottomEnd = CARD_CORNER)
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text     = app.label,
                style    = MaterialTheme.typography.labelLarge,
                color    = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
