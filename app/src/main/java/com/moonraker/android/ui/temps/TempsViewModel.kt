package com.moonraker.android.ui.temps

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moonraker.android.data.moonraker.HeaterReading
import com.moonraker.android.data.moonraker.MoonrakerRepository
import com.moonraker.android.data.moonraker.TemperatureHistorySeries
import com.moonraker.android.data.moonraker.TemperatureStoreSensor
import com.moonraker.android.data.settings.PrinterConfig
import com.moonraker.android.data.settings.SettingsRepository
import com.moonraker.android.data.settings.TempUnit
import com.moonraker.android.data.settings.toDisplayTemp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ChartSeries(
    val name: String,
    val color: Color,
    val temperatures: List<Float>,
    val targets: List<Float>,
    val powers: List<Float>,
    val current: Float,
    val target: Float,
    val power: Float,
    val visible: Boolean = true,
)

data class TempsUiState(
    val printers: List<PrinterConfig> = emptyList(),
    val activePrinter: PrinterConfig? = null,
    val series: List<ChartSeries> = emptyList(),
    val sampleCount: Int = 0,
    val windowSeconds: Int = 0,
    val connected: Boolean = false,
    val connectionMessage: String = "Disconnected",
    val loadingHistory: Boolean = false,
    val showPower: Boolean = true,
    val tempUnit: TempUnit = TempUnit.CELSIUS,
)

private val SeriesPalette = listOf(
    Color(0xFFE53935),
    Color(0xFF1E88E5),
    Color(0xFF43A047),
    Color(0xFFFB8C00),
    Color(0xFF8E24AA),
    Color(0xFF00897B),
    Color(0xFFFDD835),
    Color(0xFF6D4C41),
)

