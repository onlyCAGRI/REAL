package com.cagri.real.books

import org.junit.Assert.*
import org.junit.Test

class BookModelTest {
    @Test fun localFileAndOriginalPageMetadataSurvivePersistence() {
        val book = Book("local_123", "Deneme", "Yazar", category = "Dosyalarım", pageCount = 4,
            coverUrl = "/private/books/cover.png", fileName = "local_123.pdf", format = "pdf")
        assertEquals(book, BookShelfStore.decode(BookShelfStore.encode(book)))
    }
    @Test fun catalogEntryDoesNotInventFullBookContent() {
        val book = Book("gb_123", "Kitap", "Yazar", source = BookSource.GOOGLE_BOOKS,
            infoUrl = "https://books.google.com/books?id=123", description = "Açıklama")
        val decoded = BookShelfStore.decode(BookShelfStore.encode(book))
        assertNull(decoded.fileName)
        assertNull(decoded.contentUrl)
        assertEquals(book, decoded)
    }
    @Test fun paragraphBoundariesArePreservedWhileWrappedLinesJoin() {
        assertEquals(listOf("İlk satır devamı", "İkinci paragraf"),
            BookDocumentRepository.paragraphs("İlk satır\r\ndevamı\r\n\r\nİkinci paragraf"))
        assertTrue(BookDocumentRepository.paragraphs(" \n\n ").isEmpty())
    }
    @Test fun darkAndAmoledAreDifferentThemes() {
        assertEquals(androidx.compose.ui.graphics.Color.Black, ReaderTheme.AMOLED.background)
        assertNotEquals(ReaderTheme.AMOLED.background, ReaderTheme.DARK.background)
    }
    @Test fun headingsAreDetectedAndManualKindWins() {
        assertTrue(ParagraphKind.isHeading("Chapter 1"))
        assertTrue(ParagraphKind.isHeading("BİRİNCİ BÖLÜM"))
        assertTrue(ParagraphKind.isHeading("The Psychopathology of Everyday Things"))
        assertTrue(ParagraphKind.isHeading("1.2 Giriş"))
        assertFalse(ParagraphKind.isHeading("Bu normal bir cümle ve sonunda nokta var."))
        assertFalse(ParagraphKind.isHeading("affordances define what actions are possible"))
        assertFalse(ParagraphKind.isHeading(ParagraphKind.withKind("Chapter 1", false)))
        assertTrue(ParagraphKind.isHeading(ParagraphKind.withKind("normal satır", true)))
        assertEquals("Chapter 1", ParagraphKind.text(ParagraphKind.withKind("Chapter 1", true)))
    }
    @Test fun allBookSourcesSurvivePersistence() {
        for (source in BookSource.entries) {
            val book = Book("test_${source.name.lowercase()}", "Başlık", "Yazar", source = source)
            val decoded = BookShelfStore.decode(BookShelfStore.encode(book))
            assertEquals(source, decoded.source)
        }
    }
}
