package com.cagri.bigboscagri

import com.cagri.bigboscagri.data.ReminderItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class NowBarReminderTest {

    @Test
    fun testEligibleReminderSelection() {
        val now = 1000000L
        // A reminder exactly at 14:00 (for example)
        val alertTime = now + 10 * 60 * 1000L
        val reminder = ReminderItem(id = 1L, text = "Toplantı", createdAt = now, alertAt = alertTime)

        // It is eligible because it has alertAt and is not completed and alertAt > now - 30m
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
        val r1 = ReminderItem(id = 1L, text = "R1", createdAt = now, alertAt = now + 10 * 60 * 1000L) // +10m
        val r2 = ReminderItem(id = 2L, text = "R2", createdAt = now, alertAt = now + 5 * 60 * 1000L) // +5m (nearest)
        
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

        // Tie break by ID (lower ID wins)
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
        
        // Simulate edit
        val r1Edited = r1.copy(alertAt = now - 40 * 60 * 1000L) // edited to 40 minutes ago
        
        eligible = listOf(r1Edited).filter { 
            !it.isCompleted && !it.isNoAlert && it.alertAt != null && it.alertAt > now - 30 * 60 * 1000L
        }
        best = eligible.minWithOrNull(compareBy({ it.alertAt }, { it.id }))
        assertNull(best) // No longer eligible
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
        
        // Complete it
        val r1Completed = r1.toggleCompleted(now)
        
        eligible = listOf(r1Completed).filter { 
            !it.isCompleted && !it.isNoAlert && it.alertAt != null && it.alertAt > now - 30 * 60 * 1000L
        }
        best = eligible.minWithOrNull(compareBy({ it.alertAt }, { it.id }))
        assertNull(best) // Cancels because best becomes null
    }
}
