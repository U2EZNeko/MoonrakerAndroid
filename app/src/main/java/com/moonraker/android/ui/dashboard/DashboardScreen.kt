package com.moonraker.android.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moonraker.android.data.moonraker.HeaterReading
import com.moonraker.android.data.moonraker.PrintStatus
import com.moonraker.android.data.moonraker.WebcamEntry
import com.moonraker.android.data.settings.DashboardWidgets
import com.moonraker.android.data.settings.PrinterConfig
import com.moonraker.android.data.settings.TempUnit
import com.moonraker.android.data.settings.label
import com.moonraker.android.data.settings.orderKey
import com.moonraker.android.data.settings.toDisplayTemp
import com.moonraker.android.ui.camera.CameraPlayer
import com.moonraker.android.ui.common.AppBarTitle
import com.moonraker.android.ui.common.ConfirmCancelPrintDialog
import com.moonraker.android.ui.common.ConfirmEmergencyStopDialog
import com.moonraker.android.ui.common.EmptyState
import com.moonraker.android.ui.common.PrinterSwitcher
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()

    val printBusy = ui.printStatus.state in setOf("printing", "paused")
    KeepScreenOn(enabled = ui.keepScreenOn && (printBusy || ui.widgets.contains(DashboardWidgets.CAMERA)))

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AppBarTitle(
                        title = "Dashboard",
                        subtitle = ui.connectionMessage,
                        subtitleColor = if (ui.connected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
                actions = {
                    IconButton(
                        onClick = viewModel::refresh,
                        enabled = !ui.refreshing && ui.activePrinter != null,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    PrinterSwitcher(
                        printers = ui.printers,
                        activeId = ui.activePrinter?.id,
                        onSelect = viewModel::setActivePrinter,
                    )
                },
            )
        },
    ) { padding ->
        val printer = ui.activePrinter
        if (printer == null) {
            EmptyState(
                message = "Add a printer in Settings",
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        PullToRefreshBox(
            isRefreshing = ui.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            if (ui.widgets.isEmpty()) {
                item {
                    EmptyState(
                        message = "No widgets enabled. Configure them in Settings → Layout.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                    )
                }
            } else {
                itemsIndexed(ui.widgets, key = { _, id -> id }) { _, id ->
                    when (id) {
                        DashboardWidgets.PRINT_PROGRESS -> PrintProgressWidget(
                            status = ui.printStatus,
                            onPause = viewModel::pausePrint,
                            onResume = viewModel::resumePrint,
                            onCancelPrint = viewModel::cancelPrint,
                        )
                        DashboardWidgets.CAMERA -> key(ui.cameraEpoch, ui.selectedCamera?.orderKey()) {
                            CameraWidgetCard(
                                printer = printer,
                                camera = ui.selectedCamera,
                                client = viewModel.httpClient,
                                resolveUrl = viewModel::resolveUrl,
                                streamFps = ui.streamFps,
                                preferSnapshot = ui.preferSnapshot,
                                printBusy = printBusy,
                                cameras = ui.webcams,
                                onPickCamera = viewModel::setDashboardCamera,
                            )
                        }
                        DashboardWidgets.CONTROL -> ControlWidget(
                            status = ui.printStatus,
                            onPause = viewModel::pausePrint,
                            onResume = viewModel::resumePrint,
                            onCancelPrint = viewModel::cancelPrint,
                            onEmergencyStop = viewModel::emergencyStop,
                            onSpeed = viewModel::setSpeedPercent,
                            onFlow = viewModel::setFlowPercent,
                        )
                        DashboardWidgets.TEMPS -> TempsWidget(
                            heaters = ui.heaters,
                            unit = ui.tempUnit,
                        )
                    }
                }
            }

            ui.message?.let { msg ->
                item {
                    Text(
                        text = msg,
                        color = if (ui.isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun PrintProgressWidget(
    status: PrintStatus,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancelPrint: () -> Unit,
) {
    var confirmCancel by remember { mutableStateOf(false) }
    val progress = status.progress.coerceIn(0f, 1f)
    DashboardCard(title = "Print progress") {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = status.filename.ifBlank { "No active file" },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = status.state.replaceFirstChar { it.titlecase() },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "%.1f%%".format(progress * 100f),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric("ETA", status.etaRemainingSec?.let { formatDuration(it) } ?: "—", Modifier.weight(1f))
            Metric("Layer", formatLayers(status.currentLayer, status.totalLayers), Modifier.weight(1f))
            Metric("Speed", "%.0f%%".format(status.speedFactor * 100f), Modifier.weight(1f))
        }
        when (status.state) {
            "printing" -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onPause, modifier = Modifier.weight(1f)) { Text("Pause") }
                    OutlinedButton(
                        onClick = { confirmCancel = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Cancel…")
                    }
                }
            }
            "paused" -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onResume, modifier = Modifier.weight(1f)) { Text("Resume") }
                    OutlinedButton(
                        onClick = { confirmCancel = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Cancel…")
                    }
                }
            }
        }
    }
    if (confirmCancel) {
        ConfirmCancelPrintDialog(
            onDismiss = { confirmCancel = false },
            onConfirm = {
                confirmCancel = false
                onCancelPrint()
            },
        )
    }
}

@Composable
private fun CameraWidgetCard(
    printer: PrinterConfig,
    camera: WebcamEntry?,
    client: okhttp3.OkHttpClient,
    resolveUrl: (String) -> String,
    streamFps: Int,
    preferSnapshot: Boolean,
    printBusy: Boolean,
    cameras: List<WebcamEntry>,
    onPickCamera: (String) -> Unit,
) {
    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Camera",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (cameras.isNotEmpty()) {
                    CameraPicker(
                        cameras = cameras,
                        selectedKey = camera?.orderKey().orEmpty(),
                        onSelect = onPickCamera,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (camera == null) {
                Text(
                    text = "No cameras configured on this printer",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp)),
                ) {
                    CameraPlayer(
                        printer = printer,
                        webcam = camera,
                        client = client,
                        resolveUrl = resolveUrl,
                        isPrinterActive = printBusy,
                        streamFps = streamFps,
                        preferSnapshot = preferSnapshot,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlWidget(
    status: PrintStatus,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancelPrint: () -> Unit,
    onEmergencyStop: () -> Unit,
    onSpeed: (Int) -> Unit,
    onFlow: (Int) -> Unit,
) {
    var confirmCancel by remember { mutableStateOf(false) }
    var confirmEstop by remember { mutableStateOf(false) }
    var speed by remember(status.speedFactor) {
        mutableFloatStateOf((status.speedFactor * 100f).coerceIn(1f, 200f))
    }
    var flow by remember(status.extrudeFactor) {
        mutableFloatStateOf((status.extrudeFactor * 100f).coerceIn(1f, 200f))
    }

    DashboardCard(title = "Controls") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (status.state) {
                "printing" -> Button(onClick = onPause, modifier = Modifier.weight(1f)) { Text("Pause") }
                "paused" -> Button(onClick = onResume, modifier = Modifier.weight(1f)) { Text("Resume") }
                else -> Button(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) {
                    Text("Idle")
                }
            }
            OutlinedButton(
                onClick = { confirmCancel = true },
                enabled = status.state in setOf("printing", "paused"),
                modifier = Modifier.weight(1f),
            ) { Text("Cancel…") }
        }
        OutlinedButton(
            onClick = { confirmEstop = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Emergency stop", color = MaterialTheme.colorScheme.error)
        }
        Text("Speed ${speed.roundToInt()}%", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = speed,
            onValueChange = { speed = it },
            valueRange = 10f..200f,
            onValueChangeFinished = { onSpeed(speed.roundToInt()) },
        )
        Text("Flow ${flow.roundToInt()}%", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = flow,
            onValueChange = { flow = it },
            valueRange = 10f..200f,
            onValueChangeFinished = { onFlow(flow.roundToInt()) },
        )
    }
    if (confirmCancel) {
        ConfirmCancelPrintDialog(
            onDismiss = { confirmCancel = false },
            onConfirm = {
                confirmCancel = false
                onCancelPrint()
            },
        )
    }
    if (confirmEstop) {
        ConfirmEmergencyStopDialog(
            onDismiss = { confirmEstop = false },
            onConfirm = {
                confirmEstop = false
                onEmergencyStop()
            },
        )
    }
}

@Composable
private fun TempsWidget(
    heaters: List<HeaterReading>,
    unit: TempUnit,
) {
    DashboardCard(title = "Temperatures") {
        if (heaters.isEmpty()) {
            Text(
                text = "Waiting for temperature data…",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            heaters.take(6).forEach { heater ->
                val current = heater.temperature.toDisplayTemp(unit)
                val target = if (heater.target > 0f) heater.target.toDisplayTemp(unit) else 0f
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = heater.name.removePrefix("temperature_sensor "),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = if (target > 0f) {
                            "%.1f → %.0f %s".format(current, target, unit.label())
                        } else {
                            "%.1f %s".format(current, unit.label())
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun CameraPicker(
    cameras: List<WebcamEntry>,
    selectedKey: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = cameras.firstOrNull { it.orderKey() == selectedKey } ?: cameras.firstOrNull()
    val label = selected?.name?.ifBlank { "Camera" } ?: "Pick camera"
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = cameras.isNotEmpty()) { expanded = true }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = "Pick camera",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            cameras.forEach { cam ->
                val key = cam.orderKey()
                DropdownMenuItem(
                    text = {
                        Text(
                            text = cam.name.ifBlank { key },
                            fontWeight = if (key == selectedKey) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(key)
                    },
                )
            }
        }
    }
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

private fun formatLayers(current: Int?, total: Int?): String = when {
    current != null && total != null -> "$current / $total"
    current != null -> "$current"
    else -> "—"
}

private fun formatDuration(seconds: Float): String {
    val total = seconds.roundToInt().coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
