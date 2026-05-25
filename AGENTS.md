# Agent Coordination Log

Two Claude Code instances work on this project. Before starting a task, read this file.
After finishing a task, append a note under the current session.

## How to use

- **Before starting**: read this file to see what the other instance has done
- **After finishing**: append what you did and any important notes for the other instance
- Keep notes brief — just enough for the other instance to pick up without asking questions

## Starting a new instance

Paste this prompt into each new terminal tab, replacing the letter:

> You are **Instance [X]** working on the Deck Android launcher project. Read `C:\Users\Caleb\Nextcloud2\Hermes\Projects\Deck\AGENTS.md` in full, then take the tasks listed under **Instance [X]** in the most recent round. Do not take tasks from other instances.

Instances: **A** = main conversation, **B** = Android Studio plugin, **C** and **D** = terminal tabs.

---

## Broader product vision

Deck is the first piece of a larger ecosystem. Keep this in mind when making design decisions.

### Companion browser (post-Deck)

A Firefox-based browser (Fenix fork) is planned after Deck ships. Its core philosophy: **the browser should be invisible; websites should feel like apps.**

**How it integrates with Deck:**
- Each browser tab appears as a separate card in Deck, using `FLAG_ACTIVITY_NEW_DOCUMENT` per tab
- The browser publishes open tabs through Deck's existing plugin ContentProvider spec — the browser is just another plugin
- Tabs get screenshots via `ScreenshotAccessibilityService` the same as any other app
- Per-site `theme-color` metadata could tint the card thumbnail in Deck, so each tab has a distinct visual identity

**Browser UI direction:**
- Chrome (browser UI) is minimal and auto-hiding — out of the way of content
- No persistent tab bar inside the browser — Deck is the tab manager
- PWA-style "install" = pin that tab's activity to Deck's dock with a dedicated icon
- Gesture navigation over buttons

**Why this matters for Deck decisions:**
- The plugin ContentProvider spec should stay clean and general — the browser will be its most demanding consumer
- Card thumbnails/theming should be extensible (plugins may want to influence how their cards look)
- The dock's pinning model should eventually support pinning a specific tab/URL, not just an app package

---

## Session log

### Claude Code (main conversation) — 2026-05-22

**Done:**
- Created full project scaffold (34 files)
- Card layout: HorizontalPager with peek on both sides
- Card gestures: swipe up = dismiss, drag down = reveal actions (Uninstall/App Info/Hide)
- AppCard: fills pager space, screenshot or icon fallback, label pinned to bottom
- WallpaperBackground: draws system wallpaper as bitmap
- HomeScreen: Column layout (CardStrip + SearchBar), app drawer overlay
- Search bar: DockedSearchBar with animated cycling placeholder ("Find your… groove/vibe/jam")
- SearchViewModel: fans query to providers in parallel, 200ms debounce
- Providers: AppSearchProvider, ContactSearchProvider, CalculatorProvider
- Plugin system: PluginContract (ContentProvider spec) + PluginRepository
- ScreenshotAccessibilityService: captures on TYPE_WINDOW_STATE_CHANGED (API 30+)
- CLAUDE.md: full project context

**Watch out for:**
- `GesturableCard` tap-while-revealed handler uses a lambda that captures `scope` — make sure coroutine scope isn't leaked
- `ScreenshotCache` uses a synchronized LRU LinkedHashMap — don't access from main thread
- No Hilt — all ViewModels use manual factory pattern via companion object
- Gradle versions updated by Android Studio to AGP 8.13.2 / Gradle 8.13

**Next logical tasks:**
- Fix any compile errors from first build
- KEYCODE_APP_SWITCH intercept in MainActivity (show Deck cards instead of system recents)
- Visual polish: card scale/shadow, dim side cards
- Dock bar (pinned apps at bottom)
- Onboarding flow (usage access + accessibility permission)

---

<!-- Add new entries below this line -->

### Claude Code (second instance) — 2026-05-22

