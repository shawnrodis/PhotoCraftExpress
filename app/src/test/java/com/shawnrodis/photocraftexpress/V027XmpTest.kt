package com.shawnrodis.photocraftexpress

import com.shawnrodis.photocraftexpress.ui.XmpCopy
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.2.7 tests: XMP packet preservation as pure JPEG byte work. The
 * segment walker must find the XMP APP1 among other segments (EXIF
 * APP1 included), and injection must place a well-formed APP1 right
 * after SOI. No Android framework is involved — these are byte tests.
 */
class V027XmpTest {

    private val xmpHeader = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.US_ASCII)
    private val packet = "<x:xmpmeta>test-packet</x:xmpmeta>".toByteArray(Charsets.US_ASCII)

    /** Builds one segment: marker + 2-byte BE length + payload. */
    private fun segment(marker: Int, payload: ByteArray): ByteArray {
        val length = 2 + payload.size
        val out = ByteArray(2 + length)
        out[0] = 0xFF.toByte()
        out[1] = marker.toByte()
        out[2] = (length shr 8).toByte()
        out[3] = length.toByte()
        payload.copyInto(out, 4)
        return out
    }

    private fun jpegWith(vararg segments: ByteArray): ByteArray {
        val body = segments.fold(byteArrayOf()) { acc, s -> acc + s }
        return byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + body +
            byteArrayOf(0xFF.toByte(), 0xD9.toByte())
    }

    @Test fun extractFindsXmpAmongOtherSegments() {
        val jpeg = jpegWith(
            segment(0xE0, "JFIF\u0000".toByteArray(Charsets.US_ASCII)),
            segment(0xE1, "Exif\u0000\u0000".toByteArray(Charsets.US_ASCII) + byteArrayOf(1, 2, 3)),
            segment(0xE1, xmpHeader + packet),
            segment(0xE2, byteArrayOf(9, 9, 9))
        )
        assertArrayEquals(packet, XmpCopy.extractXmpPacket(jpeg))
    }

    @Test fun extractSkipsFillBytes() {
        // SOI, then a fill 0xFF before the APP1 marker.
        val seg = segment(0xE1, xmpHeader + packet)
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + seg +
            byteArrayOf(0xFF.toByte(), 0xD9.toByte())
        assertArrayEquals(packet, XmpCopy.extractXmpPacket(jpeg))
    }

    @Test fun extractReturnsNullWithoutXmp() {
        val jpeg = jpegWith(
            segment(0xE1, "Exif\u0000\u0000".toByteArray(Charsets.US_ASCII) + byteArrayOf(1, 2, 3))
        )
        assertNull(XmpCopy.extractXmpPacket(jpeg))
    }

    @Test fun extractRejectsNonJpegAndTruncated() {
        assertNull(XmpCopy.extractXmpPacket(byteArrayOf(1, 2, 3, 4)))
        assertNull(XmpCopy.extractXmpPacket(byteArrayOf(0xFF.toByte(), 0xD8.toByte())))
        // APP1 header claims more bytes than exist.
        val bad = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte(), 0x00, 0x40)
        assertNull(XmpCopy.extractXmpPacket(bad))
    }

    @Test fun injectRoundTrips() {
        val jpeg = jpegWith(
            segment(0xE1, "Exif\u0000\u0000".toByteArray(Charsets.US_ASCII) + byteArrayOf(1, 2, 3))
        )
        val injected = XmpCopy.injectXmpPacket(jpeg, packet)
        // SOI, then the new APP1: marker + correct length field.
        assertEquals(0xFF.toByte(), injected[0])
        assertEquals(0xD8.toByte(), injected[1])
        assertEquals(0xFF.toByte(), injected[2])
        assertEquals(0xE1.toByte(), injected[3])
        val length = ((injected[4].toInt() and 0xFF) shl 8) or (injected[5].toInt() and 0xFF)
        assertEquals(2 + xmpHeader.size + packet.size, length)
        // The original bytes follow untouched after the new segment.
        assertArrayEquals(jpeg.copyOfRange(2, jpeg.size), injected.copyOfRange(6 + xmpHeader.size + packet.size, injected.size))
        // And the packet extracts back out.
        assertArrayEquals(packet, XmpCopy.extractXmpPacket(injected))
    }

    @Test fun injectNoopsSafely() {
        val jpeg = jpegWith(segment(0xE0, byteArrayOf(1, 2)))
        assertTrue(XmpCopy.injectXmpPacket(jpeg, null).contentEquals(jpeg))
        assertTrue(XmpCopy.injectXmpPacket(jpeg, byteArrayOf()).contentEquals(jpeg))
        assertTrue(XmpCopy.injectXmpPacket(byteArrayOf(1, 2, 3), packet).contentEquals(byteArrayOf(1, 2, 3)))
        // Oversized packet (over the 64 KiB segment limit) is refused.
        val huge = ByteArray(70000) { 0x41 }
        assertTrue(XmpCopy.injectXmpPacket(jpeg, huge).contentEquals(jpeg))
    }
}
