package com.moonraker.android.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonraker.android.data.moonraker.FanReading
import com.moonraker.android.data.moonraker.HeaterReading
import com.moonraker.android.data.moonraker.LedReading
import com.moonraker.android.data.moonraker.PrintStatus
import com.moonraker.android.data.moonraker.TemperatureHistorySeries
import com.moonraker.android.data.settings.TempUnit
import com.moonraker.android.data.settings.label
import com.moonraker.android.data.settings.toDisplayTemp
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private val ChartPalette = listOf(
    Color(0xFFFF6B4E),
    Color(0xFF4FC3F7),
    Color(0xFF81C784),
    Color(0xFFFFD54F),
    Color(0xFFCE93D8),
    Color(0xFF80CBC4),
    Color(0xFFFF8A65),
    Color(0xFF90CAF9),
)

@Composable
fun ProgressRingBody(status: PrintStatus) {
    val progress = status.progress.coerceIn(0f, 1f)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val ring = minOf(maxWidth, maxHeight).coerceAtMost(120.dp)
        val showState = maxHeight >= 72.dp
        Canvas(modifier = Modifier.size(ring)) {
            val stroke = (ring.toPx() * 0.12f).coerceIn(6.dp.toPx(), 12.dp.toPx())
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(diameter, diameter),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = Size(diameter, diameter),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "%.0f%%".format(progress * 100f),
                style = if (ring < 72.dp) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.titleLarge
                },
                fontWeight = FontWeight.Bold,
            )
            if (showState) {
                Text(
                    text = status.state.replaceFirstChar { it.titlecase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun JobSummaryBody(status: PrintStatus) {
    val progress = status.progress.coerceIn(0f, 1f)
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val tall = maxHeight >= 120.dp
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = status.filename.ifBlank { "No active file" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatChip("State", status.state.replaceFirstChar { it.titlecase() })
                StatChip("Done", "%.0f%%".format(progress * 100f))
                StatChip("ETA", formatEta(status.etaRemainingSec))
            }
            if (tall) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StatChip(
                        "Layer",
                        formatLayer(status.currentLayer, status.totalLayers),
                    )
                    StatChip("Filament", formatFilament(status.filamentUsedMm))
                    StatChip("Time", formatDuration(status.printDurationSec))
                }
            }
        }
    }
}

@Composable
fun StatCardBody(label: String, value: String, hint: String = "") {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val height = maxHeight
        val width = maxWidth
        val big = height >= 64.dp && width >= 96.dp
        val showHint = hint.isNotBlank() && height >= 48.dp
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = value,
                style = if (big) {
                    MaterialTheme.typography.headlineSmall
                } else {
                    MaterialTheme.typography.titleMedium
                },
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showHint) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun GaugeBody(label: String, percent: Float, maxPercent: Float = 200f) {
    val value = percent.coerceIn(0f, maxPercent)
    val fraction = (value / maxPercent).coerceIn(0f, 1f)
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val height = maxHeight
        val gaugeH = (height * 0.55f).coerceIn(28.dp, 56.dp)
        val showLabel = height >= 72.dp
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(gaugeH),
            ) {
                val stroke = 8.dp.toPx()
                val sweep = 180f
                val start = 180f
                val diameter = min(size.width, size.height * 2f) - stroke
                val left = (size.width - diameter) / 2f
                val top = size.height - diameter / 2f - stroke / 2f
                drawArc(
                    color = track,
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(left, top),
                    size = Size(diameter, diameter),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = accent,
                    startAngle = start,
                    sweepAngle = sweep * fraction,
                    useCenter = false,
                    topLeft = Offset(left, top),
                    size = Size(diameter, diameter),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                val angle = Math.toRadians((start + sweep * fraction).toDouble())
                val cx = left + diameter / 2f
                val cy = top + diameter / 2f
                val r = diameter / 2f
                val tip = Offset(cx + (r * cos(angle)).toFloat(), cy + (r * sin(angle)).toFloat())
                drawCircle(color = accent, radius = 4.dp.toPx(), center = tip)
            }
            Text(
                text = "${value.roundToInt()}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            if (showLabel) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun SpeedFlowBody(status: PrintStatus) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            GaugeBody("Speed", status.speedFactor * 100f)
        }
        Box(modifier = Modifier.weight(1f)) {
            GaugeBody("Flow", status.extrudeFactor * 100f)
        }
    }
}

