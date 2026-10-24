package com.shawnrodis.photocraftexpress

import com.shawnrodis.photocraftexpress.engine.BlurFx
import com.shawnrodis.photocraftexpress.engine.BlurMode
import com.shawnrodis.photocraftexpress.engine.BorderFx
import com.shawnrodis.photocraftexpress.engine.BorderKind
import com.shawnrodis.photocraftexpress.engine.ColorMix
import com.shawnrodis.photocraftexpress.engine.EditState
import com.shawnrodis.photocraftexpress.engine.ExportSize
import com.shawnrodis.photocraftexpress.engine.ExportSizeMode
import com.shawnrodis.photocraftexpress.engine.HealMath
import com.shawnrodis.photocraftexpress.engine.LayerRaster
import com.shawnrodis.photocraftexpress.engine.MaskPoint
import com.shawnrodis.photocraftexpress.engine.PixelImage
import com.shawnrodis.photocraftexpress.engine.PixelPipeline
import com.shawnrodis.photocraftexpress.engine.SplitTone
import com.shawnrodis.photocraftexpress.engine.Vignette
import com.shawnrodis.photocraftexpress.engine.WarpMath
import com.shawnrodis.photocraftexpress.ui.UpdateChecker
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * v0.2.0 math tests: split tone, color mix, vignette, blur parameters,
 * export resize, border geometry, heal determinism, warp math, brush
 * masks and the updater's version compare. All imagery is synthetic.
 */
class V020NewMathTest {

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun r(p: Int) = (p shr 16) and 0xFF
    private fun g(p: Int) = (p shr 8) and 0xFF
    private fun b(p: Int) = p and 0xFF

    private fun flat(w: Int, h: Int, color: Int): PixelImage =
        PixelImage(IntArray(w * h) { color }, w, h)

    // ------------------------------------------------------------------
    // Split Tone
    // ------------------------------------------------------------------

    @Test
    fun splitTone_identityAtZeroSaturation() {
        assertTrue(SplitTone(shadowSat = 0f, highlightSat = 0f).isIdentity)
        val img = flat(8, 8, argb(120, 100, 80))
        val out = PixelPipeline.render(img, EditState(splitTone = SplitTone()))
        assertArrayEquals(img.pixels, out.pixels)
    }

    @Test
    fun splitTone_shadowHueTintsDarkPixelsOnly() {
        // Dark pixel gets the shadow hue; a bright pixel must not gain it.
        val dark = argb(30, 30, 30)
        val bright = argb(240, 240, 240)
        val img = PixelImage(intArrayOf(dark, bright), 2, 1)
        val out = PixelPipeline.render(
            img,
            EditState(splitTone = SplitTone(shadowHue = 220f, shadowSat = 100f))
        )
        val dOut = out.pixels[0]
        // Blue shadow tint: blue channel rises above red on the dark pixel.
        assertTrue(b(dOut) > r(dOut))
        // Bright pixel stays near-white (no shadow tint leaking upward).
        assertTrue(abs(r(out.pixels[1]) - 240) < 30)
    }

    @Test
    fun splitTone_highlightHueTintsBrightPixels() {
        val img = PixelImage(intArrayOf(argb(240, 240, 240)), 1, 1)
        val out = PixelPipeline.render(
            img,
            EditState(splitTone = SplitTone(highlightHue = 40f, highlightSat = 100f))
        )
        // Warm highlight tint: red above blue.
        assertTrue(r(out.pixels[0]) > b(out.pixels[0]))
    }

    // ------------------------------------------------------------------
    // Color Mix
    // ------------------------------------------------------------------

    @Test
    fun colorMix_identityByDefault() {
        assertTrue(ColorMix().isIdentity)
        val img = flat(8, 8, argb(200, 30, 30))
        val out = PixelPipeline.render(img, EditState(colorMix = ColorMix()))
        assertArrayEquals(img.pixels, out.pixels)
    }

