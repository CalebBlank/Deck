package com.hermes.deck.ui.home

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hermes.deck.service.NotificationStore

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DockBar(
    pinnedPackages: List<String>,
    onUnpin: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (pinnedPackages.isEmpty()) return

    val context = LocalContext.current
    val haptic  = LocalHapticFeedback.current

    // Collect revision so we recompose when notifications change
    val notifRevision by NotificationStore.revision.collectAsState()

    Row(
        modifier              = modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        pinnedPackages.forEach { pkg ->
            val (iconBitmap, label) = remember(pkg) {
                val d = runCatching { context.packageManager.getApplicationIcon(pkg) }
                    .getOrDefault(context.packageManager.defaultActivityIcon)
                val lbl = runCatching {
                    context.packageManager.getApplicationLabel(
                        context.packageManager.getApplicationInfo(pkg, 0)
                    ).toString()
                }.getOrDefault(pkg)
                val w = d.intrinsicWidth.coerceIn(1, 128)
                val h = d.intrinsicHeight.coerceIn(1, 128)
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                    d.setBounds(0, 0, w, h)
                    d.draw(Canvas(bmp))
                } to lbl
            }

            val hasBadge = remember(pkg, notifRevision) { NotificationStore.has(pkg) }

            Box {
                Image(
                    bitmap             = iconBitmap.asImageBitmap(),
                    contentDescription = label,
                    modifier           = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .combinedClickable(
                            onClick = {
                                context.packageManager.getLaunchIntentForPackage(pkg)
                                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    ?.let { context.startActivity(it) }
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onUnpin(pkg)
                            }
                        )
                )
                if (hasBadge) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .align(Alignment.TopEnd)
                            .background(Color.Red, CircleShape)
                            .semantics { contentDescription = "Unread notification" }
                    )
                }
            }
        }
    }
}
