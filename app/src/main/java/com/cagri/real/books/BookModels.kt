package com.cagri.real.books

import androidx.compose.ui.graphics.Color

enum class BookSource { LOCAL, ALL, GOOGLE_BOOKS, OPEN_LIBRARY, GUTENBERG, WIKISOURCE, OAPEN, DOAB, STANDARD_EBOOKS }

data class Book(
    val id: String,
    val title: String,
    val author: String,
    val category: String = "Genel",
    val pageCount: Int? = null,
    val coverUrl: String? = null,
    val source: BookSource = BookSource.LOCAL,
    val infoUrl: String? = null,
    val description: String? = null,
    val fileName: String? = null,
    val format: String? = null,
    val downloadable: Boolean = false,
    val accessCountry: String? = null,
    val contentUrl: String? = null,    // Validated full-file link, never a preview page
    val licenseUrl: String? = null,
    val rights: String? = null,
    val freeAccess: Boolean = false,
)

data class Shelf(
    val name: String,
    val books: List<Book>,
    val plankColor: Color,
)

/** Okuma sayfası temaları: AÇIK, yumuşak KOYU (saf siyah değil) ve AMOLED (saf siyah). */
enum class ReaderTheme(
    val label: String,
    val background: Color,
    val text: Color,
    val muted: Color,
    val chrome: Color,       // alttaki hap butonların rengi
    val chromeText: Color,
) {
    LIGHT("Açık", Color(0xFFEDEDEB), Color(0xFF111111), Color(0xFF8E8E93), Color(0xFF000000), Color.White),
    DARK("Koyu", Color(0xFF1C1C1E), Color(0xFFDADADC), Color(0xFF8E8E93), Color(0xFF3A3A3C), Color(0xFFF2F2F7)),
    AMOLED("AMOLED", Color(0xFF000000), Color(0xFFCFCFCF), Color(0xFF6E6E73), Color(0xFF1A1A1A), Color.White);

    fun next(): ReaderTheme = entries[(ordinal + 1) % entries.size]
}

object ReaderPositionHelper {
    /** Converts LazyColumn scroll position (where item 0 is the header) to persisted paragraph index and offset. */
    fun rawToParagraph(firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int): Pair<Int, Int> {
        val paragraphIndex = maxOf(0, firstVisibleItemIndex - 1)
        val offset = if (firstVisibleItemIndex == 0) 0 else maxOf(0, firstVisibleItemScrollOffset)
        return paragraphIndex to offset
    }

    /** Converts persisted paragraph index and offset to LazyColumn restore item index and offset. */
    fun paragraphToRaw(paragraphIndex: Int, offset: Int, paragraphCount: Int): Pair<Int, Int> {
        val targetIndex = (paragraphIndex + 1).coerceIn(0, maxOf(0, paragraphCount))
        val targetOffset = maxOf(0, offset)
        return targetIndex to targetOffset
    }
}
