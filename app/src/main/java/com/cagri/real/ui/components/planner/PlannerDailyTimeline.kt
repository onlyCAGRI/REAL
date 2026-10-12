package com.cagri.real.ui.components.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.data.CustomCategory
import com.cagri.real.ui.components.ActionGlyph
import com.cagri.real.ui.components.ActionIcon
import com.cagri.real.ui.components.CategoryGlyph
import com.cagri.real.ui.components.CategoryIcon
import com.cagri.real.ui.components.Glyph
import com.cagri.real.ui.components.ProfileAvatar
import com.cagri.real.ui.components.CategoryIconGlyph
import java.time.LocalDate
import java.time.ZoneId

/** The existing lane allocator and minute clock, with the time axis displayed vertically. */
@Composable
internal fun PlannerDailyTimeline(
    day: LocalDate,
    events: List<PlannerEvent>,
    geometry: PlannerTimelineGeometry,
    zone: ZoneId,
    today: LocalDate,
    categories: List<CustomCategory>,
    ready: Boolean,
    onOpen: (Long, Long) -> Unit,
    onAdd: (LocalDate, Int) -> Unit,
    ownerName: String = "",
    intro: PlannerIntro? = null
) {
    val eventMap = remember(events) { events.associateBy { it.occurrenceKey() } }
    val start = remember(day, zone) { day.atStartOfDay(zone).toInstant() }
    val lanes = maxOf(1, geometry.laneCount)
    // Each group of mutually overlapping events shares the width; a lone event gets the full width.
    val clusterLanes = remember(geometry) {
        val result = HashMap<String, Int>()
        val sorted = geometry.placements.sortedBy { it.interval.startMinute }
        var group = mutableListOf<PlannerTimePlacement>()
        var groupEnd = -1f
        fun flush() {
            val n = (group.maxOfOrNull { it.lane } ?: -1) + 1
            group.forEach { result[it.interval.key] = maxOf(1, n) }
            group = mutableListOf()
        }
        sorted.forEach { placement ->
            if (group.isNotEmpty() && placement.interval.startMinute >= groupEnd) { flush(); groupEnd = -1f }
            group.add(placement)
            groupEnd = maxOf(groupEnd, placement.interval.endMinute)
        }
        if (group.isNotEmpty()) flush()
        result
    }
    // Only hours that actually contain a task get a label / rule.
    val occupiedHours = remember(geometry) {
        geometry.placements.flatMap { placement ->
            val first = (placement.interval.startMinute / 60f).toInt()
            val last = kotlin.math.ceil(placement.interval.endMinute / 60.0).toInt()
            (first until maxOf(last, first + 1)).toList()
        }.toSet()
    }
    // The empty (compact) hours before the first task are trimmed so the first card sits right
    // under the "SCHEDULE" label, like the reference, instead of after a long blank run.
    val leadTrim = remember(geometry, occupiedHours) {
        val first = occupiedHours.minOrNull()
        if (first == null) 0f
        else geometry.boundaries[(first - geometry.startHour).coerceIn(0, geometry.boundaries.size - 1)]
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val hourGutter = 54.dp
        // Many overlaps can scroll horizontally instead of becoming illegibly thin cards.
        val laneWidth = ((maxWidth - hourGutter) / lanes).coerceAtLeast(128.dp)
        val canvasWidth = maxOf(maxWidth, hourGutter + laneWidth * lanes)
        val viewportWidth = maxWidth
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Box(Modifier.width(canvasWidth).height((geometry.width - leadTrim).dp + 8.dp).clipToBounds()) {
            Box(Modifier.offset(y = -leadTrim.dp).width(canvasWidth).height(geometry.width.dp + 8.dp)) {
                Box(Modifier.matchParentSize().drawBehind {
                    geometry.boundaries.forEachIndexed { index, boundary ->
                        if ((geometry.startHour + index) !in occupiedHours) return@forEachIndexed
                        val y = boundary.dp.toPx()
                        drawLine(Color(0xFF1C1C20), Offset(hourGutter.toPx(), y), Offset(size.width, y), 0.5.dp.toPx())
                    }
                })
                for (hour in geometry.startHour until geometry.endHour) {
                    if (hour !in occupiedHours) continue
                    val time = start.plusSeconds(hour * 3600L).atZone(zone)
                    val y = geometry.x(hour * 60f).dp
                    Text(
                        time.format(PlannerTimeFormat), color = Color(0xFF777780), fontSize = 11.sp,
                        modifier = Modifier.offset(y = y).width(50.dp).height(44.dp)
                            .clickable(enabled = ready, role = Role.Button) { onAdd(day, time.hour) }
                            .semantics { contentDescription = "Add event at ${time.format(PlannerTimeFormat)}" }
                            .padding(top = 3.dp, start = 4.dp)
                    )
                }
                geometry.placements.forEachIndexed { eventIndex, placement ->
                    val event = eventMap[placement.interval.key] ?: return@forEachIndexed
                    // Events pop in one after another (small -> full size), motion only.
                    val pop: Modifier = if (intro != null)
                        Modifier.plannerIntro(intro, 460 + minOf(eventIndex, 5) * 70, 480,
                            slideY = 12.dp, fromScale = 0.8f, easing = PlannerPopEase)
                    else Modifier
                    val y = geometry.x(placement.interval.startMinute)
                    val geometryHeight = geometry.x(placement.interval.endMinute) - y
                    // Events without a duration are a bit taller, but never into the next card of their lane.
                    val noDuration = event.endMillis == null && !event.isDateOnly24Hour
                    val rawHeight = if (noDuration) {
                        val nextY = geometry.placements
                            .filter { it.lane == placement.lane && it.interval.startMinute > placement.interval.startMinute }
                            .minOfOrNull { geometry.x(it.interval.startMinute) }
                        val room = if (nextY != null) nextY - y - 4f else Float.MAX_VALUE
                        maxOf(geometryHeight, minOf(48f, room))
                    } else geometryHeight
                    val done = event.reminder.isCompleted
                    val height = rawHeight
                    val surface = plannerSoftPastel(resolveNext24CardColor(event, categories))
                    val eventCategory = event.reminder.customCategoryId
                        ?.let { id -> categories.firstOrNull { it.id == id } }
                    val groupLanes = clusterLanes[placement.interval.key] ?: 1
                    val slotWidth = ((viewportWidth - hourGutter) / groupLanes).coerceAtLeast(128.dp)
                    if (done) {
                        // Completed: a small pill sized to logo + title only (like the reference "Make edits").
                        Row(
                            Modifier.offset(x = hourGutter + slotWidth * placement.lane, y = y.dp)
                                .widthIn(max = slotWidth - 6.dp).height(38.dp)
                                .then(pop)
                                .alpha(0.62f)
                                .plannerEventMotion(event, "new-daily-timeline") { onOpen(event.id, event.startMillis) }
                                .clip(CircleShape).background(surface)
                                .padding(start = 7.dp, end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlannerEventLogo(eventCategory, event.reminder.isImportant, 24.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(event.reminder.text, color = PlannerInk, fontSize = 14.sp, maxLines = 1,
                                letterSpacing = (-0.2).sp, overflow = TextOverflow.Ellipsis)
                        }
                        return@forEachIndexed
                    }
                    Column(
                        Modifier.offset(x = hourGutter + slotWidth * placement.lane, y = y.dp)
                            .width(slotWidth - 6.dp).height(height.dp)
                            .then(pop)
                            .plannerEventMotion(event, "new-daily-timeline") { onOpen(event.id, event.startMillis) }
                            .clip(RoundedCornerShape(if (height < 30f) 8.dp else 22.dp))
                            .background(surface)
                            .padding(horizontal = 14.dp, vertical = when {
                                height >= 76f -> 10.dp
                                height >= 64f -> 12.dp
                                height >= 48f -> 7.dp
                                else -> 3.dp
                            }),
                        verticalArrangement = if (height >= 76f) Arrangement.Top else Arrangement.Center
                    ) {
                        val people = event.details?.people.orEmpty()
                            .filter { it.isNotBlank() && !it.equals(ownerName, ignoreCase = true) }
                        val roomy = height >= 76f
                        val medium = !roomy && height >= 44f
                        val logoSize = if (roomy) 30.dp else if (medium) 24.dp else 20.dp
                        val avatarSize = if (roomy) 30.dp else if (medium) 24.dp else 20.dp
                        val maxPeople = if (groupLanes > 1) 1 else 2
                        // Same rule as the 24 hour card: real profile avatar first, then the other people.
                        val avatars: @Composable () -> Unit = {
                            PlannerCardAvatars(people, avatarSize, maxPeople, surface)
                        }
                        val logo: @Composable () -> Unit = {
                            PlannerEventLogo(eventCategory, event.reminder.isImportant, logoSize)
                        }
                        val titleSize = when {
                            groupLanes > 1 -> if (roomy) 14.sp else 12.sp
                            roomy -> 18.sp
                            medium -> 15.sp
                            else -> 13.sp
                        }
                        val title: @Composable (Modifier) -> Unit = { mod ->
                            Text(
                                event.reminder.text, color = PlannerInk, fontSize = titleSize,
                                lineHeight = titleSize * 1.15f, letterSpacing = (-0.3).sp,
                                fontWeight = FontWeight.Normal,
                                // With the time line underneath, a 1-hour card has room for one title line only.
                                maxLines = if (height >= 160f) 3 else if (height >= 124f) 2 else 1,
                                overflow = TextOverflow.Ellipsis, modifier = mod
                            )
                        }
                        // Cards of a custom category always show their time / duration.
                        // Every card that has room shows its time range (calendar events included).
                        val showTime = height >= 44f
                        val timeText: @Composable () -> Unit = {
                            Text(plannerDailyTime(event, zone), color = PlannerInk.copy(alpha = 0.6f),
                                fontSize = if (roomy) 12.sp else 11.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                        }
                        if (roomy) {
                            // Logo top-left, avatars top-right, text anchored to the bottom.
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                logo()
                                Spacer(Modifier.weight(1f))
                                avatars()
                            }
                            Spacer(Modifier.weight(1f))
                            title(Modifier)
                            if (showTime) timeText()
                        } else if (height >= 30f) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                logo()
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    title(Modifier)
                                    if (showTime) timeText()
                                }
                                Spacer(Modifier.width(6.dp))
                                avatars()
                            }
                        } else {
                            title(Modifier)
                        }
                    }
                }
                // The blue "now" line used to be drawn here; it was removed on purpose
                // (it cut across the card that is currently running).
            }
            }
        }
    }
}

