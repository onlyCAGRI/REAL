package com.cagri.real

import com.cagri.real.data.*
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

        
        val recurringReminder = ReminderItem(
            id = 2L,
            text = "Her akşam ilaç",
            createdAt = 1000L,
            alertAt = tomorrow19,
            anchorAlertAt = today19,
            repeatRule = RecurrenceRule.DAILY
        )

        
        
        val afterComplete = if (!recurringReminder.isCompleted && recurringReminder.hasRepeat) {
            recurringReminder 
        } else {
            recurringReminder.copy(completedAt = today19)
        }

        assertFalse(afterComplete.isCompleted)
        assertNull(afterComplete.completedAt)
        assertEquals(tomorrow19, afterComplete.alertAt)
        assertEquals(today19, afterComplete.anchorAlertAt)
        assertEquals(RecurrenceRule.DAILY, afterComplete.repeatRule)

        
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

        
        val snoozed = recurringReminder.copy(
            alertAt = today1910,
            notifiedAlertAt = null,
            anchorAlertAt = recurringReminder.anchorAlertAt ?: recurringReminder.alertAt,
            updatedAt = today19
        )

        assertEquals(today1910, snoozed.alertAt)
        assertEquals(today19, snoozed.anchorAlertAt) 
        assertNull(snoozed.notifiedAlertAt)

        
        assertTrue(snoozed.isAlertDue(today1910))

        
        val anchor = snoozed.anchorAlertAt ?: snoozed.alertAt!!
        val nextOccurrence = computeNextOccurrence(anchor, snoozed.repeatRule, zone, afterMillis = today1910)

        assertNotNull(nextOccurrence)
        assertEquals(tomorrow19, nextOccurrence) 

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

        
        val deliveredReminder = ReminderItem(
            id = 4L,
            text = "Tek seferlik görev",
            createdAt = now,
            alertAt = alertAt,
            notifiedAlertAt = alertAt,
            repeatRule = RecurrenceRule.NONE
        )
        assertFalse(deliveredReminder.isAlertDue(alertAt))

        
        val snoozed = deliveredReminder.copy(
            alertAt = snoozeUntil,
            notifiedAlertAt = null,
            anchorAlertAt = deliveredReminder.anchorAlertAt ?: deliveredReminder.alertAt,
            updatedAt = alertAt
        )

        assertEquals(snoozeUntil, snoozed.alertAt)
        assertNull(snoozed.notifiedAlertAt)
        
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

        
        val firstSnooze = reminder.copy(
            alertAt = snoozeUntil,
            notifiedAlertAt = null,
            anchorAlertAt = reminder.anchorAlertAt ?: reminder.alertAt,
            updatedAt = alertAt
        )

        
        val secondSnooze = firstSnooze.copy(
            alertAt = snoozeUntil,
            notifiedAlertAt = null,
            anchorAlertAt = firstSnooze.anchorAlertAt ?: firstSnooze.alertAt,
            updatedAt = alertAt
        )

        
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

        
        val target = items.firstOrNull { it.id == deletedId }
        assertNull(target)

        
        val updated = items.map { if (it.id == deletedId) it.copy(completedAt = 2000L) else it }
        assertEquals(items, updated)
    }

    @Test
    fun sequenceA_dailyReminderToday18_uiCompleteAt14_advancesToTomorrow18() {
        val zone = ZoneId.of("Europe/Istanbul")
        val today14 = LocalDate.of(2026, 9, 19).atTime(14, 0).atZone(zone).toInstant().toEpochMilli()
        val today18 = LocalDate.of(2026, 9, 19).atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
        val tomorrow18 = LocalDate.of(2026, 9, 20).atTime(18, 0).atZone(zone).toInstant().toEpochMilli()

        val reminder = ReminderItem(
            id = 100L,
            text = "Günlük vitamin",
            createdAt = 1000L,
            alertAt = today18,
            anchorAlertAt = today18,
            repeatRule = RecurrenceRule.DAILY
        )

        
        val afterComplete = reminder.toggleCompleted(now = today14)

        assertFalse(afterComplete.isCompleted)
        assertNull(afterComplete.completedAt)
        assertNull(afterComplete.notifiedAlertAt)
        assertEquals(tomorrow18, afterComplete.alertAt)
        assertEquals(today18, afterComplete.anchorAlertAt)

        val next = listOf(afterComplete).nextAlertAfter(today14)
        assertEquals(tomorrow18, next)
    }

    @Test
    fun sequenceB_today18Fires_deliverDueAdvances_notificationComplete_keepsTomorrow18() {
        val zone = ZoneId.of("Europe/Istanbul")
        val today18 = LocalDate.of(2026, 9, 19).atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
        val tomorrow18 = LocalDate.of(2026, 9, 20).atTime(18, 0).atZone(zone).toInstant().toEpochMilli()

        
        val delivered = ReminderItem(
            id = 101L,
            text = "Günlük vitamin",
            createdAt = 1000L,
            alertAt = tomorrow18,
            anchorAlertAt = today18,
            notifiedAlertAt = today18,
            repeatRule = RecurrenceRule.DAILY
        )

        
        val afterComplete = if (!delivered.isCompleted && delivered.hasRepeat) {
            delivered.copy(notifiedAlertAt = null, updatedAt = today18)
        } else delivered.copy(completedAt = today18)

        assertFalse(afterComplete.isCompleted)
        assertNull(afterComplete.completedAt)
        assertNull(afterComplete.notifiedAlertAt)
        
        assertEquals(tomorrow18, afterComplete.alertAt)

        val next = listOf(afterComplete).nextAlertAfter(today18)
        assertEquals(tomorrow18, next)
    }

    @Test
    fun sequenceC_today18Fires_userDismissesNotification_laterCompletesFromUi_noAccidentalDoubleAdvance() {
        val zone = ZoneId.of("Europe/Istanbul")
        val today18 = LocalDate.of(2026, 9, 19).atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
        val today1830 = today18 + 30 * 60 * 1000L
        val tomorrow18 = LocalDate.of(2026, 9, 20).atTime(18, 0).atZone(zone).toInstant().toEpochMilli()

        
        
        val reminder = ReminderItem(
            id = 102L,
            text = "Günlük vitamin",
            createdAt = 1000L,
            alertAt = tomorrow18,
            anchorAlertAt = today18,
            notifiedAlertAt = today18,
            repeatRule = RecurrenceRule.DAILY
        )

        
        val afterComplete = reminder.toggleCompleted(now = today1830)

        assertFalse(afterComplete.isCompleted)
        assertNull(afterComplete.completedAt)
        assertNull(afterComplete.notifiedAlertAt)
        
        assertEquals(tomorrow18, afterComplete.alertAt)

        val next = listOf(afterComplete).nextAlertAfter(today1830)
        assertEquals(tomorrow18, next)
    }

    @Test
    fun sequenceD_today18Fires_processRecreated_thenCompletesFromUi_deterministicResult() {
        val zone = ZoneId.of("Europe/Istanbul")
        val today18 = LocalDate.of(2026, 9, 19).atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
        val today1900 = today18 + 60 * 60 * 1000L
        val tomorrow18 = LocalDate.of(2026, 9, 20).atTime(18, 0).atZone(zone).toInstant().toEpochMilli()

        
        val reloadedReminder = ReminderItem(
            id = 103L,
            text = "Günlük vitamin",
            createdAt = 1000L,
            alertAt = tomorrow18,
            anchorAlertAt = today18,
            notifiedAlertAt = today18,
            repeatRule = RecurrenceRule.DAILY
        )

        val afterComplete = reloadedReminder.toggleCompleted(now = today1900)

        assertFalse(afterComplete.isCompleted)
        assertNull(afterComplete.completedAt)
        assertNull(afterComplete.notifiedAlertAt)
        assertEquals(tomorrow18, afterComplete.alertAt)

        val next = listOf(afterComplete).nextAlertAfter(today1900)
        assertEquals(tomorrow18, next)
    }

    @Test
    fun sequenceE_afterPreviousOccurrenceAcknowledged_tomorrowAt14UiComplete_skipsTomorrow18ToFollowing() {
        val zone = ZoneId.of("Europe/Istanbul")
        val today18 = LocalDate.of(2026, 9, 19).atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
        val tomorrow14 = LocalDate.of(2026, 9, 20).atTime(14, 0).atZone(zone).toInstant().toEpochMilli()
        val tomorrow18 = LocalDate.of(2026, 9, 20).atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
        val dayAfterTomorrow18 = LocalDate.of(2026, 9, 21).atTime(18, 0).atZone(zone).toInstant().toEpochMilli()

        
        val reminder = ReminderItem(
            id = 104L,
            text = "Günlük vitamin",
            createdAt = 1000L,
            alertAt = tomorrow18,
            anchorAlertAt = today18,
            notifiedAlertAt = null,
            repeatRule = RecurrenceRule.DAILY
        )

        
        val afterComplete = reminder.toggleCompleted(now = tomorrow14)

        assertFalse(afterComplete.isCompleted)
        assertNull(afterComplete.completedAt)
        assertNull(afterComplete.notifiedAlertAt)
        
        assertEquals(dayAfterTomorrow18, afterComplete.alertAt)

        val next = listOf(afterComplete).nextAlertAfter(tomorrow14)
        assertEquals(dayAfterTomorrow18, next)
    }

    @Test
    fun sequenceF_legacyRecurringItemWithCompletedAtNotNull_undo_becomesActiveSafely() {
        val zone = ZoneId.of("Europe/Istanbul")
        val today18 = LocalDate.of(2026, 9, 19).atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
        val tomorrow18 = LocalDate.of(2026, 9, 20).atTime(18, 0).atZone(zone).toInstant().toEpochMilli()

        
        val legacyCompleted = ReminderItem(
            id = 105L,
            text = "Eski tamamlanmış tekrarlı not",
            createdAt = 1000L,
            completedAt = today18,
            alertAt = tomorrow18,
            anchorAlertAt = today18,
            repeatRule = RecurrenceRule.DAILY
        )
        assertTrue(legacyCompleted.isCompleted)

        
        val restored = legacyCompleted.toggleCompleted(now = today18 + 1000L)

        assertFalse(restored.isCompleted)
        assertNull(restored.completedAt)
        assertNull(restored.notifiedAlertAt)
        assertEquals(tomorrow18, restored.alertAt)

        val next = listOf(restored).nextAlertAfter(today18)
        assertEquals(tomorrow18, next)
    }
}
