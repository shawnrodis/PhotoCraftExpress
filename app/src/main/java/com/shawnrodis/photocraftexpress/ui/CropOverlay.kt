package com.shawnrodis.photocraftexpress.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.shawnrodis.photocraftexpress.engine.CropRect
import com.shawnrodis.photocraftexpress.engine.ZoomState
import com.shawnrodis.photocraftexpress.engine.fitFrame
import com.shawnrodis.photocraftexpress.engine.normToScreen

/**
 * The crop interaction layer, DRAW-ONLY since v0.2.3: all input flows
 * through the canvas gesture gate (single-finger crop drags, two-finger
 * pinch/pan, double-tap zoom), so there is exactly one detector and no
 * gate can swallow a crop drag. This layer draws the dimmed surround,
 * rule-of-thirds grid, border, corner handles and (new) edge handles.
 *
 * It sits OUTSIDE the zoom layer and maps the rect to screen px
 * explicitly through [zoom], so handles stay constant screen size at
 * any zoom (inside the zoom layer they would scale up with the image)
 * and the drawn rect always aligns with the zoomed image: both use the
 * same fit frame + ZoomState.forward mapping.
 */
@Composable
fun CropOverlay(
    containerSize: IntSize,
    imageWidth: Int,
    imageHeight: Int,
    crop: CropRect,
    zoom: ZoomState,
    modifier: Modifier = Modifier
) {
    val cw = containerSize.width.toFloat()
    val ch = containerSize.height.toFloat()
    if (cw <= 0f || ch <= 0f || imageWidth <= 0 || imageHeight <= 0) return
    val fit = fitFrame(cw, ch, imageWidth, imageHeight)
    // Screen px of the rect's corners, through the zoom.
    val (lx, ty) = normToScreen(fit, zoom, crop.left, crop.top)
    val (rx, by) = normToScreen(fit, zoom, crop.right, crop.bottom)

    Canvas(modifier = modifier.fillMaxSize()) {
        val scrim = Color.Black.copy(alpha = 0.55f)
        // Dimmed surround, clipped to the canvas (only positive sizes).
        fun dim(x: Float, y: Float, w: Float, h: Float) {
            val cx0 = x.coerceIn(0f, size.width)
            val cy0 = y.coerceIn(0f, size.height)
            val cx1 = (x + w).coerceIn(0f, size.width)
            val cy1 = (y + h).coerceIn(0f, size.height)
            if (cx1 > cx0 && cy1 > cy0) drawRect(scrim, Offset(cx0, cy0), Size(cx1 - cx0, cy1 - cy0))
        }
        dim(0f, 0f, size.width, ty)
        dim(0f, by, size.width, size.height - by)
        dim(0f, ty, lx, by - ty)
        dim(rx, ty, size.width - rx, by - ty)
        // Rule-of-thirds grid.
        val grid = Color.White.copy(alpha = 0.4f)
        for (i in 1..2) {
            val gx = lx + (rx - lx) * i / 3f
            val gy = ty + (by - ty) * i / 3f
            drawLine(grid, Offset(gx, ty), Offset(gx, by), strokeWidth = 1.dp.toPx())
            drawLine(grid, Offset(lx, gy), Offset(rx, gy), strokeWidth = 1.dp.toPx())
        }
        // Border.
        drawRect(
            Color.White, Offset(lx, ty), Size(rx - lx, by - ty),
            style = Stroke(width = 1.5.dp.toPx())
        )
        // Corner handles (L-shaped, thicker).
        val arm = 20.dp.toPx()
        val hw = 3.5.dp.toPx()
        val corners = listOf(
            Triple(Offset(lx, ty), 1f to 1f, Unit),
            Triple(Offset(rx, ty), -1f to 1f, Unit),
            Triple(Offset(lx, by), 1f to -1f, Unit),
            Triple(Offset(rx, by), -1f to -1f, Unit)
        )
        for ((c, dir) in corners) {
            val (sx, sy) = dir
            drawLine(Color.White, c, Offset(c.x + arm * sx, c.y), strokeWidth = hw)
            drawLine(Color.White, c, Offset(c.x, c.y + arm * sy), strokeWidth = hw)
        }
        // Edge handles (short bars at the edge midpoints, matching the
        // v0.2.3 edge-drag targets in the gesture gate).
        val midX = (lx + rx) / 2f
        val midY = (ty + by) / 2f
        drawLine(Color.White, Offset(midX - arm / 2f, ty), Offset(midX + arm / 2f, ty), strokeWidth = hw)
        drawLine(Color.White, Offset(midX - arm / 2f, by), Offset(midX + arm / 2f, by), strokeWidth = hw)
        drawLine(Color.White, Offset(lx, midY - arm / 2f), Offset(lx, midY + arm / 2f), strokeWidth = hw)
        drawLine(Color.White, Offset(rx, midY - arm / 2f), Offset(rx, midY + arm / 2f), strokeWidth = hw)
    }
}
