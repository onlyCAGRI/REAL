package com.cagri.real.books

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.text.Text
import kotlin.math.tan

/**
 * OCR sonucundan sayfa düzenini çıkarır: paragraflar, paragraflar arası boşluk ve eğik (italik) yazı.
 *
 * Android'in PdfRenderer'ı yazı tipi / konum bilgisi vermediği için hem taranmış (OCR'lı) hem de metin
 * katmanlı PDF'lerde aynı yol kullanılır: sayfa görüntüsü okunur, kelimelerin konumu ve eğimi buradan çıkar.
 */
internal object PageLayout {
    private val analysisJob = ThreadLocal<kotlinx.coroutines.Job?>()
    fun <T> withCancellation(job: kotlinx.coroutines.Job?, block: () -> T): T {
        val previous = analysisJob.get()
        analysisJob.set(job)
        return try { block() } finally { analysisJob.set(previous) }
    }
    private fun checkActive() { analysisJob.get()?.let { job ->
        if (!job.isActive) throw kotlinx.coroutines.CancellationException("Page analysis cancelled")
    } }
    private class Word(val text: String, val box: Rect, var italic: Boolean?, val lineIdx: Int, val trustedBox: Boolean = true)
    private class Line(val words: List<Word>, val box: Rect, val block: Int, val forcedHeading: Boolean = false) {
        val text get() = words.joinToString(" ") { it.text }
    }

    fun paragraphs(bitmap: Bitmap, result: Text, headers: List<PdfRunningHeaders.Snapshot> = emptyList(),
                   preserved: List<NativeRun> = emptyList()): List<String> {
        val lines = ArrayList<Line>()
        result.textBlocks.forEachIndexed { b, block ->
            for (l in block.lines) {
                val lb = l.boundingBox ?: continue
                val idx = lines.size
                val words = l.elements.mapNotNull { e -> e.boundingBox?.let { Word(e.text, it, null, idx) } }
                if (words.isEmpty() || l.text.isBlank()) continue
                lines.add(Line(words, lb, b))
            }
        }
        // Recover a short native chapter title when OCR omitted it. Never replace recognised body text.
        for (run in preserved) {
            val overlapping = lines.filter { Rect.intersects(it.box, run.box) }
            if (overlapping.any { it.text.length > 32 || it.box.height() > run.box.height() * 1.8f }) continue
            lines.removeAll(overlapping.toSet())
            val idx = lines.size
            lines.add(Line(listOf(Word(run.text, run.box, null, idx)), run.box, -1, forcedHeading = true))
        }
        return layout(bitmap, lines, headers = headers)
    }

    /** Metin katmanından alınan bir parça (satır ya da blok) ve sayfa görüntüsündeki (piksel) kutusu. */
    class NativeRun(val text: String, val box: Rect)

    /**
     * Metin katmanlı PDF: METİN dosyadan olduğu gibi gelir (OCR yok); konum bilgisi yalnız paragraf aralıklarını
     * ve eğik yazıyı çıkarmak için kullanılır, böylece okuyucudaki görünüm OCR'lı sayfayla aynı kalır.
     * Parçalar satır satırsa kelime kutuları satır genişliğine orantılanır ve eğik yazı aranır;
     * parçalar zaten paragraf bloklarıysa yalnızca boşluklar çıkarılır.
     */
    fun paragraphsFromNative(bitmap: Bitmap, runs: List<NativeRun>, headers: List<PdfRunningHeaders.Snapshot> = emptyList()): List<String> {
        val usable = normalizedRuns(runs)
        if (usable.isEmpty()) return emptyList()
        val blockMode = usable.count { it.text.trim().contains('\n') } * 2 > usable.size
        val lineHeights = usable.map { r ->
            r.box.height() / (if (blockMode) r.text.trim().count { it == '\n' } + 1 else 1)
        }.sorted()
        val medH = lineHeights[lineHeights.size / 2].coerceAtLeast(8)
        val lines = ArrayList<Line>()
        usable.forEachIndexed { b, run ->
            val tokens = run.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return@forEachIndexed
            val idx = lines.size
            val box = run.box
            val total = tokens.sumOf { it.length + 1 }.toFloat()
            var x = box.left.toFloat()
            val inkBoxes = nativeWordBoxes(bitmap, box, tokens.size)
            val words = tokens.mapIndexed { tokenIndex, t ->
                val w = box.width() * ((t.length + 1) / total)
                val left = x.toInt()
                val r = Rect(left, box.top, (x + w).toInt().coerceAtLeast(left + 1), box.bottom)
                x += w
                Word(t, inkBoxes?.get(tokenIndex) ?: r, null, idx, trustedBox = inkBoxes != null)
            }
            // Satır modunda tüm parçalar tek blok sayılır: satır arası boşluk paragraf sınırı olmasın.
            lines.add(Line(words, box, if (blockMode) b else 0))
        }
        return layout(bitmap, lines, medHOverride = medH, italics = !blockMode, blocksAreParagraphs = blockMode, headers = headers)
    }

