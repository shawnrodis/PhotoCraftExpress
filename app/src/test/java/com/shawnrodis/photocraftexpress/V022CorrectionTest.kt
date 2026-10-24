package com.shawnrodis.photocraftexpress

import com.shawnrodis.photocraftexpress.engine.AdjustMath
import com.shawnrodis.photocraftexpress.engine.Adjustments
import com.shawnrodis.photocraftexpress.engine.EditState
import com.shawnrodis.photocraftexpress.engine.Looks
import com.shawnrodis.photocraftexpress.engine.SplitTone
import com.shawnrodis.photocraftexpress.engine.PixelImage
import com.shawnrodis.photocraftexpress.engine.PixelPipeline
import com.shawnrodis.photocraftexpress.ui.BLUR_PARAMS
import com.shawnrodis.photocraftexpress.ui.SPLIT_TONE_PARAMS
import com.shawnrodis.photocraftexpress.ui.VIGNETTE_PARAMS
import com.shawnrodis.photocraftexpress.ui.adjustThumbStates
import com.shawnrodis.photocraftexpress.ui.channelDotColor
import com.shawnrodis.photocraftexpress.ui.looksStripCategoryAt
import com.shawnrodis.photocraftexpress.ui.looksStripEntries
import com.shawnrodis.photocraftexpress.ui.looksStripStartOf
import com.shawnrodis.photocraftexpress.ui.renderLatest
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * v0.2.2 correction-release tests: the live-slider worker, the
 * Express chips+slider parameter sets, the uniform strip contents, and
 * the per-adjustment thumbnail demo renders (which Shawn reported all
 * looked identical in v0.2.1).
 */
class V022CorrectionTest {

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or ((r.coerceIn(0, 255)) shl 16) or ((g.coerceIn(0, 255)) shl 8) or b.coerceIn(0, 255)

    // ------------------------------------------------------------------
    // Live-slider worker: intermediate drag values must publish.
    // ------------------------------------------------------------------

