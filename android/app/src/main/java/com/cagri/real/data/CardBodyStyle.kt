package com.cagri.real.data

import org.json.JSONArray
import org.json.JSONObject

/** Marks of one group exclude each other on a character (one size, one colour); STYLE marks combine. */
enum class BodyMarkGroup { STYLE, SIZE, COLOR }

/**
 * Styles a card stack body can carry. [key] is the stored form; keep it stable. SIZE marks are
 * paragraph formats (they always cover whole paragraphs); "Gövde" and the default colour are no mark.
 */
enum class BodyMark(val key: String, val group: BodyMarkGroup = BodyMarkGroup.STYLE) {
    BOLD("b"), ITALIC("i"), UNDERLINE("u"), STRIKE("s"),
    SMALL("sm", BodyMarkGroup.SIZE), LARGE("lg", BodyMarkGroup.SIZE), TITLE("h", BodyMarkGroup.SIZE),
    ORANGE("co", BodyMarkGroup.COLOR), YELLOW("cy", BodyMarkGroup.COLOR), GREEN("cg", BodyMarkGroup.COLOR),
    BLUE("cb", BodyMarkGroup.COLOR), PINK("cp", BodyMarkGroup.COLOR);

    companion object {
        fun of(key: String?): BodyMark? = entries.firstOrNull { it.key == key }
        fun of(group: BodyMarkGroup): List<BodyMark> = entries.filter { it.group == group }
    }
}

enum class BodySize(val key: String) { SMALL("s"), MEDIUM("m"), LARGE("l") }
enum class BodyGap(val key: String) { NONE("0"), SMALL("1"), LARGE("2") }

/** Writing settings of a whole card stack (set from its ⚙ panel, copied onto every card of it). */
data class BodyFormat(val size: BodySize = BodySize.MEDIUM, val indent: Boolean = false, val gap: BodyGap = BodyGap.NONE) {
    val isDefault: Boolean get() = this == BodyFormat()

    companion object {
        fun encode(format: BodyFormat): JSONObject? = if (format.isDefault) null else JSONObject().apply {
            put("size", format.size.key); put("indent", format.indent); put("gap", format.gap.key)
        }

        fun decode(obj: JSONObject?): BodyFormat = if (obj == null) BodyFormat() else BodyFormat(
            size = BodySize.entries.firstOrNull { it.key == obj.optString("size") } ?: BodySize.MEDIUM,
            indent = obj.optBoolean("indent", false),
            gap = BodyGap.entries.firstOrNull { it.key == obj.optString("gap") } ?: BodyGap.NONE
        )
    }
}

/** [start] inclusive, [end] exclusive, in UTF-16 offsets of the plain body. */
data class BodySpan(val start: Int, val end: Int, val mark: BodyMark)

/**
 * The body stays plain text; styles ride next to it as ranges. Every edit goes through [applyEdit] so
 * the ranges move with the text, and auto-flow cuts them with [slice].
 */
object BodySpans {
    /** Clamped to [length], empty ranges dropped, overlapping or touching ranges of one mark merged. */
    fun normalize(spans: List<BodySpan>, length: Int): List<BodySpan> =
        spans.groupBy { it.mark }.flatMap { (mark, list) ->
            val merged = mutableListOf<BodySpan>()
            list.map { BodySpan(it.start.coerceIn(0, length), it.end.coerceIn(0, length), mark) }
                .filter { it.end > it.start }.sortedBy { it.start }
                .forEach { span ->
                    val last = merged.lastOrNull()
                    if (last != null && span.start <= last.end) merged[merged.lastIndex] = last.copy(end = maxOf(last.end, span.end))
                    else merged += span
                }
            merged
        }.sortedWith(compareBy({ it.start }, { it.mark.ordinal }))

    /** True when every character of [start, end) carries [mark]. */
    fun covers(spans: List<BodySpan>, mark: BodyMark, start: Int, end: Int): Boolean {
        if (end <= start) return false
        var at = start
        for (s in spans.filter { it.mark == mark }.sortedBy { it.start }) {
            if (s.start > at) break
            if (s.end > at) at = s.end
            if (at >= end) return true
        }
        return false
    }

    /** Marks new text typed at [position] continues: a span reaching up to the caret keeps going. */
    fun marksAt(spans: List<BodySpan>, position: Int): Set<BodyMark> =
        spans.filter { it.start < position && position <= it.end }.mapTo(LinkedHashSet()) { it.mark }

    fun add(spans: List<BodySpan>, mark: BodyMark, start: Int, end: Int, length: Int): List<BodySpan> =
        normalize(spans + BodySpan(start, end, mark), length)

    fun remove(spans: List<BodySpan>, mark: BodyMark, start: Int, end: Int): List<BodySpan> =
        spans.flatMap { s ->
            if (s.mark != mark || s.end <= start || s.start >= end) listOf(s)
            else listOfNotNull(
                BodySpan(s.start, start, mark).takeIf { start > s.start },
                BodySpan(end, s.end, mark).takeIf { s.end > end }
            )
        }

