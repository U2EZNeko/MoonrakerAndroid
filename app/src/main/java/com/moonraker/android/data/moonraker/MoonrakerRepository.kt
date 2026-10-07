package com.moonraker.android.data.moonraker

import com.moonraker.android.data.settings.PrinterConfig
import com.moonraker.android.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

class MoonrakerRepository(
    private val settingsRepository: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val api = MoonrakerApiClient()
    val webSocket = MoonrakerWebSocket(api.httpClient(), scope)

    val activePrinter: StateFlow<PrinterConfig?> = settingsRepository.settings
        .map { it.activePrinter }
        .distinctUntilChanged { old, new -> old?.id == new?.id && old == new }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val connection = webSocket.connection
    val heaters: StateFlow<List<HeaterReading>> = webSocket.status
        .map { status -> parseHeaters(status) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val printStatus: StateFlow<PrintStatus> = webSocket.status
        .map { status -> parsePrintStatus(status) }
        .stateIn(scope, SharingStarted.Eagerly, PrintStatus())

    init {
        scope.launch {
            activePrinter.collect { printer ->
                if (printer == null) {
                    webSocket.disconnect()
                } else {
                    webSocket.connect(printer)
                }
            }
        }
    }

    suspend fun testConnection(printer: PrinterConfig) = api.testConnection(printer)

    suspend fun listWebcams(printer: PrinterConfig) = api.listWebcams(printer)

    suspend fun uploadAndStart(
        printer: PrinterConfig,
        file: File,
        startImmediately: Boolean,
    ): Result<UploadedFile> {
        return api.uploadGcode(printer, file, startPrint = startImmediately).mapCatching { item ->
            if (!startImmediately) {
                api.startPrint(printer, item.path).getOrThrow()
            }
            item
        }
    }

    suspend fun cancelPrint(printer: PrinterConfig) = api.cancelPrint(printer)

    suspend fun pausePrint(printer: PrinterConfig) = api.pausePrint(printer)

    suspend fun resumePrint(printer: PrinterConfig) = api.resumePrint(printer)

    suspend fun emergencyStop(printer: PrinterConfig) = api.emergencyStop(printer)

    suspend fun runGcode(printer: PrinterConfig, script: String) = api.runGcode(printer, script)

    suspend fun temperatureStore(printer: PrinterConfig) = api.temperatureStore(printer)

    suspend fun listTimelapses(printer: PrinterConfig) = api.listTimelapses(printer)

    fun fileDownloadUrl(printer: PrinterConfig, relativePath: String) =
        api.fileDownloadUrl(printer, relativePath)

    fun resolveUrl(printer: PrinterConfig, pathOrUrl: String) = api.resolveUrl(printer, pathOrUrl)

    private fun parseHeaters(status: JsonObject): List<HeaterReading> {
        val readings = mutableListOf<HeaterReading>()
        status.forEach { (name, value) ->
            val obj = value as? JsonObject ?: return@forEach
            val temp = obj["temperature"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: return@forEach
            val target = obj["target"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 0f
            val power = obj["power"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 0f
            val isHeater = name.startsWith("extruder") ||
                name == "heater_bed" ||
                name.startsWith("heater_generic") ||
                name.startsWith("temperature_sensor") ||
                name.startsWith("temperature_fan")
            if (isHeater) {
                readings += HeaterReading(
                    name = name,
                    temperature = temp,
                    target = target,
                    power = power,
                )
            }
        }
        return readings.sortedBy { it.name }
    }

    private fun parsePrintStatus(status: JsonObject): PrintStatus {
        val stats = status["print_stats"]?.jsonObject
        val display = status["display_status"]?.jsonObject
        val sdcard = status["virtual_sdcard"]?.jsonObject
        val gcodeMove = status["gcode_move"]?.jsonObject
        val toolhead = status["toolhead"]?.jsonObject
        val motion = status["motion_report"]?.jsonObject
        val info = stats?.get("info")?.jsonObject

        val displayProgress = display?.floatOrNull("progress")
        val sdProgress = sdcard?.floatOrNull("progress")
        val progress = when {
            displayProgress != null && displayProgress > 0f -> displayProgress
            sdProgress != null -> sdProgress
            else -> displayProgress ?: 0f
        }

        val gcodePos = gcodeMove?.get("gcode_position")?.jsonArray
        val livePos = motion?.get("live_position")?.jsonArray

        return PrintStatus(
            filename = stats?.stringOrNull("filename").orEmpty(),
            state = stats?.stringOrNull("state") ?: "standby",
            progress = progress,
            message = stats?.stringOrNull("message").orEmpty(),
            printDurationSec = stats?.floatOrNull("print_duration") ?: 0f,
            totalDurationSec = stats?.floatOrNull("total_duration") ?: 0f,
            filamentUsedMm = stats?.floatOrNull("filament_used") ?: 0f,
            currentLayer = info?.intOrNull("current_layer"),
            totalLayers = info?.intOrNull("total_layer"),
            gcodeX = gcodePos.floatAt(0),
            gcodeY = gcodePos.floatAt(1),
            gcodeZ = gcodePos.floatAt(2),
            liveX = livePos.floatAt(0),
            liveY = livePos.floatAt(1),
            liveZ = livePos.floatAt(2),
            liveVelocity = motion?.floatOrNull("live_velocity"),
            speedFactor = gcodeMove?.floatOrNull("speed_factor") ?: 1f,
            extrudeFactor = gcodeMove?.floatOrNull("extrude_factor") ?: 1f,
            homedAxes = toolhead?.stringOrNull("homed_axes").orEmpty(),
            activeExtruder = toolhead?.stringOrNull("extruder").orEmpty(),
        )
    }
}

private fun JsonObject.intOrNull(key: String): Int? =
    this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        ?: this[key]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()?.toInt()

private fun JsonArray?.floatAt(index: Int): Float? =
    this?.getOrNull(index)?.jsonPrimitive?.contentOrNull?.toFloatOrNull()

