package com.moonraker.android.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moonraker.android.data.moonraker.FanReading
import com.moonraker.android.data.moonraker.GcodePathPreview
import com.moonraker.android.data.moonraker.HeaterReading
import com.moonraker.android.data.moonraker.LedReading
import com.moonraker.android.data.moonraker.MoonrakerRepository
import com.moonraker.android.data.moonraker.PrintStatus
import com.moonraker.android.data.moonraker.TemperatureHistorySeries
import com.moonraker.android.data.moonraker.TemperatureStoreSensor
import com.moonraker.android.data.moonraker.WebcamEntry
import com.moonraker.android.data.settings.DashboardGrid
import com.moonraker.android.data.settings.DashboardTile
import com.moonraker.android.data.settings.DashboardTileType
import com.moonraker.android.data.settings.PrinterConfig
import com.moonraker.android.data.settings.SettingsRepository
import com.moonraker.android.data.settings.TempUnit
import com.moonraker.android.data.settings.orderKey
import com.moonraker.android.data.settings.sortedByCameraOrder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.UUID

data class CatalogOption(
    val type: String,
    val ref: String = "",
    val title: String,
    val subtitle: String = "",
    val category: String = "Other",
)

enum class QuickControl {
    SPEED,
    FLOW,
    SPEED_FLOW,
}

