package com.hermes.deck.ui.home

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WallpaperBackground(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null) {
        value = withContext(Dispatchers.IO) { decodeBitmap(context) }
        val scope = this
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                scope.launch { value = withContext(Dispatchers.IO) { decodeBitmap(context) } }
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_WALLPAPER_CHANGED))
        awaitDispose { context.unregisterReceiver(receiver) }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (bitmap != null) {
            Image(
                bitmap             = bitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )
        }
    }
}

private fun decodeBitmap(context: Context): Bitmap? = runCatching {
    val wm = WallpaperManager.getInstance(context)
    val drawable: Drawable = wm.drawable ?: wm.builtInDrawable ?: return@runCatching null
    val dm = context.resources.displayMetrics
    val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else dm.widthPixels
    val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else dm.heightPixels
    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
        drawable.setBounds(0, 0, w, h)
        drawable.draw(Canvas(bmp))
    }
}.getOrNull()
