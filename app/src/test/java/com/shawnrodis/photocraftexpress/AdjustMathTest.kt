package com.shawnrodis.photocraftexpress

import com.shawnrodis.photocraftexpress.engine.AdjustMath
import com.shawnrodis.photocraftexpress.engine.Adjustments
import com.shawnrodis.photocraftexpress.engine.CurvePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AdjustMathTest {

    private fun assertLutIdentity(lut: FloatArray, tol: Float = 1.5f / 255f) {
        for (i in lut.indices) {
            val id = i / (lut.size - 1).toFloat()
            assertTrue("lut[$i]=${lut[i]} expected ~$id", abs(lut[i] - id) <= tol)
        }
    }

    @Test
    fun toneLut_identityAtDefaults() {
        assertLutIdentity(AdjustMath.buildToneLut(Adjustments()), tol = 0.01f / 255f)
    }

    @Test
    fun exposureLut_monotonicAndDirectional() {
        val up = AdjustMath.buildToneLut(Adjustments(exposure = 50f))
        val down = AdjustMath.buildToneLut(Adjustments(exposure = -50f))
        for (i in 1 until up.size) {
            assertTrue("exposure LUT must be non-decreasing at $i", up[i] >= up[i - 1] - 1e-6f)
        }
        assertTrue("+exposure brightens midtones", up[128] > 128f / 255f + 0.05f)
        assertTrue("-exposure darkens midtones", down[128] < 128f / 255f - 0.05f)
        assertEquals(0f, up[0], 1e-6f)
    }

    @Test
    fun contrastLut_endpointsFixedMidtonePivot() {
        val lut = AdjustMath.buildToneLut(Adjustments(contrast = 60f))
        assertEquals(0f, lut[0], 1e-6f)
        assertEquals(1f, lut[255], 1e-6f)
        assertEquals(0.5f, lut[128], 2f / 255f)
        assertTrue("contrast darkens shadows", lut[64] < 64f / 255f)
        assertTrue("contrast brightens highlights", lut[192] > 192f / 255f)
    }

    @Test
    fun levelsGamma_identityAtOne() {
        for (u in listOf(0f, 0.1f, 0.25f, 0.5f, 0.9f, 1f)) {
            assertEquals(u, AdjustMath.levelsGamma(u, 1f), 1e-5f)
        }
    }

    @Test
    fun levelsGamma_endpointsAndMidtoneLift() {
        assertEquals(0f, AdjustMath.levelsGamma(0f, 2.2f), 1e-6f)
        assertEquals(1f, AdjustMath.levelsGamma(1f, 2.2f), 1e-6f)
        assertTrue("gamma > 1 lifts midtones", AdjustMath.levelsGamma(0.5f, 2f) > 0.5f)
        assertTrue("gamma < 1 lowers midtones", AdjustMath.levelsGamma(0.5f, 0.5f) < 0.5f)
        // Above the toe join the ported curve follows the plain power curve.
        assertEquals(0.5f, AdjustMath.levelsGamma(0.25f, 2f), 0.02f)
    }

    @Test
    fun levelsTransform_fadeLiftsBlackPoint() {
        val lifted = AdjustMath.levelsTransform(0f, 0f, 1f, 1f, 0.14f, 0.958f)
        assertEquals(0.14f, lifted, 1e-5f)
        val top = AdjustMath.levelsTransform(1f, 0f, 1f, 1f, 0.14f, 0.958f)
        assertEquals(0.958f, top, 1e-5f)
        val identity = AdjustMath.levelsTransform(0.37f, 0f, 1f, 1f, 0f, 1f)
        assertEquals(0.37f, identity, 1e-5f)
    }

    @Test
    fun curveLut_diagonalIsIdentity() {
        val lut = AdjustMath.curveLut(listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)))
        assertLutIdentity(lut, tol = 1f / 255f)
    }

    @Test
    fun curveLut_sCurveShape() {
        val lut = AdjustMath.curveLut(
            listOf(
                CurvePoint(0f, 0f), CurvePoint(0.25f, 0.15f), CurvePoint(0.5f, 0.5f),
                CurvePoint(0.75f, 0.85f), CurvePoint(1f, 1f)
            )
        )
        assertEquals(0f, lut[0], 1e-6f)
        assertEquals(1f, lut[255], 1e-6f)
        assertEquals(0.5f, lut[128], 3f / 255f)
        assertTrue("S-curve darkens quarter tones", lut[64] < 64f / 255f - 0.02f)
        assertTrue("S-curve brightens three-quarter tones", lut[192] > 192f / 255f + 0.02f)
    }

    @Test
    fun lutComposition_matchesSequentialApplication() {
        val f = AdjustMath.buildToneLut(Adjustments(contrast = 40f))
        val g = AdjustMath.buildToneLut(Adjustments(exposure = 30f))
        val composed = AdjustMath.composeLuts(f, g)
        for (i in composed.indices) {
            val sequential = AdjustMath.sampleLut(g, f[i])
            assertTrue("compose mismatch at $i", abs(composed[i] - sequential) <= 1.5f / 255f)
        }
    }

    @Test
    fun blendLut_zeroIsIdentity_fullIsOriginal() {
        val lut = AdjustMath.buildToneLut(Adjustments(exposure = 80f, contrast = 30f))
        assertLutIdentity(AdjustMath.blendLut(lut, 0f), tol = 0.01f / 255f)
        val full = AdjustMath.blendLut(lut, 1f)
        for (i in lut.indices) assertEquals(lut[i], full[i], 1e-6f)
        val half = AdjustMath.blendLut(lut, 0.5f)
        assertEquals((lut[200] + 200f / 255f) / 2f, half[200], 1e-5f)
    }

    @Test
    fun vibrance_greyPixelUnchanged() {
        val out = FloatArray(3)
        AdjustMath.vibranceLin(0.4f, 0.4f, 0.4f, 0.9f, out)
        assertEquals(0.4f, out[0], 1e-6f)
        assertEquals(0.4f, out[1], 1e-6f)
        assertEquals(0.4f, out[2], 1e-6f)
    }

    @Test
    fun vibrance_boostsMutedColour_dampsWhenNegative() {
        val out = FloatArray(3)
        AdjustMath.vibranceLin(0.50f, 0.42f, 0.40f, 1f, out)
        val spreadIn = 0.50f - 0.40f
        val spreadOut = maxOf(out[0], out[1], out[2]) - minOf(out[0], out[1], out[2])
        assertTrue("positive vibrance widens channel spread", spreadOut > spreadIn)

        AdjustMath.vibranceLin(0.50f, 0.42f, 0.40f, -1f, out)
        val spreadNeg = maxOf(out[0], out[1], out[2]) - minOf(out[0], out[1], out[2])
        assertTrue("negative vibrance narrows channel spread", spreadNeg < spreadIn)
    }

    @Test
    fun saturationGrey_usesPhotocraftWeights() {
        // 0.288 R + 0.712 G, blue contributes nothing.
        assertEquals(0.288f * 0.5f + 0.712f * 0.25f, AdjustMath.saturationGrey(0.5f, 0.25f), 1e-6f)
    }
}
