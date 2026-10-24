package com.shawnrodis.photocraftexpress.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.shawnrodis.photocraftexpress.engine.CropCorner
import com.shawnrodis.photocraftexpress.engine.CropGeometry
import com.shawnrodis.photocraftexpress.engine.CropRect
import kotlin.math.abs

/**
 * The crop interaction layer drawn over the (uncropped) photo: dimmed
 * surround, rule-of-thirds grid, corner handles. Dragging inside moves the
 * rect; dragging a corner resizes it. [onCommit] fires once per drag.
 */
@Composable
fun CropOverlay(
    containerSize: IntSize,
    imageWidth: Int,
    imageHeight: Int,
    crop: CropRect,
    aspectNorm: Float?,
    onCommit: (CropRect) -> Unit,
    modifier: Modifier = Modifier
) {
    var local by remember(crop) { mutableStateOf(crop) }

    // Fit rect of the image inside the container (ContentScale.Fit maths).
    val cw = containerSize.width.toFloat()
    val ch = containerSize.height.toFloat()
    if (cw <= 0f || ch <= 0f || imageWidth <= 0 || imageHeight <= 0) return
    val scale = minOf(cw / imageWidth, ch / imageHeight)
    val dispW = imageWidth * scale
    val dispH = imageHeight * scale
    val offX = (cw - dispW) / 2f
    val offY = (ch - dispH) / 2f

    fun toNorm(p: Offset): Pair<Float, Float> =
        ((p.x - offX) / dispW).coerceIn(0f, 1f) to ((p.y - offY) / dispH).coerceIn(0f, 1f)

    var dragMode by remember { mutableStateOf<DragMode>(DragMode.None) }
    var startRect by remember { mutableStateOf(crop) }
    var startNorm by remember { mutableStateOf(0f to 0f) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(imageWidth, imageHeight, containerSize) {
                detectDragGestures(
                    onDragStart = { pos ->
                        val (nx, ny) = toNorm(pos)
                        startNorm = nx to ny
                        startRect = local
                        val handlePx = 44.dp.toPx()
                        val corners = listOf(
                            CropCorner.TOP_LEFT to Offset(offX + local.left * dispW, offY + local.top * dispH),
                            CropCorner.TOP_RIGHT to Offset(offX + local.right * dispW, offY + local.top * dispH),
                            CropCorner.BOTTOM_LEFT to Offset(offX + local.left * dispW, offY + local.bottom * dispH),
                            CropCorner.BOTTOM_RIGHT to Offset(offX + local.right * dispW, offY + local.bottom * dispH)
                        )
                        val near = corners.firstOrNull { (_, c) ->
                            abs(c.x - pos.x) <= handlePx && abs(c.y - pos.y) <= handlePx
                        }
                        dragMode = when {
                            near != null -> DragMode.Corner(near.first)
                            nx in local.left..local.right && ny in local.top..local.bottom -> DragMode.Move
                            else -> DragMode.None
                        }
                    },
                    onDrag = { change, _ ->
                        val (nx, ny) = toNorm(change.position)
                        val dx = nx - startNorm.first
                        val dy = ny - startNorm.second
                        local = when (val mode = dragMode) {
                            is DragMode.Corner -> CropGeometry.resizeCorner(
                                startRect, mode.corner, nx, ny, aspectNorm
                            )
                            DragMode.Move -> CropGeometry.moveRect(startRect, dx, dy)
                            DragMode.None -> local
                        }
                    },
                    onDragEnd = {
                        if (dragMode != DragMode.None && local != crop) onCommit(local)
                        dragMode = DragMode.None
                    },
                    onDragCancel = {
                        local = crop
                        dragMode = DragMode.None
                    }
                )
            }
    ) {
        val l = offX + local.left * dispW
        val t = offY + local.top * dispH
        val r = offX + local.right * dispW
        val b = offY + local.bottom * dispH
        val scrim = Color.Black.copy(alpha = 0.55f)
        // Dimmed surround.
        drawRect(scrim, Offset(offX, offY), Size(dispW, t - offY))
        drawRect(scrim, Offset(offX, b), Size(dispW, offY + dispH - b))
        drawRect(scrim, Offset(offX, t), Size(l - offX, b - t))
        drawRect(scrim, Offset(r, t), Size(offX + dispW - r, b - t))
        // Rule-of-thirds grid.
        val grid = Color.White.copy(alpha = 0.4f)
        for (i in 1..2) {
            val gx = l + (r - l) * i / 3f
            val gy = t + (b - t) * i / 3f
            drawLine(grid, Offset(gx, t), Offset(gx, b), strokeWidth = 1.dp.toPx())
            drawLine(grid, Offset(l, gy), Offset(r, gy), strokeWidth = 1.dp.toPx())
        }
        // Border.
        drawRect(
            Color.White, Offset(l, t), Size(r - l, b - t),
            style = Stroke(width = 1.5.dp.toPx())
        )
        // Corner handles (L-shaped, thicker).
        val arm = 20.dp.toPx()
        val hw = 3.5.dp.toPx()
        val corners = listOf(
            Triple(Offset(l, t), 1f to 1f, Unit),
            Triple(Offset(r, t), -1f to 1f, Unit),
            Triple(Offset(l, b), 1f to -1f, Unit),
            Triple(Offset(r, b), -1f to -1f, Unit)
        )
        for ((c, dir) in corners) {
            val (sx, sy) = dir
            drawLine(Color.White, c, Offset(c.x + arm * sx, c.y), strokeWidth = hw)
            drawLine(Color.White, c, Offset(c.x, c.y + arm * sy), strokeWidth = hw)
        }
    }
}

private sealed interface DragMode {
    object None : DragMode
    object Move : DragMode
    data class Corner(val corner: CropCorner) : DragMode
}
