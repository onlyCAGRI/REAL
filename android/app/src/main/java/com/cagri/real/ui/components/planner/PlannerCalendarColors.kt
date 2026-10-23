package com.cagri.real.ui.components.planner

import com.cagri.real.i18n.t
import androidx.compose.ui.graphics.Color
import com.cagri.real.data.CustomCategory
import com.cagri.real.ui.components.ReminderColorChoices
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

/**
 * Fixed 10-color pastel palette for REAL calendar.
 * Every calendar pastel, daily surface, day tile, and decorative assignment resolves from this palette.
 */
val RealCalendarPastels = listOf(
    Color(0xFFF7E1E6), // Blush
    Color(0xFFFFD6A5), // Peach
    Color(0xFFE6D6F7), // Lilac
    Color(0xFFD6EAD4), // Sage
    Color(0xFFFEF3B0), // Lemon
    Color(0xFFE6D6E7), // Mauve
    Color(0xFFFFC1B6), // Coral
    Color(0xFFD0F0ED), // Aqua
    Color(0xFFEAD8B0), // Sand
    Color(0xFFDCE6F7)  // Sky
)

val RealDayCardPastels = RealCalendarPastels

internal fun realDayCardSurface(day: LocalDate): Color =
    RealCalendarPastels[Math.floorMod(day.toEpochDay().toInt(), RealCalendarPastels.size)]

data class PlannerCategoryVisual(
    val name: String,
    val surfacePastel: Color,
    val capsuleColor: Color,
    val dotColor: Color,
    /** The category's (or card's) own colour, unchanged: the monthly calendar and goal screen use it. */
    val realColor: Color = surfacePastel
) {
    val pastelColor: Color get() = capsuleColor
}

private fun rgbToHsl(r: Float, g: Float, b: Float, hsl: FloatArray) {
    val max = maxOf(r, maxOf(g, b))
    val min = minOf(r, minOf(g, b))
    val delta = max - min
    val l = (max + min) / 2f
    val s = if (delta == 0f) 0f else if (l <= 0.5f) delta / (max + min) else delta / (2f - max - min)
    val h = when {
        delta == 0f -> 0f
        max == r -> ((g - b) / delta + (if (g < b) 6f else 0f)) * 60f
        max == g -> ((b - r) / delta + 2f) * 60f
        else -> ((r - g) / delta + 4f) * 60f
    }
    hsl[0] = h
    hsl[1] = s
    hsl[2] = l
}

private fun hslToRgb(h: Float, s: Float, l: Float): Color {
    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r1, g1, b1) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        (r1 + m).coerceIn(0f, 1f),
        (g1 + m).coerceIn(0f, 1f),
        (b1 + m).coerceIn(0f, 1f),
        1f
    )
}

/**
 * Deterministically maps any raw category color to the nearest matching pastel from RealCalendarPastels.
 */
internal fun nearestCalendarPastel(rawColor: Color): Color {
    val rawHsl = FloatArray(3)
    rgbToHsl(rawColor.red, rawColor.green, rawColor.blue, rawHsl)
    val rawHue = rawHsl[0]
    val rawSat = rawHsl[1]

    if (rawSat < 0.08f) {
        return Color(0xFFEAD8B0) // Sand for neutral/gray tones
    }

    var bestPastel = RealCalendarPastels[0]
    var minDistance = Float.MAX_VALUE

    for (pastel in RealCalendarPastels) {
        val pastelHsl = FloatArray(3)
        rgbToHsl(pastel.red, pastel.green, pastel.blue, pastelHsl)
        val pastelHue = pastelHsl[0]
        val dHue = abs(rawHue - pastelHue).let { minOf(it, 360f - it) }
        val distance = dHue + abs(rawSat - pastelHsl[1]) * 20f
        if (distance < minDistance) {
            minDistance = distance
            bestPastel = pastel
        }
    }
    return bestPastel
}

/**
 * Maps raw category color into a soft, calm pastel for Day Card and Month Tile surfaces.
 * Strictly resolves to the nearest pastel from the fixed 10-color palette.
 */
internal fun categoryToDaySurfacePastel(rawColor: Color): Color = nearestCalendarPastel(rawColor)

/**
 * Maps raw category color into a clearly more saturated, slightly deeper event capsule color.
 * Preserves the exact palette family hue while sitting distinctly on top of the host day surface.
 */
