package com.hermes.deck

import android.app.role.RoleManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.compose.ui.graphics.Color
import com.hermes.deck.service.AppSwitchEventBus
import com.hermes.deck.ui.home.HomeScreen
import com.hermes.deck.ui.home.HomeViewModel
import com.hermes.deck.ui.onboarding.OnboardingActivity
import com.hermes.deck.ui.search.providers.ClaudeDeepLink
import com.hermes.deck.ui.search.providers.ClaudeNotifications
import com.hermes.deck.ui.theme.DeckTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        /** True while MainActivity is resumed. Read by ScreenshotAccessibilityService.onKeyEvent. */
        @Volatile var isInForeground = false
    }

    private lateinit var homeVm: HomeViewModel
    private var isDarkTheme    by mutableStateOf(true)
    private var isDynamicColor by mutableStateOf(true)
    private var seedColor      by mutableStateOf<Color?>(null)

    private lateinit var appPrefs: SharedPreferences
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        when (key) {
            "material_you" -> { isDynamicColor = prefs.getBoolean(key, true); seedColor = readSeedColor(prefs) }
            "seed_color"   -> seedColor = readSeedColor(prefs)
            "theme_mode"   -> { isDarkTheme = resolveIsDark(prefs.getString(key, "system") ?: "system"); applyStatusBarIconColor() }
        }
    }

    private val requestDefaultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* result handled passively — RoleManager updates system state */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Idempotent: enables on-disk screenshot persistence + preloads any saved shots so restored
        // cards (esp. browser tabs) aren't blank frames. Also called from the accessibility service,
        // since there's no Application subclass and either may be the first entry point.
        com.hermes.deck.data.ScreenshotCache.init(applicationContext)
        homeVm = ViewModelProvider(this, HomeViewModel.factory(this))[HomeViewModel::class.java]
        appPrefs = getSharedPreferences("deck_prefs", MODE_PRIVATE)
        isDarkTheme    = resolveIsDark(appPrefs.getString("theme_mode", "system") ?: "system")
        isDynamicColor = appPrefs.getBoolean("material_you", true)
        seedColor      = readSeedColor(appPrefs)
        appPrefs.registerOnSharedPreferenceChangeListener(prefsListener)
        // Launchers must never exit on back. Compose BackHandlers take priority (registered later,
        // so higher LIFO priority). This fallback fires only when no Compose handler is enabled,
        // e.g. if the drawer is open but its BackHandler is disabled due to a state tracking lag.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { homeVm.requestDrawerClose() }
        })
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        applyStatusBarIconColor()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            DeckTheme(darkTheme = isDarkTheme, dynamicColor = isDynamicColor, seedColor = seedColor) {
                HomeScreen(modifier = Modifier.fillMaxSize())
            }
        }
        maybeShowOnboarding()
        maybeRequestDefaultLauncher()
        handleClaudeDeepLink(intent)   // cold-started from a "Claude replied" notification tap
        // AppSwitchEventBus is emitted by ScreenshotAccessibilityService (which always consumes
        // KEYCODE_APP_SWITCH so SystemUI never sees it, even during unlock transitions).
        lifecycleScope.launch {
            AppSwitchEventBus.events.collect { homeVm.cycleCard() }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            homeVm.backspaceKeyInput()
            return true
        }
        val ch = event?.unicodeChar?.takeIf { it >= 0x20 }?.toChar()
        if (ch != null) {
            homeVm.appendKeyInput(ch)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        isInForeground = true
        val prefs = getSharedPreferences("deck_prefs", MODE_PRIVATE)
        isDarkTheme    = resolveIsDark(prefs.getString("theme_mode", "system") ?: "system")
        isDynamicColor = prefs.getBoolean("material_you", true)
        seedColor      = readSeedColor(prefs)
        applyStatusBarIconColor()
        homeVm.refresh()
        homeVm.reloadPinned()
        // Re-sync browser-tab cards with the browser's live tabs so Deck matches native recents
        // (drops tabs closed/swiped-away there, adds new ones). Self-guards against the init race.
        homeVm.syncBrowserTabs()
        // Follow the user: jump the carousel to the card for whatever app/tab they were just in.
        homeVm.focusLastUsed()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // A tapped "Claude replied" notification reopens that chat instead of closing the drawer.
        if (handleClaudeDeepLink(intent)) return
        // Fired when the launcher is already in the foreground and receives a new launch
        // intent — e.g. user presses the home button again or uses the system home gesture.
        homeVm.requestDrawerClose()
    }

    /** If the intent came from a "Claude replied" notification, publish the session id for the
     *  search surface to reopen and cancel the notification. Returns true if it was consumed. */
    private fun handleClaudeDeepLink(intent: Intent?): Boolean {
        val sessionId = intent?.getStringExtra(ClaudeDeepLink.EXTRA_SESSION) ?: return false
        intent.removeExtra(ClaudeDeepLink.EXTRA_SESSION)   // don't re-fire on rotation / re-resume
        ClaudeDeepLink.pendingSessionId.value = sessionId
        ClaudeNotifications.cancel(this, sessionId)
        return true
    }

    override fun onPause() {
        super.onPause()
        isInForeground = false
    }

    override fun onDestroy() {
        super.onDestroy()
        appPrefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    private fun readSeedColor(prefs: android.content.SharedPreferences): Color? {
        if (isDynamicColor) return null
        val stored = prefs.getInt("seed_color", 0)
        return if (stored == 0) null else Color(stored)
    }

    private fun resolveIsDark(themeMode: String): Boolean = when (themeMode) {
        "dark"  -> true
        "light" -> false
        else    -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
    }

    private fun applyStatusBarIconColor() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            // Light status bars = dark (black) icons; we want white icons in dark mode
            isAppearanceLightStatusBars     = !isDarkTheme
            isAppearanceLightNavigationBars = !isDarkTheme
        }
    }

    private fun maybeShowOnboarding() {
        val prefs = getSharedPreferences("deck_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("onboarding_done", false)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            prefs.edit().putBoolean("onboarding_done", true).apply()
        }
    }

    private fun maybeRequestDefaultLauncher() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val prefs = getSharedPreferences("deck_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("launcher_prompt_shown", false)) return
        prefs.edit().putBoolean("launcher_prompt_shown", true).apply()

        val roleManager = getSystemService(RoleManager::class.java)
        if (!roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
            requestDefaultLauncher.launch(
                roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
            )
        }
    }

}
