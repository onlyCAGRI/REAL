package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.data.BodyFormat
import com.cagri.real.data.BodyGap
import com.cagri.real.data.BodyMark
import com.cagri.real.data.BodyMarkGroup
import com.cagri.real.data.BodySize
import com.cagri.real.data.BodySpan
import com.cagri.real.data.BodySpans

private fun BodySize.fontSp(): Float = when (this) { BodySize.SMALL -> 13f; BodySize.MEDIUM -> 14f; BodySize.LARGE -> 16f }
private fun BodySize.lineSp(): Float = when (this) { BodySize.SMALL -> 18f; BodySize.MEDIUM -> 20f; BodySize.LARGE -> 23f }
/** Font size of the short empty line drawn between paragraphs; its height follows that font. */
private fun BodyGap.sp(): Float = when (this) { BodyGap.NONE -> 0f; BodyGap.SMALL -> 4f; BodyGap.LARGE -> 9f }

/**
 * Base style of a card body under its stack's settings; also what auto-flow measures with. A fixed
 * line height would force the gap lines to a full line too, so with a gap lines keep their natural height.
 */
fun BodyFormat.textStyle(): TextStyle = TextStyle(
    fontSize = size.fontSp().sp, fontWeight = FontWeight.Normal,
    lineHeight = if (gap == BodyGap.NONE) size.lineSp().sp else androidx.compose.ui.unit.TextUnit.Unspecified,
    textIndent = if (indent) TextIndent(firstLine = (size.fontSp() * 1.6f).sp) else null
)

/** Text colours readable on both card tones: [0] on dark cards, [1] on light ones. */
private fun BodyMark.inkPair(): Pair<Color, Color>? = when (this) {
    BodyMark.ORANGE -> Color(0xFFFF8A5C) to Color(0xFFC2410C)
    BodyMark.YELLOW -> Color(0xFFF5D96B) to Color(0xFF8A6A00)
    BodyMark.GREEN -> Color(0xFF7FDCA4) to Color(0xFF1E7A4C)
    BodyMark.BLUE -> Color(0xFF8EB8FF) to Color(0xFF1F5BB8)
    BodyMark.PINK -> Color(0xFFF4A3C4) to Color(0xFFB0306A)
    else -> null
}

private fun BodyMark.sizeFactor(): Float = when (this) { BodyMark.SMALL -> 0.86f; BodyMark.LARGE -> 1.22f; BodyMark.TITLE -> 1.5f; else -> 1f }

/**
 * Plain body + style ranges + stack settings → what the card draws. With a paragraph gap, each line
 * break is followed by one short empty line, so offsets differ: [mapping] / [toOriginal] translate.
 */
class CardBodyLayout(val text: String, styles: List<BodySpan>, val format: BodyFormat, onDark: Boolean) {
    private val spans = BodySpans.normalize(styles, text.length)
    private val gap = format.gap.sp()
    private val breaks: IntArray = if (gap > 0f) text.indices.filter { text[it] == '\n' }.toIntArray() else IntArray(0)
    /** Positions of the added gap lines' line breaks, in drawn offsets. */
    private val inserted = IntArray(breaks.size) { breaks[it] + it + 1 }

    fun toTransformed(offset: Int): Int = offset + breaks.count { it < offset }
    fun toOriginal(offset: Int): Int = (offset - inserted.count { it < offset }).coerceIn(0, text.length)
    /** End of a range: one ending after a line break stops before the gap line that follows it. */
    private fun endOf(offset: Int): Int = if (offset > 0 && text[offset - 1] == '\n') toTransformed(offset - 1) + 1 else toTransformed(offset)

    val mapping = object : OffsetMapping {
        override fun originalToTransformed(offset: Int) = toTransformed(offset.coerceIn(0, text.length))
        override fun transformedToOriginal(offset: Int) = toOriginal(offset)
    }

