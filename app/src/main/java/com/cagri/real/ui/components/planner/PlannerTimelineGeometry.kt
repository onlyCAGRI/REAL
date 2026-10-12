package com.cagri.real.ui.components.planner

import kotlin.math.ceil

/** Values are dp; all lanes and labels share this one piecewise-linear clock. */
internal const val PlannerCompactHourDp = 12f
internal const val PlannerExpandedHourDp = 72f

/** [small] intervals (e.g. completed pills) only expand their hours to [PlannerTimelineGeometry.smallHourDp]. */
internal data class PlannerTimeInterval(
    val key: String,
    val startMinute: Float,
    val endMinute: Float,
    val small: Boolean = false
)
internal data class PlannerTimePlacement(val interval: PlannerTimeInterval, val lane: Int)

internal class PlannerTimelineGeometry(
    intervals: List<PlannerTimeInterval>,
    val dayMinutes: Int = 1440,
    val startHour: Int = 0,
    val endHour: Int = ceil(dayMinutes / 60.0).toInt(),
    val compactHourDp: Float = PlannerCompactHourDp,
    val expandedHourDp: Float = PlannerExpandedHourDp,
    val smallHourDp: Float = expandedHourDp
) {
    val placements: List<PlannerTimePlacement>
    val hourWidths: List<Float>
    val boundaries: List<Float>
    val width: Float get() = boundaries.last()
    val laneCount: Int get() = (placements.maxOfOrNull { it.lane } ?: -1) + 1

    init {
        require(dayMinutes > 0)
        require(endHour > startHour) { "endHour must be > startHour" }
        val laneEnds = mutableListOf<Float>()
        placements = intervals.mapNotNull { interval ->
            val start = interval.startMinute.coerceIn(0f, dayMinutes.toFloat())
            val end = interval.endMinute.coerceIn(0f, dayMinutes.toFloat())
            if (end <= start) null else interval.copy(startMinute = start, endMinute = end)
        }.sortedWith(compareBy<PlannerTimeInterval> { it.startMinute }.thenBy { it.endMinute }.thenBy { it.key })
            .map { interval ->
                val available = laneEnds.indexOfFirst { it <= interval.startMinute }
                val lane = if (available < 0) laneEnds.size else available
                if (available < 0) laneEnds.add(interval.endMinute) else laneEnds[lane] = interval.endMinute
                PlannerTimePlacement(interval, lane)
            }

        val totalHours = endHour - startHour
        hourWidths = List(totalHours) { index ->
            val hour = startHour + index
            fun touches(small: Boolean) = placements.any {
                it.interval.small == small &&
                    it.interval.startMinute < (hour + 1) * 60 && it.interval.endMinute > hour * 60
            }
            val hourDp = when {
                touches(small = false) -> expandedHourDp
                touches(small = true) -> smallHourDp
                else -> compactHourDp
            }
            val fraction = minOf(60, dayMinutes - hour * 60).coerceAtLeast(0) / 60f
            hourDp * (if (fraction > 0f) fraction else 1f)
        }
        boundaries = hourWidths.runningFold(0f) { acc, value -> acc + value }
    }

    fun x(minute: Float): Float {
        val time = minute.coerceIn(startHour * 60f, endHour * 60f)
        val hourOffset = ((time / 60).toInt() - startHour).coerceIn(0, hourWidths.size - 1)
        val hour = startHour + hourOffset
        val minutesInHour = minOf(60, dayMinutes - hour * 60).coerceAtLeast(1)
        val frac = (time - hour * 60) / minutesInHour
        return boundaries[hourOffset] + hourWidths[hourOffset] * frac
    }

    fun minuteAt(x: Float): Float {
        val point = x.coerceIn(0f, width)
        val idx = hourWidths.indices.firstOrNull { point < boundaries[it + 1] } ?: (hourWidths.size - 1)
        val hour = startHour + idx
        val minutesInHour = minOf(60, dayMinutes - hour * 60).coerceAtLeast(1)
        return hour * 60 + (point - boundaries[idx]) / hourWidths[idx] * minutesInHour
    }
}
