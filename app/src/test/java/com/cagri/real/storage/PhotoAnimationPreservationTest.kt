package com.cagri.real.storage

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Pure file-signature tests; real Bitmap compression/EXIF needs device validation. */
class PhotoAnimationPreservationTest {
    private fun check(bytes: ByteArray, expected: Boolean) {
        val file = File.createTempFile("real-animation", ".bin")
        try { file.writeBytes(bytes); assertEquals(expected, PhotoOptimizer.animated(file)) } finally { file.delete() }
    }
    @Test fun gifWithNoExtensionIsPreserved() = check("GIF89a".toByteArray(), true)
    @Test fun animatedWebpIsPreserved() {
        val bytes = ByteArray(32)
        "RIFF".toByteArray().copyInto(bytes); "WEBPVP8X".toByteArray().copyInto(bytes, 8); bytes[20] = 2
        check(bytes, true); bytes[20] = 0; check(bytes, false)
    }
    @Test fun animatedPngControlChunkIsPreserved() {
        val bytes = byteArrayOf(0x89.toByte(), 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 0) + "acTL".toByteArray() + ByteArray(4)
        check(bytes, true)
    }
    @Test fun jpegIsEligibleAndEmptyFileDoesNotCrashSniffing() {
        check(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte()), false); check(byteArrayOf(), false)
    }
}