    val annotated: AnnotatedString = buildAnnotatedString {
        if (breaks.isEmpty()) append(text)
        else text.forEach { c -> append(c); if (c == '\n') append('\n') }
        val base = format.size.fontSp()
        spans.forEach { s ->
            val style = when (s.mark) {
                BodyMark.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                BodyMark.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                BodyMark.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
                BodyMark.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                else -> s.mark.inkPair()?.let { SpanStyle(color = if (onDark) it.first else it.second) }
            } ?: return@forEach
            addStyle(style, toTransformed(s.start), endOf(s.end))
        }
        // Two decorations on one character would override each other; merge them where they overlap.
        val under = spans.filter { it.mark == BodyMark.UNDERLINE }
        spans.filter { it.mark == BodyMark.STRIKE }.forEach { st ->
            under.forEach { u ->
                val a = maxOf(st.start, u.start); val b = minOf(st.end, u.end)
                if (b > a) addStyle(SpanStyle(textDecoration = TextDecoration.combine(
                    listOf(TextDecoration.Underline, TextDecoration.LineThrough))), toTransformed(a), endOf(b))
            }
        }
        // Sizes are paragraph formats: widen to whole paragraphs, first one wins where two meet.
        var claimedTo = 0
        spans.filter { it.mark.group == BodyMarkGroup.SIZE }.sortedBy { it.start }.forEach { s ->
            val p = BodySpans.paragraphRange(text, s.start, s.end)
            val from = maxOf(p.first, claimedTo); val to = p.last + 1
            if (to <= from) return@forEach
            claimedTo = to
            val font = base * s.mark.sizeFactor()
            val a = toTransformed(from); val b = endOf(to)
            addStyle(ParagraphStyle(lineHeight = (font * if (s.mark == BodyMark.TITLE) 1.25f else 1.4f).sp), a, b)
            addStyle(SpanStyle(fontSize = font.sp, fontWeight = if (s.mark == BodyMark.TITLE) FontWeight.Bold else null), a, b)
        }
        // Only a small font on the added line break: an empty line is as tall as the font it carries.
        inserted.forEach { at -> addStyle(SpanStyle(fontSize = gap.sp), at, at + 1) }
    }
}

/** Draws styles and paragraph gaps over the editable plain text. */
fun cardBodyTransformation(styles: List<BodySpan>, format: BodyFormat, onDark: Boolean): VisualTransformation =
    if (styles.isEmpty() && format.gap == BodyGap.NONE) VisualTransformation.None
    else VisualTransformation { CardBodyLayout(it.text, styles, format, onDark).let { l -> TransformedText(l.annotated, l.mapping) } }

/**
 * [CardBodyFormatBar] wired to a text being edited: works out what is lit and turns a tap into new
 * style ranges ([onStyles]) or, with no selection, into the marks of the next typed letters ([onPending]).
 * Sizes act on the whole paragraphs under the caret or selection.
 */
@Composable
internal fun BodyFormatControls(
    text: String,
    selection: androidx.compose.ui.text.TextRange,
    styles: List<BodySpan>,
    pending: Set<BodyMark>?,
    open: Boolean,
    foreground: Color,
    onDark: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onPending: (Set<BodyMark>) -> Unit,
    onStyles: (List<BodySpan>) -> Unit,
    modifier: Modifier = Modifier
) {
    val paragraphs = BodySpans.paragraphRange(text, selection.min, selection.max)
    val charMarks = if (selection.collapsed) BodySpans.typingMarks(styles, selection.start, pending)
        else BodyMark.entries.filterTo(HashSet()) { BodySpans.covers(styles, it, selection.min, selection.max) }
    val active = charMarks.filterTo(HashSet()) { it.group != BodyMarkGroup.SIZE } +
        if (paragraphs.isEmpty()) charMarks.filter { it.group == BodyMarkGroup.SIZE }
        else BodyMark.of(BodyMarkGroup.SIZE).filter { BodySpans.covers(styles, it, paragraphs.first, paragraphs.last + 1) }
    fun commit(next: List<BodySpan>) = onStyles(BodySpans.normalize(next, text.length))
    CardBodyFormatBar(
        open = open, foreground = foreground, onDark = onDark, active = active, reduceAnimations = false,
        onOpenChange = onOpenChange,
        onToggle = { mark ->
            when {
                mark.group == BodyMarkGroup.SIZE && !paragraphs.isEmpty() ->
                    commit(BodySpans.toggle(styles, mark, paragraphs.first, paragraphs.last + 1, text.length))
                selection.collapsed -> onPending(BodySpans.switchMark(active, mark))
                else -> commit(BodySpans.toggle(styles, mark, selection.min, selection.max, text.length))
            }
        },
        onClearGroup = { group ->
            when {
                group == BodyMarkGroup.SIZE && !paragraphs.isEmpty() ->
                    commit(BodySpans.clearGroup(styles, group, paragraphs.first, paragraphs.last + 1))
                selection.collapsed -> onPending(active.filterTo(LinkedHashSet()) { it.group != group })
                else -> commit(BodySpans.clearGroup(styles, group, selection.min, selection.max))
            }
        },
        modifier = modifier
    )
}

