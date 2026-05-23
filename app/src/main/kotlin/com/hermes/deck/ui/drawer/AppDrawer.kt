package com.hermes.deck.ui.drawer

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hermes.deck.data.AppInfo

@Composable
fun AppDrawer(
    onClose: () -> Unit,
    onAppLaunch: (AppInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vm: DrawerViewModel = viewModel(factory = DrawerViewModel.factory(context))
    val apps by vm.apps.collectAsState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color    = MaterialTheme.colorScheme.background.copy(alpha = 0.96f)
    ) {
        Column(modifier = Modifier.systemBarsPadding()) {
            // Drag handle / close button
            Box(
                modifier            = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment    = Alignment.Center
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close")
                }
            }

            LazyVerticalGrid(
                columns             = GridCells.Adaptive(minSize = 76.dp),
                contentPadding      = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(apps, key = { it.packageName }) { app ->
                    AppGridItem(app = app, onClick = { onAppLaunch(app) })
                }
            }
        }
    }
}

@Composable
private fun AppGridItem(app: AppInfo, onClick: () -> Unit) {
    val iconBitmap: Bitmap = remember(app.packageName) {
        val d = app.icon
        val w = d.intrinsicWidth.coerceIn(1, 192)
        val h = d.intrinsicHeight.coerceIn(1, 192)
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
            d.setBounds(0, 0, w, h)
            d.draw(Canvas(bmp))
        }
    }

    Column(
        modifier            = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Image(
            bitmap             = iconBitmap.asImageBitmap(),
            contentDescription = app.label,
            modifier           = Modifier.size(52.dp)
        )
        Text(
            text     = app.label,
            style    = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
