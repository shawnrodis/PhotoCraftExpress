package com.shawnrodis.photocraftexpress.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.shawnrodis.photocraftexpress.engine.PixelImage
import kotlin.math.max

/** Conversions between Android Bitmaps and the engine's [PixelImage]. */
object BitmapBridge {

    fun toPixelImage(bitmap: Bitmap): PixelImage {
        val w = bitmap.width
        val h = bitmap.height
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, 0, 0, w, h)
        return PixelImage(px, w, h)
    }

    fun toBitmap(img: PixelImage): Bitmap {
        val bmp = Bitmap.createBitmap(img.width, img.height, Bitmap.Config.ARGB_8888)
        bmp.setPixels(img.pixels, 0, img.width, 0, 0, img.width, img.height)
        return bmp
    }

    /** Downscales so the long edge is at most [maxLongEdge] (no-op if smaller). */
    fun downscale(img: PixelImage, maxLongEdge: Int): PixelImage {
        val longEdge = max(img.width, img.height)
        if (longEdge <= maxLongEdge) return img
        val scale = maxLongEdge.toFloat() / longEdge
        val nw = max(1, (img.width * scale).toInt())
        val nh = max(1, (img.height * scale).toInt())
        val scaled = Bitmap.createScaledBitmap(toBitmap(img), nw, nh, true)
        return toPixelImage(scaled)
    }

    /**
     * Loads a photo from [uri], EXIF-corrected, sampled so its long edge is
     * at most [maxLongEdge] and its pixel count stays under [maxPixels].
     * Returns null if the image cannot be decoded.
     */
    fun loadImage(context: Context, uri: Uri, maxLongEdge: Int, maxPixels: Long = Long.MAX_VALUE): PixelImage? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (true) {
            val w = bounds.outWidth / sample
            val h = bounds.outHeight / sample
            val longEdge = max(w, h)
            val pixels = w.toLong() * h.toLong()
            if ((longEdge <= maxLongEdge || sample >= 32) && pixels <= maxPixels) break
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        bmp = applyExifOrientation(context, uri, bmp)
        // The power-of-two sampler can leave us slightly over the caps for
        // extreme aspect ratios; finish with an exact scale if needed.
        val img = toPixelImage(bmp)
        val overEdge = max(img.width, img.height) > maxLongEdge
        val overPixels = img.width.toLong() * img.height.toLong() > maxPixels
        return if (overEdge || overPixels) {
            val edgeScale = if (overEdge) maxLongEdge.toFloat() / max(img.width, img.height) else 1f
            val pixScale = if (overPixels) kotlin.math.sqrt(maxPixels.toDouble() / (img.width.toDouble() * img.height)).toFloat() else 1f
            val target = max(1, (max(img.width, img.height) * minOf(edgeScale, pixScale)).toInt())
            downscale(img, target)
        } else {
            img
        }
    }

    private fun applyExifOrientation(context: Context, uri: Uri, bmp: Bitmap): Bitmap {
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f); matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f); matrix.postScale(-1f, 1f)
            }
            else -> return bmp
        }
        return try {
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        } catch (e: Exception) {
            bmp
        }
    }
}
