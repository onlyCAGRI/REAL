package com.cagri.real.ui.components.planner

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
            entries.firstOrNull { it.preference == value || it.name.equals(value, ignoreCase = true) || (value == "24_hours" && it == Hours24) } ?: Hours24
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
    onLight: Boolean = false
) {
    val visible = colors.take(3)
    if (visible.isEmpty()) return
    val dotDiameter = 4.8.dp
    val dotSpacing = 3.5.dp
    val totalHeight = dotDiameter * visible.size + dotSpacing * (visible.size - 1)
    Canvas(modifier.size(width = dotDiameter, height = totalHeight)) {
        val rPx = (dotDiameter / 2).toPx()
        val stepPx = (dotDiameter + dotSpacing).toPx()
        visible.forEachIndexed { index, color ->
            val centerY = rPx + index * stepPx
            val center = Offset(size.width / 2f, centerY)
            if (onLight) {
                // Subtle contrast ring on light pastel tiles
                drawCircle(
                    color = PlannerInk.copy(alpha = 0.20f),
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

@Composable
internal fun PlannerDayCard(
    day: LocalDate,
    events: List<PlannerEvent>,
    zone: ZoneId,
    onOpen: (Long, Long) -> Unit,
    onAdd: (LocalDate, Int) -> Unit,
    categoryColors: Map<Long, Color> = emptyMap(),
    customCategories: List<CustomCategory> = emptyList(),
    today: LocalDate = LocalDate.now(zone),
    now: Instant = Instant.now(),
    ready: Boolean = true,
    reduceAnimations: Boolean = false,
    modifier: Modifier = Modifier
) {
    val dominantPastel = remember(events, customCategories) {
        resolveDominantDayCategoryPastel(events, customCategories)
    }
    val surface = dominantPastel ?: realDayCardSurface(day)
    val dayStart = remember(day, zone) { day.atStartOfDay(zone).toInstant().toEpochMilli() }
    val dayEnd = remember(day, zone) { day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() }
    val dateOnly = remember(events) { events.filter { it.isDateOnly24Hour } }
    val timedEvents = remember(events) { events.filterNot { it.isDateOnly24Hour } }
    val eventMap = remember(events) { events.associateBy { it.occurrenceKey() } }

    // Full 24 hours of the day (12 AM to 11 PM) in chronological order
    val startHour = 0
    val endHour = 24

    val geometry = remember(timedEvents, dayStart, dayEnd) {
        val intervals = timedEvents.map { event ->
            val intervalEnd = event.endMillis ?: (event.startMillis + 20 * 60_000L)
            PlannerTimeInterval(
                event.occurrenceKey(),
                ((maxOf(event.startMillis, dayStart) - dayStart) / 60_000.0).toFloat(),
                ((minOf(intervalEnd, dayEnd) - dayStart) / 60_000.0).toFloat()
            )
        }
        PlannerTimelineGeometry(
            intervals = intervals,
            dayMinutes = ((dayEnd - dayStart) / 60_000L).toInt(),
            startHour = startHour,
            endHour = endHour,
            compactHourDp = 26f,
            expandedHourDp = 76f
        )
    }

    val density = LocalDensity.current
    val scroll = rememberScrollState()
    var positioned by rememberSaveable(day.toEpochDay()) { mutableStateOf(false) }

    LaunchedEffect(day, ready, geometry, density) {
        if (ready && !positioned) {
            val nowMinute = ((now.toEpochMilli() - dayStart) / 60_000.0).toFloat()
            val focus = if (day == today) {
                geometry.placements.firstOrNull { it.interval.endMinute > nowMinute }
                    ?: geometry.placements.firstOrNull()
            } else {
                geometry.placements.firstOrNull()
            }
            val minute = if (focus != null) {
                ((focus.interval.startMinute / 60).toInt() * 60f - 60f).coerceAtLeast(0f)
            } else if (day == today) {
                val nowHour = now.atZone(zone).hour
                ((nowHour - 1) * 60f).coerceAtLeast(0f)
            } else {
                8 * 60f // 8 AM for an empty day
            }
            scroll.scrollTo(with(density) { geometry.x(minute).dp.roundToPx() })
            positioned = true
        }
    }

    val scope = rememberCoroutineScope()
    val laneHeight = (40f * density.fontScale.coerceAtLeast(1f)).dp
    val laneGap = 6.dp
    val eventTop = 52.dp
    val timelineHeight = maxOf(122.dp, eventTop + (laneHeight + laneGap) * geometry.laneCount + 34.dp)

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(surface)
            .padding(start = 18.dp, top = 15.dp, end = 8.dp, bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                plannerDayLabel(day),
                color = PlannerInk.copy(alpha = 0.85f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            if (day == today) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "Today",
                    color = PlannerInk.copy(alpha = 0.55f),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        if (dateOnly.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                dateOnly.forEach { event ->
                    val visual = plannerEventCategoryVisual(event, customCategories)
                    val isPlanCategory = visual.name.equals("Plan", ignoreCase = true)
                    val cardColor = if (isPlanCategory) planCapsuleFamilyColor(surface) else visual.capsuleColor
                    Text(
                        "Takvim · ${event.reminder.text}",
                        color = PlannerInk,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .plannerEventMotion(event, "day-note:$day") { onOpen(event.id, event.startMillis) }
                            .clip(RoundedCornerShape(10.dp))
                            .background(cardColor)
                            .border(0.6.dp, PlannerInk.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.width(86.dp)) {
                Text(
                    day.dayOfMonth.toString(),
                    color = PlannerInk,
                    fontSize = 46.sp,
                    lineHeight = 49.sp,
                    letterSpacing = (-2).sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    plannerMonthLabel(day),
                    color = PlannerInk,
                    fontSize = 35.sp,
                    lineHeight = 39.sp,
                    letterSpacing = (-1).sp,
                    fontWeight = FontWeight.Medium
                )
            }

            BoxWithConstraints(Modifier.weight(1f).height(timelineHeight).clipToBounds()) {
                val viewportWidth = maxWidth.value
                val scrollVal = scroll.value

                // Hidden event indicators before and after the visible viewport
                val hiddenBefore by remember(geometry, density, viewportWidth, scrollVal) {
                    derivedStateOf {
                        val left = scrollVal / density.density
                        geometry.placements.filter { geometry.x(it.interval.endMinute) <= left + 2f }
                            .sortedByDescending { it.interval.endMinute }
                    }
                }
                val hiddenAfter by remember(geometry, density, viewportWidth, scrollVal) {
                    derivedStateOf {
                        val right = scrollVal / density.density + viewportWidth
                        geometry.placements.filter { geometry.x(it.interval.startMinute) >= right - 2f }
                            .sortedBy { it.interval.startMinute }
                    }
                }

                Box(Modifier.fillMaxSize().horizontalScroll(scroll)) {
                    Box(Modifier.width(geometry.width.dp).height(timelineHeight)) {
                        // 1. Column divider lines & rotated 12-hour AM/PM labels
                        geometry.hourWidths.forEachIndexed { i, width ->
                            val hour = startHour + i
                            val x = geometry.boundaries[i].dp
                            val label = formatHourAmPm(hour)

                            Box(Modifier.offset(x = x).width(width.dp).fillMaxHeight()) {
                                Box(
                                    Modifier
                                        .width(0.6.dp)
                                        .fillMaxHeight()
                                        .background(PlannerInk.copy(alpha = 0.12f))
                                )
                                ClockwiseHourLabel(
                                    label = label,
                                    modifier = Modifier.width(width.dp).padding(top = 14.dp)
                                )
                            }
                        }

                        // Rightmost boundary line
                        Box(
                            Modifier
                                .offset(x = geometry.width.dp)
                                .width(0.6.dp)
                                .fillMaxHeight()
                                .background(PlannerInk.copy(alpha = 0.10f))
                        )

                        // 2. Empty day state: Intentionally left clean & empty (NO "Plan yok" text)

                        // 3. Event capsules (4. Category-derived pastel background with dark ink text)
                        geometry.placements.forEach { placement ->
                            val interval = placement.interval
                            val event = eventMap.getValue(interval.key)
                            val visual = plannerEventCategoryVisual(event, customCategories)
                            val isPlanCategory = visual.name.equals("Plan", ignoreCase = true)
                            val capsuleColor = if (isPlanCategory) planCapsuleFamilyColor(surface) else visual.capsuleColor
                            val leftX = geometry.x(interval.startMinute)
                            val rightX = geometry.x(interval.endMinute)
                            val width = (rightX - leftX).coerceAtLeast(16f)
                            val endMillis = event.endMillis
                            val timeRange = if (endMillis != null) {
                                formatTimeRangeAmPm(maxOf(event.startMillis, dayStart), minOf(endMillis, dayEnd), zone)
                            } else {
                                formatTimeAmPm(maxOf(event.startMillis, dayStart), zone)
                            }
                            val startTime = formatTimeAmPm(maxOf(event.startMillis, dayStart), zone)

                            Column(
                                Modifier
                                    .offset(x = leftX.dp, y = eventTop + (laneHeight + laneGap) * placement.lane)
                                    .width(width.dp)
                                    .height(laneHeight)
                                    .plannerEventMotion(event, "timeline:$day") { onOpen(event.id, event.startMillis) }
                                    .clip(RoundedCornerShape(minOf(12f, width / 2f).dp))
                                    .background(capsuleColor)
                                    .border(0.75.dp, PlannerInk.copy(alpha = 0.10f), RoundedCornerShape(minOf(12f, width / 2f).dp))
                                    .semantics { contentDescription = "${event.reminder.text}, $timeRange" }
                                    .padding(horizontal = if (width >= 60f) 8.dp else 4.dp, vertical = 2.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    event.reminder.text,
                                    color = PlannerInk,
                                    fontSize = if (width < 60f) 10.sp else 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 14.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    textDecoration = if (event.reminder.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                                )
                                if (width >= 75f && density.fontScale <= 1.25f) {
                                    Text(
                                        if (width >= 115f) timeRange else startTime,
                                        color = PlannerInk.copy(alpha = 0.72f),
                                        fontSize = 8.5.sp,
                                        lineHeight = 10.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // 4. Consistently aligned plus buttons on lower row
                        for (i in 0 until (endHour - startHour)) {
                            val hour = startHour + i
                            val colStartX = geometry.boundaries[i]
                            val colWidth = geometry.hourWidths[i]
                            Box(
                                Modifier
                                    .offset(x = colStartX.dp)
                                    .width(colWidth.dp)
                                    .height(34.dp)
                                    .align(Alignment.BottomStart)
                                    .clickable(enabled = ready, role = Role.Button) { onAdd(day, hour) }
                                    .semantics { contentDescription = "${plannerDayLabel(day)} ${formatHourAmPm(hour)} için etkinlik ekle" },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    Modifier
                                        .size(15.dp)
                                        .border(0.75.dp, PlannerInk.copy(alpha = 0.22f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    ActionIcon(ActionGlyph.Add, Modifier.size(8.dp), PlannerInk.copy(alpha = 0.42f))
                                }
                            }
                        }
                    }
                }

                // 5. Side mini indicators (left / right) using the same category pastel mapper
                if (hiddenBefore.isNotEmpty()) {
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 2.dp, top = 0.dp)
                            .size(48.dp)
                            .clickable(role = Role.Button) {
                                val target = hiddenBefore.first()
                                val offset = (geometry.x(target.interval.startMinute) - 12f).coerceAtLeast(0f)
                                scope.launch {
                                    val px = with(density) { offset.dp.roundToPx() }
                                    if (reduceAnimations) scroll.scrollTo(px) else scroll.animateScrollTo(px)
                                }
                            }
                            .semantics { contentDescription = "Daha önce ${hiddenBefore.size} etkinlik; önceki etkinliğe git" },
                        contentAlignment = Alignment.TopStart
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(start = 3.dp, top = 1.dp)
                        ) {
                            hiddenBefore.take(3).forEach { placement ->
                                val ev = eventMap[placement.interval.key]
                                val dotColor = ev?.let { resolvePlannerEventIndicatorColor(it, customCategories) }
                                    ?: Color(0xFFD6CCE8)
                                Box(
                                    Modifier
                                        .size(4.8.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                            }
                        }
                    }
                }

                if (hiddenAfter.isNotEmpty()) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 2.dp, top = 0.dp)
                            .size(48.dp)
                            .clickable(role = Role.Button) {
                                val target = hiddenAfter.first()
                                val offset = (geometry.x(target.interval.startMinute) - 12f).coerceAtLeast(0f)
                                scope.launch {
                                    val px = with(density) { offset.dp.roundToPx() }
                                    if (reduceAnimations) scroll.scrollTo(px) else scroll.animateScrollTo(px)
                                }
                            }
                            .semantics { contentDescription = "Daha sonra ${hiddenAfter.size} etkinlik; sonraki etkinliğe git" },
                        contentAlignment = Alignment.TopEnd
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(end = 3.dp, top = 1.dp)
                        ) {
                            hiddenAfter.take(3).forEach { placement ->
                                val ev = eventMap[placement.interval.key]
                                val dotColor = ev?.let { resolvePlannerEventIndicatorColor(it, customCategories) }
                                    ?: Color(0xFFE8BF7A)
                                Box(
                                    Modifier
                                        .size(4.8.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                            }
                        }
                    }
                }
            }
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
    topPadding: androidx.compose.ui.unit.Dp = 4.dp
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
                        280, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
                    )
                    ((slideInHorizontally(movement) { it * direction } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(movement) { -it * direction } + fadeOut(tween(180))))
                        .using(SizeTransform(clip = true))
                }
            },
            label = "monthGridSlide"
        ) { displayedMonth ->
        // 6. Month Grid (Date tiles represent schedule load / busyness)
        Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            plannerMonthDays(displayedMonth).chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { day ->
                        val inMonth = YearMonth.from(day) == displayedMonth
                        val isToday = day == today
                        val isSelected = day == selectedDay
                        val dayEvents = eventsByDay[day].orEmpty().sortedWith(compareBy<PlannerEvent> { it.startMillis }.thenBy { it.id })
                        // Completed tasks do not pass their colour to the monthly tile or its dots.
                        val colorEvents = dayEvents.filterNot { it.reminder.isCompleted }
                        val dominantCategoryPastel = remember(colorEvents, customCategories) {
                            if (inMonth && colorEvents.isNotEmpty()) {
                                resolveDominantDayCategoryPastel(colorEvents, customCategories)
                            } else {
                                null
                            }
                        }

                        val hasPastelBg = inMonth && dominantCategoryPastel != null

                        val tileBackground = when {
                            !inMonth -> Color(0xFF141517)
                            dominantCategoryPastel != null -> dominantCategoryPastel
                            else -> Color(0xFF1B1C1E)
                        }

                        val textColor = when {
                            !inMonth -> Color.White.copy(alpha = 0.20f)
                            hasPastelBg -> if (isSelected) PlannerInk else PlannerInk.copy(alpha = 0.88f)
                            isSelected -> Color.White
                            isToday -> Color.White
                            else -> Color.White.copy(alpha = 0.88f)
                        }

                        val borderModifier = when {
                            !inMonth -> Modifier
                            isSelected -> Modifier.border(2.dp, if (hasPastelBg) PlannerInk else Color.White, RoundedCornerShape(13.dp))
                            isToday -> Modifier.border(1.2.dp, if (hasPastelBg) PlannerInk.copy(alpha = 0.70f) else Color.White.copy(alpha = 0.80f), RoundedCornerShape(13.dp))
                            else -> Modifier.border(0.5.dp, if (hasPastelBg) PlannerInk.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.06f), RoundedCornerShape(13.dp))
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
                                val indicatorColors = remember(colorEvents, customCategories) {
                                    resolveDayIndicatorColors(colorEvents, customCategories)
                                }
                                if (indicatorColors.isNotEmpty()) {
                                    CalendarDots(
                                        colors = indicatorColors,
                                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 7.dp),
                                        onLight = hasPastelBg
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

        // 13. Selected Day Details Header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${selectedDay.dayOfMonth} ${selectedDay.format(DateTimeFormatter.ofPattern("MMMM", PlannerLocale))} · ${plannerDayLabel(selectedDay)}",
                color = Color.White,
                style = PlannerType.selectedDayHeader,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${selectedDayEvents.size} etkinlik",
                color = Color(0xFF909396),
                fontSize = 13.sp
            )
        }

        // Selected Day Event List
        if (selectedDayEvents.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                selectedDayEvents.forEach { event ->
                    val visual = plannerEventCategoryVisual(event, customCategories)
                    val endMillis = event.endMillis
                    val timeRange = when {
                        event.isDateOnly24Hour -> "24 hour"
                        endMillis != null -> formatTimeRangeAmPm(
                            event.startMillis,
                            minOf(endMillis, selectedDay.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()),
                            ZoneId.systemDefault()
                        )
                        else -> formatTimeAmPm(event.startMillis, ZoneId.systemDefault())
                    }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .plannerEventMotion(event, "month:$selectedDay") { onOpenEvent(event.id, event.startMillis) }
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF1B1C1E))
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(visual.dotColor)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            event.reminder.text,
                            color = Color.White,
                            style = PlannerType.eventListTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            timeRange,
                            color = Color(0xFF8E9094),
                            style = PlannerType.eventListTime
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "›",
                            color = Color(0xFF6E7175),
                            fontSize = 18.sp
                        )
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
                                    .background(cat.dotColor)
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
