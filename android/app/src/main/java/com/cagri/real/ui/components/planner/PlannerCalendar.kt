package com.cagri.real.ui.components.planner

import com.cagri.real.ui.components.RealMotion
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.data.CustomCategory
import com.cagri.real.ui.components.ActionGlyph
import com.cagri.real.ui.components.ActionIcon
import com.cagri.real.ui.components.liquidChrome
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import java.time.format.DateTimeFormatter

internal enum class PlannerViewMode(val preference: String) {
    Hours24("hours_24"),
    Daily("daily"),
    Monthly("monthly");

    companion object {
        fun fromPreference(value: String?): PlannerViewMode =
            (entries.firstOrNull { it.preference == value || it.name.equals(value, ignoreCase = true) || (value == "24_hours" && it == Hours24) } ?: Hours24)
                // The daily focus view is gone: Takvim is the month view only.
                .let { if (it == Daily) Monthly else it }
    }
}
internal fun PlannerEvent.occurrenceKey(): String = "$id:$startMillis"

internal fun plannerMonthDays(month: YearMonth): List<LocalDate> {
    val first = month.atDay(1)
    val leading = first.dayOfWeek.value - 1
    val start = first.minusDays(leading.toLong())
    val cells = ((leading + month.lengthOfMonth() + 6) / 7) * 7
    return List(cells) { start.plusDays(it.toLong()) }
}

@Composable
private fun CalendarDots(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    onLight: Boolean = false,
    /** Contrast ring for dots on a coloured tile; null = none. */
    ring: Color? = if (onLight) PlannerInk.copy(alpha = 0.20f) else null,
    /** Fixed slot (0 top, 1 middle, 2 bottom) per dot, so a several-day event keeps one height day to day. */
    slots: List<Int>? = null
) {
    val visible = colors.take(3)
    if (visible.isEmpty()) return
    val dotDiameter = 4.8.dp
    val dotSpacing = 3.5.dp
    val rows = if (slots != null) 3 else visible.size
    val totalHeight = dotDiameter * rows + dotSpacing * (rows - 1)
    Canvas(modifier.size(width = dotDiameter, height = totalHeight)) {
        val rPx = (dotDiameter / 2).toPx()
        val stepPx = (dotDiameter + dotSpacing).toPx()
        visible.forEachIndexed { index, color ->
            val centerY = rPx + (slots?.getOrNull(index) ?: index) * stepPx
            val center = Offset(size.width / 2f, centerY)
            if (ring != null) {
                // Subtle contrast ring so a dot stays visible on a coloured tile
                drawCircle(
                    color = ring,
                    radius = rPx + 0.6.dp.toPx(),
                    center = center
                )
            }
            drawCircle(
                color = color,
                radius = rPx,
                center = center
            )
        }
    }
}

