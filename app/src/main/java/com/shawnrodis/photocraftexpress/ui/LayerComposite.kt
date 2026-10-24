package com.shawnrodis.photocraftexpress.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.shawnrodis.photocraftexpress.engine.EditState
import com.shawnrodis.photocraftexpress.engine.LayerRaster
import com.shawnrodis.photocraftexpress.engine.StickerKind
import com.shawnrodis.photocraftexpress.engine.TextFont
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Bitmap-layer compositing, applied after the pure pixel pipeline:
 * inserted photos, text and stickers. These need Android Canvas, so they
 * live outside PixelPipeline — but the same code runs for preview and
 * export, so what you see is what you save.
 */
object LayerComposite {

    /** Draws inserted photos, text and stickers onto a copy of [bitmap]. */
    fun composite(
        bitmap: Bitmap,
        state: EditState,
        insertedBitmaps: Map<String, Bitmap>
    ): Bitmap {
        if (state.insertedImages.isEmpty() && state.textItems.isEmpty() && state.stickerItems.isEmpty()) {
            return bitmap
        }
        val out = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val w = out.width
        val h = out.height

        for (layer in state.insertedImages) {
            val bmp = insertedBitmaps[layer.id.toString()] ?: continue
            drawInserted(canvas, w, h, layer, bmp)
        }
        for (item in state.textItems) {
            drawText(canvas, w, h, item)
        }
        for (item in state.stickerItems) {
            drawSticker(canvas, w, h, item)
        }
        return out
    }

    private fun drawInserted(
        canvas: Canvas, w: Int, h: Int,
        layer: com.shawnrodis.photocraftexpress.engine.InsertedImage,
        bmp: Bitmap
    ) {
        val targetW = (layer.scale * w).toInt().coerceAtLeast(1)
        val targetH = (targetW.toFloat() * bmp.height / bmp.width).toInt().coerceAtLeast(1)
        val cx = layer.x * w
        val cy = layer.y * h
        if (!layer.useMask || layer.maskStrokes.isEmpty()) {
            val left = cx - targetW / 2f
            val top = cy - targetH / 2f
            canvas.save()
            canvas.rotate(layer.rotation, cx, cy)
            canvas.drawBitmap(
                Bitmap.createScaledBitmap(bmp, targetW, targetH, true),
                left, top, null
            )
            canvas.restore()
            return
        }
        // Masked: build a bounds-sized temp, sampling the full-frame mask
        // (rasterized from the brush strokes) at each canvas position,
        // accounting for the layer rotation.
        val rot = Math.toRadians(layer.rotation.toDouble())
        val cosR = cos(rot)
        val sinR = sin(rot)
        val halfDiag = kotlin.math.sqrt((targetW / 2f) * (targetW / 2f) + (targetH / 2f) * (targetH / 2f))
        val bw = (halfDiag * 2).toInt().coerceAtLeast(1)
        val bh = bw
        val mask = LayerRaster.rasterizeMask(w, h, layer.maskStrokes)
        val scaled = Bitmap.createScaledBitmap(bmp, targetW, targetH, true)
        val temp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val px = IntArray(bw * bh)
        val spx = IntArray(targetW * targetH)
        scaled.getPixels(spx, 0, targetW, 0, 0, targetW, targetH)
        val left = (cx - bw / 2f).toInt()
        val top = (cy - bh / 2f).toInt()
        for (ty in 0 until bh) {
            for (tx in 0 until bw) {
                // Canvas position of this temp pixel.
                val canvasX = left + tx
                val canvasY = top + ty
                if (canvasX !in 0 until w || canvasY !in 0 until h) continue
                // Inverse-rotate around the layer centre into layer space.
                val dx = canvasX - cx
                val dy = canvasY - cy
                val lx = (dx * cosR + dy * sinR + targetW / 2f).toInt()
                val ly = (-dx * sinR + dy * cosR + targetH / 2f).toInt()
                if (lx !in 0 until targetW || ly !in 0 until targetH) continue
                val m = mask[canvasY * w + canvasX]
                if (m <= 0f) continue
                val src = spx[ly * targetW + lx]
                val a = ((src ushr 24) * m).toInt().coerceIn(0, 255)
                px[ty * bw + tx] = (a shl 24) or (src and 0x00FFFFFF)
            }
        }
        temp.setPixels(px, 0, bw, 0, 0, bw, bh)
        canvas.drawBitmap(temp, left.toFloat(), top.toFloat(), null)
    }