/**
 * Small tinted circle (darker than the card, like the reference) with the card's logo:
 * custom category icon, else the star for starred cards, else the planner (calendar) logo.
 */
@Composable
internal fun PlannerEventLogo(category: CustomCategory?, starred: Boolean, size: androidx.compose.ui.unit.Dp) {
    val icon = size * 0.52f
    Box(Modifier.size(size).clip(CircleShape).background(PlannerInk.copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center) {
        when {
            category != null ->
                CategoryIconGlyph(CategoryIcon.fromKey(category.iconKey), Modifier.size(icon), PlannerInk)
            starred -> ActionIcon(ActionGlyph.Star, Modifier.size(icon), PlannerInk)
            else -> CategoryGlyph(Glyph.Calendar, Modifier.size(icon), PlannerInk)
        }
    }
}

/**
 * Same as the 24 hour card: the user's real ProfileAvatar first, then the other people as
 * overlapping initials circles ("+N" past [maxPeople]).
 */
@Composable
private fun PlannerCardAvatars(
    people: List<String>, size: androidx.compose.ui.unit.Dp, maxPeople: Int, surface: Color
) {
    val shown = people.take(maxPeople)
    val extra = people.size - shown.size
    Row(horizontalArrangement = Arrangement.spacedBy(-(size * 0.27f)), verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics { contentDescription = people.joinToString(", ") }) {
        ProfileAvatar(Modifier.size(size).border(1.5.dp, Color.White.copy(alpha = 0.70f), CircleShape))
        shown.forEachIndexed { index, name ->
            Box(Modifier.size(size).clip(CircleShape)
                .background(if (index == 0) Color(0xFFE9E0D3) else Color(0xFF596361))
                .border(1.5.dp, Color.White.copy(alpha = 0.65f), CircleShape),
                contentAlignment = Alignment.Center) {
                Text(plannerInitials(name).ifEmpty { "?" },
                    color = if (index == 0) PlannerInk else Color.White,
                    fontSize = (size.value * 0.36f).sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
        if (extra > 0) {
            Box(Modifier.size(size).clip(CircleShape).background(PlannerInk)
                .border(1.5.dp, surface, CircleShape), contentAlignment = Alignment.Center) {
                Text("+$extra", color = Color.White, fontSize = (size.value * 0.34f).sp, maxLines = 1)
            }
        }
    }
}
