package com.cagri.real.boards

import org.junit.Assert.*
import org.junit.Test

class BoardImageMimeTest {
    @Test fun gif87And89AreDetectedFromBytesWithoutChangingThem() {
        for(signature in listOf("GIF87a","GIF89a")){
            val bytes=(signature+"payload").toByteArray(Charsets.US_ASCII)
            val original=bytes.copyOf()
            assertEquals("image/gif",boardImageMime(bytes))
            assertArrayEquals(original,bytes)
        }
    }
    @Test fun actualPhotoFormatOverridesMisleadingGifNameOrMimeHint() {
        assertEquals("image/jpeg",boardImageMime(byteArrayOf(-1,-40,-1,0)))
        assertEquals("image/png",boardImageMime(listOf(137,80,78,71,13,10,26,10).map{it.toByte()}.toByteArray()))
        assertEquals("image/webp",boardImageMime("RIFFxxxxWEBP".toByteArray(Charsets.US_ASCII)))
    }
    @Test fun incompleteOrUnsupportedFilesAreNotImportedAsGif() {
        assertNull(boardImageMime(byteArrayOf()))
        assertNull(boardImageMime("GIF8".toByteArray(Charsets.US_ASCII)))
        assertNull(boardImageMime("<html>error".toByteArray(Charsets.US_ASCII)))
        assertNull(boardImageMime("GIF00a".toByteArray(Charsets.US_ASCII)))
    }
}
