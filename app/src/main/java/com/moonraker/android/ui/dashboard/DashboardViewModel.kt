package com.moonraker.android.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moonraker.android.data.moonraker.HeaterReading
import com.moonraker.android.data.moonraker.MoonrakerRepository
import com.moonraker.android.data.moonraker.PrintStatus
import com.moonraker.android.data.moonraker.WebcamEntry
import com.moonraker.android.data.settings.DashboardWidgets
import com.moonraker.android.data.settings.PrinterConfig
import com.moonraker.android.data.settings.SettingsRepository
import com.moonraker.android.data.settings.TempUnit
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

data class DashboardUiState(
    val printers: List<PrinterConfig> = emptyList(),
    val activePrinter: PrinterConfig? = null,
    val widgets: List<String> = DashboardWidgets.defaultOrder,
    val availableWidgets: List<String> = DashboardWidgets.all,
    val printStatus: PrintStatus = PrintStatus(),
    val heaters: List<HeaterReading> = emptyList(),
    val webcams: List<WebcamEntry> = emptyList(),
    val selectedCamera: WebcamEntry? = null,
    val dashboardCameraKey: String = "",
    val streamFps: Int = 30,
    val preferSnapshot: Boolean = false,
    val tempUnit: TempUnit = TempUnit.CELSIUS,
    val connected: Boolean = false,
    val connectionMessage: String = "Disconnected",
    val message: String? = null,
    val isError: Boolean = false,
    val keepScreenOn: Boolean = true,
    val refreshing: Boolean = false,
    /** Bumped on refresh so the camera player remounts. */
    val cameraEpoch: Int = 0,
)

