package com.hermes.deck.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Singleton bus: accessibility service → ViewModel. Thread-safe via SharedFlow. */
object ForegroundEventBus {
    private val _foregroundPackage = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val foregroundPackage = _foregroundPackage.asSharedFlow()

    fun emit(packageName: String) { _foregroundPackage.tryEmit(packageName) }
}