    /**
     * Bazı PDF'lerde her karakter (ya da kelime) ayrı metin nesnesidir ve dosyada gerçek boşluk yoktur; boşluklar ve
     * satır sonları yalnız konumdan anlaşılır. Parçalar bu kadar küçükse (ortanca uzunluk <= 8) önce aynı yatay hizadakileri
     * satırlara toplar, kelime boşluğunu yatay aralıktan çıkarır. Satır ya da blok olarak gelen parçalara dokunmaz.
     * Eşikler gerçek bir PDF'te pdfium'un kendi çıktısına göre ayarlandı: harf arası aralık en çok ~0,19 em, kelime arası
     * en az ~0,19 em ve çoğunlukla çok daha fazla; 0,33 x (ascender'lı harf yüksekliği) en az hata veriyor.
     */
    private fun regroupFragments(usable: List<NativeRun>): List<NativeRun> {
        if (usable.size < 8) return usable
        val medLen = usable.map { it.text.trim().length }.sorted().let { it[it.size / 2] }
        if (medLen > 8) return usable
        fun cy(r: NativeRun) = (r.box.top + r.box.bottom) / 2f
        val medH = usable.map { it.box.height() }.sorted().let { it[it.size / 2] }.coerceAtLeast(4)
        val byY = usable.sortedBy { cy(it) }
        val rows = ArrayList<MutableList<NativeRun>>()
        rows.add(mutableListOf(byY[0]))
        for (i in 1 until byY.size) {
            // Aynı satırın karakterlerinin orta noktaları birbirine yakındır; satırlar arası sıçrama belirgindir.
            if (cy(byY[i]) - cy(byY[i - 1]) > 0.55f * medH) rows.add(mutableListOf(byY[i])) else rows.last().add(byY[i])
        }
        return rows.map { row ->
            val its = row.sortedBy { it.box.left }
            val hs = its.map { it.box.height() }.sorted()
            val fs = hs[(hs.size * 3) / 4].coerceAtLeast(4).toFloat()     // yazı boyutu vekili: ascender'lı harflerin yüksekliği
            val gaps = (0 until its.size - 1).map { its[it + 1].box.left - its[it].box.right }
            // Harf aralıklı başlık ("İ K İ"): hepsi tek karakter, aralıklar birbirine yakın ve belirgin → tek kelime.
            val letterSpaced = its.size in 2..8 && its.all { it.text.trim().length == 1 } &&
                (gaps.minOrNull() ?: 0) > 0.25f * fs && (gaps.maxOrNull() ?: 0) <= 1.7f * (gaps.minOrNull() ?: 1)
            val sb = StringBuilder(its[0].text.trim())
            for (i in 1 until its.size) {
                if (!letterSpaced && gaps[i - 1] > 0.33f * fs) sb.append(' ')
                sb.append(its[i].text.trim())
            }
            NativeRun(sb.toString(), Rect(its.minOf { it.box.left }, its.minOf { it.box.top },
                its.maxOf { it.box.right }, its.maxOf { it.box.bottom }))
        }
    }

    fun normalizedRuns(runs: List<NativeRun>): List<NativeRun> =
        regroupFragments(runs.filter { it.text.isNotBlank() && !it.box.isEmpty })

