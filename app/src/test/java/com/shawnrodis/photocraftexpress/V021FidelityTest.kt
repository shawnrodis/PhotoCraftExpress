package com.shawnrodis.photocraftexpress

import com.shawnrodis.photocraftexpress.engine.AdjustMask
import com.shawnrodis.photocraftexpress.engine.AdjustStages
import com.shawnrodis.photocraftexpress.engine.Adjustments
import com.shawnrodis.photocraftexpress.engine.EditState
import com.shawnrodis.photocraftexpress.engine.PixelImage
import com.shawnrodis.photocraftexpress.engine.PixelPipeline
import com.shawnrodis.photocraftexpress.engine.RenderAssets
import com.shawnrodis.photocraftexpress.engine.Vignette
import com.shawnrodis.photocraftexpress.engine.ZoomState
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * v0.2.1 fidelity tests: vignette falloff smoothness, canvas zoom/pan
 * math, and the Full / Subject / Background adjustment mask. All
 * imagery is synthetic.
 */
class V021FidelityTest {

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun r(p: Int) = (p shr 16) and 0xFF

    private fun flat(w: Int, h: Int, color: Int): PixelImage =
        PixelImage(IntArray(w * h) { color }, w, h)

    // ------------------------------------------------------------------
    // Vignette falloff
    // ------------------------------------------------------------------

    @Test
    fun vignetteCenterIsUntouched() {
        assertEquals(1f, AdjustStages.vignetteFactor(0f, 100f, 50f, 50f), 0.001f)
        assertEquals(1f, AdjustStages.vignetteFactor(0.2f, 100f, 50f, 50f), 0.001f)
    }

    @Test
    fun vignetteCornerReachesFullAmount() {
        // At full falloff the multiplier is exactly 1 - amount.
        assertEquals(0f, AdjustStages.vignetteFactor(1f, 100f, 0f, 50f), 0.001f)
        assertEquals(0.5f, AdjustStages.vignetteFactor(1f, 50f, 0f, 50f), 0.001f)
    }

    @Test
    fun vignetteIsMonotonicAndStepFree() {
        var prev = Float.MAX_VALUE
        var maxStep = 0f
        var d = 0f
        while (d <= 1.0001f) {
            val f = AdjustStages.vignetteFactor(d, 100f, 30f, 60f)
            assertTrue("falloff rose at d=$d ($prev -> $f)", f <= prev + 1e-6f)
            if (prev != Float.MAX_VALUE) maxStep = maxOf(maxStep, prev - f)
            prev = f
            d += 0.005f
        }
        // No visible banding: adjacent samples (0.5% of radius apart)
        // never jump by more than 2% of the multiplier range.
        assertTrue("max adjacent step $maxStep", maxStep < 0.02f)
    }

    @Test
    fun vignetteSlopeIsZeroAtFalloffStart() {
        // Smoothstep has zero slope at both ends of the feather zone;
        // a bare linear/quadratic ramp would not. Compare the slope
        // just inside the zone against the mid-zone slope.
        val mid = 0.3f
        val startSlope = abs(
            AdjustStages.vignetteFactor(mid + 0.02f, 100f, 30f, 60f) -
                AdjustStages.vignetteFactor(mid, 100f, 30f, 60f)
        ) / 0.02f
        val midSlope = abs(
            AdjustStages.vignetteFactor(0.62f, 100f, 30f, 60f) -
                AdjustStages.vignetteFactor(0.60f, 100f, 30f, 60f)
        ) / 0.02f
        assertTrue("start slope $startSlope not below mid slope $midSlope", startSlope < midSlope)
    }

    @Test
    fun vignetteNegativeAmountBrightensMonotonically() {
        var prev = 1f
        var d = 0f
        while (d <= 1.0001f) {
            val f = AdjustStages.vignetteFactor(d, -100f, 30f, 60f)
            assertTrue("brightening fell at d=$d ($prev -> $f)", f >= prev - 1e-6f)
            prev = f
            d += 0.01f
        }
        assertEquals(2f, prev, 0.001f)
    }

