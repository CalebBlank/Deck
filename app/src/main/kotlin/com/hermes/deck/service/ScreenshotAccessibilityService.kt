package com.hermes.deck.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import com.hermes.deck.data.ScreenshotCache
import java.util.concurrent.Executors

class ScreenshotAccessibilityService : AccessibilityService() {

    private val executor = Executors.newSingleThreadExecutor()
    private var lastCapturedPackage: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return         // don't capture the launcher itself
        if (pkg == lastCapturedPackage) return // already captured this app in this session
        lastCapturedPackage = pkg

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            captureScreenshot(pkg)
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
                // Copy to software bitmap so it can be drawn anywhere
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
        executor.shutdown()
    }
}
