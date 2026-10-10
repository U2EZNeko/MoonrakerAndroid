package com.moonraker.android.ui.gcode

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.moonraker.android.data.moonraker.GcodePathPreview
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Toolpath canvas with Fluidd-style layer + time scrubbers.
 */
@Composable
fun GcodePreviewViewer(
    preview: GcodePathPreview,
    liveX: Float? = null,
    liveY: Float? = null,
    liveLayer: Int? = null,
    liveProgress: Float? = null,
    followLive: Boolean = false,
    onFollowLiveChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val maxLayer = preview.maxLayer.coerceAtLeast(0)
    val totalTime = preview.totalTimeSec.coerceAtLeast(1f)
    val scheme = MaterialTheme.colorScheme
    val darkSurface = scheme.background.luminance() < 0.35f
    val pathColors = remember(scheme.primary, scheme.error, darkSurface) {
        pathColorsForTheme(scheme.primary, scheme.error, darkSurface)
    }

    var layerCutoff by remember(preview) { mutableIntStateOf(maxLayer) }
    var timeCutoff by remember(preview) { mutableFloatStateOf(totalTime) }
    var following by remember(preview) { mutableStateOf(followLive) }

    LaunchedEffect(followLive) {
        if (followLive) following = true
    }

    LaunchedEffect(following, liveLayer, liveProgress, maxLayer, totalTime) {
        if (!following) return@LaunchedEffect
        liveLayer?.let { layerCutoff = it.coerceIn(0, maxLayer) }
        liveProgress?.let { timeCutoff = (it.coerceIn(0f, 1f) * totalTime) }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onFollowLiveChange != null && liveProgress != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = following,
                    onClick = {
                        following = !following
                        onFollowLiveChange(following)
                    },
                    label = { Text(if (following) "Following print" else "Follow print") },
                )
            }
        }

        val canvasShape = RoundedCornerShape(12.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(canvasShape)
                .background(pathColors.canvasBackground)
                .border(
                    width = 1.dp,
                    color = if (darkSurface) {
                        Color.White.copy(alpha = 0.12f)
                    } else {
                        scheme.outline.copy(alpha = 0.45f)
                    },
                    shape = canvasShape,
                ),
        ) {
            PathCanvas(
                preview = preview,
                maxLayer = layerCutoff,
                maxTimeSec = timeCutoff,
                travelColor = pathColors.travel,
                extrudeColor = pathColors.extrude,
                futureColor = pathColors.future,
                liveColor = pathColors.live,
                liveX = if (following) liveX else null,
                liveY = if (following) liveY else null,
                strokeBoost = if (darkSurface) 1.35f else 1f,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Text(
            text = "Layer  ${layerCutoff + 1} / ${maxLayer + 1}  (this layer only)",
            style = MaterialTheme.typography.labelLarge,
        )
        Slider(
            value = layerCutoff.toFloat(),
            onValueChange = {
                following = false
                onFollowLiveChange?.invoke(false)
                layerCutoff = it.roundToInt().coerceIn(0, maxLayer)
            },
            valueRange = 0f..maxLayer.toFloat().coerceAtLeast(0f),
            steps = (maxLayer - 1).coerceAtLeast(0),
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = "Time  ${formatClock(timeCutoff)} / ${formatClock(totalTime)}",
            style = MaterialTheme.typography.labelLarge,
        )
        Slider(
            value = timeCutoff,
            onValueChange = {
                following = false
                onFollowLiveChange?.invoke(false)
                timeCutoff = it.coerceIn(0f, totalTime)
            },
            valueRange = 0f..totalTime,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PathCanvas(
    preview: GcodePathPreview,
    maxLayer: Int,
    maxTimeSec: Float,
    travelColor: Color,
    extrudeColor: Color,
    futureColor: Color,
    liveColor: Color,
    liveX: Float?,
    liveY: Float?,
    strokeBoost: Float,
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
            // Only the selected layer (not cumulative stack) — matches Fluidd "current layer".
            if (seg.layer != maxLayer) continue
            val reached = seg.timeSec <= maxTimeSec + 1e-3f
            val color = when {
                !reached -> futureColor
                !seg.extruding -> travelColor
                else -> extrudeColor
            }
            drawLine(
                color = color,
                start = Offset(mapX(seg.x0), mapY(seg.y0)),
                end = Offset(mapX(seg.x1), mapY(seg.y1)),
                strokeWidth = (if (seg.extruding && reached) 2.6f else 1.4f) * strokeBoost,
                cap = StrokeCap.Round,
            )
        }

        if (liveX != null && liveY != null) {
            val cx = mapX(liveX)
            val cy = mapY(liveY)
            drawCircle(color = liveColor.copy(alpha = 0.28f), radius = 15.dp.toPx(), center = Offset(cx, cy))
            drawCircle(color = liveColor, radius = 6.5.dp.toPx(), center = Offset(cx, cy))
            drawCircle(
                color = Color.White,
                radius = 6.5.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

private data class PathColors(
    val canvasBackground: Color,
    val extrude: Color,
    val travel: Color,
    val future: Color,
    val live: Color,
)

/** High-contrast strokes against a dedicated canvas (especially dark / OLED). */
private fun pathColorsForTheme(primary: Color, error: Color, darkSurface: Boolean): PathColors {
    return if (darkSurface) {
        PathColors(
            canvasBackground = Color(0xFF000000),
            extrude = primary.ensureVisibleOnDark(),
            travel = Color(0xFFB0B0B0),
            future = Color(0xFF5C5C5C),
            live = if (error.luminance() > 0.25f) error else Color(0xFFFF5252),
        )
    } else {
        PathColors(
            canvasBackground = Color(0xFFF3F1EE),
            extrude = primary.ensureVisibleOnLight(),
            travel = Color(0xFF6B6B6B),
            future = Color(0xFFC2C2C2),
            live = error,
        )
    }
}

private fun Color.ensureVisibleOnDark(): Color {
    val lum = luminance()
    return when {
        lum >= 0.38f -> this
        lum >= 0.18f -> blendToward(Color.White, 0.35f)
        else -> blendToward(Color.White, 0.55f)
    }
}

private fun Color.ensureVisibleOnLight(): Color {
    val lum = luminance()
    return when {
        lum <= 0.72f -> this
        else -> blendToward(Color.Black, 0.35f)
    }
}

private fun Color.blendToward(other: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = red + (other.red - red) * t,
        green = green + (other.green - green) * t,
        blue = blue + (other.blue - blue) * t,
        alpha = alpha,
    )
}

private fun formatClock(sec: Float): String {
    val total = sec.roundToLong().coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
