package com.hermes.deck.ui.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.toArgb
import com.hermes.deck.data.IconShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun rememberAppIconBitmap(
    key: Any,
    drawable: Drawable?,
    iconShape: IconShape = IconShape.NONE,
    size: Int = 128
): Bitmap? {
    val bgArgb = MaterialTheme.colorScheme.primaryContainer.toArgb()
    return produceState<Bitmap?>(null, key, drawable, iconShape, bgArgb) {
        value = withContext(Dispatchers.Default) {
            val d = drawable ?: return@withContext null
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bmp ->
                val canvas = Canvas(bmp)
                if (iconShape != IconShape.NONE) {
                    iconShape.clipCanvas(canvas, size.toFloat())
                    canvas.drawColor(bgArgb)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && d is AdaptiveIconDrawable) {
                        val extra = (size * 0.25f).roundToInt()
                        d.background?.let { it.setBounds(-extra, -extra, size + extra, size + extra); it.draw(canvas) }
                        d.foreground?.let { it.setBounds(-extra, -extra, size + extra, size + extra); it.draw(canvas) }
                    } else {
                        d.setBounds(0, 0, size, size); d.draw(canvas)
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && d is AdaptiveIconDrawable) {
                    val extra = (size * 0.25f).roundToInt()
                    d.background?.let { it.setBounds(-extra, -extra, size + extra, size + extra); it.draw(canvas) }
                    d.foreground?.let { it.setBounds(-extra, -extra, size + extra, size + extra); it.draw(canvas) }
                } else {
                    d.setBounds(0, 0, size, size); d.draw(canvas)
                }
            }
        }
    }.value
}
