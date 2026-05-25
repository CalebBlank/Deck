package com.hermes.deck.ui.home

import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hermes.deck.ui.drawer.AppDrawer
import com.hermes.deck.ui.search.LauncherSearchBar
import kotlinx.coroutines.flow.MutableSharedFlow

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(context))
    val state by vm.uiState.collectAsState()
    val pinnedPackages by vm.pinnedPackages.collectAsState()
    val overviewMode by vm.overviewMode.collectAsState()
    var drawerIsOpen by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }
    val drawerOpenSignal    = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val drawerCloseSignal   = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val drawerDragDeltaFlow = remember { MutableSharedFlow<Float>(extraBufferCapacity = 64) }
    val drawerSettleFlow    = remember { MutableSharedFlow<Float>(extraBufferCapacity = 1) }
    val searchDismissSignal = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }

    LaunchedEffect(Unit) { vm.refresh() }

    // Close the drawer when MainActivity signals it (home button press / system home gesture).
    LaunchedEffect(Unit) {
        vm.drawerCloseEvent.collect {
            Log.d("DeckHome", "drawerCloseEvent received, drawerIsOpen=$drawerIsOpen")
            drawerCloseSignal.tryEmit(Unit)
        }
    }

    BackHandler(enabled = overviewMode) { vm.exitOverviewMode() }
    BackHandler(enabled = searchActive) { searchActive = false }

    val statusBarTop  = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom  = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    var anyCardRevealed by remember { mutableStateOf(false) }
    val currentDrawerOpen   = rememberUpdatedState(drawerIsOpen)
    val currentSearchActive = rememberUpdatedState(searchActive)
    val currentAnyCardRevealed = rememberUpdatedState(anyCardRevealed)

    Box(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)

                // Close drawer on upward swipe from bottom 3% while drawer is open
                if (currentDrawerOpen.value && down.position.y > size.height * 0.97f) {
                    var cumDelta = 0f
                    dragLoop@ while (true) {
                        val event = awaitPointerEvent()
                        for (change in event.changes) {
                            if (!change.pressed) break@dragLoop
                            cumDelta += change.position.y - change.previousPosition.y
                        }
                    }
                    if (cumDelta < -30f) drawerCloseSignal.tryEmit(Unit)
                    return@awaitEachGesture
                }

                if (currentDrawerOpen.value || currentSearchActive.value) return@awaitEachGesture
                val threshold = if (currentAnyCardRevealed.value) 0.875f else 0.80f
                if (down.position.y < size.height * threshold || down.position.y > size.height * 0.97f) return@awaitEachGesture
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, down.position)
                var cumulativeDelta = 0f
                var tookControl = false
                dragLoop@ while (true) {
                    val event = awaitPointerEvent()
                    for (change in event.changes) {
                        if (!change.pressed) break@dragLoop
                        val delta = change.position.y - change.previousPosition.y
                        tracker.addPosition(change.uptimeMillis, change.position)
                        cumulativeDelta += delta
                        if (!tookControl && cumulativeDelta < -20f) {
                            tookControl = true
                        }
                        if (tookControl) {
                            change.consume()
                            drawerDragDeltaFlow.tryEmit(delta)
                        }
                    }
                }
                val velocity = tracker.calculateVelocity().y
                if (tookControl) {
                    if (cumulativeDelta < -30f || kotlin.math.abs(velocity) > 300f) {
                        Log.d("DeckHome", "outer swipe settle → velocity=$velocity")
                        drawerSettleFlow.tryEmit(velocity)
                    } else {
                        Log.d("DeckHome", "outer swipe short → drawerOpenSignal")
                        drawerOpenSignal.tryEmit(Unit)
                    }
                }
            }
        }
    ) {
        // Protects status bar icons against bright wallpapers
        Box(
            Modifier
                .fillMaxWidth()
                .height(statusBarTop + 32.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Card strip fills all space above the search bar
            CardStrip(
                cards           = state.recentApps,
                cycleEvent      = vm.cycleEvent,
                focusEvent      = vm.focusEvent,
                overviewMode    = overviewMode,
                onEnterOverview = vm::enterOverviewMode,
                onExitOverview  = vm::exitOverviewMode,
                onCardMoveLeft  = vm::moveCardLeft,
                onCardMoveRight = vm::moveCardRight,
                modifier        = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 8.dp)
                    .pointerInput(searchActive) {
                        if (searchActive) {
                            detectTapGestures {
                                searchActive = false
                                searchDismissSignal.tryEmit(Unit)
                            }
                        }
                    },
                onCardTap       = { app ->
                    context.packageManager.getLaunchIntentForPackage(app.packageName)
                        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ?.let { context.startActivity(it) }
                },
                onCardDismiss   = vm::dismissCard,
                onPageChange    = vm::updateFocusedIndex,
                onCardRevealed  = { anyCardRevealed = it }
            )

            DockBar(
                pinnedPackages = pinnedPackages,
                onUnpin        = vm::unpinApp,
                modifier       = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(72.dp + navBarBottom))
        }

        // Search bar as bottom overlay — expanded state floats over cards instead of compressing them
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Column {
                if (!searchActive) {
                    Spacer(Modifier.fillMaxWidth().height(48.dp))
                }
                LauncherSearchBar(
                    pendingKeyInput      = vm.pendingKeyInput,
                    onKeyInputConsumed   = vm::clearKeyInput,
                    backspaceEvent       = vm.backspaceEvent,
                    onSearchActiveChange = { searchActive = it },
                    dismissSignal        = searchDismissSignal,
                    modifier             = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
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

        if (drawerIsOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial)
                                    .changes.forEach { it.consume() }
                            }
                        }
                    }
            )
        }

        AppDrawer(
            onClose       = { Log.d("DeckHome", "onClose() → drawerIsOpen=false"); drawerIsOpen = false },
            onOpen        = { Log.d("DeckHome", "onOpen() → drawerIsOpen=true"); drawerIsOpen = true },
            openSignal    = drawerOpenSignal,
            closeSignal   = drawerCloseSignal,
            dragDeltaFlow = drawerDragDeltaFlow,
            settleFlow    = drawerSettleFlow,
            onAppLaunch   = { app ->
                drawerIsOpen = false
                context.packageManager.getLaunchIntentForPackage(app.packageName)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ?.let { context.startActivity(it) }
            },
            modifier      = Modifier.zIndex(2f)
        )
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
