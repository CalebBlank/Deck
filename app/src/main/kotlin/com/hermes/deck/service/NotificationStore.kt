package com.hermes.deck.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object NotificationStore {

    private data class Entry(val title: String, val text: String)

    private val store = LinkedHashMap<String, Entry>()

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    @Synchronized
    fun post(packageName: String, title: String, text: String) {
        store[packageName] = Entry(title, text)
        _revision.update { it + 1 }
    }

    @Synchronized
    fun remove(packageName: String) {
        if (store.remove(packageName) != null) {
            _revision.update { it + 1 }
        }
    }

    @Synchronized
    fun has(packageName: String): Boolean = store.containsKey(packageName)

    @Synchronized
    fun clear() {
        store.clear()
        _revision.update { it + 1 }
    }
}