    private fun drawText(
        canvas: Canvas, w: Int, h: Int,
        item: com.shawnrodis.photocraftexpress.engine.TextItem
    ) {
        val textSize = item.size * h
        if (textSize < 4f || item.text.isEmpty()) return
        val family = when (item.font) {
            TextFont.SANS -> Typeface.SANS_SERIF
            TextFont.SERIF -> Typeface.SERIF
            TextFont.MONO -> Typeface.MONOSPACE
        }
        val style = when {
            item.bold && item.italic -> Typeface.BOLD_ITALIC
            item.bold -> Typeface.BOLD
            item.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(family, style)
            this.textSize = textSize
            textAlign = Paint.Align.CENTER
        }
        val cx = item.x * w
        val cy = item.y * h
        // Draw stroke first (outline), then fill, centred on (cx, cy).
        if (item.strokeWidth > 0f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = item.strokeWidth * textSize
            paint.strokeJoin = Paint.Join.ROUND
            paint.color = item.strokeColor
            canvas.drawText(item.text, cx, cy + textSize * 0.35f, paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = item.color
        canvas.drawText(item.text, cx, cy + textSize * 0.35f, paint)
    }

    private fun starPath(cx: Float, cy: Float, r: Float, points: Int = 5): Path {
        val path = Path()
        val inner = r * 0.45f
        for (i in 0 until points * 2) {
            val rad = if (i % 2 == 0) r else inner
            val ang = Math.PI * i / points - Math.PI / 2
            val x = (cx + rad * cos(ang)).toFloat()
            val y = (cy + rad * sin(ang)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    private fun heartPath(cx: Float, cy: Float, s: Float): Path {
        val path = Path()
        path.moveTo(cx, cy + s * 0.85f)
        path.cubicTo(cx - s * 1.3f, cy - s * 0.1f, cx - s * 0.6f, cy - s * 0.95f, cx, cy - s * 0.3f)
        path.cubicTo(cx + s * 0.6f, cy - s * 0.95f, cx + s * 1.3f, cy - s * 0.1f, cx, cy + s * 0.85f)
        path.close()
        return path
    }

    private fun drawSticker(
        canvas: Canvas, w: Int, h: Int,
        item: com.shawnrodis.photocraftexpress.engine.StickerItem
    ) {
        val s = item.scale * w
        if (s < 4f) return
        val cx = item.x * w
        val cy = item.y * h
        val path: Path? = when (item.kind) {
            StickerKind.STAR -> starPath(cx, cy, s / 2f)
            StickerKind.SPARKLE -> starPath(cx, cy, s / 2f, 4)
            StickerKind.HEART -> heartPath(cx, cy, s / 2f)
            StickerKind.CIRCLE -> Path().apply { addCircle(cx, cy, s / 2f, Path.Direction.CW) }
            StickerKind.SQUARE -> Path().apply { addRect(cx - s / 2f, cy - s / 2f, cx + s / 2f, cy + s / 2f, Path.Direction.CW) }
            StickerKind.TRIANGLE -> Path().apply {
                moveTo(cx, cy - s / 2f)
                lineTo(cx + s / 2f, cy + s / 2f)
                lineTo(cx - s / 2f, cy + s / 2f)
                close()
            }
            StickerKind.ARROW -> Path().apply {
                moveTo(cx - s / 2f, cy - s * 0.12f)
                lineTo(cx + s * 0.1f, cy - s * 0.12f)
                lineTo(cx + s * 0.1f, cy - s * 0.35f)
                lineTo(cx + s / 2f, cy)
                lineTo(cx + s * 0.1f, cy + s * 0.35f)
                lineTo(cx + s * 0.1f, cy + s * 0.12f)
                lineTo(cx - s / 2f, cy + s * 0.12f)
                close()
            }
            StickerKind.SMILEY -> null // drawn specially below
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = item.color
        }
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(1f, s * 0.04f)
            color = -0x1000000
        }
        canvas.save()
        canvas.rotate(item.rotation, cx, cy)
        if (item.kind == StickerKind.SMILEY) {
            canvas.drawCircle(cx, cy, s / 2f, fill)
            canvas.drawCircle(cx, cy, s / 2f, outline)
            val eyeR = s * 0.07f
            val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = -0x1000000 }
            canvas.drawCircle(cx - s * 0.18f, cy - s * 0.12f, eyeR, eyePaint)
            canvas.drawCircle(cx + s * 0.18f, cy - s * 0.12f, eyeR, eyePaint)
            val smile = Path().apply {
                val rect = RectF(cx - s * 0.28f, cy - s * 0.05f, cx + s * 0.28f, cy + s * 0.42f)
                addArc(rect, 20f, 140f)
            }
            canvas.drawPath(smile, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = max(1f, s * 0.045f)
                strokeCap = Paint.Cap.ROUND
                color = -0x1000000
            })
        } else if (path != null) {
            canvas.drawPath(path, fill)
            canvas.drawPath(path, outline)
        }
        canvas.restore()
    }
}
