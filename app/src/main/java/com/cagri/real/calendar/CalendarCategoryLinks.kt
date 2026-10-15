package com.cagri.real.calendar

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import com.cagri.real.platform.android.sync.SyncLocal

/**
 * "Kategorize et" for phone calendars: a whole device calendar (e.g. Formula 1) or a single event is shown in a REAL
 * category's colour in the planner. Events stay in their calendar; nothing is copied into REAL. Calendar and event
 * ids belong to this phone, so the links are stored on this device only (per REAL workspace).
 */
internal class CalendarCategoryLinks(context: Context) {
    private val prefs = SyncLocal.preferences(context.applicationContext, "real_calendar_categories", Context.MODE_PRIVATE)

    /** The event's own link first, then its calendar's. */
    fun categoryFor(eventId: Long, calendarId: Long): Long? =
        prefs.getLong("event_$eventId", -1L).takeIf { it >= 0 } ?: prefs.getLong("calendar_$calendarId", -1L).takeIf { it >= 0 }

    fun isWholeCalendar(calendarId: Long): Boolean = prefs.contains("calendar_$calendarId")

    /** True when any calendar or event is filed under [categoryId] (such categories are never shared). */
    fun hasCategory(categoryId: Long): Boolean = prefs.all.values.any { (it as? Long) == categoryId }

    /** Links (or, with null, unlinks) the event alone or its whole calendar. */
    fun link(eventId: Long, calendarId: Long, categoryId: Long?, wholeCalendar: Boolean) {
        prefs.edit().apply {
            remove("event_$eventId")
            if (wholeCalendar || categoryId == null) remove("calendar_$calendarId")
            if (categoryId != null) {
                if (wholeCalendar) putLong("calendar_$calendarId", categoryId) else putLong("event_$eventId", categoryId)
            }
        }.apply()
        revision.intValue++
    }

    companion object {
        /** Bumped on every change so the planner recolours at once. */
        val revision = mutableIntStateOf(0)
    }
}
