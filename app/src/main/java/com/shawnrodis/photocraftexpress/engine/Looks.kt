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
 *
 * v0.2.2: Looks can also carry the deeper stages — [splitTone], [grain],
 * [vignette] and [colorMix] — plus the [adj] chain's own clarity, texture
 * and dehaze (surfaced by resolve()). In the pipeline these ADD to the
 * user's panel values (same philosophy as temperature/tint), so a Look
 * grades the photo and the user's sliders still stack on top. The
 * mask-blend base (Subject/Background) carries the Look's stages too,
 * so the mask gates only the user's panel edits, never the Look itself.
 * All recipes are original parameter combinations — no Adobe preset
 * data, LUTs or artwork is copied.
 */
data class LookDef(
    val id: String,
    val name: String,
    val adj: Adjustments = Adjustments(),
    val anchors: List<CurvePoint> = emptyList(),
    val gamma: Float = 1f,
    val fade: Float = 0f,
    val mono: Boolean = false,
    val splitTone: SplitTone = SplitTone(),
    val grain: Float = 0f,
    val vignette: Vignette = Vignette(),
    val colorMix: ColorMix = ColorMix()
)

/** A Look resolved against an intensity (0..100) for the render pipeline. */
data class ResolvedLook(
    val toneLut: FloatArray,
    val temperature: Float,
    val tint: Float,
    val vibrance: Float,
    val saturation: Float,
    val monoAmount: Float,
    val sharpen: Float,
    /** Deeper stages, already scaled by intensity. Zero/identity when the Look sets none. */
    val clarity: Float = 0f,
    val texture: Float = 0f,
    val dehaze: Float = 0f,
    val splitTone: SplitTone = SplitTone(),
    val grain: Float = 0f,
    val vignette: Vignette = Vignette(),
    val colorMix: ColorMix = ColorMix()
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
        // ---- Normal (the originals, unchanged) ----
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
            "natural", "Natural",
            adj = Adjustments(contrast = 5f, shadows = 5f, vibrance = 8f, saturation = 5f)
        ),
        LookDef(
            "soft", "Soft",
            adj = Adjustments(contrast = -10f, highlights = 5f, shadows = 8f, saturation = -5f),
            fade = 0.05f
        ),
        // ---- Free (seasonal / nature moods) ----
        LookDef(
            "spring", "Spring",
            adj = Adjustments(highlights = 8f, temperature = 8f, tint = 10f, vibrance = 25f, saturation = 10f)
        ),
        LookDef(
            "summer", "Summer",
            adj = Adjustments(exposure = 8f, contrast = 8f, temperature = 15f, vibrance = 20f, saturation = 15f)
        ),
        LookDef(
            "fall", "Fall",
            adj = Adjustments(shadows = 8f, temperature = 22f, tint = 10f, vibrance = 25f, saturation = 20f)
        ),
        LookDef(
            "winter", "Winter",
            adj = Adjustments(contrast = 10f, highlights = 10f, temperature = -20f, tint = -5f, saturation = -5f, sharpen = 10f)
        ),
        LookDef(
            "bloom", "Bloom",
            adj = Adjustments(contrast = -8f, highlights = 10f, temperature = 10f, tint = 12f, saturation = 15f),
            fade = 0.08f
        ),
        LookDef(
            "sunset-glow", "Sunset Glow",
            adj = Adjustments(highlights = -10f, temperature = 30f, vibrance = 15f, saturation = 15f),
            vignette = Vignette(amount = 20f)
        ),
        // ---- HDR ----
        LookDef(
            "drama", "Drama",
            adj = Adjustments(
                contrast = 38f, highlights = -25f, shadows = -18f,
                vibrance = 10f, sharpen = 25f
            )
        ),
        LookDef(
            "chrome", "Chrome",
            adj = Adjustments(contrast = 22f, blacks = -15f, saturation = 22f, sharpen = 35f)
        ),
        LookDef(
            "hdr-light", "HDR Light",
            adj = Adjustments(contrast = 20f, highlights = -30f, shadows = 25f, clarity = 20f, dehaze = 15f, vibrance = 10f)
        ),
        LookDef(
            "hdr-strong", "HDR Strong",
            adj = Adjustments(contrast = 30f, highlights = -40f, shadows = 35f, clarity = 35f, dehaze = 25f, vibrance = 15f, sharpen = 20f),
            anchors = listOf(
                CurvePoint(0f, 0f), CurvePoint(0.25f, 0.18f), CurvePoint(0.5f, 0.5f),
                CurvePoint(0.75f, 0.82f), CurvePoint(1f, 1f)
            )
        ),
        LookDef(
            "hdr-vivid", "HDR Vivid",
            adj = Adjustments(contrast = 25f, highlights = -30f, shadows = 30f, clarity = 25f, dehaze = 20f, vibrance = 20f, saturation = 25f)
        ),
        LookDef(
            "hdr-mono", "HDR Mono",
            adj = Adjustments(contrast = 35f, highlights = -30f, shadows = 30f, clarity = 30f, dehaze = 20f, sharpen = 20f),
            mono = true
        ),
        LookDef(
            "hdr-sunset", "HDR Sunset",
            adj = Adjustments(contrast = 25f, highlights = -35f, shadows = 25f, clarity = 20f, temperature = 20f, vibrance = 20f)
        ),
        // ---- Charm (pastel tints) ----
        LookDef(
            "charm-pink", "Charm Pink",
            adj = Adjustments(contrast = -10f, highlights = 8f, temperature = 12f, tint = 15f, vibrance = 10f, saturation = -5f),
            fade = 0.10f
        ),
        LookDef(
            "charm-peach", "Charm Peach",
            adj = Adjustments(contrast = -8f, temperature = 18f, tint = 8f, saturation = 5f),
            fade = 0.08f
        ),
        LookDef(
            "charm-mint", "Charm Mint",
            adj = Adjustments(contrast = -10f, temperature = -12f, tint = 12f, saturation = -5f),
            fade = 0.10f
        ),
        LookDef(
            "charm-lavender", "Charm Lavender",
            adj = Adjustments(contrast = -10f, shadows = 8f, temperature = -5f, tint = 15f, saturation = -5f),
            fade = 0.10f
        ),
        LookDef(
            "charm-sky", "Charm Sky",
            adj = Adjustments(contrast = -8f, highlights = 10f, temperature = -18f, tint = 2f, saturation = 5f),
            fade = 0.08f
        ),
        LookDef(
            "charm-cream", "Charm Cream",
            adj = Adjustments(contrast = -15f, highlights = 8f, temperature = 15f, saturation = -15f),
            fade = 0.12f
        ),
        // ---- Black & White ----
        LookDef(
            "mono", "Mono",
            adj = Adjustments(contrast = 18f, whites = 8f, blacks = -12f),
            mono = true
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
            "bw-contrast", "B&W Contrast",
            adj = Adjustments(contrast = 30f, whites = 10f, blacks = -20f, sharpen = 10f),
            mono = true
        ),
        LookDef(
            "bw-soft", "B&W Soft",
            adj = Adjustments(contrast = -5f, highlights = 8f, shadows = 10f),
            fade = 0.12f, mono = true
        ),
        LookDef(
            "bw-film", "B&W Film",
            adj = Adjustments(contrast = 15f, sharpen = 10f),
            fade = 0.05f, grain = 35f, mono = true
        ),
        LookDef(
            "sepia", "Sepia",
            adj = Adjustments(contrast = 10f),
            fade = 0.05f, mono = true,
            splitTone = SplitTone(shadowHue = 35f, shadowSat = 35f, highlightHue = 45f, highlightSat = 20f)
        ),
        LookDef(
            "bw-infrared", "B&W Infrared",
            adj = Adjustments(contrast = 40f, highlights = -15f, blacks = -25f),
            anchors = listOf(
                CurvePoint(0f, 0f), CurvePoint(0.25f, 0.14f), CurvePoint(0.5f, 0.5f),
                CurvePoint(0.75f, 0.86f), CurvePoint(1f, 1f)
            ),
            mono = true
        ),
        LookDef(
            "bw-vintage", "B&W Vintage",
            adj = Adjustments(contrast = -10f),
            fade = 0.15f, mono = true,
            splitTone = SplitTone(shadowHue = 40f, shadowSat = 25f, highlightHue = 45f, highlightSat = 15f),
            vignette = Vignette(amount = 25f)
        ),
        // ---- Portrait ----
        LookDef(
            "glamour", "Glamour",
            adj = Adjustments(contrast = -12f, highlights = 8f, shadows = 10f, temperature = 10f, saturation = -8f),
            fade = 0.10f
        ),
        LookDef(
            "vintage-portrait", "Vintage Portrait",
            adj = Adjustments(contrast = -15f, temperature = 15f, saturation = -20f),
            fade = 0.14f,
            splitTone = SplitTone(shadowHue = 40f, shadowSat = 15f, highlightHue = 45f, highlightSat = 10f)
        ),
        LookDef(
            "rosy", "Rosy",
            adj = Adjustments(contrast = -5f, highlights = 5f, temperature = 12f, tint = 18f, vibrance = 15f, saturation = 10f)
        ),
        LookDef(
            "golden-hour", "Golden Hour",
            adj = Adjustments(highlights = -8f, temperature = 28f, vibrance = 15f, saturation = 12f),
            fade = 0.04f, vignette = Vignette(amount = 15f)
        ),
        LookDef(
            "studio", "Studio",
            adj = Adjustments(contrast = 12f, highlights = 5f, whites = 8f, blacks = -8f, saturation = 5f, sharpen = 15f)
        ),
        LookDef(
            "matte-portrait", "Matte Portrait",
            adj = Adjustments(contrast = -18f, temperature = 8f, saturation = -12f),
            fade = 0.12f
        ),
        // ---- Matte (Fade and Matte moved here from the old Faded group) ----
        LookDef(
            "fade", "Fade",
            adj = Adjustments(contrast = -22f, saturation = -25f),
            gamma = 1.05f, fade = 0.14f
        ),
        LookDef(
            "matte", "Matte",
            adj = Adjustments(contrast = -28f, temperature = 8f, saturation = -12f),
            fade = 0.17f
        ),
        LookDef(
            "matte-warm", "Matte Warm",
            adj = Adjustments(contrast = -20f, temperature = 15f, saturation = -15f),
            fade = 0.14f
        ),
        LookDef(
            "matte-cool", "Matte Cool",
            adj = Adjustments(contrast = -20f, temperature = -15f, saturation = -15f),
            fade = 0.14f
        ),
        LookDef(
            "matte-film", "Matte Film",
            adj = Adjustments(contrast = -18f, saturation = -18f),
            fade = 0.15f, grain = 30f
        ),
        LookDef(
            "matte-pastel", "Matte Pastel",
            adj = Adjustments(contrast = -22f, highlights = 8f, temperature = 5f, saturation = -25f),
            fade = 0.16f
        ),
        // ---- White Balance (temperature / tint shifts) ----
        LookDef(
            "wb-cloudy", "Cloudy",
            adj = Adjustments(temperature = 12f, tint = 2f)
        ),
        LookDef(
            "wb-shade", "Shade",
            adj = Adjustments(temperature = 22f, tint = 4f)
        ),
        LookDef(
            "wb-tungsten", "Tungsten",
            adj = Adjustments(temperature = -35f, tint = -2f)
        ),
        LookDef(
            "wb-daylight", "Daylight",
            adj = Adjustments(temperature = 3f, saturation = 2f)
        ),
        LookDef(
            "wb-fluorescent", "Fluorescent",
            adj = Adjustments(temperature = -8f, tint = -12f)
        ),
        LookDef(
            "wb-flash", "Flash",
            adj = Adjustments(highlights = -5f, temperature = -5f)
        ),
        // ---- Splash (selective colour via ColorMix: every channel
        // desaturated except the pop channel — a real selective-colour
        // grade through the existing engine, not a tint overlay) ----
        LookDef(
            "splash-red", "Splash Red",
            colorMix = splashMix(0)
        ),
        LookDef(
            "splash-orange", "Splash Orange",
            colorMix = splashMix(1)
        ),
        LookDef(
            "splash-yellow", "Splash Yellow",
            colorMix = splashMix(2)
        ),
        LookDef(
            "splash-green", "Splash Green",
            colorMix = splashMix(3)
        ),
        LookDef(
            "splash-blue", "Splash Blue",
            colorMix = splashMix(5)
        ),
        LookDef(
            "splash-purple", "Splash Purple",
            colorMix = splashMix(6)
        ),
        LookDef(
            "splash-pink", "Splash Pink",
            colorMix = splashMix(7)
        ),
        // ---- Nature ----
        LookDef(
            "cinematic", "Cinematic",
            adj = Adjustments(contrast = 20f, highlights = -10f, saturation = 10f),
            splitTone = SplitTone(shadowHue = 195f, shadowSat = 25f, highlightHue = 35f, highlightSat = 20f),
            vignette = Vignette(amount = 20f)
        ),
        LookDef(
            "twilight", "Twilight",
            adj = Adjustments(contrast = 15f, highlights = -15f, temperature = -18f, tint = 8f),
            splitTone = SplitTone(shadowHue = 260f, shadowSat = 30f, highlightHue = 45f, highlightSat = 15f),
            vignette = Vignette(amount = 25f)
        ),
        LookDef(
            "forest", "Forest",
            adj = Adjustments(contrast = 10f, shadows = 10f, clarity = 15f, temperature = -5f, tint = 10f, vibrance = 30f, saturation = 15f)
        ),
        LookDef(
            "ocean", "Ocean",
            adj = Adjustments(contrast = 8f, highlights = 8f, temperature = -22f, tint = 5f, vibrance = 25f, saturation = 15f)
        ),
        LookDef(
            "desert", "Desert",
            adj = Adjustments(contrast = 5f, highlights = -5f, temperature = 25f, saturation = 10f),
            fade = 0.06f
        ),
        LookDef(
            "mountain", "Mountain",
            adj = Adjustments(contrast = 15f, highlights = -10f, clarity = 20f, dehaze = 20f, temperature = -12f, vibrance = 10f)
        ),
        // ---- Light ----
        LookDef(
            "breeze", "Breeze",
            adj = Adjustments(exposure = 8f, highlights = 12f, shadows = 12f, temperature = -12f, saturation = 10f)
        ),
        LookDef(
            "pastel", "Pastel",
            adj = Adjustments(exposure = 6f, contrast = -12f, temperature = 6f, saturation = -18f),
            fade = 0.10f
        ),
        LookDef(
            "light-airy", "Light Airy",
            adj = Adjustments(exposure = 12f, contrast = -15f, highlights = 15f, shadows = 15f, saturation = -10f),
            fade = 0.08f
        ),
        LookDef(
            "light-glow", "Light Glow",
            adj = Adjustments(exposure = 10f, contrast = -12f, highlights = 10f, temperature = 8f),
            fade = 0.10f
        ),
        LookDef(
            "light-bright", "Light Bright",
            adj = Adjustments(exposure = 15f, contrast = 5f, highlights = 10f, whites = 10f, saturation = 5f)
        ),
        LookDef(
            "light-soft", "Light Soft",
            adj = Adjustments(exposure = 8f, contrast = -18f, shadows = 12f, saturation = -12f),
            fade = 0.12f
        ),
        LookDef(
            "light-golden", "Light Golden",
            adj = Adjustments(exposure = 8f, contrast = -5f, highlights = 10f, temperature = 20f, saturation = 8f)
        ),
        // ---- Warm ----
        LookDef(
            "golden", "Golden",
            adj = Adjustments(highlights = 10f, temperature = 34f, saturation = 12f),
            fade = 0.05f
        ),
        LookDef(
            "autumn", "Autumn",
            adj = Adjustments(shadows = 8f, temperature = 18f, tint = 8f, vibrance = 20f, saturation = 18f)
        ),
        LookDef(
            "warm-glow", "Warm Glow",
            adj = Adjustments(highlights = -5f, temperature = 25f, vibrance = 15f, saturation = 12f),
            vignette = Vignette(amount = 12f)
        ),
        LookDef(
            "warm-amber", "Warm Amber",
            adj = Adjustments(contrast = 8f, shadows = 8f, temperature = 32f, tint = 6f, saturation = 15f)
        ),
        LookDef(
            "warm-honey", "Warm Honey",
            adj = Adjustments(contrast = 5f, highlights = 5f, temperature = 22f, saturation = 18f),
            fade = 0.05f
        ),
        LookDef(
            "warm-sunset", "Warm Sunset",
            adj = Adjustments(highlights = -15f, temperature = 35f, vibrance = 20f, saturation = 18f),
            vignette = Vignette(amount = 15f)
        ),
        LookDef(
            "warm-vintage", "Warm Vintage",
            adj = Adjustments(contrast = -12f, temperature = 20f, saturation = -5f),
            fade = 0.12f,
            splitTone = SplitTone(shadowHue = 40f, shadowSat = 20f, highlightHue = 45f, highlightSat = 12f)
        ),
        // ---- Duo Tone (B&W base + shadow/highlight tint pair) ----
        LookDef(
            "duo-rose-gold", "Rose Gold",
            adj = Adjustments(contrast = 12f),
            mono = true,
            splitTone = SplitTone(shadowHue = 340f, shadowSat = 30f, highlightHue = 40f, highlightSat = 30f)
        ),
        LookDef(
            "duo-teal-gold", "Teal Gold",
            adj = Adjustments(contrast = 15f),
            mono = true,
            splitTone = SplitTone(shadowHue = 190f, shadowSat = 35f, highlightHue = 45f, highlightSat = 30f)
        ),
        LookDef(
            "duo-purple-gold", "Purple Gold",
            adj = Adjustments(contrast = 12f),
            mono = true,
            splitTone = SplitTone(shadowHue = 270f, shadowSat = 30f, highlightHue = 45f, highlightSat = 25f)
        ),
        LookDef(
            "duo-blue-silver", "Blue Silver",
            adj = Adjustments(contrast = 18f),
            mono = true,
            splitTone = SplitTone(shadowHue = 220f, shadowSat = 25f, highlightHue = 210f, highlightSat = 10f)
        ),
        LookDef(
            "duo-green-gold", "Green Gold",
            adj = Adjustments(contrast = 12f),
            mono = true,
            splitTone = SplitTone(shadowHue = 140f, shadowSat = 25f, highlightHue = 45f, highlightSat = 25f)
        ),
        LookDef(
            "duo-copper", "Copper",
            adj = Adjustments(contrast = 15f),
            mono = true,
            splitTone = SplitTone(shadowHue = 20f, shadowSat = 35f, highlightHue = 35f, highlightSat = 20f)
        ),
        LookDef(
            "duo-midnight", "Midnight",
            adj = Adjustments(contrast = 20f, highlights = -10f),
            mono = true,
            splitTone = SplitTone(shadowHue = 240f, shadowSat = 35f, highlightHue = 220f, highlightSat = 15f)
        ),
        LookDef(
            "duo-coral-teal", "Coral Teal",
            adj = Adjustments(contrast = 12f),
            mono = true,
            splitTone = SplitTone(shadowHue = 190f, shadowSat = 30f, highlightHue = 15f, highlightSat = 30f)
        )
    )

    /**
     * Selective-colour base for Splash: every channel desaturated except
     * [pop] (MIX_CHANNELS order: Red 0, Orange 1, Yellow 2, Green 3,
     * Aqua 4, Blue 5, Purple 6, Magenta 7).
     */
    private fun splashMix(pop: Int): ColorMix =
        ColorMix(sat = FloatArray(8) { if (it == pop) 0f else -100f })

    /**
     * Looks categories, mirroring Express's grouped strip. Every preset
     * belongs to exactly one category; Original lives in Normal.
     */
    val CATEGORIES: List<Pair<String, List<LookDef>>> by lazy {
        val byIdMap = ALL.associateBy { it.id }
        fun group(name: String, vararg ids: String) =
            name to ids.mapNotNull { byIdMap[it] }
        listOf(
            group("Normal", "original", "vivid", "punch", "warm", "cool", "natural", "soft"),
            group("Free", "spring", "summer", "fall", "winter", "bloom", "sunset-glow"),
            group("HDR", "drama", "chrome", "hdr-light", "hdr-strong", "hdr-vivid", "hdr-mono", "hdr-sunset"),
            group("Charm", "charm-pink", "charm-peach", "charm-mint", "charm-lavender", "charm-sky", "charm-cream"),
            group("Black & White", "mono", "noir", "bw-contrast", "bw-soft", "bw-film", "sepia", "bw-infrared", "bw-vintage"),
            group("Portrait", "glamour", "vintage-portrait", "rosy", "golden-hour", "studio", "matte-portrait"),
            group("Matte", "fade", "matte", "matte-warm", "matte-cool", "matte-film", "matte-pastel"),
            group("White Balance", "wb-cloudy", "wb-shade", "wb-tungsten", "wb-daylight", "wb-fluorescent", "wb-flash"),
            group("Splash", "splash-red", "splash-orange", "splash-yellow", "splash-green", "splash-blue", "splash-purple", "splash-pink"),
            group("Nature", "cinematic", "twilight", "forest", "ocean", "desert", "mountain"),
            group("Light", "breeze", "pastel", "light-airy", "light-glow", "light-bright", "light-soft", "light-golden"),
            group("Warm", "golden", "autumn", "warm-glow", "warm-amber", "warm-honey", "warm-sunset", "warm-vintage"),
            group("Duo Tone", "duo-rose-gold", "duo-teal-gold", "duo-purple-gold", "duo-blue-silver", "duo-green-gold", "duo-copper", "duo-midnight", "duo-coral-teal")
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
            sharpen = look.adj.sharpen * k,
            clarity = look.adj.clarity * k,
            texture = look.adj.texture * k,
            dehaze = look.adj.dehaze * k,
            splitTone = SplitTone(
                shadowHue = look.splitTone.shadowHue,
                shadowSat = look.splitTone.shadowSat * k,
                highlightHue = look.splitTone.highlightHue,
                highlightSat = look.splitTone.highlightSat * k
            ),
            grain = look.grain * k,
            vignette = Vignette(
                amount = look.vignette.amount * k,
                midPoint = look.vignette.midPoint,
                feather = look.vignette.feather,
                roundness = look.vignette.roundness
            ),
            colorMix = ColorMix(
                FloatArray(8) { look.colorMix.hue[it] * k },
                FloatArray(8) { look.colorMix.sat[it] * k },
                FloatArray(8) { look.colorMix.lum[it] * k }
            )
        )
    }
}
