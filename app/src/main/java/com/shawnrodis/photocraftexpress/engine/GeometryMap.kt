package com.shawnrodis.photocraftexpress.engine

/**
 * Maps normalized points and rectangles from the SOURCE photo's frame
 * into the RENDERED image's frame, following the same order the pipeline
 * applies geometry: rotation -> flips -> crop -> perspective transform.
 * Used to place ML Kit detections (face rects, landmarks, person masks
 * are computed per-pixel elsewhere) into rendered coordinates.
 * All functions are pure and JVM-testable.
 */
object GeometryMap {

    /** Maps one normalized point through rotation/flips/crop (no perspective). */
    fun mapPoint(
        x: Float, y: Float,
        rotation: Int, flipH: Boolean, flipV: Boolean,
        crop: CropRect
    ): Pair<Float, Float> {
        var px = x
        var py = y
        when (((rotation % 360) + 360) % 360) {
            90 -> {
                val nx = 1f - py
                val ny = px
                px = nx; py = ny
            }
            180 -> {
                px = 1f - px
                py = 1f - py
            }
            270 -> {
                val nx = py
                val ny = 1f - px
                px = nx; py = ny
            }
        }
        if (flipH) px = 1f - px
        if (flipV) py = 1f - py
        if (crop != CropRect.FULL) {
            px = ((px - crop.left) / crop.width).coerceIn(0f, 1f)
            py = ((py - crop.top) / crop.height).coerceIn(0f, 1f)
        }
        return px to py
    }

    /** Maps an axis-aligned normalized rect by mapping its corners. */
    fun mapRect(
        rect: CropRect,
        rotation: Int, flipH: Boolean, flipV: Boolean,
        crop: CropRect
    ): CropRect {
        val corners = listOf(
            mapPoint(rect.left, rect.top, rotation, flipH, flipV, crop),
            mapPoint(rect.right, rect.top, rotation, flipH, flipV, crop),
            mapPoint(rect.right, rect.bottom, rotation, flipH, flipV, crop),
            mapPoint(rect.left, rect.bottom, rotation, flipH, flipV, crop)
        )
        val xs = corners.map { it.first }
        val ys = corners.map { it.second }
        return CropRect(
            xs.minOrNull() ?: 0f,
            ys.minOrNull() ?: 0f,
            xs.maxOrNull() ?: 1f,
            ys.maxOrNull() ?: 1f
        )
    }

    /**
     * Full mapping including the perspective transform: the perspective
     * quad lives in cropped-frame normalized coordinates, so it applies
     * after crop.
     */
    fun mapPointFull(
        x: Float, y: Float,
        rotation: Int, flipH: Boolean, flipV: Boolean,
        crop: CropRect, transform: WarpMath.Quad?
    ): Pair<Float, Float> {
        val (px, py) = mapPoint(x, y, rotation, flipH, flipV, crop)
        if (transform == null || transform.isIdentity) return px to py
        return WarpMath.forwardPoint(transform, px, py)
    }
}