class TempsViewModel(
    private val settingsRepository: SettingsRepository,
    private val moonrakerRepository: MoonrakerRepository,
) : ViewModel() {
    private val storeSeries = MutableStateFlow<List<TemperatureHistorySeries>>(emptyList())
    private val hidden = MutableStateFlow<Set<String>>(emptySet())
    private val showPower = MutableStateFlow(true)
    private val loadingHistory = MutableStateFlow(false)
    private var appendJob: Job? = null
    private var refreshJob: Job? = null
    private var storeCapacity = 1200

    private val chartState = combine(storeSeries, hidden, showPower, loadingHistory) {
            series, hiddenNames, powerOn, loading ->
        ChartInternal(series, hiddenNames, powerOn, loading)
    }

    val uiState: StateFlow<TempsUiState> = combine(
        settingsRepository.settings,
        moonrakerRepository.connection,
        chartState,
    ) { settings, connection, chart ->
        val unit = settings.tempUnit
        val sampleCount = chart.series.maxOfOrNull { it.temperatures.size } ?: 0
        TempsUiState(
            printers = settings.printers,
            activePrinter = settings.activePrinter,
            series = chart.series.mapIndexed { index, item ->
                ChartSeries(
                    name = item.name,
                    color = SeriesPalette[index % SeriesPalette.size],
                    temperatures = item.temperatures.map { it.toDisplayTemp(unit) },
                    targets = item.targets.map { t ->
                        if (t > 0f) t.toDisplayTemp(unit) else 0f
                    },
                    powers = item.powers,
                    current = item.current.toDisplayTemp(unit),
                    target = if (item.target > 0f) item.target.toDisplayTemp(unit) else 0f,
                    power = item.power,
                    visible = item.name !in chart.hiddenNames,
                )
            },
            sampleCount = sampleCount,
            windowSeconds = sampleCount,
            connected = connection.connected,
            connectionMessage = connection.message,
            loadingHistory = chart.loading,
            showPower = chart.powerOn,
            tempUnit = unit,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TempsUiState())

    init {
        viewModelScope.launch {
            moonrakerRepository.activePrinter.collect { printer ->
                appendJob?.cancel()
                refreshJob?.cancel()
                storeSeries.value = emptyList()
                if (printer != null) {
                    loadStore(printer)
                    startLiveAppend()
                    startPeriodicRefresh(printer)
                }
            }
        }
    }

    fun setActivePrinter(id: String) {
        viewModelScope.launch { settingsRepository.setActivePrinter(id) }
    }

    fun toggleSeries(name: String) {
        val current = hidden.value.toMutableSet()
        if (!current.add(name)) current.remove(name)
        hidden.value = current
    }

    fun togglePower() {
        showPower.value = !showPower.value
    }

    fun refreshHistory() {
        val printer = uiState.value.activePrinter ?: return
        viewModelScope.launch { loadStore(printer) }
    }

    private suspend fun loadStore(printer: PrinterConfig) {
        loadingHistory.value = true
        moonrakerRepository.temperatureStore(printer)
            .onSuccess { map ->
                val parsed = map.entries
                    .map { (name, sensor) -> sensor.toHistory(name) }
                    .filter { it.temperatures.isNotEmpty() }
                    .sortedBy { it.name }
                storeCapacity = parsed.maxOfOrNull { it.temperatures.size }?.coerceAtLeast(1200) ?: 1200
                storeSeries.value = parsed
            }
        loadingHistory.value = false
    }

    private fun startPeriodicRefresh(printer: PrinterConfig) {
        refreshJob = viewModelScope.launch {
            while (isActive) {
                delay(60_000)
                loadStore(printer)
            }
        }
    }

    private fun startLiveAppend() {
        appendJob = viewModelScope.launch {
            var lastStamp = 0L
            moonrakerRepository.heaters.collect { heaters ->
                val now = System.currentTimeMillis()
                if (now - lastStamp < 900) return@collect
                lastStamp = now
                appendLive(heaters)
            }
        }
    }

    private fun appendLive(heaters: List<HeaterReading>) {
        if (heaters.isEmpty()) return
        val current = storeSeries.value
        if (current.isEmpty()) return
        val byName = heaters.associateBy { it.name }
        val capacity = storeCapacity
        val updated = current.map { series ->
            val live = byName[series.name]
            val temp = live?.temperature ?: series.temperatures.lastOrNull() ?: 0f
            val target = live?.target ?: series.targets.lastOrNull() ?: 0f
            val power = live?.power ?: series.powers.lastOrNull() ?: 0f
            series.copy(
                temperatures = (series.temperatures + temp).takeLast(capacity),
                targets = (series.targets + target).takeLast(capacity),
                powers = (series.powers + power).takeLast(capacity),
                current = temp,
                target = target,
                power = power,
            )
        }
        val known = updated.map { it.name }.toSet()
        val extras = heaters.filter { it.name !in known }.map { heater ->
            TemperatureHistorySeries(
                name = heater.name,
                temperatures = List(minOf(capacity, updated.firstOrNull()?.temperatures?.size ?: 1)) {
                    heater.temperature
                },
                targets = List(minOf(capacity, updated.firstOrNull()?.temperatures?.size ?: 1)) {
                    heater.target
                },
                powers = List(minOf(capacity, updated.firstOrNull()?.temperatures?.size ?: 1)) {
                    heater.power
                },
                current = heater.temperature,
                target = heater.target,
                power = heater.power,
            )
        }
        storeSeries.value = (updated + extras).sortedBy { it.name }
    }

    companion object {
        fun factory(
            settingsRepository: SettingsRepository,
            moonrakerRepository: MoonrakerRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TempsViewModel(settingsRepository, moonrakerRepository) as T
            }
        }
    }
}

private data class ChartInternal(
    val series: List<TemperatureHistorySeries>,
    val hiddenNames: Set<String>,
    val powerOn: Boolean,
    val loading: Boolean,
)

private fun TemperatureStoreSensor.toHistory(name: String): TemperatureHistorySeries {
    val tempsRaw = temperatures
    val temps = tempsRaw.mapIndexed { index, value ->
        value ?: tempsRaw.asReversed().firstOrNull { it != null } ?: 0f
    }
    val targs = padOrTrim(targets.map { it ?: 0f }, temps.size)
    val pows = padOrTrim(powers.map { it ?: 0f }, temps.size)
    return TemperatureHistorySeries(
        name = name,
        temperatures = temps,
        targets = targs,
        powers = pows,
        current = temps.lastOrNull() ?: 0f,
        target = targs.lastOrNull() ?: 0f,
        power = pows.lastOrNull() ?: 0f,
    )
}

private fun padOrTrim(values: List<Float>, size: Int): List<Float> = when {
    values.size == size -> values
    values.size > size -> values.takeLast(size)
    values.isEmpty() -> List(size) { 0f }
    else -> values + List(size - values.size) { values.last() }
}
