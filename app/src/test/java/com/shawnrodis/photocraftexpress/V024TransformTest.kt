package com.shawnrodis.photocraftexpress

import com.shawnrodis.photocraftexpress.engine.ZoomState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v0.2.4: Transform-handle drags must map displayed pixels back to
 * canvas pixels through the zoom scale. The graphics layer scales
 * rendering but NOT pointer coordinates, so raw deltas moved corners
 * scale-times too far whenever zoomed (2x too far at 2x zoom).
 */
class V024TransformTest {

    private fun assertDelta(
        dx: Float, dy: Float, scale: Float,
        expectedX: Float, expectedY: Float
    ) {
        val (x, y) = ZoomState(scale = scale).canvasDragDelta(dx, dy)
        assertEquals(expectedX, x, 0.001f)
        assertEquals(expectedY, y, 0.001f)
    }

    @Test
    fun unzoomedDragIsIdentity() {
        assertDelta(20f, -12f, 1f, 20f, -12f)
    }

    @Test
    fun zoom2HalvesTheDelta() {
        // The reported v0.2.3 failure: at 2x zoom a 20px finger drag
        // moved the corner 20 canvas px instead of 10.
        assertDelta(20f, 10f, 2f, 10f, 5f)
    }

    @Test
    fun zoom25DoubleTapLevel() {
        // Double-tap zoom anchors at 2.5x; deltas must scale by it.
        assertDelta(25f, -10f, 2.5f, 10f, -4f)
    }

    @Test
    fun zoom8MaxScale() {
        assertDelta(80f, 40f, 8f, 10f, 5f)
    }

    @Test
    fun subUnitScaleClampsToIdentity() {
        // Scale never drops below 1 in the UI; a defensive clamp keeps
        // the mapping from ever amplifying a drag.
        assertDelta(10f, 6f, 0.5f, 10f, 6f)
    }

    @Test
    fun zeroDragStaysZeroAtAnyScale() {
        assertDelta(0f, 0f, 4f, 0f, 0f)
    }

    @Test
    fun dragDeltaAgreesWithInverseMapping() {
        // A displayed-frame delta of D at scale s must equal the
        // difference of two inverse-mapped points D apart: the gate's
        // norm() and the handle mapping must agree, or tools would
        // disagree about where a drag lands.
        val z = ZoomState(scale = 3f, offsetX = 0.2f, offsetY = -0.1f)
        val (x0, y0) = z.inverse(0.4f, 0.5f)
        val (x1, y1) = z.inverse(0.4f + 0.09f, 0.5f - 0.06f)
        val (dx, dy) = z.canvasDragDelta(0.09f * 1000f, -0.06f * 1000f)
        assertEquals((x1 - x0) * 1000f, dx, 0.01f)
        assertEquals((y1 - y0) * 1000f, dy, 0.01f)
    }
}
