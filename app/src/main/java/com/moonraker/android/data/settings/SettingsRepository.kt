package com.moonraker.android.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "moonraker_settings")

class SettingsRepository(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val printersKey = stringPreferencesKey("printers_json")
    private val activePrinterKey = stringPreferencesKey("active_printer_id")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val accentKey = longPreferencesKey("accent_argb")
    private val dynamicColorKey = booleanPreferencesKey("use_dynamic_color")
    private val widgetsKey = stringPreferencesKey("widget_bindings_json")
    private val tabOrderKey = stringPreferencesKey("tab_order_json")
    private val bottomTabsKey = stringPreferencesKey("bottom_tabs_json")
    private val dashboardWidgetsKey = stringPreferencesKey("dashboard_widgets_json")
    private val dashboardLayoutKey = stringPreferencesKey("dashboard_layout_json")
    private val dashboardCameraKey = stringPreferencesKey("dashboard_camera_key")
    private val cameraOrderKey = stringPreferencesKey("camera_order_json")
    private val streamFpsKey = intPreferencesKey("stream_fps")
    private val preferSnapshotKey = booleanPreferencesKey("prefer_snapshot_streams")
    private val tempUnitKey = stringPreferencesKey("temp_unit")
    private val keepScreenCamerasKey = booleanPreferencesKey("keep_screen_on_cameras")
    private val keepScreenPrintKey = booleanPreferencesKey("keep_screen_on_print")
    private val loadCurrentGcodeOnStartKey = booleanPreferencesKey("load_current_gcode_on_start")

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val printers = prefs[printersKey]?.let {
            runCatching { json.decodeFromString<List<PrinterConfig>>(it) }.getOrDefault(emptyList())
        } ?: emptyList()
        val widgets = prefs[widgetsKey]?.let {
            runCatching { json.decodeFromString<List<WidgetBinding>>(it) }.getOrDefault(emptyList())
        } ?: emptyList()
        val tabOrder = prefs[tabOrderKey]?.let {
            runCatching { json.decodeFromString<List<String>>(it) }.getOrDefault(AppTabs.defaultOrder)
        } ?: AppTabs.defaultOrder
        val bottomTabs = prefs[bottomTabsKey]?.let {
            runCatching { json.decodeFromString<List<String>>(it) }.getOrDefault(AppTabs.defaultBottomTabs)
        } ?: AppTabs.defaultBottomTabs
        val cameraKey = prefs[dashboardCameraKey].orEmpty()
        val dashboardLayout = prefs[dashboardLayoutKey]?.let {
            runCatching { json.decodeFromString<List<DashboardTile>>(it) }.getOrNull()
        }?.let { DashboardGrid.normalize(it) }
            ?: prefs[dashboardWidgetsKey]?.let { legacy ->
                runCatching { json.decodeFromString<List<String>>(legacy) }.getOrNull()
                    ?.let { DashboardGrid.fromLegacyTypes(it, cameraKey) }
            }
            ?: DashboardGrid.defaultLayout
        val cameraOrder = prefs[cameraOrderKey]?.let {
            runCatching { json.decodeFromString<Map<String, List<String>>>(it) }.getOrDefault(emptyMap())
        } ?: emptyMap()
        AppSettings(
            printers = printers,
            activePrinterId = prefs[activePrinterKey],
            themeMode = prefs[themeModeKey]?.let {
                runCatching { ThemeMode.valueOf(it) }.getOrDefault(ThemeMode.SYSTEM)
            } ?: ThemeMode.SYSTEM,
            accentColorArgb = prefs[accentKey] ?: 0xFFFF6B4E,
            useDynamicColor = prefs[dynamicColorKey] ?: false,
            widgetBindings = widgets,
            tabOrder = AppTabs.normalize(tabOrder),
            bottomTabs = AppTabs.normalizeBottomTabs(bottomTabs),
            dashboardLayout = dashboardLayout,
            cameraOrderByPrinter = cameraOrder,
            streamFps = (prefs[streamFpsKey] ?: 30).coerceIn(1, 30),
            preferSnapshotStreams = prefs[preferSnapshotKey] ?: false,
            tempUnit = prefs[tempUnitKey]?.let {
                runCatching { TempUnit.valueOf(it) }.getOrDefault(TempUnit.CELSIUS)
            } ?: TempUnit.CELSIUS,
            keepScreenOnCameras = prefs[keepScreenCamerasKey] ?: true,
            keepScreenOnPrint = prefs[keepScreenPrintKey] ?: true,
            loadCurrentGcodeOnStart = prefs[loadCurrentGcodeOnStartKey] ?: false,
        )
    }

    suspend fun upsertPrinter(printer: PrinterConfig) {
        context.dataStore.edit { prefs ->
            val current = prefs[printersKey]?.let {
                runCatching { json.decodeFromString<List<PrinterConfig>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            val next = current.filterNot { it.id == printer.id } + printer
            prefs[printersKey] = json.encodeToString(next)
            if (prefs[activePrinterKey].isNullOrBlank()) {
                prefs[activePrinterKey] = printer.id
            }
        }
    }

    suspend fun deletePrinter(printerId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[printersKey]?.let {
                runCatching { json.decodeFromString<List<PrinterConfig>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            val next = current.filterNot { it.id == printerId }
            prefs[printersKey] = json.encodeToString(next)
            if (prefs[activePrinterKey] == printerId) {
                prefs[activePrinterKey] = next.firstOrNull()?.id.orEmpty()
            }
            val widgets = prefs[widgetsKey]?.let {
                runCatching { json.decodeFromString<List<WidgetBinding>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            prefs[widgetsKey] = json.encodeToString(widgets.filterNot { it.printerId == printerId })
        }
    }

    suspend fun setActivePrinter(printerId: String) {
        context.dataStore.edit { prefs ->
            prefs[activePrinterKey] = printerId
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[themeModeKey] = mode.name
        }
    }

    suspend fun setAccentColor(argb: Long) {
        context.dataStore.edit { prefs ->
            prefs[accentKey] = argb
        }
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[dynamicColorKey] = enabled
        }
    }

    suspend fun setTabOrder(order: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[tabOrderKey] = json.encodeToString(AppTabs.normalize(order))
        }
    }

    suspend fun setBottomTabs(tabs: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[bottomTabsKey] = json.encodeToString(AppTabs.normalizeBottomTabs(tabs))
        }
    }

    suspend fun setDashboardLayout(tiles: List<DashboardTile>) {
        context.dataStore.edit { prefs ->
            prefs[dashboardLayoutKey] = json.encodeToString(DashboardGrid.normalize(tiles))
        }
    }

    suspend fun resetDashboardLayout() {
        setDashboardLayout(DashboardGrid.defaultLayout)
    }

    suspend fun setCameraOrder(printerId: String, orderedKeys: List<String>) {
        context.dataStore.edit { prefs ->
            val current = prefs[cameraOrderKey]?.let {
                runCatching { json.decodeFromString<Map<String, List<String>>>(it) }.getOrDefault(emptyMap())
            } ?: emptyMap()
            prefs[cameraOrderKey] = json.encodeToString(current + (printerId to orderedKeys))
        }
    }

    suspend fun setStreamFps(fps: Int) {
        context.dataStore.edit { prefs ->
            prefs[streamFpsKey] = fps.coerceIn(1, 30)
        }
    }

    suspend fun setPreferSnapshotStreams(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[preferSnapshotKey] = enabled
        }
    }

    suspend fun setTempUnit(unit: TempUnit) {
        context.dataStore.edit { prefs ->
            prefs[tempUnitKey] = unit.name
        }
    }

    suspend fun setKeepScreenOnCameras(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[keepScreenCamerasKey] = enabled
        }
    }

    suspend fun setKeepScreenOnPrint(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[keepScreenPrintKey] = enabled
        }
    }

    suspend fun setLoadCurrentGcodeOnStart(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[loadCurrentGcodeOnStartKey] = enabled
        }
    }

    suspend fun saveWidgetBinding(binding: WidgetBinding) {
        context.dataStore.edit { prefs ->
            val current = prefs[widgetsKey]?.let {
                runCatching { json.decodeFromString<List<WidgetBinding>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            val next = current.filterNot { it.appWidgetId == binding.appWidgetId } + binding
            prefs[widgetsKey] = json.encodeToString(next)
        }
    }

    suspend fun removeWidgetBinding(appWidgetId: Int) {
        context.dataStore.edit { prefs ->
            val current = prefs[widgetsKey]?.let {
                runCatching { json.decodeFromString<List<WidgetBinding>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            prefs[widgetsKey] = json.encodeToString(current.filterNot { it.appWidgetId == appWidgetId })
        }
    }

    fun newPrinterId(): String = UUID.randomUUID().toString()
}
