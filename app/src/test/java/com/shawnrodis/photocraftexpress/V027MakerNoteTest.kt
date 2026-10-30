package com.shawnrodis.photocraftexpress

import androidx.exifinterface.media.ExifInterface
import com.shawnrodis.photocraftexpress.ui.ExifCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

/**
 * v0.2.7 tests: MakerNote + tag-audit additions. v0.2.6 excluded
 * MakerNote as a "device-private blob", but lens identity lives in it
 * on some phones (the missing "Wide-angle lens" line is the evidence),
 * so it joins the verbatim copy list. The audit also adds lens/body
 * serials, image unique ID, digital zoom, subject distance, and GPS
 * completeness tags. SOFTWARE and USER_COMMENT stay excluded.
 */
class V027MakerNoteTest {

    private fun tempJpeg(): File {
        val f = File.createTempFile("v027maker", ".jpg")
        f.deleteOnExit()
        FileOutputStream(f).use { it.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())) }
        return f
    }

    @Test fun makerNoteAndAuditTagsCopy() {
        val src = tempJpeg()
        ExifInterface(src.absolutePath).apply {
            setAttribute(ExifInterface.TAG_MAKER_NOTE, "TestMakerNote-12345")
            setAttribute(ExifInterface.TAG_BODY_SERIAL_NUMBER, "BSN-5432")
            setAttribute(ExifInterface.TAG_IMAGE_UNIQUE_ID, "UID-abcdef0123456789")
            setAttribute(ExifInterface.TAG_GPS_SATELLITES, "12")
            setAttribute(ExifInterface.TAG_GPS_MEASURE_MODE, "3")
            setAttribute(ExifInterface.TAG_GPS_DOP, "50/10")
            saveAttributes()
        }

        val dst = tempJpeg()
        ExifCopy.copyToFile(src, dst, 100, 200)

        ExifInterface(dst.absolutePath).apply {
            assertEquals("TestMakerNote-12345", getAttribute(ExifInterface.TAG_MAKER_NOTE))
            assertEquals("BSN-5432", getAttribute(ExifInterface.TAG_BODY_SERIAL_NUMBER))
            assertEquals("UID-abcdef0123456789", getAttribute(ExifInterface.TAG_IMAGE_UNIQUE_ID))
            assertEquals("12", getAttribute(ExifInterface.TAG_GPS_SATELLITES))
            assertEquals("3", getAttribute(ExifInterface.TAG_GPS_MEASURE_MODE))
            assertEquals("50/10", getAttribute(ExifInterface.TAG_GPS_DOP))
        }
        // NOTE: LENS_SERIAL_NUMBER, DIGITAL_ZOOM_RATIO and
        // SUBJECT_DISTANCE were audited for this list but are NOT
        // asserted here: a probe proved ExifInterface 1.3.7 silently
        // drops them on save (constants exist, values don't persist),
        // so they are deliberately not in COPIED_TAGS.
    }

    @Test fun softwareAndUserCommentStayExcluded() {
        val src = tempJpeg()
        ExifInterface(src.absolutePath).apply {
            setAttribute(ExifInterface.TAG_SOFTWARE, "SomeCameraApp 1.0")
            setAttribute(ExifInterface.TAG_USER_COMMENT, "free text")
            saveAttributes()
        }

        val dst = tempJpeg()
        ExifCopy.copyToFile(src, dst, 100, 200)

        ExifInterface(dst.absolutePath).apply {
            assertNull(getAttribute(ExifInterface.TAG_SOFTWARE))
            assertNull(getAttribute(ExifInterface.TAG_USER_COMMENT))
        }
    }
}
