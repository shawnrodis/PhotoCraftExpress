package com.shawnrodis.photocraftexpress

import com.shawnrodis.photocraftexpress.engine.CropCorner
import com.shawnrodis.photocraftexpress.engine.CropDragKind
import com.shawnrodis.photocraftexpress.engine.CropEdge
import com.shawnrodis.photocraftexpress.engine.CropGeometry
import com.shawnrodis.photocraftexpress.engine.CropRect
import com.shawnrodis.photocraftexpress.engine.ZoomState
import com.shawnrodis.photocraftexpress.engine.cropHandleAt
import com.shawnrodis.photocraftexpress.engine.fitFrame
import com.shawnrodis.photocraftexpress.engine.fractionToNorm
import com.shawnrodis.photocraftexpress.engine.normToFraction
import com.shawnrodis.photocraftexpress.engine.normToScreen
import com.shawnrodis.photocraftexpress.engine.screenToNorm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * v0.2.3 tests: the gate-driven crop math — screen-space hit testing
 * (corners, edges, move, none, zoomed), edge resizing with and without
 * an aspect lock, and the screen<->normalized mapping round-trips.
 * These are the paths Shawn's Crop video showed failing: edge drags
 * had no handles at all, and the old overlay passed the raw pixel
 * ratio as the normalized aspect lock.
 */
class V023CropTest {

    private fun approx(a: Float, b: Float, eps: Float = 0.001f) =
        assertTrue("expected $b, got $a", abs(a - b) < eps)

    private fun approxRect(r: CropRect, l: Float, t: Float, rgt: Float, b: Float) {
        approx(r.left, l)
        approx(r.top, t)
        approx(r.right, rgt)
        approx(r.bottom, b)
    }

    // ------------------------------------------------------------------
    // Fit frame + mapping
    // ------------------------------------------------------------------

    @Test fun fitFrameCentres() {
        // Landscape 2:1 image in a square box: full width, centred vertically.
        val land = fitFrame(1000f, 1000f, 2000, 1000)
        approx(land.dispW, 1000f)
        approx(land.dispH, 500f)
        approx(land.offX, 0f)
        approx(land.offY, 250f)
        // Portrait 1:2 image in a square box: full height, centred horizontally.
        val port = fitFrame(1000f, 1000f, 1000, 2000)
        approx(port.dispW, 500f)
        approx(port.dispH, 1000f)
        approx(port.offX, 250f)
        approx(port.offY, 0f)
    }

    @Test fun fractionNormRoundTrip() {
        val fit = fitFrame(1000f, 1000f, 2000, 1000)
        for ((nx, ny) in listOf(0f to 0f, 0.25f to 0.75f, 1f to 1f, 0.5f to 0.5f)) {
            val (fx, fy) = normToFraction(fit, nx, ny)
            val (bx, by) = fractionToNorm(fit, fx, fy)
            approx(bx, nx)
            approx(by, ny)
        }
    }

    @Test fun screenNormRoundTripIdentityAndZoomed() {
        val fit = fitFrame(1000f, 1000f, 1000, 1000)
        for (zoom in listOf(ZoomState.IDENTITY, ZoomState(2.5f, 0.3f, -0.2f))) {
            for ((nx, ny) in listOf(0.1f to 0.1f, 0.5f to 0.5f, 0.9f to 0.2f)) {
                val (px, py) = normToScreen(fit, zoom, nx, ny)
                val (bx, by) = screenToNorm(fit, zoom, px, py)
                approx(bx, nx)
                approx(by, ny)
            }
        }
    }

    // ------------------------------------------------------------------
    // Hit testing
    // ------------------------------------------------------------------

    private val squareFit = fitFrame(1000f, 1000f, 1000, 1000)
    private val rect = CropRect(0.2f, 0.2f, 0.8f, 0.8f)
    private val identity = ZoomState.IDENTITY

    @Test fun hitCornersEdgesMoveNone() {
        assertEquals(
            CropDragKind.Corner(CropCorner.TOP_LEFT),
            cropHandleAt(rect, squareFit, identity, 200f, 200f, 56f)
        )
        assertEquals(
            CropDragKind.Corner(CropCorner.BOTTOM_RIGHT),
            cropHandleAt(rect, squareFit, identity, 800f, 800f, 56f)
        )
        assertEquals(
            CropDragKind.Edge(CropEdge.LEFT),
            cropHandleAt(rect, squareFit, identity, 200f, 500f, 56f)
        )
        assertEquals(
            CropDragKind.Edge(CropEdge.TOP),
            cropHandleAt(rect, squareFit, identity, 500f, 200f, 56f)
        )
        assertEquals(
            CropDragKind.Move,
            cropHandleAt(rect, squareFit, identity, 500f, 500f, 56f)
        )
        // Far outside: not near any handle, not inside.
        assertEquals(
            CropDragKind.None,
            cropHandleAt(rect, squareFit, identity, 50f, 50f, 56f)
        )
        // Just inside the rect but away from handles still moves.
        assertEquals(
            CropDragKind.Move,
            cropHandleAt(rect, squareFit, identity, 500f, 300f, 56f)
        )
    }

