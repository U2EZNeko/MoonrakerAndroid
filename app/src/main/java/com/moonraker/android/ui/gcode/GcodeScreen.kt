package com.moonraker.android.ui.gcode

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moonraker.android.data.moonraker.GcodeFileEntry
import com.moonraker.android.data.moonraker.GcodeMetadata
import com.moonraker.android.data.moonraker.GcodePathPreview
import com.moonraker.android.ui.common.AppBarTitle
import com.moonraker.android.ui.common.EmptyState
import com.moonraker.android.ui.common.PrinterSwitcher
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GcodeScreen(viewModel: GcodeViewModel) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val selected = ui.selectedPath

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AppBarTitle(
                        title = when {
                            selected == null -> "G-code"
                            ui.isLivePreview -> "Live · ${selected.substringAfterLast('/')}"
                            else -> selected.substringAfterLast('/')
                        },
                    )
                },
                navigationIcon = {
                    if (selected != null) {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
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
        when {
            ui.activePrinter == null -> EmptyState(
                message = "Add a printer in Settings",
                modifier = Modifier.padding(padding),
            )

            selected != null -> PreviewPane(
                ui = ui,
                padding = padding,
                onStart = viewModel::startPrint,
            )

            ui.loading && ui.files.isEmpty() -> EmptyState(
                message = "Loading G-code files…",
                modifier = Modifier.padding(padding),
            )

            ui.error != null && ui.files.isEmpty() -> EmptyState(
                message = ui.error ?: "Error",
                modifier = Modifier.padding(padding),
            )

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (ui.currentJobFilename.isNotBlank() &&
                        ui.currentJobState in setOf("printing", "paused")
                    ) {
                        item {
                            CurrentJobCard(
                                filename = ui.currentJobFilename,
                                state = ui.currentJobState,
                                progress = ui.currentJobProgress,
                                onOpen = viewModel::openCurrentJob,
                            )
                        }
                    }

                    if (ui.files.isEmpty()) {
                        item {
                            Text(
                                text = ui.message ?: "No G-code files on printer",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(ui.files, key = { it.path }) { file ->
                            FileRow(
                                file = file,
                                isCurrent = file.path == ui.currentJobFilename,
                                onClick = { viewModel.selectFile(file.path) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentJobCard(
    filename: String,
    state: String,
    progress: Float,
    onOpen: () -> Unit,
) {
    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Current print", style = MaterialTheme.typography.titleMedium)
            Text(
                text = filename,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${state.replaceFirstChar { it.titlecase() }} · ${(progress * 100f).roundToInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                Text("Open live preview")
            }
        }
    }
}

@Composable
private fun FileRow(
    file: GcodeFileEntry,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        tonalElevation = if (isCurrent) 3.dp else 1.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = file.path,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = buildString {
                    append(formatSize(file.size))
                    append(" · ")
                    append(formatDate(file.modified))
                    if (isCurrent) append(" · printing")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PreviewPane(
    ui: GcodeUiState,
    padding: PaddingValues,
    onStart: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (ui.previewLoading) {
            item {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        if (ui.isLivePreview) {
            item {
                Surface(
                    tonalElevation = 1.dp,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Live toolpath",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "${ui.currentJobState.replaceFirstChar { it.titlecase() }} · " +
                                "${(ui.currentJobProgress * 100f).roundToInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        LinearProgressIndicator(
                            progress = { ui.currentJobProgress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val x = ui.liveX
                        val y = ui.liveY
                        if (x != null && y != null) {
                            Text(
                                text = "Nozzle  X %.2f  Y %.2f".format(x, y),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        item {
            ToolpathCard(
                pathPreview = ui.pathPreview,
                liveX = if (ui.isLivePreview) ui.liveX else null,
                liveY = if (ui.isLivePreview) ui.liveY else null,
                liveLayer = if (ui.isLivePreview) ui.liveLayer else null,
                liveProgress = if (ui.isLivePreview) ui.currentJobProgress else null,
                followLive = ui.isLivePreview,
            )
        }

        ui.metadata?.let { meta ->
            item { MetadataCard(meta) }
        }

        if (!ui.isLivePreview) {
            item {
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start print")
                }
            }
        } else {
            item {
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Live preview of active job")
                }
            }
        }

        ui.message?.let { msg ->
            item {
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ToolpathCard(
    pathPreview: GcodePathPreview?,
    liveX: Float?,
    liveY: Float?,
    liveLayer: Int?,
    liveProgress: Float?,
    followLive: Boolean,
) {
    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (pathPreview != null && pathPreview.segments.isNotEmpty()) {
                Text("Toolpath", style = MaterialTheme.typography.titleSmall)
                GcodePreviewViewer(
                    preview = pathPreview,
                    liveX = liveX,
                    liveY = liveY,
                    liveLayer = liveLayer,
                    liveProgress = liveProgress,
                    followLive = followLive,
                )
            } else {
                Text(
                    text = "No toolpath available for this file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun MetadataCard(meta: GcodeMetadata) {
    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Details", style = MaterialTheme.typography.titleMedium)
            MetaRow("Size", formatSize(meta.size))
            meta.estimatedTimeSec?.let { MetaRow("Estimate", formatDuration(it)) }
            meta.layerCount?.let { MetaRow("Layers", it.toString()) }
            meta.layerHeight?.let { MetaRow("Layer height", "%.2f mm".format(it)) }
            meta.objectHeight?.let { MetaRow("Object height", "%.2f mm".format(it)) }
            meta.filamentTotalMm?.let { MetaRow("Filament", "%.1f m".format(it / 1000f)) }
            meta.filamentWeightG?.let { MetaRow("Weight", "%.1f g".format(it)) }
            val slicer = listOfNotNull(meta.slicer, meta.slicerVersion).joinToString(" ")
            if (slicer.isNotBlank()) MetaRow("Slicer", slicer)
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.0f KB".format(kb)
    val mb = kb / 1024.0
    return if (mb < 1024) "%.1f MB".format(mb) else "%.2f GB".format(mb / 1024.0)
}

private fun formatDate(epochSec: Double): String {
    val millis = when {
        epochSec > 1e12 -> epochSec.roundToLong()
        else -> (epochSec * 1000.0).roundToLong()
    }
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(millis))
}

private fun formatDuration(sec: Float): String {
    val total = sec.roundToLong().coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%dh %02dm".format(h, m) else "%dm %02ds".format(m, s)
}