internal fun categoryToEventCapsuleColor(rawColor: Color): Color {
    val pastel = nearestCalendarPastel(rawColor)
    val hsl = FloatArray(3)
    rgbToHsl(pastel.red, pastel.green, pastel.blue, hsl)
    val s = (hsl[1] * 1.45f + 0.22f).coerceIn(0.52f, 0.76f)
    val l = 0.65f // deeper, richer tone within same palette family with high contrast PlannerInk legibility
    return hslToRgb(hsl[0], s, l)
}

/**
 * Maps raw category color into a strong, saturated dot indicator color within the same palette family.
 */
internal fun categoryToEventDotColor(rawColor: Color): Color {
    val pastel = nearestCalendarPastel(rawColor)
    val hsl = FloatArray(3)
    rgbToHsl(pastel.red, pastel.green, pastel.blue, hsl)
    val s = (hsl[1] * 1.55f + 0.28f).coerceIn(0.60f, 0.82f)
    val l = 0.58f // crisp saturated dot tone
    return hslToRgb(hsl[0], s, l)
}

/** Backward compatibility alias */
internal fun categoryToCalendarPastel(rawColor: Color): Color = categoryToDaySurfacePastel(rawColor)

/** Uncategorized card without its own colour: a REAL palette colour picked from its id (stable, varied). */
internal fun uncategorizedRealColor(id: Long): Color =
    com.cagri.real.ui.components.ReminderColorDisplayOrder.filter { it != 1 }.let { order -> ReminderColorChoices[order[Math.floorMod(id.hashCode(), order.size)]].color }

/** Deterministic decorative pastel from the fixed palette for uncategorized events. */
internal fun uncategorizedDecorativePastel(id: Long): Color {
    val index = Math.floorMod(id.hashCode(), RealCalendarPastels.size)
    return RealCalendarPastels[index]
}

/**
 * Resolves the semantic "Gelen kutu" category visual using the fixed palette.
 */
internal fun resolveGelenKutuVisual(customCategories: List<CustomCategory> = emptyList()): PlannerCategoryVisual {
    val matched = customCategories.firstOrNull {
        val n = it.name.trim().lowercase(PlannerLocale)
        n == "gelen kutu" || n == "gelen kutusu" || n == "gelen" || n == "inbox"
    }
    val rawColor = if (matched != null) {
        matched.customSolidColorArgb?.let {
            Color(
                ((it shr 16) and 0xFF) / 255f,
                ((it shr 8) and 0xFF) / 255f,
                (it and 0xFF) / 255f,
                1f
            )
        } ?: ReminderColorChoices.getOrNull(matched.accentStart)?.color
            ?: Color(0xFF4558C8)
    } else {
        ReminderColorChoices.getOrNull(2)?.color ?: Color(0xFF4558C8)
    }
    val label = matched?.name ?: t("Gelen kutu")
    val pastel = nearestCalendarPastel(rawColor)
    return PlannerCategoryVisual(
        name = label,
        surfacePastel = pastel,
        capsuleColor = categoryToEventCapsuleColor(rawColor),
        dotColor = categoryToEventDotColor(rawColor)
    )
}

internal fun plannerCalendarPastel(category: CustomCategory?): Color {
    if (category == null) return resolveGelenKutuVisual().capsuleColor
    val base = category.customSolidColorArgb?.let {
        Color(
            ((it shr 16) and 0xFF) / 255f,
            ((it shr 8) and 0xFF) / 255f,
            (it and 0xFF) / 255f,
            1f
        )
    } ?: ReminderColorChoices.getOrNull(category.accentStart)?.color ?: Color(0xFF4558C8)
    return categoryToEventCapsuleColor(base)
}

/**
 * Resolves the category visual for a planner event:
 * - Categorized event: maps category color to nearest pastel family from the fixed 10-color palette.
 * - Uncategorized event: deterministically assigns a decorative pastel from the same fixed palette without fake categories.
 */
