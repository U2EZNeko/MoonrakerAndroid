package com.moonraker.android.data.settings

import kotlinx.serialization.Serializable

@Serializable
data class PrinterConfig(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = 7125,
    val useTls: Boolean = false,
    val apiKey: String = "",
    /** Port used to resolve relative webcam URLs (nginx front door). 0 = scheme default. */
    val mediaPort: Int = 0,
) {
    val baseUrl: String
        get() {
            val scheme = if (useTls) "https" else "http"
            return "$scheme://$host:$port"
        }

    val mediaBaseUrl: String
        get() {
            val scheme = if (useTls) "https" else "http"
            val p = when {
                mediaPort > 0 -> mediaPort
                useTls -> 443
                else -> 80
            }
            return if ((!useTls && p == 80) || (useTls && p == 443)) {
                "$scheme://$host"
            } else {
                "$scheme://$host:$p"
            }
        }

    val websocketUrl: String
        get() {
            val scheme = if (useTls) "wss" else "ws"
            return "$scheme://$host:$port/websocket"
        }
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    OLED,
}

enum class TempUnit {
    CELSIUS,
    FAHRENHEIT,
}

fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
    ThemeMode.OLED -> "OLED black"
}

fun TempUnit.label(): String = when (this) {
    TempUnit.CELSIUS -> "°C"
    TempUnit.FAHRENHEIT -> "°F"
}

fun Float.toDisplayTemp(unit: TempUnit): Float = when (unit) {
    TempUnit.CELSIUS -> this
    TempUnit.FAHRENHEIT -> this * 9f / 5f + 32f
}

object AppTabs {
    const val DASHBOARD = "dashboard"
    const val CAMERAS = "cameras"
    const val TEMPS = "temps"
    const val PRINT = "print"
    const val TIMELAPSE = "timelapse"
    const val GCODE = "gcode"
    const val FANS = "fans"
    const val CONSOLE = "console"
    const val FIRMWARE = "firmware"
    const val SETTINGS = "settings"
    /** Overflow tab — always pinned on the bottom bar; not hideable. */
    const val MORE = "more"

    /** Feature tabs that can appear in the bottom bar or be tucked into More. */
    val featureTabs: List<String> = listOf(
        DASHBOARD,
        CAMERAS,
        TEMPS,
        PRINT,
        TIMELAPSE,
        GCODE,
        FANS,
        CONSOLE,
        FIRMWARE,
        SETTINGS,
    )

    val defaultOrder: List<String> = featureTabs

    /** Tabs shown on the bottom bar by default (More is appended separately). */
    val defaultBottomTabs: List<String> = listOf(
        DASHBOARD,
        CAMERAS,
        TEMPS,
        PRINT,
        TIMELAPSE,
        SETTINGS,
    )

    fun normalize(order: List<String>): List<String> {
        val known = featureTabs.toSet()
        val cleaned = order.filter { it in known }.distinct()
        val missing = featureTabs.filterNot { it in cleaned }
        return if (DASHBOARD in missing && cleaned.isNotEmpty()) {
            listOf(DASHBOARD) + cleaned + missing.filter { it != DASHBOARD }
        } else {
            cleaned + missing
        }
    }

    fun normalizeBottomTabs(visible: List<String>): List<String> {
        val known = featureTabs.toSet()
        return visible.filter { it in known && it != MORE }.distinct()
    }

    fun label(route: String): String = when (route) {
        DASHBOARD -> "Home"
        CAMERAS -> "Cameras"
        TEMPS -> "Temps"
        PRINT -> "Print"
        TIMELAPSE -> "Timelapse"
        GCODE -> "G-code"
        FANS -> "Fans"
        CONSOLE -> "Console"
        FIRMWARE -> "Firmware"
        SETTINGS -> "Settings"
        MORE -> "More"
        else -> route
    }
}

