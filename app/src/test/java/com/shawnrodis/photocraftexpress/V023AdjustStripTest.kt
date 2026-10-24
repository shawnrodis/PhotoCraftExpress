package com.shawnrodis.photocraftexpress

import com.shawnrodis.photocraftexpress.engine.BlurMode
import com.shawnrodis.photocraftexpress.ui.AdjustStripEntry
import com.shawnrodis.photocraftexpress.ui.AdjustTab
import com.shawnrodis.photocraftexpress.ui.adjustStripEntries
import com.shawnrodis.photocraftexpress.ui.adjustStripSectionAt
import com.shawnrodis.photocraftexpress.ui.adjustStripStartOf
import com.shawnrodis.photocraftexpress.ui.adjustTabLabel
import com.shawnrodis.photocraftexpress.ui.resolveAdjustSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.2.3 tests: the continuous Adjustments strip — section order,
 * section boundaries, tap-to-jump targets, and selection fallback
 * when the blur mode shrinks the strip. Pure data, no composition.
 */
class V023AdjustStripTest {

    // 18 adjustments + 4 split tone + 8 color mix + 3 blur modes +
    // blur params (1, or 5 when Radial) + 4 vignette + 1 optics.
    @Test fun stripCounts() {
        assertEquals(39, adjustStripEntries(BlurMode.NONE).size)
        assertEquals(39, adjustStripEntries(BlurMode.FULL).size)
        assertEquals(43, adjustStripEntries(BlurMode.RADIAL).size)
    }

    @Test fun stripOrderIsTabOrder() {
        val entries = adjustStripEntries(BlurMode.NONE)
        val sections = entries.map { it.section }.distinct()
        assertEquals(
            listOf(
                AdjustTab.ADJUSTMENTS, AdjustTab.SPLIT_TONE, AdjustTab.COLOR_MIX,
                AdjustTab.BLUR, AdjustTab.VIGNETTE, AdjustTab.OPTICS
            ),
            sections
        )
        // Adjustment thumbnails keep the v0.2.2 strip order.
        val adjNames = entries.filterIsInstance<AdjustStripEntry.Adjustment>()
            .map { it.specName }
        assertEquals(
            listOf(
                "Clarity", "Sharpen", "Reduce Color Noise", "Reduce Luminance Noise",
                "Dehaze", "Grain", "Fade", "Exposure", "Contrast", "Highlights",
                "Shadows", "Whites", "Blacks", "Temperature", "Tint", "Vibrance",
                "Saturation", "Texture"
            ),
            adjNames
        )
        assertEquals(
            listOf("Shadow Hue", "Shadow Saturation", "Highlight Hue", "Highlight Saturation"),
            entries.filterIsInstance<AdjustStripEntry.SplitToneParam>().map { it.label }
        )
        assertEquals(
            listOf("Red", "Orange", "Yellow", "Green", "Aqua", "Blue", "Purple", "Magenta"),
            entries.filterIsInstance<AdjustStripEntry.ColorMixChannel>().map {
                listOf(
                    "Red", "Orange", "Yellow", "Green",
                    "Aqua", "Blue", "Purple", "Magenta"
                )[it.channel]
            }
        )
        assertEquals(
            listOf("Amount", "Mid Point", "Feather", "Roundness"),
            entries.filterIsInstance<AdjustStripEntry.VignetteParam>().map { it.label }
        )
    }

    @Test fun stripKeysAreUnique() {
        for (mode in listOf(BlurMode.NONE, BlurMode.RADIAL)) {
            val keys = adjustStripEntries(mode).map { it.key }
            assertEquals(keys.size, keys.distinct().size)
        }
    }

    @Test fun sectionStarts() {
        val none = adjustStripEntries(BlurMode.NONE)
        assertEquals(0, adjustStripStartOf(none, AdjustTab.ADJUSTMENTS))
        assertEquals(18, adjustStripStartOf(none, AdjustTab.SPLIT_TONE))
        assertEquals(22, adjustStripStartOf(none, AdjustTab.COLOR_MIX))
        assertEquals(30, adjustStripStartOf(none, AdjustTab.BLUR))
        assertEquals(34, adjustStripStartOf(none, AdjustTab.VIGNETTE))
        assertEquals(38, adjustStripStartOf(none, AdjustTab.OPTICS))
        val radial = adjustStripEntries(BlurMode.RADIAL)
        assertEquals(38, adjustStripStartOf(radial, AdjustTab.VIGNETTE))
        assertEquals(42, adjustStripStartOf(radial, AdjustTab.OPTICS))
    }

