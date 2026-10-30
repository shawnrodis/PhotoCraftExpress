package com.shawnrodis.photocraftexpress

import com.shawnrodis.photocraftexpress.engine.CropCorner
import com.shawnrodis.photocraftexpress.engine.CropEdge
import com.shawnrodis.photocraftexpress.engine.CropGeometry
import com.shawnrodis.photocraftexpress.engine.CropRect
import com.shawnrodis.photocraftexpress.engine.fitFrame
import com.shawnrodis.photocraftexpress.engine.originalPixelRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * v0.2.6 tests: the "Original" aspect lock. cropAspectNorm() used to
 * return a ratio only for Fixed chips, so Original drags were
 * unconstrained — exactly Free. Original now resolves to the displayed
 * photo's own ratio; these tests pin that ratio resolution and prove
 * corner/edge drags under it preserve the photo's ratio on portrait
 * and landscape photos, while Free stays unconstrained and Fixed
 * chips behave as before.
 */
class V026CropOriginalTest {

    private fun approx(a: Float, b: Float, eps: Float = 0.001f) =
        assertTrue("expected $b, got $a", abs(a - b) < eps)

    /** Pixel w/h of a normalized rect in a frame of [frameAspect] w/h. */
    private fun pixelRatio(r: CropRect, frameAspect: Float) =
        (r.width * frameAspect) / r.height

    /**
     * The normalized lock cropAspectNorm() produces for a pixel [ratio]
     * in a frame fitted into a canvas: ratio * dispH / dispW.
     */
    private fun aspectNorm(ratio: Float, imageW: Int, imageH: Int, canvasW: Float, canvasH: Float): Float {
        val fit = fitFrame(canvasW, canvasH, imageW, imageH)
        return ratio * fit.dispH / fit.dispW
    }

    // ------------------------------------------------------------------
    // Ratio resolution
    // ------------------------------------------------------------------

    @Test fun originalRatioPortraitNoRotation() {
        // Shawn's field photo: 3000x4000.
        approx(originalPixelRatio(3000, 4000, 0), 0.75f)
    }

    @Test fun originalRatioPortraitRotated() {
        approx(originalPixelRatio(3000, 4000, 90), 4f / 3f)
        approx(originalPixelRatio(3000, 4000, 270), 4f / 3f)
        // 180 keeps the frame: no swap.
        approx(originalPixelRatio(3000, 4000, 180), 0.75f)
    }

    @Test fun originalRatioLandscape() {
        approx(originalPixelRatio(4000, 3000, 0), 4f / 3f)
        approx(originalPixelRatio(4000, 3000, 90), 0.75f)
    }

    @Test fun originalRatioDegenerate() {
        assertEquals(0f, originalPixelRatio(0, 4000, 0))
        assertEquals(0f, originalPixelRatio(3000, 0, 0))
    }

    // ------------------------------------------------------------------
    // Drags under the Original lock preserve the photo's ratio
    // ------------------------------------------------------------------

    @Test fun originalCornerDragPreservesRatioPortrait() {
        val lock = aspectNorm(originalPixelRatio(3000, 4000, 0), 3000, 4000, 1000f, 1000f)
        val r = CropGeometry.resizeCorner(CropRect.FULL, CropCorner.BOTTOM_RIGHT, 0.6f, 0.8f, lock)
        approx(pixelRatio(r, 0.75f), 0.75f)
    }

    @Test fun originalCornerDragPreservesRatioLandscape() {
        val lock = aspectNorm(originalPixelRatio(4000, 3000, 0), 4000, 3000, 1000f, 1000f)
        val r = CropGeometry.resizeCorner(CropRect.FULL, CropCorner.BOTTOM_RIGHT, 0.6f, 0.8f, lock)
        approx(pixelRatio(r, 4f / 3f), 4f / 3f)
    }

    @Test fun originalEdgeDragPreservesRatioPortrait() {
        val lock = aspectNorm(originalPixelRatio(3000, 4000, 0), 3000, 4000, 1000f, 1000f)
        val r = CropGeometry.resizeEdge(CropRect.FULL, CropEdge.RIGHT, 0.6f, 0.5f, lock)
        approx(pixelRatio(r, 0.75f), 0.75f)
    }

    // ------------------------------------------------------------------
    // Free stays unconstrained; Fixed chips unchanged
    // ------------------------------------------------------------------

    @Test fun freeDragDoesNotPreserveRatio() {
        val r = CropGeometry.resizeCorner(CropRect.FULL, CropCorner.BOTTOM_RIGHT, 0.6f, 0.8f, null)
        val ratio = pixelRatio(r, 0.75f)
        assertTrue("free drag must not hold 0.75, got $ratio", abs(ratio - 0.75f) > 0.05f)
    }

    @Test fun fixedOneToOneStillLocks() {
        val lock = aspectNorm(1f, 3000, 4000, 1000f, 1000f)
        val r = CropGeometry.resizeCorner(CropRect.FULL, CropCorner.BOTTOM_RIGHT, 0.5f, 0.6f, lock)
        approx(pixelRatio(r, 0.75f), 1f)
    }
}
