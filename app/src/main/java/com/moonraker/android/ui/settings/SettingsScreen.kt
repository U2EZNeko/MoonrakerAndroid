package com.moonraker.android.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moonraker.android.data.settings.AppTabs
import com.moonraker.android.data.settings.PrinterConfig
import com.moonraker.android.data.settings.TempUnit
import com.moonraker.android.data.settings.ThemeMode
import com.moonraker.android.data.settings.label
import com.moonraker.android.ui.common.AppBarTitle
import com.moonraker.android.ui.theme.AccentChoices
import com.moonraker.android.ui.theme.colorToHsv
import com.moonraker.android.ui.theme.hsvToColor
import com.moonraker.android.ui.theme.toArgbLong
import com.moonraker.android.ui.theme.toComposeColor

private val FpsPresets = listOf(5, 10, 15, 20, 24, 30)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<PrinterConfig?>(null) }
    var creating by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "1.0.0" }
    }
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Scaffold(
        topBar = { TopAppBar(title = { AppBarTitle(title = "Settings") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add printer")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Text("Printers", style = MaterialTheme.typography.titleLarge) }
            if (ui.printers.isEmpty()) {
                item {
                    Text(
                        text = "No printers yet. Tap + to add one.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(ui.printers, key = { it.id }) { printer ->
                Surface(
                    tonalElevation = 1.dp,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(printer.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = printer.baseUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "Media ${printer.mediaBaseUrl}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (ui.activePrinterId == printer.id) {
                                Text(
                                    text = "Active",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row {
                            TextButton(onClick = { viewModel.setActivePrinter(printer.id) }) {
                                Text("Set active")
                            }
                            TextButton(onClick = { viewModel.testPrinter(printer) }) {
                                Text("Test")
                            }
                            IconButton(onClick = { editing = printer }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                            IconButton(onClick = { viewModel.deletePrinter(printer.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }

            item {
                ui.testMessage?.let { message ->
                    Text(
                        text = message,
                        color = if (ui.testIsError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }

            item { Text("Dashboard", style = MaterialTheme.typography.titleLarge) }
            item {
                SettingsCard {
                    Text(
                        text = "Home uses a 6-column grid. On the Home tab, tap the pencil to add, " +
                            "move, resize, and remove widgets (cameras, toolheads, fans, …).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = viewModel::resetDashboardLayout,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Reset dashboard layout")
                    }
                }
            }

            item { Text("Appearance", style = MaterialTheme.typography.titleLarge) }
            item {
                SettingsCard {
                    Text("Theme", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = ui.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(mode.label()) },
                            )
                        }
                    }
                    if (dynamicAvailable) {
                        SettingsToggle(
                            title = "Material You colors",
                            subtitle = "Pull accent from wallpaper (Android 12+)",
                            checked = ui.useDynamicColor,
                            onCheckedChange = viewModel::setUseDynamicColor,
                        )
                    }
                    if (!ui.useDynamicColor || !dynamicAvailable) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Accent", style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AccentChoices.forEach { choice ->
                                val selected = ui.accentArgb == choice.color.toArgbLong()
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(choice.color)
                                        .border(
                                            width = if (selected) 3.dp else 1.dp,
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.onSurface
                                            } else {
                                                MaterialTheme.colorScheme.outline
                                            },
                                            shape = CircleShape,
                                        )
                                        .clickable { viewModel.setAccent(choice.color.toArgbLong()) },
                                )
                            }
                        }
                        AccentSliders(
                            accentArgb = ui.accentArgb,
                            onChange = viewModel::setAccent,
                        )
                    }
                }
            }

            item { Text("Streaming", style = MaterialTheme.typography.titleLarge) }
            item {
                SettingsCard {
                    Text("Camera stream FPS", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${ui.streamFps} fps — snapshot / adaptive polling rate",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FpsPresets.forEach { fps ->
                            FilterChip(
                                selected = ui.streamFps == fps,
                                onClick = { viewModel.setStreamFps(fps) },
                                label = { Text("$fps") },
                            )
                        }
                    }
                    Slider(
                        value = ui.streamFps.toFloat(),
                        onValueChange = { viewModel.setStreamFps(it.toInt()) },
                        valueRange = 1f..30f,
                        steps = 28,
                    )
                    SettingsToggle(
                        title = "Prefer snapshot streams",
                        subtitle = "Skip WebRTC when a snapshot URL exists (more reliable, higher load)",
                        checked = ui.preferSnapshotStreams,
                        onCheckedChange = viewModel::setPreferSnapshotStreams,
                    )
                }
            }

            item { Text("Behavior", style = MaterialTheme.typography.titleLarge) }
            item {
                SettingsCard {
                    Text("Temperature unit", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TempUnit.entries.forEach { unit ->
                            FilterChip(
                                selected = ui.tempUnit == unit,
                                onClick = { viewModel.setTempUnit(unit) },
                                label = { Text(unit.label()) },
                            )
                        }
                    }
                    SettingsToggle(
                        title = "Keep screen on — cameras",
                        subtitle = "Prevent sleep while a camera feed is open",
                        checked = ui.keepScreenOnCameras,
                        onCheckedChange = viewModel::setKeepScreenOnCameras,
                    )
                    SettingsToggle(
                        title = "Keep screen on — printing",
                        subtitle = "Prevent sleep while a print is active or paused",
                        checked = ui.keepScreenOnPrint,
                        onCheckedChange = viewModel::setKeepScreenOnPrint,
                    )
                    SettingsToggle(
                        title = "Load current G-code on start",
                        subtitle = "Open the G-code tab and live-preview the active job when the app launches",
                        checked = ui.loadCurrentGcodeOnStart,
                        onCheckedChange = viewModel::setLoadCurrentGcodeOnStart,
                    )
                }
            }

            item { Text("Layout", style = MaterialTheme.typography.titleLarge) }
            item {
                SettingsCard {
                    Text("Tab order", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "First visible tab opens on launch. Hide tabs from the bottom bar in More.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ui.tabOrder.forEachIndexed { index, route ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = AppTabs.label(route),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            IconButton(
                                onClick = { viewModel.moveTab(index, -1) },
                                enabled = index > 0,
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
                            }
                            IconButton(
                                onClick = { viewModel.moveTab(index, 1) },
                                enabled = index < ui.tabOrder.lastIndex,
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
                            }
                        }
                    }
                    Text(
                        text = "Cameras can also be reordered with arrows on the Cameras tab.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { Text("About", style = MaterialTheme.typography.titleLarge) }
            item {
                SettingsCard {
                    Text("Moonraker Android", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Version $versionName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Home screen: long-press → Widgets → Camera snapshot. Tap a widget to refresh.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (creating) {
        PrinterEditorDialog(
            initial = PrinterConfig(
                id = viewModel.newPrinterId(),
                name = "Printer",
                host = "192.168.69.56",
                port = 7125,
            ),
            onDismiss = { creating = false },
            onSave = {
                viewModel.savePrinter(it)
                creating = false
            },
        )
    }
    editing?.let { printer ->
        PrinterEditorDialog(
            initial = printer,
            onDismiss = { editing = null },
            onSave = {
                viewModel.savePrinter(it)
                editing = null
            },
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = { content() },
        )
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AccentSliders(
    accentArgb: Long,
    onChange: (Long) -> Unit,
) {
    val color = accentArgb.toComposeColor()
    val hsv = remember(accentArgb) { colorToHsv(color) }
    var hue by remember(accentArgb) { mutableFloatStateOf(hsv[0]) }
    var sat by remember(accentArgb) { mutableFloatStateOf(hsv[1]) }
    var value by remember(accentArgb) { mutableFloatStateOf(hsv[2]) }

    Column {
        Text("Hue")
        Slider(
            value = hue,
            onValueChange = {
                hue = it
                onChange(hsvToColor(hue, sat, value).toArgbLong())
            },
            valueRange = 0f..360f,
        )
        Text("Saturation")
        Slider(
            value = sat,
            onValueChange = {
                sat = it
                onChange(hsvToColor(hue, sat, value).toArgbLong())
            },
            valueRange = 0f..1f,
        )
        Text("Brightness")
        Slider(
            value = value,
            onValueChange = {
                value = it
                onChange(hsvToColor(hue, sat, value).toArgbLong())
            },
            valueRange = 0.2f..1f,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(MaterialTheme.shapes.small)
                .background(hsvToColor(hue, sat, value)),
        )
    }
}

@Composable
private fun PrinterEditorDialog(
    initial: PrinterConfig,
    onDismiss: () -> Unit,
    onSave: (PrinterConfig) -> Unit,
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var host by remember(initial.id) { mutableStateOf(initial.host) }
    var port by remember(initial.id) { mutableStateOf(initial.port.toString()) }
    var mediaPort by remember(initial.id) {
        mutableStateOf(if (initial.mediaPort > 0) initial.mediaPort.toString() else "")
    }
    var tls by remember(initial.id) { mutableStateOf(initial.useTls) }
    var apiKey by remember(initial.id) { mutableStateOf(initial.apiKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.host.isBlank()) "Add printer" else "Edit printer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Host / IP") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter { ch -> ch.isDigit() }.take(5) },
                    label = { Text("API port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = mediaPort,
                    onValueChange = { mediaPort = it.filter { ch -> ch.isDigit() }.take(5) },
                    label = { Text("Media port (blank = 80/443)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("TLS (HTTPS/WSS)")
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(checked = tls, onCheckedChange = { tls = it })
                }
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API key (optional)") },
                    singleLine = true,
                )
                Text(
                    text = "Camera URLs resolve via the media host/port (nginx), not the Moonraker API port.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.ifBlank { "Printer" },
                            host = host.trim(),
                            port = port.toIntOrNull() ?: 7125,
                            mediaPort = mediaPort.toIntOrNull() ?: 0,
                            useTls = tls,
                            apiKey = apiKey.trim(),
                        ),
                    )
                },
                enabled = host.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
