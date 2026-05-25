package com.hermes.deck

import android.app.AppOpsManager
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
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
import com.hermes.deck.ui.home.HomeScreen
import com.hermes.deck.ui.home.HomeViewModel
import com.hermes.deck.ui.onboarding.OnboardingActivity
import com.hermes.deck.ui.theme.DeckTheme

class MainActivity : ComponentActivity() {

    private lateinit var homeVm: HomeViewModel
    private var isDarkTheme    by mutableStateOf(true)
    private var isDynamicColor by mutableStateOf(true)

    private val requestDefaultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* result handled passively — RoleManager updates system state */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        homeVm = ViewModelProvider(this, HomeViewModel.factory(this))[HomeViewModel::class.java]
        val prefs = getSharedPreferences("deck_prefs", MODE_PRIVATE)
        isDarkTheme    = prefs.getBoolean("dark_mode", true)
        isDynamicColor = prefs.getBoolean("material_you", true)
        // Launchers must never exit on back — Compose BackHandlers take priority for UI state
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* no-op */ }
        })
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        applyStatusBarIconColor()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            DeckTheme(darkTheme = isDarkTheme, dynamicColor = isDynamicColor) {
                HomeScreen(modifier = Modifier.fillMaxSize())
            }
        }
        maybeShowOnboarding()
        maybeRequestDefaultLauncher()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_APP_SWITCH) {
            homeVm.cycleCard()
            return true
        }
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
        val prefs = getSharedPreferences("deck_prefs", MODE_PRIVATE)
        isDarkTheme    = prefs.getBoolean("dark_mode", true)
        isDynamicColor = prefs.getBoolean("material_you", true)
        applyStatusBarIconColor()
        homeVm.refresh()
        homeVm.reloadPinned()
        homeVm.requestDrawerClose()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Fired when the launcher is already in the foreground and receives a new launch
        // intent — e.g. user presses the home button again or uses the system home gesture.
        homeVm.requestDrawerClose()
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
            prefs.edit().putBoolean("onboarding_done", true).apply()
            startActivity(Intent(this, OnboardingActivity::class.java))
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

    fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabled?.contains(packageName) == true
    }

    fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