    @Test
    fun vignetteEndToEndCenterUntouchedCornerDarkened() {
        val grey = argb(128, 128, 128)
        val img = flat(20, 20, grey)
        val out = PixelPipeline.render(
            img, EditState(vignette = Vignette(amount = 100f, midPoint = 30f, feather = 60f))
        )
        // Centre pixel: inside the falloff start, untouched.
        assertEquals(grey, out.pixels[10 * 20 + 10])
        // Corner pixel: full falloff, fully darkened.
        assertEquals(argb(0, 0, 0), out.pixels[0])
        // Monotonic along the centre row from centre to edge.
        var prev = r(out.pixels[10 * 20 + 10])
        for (x in 9 downTo 0) {
            val v = r(out.pixels[10 * 20 + x])
            assertTrue("row rose at x=$x ($prev -> $v)", v <= prev)
            prev = v
        }
    }

    // ------------------------------------------------------------------
    // Zoom / pan math
    // ------------------------------------------------------------------

    @Test
    fun zoomIdentityMaps() {
        val z = ZoomState.IDENTITY
        assertEquals(0.25f to 0.75f, z.forward(0.25f, 0.75f))
        assertEquals(0.25f to 0.75f, z.inverse(0.25f, 0.75f))
        assertTrue(!z.isZoomed)
    }

    @Test
    fun zoomForwardInverseRoundTrip() {
        val z = ZoomState.IDENTITY.zoomAt(0.3f, 0.7f, 2f).pan(0.1f, -0.05f)
        for ((x, y) in listOf(0f to 0f, 0.5f to 0.5f, 1f to 1f, 0.2f to 0.9f)) {
            val (fx, fy) = z.forward(x, y)
            val (bx, by) = z.inverse(fx, fy)
            assertEquals(x, bx, 1e-5f)
            assertEquals(y, by, 1e-5f)
        }
    }

    @Test
    fun zoomAtAnchorsTheCentroid() {
        val z = ZoomState.IDENTITY.zoomAt(0.4f, 0.6f, 3f)
        val (fx, fy) = z.forward(0.4f, 0.6f)
        assertEquals(0.4f, fx, 1e-5f)
        assertEquals(0.6f, fy, 1e-5f)
        assertEquals(3f, z.scale, 1e-5f)
    }

    @Test
    fun zoomPanShifts() {
        val z = ZoomState.IDENTITY.zoomAt(0.5f, 0.5f, 2f).pan(0.1f, 0.2f)
        val (fx, fy) = z.forward(0.5f, 0.5f)
        assertEquals(0.6f, fx, 1e-5f)
        assertEquals(0.7f, fy, 1e-5f)
    }

    @Test
    fun zoomScaleIsClamped() {
        assertEquals(8f, ZoomState.IDENTITY.zoomAt(0.5f, 0.5f, 100f).scale, 1e-5f)
        assertEquals(1f, ZoomState.IDENTITY.zoomAt(0.5f, 0.5f, 0.01f).scale, 1e-5f)
        assertEquals(1f, ZoomState.IDENTITY.pan(5f, 5f).scale, 1e-5f)
    }

    @Test
    fun zoomToggleAt() {
        val zoomed = ZoomState.IDENTITY.toggleAt(0.25f, 0.25f)
        assertEquals(2.5f, zoomed.scale, 1e-5f)
        assertTrue(zoomed.isZoomed)
        val (fx, fy) = zoomed.forward(0.25f, 0.25f)
        assertEquals(0.25f, fx, 1e-5f)
        assertEquals(0.25f, fy, 1e-5f)
        assertEquals(ZoomState.IDENTITY, zoomed.toggleAt(0.25f, 0.25f))
    }

    // ------------------------------------------------------------------
    // Adjustment mask
    // ------------------------------------------------------------------

    /** Person mask: left half confident, right half empty. */
    private fun halfMask(w: Int, h: Int): PixelImage {
        val px = IntArray(w * h) { i -> if (i % w < w / 2) argb(255, 0, 0) else argb(0, 0, 0) }
        return PixelImage(px, w, h)
    }

