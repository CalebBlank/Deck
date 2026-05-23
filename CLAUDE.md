# Deck — Android Launcher

A webOS-inspired Android launcher for the Clicks Communicator, modernized with Material 3.

## Vision

The home screen IS the multitasking view. Recent apps are shown as large cards (webOS card metaphor) that fill the screen. A search bar at the bottom provides universal search across apps, contacts, calculator, and third-party plugins. No traditional home screen grid — the app drawer slides up from the search bar.

## Target Device

**Clicks Communicator** — compact Android device with a physical QWERTY keyboard and trackpad surface. Design for small/compact screen density from the start. Use `WindowSizeClass` for layout adaptation.

## Key Design Decisions

- **No live app thumbnails via public API** — `ActivityManager.getRecentTasks()` is deprecated and returns nothing useful for third-party apps. Instead, `ScreenshotAccessibilityService` captures screenshots via `AccessibilityService.takeScreenshot()` (API 30+) whenever an app comes to foreground, stores them in `ScreenshotCache`. Icon fallback shown until a screenshot is captured.
- **Card dismissal is logical only** — we cannot remove apps from the system recents stack without system privileges. Dismissing a card removes it from Deck's list only.
- **Search results are custom Compose cards** — do NOT use `AppWidgetHost` for search results. The lifecycle management is wrong for transient results. Build custom composables that fetch the same data.
- **NEVER use `AnimationCurveCustomFunction`** — crashes firmware on Pebble. (Unrelated to this project but noted in memory.)
- **Plugin system uses ContentProvider** — third-party plugins declare a ContentProvider with authority starting with `com.hermes.deck.plugin.`. See `PluginContract.kt` for the full spec.

## Architecture

```
data/
  AppInfo.kt                  — data class (packageName, label, icon, lastUsed)
  ScreenshotCache.kt          — LRU singleton, shared between service and UI
  RecentAppsRepository.kt     — UsageStatsManager → recent apps list
  InstalledAppsRepository.kt  — PackageManager → all installed apps

service/
  ScreenshotAccessibilityService.kt  — captures screenshot on TYPE_WINDOW_STATE_CHANGED

ui/theme/
  Theme.kt                    — Material 3 dynamic color

ui/home/
  HomeScreen.kt               — root layout (Column: CardStrip + SearchBar), drawer overlay
  HomeViewModel.kt            — recentApps StateFlow, dismissCard()
  WallpaperBackground.kt      — draws system wallpaper as bitmap
  CardStrip.kt                — HorizontalPager, peek on both sides, gesture handling
  AppCard.kt                  — fills pager space, screenshot or icon, label at bottom
  CardActions.kt              — Uninstall / App Info / Hide row (revealed by dragging card down)

ui/drawer/
  AppDrawer.kt                — full-screen grid, slides up from bottom
  DrawerViewModel.kt          — installed apps StateFlow

ui/search/
  LauncherSearchBar.kt        — DockedSearchBar, animated cycling placeholder
  SearchViewModel.kt          — fans query to all providers in parallel (200ms debounce)
  SearchResult.kt             — sealed class: AppResult, ContactResult, CalculatorResult, PluginResult
  providers/
    SearchProvider.kt         — interface: id + suspend query(q) → List<SearchResult>
    AppSearchProvider.kt      — filters installed apps by label
    ContactSearchProvider.kt  — queries ContactsContract
    CalculatorProvider.kt     — simple left-to-right math eval

plugin/
  PluginContract.kt           — ContentProvider authority/column spec for third-party plugins
  PluginRepository.kt         — discovers plugin APKs, queries them
```

## Card Gestures

- **Swipe up** → dismiss (spring animation off-screen, removed from list)
- **Drag down** → `CardActions` row fades in above card; release past threshold snaps to revealed state
- **Tap card while actions revealed** → collapses back
- **Horizontal swipe** → handled by `HorizontalPager` to navigate between cards

## Permissions Required

| Permission | Why |
|---|---|
| `QUERY_ALL_PACKAGES` | See all installed apps (Play Store: declare as launcher) |
| `PACKAGE_USAGE_STATS` | Recent apps via UsageStatsManager (user must grant manually in Settings) |
| `READ_CONTACTS` | Contact search provider |
| `SET_WALLPAPER` | WallpaperBackground |
| `BIND_ACCESSIBILITY_SERVICE` | ScreenshotAccessibilityService |

`PACKAGE_USAGE_STATS` is not auto-granted. On first launch, HomeScreen shows a nudge card if permission is missing, linking to `Settings.ACTION_USAGE_ACCESS_SETTINGS`.

## Search Bar

`DockedSearchBar` (Material 3) anchored to the bottom of HomeScreen. Placeholder cycles through words ("Find your… groove/vibe/jam…") every 3 seconds with a vertical slide animation. Query is debounced 200ms before fanning out to providers in parallel. Results trickle in as providers respond.

## What's Not Yet Built

- AI query interpretation (Gemini Nano / ML Kit GenAI)
- Notification summarization
- `KEYCODE_APP_SWITCH` intercept (show Deck cards instead of system recents on 3-button nav)
- Per-card scale/shadow visual polish
- Dock bar (pinned apps)
- Onboarding flow (accessibility permission, usage access)
- Any actual search result UI cards beyond basic ListItems

## Build

- AGP 8.4.0, Kotlin 2.0.0, Compose BOM 2024.06.00
- `compileSdk 35`, `minSdk 26`, `targetSdk 35`
- No Hilt — ViewModels use manual factory pattern (`companion object { fun factory(context) }`)
- No Coil/Accompanist — Drawable→Bitmap conversion done manually

Open in Android Studio and sync Gradle. The Gradle wrapper JAR is generated on first sync.