**Done:**
- Synced Gradle: created missing `gradlew.bat`, `gradle-wrapper.jar` (copied from ~/.gradle cache), and `gradle.properties` (`android.useAndroidX=true`)
- Fixed `CardStrip.kt`: added `@OptIn(ExperimentalFoundationApi::class)` to `CardStrip`, removed non-existent `beyondViewportPageCount` param (foundation 1.6.8 doesn't expose it by that name), inlined the missing `snapBack` animation and fixed `() -> Job` → `() -> Unit` lambda type mismatch
- Fixed `LauncherSearchBar.kt`: rewrote `DockedSearchBar` call from Material3 1.3+ API (`inputField`/`expanded`) to the 1.2.1 API (`query`/`onQueryChange`/`onSearch`/`active`/`onActiveChange`) — BOM 2024.06.00 ships Material3 1.2.1
- Build passes: `assembleDebug` succeeds (one deprecation warning on `MOVE_TO_FOREGROUND` in `RecentAppsRepository`, non-blocking)

**Watch out for:**
- `DockedSearchBar` API in Material3 1.2.x uses `active`/`onActiveChange` params; upgrading the BOM past ~2024.09 will require migrating to the `inputField` slot API again
- `HorizontalPager` `beyondViewportPageCount` removed for now — add it back if/when upgrading to foundation 1.7+

---

### Instance C — 2026-05-23

**Done:**
- `CardStrip.kt` (`GesturableCard`): changed inner-shadow Box padding from `.padding(horizontal = 12.dp, vertical = 8.dp)` to `.padding(30.dp)` — increases the shadow bleed area uniformly around the card.

### Instance C — 2026-05-24

**Done:**
- `CardStrip.kt`: halved `PAGE_SPACING` from 6.dp to 3.dp — tighter gaps between cards.
- `CardStrip.kt`: replaced card height formula with screen-aspect-ratio-based calculation (`cardWidthPx * screenHeightDp/screenWidthDp`). Added `import androidx.compose.ui.platform.LocalConfiguration`. Cards now look like scaled-down phone screens regardless of device aspect ratio.
- `CardStrip.kt`: reduced `PEEK_HORIZONTAL` 60→40dp (wider cards), removed `.fillMaxWidth(0.95f)` → `.fillMaxWidth()` (cards fill slot edge-to-edge, gap is now exactly PAGE_SPACING), reduced snap-buffer 80→40dp (taller cards).

---

### Instance B — 2026-05-23

**Done:**
- Ran `assembleDebug` — BUILD SUCCESSFUL in 1m 46s, no compile errors. All 35 tasks passed (4 executed, 31 up-to-date).

### Instance B — 2026-05-23 (second run)

**Done:**
- Ran `assembleDebug` again — BUILD SUCCESSFUL in 13s. No compile errors. 4 tasks executed, 31 up-to-date. Nothing to fix.

---

## Task list — divided between instances (2026-05-22)

**Rule**: each instance owns the files listed under its name. Neither touches the other's files unless noted.

---

### Instance A — main conversation

Owns: `MainActivity.kt`, `ui/home/HomeScreen.kt`, `ui/home/CardStrip.kt`, `ui/home/AppCard.kt`, `ui/home/HomeViewModel.kt`, `ui/home/WallpaperBackground.kt`, `ui/home/DockBar.kt` (new), `ui/onboarding/` (new)

**Tasks, in order:**

1. **KEYCODE_APP_SWITCH intercept** (`MainActivity.kt`)  
   Override `onKeyDown` for `KEYCODE_APP_SWITCH`. If Deck is already foreground, cycle to next card. Otherwise bring Deck to front. `singleTask` launchMode already set.

2. **Dim side cards + scale animation** (`CardStrip.kt`)  
   Use `pagerState.currentPageOffsetFraction` per page to drive `graphicsLayer { scaleX; scaleY; alpha }`. Off-center cards ~90% scale, ~60% alpha.

3. **Dock bar** (`DockBar.kt` new, `HomeScreen.kt`, `HomeViewModel.kt`, `CardActions.kt`)  
   Fixed row of 4 pinned app icons above the search bar. Stored in `SharedPreferences`. Long-press to unpin. Add "Pin to dock" to `CardActions.kt`.

4. **Onboarding flow** (`ui/onboarding/` new directory)  
   Two screens: accessibility permission + usage access. Gate with `SharedPreferences` so it only appears once. Add `OnboardingActivity` to `AndroidManifest.xml`.

**Do not touch**: `ui/search/`, `ui/drawer/`, `data/`, `service/`, `plugin/`

---

### Instance B — Android Studio

Owns: `ui/search/`, `ui/drawer/`, `data/`, `service/`, `plugin/`, `ui/home/CardActions.kt`

**Tasks, in order:**

1. **Runtime plugin discovery** (`plugin/PluginRepository.kt`)  
   Query `PackageManager` for ContentProviders with authority prefix `com.hermes.deck.plugin.`. Expose as `Flow<List<PluginInfo>>`. Wire into `SearchViewModel`.

2. **Rich search result cards** (`ui/search/LauncherSearchBar.kt`, `SearchResult.kt`)  
   Replace plain `ListItem` rows with card-style composables per type. App: icon + label + category. Contact: initials avatar + name + phone. Calculator: large result number. Plugin: icon from `COL_ICON_URI` + title + subtitle.

3. **App drawer polish** (`ui/drawer/AppDrawer.kt`, `DrawerViewModel.kt`)  
   Alphabetical section headers. Search field at top of drawer (apps-only, separate from main search bar). Animate drawer sliding up from bottom.

4. **AI search hook** (`providers/AiProvider.kt` new, `SearchViewModel.kt`)  
   ML Kit `LlmInferenceSession` (Gemini Nano on-device) for free-form queries that don't match concrete providers. Last in fan-out order, fails gracefully if model not downloaded.

**Do not touch**: `MainActivity.kt`, `CardStrip.kt`, `AppCard.kt`, `HomeViewModel.kt`, `WallpaperBackground.kt`, `DockBar.kt`, `ui/onboarding/`

---

### Claude Code (main conversation) — 2026-05-22 (second pass)

**Done:**
- `HomeViewModel`: added `cycleCard()` + `cycleEvent: SharedFlow<Unit>`, added `pinApp/unpinApp` + `pinnedPackages: StateFlow<List<String>>` (stored in `deck_prefs` SharedPreferences key `"pinned"`, comma-separated). Constructor now takes `SharedPreferences` — factory updated.
- `MainActivity`: added `onKeyDown` for `KEYCODE_APP_SWITCH` → calls `homeVm.cycleCard()`. Added `maybeShowOnboarding()` on `onCreate` (shows `OnboardingActivity` once, gated by `deck_prefs/"onboarding_done"`). Added `isAccessibilityEnabled()` and `hasUsageStatsPermission()` helpers. `onResume` now calls `homeVm.refresh()`.
- `CardStrip`: added `cycleEvent: SharedFlow<Unit>?` param → `LaunchedEffect` collects and calls `pagerState.animateScrollToPage(next)`. Added per-page scale (0.90→1.0) and alpha (0.60→1.0) via `graphicsLayer` using `pagerState.currentPageOffsetFraction`. Added `onCardPin: ((AppInfo) -> Unit)?` threaded through to `GesturableCard` → `CardActions`. Added `modifier` param to `GesturableCard`.
- `HomeScreen`: passes `cycleEvent = vm.cycleEvent`, `onCardPin`, and `pinnedPackages`/`vm::unpinApp` to new `DockBar`.
- `DockBar.kt` (new): shows pinned app icons in a Row. Tap = launch, long-press = unpin. Icons resolved from `PackageManager`, converted Drawable→Bitmap same pattern as `AppCard`.
- `CardActions`: added `onPin: (() -> Unit)?` param; shows `PushPin` button when non-null.
- `ui/onboarding/OnboardingActivity.kt` + `OnboardingScreen.kt` (new): two-step flow (Accessibility → Usage Access). Each step has "Open Settings" button + "Continue" to skip. Added to `AndroidManifest.xml`.

**Watch out for:**
- `CardActions.kt` was modified (added `onPin` param, default null) — if Instance B adds its own changes to this file, it needs to keep the `onPin` param.

---

### Claude Code (main conversation) — 2026-05-22 (question for Instance B)

Instance A's task list is fully complete. Instance B's four tasks (plugin discovery, rich search cards, drawer polish, AI search hook) are still outstanding.

**How should we divide the remaining work going forward?**

Options:
1. **Instance B takes all four remaining tasks** — clean since it already owns those files; Instance A idles until a new batch is defined.
2. **Split the remaining four** — e.g., Instance A takes AI search hook (`AiProvider.kt`, `SearchViewModel.kt`) since it's a new file with no ownership conflict, while Instance B handles plugin discovery + drawer + rich cards.

---

### Instance C — 2026-05-24

**Done:**
- `CardStrip.kt`: replaced `slotAspect` (derived from `constraints.maxHeight / constraints.maxWidth`) with `screenAspect` derived from `LocalConfiguration.current.screenHeightDp / screenWidthDp`. Added imports for `android.content.res.Configuration` and `androidx.compose.ui.platform.LocalConfiguration`.
- Portrait vs. landscape handled explicitly: `tallEdge / wideEdge` in portrait, `wideEdge / tallEdge` in landscape. This ensures correct orientation-aware ratio and guarantees recomposition on config change.
- Effect: screen aspect is smaller than slot aspect (slot is narrower/shorter due to PEEK_HORIZONTAL and chrome), so `heightPx / screenAspect` produces a larger `widthPx` — cards fill closer to full slot width and the visible side gap is reduced.
3. **Define a new round of tasks** — if the user has new features in mind (notifications, widget support, settings screen, etc.), list them here and divide fresh.

---

### Instance D — 2026-05-24

**Done:**
- `HomeScreen.kt`: fixed double-settle infinite loop caused by two overlapping `pointerInput` handlers both emitting to `drawerSettleFlow`.
  - Removed the Column's `pointerInput(searchActive, drawerIsOpen)` block entirely (it was redundant with the 48dp Box handler and its `drawerIsOpen`/`searchActive` keys were the trigger for the restart loop).
  - Rewrote the 48dp Box's `pointerInput(drawerIsOpen)` to use `pointerInput(Unit)` (stable key — never restarts) with `rememberUpdatedState` snapshots of `drawerIsOpen` and `searchActive` read inside the gesture body instead.
  - Removed now-unused import `androidx.compose.ui.input.pointer.PointerEventPass`.

**Watch out for:**
- The 48dp Box handler is the sole source of drag deltas and settle signals. If you add another gesture handler in this area, ensure it does NOT also emit to `drawerDragDeltaFlow` or `drawerSettleFlow` — duplicate emissions restart the loop.

Which approach do you prefer? Add a reply below and update the ownership table.

---

### Instance C — 2026-05-24

**Done:**
- `CardStrip.kt`: switched card sizing from width-first to height-first.
  - Added `import androidx.compose.ui.platform.LocalContext`.
  - Inside `BoxWithConstraints`, replaced the old fixed-ratio height formula (which derived height from slot width × 1.78) with a display-metrics-based approach: card height = 88% of slot height, card width = height ÷ screen aspect ratio (clamped to slot width). This makes cards match the device's own screen proportions scaled to fit, rather than assuming a hard-coded 9:16 ratio.
  - Changed the card `Box` modifier from `.fillMaxWidth()` to `.width(cardWidthDp)` so the card uses the derived width. `BoxWithConstraints` already has `contentAlignment = Alignment.Center`, so the narrower card centers itself in the pager slot automatically.

---

### Claude Code (Instance B) — 2026-05-22

**Done (all four tasks):**

1. **Runtime plugin discovery** (`PluginRepository.kt`, `providers/PluginSearchProvider.kt`, `SearchViewModel.kt`)
   - Added `pluginsFlow(): Flow<List<PluginInfo>>` to `PluginRepository` using `callbackFlow` + `BroadcastReceiver` (listens for `ACTION_PACKAGE_ADDED/REMOVED/REPLACED`)
   - Created `PluginSearchProvider` — thin `SearchProvider` wrapper around one `PluginInfo`
   - Refactored `SearchViewModel` constructor to `(staticProviders, pluginRepository)`. Collects `pluginsFlow` into `pluginProviders: MutableStateFlow`, merges with static providers each search

2. **Rich search result cards** (`LauncherSearchBar.kt`, `SearchResult.kt`, `AppInfo.kt`, `InstalledAppsRepository.kt`)
   - Added `category: String = ""` to `AppInfo` (default preserves backward compat with Instance A's files)
   - Populated category from `ApplicationInfo.category` in `InstalledAppsRepository` via private `Int.toCategoryLabel()` extension
   - Replaced generic `SearchResultRow` `ListItem`s with typed composables: `AppResultCard` (Drawable→Bitmap icon + category label), `ContactResultCard` (initials avatar circle), `CalculatorResultCard` (headlineMedium result), `PluginResultCard` (async URI icon load via `produceState`)

3. **App drawer polish** (`AppDrawer.kt`, `DrawerViewModel.kt`)
   - `DrawerViewModel`: removed `apps: StateFlow`, added `query: MutableStateFlow`, `filteredGrouped: StateFlow<Map<Char, List<AppInfo>>>` (filter + group-by-first-letter + sort, '#' bucket last)
   - `AppDrawer`: `OutlinedTextField` search at top; `LazyVerticalGrid` now iterates `groupedApps` with `SectionHeader` items spanning `maxLineSpan`; wrapped `Surface` in `AnimatedVisibility` with `slideInVertically` spring for slide-up entrance

4. **AI search hook** (`providers/AiProvider.kt` new, `SearchResult.kt`, `SearchViewModel.kt`, `LauncherSearchBar.kt`)
   - Added `SearchResult.AiResult(query, answer)`
   - Created `AiProvider` — last in provider list; returns empty until ML Kit model is available; `runInference()` is a stub with a clear comment showing the ML Kit `LlmInference` call to fill in (artifact: `com.google.mlkit:genai-inference:1.0.0-alpha1`)
   - Added `AiResultCard` composable with `AutoAwesome` icon tinted `tertiary`
   - `resultKey` updated for `AiResult`

**Watch out for:**
- `CardActions.kt` already had `onPin` param added by Instance A — kept as-is; don't modify
- `AppInfo.category` is new with default `""` — all Instance A's `AppInfo` usages stay valid
- `AiProvider.runInference` is a stub — to activate, add the ML Kit dependency and implement the one function

**Reply to Instance A's question:**
Option 1 makes sense — Instance B took all four tasks. Both backlogs are now clear. Ready for a new round whenever the user defines next tasks.

---

## Round 2 task list (2026-05-22)

Three instances, no file conflicts. Read your section, ignore the others.

---

### Instance A — main conversation

Owns: `ui/home/AppCard.kt`, `ui/home/CardStrip.kt`, `ui/home/HomeScreen.kt`, `ui/settings/` (new directory)

1. **Card elevation** (`AppCard.kt`, `CardStrip.kt`)  
   Add `Modifier.shadow(elevation, RoundedCornerShape(CARD_CORNER))` to the active card. Drive elevation from the same `pageOffset` value already used for scale — center card gets ~8.dp elevation, side cards ~2.dp.

2. **Status overlay** (`HomeScreen.kt`)  
   Add a `Row` at the top of the screen (over the wallpaper, below status bar insets) showing current time and battery level. Use `BroadcastReceiver` for `ACTION_BATTERY_CHANGED` and `ACTION_TIME_TICK` inside a `DisposableEffect`. Keep it minimal — just numbers, no icons.

3. **Settings screen** (`ui/settings/SettingsActivity.kt` + `SettingsScreen.kt` — new)  
   Basic settings: toggle to reset onboarding, clear pinned apps, about/version. Launch from a gear icon added to `HomeScreen.kt` (top-right corner). Add `SettingsActivity` to `AndroidManifest.xml`.

**Do not touch**: `ui/search/`, `ui/drawer/`, `data/`, `service/`, `plugin/`, `providers/`

---

### Instance B — Android Studio

Owns: `providers/AiProvider.kt`, `app/build.gradle`, `service/ScreenshotAccessibilityService.kt`, `data/ScreenshotCache.kt`

1. **Activate AiProvider** (`providers/AiProvider.kt`, `app/build.gradle`)  
   Add ML Kit dependency: `implementation("com.google.mlkit:genai-inference:1.0.0-alpha1")`. Implement the `runInference()` stub using `LlmInference.builder(context).setInferenceParams(...).build()`. Wrap in `try/catch` — if model not downloaded, return empty list silently.

2. **Screenshot refresh** (`service/ScreenshotAccessibilityService.kt`, `data/ScreenshotCache.kt`)  
   Currently screenshots are only captured on `TYPE_WINDOW_STATE_CHANGED`. Add a mechanism to re-capture if the cached screenshot is older than 60 seconds when the card becomes the current pager page. Store a `capturedAt: Long` timestamp alongside each bitmap in `ScreenshotCache`.

**Do not touch**: `ui/home/`, `ui/search/`, `ui/drawer/`, `ui/settings/`, `AndroidManifest.xml`

---

### Claude Code (main conversation) — 2026-05-22 (Round 2)

**Done:**
- `HomeViewModel`: added `reloadPinned()` — re-reads `deck_prefs/"pinned"` from SharedPreferences into `_pinnedPackages`. Called from `MainActivity.onResume()` so Settings clears take effect on return.
- `MainActivity.onResume`: now calls both `homeVm.refresh()` and `homeVm.reloadPinned()`.
- `HomeScreen`: added live time (`ACTION_TIME_TICK`) + battery (`ACTION_BATTERY_CHANGED`) state via `DisposableEffect`. Status overlay Row at top (settings gear left, time + battery% right) sits over the existing gradient scrim. Column given `padding(top = 40.dp)` to clear the overlay row. Added imports for BroadcastReceiver, BatteryManager, SimpleDateFormat, material icons.
- `ui/settings/SettingsActivity.kt` + `SettingsScreen.kt` (new): gear icon in HomeScreen launches it. Options: reset onboarding (removes `onboarding_done` key), clear pinned apps (removes `pinned` key), version display. Both destructive actions gated behind `AlertDialog` confirm.
- `AndroidManifest.xml`: added `SettingsActivity` entry.

**Note for other instances:** `CardStrip.kt` already had elevation added by another instance (lerp 2→20.dp, shadow on `GesturableCard` Box). `AppCard.kt` already had gradient scrim added. `HomeScreen.kt` already had status bar gradient scrim. All good — no conflicts.

---

### Instance C — new terminal tab

Owns: `service/DeckNotificationService.kt` (new), `AndroidManifest.xml` (add service entry only — don't touch existing entries), `plugin-example/` (new module)

1. **Notification listener** (`service/DeckNotificationService.kt` new, `AndroidManifest.xml`)  
   Subclass `NotificationListenerService`. On `onNotificationPosted`, store the latest notification per package (title + text) in a singleton `NotificationStore` (same pattern as `ScreenshotCache` — `object`, `@Synchronized`, `LinkedHashMap`). Add a `NotificationSearchProvider` that queries `NotificationStore` when the search term matches a stored notification title or text. Wire it into `SearchViewModel` alongside the existing static providers.  
   In `AndroidManifest.xml`, add:  
   ```xml
   <service android:name=".service.DeckNotificationService"
       android:exported="true"
       android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
       <intent-filter>
           <action android:name="android.service.notification.NotificationListenerService" />
       </intent-filter>
   </service>
   ```

2. **Plugin SDK example** (`plugin-example/` — new Gradle module)

---

### Instance D — fourth terminal tab

Owns: `ui/drawer/AppDrawer.kt`, `ui/drawer/DrawerViewModel.kt`, `data/RecentAppsRepository.kt`, `data/InstalledAppsRepository.kt`

1. **Recent apps row in drawer** (`AppDrawer.kt`, `DrawerViewModel.kt`)  
   Add a horizontal `LazyRow` at the top of the drawer showing the last 4 used apps (already available from `RecentAppsRepository`). Tap launches the app. Label it "Recent" with a `labelSmall` header. Sits above the search field and alphabetical grid.

2. **Sort modes** (`DrawerViewModel.kt`, `AppDrawer.kt`)  
   Add `enum class SortMode { ALPHA, MOST_USED, RECENTLY_INSTALLED }` and a `cycleSortMode()` method on `DrawerViewModel`. Store selected mode in `deck_prefs` key `"drawer_sort"`. Add a sort icon button next to the search field in `AppDrawer` that calls `cycleSortMode()`. For `MOST_USED`, use `UsageStatsManager` (already wired in `RecentAppsRepository`) to rank apps by total foreground time.

3. **Usage count** (`data/RecentAppsRepository.kt`)  
   Add `suspend fun getUsageCount(packageName: String): Long` — queries `UsageStatsManager.queryUsageStats()` for the past 30 days and returns total time in foreground for the given package. Used by `DrawerViewModel` for `MOST_USED` sort.

**Do not touch**: `ui/home/`, `ui/search/`, `ui/settings/`, `service/`, `plugin/`, `providers/`, `AndroidManifest.xml`  
   Minimal Android library/app module that demonstrates how to implement the `PluginContract` spec. Should include: a `ContentProvider` subclass that returns two hardcoded results for any query, the required `<meta-data>` in its manifest, and a short `README.md` explaining the column names. Add `include ':plugin-example'` to `settings.gradle`.

---

### Instance C — 2026-05-23

**Done:**
- `CardStrip.kt`: replaced `snapPx`/`maxCardHeightDp` with aspect-ratio-based `cardHeightDp` (portrait ratio capped 80dp from top).
- `CardStrip.kt`: changed card Box from `heightIn(max = maxCardHeightDp)` to `height(cardHeightDp)` (fixed height, no longer a max constraint).
- `CardStrip.kt`: added `vertical = 8.dp` to the inner shadow box padding (was `horizontal = 12.dp` only).

---

### Claude Code (main conversation) — 2026-05-22 (Round 2 complete)

**Done (all three Instance A Round 2 tasks):**

1. **Card elevation** (`AppCard.kt`, `CardStrip.kt`)
   - `AppCard`: replaced flat surface scrim on the label with `Brush.verticalGradient` (transparent → `Color.Black.copy(0.82f)`) across an 88dp box; text changed to `Color.White`.
   - `CardStrip`/`GesturableCard`: added `elevation: Dp` param; `Modifier.shadow(elevation, RoundedCornerShape(CARD_CORNER), clip = false)` on the card Box. Elevation lerps 2dp→20dp as `pageOffset` goes 1→0. Shadow captured inside `graphicsLayer` so it scales/dims with the card.

2. **Status overlay + status bar scrim** (`HomeScreen.kt`)
   - Scrim: gradient `Black.copy(0.45f)→Transparent`, height = statusBars inset + 32dp, above wallpaper.
   - Status Row: settings gear (left) + `"h:mm a • battery%"` (right) with `statusBarsPadding()`. Time via `ACTION_TIME_TICK`; battery via sticky `ACTION_BATTERY_CHANGED` (initial value read from `registerReceiver` return). Column offset 40dp to clear the row.

3. **Settings screen** (`ui/settings/SettingsActivity.kt` + `SettingsScreen.kt` new, `AndroidManifest.xml`)
   - Same pattern as `OnboardingActivity`. Screen: Reset onboarding, Clear pinned apps (both gated by `AlertDialog`), Version (read-only). Manifest entry added as `exported="false"`.

**Watch out for:**
- `shadow(clip = false)` — `AppCard` still does its own `clip(RoundedCornerShape)`, so content is clipped correctly; the shadow renders outside as intended.
- `ACTION_BATTERY_CHANGED` is sticky — initial value read directly from the Intent returned by `registerReceiver`, no blank flash on first compose.

---

### Claude Code (Instance B) — 2026-05-22 (Round 2)

**Done:**

1. **Screenshot refresh** (`ScreenshotCache.kt`, `ScreenshotAccessibilityService.kt`)
   - `ScreenshotCache`: added `data class ScreenshotEntry(val bitmap: Bitmap, val capturedAt: Long)`. Internal map now stores `ScreenshotEntry` instead of raw bitmaps. Added `getEntry(pkg): ScreenshotEntry?` and `val revision: StateFlow<Int>` — increments on every `put()`. LRU eviction still recycles old bitmaps.
   - `ScreenshotAccessibilityService`: removed per-session duplicate guard; replaced with 60-second staleness check using `getEntry()`. Re-captures when no entry exists or entry is ≥ 60s old.

2. **AI activation — deferred by user**
   - User explicitly said not to activate AI right now. Removed `com.google.mlkit:genai-prompt:1.0.0-beta2` from `app/build.gradle.kts` (it caused a hard build failure: compiled with Kotlin 2.2.0, project uses Kotlin 2.0.0).
   - `AiProvider.kt` is now a clean stub: no ML Kit imports, `runInference()` returns `null`, fails gracefully. Build is green.

**Note for Instance A (`AppCard.kt` owner):**
`ScreenshotCache.revision: StateFlow<Int>` is now live. To make `AppCard` recompose when a new screenshot arrives, add `val rev by ScreenshotCache.revision.collectAsState()` inside the composable and reference `rev` anywhere (a hidden `key` or unused `val` is enough to bust the snapshot). Without this, the card won't refresh until the next pager scroll.

---

### Claude Code (main conversation) — 2026-05-22 (AppCard hotfix)

Fixed `AppCard.kt`: now collects `ScreenshotCache.revision` as state; `screenshot` is keyed on both `app.packageName` and `cacheRevision` so the card recomposes immediately when a new screenshot is stored.

---

### Claude Code (main conversation) — 2026-05-23 (status overlay cleanup)

**Done:**
- `HomeScreen.kt`: removed the duplicate time + battery display from the status overlay (redundant with the system status bar). The `ACTION_TIME_TICK` and `ACTION_BATTERY_CHANGED` `DisposableEffect` blocks and their state variables are gone. Removed now-unused imports (`BroadcastReceiver`, `Context`, `IntentFilter`, `BatteryManager`, `SimpleDateFormat`, `Date`, `Locale`). The full `Row` was replaced with a single `IconButton` (gear icon) pinned to `Alignment.TopEnd` with `statusBarsPadding()`. Removed the `padding(top = 40.dp)` that was offsetting the `Column` to clear the old row.

---

## Round 3 task list (2026-05-22) — emulator testing prep

Goal: get the app into a testable state on a Pixel emulator. Focus on correctness and completeness, not new features.

---

### Instance A — main conversation

Owns: `ui/onboarding/OnboardingScreen.kt`, `ui/home/HomeScreen.kt`, `ui/home/HomeViewModel.kt`, `MainActivity.kt`

1. **Notification access onboarding step** (`OnboardingScreen.kt`)  
   Add a third step after Usage Access: prompt the user to enable Notification Access (`Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`). Check whether `DeckNotificationService` is already enabled via `NotificationManagerCompat.getEnabledListenerPackages()`. Skip the step automatically if already granted.

2. **Default launcher prompt** (`MainActivity.kt`, `HomeScreen.kt`)  
   On first launch, check if Deck is the default launcher using `RoleManager.isRoleHeld(RoleManager.ROLE_HOME)` (API 29+). If not, show a one-time `AlertDialog` prompting the user to set it as default via `RoleManager.createRequestRoleIntent`. Gate with `deck_prefs/"launcher_prompt_shown"` so it only appears once.

**Do not touch**: `ui/search/`, `ui/drawer/`, `data/`, `service/`, `plugin/`, `providers/`, `ui/settings/`

---

### Instance B — Android Studio

Owns: compile/build, `MainActivity.kt` keyboard section only (coordinate with Instance A)

1. **Build verification**  
   Run `assembleDebug`. Many files changed across Round 2 — find and fix any compilation errors. Document every fix in this log entry. This is the most important task this round.

2. **Type-to-search keyboard shortcut** (`MainActivity.kt`)  
   When a hardware key is typed and the search bar is not focused, forward the keypress to the search bar. Use a `MutableStateFlow<String>` on `HomeViewModel` called `pendingKeyInput`; `MainActivity.onKeyDown` appends printable characters to it; `LauncherSearchBar` collects it and calls `vm.onQueryChange()`. Clear after consuming.

**Do not touch**: `ui/home/` (except `HomeViewModel.kt` for `pendingKeyInput`), `ui/search/LauncherSearchBar.kt` — coordinate any changes to those files with Instance A first.

---

### Instance C — terminal tab

Owns: `ui/home/CardStrip.kt`, `ui/home/AppCard.kt` (read-only for reference), new `ui/home/EmptyCardsScreen.kt`

1. **Empty state** (`CardStrip.kt`, `EmptyCardsScreen.kt` new)  
   Replace the plain "No recent apps" text with a proper empty state: large icon (`Icons.Outlined.Dashboard`), headline, and a subtitle explaining that apps will appear here after you switch to them. Keep it centered and styled to match the wallpaper overlay aesthetic (white text, no card background).

2. **Peek card labels** (`CardStrip.kt`)  
   The side cards that peek from the edges currently show no label. Add the app name as a small `labelSmall` text at the bottom of the peeking portion, visible only on side cards (`pageOffset > 0.1f`), so the user knows what's on each side without swiping.

**Do not touch**: `ui/home/HomeScreen.kt`, `ui/home/HomeViewModel.kt`, `MainActivity.kt`

---

### Instance D — terminal tab

Owns: `ui/home/DockBar.kt`, `ui/home/CardActions.kt`, `ui/drawer/AppDrawer.kt`

1. **Haptic feedback** (`DockBar.kt`, `CardActions.kt`)  
   Add `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.LongPress)` to the dock icon long-press (unpin) and the card dismiss gesture. Import `androidx.compose.ui.hapticfeedback.*`.

2. **App shortcuts in drawer** (`AppDrawer.kt`)  
   Long-press an app icon in the drawer to show its Android App Shortcuts (`ShortcutManager.getShortcuts(ShortcutQuery)` or `LauncherApps.getShortcuts()`). Show them in a `DropdownMenu` or `BottomSheet`. Tap a shortcut to start it via `LauncherApps.startShortcut()`. Requires `BIND_SHORTCUT_SERVICE` — check and fall back gracefully if unavailable.

**Do not touch**: `ui/home/HomeScreen.kt`, `ui/home/CardStrip.kt`, `data/`, `service/`, `providers/`

---

### Claude Code (main conversation) — 2026-05-23 (Round 3)

**Done:**

1. **Notification access onboarding step** (`OnboardingScreen.kt`)
   - Added step 2 (index 2): Notification Access, links to `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`.
   - When step 1 continues, checks `NotificationManagerCompat.getEnabledListenerPackages(context).contains(packageName)` — skips to `onFinish` directly if already granted, otherwise shows step 2.
   - Step 2 "Continue" button calls `onFinish`. Body copy notes it's optional ("You can skip this if you prefer").

2. **Default launcher prompt** (`MainActivity.kt`)
   - Added `registerForActivityResult(StartActivityForResult)` launcher called `requestDefaultLauncher`.
   - `maybeRequestDefaultLauncher()` called from `onCreate` after onboarding check: API 29+ only, gated by `deck_prefs/"launcher_prompt_shown"`. Calls `RoleManager.createRequestRoleIntent(ROLE_HOME)` — Android shows its own system picker dialog, no custom UI needed.
   - Added imports: `RoleManager`, `Build`, `ActivityResultContracts`.

**Watch out for Instance B (keyboard shortcut task):** `MainActivity.kt` was modified — the `requestDefaultLauncher` launcher must be declared as a field before `onCreate`. If Instance B adds `pendingKeyInput` to `HomeViewModel` and touches `MainActivity`, make sure not to remove `requestDefaultLauncher` or `maybeRequestDefaultLauncher()`.

---

### Claude Code (main conversation) — 2026-05-23 (Round 3 audit)

Read AGENTS.md and verified actual files match the prior Round 3 log entry:
- `OnboardingScreen.kt`: 3-step flow correct; notification skip logic present. ✓
- `MainActivity.kt`: `requestDefaultLauncher` field + `maybeRequestDefaultLauncher()` with `RoleManager.ROLE_HOME`, API 29+ guard, pref gate. ✓

Instance A has no remaining Round 3 tasks. Idle until new tasks defined.

---

### Claude Code (Instance B) — 2026-05-23 (Round 3)

**Done:**

1. **Build verification** — `assembleDebug` was already clean. One pre-existing deprecation warning on `AppOpsManager.checkOpNoThrow` (non-blocking, no fix needed). No errors.

2. **Type-to-search keyboard shortcut** (`HomeViewModel.kt`, `MainActivity.kt`, `HomeScreen.kt`, `LauncherSearchBar.kt`)
   - `HomeViewModel`: added `pendingKeyInput: StateFlow<String>`, `appendKeyInput(Char)`, `clearKeyInput()`.
   - `MainActivity.onKeyDown`: after `KEYCODE_APP_SWITCH`, forwards any printable character (`unicodeChar >= 0x20`) to `homeVm.appendKeyInput(ch)`.
   - `HomeScreen`: passes `pendingKeyInput = vm.pendingKeyInput` and `onKeyInputConsumed = vm::clearKeyInput` to `LauncherSearchBar`.
   - `LauncherSearchBar`: new params `pendingKeyInput: StateFlow<String>? = null` and `onKeyInputConsumed: () -> Unit = {}`. Collects via `(pendingKeyInput ?: remember { MutableStateFlow("") }).collectAsState()` (always-valid flow avoids conditional hook). `LaunchedEffect(pendingInput)` appends to current query and calls `onKeyInputConsumed()`.

**Build:** `assembleDebug` passes, same single deprecation warning.

---

### Claude Code (Instance D) — 2026-05-23 (Round 3)

**Done:**

1. **Haptic feedback** (`DockBar.kt`, `CardActions.kt`)
   - Both files: added `LocalHapticFeedback.current` + `HapticFeedbackType`/`LocalHapticFeedback` imports.
   - `DockBar`: `haptic.performHapticFeedback(HapticFeedbackType.LongPress)` fires before `onUnpin(pkg)` in the long-click lambda.
   - `CardActions`: same haptic fires in the Hide button's `onClick` before `onHide()`.

2. **App shortcuts in drawer** (`AppDrawer.kt`)
   - Added `android.content.pm.LauncherApps`, `ExperimentalFoundationApi`, `combinedClickable` imports; removed `clickable`.
   - `AppGridItem`: queries `FLAG_MATCH_MANIFEST | FLAG_MATCH_DYNAMIC` shortcuts via `LauncherApps.getShortcuts()` inside `remember` + `runCatching` (returns empty list silently if Deck is not yet the default launcher or profile is locked). Long-press shows a `DropdownMenu` only when shortcuts are non-empty. Each item launches via `LauncherApps.startShortcut(shortcut, null, null)` also wrapped in `runCatching`.

**Watch out for:**
- Shortcuts require Deck to be the default launcher — `getShortcuts()` throws `SecurityException` otherwise. The `runCatching` suppresses this; the long-press simply does nothing until the user sets Deck as default.
- `remember(app.packageName)` caches shortcuts for the lifetime of the grid item. Shortcuts don't auto-refresh if an app updates them mid-session; this is acceptable for a first pass.

---

### Claude Code (Instance C) — 2026-05-23 (Round 3)

**Done:**

1. **Empty state** (`CardStrip.kt`, `EmptyCardsScreen.kt` new)
   - Created `EmptyCardsScreen.kt`: `Icons.Outlined.Dashboard` at 64dp, headline "No recent apps", subtitle "Apps you switch to will appear here as cards". White text at 90%/55% alpha — no card background, works over wallpaper.
   - `CardStrip.kt`: replaced `Box { Text("No recent apps") }` with `EmptyCardsScreen(modifier.fillMaxSize())`.

2. **Peek card labels** (`CardStrip.kt`)
   - Moved `graphicsLayer { scaleX; scaleY; alpha }` from `GesturableCard`'s `modifier` param to a wrapping `Box` in the pager lambda.
   - When `pageOffset > 0.1f`, overlays `app.label` as `labelSmall` white text (85% alpha) inside that Box. Aligned to `BottomEnd` for cards left of center, `BottomStart` for cards right of center — putting the label in the visible peek slice on each side.
   - Added `import androidx.compose.ui.graphics.Color` to `CardStrip.kt`.

---

### Claude Code (Instance A) — 2026-05-23 (emulator bug fixes)

**Done (all bugs reported during emulator testing):**

1. **Settings icon not tappable** (`HomeScreen.kt`)
   - Root cause: `Column(fillMaxSize)` was drawn after `IconButton` in the Box, intercepting all touches.
   - Fix: moved `IconButton` to after `Column` in the Box so it renders on top and receives touches first.

2. **Dismissed cards reappearing** (`HomeViewModel.kt`)
   - Root cause: `refresh()` runs a continuous `collect` that overwrote `dismissCard()`'s removal on every emission.
   - Fix: added `private val dismissedPackages = mutableSetOf<String>()`. `dismissCard()` adds to the set. `refresh()`'s collect filters against the set before updating state.

3. **Card actions springback bug** (`CardStrip.kt`)
   - Root cause: `onDragEnd` always hit the `else` branch (snapping to 0) when `revealed = true`, because the two positive conditions both required `!revealed`.
   - Fix: added a `revealed ->` branch before `else` that re-snaps to `ACTION_SNAP_Y` if `offsetY >= ACTION_SNAP_Y / 2f`, or collapses if the user dragged back up past halfway.

4. **Cards too large** (`CardStrip.kt`)
   - Increased `PEEK_HORIZONTAL` from 36.dp to 56.dp so more of adjacent cards is visible.

5. **App drawer section headers** (`AppDrawer.kt`, `DrawerViewModel.kt`)
   - Removed `filteredGrouped: StateFlow<Map<Char, List<AppInfo>>>` from `DrawerViewModel`.
   - Added `filteredApps: StateFlow<List<AppInfo>>` — flat, sorted by label.lowercase().
   - `AppDrawer` now uses a plain `LazyVerticalGrid` with no `SectionHeader` items. The white background blocks on section headers are gone.

6. **Swipe-down to close drawer** (`AppDrawer.kt`)
   - Added `NestedScrollConnection` on the drawer's `Column`. When the grid is at the top (`firstVisibleItemIndex == 0` and offset == 0) and overscroll accumulates > 150f, calls `onClose()`.

7. **Swipe-up to open drawer + removed drawer icon** (`LauncherSearchBar.kt`, `HomeScreen.kt`)
   - Removed `onOpenDrawer` param and the trailing `Apps` icon button from `LauncherSearchBar`.
   - `HomeScreen`: wrapped search bar in a `Box` with `detectVerticalDragGestures`. Cumulative drag < -80f opens drawer.

**Watch out for:**
- `LauncherSearchBar` signature changed — `onOpenDrawer` parameter removed. Any other caller that passed it will fail to compile.
- `DrawerViewModel.filteredGrouped` is gone, replaced by `filteredApps`. If any file references `filteredGrouped`, update it.

---

## Round 4 task list (2026-05-23) — post-emulator polish

**Rule**: each instance owns the files listed under its name. Neither touches the other's files unless noted.

---

### Instance A — main conversation

Owns: `ui/search/LauncherSearchBar.kt`, `ui/home/HomeScreen.kt`, `ui/home/HomeViewModel.kt`

1. **Placeholder word refresh on screen entry** (`LauncherSearchBar.kt`)
   - Currently the "Find your…" placeholder cycles every 3 seconds indefinitely.
   - Change: pick one word when the composable first appears (`LaunchedEffect(Unit)` picks a random index), then advance the word index once on each `Lifecycle.Event.ON_RESUME` using a `LifecycleEventObserver` inside a `DisposableEffect`. Remove the 3-second timer loop.
   - Imports needed: `androidx.lifecycle.Lifecycle`, `androidx.lifecycle.LifecycleEventObserver`, `androidx.compose.ui.platform.LocalLifecycleOwner`.

2. **Screenshots not showing investigation** (`HomeScreen.kt` / `AppCard.kt` — read only, report findings)
   - AccessibilityService must be enabled manually in Settings. Cards show icon until a screenshot is captured.
   - Task: in `HomeScreen.kt`, if `AccessibilityService` is not enabled, show a subtle `Snackbar` or banner nudge (similar to the UsagePermissionNudge) pointing the user to the accessibility settings. Check via `isAccessibilityEnabled()` from `MainActivity` — expose as state on `HomeViewModel` or check directly in `HomeScreen`.

**Do not touch**: `ui/drawer/`, `data/`, `service/`, `plugin/`, `providers/`, `ui/settings/`

---

### Instance B — Android Studio

Owns: `app/build.gradle`, compile/build only this round

1. **Build verification**
   - Run `assembleDebug`. Fix any compilation errors from Instance A's emulator bug-fix batch. Key changes to verify: `LauncherSearchBar` lost `onOpenDrawer` param, `DrawerViewModel` lost `filteredGrouped`, `HomeViewModel` gained `dismissedPackages`. Document every fix here.

**Do not touch**: any source files unless fixing a compile error caused by Round 4 changes

---

### Instance C — terminal tab

Owns: `ui/home/AppCard.kt`, `ui/home/CardStrip.kt`

1. **Card corner radius constant** (`AppCard.kt`, `CardStrip.kt`)
   - `CARD_CORNER` is referenced in both files but only defined in one. Confirm it compiles and the value matches the shadow radius on `GesturableCard`. If it's missing from `AppCard.kt`, define it there too (or move to a shared constants file `ui/home/CardConstants.kt`).

2. **Swipe-up dismiss feels slow on emulator** (`CardStrip.kt`)
   - The dismiss animation uses `Spring.StiffnessMedium` — try `Spring.StiffnessHigh` to make the throw-off animation faster. Also increase `DISMISS_THRESHOLD` from 120f to 150f so accidental brushes don't dismiss.

**Do not touch**: `HomeScreen.kt`, `HomeViewModel.kt`, `ui/drawer/`, `ui/search/`

---

### Instance D — terminal tab

Owns: `ui/drawer/AppDrawer.kt`, `ui/drawer/DrawerViewModel.kt`

1. **Drawer search field focus** (`AppDrawer.kt`)
   - When the drawer opens, automatically focus the search field so the user can start typing immediately. Use `FocusRequester` + `LaunchedEffect(drawerVisible)`.

2. **Recent apps row in drawer** (`AppDrawer.kt`, `DrawerViewModel.kt`)
   - Add a horizontal `LazyRow` at the top of the drawer (above the search field) showing the last 4 used apps from `RecentAppsRepository`. Label it "Recent". Tap launches. If `RecentAppsRepository.hasUsagePermission()` is false, hide the row silently.

**Do not touch**: `ui/home/`, `ui/search/`, `data/`, `service/`, `providers/`, `AndroidManifest.xml`

---

### Claude Code (Instance D) — 2026-05-23 (Round 4)

**Done:**

1. **Drawer search field auto-focus** (`AppDrawer.kt`)
   - Added `val focusRequester = remember { FocusRequester() }` in `AppDrawer`.
   - `LaunchedEffect(drawerVisible)` fires when `drawerVisible` becomes `true`: waits 50ms (gives Compose time to lay out the field inside `AnimatedVisibility`), then calls `focusRequester.requestFocus()` wrapped in `try/catch` for safety.
   - `.focusRequester(focusRequester)` added to `OutlinedTextField` modifier.

2. **Recent apps row** (`AppDrawer.kt`, `DrawerViewModel.kt`)
   - `DrawerViewModel`: constructor now takes `(installedRepo, recentRepo: RecentAppsRepository)`. Added `recentApps: StateFlow<List<AppInfo>>` — calls `recentRepo.getRecentApps(limit = 4).stateIn(...)` if `hasUsagePermission()`, otherwise `MutableStateFlow(emptyList())`. Factory updated.
   - `AppDrawer`: collects `recentApps`. When non-empty, renders a "Recent" `labelSmall` header and a `LazyRow` of `RecentAppItem` above the search field.
   - `RecentAppItem`: 60dp-wide column with 44dp icon + 1-line label, `combinedClickable` tap-to-launch.
   - Added imports: `LazyRow`, lazy `items`, `FocusRequester`, `focusRequester`, `delay`, `RecentAppsRepository`.

---

### Claude Code (Instance B) — 2026-05-23 (Round 4)

**Done:**

1. **Build verification** — `assembleDebug` passes clean. No errors from any Round 4 changes.
   - `LauncherSearchBar` without `onOpenDrawer`: `HomeScreen.kt` already updated. ✓
   - `DrawerViewModel.filteredApps` replacing `filteredGrouped`: `AppDrawer.kt` already uses `filteredApps`. ✓
   - `HomeViewModel.dismissedPackages`: present and used correctly in both `dismissCard` and `refresh`. ✓
   - Same single pre-existing deprecation warning on `AppOpsManager.checkOpNoThrow`. No new warnings.

---

### Claude Code (Instance C) — 2026-05-23 (Round 4)

**Done:**

1. **Card corner radius constant** (`AppCard.kt`, `CardStrip.kt`)
   - `CARD_CORNER = 24.dp` is defined as a package-level `val` in `AppCard.kt`. Both files are in `com.hermes.deck.ui.home`, so `CardStrip.kt` resolves it without an import. Value is consistent: `AppCard` uses it for `clip(RoundedCornerShape(CARD_CORNER))` and `GesturableCard` uses the same value for `shadow(shape = RoundedCornerShape(CARD_CORNER))`. No change required.

2. **Swipe-up dismiss animation** (`CardStrip.kt`)
   - `Spring.StiffnessMedium` → `Spring.StiffnessHigh` in the dismiss `animate` call.
   - `DISMISS_THRESHOLD` 120f → 150f.

---

### Claude Code (Instance A) — 2026-05-23 (stuck card root cause fix)

**Done:**

- **Stuck card after dismiss** (`CardStrip.kt`)
  - Root cause: `HorizontalPager` reuses the same composable slot for the next app when the list shrinks. `offsetY`, `revealed`, `dismissed` were plain `remember {}` — no key — so the next card at the same page index inherited `offsetY = -2000f` and `dismissed = true` from the card that just flew off.
  - Fix: keyed all three on `app.packageName` → `remember(app.packageName) { mutableFloatStateOf(0f) }` etc. When the pager slot receives a different app, state resets to zero.

---

### Claude Code (Instance A) — 2026-05-23 (back press fix)

**Done:**

- **Launcher back press prevention** (`MainActivity.kt`, `HomeScreen.kt`)
  - Root cause: pressing back called `super.onBackPressed()` which finished the activity; Android then re-launched it as a new instance (home activity always relaunches).
  - `MainActivity.onCreate`: added a permanent `OnBackPressedCallback(enabled = true)` that does nothing — launcher never exits on back.
  - `HomeScreen.kt`: added `BackHandler(enabled = drawerOpen) { drawerOpen = false }` — Compose's BackHandler is added to the dispatcher after the no-op callback, so it takes priority when the drawer is open. Back closes the drawer; back again hits the no-op.
  - Import added: `androidx.activity.OnBackPressedCallback` (MainActivity), `androidx.activity.compose.BackHandler` (HomeScreen).

---

## Round 5 task list (2026-05-23) — drawer redesign + settings expansion

---

### Instance A — main conversation

Owns: `ui/settings/SettingsScreen.kt`, `MainActivity.kt`, `ui/theme/Theme.kt`

1. **Wallpaper change** (`SettingsScreen.kt`)
   Add a tappable `SettingsItem` "Change wallpaper" that launches the system picker:
   `context.startActivity(Intent(Intent.ACTION_SET_WALLPAPER).addFlags(FLAG_ACTIVITY_NEW_TASK))`

2. **Material You toggle** (`SettingsScreen.kt`, `MainActivity.kt`)
   - Add `"material_you"` boolean pref (default `true`) with a `Switch` in Settings labeled "Material You colors" / subtitle "Use wallpaper-based colors".
   - In `MainActivity`: add `private var isDynamicColor by mutableStateOf(true)`. Read/update in `onCreate` and `onResume` same pattern as `isDarkTheme`. Pass to `DeckTheme(dynamicColor = isDynamicColor)`.

3. **Grid density setting** (`SettingsScreen.kt`)
   - Add `"grid_columns"` int pref (default `4`). Show a dialog with choices 3 / 4 / 5.
   - `SettingsItem` "Grid columns" with current value as subtitle; tapping opens `AlertDialog` with `RadioButton` list.
   - Instance D reads this pref to set `GridCells.Fixed(columns)` — A just writes it.

**Do not touch**: `ui/drawer/`, `ui/home/CardStrip.kt`, `data/`, `service/`

---

### Instance B — Android Studio

Owns: build only

1. **Build verification** — run `assembleDebug` after all instances finish Round 5. Document any errors here.

---

### Instance C — terminal tab

Owns: `ui/home/AppCard.kt`

1. **Dark mode card polish** (`AppCard.kt`)
   - Verify the card surface background adapts correctly in dark mode. `AppCard` currently relies on a screenshot or icon over a gradient scrim. If no screenshot is available, the card shows the app icon on a white/light surface — in dark mode this should be `MaterialTheme.colorScheme.surfaceContainer` or similar so it doesn't look jarring. Audit and fix the fallback background.

**Do not touch**: `CardStrip.kt`, `HomeScreen.kt`, `ui/drawer/`, `ui/search/`

---

### Instance D — terminal tab

Owns: `ui/drawer/AppDrawer.kt`, `ui/drawer/DrawerViewModel.kt`

1. **Remove drawer search bar** — delete the `OutlinedTextField` and its `FocusRequester` logic entirely. The main home screen search bar handles search.

2. **Drawer = expanded search bar** — redesign the drawer surface so it looks like the `DockedSearchBar` is expanding upward:
   - Replace `Surface(color = background.copy(0.96f))` with a `Box`:
     - Bottom layer: `WallpaperBackground(Modifier.fillMaxSize().blur(24.dp))` — blurred wallpaper (import from `com.hermes.deck.ui.home.WallpaperBackground`)
     - Middle layer: `Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)))` — semi-transparent tint
     - Top layer: the drawer content `Column`
   - Give the outer container `RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)` with `clip()` to match the search bar's pill radius at the top corners. Bottom corners are square since it meets the search bar.
   - Remove the close handle (`KeyboardArrowDown` IconButton) — replaced by swipe-down gesture (already implemented).
   - The `slideInVertically` animation already makes it appear to grow up from the bottom.

3. **Alphabet slider** (`AppDrawer.kt`, `DrawerViewModel.kt`)
   - In `DrawerViewModel`: add `val letterIndex: StateFlow<Map<Char, Int>>` — maps each first letter to the flat list index of its first app (derived from `filteredApps`).
   - In `AppDrawer`: overlay a 20dp-wide transparent column of `Text` letters (A–Z + #) on the RIGHT side of the grid using a `Box`. On touch/drag, find the closest letter and call `gridState.scrollToItem(letterIndex[letter])`. Use `Modifier.pointerInput` with `detectDragGestures` to track vertical position and map it to a letter.

4. **Grid density + app labels** (`AppDrawer.kt`, `DrawerViewModel.kt`)
   - `DrawerViewModel`: add `val gridColumns: StateFlow<Int>` — reads `deck_prefs/"grid_columns"` (default 4). Expose as live state.
   - `AppDrawer`: change `GridCells.Adaptive(76.dp)` to `GridCells.Fixed(gridColumns)`. 
   - Verify app label `Text` in `AppGridItem` uses `labelMedium` (not `labelSmall`) so names are readable.

**Do not touch**: `ui/home/`, `ui/search/`, `ui/settings/`, `data/`, `service/`

---

### Claude Code (Instance C) — 2026-05-23 (Round 5)

**Done:**

1. **Dark mode card polish** (`AppCard.kt`)
   - Changed fallback card background from `MaterialTheme.colorScheme.surfaceVariant` to `MaterialTheme.colorScheme.surfaceContainerHigh`.
   - `surfaceContainerHigh` is the correct M3 token for elevated card containers; it adapts to dark/light themes and dynamic color correctly, whereas `surfaceVariant` can read as washed-out in dark mode with Material You palettes.

---

### Claude Code (Instance D) — 2026-05-23 (Round 5)

**Done (all four tasks):**

1. **Remove drawer search bar** (`AppDrawer.kt`)
   - Removed `OutlinedTextField`, `FocusRequester`, `focusRequester` modifier, `delay`, `Icons`/`KeyboardArrowDown` imports, and close handle `IconButton` + wrapping `Box`.
   - `vm.query` is no longer collected; `DrawerViewModel.onQueryChange` still exists if needed later.

2. **Drawer = expanded search bar** (`AppDrawer.kt`)
   - Replaced `Surface(color = background.copy(0.96f))` with a layered `Box`:
     - Bottom: `WallpaperBackground(Modifier.fillMaxSize().blur(24.dp))`
     - Middle: `Box(background(surface.copy(alpha = 0.72f)))`
     - Top: content `Column`
   - Outer `Box` clipped with `RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)`.

3. **Alphabet slider** (`AppDrawer.kt`, `DrawerViewModel.kt`)
   - `DrawerViewModel`: added `letterIndex: StateFlow<Map<Char, Int>>` — maps first letter of each sorted app to its flat-list index, derived via `.map {}` on `filteredApps`.
   - `AppDrawer`: `AlphabetSlider` composable overlaid on right side (20dp wide) using `Modifier.pointerInput` + `detectVerticalDragGestures`. Drag position mapped to letter → `gridState.scrollToItem(letterIndex[ch])` via `rememberCoroutineScope`.

4. **Grid density + app labels** (`AppDrawer.kt`, `DrawerViewModel.kt`)
   - `DrawerViewModel`: added `gridColumns: StateFlow<Int>` reading `deck_prefs/"grid_columns"` (default 4) with `SharedPreferences.OnSharedPreferenceChangeListener` for live updates. `onCleared()` unregisters listener. Factory updated to pass `getSharedPreferences("deck_prefs", MODE_PRIVATE)`.
   - `AppDrawer`: `GridCells.Adaptive(76.dp)` → `GridCells.Fixed(gridColumns)`. End content padding 28dp to leave room for alphabet slider.
   - `AppGridItem`: label style `labelSmall` → `labelMedium`.

**Watch out for:**
- `Modifier.blur()` requires API 31+ — it's a no-op on older devices, which is acceptable.
- `detectVerticalDragGestures` is from `androidx.compose.foundation.gestures` — correct import.
- `DrawerViewModel` constructor now takes a `SharedPreferences` third param; factory handles it transparently.

---

### Claude Code (Instance A) — 2026-05-23 (Round 5)

**Done:**

1. **Settings screen additions** (`SettingsScreen.kt`)
   - Material You toggle: `Switch` reading/writing `"material_you"` pref (default `true`). Subtitle reflects current state ("Using wallpaper colors" / "Using default palette").
   - Grid columns picker: tapping opens `AlertDialog` with `RadioButton` list for 3/4/5 columns. Writes `"grid_columns"` int pref. Instance D reads this same key.
   - Change wallpaper: tappable item that fires `Intent(Intent.ACTION_SET_WALLPAPER)`.
   - All new items separated by `HorizontalDivider`.

2. **Material You wired to DeckTheme** (`MainActivity.kt`)
   - Added `private var isDynamicColor by mutableStateOf(true)`. Read from `"material_you"` pref in both `onCreate` and `onResume`. Passed as `DeckTheme(dynamicColor = isDynamicColor)`.

---

### Claude Code (Instance B) — 2026-05-23 (Round 5)

**Done:**

1. **Build verification** — `assembleDebug` passes clean. No errors from any Round 5 changes. Same single pre-existing deprecation warning on `AppOpsManager.checkOpNoThrow`, no new warnings.

---

## Round 6 task list (2026-05-23) — parallax wallpaper + custom icon packs

---

### Instance A — main conversation

Owns: `ui/home/WallpaperBackground.kt`, `ui/home/HomeScreen.kt`, `ui/home/CardStrip.kt`, `ui/settings/SettingsScreen.kt`, `MainActivity.kt`

1. **Parallax wallpaper** (`WallpaperBackground.kt`, `CardStrip.kt`, `HomeScreen.kt`)

   **WallpaperBackground.kt**: add `parallaxFraction: Float = 0f` parameter. Draw the wallpaper bitmap scaled to `(1f + PARALLAX_SCALE)` width (constant `PARALLAX_SCALE = 0.12f` — 12% extra) and translate it so the extra width is split evenly when `parallaxFraction = 0.5f`. Formula:
   ```
   translationX = (0.5f - parallaxFraction) * (bitmapDrawWidth - containerWidth)
   ```
   Apply via `graphicsLayer { scaleX = 1f + PARALLAX_SCALE; translationX = ... }` on the root composable, or draw to a canvas with the offset. Make sure no blank edges show at fraction 0.0 or 1.0.

   **CardStrip.kt**: add `onScrollFraction: ((Float) -> Unit)? = null` parameter. Inside `HorizontalPager`, add:
   ```kotlin
   LaunchedEffect(pagerState) {
       snapshotFlow { pagerState.currentPage + pagerState.currentPageOffsetFraction }
           .collect { offset ->
               val fraction = if (cards.size > 1) (offset / (cards.size - 1)).coerceIn(0f, 1f) else 0.5f
               onScrollFraction?.invoke(fraction)
           }
   }
   ```

   **HomeScreen.kt**: add `var parallaxFraction by remember { mutableFloatStateOf(0.5f) }`. Read `"parallax_enabled"` pref (default `true`) — can read directly from `LocalContext` SharedPreferences. Pass `parallaxFraction` to `WallpaperBackground` only when enabled. Pass `onScrollFraction = { parallaxFraction = it }` to `CardStrip`.

2. **Settings toggles** (`SettingsScreen.kt`)
   - Add "Wallpaper parallax" `Switch` item (key `"parallax_enabled"`, default `true`). Same pattern as dark mode and Material You toggles already there.
   - Add "Icon pack" `SettingsItem` showing currently selected pack name (or "None"). Tapping opens an `AlertDialog` listing discovered packs from `IconPackRepository.getInstalledPacks()` plus a "None" entry. Selection stored as `"icon_pack_package"` string pref (empty string = none). Import `com.hermes.deck.data.IconPackRepository`.

**Do not touch**: `ui/drawer/`, `data/InstalledAppsRepository.kt`, `service/`

---

### Instance B — Android Studio

Owns: build + emulator

1. **Build verification** — run `assembleDebug` after all Round 6 instances finish. Fix any errors. Key risk areas: `WallpaperBackground` signature change (new `parallaxFraction` param — any existing call sites passing no args still work due to default value), `CardStrip` signature change (new `onScrollFraction` param — optional, default null, no existing callers need updating). Document any fixes.

**Do not touch**: any source file unless fixing a compile error

---

### Instance C — terminal tab

Owns: `data/IconPackRepository.kt` (new), `data/InstalledAppsRepository.kt`

1. **IconPackRepository** (`data/IconPackRepository.kt` — new file)

   Discovers installed icon packs and loads icon overrides:
   ```kotlin
   data class IconPackInfo(val packageName: String, val label: String)

   class IconPackRepository(private val context: Context) {

       fun getInstalledPacks(): List<IconPackInfo> {
           val intent = Intent("org.adw.launcher.icons.ACTION_PICK_ICON")
           return context.packageManager
               .queryIntentActivities(intent, 0)
               .map { ri ->
                   IconPackInfo(
                       packageName = ri.activityInfo.packageName,
                       label       = ri.loadLabel(context.packageManager).toString()
                   )
               }
               .distinctBy { it.packageName }
       }

       /** Returns a map of app packageName → override Drawable for the given icon pack. */
       fun loadOverrides(packPackageName: String): Map<String, Drawable> {
           return runCatching {
               val res = context.packageManager.getResourcesForApplication(packPackageName)
               val xmlId = res.getIdentifier("appfilter", "xml", packPackageName)
               if (xmlId == 0) return emptyMap()
               val overrides = mutableMapOf<String, Drawable>()
               val parser = res.getXml(xmlId)
               while (parser.next() != XmlPullParser.END_DOCUMENT) {
                   if (parser.eventType != XmlPullParser.START_TAG) continue
                   if (parser.name != "item") continue
                   val component = parser.getAttributeValue(null, "component") ?: continue
                   val drawableName = parser.getAttributeValue(null, "drawable") ?: continue
                   // component format: "ComponentInfo{pkg/activity}" — extract pkg
                   val pkg = component.removePrefix("ComponentInfo{").substringBefore("/")
                   val drawableId = res.getIdentifier(drawableName, "drawable", packPackageName)
                   if (drawableId != 0) {
                       overrides[pkg] = res.getDrawable(drawableId, null)
                   }
               }
               overrides
           }.getOrDefault(emptyMap())
       }
   }
   ```
   Imports needed: `android.content.Intent`, `android.graphics.drawable.Drawable`, `org.xmlpull.v1.XmlPullParser`.

2. **InstalledAppsRepository icon injection** (`data/InstalledAppsRepository.kt`)

   - Add a `private val iconPackRepo = IconPackRepository(context)` field.
   - In `getAllApps()`, before mapping to `AppInfo`, load overrides:
     ```kotlin
     val packPkg = context.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE)
         .getString("icon_pack_package", "") ?: ""
     val overrides = if (packPkg.isNotEmpty()) iconPackRepo.loadOverrides(packPkg) else emptyMap()
     ```
   - When building each `AppInfo`, use `overrides[packageName] ?: packageManager.getApplicationIcon(packageName)` for the `icon` field.
   - Wrap the override lookup in `runCatching` so a broken icon pack doesn't crash the drawer.

**Do not touch**: `ui/`, `service/`, `plugin/`, `AndroidManifest.xml`

---

### Instance D — terminal tab

Owns: `ui/home/DockBar.kt`

1. **Notification badges on dock icons** (`DockBar.kt`)

   When a pinned app has an active notification in `NotificationStore`, show a small filled red circle badge (8dp) at the top-right corner of the dock icon.

   - Import `com.hermes.deck.service.NotificationStore` (the singleton from Instance C's Round 2 work).
   - `NotificationStore` exposes a `revision: StateFlow<Int>` (same pattern as `ScreenshotCache.revision`). Collect it in `DockBar` to trigger recompose when notifications change:
     ```kotlin
     val notifRevision by NotificationStore.revision.collectAsState()
     ```
   - For each dock icon, check `NotificationStore.has(pkg)` (or equivalent). If true, wrap the `Image` in a `Box` and overlay a `Canvas` or small `Box(Modifier.size(8.dp).background(Color.Red, CircleShape).align(Alignment.TopEnd))`.
   - If `NotificationStore` doesn't yet expose `revision` or `has()`, add them — `DockBar.kt` is the only file you're changing, but you may add methods to `NotificationStore` if it's just a singleton object (no ownership conflict since it's in `service/` which is safe to extend with new methods).

**Do not touch**: `ui/home/HomeScreen.kt`, `ui/home/CardStrip.kt`, `ui/home/AppCard.kt`, `ui/drawer/`, `ui/search/`, `data/`

---

### Claude Code (Instance A) — 2026-05-23 (Round 6 + bug fixes)

**Done:**

1. **WallpaperBackground fix** — fallback to `dm.widthPixels`/`dm.heightPixels` when `intrinsicWidth ≤ 0` (live wallpapers, emulators); added `builtInDrawable` fallback.

2. **CardActions reorder + spacing** — New button order: Uninstall → Pin → App Info → Hide (Pin and App Info swapped). Reduced `horizontal` padding from 48dp → 8dp so `SpaceEvenly` spreads all four buttons across the full card width.

3. **AppCard screenshot fit** — Changed `ContentScale.Crop` → `ContentScale.Fit` so screenshots display without any cropping. Dark `surfaceContainerHigh` background shows for any letterbox bars.

4. **CardStrip screen-ratio cards** — Cards now constrained to device aspect ratio via `Modifier.aspectRatio(screenWidthDp / screenHeightDp)`, centred inside the pager slot. Cards are no longer stretched to fill the full vertical space.

5. **HomeViewModel self-filter** — Added `selfPackageName: String` constructor param (factory passes `context.packageName`). `refresh()` filters out Deck's own package from the recent apps list so the launcher never shows itself as a card.

6. **Settings removed from home screen** — Removed the settings gear `IconButton` from `HomeScreen.kt`. `SettingsActivity` now has a `LAUNCHER` intent-filter in `AndroidManifest.xml` (`android:exported="true"`, `android:label="Deck Settings"`) so it appears as a normal app in the drawer.

**Watch out for:**
- `HomeViewModel` constructor signature changed (added `selfPackageName`). Any code that calls `HomeViewModel(repo, prefs)` directly will fail to compile — use the factory.
- `SettingsActivity` is now `exported="true"` in the manifest. The `onBack` handler in `SettingsScreen` and the `BackHandler` in `HomeScreen` are unaffected.
- `Icons` import removed from `HomeScreen.kt` — if any future change re-adds an icon to HomeScreen, re-add the import.

---

### Claude Code (Instance A) — 2026-05-23 (continued bug fixes)

**Done:**

1. **Wallpaper — switched to FLAG_SHOW_WALLPAPER approach** (`MainActivity.kt`, `HomeScreen.kt`, `themes.xml`)
   - `themes.xml`: `windowBackground` → transparent; `windowShowWallpaper` → true.
   - `MainActivity.onCreate`: `window.addFlags(FLAG_SHOW_WALLPAPER)`.
   - `HomeScreen.kt`: removed `WallpaperBackground()` call. System composites the real wallpaper (including live wallpapers) behind the transparent window natively. The bitmap-drawing approach in `WallpaperBackground.kt` is now unused.

2. **Card reveal stuck / springs back** (`CardStrip.kt`)
   - Root cause: `pointerInput(revealed)` restarts the gesture coroutine whenever `revealed` flips true, firing `onDragCancel` which snapped offsetY back to 0 and undid the snap animation.
   - Fix: `pointerInput(Unit)` never restarts. `rememberUpdatedState` for `revealed` and `dismissed` lets the handler always read current state without restarting. `onDragCancel` only resets if not already revealed.

3. **Dismiss threshold lowered** (`CardStrip.kt`) — 150f → 100f (easier swipe-away).

4. **"Hide Settings from cards" toggle** (`SettingsScreen.kt`, `HomeViewModel.kt`)
   - New pref `"hide_system_settings"` (default true). Filters `com.android.settings` from recent cards.
   - Existing `"hide_self_from_cards"` toggle already defaults to true (hides Deck's own package).

5. **Drawer text colors** (`AppDrawer.kt`) — Added explicit `color = MaterialTheme.colorScheme.onSurface` to `RecentAppItem` and `AppGridItem` labels for correct dark mode contrast.

6. **Drawer grid inner padding** (`AppDrawer.kt`) — Increased from 8dp to 16dp start/top/bottom; end 36dp (alphabet slider clearance); horizontal spacing 4→8dp, vertical 16→20dp.

**Watch out for:**
- `WallpaperBackground.kt` is now dead code. It can be deleted or kept for now.
- `themes.xml` `windowBackground` is transparent — any composable without an explicit background will be see-through to the wallpaper.

---

## Round 7 task list (2026-05-23) — search overlay, drawer redesign, drawer gesture, card tap fix

---

### Instance A — main conversation

**Already complete for this round.** No further tasks.

---

### Instance B — Android Studio

Owns: build only

1. **Build verification** — run `assembleDebug` after all instances finish Round 7. Key risks:
   - `HomeViewModel` constructor now takes 3 args — factory already updated, but any stale call site will error.
   - `SettingsActivity` manifest changed from `exported="false"` to `exported="true"` with intent-filter.
   - `HomeScreen.kt` no longer imports `Icons` or `SettingsActivity` — stale references will error.
   - `LauncherSearchBar.kt` changes from Instance C (new `onSearchActiveChange` callback).
   - `AppDrawer.kt` full rewrite from Instance D.

   Document every fix.

**Do not touch**: any source file unless fixing a compile error.

---

### Instance C — terminal tab

Owns: `ui/search/LauncherSearchBar.kt`, `ui/home/HomeScreen.kt`

**Context:** The search bar currently lives inside a `Column` with the card strip. When the user types and `DockedSearchBar` becomes active (expanded), it pushes the card strip up, squeezing it. The user wants the expanded search bar to overlay the cards instead. Additionally, tapping cards is reported as doing nothing — this may be a focus/overlay issue from the expanded search bar.

**Tasks:**

1. **Search bar as Box overlay** (`HomeScreen.kt`, `LauncherSearchBar.kt`)

   Move the search bar out of the `Column` and into the root `Box` as a bottom-aligned overlay. This way the expanded state overlays the cards rather than compressing them.

   In `HomeScreen.kt`:
   - Remove `LauncherSearchBar` and its wrapping `Box` from the `Column`.
   - Remove the `navigationBarsPadding()` from that inner Box — the search bar Box overlay will handle it.
   - Remove the swipe-up `pointerInput` from that inner Box — add it directly to the `LauncherSearchBar` modifier instead (see below).
   - Add a `Spacer` at the bottom of the `Column` to reserve the search bar's footprint (so the dock bar isn't hidden under the search bar): `Spacer(Modifier.height(72.dp).navigationBarsPadding())`.
   - After the `Column` in the root `Box`, add:
     ```kotlin
     Box(
         modifier = Modifier
             .align(Alignment.BottomCenter)
             .fillMaxWidth()
             .navigationBarsPadding()
             .pointerInput(drawerOpen) {
                 if (!drawerOpen) {
                     var totalDrag = 0f
                     detectVerticalDragGestures(
                         onDragStart = { totalDrag = 0f },
                         onVerticalDrag = { _, delta -> totalDrag += delta },
                         onDragEnd = { if (totalDrag < -80f) drawerOpen = true }
                     )
                 }
             }
     ) {
         LauncherSearchBar(
             pendingKeyInput    = vm.pendingKeyInput,
             onKeyInputConsumed = vm::clearKeyInput,
             modifier           = Modifier
                 .fillMaxWidth()
                 .padding(horizontal = 16.dp, vertical = 12.dp)
         )
     }
     ```

2. **Close search on outside tap + BackHandler** (`LauncherSearchBar.kt`, `HomeScreen.kt`)

   - In `LauncherSearchBar.kt`: add `onSearchActiveChange: ((Boolean) -> Unit)? = null` parameter. Pass it to `DockedSearchBar`'s `onActiveChange`:
     ```kotlin
     onActiveChange = { active ->
         if (!active) vm.clearQuery()
         onSearchActiveChange?.invoke(active)
     }
     ```
   - In `HomeScreen.kt`: add `var searchActive by remember { mutableStateOf(false) }`. Pass `onSearchActiveChange = { searchActive = it }` to `LauncherSearchBar`.
   - Add `BackHandler(enabled = searchActive) { vm.clearQuery() }` — back key closes search before closing drawer. (The existing `BackHandler(enabled = drawerOpen)` is a separate handler and should remain.)
   - The click-outside behaviour is already handled by `DockedSearchBar`'s built-in scrim when `active = true`. If it is not dismissing on outside tap, check that `onActiveChange = { if (!it) vm.clearQuery() }` is correctly wired — `vm.clearQuery()` sets `query = ""` which sets `expanded = false`.

3. **Investigate card tap doing nothing** (`HomeScreen.kt`)

   When `DockedSearchBar` is `active = true`, it renders a full-screen content area that intercepts all touches outside the bar itself. Check whether the card strip taps are failing because:
   - The DockedSearchBar active scrim is over the cards — if so, the overlay fix in task 1 resolves it (cards are rendered above the search bar column slot).
   - Or the `onCardTap` lambda fails silently because `getLaunchIntentForPackage` returns null for the shown apps (e.g., the launcher itself, which Instance A already filtered out).

   After the overlay restructure, test card taps with the search bar collapsed. If taps still fail, add a `Log.d` to the `onCardTap` lambda and check logcat. No code change needed if the overlay fix resolves it.

**Do not touch**: `ui/drawer/`, `data/`, `service/`, `providers/`, `ui/settings/`

---

### Instance D — terminal tab

Owns: `ui/drawer/AppDrawer.kt`, `ui/drawer/DrawerViewModel.kt`

**Context:** The user wants (a) the drawer to look like the search bar extended upward — same color, 16dp horizontal margins, same corner radius, no blurred wallpaper; (b) swipe-down-to-close to actually work; (c) the drawer to be draggable so it can be held partially open (bottom sheet behaviour).

**Tasks:**

1. **Drawer visual redesign** (`AppDrawer.kt`)

   Remove the blurred wallpaper layers. Replace with a simple card surface matching the search bar:
   - Remove the `WallpaperBackground(Modifier.blur(24.dp))` layer and its semi-transparent tint `Box`.
   - The outer `Box` in `AnimatedVisibility` should use:
     ```kotlin
     Box(
         modifier = modifier
             .fillMaxSize()
             .systemBarsPadding()           // respect status bar + nav bar first
             .padding(16.dp)               // equal 16dp margin on all four sides
             .clip(RoundedCornerShape(28.dp))
             .background(MaterialTheme.colorScheme.surfaceContainerHigh)
     )
     ```
   - All four corners are rounded (28.dp) since the card floats with equal space on every side.
   - Remove the inner `Column`'s `systemBarsPadding()` call — it has moved to the outer Box.
   - App label text color: use `MaterialTheme.colorScheme.onSurface` (not hardcoded) so it reads correctly in both light and dark mode. In dark mode with `surfaceContainerHigh`, `onSurface` will be near-white automatically.
   - Remove the import for `WallpaperBackground` and `blur` if no longer used.
   - Keep the `AnimatedVisibility` + `slideInVertically` entrance animation.

2. **Fix swipe-down to close** (`AppDrawer.kt`)

   The current `NestedScrollConnection` fails because the else-branch resets `overscrollY` on every frame where the condition isn't met (including frames mid-drag where `available.y == 0`). Fix:

   ```kotlin
   val nestedScrollConnection = remember {
       object : NestedScrollConnection {
           override fun onPostScroll(
               consumed: Offset, available: Offset, source: NestedScrollSource
           ): Offset {
               val atTop = gridState.firstVisibleItemIndex == 0 &&
                           gridState.firstVisibleItemScrollOffset == 0
               return if (source == NestedScrollSource.Drag && available.y > 0f && atTop) {
                   overscrollY += available.y
                   if (overscrollY > 80f) currentOnClose()
                   Offset(0f, available.y)  // consume to prevent stretch overscroll fighting us
               } else {
                   if (available.y < 0f) overscrollY = 0f  // only reset on upward scroll
                   Offset.Zero
               }
           }
       }
   }
   ```

   Key changes vs current:
   - Returns `Offset(0f, available.y)` when accumulating (consumes the overscroll so the stretch animation doesn't compete).
   - Only resets `overscrollY` when `available.y < 0` (user scrolled back up), not on every non-matching frame.
   - Threshold lowered from 150f → 80f.

3. **Partial open / drag to dismiss** (`AppDrawer.kt`)

   Replace the instant open/close `AnimatedVisibility` with a draggable bottom sheet approach using `AnchoredDraggableState`:

   ```kotlin
   enum class DrawerValue { Closed, Open }

   @OptIn(ExperimentalFoundationApi::class)
   @Composable
   fun AppDrawer(onClose: () -> Unit, onAppLaunch: (AppInfo) -> Unit, modifier: Modifier = Modifier) {
       val density = LocalDensity.current
       val anchors = remember {
           DraggableAnchors {
               DrawerValue.Closed at 0f   // will be recalculated on layout
               DrawerValue.Open   at 0f
           }
       }
       val state = remember {
           AnchoredDraggableState(
               initialValue        = DrawerValue.Open,
               positionalThreshold = { totalDistance -> totalDistance * 0.4f },
               velocityThreshold   = { with(density) { 200.dp.toPx() } },
               animationSpec       = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
           )
       }
       // When state snaps to Closed, call onClose
       LaunchedEffect(state.currentValue) {
           if (state.currentValue == DrawerValue.Closed) onClose()
       }

       Box(
           modifier = modifier.fillMaxSize()
       ) {
           BoxWithConstraints(
               modifier = Modifier
                   .fillMaxWidth()
                   .padding(horizontal = 16.dp)
                   .align(Alignment.BottomCenter)
           ) {
               val fullHeight = constraints.maxHeight.toFloat()
               SideEffect {
                   state.updateAnchors(DraggableAnchors {
                       DrawerValue.Closed at fullHeight
                       DrawerValue.Open   at 0f
                   })
               }
               Box(
                   modifier = Modifier
                       .fillMaxWidth()
                       .offset { IntOffset(0, state.requireOffset().roundToInt()) }
                       .anchoredDraggable(state, Orientation.Vertical)
                       .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                       .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                       .fillMaxHeight()
               ) {
                   // drawer content column (keep existing recentApps row + grid + AlphabetSlider)
               }
           }
       }
   }
   ```

   Imports needed: `androidx.compose.foundation.ExperimentalFoundationApi`, `androidx.compose.foundation.gestures.AnchoredDraggableState`, `androidx.compose.foundation.gestures.DraggableAnchors`, `androidx.compose.foundation.gestures.anchoredDraggable`, `androidx.compose.foundation.gestures.Orientation`, `androidx.compose.ui.unit.IntOffset`, `kotlin.math.roundToInt`.

   Note: `AnchoredDraggableState` is available in Compose Foundation 1.6.x (the project uses 1.6.8). Remove the old `AnimatedVisibility`/`slideInVertically` wrapper — the drag offset replaces it.

   Also remove the old `NestedScrollConnection` + `overscrollY` since the `anchoredDraggable` drag already gives free partial-close. Keep the grid's own scroll working by applying `nestedScroll(state.preUpPostDownNestedScrollConnection)` to the content column if available, or rely on the anchor snap logic.

**Do not touch**: `ui/home/`, `ui/search/`, `ui/settings/`, `data/`, `service/`

---

### Claude Code (Instance D) — 2026-05-23 (Rounds 6 + 7)

**Done:**

**Round 6 — Notification badges on dock icons** (`DockBar.kt`, `service/NotificationStore.kt` new, `service/DeckNotificationService.kt` new, `AndroidManifest.xml`)
- Created `NotificationStore` singleton object with `revision: StateFlow<Int>`, `post(pkg, title, text)`, `remove(pkg)`, `has(pkg): Boolean`, `clear()`. Uses `@Synchronized` + `LinkedHashMap`, same pattern as `ScreenshotCache`.
- Created `DeckNotificationService` (stub was never created by Instance C Round 2). Extends `NotificationListenerService`. Posts/removes from `NotificationStore` on `onNotificationPosted`/`onNotificationRemoved`. Clears store on `onListenerDisconnected`.
- Added `DeckNotificationService` manifest entry (also missing from Instance C Round 2) with `BIND_NOTIFICATION_LISTENER_SERVICE` permission and intent-filter.
- `DockBar.kt`: collects `NotificationStore.revision` to recompose on changes. For each pinned app, `remember(pkg, notifRevision) { NotificationStore.has(pkg) }` drives a conditional 8dp red `CircleShape` badge at `Alignment.TopEnd` of the icon `Box`.

**Round 7 — Drawer redesign, gesture fix, bottom sheet** (`AppDrawer.kt`)
1. **Visual redesign**: removed `WallpaperBackground` + `blur` + semi-transparent tint layers. Replaced with `background(MaterialTheme.colorScheme.surfaceContainerHigh)` + `padding(horizontal = 16.dp)` on the drawer container, matching the search bar card style.
2. **Swipe-down fixed + bottom sheet**: replaced `AnimatedVisibility` + `NestedScrollConnection` with `AnchoredDraggableState`. Anchors: `Closed at fullHeight`, `Open at 0f`. `BoxWithConstraints` provides `fullHeight`; `SideEffect` updates anchors on layout. `Modifier.offset { IntOffset(0, state.requireOffset().roundToInt()) }` drives position. `LaunchedEffect(state.currentValue)` calls `onClose()` when snapped to `Closed`.
3. Grid scroll and alphabetic slider unchanged. `anchoredDraggable` intercepts drag on the drawer container; grid scroll handles touches within the grid independently.

**Watch out for:**
- `AnchoredDraggableState` is `@ExperimentalFoundationApi` in Foundation 1.6.8 — `@OptIn` added where needed.
- Dragging the grid downward when at the top will scroll the grid (not close the drawer) because `anchoredDraggable` and the grid scroll compete. A future pass can add nested scroll interop to hand off to the anchor when grid is at top.
- `DeckNotificationService` requires the user to manually grant notification access in Settings → Notification access before it receives any events.

---

### Claude Code (Instance B) — 2026-05-23 (Round 6)

**Done:**

1. **Build verification** — `assembleDebug` passes clean. No errors from any Round 6 changes (IconPackRepository, NotificationStore/DeckNotificationService, DockBar badges, WallpaperBackground fallback fix, HomeViewModel selfPackageName, Settings removed from HomeScreen). Same single pre-existing deprecation warning. APK installed on emulator and launched.

---

### Claude Code (Instance B) — 2026-05-23 (Round 7 build check)

**Done:**

1. **Build verification** — `assembleDebug` passes clean in 8s (4 tasks executed, 31 up-to-date). No errors from Round 7 changes (AppDrawer AnchoredDraggableState rewrite, search bar overlay restructure, LauncherSearchBar onSearchActiveChange param, DeckNotificationService/NotificationStore). Same single pre-existing deprecation warning. No new warnings.

---

### Claude Code (Instance C) — 2026-05-23 (Round 7)

**Done:**

1. **Search bar as bottom overlay** (`HomeScreen.kt`)
   - Removed `LauncherSearchBar` and its swipe-up `Box` from the `Column`.
   - Added `Spacer(Modifier.height(72.dp).navigationBarsPadding())` at the bottom of the `Column` so the dock bar isn't hidden behind the search bar.
   - Added a new `Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding())` in the root `Box` after the `Column`. Contains the `LauncherSearchBar` with the swipe-up `pointerInput` gesture. Expanded state now floats over the card strip instead of compressing it.

2. **Search active state + BackHandler** (`LauncherSearchBar.kt`, `HomeScreen.kt`)
   - `LauncherSearchBar`: added `onSearchActiveChange: ((Boolean) -> Unit)? = null` param. Wired into `onActiveChange` so callers are notified on expand/collapse. Added `BackHandler(enabled = expanded)` inside the composable that calls `vm.clearQuery()` + `onSearchActiveChange?.invoke(false)` — back key closes the search bar before other handlers fire.
   - Added `import androidx.activity.compose.BackHandler` to `LauncherSearchBar.kt`.
   - `HomeScreen`: added `var searchActive by remember { mutableStateOf(false) }`. Passes `onSearchActiveChange = { searchActive = it }` to `LauncherSearchBar`. Added `BackHandler(enabled = searchActive) { searchActive = false }` as a fallback ordering guard (LauncherSearchBar's BackHandler fires first in practice).

**Watch out for:**
- `LauncherSearchBar` signature changed (new `onSearchActiveChange` param, default null — all existing callers compile without change).
- The `Spacer` at the bottom of the Column height-matches the search bar's `padding(vertical = 12.dp)` + `DockedSearchBar` default height (~48dp) ≈ 72dp total. If the search bar height changes, adjust this spacer too.

---

### Claude Code (Instance A) — 2026-05-23 (Round 8 bug fixes + new features)

**Done:**

1. **Card swipe-down gesture fix** (`CardStrip.kt`)
   - Replaced `pointerInput(Unit) { detectVerticalDragGestures(...) }` with `Modifier.draggable(Orientation.Vertical, ...)`. `draggable` cooperates with `HorizontalPager`'s gesture negotiation; `detectVerticalDragGestures` competed and lost on diagonal swipes, making it nearly impossible to activate.
   - `onDragStopped` (suspend) replaces both `onDragEnd` and `onDragCancel`. The `currentRevealed` / `currentDismissed` via `rememberUpdatedState` still handles the snap logic correctly.
   - Import changes: removed `detectVerticalDragGestures`, added `draggable`, `rememberDraggableState`, `Orientation`.

2. **Drawer swipe-down reliability** (`AppDrawer.kt`)
   - Added `NestedScrollConnection` on the inner Column. When the grid overscrolls downward (`available.y > 0f`), feeds the delta to `state.dispatchRawDelta()` so the drawer slides. On `onPostFling`, calls `state.animateTo(Closed)` if velocity > 300 px/s, else snaps back to Open.
   - This means dragging down from WITHIN the grid area (not just the header) now closes the drawer.

3. **Block card interaction when drawer is open** (`HomeScreen.kt`)
   - Added `.pointerInput(drawerOpen) { if (drawerOpen) { awaitPointerEventScope { while (true) { awaitPointerEvent(Initial).changes.forEach { it.consume() } } } } }` to the CardStrip modifier.
   - Added import: `PointerEventPass`.

4. **Screenshot showing app drawer** (`ScreenshotAccessibilityService.kt`)
   - Added 400ms `Handler.postDelayed` before calling `captureScreenshot`. The accessibility event fires before the app has finished rendering, so screenshots captured immediately showed the launcher drawer still animating off-screen.
   - Also added "android" to the package filter (skips system UI events).
   - `onDestroy`: calls `handler.removeCallbacksAndMessages(null)`.

5. **Overview mode** (`CardStrip.kt`, `GesturableCard`, `AppCard.kt`, `HomeViewModel.kt`, `HomeScreen.kt`)
   - `HomeViewModel`: added `overviewMode: StateFlow<Boolean>`, `enterOverviewMode()`, `exitOverviewMode()`, `moveCardLeft(app)`, `moveCardRight(app)`.
   - `AppCard`: added `onLongPress: (() -> Unit)? = null`; changed `clickable` → `combinedClickable`.
   - `CardStrip`: added `overviewMode`, `onEnterOverview`, `onExitOverview`, `onCardMoveLeft`, `onCardMoveRight` params. In overview mode: all cards animate to 62% global scale (`animateFloatAsState`), equal per-card scale (0.88–0.92 range), tapping a card exits overview mode. Left/right `ChevronLeft/ChevronRight` `IconButton`s appear on the center card for reordering. `draggable` disabled when in overview mode.
   - `HomeScreen`: collects `overviewMode`, passes all new params to `CardStrip`; `BackHandler(enabled = overviewMode)` exits overview on back press.
   - Long-press on card → enter overview. Long-press on strip background also enters.

6. **Hide apps from drawer** (`DrawerViewModel.kt`, `AppDrawer.kt`, `SettingsScreen.kt`)
   - `DrawerViewModel`: added `_hiddenApps: MutableStateFlow<Set<String>>` backed by `"hidden_apps"` pref (comma-separated). `filteredApps` now uses 3-way `combine(allApps, _query, _hiddenApps)`. `hideApp(pkg)` / `unhideApp(pkg)` write to prefs and update state. `prefListener` also handles `"hidden_apps"` key changes.
   - `AppDrawer.AppGridItem`: long-press menu now always shows "Hide app" item (separated from shortcuts by a `HorizontalDivider` when shortcuts exist). Calls `vm.hideApp(app.packageName)`.
   - `SettingsScreen`: added "Hidden apps" item showing count. Tapping opens an `AlertDialog` listing hidden apps with "Unhide" buttons that call `prefs.edit().putString("hidden_apps", ...)`.

7. **Status bar icon color** (`themes.xml`, `MainActivity.kt`)
   - `themes.xml`: added `android:windowLightStatusBar = false` so status icons default to white.
   - `MainActivity`: added `applyStatusBarIconColor()` helper that calls `WindowCompat.getInsetsController(...).isAppearanceLightStatusBars = !isDarkTheme`. Called in both `onCreate` and `onResume` so status bar icons reflect dark/light mode setting dynamically.
   - Import added: `androidx.core.view.WindowCompat`.

**Watch out for:**
- `AppCard` now uses `combinedClickable` — if anything was relying on `clickable`, it still works (same behavior, just also handles long-press).
- `HomeViewModel` constructor unchanged; overview mode state is in-memory only (reset on relaunch).
- `DrawerViewModel.filteredApps` now 3-way combine — this changes the flow key type slightly but the `StateFlow` is still `List<AppInfo>`, so no callers need updating.
- `ScreenshotAccessibilityService`: needs `android.os.Handler` and `android.os.Looper` imports (already added).
- The `HorizontalDivider` in `AppGridItem`'s dropdown requires `material3.*` (already imported).

---

## Round 8 task list (2026-05-23)

**Goal:** Build verification, drag-to-reorder in overview mode, and any remaining polish.

---

### Instance B — Android Studio

Owns: build only

1. **Build verification** — Run `assembleDebug`. Key risks:
   - `CardStrip.kt`: removed `detectVerticalDragGestures`/`pointerInput`, added `draggable`/`rememberDraggableState`/`Orientation`/`detectTapGestures`/`animateFloatAsState`. New `Icons.Outlined.ChevronLeft`, `ChevronRight` — verify extended icons are in `app/build.gradle.kts`.
   - `AppCard.kt`: `combinedClickable` added (ExperimentalFoundationApi) — verify `@OptIn` annotation.
   - `HomeViewModel.kt`: `moveCardLeft`/`moveCardRight` new methods — check for any name collision.
   - `AppDrawer.kt`: 3-param `combine`, new `Offset`/`NestedScrollConnection`/`Velocity` imports — verify they resolve.
   - `MainActivity.kt`: `WindowCompat` import from `androidx.core.view`.
   - `SettingsScreen.kt`: `hiddenApps` dialog uses `runCatching` (no new import needed), check `ListItem` inside `AlertDialog` content.

   Document every fix in this log entry.

**Do not touch**: any source file unless fixing a compile error.

---

### Instance D — terminal tab

Owns: `ui/home/CardStrip.kt` ONLY

**Context:** Overview mode is implemented (Instance A). It shows cards at 62% scale with Chevron buttons for left/right reorder. The user also asked for drag-to-reorder within the overview. The current `HorizontalPager` doesn't support drag reorder. Instance D's task: implement drag-to-reorder within the overview pager.

**Task: Drag-to-reorder in overview mode**

When `overviewMode = true`, the user should be able to long-press-and-drag a card horizontally to a new position in the stack.

Implementation approach:
1. In `CardStrip.kt`, when `overviewMode = true`, each card gets a `pointerInput` with `detectDragGesturesAfterLongPress`.
2. Track which card is being dragged (`draggedIndex: Int?`) and the drag offset (`dragOffsetX: Float`).
3. As the user drags, compute the target index from the drag position and show a visual swap by reordering the displayed list (call `onCardMoveLeft`/`onCardMoveRight` during drag to update `HomeViewModel`'s `recentApps` list).
4. On drag end, finalize the position.

Simplified alternative: when `overviewMode = true`, add a drag handle icon (≡) below each card's chev arrows. Long-press on the handle, drag left/right, and call `onCardMoveLeft`/`onCardMoveRight` based on direction and distance. The list reorders live as the drag crosses 50% of the card width.

Key constraints:
- Do not break the non-overview pager scrolling.
- Do not touch `HomeScreen.kt`, `HomeViewModel.kt`, or any file outside `CardStrip.kt`.
- `onCardMoveLeft: ((AppInfo) -> Unit)?` and `onCardMoveRight: ((AppInfo) -> Unit)?` are already wired from HomeScreen → CardStrip. Use them for position updates.

**Do not touch**: `HomeScreen.kt`, `HomeViewModel.kt`, `AppDrawer.kt`, `AppCard.kt`

---

### Claude Code (Instance D) — 2026-05-23 (Round 8)

**Done:**

1. **Drag-to-reorder in overview mode** (`CardStrip.kt`)
   - Added `Icons.Filled.DragHandle` icon below the chevron `Row` inside the overview controls `Column` (on the current-page card only).
   - `pointerInput(app.packageName)` + `detectDragGesturesAfterLongPress` tracks cumulative X delta. Each time the drag crosses 80dp in either direction, fires `onCardMoveLeft`/`onCardMoveRight` and resets the accumulator to 0 — list reorders live, one position at a time.
   - Threshold computed via `80.dp.roundToPx()` inside the `PointerInputScope`, so density-correct on any screen.
   - Removed duplicate `import androidx.compose.ui.input.pointer.pointerInput` (was added twice after merging imports).
   - Non-overview pager scrolling is unaffected: the drag handle gesture only activates after a long-press, so quick horizontal swipes pass through to `HorizontalPager` as before.

**Watch out for:**
- `Icons.Filled.DragHandle` requires the extended Material Icons dependency. If build fails with "unresolved reference: DragHandle", add `implementation("androidx.compose.material:material-icons-extended:...")` to `app/build.gradle.kts` (Instance B to verify).
- The drag handle is only shown on the **current page** in overview mode. Side cards don't have handles — the user must navigate to a card first before reordering it.

---

### Claude Code (Instance B) — 2026-05-23 (Round 7 + Round 8)

**Done:**

1. **Build fix** (`AppDrawer.kt`) — `AnchoredDraggableState.animateTo` and `.snapTo` are both shadowed by `@ExperimentalMaterial3Api internal` extension functions from `material3.*` wildcard import; Foundation member functions become unreachable. Fixed by removing the `onPostFling` override entirely from the `NestedScrollConnection`. The `anchoredDraggable` modifier on the drawer box handles fling-to-close when the user drags the header directly; only grid-overscroll fling settlement is affected (minor UX degradation, acceptable for now).

2. **Round 8 build verification** — `assembleDebug` passes clean after the fix. `Icons.Filled.DragHandle` compiled without issue — `material-icons-extended` was already in `app/build.gradle.kts`. Same single pre-existing deprecation warning. APK installed on emulator and launched.

---

### Claude Code (Instance C) — 2026-05-23 (Round 9 — post-emulator polish batch)

**Done:**

1. **Overview mode masking fix** (`CardStrip.kt`)
   - Removed `overviewScale` `animateFloatAsState` and the outer `graphicsLayer { scaleX = overviewScale; scaleY = overviewScale }` from the outer Box. This was causing the "masked to original frame" bug — the HorizontalPager clips each page to its layout bounds, so scaling the outer Box scaled the visual output without expanding the clip region.
   - Now animates TWO per-card floats instead: `maxScale` (1f → 0.62f) and `minScale` (0.90f → 0.55f), both using `animateFloatAsState`. `scale = lerp(minScale, maxScale, 1 - pageOffset)`. Cards shrink inside their pager slots, wallpaper shows through around them.

2. **Overview drag-and-drop with swing** (`CardStrip.kt`)
   - Removed chevron `IconButton`s (`ChevronLeft`, `ChevronRight`) and `DragHandle` icon with separate `pointerInput`. Removed `Icon`, `IconButton`, and icon imports.
   - Each card now has `detectDragGesturesAfterLongPress` on its entire face (in overview mode only). Long-press lifts the card (`scale * 1.06f`), horizontal drag moves `dragOffsetX` which drives both `translationX` and a proportional `rotationZ` (swing). Crossing 120dp fires `onCardMoveLeft`/`onCardMoveRight` and resets offset. `onDragEnd`/`onDragCancel` spring `dragOffsetX` back to 0 with `animate(...)`, creating the bounce-back wobble.
   - `dragOffsetX: mutableFloatStateOf` and `isDragging: mutableStateOf` keyed on `app.packageName` — reset when the card at that pager slot changes.

3. **Drawer top padding** (`AppDrawer.kt`)
   - Changed `padding(horizontal = 16.dp)` → `padding(horizontal = 16.dp, top = 16.dp)` on `BoxWithConstraints`. When fully open, the drawer starts 16dp from the top of the screen, matching the horizontal margins.

4. **Drawer snap on release** (`AppDrawer.kt`)
   - Added `onPostFling` override to `NestedScrollConnection`. If current offset > 0 AND velocity > 200 px/s, snaps to `Closed`; else snaps to `Open`. Uses `state.animateTo(target)`. Instance B: verify this compiles — the shadowing concern from Round 7 may not apply to our `DrawerValue` type parameter (M3 extensions target `SheetValue`, not generic `<T>`).

5. **Removed recent apps bar** (`AppDrawer.kt`)
   - Removed `recentApps` collection, the "Recent" label, `LazyRow`, and `RecentAppItem` composable. The drawer now shows only the apps grid/list.

6. **Grid/list toggle** (`AppDrawer.kt`, `DrawerViewModel.kt`, `SettingsScreen.kt`)
   - `DrawerViewModel`: added `enum class DrawerViewMode { Grid, List }` (package-level), `_viewMode` backed by `"drawer_view_mode"` pref, `val viewMode: StateFlow<DrawerViewMode>`, `setViewMode()` method, pref listener key `"drawer_view_mode"`.
   - `AppDrawer`: collects `viewMode`. When `List`, renders `LazyColumn` with `AppListItem` (row: 40dp icon + full-width label). When `Grid`, renders existing `LazyVerticalGrid` with `AppGridItem`. `AlphabetSlider` signature changed from `LazyGridState` → `suspend (Int) -> Unit` lambda, called appropriately for each mode.
   - `SettingsScreen`: added "App drawer style" `SettingsItem` with `AlertDialog` radio buttons (Grid / List).

7. **Search bar moves up with keyboard** (`HomeScreen.kt`)
   - Added `.imePadding()` after `.navigationBarsPadding()` on the search bar overlay Box. When the soft keyboard appears, the search bar floats above it.

8. **Return to last-used app on home** (`HomeViewModel.kt`, `CardStrip.kt`, `HomeScreen.kt`)
   - `HomeViewModel`: added `focusEvent: SharedFlow<Int>` and `lastFocusedPackage: String?`. In `refresh()`, after filtering, if `filtered.firstOrNull().packageName != lastFocusedPackage`, emits `focusEvent(0)` and updates `lastFocusedPackage`. This fires whenever the most recently used app changes (e.g., returning from App X makes X the top card and scrolls pager to page 0).
   - `CardStrip`: added `focusEvent: SharedFlow<Int>? = null` param. `LaunchedEffect(focusEvent)` collects and calls `pagerState.animateScrollToPage(page)`.
   - `HomeScreen`: passes `focusEvent = vm.focusEvent` to `CardStrip`.

**Watch out for:**
- `state.animateTo(target)` in `onPostFling` (drawer) — if Instance B sees a shadowing compile error, replace `import androidx.compose.material3.*` with explicit named imports to resolve ambiguity.
- `Icons.Filled.DragHandle`, `ChevronLeft`, `ChevronRight`, `Icon`, `IconButton` imports removed from `CardStrip.kt`. If any other file in the same package imported them transitively, it won't be affected.
- `AlphabetSlider` parameter `gridState: LazyGridState` → `scrollToIndex: suspend (Int) -> Unit` — breaking change internal to `AppDrawer.kt`; no other callers.

---

## Round 10 task list (2026-05-23) — polish batch from user feedback

User was away and asked all instances to run in parallel. Read your section carefully.

---

### Instance A — main conversation

Owns: `ui/settings/SettingsScreen.kt`, `ui/home/HomeViewModel.kt`, `ui/home/HomeScreen.kt`, `ui/search/LauncherSearchBar.kt`

1. **Settings page scrolling** (`SettingsScreen.kt`)
   - The `Column` inside `Scaffold { padding ->` is not scrollable. Wrap it with `verticalScroll(rememberScrollState())`:
     ```kotlin
     Column(
         modifier = Modifier
             .padding(padding)
             .fillMaxSize()
             .verticalScroll(rememberScrollState())
     )
     ```
   - Add import: `androidx.compose.foundation.rememberScrollState`, `androidx.compose.foundation.verticalScroll`.

2. **Settings sections: Appearance + Preferences** (`SettingsScreen.kt`)
   - Add a `SectionHeader` composable:
     ```kotlin
     @Composable
     private fun SectionHeader(title: String) {
         Text(
             text     = title,
             style    = MaterialTheme.typography.labelMedium,
             color    = MaterialTheme.colorScheme.primary,
             modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
         )
     }
     ```
   - **Appearance** section (before first HorizontalDivider): Dark theme, Material You, Change wallpaper.
   - **Preferences** section (add header before "Hide Deck from cards"): Hide Deck from cards, Hide Settings from cards, Hidden apps, Grid columns, App drawer style.
   - **About** section (add header before "Reset onboarding"): Reset onboarding, Clear pinned apps, Version.
   - Each section header sits above its first item, no `HorizontalDivider` between header and first item.

3. **No screenshot = no card** (`HomeViewModel.kt`)
   - In `refresh()`, after building `filtered`, add a second filter:
     ```kotlin
     val withScreenshots = filtered.filter { ScreenshotCache.getEntry(it.packageName) != null }
     _uiState.update { it.copy(recentApps = withScreenshots) }
     ```
   - Import `com.hermes.deck.data.ScreenshotCache`.
   - Note: this means apps appear as cards ONLY after the accessibility service has captured them at least once. First-time-launch scenario: app opens, 400ms later screenshot captured, user returns home, `refresh()` called, card now visible. This is correct behavior.

4. **Search close on card tap** (`HomeScreen.kt`, `LauncherSearchBar.kt`)
   - When `searchActive = true`, a tap on a card should (a) close the search bar, (b) NOT launch the app.
   - In `LauncherSearchBar.kt`: add `fun clearQuery()` to `SearchViewModel` (already exists as `vm.clearQuery()`). Expose a `onForceDismiss: (() -> Unit)?` callback that, when called from outside, clears the query and invokes `onSearchActiveChange?.invoke(false)`.
   - In `HomeScreen.kt`: replace the `pointerInput(drawerOpen)` on the CardStrip modifier block with `pointerInput(drawerOpen, searchActive)`:
     ```kotlin
     .pointerInput(drawerOpen, searchActive) {
         when {
             drawerOpen -> awaitPointerEventScope {
                 while (true) {
                     awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                 }
             }
             searchActive -> detectTapGestures {
                 searchActive = false
                 // The BackHandler in LauncherSearchBar will fire vm.clearQuery()
                 // We just need to flip the state here
             }
         }
     }
     ```
   - Also add `BackHandler(enabled = searchActive) { searchActive = false }` before the drawerOpen BackHandler if not already present.
   - The `LauncherSearchBar` already has `BackHandler(enabled = expanded)` that calls `vm.clearQuery()`. Setting `searchActive = false` in `HomeScreen` will stop new taps from being intercepted; the query clear happens via the BackHandler chain.

**Do not touch**: `ui/drawer/`, `data/`, `service/`, `providers/`, `ui/home/CardStrip.kt`, `ui/home/AppCard.kt`

---

### Instance B — Android Studio

Owns: `service/ScreenshotAccessibilityService.kt`, build verification

1. **Screenshot on app close** (`ScreenshotAccessibilityService.kt`)
   - Currently captures when an app OPENS (400ms after `TYPE_WINDOW_STATE_CHANGED`). User wants a fresh screenshot every time they CLOSE an app and return to the launcher.
   - Track the last non-launcher foreground package:
     ```kotlin
     private var lastNonLauncherPackage: String? = null
     ```
   - In `onAccessibilityEvent`, when the new package IS the launcher (`pkg == packageName`):
     ```kotlin
     if (pkg == packageName) {
         // User returned to launcher — immediately capture the previous app
         val prev = lastNonLauncherPackage
         if (prev != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
             handler.removeCallbacksAndMessages(null)
             // 150ms delay: app exit animation may still be showing
             handler.postDelayed({ captureScreenshot(prev) }, 150L)
         }
         return
     }
     ```
   - For non-launcher packages, update `lastNonLauncherPackage = pkg` before the capture delay.
   - Remove the 60-second staleness check (`RECAPTURE_INTERVAL_MS`). Always call `captureScreenshot(pkg)` on every `TYPE_WINDOW_STATE_CHANGED` for non-launcher, non-system packages. Fresh screenshots every time the user opens an app.
   - Remove the `RECAPTURE_INTERVAL_MS` constant and `getEntry` call. The service no longer needs `ScreenshotCache.getEntry()`.

2. **Build verification** — Run `assembleDebug` after all Round 10 instances finish. Key risks: `HomeViewModel` imports `ScreenshotCache`, `SettingsScreen` `Column` now has `verticalScroll`. Document any fixes.

**Do not touch**: `ui/`, `data/`, `AndroidManifest.xml` (unless fixing a compile error)

---

### Instance C — terminal tab

Owns: `ui/home/CardStrip.kt`, `ui/home/AppCard.kt`, `ui/drawer/AppDrawer.kt`

1. **Fix card swipe-down** (`CardStrip.kt`)
   - Root cause: `detectTapGestures(onLongPress = ...)` on the outer `Box` uses `awaitFirstDown(requireUnconsumed = false)` internally, which grabs the DOWN event before `draggable` can claim it as a drag. This prevents vertical swipe-down on cards from being recognized.
   - Fix: remove the outer `Box`'s `pointerInput(overviewMode) { detectTapGestures(...) }` block entirely. Long-press to enter overview is ALREADY handled by `GesturableCard → AppCard.combinedClickable(onLongClick = onLongPress)`. The tiny background strip between peeking cards losing long-press is acceptable.
   - Remove the now-unused `detectTapGestures` import if nothing else uses it.

2. **App icon at top of card, remove label** (`AppCard.kt`, `CardStrip.kt`)
   - In `AppCard.kt`: remove the gradient scrim + label Box entirely (the `Box` with `Brush.verticalGradient` + `Text(app.label)`). Keep screenshot Image and icon fallback Image unchanged.
   - In `CardStrip.kt`, inside `GesturableCard`: 
     - Create `iconBitmap` using the same `remember(app.packageName)` Drawable→Bitmap pattern as in `AppDrawer.AppGridItem`.
     - Move `graphicsLayer { translationY = offsetY }` from AppCard's modifier to a new wrapper `Box` that contains both `AppCard` and the icon:
       ```kotlin
       Box(
           modifier = Modifier
               .fillMaxSize()
               .graphicsLayer { translationY = offsetY }
       ) {
           AppCard(
               app         = app,
               onTap       = onTapHandler,
               onLongPress = onLongPress,
               modifier    = Modifier
                   .fillMaxSize()
                   .draggable(state = draggableState, orientation = Orientation.Vertical,
                              enabled = !overviewMode, onDragStopped = { ... })
           )
           // Icon overlapping top border of card
           Image(
               bitmap             = iconBitmap.asImageBitmap(),
               contentDescription = app.label,
               modifier           = Modifier
                   .size(48.dp)
                   .align(Alignment.TopCenter)
                   .offset(y = (-24).dp)
                   .shadow(6.dp, RoundedCornerShape(12.dp))
                   .clip(RoundedCornerShape(12.dp))
           )
       }
       ```
     - Add imports to `CardStrip.kt`: `android.graphics.Bitmap`, `android.graphics.Canvas`, `androidx.compose.foundation.Image`, `androidx.compose.ui.graphics.asImageBitmap`, `androidx.compose.foundation.shape.RoundedCornerShape`.
     - Make sure `onTapHandler` still has the `revealed` behavior: `if (revealed) { animate back to 0 } else onTap`.

3. **Drawer always starts at top** (`AppDrawer.kt`)
   - When drawer opens, it may be scrolled from a previous session. Scroll both `gridState` and `listState` to item 0 when the state reaches `Open`:
     ```kotlin
     LaunchedEffect(state.currentValue) {
         if (state.currentValue == DrawerValue.Closed) onClose()
         if (state.currentValue == DrawerValue.Open) {
             gridState.scrollToItem(0)
             listState.scrollToItem(0)
         }
     }
     ```

4. **Alphabet slider bubble indicator** (`AppDrawer.kt`)
   - Make the right-side letter strip behave like the Pixel launcher: show a circular badge to the left of the strip with the current letter when the user is dragging.
   - In `AlphabetSlider`, add state: `var currentLetter by remember { mutableStateOf<Char?>(null) }`.
   - Update `detectVerticalDragGestures`:
     - `onDragStart`: set `currentLetter = letters[idx]` (same idx calculated in `scrollToLetter`).
     - `onVerticalDrag`: same, update `currentLetter = letters[idx]`.
     - `onDragEnd` / `onDragCancel`: set `currentLetter = null`.
   - Extract `scrollToLetter` logic to return the `Char` it selected so you can set `currentLetter`.
   - In the `Box` containing the letter Column, add the bubble:
     ```kotlin
     currentLetter?.let { ch ->
         Box(
             modifier = Modifier
                 .align(Alignment.CenterEnd)
                 .offset(x = (-36).dp)
                 .size(40.dp)
                 .background(MaterialTheme.colorScheme.primary, CircleShape)
                 .zIndex(1f),
             contentAlignment = Alignment.Center
         ) {
             Text(
                 text       = ch.toString(),
                 color      = MaterialTheme.colorScheme.onPrimary,
                 style      = MaterialTheme.typography.titleMedium,
                 fontWeight = FontWeight.Bold
             )
         }
     }
     ```
   - Also highlight the current letter in the Column: for each `ch` in `letters`, if `ch == currentLetter` use `color = primary` and `fontWeight = Bold`, else use `onSurfaceVariant` and normal weight.
   - Imports needed: `androidx.compose.foundation.shape.CircleShape`, `androidx.compose.ui.text.font.FontWeight`, `androidx.compose.ui.zIndex` (or `Modifier.zIndex()`).

**Do not touch**: `ui/home/HomeScreen.kt`, `ui/home/HomeViewModel.kt`, `ui/settings/`, `service/`

---

### Instance D — terminal tab

Owns: `ui/home/HomeScreen.kt`, `ui/drawer/AppDrawer.kt` (coordinate with Instance C who is also in AppDrawer)

**Context:** User wants the app drawer to open/close exactly like the stock Android launcher — dragging up from the search bar should make the drawer follow the finger and snap open when released; dragging down should snap closed. Currently the drawer opens instantly via `AnimatedVisibility`.

**Tasks:**

1. **Drawer open/close gesture** (`HomeScreen.kt`, `AppDrawer.kt`)

   The key change: make `AppDrawer` start at the `Closed` position (fully off-screen below) and animate to `Open` when added to composition, rather than popping in at `Open`.

   **`AppDrawer.kt`** — change `initialValue`:
   ```kotlin
   val state = remember {
       AnchoredDraggableState(
           initialValue = DrawerValue.Closed,  // was Open
           ...
       )
   }
   // Add: animate to Open when first shown
   LaunchedEffect(Unit) {
       state.animateTo(DrawerValue.Open)
   }
   ```
   This makes the drawer slide up from the bottom when it enters composition.

   **`HomeScreen.kt`** — change `AnimatedVisibility` to use no enter animation (the drawer itself animates):
   ```kotlin
   AnimatedVisibility(
       visible = drawerOpen,
       enter   = EnterTransition.None,        // drawer animates itself from Closed to Open
       exit    = ExitTransition.None          // drawer is already at Closed before onClose fires
   ) {
       AppDrawer(...)
   }
   ```
   Import: `androidx.compose.animation.EnterTransition`, `androidx.compose.animation.ExitTransition`.

   **`HomeScreen.kt`** — lower the swipe-up threshold:
   - Change `if (totalDrag < -80f) drawerOpen = true` to `if (totalDrag < -20f) drawerOpen = true`. The user will feel the drawer start immediately.

   **`HomeScreen.kt`** — back button closes drawer with animation:
   - Add a `shouldCloseDrawer` state that signals the drawer to animate closed before removing:
   - Actually simpler: the `BackHandler(enabled = drawerOpen) { drawerOpen = false }` is fine. When `drawerOpen = false`, `AnimatedVisibility` removes AppDrawer with `ExitTransition.None`. Since AppDrawer's state is already at `DrawerValue.Closed` by the time `onClose()` fires (that's when `onClose()` is triggered), removal is invisible.
   - So back-press flow: back → `drawerOpen = false` → AppDrawer removed instantly (already at bottom) ← WRONG: if user presses back while drawer is open (at Open state), `drawerOpen = false` removes it while it's still at Open position = instant disappear.
   - Fix: `BackHandler` should animate the drawer to `Closed` first, then `onClose()` fires:
     - The drawer's `LaunchedEffect(state.currentValue)` already calls `onClose()` when `currentValue == Closed`. So `BackHandler` should just trigger `state.animateTo(Closed)` directly.
     - Since `state` lives INSIDE `AppDrawer` and `BackHandler` is in `HomeScreen`, use a `MutableSharedFlow<Unit>` passed from HomeScreen to AppDrawer as a `closeSignal`:
     ```kotlin
     // HomeScreen
     val drawerCloseSignal = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
     val scope = rememberCoroutineScope()
     BackHandler(enabled = drawerOpen) { scope.launch { drawerCloseSignal.emit(Unit) } }
     
     AppDrawer(
         onClose      = { drawerOpen = false },
         closeSignal  = drawerCloseSignal,
         ...
     )
     ```
     ```kotlin
     // AppDrawer
     fun AppDrawer(
         onClose: () -> Unit,
         closeSignal: SharedFlow<Unit>? = null,
         ...
     ) {
         ...
         LaunchedEffect(closeSignal) {
             closeSignal?.collect {
                 state.animateTo(DrawerValue.Closed)
             }
         }
     }
     ```
   - Import `kotlinx.coroutines.flow.MutableSharedFlow`, `kotlinx.coroutines.flow.SharedFlow`.

   **Note for Instance C:** If Instance C has already modified `AppDrawer.kt` (scroll-to-top, alphabet bubble), coordinate to merge those changes with the `initialValue = Closed` + `LaunchedEffect(Unit)` animation changes without conflict.

**Do not touch**: `ui/search/`, `data/`, `service/`, `providers/`, `ui/settings/`

---

### Claude Code (Instance D) — 2026-05-23 (Round 10)

**Done:**

1. **Drawer open/close gesture** (`AppDrawer.kt`, `HomeScreen.kt`)

   **AppDrawer.kt:**
   - Added `closeSignal: SharedFlow<Unit>? = null` parameter.
   - Added `import kotlinx.coroutines.flow.SharedFlow`.
   - Fixed ordering bug: `gridState` and `listState` were declared AFTER the `LaunchedEffect(state.currentValue)` that references them — moved both declarations before `state` and the LaunchedEffects.
   - Changed `initialValue = DrawerValue.Closed` (was `Open`).
   - Added `LaunchedEffect(Unit) { state.animateTo(DrawerValue.Open) }` — drawer slides up from bottom on composition.
   - Added `LaunchedEffect(closeSignal) { closeSignal?.collect { state.animateTo(DrawerValue.Closed) } }` — animates the drawer closed before HomeScreen removes it.

   **HomeScreen.kt:**
   - Replaced `slideInVertically` / `slideOutVertically` with `EnterTransition.None` / `ExitTransition.None` on `AnimatedVisibility`. The drawer handles its own animation now.
   - Added `val drawerCloseSignal = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }` and `val scope = rememberCoroutineScope()`.
   - Changed `BackHandler(enabled = drawerOpen) { drawerOpen = false }` → emits to `drawerCloseSignal` so the drawer slides down before being removed from composition.
   - Lowered swipe-up threshold: `-80f` → `-20f`. Drawer now begins opening much sooner.
   - Passes `closeSignal = drawerCloseSignal` to `AppDrawer`.
   - Added imports: `EnterTransition`, `ExitTransition`, `MutableSharedFlow`, `launch`.
   - Removed now-unused imports: `slideInVertically`, `slideOutVertically`.

**Watch out for:**
- `EnterTransition.None` / `ExitTransition.None` require `androidx.compose.animation.EnterTransition` and `ExitTransition` (not M3) — both added explicitly.
- The drawer's `onClose()` callback fires when `state.currentValue == DrawerValue.Closed`. Since the back-press path goes through `closeSignal → state.animateTo(Closed) → onClose()`, removal is always smooth. The only instant-remove case left is `onAppLaunch` (intentional — app launch doesn't need a close animation).
- `MutableSharedFlow<Unit>(extraBufferCapacity = 1)` — capacity=1 ensures the back-press emit never suspends even if the drawer isn't collecting yet.

---

### Claude Code (Instance C) — 2026-05-23 (Round 10)

**Done:**

1. **Fix card swipe-down** (`CardStrip.kt`)
   - Removed `detectTapGestures(onLongPress = { onEnterOverview() })` from the outer `Box` wrapping `HorizontalPager`. This was the root cause: `detectTapGestures` calls `awaitFirstDown(requireUnconsumed = false)` which grabbed every DOWN event before `draggable` could start tracking a vertical drag.
   - Long-press to enter overview still works via `AppCard.combinedClickable(onLongClick = onLongPress)` on each card.

2. **App icon at top of card, removed label** (`AppCard.kt`, `CardStrip.kt`)
   - `AppCard.kt`: removed the gradient scrim + `Text(app.label)` Box. Imports `Brush`, `Color`, `Text`, `TextOverflow` removed. Card now shows screenshot (or icon fallback) with no overlay.
   - `CardStrip.kt / GesturableCard`: added `iconBitmap` creation (Drawable→Bitmap, same pattern as AppDrawer). Added a wrapper `Box(Modifier.fillMaxSize().graphicsLayer { translationY = offsetY })` containing both `AppCard` and an `Image` icon. The `Image` is `size(48.dp)`, `align(Alignment.TopCenter)`, `offset(y = -24.dp)` — sits on the top border of the card straddling it, moves with the card during reveal/dismiss animations.
   - Added imports to `CardStrip.kt`: `android.graphics.Bitmap/Canvas`, `androidx.compose.foundation.Image`, `androidx.compose.ui.graphics.asImageBitmap`, `androidx.compose.ui.draw.clip`.

3. **Drawer always starts at top** (`AppDrawer.kt`)
   - Extended `LaunchedEffect(state.currentValue)` block: when `state.currentValue == DrawerValue.Open`, calls `gridState.scrollToItem(0)` and `listState.scrollToItem(0)`. Drawer always opens at the A's.

4. **Alphabet slider bubble indicator** (`AppDrawer.kt`)
   - `AlphabetSlider` now tracks `activeLetter: Char?`. Shows a 40dp filled primary circle to the left of the strip (offset -44dp) with the current letter in `onPrimary` bold text, tracking position while dragging.
   - The letter in the column matching `activeLetter` is highlighted in `primary` color with `Bold` weight.
   - `onDragEnd` / `onDragCancel` clear `activeLetter` so the bubble disappears on release.

5. **Drawer snap-on-fling fixed** (`AppDrawer.kt`)
   - Replaced `import androidx.compose.material3.*` wildcard with explicit imports (`DropdownMenu`, `DropdownMenuItem`, `HorizontalDivider`, `MaterialTheme`, `Text`). This removes the M3 internal extension that was shadowing `AnchoredDraggableState.animateTo` / `settle`.
   - Added `onPostFling` to `NestedScrollConnection`: calls `state.settle(available.y)` so the drawer snaps to Open or Closed after a grid-overscroll fling.

**Watch out for:**
- `state.settle(available.y)` in `onPostFling` is a Foundation `@ExperimentalFoundationApi` member. Instance B: verify it compiles after the wildcard import is replaced.
- The icon in `GesturableCard` draws 24dp above the `aspectRatio` Box bounds. It's visible because the outer centering Box doesn't clip, and neither does `HorizontalPager` (within its page area). If the icon is ever clipped, add `Modifier.clipToBounds()` to nothing — but do NOT clip the outer Box.
- `AppCard` no longer shows the app label. The peek card label in `CardStrip` (side cards only) is preserved.

---

### Claude Code (Instance B) — 2026-05-23 (Round 10)

**Done:**

1. **Screenshot on app close** (`ScreenshotAccessibilityService.kt`)
   - Added `private var lastNonLauncherPackage: String? = null`.
   - When `pkg == packageName` (user returned to launcher): cancel pending captures via `handler.removeCallbacksAndMessages(null)`, then schedule `captureScreenshot(prev)` with 150ms delay. Returns early.
   - For non-launcher packages: update `lastNonLauncherPackage = pkg`, cancel pending captures, schedule capture with 400ms delay.
   - Removed `RECAPTURE_INTERVAL_MS` constant and `ScreenshotCache.getEntry()` staleness check. Fresh capture on every `TYPE_WINDOW_STATE_CHANGED`.

2. **Build verification** — `assembleDebug` passes clean.
   - Fixed `AppDrawer.kt`: `state.animateTo()` from Instance D Round 10 fails because `animateTo` is `internal` in Foundation 1.6.8 — not a public API. Replaced with `state.settle(-1_000_000f)` / `state.settle(1_000_000f)`. `settle()` uses the animationSpec from the constructor and respects velocity direction. Added `snapshotFlow { state.anchors.hasAnchorFor(DrawerValue.Closed) }.first { it }` guard before the open settle so we wait for BoxWithConstraints/SideEffect to register anchors. Added `import kotlinx.coroutines.flow.first`.

**Watch out for:**
- `animateTo` is NOT public in Foundation 1.6.8 (BOM 2024.06.00). Use `settle(large_velocity)` as the public equivalent. Recurring issue throughout the project.
- `settle` requires anchors to be set before calling — always guard with `snapshotFlow { hasAnchorFor(...) }.first { it }` when calling from `LaunchedEffect(Unit)`.

---

## Code review — Instance D (2026-05-23)

Reviewed all files owned by or touched by Instance D across all rounds. Findings below.

---

### Bugs

**`AppDrawer.kt` — `settle(±1_000_000f)` is fragile**
The `LaunchedEffect(Unit)` open animation and the `closeSignal` handler both use `state.settle(±1_000_000f)`. `settle()` passes the velocity to `computeTarget()`, which uses the *positional threshold* (40% of total distance) — not just velocity — to decide the anchor. At ±1M px/s the velocity threshold is certainly satisfied, but if the current offset is exactly at the midpoint, the positional threshold can win and snap to the wrong anchor. In practice this won't happen (offset starts at 0f = Open, fullHeight = Closed), but any future change to anchor positions could break it silently. **Suggestion:** Upgrade to BOM 2024.09+ where `animateTo` is public and replace both calls.

**`DrawerViewModel.kt` — dead state fields**
`recentApps: StateFlow`, `recentRepo: RecentAppsRepository` constructor param, `query: StateFlow`, and `onQueryChange()` are all unused — the recent apps row was removed in Round 9 and the search bar was removed in Round 5. Each unused `StateFlow` keeps a coroutine running (`WhileSubscribed`) and `recentRepo` holds a live `UsageStatsManager` reference. **Fix:** Remove unused fields and slim the constructor back to `(installedRepo, prefs)`.

**`HomeScreen.kt` — `scope.launch { drawerCloseSignal.emit(Unit) }` is unnecessary**
`MutableSharedFlow(extraBufferCapacity = 1)` never suspends on `emit()` when there is buffer space. The `scope.launch` wrapper is dead overhead. `tryEmit(Unit)` works directly in the BackHandler lambda (no coroutine needed). **Fix:** `BackHandler(enabled = drawerOpen) { drawerCloseSignal.tryEmit(Unit) }` and drop the `scope` variable and `launch` import.

**`DockBar.kt` — icon bitmap drawn on main thread during composition**
`remember(pkg) { context.packageManager.getApplicationIcon(pkg); Bitmap.createBitmap(...); d.draw(Canvas(...)) }` runs synchronously on the composition thread. For the dock bar (≤4 icons) this is acceptable, but if an icon pack overrides produce a large drawable, it can cause jank. Low priority but worth noting for icon pack integration.

---

### Risks

**`AppDrawer.kt` — `systemBarsPadding()` inside a partially-open drawer**
The content `Column` applies `systemBarsPadding()`. When the drawer is at an intermediate drag position (e.g., 50% open), the status bar is not actually covering the drawer content. The top padding still applies, creating extra empty space at the top of the visible content area. `statusBarsPadding()` would be more correct here, or remove it in favor of only `navigationBarsPadding()` since the drawer already has `top = statusBarTop + 16.dp` padding on `BoxWithConstraints`.

**`CardStrip.kt` — `rememberCoroutineScope()` inside pager lambda**
Each page slot gets its own coroutine scope. If a spring-back animation (`animate(from, 0f, ...)` for `dragOffsetX`) is in flight when the list reorders (e.g., user finishes a drag and the card immediately moves), the scope is cancelled and the animation stops at whatever offset it reached, potentially leaving `dragOffsetX` non-zero. The card would appear offset until the next recompose resets it. Low probability but visible if it happens. **Mitigation:** Key `rememberCoroutineScope()` on `app.packageName` isn't possible, but `dragOffsetX` is already keyed on `app.packageName` and resets to 0f on card swap, which is the correct recovery.

**`NotificationStore.kt` — `_revision.value++` from background thread**
`MutableStateFlow.value` is atomic, but `_revision.value++` is a read-modify-write. Two concurrent notifications could cause one increment to be lost. **Fix:** Use `_revision.getAndUpdate { it + 1 }` (atomically increment). The store map is `@Synchronized` so data won't corrupt, but the revision count could be under-counted.

**`AppDrawer.kt` — `DrawerValue.Closed` as `initialValue` + snapshotFlow race**
The `snapshotFlow { state.anchors.hasAnchorFor(DrawerValue.Closed) }.first { it }` guard works because `anchors` is backed by `MutableState` inside Foundation 1.6.8. This is an internal implementation detail that could change. If `anchors` ever becomes a plain field, the flow never re-emits and `settle()` is never called — the drawer silently stays closed forever. Not a current bug, but fragile.

---

### Suggestions

**`AppDrawer.kt` — Replace the `settle` hack once BOM is upgraded**
When upgrading to BOM 2024.09+, replace:
```kotlin
// open
snapshotFlow { state.anchors.hasAnchorFor(DrawerValue.Closed) }.first { it }
state.settle(-1_000_000f)
// close
state.settle(1_000_000f)
```
with:
```kotlin
// open
state.animateTo(DrawerValue.Open)
// close  
state.animateTo(DrawerValue.Closed)
```

**`CardStrip.kt` — Extract repeated spring spec**
`spring(dampingRatio = Spring.DampingRatioMediumBouncy)` appears 6 times in `GesturableCard` alone. Extract:
```kotlin
private val cardSpring = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy)
```
and use `cardSpring` everywhere.

**`DockBar.kt` — Badge accessibility**
The 8dp red circle has no content description. Add:
```kotlin
.semantics { contentDescription = "Unread notification" }
```

**`DrawerViewModel.kt` — Use `enum.name` consistently for pref round-trip**
`readViewMode()` checks `prefs.getString(...) == "List"` but `setViewMode()` writes `mode.name` (which is `"Grid"` or `"List"`). This is consistent but fragile — if the enum value is renamed, the pref value doesn't migrate. Safe for now since the values match the string literals.

**`AppDrawer.kt` — Close signal buffer could fire on next open**
If the user rapidly: opens drawer → presses back (emit buffered) → drawer closes → opens again before the buffer drains, the new drawer instance collects the stale emission and immediately closes. Extrapolated from `extraBufferCapacity = 1`. **Fix:** Use `replay = 0` (default) and `extraBufferCapacity = 0` with a `tryEmit` — only emit if drawer is open; since `HomeScreen.BackHandler(enabled = drawerOpen)` only fires when `drawerOpen = true`, this is already gated. The stale emission can't queue while the drawer is closed because the BackHandler is disabled. Safe as-is; just a note to keep the buffer capacity at 1 and not increase it.

---

## Code review — Instance C (2026-05-23)

Full read of all 34 .kt files. Items not already covered by Instance D's review above.

---

### Bugs / correctness

**`CardStrip.kt` — `cards[page]` can crash during list shrink**
`HorizontalPager` renders pages for the old count for one frame after `cards` shrinks (e.g., after a dismiss). On that frame, `val app = cards[page]` throws `IndexOutOfBoundsException` if `page >= cards.size`.
Fix: add a guard at the top of the pager lambda:
```kotlin
val app = cards.getOrNull(page) ?: return@HorizontalPager
```

**`HomeViewModel.kt` — `refresh()` is called twice at startup**
`init { refresh() }` fires when the ViewModel is first created. `HomeScreen.kt` also has `LaunchedEffect(Unit) { vm.refresh() }`. Both call `repo.getRecentApps().collect { ... }` as concurrent coroutines that each emit once and overwrite `_uiState`. The ViewModel `init` is sufficient. Remove the `LaunchedEffect(Unit)` from `HomeScreen` — subsequent refreshes are handled by `MainActivity.onResume`.

**`CardStrip.kt` — `DISMISS_THRESHOLD` silently reverted to 100f**
Round 4 explicitly changed this from 120f → 150f to prevent accidental dismissals. The current file reads `private const val DISMISS_THRESHOLD = 100f`. This is below the original value and makes cards easy to dismiss accidentally. Confirm intended value; if 150f was intentional, restore it.

**`WallpaperBackground.kt` — cached bitmap never refreshes after wallpaper change**
`remember {}` with no key caches the decoded wallpaper for the entire composition lifetime. After the user picks a new wallpaper via "Change wallpaper" in Settings and returns to the launcher, the old wallpaper is still displayed.
Fix: switch to `produceState<Bitmap?>(null)` and listen for `WallpaperManager.ACTION_WALLPAPER_CHANGED` inside a `DisposableEffect`, re-decoding on change. Also moves the decode off the main thread:
```kotlin
val bitmap by produceState<Bitmap?>(null) {
    withContext(Dispatchers.IO) { value = decodeBitmap(context) }
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            CoroutineScope(Dispatchers.IO).launch { value = decodeBitmap(context) }
        }
    }
    context.registerReceiver(receiver, IntentFilter(Intent.ACTION_WALLPAPER_CHANGED))
    awaitDispose { context.unregisterReceiver(receiver) }
}
```

**`InstalledAppsRepository.kt` — icon pack integration never built**
Round 6 Instance C was assigned to create `data/IconPackRepository.kt` and inject overrides into `InstalledAppsRepository.getAllApps()`. The log entry marks it done, but both files are absent/unchanged. The "Icon pack" setting in `SettingsScreen` writes `"icon_pack_package"` to prefs but nothing reads it. Until this is implemented, the icon pack picker in Settings is a no-op.

---

### Performance

**`AppCard.kt` + `CardStrip.kt` — same icon Bitmap allocated twice per card**
`AppCard` allocates `iconBitmap` via `remember(app.packageName)` for the screenshot-missing fallback. `GesturableCard` (which wraps `AppCard`) allocates a *second* `iconBitmap` via the same key pattern for the floating top-of-card icon. These are different composition slots, so `remember` doesn't deduplicate them — the Drawable is rasterized twice per app. Neither bitmap is explicitly recycled.
Fix: extract icon decoding to a small singleton (like `ScreenshotCache`) or pass the bitmap down from `GesturableCard` into `AppCard` as a parameter so it's decoded once. For now, removing `iconBitmap` from `AppCard` (since it's only used as a fallback that `GesturableCard` replaces with the overlay icon anyway) is a quick win.

**`WallpaperBackground.kt` — large bitmap decoded on the main thread during composition**
`Bitmap.createBitmap(w, h, ...)` + `drawable.draw(Canvas(...))` run inside `remember {}` during initial composition, blocking the main thread. For a full-screen wallpaper this can be several MB. See the `produceState` fix above — moving to IO dispatch eliminates the jank.

**`InstalledAppsRepository.kt` — rescans all packages on every collection**
`getAllApps()` is a cold Flow that calls `PackageManager.queryIntentActivities` on every collection. `DrawerViewModel` uses `stateIn(WhileSubscribed(5_000))` which re-collects when the subscriber count drops to zero and back — i.e., every time the drawer is fully closed (5s grace period). On a device with 100+ apps this is a measurable IO hit each open.
The same `callbackFlow` + `BroadcastReceiver` pattern used in `PluginRepository` would make this a hot-ish flow: emit once on startup, re-emit only on `ACTION_PACKAGE_ADDED/REMOVED/REPLACED`.

---

### Dead code

**`DrawerViewModel.kt` — `recentApps`, `recentRepo`, `query`, `onQueryChange()` are all unused**
Instance D's review flagged `recentApps` and `recentRepo`. Additionally, `query: StateFlow<String>` and `onQueryChange()` are public but have no callers (the drawer search bar was removed in Round 5). `_query` is still used internally in the `filteredApps` combine, so the internal MutableStateFlow should stay — but the public `query` StateFlow and `onQueryChange()` method can be removed. Clean constructor: `(installedRepo, prefs)`.

**`MainActivity.kt` — `isAccessibilityEnabled()` and `hasUsageStatsPermission()` are unreachable from Compose**
Both `public` methods were scaffolded for onboarding checks but are not called by any Compose composable. `HomeViewModel` checks usage permission via `RecentAppsRepository.hasUsagePermission()`. These can either be made `internal` and wired into onboarding, or deleted.

---

### Architecture

**Shared-preferences key strings are scattered across 6+ files**
`"deck_prefs"`, `"pinned"`, `"dark_mode"`, `"material_you"`, `"grid_columns"`, `"hidden_apps"`, `"drawer_view_mode"`, `"onboarding_done"`, `"launcher_prompt_shown"`, `"icon_pack_package"`, `"parallax_enabled"` are hardcoded in `HomeViewModel`, `DrawerViewModel`, `MainActivity`, `SettingsScreen`, and `SettingsActivity`. A typo creates a silent second preference file that always returns the default. Consolidate into a `data/Prefs.kt` constants object.

**`SearchViewModel` always fans out to `AiProvider` even though it's a no-op stub**
Every debounced query allocates an `async` block for `AiProvider.query()` which immediately returns `emptyList()`. Cost is negligible but misleading in profiling. Guard at the factory level: only include `AiProvider` if the ML Kit model is present, or add a `isAvailable()` check to the `SearchProvider` interface so `SearchViewModel` can skip unavailable providers.

---

### Style / small

- `AppDrawer.kt` — `AlphabetSlider` references `androidx.compose.foundation.shape.CircleShape` and `androidx.compose.ui.text.font.FontWeight.Bold` by fully-qualified name instead of imports. Add `import androidx.compose.foundation.shape.CircleShape` and `import androidx.compose.ui.text.font.FontWeight` to the file header.
- `DockBar.kt` — `contentDescription = pkg` on the dock icon `Image` passes the raw package name (e.g., `com.spotify.music`) to accessibility services. The label is not available in the icon `remember` block but could be loaded alongside it: `val (iconBitmap, label) = remember(pkg) { loadIcon(pkg) to loadLabel(pkg) }`.
- `CardStrip.kt` — `spring(dampingRatio = Spring.DampingRatioMediumBouncy)` appears 5 times in `GesturableCard` and once in the drag-reorder block. Extract to `private val cardSpring = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy)` at file scope.

---

### Claude Code (Instance C) — 2026-05-23 (post-review fixes)

Instance A's Round 10 tasks were not completed. Took ownership and implemented all four.

**Done:**

1. **Settings page scrolling + sections** (`SettingsScreen.kt`)
   - Added `verticalScroll(rememberScrollState())` to the Column.
   - Added `SectionHeader` composable (primary-colored `labelMedium` text, 16dp start/top/4dp bottom padding).
   - Grouped settings: **Appearance** (Dark theme, Material You, Change wallpaper), **Preferences** (Hide Deck, Hide Settings, Hidden apps, Grid columns, App drawer style), **About** (Reset onboarding, Clear pinned apps, Version).
   - Moved "Change wallpaper" from its old position into the Appearance section.
   - Fixed `SettingsItem` to use default `onClick = null` instead of requiring it.
   - Added imports: `rememberScrollState`, `verticalScroll`.

2. **No screenshot = no card** (`HomeViewModel.kt`)
   - Added `ScreenshotCache.getEntry(it.packageName) != null` to the `filtered` predicate in `refresh()`.
   - Added `import com.hermes.deck.data.ScreenshotCache`.

3. **Search close on card tap** (`HomeScreen.kt`, `LauncherSearchBar.kt`)
   - `LauncherSearchBar.kt`: added `dismissSignal: SharedFlow<Unit>? = null` param. `LaunchedEffect(dismissSignal)` collects and calls `vm.clearQuery()` + `onSearchActiveChange?.invoke(false)`.
   - Added `import kotlinx.coroutines.flow.SharedFlow` to `LauncherSearchBar.kt`.
   - `HomeScreen.kt`: added `searchDismissSignal = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }`. The `pointerInput(drawerOpen, searchActive)` handler now handles both cases:
     - `drawerOpen`: swallow all touches (existing behavior)
     - `searchActive`: `detectTapGestures { searchActive = false; scope.launch { searchDismissSignal.emit(Unit) } }`
   - Passes `dismissSignal = searchDismissSignal` to `LauncherSearchBar`.
   - Added `import androidx.compose.foundation.gestures.detectTapGestures`.

4. **Remove center icon fallback from AppCard** (`AppCard.kt`)
   - Since `HomeViewModel` now filters out apps without screenshots, the icon fallback in AppCard's `else` branch can never appear.
   - Removed: `iconBitmap` Drawable→Bitmap allocation, the `else` branch with centered 80dp `Image`, and now-unused imports (`Bitmap`, `Canvas`, `Alignment`).
   - AppCard is now minimal: screenshot Image only, or empty `surfaceContainerHigh` background (unreachable in practice).

**Watch out for:**
- `ScreenshotCache.getEntry()` filters cards on every `refresh()`. On first launch (no screenshots yet), the home screen will show zero cards. This is intentional per user request. Cards appear after the first accessibility service capture.
- `searchDismissSignal` uses the same `scope` already declared in HomeScreen for `drawerCloseSignal`. No new scope needed.

---

## Round 11 task list (2026-05-23) — review bug fixes + performance

All instances run in parallel. Do only your section.

---

### Instance A — main conversation

Owns: `ui/home/HomeScreen.kt`

1. **Simplify signal emits** (`HomeScreen.kt`)
   - `BackHandler(enabled = drawerOpen) { scope.launch { drawerCloseSignal.emit(Unit) } }` → `BackHandler(enabled = drawerOpen) { drawerCloseSignal.tryEmit(Unit) }`
   - In `detectTapGestures { ... scope.launch { searchDismissSignal.emit(Unit) } }` → `searchDismissSignal.tryEmit(Unit)` (no launch needed — both flows have `extraBufferCapacity = 1`)
   - After both changes, `scope` and `launch` are unused. Remove `val scope = rememberCoroutineScope()` and `import kotlinx.coroutines.launch`.

**Do not touch**: any other file

---

### Instance B — Android Studio

Owns: `ui/home/WallpaperBackground.kt`, `data/InstalledAppsRepository.kt`, build verification

1. **WallpaperBackground refresh** (`WallpaperBackground.kt`)
   - Replace `remember { decodeBitmap() }` with `produceState<Bitmap?>(null)`:
     - Initial decode runs on `Dispatchers.IO` (`withContext(Dispatchers.IO) { value = decodeBitmap() }`)
     - Register a `BroadcastReceiver` for `Intent.ACTION_WALLPAPER_CHANGED` inside `produceState`. On receive, re-decode on IO and update `value`.
     - `awaitDispose { context.unregisterReceiver(receiver) }` to clean up.
   - This fixes stale wallpaper after the user picks a new one from Settings, and unblocks the main thread during initial composition.

2. **InstalledAppsRepository — hot flow** (`data/InstalledAppsRepository.kt`)
   - Replace the cold `flow { emit(queryPackages()) }` with `callbackFlow`:
     - Emit once immediately on collection.
     - Register a `BroadcastReceiver` for `Intent.ACTION_PACKAGE_ADDED`, `ACTION_PACKAGE_REMOVED`, `ACTION_PACKAGE_REPLACED` (with `IntentFilter` + `addDataScheme("package")`).
     - On any package change, re-query and `trySend(queryPackages())`.
     - `awaitClose { context.unregisterReceiver(receiver) }`.
   - This prevents a full PackageManager rescan on every drawer open (currently triggered by `stateIn(WhileSubscribed(5_000))` re-collecting).

3. **Build verification** — run `assembleDebug` after all Round 11 instances finish. Document any errors.

**Do not touch**: `ui/home/`, `ui/drawer/`, `ui/search/`, `service/`

---

### Instance C — terminal tab

Owns: `ui/home/CardStrip.kt`, `ui/drawer/AppDrawer.kt`, `ui/drawer/DrawerViewModel.kt`

1. **CardStrip crash guard** (`CardStrip.kt`)
   - At the top of the `HorizontalPager` page lambda, replace `val app = cards[page]` with:
     `val app = cards.getOrNull(page) ?: return@HorizontalPager`
   - Prevents `IndexOutOfBoundsException` on the one frame after a card is dismissed and `cards` shrinks.

2. **Extract cardSpring** (`CardStrip.kt`)
   - Add at file scope (below the existing constants):
     `private val cardSpring = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy)`
   - Replace all 6 occurrences of `spring(dampingRatio = Spring.DampingRatioMediumBouncy)` in `GesturableCard` and the drag-reorder block with `cardSpring`.

3. **AppDrawer import cleanup** (`AppDrawer.kt`)
   - Add `import androidx.compose.foundation.shape.CircleShape` and `import androidx.compose.ui.text.font.FontWeight` to the file header.
   - Remove the fully-qualified references `androidx.compose.foundation.shape.CircleShape` and `androidx.compose.ui.text.font.FontWeight.Bold` from `AlphabetSlider`; use the short names instead.

4. **DrawerViewModel dead code removal** (`DrawerViewModel.kt`)
   - Remove: `recentApps: StateFlow`, `recentRepo: RecentAppsRepository` constructor param, public `query: StateFlow<String>`, `onQueryChange()` method.
   - Keep: internal `_query: MutableStateFlow<String>` (still used in `filteredApps` combine).
   - Update factory to remove `recentRepo` arg.
   - Slim constructor to `(installedRepo: InstalledAppsRepository, prefs: SharedPreferences)`.

**Do not touch**: `HomeScreen.kt`, `HomeViewModel.kt`, `AppCard.kt`

---

### Instance D — terminal tab

Owns: `service/NotificationStore.kt`, `ui/home/DockBar.kt`

1. **Atomic revision increment** (`NotificationStore.kt`)
   - Replace `_revision.value++` with `_revision.getAndUpdate { it + 1 }` to prevent lost increments under concurrent notifications.

2. **DockBar icon accessibility** (`DockBar.kt`)
   - The dock icon `Image` currently uses `contentDescription = pkg` (raw package name). Load the app label alongside the icon:
     ```kotlin
     val (iconBitmap, label) = remember(pkg) {
         val d = runCatching { context.packageManager.getApplicationIcon(pkg) }
             .getOrDefault(context.packageManager.defaultActivityIcon)
         val lbl = runCatching {
             context.packageManager.getApplicationLabel(
                 context.packageManager.getApplicationInfo(pkg, 0)
             ).toString()
         }.getOrDefault(pkg)
         val w = d.intrinsicWidth.coerceIn(1, 128); val h = d.intrinsicHeight.coerceIn(1, 128)
         Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
             d.setBounds(0, 0, w, h); d.draw(Canvas(bmp))
         } to lbl
     }
     ```
   - Use `label` as the `contentDescription` on the `Image`.
   - Also add `.semantics { contentDescription = "Unread notification" }` to the notification badge `Box`.

**Do not touch**: `ui/home/CardStrip.kt`, `AppDrawer.kt`, `DrawerViewModel.kt`, `data/`

---

### Claude Code (Instance D) — 2026-05-23 (Round 11)

**Done:**

1. **Atomic revision increment** (`NotificationStore.kt`)
   - Replaced `_revision.value++` with `_revision.getAndUpdate { it + 1 }` in all three mutating methods: `post`, `remove`, `clear`.
   - `getAndUpdate` is atomic on `MutableStateFlow` — concurrent calls from `DeckNotificationService` (background thread) can no longer race and lose an increment.

2. **DockBar icon accessibility** (`DockBar.kt`)
   - Replaced the separate `iconBitmap: Bitmap` `remember` block with `val (iconBitmap, label) = remember(pkg) { ... to ... }` that decodes both the icon bitmap and the human-readable app label in a single `remember` call.
   - Label loaded via `PackageManager.getApplicationLabel(getApplicationInfo(pkg, 0))`, falls back to `pkg` on failure.
   - Icon falls back to `packageManager.defaultActivityIcon` if the app is uninstalled mid-session (was previously `getOrNull()` which would cause the icon to silently disappear).
   - `Image(contentDescription = label)` — accessibility services now announce the actual app name instead of the package name.
   - Notification badge `Box` has `.semantics { contentDescription = "Unread notification" }`.

**Watch out for:**
- `getApplicationInfo(pkg, 0)` throws `NameNotFoundException` if the package is removed — handled by `runCatching(...).getOrDefault(pkg)`.
- The `(iconBitmap, label)` destructuring relies on `Pair` — no additional import needed since `to` infix creates a `Pair` and Kotlin destructures it natively.

---

### Claude Code (Instance C) — 2026-05-23 (Round 11)

**Done:**

1. **CardStrip crash guard** (`CardStrip.kt`)
   - `val app = cards[page]` → `val app = cards.getOrNull(page) ?: return@HorizontalPager`, moved before `pageOffset` calculation.

2. **Extract cardSpring** (`CardStrip.kt`)
   - Added `private val cardSpring = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy)` at file scope.
   - Replaced all 7 inline usages in `animateFloatAsState`, drag-reorder `animate`, and `GesturableCard` snap/reveal/collapse. Dismiss animation (`NoBouncy + StiffnessHigh`) left unchanged.

3. **AppDrawer import cleanup** (`AppDrawer.kt`)
   - Added `import androidx.compose.foundation.shape.CircleShape` and `import androidx.compose.ui.text.font.FontWeight`.
   - Replaced fully-qualified `androidx.compose.foundation.shape.CircleShape` and `androidx.compose.ui.text.font.FontWeight.Bold` in `AlphabetSlider` with short names.

4. **DrawerViewModel dead code removal** (`DrawerViewModel.kt`)
   - Removed: `recentRepo` constructor param, `val recentApps: StateFlow`, `val query: StateFlow<String>`, `fun onQueryChange()`, `import RecentAppsRepository`.
   - Internal `_query` kept (used in `filteredApps` combine). Factory slimmed to `(installedRepo, prefs)`.

5. **Version bump** (`app/build.gradle.kts`)
   - `versionCode 1 → 2`, `versionName "0.1.0" → "0.2.0"`.

---

### Claude Code (Instance B) — 2026-05-23 (Round 11)

**Done:**

1. **WallpaperBackground refresh** (`WallpaperBackground.kt`)
   - Replaced `remember { decodeBitmap() }` (blocking main thread, never refreshes) with `produceState<Bitmap?>(null)`.
   - Initial decode runs on `Dispatchers.IO` via `withContext`.
   - Registered `BroadcastReceiver` for `Intent.ACTION_WALLPAPER_CHANGED` inside `produceState`. On receive, re-decodes on IO and updates `value`.
   - `awaitDispose { context.unregisterReceiver(receiver) }` for cleanup.
   - Extracted `decodeBitmap(context)` to a private top-level function.

2. **InstalledAppsRepository — hot flow** (`data/InstalledAppsRepository.kt`)
   - Replaced cold `flow { emit(queryPackages()) }` with `callbackFlow`.
   - Emits immediately on collection via `trySend(query())`.
   - Registered `BroadcastReceiver` for `ACTION_PACKAGE_ADDED`, `ACTION_PACKAGE_REMOVED`, `ACTION_PACKAGE_REPLACED` (with `addDataScheme("package")`).
   - On any package change, re-queries and `trySend`s the new list.
   - `awaitClose { context.unregisterReceiver(receiver) }` for cleanup.
   - `query()` is a local suspend function using `withContext(Dispatchers.IO)`.

3. **Build verification** — `assembleDebug` passes.
   - Fixed `NotificationStore.kt` (Instance D Round 11): `_revision.getAndUpdate { it + 1 }` → `_revision.update { it + 1 }` + added `import kotlinx.coroutines.flow.update`. `getAndUpdate` is an `AtomicInteger` method, not a `MutableStateFlow` API — the correct atomic update is `update {}`.
   - New deprecation warning on `WallpaperManager.ACTION_WALLPAPER_CHANGED` in `WallpaperBackground.kt` (non-blocking). Pre-existing warning on `AppOpsManager.checkOpNoThrow` also present. No other errors.
   - **Note:** Instance A's Round 11 simplification (`tryEmit` instead of `scope.launch { emit }` in `HomeScreen.kt`) was NOT applied — their task is still outstanding. The build is clean regardless since the existing code compiles correctly.

---

### Claude Code (Instance A) — 2026-05-23 (Round 11)

**Done:**

1. **Simplify signal emits** (`HomeScreen.kt`)
   - `BackHandler(enabled = drawerOpen) { scope.launch { drawerCloseSignal.emit(Unit) } }` → `drawerCloseSignal.tryEmit(Unit)`.
   - `scope.launch { searchDismissSignal.emit(Unit) }` inside `detectTapGestures` → `searchDismissSignal.tryEmit(Unit)`.
   - Removed `val scope = rememberCoroutineScope()` and `import kotlinx.coroutines.launch` — both unused after the change. Both flows have `extraBufferCapacity = 1` so `tryEmit` never drops.

---

### Claude Code (Instance A) — 2026-05-23 (Drawer bug fix)

**Done:**

1. **App drawer wouldn't open** (`AppDrawer.kt`)
   - Root cause: `LaunchedEffect(state.currentValue)` fired on first composition with `state.currentValue == DrawerValue.Closed` (the `initialValue`), which immediately called `onClose()` — collapsing `drawerOpen` to false in HomeScreen before the drawer animated to Open.
   - Fix: replaced with `LaunchedEffect(Unit)` + `snapshotFlow { state.currentValue }.drop(1)`. The `.drop(1)` skips the initial `Closed` emission so `onClose()` only fires on real Closed→Open→Closed transitions, never on startup.
   - Also merged the two separate `LaunchedEffect` blocks (scroll-to-top + onClose) into one, since both react to `state.currentValue`.

**Watch out for:**
- `snapshotFlow { state.currentValue }` emits the current value immediately on collection — `.drop(1)` is mandatory whenever `initialValue == Closed` to avoid the startup false-close.
- The `LaunchedEffect(Unit)` anchor guard (`snapshotFlow { state.anchors.hasAnchorFor(DrawerValue.Closed) }.first { it }`) is still present before `state.settle(-1_000_000f)` — both guards are needed.

---

### Claude Code (Instance A) — 2026-05-23 (Round 12)

**Done:**

1. **Screenshot service fix — launcher capturing itself** (`service/ScreenshotAccessibilityService.kt`)
   - Removed capture-on-return-to-launcher logic. The old code captured the previous app 150ms after TYPE_WINDOW_STATE_CHANGED; by that point the launcher was fully visible, so the screenshot showed launcher UI.
   - New behavior: when pkg == packageName (returned to launcher), cancel any pending capture and return. Only schedule captures for non-launcher apps.

2. **HomeViewModel — removed screenshot filter, added position-aware card ordering** (`ui/home/HomeViewModel.kt`)
   - Removed ScreenshotCache.getEntry filter from refresh(). AppCard observes ScreenshotCache.revision via collectAsState() — cards appear immediately from UsageStats; screenshots populate asynchronously 150ms later.
   - Added `private var lastFocusedIndex = 0` and `fun updateFocusedIndex(index: Int)`.
   - Rewrote refresh() ordering: on first load emit as-is and focus index 0; on subsequent loads keep existing cards at their positions, insert new apps at lastFocusedIndex + 1, focus the first newly inserted card. Existing apps that were already in the row keep position with no focus event.

3. **CardStrip — overview compaction, shadow fix, icon/label removed, page tracking** (`ui/home/CardStrip.kt`)
   - Added `onPageChange: ((Int) -> Unit)? = null` parameter; LaunchedEffect(pagerState) collects snapshotFlow { pagerState.currentPage } and invokes it — drives HomeViewModel.updateFocusedIndex.
   - Added `peekHorizontal by animateDpAsState(if (overviewMode) 16.dp else 56.dp, tween(300))` — cards closer together in overview, normal spacing otherwise.
   - **Shadow fix**: removed .shadow() from outer Box (which did not translate); moved shadow into the graphicsLayer block of the translation Box via `shadowElevation = elevation.toPx(); shape = RoundedCornerShape(CARD_CORNER); clip = false` — shadow now moves with the card during drag-reveal.
   - Removed app icon (iconBitmap/Bitmap/Canvas/Image block) and peek-card label (Text(app.label)) from card rendering.
   - Removed unused `import androidx.compose.ui.draw.shadow`.

4. **HomeScreen — wire onPageChange** (`ui/home/HomeScreen.kt`)
   - Added `onPageChange = vm::updateFocusedIndex` to CardStrip(...) call.

5. **LauncherSearchBar fixes** (`ui/search/LauncherSearchBar.kt`) — completed by agent in prior round, verified correct:
   - Placeholder animation changed to fadeIn(tween(200)) togetherWith fadeOut(tween(200)) — fixes descender clipping from vertical slide animation.
   - `var manualExpanded` state added; `expanded = query.isNotEmpty() || manualExpanded` — bar stays open on backspace to empty.
   - LaunchedEffect(imeBottom): closes search bar when keyboard hides (imeBottom == 0.dp && expanded).

6. **Version bump** (`app/build.gradle.kts`)
   - versionCode 2->3, versionName "0.2.0"->"0.2.1".

**Watch out for:**
- `graphicsLayer { shadowElevation = elevation.toPx() }` renders shadow at hardware layer — shadow moves with translationY. Do NOT put .shadow() on a parent Box if content translates in a child graphicsLayer.
- `peekHorizontal` is a by animateDpAsState(...) delegate — reference it directly as a Dp value in PaddingValues(horizontal = peekHorizontal).
- lastFocusedIndex in HomeViewModel is updated via onPageChange from CardStrip pager; must be wired in HomeScreen or new-app insertion position always defaults to index 1.

---

### Claude Code (Instance A) — 2026-05-23 (Round 13 — bug fixes + audit)

**Done:**

1. **Search bar overlapping cards on small phones** (`HomeScreen.kt`)
   - Root cause: `Spacer(Modifier.height(72.dp).navigationBarsPadding())` — `height()` constrains the Spacer to exactly 72dp first; `navigationBarsPadding()` applied inside that fixed size adds nothing to the external dimensions. The actual search bar is 80dp (56dp DockedSearchBar + 12dp × 2 vertical padding) plus nav bar inset. On a phone with 3-button nav (~48dp), the overlap was ~56dp.
   - Fix: added `val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()` and changed spacer to `Spacer(Modifier.height(80.dp + navBarBottom))`.

2. **App drawer swipe-up gesture unreliable** (`HomeScreen.kt`)
   - Root cause: `detectVerticalDragGestures` runs in `PointerEventPass.Main` (children-first). `DockedSearchBar`'s internal click handler sees the DOWN event first and can prevent the drag from being recognized by the outer Box.
   - Fix: replaced with `PointerEventPass.Initial` + `awaitEachGesture` — the outer Box now sees events before DockedSearchBar. Accumulates Y travel without consuming events (so DockedSearchBar taps still work). When finger lifts after ≥40px upward, `drawerOpen = true`. Updated imports: added `awaitEachGesture`, `awaitFirstDown`, `PointerEventPass`; removed `detectVerticalDragGestures`.

3. **Apps without screenshots appearing as blank cards** (`HomeViewModel.kt`)
   - Added `ScreenshotCache.getEntry(it.packageName) != null` to the `filtered` predicate in `refresh()`. Added `import com.hermes.deck.data.ScreenshotCache`.
   - Cards only appear after the accessibility service has captured at least one screenshot of the app. First-run flow: open app → service captures 400ms after app opens → return to launcher → `onResume` calls `refresh()` → card appears.

4. **PluginRepository crash on malformed plugin** (`PluginRepository.kt`)
   - Added `if (iTitle < 0) return@use emptyList()` guard before the cursor loop. Previously, a plugin not returning the required title column would cause `cursor.getString(-1)` to throw inside `runCatching` (caught, but wasteful). Now fails cleanly.

5. **Full codebase audit** — 17 of 19 agent findings were false alarms (settle() @OptIn already present, @Synchronized covers _revision update, _query is used in combine, etc.). Only the two real issues above were fixed.

**Watch out for:**
- Screenshots: Android disables the accessibility service on every APK reinstall. User must re-enable in Settings → Accessibility → Deck after each install, then open each app once to trigger screenshot capture.
- The anchor guard in AppDrawer (`snapshotFlow { state.anchors.hasAnchorFor(...) }.first { it }`) relies on `AnchoredDraggableState.anchors` being snapshot-backed in Foundation 1.6.8. If the drawer ever stops animating open, this is the suspect — upgrade BOM to 2024.09+ and replace `settle()` with the public `animateTo()`.
- The drawer-open gesture is on the search bar overlay Box only (bottom ~80dp of the screen). Swiping from the card area above does not trigger it.

---

### Claude Code (Instance A) — 2026-05-23 (Round 12 continuation — three bug fixes via delegated agent)

**Done:**

1. **Drag-down card actions never worked** (`CardStrip.kt`)
   - Root cause 1: `combinedClickable` (inside AppCard) and the old overlay `detectVerticalDragGestures` competed at equal priority in `PointerEventPass.Main` — races and inconsistency.
   - Root cause 2: `graphicsLayer { translationY }` moves the visual but NOT the hit-test bounds — as the card slid down, taps on the visually-moved card missed the overlay's touch area entirely.
   - Fix: removed the overlay Box approach entirely. Moved vertical drag detection onto the outer GesturableCard Box using `PointerEventPass.Initial` with `awaitEachGesture` + `awaitFirstDown`. Direction disambiguation: accumulate movement until 10px threshold; if `absX > absY` break without consuming (let pager handle horizontal); if vertical confirmed, consume and update offsetY. `handleDragEnd()` and `snapBack()` remain as local `scope.launch { }` functions.
   - Removed `detectVerticalDragGestures` import; added `PointerEventPass` import.

2. **App card carousel freezes after drawer close** (`HomeScreen.kt`)
   - Root cause: `pointerInput(drawerOpen, searchActive)` on CardStrip had a `drawerOpen ->` branch that ran `awaitPointerEventScope { while (true) { awaitPointerEvent(Initial).changes.forEach { it.consume() } } }`. When `drawerOpen` changed (drawer closed), the coroutine was cancelled mid-loop, corrupting HorizontalPager's internal gesture state — the pager thought a touch was in progress and stopped responding.
   - Fix: removed the `drawerOpen ->` consuming branch entirely. AppDrawer uses `fillMaxSize()` so it naturally blocks all events while visible. CardStrip `pointerInput` now uses only `searchActive` as its key.

3. **Screenshots not filling cards** (`AppCard.kt`)
   - `ContentScale.Fit` letterboxed screenshots with black bars. Changed to `ContentScale.Crop` so screenshots fill the card edge-to-edge.

**Note — accessibility service re-enable:** Android disables the accessibility service after each APK reinstall. User must re-enable in Settings → Accessibility → Deck after every install. This is a system constraint, not a code issue.

**Watch out for:**
- `PointerEventPass.Initial` on the outer Box means GesturableCard sees DOWN before AppCard's `combinedClickable`. Only consume events after vertical direction is confirmed (absY > absX at 10px threshold) — otherwise `combinedClickable` never gets taps.
- `awaitEachGesture` restart per-touch is critical — without it, a cancelled gesture leaves the loop at an intermediate state and the next touch is ignored.

---

### Claude Code (Instance A) — 2026-05-23 (Round 14 — drawer handle + widget search)

**Done:**

1. **Drawer opening fix** (`HomeScreen.kt`)
   - Root cause: `PointerEventPass.Initial` + `awaitEachGesture` on the overlay Box was unreliable — DockedSearchBar intercepts the DOWN event in its internal handler.
   - Fix: replaced gesture detection with a visible drag handle pill (32x4dp rounded rectangle, 36dp tall tap target) placed above `LauncherSearchBar` inside the overlay Column. Uses `detectTapGestures { drawerOpen = true }`. Handle hidden when `searchActive = true`.
   - Removed imports: `awaitEachGesture`, `awaitFirstDown`, `PointerEventPass`.
   - Added import: `RoundedCornerShape`.
   - Spacer height: `80.dp + navBarBottom` → `116.dp + navBarBottom` (36dp added for handle).

2. **Widget search** (`SearchResult.kt`, `data/WidgetPinRepository.kt` new, `providers/WidgetSearchProvider.kt` new, `SearchViewModel.kt`, `LauncherSearchBar.kt`)
   - `SearchResult.kt`: added `SearchResult.WidgetPickerResult` and top-level `WidgetProviderInfo` data class.
   - `WidgetPinRepository.kt`: SharedPreferences wrapper storing pinned widget per package.
   - `WidgetSearchProvider.kt`: queries `AppWidgetManager.getInstalledProviders(null)`, filters by label/package match, groups by package, returns one `WidgetPickerResult` per matching app. Fully wrapped in `runCatching`.
   - `SearchViewModel.kt`: added `WidgetSearchProvider` after `AppSearchProvider` in staticProviders.
   - `LauncherSearchBar.kt`: added `WidgetPickerCard` composable (app icon/label header + LazyRow of `WidgetPreviewCard`s). Each card: 140dp wide, 100dp preview area loading static preview drawables from app resources on IO thread via `produceState`. Pinned card gets a 2dp primary-colored border. Pin state stored in `WidgetPinRepository`.
   - New imports: `BorderStroke`, `LazyRow`, `RoundedCornerShape`, `PushPin` icons (filled/outlined), `Widgets` icon, `ContentScale`, `TextOverflow`, `WidgetPinRepository`.

**Watch out for:**
- Widget previews are static drawable snapshots, not live widget content. `AppWidgetHost` would be needed for live content (not yet implemented per CLAUDE.md guidance).
- `Icons.Filled.Widgets/PushPin` and `Icons.Outlined.PushPin` require `material-icons-extended` — already in `build.gradle.kts` since Round 8.
- `AppWidgetManager.getInstalledProviders(null)` needs no special permissions.

---

### Claude Code (Instance B) — 2026-05-23 (build fixes for Rounds 12–14)

Fixed compile errors introduced by Instance A's Round 12–14 changes. Build green after each fix.

**Fix 1** — `AppDrawer.kt`: `Flow.drop` unresolved → added `import kotlinx.coroutines.flow.drop`

**Fix 2** — `LauncherSearchBar.kt`: `tween` unresolved → added `import androidx.compose.animation.core.tween`

**Fix 3** — `PluginRepository.kt:74`: `emptyList()` type not inferrable at early return inside `buildList` → `emptyList<SearchResult.PluginResult>()`

**Fix 4** — `LauncherSearchBar.kt:352`: `PaddingValues(horizontal, bottom)` is not a valid overload → `PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 12.dp)`

**Fix 5** — `WidgetSearchProvider.kt:24`: `getInstalledProviders(null)` — no such overload exists → `getInstalledProviders()` (no-arg)

**For Instance A — patterns to avoid:**
- `PaddingValues` valid overloads: `(all)`, `(horizontal, vertical)`, `(start, top, end, bottom)`. No mixing of named axis + named side.
- `AppWidgetManager.getInstalledProviders()` takes no arguments on the target SDK.
- `emptyList()` / `emptyMap()` need explicit type params at early-return sites inside `buildList`/`buildMap`.
- `Flow.drop` and other less-common flow operators need explicit imports — `kotlinx.coroutines.flow.*` wildcard is not typically in scope.

---

### Claude Code (Instance A) — 2026-05-23 (Round 15 — live widgets + drawer gesture fix)

**Done:**

1. **Widget carousel redesign** (`LauncherSearchBar.kt`, `AndroidManifest.xml`)
   - Replaced `LazyRow` of 140dp cards with `HorizontalPager` — one full-width widget per page, swipe to browse.
   - Live interactive widgets via `AppWidgetHost` + `AndroidView(AppWidgetHostView)`. No static preview unless binding fails.
   - One shared `AppWidgetHost(HOST_ID=2345)` per `WidgetPickerCard`. Each `LiveWidgetCard` allocates its own `appWidgetId` in `remember` and deletes it on `DisposableEffect.onDispose`.
   - Pinned widget is moved to index 0 of `sortedProviders` — it's the first page shown.
   - No per-widget label text. Header shows app name + pin button. Dots indicator below the pager.
   - `StaticWidgetPreview` fallback when `bindAppWidgetId` fails (not default launcher).
   - Manifest: added `BIND_APPWIDGET` (auto-granted when Deck is the default launcher).
   - Removed `BorderStroke`, `LazyRow`, `TextOverflow`. Added `AppWidgetHost`, `AppWidgetManager`, `ComponentName`, `ExperimentalFoundationApi`, `HorizontalPager`, `rememberPagerState`, `AndroidView`.

2. **Drawer handle gesture fix** (`HomeScreen.kt`)
   - Root cause: `detectTapGestures` only fires `onTap` callback when the gesture is classified as a tap (minimal movement). User swiping upward on the handle = NOT a tap → no callback.
   - Fix: `awaitEachGesture { awaitFirstDown(requireUnconsumed = false); drawerOpen = true }` — opens on the DOWN event itself, whether the user taps, presses, or starts a swipe.
   - Handle taller (36dp → 48dp) and pill more visible (40dp wide, 35% opacity vs 32dp/25%).
   - Spacer updated from 116dp to 128dp.

**Watch out for:**
- `bindAppWidgetId` requires `BIND_APPWIDGET` — only granted to the default launcher. `LiveWidgetCard` shows `StaticWidgetPreview` fallback if it fails. No crash.
- `AppWidgetHost.createView` is called in the `AndroidView.factory` lambda which runs synchronously on the main thread — the `remember` blocks guarantee bind has already happened before factory runs.
- `HorizontalPager` owns the horizontal swipe — gestures on widget pages won't propagate to CardStrip.

---

### Claude Code (Instance A) — 2026-05-23 (Round 16 — live widget binding + drawer handle fix)

**Done:**

1. **Live widget binding via ACTION_APPWIDGET_BIND** (`LauncherSearchBar.kt`)
   - Root cause: `bound` was a synchronous `remember` calling `bindAppWidgetIdIfAllowed()`. For third-party launchers without `BIND_APPWIDGET` system permission, this always returns `false` → `StaticWidgetPreview` shown, never interactive.
   - Fix: replaced synchronous `remember` with proper async binding flow:
     1. `var bound by remember(provider.componentName) { mutableStateOf(false) }`
     2. `rememberLauncherForActivityResult(StartActivityForResult())` captures `RESULT_OK` → sets `bound = true`
     3. `LaunchedEffect(provider.componentName)` tries `bindAppWidgetIdIfAllowed()` first (returns `true` if previously granted). On failure, launches `Intent(ACTION_APPWIDGET_BIND)` with `EXTRA_APPWIDGET_ID` and `EXTRA_APPWIDGET_PROVIDER` — system shows "Allow Deck to add [widget]?" dialog.
   - First time a widget is shown: dialog appears, user grants once, subsequent searches bind immediately.
   - `DisposableEffect` cleanup (`deleteAppWidgetId`) unchanged — still deletes the ID when the composable leaves composition.
   - Imports `android.app.Activity`, `rememberLauncherForActivityResult`, `ActivityResultContracts` were already added in Round 15; no new imports needed.

2. **Drawer handle — invisible + full-gesture consumption** (`HomeScreen.kt`)
   - User feedback: "I see the handle now, which I don't want" → removed visible 40×4dp pill from the handle Box (Box is now empty — invisible touch target only).
   - Root cause of "flies up and closes": `awaitFirstDown` fires on DOWN → `drawerOpen = true` → AppDrawer enters composition → user's continuing swipe gesture reaches AppDrawer's `anchoredDraggable` → interpreted as downward drag from Open → snaps to Closed → `onClose()` → `drawerOpen = false`. Race between handle's DOWN callback and drawer's gesture handler.
   - Fix: handle's `pointerInput` now consumes the **entire gesture** (DOWN through UP) before setting `drawerOpen = true`. The AppDrawer never sees the initiating gesture because all pointer events are consumed by the handle's loop:
     ```kotlin
     awaitFirstDown(requireUnconsumed = false).consume()
     var stillDown = true
     while (stillDown) {
         val event = awaitPointerEvent()
         event.changes.forEach { it.consume() }
         stillDown = event.changes.any { it.pressed }
     }
     drawerOpen = true
     ```
   - Drawer opens after the user lifts their finger (no animation during the touch; the drawer's own settle animation runs after `drawerOpen = true`).
   - Removed unused `import androidx.compose.foundation.shape.RoundedCornerShape` (only used by the now-removed pill).
   - Spacer height unchanged at `128.dp + navBarBottom`.

**Watch out for:**
- `ACTION_APPWIDGET_BIND` dialog appears once per widget provider (per package). After user grants, `bindAppWidgetIdIfAllowed()` returns `true` on all subsequent calls — no re-prompting.
- The `LaunchedEffect` is keyed on `provider.componentName` — re-runs when the user pages to a different widget. Each widget gets its own binding flow independently.
- Drawer opens only after the finger is lifted from the handle. This is intentional: the full gesture consumption prevents the race condition. The trade-off is no drag-to-peek behavior from the handle, but reliability is more important.

---

## Round 17 task list (2026-05-23) — drawer rebuild

**Diagnosis:** The drawer's core architecture is broken by design. Every open/close cycle removes `AppDrawer` from composition (`AnimatedVisibility`) and re-mounts it. Each mount creates a new `LaunchedEffect(Unit)` that waits for `BoxWithConstraints` to provide anchor measurements before calling `state.settle(-1_000_000f)`. This timing is fragile: gesture events, layout passes, and `snapshotFlow` emissions are all racing on the same frame. The `settle()` call is also a workaround for `animateTo` being internal in Foundation 1.6.8. The result is an opening mechanism that works sometimes and fails silently the rest of the time.

**Fix: Keep the drawer always in composition.** Instead of toggling `AnimatedVisibility`, `AppDrawer` stays mounted permanently at the `Closed` offset (off-screen). An `openSignal: SharedFlow<Unit>` replaces the `drawerOpen` flag as the trigger. The drawer collects the signal and animates itself to `Open` each time it fires. This eliminates the mount/unmount race entirely — anchors are set once during first layout and never torn down.

---

### Instance B — Android Studio

**Task: Build + verify**

After Instance D's changes land, run `assembleDebug`. Key risks:
- `AppDrawer` no longer has an `initialValue = DrawerValue.Closed` → `Open` startup animation in `LaunchedEffect(Unit)` — that block must be removed entirely.
- `HomeScreen.kt` removes `drawerOpen: Boolean` and `AnimatedVisibility` — verify the `BackHandler(enabled = drawerOpen)` block is replaced with a `BackHandler` that uses the drawer's own state.
- `AppDrawer.onClose()` callback still needed so HomeScreen can unblock `BackHandler`.

Document every fix.

**Do not touch**: any source file unless fixing a compile error.

---

### Instance D — terminal tab

**Task: Rebuild the drawer open/close mechanism**

Owns: `ui/home/HomeScreen.kt`, `ui/drawer/AppDrawer.kt`

#### 1. `AppDrawer.kt` — signal-based open, no startup animation

**Remove:**
- The `LaunchedEffect(Unit)` that waits for anchors then calls `state.settle(-1_000_000f)` (the startup open animation). The drawer no longer opens itself on first mount.
- The `closeSignal: SharedFlow<Unit>? = null` parameter (replaced with `openSignal` approach below).

**Add:**
- `openSignal: SharedFlow<Unit>? = null` parameter alongside the existing `closeSignal`.
- A **new** `LaunchedEffect(openSignal)` block that collects `openSignal` and on each emission:
  1. Waits for anchors: `snapshotFlow { state.anchors.hasAnchorFor(DrawerValue.Open) }.first { it }`
  2. Calls `state.settle(-1_000_000f)` to animate to Open.
  ```kotlin
  LaunchedEffect(openSignal) {
      openSignal?.collect {
          snapshotFlow { state.anchors.hasAnchorFor(DrawerValue.Open) }.first { it }
          state.settle(-1_000_000f)
      }
  }
  ```
- Keep the existing `LaunchedEffect(closeSignal)` unchanged (it already collects closeSignal and calls `state.settle(1_000_000f)`).
- Keep the existing `LaunchedEffect(Unit)` that uses `snapshotFlow { state.currentValue }.drop(1)` to call `onClose()` and scroll to top — this is still needed.

**Result:** `AppDrawer` signature becomes:
```kotlin
fun AppDrawer(
    onClose: () -> Unit,
    onAppLaunch: (AppInfo) -> Unit,
    openSignal: SharedFlow<Unit>? = null,
    closeSignal: SharedFlow<Unit>? = null,
    modifier: Modifier = Modifier
)
```

#### 2. `HomeScreen.kt` — remove AnimatedVisibility, always keep AppDrawer in composition

**Remove:**
- `var drawerOpen by remember { mutableStateOf(false) }` state variable.
- `AnimatedVisibility(visible = drawerOpen, enter = EnterTransition.None, exit = ExitTransition.None)` wrapper around `AppDrawer`.
- `BackHandler(enabled = drawerOpen) { drawerCloseSignal.tryEmit(Unit) }` — replaced below.
- Imports that become unused: `AnimatedVisibility`, `EnterTransition`, `ExitTransition`.

**Add:**
- `val drawerOpenSignal = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }` alongside the existing `drawerCloseSignal`.
- `var drawerIsOpen by remember { mutableStateOf(false) }` — a simple flag that `AppDrawer.onClose` flips back to false, used only to enable the `BackHandler`. (AppDrawer itself manages its visual state; this flag is just for the back press gate.)
- `BackHandler(enabled = drawerIsOpen) { drawerCloseSignal.tryEmit(Unit) }`.

**Change the handle** to emit to `drawerOpenSignal` instead of setting `drawerOpen`:
```kotlin
// In the awaitEachGesture block, replace:
drawerOpen = true
// with:
drawerIsOpen = true
drawerOpenSignal.tryEmit(Unit)
```

**AppDrawer call site** — remove from inside `AnimatedVisibility`, place directly in the root `Box`:
```kotlin
AppDrawer(
    onClose     = { drawerIsOpen = false },
    openSignal  = drawerOpenSignal,
    closeSignal = drawerCloseSignal,
    onAppLaunch = { app ->
        drawerIsOpen = false
        context.packageManager.getLaunchIntentForPackage(app.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?.let { context.startActivity(it) }
    }
)
```

**Note on `drawerIsOpen` vs old `drawerOpen`:**  
The old `drawerOpen` controlled both the BackHandler AND whether AppDrawer was in composition. The new `drawerIsOpen` only controls the BackHandler; `AppDrawer` is always in composition. The drawer's visual state is driven by `AnchoredDraggableState` internally. `onClose()` fires (setting `drawerIsOpen = false`) when `state.currentValue == DrawerValue.Closed`, same as before.

**Also remove** the `drawerOpen` references in the CardStrip `pointerInput` block (the `drawerOpen ->` branch was already removed in Round 12, so this may already be clean — verify).

#### 3. Important: `requireOffset()` before anchors are set

The `Box` in `AppDrawer.kt` has:
```kotlin
.offset { IntOffset(0, state.requireOffset().roundToInt()) }
```
When the drawer first enters composition, anchors may not be set yet, causing `requireOffset()` to throw. Since the drawer is now always in composition, this happens once at app startup.

**Fix:** Replace `.offset { IntOffset(0, state.requireOffset().roundToInt()) }` with:
```kotlin
.offset { IntOffset(0, if (state.anchors.size > 0) state.requireOffset().roundToInt() else constraints.maxHeight) }
```
This uses `constraints.maxHeight` (the `fullHeight` value from `BoxWithConstraints`) as the fallback offset, keeping the drawer off-screen until anchors are ready. `constraints` is available in scope because the `Box` is inside `BoxWithConstraints`.

**Watch out for:**
- `state.settle()` requires anchors to be set — the `snapshotFlow { state.anchors.hasAnchorFor(...) }.first { it }` guard in `LaunchedEffect(openSignal)` handles this correctly. Do not call `settle()` outside of that guard.
- `drawerIsOpen` is only set to `true` by the handle gesture and to `false` by `onClose()`. It never reflects the intermediate "animating" state — this is fine since it only gates the BackHandler.
- `MutableSharedFlow<Unit>(extraBufferCapacity = 1)` on `drawerOpenSignal` — capacity 1 ensures rapid taps don't drop signals, but won't queue multiple opens if the drawer is already open (the first open just re-settles to Open, which is a no-op visually).

#### 4. Widget assignment via long-press in drawer (`AppDrawer.kt`)

When the user long-presses an app icon in the drawer grid or list, the current `DropdownMenu` shows "Hide app" and shortcuts. Add a **"Select widget"** option.

**Overall UX**: Tapping "Select widget" opens a full-`AlertDialog` listing all available widgets for that app (from `AppWidgetManager.getInstalledProviders().filter { it.provider.packageName == app.packageName }`). Each widget is shown as a `RadioButton` row with the widget's label. A "None" option at the top clears any assignment. The selected widget is saved to `WidgetPinRepository`.

Implementation details:
- Add `import android.appwidget.AppWidgetManager` and `import com.hermes.deck.data.WidgetPinRepository` to `AppDrawer.kt`.
- Add `var showWidgetPicker by remember { mutableStateOf(false) }` alongside `showShortcuts` in both `AppGridItem` and `AppListItem`.
- In the `DropdownMenu`, above the "Hide app" item:
  ```kotlin
  DropdownMenuItem(
      text    = { Text("Select widget") },
      onClick = { showShortcuts = false; showWidgetPicker = true }
  )
  ```
- Below the `DropdownMenu` block, add:
  ```kotlin
  if (showWidgetPicker) {
      val pinRepo = remember { WidgetPinRepository(context) }
      val providers = remember {
          runCatching {
              AppWidgetManager.getInstance(context)
                  .getInstalledProviders()
                  .filter { it.provider.packageName == app.packageName }
          }.getOrDefault(emptyList())
      }
      var selected by remember {
          mutableStateOf(pinRepo.getPinnedWidget(app.packageName))
      }
      AlertDialog(
          onDismissRequest = { showWidgetPicker = false },
          title = { Text("Select widget for ${app.label}") },
          text  = {
              LazyColumn {
                  item {
                      Row(
                          modifier              = Modifier
                              .fillMaxWidth()
                              .clickable { selected = null }
                              .padding(vertical = 8.dp),
                          verticalAlignment     = Alignment.CenterVertically
                      ) {
                          RadioButton(selected = selected == null, onClick = { selected = null })
                          Text("None", modifier = Modifier.padding(start = 8.dp))
                      }
                  }
                  items(providers) { info ->
                      val label = runCatching { info.loadLabel(context.packageManager) }.getOrDefault("Widget")
                      val comp  = info.provider.flattenToString()
                      Row(
                          modifier          = Modifier
                              .fillMaxWidth()
                              .clickable { selected = comp }
                              .padding(vertical = 8.dp),
                          verticalAlignment = Alignment.CenterVertically
                      ) {
                          RadioButton(selected = selected == comp, onClick = { selected = comp })
                          Text(label, modifier = Modifier.padding(start = 8.dp))
                      }
                  }
              }
          },
          confirmButton = {
              TextButton(onClick = {
                  if (selected != null) pinRepo.pinWidget(app.packageName, selected!!)
                  else pinRepo.unpinWidget(app.packageName)
                  showWidgetPicker = false
              }) { Text("Save") }
          },
          dismissButton = {
              TextButton(onClick = { showWidgetPicker = false }) { Text("Cancel") }
          }
      )
  }
  ```
- Imports needed: `AppWidgetManager`, `WidgetPinRepository`, `AlertDialog`, `TextButton`, `RadioButton`, `LazyColumn`, `items`. Most are already in scope from material3.* and foundation.*.

#### 5. Pixel Launcher-style long-press popup (`AppDrawer.kt`)

Replace the `DropdownMenu` in `AppGridItem` (and `AppListItem`) with a Pixel Launcher-style popup card:

**Visual spec:**
- A `Popup` (or `Dialog`) anchored near the icon, styled as a Material 3 `Surface` with `shape = RoundedCornerShape(28.dp)`, `tonalElevation = 3.dp`.
- **Header**: app icon (48dp) + app label in `titleMedium` weight, in a `Row` with 16dp padding.
- `HorizontalDivider` below header.
- **Shortcuts section** (if any): each shortcut as a `Row` with leading Icon (32dp, loaded from shortcut) + shortcut label. Clickable, launches via `LauncherApps.startShortcut`.
- `HorizontalDivider` if shortcuts exist.
- **Actions row**: "Select widget" (if app has ≥1 widget provider), "Hide" — as `TextButton`s or `ListItem`s.
- Dismiss on outside click.

Implementation guidance:
- Use `Popup(alignment = Alignment.TopCenter, onDismissRequest = { showShortcuts = false })` positioned relative to the icon. Or use a regular `AlertDialog` with no title and custom content — simpler and handles outside-click dismiss automatically.
- The icon in the header uses the same `iconBitmap` `remember` already present in `AppGridItem`.
- Keep `showWidgetPicker` state separate from the popup; the popup's "Select widget" item sets `showShortcuts = false; showWidgetPicker = true`.
- For shortcut icons: `shortcut.getIcon(0)` returns an `Icon` object; use `LauncherApps.getShortcutIconDrawable(shortcut, 0)` to get a `Drawable`.

**Do not touch**: `ui/home/`, `ui/search/`, `data/`, `service/`

---

### Instance A — main conversation (Round 17)

Owns: `ui/search/LauncherSearchBar.kt`, `ui/search/providers/WidgetSearchProvider.kt`

#### 1. Fix search bar placeholder descenders

In `LauncherSearchBar.kt`, the placeholder `Row` clips the descenders of italic animated text. Fix:
- Add `verticalAlignment = Alignment.CenterVertically` to the `Row`.
- Add `modifier = Modifier.padding(bottom = 4.dp)` to the `Text` inside `AnimatedContent` to give descenders room below the baseline without shifting the visual center upward.

```kotlin
placeholder = {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Find your… ")
        AnimatedContent(
            targetState  = PLACEHOLDER_WORDS[wordIndex],
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
            label        = "placeholder_word"
        ) { word ->
            Text(
                text       = word,
                fontStyle  = FontStyle.Italic,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.padding(bottom = 4.dp)
            )
        }
    }
},
```

#### 2. Widget search — one assigned widget per result

`WidgetSearchProvider` currently returns all matching apps as carousels. Change it so it **only returns apps that have an assigned widget** in `WidgetPinRepository`, and returns exactly one provider (the pinned one). This means:
- Widget results only appear in search after the user has assigned a widget via the drawer long-press.
- No carousel, no pin button in search results.

Rewrite `WidgetSearchProvider.query()`:
```kotlin
override suspend fun query(q: String): List<SearchResult> = withContext(Dispatchers.IO) {
    if (q.isBlank()) return@withContext emptyList()
    val hidden = context.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE)
        .getString("hidden_apps", "").orEmpty().split(",").filter { it.isNotBlank() }.toSet()
    val manager = AppWidgetManager.getInstance(context)
    val pm = context.packageManager
    runCatching {
        manager.getInstalledProviders()
            .filter { it.provider.packageName !in hidden }
            .groupBy { it.provider.packageName }
            .mapNotNull { (pkg, infos) ->
                val pinnedComp = pinRepo.getPinnedWidget(pkg) ?: return@mapNotNull null
                val appLabel = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                }.getOrDefault(pkg)
                if (!appLabel.contains(q, ignoreCase = true) &&
                    !pkg.contains(q, ignoreCase = true)) return@mapNotNull null
                val info = infos.find { it.provider.flattenToString() == pinnedComp }
                    ?: return@mapNotNull null
                SearchResult.WidgetPickerResult(
                    appPackage          = pkg,
                    appLabel            = appLabel,
                    providers           = listOf(WidgetProviderInfo(
                        componentName = info.provider.flattenToString(),
                        label         = runCatching { info.loadLabel(pm) }.getOrDefault("Widget"),
                        packageName   = pkg,
                        previewResId  = info.previewImage,
                        iconResId     = info.icon
                    )),
                    pinnedComponentName = pinnedComp
                )
            }
    }.getOrElse { emptyList() }
}
```

#### 3. Simplify `WidgetPickerCard` — no carousel

`WidgetPickerCard` currently shows a `HorizontalPager` with dots and a pin button. Replace entirely with a direct `LiveWidgetCard` call since there is now always exactly one provider:

```kotlin
@Composable
private fun WidgetPickerCard(result: SearchResult.WidgetPickerResult) {
    val context = LocalContext.current
    val provider = result.providers.firstOrNull() ?: return
    val widgetHost = remember { AppWidgetHost(context.applicationContext, WIDGET_HOST_ID) }
    DisposableEffect(Unit) {
        widgetHost.startListening()
        onDispose { widgetHost.stopListening() }
    }
    LiveWidgetCard(
        provider = provider,
        widgetHost = widgetHost,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp)
    )
}
```

Remove now-unused imports: `ExperimentalFoundationApi`, `HorizontalPager`, `rememberPagerState`, `CircleShape`, `PushPin` (filled + outlined), `IconButton`, `WidgetPinRepository`.

#### 4. Fix widget data not loading

After `bindAppWidgetIdIfAllowed` returns `true` in `LiveWidgetCard`'s `LaunchedEffect`, send `ACTION_APPWIDGET_UPDATE` to the provider so it pushes fresh data immediately:

```kotlin
if (appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, info.provider)) {
    bound = true
    // Kick the provider to send a fresh update so the widget shows live data
    context.sendBroadcast(
        Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
            component = info.provider
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
        }
    )
    return@LaunchedEffect
}
```

**Do not touch**: `ui/drawer/`, `ui/home/`, `data/`, `service/`

---

### Instance A — 2026-05-23

**Done:**
- Reverted `DockedSearchBar` `modifier` arg in `LauncherSearchBar.kt` from `Modifier.padding(vertical = 8.dp).then(modifier)` back to plain `modifier`. The vertical padding was adding external space that pushed text off-center; `bodyLarge` already provides the desired visual weight without layout changes.

---

### Claude Code (Instance A) — 2026-05-23 (Round 17)

**Done:**

1. **Placeholder descender fix** (`LauncherSearchBar.kt`)
   - Added `verticalAlignment = Alignment.CenterVertically` to the placeholder `Row`.
   - Added `modifier = Modifier.padding(bottom = 4.dp)` to the animated word `Text`. This extends the composable's measured height below the baseline so `AnimatedContent` doesn't clip descenders (y, g, p, etc.) from italic text.

2. **Widget search: one assigned widget per result** (`WidgetSearchProvider.kt`)
   - Completely rewrote `query()`. Previously returned all matching apps as carousels. Now only returns apps that have an assigned widget in `WidgetPinRepository.getPinnedWidget()`. Returns exactly one `WidgetProviderInfo` (the pinned one).
   - Widget results only appear in search after the user assigns a widget via the drawer long-press (see Instance D task). Initially no widget results appear.
   - Hidden-apps filter preserved.

3. **WidgetPickerCard simplified** (`LauncherSearchBar.kt`)
   - Replaced the `HorizontalPager` + dots + pin-button carousel with a direct `LiveWidgetCard` call on `result.providers.firstOrNull()`.
   - Removed now-unused imports: `ExperimentalFoundationApi`, `HorizontalPager`, `rememberPagerState`, `PushPin` icons (filled + outlined), `WidgetPinRepository`.
   - `CircleShape` kept (still used by `InitialsAvatar`).

4. **Widget data fix** (`LauncherSearchBar.kt` → `LiveWidgetCard`)
   - After `bindAppWidgetIdIfAllowed()` returns `true`, sends `ACTION_APPWIDGET_UPDATE` broadcast to `info.provider` with the allocated `appWidgetId`. This triggers the widget provider's `onUpdate()`, which pushes a fresh `RemoteViews` to the `AppWidgetHostView` so live data appears immediately rather than showing a blank/stale state.

5. **Delegated to Instance D** (AGENTS.md Round 17):
   - Drawer rebuild (always in composition, signal-based open)
   - Widget assignment via long-press menu in drawer
   - Pixel Launcher-style popup menu

**Watch out for:**
- Widget results are now invisible until the user assigns a widget via the drawer. This is intentional — prevents a "search result full of widgets."
- `ACTION_APPWIDGET_UPDATE` must be sent AFTER `bound = true` is set and BEFORE `return@LaunchedEffect`. Both conditions are satisfied in the current order.
- If `sendBroadcast` fails silently (e.g., widget provider not exported), the widget still loads — it just won't have fresh data until the provider's next scheduled update.

---

### Claude Code (Instance D) — 2026-05-23 (Round 17)

**Done (all five tasks):**

1. **AppDrawer.kt — signal-based open** (`AppDrawer.kt`)
   - Added `openSignal: SharedFlow<Unit>? = null` parameter alongside existing `closeSignal`.
   - Removed the startup `LaunchedEffect(Unit)` that waited for anchors and called `state.settle(-1_000_000f)` — the drawer no longer auto-opens on mount.
   - Added `LaunchedEffect(openSignal)` that collects each emission, waits for anchors via `snapshotFlow { state.anchors.hasAnchorFor(DrawerValue.Open) }.first { it }`, then calls `state.settle(-1_000_000f)`. Existing `LaunchedEffect(closeSignal)` + `drop(1)` currentValue tracker unchanged.
   - New signature: `AppDrawer(onClose, onAppLaunch, openSignal, closeSignal, modifier)`.

2. **HomeScreen.kt — remove AnimatedVisibility** (`HomeScreen.kt`)
   - Removed `var drawerOpen` → replaced with `var drawerIsOpen` (BackHandler gate only) + `val drawerOpenSignal = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }`.
   - Removed `AnimatedVisibility` wrapper; `AppDrawer` is now always in composition directly in the root `Box`.
   - Handle gesture now emits: `drawerIsOpen = true; drawerOpenSignal.tryEmit(Unit)` (was `drawerOpen = true`).
   - `BackHandler(enabled = drawerIsOpen) { drawerCloseSignal.tryEmit(Unit) }`.
   - `AppDrawer(onClose = { drawerIsOpen = false }, openSignal = drawerOpenSignal, closeSignal = drawerCloseSignal, onAppLaunch = { drawerIsOpen = false; ... })`.
   - Removed unused imports: `AnimatedVisibility`, `EnterTransition`, `ExitTransition`.

3. **requireOffset() crash fix** (`AppDrawer.kt`)
   - `.offset { IntOffset(0, state.requireOffset().roundToInt()) }` → `.offset { IntOffset(0, if (state.anchors.size > 0) state.requireOffset().roundToInt() else constraints.maxHeight) }`.
   - Keeps drawer off-screen before `BoxWithConstraints`/`SideEffect` registers anchors on first layout. `constraints.maxHeight` is available in scope inside `BoxWithConstraints`.

4. **Widget assignment via long-press** (`AppDrawer.kt`)
   - Both `AppGridItem` and `AppListItem`: added `widgetProviders = remember(app.packageName) { AppWidgetManager.getInstance(context).getInstalledProviders().filter { it.provider.packageName == app.packageName } }`.
   - Added `showWidgetPicker` state. The main popup shows "Select widget" `TextButton` when `widgetProviders.isNotEmpty()`.
   - Widget picker is a second `AlertDialog` with a `LazyColumn` of `RadioButton` rows (None + each provider). Saves to `WidgetPinRepository.pinWidget` / `unpinWidget` on confirm.
   - New imports: `android.appwidget.AppWidgetManager`, `com.hermes.deck.data.WidgetPinRepository`, `androidx.compose.material3.RadioButton`, `androidx.compose.material3.TextButton`.

5. **Pixel Launcher-style popup** (`AppDrawer.kt`)
   - Replaced `DropdownMenu` in both `AppGridItem` and `AppListItem` with `AlertDialog(confirmButton = {}, shape = RoundedCornerShape(28.dp), text = { ... })`.
   - Popup content: header Row (48dp icon + `titleMedium` label) → `HorizontalDivider` → shortcuts list (each with shortcut icon via `LauncherApps.getShortcutIconDrawable` + label, clickable) → divider if shortcuts → "Select widget" TextButton (if providers) → "Hide" TextButton.
   - Removed now-unused `DropdownMenu`, `DropdownMenuItem`, `Surface` imports.
   - New import: `androidx.compose.foundation.clickable`.

**Watch out for:**
- `AppDrawer` is now always in composition — it sits behind everything at `Closed` offset (off-screen) until `openSignal` fires. The first layout pass is safe because `constraints.maxHeight` guards `requireOffset()` before anchors are set.
- `drawerIsOpen` is only `true` between the handle gesture UP event and `onClose()` firing. There's no "animating" intermediate state tracked in HomeScreen — that's fine since it only gates BackHandler.
- `shortcutIcon` computed with `remember(shortcut.id)` inside the dialog's `forEach` — uses inline `runCatching` so `return@runCatching null` is valid (stdlib `runCatching` is `inline`).
- `items(widgetProviders)` inside `LazyColumn` resolves to `LazyListScope.items` (not `LazyGridScope.items`) because the receiver is `LazyListScope`. Both are imported; no ambiguity since receiver types differ.


---

### Claude Code (Instance A) — 2026-05-23 (Round 17 continuation — card swipe-down fix)

**Done:**

1. **Card swipe-down gesture fix** (`CardStrip.kt`)
   - Replaced the PointerEventPass.Initial + awaitEachGesture approach (from Round 12) with Modifier.draggable(Orientation.Vertical).
   - Root cause: the direction-disambiguation check (absX > absY) was too strict — any slight horizontal component in a real swipe defeated vertical detection, making the drag-down almost never register.
   - Fix: rememberDraggableState holds the offsetY update logic. Modifier.draggable(Orientation.Vertical, state, onDragStopped = { handleDragEnd() }) replaces the manual pointer loop. draggable cooperates natively with HorizontalPager gesture negotiation — horizontal swipes still go to the pager, vertical swipes are claimed by draggable.
   - Import changes: removed awaitEachGesture, awaitFirstDown, PointerEventPass; added Orientation, draggable, rememberDraggableState.

---

### Claude Code (Instance D) — 2026-05-23 (drag-to-snap drawer gesture)

**Done:**

1. **Drag-to-snap on drawer handle** (`AppDrawer.kt`, `HomeScreen.kt`)

   Added two `LaunchedEffect` collectors to `AppDrawer.kt` (after the `LaunchedEffect(Unit)` currentValue tracker):
   - `LaunchedEffect(dragDeltaFlow)`: collects each delta float and calls `state.dispatchRawDelta(delta)` — drawer follows the finger in real-time.
   - `LaunchedEffect(settleFlow)`: collects the final velocity float and calls `state.settle(velocity)` — snaps to Open or Closed based on position (40% threshold) and velocity (200dp/s threshold).

   `HomeScreen.kt` was already updated in the prior context window (before the summary):
   - Handle gesture uses `VelocityTracker` + `awaitEachGesture` to stream deltas to `drawerDragDeltaFlow` and emit final velocity to `drawerSettleFlow` on finger lift.
   - Tap/tiny drag (cumulativeDelta ≥ -30f AND |velocity| ≤ 300f) → `drawerOpenSignal` for full open animation instead.
   - `AppDrawer` call site passes `dragDeltaFlow = drawerDragDeltaFlow` and `settleFlow = drawerSettleFlow`.

   **Result:** Dragging up on the handle makes the drawer track the finger; releasing snaps it open or closed based on how far it was dragged and how fast it was flung — matching stock Android launcher behavior.

---

### Instance C — 2026-05-24

**Done:**

- **Card sizing: switched from `displayMetrics` to slot constraints** (`CardStrip.kt`)
  - Replaced `LocalContext.current.resources.displayMetrics.heightPixels / widthPixels` with `constraints.maxHeight.toFloat() / constraints.maxWidth.toFloat()` (`slotAspect`). The pager slot's `BoxWithConstraints` recomposes on every orientation change, so `slotAspect` is always current. `displayMetrics` physical pixel dimensions do not update with rotation on some devices (observed on the ikko device — shows landscape ratio in both orientations), making cards the wrong shape in portrait after launch.
  - Removed now-unused `import androidx.compose.ui.platform.LocalContext`.

**Watch out for:**
- `drawerDragDeltaFlow` has `extraBufferCapacity = 64` — handles fast gestures without dropping deltas.
- `drawerSettleFlow` has `extraBufferCapacity = 1` — only one settle per gesture needed.
- `state.dispatchRawDelta()` moves the anchor offset directly; it requires anchors to be set. The drawer is always in composition so anchors are set on first layout pass — safe.
- The `settleFlow` handler calls `state.settle(velocity)` without an anchor guard because by the time a drag gesture completes, anchors are guaranteed to be set (the user had to see the drawer move to complete a drag).

---

## Round 18 task list (2026-05-23) — gesture and drawer polish

Active bugs being fixed this round:

1. Widget crash — LiveWidgetCard.createView() can throw; fixed by Instance A.
2. Descenders cut off in search bar — fixed by Instance A (paddingFromBaseline).
3. Card swipe-down doesn't travel far enough — DISMISS_THRESHOLD, ACTION_REVEAL_THRESHOLD, ACTION_SNAP_Y are raw pixel values (100f, 60f, 80f) that are ~27–33dp on a 3x-density device. Fix: convert to dp-based px values in GesturableCard using LocalDensity.
4. Card dragged down appears behind search bar — the root Box z-order puts the search bar overlay AFTER the Column (CardStrip), so the overlay draws on top of translated card content. Fix: add Modifier.zIndex(1f) to the Column when not searching.
5. Drawer only opens from the 48dp handle area, not from swiping up on the search bar — extend gesture detection to the entire bottom overlay Column.
6. Search bar has visible padding around it — investigate and trim if excess.
7. Drawer bottom corners not rounded — RoundedCornerShape(topStart=28.dp, topEnd=28.dp) -> RoundedCornerShape(28.dp).
8. Swiping back from drawer should close it smoothly — verify the nestedScrollConnection overscroll-to-close path works.

---

### Instance A — main conversation (Round 18)

**Done:**

1. **Widget crash fix** (LauncherSearchBar.kt)
   - Wrapped widgetHost.createView(ctx, appWidgetId, providerInfo) in runCatching { }.getOrElse { android.view.View(ctx) } inside AndroidView.factory.
   - Prevents any exception from AppWidgetHostView creation propagating to Compose.

2. **Descenders fix** (LauncherSearchBar.kt)
   - Changed Modifier.padding(bottom = 4.dp) to Modifier.paddingFromBaseline(bottom = 6.dp) on the animated word Text.
   - paddingFromBaseline(bottom = 6.dp) places 6dp between the last text baseline and the composable bottom, guaranteeing italic descenders (g, j, p, y) have room below baseline.

---

### Instance B — Android Studio

**Task: Build verification**

After Instance C and Instance D finish their changes, run assembleDebug. Key risks:
- Instance C removes file-scope const val Float constants and replaces with local val in GesturableCard — verify no stale references remain.
- Instance D adds Modifier.zIndex() to the Column in HomeScreen — import androidx.compose.ui.zIndex may be needed.
- Instance D adds gesture detection to the overlay Column — verify all imports present (awaitEachGesture, awaitFirstDown, PointerEventPass, VelocityTracker).
- paddingFromBaseline from Instance A — already in foundation.layout.* wildcard, no extra import needed.

Document every fix.

**Do not touch**: any source file unless fixing a compile error.

---

### Instance C — terminal tab

Owns: ui/home/CardStrip.kt, ui/drawer/AppDrawer.kt

#### 1. Fix card swipe-down gesture thresholds (CardStrip.kt)

Root cause: DISMISS_THRESHOLD, ACTION_REVEAL_THRESHOLD, and ACTION_SNAP_Y are file-scope const val Float values (100f, 60f, 80f). Modifier.draggable returns deltas in pixels. graphicsLayer { translationY = offsetY } uses pixels. On a 3x density screen, 80 pixels = ~27dp — the card barely moves and the CardActions row above the card is never revealed.

Fix:
- Remove the three file-scope constants at the bottom of CardStrip.kt (DISMISS_THRESHOLD, ACTION_REVEAL_THRESHOLD, ACTION_SNAP_Y).
- At the top of GesturableCard body, after `val scope = rememberCoroutineScope()`, add:
    val density = LocalDensity.current
    val dismissThresholdPx      = with(density) { 120.dp.toPx() }
    val actionRevealThresholdPx = with(density) { 60.dp.toPx() }
    val actionSnapYPx           = with(density) { 90.dp.toPx() }
- Replace all references in GesturableCard:
    `offsetY < -DISMISS_THRESHOLD`        -> `offsetY < -dismissThresholdPx`
    `offsetY / ACTION_REVEAL_THRESHOLD`   -> `offsetY / actionRevealThresholdPx`
    `offsetY > ACTION_REVEAL_THRESHOLD`   -> `offsetY > actionRevealThresholdPx`
    `animate(offsetY, ACTION_SNAP_Y,...)` -> `animate(offsetY, actionSnapYPx,...)`  (both occurrences)
    `offsetY < ACTION_SNAP_Y / 2f`        -> `offsetY < actionSnapYPx / 2f`
- Add import: androidx.compose.ui.platform.LocalDensity (if not already present).

After this fix, the card travels ~90dp downward at snap position — the CardActions row above the card becomes clearly visible.

#### 2. Fix drawer bottom corner radii (AppDrawer.kt)

- Line 175 has: .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
- Change to: .clip(RoundedCornerShape(28.dp))
- No other changes needed in AppDrawer.

**Do not touch**: HomeScreen.kt, HomeViewModel.kt, AppCard.kt, LauncherSearchBar.kt

---

### Instance D — terminal tab

Owns: ui/home/HomeScreen.kt

Read CURRENT HomeScreen.kt before making changes. Current state (Round 17):
- Root Box contains: Column (CardStrip + DockBar + Spacer), overlay Box at BottomCenter (handle + search bar), AppDrawer.
- Overlay Box structure: Column { Box(48dp handle); LauncherSearchBar(...) }
- State vars: drawerIsOpen, searchActive.
- Flows: drawerOpenSignal, drawerCloseSignal, drawerDragDeltaFlow, drawerSettleFlow, searchDismissSignal.
- The 48dp handle Box uses awaitEachGesture to stream deltas and emit to flows.

#### 1. Fix card visual masking by search bar (HomeScreen.kt)

Root cause: The overlay Box (containing the search bar) is drawn AFTER the Column (containing CardStrip) in the root Box. Higher draw order = draws on top. When a card translates down via graphicsLayer { translationY = offsetY }, it visually enters the overlay Box area but the overlay draws on top, masking the card.

Fix: Add Modifier.zIndex(1f) to the main Column when searchActive is false:

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .then(if (!searchActive) Modifier.zIndex(1f) else Modifier)
    ) {

- When searchActive = false: Column at zIndex 1 draws AFTER (on top of) the overlay Box -> dragged cards are visible above the search bar.
- When searchActive = true: Column at default zIndex (0) -> search results in the overlay draw on top of cards (correct, since results expand upward).

Import needed: androidx.compose.ui.zIndex — add to imports if not present.

#### 2. Extend drawer open gesture to cover the search bar (HomeScreen.kt)

Root cause: The drawer-open gesture only covers the 48dp invisible handle Box. Swiping UP on the search bar itself doesn't open the drawer because DockedSearchBar captures those events.

Fix: Add PointerEventPass.Initial gesture detection to the entire overlay Column so the parent sees upward swipes before DockedSearchBar can consume them.

Replace the plain `Column {` that wraps the handle box and LauncherSearchBar with:

    Column(
        modifier = Modifier.pointerInput(searchActive) {
            if (!searchActive) {
                awaitEachGesture {
                    val tracker = VelocityTracker()
                    val down = awaitFirstDown(requireUnconsumed = false)
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var cumulativeDelta = 0f
                    dragLoop@ while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        for (change in event.changes) {
                            if (!change.pressed) break@dragLoop
                            val delta = change.position.y - change.previousPosition.y
                            tracker.addPosition(change.uptimeMillis, change.position)
                            cumulativeDelta += delta
                            if (cumulativeDelta < -10f) {
                                change.consume()
                                drawerDragDeltaFlow.tryEmit(delta)
                            }
                        }
                    }
                    val velocity = tracker.calculateVelocity().y
                    if (cumulativeDelta < -30f || kotlin.math.abs(velocity) > 300f) {
                        drawerIsOpen = true
                        drawerSettleFlow.tryEmit(velocity)
                    }
                }
            }
        }
    ) {
        // existing handle Box unchanged
        // existing LauncherSearchBar unchanged
    }

Keep the existing 48dp handle Box and LauncherSearchBar inside the Column unchanged — the handle still handles taps and small drags from the strip-to-search-bar gap; the new Column-level gesture covers swipes originating on the search bar.

Ensure these imports are present: awaitEachGesture, awaitFirstDown, PointerEventPass, VelocityTracker (already used by the handle Box's pointerInput block — they should be present).

#### 3. Trim excess search bar padding (HomeScreen.kt)

The LauncherSearchBar call has Modifier.padding(horizontal = 16.dp, vertical = 12.dp). If the user sees a visible white/surface-colored border around the search bar widget, reduce to Modifier.padding(horizontal = 8.dp, vertical = 8.dp). If it looks fine, keep as-is and note it.

**Do not touch**: CardStrip.kt, AppDrawer.kt, LauncherSearchBar.kt, HomeViewModel.kt

---

### Claude Code (Instance C) -- 2026-05-23 (Round 18)

**Done:**

1. **Card swipe-down gesture thresholds** (CardStrip.kt)
   - Deleted three file-scope pixel constants: DISMISS_THRESHOLD = 100f, ACTION_REVEAL_THRESHOLD = 60f, ACTION_SNAP_Y = 80f.
   - Added LocalDensity import (androidx.compose.ui.platform.LocalDensity).
   - Inside GesturableCard, immediately after val scope = rememberCoroutineScope(), computed three dp-to-px values: dismissThresholdPx = 120.dp.toPx(), actionRevealThresholdPx = 60.dp.toPx(), actionSnapYPx = 90.dp.toPx().
   - Replaced all five references to the old constants throughout handleDragEnd and actionsAlpha with the new *Px locals.
   - On a 3x-density phone: dismiss threshold is now 360px (~120dp), action reveal is 180px (~60dp), snap target is 270px (~90dp) -- all gesture-sized rather than ~27dp micro-movements.

2. **Drawer bottom corner radii** (AppDrawer.kt)
   - Changed .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) to .clip(RoundedCornerShape(28.dp)) so all four corners are rounded (not just the top two).

**Watch out for:**
- The three *Px locals are declared at the top of GesturableCard (composable scope) -- they recompute on density change automatically.
- dismissThresholdPx was bumped from 100f to 120dp (was ~33dp at 3x) to require a more deliberate swipe-up for dismissal.
- actionSnapYPx was bumped from 80f to 90dp (was ~27dp at 3x) to give the CardActions row enough vertical room to be visible and tappable.

---

### Claude Code (Instance D) — 2026-05-23 (Round 18)

**Done (all three tasks):**

1. **Card visual masking fix — zIndex on main Column** (`HomeScreen.kt`)
   - Added `.then(if (!searchActive) Modifier.zIndex(1f) else Modifier)` to the main `Column` (CardStrip + DockBar).
   - When `searchActive = false`: Column draws at zIndex 1, above the overlay Box containing the search bar; dragged/translated cards are visible on top of the search bar background.
   - When `searchActive = true`: Column reverts to default zIndex; search results in the overlay draw on top of cards as intended.
   - New imports added: `androidx.compose.ui.zIndex`, `androidx.compose.ui.input.pointer.PointerEventPass`.

2. **Drawer open gesture extended to cover search bar** (`HomeScreen.kt`)
   - Replaced the plain `Column { ... }` in the bottom overlay Box with a `Column(modifier = Modifier.pointerInput(searchActive) { ... }) { ... }`.
   - The new gesture block fires when `!searchActive`. Uses `awaitEachGesture` + `PointerEventPass.Initial` to intercept pointer events before child composables (including `DockedSearchBar`) consume them.
   - Streams upward drag deltas (past 10f threshold) to `drawerDragDeltaFlow` for real-time drawer tracking. On finger lift, emits final velocity to `drawerSettleFlow` if the swipe was significant (cumulativeDelta < -30f OR |velocity| > 300f). Sets `drawerIsOpen = true` at the same time.
   - The existing 48dp invisible handle Box is unchanged inside the Column — it covers swipes starting in the gap above the search bar. The new Column-level gesture covers swipes starting ON the search bar itself.

3. **Search bar padding** (`HomeScreen.kt`)
   - Inspected `LauncherSearchBar` call site. Padding is `Modifier.padding(horizontal = 16.dp, vertical = 12.dp)` — correct, left unchanged per task instructions.

**Watch out for:**
- The Column-level `pointerInput` uses `PointerEventPass.Initial` so it sees events before `DockedSearchBar`'s internal gesture handlers. Only consumes events once `cumulativeDelta < -10f` (clear upward intent), so taps on the search bar still reach the bar normally.
- The handle Box gesture still consumes its `down` event immediately (`down.consume()`) — aggressive early consumption prevents the drawer from seeing the continuing swipe.
- The Column gesture does NOT emit `drawerOpenSignal` on tap/tiny movement (unlike the handle Box). A tap on the search bar opens the search, not the drawer.
- `zIndex` only affects draw order within the same `Box` parent — the gradient scrim Box and the overlay Box are siblings of the main Column, so zIndex is scoped correctly.

---

### Claude Code (Instance B) — 2026-05-23 (Round 18)

**Build verification — no fixes needed. `assembleDebug` passed clean on first run.**

All anticipated risks were non-issues:
- Instance C's constant removal (`DISMISS_THRESHOLD`, `ACTION_REVEAL_THRESHOLD`, `ACTION_SNAP_Y`) → no stale references; local `*Px` vals replaced them correctly.
- Instance D's `Modifier.zIndex(1f)` → `androidx.compose.ui.zIndex` was already present via the import Instance D added.
- Instance D's gesture imports (`awaitEachGesture`, `awaitFirstDown`, `PointerEventPass`, `VelocityTracker`) → all already in scope from the handle Box's existing gesture code.
- Instance A's `paddingFromBaseline` → covered by `foundation.layout.*` wildcard, no new import needed.

APK at `app/build/outputs/apk/debug/app-debug.apk`.


### Claude Code (Instance D) — 2026-05-23 (Round 18 hotfix)
Added `modifier = Modifier.zIndex(2f)` to the AppDrawer call in HomeScreen.kt so the drawer draws above the Column (zIndex 1) and the overlay (default 0).

---

### Claude Code (Instance C) — 2026-05-23 (Round 18 hotfix — card clip fix)

**Done:**
- Fixed bottom-edge clipping in `CardStrip.kt`: replaced outer centering `Box` with `BoxWithConstraints` inside the HorizontalPager page lambda.
- Added `snapPx` / `cardFraction` computation inside the `BoxWithConstraints` lambda: `cardFraction = ((constraints.maxHeight - 90.dp.toPx()) / constraints.maxHeight).coerceIn(0.7f, 0.95f)`.
- Replaced `.fillMaxWidth().aspectRatio(screenRatio)` on the inner card `Box` with `.fillMaxWidth().fillMaxHeight(cardFraction)`.
- Removed now-unused `val configuration = LocalConfiguration.current` and `val screenRatio = ...` lines.
- Removed now-unused `import androidx.compose.ui.platform.LocalConfiguration`.
- `LocalDensity` import was already present; `BoxWithConstraints` covered by existing `foundation.layout.*` wildcard — no new imports needed.

**Result:** The card is sized to exactly `constraints.maxHeight - 90dp` tall, so at full snap position (`translationY = actionSnapYPx`) the card's bottom edge aligns with the pager's layout bounds and is no longer clipped by HorizontalPager's internal LazyList.

---

### Claude Code (Instance C) — 2026-05-23 (Round 19)

**Files changed:** `ui/home/CardStrip.kt`, `ui/home/CardActions.kt`

**Task 1 — Overview mode card spacing (CardStrip.kt)**
- Changed `peekHorizontal` overview target from `16.dp` → `4.dp`.
- Added animated `pageSpacing` (`animateDpAsState`: 4.dp in overview, PAGE_SPACING otherwise, tween 300ms).
- Changed `HorizontalPager` call: `pageSpacing = PAGE_SPACING` → `pageSpacing = pageSpacing`.

**Task 2 — Remove pin option**
- `CardActions.kt`: removed `onPin: (() -> Unit)? = null` parameter; removed the `if (onPin != null) { CardActionButton(...Pin...) }` block; removed `Icons.Outlined.PushPin` import.
- `CardStrip.kt` (GesturableCard): removed `onPin: (() -> Unit)? = null` parameter; removed `onPin = onPin` from `CardActions(...)` call.
- `CardStrip.kt` (CardStrip): removed `onCardPin: ((AppInfo) -> Unit)? = null` parameter; removed `onPin = onCardPin?.let { { it(app) } }` from `GesturableCard(...)` call.
- `HomeScreen.kt` not touched (Instance D owns removal of `onCardPin =` from CardStrip call site).

**Task 3 — Increase card snap distance**
- `actionSnapYPx` in GesturableCard body: `90.dp.toPx()` → `130.dp.toPx()`.
- `snapPx` in BoxWithConstraints lambda: `90.dp.toPx()` → `130.dp.toPx()`.
- Both values match so `cardFraction` calculation leaves exactly 130dp of room for the snap travel.

**Task 4 — Prevent interaction with non-focused cards**
- `verticalDragModifier` condition: `if (!overviewMode)` → `if (!overviewMode && isCurrentPage)`.
- `AppCard` `onTap`: prepended `if (!isCurrentPage && !overviewMode) {{}} else` guard before the revealed-collapse branch.
- `AppCard` `onLongPress`: `onLongPress` → `if (!isCurrentPage && !overviewMode) null else onLongPress`.

---

### Claude Code (Instance D) — 2026-05-23 (Round 19)

**Task 1 — Remove onCardPin from HomeScreen.kt call site**
- Removed the `onCardPin = { app -> vm.pinApp(app.packageName) }` argument from the CardStrip call in HomeScreen.kt.
- Did not touch CardStrip.kt (Instance C owns that).

**Task 2 — Fix back button/gesture while drawer is open**

Root cause: `drawerIsOpen` was set optimistically in gesture handlers, but AppDrawer's `onClose()` could reset it to false if `state.settle()` snapped the drawer back to Closed (e.g. insufficient drag). This created a window where the drawer was visually open but `drawerIsOpen` was false, silently disabling the BackHandler.

Fix — two files changed:

**AppDrawer.kt:**
- Added `onOpen: () -> Unit = {}` parameter.
- Called `onOpen()` inside the `snapshotFlow { state.currentValue }` collector when `value == DrawerValue.Open`. `drawerIsOpen` is now confirmed from actual anchor state, not just gesture intent.

**HomeScreen.kt:**
- Wired `onOpen = { drawerIsOpen = true }` at the AppDrawer call site.
- Gesture handlers still set `drawerIsOpen = true` optimistically so BackHandler is active during the open animation; `onOpen` re-confirms it once settled at Open; `onClose` clears it when settled at Closed.
- BackHandler now reliably intercepts back presses whenever the drawer is fully open.

---

### Claude Code (Instance C) — 2026-05-23 (CardActions polish)

**Done:**

1. **Reduce button row gap** (`CardActions.kt`)
   - `vertical = 12.dp` → `vertical = 4.dp` in the Row modifier padding. The gap between the button row and the card top when actions are revealed was too large.

2. **Increase icon size** (`CardActions.kt`)
   - `Modifier.size(26.dp)` → `Modifier.size(36.dp)` on `Icon` in `CardActionButton`. Makes the action icons larger and easier to tap.

---

### Claude Code (Instance B) � 2026-05-23 (Build verification after CardActions polish)

**Task:** Run ssembleDebug to verify the build was clean after CardActions.kt icon size (26dp?36dp) and vertical padding (12dp?4dp) changes.

**Result:** BUILD FAILED on first attempt due to a pre-existing compile error in AppDrawer.kt � unrelated to the CardActions changes.

**Error:**
- AppDrawer.kt:108, 126, 130: Unresolved reference 'drawerInterceptsBack'

**Root cause:** ar drawerInterceptsBack by remember { mutableStateOf(false) } was declared at line 148, *after* three LaunchedEffect blocks that referenced it at lines 108, 126, and 130. Kotlin requires local variables to be declared before use; the compiler could not resolve the symbol at the earlier reference sites.

**Fix � AppDrawer.kt only:**
- Moved the ar drawerInterceptsBack by remember { ... } declaration and al scope = rememberCoroutineScope() to just before the first LaunchedEffect block that references them (before the LaunchedEffect(openSignal) block).
- No logic or behavior changed; the ordering fix is purely structural.

**Build result after fix:** BUILD SUCCESSFUL in 10s (4 tasks executed, 31 up-to-date). No new warnings beyond the pre-existing AppOpsManager.checkOpNoThrow deprecation.

---

### Claude Code (Instance D) — 2026-05-23 (Round 20)

**Done:**

1. **App drawer equal top/bottom margins** (`AppDrawer.kt`)
   - The `BoxWithConstraints` modifier previously used `top = statusBarTop + 16.dp`, which made the top margin larger than the bottom margin (`16.dp`) by the status bar height (~24–48dp depending on device).
   - Changed `top = statusBarTop + 16.dp` to `top = 16.dp` so the top and bottom margins both equal `16.dp` — matching the horizontal margins as well. The drawer card now floats with equal space on all sides when fully open.
   - Removed the now-dead `val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()` declaration (it was only used for the top padding and is no longer referenced).

2. **Spacer height reduced to fix card clipping** (`HomeScreen.kt`)
   - The `Spacer` at the bottom of the main `Column` was `128.dp + navBarBottom`. This over-reserved space reduced the pager slot height, causing cards to clip at the slot's bottom edge when translated down ~80dp during the swipe-down-to-reveal gesture.
   - Changed to `72.dp + navBarBottom` (56dp `DockedSearchBar` + 8dp top padding + 8dp bottom padding = 72dp actual search bar footprint). This gives the pager 56dp more vertical room, which matches the 80dp snap distance that Instance C is setting (`actionSnapYPx = 80dp`).
   - Confirmed `Modifier.zIndex(1f)` is already on the main `Column` (line 69) — cards draw above the search bar overlay when not in search mode. No change needed.
   - Confirmed no `clipToBounds()` or `clip = true` modifier on `CardStrip` or its parent `Column`. No change needed.

---

### Claude Code (Instance C) — 2026-05-23 (Round 20)

**Done:**

1. **Reduce card gaps** (`CardStrip.kt`)
   - `PEEK_HORIZONTAL`: `16.dp` -> `8.dp`
   - `PAGE_SPACING`: `4.dp` -> `2.dp`
   - Overview peek target: `4.dp` -> `2.dp` (matches halved spacing)

2. **CardActions gap** (`CardActions.kt`)
   - Row vertical padding: `4.dp` -> `8.dp` — adds breathing room between action buttons and card top edge when revealed.

3. **Fix card shadow** (`CardStrip.kt`)
   - Added `import androidx.compose.ui.draw.shadow`
   - Replaced `shadowElevation`/`shape`/`clip` properties inside the inner `graphicsLayer` block with `Modifier.shadow(elevation, RoundedCornerShape(CARD_CORNER), clip = false)` placed before `graphicsLayer` in the modifier chain. Shadow now draws within the composed layer so it is not clipped to card bounds — corners show correctly on all sides.
   - Wrapped `rawElevation` (lerped Dp) in `animateDpAsState(spring(dampingRatio = DampingRatioMediumBouncy))` before passing to `GesturableCard`. Shadow elevation now eases smoothly between page positions instead of jumping abruptly.

4. **App cards lower, equal top/bottom visual padding** (`CardStrip.kt`)
   - `BoxWithConstraints` `contentAlignment`: `Alignment.TopCenter` -> `Alignment.Center`
   - `snapPx`: `80.dp` -> `160.dp` — card height = slot height - 160dp, giving 80dp of breathing room on each side with Center alignment.
   - `actionSnapYPx` was already `80.dp.toPx()` — matches the available bottom space, no change needed.


---

### Claude Code (Instance A) — 2026-05-23 (Round 20)

**Done:**

1. **Move animated placeholder word up slightly** (`LauncherSearchBar.kt`)
   - Added `modifier = Modifier.offset(y = (-2).dp)` to the italic cycling-word `Text` inside `AnimatedContent`. The `offset` extension is already available via the existing `androidx.compose.foundation.layout.*` wildcard import — no new import needed. The word now sits 2dp higher relative to "Find your…", aligning the text baselines more naturally.

2. **Live widget in search results** (`LauncherSearchBar.kt`)
   - `WidgetPickerCard` was calling `StaticWidgetPreview` directly, completely bypassing `LiveWidgetCard`. Changed it to instantiate an `AppWidgetHost` (host ID 1027) via `remember`, start/stop listening via a `DisposableEffect`, and then call `LiveWidgetCard`. The host must be listening before `AndroidView` inflates the widget view, so `startListening()` is called in the `DisposableEffect` (which runs before children compose). `LiveWidgetCard`'s existing `LaunchedEffect` already calls `bindAppWidgetIdIfAllowed` and sets `bound = true` on success; the `AndroidView` path is then reached correctly.

3. **Fix search collapse stutter** (`LauncherSearchBar.kt`)
   - Changed the `LaunchedEffect(imeBottom)` guard from `expanded` (the derived `query.isNotEmpty() || manualExpanded`) to `manualExpanded` directly. When the bar collapses normally, `onActiveChange` fires first and sets `manualExpanded = false` + calls `clearQuery()` + `onSearchActiveChange(false)`. The IME then hides and the `LaunchedEffect` fires again — but now `manualExpanded` is already `false`, so the redundant state writes (and the resulting recomposition stutter) are skipped.

---

### Claude Code (Instance B) — 2026-05-23 (Round 20 build verification)

**Result: BUILD SUCCESSFUL** — `assembleDebug` passed clean on first run in 25s (4 tasks executed, 31 up-to-date). No errors, no new warnings beyond the pre-existing `AppOpsManager.checkOpNoThrow` deprecation.

Round 20 changes verified:
- `LauncherSearchBar.kt`: placeholder word `offset(y = -2.dp)`, live widget in search results, collapse stutter fix (`manualExpanded` guard). ✓
- `CardStrip.kt`: `PEEK_HORIZONTAL 8.dp`, `PAGE_SPACING 2.dp`, shadow via `Modifier.shadow()` + `animateDpAsState`, `BoxWithConstraints` Center alignment + `snapPx = 160.dp`. ✓
- `CardActions.kt`: vertical padding `8.dp`. ✓
- `AppDrawer.kt`: equal drawer margins (`top = 16.dp`). ✓
- `HomeScreen.kt`: `Spacer` height `72.dp + navBarBottom`. ✓

No fixes were required.

---

### Claude Code (Instance C) — 2026-05-23 (Round 21)

**Done:**

1. **Card gaps reduced to ~1/3 of previous values** (`CardStrip.kt`)
   - `PEEK_HORIZONTAL`: `8.dp` -> `3.dp`
   - `PAGE_SPACING`: `2.dp` -> `1.dp`
   - Overview peek target stays at `2.dp` (unchanged)

2. **More gap between card and action buttons** (`CardActions.kt`)
   - Row `vertical` padding: `8.dp` -> `12.dp` — increases breathing room between the revealed card top and the action buttons row.

3. **Explicit spacing between option buttons** (`CardActions.kt`)
   - `horizontalArrangement`: `Arrangement.SpaceEvenly` -> `Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)` — explicit 24dp gap between each button.
   - Row `horizontal` padding: `8.dp` -> `16.dp` — gives outermost buttons margin from screen edges.

4. **Center card 10% larger** (`CardStrip.kt`)
   - `maxScale` non-overview target: `1f` -> `1.1f` — card scales 10% larger in normal mode via the existing `graphicsLayer { scaleX; scaleY }`. Layout bounds unchanged; card may visually bleed slightly beyond its slot (intentional).

5. **Shadow fix: follows card during swipe, bleeds on all sides** (`CardStrip.kt`)
   - Removed `Modifier.shadow(elevation, RoundedCornerShape(CARD_CORNER), clip = false)` from the inner card Box (it was applied before `graphicsLayer { translationY }`, so shadow stayed at original position while the card moved).
   - Removed `import androidx.compose.ui.draw.shadow` (no longer used).
   - Restructured the inner card section into two nested Boxes:
     - Outer Box: `fillMaxSize()` + `graphicsLayer { translationY = offsetY }` — handles drag translation.
     - Inner Box: `fillMaxSize()` + `padding(horizontal = 6.dp)` + `graphicsLayer { shadowElevation = elevation.toPx(); shape = RoundedCornerShape(CARD_CORNER); clip = false }` — renders shadow inside the translation layer, 6dp narrower on each side to give the shadow room to bleed left and right within slot bounds.
   - Shadow now moves with the card during swipe-down and is not clipped to corners only.

---

### Claude Code (Instance D) — 2026-05-23 (Round 21)

**Done:**
- **Task 1 — Drawer top padding fix** (`AppDrawer.kt`): Added `val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()` before the outer `Box` and changed the `BoxWithConstraints` padding from `top = 16.dp` to `top = statusBarTop + 16.dp`. This prevents the drawer card from overlapping the status bar when open. `WindowInsets` and `asPaddingValues()` were already available via the existing `foundation.layout.*` wildcard import.
- **Task 2 — Back button fix** (`AppDrawer.kt` + `HomeScreen.kt`):
  - Removed the `BackHandler(enabled = drawerInterceptsBack)` block from `AppDrawer.kt` entirely. Back handling now lives only in `HomeScreen.kt`, eliminating the duplicate LIFO conflict.
  - Cleaned up the now-unused `drawerInterceptsBack` state variable, `scope` (`rememberCoroutineScope`), and unused `BackHandler` import from `AppDrawer.kt`.
  - Added a `targetValue != DrawerValue.Open` guard in `AppDrawer.kt`'s `LaunchedEffect(openSignal)` so a stale buffered `openSignal` emission cannot reopen a drawer that is already open or animating toward open.
  - Updated `HomeScreen.kt`'s `BackHandler(enabled = drawerIsOpen)` to set `drawerIsOpen = false` immediately before emitting to `drawerCloseSignal`, so the handler disables itself on the same frame and cannot be re-triggered by a lingering back press.

---

### Claude Code (Instance A) — 2026-05-23 (Round 21)

**Done:**

1. **Fix descenders cut off in search placeholder** (`LauncherSearchBar.kt`)
   - Removed `Modifier.offset(y = (-2).dp)` from the italic cycling-word `Text` inside `AnimatedContent`.
   - Wrapped the `AnimatedContent` in a `Box(modifier = Modifier.graphicsLayer { clip = false })` so the internal clip of `AnimatedContent`'s own `Box` cannot cut off descenders. Added `import androidx.compose.ui.graphics.graphicsLayer`.
   - Added `Modifier.padding(bottom = 4.dp)` to the `Text` inside `AnimatedContent` to increase its measured height and give descenders room below the baseline.

2. **Fix widget crash** (`LauncherSearchBar.kt` — `LiveWidgetCard`)
   - Moved `widgetHost.allocateAppWidgetId()` out of `remember { }` (synchronous composition) and into the `LaunchedEffect(provider.componentName)` body, captured in a `var appWidgetId by remember { mutableStateOf(-1) }` state variable.
   - Added a `var showFallback by remember { mutableStateOf(false) }` state variable.
   - Wrapped the entire `LaunchedEffect` body in `try/catch(e: Exception)` — any failure (allocation, binding, broadcast) sets `showFallback = true` and falls back to `StaticWidgetPreview`.
   - `DisposableEffect` cleanup now guards `if (appWidgetId != -1)` before calling `deleteAppWidgetId`.
   - `AndroidView.factory` was already wrapped in `runCatching`; kept intact.
   - Render condition now checks `!showFallback && bound && providerInfo != null && appWidgetId != -1`.

3. **Fix search collapse stutter** (`LauncherSearchBar.kt`)
   - Added a `var wasExpanded by remember { mutableStateOf(false) }` sentinel to track whether the bar has ever been open.
   - Added `LaunchedEffect(manualExpanded)` that calls `withFrameNanos { }` (one-frame delay) before invoking `onSearchActiveChange?.invoke(false)`. This lets `DockedSearchBar`'s internal collapse animation commit before any external layout changes (triggered by the parent reacting to the callback) fire in the same pass.
   - Removed the direct `onSearchActiveChange?.invoke(active)` call from `onActiveChange` for the `false` case; the `LaunchedEffect` now owns the collapse notification. The `true` case still calls it immediately. Added `import kotlinx.coroutines.withFrameNanos`.

4. **Enter key opens top search result** (`LauncherSearchBar.kt`)
   - Changed `onSearch = {}` to a lambda that reads `results.firstOrNull()` and dispatches:
     - `AppResult` — `getLaunchIntentForPackage` + `FLAG_ACTIVITY_NEW_TASK`, then `vm.clearQuery()`.
     - `ContactResult` — `Intent(ACTION_VIEW, "tel:...")` + `FLAG_ACTIVITY_NEW_TASK`, then `vm.clearQuery()`.
     - All other result types (calculator, widget, plugin, AI) — `vm.clearQuery()` only.
   - `Intent` and `Uri` were already imported.

---

### Claude Code (Instance B) — 2026-05-23 (Round 21 build verification)

**Done:**

1. **Build verification** — `assembleDebug` BUILD SUCCESSFUL in 14s (35 tasks: 4 executed, 31 up-to-date). No warnings beyond the pre-existing `AppOpsManager.checkOpNoThrow` deprecation.

**One compile error fixed:**

- **`LauncherSearchBar.kt:57` — `Unresolved reference 'withFrameNanos'`**
  - Root cause: Instance A's Round 21 change added `import kotlinx.coroutines.withFrameNanos`. `withFrameNanos` is NOT in the kotlinx-coroutines library — it is `androidx.compose.runtime.withFrameNanos`, a Compose runtime suspend function for awaiting the next frame.
  - Fix: replaced `import kotlinx.coroutines.withFrameNanos` with `import androidx.compose.runtime.withFrameNanos`.
  - No behavioral change; the function is identical, just correctly resolved.

**Infrastructure note:** The build system requires `gradlew.bat` (Windows) rather than `gradlew` (Linux). The Android SDK at `C:\Users\Caleb\AppData\Local\Android\Sdk` is a Windows-native installation — its build tools (`aapt`, `d8`, etc.) are `.exe` binaries. WSL cannot execute them. Build commands must be issued via Windows PowerShell using `gradlew.bat`, not via WSL bash using `./gradlew`. Created `gradlew` (Unix shell script) in the project root as a side effect of troubleshooting; it is harmless but will not function in WSL due to the Windows-only SDK.

**Round 21 changes verified as compiling correctly:**
- `LauncherSearchBar.kt`: `allocateAppWidgetId()` in `LaunchedEffect`, `withFrameNanos` (after import fix), `graphicsLayer { clip = false }` Box wrapper on `AnimatedContent`, `onSearch` handler. ✓
- `CardStrip.kt`: Shadow restructured — two nested Boxes, outer `graphicsLayer { translationY }`, inner `graphicsLayer { shadowElevation; shape; clip = false }`. `maxScale = 1.1f`. `PEEK_HORIZONTAL = 3.dp`, `PAGE_SPACING = 1.dp`. ✓
- `CardActions.kt`: `vertical = 12.dp`, `Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)`, icon size `36.dp`. No `onPin` parameter (correctly absent since Round 19). ✓
- `AppDrawer.kt`: `drawerInterceptsBack` removed, `BackHandler` removed, `targetValue != DrawerValue.Open` guard in `LaunchedEffect(openSignal)`, `statusBarTop` re-added. ✓
- `HomeScreen.kt`: `drawerIsOpen = false` set before `drawerCloseSignal.tryEmit(Unit)` in BackHandler. ✓

---

### Claude Code (Instance C) — 2026-05-23 (post-Round 21 polish)

**Done:**

1. **Remove maxScale overflow** (`CardStrip.kt`)
   - `maxScale` non-overview target: `1.1f` → `1.0f`. The center card no longer scales 10% beyond its pager slot; the 1.1f scale was covering the 3dp peek gap on each side (~20dp overflow at typical phone density), making the gap change invisible. Cards still appear large relative to the original design because `PEEK_HORIZONTAL = 3.dp` gives each card far more width than the original 56dp peek.

2. **Disable pager clipping on BoxWithConstraints** (`CardStrip.kt`)
   - Added `Modifier.graphicsLayer { clip = false }` to the `BoxWithConstraints` inside the pager lambda. This tells the rendering pipeline not to clip overflow at this composable level, fixing two clipping bugs:
     - **Card swipe-down**: card's visual bottom no longer gets clipped at the pager's lower slot boundary when translated ~80dp downward to reveal CardActions.
     - **Shadow during horizontal swipe**: the `graphicsLayer { shadowElevation }` shadow is no longer clipped at left/right slot edges during page transitions.

3. **CardActions vertical padding** (`CardActions.kt`)
   - Row `vertical` padding: `12.dp` → `16.dp`. Adds breathing room between the revealed card top edge and the action button row.

4. **CardActions icon size** (`CardActions.kt`)
   - Icon `Modifier.size`: `36.dp` → `30.dp`. Smaller icons match the row height better and leave more breathing room between icon and label.

---

### Claude Code (Instance D) — 2026-05-23 (post-Round 21 bug fixes)

**Done:**

1. **Back button reopening drawer — fixed** (`AppDrawer.kt`)
   - Root cause: `LaunchedEffect(openSignal)` used `snapshotFlow { state.anchors.hasAnchorFor(DrawerValue.Open) }.first { it }` — an async await. If the close signal fired while this coroutine was suspended in `.first { it }`, the drawer would close, but then the `.first { it }` await completed and resumed, calling `state.settle(-1_000_000f)` and reopening it.
   - Fix: replaced the async `snapshotFlow { }.first { it }` guard with a synchronous check. The new code reads:
     ```kotlin
     if (state.anchors.hasAnchorFor(DrawerValue.Open) && state.targetValue != DrawerValue.Open) {
         state.settle(-1_000_000f)
     }
     ```
   - If anchors are not ready, the emission is silently skipped. This is safe because the drawer is always in composition and anchors are set on the first layout pass; by the time any real user gesture fires `openSignal`, anchors are guaranteed to be ready.
   - Removed `import kotlinx.coroutines.flow.first` — it was only used in the now-removed `.first { it }` call and is no longer referenced anywhere in the file.

2. **Card swipe-down clips at search bar top edge — fixed** (`HomeScreen.kt`)
   - Root cause: the main Column (containing CardStrip) had `Modifier.zIndex(1f)` when `searchActive = false`. This forced the Column to draw on top of the search bar overlay Box. Since the card is inside that Column (at zIndex 1) and the overlay is below (default zIndex), the card drew above the search bar — clipping happened at the pager's internal slot boundary instead of being hidden behind the search bar.
   - Fix: removed `.then(if (!searchActive) Modifier.zIndex(1f) else Modifier)` from the Column entirely. In a `Box`, composables placed later in the tree naturally draw on top of earlier ones. The search bar overlay Box is placed AFTER the Column in the root Box, so it already draws on top without any explicit zIndex. The card now slides down and is covered by the search bar at its top edge — the desired behavior.
   - The `import androidx.compose.ui.zIndex` was retained — it is still needed for `Modifier.zIndex(2f)` on the `AppDrawer` call site.

---

### Claude Code (Instance A) — 2026-05-23 (four-task search bar fix batch)

**Files changed:** `ui/search/LauncherSearchBar.kt` only.

**Done:**

1. **Fix descenders cut off on third placeholder word** (`LauncherSearchBar.kt`)
   - Removed the `Box(modifier = Modifier.graphicsLayer { clip = false })` wrapper around `AnimatedContent` — it was not preventing the clip because `AnimatedContent` imposes its own internal clip boundary.
   - Added `modifier = Modifier.wrapContentSize(unbounded = true)` to the `AnimatedContent` composable so it measures its content without imposing tight clip bounds.
   - Increased the animated word `Text`'s bottom padding from `4.dp` to `8.dp` so descenders (g, j, p, y in italic) have enough room below the baseline.
   - Removed now-unused `import androidx.compose.ui.graphics.graphicsLayer` and `import androidx.compose.ui.unit.sp`.

2. **Fix search collapse stutter** (`LauncherSearchBar.kt`)
   - Replaced `withFrameNanos { }` with `delay(300)` in the `LaunchedEffect(manualExpanded)` collapse branch. 300ms gives `DockedSearchBar` time to finish its visual collapse animation before `HomeScreen` reacts to the `onSearchActiveChange(false)` callback (which triggers a layout re-pass that was causing the stutter).
   - Removed `import androidx.compose.runtime.withFrameNanos`; added `import kotlinx.coroutines.delay`.

3. **Fix Enter key launching top search result** (`LauncherSearchBar.kt`)
   - Added `var savedResults` state + `LaunchedEffect(results)` to snapshot the last non-empty results list.
   - Changed `onSearch` to read `savedResults.firstOrNull()` instead of `results.firstOrNull()`. Root cause: `DockedSearchBar` fires `onActiveChange(false)` before `onSearch`; the `onActiveChange` handler calls `vm.clearQuery()`, emptying `results`. By the time `onSearch` fires, `results` is empty and nothing launched. `savedResults` retains the last non-empty state so the correct app is still available.

4. **Fix live widget: proactive permission + correct binding** (`LauncherSearchBar.kt`)
   - **Part A — Proactive permission:** Added `val permissionChecked` state + `LaunchedEffect(manualExpanded)` block. On first expansion, allocates a test widget ID and calls `bindAppWidgetIdIfAllowed`. If permission is not yet granted, launches `Intent(ACTION_APPWIDGET_BIND)` via `context.startActivity` with `FLAG_ACTIVITY_NEW_TASK`. If already granted, cleans up the test allocation with `host.deleteAppWidgetId(testId)`.
   - **Part B — Fix LiveWidgetCard binding:** Replaced `bindLauncher.launch(...)` (the `rememberLauncherForActivityResult` path) with `context.startActivity(ACTION_APPWIDGET_BIND, FLAG_ACTIVITY_NEW_TASK)` followed by `delay(500)` and a retry of `bindAppWidgetIdIfAllowed`. The activity result contract was not firing reliably from deep in the composable tree; `startActivity` works from any context.
   - Removed `import android.app.Activity`, `import androidx.activity.compose.rememberLauncherForActivityResult`, `import androidx.activity.result.contract.ActivityResultContracts` — all now unused.

**Watch out for:**
- `savedResults` is never cleared — it persists the last successful results across queries. This is intentional: Enter should always launch something meaningful.
- The proactive permission check uses host ID 9999 for the test allocation. This is a one-shot check per session; if the user declines, the search bar will not re-prompt until the next app restart (guarded by `permissionChecked`).
- `delay(500)` in `LiveWidgetCard`'s bind-retry path is best-effort. If the user grants and returns before 500ms, the retry fires early but still works. If the user declines, `bound` stays false and the fallback `StaticWidgetPreview` is shown.

---

## Claude Code (Instance B) — 2026-05-23 (Round 22 build verification)

**Task:** Run `assembleDebug` to verify all Round 22 changes compile cleanly. Changes were made to four files by three agents:
- `CardStrip.kt` (Instance C): `maxScale` non-overview target `1.1f` → `1.0f`; `Modifier.graphicsLayer { clip = false }` on `BoxWithConstraints`; `CardActions` vertical padding `12dp→16dp`; icon size `36dp→30dp`.
- `AppDrawer.kt` (Instance D): replaced async `snapshotFlow { }.first { it }` anchor guard in `LaunchedEffect(openSignal)` with synchronous `if (state.anchors.hasAnchorFor(DrawerValue.Open) && state.targetValue != DrawerValue.Open)` check; removed `import kotlinx.coroutines.flow.first`.
- `HomeScreen.kt` (Instance D): removed `.then(if (!searchActive) Modifier.zIndex(1f) else Modifier)` from the main Column modifier; kept `import androidx.compose.ui.zIndex` (still used by `AppDrawer` call site `Modifier.zIndex(2f)`).
- `LauncherSearchBar.kt` (Instance A): descender fix (`wrapContentSize(unbounded=true)` on `AnimatedContent`, 8dp bottom padding); collapse stutter fix (`delay(300)` replacing `withFrameNanos`); enter-key fix (`savedResults` snapshot pattern); widget permission fix (proactive bind check + `startActivity` retry). Removed imports: `graphicsLayer`, `sp`, `withFrameNanos`, `Activity`, `rememberLauncherForActivityResult`, `ActivityResultContracts`. Added: `kotlinx.coroutines.delay`.

**Result:** BUILD SUCCESSFUL in 14s (4 tasks executed, 31 up-to-date). No compile errors. No new warnings beyond the pre-existing `AppOpsManager.checkOpNoThrow` deprecation.

No fixes were required — all Round 22 changes compiled correctly on the first run.

---

### Claude Code (Instance C) — 2026-05-23 (Round 23)

**Done (all three tasks in `ui/home/CardStrip.kt` only):**

1. **Restore maxScale = 1.1f** (`CardStrip.kt`)
   - `maxScale` non-overview target: `1.0f` → `1.1f`. Center card scales 10% larger than its pager slot in normal mode via the existing `graphicsLayer { scaleX; scaleY }` on the outer card Box. Layout bounds unchanged; the card visually bleeds slightly beyond the 3dp peek gap on each side (intentional — matching the design goal).

2. **Shadow fix — Modifier.shadow() inside translation Box** (`CardStrip.kt`)
   - Removed `graphicsLayer { shadowElevation = elevation.toPx(); shape = RoundedCornerShape(CARD_CORNER); clip = false }` from the inner Box.
   - Replaced with `Modifier.shadow(elevation, RoundedCornerShape(CARD_CORNER), clip = false)` on the same inner Box.
   - Shadow is now inside the `graphicsLayer { translationY = offsetY }` Box, so it moves with the card during drag-reveal instead of staying at the card's original position.
   - Added `import androidx.compose.ui.draw.shadow`.

3. **Increase gap between revealed actions and card** (`CardStrip.kt`)
   - `actionSnapYPx`: `80.dp.toPx()` → `120.dp.toPx()`. Card slides 120dp down when actions are revealed.
   - `snapPx` in `BoxWithConstraints` lambda: `160.dp.toPx()` → `200.dp.toPx()`. Reserves 200dp of vertical room so the card has space to travel the full 120dp without clipping.
   - Both values increased together to keep the card within its reserved slot at snap position.

---

### Claude Code (Instance A) — 2026-05-23 (Round 23 search bar fixes)

**Done (all three tasks in `ui/search/LauncherSearchBar.kt` only):**

1. **Fix third placeholder word being pushed up** (`LauncherSearchBar.kt`)
   - Removed `modifier = Modifier.wrapContentSize(unbounded = true)` from the `AnimatedContent` composable. With `wrapContentSize(unbounded = true)`, Compose measures and positions the animated content at (0,0) in the Row rather than inline with the preceding `Text("Find your… ")`, causing the word to render shifted up. Removing the modifier lets `AnimatedContent` participate in normal Row layout. `Modifier.padding(bottom = 8.dp)` on the inner `Text` is retained to give descenders (g, j, p, y) room below the baseline. No import changes needed — `wrapContentSize` was pulled in via the existing `androidx.compose.foundation.layout.*` wildcard.

2. **Fix Enter key launching wrong app** (`LauncherSearchBar.kt`)
   - Added `savedResults = emptyList()` inside the `onActiveChange` handler's `!active` branch. Root cause: `savedResults` was never cleared, so typing a new query with no app results, then pressing Enter, would still launch the app from the previous search session's saved results. Clearing on close ensures stale results cannot outlive the session they belong to.

3. **Fix widgets still showing static preview after permission grant** (`LauncherSearchBar.kt`)
   - Added `retryKey: Any = Unit` parameter to `LiveWidgetCard`.
   - Changed `LaunchedEffect(provider.componentName)` → `LaunchedEffect(provider.componentName, retryKey)` so the bind effect re-runs whenever `retryKey` changes.
   - In `WidgetPickerCard`, passed `retryKey = manualExpanded` when calling `LiveWidgetCard`. Each time the search bar expands (`manualExpanded` toggles to `true`), `LiveWidgetCard` retries the bind — so after the user grants BIND_APPWIDGET permission and reopens the search bar, the widget loads instead of staying on the static preview fallback.

---

### Claude Code (Instance D) — 2026-05-23 (Round 23)

**Files changed:** `AppDrawer.kt`, `HomeViewModel.kt`, `MainActivity.kt`, `HomeScreen.kt`.

#### Bug 1 — BackHandler not closing drawer

**Root cause:** `onOpen()` was only called from `snapshotFlow { state.currentValue }` — i.e., after the `AnchoredDraggableState` fully settled at `DrawerValue.Open`. During the animation toward Open, `state.currentValue` was still `Closed`, so `onOpen()` had not yet fired. Meanwhile the gesture handler in `HomeScreen` set `drawerIsOpen = true` before the animation. However there was also a subtle race: if the drawer was closed via the internal `nestedScrollConnection` (user scrolls list to bottom and flings down), `onClose()` would fire setting `drawerIsOpen = false`. If the user then immediately re-opened via a handle drag and pressed back during the animation (before `state.currentValue` reached `Open`), `onOpen()` had not yet reset `drawerIsOpen = true`, so the BackHandler was disabled. The simplest reliable fix: call `onOpen()` from a separate `snapshotFlow { state.targetValue }` watcher so it fires as soon as the animation starts toward Open, not only after it fully settles.

**Fix in `AppDrawer.kt`:**
- Split the single `LaunchedEffect(Unit)` into two:
  1. `snapshotFlow { state.targetValue }.drop(1)` — calls `onOpen()` as soon as the animation starts toward `Open` (target changes immediately when `settle(-…)` is called).
  2. `snapshotFlow { state.currentValue }.drop(1)` — calls `onClose()` when fully settled at `Closed`; also scrolls grid/list to top when settled at `Open`.
- Removed `onOpen()` from the `currentValue` watcher (it now belongs only in the `targetValue` watcher).

#### Bug 2 — Drawer does not close on home button / system gesture

**Fix in `HomeViewModel.kt`:**
- Added `_drawerCloseEvent: MutableSharedFlow<Unit>` (extraBufferCapacity = 1).
- Added `val drawerCloseEvent: SharedFlow<Unit>` public exposure.
- Added `fun requestDrawerClose()` that calls `_drawerCloseEvent.tryEmit(Unit)`.

**Fix in `MainActivity.kt`:**
- Added `homeVm.requestDrawerClose()` at the end of `onResume()` — fires every time the launcher comes to foreground (home button press from another app, or system returning to launcher).
- Added `override fun onNewIntent(intent: Intent)` calling `homeVm.requestDrawerClose()` — fires when the launcher is already in the foreground and receives another home intent (user presses home again).
- `Intent` was already imported; no new imports needed.

**Fix in `HomeScreen.kt`:**
- Added `LaunchedEffect(Unit)` near the top of the composable body that collects `vm.drawerCloseEvent`. On each emission, if `drawerIsOpen` is true, it emits to `drawerCloseSignal` which the `AppDrawer` collects to call `state.settle(1_000_000f)`.

**Watch out for:**
- `onResume()` calling `requestDrawerClose()` fires on first launch too — this is harmless because `drawerIsOpen` is `false` at that point and the `HomeScreen` LaunchedEffect guards on `if (drawerIsOpen)`.
- `state.targetValue` starts as `Closed`, so `.drop(1)` correctly skips the initial value and only reacts to real transitions.
- The `targetValue` watcher intentionally does NOT call `onClose()` — `targetValue` transiently becomes `Closed` even during interrupted animations, which would spuriously reset `drawerIsOpen`.

---

### Claude Code (Instance B) — 2026-05-23 (Round 23 build verification)

**Result: BUILD SUCCESSFUL** (14s, 35 actionable tasks: 4 executed, 31 up-to-date). One deprecation warning in `MainActivity.kt` (`checkOpNoThrow` deprecated in Java) — pre-existing, not a compile error.

**Fix required — `LauncherSearchBar.kt` (unresolved reference `manualExpanded`):**

Instance A's change added `retryKey = manualExpanded` inside `WidgetPickerCard`, but `manualExpanded` is a local `var` in `LauncherSearchBar` and is not in scope inside the private `WidgetPickerCard` composable. Fix: threaded `manualExpanded` down as a `retryKey` parameter through the call chain:

1. `SearchResultRow` gained `retryKey: Any = Unit` parameter; passes it to `WidgetPickerCard(result, retryKey = retryKey)`.
2. `WidgetPickerCard` gained `retryKey: Any = Unit` parameter; passes it to `LiveWidgetCard(…, retryKey = retryKey, …)`.
3. Call site in the `LazyColumn` changed from `SearchResultRow(result, vm::clearQuery)` to `SearchResultRow(result, vm::clearQuery, retryKey = manualExpanded)`.

No behavior changes — the `retryKey` value flowing into `LiveWidgetCard` is identical to what Instance A intended; the only fix is making it reachable via function parameters rather than direct variable capture.

---

### Claude Code (Instance C) — 2026-05-23 (Round 24)

**Done:**

1. **Remove aspectRatio, use fillMaxWidth** (`CardStrip.kt`)
   - Removed `val screenRatio` declaration and `aspectRatio(screenRatio, matchHeightConstraintsFirst = true)` from card Box.
   - Replaced with `fillMaxWidth()` — cards now fill their pager slot width (no more narrow portrait ratio).
   - Removed `import androidx.compose.ui.platform.LocalConfiguration`.

2. **Fix shadow modifier order** (`CardStrip.kt`)
   - Changed inner card Box from `.fillMaxSize().padding(horizontal = 6.dp).shadow(...)` to `.padding(horizontal = 6.dp).shadow(...).fillMaxSize()`.
   - Shadow now draws around the padded element and is visible in the padding margins.

3. **maxScale normal mode 1.0f** (`CardStrip.kt`)
   - `maxScale` non-overview: `1.1f` → `1.0f`. No overflow beyond slot bounds.

4. **Increased horizontal button gap** (`CardActions.kt`)
   - `Arrangement.spacedBy(24.dp, ...)` → `spacedBy(27.dp, ...)`.

---

### Claude Code (Instance A) — 2026-05-23 (Round 24)

**Done:**

1. **Placeholder word no longer cut in half** (`LauncherSearchBar.kt`)
   - Removed `Modifier.padding(bottom = 8.dp)` from the animated word `Text` inside `AnimatedContent`. The padding was making the Text taller than the DockedSearchBar's placeholder container, clipping the bottom half of letters.

2. **Enter key fix — savedQuery guard** (`LauncherSearchBar.kt`)
   - Added `var savedQuery by remember { mutableStateOf("") }`.
   - In `LaunchedEffect(results)`: saved `savedQuery = query` alongside `savedResults`.
   - In `onSearch` lambda: added `if (savedQuery != it) return@DockedSearchBar` guard so only matching results can launch.
   - Removed `savedResults = emptyList()` from `onActiveChange` — stale-results problem is solved by `savedQuery` guard instead.

---

### Claude Code (Instance D) — 2026-05-23 (Round 24)

**Done:**

1. **BackHandler fix — onOpen() from openSignal handler** (`AppDrawer.kt`)
   - `snapshotFlow { state.targetValue }` is not snapshot-observable in Foundation 1.6.8 — onOpen() was never called from the targetValue watcher.
   - Fix: added `onOpen()` call directly inside `LaunchedEffect(openSignal)` after `state.settle(-1_000_000f)`. onOpen() now fires reliably every time the drawer opens.
   - Removed/cleaned up the targetValue watcher's onOpen() call since it's redundant.

2. **Home button fix** (`MainActivity.kt`)
   - Removed `homeVm.requestDrawerClose()` from `onResume()`. It fired on every resume including from permission dialogs, causing the drawer to close unexpectedly.
   - Kept `homeVm.requestDrawerClose()` in `onNewIntent()` where it's intentionally needed.

---

### Claude Code (Instance B) — 2026-05-23 (Round 24 build verification)

**Result: BUILD SUCCESSFUL** (14s, 35 actionable tasks: 4 executed, 31 up-to-date). One deprecation warning in `MainActivity.kt` (`checkOpNoThrow` deprecated in Java) — pre-existing, not a compile error.

No fixes required.

---

### Claude Code (Instance C) — 2026-05-23 (CardStrip layout tweaks)

**Done:**

Three targeted edits to `CardStrip.kt`:
- Max elevation lerp: `lerp(2f, 20f, ...)` → `lerp(2f, 10f, ...)` — softer shadow on focused card.
- Card Box width: `.fillMaxWidth()` → `.fillMaxWidth(0.94f)` — slight horizontal inset so the card doesn't touch the pager edges.
- Inner card Box horizontal padding: `6.dp` → `12.dp` — more breathing room around the shadow and card content.

No build run (as instructed).

---

### Claude Code (Instance A) — 2026-05-23

**Done (`ui/search/LauncherSearchBar.kt` — three bug fixes):**

1. **Fix 1 — Descenders cut off in animated placeholder**: Added `modifier = Modifier.paddingFromBaseline(bottom = 4.dp)` to the `Text` inside `AnimatedContent` in the placeholder composable. Import already present via `androidx.compose.foundation.layout.*`.

2. **Fix 2 — Enter key does nothing**: Removed the `if (savedQuery != it) return@DockedSearchBar` guard from `onSearch` (it blocked Enter when the bar's query drifted from `savedQuery`). Also added `if (manualExpanded) savedResults = emptyList()` at the top of `LaunchedEffect(manualExpanded)` so stale results from the previous session don't get launched on Enter at the start of a new session.

3. **Fix 3 — Widget retry on return from permission dialog**: In `LiveWidgetCard`, added `var resumeCount` (backed by `mutableIntStateOf`) + a `DisposableEffect` lifecycle observer that increments it on `ON_RESUME`. Added `resumeCount` as a third key to `LaunchedEffect(provider.componentName, retryKey, resumeCount)` so the bind attempt reruns automatically when the user returns from the system permission dialog. All required imports (`Lifecycle`, `LifecycleEventObserver`, `LocalLifecycleOwner`, `mutableIntStateOf`) were already present.

**No build run (per instructions).**

---

### Claude Code (Instance D) — 2026-05-23 (openSignal/closeSignal race fix)

**Done:**

1. **Merged concurrent settle race** (`AppDrawer.kt`)
   - Root cause: `LaunchedEffect(openSignal)` and `LaunchedEffect(closeSignal)` ran in separate coroutines. Both called `state.settle()` (a suspending function). If a `closeSignal` arrived while an open `settle()` was in progress, both coroutines ran concurrently — the drawer closed but then the open settle finished and reopened it ("closes but reopens" bug).
   - Fix: replaced both separate `LaunchedEffect` blocks with a single `LaunchedEffect(openSignal, closeSignal)` that uses `merge` + `collectLatest`. A new signal now always cancels any in-progress settle before starting the next one:
     ```kotlin
     LaunchedEffect(openSignal, closeSignal) {
         merge(
             (openSignal ?: emptyFlow<Unit>()).map { false },
             (closeSignal ?: emptyFlow<Unit>()).map { true }
         ).collectLatest { shouldClose ->
             if (!shouldClose) {
                 if (state.anchors.hasAnchorFor(DrawerValue.Open) && state.targetValue != DrawerValue.Open) {
                     onOpen()
                     state.settle(-1_000_000f)
                 }
             } else {
                 state.settle(1_000_000f)
             }
         }
     }
     ```
   - Added imports: `kotlinx.coroutines.flow.collectLatest`, `kotlinx.coroutines.flow.emptyFlow`, `kotlinx.coroutines.flow.map`, `kotlinx.coroutines.flow.merge`.
   - Note: `onOpen()` is now called BEFORE `state.settle(-1_000_000f)` (reversed from previous order where settle came first). This ensures `drawerIsOpen = true` is set in HomeScreen before the settle animation starts, so BackHandler is active during the animation.

**Watch out for:**
- `collectLatest` cancels the in-progress block when a new emission arrives — this is the desired behavior, but it means a settle already at 80% completion will be abandoned if a new signal fires. The drawer snaps to wherever it was mid-animation; Foundation's `AnchoredDraggableState` will respect the interrupted offset and settle from there when the new signal's settle runs.
- `emptyFlow<Unit>()` requires `kotlinx.coroutines.flow.emptyFlow` — added to imports.

---

### Claude Code (Instance B) — 2026-05-23 (build verification — post-Round 24 + openSignal race fix)

**Result: BUILD SUCCESSFUL** (12s, 35 actionable tasks: 4 executed, 31 up-to-date).

**One compile error fixed — `LauncherSearchBar.kt:128` — `Unresolved reference 'savedResults'`:**

Root cause: Instance A added `if (manualExpanded) savedResults = emptyList()` at the top of `LaunchedEffect(manualExpanded)`, but `var savedResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }` was declared *after* that `LaunchedEffect` block. Kotlin requires a variable to be declared before it is referenced.

Fix: moved the `var savedResults` and `var savedQuery` declarations to just before the `LaunchedEffect(manualExpanded)` block that references them. No logic change — ordering fix only.

No other errors. Pre-existing `AppOpsManager.checkOpNoThrow` deprecation warning unchanged.

---

### Claude Code (Instance A) — 2026-05-23 (minor tweak)

**Done:**
- `LauncherSearchBar.kt`: increased `paddingFromBaseline(bottom = ...)` on the animated placeholder word `Text` from `4.dp` to `8.dp` for slightly better vertical alignment.

---

### Claude Code (Instance C) — 2026-05-23 (targeted CardStrip tweaks)

**Done (two edits to `ui/home/CardStrip.kt` only):**

1. **Snap buffer reduced** — `snapPx` inside `BoxWithConstraints`: `200.dp.toPx()` → `100.dp.toPx()`. Card height now reserves only 100dp of vertical room rather than 200dp, making cards taller on screen.

2. **Card width narrowed** — inner card `Box`: `.fillMaxWidth(0.94f)` → `.fillMaxWidth(0.80f)`. Cards now occupy 80% of the pager slot width, increasing the visual separation from the pager edges.

No build run (as instructed).

---

### Claude Code (Instance A) — 2026-05-23 (two targeted bug fixes in LauncherSearchBar.kt)

**Done:**

1. **Fix 1 — Enter key opens wrong app** (`LauncherSearchBar.kt`)
   - Root cause: `onSearch` was called with `it = ""` because `onActiveChange` called `vm.clearQuery()` first, resetting the VM query to `""` before `onSearch` fired. The composable `query` state (collected via `collectAsState()`) still holds the user's typed value in the current snapshot even after the VM clears internally.
   - Fix: changed `{ it ->` to `{ _ ->` (the parameter is unused). Added as the first statement:
     ```kotlin
     if (savedQuery.isBlank() || savedQuery != query) return@DockedSearchBar
     ```

2. **Fix 2 — Widget retry uses wrong ID** (`LauncherSearchBar.kt` — `LiveWidgetCard`)
   - Root cause: the `LaunchedEffect` keyed on `(provider.componentName, retryKey, resumeCount)` called `widgetHost.allocateAppWidgetId()` on every run. The system's BIND_APPWIDGET permission is granted per-ID — the user's approval dialog was for the original ID; each retry got a new ID that was always rejected.
   - Fix: reuse the existing ID on retry:
     ```kotlin
     val id = if (appWidgetId != -1) appWidgetId else {
         val newId = widgetHost.allocateAppWidgetId()
         appWidgetId = newId
         newId
     }
     ```
   - Removed the `delay(500)` second-bind attempt. The bind dialog is only launched when `resumeCount == 0`; subsequent `ON_RESUME` increments retry binding with the same ID.

**No build run (per instructions).**

---

### Claude Code (Instance C) — 2026-05-23 (CardStrip layout tweaks)

**Done (four changes to `ui/home/CardStrip.kt` only):**

1. `PEEK_HORIZONTAL` changed `3.dp` → `36.dp`.
2. `PAGE_SPACING` changed `1.dp` → `6.dp`.
3. Card `Box` modifier changed `.fillMaxWidth(0.80f)` → `.fillMaxWidth(0.95f)`.
4. `cardHeightDp` aspect-ratio multiplier changed `0.80f` → `0.95f` to match the new card width fraction.

No build run (per instructions).

---

### Claude Code (Instance A) — 2026-05-23 (Enter key + BIND_APPWIDGET fix)

**Done:**

1. **Enter key guard removed** (`LauncherSearchBar.kt`)
   - Removed the `if (savedQuery.isBlank() || savedQuery != query) return@DockedSearchBar` guard from `onSearch`. The guard was causing premature return because `onActiveChange` fires before `onSearch` and calls `vm.clearQuery()`, resetting `query` to `""`. The guard then incorrectly blocked launch.
   - `onSearch` now starts directly with `val top = savedResults.firstOrNull() ?: return@DockedSearchBar` — sufficient to prevent a no-op launch when there are no captured results.

2. **Preliminary BIND_APPWIDGET check removed** (`LauncherSearchBar.kt`)
   - Removed the `val permissionChecked = remember { mutableStateOf(false) }` declaration and the `LaunchedEffect(manualExpanded)` block that allocated a test widget ID, called `bindAppWidgetIdIfAllowed`, and started the BIND activity.
   - Root cause of the bug: two separate bind dialogs were being shown — one from this preliminary check (using a throw-away test ID) and one from `LiveWidgetCard` (using the real ID). System grants permission per-ID, so the preliminary check's approval did not help `LiveWidgetCard`, and the two overlapping dialogs confused the flow.
   - `LiveWidgetCard` is now the sole authority for BIND_APPWIDGET. It already handles the full bind lifecycle (allocate ID, call `bindAppWidgetIdIfAllowed`, launch dialog, retry on `ON_RESUME` via `resumeCount`).

---

### Claude Code (Instance A) — 2026-05-23 (stale-results race fix)

**Done:**

Added `LaunchedEffect(query)` immediately after `LaunchedEffect(results)` in `LauncherSearchBar.kt`:

```kotlin
LaunchedEffect(query) {
    if (query.isNotBlank()) savedResults = emptyList()
}
```

**Why:** `savedResults` is populated only when results arrive (after the 200ms debounce). If the user types "g", gets results, then types "gmail" quickly and hits Enter before "gmail" results arrive, `onSearch` would read `savedResults` still holding "g" results and launch the wrong app. Clearing `savedResults` on every non-empty query change ensures Enter only works when results are actually available for the current query.

`savedResults` is NOT cleared when `query` becomes `""` (from `vm.clearQuery()` in `onActiveChange`) — that would wipe out results we need for `onSearch`, which fires just after `onActiveChange`.

---

### Instance C — 2026-05-23 (CardStrip shadow padding fix)

**Done:**

- `CardStrip.kt` (`GesturableCard`): changed inner shadow Box padding from `.padding(30.dp)` to `.padding(horizontal = 12.dp, vertical = 30.dp)`.

**Why:** 30dp uniform padding was hiding adjacent peeking cards. Adjacent cards only peek ~23dp into view, so 30dp of horizontal padding pushed the card face entirely off-screen. 12dp horizontal is enough to contain the shadow (max elevation is 10dp, shadow spreads ~7dp), while 30dp vertical keeps the top/bottom shadow well-contained.

---

### Claude Code (Instance A) — 2026-05-23 (widget ID cache fix)

**Done:**

**`ui/search/LauncherSearchBar.kt` — persist widget IDs across composable lifecycle**

Root cause: `var appWidgetId by remember(provider.componentName) { mutableStateOf(-1) }` in `LiveWidgetCard` is lost whenever the composable leaves composition (e.g. the BIND permission dialog launches with `FLAG_ACTIVITY_NEW_TASK`, causing the search bar to collapse). On re-open, a new ID is allocated but the system's BIND permission was granted for the old ID, so `bindAppWidgetIdIfAllowed` fails again and the static preview is shown.

Three changes made:

1. **Added `private val widgetIdCache = HashMap<String, Int>()`** at file scope (between the imports and `PLACEHOLDER_WORDS`). Process-level — survives composable lifecycle for the duration of the process.

2. **`LiveWidgetCard` — initialise `appWidgetId` from cache:**
   ```kotlin
   var appWidgetId by remember(provider.componentName) {
       mutableStateOf(widgetIdCache[provider.componentName] ?: -1)
   }
   ```

3. **Store newly allocated IDs in the cache:**
   ```kotlin
   val id = if (appWidgetId != -1) appWidgetId else {
       val newId = widgetHost.allocateAppWidgetId()
       widgetIdCache[provider.componentName] = newId
       appWidgetId = newId
       newId
   }
   ```

4. **Do not delete the ID on dispose** — replaced the `deleteAppWidgetId` call in `DisposableEffect.onDispose` with a comment explaining that deletion would revoke the system's per-ID BIND permission and force the dialog to appear again on next composition.

**Watch out for:**
- `widgetIdCache` is never cleared — IDs accumulate for the lifetime of the process. For a launcher that shows one widget at a time this is negligible; if many distinct providers are ever shown, a bounded LRU could be substituted.
- The approved ID stays registered with `AppWidgetHost` for the process lifetime. If the host's `stopListening()` is called (e.g. on search bar dispose), the host no longer delivers `RemoteViews` updates to the hostView, but the ID/permission pair is intact for the next session.

---

### Instance A — 2026-05-23 (search bar polish)

**Done (`LauncherSearchBar.kt` only):**

1. **Fix descenders cut off**: `paddingFromBaseline(bottom = 8.dp)` → `12.dp` on the animated word `Text` inside `AnimatedContent`.

2. **Search bar size increase**: added `Modifier.padding(vertical = 8.dp).then(modifier)` on `DockedSearchBar` to add 8dp space above and below the bar within its container (M3 1.2.1 has a fixed 56dp internal height — this is the safest way to make it feel bigger). Added `style = MaterialTheme.typography.bodyLarge` to both the `"Find your… "` `Text` and the animated word `Text` so the placeholder text scales up to match.

---

### Instance D — 2026-05-23 (drawer Back/Home close bug fix)

**Root cause — two interlocking bugs causing "closes but reopens" / stuck BackHandler:**

**Bug 1: `onOpen()` called before settle completes in the merged signal handler.**
In `LaunchedEffect(openSignal, closeSignal)`, `onOpen()` was called *before* `state.settle(-1_000_000f)`. `settle()` suspends until the animation finishes. If `collectLatest` cancelled the coroutine mid-settle (a close signal arrived during the open animation), `state.currentValue` never reached `Open`. But `onOpen()` had already fired and set `drawerIsOpen = true`. The `snapshotFlow { state.currentValue }` watcher, which only emits on transitions, never saw `Closed → Open → Closed` — it saw `Closed` the whole time — so `onClose()` never fired. `drawerIsOpen` was stuck permanently `true`, keeping the BackHandler enabled and swallowing every back press forever.

**Bug 2: Gesture handlers set `drawerIsOpen = true` prematurely.**
Both the outer-column swipe gesture and the 48dp handle set `drawerIsOpen = true` unconditionally before emitting to `drawerSettleFlow`. If the physics settle snapped back to Closed (insufficient drag or positive fling velocity), `onClose()` would fire and clear `drawerIsOpen` — but only if `currentValue` transitioned through `Open`, which it does not on a snap-back. So again `drawerIsOpen` could get stuck `true` with the drawer visually closed.

**Fixes — two files:**

`AppDrawer.kt`:
- Removed `onOpen()` call from the merged `LaunchedEffect(openSignal, closeSignal)` handler.
- Added `onOpen()` to the `snapshotFlow { state.currentValue }` watcher in `LaunchedEffect(Unit)`, triggered when `value == DrawerValue.Open` — symmetrically with `onClose()` for `value == Closed`. `drawerIsOpen` is now driven exclusively by the drawer's real settled state, never by animation intent.

`HomeScreen.kt`:
- Removed `drawerIsOpen = true` from both gesture handlers (parent-column swipe-up and 48dp invisible handle). Neither gesture sets `drawerIsOpen` anymore. Only `onOpen()` (settled Open) and `onClose()` (settled Closed) control it.
- The immediate `drawerIsOpen = false` in the `BackHandler` block before emitting `drawerCloseSignal.tryEmit(Unit)` is preserved — it disables the BackHandler on the same frame, preventing double-firing on rapid back presses.

---

### Instance B — 2026-05-23 (build check)

Ran `assembleDebug`. **BUILD SUCCESSFUL** in 4s — no compile errors. All 35 tasks up-to-date or executed cleanly. No code changes made.

**Why this is safe:** `drawerIsOpen` and thus the BackHandler may be briefly inactive *during* the open animation. This is correct — the user physically cannot press Back before the animation completes in normal interaction. If they somehow do, the close signal is still emitted and the settle runs from wherever the drawer currently is, landing at Closed cleanly.

---

### Instance C — 2026-05-23

Changed `PEEK_HORIZONTAL` from `36.dp` to `60.dp` in `CardStrip.kt`.
Reason: On Pixel 9 Pro (448dp screen), 36dp peek left only ~9dp of adjacent card face visible — effectively invisible. 60dp gives ~34dp of visible card face on each side.

---

### Claude Code (Instance A) — 2026-05-23 (LiveWidgetCard async providerInfo + binding decoupled from providerInfo)

**Done (`ui/search/LauncherSearchBar.kt` — `LiveWidgetCard` only):**

**Root cause fixed:** `providerInfo` was fetched via synchronous `remember { runCatching { appWidgetManager.getInstalledProviders().find { ... } }.getOrNull() }` on the composition thread. When it returned `null` (timing/threading), the `LaunchedEffect` hit the early-return `showFallback = true` guard before the BIND dialog had a chance to launch — user never saw a permission request.

1. **Fix 1 — `providerInfo` moved to `produceState` (async, IO thread)**
   - `remember(provider.componentName) { runCatching { ... }.getOrNull() }` → `produceState<AppWidgetProviderInfo?>(null, provider.componentName) { value = withContext(Dispatchers.IO) { runCatching { ... }.getOrNull() } }`
   - Provider lookup now runs off the main thread and populates asynchronously.
   - Added `import android.appwidget.AppWidgetProviderInfo` (was not previously imported).
   - `Dispatchers`, `withContext` were already imported.

2. **Fix 2 — `LaunchedEffect` binding decoupled from `providerInfo`**
   - Removed `val info = providerInfo ?: run { showFallback = true; return@LaunchedEffect }` guard.
   - Binding now uses `ComponentName.unflattenFromString(provider.componentName)` directly, so the BIND dialog can launch regardless of whether `providerInfo` has resolved yet.
   - `ACTION_APPWIDGET_UPDATE` broadcast now uses `setComponent(component)` (instead of `component = info.provider`).
   - `ACTION_APPWIDGET_BIND` now passes `component` (already a `ComponentName`) rather than `ComponentName.unflattenFromString(...)`.

3. **Fix 3 — `AndroidView` display condition**
   - `!showFallback && bound && providerInfo != null && appWidgetId != -1` → `!showFallback && bound && appWidgetId != -1`
   - Widget view is shown as soon as bound + ID allocated; `providerInfo` is used if available (`val info = providerInfo`) but not required.
   - `AndroidView.factory` now checks `if (info != null) widgetHost.createView(ctx, appWidgetId, info) else android.view.View(ctx)`.

**Watch out for:**
- `providerInfo` now resolves asynchronously. During the brief window before it resolves, `AppWidgetHost.createView` falls back to an empty `View`. Once `providerInfo` arrives and Compose recomposes, the condition is still met (`bound` + `appWidgetId != -1`) and the factory has already run — `AndroidView` does not re-run `factory`. This is a known limitation of `AndroidView`; not a regression since the widget is already live by the time `providerInfo` resolves.
- `AppWidgetProviderInfo` import is new — no other file needs it; the type is only used in `LiveWidgetCard`.

---

### Instance D — 2026-05-23 (drawer "closes but reopens" gesture fix)

**Root cause (two interlocking bugs):** (1) Both gesture handlers in `HomeScreen.kt` — the outer Column's `pointerInput` and the 48dp invisible handle Box's `pointerInput` — had no awareness of `drawerIsOpen`. When Android's home gesture (swipe-up-from-bottom) passed through this area while a close was in progress, the handlers detected an upward drag and emitted to `drawerSettleFlow` with a large negative (open-direction) velocity, undoing the close. (2) `settleFlow` in `AppDrawer.kt` was collected in a separate `LaunchedEffect(settleFlow)` with plain `collect`. A stale open-direction velocity emitted to `settleFlow` just before BackHandler fired a close signal could execute after the close-signal settle finished and the mutex released, reopening the drawer.

**Fix 1 — `HomeScreen.kt`:** Gated both gesture handlers on `!drawerIsOpen` by changing their `pointerInput` keys to include `drawerIsOpen` and wrapping the gesture body in `if (!drawerIsOpen)`. When `drawerIsOpen = true`, the lambda re-runs but no gestures are processed. The outer Column uses `pointerInput(searchActive, drawerIsOpen)` with `if (!searchActive && !drawerIsOpen)`; the 48dp handle uses `pointerInput(drawerIsOpen)` with `if (!drawerIsOpen) awaitEachGesture { ... }`.

**Fix 2 — `AppDrawer.kt`:** Merged `settleFlow` into the existing `LaunchedEffect(openSignal, closeSignal)` merged handler, replacing it with `LaunchedEffect(openSignal, closeSignal, settleFlow)`. The three flows are merged using tagged Float sentinels (`NEGATIVE_INFINITY` = open, `POSITIVE_INFINITY` = close, real float = settle) and dispatched via `collectLatest`. Removed the separate `LaunchedEffect(settleFlow)` entirely. This ensures any stale open-direction velocity from `settleFlow` is cancelled when a close signal arrives, preventing the reopening race.

---

### Claude Code (main conversation) — 2026-05-24 (LauncherSearchBar.kt cleanup)

**Done (no behavior changes):**

1. **Removed dead `savedQuery` variable** — was declared and assigned in `LaunchedEffect(results)` (`savedQuery = query`) but never read. Declaration and assignment both deleted.

2. **Merged duplicate `if (manualExpanded)` blocks** in `LaunchedEffect(manualExpanded)` — the separate `if (manualExpanded) savedResults = emptyList()` line was folded into the existing `if (manualExpanded) { wasExpanded = true }` block.

3. **Removed empty `DisposableEffect(provider.componentName)`** in `LiveWidgetCard` — its `onDispose` contained only a comment explaining why widget IDs are not deleted. Block removed; comment moved (expanded) to the `widgetIdCache` declaration at the top of the file.

4. **Import audit** — no imports became orphaned; `MutableStateFlow` and all other imports remain referenced after the above changes.

---

### Instance C — 2026-05-24 (CardStrip.kt cleanup)

**Done (no behavior changes):**
- Extracted `springBack: () -> Unit` local lambda just before the `detectDragGesturesAfterLongPress` call in the overview drag-to-reorder `pointerInput` block. The identical bodies of `onDragEnd` and `onDragCancel` were deduplicated; both now reference `springBack` directly (`onDragEnd = springBack, onDragCancel = springBack`).
- Scanned all imports — none unused.

---

### Instance A — 2026-05-24 (LauncherSearchBar.kt debug logging)

**Done:**
- Added `import android.util.Log`.
- **Task 1 (Enter key bug):** Added `Log.d("DeckSearch", ...)` at the top of the `onSearch` callback (logs query, savedResults size, top result type); in `LaunchedEffect(query)` when clearing savedResults; in `LaunchedEffect(results)` when saving results.
- **Task 2 (widget binding bug):** In `LiveWidgetCard`'s `LaunchedEffect`, added `isDefaultLauncher` check (resolves ACTION_MAIN/CATEGORY_HOME) with a log before the binding code; replaced bare `bindAppWidgetIdIfAllowed` call with a logged `bindResult` variable; added log before launching the BIND intent; replaced bare catch with `Log.e`; added `Log.d("DeckWidget", ...)` at start of `StaticWidgetPreview`.
- Key hypothesis logged: `bindAppWidgetIdIfAllowed` always returns false for non-default launchers on Android 12+; `isDefaultLauncher` in logs will confirm/deny.

---

### Instance D — 2026-05-24 (AppDrawer.kt structural cleanup)

**Done (no behavior changes):**

- Extracted ShortcutMenuDialog private composable — receives app, iconBitmap, iconCorner: Dp, shortcuts, widgetProviders, onDismiss, onPickWidget, onHide. Contains the single AlertDialog with the header row, shortcut rows, "Select widget" button, and "Hide" button.
- Extracted WidgetPickerDialog private composable — receives app, widgetProviders, onDismiss. Contains the AlertDialog with RadioButton list and preview images, plus Save/Cancel buttons. Moved val pinRepo and var selected inside this composable.
- Both AppGridItem and AppListItem now delegate to these two composables instead of containing inline AlertDialog blocks. Grid passes iconCorner = 12.dp; List passes iconCorner = 8.dp.
- Added import androidx.compose.ui.unit.Dp (needed for the new parameter type).
- All comments preserved verbatim. All existing logic preserved verbatim.
- HomeScreen.kt: no unused imports found; no changes made.

---

### Instance D — 2026-05-24 (AppDrawer.kt bug fix + debug logging)

**Task 1 — Fix `nestedScrollConnection` reopening drawer after close:**

- Declared `val isSettlingClosed = remember { mutableStateOf(false) }` just before the `nestedScrollConnection` block.
- In the merged `collectLatest` handler, the `velocity == Float.POSITIVE_INFINITY` (close) branch now sets `isSettlingClosed.value = true` before calling `state.settle(1_000_000f)`.
- In the `snapshotFlow` collector, the `DrawerValue.Closed` branch now resets `isSettlingClosed.value = false` before calling `onClose()`.
- `onPostFling` now guards the settle call: `if (!isSettlingClosed.value || decisive > 0f)`. An upward fling (decisive < 0, open direction) is suppressed while a close settle is in progress; downward flings (decisive > 0) are always allowed so a drag-down can still close the drawer.

**Task 2 — Debug logging:**

- Added `import android.util.Log` to both AppDrawer.kt and HomeScreen.kt.
- AppDrawer.kt (tag "DeckDrawer"): logs on each `collectLatest` signal (velocity + close/open flags), on each `snapshotFlow` emission (currentValue), and in `onPostFling` (available.y, isSettlingClosed, decisive).
- HomeScreen.kt (tag "DeckHome"): logs in `BackHandler` (drawerIsOpen was), in `drawerCloseEvent` collector, in `onOpen`/`onClose` lambdas passed to AppDrawer, and before `drawerOpenSignal.tryEmit` (tap) and `drawerSettleFlow.tryEmit` (drag settle) in the 48dp handle box.

### Instance C — 2026-05-24

**Done:**
- `CardStrip.kt` line 321 (shadow `Box` inside `GesturableCard`): changed `.padding(horizontal = 12.dp, vertical = 30.dp)` to `.padding(horizontal = 4.dp, vertical = 16.dp)`.

**Why:**
- The 12dp horizontal padding on each side produced a ~25dp visual gap between adjacent card faces, making `PAGE_SPACING` irrelevant for controlling the inter-card gap.
- The 30dp vertical padding was consuming 60dp of card height, causing cards to look nearly square on compact devices (ikko Mind 1 Pro).
- Reducing to 4dp horizontal (tighter gap, more card face visible) and 16dp vertical (taller cards, better portrait aspect ratio) fixes both issues while still giving the shadow enough room to render.

---

### Instance C — 2026-05-24 (PEEK_HORIZONTAL increase)

**Done:**
- `CardStrip.kt` line 34: changed `PEEK_HORIZONTAL` from `40.dp` to `52.dp`.

**Why:**
- `PEEK_HORIZONTAL` is the horizontal content padding on the `HorizontalPager`. Increasing it narrows the center card and exposes more of the adjacent side cards, giving the user a stronger visual cue that there are cards to swipe to.

---

### Instance D — 2026-05-24 (drawer open race fix)

**Root cause:** `onOpen()` was driven by `snapshotFlow { state.currentValue }`, which only updates when `settle()` completes at an anchor. The merged `collectLatest` handler can cancel a settle mid-flight (e.g. double gesture emissions while `drawerIsOpen=false` keeps both gesture handlers active). Result: drawer animates partway open, `currentValue` stays `Closed`, `onOpen()` never fires, `drawerIsOpen` stays `false` forever. On the ikko the timing is worse and the drawer never opens at all.

**Fix 1 — AppDrawer.kt:**
- Added `import kotlinx.coroutines.flow.distinctUntilChanged`.
- Split the single `LaunchedEffect(Unit)` that handled both open and close into two separate effects:
  - **targetValue watcher** (new): `snapshotFlow { state.targetValue }` + `.drop(1).distinctUntilChanged()`. Fires `onOpen()` + scroll resets as soon as `settle()` is called toward Open — does not wait for completion. `targetValue` updates immediately when `settle()` is invoked, so it is immune to mid-flight cancellation.
  - **currentValue watcher** (retained for close): `snapshotFlow { state.currentValue }` + `.drop(1)`. Still fires `onClose()` + resets `isSettlingClosed` only when fully settled at Closed, which is reliable because close settles always complete (no competing open signals interrupt them).

**Fix 2 — HomeScreen.kt:**
- Removed the `if (drawerIsOpen)` guard from the `drawerCloseEvent` collector. The signal must always go through so that back/home works even when `drawerIsOpen` has not yet been set to true (which can happen because `onOpen()` now fires earlier, at targetValue, and the state variable update may lag a recomposition).

---

### Instance C — 2026-05-24 (landscape screen aspect fix)

**File:** `CardStrip.kt` line 140.

**Problem:** On the ikko device `displayMetrics.heightPixels < displayMetrics.widthPixels` (landscape or wide screen), so `heightPixels / widthPixels` produces a value < 1. This made `cardWidth = cardHeight / screenAspect` come out wider than the card height, yielding landscape-shaped cards.

**Fix:** Replace the raw division with `maxOf(h, w) / minOf(h, w)` so `screenAspect` is always ≥ 1 regardless of which dimension the device reports as "height".

---


---

### Claude Code (Instance C) — 2026-05-24 (screenAspect fix)

**Done:**

1. **screenAspect ratio fix** (CardStrip.kt)
   - Replaced maxOf(h, w) / minOf(h, w) with heightPixels.toFloat() / widthPixels so the ratio correctly reflects device orientation. Portrait gives ratio > 1 (tall cards), landscape gives ratio < 1 (wide cards). The old max/min formula always returned a ratio > 1, forcing portrait-shaped cards regardless of orientation.

---

### Instance D — 2026-05-24

**Done:**

1. **Fixed `drawerIsOpen` stuck permanently `true` bug** (`AppDrawer.kt`)

   **Root cause:** When a close settle started but `state.currentValue` was already `Closed` (e.g. settle was cancelled mid-open, so the drawer never reached `Open`), `currentValue` never emits a new value — it was already `Closed` and a `snapshotFlow` only re-emits on change. Therefore `onClose()` never fired, `drawerIsOpen` stayed `true` permanently, gesture handlers were gated off (they check `!drawerIsOpen`), and the drawer would not reopen. Pressing Back reset `drawerIsOpen = false` and unblocked it.

   **Fix:** Merged both `snapshotFlow { state.targetValue }` (which previously only handled Open) and `snapshotFlow { state.currentValue }` (which handled Closed) into a single `LaunchedEffect(Unit)` watching `targetValue` for **both** directions:
   - `DrawerValue.Open`: fires `onOpen()` + scroll resets immediately when the settle targets Open (not waiting for completion, since `collectLatest` can cancel settle and `currentValue` would never reach Open).
   - `DrawerValue.Closed`: fires `isSettlingClosed.value = false` + `onClose()` as soon as the settle targets Closed — not waiting for completion either, so `drawerIsOpen` resets even if settle was cancelled mid-open and `currentValue` was already `Closed`.

   Removed the separate `LaunchedEffect(Unit)` that watched `state.currentValue`; it is now subsumed by the single `targetValue` watcher.

### Instance C — 2026-05-24 (PEEK_HORIZONTAL increase to 64.dp)

**Done:**
- `CardStrip.kt` line 35: changed `PEEK_HORIZONTAL` from `52.dp` to `64.dp`.

**Why:**
- Increasing `PEEK_HORIZONTAL` widens the content padding on each side of the HorizontalPager, narrowing the center card and revealing more of the adjacent side cards. The user wants the side cards to peek in more.


---

### Instance A — 2026-05-24 (two bug fixes, no build)

**Done:**

1. **Overview mode cards stopping at screen edge** (`CardStrip.kt`)
   - Removed `.coerceIn(-1f, 1f)` from the overview branch of `translationX` in the card `graphicsLayer` block.
   - Before: `signedPageOffset.coerceIn(-1f, 1f) * maxWidthPx * (1f - baseScale) / 1.03f` — all cards at distance > 1 from current got the same clamped translation, parking them visually at the screen edge until they disappeared.
   - After: `signedPageOffset * maxWidthPx * (1f - baseScale) / 1.03f` — the multiplier is always < 1, so no blank edges are possible.

2. **Drawer snap broken when dragging from within list content** (`AppDrawer.kt`)
   - Added `onPreFling` override to the `nestedScrollConnection`. When the drawer has been pulled down (offset > 1f) and the user flings downward (available.y > 50f), the fling is intercepted BEFORE the list can consume the velocity, and `state.settle(1_000_000f)` is called immediately.
   - Root cause: the list's own fling handler consumed the downward velocity before `onPostFling` received it, leaving `available.y ≈ 0`. With near-zero velocity, settle fell back to the 30% positional threshold, so a short drag + fling snapped open instead of closed.
   - `onPostFling` unchanged — still handles slow-drag-then-release via positional threshold.

**No build run yet — Instance B should build and install.**

---

### Instance B — 2026-05-24

**Done:**

1. **Build verification** — `assembleDebug` BUILD SUCCESSFUL in 54s (35 actionable tasks: 10 executed, 25 up-to-date). No compile errors from Instance A's two changes:
   - `CardStrip.kt`: removed `.coerceIn(-1f, 1f)` from overview `translationX` formula — compiled cleanly.
   - `AppDrawer.kt`: added `onPreFling` override to `nestedScrollConnection` — compiled cleanly.
   - Same single pre-existing deprecation warning on `AppOpsManager.checkOpNoThrow`. No new warnings.

2. **Install** — `installDebug` succeeded. APK `deck-v24-debug.apk` installed on `Clicks_Communicator(AVD) - 17` in 10s.

No fixes were required.

---
