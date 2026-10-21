package com.cagri.real.calendar

import org.junit.Assert.*
import org.junit.Test

class CalendarExporterTest {
    private class Memory : CalendarLinkStorage {
        var value = emptyList<CalendarLink>(); var active = true
        override fun links() = value
        override fun saveLinks(links: List<CalendarLink>) { value = links.toList() }
        override fun isCurrent() = active
    }
    private class Fake : CalendarGateway {
        val calendar = DeviceCalendar(10, "Personal", "user@example.com", "com.google", true, true)
        val events = mutableMapOf<Long, DeviceEvent>(); var next = 1L; var inserts = 0; var updates = 0; var deletes = 0
        override fun calendars() = listOf(calendar)
        override fun event(id: Long) = events[id]
        override fun find(calendarId: Long, marker: String) = events.values.filter {
            it.calendarId == calendarId && it.description == DeviceCalendarProvider.description(marker)
        }
        override fun insert(calendarId: Long, plan: CalendarPlan, marker: String): DeviceEvent {
            inserts++
            return DeviceEvent(next++, calendarId, DeviceCalendarProvider.expected(plan, marker) + listOf(null, null, null, "0", "0", "1", "0", "0"))
                .also { events[it.id] = it }
        }
        override fun update(before: DeviceEvent, plan: CalendarPlan, marker: String): DeviceEvent? {
            if (events[before.id] != before) return null
            updates++
            return before.copy(fields = DeviceCalendarProvider.expected(plan, marker) + before.fields.drop(6)).also { events[it.id] = it }
        }
        override fun delete(before: DeviceEvent): Boolean {
            if (events[before.id] != before) return false
            deletes++; events.remove(before.id); return true
        }
    }
    private val plan = CalendarPlan("1:single", 1, "Spor", 100_000L, 200_000L, false, "Europe/Istanbul")
    private fun options(f: Fake) = CalendarOptions(targetId = f.calendar.id, targetIdentity = f.calendar.identity, exportEnabled = true)

