package com.hermes.deck.ui.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hermes.deck.ui.drawer.AppDrawer
import com.hermes.deck.ui.search.LauncherSearchBar

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(context))
    val state by vm.uiState.collectAsState()
    var drawerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refresh() }

    Box(modifier = modifier) {
        WallpaperBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Card strip fills all space above the search bar
            CardStrip(
                cards         = state.recentApps,
                modifier      = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 8.dp),
                onCardTap     = { app ->
                    context.packageManager.getLaunchIntentForPackage(app.packageName)
                        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ?.let { context.startActivity(it) }
                },
                onCardDismiss = vm::dismissCard
            )

            // Search bar pinned to bottom
            LauncherSearchBar(
                onOpenDrawer = { drawerOpen = true },
                modifier     = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }

        if (!state.hasUsagePermission) {
            UsagePermissionNudge(
                modifier = Modifier.align(Alignment.Center),
                onGrant  = {
                    context.startActivity(
                        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            )
        }

        AnimatedVisibility(
            visible = drawerOpen,
            enter   = slideInVertically(initialOffsetY = { it }),
            exit    = slideOutVertically(targetOffsetY = { it })
        ) {
            AppDrawer(
                onClose     = { drawerOpen = false },
                onAppLaunch = { app ->
                    drawerOpen = false
                    context.packageManager.getLaunchIntentForPackage(app.packageName)
                        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ?.let { context.startActivity(it) }
                }
            )
        }
    }
}

@Composable
private fun UsagePermissionNudge(onGrant: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.padding(32.dp)) {
        Column(
            modifier            = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Usage access needed", style = MaterialTheme.typography.titleMedium)
            Text(
                text  = "Deck needs usage access to show your recent apps as cards.",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = onGrant) { Text("Grant Access") }
        }
    }
}
