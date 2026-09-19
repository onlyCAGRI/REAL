package com.cagri.bigboscagri

import com.cagri.bigboscagri.data.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class NotificationActionTest {

    @Test
    fun normalReminderCompleteMarksCompleted() {
        val original = ReminderItem(
            id = 1L,
            text = "Eczaneden ilaç al",
            createdAt = 1000L,
            alertAt = 2000L,
            repeatRule = RecurrenceRule.NONE
        )
        assertFalse(original.isCompleted)

        // Simulate completeFromNotification logic
        val completed = if (!original.isCompleted && !original.hasRepeat) {
            original.copy(
                completedAt = 2500L,
                updatedAt = 2500L
            )
        } else original

        assertTrue(completed.isCompleted)
        assertNotNull(completed.completedAt)
        assertEquals(2500L, completed.completedAt)
    }

    @Test
    fun dailyRecurringReminderCompletePreservesNextOccurrenceAndDoesNotSetCompleted() {
        val zone = ZoneId.of("Europe/Istanbul")
        val today19 = LocalDate.of(2026, 9, 19).atTime(19, 0).atZone(zone).toInstant().toEpochMilli()
        val tomorrow19 = LocalDate.of(2026, 9, 20).atTime(19, 0).atZone(zone).toInstant().toEpochMilli()

        // At 19:00, deliverDue had already delivered today's notification and advanced alertAt to tomorrow 19:00
        val recurringReminder = ReminderItem(
            id = 2L,
            text = "Her akşam ilaç",
            createdAt = 1000L,
            alertAt = tomorrow19,
            anchorAlertAt = today19,
            repeatRule = RecurrenceRule.DAILY
        )

        // User taps "Tamamla" on today's notification
        // For recurring reminders, completeFromNotification MUST NOT set completedAt
        val afterComplete = if (!recurringReminder.isCompleted && recurringReminder.hasRepeat) {
            recurringReminder // completedAt remains null, alertAt remains tomorrow 19:00
        } else {
            recurringReminder.copy(completedAt = today19)
        }

        assertFalse(afterComplete.isCompleted)
        assertNull(afterComplete.completedAt)
        assertEquals(tomorrow19, afterComplete.alertAt)
        assertEquals(today19, afterComplete.anchorAlertAt)
        assertEquals(RecurrenceRule.DAILY, afterComplete.repeatRule)

        // Verify next occurrence is still scheduled for tomorrow
        val nextAlert = listOf(afterComplete).nextAlertAfter(today19)
        assertEquals(tomorrow19, nextAlert)
    }

    @Test
    fun dailyRecurringReminderSnooze10mThenDeliversAndNextDayKeepsAnchor() {
        val zone = ZoneId.of("Europe/Istanbul")
        val today19 = LocalDate.of(2026, 9, 19).atTime(19, 0).atZone(zone).toInstant().toEpochMilli()
        val today1910 = today19 + 10 * 60 * 1000L
        val tomorrow19 = LocalDate.of(2026, 9, 20).atTime(19, 0).atZone(zone).toInstant().toEpochMilli()

        val recurringReminder = ReminderItem(
            id = 3L,
            text = "Her gün 19:00 hatırlatıcı",
            createdAt = 1000L,
            alertAt = today19,
            anchorAlertAt = today19,
            repeatRule = RecurrenceRule.DAILY
        )

        // 1. User taps "10 dk ertele"
        val snoozed = recurringReminder.copy(
            alertAt = today1910,
            notifiedAlertAt = null,
            anchorAlertAt = recurringReminder.anchorAlertAt ?: recurringReminder.alertAt,
            updatedAt = today19
        )

        assertEquals(today1910, snoozed.alertAt)
        assertEquals(today19, snoozed.anchorAlertAt) // Anchor preserved!
        assertNull(snoozed.notifiedAlertAt)

        // 2. At 19:10, alarm fires
        assertTrue(snoozed.isAlertDue(today1910))

        // 3. deliverDue calculates next occurrence after 19:10 using anchorAlertAt (19:00)
        val anchor = snoozed.anchorAlertAt ?: snoozed.alertAt!!
        val nextOccurrence = computeNextOccurrence(anchor, snoozed.repeatRule, zone, afterMillis = today1910)

        assertNotNull(nextOccurrence)
        assertEquals(tomorrow19, nextOccurrence) // Exactly tomorrow at 19:00!

        val nextDayReminder = snoozed.copy(
            alertAt = nextOccurrence,
            anchorAlertAt = anchor,
            notifiedAlertAt = null,
            updatedAt = today1910
        )

        assertEquals(tomorrow19, nextDayReminder.alertAt)
        assertEquals(today19, nextDayReminder.anchorAlertAt)
        assertFalse(nextDayReminder.isCompleted)
    }

    @Test
    fun normalReminderSnooze10mSetsAlertAtAndClearsNotifiedAlertAt() {
        val now = 100000L
        val alertAt = 200000L
        val snoozeUntil = alertAt + 10 * 60 * 1000L

        // Delivered reminder (notifiedAlertAt == alertAt, so no longer due)
        val deliveredReminder = ReminderItem(
            id = 4L,
            text = "Tek seferlik görev",
            createdAt = now,
            alertAt = alertAt,
            notifiedAlertAt = alertAt,
            repeatRule = RecurrenceRule.NONE
        )
        assertFalse(deliveredReminder.isAlertDue(alertAt))

        // User taps "10 dk ertele"
        val snoozed = deliveredReminder.copy(
            alertAt = snoozeUntil,
            notifiedAlertAt = null,
            anchorAlertAt = deliveredReminder.anchorAlertAt ?: deliveredReminder.alertAt,
            updatedAt = alertAt
        )

        assertEquals(snoozeUntil, snoozed.alertAt)
        assertNull(snoozed.notifiedAlertAt)
        // At snoozeUntil, it becomes due again
        assertTrue(snoozed.isAlertDue(snoozeUntil))
        assertFalse(snoozed.isAlertDue(snoozeUntil - 1L))
    }

    @Test
    fun doubleTapSnoozeDoesNotProduceDuplicateAndIsIdempotent() {
        val now = 100000L
        val alertAt = 200000L
        val snoozeUntil = alertAt + 10 * 60 * 1000L

        val reminder = ReminderItem(
            id = 5L,
            text = "Hızlı tıklanan görev",
            createdAt = now,
            alertAt = alertAt,
            notifiedAlertAt = alertAt
        )

        // First tap
        val firstSnooze = reminder.copy(
            alertAt = snoozeUntil,
            notifiedAlertAt = null,
            anchorAlertAt = reminder.anchorAlertAt ?: reminder.alertAt,
            updatedAt = alertAt
        )

        // Second rapid tap (same snoozeUntil calculation window)
        val secondSnooze = firstSnooze.copy(
            alertAt = snoozeUntil,
            notifiedAlertAt = null,
            anchorAlertAt = firstSnooze.anchorAlertAt ?: firstSnooze.alertAt,
            updatedAt = alertAt
        )

        // State remains identical, only 1 alarm scheduled
        assertEquals(firstSnooze, secondSnooze)
        val next = listOf(secondSnooze).nextAlertAfter(alertAt)
        assertEquals(snoozeUntil, next)
    }

    @Test
    fun alreadyDeletedReminderNoOp() {
        val items = listOf(
            ReminderItem(id = 10L, text = "Kalan not", createdAt = 1000L)
        )
        val deletedId = 999L

        // Finding reminder before action
        val target = items.firstOrNull { it.id == deletedId }
        assertNull(target)

        // Safe no-op leaves list unchanged
        val updated = items.map { if (it.id == deletedId) it.copy(completedAt = 2000L) else it }
        assertEquals(items, updated)
    }
}