internal fun plannerEventCategoryVisual(
    event: PlannerEvent,
    customCategories: List<CustomCategory> = emptyList()
): PlannerCategoryVisual {
    // 1. Explicitly assigned custom category
    if (event.reminder.customCategoryId != null) {
        val cat = customCategories.firstOrNull { it.id == event.reminder.customCategoryId }
        if (cat != null && cat.name.isNotBlank()) {
            val base = cat.customSolidColorArgb?.let {
                Color(
                    ((it shr 16) and 0xFF) / 255f,
                    ((it shr 8) and 0xFF) / 255f,
                    (it and 0xFF) / 255f,
                    1f
                )
            } ?: ReminderColorChoices.getOrNull(cat.accentStart)?.color
                ?: Color(0xFF4558C8)
            val pastel = nearestCalendarPastel(base)
            return PlannerCategoryVisual(
                name = cat.name,
                surfacePastel = pastel,
                capsuleColor = categoryToEventCapsuleColor(base),
                dotColor = categoryToEventDotColor(base),
                realColor = base
            )
        }
    }

    // 2. Explicit custom solid color on the reminder item itself if no category assigned
    if (event.reminder.customSolidColorArgb != null) {
        val base = Color(event.reminder.customSolidColorArgb)
        val pastel = nearestCalendarPastel(base)
        return PlannerCategoryVisual(
            name = "",
            surfacePastel = pastel,
            capsuleColor = categoryToEventCapsuleColor(base),
            dotColor = categoryToEventDotColor(base),
            realColor = base
        )
    }

    // 3. Explicit accentStart on reminder item itself if no category assigned
    if (event.reminder.accentStart != null) {
        val base = ReminderColorChoices.getOrNull(event.reminder.accentStart)?.color ?: Color(0xFF4558C8)
        val pastel = nearestCalendarPastel(base)
        return PlannerCategoryVisual(
            name = "",
            surfacePastel = pastel,
            capsuleColor = categoryToEventCapsuleColor(base),
            dotColor = categoryToEventDotColor(base),
            realColor = base
        )
    }

    // 4. Uncategorized event:
    // Deterministic decorative pastel from the fixed palette. Do NOT assign fake categories.
    val decPastel = uncategorizedDecorativePastel(event.id)
    return PlannerCategoryVisual(
        name = "",
        surfacePastel = decPastel,
        capsuleColor = categoryToEventCapsuleColor(decPastel),
        dotColor = categoryToEventDotColor(decPastel),
        realColor = uncategorizedRealColor(event.id)
    )
}

/**
 * Special rule for the "Plan" category:
 * Events in the "Plan" category inherit the visual family of the hosting day card
 * (a slightly darkened and more saturated tone in that same family), with dark ink text.
 */
internal fun planCapsuleFamilyColor(dayCardSurface: Color): Color {
    val hsl = FloatArray(3)
    rgbToHsl(dayCardSurface.red, dayCardSurface.green, dayCardSurface.blue, hsl)
    val s = (hsl[1] * 1.45f + 0.22f).coerceIn(0.50f, 0.76f)
    val l = (hsl[2] - 0.18f).coerceIn(0.58f, 0.68f)
    return hslToRgb(hsl[0], s, l)
}

/**
 * Resolves the dominant category pastel color for a day:
 * - Empty day = null (caller uses dark neutral tile / decorative day pastel fallback)
 * - Day with events = surface pastel color of the day's dominant category
 *
 * Dominant category is determined by TOTAL SCHEDULED MINUTES on that date, not raw event count.
 * If the day contains mostly uncategorized events, falls back to REAL's Gelen kutu pastel.
 * Tie-breaking:
 * 1. Longest single event
 * 2. Earliest event start on that day
 */
internal fun resolveDominantDayCategoryPastel(
    events: List<PlannerEvent>,
    customCategories: List<CustomCategory> = emptyList(),
    /** Monthly calendar: the dominant category's own colour instead of its pastel. */
    realColors: Boolean = false
): Color? {
    if (events.isEmpty()) return null

    class CategoryStats(
        val visual: PlannerCategoryVisual,
        var totalMinutes: Long = 0L,
        var maxSingleMinutes: Long = 0L,
        var earliestStart: Long = Long.MAX_VALUE
    )

    val statsMap = mutableMapOf<String, CategoryStats>()

    for (event in events) {
        val visual = plannerEventCategoryVisual(event, customCategories)
        val durationMin: Long = if (event.isDateOnly24Hour) {
            15L
        } else {
            val d = event.details?.durationMinutes?.toLong()
            val end = event.endMillis
            if (d != null && d > 0) {
                d
            } else if (end != null && end > event.startMillis) {
                maxOf(1L, (end - event.startMillis) / 60_000L)
            } else {
                1L
            }
        }
        val key = visual.name.lowercase(PlannerLocale)
        val stats = statsMap.getOrPut(key) {
            CategoryStats(visual = visual)
        }
        stats.totalMinutes += durationMin
        if (durationMin > stats.maxSingleMinutes) {
            stats.maxSingleMinutes = durationMin
        }
        if (event.startMillis < stats.earliestStart) {
            stats.earliestStart = event.startMillis
        }
    }

    val dominant = statsMap.values.sortedWith(
        compareByDescending<CategoryStats> { it.totalMinutes }
            .thenByDescending { it.maxSingleMinutes }
            .thenBy { it.earliestStart }
    ).firstOrNull()

    return if (realColors) dominant?.visual?.realColor else dominant?.visual?.surfacePastel
}

