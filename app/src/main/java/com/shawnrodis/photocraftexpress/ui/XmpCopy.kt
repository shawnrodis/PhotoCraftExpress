package com.shawnrodis.photocraftexpress.ui

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * v0.2.7: XMP packet preservation. ExifInterface never touches XMP, so
 * the v0.2.6 EXIF copy left the source's XMP packet behind — one of the
 * two candidates for the missing "Wide-angle lens" line (the other is
 * MakerNote, now copied by ExifCopy). XMP lives in its own JPEG APP1
 * segment, so this is pure JPEG byte work: no EXIF library, no
 * re-encoding, no pixel changes, and it runs AFTER the ExifInterface
 * step in the export pipeline so no later rewrite can drop it.
 *
 * Only the standard single APP1 packet is handled. Extended XMP (split
 * across segments) is left alone — a partial packet is worse than none.
 * Everything here is no-op-safe: a missing/malformed packet, an
 * oversized packet, or any I/O failure leaves the export untouched
 * (never worth failing an export over metadata).
 */
object XmpCopy {

    /** "http://ns.adobe.com/xap/1.0/" + NUL, the XMP APP1 header. */
    private val XMP_HEADER =
        "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.US_ASCII)

    /**
     * Extracts the XMP packet from a JPEG's bytes, or null if there is
     * none (or the bytes aren't a parseable JPEG). Walks the segment
     * chain from SOI, stopping at SOS/EOI; fill bytes between segments
     * are skipped. Only an APP1 segment whose payload starts with the
     * XMP header counts — the EXIF APP1 ("Exif\0\0") is skipped.
     */
    fun extractXmpPacket(jpeg: ByteArray): ByteArray? {
        if (jpeg.size < 4) return null
        if (jpeg[0] != 0xFF.toByte() || jpeg[1] != 0xD8.toByte()) return null
        var pos = 2
        while (pos + 4 <= jpeg.size) {
            if (jpeg[pos] != 0xFF.toByte()) return null // lost sync: bail
            // Skip fill bytes: segStart is the last 0xFF before the marker.
            var m = pos + 1
            while (m < jpeg.size && jpeg[m] == 0xFF.toByte()) m++
            if (m >= jpeg.size) return null
            val marker = jpeg[m].toInt() and 0xFF
            val segStart = m - 1
            when (marker) {
                0xD9 -> return null // EOI: no XMP found
                0xDA -> return null // SOS: scan data follows, can't walk further
                0x01, in 0xD0..0xD7 -> pos = m + 1 // standalone, no length
                else -> {
                    if (m + 3 > jpeg.size) return null
                    val length = ((jpeg[m + 1].toInt() and 0xFF) shl 8) or
                        (jpeg[m + 2].toInt() and 0xFF)
                    if (length < 2 || segStart + 2 + length > jpeg.size) return null
                    if (marker == 0xE1) {
                        val dataStart = segStart + 4
                        val dataEnd = segStart + 2 + length
                        if (dataEnd - dataStart >= XMP_HEADER.size &&
                            jpeg.copyOfRange(dataStart, dataStart + XMP_HEADER.size)
                                .contentEquals(XMP_HEADER)
                        ) {
                            return jpeg.copyOfRange(dataStart + XMP_HEADER.size, dataEnd)
                        }
                    }
                    pos = segStart + 2 + length
                }
            }
        }
        return null
    }

    /**
     * Returns [jpeg] with the XMP APP1 segment inserted right after SOI
     * (before any existing segments, so readers find it first). If
     * [packet] is null/empty, the JPEG has no SOI, or the segment would
     * exceed the 64 KiB JPEG segment limit, the input is returned
     * unchanged.
     */
    fun injectXmpPacket(jpeg: ByteArray, packet: ByteArray?): ByteArray {
        if (packet == null || packet.isEmpty()) return jpeg
        if (jpeg.size < 2 || jpeg[0] != 0xFF.toByte() || jpeg[1] != 0xD8.toByte()) return jpeg
        // The length field counts itself (2) + header + packet, but NOT
        // the marker (2), which sits outside it.
        val segmentLength = 2 + XMP_HEADER.size + packet.size
        if (segmentLength > 0xFFFF) return jpeg
        val out = ByteArray(2 + 2 + segmentLength + (jpeg.size - 2))
        var p = 0
        out[p++] = 0xFF.toByte()
        out[p++] = 0xD8.toByte() // SOI
        out[p++] = 0xFF.toByte()
        out[p++] = 0xE1.toByte() // APP1
        out[p++] = (segmentLength shr 8).toByte()
        out[p++] = segmentLength.toByte()
        XMP_HEADER.copyInto(out, p)
        p += XMP_HEADER.size
        packet.copyInto(out, p)
        p += packet.size
        jpeg.copyInto(out, p, 2, jpeg.size)
        return out
    }

    /** Reads the source's XMP packet through the unredacted-read path. */
    private fun readSourcePacket(context: Context, sourceUri: Uri): ByteArray? {
        return try {
            ExifCopy.openSourceStream(context, sourceUri)?.use { extractXmpPacket(it.readBytes()) }
        } catch (_: Exception) {
            null
        }
    }

    /** Copies the source's XMP packet onto a destination file. */
    fun copyXmpToFile(context: Context, sourceUri: Uri, destFile: File) {
        try {
            val packet = readSourcePacket(context, sourceUri) ?: return
            val bytes = destFile.readBytes()
            val injected = injectXmpPacket(bytes, packet)
            if (!injected.contentEquals(bytes)) destFile.writeBytes(injected)
        } catch (_: Exception) {
            // Metadata only: never fail an export over it.
        }
    }

    /** Same, but the destination is a MediaStore URI (SDK 29+ save path). */
    fun copyXmpToOutputUri(context: Context, sourceUri: Uri, outputUri: Uri) {
        try {
            val packet = readSourcePacket(context, sourceUri) ?: return
            // Two independent descriptors: the read FD's offset ends at
            // EOF, so a second fresh FD (offset 0) is used for the
            // overwrite. Mode "w" does not truncate, but that is safe:
            // injection only ever grows the file, so no stale tail can
            // remain.
            val bytes = context.contentResolver.openFileDescriptor(outputUri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).readBytes()
            } ?: return
            val injected = injectXmpPacket(bytes, packet)
            if (injected.contentEquals(bytes)) return
            context.contentResolver.openFileDescriptor(outputUri, "w")?.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).write(injected)
            }
        } catch (_: Exception) {
            // Metadata only: never fail an export over it.
        }
    }
}