/** Built-in dashboard tile kinds (instance-specific refs live on [DashboardTile.ref]). */
object DashboardTileType {
    const val PRINT_PROGRESS = "print_progress"
    const val CONTROL = "control"
    const val GCODE_PREVIEW = "gcode_preview"
    const val TEMPS = "temps"
    const val TEMP_CHART = "temp_chart"
    const val CAMERA = "camera"
    const val HEATER = "heater"
    const val FAN = "fan"
    const val PROGRESS_RING = "progress_ring"
    const val JOB_SUMMARY = "job_summary"
    const val STAT_ETA = "stat_eta"
    const val STAT_LAYER = "stat_layer"
    const val STAT_FILAMENT = "stat_filament"
    const val STAT_TIME = "stat_time"
    const val SPEED_GAUGE = "speed_gauge"
    const val FLOW_GAUGE = "flow_gauge"
    const val SPEED_FLOW = "speed_flow"
    const val POSITION = "position"
    const val CONNECTION = "connection"
    const val HOMED = "homed"
    const val HEATERS_STRIP = "heaters_strip"
    const val FANS_STRIP = "fans_strip"
    const val LED = "led"
    const val LEDS_STRIP = "leds_strip"
    const val VELOCITY = "velocity"

    /** At most one of these may exist on a layout. */
    val singletons: Set<String> = setOf(
        PRINT_PROGRESS,
        CONTROL,
        GCODE_PREVIEW,
        TEMPS,
        TEMP_CHART,
        PROGRESS_RING,
        JOB_SUMMARY,
        STAT_ETA,
        STAT_LAYER,
        STAT_FILAMENT,
        STAT_TIME,
        SPEED_GAUGE,
        FLOW_GAUGE,
        SPEED_FLOW,
        POSITION,
        CONNECTION,
        HOMED,
        HEATERS_STRIP,
        FANS_STRIP,
        LEDS_STRIP,
        VELOCITY,
    )

    fun label(type: String, ref: String = ""): String = when (type) {
        PRINT_PROGRESS -> "Print progress"
        CONTROL -> "Controls"
        GCODE_PREVIEW -> "G-code preview"
        TEMPS -> "All temperatures"
        TEMP_CHART -> "Temperature graph"
        CAMERA -> if (ref.isBlank()) "Camera" else "Camera · $ref"
        HEATER -> if (ref.isBlank()) "Heater" else ref.removePrefix("temperature_sensor ")
        FAN -> if (ref.isBlank()) "Fan" else ref
        LED -> if (ref.isBlank()) "LED" else ref.substringAfter(' ', missingDelimiterValue = ref)
        PROGRESS_RING -> "Progress ring"
        JOB_SUMMARY -> "Job summary"
        STAT_ETA -> "ETA"
        STAT_LAYER -> "Layer"
        STAT_FILAMENT -> "Filament used"
        STAT_TIME -> "Print time"
        SPEED_GAUGE -> "Speed gauge"
        FLOW_GAUGE -> "Flow gauge"
        SPEED_FLOW -> "Speed & flow"
        POSITION -> "Live position"
        CONNECTION -> "Connection"
        HOMED -> "Homed / extruder"
        HEATERS_STRIP -> "Heaters strip"
        FANS_STRIP -> "Fans strip"
        LEDS_STRIP -> "LED controls"
        VELOCITY -> "Velocity"
        else -> type
    }

    fun category(type: String): String = when (type) {
        PRINT_PROGRESS, PROGRESS_RING, JOB_SUMMARY, STAT_ETA, STAT_LAYER,
        STAT_FILAMENT, STAT_TIME, CONTROL, GCODE_PREVIEW -> "Print"
        TEMPS, TEMP_CHART, HEATER, HEATERS_STRIP -> "Thermal"
        CAMERA -> "Cameras"
        FAN, FANS_STRIP -> "Fans"
        LED, LEDS_STRIP -> "Lights"
        SPEED_GAUGE, FLOW_GAUGE, SPEED_FLOW, POSITION, VELOCITY, HOMED, CONNECTION -> "Machine"
        else -> "Other"
    }

    fun defaultColSpan(type: String): Int = when (type) {
        PRINT_PROGRESS, CONTROL, GCODE_PREVIEW, TEMPS, TEMP_CHART,
        JOB_SUMMARY, HEATERS_STRIP, FANS_STRIP, LEDS_STRIP, SPEED_FLOW -> 6
        CAMERA -> 6
        PROGRESS_RING -> 2
        STAT_ETA, STAT_LAYER, STAT_FILAMENT, STAT_TIME,
        SPEED_GAUGE, FLOW_GAUGE, POSITION, CONNECTION, HOMED, VELOCITY, LED -> 2
        HEATER, FAN -> 2
        else -> 3
    }

