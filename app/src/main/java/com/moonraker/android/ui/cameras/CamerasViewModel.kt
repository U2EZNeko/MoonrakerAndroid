package com.moonraker.android.ui.cameras

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moonraker.android.data.moonraker.MoonrakerRepository
import com.moonraker.android.data.moonraker.WebcamEntry
import com.moonraker.android.data.settings.PrinterConfig
import com.moonraker.android.data.settings.SettingsRepository
import com.moonraker.android.data.settings.moveItem
import com.moonraker.android.data.settings.orderKey
import com.moonraker.android.data.settings.sortedByCameraOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

data class CamerasUiState(
    val printers: List<PrinterConfig> = emptyList(),
    val activePrinter: PrinterConfig? = null,
    val webcams: List<WebcamEntry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val printActive: Boolean = false,
    val streamFps: Int = 30,
    val preferSnapshot: Boolean = false,
    val keepScreenOn: Boolean = true,
    val connected: Boolean = false,
    val connectionMessage: String = "Disconnected",
)

class CamerasViewModel(
    private val settingsRepository: SettingsRepository,
    private val moonrakerRepository: MoonrakerRepository,
) : ViewModel() {
    val httpClient: OkHttpClient = moonrakerRepository.api.httpClient()

    private val rawWebcams = MutableStateFlow<List<WebcamEntry>>(emptyList())
    private val loading = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CamerasUiState> = combine(
        combine(
            settingsRepository.settings,
            rawWebcams,
            loading,
            error,
            moonrakerRepository.printStatus,
        ) { settings, cams, isLoading, err, printStatus ->
            Quint(settings, cams, isLoading, err, printStatus)
        },
        moonrakerRepository.connection,
    ) { quint, connection ->
        val (settings, cams, isLoading, err, printStatus) = quint
        val printerId = settings.activePrinter?.id.orEmpty()
        val order = settings.cameraOrderByPrinter[printerId].orEmpty()
        CamerasUiState(
            printers = settings.printers,
            activePrinter = settings.activePrinter,
            webcams = cams.sortedByCameraOrder(order),
            loading = isLoading,
            error = err,
            printActive = printStatus.state in setOf("printing", "paused"),
            streamFps = settings.streamFps,
            preferSnapshot = settings.preferSnapshotStreams,
            keepScreenOn = settings.keepScreenOnCameras,
            connected = connection.connected,
            connectionMessage = connection.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CamerasUiState())

    private data class Quint(
        val settings: com.moonraker.android.data.settings.AppSettings,
        val cams: List<WebcamEntry>,
        val isLoading: Boolean,
        val err: String?,
        val printStatus: com.moonraker.android.data.moonraker.PrintStatus,
    )

    init {
        viewModelScope.launch {
            moonrakerRepository.activePrinter.collect { printer ->
                if (printer == null) {
                    rawWebcams.value = emptyList()
                    error.value = null
                } else {
                    refresh(printer)
                }
            }
        }
    }

    fun setActivePrinter(id: String) {
        viewModelScope.launch { settingsRepository.setActivePrinter(id) }
    }

    fun moveCamera(index: Int, delta: Int) {
        val state = uiState.value
        val printer = state.activePrinter ?: return
        val cams = state.webcams
        val to = index + delta
        if (index !in cams.indices || to !in cams.indices) return
        val keys = cams.map { it.orderKey() }.moveItem(index, to)
        viewModelScope.launch {
            settingsRepository.setCameraOrder(printer.id, keys)
        }
    }

    fun resolveUrl(pathOrUrl: String): String {
        val printer = uiState.value.activePrinter ?: return pathOrUrl
        return moonrakerRepository.resolveUrl(printer, pathOrUrl)
    }

    private suspend fun refresh(printer: PrinterConfig) {
        loading.value = true
        error.value = null
        moonrakerRepository.listWebcams(printer)
            .onSuccess { rawWebcams.value = it }
            .onFailure {
                rawWebcams.value = emptyList()
                error.value = it.message ?: "Failed to load cameras"
            }
        loading.value = false
    }

    companion object {
        fun factory(
            settingsRepository: SettingsRepository,
            moonrakerRepository: MoonrakerRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return CamerasViewModel(settingsRepository, moonrakerRepository) as T
            }
        }
    }
}
