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
}