@Composable
fun PositionBody(status: PrintStatus) {
    val x = status.liveX ?: status.gcodeX
    val y = status.liveY ?: status.gcodeY
    val z = status.liveZ ?: status.gcodeZ
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AxisValue("X", x)
        AxisValue("Y", y)
        AxisValue("Z", z)
    }
}

@Composable
private fun AxisValue(axis: String, value: Float?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = axis,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value?.let { "%.2f".format(it) } ?: "—",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun ConnectionBody(connected: Boolean, message: String) {
    val color = if (connected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Canvas(modifier = Modifier.size(14.dp)) {
            drawCircle(color = color)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (connected) "Connected" else "Offline",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun HomedBody(status: PrintStatus) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Homed: ${status.homedAxes.ifBlank { "—" }.uppercase()}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Extruder: ${status.activeExtruder.ifBlank { "—" }}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun VelocityBody(status: PrintStatus) {
    val v = status.liveVelocity
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = v?.let { "%.0f".format(it) } ?: "—",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "mm/s",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun HeatersStripBody(heaters: List<HeaterReading>, unit: TempUnit) {
    if (heaters.isEmpty()) {
        Text("Waiting…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        heaters.take(8).forEachIndexed { index, heater ->
            val color = ChartPalette[index % ChartPalette.size]
            Column(
                modifier = Modifier.width(72.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = shortHeaterName(heater.name),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = color,
                )
                Text(
                    text = "%.0f".format(heater.temperature.toDisplayTemp(unit)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (heater.target > 0f) {
                    Text(
                        text = "→%.0f".format(heater.target.toDisplayTemp(unit)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MiniPowerBar(power = heater.power, color = color)
            }
        }
    }
}

@Composable
fun LedBulbIcon(
    on: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    contentDescription: String? = null,
) {
    Icon(
        imageVector = Icons.Filled.Lightbulb,
        contentDescription = contentDescription,
        tint = if (on) {
            Color(0xFFFFC107)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        },
        modifier = modifier.size(size),
    )
}

@Composable
fun LedBody(
    led: LedReading?,
    onToggle: ((Boolean) -> Unit)? = null,
) {
    if (led == null) {
        Text("Offline", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LedBulbIcon(on = led.isOn, size = 36.dp, contentDescription = led.displayName)
        Text(
            text = if (led.isOn) "On" else "Off",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        if (onToggle != null) {
            Switch(
                checked = led.isOn,
                onCheckedChange = onToggle,
            )
        }
    }
}

@Composable
fun LedsStripBody(
    leds: List<LedReading>,
    onToggle: ((LedReading, Boolean) -> Unit)? = null,
) {
    if (leds.isEmpty()) {
        Text("No LEDs", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        leds.take(10).forEach { led ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LedBulbIcon(on = led.isOn, size = 24.dp, contentDescription = led.displayName)
                Text(
                    text = led.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (onToggle != null) {
                    Switch(
                        checked = led.isOn,
                        onCheckedChange = { onToggle(led, it) },
                    )
                }
            }
        }
    }
}

@Composable
fun FansStripBody(fans: List<FanReading>) {
    if (fans.isEmpty()) {
        Text("No fans", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        fans.take(8).forEach { fan ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = fan.displayName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${(fan.speed * 100f).roundToInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.width(8.dp))
                MiniPowerBar(power = fan.speed, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun HeaterSparkBody(
    heater: HeaterReading?,
    unit: TempUnit,
    sparkline: List<Float>,
) {
    if (heater == null) {
        Text("Offline", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val current = heater.temperature.toDisplayTemp(unit)
    val target = if (heater.target > 0f) heater.target.toDisplayTemp(unit) else 0f
    val accent = MaterialTheme.colorScheme.primary
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val height = maxHeight
        val width = maxWidth
        val showSpark = height >= 56.dp
        val showPower = height >= 72.dp
        val compactTemp = width < 100.dp
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = "%.1f".format(current),
                    style = if (compactTemp) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.headlineSmall
                    },
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit.label(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
                Spacer(modifier = Modifier.weight(1f))
                if (target > 0f) {
                    Text(
                        text = "→%.0f".format(target),
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                        maxLines = 1,
                    )
                }
            }
            if (showSpark) {
                Sparkline(
                    values = sparkline.ifEmpty { listOf(heater.temperature) },
                    color = accent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true),
                )
            }
            if (showPower) {
                Text(
                    text = "Power %.0f%%".format(heater.power * 100f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun TempChartBody(
    series: List<TemperatureHistorySeries>,
    unit: TempUnit,
) {
    if (series.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading history…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val visible = remember(series) { series.take(6) }
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    Column(modifier = Modifier.fillMaxSize()) {
        MultiSparkChart(
            series = visible,
            gridColor = gridColor,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            visible.forEachIndexed { index, item ->
                val color = ChartPalette[index % ChartPalette.size]
                Text(
                    text = "%.0f° %s".format(
                        item.current.toDisplayTemp(unit),
                        shortHeaterName(item.name),
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun Sparkline(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val fill = color.copy(alpha = 0.18f)
    Canvas(modifier = modifier) {
        if (values.isEmpty() || size.width <= 0f || size.height <= 0f) return@Canvas
        val minV = values.minOrNull() ?: 0f
        val maxV = values.maxOrNull() ?: 1f
        val range = (maxV - minV).coerceAtLeast(1f)
        val path = Path()
        val fillPath = Path()
        values.forEachIndexed { index, value ->
            val x = if (values.size == 1) size.width else size.width * index / (values.size - 1)
            val y = size.height - ((value - minV) / range) * size.height
            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(size.width, size.height)
        fillPath.close()
        drawPath(fillPath, color = fill)
        drawPath(path, color = color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun MultiSparkChart(
    series: List<TemperatureHistorySeries>,
    gridColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val all = series.flatMap { it.temperatures.takeLast(120) }
        val minY = 0f
        val maxY = ceil((all.maxOrNull() ?: 50f) / 10f) * 10f
        val range = (maxY - minY).coerceAtLeast(1f)
        for (i in 0..4) {
            val y = size.height * i / 4f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        series.forEachIndexed { index, item ->
            val values = item.temperatures.takeLast(120)
            if (values.isEmpty()) return@forEachIndexed
            val color = ChartPalette[index % ChartPalette.size]
            val path = Path()
            values.forEachIndexed { i, value ->
                val x = if (values.size == 1) 0f else size.width * i / (values.size - 1)
                val y = size.height - ((value - minY) / range) * size.height
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun MiniPowerBar(power: Float, color: Color) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp),
    ) {
        drawRoundRect(color = track, size = size, cornerRadius = CornerRadius(2.dp.toPx()))
        drawRoundRect(
            color = color,
            size = Size(size.width * power.coerceIn(0f, 1f), size.height),
            cornerRadius = CornerRadius(2.dp.toPx()),
        )
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun formatEta(seconds: Float?): String {
    if (seconds == null) return "—"
    return formatDuration(seconds)
}

internal fun formatDuration(seconds: Float): String {
    val total = seconds.coerceAtLeast(0f).roundToInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    return when {
        h > 0 -> "%dh %02dm".format(h, m)
        else -> "%dm".format(m)
    }
}

internal fun formatLayer(current: Int?, total: Int?): String = when {
    current != null && total != null -> "$current / $total"
    current != null -> "$current"
    else -> "—"
}

internal fun formatFilament(mm: Float): String = when {
    mm <= 0f -> "—"
    mm >= 1000f -> "%.2f m".format(mm / 1000f)
    else -> "%.0f mm".format(mm)
}

private fun shortHeaterName(name: String): String =
    name.removePrefix("temperature_sensor ")
        .removePrefix("extruder")
        .let { if (it.isBlank() && name.startsWith("extruder")) name else it }
        .ifBlank { name }
        .take(10)
