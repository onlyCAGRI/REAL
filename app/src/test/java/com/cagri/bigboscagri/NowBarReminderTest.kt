package com.cagri.bigboscagri

import com.cagri.bigboscagri.data.ReminderItem
import com.cagri.bigboscagri.notifications.NowBarReminderController
import com.cagri.bigboscagri.notifications.ReminderNotifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class NowBarReminderTest {

    @Test
    fun testEligibleReminderSelection() {
        val now = 1000000L
        
        val alertTime = now + 10 * 60 * 1000L
        val reminder = ReminderItem(id = 1L, text = "Toplantı", createdAt = now, alertAt = alertTime)

        
        val eligible = listOf(reminder).filter {
            !it.isCompleted && !it.isNoAlert && it.alertAt != null && it.alertAt > now - 30 * 60 * 1000L
        }
        assertEquals(1, eligible.size)
        assertEquals(reminder, eligible.first())
    }

    @Test
    fun testCompletedReminderNotEligible() {
        val now = 1000000L
        val reminder = ReminderItem(id = 1L, text = "Toplantı", createdAt = now, alertAt = now + 1000, completedAt = now)

        val eligible = listOf(reminder).filter {
            !it.isCompleted && !it.isNoAlert && it.alertAt != null && it.alertAt > now - 30 * 60 * 1000L
        }
        assertEquals(0, eligible.size)
    }

    @Test
    fun testNoExactTimeNotEligible() {
        val now = 1000000L
        val reminder = ReminderItem(id = 1L, text = "Toplantı", createdAt = now, alertAt = null)

        val eligible = listOf(reminder).filter {
            !it.isCompleted && !it.isNoAlert && it.alertAt != null && it.alertAt > now - 30 * 60 * 1000L
        }
        assertEquals(0, eligible.size)
    }

    @Test
    fun testNoAlertNotEligible() {
        val now = 1000000L
        val reminder = ReminderItem(id = 1L, text = "Uyarı Yok", createdAt = now, alertAt = null, customCategoryId = null)

        val eligible = listOf(reminder).filter {
            !it.isCompleted && !it.isNoAlert && it.alertAt != null && it.alertAt > now - 30 * 60 * 1000L
        }
        assertEquals(0, eligible.size)
    }

    @Test
    fun testOverlappingRemindersNearestWins() {
        val now = 1000000L
        val r1 = ReminderItem(id = 1L, text = "R1", createdAt = now, alertAt = now + 10 * 60 * 1000L) 
        val r2 = ReminderItem(id = 2L, text = "R2", createdAt = now, alertAt = now + 5 * 60 * 1000L) 

        val eligible = listOf(r1, r2).filter {
            !it.isCompleted && !it.isNoAlert && it.alertAt != null && it.alertAt > now - 30 * 60 * 1000L
        }

        val best = eligible.minWithOrNull(compareBy({ it.alertAt }, { it.id }))
        assertNotNull(best)
        assertEquals(2L, best?.id)
    }

    @Test
    fun testOverlappingRemindersTieBreaker() {
        val now = 1000000L
        val r1 = ReminderItem(id = 5L, text = "R1", createdAt = now, alertAt = now + 5 * 60 * 1000L)
        val r2 = ReminderItem(id = 2L, text = "R2", createdAt = now, alertAt = now + 5 * 60 * 1000L)

        val eligible = listOf(r1, r2).filter {
            !it.isCompleted && !it.isNoAlert && it.alertAt != null && it.alertAt > now - 30 * 60 * 1000L
        }

        
        val best = eligible.minWithOrNull(compareBy({ it.alertAt }, { it.id }))
        assertNotNull(best)
        assertEquals(2L, best?.id)
    }

    @Test
    fun testDateEditRecalculatesBest() {
        val now = 1000000L
        val r1 = ReminderItem(id = 1L, text = "R1", createdAt = now, alertAt = now + 10 * 60 * 1000L)

        var eligible = listOf(r1).filter {
            !it.isCompleted && !it.isNoAlert && it.alertAt != null && it.alertAt > now - 30 * 60 * 1000L
        }
        var best = eligible.minWithOrNull(compareBy({ it.alertAt }, { it.id }))
        assertNotNull(best)

        
        val r1Edited = r1.copy(alertAt = now - 40 * 60 * 1000L) 

        eligible = listOf(r1Edited).filter {
            !it.isCompleted && !it.isNoAlert && it.alertAt != null && it.alertAt > now - 30 * 60 * 1000L
        }
        best = eligible.minWithOrNull(compareBy({ it.alertAt }, { it.id }))
        assertNull(best) 
    }

    @Test
    fun testCompletionCancelsNowBar() {
        val now = 1000000L
        val r1 = ReminderItem(id = 1L, text = "R1", createdAt = now, alertAt = now + 5 * 60 * 1000L)

        var eligible = listOf(r1).filter {
            !it.isCompleted && !it.isNoAlert && it.alertAt != null && it.alertAt > now - 30 * 60 * 1000L
        }
        var best = eligible.minWithOrNull(compareBy({ it.alertAt }, { it.id }))
        assertNotNull(best)

        
        val r1Completed = r1.toggleCompleted(now)

        eligible = listOf(r1Completed).filter {
            !it.isCompleted && !it.isNoAlert && it.alertAt != null && it.alertAt > now - 30 * 60 * 1000L
        }
        best = eligible.minWithOrNull(compareBy({ it.alertAt }, { it.id }))
        assertNull(best) 
    }

    @Test
    fun manualPinOverridesAutomaticCandidate() {
        val now = 1_000_000L
        val automatic = ReminderItem(id = 1L, text = "Yakın", createdAt = now, alertAt = now + 60_000L)
        val manual = ReminderItem(id = 2L, text = "Manuel", createdAt = now, alertAt = null)

        val selected = NowBarReminderController.selectNowBarReminder(listOf(automatic, manual), now, manual.id)

        assertEquals(manual, selected)
    }

    @Test
    fun changingManualPinReplacesPreviousSelection() {
        val now = 1_000_000L
        val first = ReminderItem(id = 1L, text = "İlk", createdAt = now)
        val second = ReminderItem(id = 2L, text = "İkinci", createdAt = now)

        assertEquals(first, NowBarReminderController.selectNowBarReminder(listOf(first, second), now, first.id))
        assertEquals(second, NowBarReminderController.selectNowBarReminder(listOf(first, second), now, second.id))
    }

    @Test
    fun removingManualPinFallsBackToAutomaticCandidate() {
        val now = 1_000_000L
        val automatic = ReminderItem(id = 1L, text = "Yakın", createdAt = now, alertAt = now + 60_000L)
        val manual = ReminderItem(id = 2L, text = "Manuel", createdAt = now)

        val selected = NowBarReminderController.selectNowBarReminder(listOf(automatic, manual), now, null)

        assertEquals(automatic, selected)
    }

    @Test
    fun completedManualPinIsInvalid() {
        val now = 1_000_000L
        val completed = ReminderItem(id = 2L, text = "Bitti", createdAt = now, completedAt = now)

        assertNull(NowBarReminderController.resolveValidManualPinnedReminderId(listOf(completed), completed.id))
    }

    @Test
    fun missingManualPinIsInvalid() {
        val now = 1_000_000L
        val reminder = ReminderItem(id = 1L, text = "Var", createdAt = now)

        assertNull(NowBarReminderController.resolveValidManualPinnedReminderId(listOf(reminder), 99L))
    }

    @Test
    fun manualReminderWithoutDateHasNoSecondaryLabel() {
        val now = 1_000_000L
        val reminder = ReminderItem(id = 1L, text = "Not", createdAt = now)

        assertEquals("", NowBarReminderController.secondaryText(reminder, now, manuallyPinned = true))
    }

    @Test
    fun nowBarSnoozeActionOnlyAppearsInsideScheduledWindow() {
        val now = 1_000_000L
        val noDate = ReminderItem(id = 1L, text = "Not", createdAt = now)
        val farAway = ReminderItem(id = 2L, text = "Sonra", createdAt = now, alertAt = now + 60 * 60 * 1000L)
        val withinFiveMinutes = ReminderItem(id = 3L, text = "Yakın", createdAt = now, alertAt = now + 4 * 60 * 1000L)
        val due = ReminderItem(id = 4L, text = "Şimdi", createdAt = now, alertAt = now - 1L)

        assertEquals(false, NowBarReminderController.shouldShowSnoozeAction(noDate, now))
        assertEquals(false, NowBarReminderController.shouldShowSnoozeAction(farAway, now))
        assertEquals(true, NowBarReminderController.shouldShowSnoozeAction(withinFiveMinutes, now))
        assertEquals(true, NowBarReminderController.shouldShowSnoozeAction(due, now))
    }

    @Test
    fun pinnedNotificationOwnershipDoesNotIncludeNowBarChannel() {
        assertEquals(true, ReminderNotifications.ownsPinnedNotification("important_reminders", "reminder"))
        assertEquals(true, ReminderNotifications.ownsPinnedNotification("real_reminders_v4", "reminder"))
        assertEquals(false, ReminderNotifications.ownsPinnedNotification("real_now_bar_reminders_v1", "status"))
    }
}