    fun defaultRowSpan(type: String): Int = when (type) {
        CAMERA, GCODE_PREVIEW -> 3
        TEMP_CHART -> 3
        CONTROL -> 2
        PRINT_PROGRESS, JOB_SUMMARY -> 2
        TEMPS, HEATERS_STRIP, FANS_STRIP, LEDS_STRIP, SPEED_FLOW -> 2
        PROGRESS_RING, LED -> 2
        HEATER -> 2
        FAN, STAT_ETA, STAT_LAYER, STAT_FILAMENT, STAT_TIME,
        SPEED_GAUGE, FLOW_GAUGE, POSITION, CONNECTION, HOMED, VELOCITY -> 1
        else -> 2
    }
}

@Serializable
data class DashboardTile(
    val id: String,
    val type: String,
    /** Column 0..[DashboardGrid.COLUMNS)-1 */
    val col: Int = 0,
    val row: Int = 0,
    val colSpan: Int = 2,
    val rowSpan: Int = 1,
    /** Camera key, heater object name, fan object name, etc. */
    val ref: String = "",
)

object DashboardGrid {
    const val COLUMNS = 6
    const val MAX_ROW_SPAN = 6
    const val MAX_COL_SPAN = 6

    fun normalize(tiles: List<DashboardTile>): List<DashboardTile> =
        tiles.map { tile ->
            val w = tile.colSpan.coerceIn(1, MAX_COL_SPAN)
            val h = tile.rowSpan.coerceIn(1, MAX_ROW_SPAN)
            val col = tile.col.coerceIn(0, (COLUMNS - w).coerceAtLeast(0))
            tile.copy(
                col = col,
                row = tile.row.coerceAtLeast(0),
                colSpan = w,
                rowSpan = h,
            )
        }

    fun overlaps(a: DashboardTile, b: DashboardTile): Boolean {
        if (a.id == b.id) return false
        val aRight = a.col + a.colSpan
        val aBottom = a.row + a.rowSpan
        val bRight = b.col + b.colSpan
        val bBottom = b.row + b.rowSpan
        return a.col < bRight && aRight > b.col && a.row < bBottom && aBottom > b.row
    }

    fun canPlace(tiles: List<DashboardTile>, candidate: DashboardTile): Boolean {
        if (candidate.col < 0 || candidate.row < 0) return false
        if (candidate.col + candidate.colSpan > COLUMNS) return false
        if (candidate.colSpan !in 1..MAX_COL_SPAN) return false
        if (candidate.rowSpan !in 1..MAX_ROW_SPAN) return false
        return tiles.none { overlaps(it, candidate) }
    }

    fun findFreeSlot(
        tiles: List<DashboardTile>,
        colSpan: Int,
        rowSpan: Int,
        excludeId: String? = null,
        preferCol: Int? = null,
        preferRow: Int? = null,
    ): Pair<Int, Int>? {
        val w = colSpan.coerceIn(1, MAX_COL_SPAN)
        val h = rowSpan.coerceIn(1, MAX_ROW_SPAN)
        val others = tiles.filter { it.id != excludeId }
        val maxRow = (others.maxOfOrNull { it.row + it.rowSpan } ?: 0) + 12
        if (preferCol != null && preferRow != null) {
            val col = preferCol.coerceIn(0, (COLUMNS - w).coerceAtLeast(0))
            val row = preferRow.coerceAtLeast(0)
            val probe = DashboardTile(
                id = excludeId ?: "_",
                type = "_",
                col = col,
                row = row,
                colSpan = w,
                rowSpan = h,
            )
            if (canPlace(others, probe)) return col to row
        }
        // Spiral-ish search around preferred cell, then full scan.
        if (preferCol != null && preferRow != null) {
            for (radius in 1..maxRow) {
                for (dr in -radius..radius) {
                    for (dc in -radius..radius) {
                        if (kotlin.math.abs(dr) != radius && kotlin.math.abs(dc) != radius) continue
                        val col = (preferCol + dc).coerceIn(0, (COLUMNS - w).coerceAtLeast(0))
                        val row = (preferRow + dr).coerceAtLeast(0)
                        val probe = DashboardTile(
                            id = excludeId ?: "_",
                            type = "_",
                            col = col,
                            row = row,
                            colSpan = w,
                            rowSpan = h,
                        )
                        if (canPlace(others, probe)) return col to row
                    }
                }
            }
        }
        for (row in 0..maxRow) {
            for (col in 0..(COLUMNS - w)) {
                val probe = DashboardTile(
                    id = excludeId ?: "_",
                    type = "_",
                    col = col,
                    row = row,
                    colSpan = w,
                    rowSpan = h,
                )
                if (canPlace(others, probe)) return col to row
            }
        }
        return null
    }

