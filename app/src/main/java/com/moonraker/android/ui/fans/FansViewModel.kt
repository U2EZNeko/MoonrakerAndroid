package com.moonraker.android.ui.fans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moonraker.android.data.moonraker.FanReading
import com.moonraker.android.data.moonraker.MoonrakerRepository
import com.moonraker.android.data.settings.PrinterConfig
import com.moonraker.android.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FansUiState(
    val printers: List<PrinterConfig> = emptyList(),
    val activePrinter: PrinterConfig? = null,
    val fans: List<FanReading> = emptyList(),
    val connected: Boolean = false,
    val connectionMessage: String = "Disconnected",
    val message: String? = null,
    val pendingObject: String? = null,
)

class FansViewModel(
    private val settingsRepository: SettingsRepository,
    private val moonrakerRepository: MoonrakerRepository,
) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)
    private val pendingObject = MutableStateFlow<String?>(null)

    val uiState: StateFlow<FansUiState> = combine(
        settingsRepository.settings,
        moonrakerRepository.connection,
        moonrakerRepository.fans,
        message,
        pendingObject,
    ) { settings, connection, fans, msg, pending ->
        FansUiState(
            printers = settings.printers,
            activePrinter = settings.activePrinter,
            fans = fans,
            connected = connection.connected,
            connectionMessage = connection.message,
            message = msg,
            pendingObject = pending,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FansUiState())

    fun setActivePrinter(id: String) {
        viewModelScope.launch { settingsRepository.setActivePrinter(id) }
    }

    fun setFanSpeed(objectName: String, speed: Float) {
        val printer = uiState.value.activePrinter ?: return
        viewModelScope.launch {
            pendingObject.value = objectName
            message.value = null
            moonrakerRepository.setFanSpeed(printer, objectName, speed)
                .onSuccess {
                    message.value = null
                }
                .onFailure {
                    message.value = it.message ?: "Failed to set fan speed"
                }
            pendingObject.value = null
        }
    }

    companion object {
        fun factory(
            settingsRepository: SettingsRepository,
            moonrakerRepository: MoonrakerRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return FansViewModel(settingsRepository, moonrakerRepository) as T
            }
        }
    }
}