/**
 * "Aa" on a card being written in. Open: K İ A Ü beside it, and under it a row of paragraph sizes
 * and text colours. [active] lights what the selection (or the next typed letter) carries.
 */
@Composable
internal fun CardBodyFormatBar(
    open: Boolean,
    foreground: Color,
    onDark: Boolean,
    active: Set<BodyMark>,
    reduceAnimations: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onToggle: (BodyMark) -> Unit,
    onClearGroup: (BodyMarkGroup) -> Unit,
    modifier: Modifier = Modifier
) {
    val chip = RoundedCornerShape(12.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            FormatChip(open, foreground, chip, if (open) t("Biçim şeridini kapat") else t("Biçim şeridini aç"), { onOpenChange(!open) }) {
                Text("Aa", color = if (open) Color(0xFF161616) else foreground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            if (open) BodyMark.of(BodyMarkGroup.STYLE).forEach { mark ->
                val on = mark in active
                FormatChip(on, foreground, chip, mark.label(), { onToggle(mark) }) {
                    Text(mark.glyph(), color = if (on) Color(0xFF161616) else foreground, style = TextStyle(
                        fontSize = 15.sp, fontWeight = if (mark == BodyMark.BOLD) FontWeight.Bold else FontWeight.Medium,
                        fontStyle = if (mark == BodyMark.ITALIC) FontStyle.Italic else FontStyle.Normal,
                        textDecoration = when (mark) {
                            BodyMark.UNDERLINE -> TextDecoration.Underline
                            BodyMark.STRIKE -> TextDecoration.LineThrough
                            else -> null
                        }))
                }
            }
        }
        AnimatedVisibility(
            visible = open,
            enter = if (reduceAnimations) EnterTransition.None else expandVertically(tween(200, easing = RealMotion.Emphasized)) + fadeIn(tween(140)),
            exit = if (reduceAnimations) ExitTransition.None else shrinkVertically(tween(160)) + fadeOut(tween(100))
        ) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                val noSize = BodyMark.of(BodyMarkGroup.SIZE).none { it in active }
                SizeChip(t("Küçük"), 11f, false, BodyMark.SMALL in active, foreground) { onToggle(BodyMark.SMALL) }
                SizeChip(t("Gövde"), 13f, false, noSize, foreground) { onClearGroup(BodyMarkGroup.SIZE) }
                SizeChip(t("Büyük"), 15f, false, BodyMark.LARGE in active, foreground) { onToggle(BodyMark.LARGE) }
                SizeChip(t("Başlık"), 15f, true, BodyMark.TITLE in active, foreground) { onToggle(BodyMark.TITLE) }
                Box(Modifier.padding(horizontal = 2.dp).width(1.dp).height(20.dp).background(foreground.copy(alpha = 0.18f)))
                val noColor = BodyMark.of(BodyMarkGroup.COLOR).none { it in active }
                ColorDot(foreground, noColor, t("Varsayılan renk"), foreground) { onClearGroup(BodyMarkGroup.COLOR) }
                BodyMark.of(BodyMarkGroup.COLOR).forEach { mark ->
                    val ink = mark.inkPair()!!.let { if (onDark) it.first else it.second }
                    ColorDot(ink, mark in active, mark.label(), foreground) { onToggle(mark) }
                }
            }
        }
    }
}

