package com.moonraker.android.ui.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moonraker.android.data.settings.AppTabs
import com.moonraker.android.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MoreUiState(
    val tabOrder: List<String> = AppTabs.defaultOrder,
    val bottomTabs: List<String> = AppTabs.defaultBottomTabs,
) {
    val hiddenTabs: List<String>
        get() {
            val visible = bottomTabs.toSet()
            return tabOrder.filterNot { it in visible }
        }
}

class MoreViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val uiState: StateFlow<MoreUiState> = settingsRepository.settings
        .map { settings ->
            MoreUiState(
                tabOrder = settings.tabOrder,
                bottomTabs = settings.bottomTabs,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoreUiState())

    fun setBottomTabVisible(route: String, visible: Boolean) {
        if (route == AppTabs.MORE || route !in AppTabs.featureTabs) return
        val current = uiState.value.bottomTabs
        val next = if (visible) {
            if (route in current) current else current + route
        } else {
            current.filterNot { it == route }
        }
        viewModelScope.launch {
            settingsRepository.setBottomTabs(AppTabs.normalizeBottomTabs(next))
        }
    }

    companion object {
        fun factory(settingsRepository: SettingsRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MoreViewModel(settingsRepository) as T
                }
            }
    }
}
