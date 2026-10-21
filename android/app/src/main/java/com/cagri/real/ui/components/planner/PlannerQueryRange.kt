package com.cagri.real.ui.components.planner

import com.cagri.real.data.ROLLING_24_HOURS_MILLIS
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

internal data class PlannerQueryRange(val startMillis: Long, val endMillis: Long)

/** Provider input is stable within a local day; the visible 24h filter still uses live now.
 * Tomorrow's midnight + 24 elapsed hours also covers the full window across a short DST day. */
internal fun plannerQueryRange(month: YearMonth, today: LocalDate, zone: ZoneId): PlannerQueryRange =
    PlannerQueryRange(
        startMillis = minOf(
            month.atDay(1).minusDays(7).atStartOfDay(zone).toInstant().toEpochMilli(),
            today.atStartOfDay(zone).toInstant().toEpochMilli()
        ),
        endMillis = maxOf(
            month.plusMonths(1).atDay(1).plusDays(6).atStartOfDay(zone).toInstant().toEpochMilli(),
            today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() + ROLLING_24_HOURS_MILLIS
        )
    )
