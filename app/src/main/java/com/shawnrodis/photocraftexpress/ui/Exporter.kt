package com.shawnrodis.photocraftexpress.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.shawnrodis.photocraftexpress.engine.EditState
import com.shawnrodis.photocraftexpress.engine.PixelPipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-resolution export: renders the untouched source through the pipeline
 * at (up to) its native resolution, capped at 24 MP to bound memory, then
 * saves a JPEG to Pictures/PhotoCraftExpress or stages it for sharing.
 */
object Exporter {

    private const val MAX_EXPORT_PIXELS = 24_000_000L
    private const val JPEG_QUALITY = 92

    /** Returns a user-facing result message. */
    suspend fun export(context: Context, sourceUri: Uri, state: EditState, share: Boolean): String =
        withContext(Dispatchers.Default) {
            val full = BitmapBridge.loadImage(
                context, sourceUri,
                maxLongEdge = 6000,
                maxPixels = MAX_EXPORT_PIXELS
            ) ?: return@withContext "Could not load the full-resolution photo."
            val rendered = try {
                PixelPipeline.render(full, state)
            } catch (e: OutOfMemoryError) {
                return@withContext "Photo too large to export at full size on this device."
            }
            val bmp = BitmapBridge.toBitmap(rendered)
            val name = "PCE_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
            if (share) shareImage(context, bmp, name) else saveToGallery(context, bmp, name)
        }

    private fun saveToGallery(context: Context, bmp: Bitmap, name: String): String {
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PhotoCraftExpress")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                ) ?: return "Save failed: MediaStore refused the image."
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
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
                    bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null)
                "Saved to Pictures/PhotoCraftExpress"
            }
        } catch (e: Exception) {
            "Save failed: ${e.message ?: "unknown error"}"
        }
    }

    private fun shareImage(context: Context, bmp: Bitmap, name: String): String {
        return try {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, name)
            file.outputStream().use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share photo"))
            "Opening share sheet…"
        } catch (e: Exception) {
            "Share failed: ${e.message ?: "unknown error"}"
        }
    }
}
