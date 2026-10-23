package com.cagri.real.calendar

import com.cagri.real.i18n.t
import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars as Calendars
import android.provider.CalendarContract.Instances as Instances
import androidx.core.content.ContextCompat
import java.security.MessageDigest

internal data class DeviceEvent(val id: Long, val calendarId: Long, val fields: List<String?>) {
    val fingerprint: String get() = hash(fields)
    /**
     * What a person changes in a calendar app: title, time, all-day, repeat, place, and the description apart from REAL's
     * own block (REAL rewrites it when it adds a web link). Google's housekeeping — time zone normalising, default
     * alarms, status, availability, access — is not an edit, so it no longer blocks REAL from updating or removing
     * its own copy.
     */
    val userFingerprint: String get() = hash(listOf(fields[0], fields[1], fields[2], fields[3],
        fields[5]?.let { d -> if (d.startsWith("REAL planı") && d.contains("\n[REAL:")) "REAL" else d },
        fields[6], fields[7], fields[8]))
    val description get() = fields[5]

    private fun hash(values: List<String?>) = MessageDigest.getInstance("SHA-256")
        .digest(values.joinToString("\u0000") { it ?: "\u0001" }.toByteArray())
        .joinToString("") { "%02x".format(it) }
}

internal interface CalendarGateway {
    fun calendars(): List<DeviceCalendar>
    fun event(id: Long): DeviceEvent?
    fun find(calendarId: Long, marker: String): List<DeviceEvent>
    fun insert(calendarId: Long, plan: CalendarPlan, marker: String): DeviceEvent
    fun update(before: DeviceEvent, plan: CalendarPlan, marker: String): DeviceEvent?
    fun delete(before: DeviceEvent): Boolean
}