    @Test fun sectionAtBoundaries() {
        val entries = adjustStripEntries(BlurMode.NONE)
        assertEquals(AdjustTab.ADJUSTMENTS, adjustStripSectionAt(entries, 0))
        assertEquals(AdjustTab.ADJUSTMENTS, adjustStripSectionAt(entries, 17))
        assertEquals(AdjustTab.SPLIT_TONE, adjustStripSectionAt(entries, 18))
        assertEquals(AdjustTab.SPLIT_TONE, adjustStripSectionAt(entries, 21))
        assertEquals(AdjustTab.COLOR_MIX, adjustStripSectionAt(entries, 22))
        assertEquals(AdjustTab.COLOR_MIX, adjustStripSectionAt(entries, 29))
        assertEquals(AdjustTab.BLUR, adjustStripSectionAt(entries, 30))
        assertEquals(AdjustTab.BLUR, adjustStripSectionAt(entries, 33))
        assertEquals(AdjustTab.VIGNETTE, adjustStripSectionAt(entries, 34))
        assertEquals(AdjustTab.VIGNETTE, adjustStripSectionAt(entries, 37))
        assertEquals(AdjustTab.OPTICS, adjustStripSectionAt(entries, 38))
        // Out-of-range indices clamp, never crash.
        assertEquals(AdjustTab.ADJUSTMENTS, adjustStripSectionAt(entries, -5))
        assertEquals(AdjustTab.OPTICS, adjustStripSectionAt(entries, 999))
        assertEquals(AdjustTab.ADJUSTMENTS, adjustStripSectionAt(emptyList(), 0))
        assertEquals(0, adjustStripStartOf(emptyList(), AdjustTab.BLUR))
    }

    @Test fun jumpTargetRoundTripsToItsSection() {
        for (mode in listOf(BlurMode.NONE, BlurMode.RADIAL)) {
            val entries = adjustStripEntries(mode)
            for (section in AdjustTab.values()) {
                val start = adjustStripStartOf(entries, section)
                assertEquals(section, adjustStripSectionAt(entries, start))
            }
        }
    }

    @Test fun selectionFallbackWhenBlurParamsLeave() {
        val none = adjustStripEntries(BlurMode.NONE)
        // Width only exists while Radial; leaving Radial falls back to Amount.
        assertEquals(
            AdjustStripEntry.BlurParam("Amount"),
            resolveAdjustSelection(none, AdjustStripEntry.BlurParam("Width"))
        )
        // Every other selection survives a strip change.
        assertEquals(
            AdjustStripEntry.Adjustment("Contrast"),
            resolveAdjustSelection(none, AdjustStripEntry.Adjustment("Contrast"))
        )
        assertEquals(
            AdjustStripEntry.VignetteParam("Feather"),
            resolveAdjustSelection(none, AdjustStripEntry.VignetteParam("Feather"))
        )
        assertEquals(
            AdjustStripEntry.Optics,
            resolveAdjustSelection(none, AdjustStripEntry.Optics)
        )
    }

    @Test fun tabLabels() {
        assertEquals("ADJUSTMENTS", adjustTabLabel(AdjustTab.ADJUSTMENTS))
        assertEquals("SPLIT TONE", adjustTabLabel(AdjustTab.SPLIT_TONE))
        assertEquals("COLOR MIX", adjustTabLabel(AdjustTab.COLOR_MIX))
        assertEquals("BLUR", adjustTabLabel(AdjustTab.BLUR))
        assertEquals("VIGNETTE", adjustTabLabel(AdjustTab.VIGNETTE))
        assertEquals("OPTICS", adjustTabLabel(AdjustTab.OPTICS))
        assertTrue(true)
    }
}