data class DashboardUiState(
    val printers: List<PrinterConfig> = emptyList(),
    val activePrinter: PrinterConfig? = null,
    val tiles: List<DashboardTile> = DashboardGrid.defaultLayout,
    val printStatus: PrintStatus = PrintStatus(),
    val heaters: List<HeaterReading> = emptyList(),
    val fans: List<FanReading> = emptyList(),
    val leds: List<LedReading> = emptyList(),
    val webcams: List<WebcamEntry> = emptyList(),
    val catalog: List<CatalogOption> = emptyList(),
    val streamFps: Int = 30,
    val preferSnapshot: Boolean = false,
    val tempUnit: TempUnit = TempUnit.CELSIUS,
    val connected: Boolean = false,
    val connectionMessage: String = "Disconnected",
    val message: String? = null,
    val isError: Boolean = false,
    val keepScreenOn: Boolean = true,
    val refreshing: Boolean = false,
    val cameraEpoch: Int = 0,
    val gcodePreview: GcodePathPreview? = null,
    val gcodePreviewLoading: Boolean = false,
    val gcodePreviewFilename: String = "",
    val editing: Boolean = false,
    val selectedTileId: String? = null,
    val showPicker: Boolean = false,
    val quickControl: QuickControl? = null,
    val tempHistory: List<TemperatureHistorySeries> = emptyList(),
    val heaterSparklines: Map<String, List<Float>> = emptyMap(),
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
    private val gcodePreview = MutableStateFlow<GcodePathPreview?>(null)
    private val gcodePreviewLoading = MutableStateFlow(false)
    private val gcodePreviewFilename = MutableStateFlow("")
    private val editing = MutableStateFlow(false)
    private val selectedTileId = MutableStateFlow<String?>(null)
    private val showPicker = MutableStateFlow(false)
    private val quickControl = MutableStateFlow<QuickControl?>(null)
    private val tempHistory = MutableStateFlow<List<TemperatureHistorySeries>>(emptyList())
    private val heaterSparklines = MutableStateFlow<Map<String, List<Float>>>(emptyMap())
    private var tempRefreshJob: Job? = null

    val uiState: StateFlow<DashboardUiState> = combine(
        combine(
            combine(
                settingsRepository.settings,
                moonrakerRepository.printStatus,
                moonrakerRepository.heaters,
                moonrakerRepository.fans,
                rawWebcams,
            ) { settings, print, heaters, fans, cams ->
                CoreBase(settings, print, heaters, fans, cams)
            },
            moonrakerRepository.leds,
        ) { base, leds -> Core(base.settings, base.printStatus, base.heaters, base.fans, leds, base.cams) },
        moonrakerRepository.connection,
        combine(message, isError, refreshing, cameraEpoch) { msg, err, refresh, epoch ->
            Meta(msg, err, refresh, epoch)
        },
        combine(gcodePreview, gcodePreviewLoading, gcodePreviewFilename) { preview, loading, name ->
            GcodeMeta(preview, loading, name)
        },
        combine(
            combine(editing, selectedTileId, showPicker, quickControl) { edit, selected, picker, control ->
                EditMeta(edit, selected, picker, control)
            },
            tempHistory,
            heaterSparklines,
        ) { edit, history, sparks ->
            Extra(edit, history, sparks)
        },
    ) { core, connection, meta, gcode, extra ->
        val (settings, print, heaters, fans, leds, cams) = core
        val printerId = settings.activePrinter?.id.orEmpty()
        val order = settings.cameraOrderByPrinter[printerId].orEmpty()
        val sortedCams = cams.sortedByCameraOrder(order)
        val tiles = settings.dashboardLayout
        DashboardUiState(
            printers = settings.printers,
            activePrinter = settings.activePrinter,
            tiles = tiles,
            printStatus = print,
            heaters = heaters,
            fans = fans,
            leds = leds,
            webcams = sortedCams,
            catalog = buildCatalog(tiles, sortedCams, heaters, fans, leds),
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
            gcodePreview = gcode.preview,
            gcodePreviewLoading = gcode.loading,
            gcodePreviewFilename = gcode.filename,
            editing = extra.edit.editing,
            selectedTileId = extra.edit.selected,
            showPicker = extra.edit.showPicker,
            quickControl = extra.edit.quickControl,
            tempHistory = extra.history,
            heaterSparklines = extra.sparks,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    private data class CoreBase(
        val settings: com.moonraker.android.data.settings.AppSettings,
        val printStatus: PrintStatus,
        val heaters: List<HeaterReading>,
        val fans: List<FanReading>,
        val cams: List<WebcamEntry>,
    )

    private data class Core(
        val settings: com.moonraker.android.data.settings.AppSettings,
        val printStatus: PrintStatus,
        val heaters: List<HeaterReading>,
        val fans: List<FanReading>,
        val leds: List<LedReading>,
        val cams: List<WebcamEntry>,
    )

    private data class Meta(
        val msg: String?,
        val err: Boolean,
        val refreshing: Boolean,
        val cameraEpoch: Int,
    )

    private data class GcodeMeta(
        val preview: GcodePathPreview?,
        val loading: Boolean,
        val filename: String,
    )

    private data class EditMeta(
        val editing: Boolean,
        val selected: String?,
        val showPicker: Boolean,
        val quickControl: QuickControl?,
    )

    private data class Extra(
        val edit: EditMeta,
        val history: List<TemperatureHistorySeries>,
        val sparks: Map<String, List<Float>>,
    )

    init {
        viewModelScope.launch {
            moonrakerRepository.activePrinter.collect { printer ->
                gcodePreview.value = null
                gcodePreviewFilename.value = ""
                tempHistory.value = emptyList()
                heaterSparklines.value = emptyMap()
                if (printer == null) {
                    rawWebcams.value = emptyList()
                } else {
                    moonrakerRepository.listWebcams(printer)
                        .onSuccess { rawWebcams.value = it }
                        .onFailure { rawWebcams.value = emptyList() }
                }
            }
        }
        viewModelScope.launch {
            combine(
                settingsRepository.settings.map { it.dashboardLayout },
                moonrakerRepository.activePrinter,
                moonrakerRepository.printStatus.map { it.filename to it.state },
            ) { tiles, printer, job ->
                Triple(tiles, printer, job)
            }.collect { (tiles, printer, job) ->
                val (filename, state) = job
                val needsGcode = tiles.any { it.type == DashboardTileType.GCODE_PREVIEW }
                if (!needsGcode || printer == null) return@collect
                val active = filename.isNotBlank() && state in setOf("printing", "paused", "complete")
                if (!active) {
                    gcodePreview.value = null
                    gcodePreviewFilename.value = ""
                    return@collect
                }
                if (filename == gcodePreviewFilename.value && gcodePreview.value != null) return@collect
                loadGcodePreview(printer, filename)
            }
        }
        viewModelScope.launch {
            combine(
                settingsRepository.settings.map { it.dashboardLayout },
                moonrakerRepository.activePrinter,
            ) { tiles, printer -> tiles to printer }.collect { (tiles, printer) ->
                tempRefreshJob?.cancel()
                val needsChart = tiles.any {
                    it.type == DashboardTileType.TEMP_CHART || it.type == DashboardTileType.HEATER
                }
                if (!needsChart || printer == null) {
                    if (tiles.none { it.type == DashboardTileType.TEMP_CHART }) {
                        tempHistory.value = emptyList()
                    }
                    return@collect
                }
                loadTempStore(printer)
                tempRefreshJob = viewModelScope.launch {
                    while (isActive) {
                        delay(60_000)
                        loadTempStore(printer)
                    }
                }
            }
        }
        viewModelScope.launch {
            var lastStamp = 0L
            moonrakerRepository.heaters.collect { heaters ->
                val now = System.currentTimeMillis()
                if (now - lastStamp < 900) return@collect
                lastStamp = now
                appendSparklines(heaters)
                appendLiveHistory(heaters)
            }
        }
    }

    fun setActivePrinter(id: String) {
        viewModelScope.launch { settingsRepository.setActivePrinter(id) }
    }

    fun setEditing(value: Boolean) {
        editing.value = value
        if (!value) {
            selectedTileId.value = null
            showPicker.value = false
        } else {
            quickControl.value = null
        }
    }

    fun selectTile(id: String?) {
        selectedTileId.value = id
    }

    fun openPicker() {
        showPicker.value = true
    }

    fun dismissPicker() {
        showPicker.value = false
    }

    fun openQuickControl(control: QuickControl) {
        if (editing.value) return
        quickControl.value = control
    }

    fun dismissQuickControl() {
        quickControl.value = null
    }

    fun addFromCatalog(option: CatalogOption) {
        val tiles = uiState.value.tiles
        if (option.type in DashboardTileType.singletons &&
            tiles.any { it.type == option.type }
        ) {
            message.value = "Already on dashboard"
            isError.value = true
            return
        }
        if (option.ref.isNotBlank() &&
            tiles.any { it.type == option.type && it.ref == option.ref }
        ) {
            message.value = "Already on dashboard"
            isError.value = true
            return
        }
        val w = DashboardTileType.defaultColSpan(option.type)
        val h = DashboardTileType.defaultRowSpan(option.type)
        val slot = DashboardGrid.findFreeSlot(tiles, w, h) ?: run {
            message.value = "No free space"
            isError.value = true
            return
        }
        val tile = DashboardTile(
            id = UUID.randomUUID().toString(),
            type = option.type,
            col = slot.first,
            row = slot.second,
            colSpan = w,
            rowSpan = h,
            ref = option.ref,
        )
        persist(tiles + tile)
        selectedTileId.value = tile.id
        showPicker.value = false
        message.value = "Added ${option.title}"
        isError.value = false
    }

    fun removeSelected() {
        val id = selectedTileId.value ?: return
        removeTile(id)
    }

    fun removeTile(id: String) {
        persist(uiState.value.tiles.filterNot { it.id == id })
        if (selectedTileId.value == id) selectedTileId.value = null
    }

    fun moveSelected(dCol: Int, dRow: Int) {
        val id = selectedTileId.value ?: return
        val tile = uiState.value.tiles.firstOrNull { it.id == id } ?: return
        moveTileTo(id, tile.col + dCol, (tile.row + dRow).coerceAtLeast(0))
    }

    fun resizeSelected(dColSpan: Int, dRowSpan: Int) {
        val id = selectedTileId.value ?: return
        val tile = uiState.value.tiles.firstOrNull { it.id == id } ?: return
        resizeTileTo(
            id,
            (tile.colSpan + dColSpan).coerceIn(1, DashboardGrid.MAX_COL_SPAN),
            (tile.rowSpan + dRowSpan).coerceIn(1, DashboardGrid.MAX_ROW_SPAN),
        )
    }

    fun moveTileTo(id: String, col: Int, row: Int) {
        val tiles = uiState.value.tiles
        if (tiles.none { it.id == id }) return
        persist(DashboardGrid.moveWithPush(tiles, id, col, row))
        selectedTileId.value = id
    }

    fun resizeTileTo(id: String, colSpan: Int, rowSpan: Int) {
        val tiles = uiState.value.tiles
        if (tiles.none { it.id == id }) return
        persist(DashboardGrid.resizeWithPush(tiles, id, colSpan, rowSpan))
        selectedTileId.value = id
    }

    fun resetLayout() {
        viewModelScope.launch {
            settingsRepository.resetDashboardLayout()
            selectedTileId.value = null
            message.value = "Dashboard reset"
            isError.value = false
        }
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
            val job = uiState.value.printStatus
            if (uiState.value.tiles.any { it.type == DashboardTileType.GCODE_PREVIEW } &&
                job.filename.isNotBlank()
            ) {
                loadGcodePreview(printer, job.filename)
            }
            if (uiState.value.tiles.any {
                    it.type == DashboardTileType.TEMP_CHART || it.type == DashboardTileType.HEATER
                }
            ) {
                loadTempStore(printer)
            }
            refreshing.value = false
        }
    }

    fun resolveUrl(pathOrUrl: String): String {
        val printer = uiState.value.activePrinter ?: return pathOrUrl
        return moonrakerRepository.resolveUrl(printer, pathOrUrl)
    }

    fun cameraFor(tile: DashboardTile): WebcamEntry? {
        val cams = uiState.value.webcams
        if (tile.ref.isBlank()) return cams.firstOrNull()
        return cams.firstOrNull { it.orderKey() == tile.ref || it.name == tile.ref || it.uid == tile.ref }
            ?: cams.firstOrNull()
    }

    fun heaterFor(tile: DashboardTile): HeaterReading? =
        uiState.value.heaters.firstOrNull { it.name == tile.ref }

    fun fanFor(tile: DashboardTile): FanReading? =
        uiState.value.fans.firstOrNull {
            it.objectName == tile.ref || it.displayName == tile.ref
        }

    fun ledFor(tile: DashboardTile): LedReading? =
        uiState.value.leds.firstOrNull {
            it.objectName == tile.ref || it.displayName == tile.ref
        }

    fun pausePrint() = runAction("Pause requested") { moonrakerRepository.pausePrint(it) }
    fun resumePrint() = runAction("Resume requested") { moonrakerRepository.resumePrint(it) }
    fun cancelPrint() = runAction("Cancel requested") { moonrakerRepository.cancelPrint(it) }
    fun emergencyStop() = runAction("Emergency stop sent") { moonrakerRepository.emergencyStop(it) }
    fun setSpeedPercent(percent: Int) = runAction("Speed ${percent}%") {
        moonrakerRepository.runGcode(it, "M220 S$percent")
    }
    fun setFlowPercent(percent: Int) = runAction("Flow ${percent}%") {
        moonrakerRepository.runGcode(it, "M221 S$percent")
    }
    fun setFanSpeed(objectName: String, speed: Float) = runAction("Fan updated") {
        moonrakerRepository.setFanSpeed(it, objectName, speed)
    }

    fun setLedOn(objectName: String, on: Boolean) =
        runAction(if (on) "LED on" else "LED off") {
            moonrakerRepository.setLedOn(it, objectName, on)
        }

    private fun updateSelected(transform: (DashboardTile) -> DashboardTile) {
        val id = selectedTileId.value ?: return
        val tiles = uiState.value.tiles
        val current = tiles.firstOrNull { it.id == id } ?: return
        val next = transform(current)
        if (next == current) return
        persist(tiles.map { if (it.id == id) next else it })
    }

    private fun persist(tiles: List<DashboardTile>) {
        viewModelScope.launch {
            settingsRepository.setDashboardLayout(DashboardGrid.normalize(tiles))
        }
    }

    private suspend fun loadGcodePreview(printer: PrinterConfig, filename: String) {
        gcodePreviewLoading.value = true
        gcodePreviewFilename.value = filename
        moonrakerRepository.downloadGcodePreview(printer, filename)
            .onSuccess { gcodePreview.value = it }
            .onFailure { gcodePreview.value = null }
        gcodePreviewLoading.value = false
    }

    private suspend fun loadTempStore(printer: PrinterConfig) {
        moonrakerRepository.temperatureStore(printer)
            .onSuccess { map ->
                tempHistory.value = map.entries
                    .map { (name, sensor) -> sensor.toHistory(name) }
                    .filter { it.temperatures.isNotEmpty() }
                    .sortedBy { it.name }
            }
    }

    private fun appendSparklines(heaters: List<HeaterReading>) {
        if (heaters.isEmpty()) return
        val current = heaterSparklines.value.toMutableMap()
        heaters.forEach { heater ->
            val prev = current[heater.name].orEmpty()
            current[heater.name] = (prev + heater.temperature).takeLast(60)
        }
        heaterSparklines.value = current
    }

    private fun appendLiveHistory(heaters: List<HeaterReading>) {
        if (heaters.isEmpty()) return
        val current = tempHistory.value
        if (current.isEmpty()) return
        val byName = heaters.associateBy { it.name }
        tempHistory.value = current.map { series ->
            val live = byName[series.name] ?: return@map series
            series.copy(
                temperatures = (series.temperatures + live.temperature).takeLast(1200),
                targets = (series.targets + live.target).takeLast(1200),
                powers = (series.powers + live.power).takeLast(1200),
                current = live.temperature,
                target = live.target,
                power = live.power,
            )
        }
    }

    private fun buildCatalog(
        tiles: List<DashboardTile>,
        cameras: List<WebcamEntry>,
        heaters: List<HeaterReading>,
        fans: List<FanReading>,
        leds: List<LedReading>,
    ): List<CatalogOption> {
        val presentTypes = tiles.map { it.type }.toSet()
        val presentRefs = tiles.map { it.type to it.ref }.toSet()
        val options = mutableListOf<CatalogOption>()

        fun addSingleton(type: String, subtitle: String = "") {
            if (type !in presentTypes) {
                options += CatalogOption(
                    type = type,
                    title = DashboardTileType.label(type),
                    subtitle = subtitle,
                    category = DashboardTileType.category(type),
                )
            }
        }

        addSingleton(DashboardTileType.JOB_SUMMARY, "File, progress, ETA, layer")
        addSingleton(DashboardTileType.PRINT_PROGRESS, "Bar + pause/cancel")
        addSingleton(DashboardTileType.PROGRESS_RING, "Circular progress")
        addSingleton(DashboardTileType.STAT_ETA, "Time remaining")
        addSingleton(DashboardTileType.STAT_LAYER, "Current / total layer")
        addSingleton(DashboardTileType.STAT_FILAMENT, "Filament used")
        addSingleton(DashboardTileType.STAT_TIME, "Elapsed print time")
        addSingleton(DashboardTileType.CONTROL, "Pause, cancel, E-stop")
        addSingleton(DashboardTileType.GCODE_PREVIEW, "Live toolpath")

        addSingleton(DashboardTileType.TEMP_CHART, "Multi-sensor history graph")
        addSingleton(DashboardTileType.TEMPS, "All heaters list")
        addSingleton(DashboardTileType.HEATERS_STRIP, "Compact heater strip")

        cameras.forEach { cam ->
            val key = cam.orderKey()
            if (DashboardTileType.CAMERA to key !in presentRefs) {
                options += CatalogOption(
                    type = DashboardTileType.CAMERA,
                    ref = key,
                    title = cam.name.ifBlank { "Camera" },
                    subtitle = "Live camera",
                    category = DashboardTileType.category(DashboardTileType.CAMERA),
                )
            }
        }
        heaters.forEach { heater ->
            if (DashboardTileType.HEATER to heater.name !in presentRefs) {
                options += CatalogOption(
                    type = DashboardTileType.HEATER,
                    ref = heater.name,
                    title = heater.name.removePrefix("temperature_sensor "),
                    subtitle = "Sparkline heater card",
                    category = DashboardTileType.category(DashboardTileType.HEATER),
                )
            }
        }

        addSingleton(DashboardTileType.FANS_STRIP, "All fans overview")
        fans.forEach { fan ->
            if (DashboardTileType.FAN to fan.objectName !in presentRefs) {
                options += CatalogOption(
                    type = DashboardTileType.FAN,
                    ref = fan.objectName,
                    title = fan.displayName,
                    subtitle = "Fan control",
                    category = DashboardTileType.category(DashboardTileType.FAN),
                )
            }
        }

        addSingleton(DashboardTileType.LEDS_STRIP, "All LEDs · on / off")
        leds.forEach { led ->
            if (DashboardTileType.LED to led.objectName !in presentRefs) {
                options += CatalogOption(
                    type = DashboardTileType.LED,
                    ref = led.objectName,
                    title = led.displayName,
                    subtitle = "On / off",
                    category = DashboardTileType.category(DashboardTileType.LED),
                )
            }
        }

        addSingleton(DashboardTileType.SPEED_FLOW, "Dual gauges")
        addSingleton(DashboardTileType.SPEED_GAUGE, "Speed factor")
        addSingleton(DashboardTileType.FLOW_GAUGE, "Flow factor")
        addSingleton(DashboardTileType.POSITION, "X / Y / Z")
        addSingleton(DashboardTileType.VELOCITY, "Live feedrate")
        addSingleton(DashboardTileType.HOMED, "Homed axes & extruder")
        addSingleton(DashboardTileType.CONNECTION, "Link status")

        return options
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

private fun TemperatureStoreSensor.toHistory(name: String): TemperatureHistorySeries {
    val tempsRaw = temperatures
    val temps = tempsRaw.map { value ->
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
