package com.moonraker.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moonraker.android.data.moonraker.MoonrakerRepository
import com.moonraker.android.data.settings.AppTabs
import com.moonraker.android.data.settings.DashboardWidgets
import com.moonraker.android.data.settings.PrinterConfig
import com.moonraker.android.data.settings.SettingsRepository
import com.moonraker.android.data.settings.TempUnit
import com.moonraker.android.data.settings.ThemeMode
import com.moonraker.android.data.settings.moveItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val printers: List<PrinterConfig> = emptyList(),
    val activePrinterId: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentArgb: Long = 0xFFFF6B4E,
    val useDynamicColor: Boolean = false,
    val tabOrder: List<String> = AppTabs.defaultOrder,
    val dashboardWidgets: List<String> = DashboardWidgets.defaultOrder,
    val streamFps: Int = 30,
    val preferSnapshotStreams: Boolean = false,
    val tempUnit: TempUnit = TempUnit.CELSIUS,
    val keepScreenOnCameras: Boolean = true,
    val keepScreenOnPrint: Boolean = true,
    val testMessage: String? = null,
    val testIsError: Boolean = false,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val moonrakerRepository: MoonrakerRepository,
) : ViewModel() {
    private val testMessage = MutableStateFlow<String?>(null)
    private val testIsError = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        testMessage,
        testIsError,
    ) { settings, msg, err ->
        SettingsUiState(
            printers = settings.printers,
            activePrinterId = settings.activePrinterId,
            themeMode = settings.themeMode,
            accentArgb = settings.accentColorArgb,
            useDynamicColor = settings.useDynamicColor,
            tabOrder = settings.tabOrder,
            dashboardWidgets = settings.dashboardWidgets,
            streamFps = settings.streamFps,
            preferSnapshotStreams = settings.preferSnapshotStreams,
            tempUnit = settings.tempUnit,
            keepScreenOnCameras = settings.keepScreenOnCameras,
            keepScreenOnPrint = settings.keepScreenOnPrint,
            testMessage = msg,
            testIsError = err,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun newPrinterId(): String = settingsRepository.newPrinterId()

    fun savePrinter(printer: PrinterConfig) {
        viewModelScope.launch { settingsRepository.upsertPrinter(printer) }
    }

    fun deletePrinter(id: String) {
        viewModelScope.launch { settingsRepository.deletePrinter(id) }
    }

    fun setActivePrinter(id: String) {
        viewModelScope.launch { settingsRepository.setActivePrinter(id) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setAccent(argb: Long) {
        viewModelScope.launch { settingsRepository.setAccentColor(argb) }
    }

    fun setUseDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setUseDynamicColor(enabled) }
    }

    fun moveTab(index: Int, delta: Int) {
        val order = uiState.value.tabOrder
        val to = index + delta
        if (index !in order.indices || to !in order.indices) return
        viewModelScope.launch {
            settingsRepository.setTabOrder(order.moveItem(index, to))
        }
    }

    fun setDashboardWidgetEnabled(id: String, enabled: Boolean) {
        val current = uiState.value.dashboardWidgets
        val next = if (enabled) {
            if (id in current) current else current + id
        } else {
            current.filterNot { it == id }
        }
        viewModelScope.launch {
            settingsRepository.setDashboardWidgets(DashboardWidgets.normalizeEnabled(next))
        }
    }

    fun moveDashboardWidget(index: Int, delta: Int) {
        val order = uiState.value.dashboardWidgets
        val to = index + delta
        if (index !in order.indices || to !in order.indices) return
        viewModelScope.launch {
            settingsRepository.setDashboardWidgets(order.moveItem(index, to))
        }
    }

    fun setStreamFps(fps: Int) {
        viewModelScope.launch { settingsRepository.setStreamFps(fps) }
    }

    fun setPreferSnapshotStreams(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPreferSnapshotStreams(enabled) }
    }

    fun setTempUnit(unit: TempUnit) {
        viewModelScope.launch { settingsRepository.setTempUnit(unit) }
    }

    fun setKeepScreenOnCameras(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setKeepScreenOnCameras(enabled) }
    }

    fun setKeepScreenOnPrint(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setKeepScreenOnPrint(enabled) }
    }

    fun testPrinter(printer: PrinterConfig) {
        viewModelScope.launch {
            moonrakerRepository.testConnection(printer)
                .onSuccess {
                    testMessage.value =
                        "OK — Moonraker ${it.moonrakerVersion.ifBlank { "connected" }} (${it.klippyState})"
                    testIsError.value = false
                }
                .onFailure {
                    testMessage.value = it.message ?: "Connection failed"
                    testIsError.value = true
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
                return SettingsViewModel(settingsRepository, moonrakerRepository) as T
            }
        }
    }
}
