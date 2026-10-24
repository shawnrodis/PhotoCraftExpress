// Portions adapted from PhotoCraft (https://github.com/storytold/photocraft), MIT OR Apache-2.0.
// The Look recipes below are original, but they are rendered through the
// PhotoCraft-derived Curves spline and Levels transforms in AdjustMath.kt.
// See the repo-root NOTICE.

package com.shawnrodis.photocraftexpress.engine

/**
 * A named one-tap preset ("Look"). Recipes are expressed in the same units
 * as the Adjust sliders so a Look and the user's own adjustments compose:
 * the Look's tone LUT renders first, then the user's Adjust tone LUT, and
 * the per-pixel parameters (temperature/tint/vibrance/saturation) add.
 *
 * [fade] lifts the output black point (and slightly lowers the white point)
 * through the Levels output range; [gamma] is a Levels midtone gamma;
 * [anchors] are extra master-curve points on top of [adj]'s tone chain.
 */
data class LookDef(
    val id: String,
    val name: String,
    val adj: Adjustments = Adjustments(),
    val anchors: List<CurvePoint> = emptyList(),
    val gamma: Float = 1f,
    val fade: Float = 0f,
    val mono: Boolean = false
)

/** A Look resolved against an intensity (0..100) for the render pipeline. */
data class ResolvedLook(
    val toneLut: FloatArray,
    val temperature: Float,
    val tint: Float,
    val vibrance: Float,
    val saturation: Float,
    val monoAmount: Float,
    val sharpen: Float
) {
    companion object {
        val NONE = ResolvedLook(
            toneLut = FloatArray(AdjustMath.LUT_SIZE) { it / (AdjustMath.LUT_SIZE - 1).toFloat() },
            temperature = 0f, tint = 0f, vibrance = 0f, saturation = 0f,
            monoAmount = 0f, sharpen = 0f
        )
    }
}

object Looks {

    const val ORIGINAL_ID = "original"

    val ORIGINAL = LookDef(ORIGINAL_ID, "Original")

    val ALL: List<LookDef> = listOf(
        ORIGINAL,
        LookDef(
            "vivid", "Vivid",
            adj = Adjustments(contrast = 12f, blacks = -10f, vibrance = 25f, saturation = 35f)
        ),
        LookDef(
            "punch", "Punch",
            adj = Adjustments(contrast = 30f, vibrance = 15f, sharpen = 20f),
            anchors = listOf(
                CurvePoint(0f, 0f), CurvePoint(0.25f, 0.19f), CurvePoint(0.5f, 0.5f),
                CurvePoint(0.75f, 0.81f), CurvePoint(1f, 1f)
            )
        ),
        LookDef(
            "warm", "Warm",
            adj = Adjustments(highlights = -8f, temperature = 28f, tint = 4f, saturation = 8f)
        ),
        LookDef(
            "cool", "Cool",
            adj = Adjustments(shadows = 6f, temperature = -30f, tint = -4f, saturation = 5f)
        ),
        LookDef(
            "mono", "Mono",
            adj = Adjustments(contrast = 18f, whites = 8f, blacks = -12f),
            mono = true
        ),
        LookDef(
            "fade", "Fade",
            adj = Adjustments(contrast = -22f, saturation = -25f),
            gamma = 1.05f, fade = 0.14f
        ),
        LookDef(
            "drama", "Drama",
            adj = Adjustments(
                contrast = 38f, highlights = -25f, shadows = -18f,
                vibrance = 10f, sharpen = 25f
            )
        ),
        LookDef(
            "autumn", "Autumn",
            adj = Adjustments(shadows = 8f, temperature = 18f, tint = 8f, vibrance = 20f, saturation = 18f)
        ),
        LookDef(
            "golden", "Golden",
            adj = Adjustments(highlights = 10f, temperature = 34f, saturation = 12f),
            fade = 0.05f
        ),
        LookDef(
            "noir", "Noir",
            adj = Adjustments(contrast = 45f, whites = 15f, blacks = -30f, sharpen = 15f),
            anchors = listOf(
                CurvePoint(0f, 0f), CurvePoint(0.25f, 0.16f), CurvePoint(0.5f, 0.5f),
                CurvePoint(0.75f, 0.84f), CurvePoint(1f, 1f)
            ),
            mono = true
        ),
        LookDef(
            "pastel", "Pastel",
            adj = Adjustments(exposure = 6f, contrast = -12f, temperature = 6f, saturation = -18f),
            fade = 0.10f
        ),
        LookDef(
            "chrome", "Chrome",
            adj = Adjustments(contrast = 22f, blacks = -15f, saturation = 22f, sharpen = 35f)
        ),
        LookDef(
            "breeze", "Breeze",
            adj = Adjustments(exposure = 8f, highlights = 12f, shadows = 12f, temperature = -12f, saturation = 10f)
        ),
        LookDef(
            "matte", "Matte",
            adj = Adjustments(contrast = -28f, temperature = 8f, saturation = -12f),
            fade = 0.17f
        )
    )

    /**
     * Looks categories, mirroring Express's grouped strip. Every preset
     * belongs to exactly one category; Original lives in Normal.
     */
    val CATEGORIES: List<Pair<String, List<LookDef>>> by lazy {
        val byIdMap = ALL.associateBy { it.id }
        fun group(name: String, vararg ids: String) =
            name to ids.mapNotNull { byIdMap[it] }
        listOf(
            group("Normal", "original", "vivid", "punch", "warm", "cool"),
            group("Black & White", "mono", "noir"),
            group("HDR", "drama", "chrome"),
            group("Warm", "golden", "autumn"),
            group("Light", "breeze", "pastel"),
            group("Faded", "fade", "matte")
        ).filter { it.second.isNotEmpty() }
    }

    private val byId: Map<String, LookDef> = ALL.associateBy { it.id }

    fun byId(id: String): LookDef = byId[id] ?: ORIGINAL

    /** The un-blended tone LUT of a Look (its adj chain, anchors, levels). */
    fun buildLookLut(look: LookDef): FloatArray {
        var lut = AdjustMath.buildToneLut(look.adj)
        if (look.anchors.isNotEmpty()) {
            lut = AdjustMath.composeLuts(lut, AdjustMath.curveLut(look.anchors))
        }
        if (look.gamma != 1f || look.fade > 0f) {
            val outBlack = look.fade
            val outWhite = 1f - look.fade * 0.3f
            lut = FloatArray(AdjustMath.LUT_SIZE) { i ->
                AdjustMath.levelsTransform(lut[i], 0f, 1f, look.gamma, outBlack, outWhite)
            }
        }
        return lut
    }

    /** Resolves a Look at [intensity] (0..100) into render-ready pieces. */
    fun resolve(look: LookDef, intensity: Float): ResolvedLook {
        if (look.id == ORIGINAL_ID) return ResolvedLook.NONE
        val k = (intensity / 100f).coerceIn(0f, 1f)
        if (k <= 0f) return ResolvedLook.NONE
        return ResolvedLook(
            toneLut = AdjustMath.blendLut(buildLookLut(look), k),
            temperature = look.adj.temperature * k,
            tint = look.adj.tint * k,
            vibrance = look.adj.vibrance * k,
            saturation = look.adj.saturation * k,
            monoAmount = if (look.mono) k else 0f,
            sharpen = look.adj.sharpen * k
        )
    }
}
