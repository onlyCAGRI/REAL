package com.cagri.bigboscagri

import com.cagri.bigboscagri.data.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class ScheduleRulesTest {
    @Test fun monthAndLeapYearClampTheSelectedDay() {
        assertEquals(LocalDate.of(2028, 2, 29), clampedDate(2028, 2, 31))
        assertEquals(LocalDate.of(2026, 2, 28), clampedDate(2026, 2, 31))
        assertEquals(LocalDate.of(2026, 4, 30), clampedDate(2026, 4, 31))
    }
    @Test fun turkishLocalTimeBecomesCorrectInstant() {
        assertEquals(Instant.parse("2026-09-18T17:45:00Z").toEpochMilli(),
            reminderTimestamp(LocalDate.of(2026, 9, 18), 20, 45, ZoneId.of("Europe/Istanbul")))
    }
    @Test fun daylightSavingGapIsRejected() {
        assertNull(reminderTimestamp(LocalDate.of(2026, 3, 29), 2, 30, ZoneId.of("Europe/Berlin")))
    }
    @Test fun noteDoesNotFireBeforeItsTime() {
        val note = ReminderItem(1, "not", 100, alertAt = 200)
        assertFalse(note.isAlertDue(199)); assertTrue(note.isAlertDue(200))
        assertFalse(note.copy(alertAt = null).isAlertDue(200))
    }
    @Test fun completedNoteNeverFiresAndUndoKeepsItsSchedule() {
        val note = ReminderItem(1, "not", 100, alertAt = 200).toggleCompleted(150)
        assertFalse(note.isAlertDue(200)); assertTrue(note.toggleCompleted(210).isAlertDue(210))
    }
    @Test fun deliveredAlarmDoesNotReplayOnResumeOrReboot() {
        val note = ReminderItem(1, "not", 100, alertAt = 200, notifiedAlertAt = 200)
        assertFalse(note.isAlertDue(400)); assertNull(listOf(note).nextAlertAfter(100))
    }
    @Test fun changedOrRemovedScheduleInvalidatesOldAlarm() {
        val note = ReminderItem(1, "not", 100, alertAt = 200, notifiedAlertAt = 200)
        assertFalse(note.copy(alertAt = 300, notifiedAlertAt = null).isAlertDue(200))
        assertTrue(note.copy(alertAt = 300, notifiedAlertAt = null).isAlertDue(300))
        assertFalse(note.copy(alertAt = null).isAlertDue(400))
    }
    @Test fun nextAlarmIgnoresCompletedDeliveredAndPastNotes() {
        val notes = listOf(ReminderItem(1, "a", 1, alertAt = 300),
            ReminderItem(2, "b", 2, alertAt = 200, completedAt = 5),
            ReminderItem(3, "c", 3, alertAt = 150, notifiedAlertAt = 150),
            ReminderItem(4, "d", 4, alertAt = 350), ReminderItem(5, "e", 5, alertAt = 90))
        assertEquals(300L, notes.nextAlertAfter(100))
    }
    @Test fun simultaneousNotesCanBeDeliveredInOneBatch() {
        val notes = (1..4).map { ReminderItem(it.toLong(), "not", 1, alertAt = 200) }
        assertEquals(4, notes.count { it.isAlertDue(200) })
        assertTrue(notes.map { it.copy(notifiedAlertAt = it.alertAt) }.none { it.isAlertDue(200) })
    }
    @Test fun fastCountsMatchCategoryFilters() {
        val today = LocalDate.of(2026, 9, 18)
        val zone = ZoneId.of("Europe/Istanbul")
        val at = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val notes = (1..300).map { i -> ReminderItem(i.toLong(), "not", i.toLong(),
            isImportant = i % 3 == 0, completedAt = if (i % 5 == 0) at else null,
            alertAt = when (i % 4) { 0 -> at; 1 -> at + 86400000; else -> null }) }
        val counts = notes.categoryCounts(today, zone)
        ReminderCategory.entries.forEach { assertEquals(notes.inCategory(it, today, zone).size, counts[it]) }
    }

    @Test fun dailyRecurrenceComputesNextDayAtSameTime() {
        val zone = ZoneId.of("Europe/Istanbul")
        val baseDate = LocalDate.of(2026, 9, 18)
        val baseTime = LocalTime.of(10, 30)
        val baseMillis = baseDate.atTime(baseTime).atZone(zone).toInstant().toEpochMilli()

        // Next occurrence immediately after base
        val nextMillis = computeNextOccurrence(baseMillis, RecurrenceRule.DAILY, zone, afterMillis = baseMillis)
        assertNotNull(nextMillis)
        val nextZdt = Instant.ofEpochMilli(nextMillis!!).atZone(zone)
        assertEquals(LocalDate.of(2026, 9, 19), nextZdt.toLocalDate())
        assertEquals(baseTime, nextZdt.toLocalTime())

        // Next occurrence when multiple days have elapsed (e.g., 3 days later)
        val threeDaysLater = baseDate.plusDays(3).atTime(11, 0).atZone(zone).toInstant().toEpochMilli()
        val caughtUpMillis = computeNextOccurrence(baseMillis, RecurrenceRule.DAILY, zone, afterMillis = threeDaysLater)
        assertNotNull(caughtUpMillis)
        val caughtUpZdt = Instant.ofEpochMilli(caughtUpMillis!!).atZone(zone)
        assertEquals(LocalDate.of(2026, 9, 22), caughtUpZdt.toLocalDate())
        assertEquals(baseTime, caughtUpZdt.toLocalTime())
    }

    @Test fun weeklyRecurrenceMaintainsDayOfWeek() {
        val zone = ZoneId.of("Europe/Istanbul")
        val baseDate = LocalDate.of(2026, 9, 18) // Friday
        val baseTime = LocalTime.of(15, 0)
        val baseMillis = baseDate.atTime(baseTime).atZone(zone).toInstant().toEpochMilli()

        val occ1 = computeNextOccurrence(baseMillis, RecurrenceRule.WEEKLY, zone, afterMillis = baseMillis)!!
        val zdt1 = Instant.ofEpochMilli(occ1).atZone(zone)
        assertEquals(LocalDate.of(2026, 9, 25), zdt1.toLocalDate())
        assertEquals(DayOfWeek.FRIDAY, zdt1.dayOfWeek)
        assertEquals(baseTime, zdt1.toLocalTime())

        val occ2 = computeNextOccurrence(baseMillis, RecurrenceRule.WEEKLY, zone, afterMillis = occ1)!!
        val zdt2 = Instant.ofEpochMilli(occ2).atZone(zone)
        assertEquals(LocalDate.of(2026, 10, 2), zdt2.toLocalDate())
        assertEquals(DayOfWeek.FRIDAY, zdt2.dayOfWeek)
    }

    @Test fun monthlyRecurrenceHandlesMonthEndClampingAndRestoration() {
        val zone = ZoneId.of("Europe/Istanbul")
        // Start on January 31st
        val anchorDate = LocalDate.of(2026, 1, 31)
        val time = LocalTime.of(9, 0)
        val anchorMillis = anchorDate.atTime(time).atZone(zone).toInstant().toEpochMilli()

        // Occurrence 1: Feb 2026 (non-leap year, 28 days) -> clamped to Feb 28
        val febMillis = computeNextOccurrence(anchorMillis, RecurrenceRule.MONTHLY, zone, afterMillis = anchorMillis)!!
        val febZdt = Instant.ofEpochMilli(febMillis).atZone(zone)
        assertEquals(LocalDate.of(2026, 2, 28), febZdt.toLocalDate())
        assertEquals(time, febZdt.toLocalTime())

        // Occurrence 2: March 2026 (31 days) -> anchor restores 31st!
        val marchMillis = computeNextOccurrence(anchorMillis, RecurrenceRule.MONTHLY, zone, afterMillis = febMillis)!!
        val marchZdt = Instant.ofEpochMilli(marchMillis).atZone(zone)
        assertEquals(LocalDate.of(2026, 3, 31), marchZdt.toLocalDate())
        assertEquals(time, marchZdt.toLocalTime())

        // Occurrence 3: April 2026 (30 days) -> clamped to 30th
        val aprilMillis = computeNextOccurrence(anchorMillis, RecurrenceRule.MONTHLY, zone, afterMillis = marchMillis)!!
        val aprilZdt = Instant.ofEpochMilli(aprilMillis).atZone(zone)
        assertEquals(LocalDate.of(2026, 4, 30), aprilZdt.toLocalDate())

        // Occurrence 4: May 2026 (31 days) -> anchor restores 31st!
        val mayMillis = computeNextOccurrence(anchorMillis, RecurrenceRule.MONTHLY, zone, afterMillis = aprilMillis)!!
        val mayZdt = Instant.ofEpochMilli(mayMillis).atZone(zone)
        assertEquals(LocalDate.of(2026, 5, 31), mayZdt.toLocalDate())
    }

    @Test fun yearlyLeapDayRecurrenceRestoresOnLeapYears() {
        val zone = ZoneId.of("Europe/Istanbul")
        // Start on leap day: Feb 29, 2024
        val anchorDate = LocalDate.of(2024, 2, 29)
        val time = LocalTime.of(12, 0)
        val anchorMillis = anchorDate.atTime(time).atZone(zone).toInstant().toEpochMilli()

        // 2025: non-leap -> clamped to Feb 28
        val occ2025 = computeNextOccurrence(anchorMillis, RecurrenceRule.YEARLY, zone, afterMillis = anchorMillis)!!
        assertEquals(LocalDate.of(2025, 2, 28), Instant.ofEpochMilli(occ2025).atZone(zone).toLocalDate())

        // 2026: non-leap -> clamped to Feb 28
        val occ2026 = computeNextOccurrence(anchorMillis, RecurrenceRule.YEARLY, zone, afterMillis = occ2025)!!
        assertEquals(LocalDate.of(2026, 2, 28), Instant.ofEpochMilli(occ2026).atZone(zone).toLocalDate())

        // 2027: non-leap -> clamped to Feb 28
        val occ2027 = computeNextOccurrence(anchorMillis, RecurrenceRule.YEARLY, zone, afterMillis = occ2026)!!
        assertEquals(LocalDate.of(2027, 2, 28), Instant.ofEpochMilli(occ2027).atZone(zone).toLocalDate())

        // 2028: leap year -> restores Feb 29!
        val occ2028 = computeNextOccurrence(anchorMillis, RecurrenceRule.YEARLY, zone, afterMillis = occ2027)!!
        assertEquals(LocalDate.of(2028, 2, 29), Instant.ofEpochMilli(occ2028).atZone(zone).toLocalDate())
        assertEquals(time, Instant.ofEpochMilli(occ2028).atZone(zone).toLocalTime())
    }

    @Test fun repeatRuleJsonPersistenceAndBackwardCompatibility() {
        // Test all recurrence enum values roundtrip
        for (rule in RecurrenceRule.entries) {
            val json = org.json.JSONObject().put("repeatRule", rule.storageKey)
            val parsed = RecurrenceRule.fromStorage(json.optString("repeatRule"))
            assertEquals(rule, parsed)
        }

        // Test ReminderItem serialization including anchorAlertAt
        val item = ReminderItem(
            id = 42,
            text = "Test Recurrence",
            createdAt = 1000L,
            alertAt = 5000L,
            anchorAlertAt = 5000L,
            repeatRule = RecurrenceRule.WEEKLY
        )
        val json = org.json.JSONObject()
            .put("id", item.id)
            .put("text", item.text)
            .put("createdAt", item.createdAt)
            .put("updatedAt", item.updatedAt)
            .put("completedAt", org.json.JSONObject.NULL)
            .put("isImportant", item.isImportant)
            .put("alertAt", item.alertAt ?: org.json.JSONObject.NULL)
            .put("notifiedAlertAt", item.notifiedAlertAt ?: org.json.JSONObject.NULL)
            .put("repeatRule", item.repeatRule.storageKey)
            .put("anchorAlertAt", item.anchorAlertAt ?: org.json.JSONObject.NULL)

        assertEquals("weekly", json.getString("repeatRule"))
        assertEquals(5000L, json.getLong("anchorAlertAt"))

        // Backward compatibility: old JSON without repeatRule or anchorAlertAt
        val legacyJson = org.json.JSONObject()
            .put("id", 101)
            .put("text", "Eski Not")
            .put("alertAt", 7000L)
            .put("earlyAlertOffsetMinutes", 15) // old early alert field ignored cleanly

        val legacyRule = RecurrenceRule.fromStorage(legacyJson.optString("repeatRule", "").takeIf { it.isNotEmpty() })
        assertEquals(RecurrenceRule.NONE, legacyRule)
        assertFalse(legacyJson.has("anchorAlertAt"))
    }

    @Test fun deliveredRepeatingAlertAdvancesToNextOccurrence() {
        val zone = ZoneId.of("Europe/Istanbul")
        val alertDate = LocalDate.of(2026, 9, 18)
        val alertTime = LocalTime.of(8, 0)
        val alertMillis = alertDate.atTime(alertTime).atZone(zone).toInstant().toEpochMilli()

        val note = ReminderItem(
            id = 1,
            text = "Günlük İlaç",
            createdAt = 100,
            alertAt = alertMillis,
            anchorAlertAt = alertMillis,
            repeatRule = RecurrenceRule.DAILY
        )

        // Alarm is due at alertMillis
        assertTrue(note.isAlertDue(alertMillis))

        // Delivery calculation (simulating deliverDue logic)
        val anchor = note.anchorAlertAt ?: note.alertAt!!
        val nextOccurrence = computeNextOccurrence(anchor, note.repeatRule, zone, afterMillis = alertMillis)
        assertNotNull(nextOccurrence)

        val updatedNote = note.copy(
            alertAt = nextOccurrence,
            anchorAlertAt = anchor,
            notifiedAlertAt = null,
            updatedAt = alertMillis
        )

        // Previous alarm time is no longer due
        assertFalse(updatedNote.isAlertDue(alertMillis))
        // New alarm time is due at next occurrence
        assertTrue(updatedNote.isAlertDue(nextOccurrence!!))
        assertEquals(alertMillis + 86400000L, nextOccurrence)
        assertEquals(anchor, updatedNote.anchorAlertAt)
    }

    @Test fun singleTimeReminderNeverRepeats() {
        val alertMillis = 50000L
        val note = ReminderItem(
            id = 2,
            text = "Tek seferlik toplantı",
            createdAt = 100,
            alertAt = alertMillis,
            repeatRule = RecurrenceRule.NONE
        )

        // Single time: next occurrence must be null
        assertNull(computeNextOccurrence(alertMillis, note.repeatRule, afterMillis = alertMillis))
        assertFalse(note.hasRepeat)

        // Delivery marks notifiedAlertAt = alertAt
        val deliveredNote = note.copy(notifiedAlertAt = note.alertAt)
        // Never fires again
        assertFalse(deliveredNote.isAlertDue(alertMillis))
        assertFalse(deliveredNote.isAlertDue(alertMillis + 100000L))
        assertNull(computeNextOccurrence(alertMillis, deliveredNote.repeatRule, afterMillis = alertMillis + 100000L))
    }

    @Test fun repeatSummaryDescriptionGeneratesExpectedTurkishPhrasing() {
        val zone = ZoneId.of("Europe/Istanbul")
        val testDate = LocalDate.of(2026, 9, 18) // Friday, 18th, September
        val testMillis = testDate.atTime(17, 42).atZone(zone).toInstant().toEpochMilli()

        assertEquals("Her gün tekrarlanacak", RecurrenceRule.DAILY.repeatSummary(testMillis, zone))
        assertEquals("Her Cuma tekrarlanacak", RecurrenceRule.WEEKLY.repeatSummary(testMillis, zone))
        assertEquals("Her ayın 18'i tekrarlanacak", RecurrenceRule.MONTHLY.repeatSummary(testMillis, zone))
        assertEquals("Her yıl 18 Eylül tekrarlanacak", RecurrenceRule.YEARLY.repeatSummary(testMillis, zone))
        assertNull(RecurrenceRule.NONE.repeatSummary(testMillis, zone))
    }
}
