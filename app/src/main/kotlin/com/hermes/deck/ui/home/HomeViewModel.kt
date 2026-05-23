package com.hermes.deck.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hermes.deck.data.AppInfo
import com.hermes.deck.data.RecentAppsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val recentApps: List<AppInfo> = emptyList(),
    val hasUsagePermission: Boolean = true
)

class HomeViewModel(private val repo: RecentAppsRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val hasPermission = repo.hasUsagePermission()
            _uiState.update { it.copy(hasUsagePermission = hasPermission) }
            if (hasPermission) {
                repo.getRecentApps().collect { apps ->
                    _uiState.update { it.copy(recentApps = apps) }
                }
            }
        }
    }

    fun dismissCard(app: AppInfo) {
        _uiState.update { state ->
            state.copy(recentApps = state.recentApps.filter { it.packageName != app.packageName })
        }
    }

    companion object {
        fun factory(context: Context) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return HomeViewModel(
                    RecentAppsRepository(context.applicationContext)
                ) as T
            }
        }
    }
}