internal class DeviceCalendarProvider(private val context: Context) : CalendarGateway {
    private val resolver = context.contentResolver
    fun canRead() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    fun canWrite() = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
    override fun calendars(): List<DeviceCalendar> {
        check(canRead()) { "Takvim okuma izni gerekli" }
        val projection = arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME, Calendars.ACCOUNT_TYPE,
            Calendars.CALENDAR_ACCESS_LEVEL, Calendars.SYNC_EVENTS)
        return resolver.query(Calendars.CONTENT_URI, projection, null, null, Calendars.CALENDAR_DISPLAY_NAME + " ASC")
            ?.use { cursor -> buildList {
                while (cursor.moveToNext()) add(DeviceCalendar(cursor.getLong(0), cursor.getString(1).orEmpty(),
                    cursor.getString(2).orEmpty(), cursor.getString(3).orEmpty(),
                    cursor.getInt(4) >= Calendars.CAL_ACCESS_CONTRIBUTOR, cursor.getInt(5) != 0))
            } } ?: error(t("Cihaz takvim sağlayıcısı kullanılamıyor"))
    }
    private fun queryEvents(selection: String, args: Array<String>): List<DeviceEvent> {
        check(canRead())
        return resolver.query(CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID, CalendarContract.Events.CALENDAR_ID) + fields,
            "(${CalendarContract.Events.DELETED}=0) AND (${selection})", args, null)?.use { cursor -> buildList {
                while (cursor.moveToNext()) add(DeviceEvent(cursor.getLong(0), cursor.getLong(1),
                    fields.indices.map { if (cursor.isNull(it + 2)) null else cursor.getString(it + 2) }))
            } } ?: error(t("Etkinlikler okunamadı"))
    }
    override fun event(id: Long) = queryEvents("${CalendarContract.Events._ID}=?", arrayOf(id.toString())).singleOrNull()
    override fun find(calendarId: Long, marker: String) = queryEvents(
        "${CalendarContract.Events.CALENDAR_ID}=? AND ${CalendarContract.Events.DESCRIPTION} LIKE ?",
        arrayOf(calendarId.toString(), "REAL planı%\n[REAL:${marker}]"))

    override fun insert(calendarId: Long, plan: CalendarPlan, marker: String): DeviceEvent {
        check(canWrite())
        val values = values(plan, marker).apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.HAS_ALARM, 0) // REAL keeps authoritative reminders; no duplicate alarms.
        }
        val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: error(t("Takvim etkinliği eklenemedi"))
        return event(ContentUris.parseId(uri)) ?: error(t("Eklenen etkinlik doğrulanamadı"))
    }
    override fun update(before: DeviceEvent, plan: CalendarPlan, marker: String): DeviceEvent? {
        check(canWrite())
        val (where, args) = compareSelection(before)
        val count = resolver.update(CalendarContract.Events.CONTENT_URI, values(plan, marker), where, args)
        return if (count == 1) event(before.id) else null
    }
    override fun delete(before: DeviceEvent): Boolean {
        check(canWrite())
        val (where, args) = compareSelection(before)
        return resolver.delete(CalendarContract.Events.CONTENT_URI, where, args) == 1
    }
    // Optimistic compare-and-set protects a simultaneous edit in Google/Samsung Calendar.
    private fun compareSelection(event: DeviceEvent): Pair<String, Array<String>> {
        val clauses = mutableListOf("${CalendarContract.Events._ID}=?", "${CalendarContract.Events.CALENDAR_ID}=?",
            "${CalendarContract.Events.DELETED}=0")
        val args = mutableListOf(event.id.toString(), event.calendarId.toString())
        fields.forEachIndexed { index, field ->
            val value = event.fields[index]
            if (value == null) clauses += "$field IS NULL" else { clauses += "$field=?"; args += value }
        }
        return clauses.joinToString(" AND ") to args.toTypedArray()
    }
    fun updateDescription(before: DeviceEvent, description: String): DeviceEvent? {
        check(canWrite())
        val (where, args) = compareSelection(before)
        val changed = resolver.update(CalendarContract.Events.CONTENT_URI,
            ContentValues().apply { put(CalendarContract.Events.DESCRIPTION, description) }, where, args)
        return if (changed == 1) event(before.id) else null
    }
    /** Rendering identity, not write authorization: Google may normalize event fields. */
    fun representedLinkedIds(links: List<CalendarLink>): Set<Long> {
        val identities = calendars().associate { it.id to it.identity }
        val mapped = links.filter { it.eventId != null && it.fingerprint != null && !it.suppressed }
            .associateBy { it.eventId!! }
        return mapped.keys.toList().chunked(100).flatMap { ids ->
            val rows = queryEvents("${CalendarContract.Events._ID} IN (${ids.joinToString(",") { "?" }})",
                ids.map { it.toString() }.toTypedArray())
            rows.filter { e -> mapped[e.id]?.let { link -> representedByReal(e, link, identities[link.calendarId]) } == true }.map { it.id }
        }.toSet()
    }
    fun occurrences(options: CalendarOptions, start: Long, end: Long, linkedIds: Set<Long>): List<CalendarOccurrence> {
        if (options.readIds.isEmpty()) return emptyList()
        val allowed = calendars().filter { it.id in options.readIds && options.readIdentities[it.id] == it.identity }
        if (allowed.isEmpty()) return emptyList()
        val names = allowed.associate { it.id to it.name }
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, start); ContentUris.appendId(it, end)
        }.build()
        val projection = arrayOf(Instances.EVENT_ID, Instances.CALENDAR_ID, Instances.TITLE, Instances.BEGIN, Instances.END, Instances.ALL_DAY)
        val placeholders = allowed.joinToString(",") { "?" }
        return resolver.query(uri, projection, "${Instances.CALENDAR_ID} IN (${placeholders})",
            allowed.map { it.id.toString() }.toTypedArray(), "${Instances.BEGIN} ASC")?.use { cursor -> buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    if (id in linkedIds) continue // Already represented by the REAL task in the planner.
                    val cal = cursor.getLong(1)
                    add(CalendarOccurrence(id, cal, cursor.getString(2)?.takeIf { it.isNotBlank() } ?: "Etkinlik",
                        cursor.getLong(3), cursor.getLong(4), cursor.getInt(5) != 0, names[cal].orEmpty()))
                }
            } } ?: error(t("Takvim etkinlikleri okunamadı"))
    }
    fun open(eventId: Long, start: Long? = null) {
        val intent = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId))
        start?.let { intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
    companion object {
        fun description(marker: String, url: String? = null) =
            "REAL planı" + (url?.let { "\nREAL kartı: ${it}" } ?: "") + "\n[REAL:$marker]"
        fun ownsDescription(value: String?, marker: String) = value != null && value.startsWith("REAL planı") &&
            value.endsWith("\n[REAL:$marker]")
        // All fields we overwrite + recurrence/location/attendees, so external changes never disappear silently.
        private val fields = arrayOf(CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND, CalendarContract.Events.ALL_DAY, CalendarContract.Events.EVENT_TIMEZONE,
            CalendarContract.Events.DESCRIPTION, CalendarContract.Events.RRULE, CalendarContract.Events.RDATE,
            CalendarContract.Events.EVENT_LOCATION, CalendarContract.Events.HAS_ATTENDEE_DATA,
            CalendarContract.Events.HAS_ALARM, CalendarContract.Events.STATUS,
            CalendarContract.Events.AVAILABILITY, CalendarContract.Events.ACCESS_LEVEL)
        fun expected(plan: CalendarPlan, marker: String) = listOf<String?>(plan.title, plan.start.toString(),
            plan.end.toString(), if (plan.allDay) "1" else "0", plan.zone, description(marker, plan.publicUrl))
        private fun values(plan: CalendarPlan, marker: String) = ContentValues().apply {
            put(CalendarContract.Events.TITLE, plan.title); put(CalendarContract.Events.DTSTART, plan.start)
            put(CalendarContract.Events.DTEND, plan.end); put(CalendarContract.Events.ALL_DAY, if (plan.allDay) 1 else 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, plan.zone); put(CalendarContract.Events.DESCRIPTION, description(marker, plan.publicUrl))
        }
        fun matches(event: DeviceEvent, plan: CalendarPlan, marker: String) = event.fields.take(6) == expected(plan, marker)
    }
}

/** Display de-duplication only. Export update/delete still require fingerprint + CAS. */
internal fun representedByReal(event: DeviceEvent, link: CalendarLink, calendarIdentity: String?): Boolean =
    !link.suppressed && event.id == link.eventId && event.calendarId == link.calendarId &&
        calendarIdentity == link.calendarIdentity && DeviceCalendarProvider.ownsDescription(event.description, link.marker)