    /** Move [id] to [col],[row], pushing overlapping tiles into free slots. */
    fun moveWithPush(tiles: List<DashboardTile>, id: String, col: Int, row: Int): List<DashboardTile> {
        val moving = tiles.firstOrNull { it.id == id } ?: return tiles
        val w = moving.colSpan.coerceIn(1, MAX_COL_SPAN)
        val h = moving.rowSpan.coerceIn(1, MAX_ROW_SPAN)
        val targetCol = col.coerceIn(0, (COLUMNS - w).coerceAtLeast(0))
        val targetRow = row.coerceAtLeast(0)
        val placed = moving.copy(col = targetCol, row = targetRow)
        var others = tiles.filter { it.id != id }
        val blockers = others.filter { overlaps(it, placed) }
        others = others.filterNot { b -> blockers.any { it.id == b.id } }
        var working = others + placed
        blockers.forEach { blocker ->
            val slot = findFreeSlot(
                tiles = working,
                colSpan = blocker.colSpan,
                rowSpan = blocker.rowSpan,
                excludeId = blocker.id,
                preferCol = blocker.col,
                preferRow = placed.row + placed.rowSpan,
            ) ?: return@forEach
            working = working + blocker.copy(col = slot.first, row = slot.second)
        }
        // Keep any blockers we couldn't place (shouldn't happen often).
        val placedIds = working.map { it.id }.toSet()
        working = working + blockers.filter { it.id !in placedIds }
        return normalize(working)
    }

    fun resizeWithPush(
        tiles: List<DashboardTile>,
        id: String,
        colSpan: Int,
        rowSpan: Int,
    ): List<DashboardTile> {
        val moving = tiles.firstOrNull { it.id == id } ?: return tiles
        val w = colSpan.coerceIn(1, MAX_COL_SPAN)
        val h = rowSpan.coerceIn(1, MAX_ROW_SPAN)
        val col = moving.col.coerceIn(0, (COLUMNS - w).coerceAtLeast(0))
        val resized = moving.copy(col = col, colSpan = w, rowSpan = h)
        if (canPlace(tiles, resized)) {
            return normalize(tiles.map { if (it.id == id) resized else it })
        }
        return moveWithPush(
            tiles.map { if (it.id == id) resized else it },
            id,
            resized.col,
            resized.row,
        )
    }

    fun rowCount(tiles: List<DashboardTile>): Int =
        (tiles.maxOfOrNull { it.row + it.rowSpan } ?: 0).coerceAtLeast(1)

    /** Migrate legacy ordered type list into a stacked full-width grid. */
    fun fromLegacyTypes(types: List<String>, cameraKey: String = ""): List<DashboardTile> {
        var row = 0
        return types.mapNotNull { type ->
            val mapped = when (type) {
                "print_progress" -> DashboardTileType.PRINT_PROGRESS
                "camera" -> DashboardTileType.CAMERA
                "control" -> DashboardTileType.CONTROL
                "temps" -> DashboardTileType.TEMPS
                "gcode_preview" -> DashboardTileType.GCODE_PREVIEW
                else -> return@mapNotNull null
            }
            val h = DashboardTileType.defaultRowSpan(mapped)
            val tile = DashboardTile(
                id = java.util.UUID.randomUUID().toString(),
                type = mapped,
                col = 0,
                row = row,
                colSpan = 6,
                rowSpan = h,
                ref = if (mapped == DashboardTileType.CAMERA) cameraKey else "",
            )
            row += h
            tile
        }
    }