class DashboardViewModel(
    private val settingsRepository: SettingsRepository,
    private val moonrakerRepository: MoonrakerRepository,
) : ViewModel() {
    val httpClient: OkHttpClient = moonrakerRepository.api.httpClient()

    private val rawWebcams = MutableStateFlow<List<WebcamEntry>>(emptyList())
    private val message = MutableStateFlow<String?>(null)
    private val isError = MutableStateFlow(false)
    private val refreshing = MutableStateFlow(false)
    private val cameraEpoch = MutableStateFlow(0)

    val uiState: StateFlow<DashboardUiState> = combine(
        combine(
            settingsRepository.settings,
            moonrakerRepository.printStatus,
            moonrakerRepository.heaters,
            rawWebcams,
            moonrakerRepository.connection,
        ) { settings, print, heaters, cams, connection ->
            Quint(settings, print, heaters, cams, connection)
        },
        combine(message, isError, refreshing, cameraEpoch) { msg, err, refresh, epoch ->
            Meta(msg, err, refresh, epoch)
        },
    ) { quint, meta ->
        val (settings, print, heaters, cams, connection) = quint
        val printerId = settings.activePrinter?.id.orEmpty()
        val order = settings.cameraOrderByPrinter[printerId].orEmpty()
        val sortedCams = cams.sortedByCameraOrder(order)
        val cameraKey = settings.dashboardCameraKey
        val selected = sortedCams.firstOrNull { it.orderKey() == cameraKey || it.name == cameraKey }
            ?: sortedCams.firstOrNull()
        DashboardUiState(
            printers = settings.printers,
            activePrinter = settings.activePrinter,
            widgets = settings.dashboardWidgets,
            availableWidgets = DashboardWidgets.all,
            printStatus = print,
            heaters = heaters,
            webcams = sortedCams,
            selectedCamera = selected,
            dashboardCameraKey = cameraKey,
            streamFps = settings.streamFps,
            preferSnapshot = settings.preferSnapshotStreams,
            tempUnit = settings.tempUnit,
            connected = connection.connected,
            connectionMessage = connection.message,
            message = meta.msg,
            isError = meta.err,
            keepScreenOn = settings.keepScreenOnCameras || settings.keepScreenOnPrint,
            refreshing = meta.refreshing,
            cameraEpoch = meta.cameraEpoch,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    private data class Meta(
        val msg: String?,
        val err: Boolean,
        val refreshing: Boolean,
        val cameraEpoch: Int,
    )

    private data class Quint(
        val settings: com.moonraker.android.data.settings.AppSettings,
        val printStatus: PrintStatus,
        val heaters: List<HeaterReading>,
        val cams: List<WebcamEntry>,
        val connection: com.moonraker.android.data.moonraker.ConnectionState,
    )

    init {
        viewModelScope.launch {
            moonrakerRepository.activePrinter.collect { printer ->
                if (printer == null) {
                    rawWebcams.value = emptyList()
                } else {
                    moonrakerRepository.listWebcams(printer)
                        .onSuccess { rawWebcams.value = it }
                        .onFailure { rawWebcams.value = emptyList() }
                }
            }
        }
    }

    fun setActivePrinter(id: String) {
        viewModelScope.launch { settingsRepository.setActivePrinter(id) }
    }

    fun setWidgetEnabled(id: String, enabled: Boolean) {
        val current = uiState.value.widgets
        val next = if (enabled) {
            if (id in current) current else current + id
        } else {
            current.filterNot { it == id }
        }
        viewModelScope.launch {
            settingsRepository.setDashboardWidgets(DashboardWidgets.normalizeEnabled(next))
        }
    }

    fun moveWidget(index: Int, delta: Int) {
        val order = uiState.value.widgets
        val to = index + delta
        if (index !in order.indices || to !in order.indices) return
        viewModelScope.launch {
            settingsRepository.setDashboardWidgets(order.moveItem(index, to))
        }
    }

    fun setDashboardCamera(key: String) {
        viewModelScope.launch { settingsRepository.setDashboardCameraKey(key) }
    }

    fun refresh() {
        val printer = uiState.value.activePrinter ?: return
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            message.value = null
            moonrakerRepository.listWebcams(printer)
                .onSuccess {
                    rawWebcams.value = it
                    isError.value = false
                }
                .onFailure {
                    message.value = it.message ?: "Refresh failed"
                    isError.value = true
                }
            cameraEpoch.value = cameraEpoch.value + 1
            refreshing.value = false
        }
    }

    fun resolveUrl(pathOrUrl: String): String {
        val printer = uiState.value.activePrinter ?: return pathOrUrl
        return moonrakerRepository.resolveUrl(printer, pathOrUrl)
    }

    fun pausePrint() = runAction("Pause requested") {
        moonrakerRepository.pausePrint(it)
    }

    fun resumePrint() = runAction("Resume requested") {
        moonrakerRepository.resumePrint(it)
    }

    fun cancelPrint() = runAction("Cancel requested") {
        moonrakerRepository.cancelPrint(it)
    }

    fun emergencyStop() = runAction("Emergency stop sent") {
        moonrakerRepository.emergencyStop(it)
    }

    fun setSpeedPercent(percent: Int) = runAction("Speed ${percent}%") {
        moonrakerRepository.runGcode(it, "M220 S$percent")
    }

    fun setFlowPercent(percent: Int) = runAction("Flow ${percent}%") {
        moonrakerRepository.runGcode(it, "M221 S$percent")
    }

    private fun runAction(
        successMessage: String,
        block: suspend (PrinterConfig) -> Result<*>,
    ) {
        val printer = uiState.value.activePrinter ?: return
        viewModelScope.launch {
            block(printer)
                .onSuccess {
                    message.value = successMessage
                    isError.value = false
                }
                .onFailure {
                    message.value = it.message ?: "Action failed"
                    isError.value = true
                }
        }
    }

    companion object {
        fun factory(
            settingsRepository: SettingsRepository,
            moonrakerRepository: MoonrakerRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DashboardViewModel(settingsRepository, moonrakerRepository) as T
            }
        }
    }
}
