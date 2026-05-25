package com.hermes.deck.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun EmptyCardsScreen(modifier: Modifier = Modifier) {
    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector        = Icons.Outlined.Dashboard,
            contentDescription = null,
            modifier           = Modifier.size(64.dp),
            tint               = Color.White.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text      = "No recent apps",
            style     = MaterialTheme.typography.titleMedium,
            color     = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text      = "Apps you switch to will appear here as cards",
            style     = MaterialTheme.typography.bodySmall,
            color     = Color.White.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
            modifier  = Modifier.padding(horizontal = 32.dp)
        )
    }
}
