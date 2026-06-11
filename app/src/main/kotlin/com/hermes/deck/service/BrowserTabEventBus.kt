package com.hermes.deck.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object BrowserTabEventBus {
    // parentPackage: the app that launched this tab (from the browser's Activity.getReferrer()),
    // null if unknown. Used to stack the tab with its source app instead of guessing via UsageStats.
    // isRestore: true when this event is replaying a persisted tab on startup (or reconcile re-add),
    // false for a genuine live TAB_OPENED. Lets the consumer drop a restored tab that the browser's
    // live-tab enumeration has since reported as dead, without affecting real new tabs.
    data class NewTabEvent(
        val packageName: String,
        val taskId: Int,
        val parentPackage: String? = null,
        val isRestore: Boolean = false
    )

    private val _events = MutableSharedFlow<NewTabEvent>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()
    fun emit(event: NewTabEvent) { _events.tryEmit(event) }

    @Volatile var currentFocusedTaskId: Int = -1

    private val _pageLoaded = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    val pageLoaded = _pageLoaded.asSharedFlow()
    fun emitPageLoaded(taskId: Int) { _pageLoaded.tryEmit(taskId) }

    // Emitted when the browser's reopen trampoline can't find a tab task (it's genuinely gone) —
    // Deck drops the now-dead card. extraBufferCapacity so a tap while no collector still lands.
    private val _tabGone = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    val tabGone = _tabGone.asSharedFlow()
    fun emitTabGone(taskId: Int) { _tabGone.tryEmit(taskId) }

    // The browser's reply to an enumerate request: the task ids of every live tab task. Deck
    // reconciles its persisted cards against this authoritative list (the browser is the only
    // process that can see its own excludeFromRecents tab tasks).
    private val _tabsList = MutableSharedFlow<IntArray>(extraBufferCapacity = 4)
    val tabsList = _tabsList.asSharedFlow()
    fun emitTabsList(taskIds: IntArray) { _tabsList.tryEmit(taskIds) }
}