    @Test
    fun colorMix_redHueShiftLeavesBlueUntouched() {
        // Channel 0 = red. Shift red hues hard; a pure blue pixel must
        // survive unchanged, proving channel isolation.
        val red = argb(220, 20, 20)
        val blue = argb(20, 20, 220)
        val img = PixelImage(intArrayOf(red, blue), 2, 1)
        val mix = ColorMix().withChannel(0, 100f, 0f, 0f)
        val out = PixelPipeline.render(img, EditState(colorMix = mix))
        assertEquals(blue, out.pixels[1])
        // The red pixel moved (hue shift changes its channel balance).
        assertFalse(out.pixels[0] == red)
    }

    @Test
    fun colorMix_luminanceShiftBrightensOnlyItsChannel() {
        val red = argb(150, 20, 20)
        val green = argb(20, 150, 20)
        val img = PixelImage(intArrayOf(red, green), 2, 1)
        val mix = ColorMix().withChannel(0, 0f, 0f, 100f)
        val out = PixelPipeline.render(img, EditState(colorMix = mix))
        assertEquals(green, out.pixels[1])
        assertTrue(r(out.pixels[0]) > r(red))
    }

    // ------------------------------------------------------------------
    // Vignette
    // ------------------------------------------------------------------

    @Test
    fun vignette_identityAtZeroAmount() {
        assertTrue(Vignette(amount = 0f).isIdentity)
        val img = flat(16, 16, argb(180, 180, 180))
        val out = PixelPipeline.render(img, EditState(vignette = Vignette()))
        assertArrayEquals(img.pixels, out.pixels)
    }

    @Test
    fun vignette_darkensCornersMoreThanCenter() {
        val img = flat(32, 32, argb(200, 200, 200))
        val out = PixelPipeline.render(
            img, EditState(vignette = Vignette(amount = 100f, midPoint = 50f, feather = 50f))
        )
        val center = out.pixels[16 * 32 + 16]
        val corner = out.pixels[0]
        // Center barely moves; corner drops a lot.
        assertTrue(r(center) > 170)
        assertTrue(r(corner) < r(center) - 40)
    }

    @Test
    fun vignette_negativeAmountBrightensCorners() {
        val img = flat(32, 32, argb(100, 100, 100))
        val out = PixelPipeline.render(
            img, EditState(vignette = Vignette(amount = -100f, midPoint = 50f, feather = 50f))
        )
        assertTrue(r(out.pixels[0]) > r(img.pixels[0]))
    }

    // ------------------------------------------------------------------
    // Blur parameters
    // ------------------------------------------------------------------

    @Test
    fun blur_identityWhenModeNoneOrAmountZero() {
        assertTrue(BlurFx(mode = BlurMode.NONE, amount = 100f).isIdentity)
        assertTrue(BlurFx(mode = BlurMode.FULL, amount = 0f).isIdentity)
        val img = flat(8, 8, argb(90, 140, 200))
        val out = PixelPipeline.render(img, EditState(blur = BlurFx(BlurMode.FULL, 0f)))
        assertArrayEquals(img.pixels, out.pixels)
    }

    @Test
    fun blur_fullBlursAHardEdge() {
        // Left half black, right half white: after a full blur the edge
        // pixels must be intermediate greys, not pure black/white.
        val w = 32
        val px = IntArray(w * 8) { i -> if ((i % w) < w / 2) argb(0, 0, 0) else argb(255, 255, 255) }
        val img = PixelImage(px, w, 8)
        val out = PixelPipeline.render(img, EditState(blur = BlurFx(BlurMode.FULL, 100f)))
        val edgeLeft = r(out.pixels[4 * w + w / 2 - 1])
        val edgeRight = r(out.pixels[4 * w + w / 2])
        assertTrue(edgeLeft in 1..254)
        assertTrue(edgeRight in 1..254)
    }

