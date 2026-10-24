package com.shawnrodis.photocraftexpress

import com.shawnrodis.photocraftexpress.engine.CropCorner
import com.shawnrodis.photocraftexpress.engine.CropGeometry
import com.shawnrodis.photocraftexpress.engine.CropRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CropGeometryTest {

    @Test
    fun clampRect_staysInUnitSquareWithMinSize() {
        val r = CropGeometry.clampRect(CropRect(-0.2f, 0.1f, 1.4f, 0.95f))
        assertTrue(r.left >= 0f && r.top >= 0f && r.right <= 1f && r.bottom <= 1f)
        val tiny = CropGeometry.clampRect(CropRect(0.5f, 0.5f, 0.51f, 0.9f))
        assertTrue(tiny.width >= CropGeometry.MIN_SIZE - 1e-6f)
    }

    @Test
    fun rectWithAspect_squareInLandscapeFrame() {
        // 4:3 frame, want 1:1: height saturates at 1, width = 3/4, centred.
        val r = CropGeometry.rectWithAspect(CropRect.FULL, aspect = 1f, frameAspect = 4f / 3f)
        assertEquals(1f, r.height, 1e-4f)
        assertEquals(0.75f, r.width, 1e-4f)
        assertEquals(0.5f, r.centerX, 1e-4f)
        assertEquals(0.5f, r.centerY, 1e-4f)
    }

    @Test
    fun rectWithAspect_wideInPortraitFrame() {
        // 9:16 frame (aspect 0.5625), want 16:9.
        val frameAspect = 9f / 16f
        val r = CropGeometry.rectWithAspect(CropRect.FULL, aspect = 16f / 9f, frameAspect = frameAspect)
        val pixelAspect = (r.width * frameAspect) / r.height
        assertEquals(16f / 9f, pixelAspect, 0.01f)
        assertEquals(1f, r.width, 1e-4f)
    }

    @Test
    fun rotateRectCw_knownMappingAndRoundTrip() {
        val r = CropRect(0.1f, 0.2f, 0.5f, 0.6f)
        val rot = CropGeometry.rotateRectCw(r)
        assertEquals(0.4f, rot.left, 1e-6f)
        assertEquals(0.1f, rot.top, 1e-6f)
        assertEquals(0.8f, rot.right, 1e-6f)
        assertEquals(0.5f, rot.bottom, 1e-6f)
        var back = r
        repeat(4) { back = CropGeometry.rotateRectCw(back) }
        assertEquals(r.left, back.left, 1e-6f)
        assertEquals(r.top, back.top, 1e-6f)
        assertEquals(r.right, back.right, 1e-6f)
        assertEquals(r.bottom, back.bottom, 1e-6f)
    }

    @Test
    fun flips_areInvolutions() {
        val r = CropRect(0.1f, 0.2f, 0.7f, 0.55f)
        val fh = CropGeometry.flipHRect(CropGeometry.flipHRect(r))
        assertEquals(r.left, fh.left, 1e-6f); assertEquals(r.right, fh.right, 1e-6f)
        val fv = CropGeometry.flipVRect(CropGeometry.flipVRect(r))
        assertEquals(r.top, fv.top, 1e-6f); assertEquals(r.bottom, fv.bottom, 1e-6f)
        assertEquals(CropRect.FULL, CropGeometry.flipHRect(CropRect.FULL))
    }

    @Test
    fun moveRect_clampsAtEdges() {
        val r = CropRect(0.2f, 0.2f, 0.6f, 0.6f)
        val moved = CropGeometry.moveRect(r, 5f, -5f)
        assertEquals(1f, moved.right, 1e-6f)
        assertEquals(0f, moved.top, 1e-6f)
        assertEquals(0.4f, moved.width, 1e-6f)
    }

    @Test
    fun resizeCorner_dragsAndKeepsAspectWhenAsked() {
        val r = CropRect(0.25f, 0.25f, 0.75f, 0.75f)
        val grown = CropGeometry.resizeCorner(r, CropCorner.BOTTOM_RIGHT, 0.9f, 0.9f)
        assertEquals(0.25f, grown.left, 1e-6f)
        assertEquals(0.9f, grown.right, 1e-4f)

        // Normalized aspect 1 (w == h in normalized units), drag mostly down.
        val sq = CropGeometry.resizeCorner(r, CropCorner.BOTTOM_RIGHT, 0.8f, 0.95f, aspectNorm = 1f)
        assertEquals(sq.width, sq.height, 1e-3f)
        assertEquals(0.25f, sq.left, 1e-6f)
        assertEquals(0.25f, sq.top, 1e-6f)
    }
}
