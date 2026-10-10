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

    val fans: StateFlow<List<FanReading>> = webSocket.status
        .map { status -> parseFans(status) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val leds: StateFlow<List<LedReading>> = webSocket.status
        .map { status -> parseLeds(status) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val printStatus: StateFlow<PrintStatus> = webSocket.status
        .map { status -> parsePrintStatus(status) }
        .stateIn(scope, SharingStarted.Eagerly, PrintStatus())

    val gcodeResponses = webSocket.events

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

    suspend fun setFanSpeed(printer: PrinterConfig, objectName: String, speed: Float): Result<Unit> {
        val clamped = speed.coerceIn(0f, 1f)
        val fanName = objectName.substringAfter(' ', missingDelimiterValue = objectName)
        val script = "SET_FAN_SPEED FAN=$fanName SPEED=${"%.3f".format(clamped)}"
        return api.runGcode(printer, script)
    }

    suspend fun setLedColor(
        printer: PrinterConfig,
        objectName: String,
        red: Float,
        green: Float,
        blue: Float,
        white: Float = 0f,
    ): Result<Unit> {
        val ledName = objectName.substringAfter(' ', missingDelimiterValue = objectName)
        val script = buildString {
            append("SET_LED LED=$ledName")
            append(" RED=${"%.3f".format(red.coerceIn(0f, 1f))}")
            append(" GREEN=${"%.3f".format(green.coerceIn(0f, 1f))}")
            append(" BLUE=${"%.3f".format(blue.coerceIn(0f, 1f))}")
            append(" WHITE=${"%.3f".format(white.coerceIn(0f, 1f))}")
            append(" TRANSMIT=1")
        }
        return api.runGcode(printer, script)
    }

    /** Simple on/off for chamber/status LEDs (full white when on). */
    suspend fun setLedOn(printer: PrinterConfig, objectName: String, on: Boolean): Result<Unit> {
        val v = if (on) 1f else 0f
        return setLedColor(printer, objectName, red = v, green = v, blue = v, white = v)
    }

    suspend fun temperatureStore(printer: PrinterConfig) = api.temperatureStore(printer)

    suspend fun listTimelapses(printer: PrinterConfig) = api.listTimelapses(printer)

    suspend fun listGcodeFiles(printer: PrinterConfig) = api.listGcodeFiles(printer)

    suspend fun gcodeMetadata(printer: PrinterConfig, filename: String) =
        api.gcodeMetadata(printer, filename)

    suspend fun downloadGcodePreview(printer: PrinterConfig, filename: String) =
        api.downloadGcodePreview(printer, filename)

    suspend fun startPrint(printer: PrinterConfig, filename: String) =
        api.startPrint(printer, filename)

    suspend fun queryPrintJob(printer: PrinterConfig) = api.queryPrintJob(printer)

    suspend fun downloadThumbnailBytes(printer: PrinterConfig, url: String) =
        api.downloadThumbnailBytes(printer, url)

    fun fileDownloadUrl(printer: PrinterConfig, relativePath: String) =
        api.fileDownloadUrl(printer, relativePath)

    fun thumbnailUrl(printer: PrinterConfig, gcodePath: String, relativeThumb: String) =
        api.thumbnailUrl(printer, gcodePath, relativeThumb)

    fun firmwareConfigUrl(printer: PrinterConfig) = api.firmwareConfigUrl(printer)

    fun resolveUrl(printer: PrinterConfig, pathOrUrl: String) = api.resolveUrl(printer, pathOrUrl)

    private fun parseFans(status: JsonObject): List<FanReading> {
        val readings = mutableListOf<FanReading>()
        status.forEach { (name, value) ->
            val obj = value as? JsonObject ?: return@forEach
            val kind = when {
                name == "fan" -> FanKind.PART_COOLING
                name.startsWith("fan_generic") -> FanKind.GENERIC
                name.startsWith("controller_fan") -> FanKind.CONTROLLER
                name.startsWith("heater_fan") -> FanKind.HEATER
                name.startsWith("temperature_fan") -> FanKind.TEMPERATURE
                else -> return@forEach
            }
            val speed = obj.floatOrNull("speed") ?: return@forEach
            val rpm = obj.floatOrNull("rpm")
            readings += FanReading(
                objectName = name,
                displayName = when (kind) {
                    FanKind.PART_COOLING -> "Part cooling"
                    else -> name.substringAfter(' ', missingDelimiterValue = name)
                },
                speed = speed.coerceIn(0f, 1f),
                rpm = rpm?.takeIf { it > 0f },
                controllable = kind == FanKind.PART_COOLING ||
                    kind == FanKind.GENERIC ||
                    kind == FanKind.CONTROLLER,
                kind = kind,
            )
        }
        return readings.sortedWith(
            compareBy<FanReading> {
                when (it.kind) {
                    FanKind.PART_COOLING -> 0
                    FanKind.GENERIC -> 1
                    FanKind.CONTROLLER -> 2
                    FanKind.TEMPERATURE -> 3
                    FanKind.HEATER -> 4
                }
            }.thenBy { it.displayName.lowercase() },
        )
    }

    private fun parseLeds(status: JsonObject): List<LedReading> {
        val readings = mutableListOf<LedReading>()
        status.forEach { (name, value) ->
            val obj = value as? JsonObject ?: return@forEach
            val isLed = name.startsWith("neopixel") ||
                name.startsWith("dotstar") ||
                name.startsWith("led ") ||
                name == "led" ||
                name.startsWith("pca9533") ||
                name.startsWith("pca9632")
            if (!isLed) return@forEach
            val (r, g, b, w) = parseLedColor(obj) ?: return@forEach
            readings += LedReading(
                objectName = name,
                displayName = name.substringAfter(' ', missingDelimiterValue = name),
                red = r,
                green = g,
                blue = b,
                white = w,
            )
        }
        return readings.sortedBy { it.displayName.lowercase() }
    }

    private fun parseLedColor(obj: JsonObject): FloatArray? {
        val colorData = obj["color_data"] as? JsonArray ?: return floatArrayOf(0f, 0f, 0f, 0f)
        val first = colorData.firstOrNull() ?: return floatArrayOf(0f, 0f, 0f, 0f)
        return when (first) {
            is JsonArray -> floatArrayOf(
                first.getOrNull(0)?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 0f,
                first.getOrNull(1)?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 0f,
                first.getOrNull(2)?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 0f,
                first.getOrNull(3)?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 0f,
            )
            is JsonObject -> floatArrayOf(
                first.floatOrNull("R") ?: first.floatOrNull("r") ?: 0f,
                first.floatOrNull("G") ?: first.floatOrNull("g") ?: 0f,
                first.floatOrNull("B") ?: first.floatOrNull("b") ?: 0f,
                first.floatOrNull("W") ?: first.floatOrNull("w") ?: 0f,
            )
            else -> floatArrayOf(0f, 0f, 0f, 0f)
        }
    }

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

