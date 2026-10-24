package com.shawnrodis.photocraftexpress.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.print.PrintHelper
import com.shawnrodis.photocraftexpress.engine.EditState
import com.shawnrodis.photocraftexpress.engine.ExportSize
import com.shawnrodis.photocraftexpress.engine.PixelPipeline
import com.shawnrodis.photocraftexpress.engine.RenderAssets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/** Output image format for the export sheet. */
enum class ExportFormat(
    val extension: String,
    val mime: String,
    val compressFormat: Bitmap.CompressFormat
) {
    JPEG("jpg", "image/jpeg", Bitmap.CompressFormat.JPEG),
    PNG("png", "image/png", Bitmap.CompressFormat.PNG)
}

/**
 * Watermark styles for the export sheet. Our own name only, drawn as
 * plain text — never Adobe's "PS Express" marks or anything resembling
 * them. NONE = no watermark.
 */
enum class WatermarkStyle {
    NONE,
    /** "PhotoCraft Express" in the bottom-right corner. */
    TEXT,
    /** Centred at the bottom, with a rule line on each side. */
    TEXT_RULES
}

/** What the export sheet does with the rendered photo. */
enum class ExportAction { SAVE, SHARE, WALLPAPER, PRINT }

/**
 * Full-resolution export: renders the untouched source through the pipeline
 * at (up to) its native resolution, capped at 24 MP to bound memory, runs
 * the same ML Kit analysis the preview uses (face + person mask, mapped
 * into the rendered frame), composites text/stickers/inserted photos,
 * applies the export sheet's output size, format, quality and watermark,
 * then dispatches to the chosen action. Save and Share keep the existing
 * MediaStore / FileProvider paths; Wallpaper uses ACTION_ATTACH_DATA and
 * Print uses the system print UI — no app-specific targets are hardcoded,
 * and there is no Facebook integration.
 */
object Exporter {

    private const val MAX_EXPORT_PIXELS = 24_000_000L

    /** The default quality on the export sheet, matching Express. */
    const val DEFAULT_QUALITY = 85

    data class RenderResult(val bitmap: Bitmap?, val error: String?)

    fun exportFileName(format: ExportFormat): String =
        "PCE_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) +
            "." + format.extension

    /**
     * Renders the export bitmap (full pipeline + layers + resize +
     * watermark). Returns the bitmap, or an error message on failure.
     * Safe to call from a background thread.
     */
    suspend fun renderExport(
        context: Context, sourceUri: Uri, state: EditState,
        size: ExportSize, format: ExportFormat, quality: Int,
        watermark: WatermarkStyle
    ): RenderResult = withContext(Dispatchers.Default) {
        val full = BitmapBridge.loadImage(
            context, sourceUri,
            maxLongEdge = 6000,
            maxPixels = MAX_EXPORT_PIXELS
        ) ?: return@withContext RenderResult(null, "Could not load the full-resolution photo.")
        val rendered = try {
            val fullBmp = BitmapBridge.toBitmap(full)
            val analysis = FaceAnalysis.analyze(fullBmp)
            val faceData = analysis.faces.firstOrNull()?.let {
                FaceAnalysis.mapFace(
                    it, state, true,
                    fullBmp.width.toFloat() / fullBmp.height
                )
            }
            PixelPipeline.render(
                full, state, true,
                RenderAssets(faceData, analysis.personMask)
            )
        } catch (e: OutOfMemoryError) {
            return@withContext RenderResult(
                null, "Photo too large to export at full size on this device."
            )
        }
        var bmp = BitmapBridge.toBitmap(rendered)
        if (state.insertedImages.isNotEmpty() || state.textItems.isNotEmpty() ||
            state.stickerItems.isNotEmpty()
        ) {
            val inserted = mutableMapOf<String, Bitmap>()
            for (layer in state.insertedImages) {
                val px = BitmapBridge.loadImage(
                    context, Uri.parse(layer.uri), 1600
                )
                if (px != null) inserted[layer.id.toString()] = BitmapBridge.toBitmap(px)
            }
            bmp = LayerComposite.composite(bmp, state, inserted)
        }
        val target = size.outputSize(bmp.width, bmp.height)
        if (target != null) {
            bmp = Bitmap.createScaledBitmap(bmp, target.first, target.second, true)
        }
        bmp = applyWatermark(bmp, watermark)
        RenderResult(bmp, null)
    }