/** The string (e.g. "6 PM") is measured unconstrained, then rotated 90° clockwise as a single text run. */
@Composable
private fun ClockwiseHourLabel(label: String, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val layout = measurer.measure(
        label,
        TextStyle(fontSize = 9.sp, color = PlannerInk.copy(alpha = 0.65f), fontWeight = FontWeight.Normal),
        maxLines = 1,
        softWrap = false
    )
    Canvas(modifier.height(38.dp)) {
        val topInsetPx = 2.dp.toPx()
        withTransform({
            translate(left = (size.width + layout.size.height) / 2f, top = topInsetPx)
            rotate(90f, pivot = Offset.Zero)
        }) {
            drawText(layout)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PlannerMonthOverview(
    month: YearMonth,
    reduceAnimations: Boolean = false,
    today: LocalDate,
    selectedDay: LocalDate,
    eventsByDay: Map<LocalDate, List<PlannerEvent>>,
    customCategories: List<CustomCategory> = emptyList(),
    activeDays: Set<LocalDate> = emptySet(),
    onChangeMonth: (YearMonth) -> Unit,
    onPickDay: (LocalDate) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onOpenEvent: (Long, Long) -> Unit,
    dayModifier: @Composable (LocalDate) -> Modifier,
    topPadding: androidx.compose.ui.unit.Dp = 4.dp,
    ownerName: String = "",
    /** "Süre ekle" on a card opens the same start / end / duration editor as 24 hour, instead of the card. */
    durationEditorId: Long? = null,
    onToggleDuration: ((PlannerEvent) -> Unit)? = null,
    durationEditor: @Composable (PlannerEvent, Boolean) -> Unit = { _, _ -> }
) {
    val effectiveActiveDays = remember(activeDays, selectedDay, today) {
        if (activeDays.isNotEmpty()) {
            activeDays + selectedDay + today
        } else {
            setOf(selectedDay.minusDays(1), selectedDay, selectedDay.plusDays(1), today)
        }
    }
    val selectedDayEvents = remember(eventsByDay, selectedDay) {
        eventsByDay[selectedDay].orEmpty().sortedWith(compareBy<PlannerEvent> { it.startMillis }.thenBy { it.id })
    }

    // 8. Dynamic category legend: Extract categories actually used in the visible month
    val monthEvents = remember(eventsByDay, month) {
        eventsByDay.filterKeys { YearMonth.from(it) == month }.values.flatten()
    }
    val usedCategories = remember(monthEvents, customCategories) {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<PlannerCategoryVisual>()
        monthEvents.forEach { ev ->
            val v = plannerEventCategoryVisual(ev, customCategories)
            if (seen.add(v.name)) {
                result.add(v)
            }
        }
        result.sortWith(compareBy({ if (it.name.equals("Gelen kutu", ignoreCase = true)) 0 else 1 }, { it.name }))
        result
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = topPadding, bottom = 24.dp)
    ) {
        // Month Navigation Header: ‹  Eylül 2026  ›
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp)
                .liquidChrome(corners = 20.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { onChangeMonth(month.minusMonths(1)) },
                contentAlignment = Alignment.Center
            ) {
                Text("‹", color = Color(0xFFB0B0B8), fontSize = 26.sp)
            }
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    month.format(DateTimeFormatter.ofPattern("MMMM", PlannerLocale))
                        .replaceFirstChar { it.uppercase(PlannerLocale) },
                    color = Color.White,
                    style = PlannerType.monthTitle,
                    modifier = Modifier.semantics {
                        contentDescription = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", PlannerLocale))
                    }
                )
            }
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { onChangeMonth(month.plusMonths(1)) },
                contentAlignment = Alignment.Center
            ) {
                Text("›", color = Color(0xFFB0B0B8), fontSize = 26.sp)
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .liquidChrome(corners = 16.dp)
                    .clickable(role = Role.Button) {
                        onChangeMonth(YearMonth.from(today))
                        onPickDay(today)
                    }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text("Bugün", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }

        // Weekday labels
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
            listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz").forEach {
                Box(Modifier.weight(1f).height(30.dp), contentAlignment = Alignment.Center) {
                    Text(it, color = Color(0xFF8E9094), style = PlannerType.weekdayLabel)
                }
            }
        }

        AnimatedContent(
            targetState = month,
            modifier = Modifier.fillMaxWidth().clipToBounds(),
            transitionSpec = {
                if (reduceAnimations) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    val direction = if (targetState > initialState) 1 else -1
                    val movement = tween<androidx.compose.ui.unit.IntOffset>(
                        280, easing = RealMotion.Standard
                    )
                    ((slideInHorizontally(movement) { it * direction } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(movement) { -it * direction } + fadeOut(tween(180))))
                        .using(SizeTransform(clip = true))
                }
            },
            label = "monthGridSlide"
        ) { displayedMonth ->
        // 6. Month Grid (Date tiles represent schedule load / busyness)
        val zone = remember { ZoneId.systemDefault() }
        // Up to three dots a day; events lasting several days come first so their dots line up day to day.
        fun dotEvents(day: LocalDate): List<PlannerEvent> = eventsByDay[day].orEmpty()
            .filterNot { it.reminder.isCompleted }
            .sortedWith(compareBy<PlannerEvent>({ if (it.isMultiDay(zone)) 0 else 1 }, { it.startMillis }, { it.id }))
            .take(3)
        val spanMeasurer = rememberTextMeasurer()
        Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            plannerMonthDays(displayedMonth).chunked(7).forEach { week ->
                val weekDots = remember(week, eventsByDay, displayedMonth) {
                    week.map { day -> if (YearMonth.from(day) == displayedMonth) dotEvents(day) else emptyList() }
                }
                val spanColors = remember(weekDots, customCategories) {
                    weekDots.map { list -> list.map { plannerEventCategoryVisual(it, customCategories).realColor } }
                }
                // Several-day events keep one dot height all week (first one in the middle, then top, then bottom), so
                // their line runs straight; a day holding one lays its other dots in the free slots.
                val weekSlots = remember(weekDots) {
                    val spanSlot = weekDots.flatten().filter { it.isMultiDay(zone) }.map { it.occurrenceKey() }.distinct()
                        .take(3).mapIndexed { i, key -> key to listOf(1, 0, 2)[i] }.toMap()
                    weekDots.map { list ->
                        if (list.none { it.occurrenceKey() in spanSlot }) null else {
                            val used = list.mapNotNull { spanSlot[it.occurrenceKey()] }.toSet()
                            val free = (0..2).filter { it !in used }.toMutableList()
                            list.map { spanSlot[it.occurrenceKey()] ?: free.removeAt(0) }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().drawWithContent {
                    drawContent()
                    // Several-day events: a thin line joins their dots across the days (and on to the next week).
                    val gap = 4.dp.toPx()
                    val tileW = (size.width - gap * 6) / 7
                    val d = 4.8.dp.toPx()
                    val s = 3.5.dp.toPx()
                    fun dotCenter(i: Int, k: Int): Offset {
                        val x = i * (tileW + gap) + tileW - 7.dp.toPx() - d / 2
                        val slot = weekSlots[i]?.getOrNull(k)
                        if (slot != null) return Offset(x, size.height / 2 + (slot - 1) * (d + s))
                        val n = weekDots[i].size
                        val total = d * n + s * (n - 1)
                        return Offset(x, (size.height - total) / 2 + d / 2 + k * (d + s))
                    }
                    fun numberGap(i: Int): ClosedFloatingPointRange<Float> {
                        val w = spanMeasurer.measure(week[i].dayOfMonth.toString(), TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)).size.width
                        val left = i * (tileW + gap) + 9.dp.toPx()
                        return (left - 3.dp.toPx())..(left + w + 3.dp.toPx())
                    }
                    val stroke = 1.6.dp.toPx()
                    fun segment(a: Offset, b: Offset, color: Color, skip: ClosedFloatingPointRange<Float>?) {
                        val c = color.copy(alpha = 0.9f)
                        if (skip == null || b.x <= skip.start || a.x >= skip.endInclusive) {
                            drawLine(c, a, b, stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round); return
                        }
                        fun at(x: Float) = Offset(x, a.y + (b.y - a.y) * ((x - a.x) / (b.x - a.x)))
                        if (skip.start > a.x) drawLine(c, a, at(skip.start), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        if (skip.endInclusive < b.x) drawLine(c, at(skip.endInclusive), b, stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    }
                    weekDots.forEachIndexed { i, list ->
                        list.forEachIndexed { k, ev ->
                            if (!ev.isMultiDay(zone)) return@forEachIndexed
                            val key = ev.occurrenceKey()
                            val color = spanColors[i][k]
                            val here = dotCenter(i, k)
                            val day = week[i]
                            val next = weekDots.getOrNull(i + 1)?.indexOfFirst { it.occurrenceKey() == key } ?: -1
                            if (next >= 0) segment(here, dotCenter(i + 1, next), color, numberGap(i + 1))
                            else if (i == 6 && ev.lastDay(zone) > day && YearMonth.from(day.plusDays(1)) == displayedMonth)
                                segment(here, Offset(size.width, here.y), color, null)
                            if (i == 0 && ev.day(zone) < day && YearMonth.from(day.minusDays(1)) == displayedMonth)
                                segment(Offset(0f, here.y), here, color, numberGap(0))
                        }
                    }
                }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { day ->
                        val inMonth = YearMonth.from(day) == displayedMonth
                        val isToday = day == today
                        val isSelected = day == selectedDay
                        val dayEvents = eventsByDay[day].orEmpty().sortedWith(compareBy<PlannerEvent> { it.startMillis }.thenBy { it.id })
                        // Completed tasks do not pass their colour to the monthly tile or its dots.
                        val colorEvents = dayEvents.filterNot { it.reminder.isCompleted }
                        // Days are not tinted by category any more: neutral tiles, the coloured dots carry the categories.
                        // Days before today are faded, today has a white frame, later days are normal.
                        val isPast = day.isBefore(today)
                        val hasPastelBg = false
                        val tileInk = Color.White
                        val tileBackground = when {
                            !inMonth -> Color(0xFF141517)
                            isPast -> Color(0xFF151618)
                            else -> Color(0xFF222326)
                        }

                        val textColor = when {
                            !inMonth -> Color.White.copy(alpha = 0.20f)
                            isSelected || isToday -> Color.White
                            isPast -> Color.White.copy(alpha = 0.38f)
                            else -> Color.White.copy(alpha = 0.92f)
                        }

                        val borderModifier = when {
                            !inMonth -> Modifier
                            isToday -> Modifier.border(2.dp, Color.White, RoundedCornerShape(13.dp))
                            isSelected -> Modifier.border(1.5.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(13.dp))
                            else -> Modifier.border(0.5.dp, Color.White.copy(alpha = if (isPast) 0.04f else 0.07f), RoundedCornerShape(13.dp))
                        }

                        Box(
                            Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp)
                                .aspectRatio(0.85f)
                                .then(dayModifier(day))
                                .clip(RoundedCornerShape(13.dp))
                                .background(tileBackground)
                                .then(borderModifier)
                                .combinedClickable(
                                    role = Role.Button,
                                    onClick = { onPickDay(day) },
                                    onDoubleClick = { onOpenDay(day) }
                                )
                                .semantics {
                                    selected = isSelected
                                    contentDescription = "${day.format(DateTimeFormatter.ofPattern("d MMMM yyyy", PlannerLocale))}, ${dayEvents.size} etkinlik${if (isToday) ", bugün" else ""}"
                                }
                        ) {
                            Text(
                                day.dayOfMonth.toString(),
                                color = textColor,
                                fontSize = 15.sp,
                                fontWeight = when {
                                    isSelected -> FontWeight.Bold
                                    isToday -> FontWeight.SemiBold
                                    else -> FontWeight.Normal
                                },
                                modifier = Modifier.align(Alignment.CenterStart).padding(start = 9.dp)
                            )
                            // 7. Monthly event indicators (deterministic 0..3 markers)
                            if (inMonth) {
                                val indicatorColors = spanColors[week.indexOf(day)]
                                if (indicatorColors.isNotEmpty()) {
                                    CalendarDots(
                                        colors = indicatorColors,
                                        slots = weekSlots[week.indexOf(day)],
                                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 7.dp),
                                        ring = if (hasPastelBg) tileInk.copy(alpha = 0.55f) else null
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        }

        Spacer(Modifier.height(18.dp))

        // Selected day (as on iOS): the date, how many plans, then the same cards as 24 hour.
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(
                "${selectedDay.dayOfMonth} ${selectedDay.format(DateTimeFormatter.ofPattern("MMMM", PlannerLocale))} · ${plannerDayLabel(selectedDay)}",
                color = Color.White,
                style = PlannerType.selectedDayHeader
            )
            Text(
                if (selectedDayEvents.isEmpty()) "Bu gün için plan yok" else "${selectedDayEvents.size} plan",
                color = Color(0xFF8C8C95),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (selectedDayEvents.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                selectedDayEvents.sortedWith(compareBy<PlannerEvent>({ if (it.isDateOnly24Hour) 0 else 1 }, { it.startMillis })).forEach { event ->
                    Column(Modifier.fillMaxWidth()) {
                        Box(Modifier.plannerEventMotion(event, "month:$selectedDay") { onOpenEvent(event.id, event.startMillis) }) {
                            PlannerNext24Card(
                                event = event,
                                cardColor = resolveNext24CardColor(event, customCategories),
                                zone = ZoneId.systemDefault(),
                                ownerName = event.externalCalendarName ?: ownerName,
                                onClick = { onOpenEvent(event.id, event.startMillis) },
                                onToggleDuration = if (event.id < 0 || onToggleDuration == null) null else ({ onToggleDuration(event) })
                            )
                        }
                        durationEditor(event, durationEditorId == event.id)
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // 8. Dynamic Category Legend
        if (usedCategories.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    "Kategori renkleri",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Etkinlik kapsüllerinde kullanılan renkler.",
                    color = Color(0xFF8E9094),
                    fontSize = 11.sp
                )
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF161719))
                    .border(0.6.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    usedCategories.forEach { cat ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(cat.realColor)
                            )
                            Text(
                                cat.name,
                                color = Color(0xFFD2D4D6),
                                style = PlannerType.legendLabel
                            )
                        }
                    }
                }
            }
        }
    }
}