    @Test
    fun blur_radialBlursInsideEllipseLeavesOutsideUntouched() {
        // Left half black, right half white; ellipse centred on the edge.
        val w = 32
        val px = IntArray(w * w) { i -> if ((i % w) < w / 2) argb(0, 0, 0) else argb(255, 255, 255) }
        val img = PixelImage(px, w, w)
        val out = PixelPipeline.render(
            img,
            EditState(
                blur = BlurFx(
                    mode = BlurMode.RADIAL, amount = 100f, feather = 0f,
                    cx = 0.5f, cy = 0.5f, rx = 0.2f, ry = 0.2f
                )
            )
        )
        // Far corner, outside the ellipse: untouched black.
        assertEquals(argb(0, 0, 0), out.pixels[0])
        // Edge pixel inside the ellipse: blurred to an intermediate grey.
        val edge = r(out.pixels[16 * w + 15])
        assertTrue(edge in 1..254)
    }

    // ------------------------------------------------------------------
    // Export resize
    // ------------------------------------------------------------------

    @Test
    fun exportSize_originalIsNull() {
        assertNull(ExportSize(ExportSizeMode.ORIGINAL).outputSize(4000, 3000))
    }

    @Test
    fun exportSize_longEdgeScalesDownOnly() {
        assertEquals(1080 to 810, ExportSize(ExportSizeMode.LONG_1080).outputSize(4000, 3000))
        // Already smaller: no upscale.
        assertNull(ExportSize(ExportSizeMode.LONG_2048).outputSize(1000, 750))
    }

    @Test
    fun exportSize_percent50Halves() {
        assertEquals(1000 to 500, ExportSize(ExportSizeMode.PERCENT_50).outputSize(2000, 1000))
    }

    @Test
    fun exportSize_customLockAspectUsesWidth() {
        val size = ExportSize(ExportSizeMode.CUSTOM, customW = 800, lockAspect = true)
        assertEquals(800 to 600, size.outputSize(4000, 3000))
    }

    @Test
    fun exportSize_customUnlockedUsesBoth() {
        val size = ExportSize(ExportSizeMode.CUSTOM, customW = 800, customH = 100, lockAspect = false)
        assertEquals(800 to 100, size.outputSize(4000, 3000))
    }

    // ------------------------------------------------------------------
    // Borders
    // ------------------------------------------------------------------

    @Test
    fun border_plainFramePaintsCornersLeavesCenter() {
        val img = flat(20, 20, argb(10, 200, 30))
        val border = BorderFx(kind = BorderKind.PLAIN, width = 0.1f, color = argb(255, 255, 255))
        val out = LayerRaster.applyBorder(img.pixels, 20, 20, border)
        assertEquals(argb(255, 255, 255), out[0])
        assertEquals(argb(255, 255, 255), out[19 * 20 + 19])
        assertEquals(argb(10, 200, 30), out[10 * 20 + 10])
    }

    @Test
    fun border_zeroWidthIsIdentity() {
        assertTrue(BorderFx(width = 0f).isIdentity)
        val img = flat(10, 10, argb(1, 2, 3))
        val out = LayerRaster.applyBorder(img.pixels, 10, 10, BorderFx())
        assertArrayEquals(img.pixels, out)
    }

    // ------------------------------------------------------------------
    // Heal determinism + eye correction
    // ------------------------------------------------------------------

    @Test
    fun heal_isDeterministic() {
        val w = 24
        val px = IntArray(w * w) { i ->
            val x = i % w
            argb(180 + (x % 5), 150, 130)
        }
        // A dark blemish in the middle.
        px[12 * w + 12] = argb(20, 20, 20)
        val spots = listOf(HealMath.HealSpot(0.5f, 0.5f, 0.08f))
        val a = HealMath.heal(px, w, w, spots)
        val b = HealMath.heal(px, w, w, spots)
        assertArrayEquals(a, b)
        // The blemish moved toward the surrounding skin, not to black.
        assertTrue(r(a[12 * w + 12]) > 100)
        // Far pixels untouched.
        assertEquals(px[0], a[0])
    }