    @Test fun `off by default and same retry never duplicates`() {
        val f = Fake(); val s = Memory(); val engine = CalendarExporter(f, s)
        engine.reconcile(CalendarOptions(), listOf(plan), 1)
        assertEquals(0, f.inserts)
        engine.reconcile(options(f), listOf(plan), 1); engine.reconcile(options(f), listOf(plan), 1)
        assertEquals(1, f.inserts); assertEquals(0, f.updates)
    }
    @Test fun `native time and title correction updates existing owned event`() {
        val f = Fake(); val s = Memory(); val engine = CalendarExporter(f, s)
        engine.reconcile(options(f), listOf(plan), 1)
        val id = f.events.keys.single()
        engine.reconcile(options(f), listOf(plan.copy(title = "Babamı ara", start = 150_000)), 1)
        assertEquals(id, f.events.keys.single()); assertEquals(1, f.updates)
    }
    @Test fun `external modification retained and reported as conflict`() {
        val f = Fake(); val s = Memory(); val engine = CalendarExporter(f, s)
        engine.reconcile(options(f), listOf(plan), 1)
        val e = f.events.values.single(); f.events[e.id] = e.copy(fields = e.fields.toMutableList().apply { this[0] = "Dış değişiklik" })
        assertEquals(1, engine.reconcile(options(f), listOf(plan.copy(title = "Yeni")), 1).conflicts)
        assertEquals("Dış değişiklik", f.events.values.single().fields[0]); assertEquals(0, f.updates)
    }
    @Test fun `external deletion is never resurrected`() {
        val f = Fake(); val s = Memory(); val engine = CalendarExporter(f, s)
        engine.reconcile(options(f), listOf(plan), 1); f.events.clear()
        engine.reconcile(options(f), listOf(plan), 1); engine.reconcile(options(f), listOf(plan), 1)
        assertEquals(1, f.inserts); assertTrue(s.value.single().suppressed)
    }
    @Test fun `future owned deletion leaves history and unrelated events alone`() {
        val f = Fake(); val s = Memory(); val engine = CalendarExporter(f, s)
        engine.reconcile(options(f), listOf(plan, plan.copy(key = "2:single", reminderId = 2, start = 0)), 1)
        val foreign = DeviceEvent(500, 10, listOf("Other", "100", "200", "0", "UTC", "Other", null, null, null, "0", "0", "1", "0", "0"))
        f.events[500] = foreign
        engine.reconcile(options(f), emptyList(), 1)
        assertEquals(1, f.deletes); assertEquals(2, f.events.size); assertEquals(foreign, f.events[500])
    }
    @Test fun `interrupted insert recovers persisted opaque marker`() {
        val f = Fake(); val s = Memory(); val marker = "opaque-test-token"
        s.value = listOf(CalendarLink(plan.key, marker, 10, f.calendar.identity, start = plan.start))
        val created = f.insert(10, plan, marker)
        CalendarExporter(f, s).reconcile(options(f), listOf(plan), 1)
        assertEquals(1, f.inserts); assertEquals(created.id, s.value.single().eventId)
    }
    @Test fun `recycled calendar identity and switched workspace block writes`() {
        val f = Fake(); val s = Memory()
        assertEquals(1, CalendarExporter(f, s).reconcile(options(f).copy(targetIdentity = "another account"), listOf(plan), 1).unavailable)
        s.active = false
        try { CalendarExporter(f, s).reconcile(options(f), listOf(plan), 1); fail("Stale workspace must fail") }
        catch (_: IllegalStateException) { }
        assertEquals(0, f.inserts)
    }
    @Test fun `Google normalization hides export echo without authorizing writes`() {
        val f = Fake(); val s = Memory(); val engine = CalendarExporter(f, s)
        engine.reconcile(options(f), listOf(plan), 1)
        val e = f.events.values.single()
        val normalized = e.copy(fields = e.fields.toMutableList().apply { this[4] = "UTC"; this[11] = "2" })
        f.events[e.id] = normalized
        assertNotEquals(s.value.single().fingerprint, normalized.fingerprint)
        assertTrue(representedByReal(normalized, s.value.single(), f.calendar.identity))
        assertEquals(1, engine.reconcile(options(f), listOf(plan.copy(title = "Yeni")), 1).conflicts)
        assertEquals(0, f.updates)
    }
    @Test fun `render suppression rejects another calendar identity or recycled row marker`() {
        val f = Fake(); val s = Memory()
        CalendarExporter(f, s).reconcile(options(f), listOf(plan), 1)
        val e = f.events.values.single(); val link = s.value.single()
        assertFalse(representedByReal(e, link, "other account"))
        assertFalse(representedByReal(e.copy(calendarId = 99), link, f.calendar.identity))
        assertFalse(representedByReal(e.copy(fields = e.fields.toMutableList().apply { this[5] = "Unrelated" }), link, f.calendar.identity))
        assertFalse(representedByReal(e, link.copy(suppressed = true), f.calendar.identity))
    }

    @Test fun `description-only link refresh never adopts outside edits as writable baseline`() {
        val f = Fake(); val s = Memory()
        CalendarExporter(f, s).reconcile(options(f), listOf(plan), 1)
        val old = f.events.values.single()
        f.events[old.id] = old.copy(fields = old.fields.toMutableList().apply { this[0] = "Google başlığı" })
        val baseline = s.value.single().fingerprint
        val engine = CalendarExporter(f, s, updatePublicLink = { event, desired, link ->
            f.events[event.id] = event.copy(fields = event.fields.toMutableList().apply {
                this[5] = DeviceCalendarProvider.description(link.marker, desired.publicUrl)
            }); true
        })
        val linkedPlan = plan.copy(publicUrl = "https://example.invalid/s/opaque")
        assertEquals(1, engine.reconcile(options(f), listOf(linkedPlan), 1).updated)
        assertEquals(baseline, s.value.single().fingerprint)
        assertEquals(1, engine.reconcile(options(f), listOf(linkedPlan), 1).conflicts)
        assertEquals("Google başlığı", f.events.values.single().fields[0]); assertEquals(0, f.updates)
    }

    @Test fun `many plans retain idempotent journal retry after indexed lookup`() {
        val f = Fake(); val s = Memory(); val engine = CalendarExporter(f, s)
        val plans = (1L..100L).map { id -> plan.copy(key = "$id:single", reminderId = id, title = "Plan $id") }
        engine.reconcile(options(f), plans, 1)
        engine.reconcile(options(f), plans, 1)
        assertEquals(100, f.inserts); assertEquals(0, f.updates); assertEquals(0, f.deletes)
        assertEquals(100, s.value.map { it.key }.toSet().size)
    }

}
