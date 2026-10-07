package com.moonraker.android.ui.temps

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moonraker.android.data.settings.TempUnit
import com.moonraker.android.data.settings.label
import com.moonraker.android.ui.common.AppBarTitle
import com.moonraker.android.ui.common.EmptyState
import com.moonraker.android.ui.common.PrinterSwitcher
import kotlin.math.ceil
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TempsScreen(viewModel: TempsViewModel) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    val axisLabelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Scaffold(
        topBar = {
            TopAppBar(
                title = { AppBarTitle(title = "Temps") },
                actions = {
                    IconButton(onClick = viewModel::refreshHistory) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload history")
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

            ui.series.isEmpty() -> EmptyState(
                message = when {
                    ui.loadingHistory -> "Loading temperature history…"
                    ui.connected -> "Waiting for temperature history…"
                    else -> ui.connectionMessage
                },
                modifier = Modifier.padding(padding),
            )

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = historyWindowLabel(ui.windowSeconds),
                            style = MaterialTheme.typography.labelLarge,
                            color = axisLabelColor,
                            modifier = Modifier.weight(1f),
                        )
                        FilterChip(
                            selected = ui.showPower,
                            onClick = viewModel::togglePower,
                            label = { Text("Power") },
                        )
                    }

                    Surface(
                        tonalElevation = 1.dp,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(280.dp),
                            ) {
                                YAxisLabels(
                                    series = ui.series.filter { it.visible },
                                    color = axisLabelColor,
                                    modifier = Modifier
                                        .width(40.dp)
                                        .fillMaxHeight(),
                                )
                                ThermalHistoryChart(
                                    series = ui.series.filter { it.visible },
                                    showPower = ui.showPower,
                                    gridColor = gridColor,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            TimeAxisLabels(
                                windowSeconds = ui.windowSeconds,
                                color = axisLabelColor,
                                modifier = Modifier.padding(start = 40.dp),
                            )
                        }
                    }

                    Text("Sensors", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Tap to show/hide. Solid = temp, dashed = target, fill = heater power.",
                        style = MaterialTheme.typography.bodySmall,
                        color = axisLabelColor,
                    )
                    ui.series.forEach { series ->
                        LegendRow(
                            series = series,
                            unit = ui.tempUnit,
                            onClick = { viewModel.toggleSeries(series.name) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun YAxisLabels(
    series: List<ChartSeries>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val allTemps = series.flatMap { it.temperatures }
    val allTargets = series.flatMap { it.targets.filter { t -> t > 0f } }
    val maxY = max(
        allTemps.maxOrNull() ?: 50f,
        max(allTargets.maxOrNull() ?: 0f, 50f),
    ).let { ceil(it / 10f) * 10f }.coerceAtLeast(50f)
    val steps = 5
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End,
    ) {
        for (i in steps downTo 0) {
            val value = maxY * i / steps
            Text(
                text = "%.0f".format(value),
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
        }
    }
}

@Composable
private fun LegendRow(
    series: ChartSeries,
    unit: TempUnit,
    onClick: () -> Unit,
) {
    val suffix = unit.label()
    Surface(
        tonalElevation = if (series.visible) 1.dp else 0.dp,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(
                        color = if (series.visible) series.color else series.color.copy(alpha = 0.25f),
                        shape = CircleShape,
                    )
                    .border(1.dp, series.color, CircleShape),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = series.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (series.visible) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    text = if (series.target > 0f) {
                        "%.1f%s → %.0f%s".format(series.current, suffix, series.target, suffix)
                    } else {
                        "%.1f%s".format(series.current, suffix)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (series.power > 0.01f) {
                Text(
                    text = "%.0f%%".format(series.power * 100f),
                    style = MaterialTheme.typography.labelLarge,
                    color = series.color,
                )
            }
        }
    }
}

@Composable
private fun TimeAxisLabels(
    windowSeconds: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (windowSeconds <= 1) return
    val marks = listOf(1f, 0.75f, 0.5f, 0.25f, 0f)
    Row(modifier = modifier.fillMaxWidth()) {
        marks.forEachIndexed { index, fraction ->
            val secondsAgo = (windowSeconds * fraction).toInt()
            Text(
                text = formatAgo(secondsAgo),
                style = MaterialTheme.typography.labelSmall,
                color = color,
                modifier = Modifier.weight(1f),
                textAlign = when (index) {
                    0 -> TextAlign.Start
                    marks.lastIndex -> TextAlign.End
                    else -> TextAlign.Center
                },
            )
        }
    }
}

@Composable
private fun ThermalHistoryChart(
    series: List<ChartSeries>,
    showPower: Boolean,
    gridColor: Color,
    modifier: Modifier = Modifier,
) {
    if (series.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No visible sensors")
        }
        return
    }

    val allTemps = series.flatMap { it.temperatures }
    val allTargets = series.flatMap { it.targets.filter { t -> t > 0f } }
    val minY = 0f
    val maxY = max(
        allTemps.maxOrNull() ?: 50f,
        max(allTargets.maxOrNull() ?: 0f, 50f),
    ).let { ceil(it / 10f) * 10f }.coerceAtLeast(50f)

    Canvas(modifier = modifier) {
        val chartW = size.width
        val chartH = size.height
        if (chartW <= 0f || chartH <= 0f) return@Canvas

        val range = (maxY - minY).coerceAtLeast(1f)
        fun xAt(index: Int, count: Int): Float {
            if (count <= 1) return 0f
            return chartW * index / (count - 1)
        }
        fun yTemp(value: Float): Float =
            chartH - ((value - minY) / range) * chartH

        val steps = 5
        for (i in 0..steps) {
            val value = minY + range * i / steps
            val y = yTemp(value)
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(chartW, y),
                strokeWidth = 1f,
            )
        }

        if (showPower) {
            series.forEach { s ->
                val powers = s.powers
                val count = max(s.temperatures.size, powers.size)
                if (count < 2) return@forEach
                val path = Path()
                for (i in 0 until count) {
                    val p = powers.getOrNull(i) ?: powers.lastOrNull() ?: 0f
                    val x = xAt(i, count)
                    val y = chartH - (p.coerceIn(0f, 1f) * chartH * 0.35f)
                    if (i == 0) {
                        path.moveTo(x, chartH)
                        path.lineTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }
                path.lineTo(xAt(count - 1, count), chartH)
                path.close()
                drawPath(path, color = s.color.copy(alpha = 0.12f))
            }
        }

        val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
        series.forEach { s ->
            val targets = s.targets
            val count = max(s.temperatures.size, targets.size)
            if (count < 2) return@forEach
            val path = Path()
            var started = false
            val step = max(1, count / chartW.toInt().coerceAtLeast(1))
            for (i in 0 until count step step) {
                val t = targets.getOrNull(i) ?: continue
                if (t <= 0f) {
                    started = false
                    continue
                }
                val x = xAt(i, count)
                val y = yTemp(t)
                if (!started) {
                    path.moveTo(x, y)
                    started = true
                } else {
                    path.lineTo(x, y)
                }
            }
            val last = targets.lastOrNull() ?: 0f
            if (last > 0f) {
                val x = xAt(count - 1, count)
                val y = yTemp(last)
                if (started) path.lineTo(x, y) else path.moveTo(x, y)
            }
            drawPath(
                path = path,
                color = s.color.copy(alpha = 0.55f),
                style = Stroke(width = 2f, pathEffect = dash, cap = StrokeCap.Round),
            )
        }

        series.forEach { s ->
            val temps = s.temperatures
            if (temps.size < 2) return@forEach
            val path = Path()
            val step = max(1, temps.size / chartW.toInt().coerceAtLeast(1))
            temps.forEachIndexed { index, value ->
                if (index % step != 0 && index != temps.lastIndex) return@forEachIndexed
                val x = xAt(index, temps.size)
                val y = yTemp(value)
                if (path.isEmpty) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = s.color,
                style = Stroke(width = 3.2f, cap = StrokeCap.Round),
            )
        }
    }
}

private fun historyWindowLabel(seconds: Int): String {
    if (seconds <= 0) return "No history yet"
    val mins = seconds / 60f
    return if (mins >= 1f) {
        "History · last %.0f min (%d samples @ 1 Hz)".format(mins, seconds)
    } else {
        "History · last %d sec".format(seconds)
    }
}

private fun formatAgo(secondsAgo: Int): String {
    if (secondsAgo <= 0) return "now"
    val m = secondsAgo / 60
    val s = secondsAgo % 60
    return if (m > 0) "-${m}m" else "-${s}s"
}