    /**
     * Renders and dispatches in one call for the background-safe actions
     * (SAVE, SHARE, WALLPAPER). PRINT must go through [renderExport] +
     * [printImage] from the main thread instead. Returns a user-facing
     * result message.
     */
    suspend fun export(
        context: Context, sourceUri: Uri, state: EditState,
        size: ExportSize, format: ExportFormat, quality: Int,
        watermark: WatermarkStyle, action: ExportAction
    ): String {
        val (bmp, error) = renderExport(
            context, sourceUri, state, size, format, quality, watermark
        )
        if (bmp == null) return error ?: "Export failed."
        val name = exportFileName(format)
        return when (action) {
            ExportAction.SAVE -> saveToGallery(context, bmp, format, quality, name)
            ExportAction.SHARE -> shareImage(context, bmp, format, quality, name)
            ExportAction.WALLPAPER -> setWallpaper(context, bmp, format, quality, name)
            ExportAction.PRINT -> "Print must run on the main thread."
        }
    }

    /**
     * Draws our own text watermark onto a copy of [src]. TEXT puts
     * "PhotoCraft Express" in the bottom-right corner; TEXT_RULES centres
     * it at the bottom with a rule line on each side. White text with a
     * soft black shadow so it stays legible on light photos.
     */
    fun applyWatermark(src: Bitmap, style: WatermarkStyle): Bitmap {
        if (style == WatermarkStyle.NONE) return src
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val text = "PhotoCraft Express"
        val textSize = max(28f, out.height * 0.032f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 220
            this.textSize = textSize
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setShadowLayer(4f, 0f, 2f, Color.BLACK)
        }
        val pad = out.width * 0.03f
        val textWidth = paint.measureText(text)
        val fm = paint.fontMetrics
        val baseline = out.height - pad - fm.descent
        when (style) {
            WatermarkStyle.TEXT -> {
                canvas.drawText(text, out.width - pad - textWidth, baseline, paint)
            }
            WatermarkStyle.TEXT_RULES -> {
                val cx = out.width / 2f
                val gap = textSize * 0.45f
                val y = baseline + fm.descent / 2f - textSize * 0.18f
                val stroke = max(2f, textSize * 0.06f)
                val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    alpha = 220
                    strokeWidth = stroke
                    setShadowLayer(4f, 0f, 2f, Color.BLACK)
                }
                canvas.drawLine(pad, y, cx - textWidth / 2f - gap, y, linePaint)
                canvas.drawLine(cx + textWidth / 2f + gap, y, out.width - pad, y, linePaint)
                canvas.drawText(text, cx - textWidth / 2f, baseline, paint)
            }
            WatermarkStyle.NONE -> Unit
        }
        return out
    }

    fun saveToGallery(
        context: Context, bmp: Bitmap, format: ExportFormat, quality: Int, name: String
    ): String {
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, format.mime)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PhotoCraftExpress")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                ) ?: return "Save failed: MediaStore refused the image."
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bmp.compress(format.compressFormat, quality, out)
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                "Saved to Pictures/PhotoCraftExpress"
            } else {
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "PhotoCraftExpress"
                )
                dir.mkdirs()
                val file = File(dir, name)
                file.outputStream().use { out ->
                    bmp.compress(format.compressFormat, quality, out)
                }
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(format.mime), null)
                "Saved to Pictures/PhotoCraftExpress"
            }
        } catch (e: Exception) {
            "Save failed: ${e.message ?: "unknown error"}"
        }
    }

    private fun stageFile(
        context: Context, bmp: Bitmap, format: ExportFormat, quality: Int, name: String
    ): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, name)
        file.outputStream().use { out ->
            bmp.compress(format.compressFormat, quality, out)
        }
        return file
    }

    fun shareImage(
        context: Context, bmp: Bitmap, format: ExportFormat, quality: Int, name: String
    ): String {
        return try {
            val file = stageFile(context, bmp, format, quality, name)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = format.mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            // The system share sheet covers Messages, Messenger, Drive and
            // every other target — nothing app-specific is hardcoded.
            context.startActivity(Intent.createChooser(intent, "Share photo"))
            "Opening share sheet…"
        } catch (e: Exception) {
            "Share failed: ${e.message ?: "unknown error"}"
        }
    }

    fun setWallpaper(
        context: Context, bmp: Bitmap, format: ExportFormat, quality: Int, name: String
    ): String {
        return try {
            val file = stageFile(context, bmp, format, quality, name)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_ATTACH_DATA).apply {
                setDataAndType(uri, format.mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Set as wallpaper"))
            "Choose where to set the wallpaper…"
        } catch (e: Exception) {
            "Wallpaper failed: ${e.message ?: "unknown error"}"
        }
    }

    /**
     * Opens the system print UI for [bmp]. Must be called on the main
     * thread (PrintHelper shows UI); callers render on a background
     * thread first via [renderExport], then hop to Main for this.
     */
    fun printImage(context: Context, bmp: Bitmap, name: String): String {
        return try {
            val helper = PrintHelper(context)
            helper.scaleMode = PrintHelper.SCALE_MODE_FIT
            helper.printBitmap(name, bmp)
            "Opening print preview…"
        } catch (e: Exception) {
            "Print failed: ${e.message ?: "unknown error"}"
        }
    }
}