    fun headerSnapshot(page: Int, runs: List<NativeRun>, width: Int, height: Int): PdfRunningHeaders.Snapshot =
        PdfRunningHeaders.snapshot(page, normalizedRuns(runs).map {
            PdfParagraphLayout.Line(it.text, it.box.left, it.box.top, it.box.right, it.box.bottom, 0)
        }, width, height)

    fun ocrRuns(result: Text): List<NativeRun> = result.textBlocks.flatMap { block ->
        block.lines.mapNotNull { line -> line.boundingBox?.let { NativeRun(line.text, it) } }
    }

    private fun layout(bitmap: Bitmap, allLines: List<Line>, medHOverride: Int? = null, italics: Boolean = true,
                       blocksAreParagraphs: Boolean = false, headers: List<PdfRunningHeaders.Snapshot> = emptyList()): List<String> {
        if (allLines.isEmpty()) return emptyList()
        val lines = dropPageNumbers(bitmap, allLines.sortedWith(compareBy<Line> { it.box.top }.thenBy { it.box.left }), medHOverride, headers)

        if (italics) detectItalics(bitmap, lines)

        val bodyHeight = (medHOverride ?: lines.map { it.box.height() }.sorted().let { it[it.size / 2] }).toFloat()
        // Unconfirmed edge candidates remain ordinary body paragraphs. Confirmed repeats
        // were already removed by dropPageNumbers; position alone is not evidence.
        val furniture = emptySet<Int>()
        val rawGeometry = lines.map { line ->
            PdfParagraphLayout.Line(if (line.forcedHeading) ParagraphKind.HEADING + line.text else line.text,
                line.box.left, line.box.top, line.box.right, line.box.bottom, line.block)
        }
        val italicShares = lines.map { line ->
            val letters = line.words.sumOf { it.text.count { c -> c.isLetter() } }.coerceAtLeast(1)
            line.words.filter { it.italic == true }.sumOf { it.text.count { c -> c.isLetter() } }.toFloat() / letters
        }
        val attributions = PdfQuoteLayout.attributions(rawGeometry, italicShares) - furniture
        val geometry = rawGeometry.mapIndexed { i, line ->
            if (i in furniture || i in attributions) line.copy(text = ParagraphKind.BODY + line.text) else line
        }
        return PdfParagraphLayout.groups(geometry, medHOverride, blocksAreParagraphs).map { group ->
            val rendered = render(lines.subList(group.from, group.until).flatMap { it.words })
            // Persist both positive AND negative decisions; UI must not reclassify body/author text.
            val paragraph = ParagraphKind.withKind(ParagraphKind.withGap(rendered, group.gap), group.heading)
            when {
                group.from in furniture -> ParagraphKind.withFurniture(paragraph)
                group.from in attributions -> ParagraphKind.withAttribution(paragraph)
                else -> paragraph
            }
        }.filter { ParagraphKind.plain(it).isNotBlank() }
    }

    // ---- Sayfa numarası algısı --------------------------------------------------------------------

    /** No position-only deletion: a candidate must recur on two independently indexed pages. */
    private fun dropPageNumbers(bitmap: Bitmap, lines: List<Line>, medHOverride: Int?,
                                headers: List<PdfRunningHeaders.Snapshot>): List<Line> {
        if (lines.size < 2 || headers.isEmpty()) return lines
        val heights = lines.map { it.box.height() }.sorted()
        val bodyHeight = (medHOverride ?: heights[heights.size / 2]).coerceAtLeast(1).toFloat()
        val kept = lines.filterNot { line ->
            val b = line.box
            PdfRunningHeaders.repeated(PdfRunningHeaders.mark(
                PdfParagraphLayout.Line(line.text, b.left, b.top, b.right, b.bottom, line.block),
                bitmap.width, bitmap.height, bodyHeight), headers)
        }
        return kept.ifEmpty { lines }
    }

    private const val PAGE_NUM_TRIM = "-–—‒−·•*[]()"
    private val PagePrefixRe = Regex("^(?:sayfa|sf|page|pg|s|p)\\.?\\s*(?=\\d)", RegexOption.IGNORE_CASE)
    private val PageDigitsRe = Regex("^(\\d{1,4})(?:\\s*[/|]\\s*\\d{1,4})?$")
    private val RomanRe = Regex("^m{0,3}(cm|cd|d?c{0,3})(xc|xl|l?x{0,3})(ix|iv|v?i{0,3})$")
    // Geçerli romen rakamı olup sıradan kelime de olanlar ("mi" soru eki gibi) numara sayılmaz.
    private val RomanStop = setOf("mi", "di", "li", "ci", "mix", "mil", "dim")

