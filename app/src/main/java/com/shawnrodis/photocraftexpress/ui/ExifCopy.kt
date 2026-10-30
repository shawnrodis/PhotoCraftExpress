package com.shawnrodis.photocraftexpress.ui

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * v0.2.6: EXIF preservation on export. Bitmap.compress() emits no EXIF,
 * so exports used to lose capture date, camera/exposure data, and GPS
 * (Photoshop Express keeps them all). This copies the source photo's
 * EXIF onto the exported JPEG, with two deliberate overrides — see
 * [copyAttributes]. JPEG only: ExifInterface write support is a JPEG
 * story, so PNG exports skip this step rather than forcing it.
 */
object ExifCopy {

    /** Tags copied verbatim from the source when present. */
    private val COPIED_TAGS = arrayOf(
        // Capture dates.
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME,
        ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
        ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
        // Location.
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_PROCESSING_METHOD,
        // Camera / lens.
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_LENS_MAKE,
        ExifInterface.TAG_LENS_MODEL,
        ExifInterface.TAG_IMAGE_DESCRIPTION,
        // Exposure.
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_APERTURE_VALUE,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_SHUTTER_SPEED_VALUE,
        ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        ExifInterface.TAG_ISO_SPEED,
        ExifInterface.TAG_FLASH,
        ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_EXPOSURE_PROGRAM,
        ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
        ExifInterface.TAG_METERING_MODE,
        ExifInterface.TAG_CONTRAST,
        ExifInterface.TAG_SATURATION,
        ExifInterface.TAG_SHARPNESS,
        ExifInterface.TAG_COLOR_SPACE,
        ExifInterface.TAG_SENSING_METHOD,
        ExifInterface.TAG_CUSTOM_RENDERED,
        ExifInterface.TAG_SCENE_CAPTURE_TYPE,
        ExifInterface.TAG_GAIN_CONTROL,
        ExifInterface.TAG_LIGHT_SOURCE,
        // Resolution (capture DPI; harmless to keep).
        ExifInterface.TAG_X_RESOLUTION,
        ExifInterface.TAG_Y_RESOLUTION,
        ExifInterface.TAG_RESOLUTION_UNIT,
    )

    /**
     * Copies EXIF from [sourceFile] onto [destFile] (both JPEGs).
     * Never throws: any failure (missing source, unreadable EXIF,
     * unwritable dest) leaves the export as-is.
     */
    fun copyToFile(sourceFile: File, destFile: File, exportW: Int, exportH: Int) {
        try {
            val src = ExifInterface(sourceFile.absolutePath)
            val dst = ExifInterface(destFile.absolutePath)
            copyAttributes(src, dst, exportW, exportH)
            dst.saveAttributes()
        } catch (_: Exception) {
            // EXIF is metadata, never worth failing an export over.
        }
    }

    /** Same, but the source is a content:// URI (the editor's photo). */
    fun copyFromUri(context: Context, sourceUri: Uri, destFile: File, exportW: Int, exportH: Int) {
        try {
            val src = context.contentResolver.openInputStream(sourceUri)?.use { ExifInterface(it) }
                ?: return
            val dst = ExifInterface(destFile.absolutePath)
            copyAttributes(src, dst, exportW, exportH)
            dst.saveAttributes()
        } catch (_: Exception) {
            // EXIF is metadata, never worth failing an export over.
        }
    }

    /** Same, but the destination is a MediaStore URI (SDK 29+ save path). */
    fun copyToOutputUri(context: Context, sourceUri: Uri, outputUri: Uri, exportW: Int, exportH: Int) {
        try {
            val src = context.contentResolver.openInputStream(sourceUri)?.use { ExifInterface(it) }
                ?: return
            context.contentResolver.openFileDescriptor(outputUri, "rw")?.use { pfd ->
                val dst = ExifInterface(pfd.fileDescriptor)
                copyAttributes(src, dst, exportW, exportH)
                dst.saveAttributes()
            }
        } catch (_: Exception) {
            // EXIF is metadata, never worth failing an export over.
        }
    }

    /**
     * The tag transfer itself, shared by every destination shape so the
     * unit tests cover the real logic. Deliberately NOT copied:
     * SOFTWARE (the file is an edited export, not the camera's output),
     * MAKER_NOTE (a device-private blob, stale after editing),
     * USER_COMMENT (free text, not capture metadata).
     */
    fun copyAttributes(src: ExifInterface, dst: ExifInterface, exportW: Int, exportH: Int) {
        for (tag in COPIED_TAGS) {
            src.getAttribute(tag)?.let { dst.setAttribute(tag, it) }
        }
        // Overrides: pixels are already upright (orientation is baked in
        // on load, and user rotation edits are baked in at render), so
        // keeping the source orientation would double-rotate. Dimensions
        // must describe the exported file, not the source (crop/resize
        // change them).
        dst.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        dst.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, exportW.toString())
        dst.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, exportH.toString())
    }
}
