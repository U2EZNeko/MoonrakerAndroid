package com.moonraker.android.ui.print

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moonraker.android.data.moonraker.PrintStatus
import com.moonraker.android.ui.common.AppBarTitle
import com.moonraker.android.ui.common.EmptyState
import com.moonraker.android.ui.common.PrinterSwitcher
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintScreen(viewModel: PrintViewModel) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmCancel by remember { mutableStateOf(false) }
    KeepScreenOn(
        enabled = ui.keepScreenOn && ui.printStatus.state in setOf("printing", "paused"),
    )

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.uploadAndStart(context, uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { AppBarTitle(title = "Print") },
                actions = {
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PrintStatusCard(
                status = ui.printStatus,
                onPause = viewModel::pausePrint,
                onResume = viewModel::resumePrint,
                onRequestCancel = { confirmCancel = true },
            )

            Button(
                onClick = {
                    picker.launch(
                        arrayOf(
                            "application/octet-stream",
                            "text/plain",
                            "*/*",
                        ),
                    )
                },
                enabled = !ui.uploading && ui.printStatus.state !in setOf("printing", "paused"),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (ui.uploading) "Uploading…" else "Pick local G-code & start")
            }

            ui.message?.let {
                Text(
                    text = it,
                    color = if (ui.isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            Text(
                text = "Files upload to gcodes/ on ${printer.name}, then printing starts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Cancel print?") },
            text = {
                Text(
                    "This will abort the current job on the printer. " +
                        "Only continue if you really mean to stop it.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmCancel = false
                        viewModel.cancelPrint()
                    },
                ) {
                    Text("Yes, cancel print", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) {
                    Text("Keep printing")
                }
            },
        )
    }
}

@Composable
private fun PrintStatusCard(
    status: PrintStatus,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRequestCancel: () -> Unit,
) {
    val progress = status.progress.coerceIn(0f, 1f)
    val posX = status.liveX ?: status.gcodeX
    val posY = status.liveY ?: status.gcodeY
    val posZ = status.liveZ ?: status.gcodeZ

    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Job status",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = status.state.replaceFirstChar { it.titlecase() },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Text(
                text = status.filename.ifBlank { "No active file" },
                style = MaterialTheme.typography.bodyLarge,
            )

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "%.1f%%".format(progress * 100f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            StatusGrid(
                rows = listOf(
                    "Print time" to formatDuration(status.printDurationSec),
                    "Total time" to formatDuration(status.totalDurationSec),
                    "ETA left" to (status.etaRemainingSec?.let { formatDuration(it) } ?: "—"),
                    "Layer" to formatLayers(status.currentLayer, status.totalLayers),
                    "Filament" to "%.1f m".format(status.filamentUsedMm / 1000f),
                    "Speed" to formatSpeed(status),
                    "Flow" to "%.0f%%".format(status.extrudeFactor * 100f),
                    "Homed" to status.homedAxes.ifBlank { "—" }.uppercase(),
                    "Extruder" to status.activeExtruder.ifBlank { "—" },
                ),
            )

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Nozzle position", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "X ${fmtAxis(posX)}   Y ${fmtAxis(posY)}   Z ${fmtAxis(posZ)}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                    )
                    if (status.gcodeX != null && status.liveX != null) {
                        Text(
                            text = "G-code  X ${fmtAxis(status.gcodeX)}  Y ${fmtAxis(status.gcodeY)}  Z ${fmtAxis(status.gcodeZ)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    status.liveVelocity?.let { velocity ->
                        Text(
                            text = "Live velocity  %.1f mm/s".format(velocity),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (status.message.isNotBlank()) {
                Text(
                    text = status.message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            when (status.state) {
                "printing" -> {
                    Button(
                        onClick = onPause,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Pause print") }
                    OutlinedButton(
                        onClick = onRequestCancel,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Cancel print…") }
                }
                "paused" -> {
                    Button(
                        onClick = onResume,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Resume print") }
                    OutlinedButton(
                        onClick = onRequestCancel,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Cancel print…") }
                }
            }
        }
    }
}

@Composable
private fun StatusGrid(rows: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                pair.forEach { (label, value) ->
                    Column(modifier = Modifier.weight(1f)) {
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
                if (pair.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun fmtAxis(value: Float?): String =
    value?.let { "%.2f".format(it) } ?: "—"

private fun formatLayers(current: Int?, total: Int?): String = when {
    current != null && total != null -> "$current / $total"
    current != null -> "$current"
    else -> "—"
}

private fun formatSpeed(status: PrintStatus): String {
    val factor = "%.0f%%".format(status.speedFactor * 100f)
    val live = status.liveVelocity?.let { " · %.0f mm/s".format(it) }.orEmpty()
    return factor + live
}

private fun formatDuration(seconds: Float): String {
    val total = seconds.roundToInt().coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}
