package com.moonraker.android.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.moonraker.android.data.settings.DashboardGrid
import com.moonraker.android.data.settings.DashboardTile
import com.moonraker.android.ui.common.EmptyState
import kotlin.math.roundToInt

internal val GridGap = 6.dp

private enum class DragMode { None, Move, Resize }

@Composable
fun DashboardGridCanvas(
    tiles: List<DashboardTile>,
    editing: Boolean,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onMoveTo: (id: String, col: Int, row: Int) -> Unit,
    onResizeTo: (id: String, colSpan: Int, rowSpan: Int) -> Unit,
    onDelete: (id: String) -> Unit = {},
    /** Window Y of the finger while dragging; null when drag ends/cancels. */
    onDragWindowY: (Float?) -> Unit = {},
    /**
     * Extra Y offset from parent scroll since drag started (scrollDelta).
     * Keeps the floating tile under the finger when the dashboard auto-scrolls.
     */
    parentScrollDeltaY: Float = 0f,
    cellHeight: Dp,
    content: @Composable (DashboardTile, Modifier) -> Unit,
) {
    val density = LocalDensity.current
    var dragMode by remember { mutableStateOf(DragMode.None) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var previewCol by remember { mutableStateOf<Int?>(null) }
    var previewRow by remember { mutableStateOf<Int?>(null) }
    var previewColSpan by remember { mutableStateOf<Int?>(null) }
    var previewRowSpan by remember { mutableStateOf<Int?>(null) }
    val moveOffsetY = dragOffsetY + parentScrollDeltaY
    val scrollDeltaState = rememberUpdatedState(parentScrollDeltaY)

    val dragging = dragMode != DragMode.None && draggingId != null
    val baseRows = maxOf(DashboardGrid.rowCount(tiles), if (editing) 6 else 1)
    val extraRows = if (dragging) 4 else if (editing) 2 else 0
    val rows = baseRows + extraRows

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val gap = GridGap
        val cellW = (maxWidth - gap * (DashboardGrid.COLUMNS - 1)) / DashboardGrid.COLUMNS
        val cellPitchX = with(density) { (cellW + gap).toPx() }
        val cellPitchY = with(density) { (cellHeight + gap).toPx() }
        val gridHeight = cellHeight * rows + gap * (rows - 1).coerceAtLeast(0)

        fun snapCol(tile: DashboardTile, offsetPx: Float): Int {
            val delta = (offsetPx / cellPitchX).roundToInt()
            return (tile.col + delta).coerceIn(0, (DashboardGrid.COLUMNS - tile.colSpan).coerceAtLeast(0))
        }

        fun snapRow(tile: DashboardTile, offsetPx: Float): Int {
            val delta = (offsetPx / cellPitchY).roundToInt()
            return (tile.row + delta).coerceAtLeast(0)
        }

        fun snapColSpan(tile: DashboardTile, offsetPx: Float): Int {
            val delta = (offsetPx / cellPitchX).roundToInt()
            return (tile.colSpan + delta)
                .coerceIn(1, DashboardGrid.MAX_COL_SPAN)
                .coerceAtMost(DashboardGrid.COLUMNS - tile.col)
        }

        fun snapRowSpan(tile: DashboardTile, offsetPx: Float): Int {
            val delta = (offsetPx / cellPitchY).roundToInt()
            return (tile.rowSpan + delta).coerceIn(1, DashboardGrid.MAX_ROW_SPAN)
        }

        fun clearDrag() {
            dragMode = DragMode.None
            draggingId = null
            dragOffsetX = 0f
            dragOffsetY = 0f
            previewCol = null
            previewRow = null
            previewColSpan = null
            previewRowSpan = null
            onDragWindowY(null)
        }

        fun reportPointer(coords: LayoutCoordinates?, local: Offset) {
            if (coords == null || !coords.isAttached) return
            onDragWindowY(coords.localToWindow(local).y)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(gridHeight),
        ) {
            if (editing) {
                Row(modifier = Modifier.fillMaxSize()) {
                    repeat(DashboardGrid.COLUMNS) { index ->
                        Box(
                            modifier = Modifier
                                .width(cellW)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
                        )
                        if (index < DashboardGrid.COLUMNS - 1) {
                            Spacer(modifier = Modifier.width(gap))
                        }
                    }
                }
            }

            if (editing && draggingId != null) {
                val tile = tiles.firstOrNull { it.id == draggingId }
                if (tile != null) {
                    val gCol = previewCol ?: tile.col
                    val gRow = previewRow ?: tile.row
                    val gW = previewColSpan ?: tile.colSpan
                    val gH = previewRowSpan ?: tile.rowSpan
                    val gx = (cellW + gap) * gCol
                    val gy = (cellHeight + gap) * gRow
                    val gw = cellW * gW + gap * (gW - 1)
                    val gh = cellHeight * gH + gap * (gH - 1)
                    Box(
                        modifier = Modifier
                            .offset(gx, gy)
                            .width(gw)
                            .height(gh)
                            .zIndex(0.5f)
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(10.dp),
                            )
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                RoundedCornerShape(10.dp),
                            ),
                    )
                }
            }

            tiles.forEach { tile ->
                val isDraggingThis = draggingId == tile.id && dragMode != DragMode.None
                val x = (cellW + gap) * tile.col
                val y = (cellHeight + gap) * tile.row
                val w = cellW * tile.colSpan + gap * (tile.colSpan - 1)
                val h = cellHeight * tile.rowSpan + gap * (tile.rowSpan - 1)
                val selected = tile.id == selectedId || isDraggingThis
                var tileCoords by remember(tile.id) { mutableStateOf<LayoutCoordinates?>(null) }
                val pixelOffset = if (isDraggingThis && dragMode == DragMode.Move) {
                    IntOffset(dragOffsetX.roundToInt(), moveOffsetY.roundToInt())
                } else {
                    IntOffset.Zero
                }
                val liveW = if (isDraggingThis && dragMode == DragMode.Resize) {
                    cellW * (previewColSpan ?: tile.colSpan) +
                        gap * ((previewColSpan ?: tile.colSpan) - 1)
                } else {
                    w
                }
                val liveH = if (isDraggingThis && dragMode == DragMode.Resize) {
                    cellHeight * (previewRowSpan ?: tile.rowSpan) +
                        gap * ((previewRowSpan ?: tile.rowSpan) - 1)
                } else {
                    h
                }

                Box(
                    modifier = Modifier
                        .offset(x, y)
                        .offset { pixelOffset }
                        .width(liveW)
                        .height(liveH)
                        .zIndex(if (isDraggingThis) 5f else if (selected) 2f else 1f)
                        .onGloballyPositioned { tileCoords = it }
                        .then(
                            if (editing) {
                                Modifier
                                    .shadow(
                                        elevation = if (isDraggingThis) 10.dp else if (selected) 4.dp else 0.dp,
                                        shape = RoundedCornerShape(10.dp),
                                    )
                                    .border(
                                        width = if (selected || isDraggingThis) 2.dp else 1.dp,
                                        color = if (selected || isDraggingThis) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                    )
                                    .alpha(if (isDraggingThis && dragMode == DragMode.Move) 0.92f else 1f)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { onSelect(tile.id) }
                                    .pointerInput(tile.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                onSelect(tile.id)
                                                dragMode = DragMode.Move
                                                draggingId = tile.id
                                                dragOffsetX = 0f
                                                dragOffsetY = 0f
                                                previewCol = tile.col
                                                previewRow = tile.row
                                                previewColSpan = tile.colSpan
                                                previewRowSpan = tile.rowSpan
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffsetX += dragAmount.x
                                                dragOffsetY += dragAmount.y
                                                previewCol = snapCol(tile, dragOffsetX)
                                                previewRow = snapRow(tile, dragOffsetY + scrollDeltaState.value)
                                                reportPointer(tileCoords, change.position)
                                            },
                                            onDragEnd = {
                                                val col = snapCol(tile, dragOffsetX)
                                                val row = snapRow(tile, dragOffsetY + scrollDeltaState.value)
                                                clearDrag()
                                                onMoveTo(tile.id, col, row)
                                            },
                                            onDragCancel = { clearDrag() },
                                        )
                                    }
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    content(tile, Modifier.fillMaxSize())

                    if (editing) {
                        // Drag handle
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (-4).dp)
                                .size(width = 36.dp, height = 5.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                    RoundedCornerShape(3.dp),
                                ),
                        )
                        // Delete (homescreen-style)
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = (-6).dp, y = (-6).dp)
                                .size(26.dp)
                                .zIndex(7f)
                                .shadow(3.dp, CircleShape)
                                .background(MaterialTheme.colorScheme.error, CircleShape)
                                .clickable {
                                    onSelect(tile.id)
                                    onDelete(tile.id)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "×",
                                color = MaterialTheme.colorScheme.onError,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        // Resize handle
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 6.dp, y = 6.dp)
                                .size(24.dp)
                                .zIndex(6f)
                                .pointerInput(tile.id, tile.colSpan, tile.rowSpan) {
                                    detectDragGestures(
                                        onDragStart = {
                                            onSelect(tile.id)
                                            dragMode = DragMode.Resize
                                            draggingId = tile.id
                                            dragOffsetX = 0f
                                            dragOffsetY = 0f
                                            previewCol = tile.col
                                            previewRow = tile.row
                                            previewColSpan = tile.colSpan
                                            previewRowSpan = tile.rowSpan
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetX += dragAmount.x
                                            dragOffsetY += dragAmount.y
                                            previewColSpan = snapColSpan(tile, dragOffsetX)
                                            previewRowSpan = snapRowSpan(tile, dragOffsetY)
                                            reportPointer(tileCoords, change.position)
                                        },
                                        onDragEnd = {
                                            val cw = previewColSpan ?: tile.colSpan
                                            val rh = previewRowSpan ?: tile.rowSpan
                                            clearDrag()
                                            onResizeTo(tile.id, cw, rh)
                                        },
                                        onDragCancel = { clearDrag() },
                                    )
                                },
                        ) {}
                    }
                }
            }

            if (tiles.isEmpty()) {
                EmptyState(
                    message = if (editing) {
                        "Tap + to add widgets"
                    } else {
                        "Empty dashboard — tap the pencil to edit"
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .align(Alignment.Center),
                )
            }
        }
    }
}
