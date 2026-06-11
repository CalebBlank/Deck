package com.hermes.deck.data

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable,
    val category: String = "",
    val lastUsed: Long = 0L,
    val activityName: String = "",
    val taskId: Int = -1
) {
    val id: String get() = if (taskId != -1) "$packageName:$taskId" else packageName
}
