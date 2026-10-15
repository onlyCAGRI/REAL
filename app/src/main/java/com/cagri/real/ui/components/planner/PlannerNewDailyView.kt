package com.cagri.real.ui.components.planner

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.toSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.data.CustomCategory
import com.cagri.real.ui.theme.RealType
import com.cagri.real.data.ReminderCategory
import com.cagri.real.data.ReminderItem
import com.cagri.real.ui.components.CategoryGlyph
import com.cagri.real.ui.components.CategoryIcon
import com.cagri.real.ui.components.CategoryIconGlyph
import com.cagri.real.ui.components.CategoryProgressStats
import com.cagri.real.ui.components.ReminderColorChoices
import com.cagri.real.ui.components.categoryProgress
import com.cagri.real.ui.components.glyph
import com.cagri.real.ui.components.ActionGlyph
import com.cagri.real.ui.components.ActionIcon
import kotlinx.coroutines.flow.filter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/** REAL palette, only a touch softer/creamier so cards read as pastel on AMOLED black. */
internal fun plannerSoftPastel(base: Color): Color = lerp(base, Color(0xFFFFFBF4), 0.24f)

/** Presentation of the parent Planner's date/events, never an independent calendar state. */
@Composable
internal fun PlannerNewDailyView(
    selectedDay: LocalDate,
    today: LocalDate,
    events: List<PlannerEvent>,
    zone: ZoneId,
    ownerName: String,
    /** All categories: event colours and names are resolved from this list. */
    customCategories: List<CustomCategory>,
    topPadding: Dp,
    ready: Boolean,
    reduceAnimations: Boolean,
    now: Instant,
    onSelectDay: (LocalDate) -> Unit,
    onOpen: (Long, Long) -> Unit,
    onAdd: (LocalDate, Int) -> Unit,
    reminders: List<ReminderItem> = emptyList(),
    onOpenCategory: (ReminderCategory?, Long?) -> Unit = { _, _ -> },
    categoryShapes: Map<Long, PlannerCategoryCardShape> = emptyMap(),
    revealOrigin: PlannerRevealOrigin? = null,
    /** Categories whose card appears in the category carousel ("REAL takviminde göster"). */
    categoryCardCategories: List<CustomCategory> = customCategories
) {
    val focus = remember(events, selectedDay, zone) { plannerFocusEvents(events, selectedDay, zone) }
    val dateOnly = remember(events, selectedDay, zone) {
        val dayStart = selectedDay.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = selectedDay.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        events.filter { it.isDateOnly24Hour && if (it.externalEndMillis != null)
            it.startMillis < dayEnd && it.externalEndMillis > dayStart else it.day(zone) == selectedDay }
    }
    val geometry = remember(events, selectedDay, zone) { plannerDailyGeometry(events, selectedDay, zone) }
    val categoryCards = remember(reminders, categoryCardCategories, today, zone, categoryShapes) {
        plannerCategoryCards(reminders, categoryCardCategories, today, zone, categoryShapes)
    }
    val carousel = rememberLazyListState()
    // Entrance choreography (motion only): greeting -> heading -> cards from the right -> ruler -> schedule -> events pop in.
    val intro = rememberPlannerIntro(reduceAnimations)
    // Coming back from a category screen: title, cards, ruler and events animate in again (like the reference video).
    val returnTick = revealOrigin?.returnTick ?: 0
    LaunchedEffect(returnTick) { if (returnTick > 0 && !reduceAnimations) intro.restart(120L) }
    LaunchedEffect(selectedDay) { carousel.scrollToItem(0) }
    LazyColumn(
        Modifier.fillMaxSize().background(Color.Black),
        contentPadding = PaddingValues(top = topPadding + 24.dp, bottom = 28.dp)
    ) {
        item("greeting") {
            Text(
                plannerFirstName(ownerName)?.let { "Hello, $it" } ?: "Hello",
                style = RealType.editorialSubtitle,
                modifier = Modifier.plannerIntro(intro, 0, 420, slideY = 8.dp).padding(horizontal = 8.dp)
            )
        }
        item("heading") {
            AnimatedContent(
                targetState = plannerFocusHeading(selectedDay, today, focus.size),
                modifier = Modifier.plannerIntro(intro, 70, 560, slideY = 14.dp),
                transitionSpec = {
                    fadeIn(tween(if (reduceAnimations) 0 else 140)) togetherWith
                        fadeOut(tween(if (reduceAnimations) 0 else 90))
                }, label = "daily-focus-heading"
            ) { heading ->
                Text(
                    heading,
                    style = RealType.editorialTitle,
                    modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 20.dp)
                )
            }
        }
        // Starred cards live on top, next card peeks in from the right.
        if (focus.isNotEmpty() || categoryCards.isNotEmpty()) {
            item("focus") {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val cardWidth = maxWidth * 0.72f
                    LazyRow(
                        state = carousel,
                        contentPadding = PaddingValues(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(focus, key = { _, e -> e.occurrenceKey() }) { index, event ->
                            PlannerFocusCard(
                                event, zone, customCategories, now, reduceAnimations, cardWidth,
                                modifier = Modifier.plannerIntro(
                                    intro, 150 + minOf(index, 3) * 90, 560, slideX = 90.dp, fromScale = 0.96f
                                )
                            ) {
                                onOpen(event.id, event.startMillis)
                            }
                        }
                        itemsIndexed(categoryCards, key = { _, c -> "category:${c.key}" }) { index, card ->
                            // Rectangle cards are wide and short; square ones keep the usual carousel width.
                            val width = if (card.shape == PlannerCategoryCardShape.Rectangle) maxWidth * 0.92f else cardWidth
                            PlannerCategoryCard(
                                card, reduceAnimations, width,
                                modifier = Modifier.plannerIntro(
                                    intro, 150 + minOf(focus.size + index, 3) * 90, 560, slideX = 90.dp, fromScale = 0.96f
                                ),
                                revealOrigin = revealOrigin
                            ) {
                                onOpenCategory(card.builtIn, card.custom?.id)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
        item("week") {
            Box(Modifier.plannerIntro(intro, 300, 440, slideY = 10.dp)) {
                PlannerFocusDateRuler(selectedDay, today, reduceAnimations, onSelectDay)
            }
        }
        item("schedule-label") {
            BoxWithConstraints(Modifier.fillMaxWidth().plannerIntro(intro, 380, 420, slideY = 10.dp)) {
                val width = maxWidth
                val pillWidth = if (selectedDay == today) TodayPillWidth else BackToTodayPillWidth
                val pillX = (width * RulerAnchorFraction - pillWidth / 2)
                    .coerceIn(0.dp, (width - pillWidth).coerceAtLeast(0.dp))
                Column(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().heightIn(min = TodayPillHeight)) {
                        // SCHEDULE with ALL DAY directly underneath (when the day has all-day notes).
                        Column(Modifier.align(Alignment.CenterStart).padding(start = 8.dp)) {
                            Text("SCHEDULE", style = RealType.editorialMeta)
                            if (dateOnly.isNotEmpty()) {
                                Text("ALL DAY", style = RealType.editorialMeta,
                                    modifier = Modifier.padding(top = 5.dp))
                            }
                            if (geometry.laneCount > 2) {
                                Text(
                                    "Swipe across overlaps",
                                    style = RealType.editorialMeta.copy(letterSpacing = 0.sp),
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                        Box(Modifier.align(Alignment.CenterStart).offset(x = pillX)) {
                            PlannerTodayPill(selectedDay, today, onSelectDay)
                        }
                    }
                    // The fading stem under "Today" is drawn over the next items instead of reserving space.
                    Box(
                        Modifier.offset(x = width * RulerAnchorFraction - 0.5.dp).width(1.dp).height(0.dp)
                            .wrapContentHeight(Alignment.Top, unbounded = true)
                    ) {
                        Box(Modifier.width(1.dp).height(44.dp)
                            .background(Brush.verticalGradient(listOf(Color(0xFF55555D), Color.Transparent))))
                    }
                    Spacer(Modifier.height(if (dateOnly.isEmpty()) 18.dp else 8.dp))
                }
            }
        }
        if (dateOnly.isNotEmpty()) {
            itemsIndexed(dateOnly, key = { _, it -> "note:${it.occurrenceKey()}" }) { index, event ->
                val done = event.reminder.isCompleted
                // All-day cards pop in like the timed events (small -> full size), just before the timeline.
                val pop = Modifier.plannerIntro(intro, 400 + minOf(index, 5) * 70, 480,
                    slideY = 12.dp, fromScale = 0.8f, easing = PlannerPopEase)
                val surface = plannerSoftPastel(resolveNext24CardColor(event, customCategories))
                if (done) {
                    val category = event.reminder.customCategoryId
                        ?.let { id -> customCategories.firstOrNull { it.id == id } }
                    Row(
                        pop.padding(horizontal = 6.dp, vertical = 3.dp)
                            .alpha(0.62f)
                            .plannerEventMotion(event, "new-day-note") { onOpen(event.id, event.startMillis) }
                            .clip(CircleShape).background(surface)
                            .padding(start = 7.dp, end = 16.dp, top = 7.dp, bottom = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlannerEventLogo(category, event.reminder.isImportant, 24.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(event.reminder.text, color = PlannerInk, fontSize = 14.sp, maxLines = 1,
                            letterSpacing = (-0.2).sp, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 260.dp))
                    }
                } else {
                    Text(
                        event.reminder.text, color = PlannerInk, fontSize = 17.sp, lineHeight = 21.sp,
                        letterSpacing = (-0.3).sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                        modifier = pop.padding(horizontal = 6.dp, vertical = 3.dp)
                            .fillMaxWidth()
                            .plannerEventMotion(event, "new-day-note") { onOpen(event.id, event.startMillis) }
                            .clip(RoundedCornerShape(22.dp))
                            .background(surface)
                            .padding(horizontal = 18.dp, vertical = 16.dp)
                    )
                }
            }
            item("all-day-gap") { Spacer(Modifier.height(14.dp)) }
        }
        item("timeline") {
            Box(Modifier.plannerIntro(intro, 380, 380)) {
                PlannerDailyTimeline(selectedDay, events, geometry, zone, today,
                    customCategories, ready, onOpen, onAdd, ownerName, intro = intro)
            }
        }
    }
}

private data class PlannerCategoryCardData(
    val key: String,
    val name: String,
    val builtIn: ReminderCategory?,
    val custom: CustomCategory?,
    val stats: CategoryProgressStats,
    val surface: Color,
    val shape: PlannerCategoryCardShape = PlannerCategoryCardShape.Square
)

/** Every user-created category (even empty or hidden-from-lists), real categoryProgress over ALL reminders. */
private fun plannerCategoryCards(
    reminders: List<ReminderItem>, categories: List<CustomCategory>, today: LocalDate, zone: ZoneId,
    shapes: Map<Long, PlannerCategoryCardShape> = emptyMap()
): List<PlannerCategoryCardData> = buildList {
    categories.forEach { category ->
        val stats = categoryProgress(reminders, null, category.id, today, zone)
        if (stats != null) {
            val raw = category.customSolidColorArgb?.let { Color(it) }
                ?: ReminderColorChoices.getOrNull(category.accentStart)?.color
                ?: Color(0xFF4558C8)
            add(PlannerCategoryCardData("custom:${category.id}", category.name, null, category, stats,
                plannerSoftPastel(nearestCalendarPastel(raw)), shapes[category.id] ?: PlannerCategoryCardShape.Square))
        }
    }
}

/** Black circle badge: category icon when the card has a category, otherwise the star. */
@Composable
private fun PlannerCardBadge(
    custom: CustomCategory?, builtIn: ReminderCategory?, size: Dp, iconSize: Dp
) {
    Box(Modifier.size(size).clip(CircleShape).background(Color(0xFF0B0B0D)), contentAlignment = Alignment.Center) {
        when {
            custom != null -> CategoryIconGlyph(CategoryIcon.fromKey(custom.iconKey), Modifier.size(iconSize), Color.White)
            builtIn != null -> CategoryGlyph(builtIn.glyph(), Modifier.size(iconSize), Color.White)
            else -> ActionIcon(ActionGlyph.Star, Modifier.size(iconSize), Color.White)
        }
    }
}

@Composable
private fun PlannerProgressPill(label: String, fraction: Float) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(PlannerInk.copy(alpha = 0.07f))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = PlannerInk.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1,
            modifier = Modifier.weight(1f))
        Box(Modifier.width(60.dp).height(4.dp).clip(CircleShape).background(PlannerInk.copy(alpha = 0.14f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).clip(CircleShape)
                .background(PlannerInk))
        }
    }
}

@Composable
private fun PlannerCategoryCard(
    card: PlannerCategoryCardData, reduceAnimations: Boolean, width: Dp,
    modifier: Modifier = Modifier, revealOrigin: PlannerRevealOrigin? = null, onClick: () -> Unit
) {
    val textScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val progress by animateFloatAsState(
        card.stats.fraction,
        animationSpec = if (reduceAnimations) snap() else tween(600), label = "category-card-progress"
    )
    // Press feedback + remembering where the card is, so the detail screen can grow out of it.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        if (pressed && !reduceAnimations) 0.965f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "category-card-press"
    )
    val boundsHolder = remember { PlannerBoundsHolder() }
    Column(
        modifier.width(width)
            .height(
                (if (card.shape == PlannerCategoryCardShape.Rectangle) 196.dp
                else (width * 0.96f).coerceAtLeast(232.dp)) * textScale
            )
            .onGloballyPositioned { boundsHolder.rect = Rect(it.positionInRoot(), it.size.toSize()) }
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .clip(RoundedCornerShape(32.dp))
            .background(card.surface)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button) {
                if (!reduceAnimations) revealOrigin?.bounds = boundsHolder.rect
                onClick()
            }
            .semantics { contentDescription = "${card.name}, ${card.stats.done} of ${card.stats.total} done" }
            .padding(18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PlannerCardBadge(card.custom, card.builtIn, 42.dp, 18.dp)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(42.dp).clip(CircleShape).border(1.dp, PlannerInk.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center) {
                ActionIcon(ActionGlyph.ChevronRight, Modifier.size(16.dp), PlannerInk)
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            card.name,
            style = RealType.editorialItem.copy(color = PlannerInk),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        PlannerProgressPill("${card.stats.done}/${card.stats.total}", progress)
    }
}

private class PlannerBoundsHolder { var rect: Rect = Rect.Zero }

private fun plannerDurationLabel(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

@Composable
private fun PlannerFocusCard(
    event: PlannerEvent, zone: ZoneId, categories: List<CustomCategory>, now: Instant,
    reduceAnimations: Boolean, width: Dp, modifier: Modifier = Modifier, onOpen: () -> Unit
) {
    val visual = plannerEventCategoryVisual(event, categories)
    val eventCategory = event.reminder.customCategoryId?.let { id -> categories.firstOrNull { it.id == id } }
    val surface = plannerSoftPastel(resolveNext24CardColor(event, categories))
    val textScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val start = event.startMillis
    val end = event.endMillis
    val hasSpan = end != null && end > start
    val span = if (hasSpan) end!! - start else 1L
    val nowMs = now.toEpochMilli()
    val targetProgress = when {
        event.isDateOnly24Hour -> 0f
        hasSpan -> ((nowMs - start).toFloat() / span).coerceIn(0f, 1f)
        else -> if (nowMs >= start) 1f else 0f
    }
    val progress by animateFloatAsState(
        targetProgress,
        animationSpec = if (reduceAnimations) snap() else tween(600), label = "focus-card-progress"
    )
    val durationText = when {
        event.isDateOnly24Hour -> "All day"
        hasSpan -> plannerDurationLabel(span / 60_000L)
        else -> "Reminder"
    }

    Column(
        modifier.width(width).height((width * 0.96f).coerceAtLeast(232.dp) * textScale)
            .plannerEventMotion(event, "daily-focus", onOpen)
            .clip(RoundedCornerShape(32.dp))
            .background(surface).padding(18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PlannerCardBadge(eventCategory, null, 42.dp, 18.dp)
            Spacer(Modifier.weight(1f))
            if (eventCategory != null) {
                ActionIcon(ActionGlyph.Star, Modifier.size(16.dp), PlannerInk.copy(alpha = 0.65f))
                Spacer(Modifier.width(10.dp))
            }
            Box(Modifier.size(42.dp).clip(CircleShape).border(1.dp, PlannerInk.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center) {
                ActionIcon(ActionGlyph.ChevronRight, Modifier.size(16.dp), PlannerInk)
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            listOf(visual.name.ifBlank { "REAL" }, plannerDailyTime(event, zone)).joinToString("  ·  "),
            color = PlannerInk.copy(alpha = 0.55f), fontSize = 12.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            event.reminder.text,
            style = RealType.editorialItem.copy(color = PlannerInk),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        )
        PlannerProgressPill(durationText, progress)
    }
}

private const val RulerAnchorFraction = 0.64f
private val TodayPillWidth = 96.dp
private val BackToTodayPillWidth = 104.dp
private val TodayPillHeight = 34.dp

/** "Çağrı Gül" -> "Çağrı"; with three or more words everything but the last one is kept. */
private fun plannerFirstName(full: String): String? {
    val parts = full.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> null
        parts.size >= 3 -> parts.dropLast(1).joinToString(" ")
        else -> parts.first()
    }
}

@Composable
private fun PlannerTodayPill(day: LocalDate, today: LocalDate, onSelect: (LocalDate) -> Unit) {
    Row(
        Modifier.width(if (day == today) TodayPillWidth else BackToTodayPillWidth)
            .height(TodayPillHeight).clip(CircleShape)
            .border(1.dp, Color(0xFF55555D), CircleShape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(24.dp).fillMaxHeight()
            .clickable(role = Role.Button) { onSelect(day.minusDays(1)) }
            .semantics { contentDescription = "Previous day" }, contentAlignment = Alignment.Center) {
            ActionIcon(ActionGlyph.Back, Modifier.size(11.dp), Color(0xFFB4B4BC))
        }
        Box(Modifier.weight(1f).fillMaxHeight()
            .clickable(role = Role.Button) { onSelect(today) }
            .semantics { contentDescription = "Go to today" }, contentAlignment = Alignment.Center) {
            Text(if (day == today) "Today" else "Back to", color = Color.White, fontSize = 12.sp,
                maxLines = 1)
        }
        Box(Modifier.width(24.dp).fillMaxHeight()
            .clickable(role = Role.Button) { onSelect(day.plusDays(1)) }
            .semantics { contentDescription = "Next day" }, contentAlignment = Alignment.Center) {
            ActionIcon(ActionGlyph.ChevronRight, Modifier.size(11.dp), Color(0xFFB4B4BC))
        }
    }
}

private const val RulerSpanDays = 1500
private val RulerItemWidth = 62.dp

/** Faded "Fr 18" ruler; drag it by hand, it snaps and the centered day becomes the selected day. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlannerFocusDateRuler(
    day: LocalDate, today: LocalDate, reduceAnimations: Boolean, onSelect: (LocalDate) -> Unit
) {
    val origin = remember(today) { today.minusDays(RulerSpanDays.toLong()) }
    fun indexOf(d: LocalDate) = ChronoUnit.DAYS.between(origin, d).toInt().coerceIn(0, RulerSpanDays * 2)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = indexOf(day))
    val currentDay by rememberUpdatedState(day)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val centeredIndex by remember {
        derivedStateOf {
            val info = state.layoutInfo
            // Item offsets are measured from the start of the content padding, so the snapped
            // (selected) item is the one whose offset is closest to 0.
            info.visibleItemsInfo.minByOrNull { abs(it.offset) }?.index
                ?: indexOf(currentDay)
        }
    }
    // Hand scroll settles -> centered day becomes the selected day.
    LaunchedEffect(state, origin) {
        snapshotFlow { state.isScrollInProgress }.filter { !it }.collect {
            val picked = origin.plusDays(centeredIndex.toLong())
            if (picked != currentDay) currentOnSelect(picked)
        }
    }
    // Selection changed from outside (tap, Today pill, parent) -> glide the ruler there.
    LaunchedEffect(day, origin) {
        val target = indexOf(day)
        if (target != centeredIndex) {
            if (reduceAnimations) state.scrollToItem(target) else state.animateScrollToItem(target)
        }
    }
    val monthFormat = remember { DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH) }
    val centeredDate = origin.plusDays(centeredIndex.toLong())

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = maxWidth
        val anchorX = width * RulerAnchorFraction
        Column(Modifier.fillMaxWidth()) {
            Text(centeredDate.format(monthFormat), color = Color(0xFF8C8C95), fontSize = 12.sp,
                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp))
            LazyRow(
                state = state,
                flingBehavior = rememberSnapFlingBehavior(state),
                contentPadding = PaddingValues(
                    start = (anchorX - RulerItemWidth / 2).coerceAtLeast(0.dp),
                    end = (width - anchorX - RulerItemWidth / 2).coerceAtLeast(0.dp)
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(RulerSpanDays * 2 + 1, key = { it }) { index ->
                    val date = origin.plusDays(index.toLong())
                    val distance = abs(index - centeredIndex)
                    val active = distance == 0
                    val ink by animateColorAsState(
                        when {
                            active -> Color.White
                            distance == 1 -> Color(0xFF7C7C85)
                            else -> Color(0xFF4F4F57)
                        },
                        animationSpec = if (reduceAnimations) snap() else tween(140), label = "ruler-ink"
                    )
                    val scale by animateFloatAsState(
                        if (active) 1.22f else 1f,
                        animationSpec = if (reduceAnimations) snap() else tween(160), label = "ruler-scale"
                    )
                    Column(
                        Modifier.width(RulerItemWidth).height(44.dp)
                            .clickable(role = Role.Tab) { onSelect(date) }
                            .semantics { selected = active; contentDescription = date.toString() },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
                            verticalAlignment = Alignment.Bottom) {
                            Text(date.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.ENGLISH).take(2),
                                color = ink, fontSize = 11.sp)
                            Spacer(Modifier.width(4.dp))
                            Text(date.dayOfMonth.toString(), color = ink, fontSize = 17.sp,
                                fontWeight = if (active) FontWeight.Medium else FontWeight.Normal)
                        }
                        Box(Modifier.padding(top = 5.dp).size(3.dp).clip(CircleShape)
                            .background(if (date == today && !active) RealCalendarPastels[9] else Color.Transparent))
                    }
                }
            }
            // Dot + short stem under the selected day; the stem continues (fading) below "Today".
            Box(Modifier.offset(x = anchorX - 3.dp).size(6.dp).clip(CircleShape).background(Color.White))
            Box(Modifier.offset(x = anchorX - 0.5.dp).width(1.dp).height(14.dp).background(Color(0xFF55555D)))
        }
    }
}
