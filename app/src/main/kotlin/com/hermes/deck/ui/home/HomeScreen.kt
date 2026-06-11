package com.hermes.deck.ui.home

import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import com.hermes.deck.ui.settings.SettingsActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hermes.deck.data.CardGroup
import com.hermes.deck.service.BrowserTabReceiver
import com.hermes.deck.ui.drawer.DrawerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val hostView = LocalView.current
    // Bounds of the card area, captured below, so an app launch can scale out of the card. A true
    // cross-process container transform isn't available; makeScaleUpAnimation from the card's on-screen
    // rect is the launcher-standard equivalent. Null (not laid out yet) -> default system animation.
    var cardBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val launchOpts: () -> android.os.Bundle? = {
        cardBounds?.let { b ->
            android.app.ActivityOptions.makeScaleUpAnimation(
                hostView, b.left.toInt(), b.top.toInt(), b.width.toInt(), b.height.toInt()
            ).toBundle()
        }
    }
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(context))
    val state          by vm.uiState.collectAsState()
    val drawerVm: DrawerViewModel = viewModel(factory = DrawerViewModel.factory(context))
    val overrideIcons  by drawerVm.overrideIcons.collectAsState()
    val resolvedIcons  by drawerVm.resolvedIcons.collectAsState()

    LaunchedEffect(Unit) { vm.refresh() }

    val lifecycleOwner = LocalLifecycleOwner.current
    val currentVm = rememberUpdatedState(vm)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                currentVm.value.refresh()
                currentVm.value.refreshPreviews()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val expandedGroup = state.expandedGroup
    val displayGroups = remember(state.cardGroups, expandedGroup) {
        if (expandedGroup != null) expandedGroup.apps.map { CardGroup.single(it) }
        else state.cardGroups
    }

    var isCollapsing     by remember { mutableStateOf(false) }
    var isExpanding      by remember { mutableStateOf(false) }
    var expandPivotIndex by remember { mutableIntStateOf(-1) }
    var pendingExpandId  by remember { mutableStateOf<String?>(null) }

    // When back is pressed, play the collapse animation then switch the state
    BackHandler(enabled = expandedGroup != null && !isCollapsing) {
        isCollapsing = true
    }
    LaunchedEffect(isCollapsing) {
        if (!isCollapsing) return@LaunchedEffect
        delay(480L)   // matches StiffnessMediumLow spring settle time
        vm.collapseStack()
        delay(48L)    // let the stack item render before animateItem resumes
        isCollapsing = false
    }
    // If expandedGroup is cleared externally (e.g. last card swiped away), reset flag.
    // Guard against firing during a controlled collapse (isCollapsing handles that case).
    LaunchedEffect(expandedGroup) {
        if (expandedGroup == null && !isCollapsing) isCollapsing = false
    }

    // Hold the normal view while non-stack cards slide out, then switch to expanded view.
    LaunchedEffect(isExpanding) {
        if (!isExpanding) return@LaunchedEffect
        delay(320L)
        val id    = pendingExpandId ?: return@LaunchedEffect
        val group = state.cardGroups.firstOrNull { it.id == id }
        group?.let { vm.expandStack(it) }
        pendingExpandId = null
        isExpanding     = false
    }

    val density      = LocalDensity.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Inflate the pager's measured bounds to exactly the pill top so drag-down reveal
    // can reach the pill without hitting the pager clip.
    val bottomInflationDp = 43.dp
    val bottomInflationPx = with(density) { bottomInflationDp.roundToPx() }
    val drawerOpenSignal = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }

    val prefs = remember { context.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE) }
    var dimAmount  by remember { mutableFloatStateOf(prefs.getFloat("wallpaper_dim", 0f)) }
    var blurTarget by remember { mutableFloatStateOf(prefs.getFloat("wallpaper_blur", 0f)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "wallpaper_dim"  -> dimAmount  = prefs.getFloat("wallpaper_dim", 0f)
                "wallpaper_blur" -> blurTarget = prefs.getFloat("wallpaper_blur", 0f)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val hasCards = state.cardGroups.isNotEmpty()
    val blurFraction by androidx.compose.animation.core.animateFloatAsState(
        targetValue   = if (hasCards) blurTarget else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 600),
        label         = "blurFraction"
    )
    val effectiveDim by androidx.compose.animation.core.animateFloatAsState(
        targetValue   = if (hasCards) dimAmount else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 600),
        label         = "effectiveDim"
    )

    // Drive window-level wallpaper blur via the system compositor. blurFraction is only read here,
    // so a plain SideEffect would NOT recompose as it animates (reads inside a SideEffect aren't
    // tracked) — the radius would stick at its pre-animation value and only update when the dim
    // animation, read in the body, happened to be recomposing too. snapshotFlow OBSERVES the
    // animated value, so the blur follows it every frame and reliably reaches 0 when cards clear.
    // Requires windowIsTranslucent=true in the theme to render. API 31+ only.
    val activity = context as? Activity
    LaunchedEffect(activity) {
        snapshotFlow { (blurFraction * 80f).roundToInt() }
            .distinctUntilChanged()
            .collect { radius ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    activity?.window?.setBackgroundBlurRadius(radius)
                }
            }
    }

    Box(modifier = modifier) {
        // System wallpaper renders through the transparent window (FLAG_SHOW_WALLPAPER +
        // transparent theme background). WallpaperBackground removed — it was a redundant copy.

        // Protects status bar icons against bright wallpapers
        Box(
            Modifier
                .fillMaxWidth()
                .height(statusBarTop + 32.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent)
                    )
                )
        )

        if (effectiveDim > 0f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = effectiveDim)))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            CardStrip(
                cardGroups        = displayGroups,
                cycleEvent        = vm.cycleEvent,
                focusEvent        = vm.focusEvent,
                bottomReservedDp  = bottomInflationDp,
                deadZoneHeightDp  = 56.dp + navBarBottom + 4.dp,
                modifier          = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 13.dp)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(
                            constraints.copy(maxHeight = constraints.maxHeight + bottomInflationPx)
                        )
                        layout(placeable.width, constraints.maxHeight) { placeable.place(0, 0) }
                    }
                    .onGloballyPositioned { cardBounds = it.boundsInRoot() },
                onGroupTap     = { group ->
                    if (group.isStack && expandedGroup == null && !isExpanding) {
                        expandPivotIndex = state.cardGroups.indexOfFirst { it.id == group.id }
                        pendingExpandId  = group.id
                        isExpanding      = true
                    } else {
                        val pkg    = group.primaryApp.packageName
                        val taskId = group.primaryApp.taskId
                        Log.d("DeckTap", "card tap pkg=$pkg taskId=$taskId isBrowser=${pkg == BrowserTabReceiver.BROWSER_PACKAGE}")
                        if (taskId != -1 && pkg == BrowserTabReceiver.BROWSER_PACKAGE) {
                            // Cross-app moveTaskToFront for a browser tab is intermittently rejected
                            // on Android 14/15 (tab stays backgrounded). Prefer the browser's own
                            // trampoline (ReopenTabActivity) which fronts the task from inside the
                            // browser process; fall back to moveTaskToFront if it can't be started.
                            Log.d("DeckTap", "reopen tab via trampoline taskId=$taskId")
                            val started = runCatching {
                                context.startActivity(
                                    Intent().apply {
                                        setClassName(
                                            BrowserTabReceiver.BROWSER_PACKAGE,
                                            "com.hermes.browser.ReopenTabActivity"
                                        )
                                        putExtra("task_id", taskId)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    },
                                    launchOpts()
                                )
                            }.isSuccess
                            if (!started) {
                                Log.d("DeckTap", "trampoline failed → moveTaskToFront($taskId)")
                                (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
                                    .moveTaskToFront(taskId, 0)
                            }
                        } else if (pkg == context.packageName) {
                            // Deck's own non-home card (e.g. Settings). Its task activity is usually already
                            // destroyed, so moveTaskToFront shows nothing; and getLaunchIntentForPackage
                            // resolves to the HOME activity. Launch Deck's non-home LAUNCHER activity
                            // (Settings) directly — startActivity resumes its task or relaunches it.
                            val comp = context.packageManager.queryIntentActivities(
                                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg), 0
                            ).map { it.activityInfo }
                                .firstOrNull { !it.name.endsWith(".MainActivity") }
                                ?.let { ComponentName(it.packageName, it.name) }
                            Log.d("DeckTap", "self card → launch non-home activity comp=$comp")
                            comp?.let {
                                context.startActivity(Intent(Intent.ACTION_MAIN).apply {
                                    addCategory(Intent.CATEGORY_LAUNCHER)
                                    component = it
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                                }, launchOpts())
                            }
                        } else {
                            Log.d("DeckTap", "else-branch launch pkg=$pkg")
                            // For all other apps: use startActivity. This brings an existing task
                            // to front OR relaunches if the task was killed — unlike moveTaskToFront
                            // which silently does nothing on a stale taskId.
                            context.packageManager.getLaunchIntentForPackage(pkg)
                            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                            ?.let { context.startActivity(it, launchOpts()) }
                        }
                    }
                },
                onGroupDismiss = { group ->
                    if (expandedGroup != null) vm.removeFromExpandedStack(group.primaryApp.id)
                    else vm.dismissGroup(group)
                },
                onGroupUnstack = { group ->
                    if (expandedGroup == null) vm.dissolveStack(group)
                },
                onStack        = if (expandedGroup == null) vm::stackGroups else { _, _ -> },
                onReorderGroup = if (expandedGroup == null) vm::reorderGroups else { _, _ -> },
                isExpandedStack  = expandedGroup != null,
                isCollapsing     = isCollapsing,
                isExpanding      = isExpanding,
                expandPivotIndex = expandPivotIndex,
                onPageChange      = vm::updateFocusedIndex,
                onCardRevealed    = {},
                overrideIcons     = overrideIcons,
                resolvedIcons     = resolvedIcons,
                onEmptyLongPress  = {
                    context.startActivity(
                        Intent(context, SettingsActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            )

            // Dead zone — swipe up opens the drawer; height matches browser pill + gap
            Spacer(
                Modifier
                    .height(56.dp + navBarBottom + 4.dp)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        var totalDrag = 0f
                        detectVerticalDragGestures(
                            onDragStart    = { totalDrag = 0f },
                            onVerticalDrag = { _, delta -> totalDrag += delta },
                            onDragEnd      = { if (totalDrag < -40f) drawerOpenSignal.tryEmit(Unit) }
                        )
                    }
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

        if (!state.hasAccessibilityService) {
            AccessibilityNudge(
                modifier = Modifier.align(Alignment.Center),
                onGrant  = {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            )
        }

        LauncherSheet(
            pendingKeyInput    = vm.pendingKeyInput,
            onKeyInputConsumed = vm::clearKeyInput,
            backspaceEvent     = vm.backspaceEvent,
            openEvent          = drawerOpenSignal,
            closeEvent         = vm.drawerCloseEvent,
            modifier           = Modifier.align(Alignment.BottomCenter).zIndex(2f).offset(y = 2.dp)
        )
    }
}

@Composable
private fun AccessibilityNudge(onGrant: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.padding(32.dp)) {
        Column(
            modifier            = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Accessibility service disabled", style = MaterialTheme.typography.titleMedium)
            Text(
                text  = "Deck's accessibility service captures app screenshots and intercepts the overview key. Enable \"Deck\" in Accessibility settings.",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = onGrant) { Text("Open Accessibility Settings") }
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
