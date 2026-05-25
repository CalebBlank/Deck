package com.hermes.deck.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import com.hermes.deck.data.ScreenshotCache
import java.util.concurrent.Executors

private const val CAPTURE_DELAY_MS = 400L

class ScreenshotAccessibilityService : AccessibilityService() {

    private val executor = Executors.newSingleThreadExecutor()
    private val handler  = Handler(Looper.getMainLooper())
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == "android") return
        if (pkg == packageName) {
            // User returned to launcher — cancel any pending capture, don't take a new one.
            // The screenshot stored from when the app was opened is already correct.
            handler.removeCallbacksAndMessages(null)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({ captureScreenshot(pkg) }, CAPTURE_DELAY_MS)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun captureScreenshot(targetPackage: String) {
        takeScreenshot(Display.DEFAULT_DISPLAY, executor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                val hw = screenshot.hardwareBuffer ?: return
                val hwBmp = Bitmap.wrapHardwareBuffer(hw, null)
                hw.close()
                if (hwBmp == null) return
                val soft = hwBmp.copy(Bitmap.Config.ARGB_8888, false)
                hwBmp.recycle()
                ScreenshotCache.put(targetPackage, soft)
            }

            override fun onFailure(errorCode: Int) {
                // Icon fallback will be shown — no action needed
            }
        })
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        executor.shutdown()
    }
}