@Composable
private fun FormatChip(
    on: Boolean, foreground: Color, shape: Shape, description: String,
    onClick: () -> Unit, modifier: Modifier = Modifier.size(36.dp), content: @Composable () -> Unit
) {
    // Not focusable: tapping a chip must leave the caret and the keyboard where they are.
    Box(
        modifier.clip(shape)
            .background(if (on) foreground else foreground.copy(alpha = 0.10f))
            .border(0.7.dp, foreground.copy(alpha = if (on) 0f else 0.18f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description; selected = on },
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun SizeChip(label: String, fontSp: Float, bold: Boolean, on: Boolean, foreground: Color, onClick: () -> Unit) =
    FormatChip(on, foreground, RoundedCornerShape(12.dp), label, onClick, Modifier.height(32.dp)) {
        Text(label, color = if (on) Color(0xFF161616) else foreground, fontSize = fontSp.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.padding(horizontal = 10.dp))
    }

@Composable
private fun ColorDot(color: Color, on: Boolean, description: String, foreground: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(28.dp).clip(CircleShape)
            .border(if (on) 2.dp else 0.7.dp, if (on) foreground else foreground.copy(alpha = 0.22f), CircleShape)
            .padding(5.dp).clip(CircleShape).background(color)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description; selected = on }
    )
}

/** "Kart yığını" panel rows: text size, first-line indent and paragraph gap for the whole stack. */
@Composable
internal fun CardBodyFormatSettings(format: BodyFormat, onChange: (BodyFormat) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingRow(t("Yazı boyutu"), listOf(t("Küçük") to BodySize.SMALL, t("Orta") to BodySize.MEDIUM, t("Büyük") to BodySize.LARGE),
            format.size) { onChange(format.copy(size = it)) }
        SettingRow(t("Paragraf başı"), listOf(t("Yok") to false, t("Girinti") to true), format.indent) { onChange(format.copy(indent = it)) }
        SettingRow(t("Paragraf aralığı"), listOf(t("Yok") to BodyGap.NONE, t("Az") to BodyGap.SMALL, t("Çok") to BodyGap.LARGE),
            format.gap) { onChange(format.copy(gap = it)) }
    }
}

@Composable
private fun <T> SettingRow(label: String, options: List<Pair<String, T>>, current: T, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.06f)).padding(3.dp)) {
            options.forEach { (text, value) ->
                val on = value == current
                Text(text, color = if (on) Color.White else Color(0xFFA5A5AD), fontSize = 12.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.clip(RoundedCornerShape(9.dp))
                        .background(if (on) Color(0xFF343439) else Color.Transparent)
                        .clickable(role = Role.Button) { if (!on) onSelect(value) }
                        .semantics { selected = on; contentDescription = "$label: $text" }
                        .padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
    }
}

private fun BodyMark.glyph(): String = when (this) {
    BodyMark.BOLD -> t("K")
    BodyMark.ITALIC -> t("İ")
    BodyMark.UNDERLINE -> t("A")
    else -> t("Ü")
}

private fun BodyMark.label(): String = when (this) {
    BodyMark.BOLD -> t("Kalın")
    BodyMark.ITALIC -> t("İtalik")
    BodyMark.UNDERLINE -> t("Altı çizili")
    BodyMark.STRIKE -> t("Üstü çizili")
    BodyMark.SMALL -> t("Küçük")
    BodyMark.LARGE -> t("Büyük")
    BodyMark.TITLE -> t("Başlık")
    BodyMark.ORANGE -> t("Turuncu")
    BodyMark.YELLOW -> t("Sarı")
    BodyMark.GREEN -> t("Yeşil")
    BodyMark.BLUE -> t("Mavi")
    BodyMark.PINK -> t("Pembe")
}
