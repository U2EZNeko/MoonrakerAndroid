package com.moonraker.android.ui.firmware

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moonraker.android.data.moonraker.MoonrakerRepository
import com.moonraker.android.data.settings.PrinterConfig
import com.moonraker.android.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FirmwareUiState(
    val printers: List<PrinterConfig> = emptyList(),
    val activePrinter: PrinterConfig? = null,
    val configUrl: String? = null,
)

class FirmwareViewModel(
    private val settingsRepository: SettingsRepository,
    private val moonrakerRepository: MoonrakerRepository,
) : ViewModel() {
    val uiState: StateFlow<FirmwareUiState> = settingsRepository.settings
        .map { settings ->
            val printer = settings.activePrinter
            FirmwareUiState(
                printers = settings.printers,
                activePrinter = printer,
                configUrl = printer?.let { moonrakerRepository.firmwareConfigUrl(it) },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FirmwareUiState())

    fun setActivePrinter(id: String) {
        viewModelScope.launch { settingsRepository.setActivePrinter(id) }
    }

    companion object {
        fun factory(
            settingsRepository: SettingsRepository,
            moonrakerRepository: MoonrakerRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return FirmwareViewModel(settingsRepository, moonrakerRepository) as T
            }
        }
    }
}
