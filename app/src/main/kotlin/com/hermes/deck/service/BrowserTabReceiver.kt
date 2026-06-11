package com.hermes.deck.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BrowserTabReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // The tabs-list reply carries an int[] of every live tab, not a single task_id, so handle
        // it before the single-task_id guard below.
        if (intent.action == ACTION_TABS_LIST) {
            val ids = intent.getIntArrayExtra(EXTRA_TASK_IDS) ?: IntArray(0)
            BrowserTabEventBus.emitTabsList(ids)
            return
        }
        val taskId = intent.getIntExtra(EXTRA_TASK_ID, -1)
        if (taskId == -1) return
        when (intent.action) {
            ACTION_TAB_OPENED  -> BrowserTabEventBus.emit(
                BrowserTabEventBus.NewTabEvent(
                    BROWSER_PACKAGE, taskId,
                    parentPackage = intent.getStringExtra(EXTRA_PARENT_PACKAGE)?.takeIf { it.isNotBlank() }
                )
            )
            ACTION_TAB_FOCUSED -> BrowserTabEventBus.currentFocusedTaskId = taskId
            ACTION_PAGE_LOADED -> BrowserTabEventBus.emitPageLoaded(taskId)
            ACTION_TAB_GONE    -> BrowserTabEventBus.emitTabGone(taskId)
        }
    }

    companion object {
        const val ACTION_TAB_OPENED  = "com.hermes.deck.ACTION_BROWSER_TAB_OPENED"
        const val ACTION_TAB_FOCUSED = "com.hermes.deck.ACTION_BROWSER_TAB_FOCUSED"
        const val ACTION_PAGE_LOADED = "com.hermes.deck.ACTION_BROWSER_PAGE_LOADED"
        const val ACTION_TAB_GONE    = "com.hermes.deck.ACTION_BROWSER_TAB_GONE"
        // Deck -> Browser request to enumerate live tab tasks; Browser replies with ACTION_TABS_LIST.
        const val ACTION_ENUMERATE_TABS = "com.hermes.browser.ACTION_ENUMERATE_TABS"
        // Deck -> Browser: close this tab's task (card swiped away in Deck) so it leaves system recents too.
        const val ACTION_CLOSE_TAB    = "com.hermes.browser.ACTION_CLOSE_TAB"
        // Browser -> Deck reply carrying the live tab task ids (int[] EXTRA_TASK_IDS).
        const val ACTION_TABS_LIST   = "com.hermes.deck.ACTION_BROWSER_TABS_LIST"
        const val EXTRA_TASK_ID      = "task_id"
        const val EXTRA_TASK_IDS     = "task_ids"
        const val EXTRA_PARENT_PACKAGE = "parent_package"
        const val BROWSER_PACKAGE    = "com.hermes.browser"
    }
}
