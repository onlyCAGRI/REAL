package com.cagri.real.calendar

import java.util.UUID

internal interface CalendarLinkStorage {
    fun links(): List<CalendarLink>
    fun saveLinks(links: List<CalendarLink>)
    fun isCurrent(): Boolean
}

/** Only touches events with a locally persisted opaque ownership marker and unchanged baseline.
 * Provider writes and the local journal are ordered; an interrupted insertion is recovered by marker. */
internal class CalendarExporter(private val gateway: CalendarGateway, private val store: CalendarLinkStorage, private val updatePublicLink: ((DeviceEvent, CalendarPlan, CalendarLink) -> Boolean)? = null, private val shouldContinue: () -> Boolean = { true }) {
    /**
     * [existingCardIds]: ids of the REAL cards that still exist. A managed copy whose card was deleted in REAL is removed
     * from the calendar even when it is already in the past (the card is gone, there is no history to keep).
     * null = unknown, old behaviour (only future copies are removed).
     * [allowRemoval]: false while the card list may not be loaded yet, so an empty list can never wipe the calendar.
     */
    fun reconcile(
        options: CalendarOptions, plans: List<CalendarPlan>, now: Long,
        existingCardIds: Set<Long>? = null, allowRemoval: Boolean = true
    ): CalendarSyncResult {
        if (!options.exportEnabled || options.targetId == null) return CalendarSyncResult()
        val target = gateway.calendars().singleOrNull {
            it.id == options.targetId && it.identity == options.targetIdentity && it.writable
        } ?: return CalendarSyncResult(unavailable = 1)
        val links = store.links().toMutableList()
        val indices = HashMap<String, Int>().apply { links.forEachIndexed { i, link -> putIfAbsent(link.key, i) } }
        var inserted = 0; var updated = 0; var removed = 0; var conflicts = 0
        fun persist() { check(store.isCurrent() && shouldContinue()); store.saveLinks(links) }
        val desired = plans.associateBy { it.key }
        for (plan in plans) {
            check(store.isCurrent() && shouldContinue())
            var index = indices[plan.key] ?: -1
            if (index < 0) {
                links += CalendarLink(plan.key, UUID.randomUUID().toString(), target.id, target.identity, start = plan.start)
                persist() // Journal BEFORE insert. Retry uses this same marker, never a new event.
                index = links.lastIndex
                indices[plan.key] = index
            }
            val link = links[index]
            if (link.suppressed || link.calendarId != target.id || link.calendarIdentity != target.identity) {
                conflicts++; continue
            }
            var existing = link.eventId?.let(gateway::event)
            if (existing != null && (existing.calendarId != link.calendarId ||
                    !DeviceCalendarProvider.ownsDescription(existing.description, link.marker))) {
                conflicts++; continue // Calendar row IDs can be recycled; never treat an unrelated event as ours.
            }
            if (existing == null) {
                val recovered = gateway.find(link.calendarId, link.marker)
                if (recovered.size > 1) { conflicts++; continue }
                existing = recovered.singleOrNull()
            }
            if (existing == null && link.eventId != null) {
                links[index] = link.copy(suppressed = true); persist(); conflicts++
                continue // User removed it outside REAL. Do not resurrect it on the next app open.
            }
            if (existing == null) {
                check(store.isCurrent() && shouldContinue())
                val created = gateway.insert(target.id, plan, link.marker)
                links[index] = link.copy(eventId = created.id, fingerprint = created.fingerprint, start = plan.start)
                persist(); inserted++; continue
            }
            if (link.fingerprint == null) {
                // Recover only the originally intended event, not an externally modified pending insert.
                if (!DeviceCalendarProvider.matches(existing, plan, link.marker) ||
                    existing.fields[6] != null || existing.fields[7] != null ||
                    !existing.fields[8].isNullOrEmpty() || existing.fields[9] !in listOf(null, "0") || existing.fields[10] !in listOf(null, "0")) {
                    conflicts++; continue
                }
                links[index] = link.copy(eventId = existing.id, fingerprint = existing.fingerprint); persist()
                continue
            }
            if (existing.fingerprint != link.fingerprint) {
                // A guarded description-only URL refresh may proceed without authorizing title/date overwrites.
                // Keep the old fingerprint: an external edit must remain a conflict on subsequent reconciles.
                if (existing.description != DeviceCalendarProvider.description(link.marker, plan.publicUrl)) {
                    check(store.isCurrent() && shouldContinue())
                    if (updatePublicLink?.invoke(existing, plan, link) == true) updated++
                }
                conflicts++; continue
            }
            if (!DeviceCalendarProvider.matches(existing, plan, link.marker)) {
                check(store.isCurrent() && shouldContinue())
                val changed = gateway.update(existing, plan, link.marker)
                if (changed == null) { conflicts++; continue }
                links[index] = link.copy(eventId = changed.id, fingerprint = changed.fingerprint, start = plan.start)
                persist(); updated++
            }
        }
        // Copies of cards that STILL exist keep their history: only future managed occurrences that dropped out of the
        // plan are removed. Copies of cards DELETED in REAL are removed whatever their date. Edited-in-calendar copies
        // (fingerprint changed) and anything we do not own are never touched.
        for (link in links.toList()) {
            if (!allowRemoval) break
            if (link.key in desired) continue
            val cardGone = existingCardIds != null &&
                link.key.substringBefore(':').toLongOrNull()?.let { it !in existingCardIds } == true
            if (link.suppressed) {
                if (cardGone) { check(store.isCurrent() && shouldContinue()); links.remove(link); persist() } // tidy the dead receipt
                continue
            }
            if (link.start < now && !cardGone) continue
            check(store.isCurrent() && shouldContinue())
            if (link.calendarId != target.id || link.calendarIdentity != target.identity) { conflicts++; continue }
            val matches = gateway.find(link.calendarId, link.marker)
            if (matches.isEmpty()) { links.remove(link); persist(); continue }
            val existing = matches.singleOrNull()
            if (existing == null || link.fingerprint == null || existing.fingerprint != link.fingerprint) {
                conflicts++; continue
            }
            check(store.isCurrent() && shouldContinue())
            if (gateway.delete(existing)) { links.remove(link); persist(); removed++ } else conflicts++
        }
        return CalendarSyncResult(inserted, updated, removed, conflicts)
    }
}