    val defaultLayout: List<DashboardTile> = listOf(
        DashboardTile(
            id = "default-job",
            type = DashboardTileType.JOB_SUMMARY,
            col = 0,
            row = 0,
            colSpan = 4,
            rowSpan = 2,
        ),
        DashboardTile(
            id = "default-ring",
            type = DashboardTileType.PROGRESS_RING,
            col = 4,
            row = 0,
            colSpan = 2,
            rowSpan = 2,
        ),
        DashboardTile(
            id = "default-eta",
            type = DashboardTileType.STAT_ETA,
            col = 0,
            row = 2,
            colSpan = 2,
            rowSpan = 1,
        ),
        DashboardTile(
            id = "default-layer",
            type = DashboardTileType.STAT_LAYER,
            col = 2,
            row = 2,
            colSpan = 2,
            rowSpan = 1,
        ),
        DashboardTile(
            id = "default-filament",
            type = DashboardTileType.STAT_FILAMENT,
            col = 4,
            row = 2,
            colSpan = 2,
            rowSpan = 1,
        ),
        DashboardTile(
            id = "default-camera",
            type = DashboardTileType.CAMERA,
            col = 0,
            row = 3,
            colSpan = 6,
            rowSpan = 3,
        ),
        DashboardTile(
            id = "default-chart",
            type = DashboardTileType.TEMP_CHART,
            col = 0,
            row = 6,
            colSpan = 6,
            rowSpan = 3,
        ),
        DashboardTile(
            id = "default-speed-flow",
            type = DashboardTileType.SPEED_FLOW,
            col = 0,
            row = 9,
            colSpan = 3,
            rowSpan = 2,
        ),
        DashboardTile(
            id = "default-position",
            type = DashboardTileType.POSITION,
            col = 3,
            row = 9,
            colSpan = 3,
            rowSpan = 1,
        ),
        DashboardTile(
            id = "default-connection",
            type = DashboardTileType.CONNECTION,
            col = 3,
            row = 10,
            colSpan = 3,
            rowSpan = 1,
        ),
        DashboardTile(
            id = "default-control",
            type = DashboardTileType.CONTROL,
            col = 0,
            row = 11,
            colSpan = 6,
            rowSpan = 2,
        ),
        DashboardTile(
            id = "default-leds",
            type = DashboardTileType.LEDS_STRIP,
            col = 0,
            row = 13,
            colSpan = 6,
            rowSpan = 2,
        ),
    )
}

@Serializable
data class WidgetBinding(
    val appWidgetId: Int,
    val printerId: String,
    val webcamUid: String,
    val webcamName: String = "",
)

data class AppSettings(
    val printers: List<PrinterConfig> = emptyList(),
    val activePrinterId: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentColorArgb: Long = 0xFFFF6B4E,
    /** Use Material You wallpaper colors when available (Android 12+). */
    val useDynamicColor: Boolean = false,
    val widgetBindings: List<WidgetBinding> = emptyList(),
    val tabOrder: List<String> = AppTabs.defaultOrder,
    /** Feature tabs shown on the bottom bar (More is always appended). */
    val bottomTabs: List<String> = AppTabs.defaultBottomTabs,
    /** Home dashboard grid tiles (6 columns). */
    val dashboardLayout: List<DashboardTile> = DashboardGrid.defaultLayout,
    /** printerId -> ordered webcam uids (or names). */
    val cameraOrderByPrinter: Map<String, List<String>> = emptyMap(),
    /** Target FPS for snapshot / adaptive camera polling (1–30). */
    val streamFps: Int = 30,
    /** Prefer JPEG snapshot polling over WebRTC/live when a snapshot URL exists. */
    val preferSnapshotStreams: Boolean = false,
    val tempUnit: TempUnit = TempUnit.CELSIUS,
    val keepScreenOnCameras: Boolean = true,
    val keepScreenOnPrint: Boolean = true,
    /** Open the G-code tab and load the active job's file when the app starts. */
    val loadCurrentGcodeOnStart: Boolean = false,
) {
    val activePrinter: PrinterConfig?
        get() = printers.firstOrNull { it.id == activePrinterId } ?: printers.firstOrNull()
}
