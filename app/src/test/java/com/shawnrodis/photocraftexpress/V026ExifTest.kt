package com.shawnrodis.photocraftexpress

import androidx.exifinterface.media.ExifInterface
import com.shawnrodis.photocraftexpress.ui.ExifCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * v0.2.6 tests: EXIF preservation on export. Exports used to write raw
 * bitmap bytes with no EXIF step at all, so capture date, camera /
 * exposure data, and GPS were lost (Photoshop Express keeps them).
 * These tests run the real transfer over temp JPEGs built with
 * ImageIO: copied tags present, GPS round-trips, orientation forced
 * to NORMAL (pixels are already upright), dimensions set to the
 * export size, and EXIF-less or missing sources no-op safely.
 */
class V026ExifTest {

    /**
     * A bare SOI+EOI JPEG. Unit-test compilation uses android.jar as the
     * boot classpath, so java.awt/javax.imageio are unavailable here —
     * and ExifInterface only needs a JPEG container to hang an APP1
     * segment on, which this is (proven by probe: saveAttributes grows
     * it to a tagged file that re-reads cleanly). w/h are ignored: the
     * file's real dims don't matter, export dims come from copyToFile.
     */
    private fun tempJpeg(@Suppress("UNUSED_PARAMETER") w: Int, @Suppress("UNUSED_PARAMETER") h: Int): File {
        val f = File.createTempFile("v026exif", ".jpg")
        f.deleteOnExit()
        FileOutputStream(f).use { it.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())) }
        return f
    }

    private fun approx(a: Double, b: Double, eps: Double = 0.001) =
        assertTrue("expected $b, got $a", abs(a - b) < eps)

    @Test fun copyPreservesCaptureTagsAndGps() {
        val src = tempJpeg(8, 6)
        ExifInterface(src.absolutePath).apply {
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:10:30 00:26:58")
            setAttribute(ExifInterface.TAG_MAKE, "samsung")
            setAttribute(ExifInterface.TAG_MODEL, "Galaxy S25 Ultra")
            setAttribute(ExifInterface.TAG_LENS_MODEL, "Wide-angle lens")
            setAttribute(ExifInterface.TAG_FOCAL_LENGTH, "23/1")
            // FNumber/ExposureTime are set too (a real camera file has
            // all four), but AndroidX ExifInterface 1.3.7 never persists
            // them — verified by probe across value formats and by an
            // independent PIL read of the written file. Their APEX twins
            // below carry the same aperture/shutter information and DO
            // survive; whether a given gallery app derives F1.7/1-120s
            // from them is a field question (see the v0.2.6 field gates).
            setAttribute(ExifInterface.TAG_F_NUMBER, "17/10")
            setAttribute(ExifInterface.TAG_APERTURE_VALUE, "17/10")
            setAttribute(ExifInterface.TAG_EXPOSURE_TIME, "1/120")
            setAttribute(ExifInterface.TAG_SHUTTER_SPEED_VALUE, "690/100")
            setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, "1250")
            setAttribute(ExifInterface.TAG_FLASH, "0")
            setAttribute(ExifInterface.TAG_WHITE_BALANCE, "0")
            // Deliberately rotated: the export must NOT keep this.
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            setLatLong(37.5, -77.5)
            setAltitude(100.0)
            saveAttributes()
        }

        val dst = tempJpeg(4, 4)
        ExifCopy.copyToFile(src, dst, 100, 200)

        ExifInterface(dst.absolutePath).apply {
            assertEquals("2026:10:30 00:26:58", getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
            assertEquals("samsung", getAttribute(ExifInterface.TAG_MAKE))
            assertEquals("Galaxy S25 Ultra", getAttribute(ExifInterface.TAG_MODEL))
            assertEquals("Wide-angle lens", getAttribute(ExifInterface.TAG_LENS_MODEL))
            assertEquals("23/1", getAttribute(ExifInterface.TAG_FOCAL_LENGTH))
            // APEX twins survive and carry the aperture/shutter info
            // (FNumber/ExposureTime are dropped by the library — see above).
            assertEquals("17/10", getAttribute(ExifInterface.TAG_APERTURE_VALUE))
            assertEquals("690/100", getAttribute(ExifInterface.TAG_SHUTTER_SPEED_VALUE))
            assertEquals("1250", getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY))
            assertEquals("0", getAttribute(ExifInterface.TAG_FLASH))
            assertEquals("0", getAttribute(ExifInterface.TAG_WHITE_BALANCE))
            val latLong = getLatLong()
            assertTrue("lat/long missing", latLong != null)
            approx(latLong!![0], 37.5)
            approx(latLong[1], -77.5)
            approx(getAltitude(0.0), 100.0)
            // Overrides: upright pixels, export dimensions.
            assertEquals(
                ExifInterface.ORIENTATION_NORMAL,
                getAttributeInt(ExifInterface.TAG_ORIENTATION, -1)
            )
            assertEquals("100", getAttribute(ExifInterface.TAG_IMAGE_WIDTH))
            assertEquals("200", getAttribute(ExifInterface.TAG_IMAGE_LENGTH))
        }
    }

    @Test fun copyFromExiflessSourceNoops() {
        val src = tempJpeg(8, 6)
        val dst = tempJpeg(4, 4)
        ExifCopy.copyToFile(src, dst, 50, 60)
        ExifInterface(dst.absolutePath).apply {
            assertEquals(
                ExifInterface.ORIENTATION_NORMAL,
                getAttributeInt(ExifInterface.TAG_ORIENTATION, -1)
            )
            assertEquals("50", getAttribute(ExifInterface.TAG_IMAGE_WIDTH))
            assertEquals("60", getAttribute(ExifInterface.TAG_IMAGE_LENGTH))
        }
    }

    @Test fun copyFromMissingSourceNoops() {
        val dst = tempJpeg(4, 4)
        // Must not throw; the export file is left untouched.
        ExifCopy.copyToFile(File("/tmp/v026-does-not-exist.jpg"), dst, 10, 10)
        assertTrue(dst.exists())
    }
}
