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
 *
 * v0.2.7: GPS was the one tag that still didn't land, because Android
 * 10+ redacts location from content:// reads without
 * ACCESS_MEDIA_LOCATION + MediaStore.setRequireOriginal() — see
 * [openSourceStream]. MakerNote joined the copy list (lens identity),
 * and the tag list gained its audit additions.
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
        // v0.2.7 audit: completeness for the GPS fix.
        ExifInterface.TAG_GPS_SATELLITES,
        ExifInterface.TAG_GPS_MEASURE_MODE,
        ExifInterface.TAG_GPS_DOP,
        // Camera / lens.
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_LENS_MAKE,
        ExifInterface.TAG_LENS_MODEL,
        // v0.2.7: MakerNote is copied after all. v0.2.6 excluded it as a
        // "device-private blob", but lens identity lives in it on some
        // phones (the missing "Wide-angle lens" line is the evidence),
        // and viewers treat it as opaque — a stale blob is harmless,
        // while a missing one loses the lens line. Copied verbatim
        // like every other tag; see the caveat in the v0.2.7 report
        // about binary blobs and string round-trips.
        ExifInterface.TAG_MAKER_NOTE,
        // v0.2.7 audit: capture-relevant tags the v0.2.6 list missed
        // that actually survive a save round-trip (probe-verified).
        // Deliberately NOT listed: LENS_SERIAL_NUMBER,
        // DIGITAL_ZOOM_RATIO, SUBJECT_DISTANCE — the constants exist
        // but ExifInterface 1.3.7 silently drops them on save, so
        // listing them would be dead weight that looks like coverage.
        ExifInterface.TAG_BODY_SERIAL_NUMBER,
        ExifInterface.TAG_IMAGE_UNIQUE_ID,
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
        // (DIGITAL_ZOOM_RATIO / SUBJECT_DISTANCE audited and dropped:
        // see the LENS_SERIAL_NUMBER note above.)
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

    /**
     * Opens the source photo's bytes for metadata reads. v0.2.7: on
     * Android 10+, GPS is REDACTED from content:// reads unless the app
     * holds ACCESS_MEDIA_LOCATION and reads through the original URI —
     * that redaction is exactly why v0.2.6 exports kept every tag
     * except the map pin. So for content URIs on API 29+, the original
     * URI is tried first; any failure (no permission, non-MediaStore
     * provider, anything else) falls back to the plain stream, which
     * still yields every tag except GPS. Never throws: null means
     * "read it some other way or skip metadata", never "fail export".
     * Callers close the returned stream. Internal so XmpCopy (same
     * package) can read the source bytes through the same path.
     */
    internal fun openSourceStream(context: Context, sourceUri: Uri): java.io.InputStream? {
        if (android.os.Build.VERSION.SDK_INT >= 29 && sourceUri.scheme == "content") {
            try {
                val original = android.provider.MediaStore.setRequireOriginal(sourceUri)
                val stream = context.contentResolver.openInputStream(original)
                if (stream != null) return stream
            } catch (_: Exception) {
                // Fall through to the plain (GPS-redacted) read.
            }
        }
        return try {
            context.contentResolver.openInputStream(sourceUri)
        } catch (_: Exception) {
            null
        }
    }

    /** Reads the source's EXIF through [openSourceStream], or null. */
    private fun openSourceExif(context: Context, sourceUri: Uri): ExifInterface? {
        return try {
            openSourceStream(context, sourceUri)?.use { ExifInterface(it) }
        } catch (_: Exception) {
            null
        }
    }

    /** Same, but the source is a content:// URI (the editor's photo). */
    fun copyFromUri(context: Context, sourceUri: Uri, destFile: File, exportW: Int, exportH: Int) {
        try {
            val src = openSourceExif(context, sourceUri) ?: return
            val dst = ExifInterface(destFile.absolutePath)
            copyAttributes(src, dst, exportW, exportH)
            dst.saveAttributes()
        } catch (_: Exception) {
            // EXIF is metadata, never worth failing an export over.
        }
    }

    /**
     * Same, but the destination is a MediaStore URI (SDK 29+ save path).
     * Note on testability: ExifInterface only saves through a
     * FileDescriptor it proves seekable (Os.lseek), which is true of
     * ParcelFileDescriptor FDs on device — v0.2.6 field evidence
     * (EXIF present on saved exports) confirms it — but on the JVM
     * the Os call is a stub, so no JVM test can execute this path.
     * The shared copyAttributes logic IS unit-tested via the File
     * path; this FD plumbing itself is field-gated.
     */
    fun copyToOutputUri(context: Context, sourceUri: Uri, outputUri: Uri, exportW: Int, exportH: Int) {
        try {
            val src = openSourceExif(context, sourceUri) ?: return
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
     * USER_COMMENT (free text, not capture metadata). MakerNote IS
     * copied since v0.2.7 (see the tag list comment).
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
