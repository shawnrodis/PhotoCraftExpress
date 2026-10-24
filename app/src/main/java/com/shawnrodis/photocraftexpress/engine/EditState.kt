package com.shawnrodis.photocraftexpress.engine

/**
 * The 11 Express-style adjustment sliders. All values are -100..100 with
 * 0 = identity, except [sharpen] which is 0..100.
 */
data class Adjustments(
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val vibrance: Float = 0f,
    val saturation: Float = 0f,
    val sharpen: Float = 0f
) {
    val isIdentity: Boolean
        get() = exposure == 0f && contrast == 0f && highlights == 0f && shadows == 0f &&
            whites == 0f && blacks == 0f && temperature == 0f && tint == 0f &&
            vibrance == 0f && saturation == 0f && sharpen == 0f

    /** Any of the sliders that feed the tonal anchor curve. */
    val hasTonal: Boolean
        get() = highlights != 0f || shadows != 0f || whites != 0f || blacks != 0f

    /** Any of the sliders applied per-pixel after the tone LUT. */
    val hasPixelStage: Boolean
        get() = temperature != 0f || tint != 0f || vibrance != 0f || saturation != 0f
}

/**
 * Immutable snapshot of every edit applied to a photo. Undo/redo is a stack
 * of these. [crop] lives in the *display frame* — the coordinate space of
 * the photo after [rotation] and the flips have been applied — so the crop
 * overlay can draw it directly; rotation/flip edits remap it (see
 * [CropGeometry]).
 */
data class EditState(
    val lookId: String = Looks.ORIGINAL_ID,
    val lookIntensity: Float = 100f,
    val adj: Adjustments = Adjustments(),
    val rotation: Int = 0, // clockwise degrees: 0, 90, 180, 270
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val crop: CropRect = CropRect.FULL
) {
    val isIdentity: Boolean
        get() = lookId == Looks.ORIGINAL_ID && adj.isIdentity && rotation == 0 &&
            !flipH && !flipV && crop == CropRect.FULL
}
