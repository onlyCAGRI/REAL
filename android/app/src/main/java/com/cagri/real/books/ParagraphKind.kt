package com.cagri.real.books

import com.cagri.real.i18n.t

/**
 * PDF / OCR paragraflarında başlık algısı. Önbellek formatı değişmez (JSON string listesi):
 * elle verilen karar paragrafın başına görünmez bir işaret karakteri koyarak saklanır.
 * İşaret yoksa metne bakarak tahmin edilir; yanlış tahmin uzun basılıp düzeltilir.
 */
object ParagraphKind {
    const val HEADING = '\u0001'
    const val BODY = '\u0002'
    /** Satır içi eğik yazı: ITALIC_ON ... ITALIC_OFF arası eğik çizilir. */
    const val ITALIC_ON = '\u0003'
    const val ITALIC_OFF = '\u0004'
    /** Orijinal sayfada bu paragrafın üstünde belirgin boşluk vardı (1 = orta, 2 = büyük). */
    const val GAP1 = '\u0005'
    const val GAP2 = '\u0006'
    const val ATTRIBUTION = '\u0007'
    const val FURNITURE = '\u0008'

    private fun isFlag(c: Char) = c == HEADING || c == BODY || c == GAP1 || c == GAP2 || c == ATTRIBUTION || c == FURNITURE

    private val keyword = Regex(
        "^(bölüm|kısım|bab|chapter|part|section|book|kitap|cilt|volume)\\s+([0-9]+|[ivxlcdm]+|bir|iki|üç|dört|beş|altı|yedi|sekiz|dokuz|on)\\b.*$",
        RegexOption.IGNORE_CASE)
    private val numbered = Regex("^([0-9]+\\.|[0-9]+(\\.[0-9]+)+\\.?)\\s+\\p{L}.*$")
    private val single = setOf("giriş", "önsöz", "sonuç", "sonsöz", "içindekiler", "teşekkür", "kaynakça", "ön söz",
        "introduction", "preface", "contents", "conclusion", "epilogue", "prologue", "foreword", "acknowledgments",
        "bibliography", "index", "appendix")

    /** Baştaki tüm işaretleri (başlık/gövde/boşluk) atar; eğik işaretleri korur. */
    fun text(raw: String): String {
        var i = 0
        while (i < raw.length && isFlag(raw[i])) i++
        return raw.substring(i)
    }

    /** Hiçbir işaret kalmaz: yalnız okunacak düz metin (analiz ve arama için). */
    fun plain(raw: String): String = text(raw).filter { it != ITALIC_ON && it != ITALIC_OFF && it != BookInline.BOLD_ON && it != BookInline.BOLD_OFF }

    /** Başlık/gövde kararını değiştirir; boşluk işaretini korur. */
    fun withKind(raw: String, heading: Boolean): String =
        (if (heading) HEADING else BODY).toString() +
            (if (isAttribution(raw)) ATTRIBUTION.toString() else "") +
            (if (isFurniture(raw)) FURNITURE.toString() else "") + gapChar(raw) + text(raw)

    fun isAttribution(raw: String): Boolean = raw.takeWhile { isFlag(it) }.contains(ATTRIBUTION)
    fun isFurniture(raw: String): Boolean = raw.takeWhile { isFlag(it) }.contains(FURNITURE)
    fun withAttribution(raw: String): String = BODY.toString() + ATTRIBUTION +
        (if (gapLevel(raw) >= 2) GAP2 else GAP1) + text(raw)
    fun withFurniture(raw: String): String = BODY.toString() + FURNITURE + gapChar(raw) + text(raw)

    private fun gapChar(raw: String): String {
        var i = 0
        while (i < raw.length && isFlag(raw[i])) { if (raw[i] == GAP1 || raw[i] == GAP2) return raw[i].toString(); i++ }
        return ""
    }

    /** 0 = boşluk yok, 1 = orta, 2 = büyük. */
    fun gapLevel(raw: String): Int = when (gapChar(raw)) { GAP1.toString() -> 1; GAP2.toString() -> 2; else -> 0 }

    /** Sayfa analizinin ürettiği paragraf: boşluk işareti + (varsa) satır içi eğik işaretleri. */
    fun withGap(raw: String, level: Int): String =
        (when (level) { 1 -> GAP1.toString(); 2 -> GAP2.toString(); else -> "" }) + raw

    /** İşaretli metni düz metne ve eğik aralıklarına ayırır. */
    fun parseItalic(marked: String): Pair<String, List<IntRange>> {
        val sb = StringBuilder(); val ranges = mutableListOf<IntRange>(); var start = -1
        for (c in marked) when (c) {
            ITALIC_ON -> if (start < 0) start = sb.length
            ITALIC_OFF -> { if (start in 0 until sb.length) ranges.add(start until sb.length); start = -1 }
            else -> sb.append(c)
        }
        if (start in 0 until sb.length) ranges.add(start until sb.length)
        return sb.toString() to ranges
    }

    fun isHeading(raw: String): Boolean {
        var i = 0
        while (i < raw.length && isFlag(raw[i])) {
            if (raw[i] == HEADING) return true
            if (raw[i] == BODY) return false
            i++
        }
        val body = plain(raw)
        return body.isNotEmpty() && auto(body)
    }

    /** Only the first letter, after opening quotation marks/whitespace, receives the initial style. */
    internal fun initialRange(paragraph: String): IntRange? {
        var index = 0
        while (index < paragraph.length) {
            val codePoint = Character.codePointAt(paragraph, index)
            if (Character.isLetter(codePoint)) return index until index + Character.charCount(codePoint)
            if (!Character.isWhitespace(codePoint) && codePoint !in listOf(34, 39, 171, 187, 8216, 8217, 8220, 8221)) return null
            index += Character.charCount(codePoint)
        }
        return null
    }

    /** Empty paragraphs do not consume the first body paragraph following a heading. */
    internal fun sectionInitials(paragraphs: List<String>, followsHeading: Boolean = false): Set<Int> {
        var pending = followsHeading
        val indices = mutableSetOf<Int>()
        paragraphs.forEachIndexed { index, raw ->
            if (text(raw).isNotBlank() && !isFurniture(raw) && !BookRichContent.isBlock(raw)) {
                if (isHeading(raw)) pending = true
                else {
                    if (pending && !isAttribution(raw)) indices.add(index)
                    pending = false
                }
            }
        }
        return indices
    }

    internal fun auto(raw: String): Boolean {
        val t = raw.trim()
        if (t.length < 2 || t.length > 70 || t.contains('\n')) return false
        if (t.lowercase() in single || keyword.matches(t)) return true
        val last = t.last()
        if (last == ',' || last == ';' || last == ':' || last == '…') return false
        if (numbered.matches(t)) return last != '.'
        val words = t.split(Regex("\\s+"))
        val letters = t.filter { it.isLetter() }
        if (letters.length >= 4 && letters.all { it.isUpperCase() } && words.size <= 10) return true
        if (last == '.' || last == '?' || last == '!') return false
        // Başlık Biçimi: "The Psychopathology of Everyday Things"
        return words.size in 2..8 && t.length <= 60 && words.first().first().isUpperCase() &&
            words.all { w -> w.length <= 3 || w.first().isUpperCase() || !w.first().isLetter() }
    }
}