/** 12-hour am/pm hour formatting: "5 pm", "12 pm", "9 am", "11 am" */
internal fun formatHourAmPm(hour: Int): String {
    val mod = (hour % 24 + 24) % 24
    val h = when (mod) {
        0 -> 12
        in 1..12 -> mod
        else -> mod - 12
    }
    val amPm = if (mod < 12) "am" else "pm"
    return "$h $amPm"
}

/** 12-hour am/pm time formatting: "6:20 pm", "8:30 pm", "6:00 pm", "11:00 am" */
internal fun formatTimeAmPm(millis: Long, zone: ZoneId): String {
    val zdt = Instant.ofEpochMilli(millis).atZone(zone)
    val hour = zdt.hour
    val minute = zdt.minute
    val h = when (hour) {
        0 -> 12
        in 1..12 -> hour
        else -> hour - 12
    }
    val amPm = if (hour < 12) "am" else "pm"
    return String.format(Locale.US, "%d:%02d %s", h, minute, amPm)
}

/** 12-hour am/pm range formatting: "6:20 pm – 8:30 pm" */
internal fun formatTimeRangeAmPm(startMillis: Long, endMillis: Long, zone: ZoneId): String {
    return "${formatTimeAmPm(startMillis, zone)} – ${formatTimeAmPm(endMillis, zone)}"
}

/**
 * Resolves the indicator color for a single planner event in Calendar views:
 * - Category assigned: maps category color to strong dot color
 * - No category / blank: uses REAL's semantic "Gelen kutu" category identity and dot color
 */
internal fun resolvePlannerEventIndicatorColor(
    event: PlannerEvent,
    customCategories: List<CustomCategory> = emptyList()
): Color {
    return plannerEventCategoryVisual(event, customCategories).dotColor
}

/**
 * Resolves deterministic indicator colors for a day in the calendar (max 3).
 *
 * Rules:
 * - 0 events = empty list (no indicator)
 * - 1 event = 1 indicator
 * - 2 events = 2 indicators
 * - 3 or more events = max 3 indicators
 * - Categorized events use their category dot color
 * - Uncategorized events use Gelen kutu category dot color
 * - If multiple events exist, prioritized: categorized events first, then earliest start time
 */
internal fun resolveDayIndicatorColors(
    events: List<PlannerEvent>,
    customCategories: List<CustomCategory> = emptyList()
): List<Color> {
    if (events.isEmpty()) return emptyList()

    val sorted = if (events.size > 3) {
        events.sortedWith(
            compareByDescending<PlannerEvent> { it.reminder.customCategoryId != null }
                .thenBy { it.startMillis }
                .thenBy { it.id }
        )
    } else {
        events.sortedWith(compareBy<PlannerEvent> { it.startMillis }.thenBy { it.id })
    }

    return sorted.take(3).map { event ->
        resolvePlannerEventIndicatorColor(event, customCategories)
    }
}

/** Predefined calm pastel pool for uncategorized 24-hour cards (matches fixed palette). */
internal val UncategorizedCardPastels = RealCalendarPastels

/** Deterministic pseudo-random mapping from card/reminder id to one of the uncategorized pastel colors. */
internal fun uncategorizedCardPastel(id: Long): Color = uncategorizedDecorativePastel(id)

/** The card colour for 24 hour and the month list: the category's own colour, else the card's, else a palette colour. */
internal fun resolveNext24CardColor(
    event: PlannerEvent,
    customCategories: List<CustomCategory> = emptyList()
): Color = plannerEventCategoryVisual(event, customCategories).realColor
