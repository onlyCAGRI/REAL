package com.cagri.real.books

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BookProgressAndFreeTest {

    @Test
    fun freeAccessDefaultsToFalse() {
        val book = Book("gb_1", "Title", "Author")
        assertFalse(book.freeAccess)
    }

    @Test
    fun freeAccessSurvivesEncodeAndDecode() {
        val freeBook = Book("gb_free", "Free PD Book", "Author", freeAccess = true)
        val encoded = BookShelfStore.encode(freeBook)
        assertTrue(encoded.getBoolean("freeAccess"))

        val decoded = BookShelfStore.decode(encoded)
        assertTrue(decoded.freeAccess)
        assertEquals(freeBook, decoded)

        val paidBook = Book("gb_paid", "Paid Book", "Author", freeAccess = false)
        val decodedPaid = BookShelfStore.decode(BookShelfStore.encode(paidBook))
        assertFalse(decodedPaid.freeAccess)
    }

    @Test
    fun legacyJsonWithoutFreeAccessFieldDefaultsToFalse() {
        val legacy = JSONObject("""{"id":"gb_old","title":"Old Book","author":"Author","source":"GOOGLE_BOOKS"}""")
        val decoded = BookShelfStore.decode(legacy)
        assertFalse(decoded.freeAccess)
    }

    @Test
    fun readingPositionClampingAndBounds() {
        val rawNegativeIndex = -5
        val rawNegativeOffset = -100
        val clampedIndex = rawNegativeIndex.coerceAtLeast(0)
        val clampedOffset = rawNegativeOffset.coerceAtLeast(0)

        assertEquals(0, clampedIndex)
        assertEquals(0, clampedOffset)

        val validIndex = 12
        val validOffset = 450
        assertEquals(12, validIndex.coerceAtLeast(0))
        assertEquals(450, validOffset.coerceAtLeast(0))

        // When list size is 10, clamping target item index + 1 (for header item)
        val paragraphCount = 10
        val targetIndex = 15 // out of bounds
        val safeIndex = (targetIndex + 1).coerceIn(0, (paragraphCount).coerceAtLeast(0))
        assertTrue(safeIndex in 0..paragraphCount)
    }

    @Test
    fun allSourcesPreserveFreeAccessFlag() {
        for (source in BookSource.entries) {
            val freeBook = Book("test_${source.name.lowercase()}", "Başlık", "Yazar", source = source, freeAccess = true)
            val decoded = BookShelfStore.decode(BookShelfStore.encode(freeBook))
            assertTrue(decoded.freeAccess)
            assertEquals(source, decoded.source)
        }
    }

    @Test
    fun readerProgressSaveAndRestorePreservesParagraphNAndOffsetXWithoutDrift() {
        val paragraphCount = 50
        val targetParagraph = 14
        val targetOffset = 230

        // 1. Reader LazyColumn is at paragraph N + offset X.
        // Since LazyColumn item 0 is the Header, paragraph N is rendered at item (N + 1).
        val rawItemIndex = targetParagraph + 1
        val rawOffset = targetOffset

        // 2. Position is saved using ReaderPositionHelper
        val (savedParagraph, savedOffset) = ReaderPositionHelper.rawToParagraph(rawItemIndex, rawOffset)
        assertEquals("Persisted index must be actual paragraph index N, not raw item index", targetParagraph, savedParagraph)
        assertEquals("Persisted offset must match paragraph offset X", targetOffset, savedOffset)

        // 3. Reader state is recreated from persisted values and restored
        val (restoredRawIndex, restoredRawOffset) = ReaderPositionHelper.paragraphToRaw(savedParagraph, savedOffset, paragraphCount)
        assertEquals(rawItemIndex, restoredRawIndex)
        assertEquals(rawOffset, restoredRawOffset)

        // 4. Verify that multiple successive save/restore cycles DO NOT drift forward
        var currentRawIndex = rawItemIndex
        var currentRawOffset = rawOffset
        repeat(5) {
            val (savedP, savedO) = ReaderPositionHelper.rawToParagraph(currentRawIndex, currentRawOffset)
            val (restoredI, restoredO) = ReaderPositionHelper.paragraphToRaw(savedP, savedO, paragraphCount)
            assertEquals("Cycle $it: Paragraph index must not drift forward", rawItemIndex, restoredI)
            assertEquals("Cycle $it: Offset must remain identical", rawOffset, restoredO)
            currentRawIndex = restoredI
            currentRawOffset = restoredO
        }
    }

    @Test
    fun headerVisibleIndexZeroStateSavesSafelyWithoutOffsetLeakage() {
        val paragraphCount = 20
        val headerScrollOffset = 85

        // User is looking at the Header (item 0 in LazyColumn)
        val rawItemIndex = 0
        val rawOffset = headerScrollOffset

        // Saving must clamp paragraph to 0 and NOT leak header scroll offset into paragraph 0
        val (savedParagraph, savedOffset) = ReaderPositionHelper.rawToParagraph(rawItemIndex, rawOffset)
        assertEquals(0, savedParagraph)
        assertEquals("Header scroll offset must not be assigned to paragraph 0", 0, savedOffset)

        // Restoring places user at beginning of paragraph 0 with 0 offset
        val (restoredRawIndex, restoredRawOffset) = ReaderPositionHelper.paragraphToRaw(savedParagraph, savedOffset, paragraphCount)
        assertEquals(1, restoredRawIndex)
        assertEquals(0, restoredRawOffset)
    }
}