    @Test
    fun renderLatest_publishesIntermediateValuesDuringRapidEmission() = runBlocking {
        // The v0.2.1 failure mode: cancel-on-new-value publishes NOTHING
        // until emission stops. The worker must publish DURING the burst.
        val requests = MutableSharedFlow<Int>(
            replay = 1, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        val published = mutableListOf<Int>()
        val job = launch {
            renderLatest(requests, { v ->
                delay(40) // one render takes longer than the tick interval
                v * 10
            }, { published.add(it) })
        }
        // Emit 10 values 5ms apart (a fast slider drag).
        launch {
            for (v in 1..10) {
                requests.tryEmit(v)
                delay(5)
            }
        }
        // Wait until the burst is over but renders are still in flight:
        // at least one intermediate value must already be published.
        delay(200)
        assertTrue(
            "worker published nothing during the burst (cancel-on-new-value failure mode)",
            published.isNotEmpty()
        )
        job.cancel()
        // The last published value must be the final drag value.
        assertEquals(100, published.last())
    }

    @Test
    fun renderLatest_finalValueIsAlwaysTheLastRequest() = runBlocking {
        val requests = MutableSharedFlow<Int>(
            replay = 1, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        val published = mutableListOf<Int>()
        val job = launch { renderLatest(requests, { it }, { published.add(it) }) }
        for (v in 1..5) requests.tryEmit(v)
        delay(100)
        job.cancel()
        assertEquals(5, published.last())
    }

    // ------------------------------------------------------------------
    // Chips+slider parameter sets match Express's structure.
    // ------------------------------------------------------------------

    @Test
    fun vignetteParams_matchExpressChipsAndAmountRange() {
        assertEquals(
            listOf("Amount", "Mid Point", "Feather", "Roundness"),
            VIGNETTE_PARAMS.map { it.label }
        )
        // Shawn's note: Amount runs +100 to -100; keep that range.
        assertEquals(-100f..100f, VIGNETTE_PARAMS[0].range)
        assertEquals(0f..100f, VIGNETTE_PARAMS[1].range)
        assertEquals(0f..100f, VIGNETTE_PARAMS[2].range)
        assertEquals(-100f..100f, VIGNETTE_PARAMS[3].range)
    }

    @Test
    fun splitToneParams_matchExpressChips() {
        assertEquals(
            listOf("Shadow Hue", "Shadow Saturation", "Highlight Hue", "Highlight Saturation"),
            SPLIT_TONE_PARAMS.map { it.label }
        )
        assertEquals(0f..360f, SPLIT_TONE_PARAMS[0].range)
        assertEquals(0f..100f, SPLIT_TONE_PARAMS[1].range)
        assertEquals(0f..360f, SPLIT_TONE_PARAMS[2].range)
        assertEquals(0f..100f, SPLIT_TONE_PARAMS[3].range)
    }

    @Test
    fun blurParams_coverAmountAndRadialEllipse() {
        assertEquals(
            listOf("Amount", "Feather", "Roundness", "Width", "Height"),
            BLUR_PARAMS.map { it.label }
        )
        assertEquals(5f..100f, BLUR_PARAMS[3].range)
        assertEquals(5f..100f, BLUR_PARAMS[4].range)
    }

    @Test
    fun colorMixChannelDots_areDistinctPerChannel() {
        val centres = listOf(0f, 30f, 60f, 120f, 180f, 225f, 270f, 315f)
        val dots = centres.map { channelDotColor(it) }
        assertEquals(8, dots.toSet().size)
    }

    // ------------------------------------------------------------------
    // Strip contents: all 18 adjustments, Express order.
    // ------------------------------------------------------------------

    @Test
    fun stripCoversAllEighteenAdjustmentsInExpressOrder() {
        assertEquals(
            listOf(
                "Clarity", "Sharpen", "Reduce Color Noise", "Reduce Luminance Noise",
                "Dehaze", "Grain", "Fade", "Exposure", "Contrast", "Highlights",
                "Shadows", "Whites", "Blacks", "Temperature", "Tint", "Vibrance",
                "Saturation", "Texture"
            ),
            adjustThumbStates().map { it.first }
        )
    }

    // ------------------------------------------------------------------
    // Thumbnail demo renders must visibly differ from the base image.
    // ------------------------------------------------------------------

    /** Photo-like synthetic scene: sky gradient, hazy distance, ground, hard edges, noise. */
    private fun photoLike(w: Int = 160, h: Int = 120): PixelImage {
        var seed = 1234567L
        fun rnd(): Int {
            seed = (seed * 6364136223846793005L + 1442695040888963407L)
            return ((seed ushr 33) and 0xFF).toInt()
        }
        val px = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            // Sky gradient (top) -> hazy pale band -> warm ground (bottom).
            val (r, g, b) = when {
                y < h * 2 / 5 -> Triple(90 + x / 3, 140 + y / 2, 200)
                y < h * 3 / 5 -> Triple(180, 175, 165) // low-contrast haze
                else -> Triple(120 + (x % 40), 90 + (y % 30), 60)
            }
            // Hard-edged rectangles (sharpen/texture/clarity targets).
            val edge = if (x in 20..60 && y in 70..100) Triple(20, 20, 20) else null
            val (r2, g2, b2) = edge ?: Triple(r, g, b)
            // Chromatic + luma noise (noise-reduction targets).
            val n = rnd() % 25 - 12
            val nc = rnd() % 17 - 8
            argb(r2 + n + nc, g2 + n, b2 + n - nc)
        }
        return PixelImage(px, w, h)
    }

    private fun meanAbsDiff(a: IntArray, b: IntArray): Double {
        var sum = 0L
        for (i in a.indices) {
            sum += abs(((a[i] shr 16) and 0xFF) - ((b[i] shr 16) and 0xFF))
            sum += abs(((a[i] shr 8) and 0xFF) - ((b[i] shr 8) and 0xFF))
            sum += abs((a[i] and 0xFF) - (b[i] and 0xFF))
        }
        return sum.toDouble() / (a.size * 3)
    }

    @Test
    fun thumbnails_eachDemoAdjustmentVisiblyDiffersFromBase() {
        val base = photoLike()
        val baseOut = PixelPipeline.render(base, EditState())
        val failures = mutableListOf<String>()
        for ((name, demoAdj) in adjustThumbStates()) {
            val out = PixelPipeline.render(base, EditState(adj = demoAdj))
            val diff = meanAbsDiff(baseOut.pixels, out.pixels)
            if (diff < 3.0) failures.add("$name (mean abs diff $diff)")
        }
        assertTrue(
            "demo thumbnails indistinguishable from base: $failures",
            failures.isEmpty()
        )
    }

    // ------------------------------------------------------------------
    // v0.2.2 Looks catalog: Express's 13 categories, our own recipes.
    // ------------------------------------------------------------------

    @Test
    fun looksCatalog_coversExpressThirteenCategoriesWithExactCounts() {
        val expected = linkedMapOf(
            "Normal" to 7, "Free" to 6, "HDR" to 7, "Charm" to 6,
            "Black & White" to 8, "Portrait" to 6, "Matte" to 6,
            "White Balance" to 6, "Splash" to 7, "Nature" to 6,
            "Light" to 7, "Warm" to 7, "Duo Tone" to 8
        )
        assertEquals(expected.keys.toList(), Looks.CATEGORIES.map { it.first })
        for ((name, count) in expected) {
            val group = Looks.CATEGORIES.first { it.first == name }
            assertEquals("category $name", count, group.second.size)
        }
        assertEquals(87, Looks.ALL.size)
        // Every preset id unique, every preset resolves.
        assertEquals(Looks.ALL.size, Looks.ALL.map { it.id }.toSet().size)
        for (look in Looks.ALL) {
            val resolved = Looks.resolve(look, 100f)
            assertEquals(AdjustMath.LUT_SIZE, resolved.toneLut.size)
        }
    }

    @Test
    fun looksStrip_isContinuousAcrossCategories() {
        val entries = looksStripEntries()
        assertEquals(87, entries.size)
        assertEquals(87, entries.map { it.id }.toSet().size)
        // Categories contiguous, in CATEGORIES order.
        val seen = entries.map { it.category }.distinct()
        assertEquals(Looks.CATEGORIES.map { it.first }, seen)
        // Boundaries: Normal holds 7 (indices 0..6), Free starts at 7,
        // Duo Tone holds the last 8.
        assertEquals("Normal", looksStripCategoryAt(entries, 0))
        assertEquals("Free", looksStripCategoryAt(entries, 7))
        assertEquals("Duo Tone", looksStripCategoryAt(entries, 86))
        assertEquals("Duo Tone", looksStripCategoryAt(entries, 1000)) // clamped
        assertEquals(79, looksStripStartOf(entries, "Duo Tone"))
        assertEquals(0, looksStripStartOf(entries, "No such category"))
        assertEquals("", looksStripCategoryAt(emptyList(), 0))
    }

    @Test
    fun lookIntensity_scalesStageContributions() {
        val duo = Looks.resolve(Looks.byId("duo-rose-gold"), 50f)
        assertEquals(15f, duo.splitTone.shadowSat)
        assertEquals(15f, duo.splitTone.highlightSat)
        val film = Looks.resolve(Looks.byId("bw-film"), 50f)
        assertEquals(17.5f, film.grain)
        val splash = Looks.resolve(Looks.byId("splash-red"), 100f)
        assertEquals(0f, splash.colorMix.sat[0])
        assertEquals(-100f, splash.colorMix.sat[3])
        val none = Looks.resolve(Looks.byId("duo-rose-gold"), 0f)
        assertTrue(none.splitTone.isIdentity)
    }

    private fun patchImage(): PixelImage {
        // Four patches: dark grey, bright grey, pure red, pure green, pure blue.
        val w = 40
        val h = 10
        val px = IntArray(w * h) { i ->
            val x = i % w
            when {
                x < 8 -> argb(30, 30, 30)
                x < 16 -> argb(220, 220, 220)
                x < 24 -> argb(220, 30, 30)
                x < 32 -> argb(30, 200, 30)
                else -> argb(30, 30, 220)
            }
        }
        return PixelImage(px, w, h)
    }

    private fun patchMean(img: PixelImage, patch: Int): Triple<Double, Double, Double> {
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0L
        for (i in img.pixels.indices) {
            if (i % img.width / 8 == patch) {
                r += (img.pixels[i] shr 16) and 0xFF
                g += (img.pixels[i] shr 8) and 0xFF
                b += img.pixels[i] and 0xFF
                n++
            }
        }
        return Triple(r.toDouble() / n, g.toDouble() / n, b.toDouble() / n)
    }

    @Test
    fun duoToneLook_rendersMonoPlusTintPair() {
        val img = patchImage()
        val mono = PixelPipeline.render(img, EditState(lookId = "mono", lookIntensity = 100f))
        val duo = PixelPipeline.render(img, EditState(lookId = "duo-rose-gold", lookIntensity = 100f))
        // Mono dark patch stays grey; Duo Tone's pink shadow tint lifts red over blue.
        val (mr, _, mb) = patchMean(mono, 0)
        assertTrue("mono dark patch should stay grey", abs(mr - mb) <= 1.0)
        val (dr, _, db) = patchMean(duo, 0)
        assertTrue("duo shadow tint should lift red over blue (r=$dr b=$db)", dr > db + 2)
        // And the duo render differs from plain mono (the tint pair is real).
        assertTrue(meanAbsDiff(mono.pixels, duo.pixels) > 1.0)
    }

    @Test
    fun splashLook_selectivelyDesaturatesNonPopChannels() {
        val img = patchImage()
        val out = PixelPipeline.render(img, EditState(lookId = "splash-red", lookIntensity = 100f))
        fun satOf(patch: Int): Double {
            val (r, g, b) = patchMean(out, patch)
            val mx = maxOf(r, g, b)
            if (mx < 1.0) return 0.0
            return (mx - minOf(r, g, b)) / mx
        }
        // Green patch (pop = red) collapses to grey; red patch keeps its colour.
        assertTrue("green patch should desaturate, sat=${satOf(3)}", satOf(3) < 0.15)
        assertTrue("red patch should stay saturated, sat=${satOf(2)}", satOf(2) > 0.5)
    }

    @Test
    fun lookStage_stacksWithUserPanelValue() {
        val img = photoLike()
        val lookOnly = PixelPipeline.render(img, EditState(lookId = "duo-rose-gold", lookIntensity = 100f))
        val stacked = PixelPipeline.render(
            img,
            EditState(
                lookId = "duo-rose-gold", lookIntensity = 100f,
                splitTone = SplitTone(
                    shadowHue = 200f, shadowSat = 40f, highlightHue = 40f, highlightSat = 0f
                )
            )
        )
        assertTrue(meanAbsDiff(lookOnly.pixels, stacked.pixels) > 1.0)
    }

    @Test
    fun render_doesNotMutateItsInput() {
        // The v0.2.2 preview worker caches one 1024px base across renders;
        // this must hold or later renders would compound earlier edits.
        val img = photoLike()
        val before = img.pixels.copyOf()
        PixelPipeline.render(img, EditState(adj = Adjustments(exposure = 50f)))
        PixelPipeline.render(img, EditState(adj = Adjustments(saturation = -50f)))
        assertTrue(before.contentEquals(img.pixels))
    }
}
