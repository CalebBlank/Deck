package com.hermes.deck.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import com.hermes.deck.MainActivity
import com.hermes.deck.data.LivePreviewRepository
import com.hermes.deck.data.ScreenshotCache
import java.util.concurrent.Executors
import kotlinx.coroutines.*

private const val TAG = "DeckScreenshot"

private const val CAPTURE_DELAY_MS = 400L
// Browser: long fallback for already-loaded tabs. Pages that finish loading sooner will
// trigger a fresh capture via the pageLoaded flow and cancel this timer.
private const val BROWSER_CAPTURE_DELAY_MS = 4_000L
// Settle time after the page-loaded signal: lets the final paint complete.
private const val PAGE_LOADED_SETTLE_MS = 400L

class ScreenshotAccessibilityService : AccessibilityService() {

    private val keyguardManager by lazy { getSystemService(KeyguardManager::class.java) }
    private val executor = Executors.newSingleThreadExecutor()
    private val handler  = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        // Idempotent: this service writes the cache and may run with Deck's UI never opened, so it's
        // a valid first entry point for enabling on-disk screenshot persistence.
        ScreenshotCache.init(applicationContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            scope.launch {
                BrowserTabEventBus.pageLoaded.collect { taskId ->
                    // Only capture if the browser is actually visible.
                    if (MainActivity.isInForeground) {
                        Log.d(TAG, "pageLoaded: skip – Deck in foreground (taskId=$taskId)")
                        return@collect
                    }
                    // ACTION_TAB_FOCUSED and ACTION_PAGE_LOADED are sequential broadcasts, but
                    // system delivery can have small delays. Re-check focused after 300ms if needed.
                    var focused = BrowserTabEventBus.currentFocusedTaskId
                    Log.d(TAG, "pageLoaded: taskId=$taskId focused=$focused")
                    if (taskId != focused) {
                        delay(300)
                        focused = BrowserTabEventBus.currentFocusedTaskId
                        Log.d(TAG, "pageLoaded: retry – focused=$focused")
                    }
                    if (taskId != focused) {
                        Log.d(TAG, "pageLoaded: skip – task in background (taskId=$taskId focused=$focused)")
                        return@collect
                    }
                    // Determine the cache key NOW (before the async capture fires) so that any
                    // subsequent tab switches don't corrupt which key we write to.
                    val cacheKey = "${BrowserTabReceiver.BROWSER_PACKAGE}:$taskId"
                    // Wrap in handler.post so remove + schedule execute atomically on the main
                    // thread, preventing onAccessibilityEvent from cancelling our postDelayed
                    // between the two calls from this IO coroutine.
                    handler.post {
                        handler.removeCallbacksAndMessages(null)
                        handler.postDelayed(
                            { captureScreenshot(BrowserTabReceiver.BROWSER_PACKAGE, cacheKey) },
                            PAGE_LOADED_SETTLE_MS
                        )
                    }
                }
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_APP_SWITCH) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                when {
                    // Keyguard is up (locked or mid-dismiss animation). Calling GLOBAL_ACTION_HOME
                    // during this window races with the keyguard dismiss and triggers the system
                    // recents overlay on top of Deck. Let the unlock complete naturally — the
                    // default launcher comes to front on its own.
                    keyguardManager?.isKeyguardLocked == true -> Unit
                    MainActivity.isInForeground -> AppSwitchEventBus.emit()
                    else -> performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }
            // Always consume — never let SystemUI see this key.
            return true
        }
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == "android") return
        if (pkg == packageName) {
            // User returned to launcher — cancel any pending capture.
            handler.removeCallbacksAndMessages(null)
            scope.launch { LivePreviewRepository.getInstance(this@ScreenshotAccessibilityService).refreshAll() }
            return
        }
        ForegroundEventBus.emit(pkg)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            handler.removeCallbacksAndMessages(null)
            if (pkg == BrowserTabReceiver.BROWSER_PACKAGE) {
                Log.d(TAG, "accessibilityEvent: browser window change, focused=${BrowserTabEventBus.currentFocusedTaskId}")
                // Determine the cache key when the timer fires (4s later), not in the async
                // onSuccess callback. By then the focused-task broadcast has definitely arrived.
                handler.postDelayed({
                    val tid = BrowserTabEventBus.currentFocusedTaskId
                    if (tid == -1) return@postDelayed
                    val key = "${BrowserTabReceiver.BROWSER_PACKAGE}:$tid"
                    captureScreenshot(BrowserTabReceiver.BROWSER_PACKAGE, key)
                }, BROWSER_CAPTURE_DELAY_MS)
            } else {
                handler.postDelayed({ captureScreenshot(pkg, pkg) }, CAPTURE_DELAY_MS)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun captureScreenshot(targetPackage: String, cacheKey: String) {
        Log.d(TAG, "Attempting screenshot for $targetPackage → key=$cacheKey")
        takeScreenshot(Display.DEFAULT_DISPLAY, executor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                val hw = screenshot.hardwareBuffer ?: run {
                    Log.w(TAG, "onSuccess but hardwareBuffer is null for $targetPackage")
                    return
                }
                val hwBmp = Bitmap.wrapHardwareBuffer(hw, null)
                hw.close()
                if (hwBmp == null) {
                    Log.w(TAG, "wrapHardwareBuffer returned null for $targetPackage")
                    return
                }
                val soft = hwBmp.copy(Bitmap.Config.ARGB_8888, false)
                hwBmp.recycle()
                // FLAG_SECURE apps (banking, password managers, etc.) render as a uniform solid fill
                // in the screenshot. Don't cache that black rectangle — drop any stale one so the
                // card falls back to the app icon instead of an unidentifiable black card.
                if (isUniformFill(soft)) {
                    Log.d(TAG, "Screenshot for $targetPackage is a uniform fill (secure/blank) → using icon")
                    soft.recycle()
                    ScreenshotCache.remove(cacheKey)
                    return
                }
                ScreenshotCache.put(cacheKey, soft)
                Log.d(TAG, "Screenshot stored → $cacheKey (${soft.width}x${soft.height})")
            }

            override fun onFailure(errorCode: Int) {
                Log.e(TAG, "Screenshot failed for $targetPackage, errorCode=$errorCode")
            }
        })
    }

    /** True if the central content area is a single solid colour — the signature of a FLAG_SECURE
     *  window (rendered black in screenshots) or a blank/splash frame, not a real preview. Status/nav
     *  bars are excluded since they aren't secure and stay visible. */
    private fun isUniformFill(bmp: Bitmap): Boolean {
        val w = bmp.width; val h = bmp.height
        if (w < 8 || h < 8) return false
        val x0 = (w * 0.10f).toInt(); val x1 = (w * 0.90f).toInt()
        val y0 = (h * 0.20f).toInt(); val y1 = (h * 0.80f).toInt()
        val steps = 10
        var rMin = 255; var rMax = 0; var gMin = 255; var gMax = 0; var bMin = 255; var bMax = 0
        for (i in 0..steps) for (j in 0..steps) {
            val px = bmp.getPixel(x0 + (x1 - x0) * i / steps, y0 + (y1 - y0) * j / steps)
            val r = (px ushr 16) and 0xFF; val g = (px ushr 8) and 0xFF; val b = px and 0xFF
            if (r < rMin) rMin = r; if (r > rMax) rMax = r
            if (g < gMin) gMin = g; if (g > gMax) gMax = g
            if (b < bMin) bMin = b; if (b > bMax) bMax = b
        }
        return (rMax - rMin) <= 6 && (gMax - gMin) <= 6 && (bMax - bMin) <= 6
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        executor.shutdown()
        scope.cancel()
    }
}
