package com.shawnrodis.photocraftexpress

import com.shawnrodis.photocraftexpress.engine.Adjustments
import com.shawnrodis.photocraftexpress.engine.CropRect
import com.shawnrodis.photocraftexpress.engine.EditState
import com.shawnrodis.photocraftexpress.engine.PixelImage
import com.shawnrodis.photocraftexpress.engine.PixelPipeline
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PixelPipelineTest {

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    /** Synthetic 16x16 gradient — no real photos involved. */
    private fun synthetic(w: Int = 16, h: Int = 16): PixelImage {
        val px = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            argb(x * 255 / (w - 1), y * 255 / (h - 1), (x + y) * 255 / (w + h - 2))
        }
        return PixelImage(px, w, h)
    }

    @Test
    fun render_identityStateReturnsInputUnchanged() {
        val img = synthetic()
        val out = PixelPipeline.render(img, EditState())
        assertEquals(img.width, out.width)
        assertEquals(img.height, out.height)
        assertArrayEquals(img.pixels, out.pixels)
    }

    @Test
    fun render_lookAtZeroIntensityIsIdentity() {
        val img = synthetic()
        val out = PixelPipeline.render(img, EditState(lookId = "vivid", lookIntensity = 0f))
        assertArrayEquals(img.pixels, out.pixels)
    }

    @Test
    fun render_monoLookProducesGreyPixels() {
        val img = synthetic()
        val out = PixelPipeline.render(img, EditState(lookId = "mono", lookIntensity = 100f))
        for (p in out.pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            assertEquals("mono pixel must be grey", r, g)
            assertEquals("mono pixel must be grey", g, b)
        }
    }

    @Test
    fun render_fullDesaturationMatchesPhotocraftGrey() {
        // Saturation -100 collapses every channel to 0.288 R + 0.712 G
        // (of the post-LUT values; the LUT is identity here).
        val img = PixelImage(intArrayOf(argb(100, 150, 200)), 1, 1)
        val out = PixelPipeline.render(img, EditState(adj = Adjustments(saturation = -100f)))
        val p = out.pixels[0]
        val expected = (0.288f * 100 + 0.712f * 150).toInt()
        assertTrue(abs(expected - ((p shr 16) and 0xFF)) <= 1)
        assertTrue(abs(expected - ((p shr 8) and 0xFF)) <= 1)
        assertTrue(abs(expected - (p and 0xFF)) <= 1)
    }

    @Test
    fun render_temperatureWarmsPixel() {
        val img = PixelImage(intArrayOf(argb(128, 128, 128)), 1, 1)
        val out = PixelPipeline.render(img, EditState(adj = Adjustments(temperature = 100f)))
        val p = out.pixels[0]
        val r = (p shr 16) and 0xFF
        val b = p and 0xFF
        assertTrue("warm pushes red up", r > 140)
        assertTrue("warm pushes blue down", b < 116)
    }

    @Test
    fun render_exposureBrightens() {
        val img = synthetic()
        val plain = PixelPipeline.render(img, EditState())
        val bright = PixelPipeline.render(img, EditState(adj = Adjustments(exposure = 60f)))
        fun meanBlue(a: IntArray) = a.sumOf { it and 0xFF } / a.size
        assertTrue(meanBlue(bright.pixels) > meanBlue(plain.pixels))
    }

    @Test
    fun geometry_rotate90Cw_knownPermutation() {
        val a = argb(10, 0, 0); val b = argb(20, 0, 0)
        val c = argb(30, 0, 0); val d = argb(40, 0, 0)
        // rows: [A B] / [C D]  ->  rotated CW: [C A] / [D B]
        val out = PixelPipeline.rotate90Cw(intArrayOf(a, b, c, d), 2, 2)
        assertEquals(2, out.width); assertEquals(2, out.height)
        assertArrayEquals(intArrayOf(c, a, d, b), out.pixels)
    }

    @Test
    fun geometry_flipHorizontal_knownPermutation() {
        val a = argb(10, 0, 0); val b = argb(20, 0, 0)
        val c = argb(30, 0, 0); val d = argb(40, 0, 0)
        val out = PixelPipeline.flipHorizontal(intArrayOf(a, b, c, d), 2, 2)
        assertArrayEquals(intArrayOf(b, a, d, c), out)
    }

    @Test
    fun geometry_stateRotationAndCrop() {
        // 4x4 where blue channel = index; rotate 90 CW then crop centre 50%.
        val px = IntArray(16) { argb(0, 0, it) }
        val img = PixelImage(px, 4, 4)
        val state = EditState(
            rotation = 90,
            crop = CropRect(0.25f, 0.25f, 0.75f, 0.75f)
        )
        val geo = PixelPipeline.applyGeometry(img, state)
        assertEquals(2, geo.width)
        assertEquals(2, geo.height)
        // Rotated image rows (blue values): original index = y*4+x.
        // CW rotation: new(x,y) = old(y, 3-x). Centre 2x2 = new(1..2, 1..2)
        // = old(1,2)=9, old(2,2)... compute: new(1,1)=old(1,2)=9? old(x,y)=y*4+x:
        // new(1,1) = old(1, 3-1=2) -> 2*4+1 = 9; new(2,1) = old(1, 3-2=1) -> 1*4+1 = 5
        // new(1,2) = old(2, 2) -> 2*4+2 = 10;    new(2,2) = old(2, 1) -> 1*4+2 = 6
        assertArrayEquals(intArrayOf(9, 5, 10, 6), geo.pixels.map { it and 0xFF }.toIntArray())
    }

    @Test
    fun geometry_cropContent() {
        val px = IntArray(16) { argb(0, 0, it) }
        val out = PixelPipeline.crop(px, 4, 4, 1, 1, 3, 3)
        assertEquals(2, out.width)
        assertArrayEquals(intArrayOf(5, 6, 9, 10), out.pixels.map { it and 0xFF }.toIntArray())
    }

    @Test
    fun sharpen_increasesEdgeContrast_flatAreaUnchanged() {
        // 8x8 vertical step edge: left half 64, right half 192.
        val w = 8; val h = 8
        val px = IntArray(w * h) { i -> if (i % w < 4) argb(64, 64, 64) else argb(192, 192, 192) }
        val out = PixelPipeline.unsharp(px, w, h, radius = 1, amount = 1.0f)
        val leftOfEdge = (out[3] shr 16) and 0xFF
        val rightOfEdge = (out[4] shr 16) and 0xFF
        assertTrue("dark side of edge gets darker", leftOfEdge < 64)
        assertTrue("bright side of edge gets brighter", rightOfEdge > 192)
        // Deep inside the flat region nothing changes.
        assertEquals(64, (out[0] shr 16) and 0xFF)
        assertEquals(192, (out[7] shr 16) and 0xFF)
    }

    @Test
    fun boxBlur_ofUniformImageIsIdentity() {
        val px = IntArray(25) { argb(77, 88, 99) }
        val blurred = PixelPipeline.boxBlur(px, 5, 5, radius = 2)
        assertArrayEquals(px, blurred)
    }
}