    /**
     * Selection fully styled: the style comes off; otherwise the whole selection gets it (replacing
     * another size or colour there).
     */
    fun toggle(spans: List<BodySpan>, mark: BodyMark, start: Int, end: Int, length: Int): List<BodySpan> =
        if (covers(spans, mark, start, end)) normalize(remove(spans, mark, start, end), length)
        else add(clearGroup(spans, mark.group, start, end), mark, start, end, length)

    /** Back to "Gövde" / default colour in [start, end). STYLE has no default and is left alone. */
    fun clearGroup(spans: List<BodySpan>, group: BodyMarkGroup, start: Int, end: Int): List<BodySpan> =
        if (group == BodyMarkGroup.STYLE) spans
        else BodyMark.of(group).fold(spans) { acc, m -> remove(acc, m, start, end) }

    /** The whole paragraphs [start, end) touches, each with its line break. */
    fun paragraphRange(text: String, start: Int, end: Int): IntRange {
        val from = text.lastIndexOf('\n', (start - 1).coerceAtLeast(-1)).let { if (start == 0) 0 else it + 1 }
        val lastChar = (maxOf(end, start + 1) - 1).coerceAtMost(text.length)
        val nl = if (lastChar >= text.length) -1 else text.indexOf('\n', lastChar)
        val to = if (nl < 0) text.length else nl + 1
        return from until maxOf(from, to)
    }

    /** Marks for the next typed letter at the caret: [pending] wins, else what the caret continues. */
    fun typingMarks(spans: List<BodySpan>, position: Int, pending: Set<BodyMark>?): Set<BodyMark> =
        pending ?: marksAt(spans, position)

    /** [current] with [mark] switched: on → the rest of its group goes off. */
    fun switchMark(current: Set<BodyMark>, mark: BodyMark): Set<BodyMark> =
        if (mark in current) current - mark
        else if (mark.group == BodyMarkGroup.STYLE) current + mark
        else current.filterTo(LinkedHashSet()) { it.group != mark.group } + mark

    /**
     * Moves [spans] from [old] to [new]. The changed region is found by common prefix / suffix; deleted
     * text takes its styles with it, inserted text gets [typingMarks] (null = continue the marks at the caret).
     */
    fun applyEdit(spans: List<BodySpan>, old: String, new: String, typingMarks: Set<BodyMark>? = null): List<BodySpan> {
        if (old == new || spans.isEmpty() && typingMarks.isNullOrEmpty()) return normalize(spans, new.length)
        var prefix = 0
        val limit = minOf(old.length, new.length)
        while (prefix < limit && old[prefix] == new[prefix]) prefix++
        var suffix = 0
        while (suffix < limit - prefix && old[old.length - 1 - suffix] == new[new.length - 1 - suffix]) suffix++
        val removedEnd = old.length - suffix
        val inserted = new.length - suffix - prefix
        val delta = new.length - old.length
        fun map(x: Int) = when {
            x <= prefix -> x
            x >= removedEnd -> x + delta
            else -> prefix
        }
        var moved = spans.map { BodySpan(map(it.start), map(it.end), it.mark) }
        if (inserted > 0) {
            // A new line after a heading starts as body text, like any notes app.
            val newLine = '\n' in new.substring(prefix, prefix + inserted)
            val marks = typingMarks ?: marksAt(spans, prefix).filterTo(HashSet()) { !newLine || it.group != BodyMarkGroup.SIZE }
            BodyMark.entries.forEach { mark ->
                moved = if (mark in marks) moved + BodySpan(prefix, prefix + inserted, mark)
                else remove(moved, mark, prefix, prefix + inserted)
            }
        }
        return normalize(moved, new.length)
    }

    /** The part of [spans] inside [from, to), shifted so [from] becomes 0. */
    fun slice(spans: List<BodySpan>, from: Int, to: Int): List<BodySpan> =
        normalize(spans.mapNotNull { s ->
            val a = maxOf(s.start, from); val b = minOf(s.end, to)
            if (b > a) BodySpan(a - from, b - from, s.mark) else null
        }, to - from)

    /** [[start, end, "b"], …]; null when there is nothing to store. */
    fun encode(spans: List<BodySpan>): JSONArray? =
        if (spans.isEmpty()) null else JSONArray().apply {
            spans.forEach { put(JSONArray().put(it.start).put(it.end).put(it.mark.key)) }
        }

    fun decode(array: JSONArray?, length: Int): List<BodySpan> {
        if (array == null) return emptyList()
        return normalize((0 until array.length()).mapNotNull { i ->
            val row = array.optJSONArray(i) ?: return@mapNotNull null
            val mark = BodyMark.of(row.optString(2)) ?: return@mapNotNull null
            BodySpan(row.optInt(0, -1), row.optInt(1, -1), mark)
        }, length)
    }
}
