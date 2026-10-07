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
    const val SETTINGS = "settings"

    val defaultOrder: List<String> = listOf(
        DASHBOARD,
        CAMERAS,
        TEMPS,
        PRINT,
        TIMELAPSE,
        SETTINGS,
    )

    fun normalize(order: List<String>): List<String> {
        val known = defaultOrder.toSet()
        val cleaned = order.filter { it in known }.distinct()
        val missing = defaultOrder.filterNot { it in cleaned }
        // Surface Dashboard for upgrades that never had it saved.
        return if (DASHBOARD in missing && cleaned.isNotEmpty()) {
            listOf(DASHBOARD) + cleaned + missing.filter { it != DASHBOARD }
        } else {
            cleaned + missing
        }
    }
}

/** Dashboard home cards — presence in the list = enabled; list order = display order. */
object DashboardWidgets {
    const val PRINT_PROGRESS = "print_progress"
    const val CAMERA = "camera"
    const val CONTROL = "control"
    const val TEMPS = "temps"

    val all: List<String> = listOf(PRINT_PROGRESS, CAMERA, CONTROL, TEMPS)

    val defaultOrder: List<String> = listOf(PRINT_PROGRESS, CAMERA, CONTROL)

    fun label(id: String): String = when (id) {
        PRINT_PROGRESS -> "Print progress"
        CAMERA -> "Camera"
        CONTROL -> "Controls"
        TEMPS -> "Temperatures"
        else -> id
    }

    fun normalizeEnabled(order: List<String>): List<String> {
        val known = all.toSet()
        return order.filter { it in known }.distinct()
    }
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
    /** Enabled dashboard widgets in display order. */
    val dashboardWidgets: List<String> = DashboardWidgets.defaultOrder,
    /** Optional webcam uid/name for the dashboard camera card (blank = first camera). */
    val dashboardCameraKey: String = "",
    /** printerId -> ordered webcam uids (or names). */
    val cameraOrderByPrinter: Map<String, List<String>> = emptyMap(),
    /** Target FPS for snapshot / adaptive camera polling (1–30). */
    val streamFps: Int = 30,
    /** Prefer JPEG snapshot polling over WebRTC/live when a snapshot URL exists. */
    val preferSnapshotStreams: Boolean = false,
    val tempUnit: TempUnit = TempUnit.CELSIUS,
    val keepScreenOnCameras: Boolean = true,
    val keepScreenOnPrint: Boolean = true,
) {
    val activePrinter: PrinterConfig?
        get() = printers.firstOrNull { it.id == activePrinterId } ?: printers.firstOrNull()
}