    /** Satırın başında ya da sonunda ayrı bir sayfa numarası belirteci var mı ("Yazar - Kitap 10", "10 Yazar - Kitap")? */
    internal fun hasEdgePageNumber(raw: String): Boolean {
        val t = raw.trim()
        if (t.length < 4 || t.length > 90) return false
        val parts = t.split(Regex("\\s+"))
        if (parts.size < 2) return false
        fun num(x: String) = x.length in 1..4 && x.all { it.isDigit() }
        val rest = when {
            num(parts.last()) -> parts.dropLast(1)
            num(parts.first()) -> parts.drop(1)
            else -> return false
        }
        return rest.any { r -> r.any { it.isLetter() } }
    }

    private val HeaderSeparatorRe = Regex("\\s[-–—|·•]\\s")
    /** Ayraçlı üst bilgi: "Yazar - Kitap 10" gibi, başta/sonda numara taşıyan kısa metin (boyut bilgisi yokken kullanılır). */
    internal fun isSeparatedHeader(raw: String): Boolean = hasEdgePageNumber(raw) && HeaderSeparatorRe.containsMatchIn(raw.trim())

    /** "47", "- 47 -", "Sayfa 47", "s. 47", "47 / 300", "xiv", "XIV" gibi yalnız sayfa numarası olan metinler. */
    internal fun isPageNumberText(raw: String): Boolean {
        val t = raw.trim { it.isWhitespace() || it in PAGE_NUM_TRIM }
        if (t.isEmpty() || t.length > 18) return false
        val body = PagePrefixRe.replace(t, "").trim()
        PageDigitsRe.matchEntire(body)?.let { m ->
            val first = m.groupValues[1]
            // 1800-2099 aralığındaki dört haneli sayı büyük olasılıkla bir yıldır.
            return !(first.length == 4 && first.toInt() in 1800..2099)
        }
        if (body.length > 7 || body.any { !it.isLetter() }) return false
        val lower = body.lowercase()
        if (body != lower && body != body.uppercase()) return false
        return lower !in RomanStop && RomanRe.matches(lower)
    }

    /**
     * Legacy text has no geometry or cross-page evidence. Keep it rather than dropping a chapter number.
     */
    internal fun stripEdgeNumbers(paras: List<String>): List<String> = paras

    /** Kelimeleri birleştirir: satır sonu tirelerini kaldırır, eğik aralıkları işaretler. */
    private fun render(words: List<Word>): String {
        val sb = StringBuilder()
        var italic = false
        words.forEachIndexed { i, w ->
            val t = w.text
            val prev = if (i > 0) words[i - 1] else null
            val joinPrev = prev != null && prev.lineIdx != w.lineIdx && sb.endsWith("-") &&
                t.firstOrNull()?.isLowerCase() == true && sb.length >= 2 && sb[sb.length - 2].isLetter()
            if (joinPrev) {
                // "kitap-\nçı" -> "kitapçı"; işaretler tireden önce kapanmışsa onları da koru.
                sb.setLength(sb.length - 1)
            } else if (i > 0) sb.append(' ')
            val want = w.italic == true
            if (want && !italic) { sb.append(ParagraphKind.ITALIC_ON); italic = true }
            else if (!want && italic) {
                // Boşluğun eğik içinde kalmaması için işareti boşluktan önce kapat.
                if (sb.isNotEmpty() && sb.last() == ' ') { sb.setLength(sb.length - 1); sb.append(ParagraphKind.ITALIC_OFF).append(' ') }
                else sb.append(ParagraphKind.ITALIC_OFF)
                italic = false
            }
            sb.append(t)
        }
        if (italic) sb.append(ParagraphKind.ITALIC_OFF)
        return sb.toString().trim()
    }

