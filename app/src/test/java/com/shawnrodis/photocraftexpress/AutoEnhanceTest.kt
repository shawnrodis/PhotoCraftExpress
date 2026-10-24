package com.shawnrodis.photocraftexpress

import com.shawnrodis.photocraftexpress.engine.Adjustments
import com.shawnrodis.photocraftexpress.engine.AutoEnhance
import com.shawnrodis.photocraftexpress.engine.EditState
import com.shawnrodis.photocraftexpress.engine.PixelImage
import com.shawnrodis.photocraftexpress.engine.PixelPipeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wand auto-enhance: histogram deltas and their visible effect. */
class AutoEnhanceTest {

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun flat(w: Int, h: Int, color: Int): PixelImage =
        PixelImage(IntArray(w * h) { color }, w, h)

    private fun meanLuma(img: PixelImage): Double {
        var sum = 0.0
        for (p in img.pixels) {
            sum += 0.299 * ((p shr 16) and 0xFF) +
                0.587 * ((p shr 8) and 0xFF) +
                0.114 * (p and 0xFF)
        }
        return sum / img.pixels.size
    }

    @Test
    fun balancedStatsYieldZeroDeltas() {
        val d = AutoEnhance.deltas(
            AutoEnhance.Stats(meanLuma = 128f, p1 = 0, p99 = 255, meanSaturation = 0.5f)
        )
        assertEquals(0f, d.exposure, 0f)
        assertEquals(0f, d.contrast, 0f)
        assertEquals(0f, d.vibrance, 0f)
    }

    @Test
    fun darkDullFlatStatsYieldExposureAndVibranceButNoContrast() {
        val d = AutoEnhance.deltas(
            AutoEnhance.Stats(meanLuma = 30f, p1 = 0, p99 = 0, meanSaturation = 0f)
        )
        // EV to centre mean 30 is >1 stop, clamped to +1 EV = +50.
        assertEquals(50f, d.exposure, 0.001f)
        // Dark photos get no contrast: it would crush their shadows.
        assertEquals(0f, d.contrast, 0.001f)
        assertEquals(30f, d.vibrance, 0.001f)
    }

    @Test
    fun balancedNarrowSpreadStatsYieldContrast() {
        val d = AutoEnhance.deltas(
            AutoEnhance.Stats(meanLuma = 128f, p1 = 60, p99 = 140, meanSaturation = 0.5f)
        )
        assertEquals(0f, d.exposure, 0.001f)
        // Spread 80 -> (200-80)/200*25 = 15.
        assertEquals(15f, d.contrast, 0.001f)
        assertEquals(0f, d.vibrance, 0.001f)
    }

    @Test
    fun brightStatsYieldNegativeExposure() {
        val d = AutoEnhance.deltas(
            AutoEnhance.Stats(meanLuma = 240f, p1 = 255, p99 = 255, meanSaturation = 0.5f)
        )
        assertTrue(d.exposure < -40f)
        assertEquals(0f, d.contrast, 0f)
    }

    @Test
    fun deadZoneMeanEarnsNoExposure() {
        val d = AutoEnhance.deltas(
            AutoEnhance.Stats(meanLuma = 130f, p1 = 0, p99 = 255, meanSaturation = 0.5f)
        )
        assertEquals(0f, d.exposure, 0f)
    }

    @Test
    fun deltasForDarkImageBrightensItThroughThePipeline() {
        // Varied dark photo: tones from 20 to 120, mean ~70.
        val w = 64
        val px = IntArray(w * w) { i ->
            val v = 20 + (i % w) * 100 / (w - 1)
            argb(v, v, v)
        }
        val img = PixelImage(px, w, w)
        val delta = AutoEnhance.deltasFor(img)
        val enhanced = AutoEnhance.apply(Adjustments(), delta)
        val out = PixelPipeline.render(img, EditState(adj = enhanced))
        // Mean moves substantially toward mid-grey, and the tonal spread
        // widens (exposure stretches the range even without contrast).
        assertTrue(meanLuma(out) > meanLuma(img) + 10.0)
        val spreadIn = 120.0 - 20.0
        var mn = 255.0
        var mx = 0.0
        for (p in out.pixels) {
            val l = 0.299 * ((p shr 16) and 0xFF) +
                0.587 * ((p shr 8) and 0xFF) + 0.114 * (p and 0xFF)
            if (l < mn) mn = l
            if (l > mx) mx = l
        }
        assertTrue(mx - mn > spreadIn)
    }

    @Test
    fun applyClampsToSliderRange() {
        val base = Adjustments(exposure = 90f)
        val out = AutoEnhance.apply(base, Adjustments(exposure = 50f))
        assertEquals(100f, out.exposure, 0f)
        val out2 = AutoEnhance.apply(Adjustments(), Adjustments(exposure = -50f))
        assertEquals(-50f, out2.exposure, 0f)
    }

    @Test
    fun secondTapConverges() {
        // Tapping the wand again earns a SMALLER correction and lands
        // closer to mid-grey: the wand converges, never oscillates.
        val w = 64
        val px = IntArray(w * w) { i ->
            val v = 20 + (i % w) * 100 / (w - 1)
            argb(v, v, v)
        }
        val img = PixelImage(px, w, w)
        val first = AutoEnhance.deltasFor(img)
        val img1 = PixelPipeline.render(
            img, EditState(adj = AutoEnhance.apply(Adjustments(), first))
        )
        val second = AutoEnhance.deltasFor(img1)
        assertTrue(kotlin.math.abs(second.exposure) < kotlin.math.abs(first.exposure))
        assertTrue(
            kotlin.math.abs(meanLuma(img1) - 128.0) <
                kotlin.math.abs(meanLuma(img) - 128.0)
        )
    }
}
