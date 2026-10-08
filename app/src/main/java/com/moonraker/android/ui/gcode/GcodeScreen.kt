package com.moonraker.android.ui.gcode

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.moonraker.android.data.moonraker.GcodeFileEntry
import com.moonraker.android.data.moonraker.GcodeMetadata
import com.moonraker.android.data.moonraker.GcodePathPreview
import com.moonraker.android.ui.common.AppBarTitle
import com.moonraker.android.ui.common.EmptyState
import com.moonraker.android.ui.common.PrinterSwitcher
import java.text.DateFormat
import java.util.Date
import kotlin.math.min
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
                        title = if (selected == null) {
                            "G-code"
                        } else {
                            selected.substringAfterLast('/')
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

            ui.files.isEmpty() -> EmptyState(
                message = ui.message ?: "No G-code files on printer",
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
                    items(ui.files, key = { it.path }) { file ->
                        FileRow(file = file, onClick = { viewModel.selectFile(file.path) })
                    }
                }
            }
        }
    }
}

@Composable
private fun FileRow(file: GcodeFileEntry, onClick: () -> Unit) {
    Surface(
        tonalElevation = 1.dp,
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
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${formatSize(file.size)} · ${formatDate(file.modified)}",
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
    val apiKey = ui.activePrinter?.apiKey.orEmpty()
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

        item {
            ThumbnailOrPath(
                thumbnailUrl = ui.thumbnailUrl,
                pathPreview = ui.pathPreview,
                apiKey = apiKey,
            )
        }

        ui.metadata?.let { meta ->
            item { MetadataCard(meta) }
        }

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
private fun ThumbnailOrPath(
    thumbnailUrl: String?,
    pathPreview: GcodePathPreview?,
    apiKey: String,
) {
    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (thumbnailUrl != null) {
                val request = ImageRequest.Builder(LocalContext.current)
                    .data(thumbnailUrl)
                    .apply {
                        if (apiKey.isNotBlank()) addHeader("X-Api-Key", apiKey)
                    }
                    .crossfade(true)
                    .build()
                AsyncImage(
                    model = request,
                    contentDescription = "Slicer thumbnail",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                )
            }
            if (pathPreview != null && pathPreview.segments.isNotEmpty()) {
                Text("Toolpath (top view)", style = MaterialTheme.typography.titleSmall)
                PathCanvas(
                    preview = pathPreview,
                    travelColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    extrudeColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                )
            } else if (thumbnailUrl == null) {
                Text(
                    text = "No thumbnail or toolpath available for this file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun PathCanvas(
    preview: GcodePathPreview,
    travelColor: Color,
    extrudeColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val pad = 12.dp.toPx()
        val availW = size.width - pad * 2
        val availH = size.height - pad * 2
        val scale = min(availW / preview.width, availH / preview.height)
        val drawW = preview.width * scale
        val drawH = preview.height * scale
        val originX = pad + (availW - drawW) / 2f
        val originY = pad + (availH - drawH) / 2f

        fun mapX(x: Float) = originX + (x - preview.minX) * scale
        fun mapY(y: Float) = originY + drawH - (y - preview.minY) * scale

        for (seg in preview.segments) {
            drawLine(
                color = if (seg.extruding) extrudeColor else travelColor,
                start = Offset(mapX(seg.x0), mapY(seg.y0)),
                end = Offset(mapX(seg.x1), mapY(seg.y1)),
                strokeWidth = if (seg.extruding) 2.5f else 1.2f,
                cap = StrokeCap.Round,
            )
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
