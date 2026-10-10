package com.moonraker.android.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue
import com.moonraker.android.data.moonraker.FanReading
import com.moonraker.android.data.moonraker.GcodePathPreview
import com.moonraker.android.data.moonraker.HeaterReading
import com.moonraker.android.data.moonraker.PrintStatus
import com.moonraker.android.data.moonraker.WebcamEntry
import com.moonraker.android.data.settings.DashboardTile
import com.moonraker.android.data.settings.DashboardTileType
import com.moonraker.android.data.settings.PrinterConfig
import com.moonraker.android.data.settings.TempUnit
import com.moonraker.android.data.settings.orderKey
import com.moonraker.android.data.settings.toDisplayTemp
import com.moonraker.android.ui.camera.CameraPlayer
import com.moonraker.android.ui.common.AppBarTitle
import com.moonraker.android.ui.common.ConfirmCancelPrintDialog
import com.moonraker.android.ui.common.ConfirmEmergencyStopDialog
import com.moonraker.android.ui.common.EmptyState
import com.moonraker.android.ui.common.PrinterSwitcher
import com.moonraker.android.ui.gcode.GcodePreviewViewer
import kotlin.math.roundToInt

private val CellHeight = 96.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val printBusy = ui.printStatus.state in setOf("printing", "paused")
    val hasCamera = ui.tiles.any { it.type == DashboardTileType.CAMERA }
    KeepScreenOn(enabled = ui.keepScreenOn && (printBusy || hasCamera))

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AppBarTitle(
                        title = if (ui.editing) "Edit dashboard" else "Dashboard",
                        subtitle = if (ui.editing) {
                            "Long-press drag to move · corner handle to resize"
                        } else {
                            ui.connectionMessage
                        },
                        subtitleColor = if (ui.connected || ui.editing) {
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
                    IconButton(onClick = { viewModel.setEditing(!ui.editing) }) {
                        Icon(
                            if (ui.editing) Icons.Default.Close else Icons.Default.Edit,
                            contentDescription = if (ui.editing) "Done" else "Edit",
                        )
                    }
                    if (!ui.editing) {
                        PrinterSwitcher(
                            printers = ui.printers,
                            activeId = ui.activePrinter?.id,
                            onSelect = viewModel::setActivePrinter,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (ui.editing) {
                FloatingActionButton(onClick = viewModel::openPicker) {
                    Icon(Icons.Default.Add, contentDescription = "Add widget")
                }
            }
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

        val scrollState = rememberScrollState()
        val density = LocalDensity.current
        var viewportTop by remember { mutableFloatStateOf(0f) }
        var viewportBottom by remember { mutableFloatStateOf(0f) }
        var edgeScrollSpeed by remember { mutableFloatStateOf(0f) }
        var dragScrollDeltaY by remember { mutableFloatStateOf(0f) }
        val edgeZonePx = with(density) { 72.dp.toPx() }

        LaunchedEffect(edgeScrollSpeed) {
            while (edgeScrollSpeed.absoluteValue > 0.01f) {
                val before = scrollState.value
                scrollState.scrollBy(edgeScrollSpeed)
                val delta = (scrollState.value - before).toFloat()
                if (delta != 0f) dragScrollDeltaY += delta
                delay(16)
            }
        }

        PullToRefreshBox(
            isRefreshing = ui.refreshing && !ui.editing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .onGloballyPositioned { coords ->
                    val bounds = coords.boundsInWindow()
                    viewportTop = bounds.top
                    viewportBottom = bounds.bottom
                },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            ) {
                if (ui.editing) {
                    EditToolbar(
                        hasSelection = ui.selectedTileId != null,
                        onDelete = viewModel::removeSelected,
                        onReset = viewModel::resetLayout,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                DashboardGridCanvas(
                    tiles = ui.tiles,
                    editing = ui.editing,
                    selectedId = ui.selectedTileId,
                    onSelect = viewModel::selectTile,
                    onMoveTo = viewModel::moveTileTo,
                    onResizeTo = viewModel::resizeTileTo,
                    onDelete = viewModel::removeTile,
                    parentScrollDeltaY = dragScrollDeltaY,
                    onDragWindowY = { windowY ->
                        if (windowY == null || !ui.editing) {
                            edgeScrollSpeed = 0f
                            dragScrollDeltaY = 0f
                            return@DashboardGridCanvas
                        }
                        val topEdge = viewportTop + edgeZonePx
                        val bottomEdge = viewportBottom - edgeZonePx
                        edgeScrollSpeed = when {
                            windowY < topEdge -> {
                                val t = ((topEdge - windowY) / edgeZonePx).coerceIn(0.25f, 1.5f)
                                -3.5f * t
                            }
                            windowY > bottomEdge -> {
                                val t = ((windowY - bottomEdge) / edgeZonePx).coerceIn(0.25f, 1.5f)
                                3.5f * t
                            }
                            else -> 0f
                        }
                    },
                    cellHeight = CellHeight,
                ) { tile, modifier ->
                    TileHost(
                        tile = tile,
                        ui = ui,
                        printer = printer,
                        viewModel = viewModel,
                        printBusy = printBusy,
                        editing = ui.editing,
                        modifier = modifier,
                    )
                }

                ui.message?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = msg,
                        color = if (ui.isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (ui.editing) {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }
    }

    if (ui.showPicker) {
        WidgetPickerDialog(
            options = ui.catalog,
            onDismiss = viewModel::dismissPicker,
            onPick = viewModel::addFromCatalog,
        )
    }

    ui.quickControl?.let { control ->
        QuickControlSheet(
            control = control,
            status = ui.printStatus,
            onDismiss = viewModel::dismissQuickControl,
            onSpeed = viewModel::setSpeedPercent,
            onFlow = viewModel::setFlowPercent,
        )
    }

}

@Composable
private fun EditToolbar(
    hasSelection: Boolean,
    onDelete: () -> Unit,
    onReset: () -> Unit,
) {
    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Long-press to drag · red × deletes · blue corner resizes",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onDelete,
                    enabled = hasSelection,
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Remove")
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onReset) { Text("Reset layout") }
            }
        }
    }
}

@Composable
private fun TileHost(
    tile: DashboardTile,
    ui: DashboardUiState,
    printer: PrinterConfig,
    viewModel: DashboardViewModel,
    printBusy: Boolean,
    editing: Boolean,
    modifier: Modifier,
) {
    val title = DashboardTileType.label(tile.type, tile.ref)
    fun openControl(control: QuickControl): (() -> Unit)? =
        if (editing) null else ({ viewModel.openQuickControl(control) })
    when (tile.type) {
        DashboardTileType.PRINT_PROGRESS -> TileCard(title, modifier) {
            PrintProgressBody(
                status = ui.printStatus,
                onPause = viewModel::pausePrint,
                onResume = viewModel::resumePrint,
                onCancelPrint = viewModel::cancelPrint,
            )
        }
        DashboardTileType.CONTROL -> TileCard(title, modifier) {
            ControlBody(
                status = ui.printStatus,
                onPause = viewModel::pausePrint,
                onResume = viewModel::resumePrint,
                onCancelPrint = viewModel::cancelPrint,
                onEmergencyStop = viewModel::emergencyStop,
            )
        }
        DashboardTileType.GCODE_PREVIEW -> TileCard(title, modifier) {
            GcodeBody(
                filename = ui.gcodePreviewFilename.ifBlank { ui.printStatus.filename },
                preview = ui.gcodePreview,
                loading = ui.gcodePreviewLoading,
                status = ui.printStatus,
            )
        }
        DashboardTileType.TEMPS -> TileCard(title, modifier) {
            TempsBody(heaters = ui.heaters, unit = ui.tempUnit)
        }
        DashboardTileType.CAMERA -> {
            val cam = viewModel.cameraFor(tile)
            key(ui.cameraEpoch, tile.ref, cam?.orderKey()) {
                TileCard(cam?.name?.ifBlank { title } ?: title, modifier) {
                    CameraBody(
                        printer = printer,
                        camera = cam,
                        client = viewModel.httpClient,
                        resolveUrl = viewModel::resolveUrl,
                        streamFps = ui.streamFps,
                        preferSnapshot = ui.preferSnapshot,
                        printBusy = printBusy,
                    )
                }
            }
        }
        DashboardTileType.HEATER -> {
            val heater = viewModel.heaterFor(tile)
            TileCard(title, modifier) {
                HeaterSparkBody(
                    heater = heater,
                    unit = ui.tempUnit,
                    sparkline = ui.heaterSparklines[tile.ref]
                        ?: ui.tempHistory.firstOrNull { it.name == tile.ref }?.temperatures?.takeLast(60)
                        ?: emptyList(),
                )
            }
        }
        DashboardTileType.FAN -> {
            val fan = viewModel.fanFor(tile)
            TileCard(title, modifier) {
                FanBody(fan = fan, onSet = viewModel::setFanSpeed)
            }
        }
        DashboardTileType.TEMP_CHART -> TileCard(title, modifier) {
            TempChartBody(series = ui.tempHistory, unit = ui.tempUnit)
        }
        DashboardTileType.PROGRESS_RING -> TileCard(title, modifier) {
            ProgressRingBody(status = ui.printStatus)
        }
        DashboardTileType.JOB_SUMMARY -> TileCard(title, modifier) {
            JobSummaryBody(status = ui.printStatus)
        }
        DashboardTileType.STAT_ETA -> TileCard(title, modifier) {
            StatCardBody(
                label = "ETA",
                value = formatEta(ui.printStatus.etaRemainingSec),
                hint = "remaining",
            )
        }
        DashboardTileType.STAT_LAYER -> TileCard(title, modifier) {
            StatCardBody(
                label = "Layer",
                value = formatLayer(ui.printStatus.currentLayer, ui.printStatus.totalLayers),
                hint = "current / total",
            )
        }
        DashboardTileType.STAT_FILAMENT -> TileCard(title, modifier) {
            StatCardBody(
                label = "Filament",
                value = formatFilament(ui.printStatus.filamentUsedMm),
                hint = "used",
            )
        }
        DashboardTileType.STAT_TIME -> TileCard(title, modifier) {
            StatCardBody(
                label = "Print time",
                value = formatDuration(ui.printStatus.printDurationSec),
                hint = "elapsed",
            )
        }
        DashboardTileType.SPEED_GAUGE -> TileCard(
            title = title,
            modifier = modifier,
            onClick = openControl(QuickControl.SPEED),
        ) {
            GaugeBody("Speed", ui.printStatus.speedFactor * 100f)
        }
        DashboardTileType.FLOW_GAUGE -> TileCard(
            title = title,
            modifier = modifier,
            onClick = openControl(QuickControl.FLOW),
        ) {
            GaugeBody("Flow", ui.printStatus.extrudeFactor * 100f)
        }
        DashboardTileType.SPEED_FLOW -> TileCard(
            title = title,
            modifier = modifier,
            onClick = openControl(QuickControl.SPEED_FLOW),
        ) {
            SpeedFlowBody(status = ui.printStatus)
        }
        DashboardTileType.POSITION -> TileCard(title, modifier) {
            PositionBody(status = ui.printStatus)
        }
        DashboardTileType.CONNECTION -> TileCard(title, modifier) {
            ConnectionBody(connected = ui.connected, message = ui.connectionMessage)
        }
        DashboardTileType.HOMED -> TileCard(title, modifier) {
            HomedBody(status = ui.printStatus)
        }
        DashboardTileType.HEATERS_STRIP -> TileCard(title, modifier) {
            HeatersStripBody(heaters = ui.heaters, unit = ui.tempUnit)
        }
        DashboardTileType.FANS_STRIP -> TileCard(title, modifier) {
            FansStripBody(fans = ui.fans)
        }
        DashboardTileType.LED -> {
            val led = viewModel.ledFor(tile)
            TileCard(title = title, modifier = modifier) {
                LedBody(
                    led = led,
                    onToggle = if (editing) {
                        null
                    } else {
                        { on -> viewModel.setLedOn(tile.ref, on) }
                    },
                )
            }
        }
        DashboardTileType.LEDS_STRIP -> TileCard(title = title, modifier = modifier) {
            LedsStripBody(
                leds = ui.leds,
                onToggle = if (editing) {
                    null
                } else {
                    { led, on -> viewModel.setLedOn(led.objectName, on) }
                },
            )
        }
        DashboardTileType.VELOCITY -> TileCard(title, modifier) {
            VelocityBody(status = ui.printStatus)
        }
        else -> TileCard(title, modifier) {
            Text("Unknown widget", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TileCard(
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            val showTitle = maxHeight >= 56.dp
            val compact = maxHeight < 100.dp
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp),
            ) {
                if (showTitle) {
                    Text(
                        text = title,
                        style = if (compact) {
                            MaterialTheme.typography.labelMedium
                        } else {
                            MaterialTheme.typography.titleSmall
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f, fill = true)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp)),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun WidgetPickerDialog(
    options: List<CatalogOption>,
    onDismiss: () -> Unit,
    onPick: (CatalogOption) -> Unit,
) {
    val grouped = remember(options) {
        options.groupBy { it.category }.toSortedMap()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add widget") },
        text = {
            if (options.isEmpty()) {
                Text("Nothing left to add — remove a tile or wait for cameras / heaters.")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    grouped.forEach { (category, itemsInCategory) ->
                        item(key = "cat-$category") {
                            Text(
                                text = category,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                            )
                        }
                        items(itemsInCategory, key = { "${it.type}:${it.ref}" }) { option ->
                            Surface(
                                tonalElevation = 1.dp,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(option) },
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(option.title, style = MaterialTheme.typography.titleSmall)
                                    if (option.subtitle.isNotBlank()) {
                                        Text(
                                            text = option.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun PrintProgressBody(
    status: PrintStatus,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancelPrint: () -> Unit,
) {
    var confirmCancel by remember { mutableStateOf(false) }
    val progress = status.progress.coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = status.filename.ifBlank { "No active file" },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Text(
            text = "${status.state.replaceFirstChar { it.titlecase() }} · %.0f%%".format(progress * 100f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        when (status.state) {
            "printing" -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onPause, modifier = Modifier.weight(1f)) { Text("Pause") }
                OutlinedButton(onClick = { confirmCancel = true }, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
            }
            "paused" -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onResume, modifier = Modifier.weight(1f)) { Text("Resume") }
                OutlinedButton(onClick = { confirmCancel = true }, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickControlSheet(
    control: QuickControl,
    status: PrintStatus,
    onDismiss: () -> Unit,
    onSpeed: (Int) -> Unit,
    onFlow: (Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var speed by remember(status.speedFactor) {
        mutableFloatStateOf((status.speedFactor * 100f).coerceIn(10f, 200f))
    }
    var flow by remember(status.extrudeFactor) {
        mutableFloatStateOf((status.extrudeFactor * 100f).coerceIn(10f, 200f))
    }
    val title = when (control) {
        QuickControl.SPEED -> "Speed"
        QuickControl.FLOW -> "Flow"
        QuickControl.SPEED_FLOW -> "Speed & flow"
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (control == QuickControl.SPEED || control == QuickControl.SPEED_FLOW) {
                Text(
                    text = "Speed ${speed.roundToInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = speed,
                    onValueChange = { speed = it },
                    valueRange = 10f..200f,
                    onValueChangeFinished = { onSpeed(speed.roundToInt()) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(50, 100, 150).forEach { preset ->
                        OutlinedButton(onClick = {
                            speed = preset.toFloat()
                            onSpeed(preset)
                        }) { Text("${preset}%") }
                    }
                }
            }
            if (control == QuickControl.FLOW || control == QuickControl.SPEED_FLOW) {
                Text(
                    text = "Flow ${flow.roundToInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = flow,
                    onValueChange = { flow = it },
                    valueRange = 10f..200f,
                    onValueChangeFinished = { onFlow(flow.roundToInt()) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(90, 100, 110).forEach { preset ->
                        OutlinedButton(onClick = {
                            flow = preset.toFloat()
                            onFlow(preset)
                        }) { Text("${preset}%") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlBody(
    status: PrintStatus,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancelPrint: () -> Unit,
    onEmergencyStop: () -> Unit,
) {
    var confirmCancel by remember { mutableStateOf(false) }
    var confirmEstop by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
            ) { Text("Cancel") }
        }
        OutlinedButton(onClick = { confirmEstop = true }, modifier = Modifier.fillMaxWidth()) {
            Text("E-stop", color = MaterialTheme.colorScheme.error)
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
private fun GcodeBody(
    filename: String,
    preview: GcodePathPreview?,
    loading: Boolean,
    status: PrintStatus,
) {
    val live = status.state in setOf("printing", "paused")
    when {
        loading -> Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        preview == null || preview.segments.isEmpty() -> Text(
            text = if (filename.isBlank()) "No active job" else "No toolpath yet",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        else -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                GcodePreviewViewer(
                    preview = preview,
                    liveX = if (live) status.liveX ?: status.gcodeX else null,
                    liveY = if (live) status.liveY ?: status.gcodeY else null,
                    liveLayer = status.currentLayer?.let { (it - 1).coerceAtLeast(0) },
                    liveProgress = if (live) status.progress else null,
                    followLive = live,
                )
            }
        }
    }
}

@Composable
private fun TempsBody(heaters: List<HeaterReading>, unit: TempUnit) {
    if (heaters.isEmpty()) {
        Text("Waiting…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        heaters.forEach { heater ->
            val current = heater.temperature.toDisplayTemp(unit)
            val target = if (heater.target > 0f) heater.target.toDisplayTemp(unit) else 0f
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = heater.name.removePrefix("temperature_sensor "),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (target > 0f) {
                        "%.0f→%.0f".format(current, target)
                    } else {
                        "%.1f".format(current)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun CameraBody(
    printer: PrinterConfig,
    camera: WebcamEntry?,
    client: okhttp3.OkHttpClient,
    resolveUrl: (String) -> String,
    streamFps: Int,
    preferSnapshot: Boolean,
    printBusy: Boolean,
) {
    if (camera == null) {
        Text("Camera missing", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
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
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun FanBody(fan: FanReading?, onSet: (String, Float) -> Unit) {
    if (fan == null) {
        Text("Offline", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    var draft by remember(fan.objectName, fan.speed) { mutableFloatStateOf(fan.speed) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "${(draft * 100f).roundToInt()}%",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        fan.rpm?.let {
            Text("%.0f RPM".format(it), style = MaterialTheme.typography.bodySmall)
        }
        if (fan.controllable) {
            Slider(
                value = draft,
                onValueChange = { draft = it },
                onValueChangeFinished = { onSet(fan.objectName, draft) },
                valueRange = 0f..1f,
            )
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