    @Test
    fun maskSubjectAndBackgroundSplit() {
        val grey = argb(128, 128, 128)
        val img = flat(8, 4, grey)
        val bright = EditState(adj = Adjustments(exposure = 100f))
        val full = PixelPipeline.render(img, bright)
        val leftFull = r(full.pixels[0])
        assertTrue("exposure must brighten, got $leftFull", leftFull > 128)

        val subject = PixelPipeline.render(
            img, bright.copy(adjustMask = AdjustMask.SUBJECT),
            assets = RenderAssets(personMaskSrc = halfMask(8, 4))
        )
        val background = PixelPipeline.render(
            img, bright.copy(adjustMask = AdjustMask.BACKGROUND),
            assets = RenderAssets(personMaskSrc = halfMask(8, 4))
        )
        // Subject: left (person) matches FULL, right (background) is the
        // untouched look-only base.
        assertEquals(leftFull, r(subject.pixels[0]))
        assertEquals(128, r(subject.pixels[7]))
        // Background: the exact inverse.
        assertEquals(128, r(background.pixels[0]))
        assertEquals(leftFull, r(background.pixels[7]))
    }

    @Test
    fun maskFullIgnoresPersonMask() {
        val grey = argb(128, 128, 128)
        val img = flat(8, 4, grey)
        val bright = EditState(adj = Adjustments(exposure = 100f))
        val withMask = PixelPipeline.render(
            img, bright, assets = RenderAssets(personMaskSrc = halfMask(8, 4))
        )
        val withoutMask = PixelPipeline.render(img, bright)
        assertTrue(withMask.pixels.contentEquals(withoutMask.pixels))
    }

    @Test
    fun maskNoPersonSuppressesSubjectAndFreesBackground() {
        val grey = argb(128, 128, 128)
        val img = flat(8, 4, grey)
        val bright = EditState(adj = Adjustments(exposure = 100f))
        val full = PixelPipeline.render(img, bright)
        val leftFull = r(full.pixels[0])

        // No mask at all: Subject selects nothing, Background everything.
        val subjectNone = PixelPipeline.render(
            img, bright.copy(adjustMask = AdjustMask.SUBJECT)
        )
        val backgroundNone = PixelPipeline.render(
            img, bright.copy(adjustMask = AdjustMask.BACKGROUND)
        )
        assertEquals(128, r(subjectNone.pixels[0]))
        assertEquals(128, r(subjectNone.pixels[7]))
        assertEquals(leftFull, r(backgroundNone.pixels[0]))
        assertEquals(leftFull, r(backgroundNone.pixels[7]))

        // Low-confidence mask everywhere (below the 0.5 person
        // threshold) behaves the same as no person.
        val weak = PixelImage(IntArray(8 * 4) { argb(100, 0, 0) }, 8, 4)
        val subjectWeak = PixelPipeline.render(
            img, bright.copy(adjustMask = AdjustMask.SUBJECT),
            assets = RenderAssets(personMaskSrc = weak)
        )
        assertEquals(128, r(subjectWeak.pixels[0]))
    }

    @Test
    fun renderCancellationAborts() {
        val img = flat(8, 8, argb(128, 128, 128))
        assertThrows(CancellationException::class.java) {
            PixelPipeline.render(
                img, EditState(adj = Adjustments(exposure = 50f)),
                isActive = { false }
            )
        }
    }

    @Test
    fun renderCancellationAbortsMidPipeline() {
        // A superseded slider drag must abort at a stage boundary, not
        // run the whole pipeline: allow the first boundary check, then
        // report inactive — the render must throw instead of finishing.
        val img = flat(8, 8, argb(128, 128, 128))
        var calls = 0
        assertThrows(CancellationException::class.java) {
            PixelPipeline.render(
                img, EditState(adj = Adjustments(exposure = 50f)),
                isActive = { ++calls == 1 }
            )
        }
        assertTrue("expected abort after the tone stage, calls=$calls", calls >= 2)
    }
}