    @Test
    fun eyeFix_desaturatesRedGlare() {
        val w = 16
        val px = IntArray(w * w) { argb(60, 60, 60) }
        px[8 * w + 8] = argb(255, 10, 10) // red glare
        val out = HealMath.correctEyes(
            px, w, w, listOf(HealMath.EyeFix(0.5f, 0.5f, 0.1f, pet = false))
        )
        // Red dominance gone: red no longer towers over green/blue.
        assertTrue(r(out[8 * w + 8]) - g(out[8 * w + 8]) < 60)
    }

    // ------------------------------------------------------------------
    // Warp math
    // ------------------------------------------------------------------

    @Test
    fun warp_identityOpsReturnInput() {
        val img = flat(12, 12, argb(77, 88, 99))
        val out = WarpMath.warp(img.pixels, 12, 12, emptyList(), emptyList())
        assertArrayEquals(img.pixels, out)
    }

    @Test
    fun warp_pushDisplacesPixels() {
        // Vertical stripe pattern: a push to the right must shift it.
        val w = 32
        val px = IntArray(w * w) { i -> if ((i % w) < 4) argb(255, 255, 255) else argb(0, 0, 0) }
        val op = WarpMath.BrushOp(
            mode = WarpMath.BrushMode.PUSH,
            cx = 0.5f, cy = 0.5f, radius = 0.4f, strength = 1f,
            dx = 0.2f, dy = 0f
        )
        val out = WarpMath.warp(px, w, w, listOf(op), emptyList())
        assertFalse(out.contentEquals(px))
    }

    @Test
    fun warp_restoreErasesEarlierOps() {
        val push = WarpMath.BrushOp(
            WarpMath.BrushMode.PUSH, 0.5f, 0.5f, 0.3f, 1f, 0.2f, 0f
        )
        val restore = WarpMath.BrushOp(
            WarpMath.BrushMode.RESTORE, 0.5f, 0.5f, 0.3f, 1f
        )
        assertTrue(WarpMath.resolveOps(listOf(push, restore)).isEmpty())
        assertEquals(1, WarpMath.resolveOps(listOf(push)).size)
    }

    @Test
    fun warp_identityQuadIsIdentity() {
        assertTrue(WarpMath.Quad.IDENTITY.isIdentity)
        val img = flat(10, 10, argb(5, 6, 7))
        val out = WarpMath.perspectiveWarp(img.pixels, 10, 10, WarpMath.Quad.IDENTITY)
        assertArrayEquals(img.pixels, out)
    }

    @Test
    fun warp_forwardPointOfIdentityQuad() {
        val p = WarpMath.forwardPoint(WarpMath.Quad.IDENTITY, 0.25f, 0.75f)
        assertEquals(0.25f, p.first, 0.001f)
        assertEquals(0.75f, p.second, 0.001f)
    }

    // ------------------------------------------------------------------
    // Brush mask + updater version compare
    // ------------------------------------------------------------------

    @Test
    fun mask_rasterizesStrokeCenterAndLeavesFarCorner() {
        val mask = LayerRaster.rasterizeMask(
            20, 20, listOf(MaskPoint(0.5f, 0.5f, 0.1f))
        )
        assertTrue(mask[10 * 20 + 10] > 0.9f)
        assertEquals(0f, mask[0], 0f)
    }

    @Test
    fun updateChecker_versionCompare() {
        assertTrue(UpdateChecker.isNewer("v0.2.0", "0.1.1"))
        assertFalse(UpdateChecker.isNewer("v0.2.0", "0.2.0"))
        assertFalse(UpdateChecker.isNewer("v0.1.9", "0.2.0"))
        assertEquals(listOf(0, 2, 0), UpdateChecker.parseVersion("v0.2.0"))
        assertEquals(listOf(1, 0), UpdateChecker.parseVersion("1.0"))
    }
}
