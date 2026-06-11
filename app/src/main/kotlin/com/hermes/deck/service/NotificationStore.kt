package com.hermes.deck.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object NotificationStore {

    private val packages = LinkedHashSet<String>()

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    @Synchronized
    fun post(packageName: String) {
        packages.add(packageName)
        _revision.update { it + 1 }
    }

    @Synchronized
    fun remove(packageName: String) {
        if (packages.remove(packageName)) _revision.update { it + 1 }
    }

    @Synchronized
    fun has(packageName: String): Boolean = packageName in packages

    @Synchronized
    fun clear() {
        packages.clear()
        _revision.update { it + 1 }
    }
}