    /** Use measured ink gaps when they match the known native token count; otherwise retain uncertainty. */
    private fun nativeWordBoxes(bitmap: Bitmap, box: Rect, count: Int): List<Rect>? {
        if (count == 1) return listOf(box)
        val x0 = box.left.coerceIn(0, bitmap.width - 1); val x1 = box.right.coerceIn(x0 + 1, bitmap.width)
        val y0 = box.top.coerceIn(0, bitmap.height - 1); val y1 = box.bottom.coerceIn(y0 + 1, bitmap.height)
        val width = x1 - x0; val height = y1 - y0
        if (width * height > 400_000) return null
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, x0, y0, width, height)
        val occupied = BooleanArray(width)
        for (y in 0 until height) {
            checkActive()
            for (x in 0 until width) {
            val c = pixels[y * width + x]
            val lum = (((c shr 16) and 255) * 3 + ((c shr 8) and 255) * 6 + (c and 255)) / 10
            if (lum < 180) occupied[x] = true
            }
        }
        val gaps = mutableListOf<Pair<Int, Int>>()
        var x = 0
        while (x < width) {
            if (occupied[x]) { x++; continue }
            val start = x
            while (x < width && !occupied[x]) x++
            if (start > 0 && x < width) gaps.add(start to x)
        }
        // Largest separators must plausibly be spaces, and not merely gaps between glyph strokes.
        val selected = gaps.filter { it.second - it.first >= height * .14f }
            .sortedByDescending { it.second - it.first }.take(count - 1).sortedBy { it.first }
        if (selected.size != count - 1) return null
        val smallest = selected.minOf { it.second - it.first }
        val otherLargest = gaps.filter { it !in selected }.maxOfOrNull { it.second - it.first } ?: 0
        if (smallest < otherLargest * 1.25f) return null
        val boxes = mutableListOf<Rect>()
        var from = 0
        for (gap in selected) { boxes.add(Rect(x0 + from, y0, x0 + gap.first, y1)); from = gap.second }
        boxes.add(Rect(x0 + from, y0, x1, y1))
        return boxes.takeIf { it.all { b -> !b.isEmpty } }
    }

    // ---- Eğik yazı algısı -------------------------------------------------------------------------

    private val Angles = intArrayOf(-4, 0, 4, 8, 12, 16, 20, 24)
    private val Tans = FloatArray(Angles.size) { tan(Math.toRadians(Angles[it].toDouble())).toFloat() }

    /** Kelime başına en iyi eğim açısı (derece) ve düz duruşa göre kazanç; belirsizse null. */
    private class Slant(val angle: Int, val gain: Float)

    private fun detectItalics(bitmap: Bitmap, lines: List<Line>) {
        val slants = HashMap<Word, Slant>()
        for (line in lines) for (w in line.words) {
            checkActive()
            if (!w.trustedBox || w.text.count { it.isLetter() } < 3) continue
            slant(bitmap, w.box)?.let { slants[w] = it }
        }
        val lineSlants = lines.filter { line -> line.words.any { !it.trustedBox } ||
            line.words.sumOf { it.text.count { c -> c.isLetter() } } >= 8 }
            .mapNotNull { line -> slant(bitmap, line.box)?.let { line to it } }.toMap()
        val evidence = slants.values.toList() + lineSlants.values
        if (evidence.isEmpty()) return
        // Clamp baseline so a predominantly italic quote page is not mistaken for upright text.
        val base = evidence.map { it.angle }.sorted().let { it[it.size / 2] }.coerceIn(-4, 4)
        for ((w, s) in slants) w.italic = (s.angle - base >= 8) && s.gain >= 1.05f
        for (line in lines) {
            val measured = lineSlants[line] ?: continue
            val italicLine = measured.angle - base >= 8 && measured.gain >= 1.08f
            for (w in line.words) if (!w.trustedBox) w.italic = italicLine
            // Complete slanted lines also cover short words and inaccurate token boundaries.
            if (italicLine && line.words.count { it.italic == true } >= line.words.size * .6f)
                line.words.forEach { it.italic = true }
        }
        // Kısa / belirsiz kelimeler komşularından miras alır: yalnız iki yanı da eğikse eğik olur.
        for (line in lines) {
            val ws = line.words
            for (i in ws.indices) if (ws[i].italic == null) {
                val before = (i - 1 downTo 0).firstNotNullOfOrNull { ws[it].italic }
                val after = (i + 1 until ws.size).firstNotNullOfOrNull { ws[it].italic }
                ws[i].italic = before == true && after == true
            }
        }
        // Tek başına kalmış kısa eğik kelime çoğu zaman gürültüdür; yalnız uzun kelimeler tek başına eğik kalabilir.
        for (line in lines) {
            val ws = line.words
            for (i in ws.indices) if (ws[i].italic == true) {
                val l = ws.getOrNull(i - 1)?.italic == true; val r = ws.getOrNull(i + 1)?.italic == true
                if (!l && !r && ws[i].text.count { it.isLetter() } < 5) ws[i].italic = false
            }
        }
        // Satırın büyük kısmı eğikse satırın tamamı eğik say.
        for (line in lines) {
            val letters = line.words.sumOf { w -> w.text.count { it.isLetter() } }.coerceAtLeast(1)
            val italicLetters = line.words.filter { it.italic == true }.sumOf { w -> w.text.count { it.isLetter() } }
            if (italicLetters.toFloat() / letters > 0.7f) line.words.forEach { it.italic = true }
        }
    }

    /**
     * Kelime kutusunu ikili görüntüye çevirir, farklı açılarda "düzeltip" dikey çizgilerin ne kadar hizalandığına bakar.
     * Eğik harfler doğru açıyla düzeltilince sütun histogramı keskinleşir.
     */
    private fun slant(bitmap: Bitmap, box: Rect): Slant? {
        checkActive()
        val padY = (box.height() * 0.05f).toInt()
        val x0 = box.left.coerceIn(0, bitmap.width - 1); val x1 = box.right.coerceIn(x0 + 1, bitmap.width)
        val y0 = (box.top - padY).coerceIn(0, bitmap.height - 1); val y1 = (box.bottom + padY).coerceIn(y0 + 1, bitmap.height)
        val w = x1 - x0; val h = y1 - y0
        if (w < 12 || h < 12 || w * h > 400_000) return null
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, x0, y0, w, h)
        val lum = IntArray(w * h) { val c = px[it]; (((c shr 16) and 255) * 3 + ((c shr 8) and 255) * 6 + (c and 255)) / 10 }
        // Otsu eşiği
        val hist = IntArray(256); for (v in lum) hist[v]++
        val total = lum.size; var sumAll = 0L; for (i in 0 until 256) sumAll += i.toLong() * hist[i]
        var wB = 0; var sumB = 0L; var best = 0.0; var thr = 128
        for (t in 0 until 256) {
            wB += hist[t]; if (wB == 0) continue
            val wF = total - wB; if (wF == 0) break
            sumB += t.toLong() * hist[t]
            val mB = sumB.toDouble() / wB; val mF = (sumAll - sumB).toDouble() / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > best) { best = between; thr = t }
        }
        var dark = 0; for (v in lum) if (v <= thr) dark++
        val inkIsDark = dark <= total / 2                      // az olan taraf mürekkep (koyu zemin de desteklenir)
        val ink = BooleanArray(total) { if (inkIsDark) lum[it] <= thr else lum[it] > thr }
        val inkCount = ink.count { it }
        if (inkCount < 40) return null
        val shift = (h * Tans.last()).toInt() + 2              // en fazla kaydırma
        val cols = IntArray(w + shift * 2 + 2)
        val cy = h / 2f
        var bestIdx = 0; var bestScore = -1L; var zeroScore = 1L
        for (a in Angles.indices) {
            checkActive()
            java.util.Arrays.fill(cols, 0)
            val t = Tans[a]
            for (y in 0 until h) {
                if (y % 16 == 0) checkActive()
                val dx = ((cy - y) * t).toInt()                 // yukarıdaki noktalar sağa kaymış: geri çek
                val row = y * w
                for (x in 0 until w) if (ink[row + x]) cols[x - dx + shift]++
            }
            var score = 0L; for (c in cols) score += c.toLong() * c
            if (Angles[a] == 0) zeroScore = score.coerceAtLeast(1)
            if (score > bestScore) { bestScore = score; bestIdx = a }
        }
        return Slant(Angles[bestIdx], bestScore.toFloat() / zeroScore)
    }
}