    @Test fun cornerWinsTieOverEdge() {
        // Small rect: corner (400,400) and TOP edge midpoint (500,400)
        // are both exactly 50px from (450,400); the corner must win.
        val small = CropRect(0.4f, 0.4f, 0.6f, 0.6f)
        assertEquals(
            CropDragKind.Corner(CropCorner.TOP_LEFT),
            cropHandleAt(small, squareFit, identity, 450f, 400f, 56f)
        )
    }

    @Test fun hitTestFollowsZoom() {
        // Zoom 2x with an offset that puts the rect's top-left corner
        // at screen (400,400): forward(0.2,0.2) = 0.5 + (-0.3)*2 + 0.5.
        val zoom = ZoomState(2f, 0.5f, 0.5f)
        assertEquals(
            CropDragKind.Corner(CropCorner.TOP_LEFT),
            cropHandleAt(rect, squareFit, zoom, 400f, 400f, 56f)
        )
        // The identity-zoom corner position no longer grabs anything:
        // under this zoom it maps to normalized (0.1,0.1), outside.
        assertEquals(
            CropDragKind.None,
            cropHandleAt(rect, squareFit, zoom, 200f, 200f, 56f)
        )
    }

    // ------------------------------------------------------------------
    // Edge + corner resizing, move
    // ------------------------------------------------------------------

    @Test fun resizeEdgeFree() {
        approxRect(
            CropGeometry.resizeEdge(rect, CropEdge.LEFT, 0.1f, 0.5f),
            0.1f, 0.2f, 0.8f, 0.8f
        )
        approxRect(
            CropGeometry.resizeEdge(rect, CropEdge.BOTTOM, 0.5f, 0.9f),
            0.2f, 0.2f, 0.8f, 0.9f
        )
        // Dragging an edge past the opposite edge clamps at min size,
        // centred on the crossing midpoint (clampRect's rule, same as
        // for corners): centre (0.95+0.8)/2 = 0.875, half-width 0.04.
        val crossed = CropGeometry.resizeEdge(rect, CropEdge.LEFT, 0.95f, 0.5f)
        approx(crossed.width, CropGeometry.MIN_SIZE)
        approx(crossed.left, 0.835f)
        approx(crossed.right, 0.915f)
    }

    @Test fun resizeEdgeWithAspect() {
        // Normalized ratio 1.5: dragging RIGHT to 0.9 widens to 0.7 and
        // grows height to 0.7/1.5, centred on the old centre (0.4).
        approxRect(
            CropGeometry.resizeEdge(
                CropRect(0.2f, 0.2f, 0.8f, 0.6f), CropEdge.RIGHT, 0.9f, 0.5f, 1.5f
            ),
            0.2f, 0.1667f, 0.9f, 0.6333f
        )
        // Dragging TOP to 0.1 grows height to 0.5 and width to 0.75,
        // centred on the old centre (0.5).
        approxRect(
            CropGeometry.resizeEdge(
                CropRect(0.2f, 0.2f, 0.8f, 0.6f), CropEdge.TOP, 0.5f, 0.1f, 1.5f
            ),
            0.125f, 0.1f, 0.875f, 0.6f
        )
    }

    @Test fun resizeCornerAndMove() {
        approxRect(
            CropGeometry.resizeCorner(rect, CropCorner.TOP_LEFT, 0.1f, 0.05f),
            0.1f, 0.05f, 0.8f, 0.8f
        )
        approxRect(
            CropGeometry.moveRect(rect, 0.1f, -0.05f),
            0.3f, 0.15f, 0.9f, 0.75f
        )
        // Moving past the frame clamps, keeping the size.
        approxRect(
            CropGeometry.moveRect(rect, 0.5f, 0f),
            0.4f, 0.2f, 1.0f, 0.8f
        )
    }

    @Test fun aspectLockUsesNormalizedUnits() {
        // Portrait frame: 1000x2000 image in a 1000x1000 box displays
        // 500px wide, 1000px tall. A pixel-square crop has normalized
        // ratio R * dispH / dispW = 1.0 * 1000/500 = 2.0 — the value the
        // editor must pass (the old overlay passed the raw ratio 1.0,
        // so aspect-locked drags held the wrong ratio on non-square
        // photos). Dragging BOTTOM_RIGHT to (0.5,0.3) with aspectNorm
        // 2.0 yields normalized 0.4 x 0.2 = 200 x 200 px: pixel-square.
        val fit = fitFrame(1000f, 1000f, 1000, 2000)
        val aspectNorm = 1.0f * fit.dispH / fit.dispW
        approx(aspectNorm, 2.0f)
        val out = CropGeometry.resizeCorner(
            CropRect(0.1f, 0.1f, 0.5f, 0.5f), CropCorner.BOTTOM_RIGHT, 0.5f, 0.3f, aspectNorm
        )
        approxRect(out, 0.1f, 0.1f, 0.5f, 0.3f)
        val pxW = out.width * fit.dispW
        val pxH = out.height * fit.dispH
        approx(pxW, pxH)
    }
}
