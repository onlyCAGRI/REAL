package com.cagri.real

import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import com.cagri.real.data.AttachmentType
import com.cagri.real.data.ChecklistItem
import com.cagri.real.data.JournalMood
import com.cagri.real.data.RecurrenceRule
import com.cagri.real.data.ReminderAttachment
import com.cagri.real.data.ReminderLocation
import com.cagri.real.data.ReminderRepository
import com.cagri.real.data.ReminderVoiceRecording
import com.cagri.real.ui.components.ReminderColorChoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class ReminderColorGradientEditTest {

    @Test
    fun serializeAndDeserializeRoundtrip_withSecondaryColorInHighRange() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)
        val initial = repo.add("Two color gradient reminder")

        // Royal Blue (#2) + Somon Turuncu (#12) with gradient blend fraction 0.65
        val updated = repo.edit(
            id = initial.id,
            text = "Two color gradient reminder",
            accentStart = 2,
            accentEnd = 12,
            gradientBlendFraction = 0.65f
        )

        val edited = updated.first { it.id == initial.id }
        assertEquals(2, edited.accentStart)
        assertEquals(12, edited.accentEnd)
        assertEquals(0.65f, edited.gradientBlendFraction!!, 0.001f)
        assertNull(edited.customSolidColorArgb)

        // Reload repository from JSON persistence (simulating app restart)
        val reloadedRepo = ReminderRepository(prefs)
        val reloaded = reloadedRepo.load().first { it.id == initial.id }
        assertEquals(2, reloaded.accentStart)
        assertEquals(12, reloaded.accentEnd)
        assertEquals(0.65f, reloaded.gradientBlendFraction!!, 0.001f)
        assertNull(reloaded.customSolidColorArgb)
    }

    @Test
    fun reproductionMatrix_case1_noColor_save() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)
        val item = repo.add("No color reminder")

        val updated = repo.edit(
            id = item.id,
            text = "No color reminder",
            accentStart = null,
            accentEnd = null,
            gradientBlendFraction = null,
            customSolidColorArgb = null
        ).first { it.id == item.id }

        assertNull(updated.accentStart)
        assertNull(updated.accentEnd)
        assertNull(updated.gradientBlendFraction)
        assertNull(updated.customSolidColorArgb)

        val reloaded = ReminderRepository(prefs).load().first { it.id == item.id }
        assertNull(reloaded.accentStart)
        assertNull(reloaded.accentEnd)
        assertNull(reloaded.gradientBlendFraction)
        assertNull(reloaded.customSolidColorArgb)
    }

    @Test
    fun reproductionMatrix_case2_onlyPrimaryColor_save() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)
        val item = repo.add("Primary color only")

        // Test with both low (<10) and high (>=10) primary color indices
        for (colorIdx in listOf(3, 15)) {
            val updated = repo.edit(
                id = item.id,
                text = "Primary color only",
                accentStart = colorIdx,
                accentEnd = null,
                gradientBlendFraction = null,
                customSolidColorArgb = null
            ).first { it.id == item.id }

            assertEquals(colorIdx, updated.accentStart)
            assertNull(updated.accentEnd)
            assertNull(updated.gradientBlendFraction)

            val reloaded = ReminderRepository(prefs).load().first { it.id == item.id }
            assertEquals(colorIdx, reloaded.accentStart)
            assertNull(reloaded.accentEnd)
            assertNull(reloaded.gradientBlendFraction)
        }
    }

    @Test
    fun reproductionMatrix_case3_primaryPlusSecondaryColor_save() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)
        val item = repo.add("Primary + Secondary")

        val updated = repo.edit(
            id = item.id,
            text = "Primary + Secondary",
            accentStart = 4, // Gökyüzü
            accentEnd = 17,  // Lavanta
            gradientBlendFraction = 0.50f
        ).first { it.id == item.id }

        assertEquals(4, updated.accentStart)
        assertEquals(17, updated.accentEnd)
        assertEquals(0.50f, updated.gradientBlendFraction!!, 0.001f)

        val reloaded = ReminderRepository(prefs).load().first { it.id == item.id }
        assertEquals(4, reloaded.accentStart)
        assertEquals(17, reloaded.accentEnd)
        assertEquals(0.50f, reloaded.gradientBlendFraction!!, 0.001f)
    }

    @Test
    fun reproductionMatrix_case4_twoColorSelect_gradientToggleOff_save() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)
        val item = repo.add("Transition bridge solid")

        // When "Geçiş rengini kullan" is toggled, secondary color is converted to bridge solid color
        val startColor = ReminderColorChoices[2].color
        val endColor = ReminderColorChoices[13].color
        val bridgeArgb = lerp(startColor, endColor, 0.40f).toArgb()

        val updated = repo.edit(
            id = item.id,
            text = "Transition bridge solid",
            accentStart = 2,
            accentEnd = null,
            gradientBlendFraction = null,
            customSolidColorArgb = bridgeArgb
        ).first { it.id == item.id }

        assertEquals(2, updated.accentStart)
        assertNull(updated.accentEnd)
        assertNull(updated.gradientBlendFraction)
        assertEquals(bridgeArgb, updated.customSolidColorArgb)

        val reloaded = ReminderRepository(prefs).load().first { it.id == item.id }
        assertEquals(2, reloaded.accentStart)
        assertNull(reloaded.accentEnd)
        assertNull(reloaded.gradientBlendFraction)
        assertEquals(bridgeArgb, reloaded.customSolidColorArgb)
    }

    @Test
    fun reproductionMatrix_case5_twoColorSelect_gradientToggleOn_save() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)
        val item = repo.add("Active gradient")

        val updated = repo.edit(
            id = item.id,
            text = "Active gradient",
            accentStart = 6, // Turkuaz
            accentEnd = 16,  // Fuşya
            gradientBlendFraction = 0.72f,
            customSolidColorArgb = null
        ).first { it.id == item.id }

        assertEquals(6, updated.accentStart)
        assertEquals(16, updated.accentEnd)
        assertEquals(0.72f, updated.gradientBlendFraction!!, 0.001f)
        assertNull(updated.customSolidColorArgb)

        val reloaded = ReminderRepository(prefs).load().first { it.id == item.id }
        assertEquals(6, reloaded.accentStart)
        assertEquals(16, reloaded.accentEnd)
        assertEquals(0.72f, reloaded.gradientBlendFraction!!, 0.001f)
        assertNull(reloaded.customSolidColorArgb)
    }

    @Test
    fun reproductionMatrix_case6_singleColorToTwoColors_save() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)
        val item = repo.add("Single to two-color")

        // First save as single color
        repo.edit(item.id, "Single to two-color", accentStart = 1, accentEnd = null)

        // Then edit to add secondary color >= 10
        val updated = repo.edit(
            id = item.id,
            text = "Single to two-color",
            accentStart = 1,
            accentEnd = 10, // Altın
            gradientBlendFraction = 0.50f
        ).first { it.id == item.id }

        assertEquals(1, updated.accentStart)
        assertEquals(10, updated.accentEnd)
        assertEquals(0.50f, updated.gradientBlendFraction!!, 0.001f)

        val reloaded = ReminderRepository(prefs).load().first { it.id == item.id }
        assertEquals(1, reloaded.accentStart)
        assertEquals(10, reloaded.accentEnd)
        assertEquals(0.50f, reloaded.gradientBlendFraction!!, 0.001f)
    }

    @Test
    fun reproductionMatrix_case7_changeTwoColorGradient_save() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)
        val item = repo.add("Two-color modify")

        repo.edit(item.id, "Two-color modify", accentStart = 2, accentEnd = 12, gradientBlendFraction = 0.35f)

        // Modify both colors to new choices and fraction
        val updated = repo.edit(
            id = item.id,
            text = "Two-color modify",
            accentStart = 5,
            accentEnd = 18,
            gradientBlendFraction = 0.80f
        ).first { it.id == item.id }

        assertEquals(5, updated.accentStart)
        assertEquals(18, updated.accentEnd)
        assertEquals(0.80f, updated.gradientBlendFraction!!, 0.001f)

        val reloaded = ReminderRepository(prefs).load().first { it.id == item.id }
        assertEquals(5, reloaded.accentStart)
        assertEquals(18, reloaded.accentEnd)
        assertEquals(0.80f, reloaded.gradientBlendFraction!!, 0.001f)
    }

    @Test
    fun reproductionMatrix_case8_revertTwoColorsToSingleColor_save() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)
        val item = repo.add("Revert to single")

        repo.edit(item.id, "Revert to single", accentStart = 3, accentEnd = 14, gradientBlendFraction = 0.60f)

        // Revert to single color (accentEnd = null)
        val updated = repo.edit(
            id = item.id,
            text = "Revert to single",
            accentStart = 3,
            accentEnd = null,
            gradientBlendFraction = null
        ).first { it.id == item.id }

        assertEquals(3, updated.accentStart)
        assertNull(updated.accentEnd)
        assertNull(updated.gradientBlendFraction)

        val reloaded = ReminderRepository(prefs).load().first { it.id == item.id }
        assertEquals(3, reloaded.accentStart)
        assertNull(reloaded.accentEnd)
        assertNull(reloaded.gradientBlendFraction)
    }

    @Test
    fun reproductionMatrix_case9_reopenApp_preservesBothColorsAndGradient() {
        val prefs = FakeSharedPreferences()
        val repo1 = ReminderRepository(prefs)
        val item = repo1.add("Persistence test")

        repo1.edit(item.id, "Persistence test", accentStart = 7, accentEnd = 19, gradientBlendFraction = 0.45f)

        // Completely new repository instance reading from same shared preferences
        val repo2 = ReminderRepository(prefs)
        val loaded = repo2.load().first { it.id == item.id }
        assertEquals(7, loaded.accentStart)
        assertEquals(19, loaded.accentEnd)
        assertEquals(0.45f, loaded.gradientBlendFraction!!, 0.001f)
    }

    @Test
    fun dataSafety_colorEditPreservesAllReminderFields() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)

        val alertTime = System.currentTimeMillis() + 3_600_000L
        val item = repo.add(
            text = "Important data note",
            alertAt = alertTime,
            repeatRule = RecurrenceRule.DAILY,
            customCategoryId = 42L,
            isChecklist = true,
            checklistItems = listOf(
                ChecklistItem(id = 101L, text = "First item", completedAt = 500L),
                ChecklistItem(id = 102L, text = "Second item")
            ),
            referenceUrl = "https://example.com/ref",
            attachments = listOf(
                ReminderAttachment(id = "att_1", localPath = "/data/file.jpg", type = AttachmentType.IMAGE)
            ),
            voiceRecordings = listOf(
                ReminderVoiceRecording(id = "rec_1", localPath = "/data/audio.m4a", durationMillis = 5000L, createdAt = 1000L)
            ),
            location = ReminderLocation(placeName = "Office", address = "123 Main St", latitude = 41.0, longitude = 29.0)
        )

        // Set journal and mood
        repo.setJournal(item.id, JournalMood.GOOD, "Today was a productive day")
        repo.setMood(item.id, JournalMood.CALM)

        val before = repo.load().first { it.id == item.id }

        // Perform color edit with two colors
        val updatedList = repo.edit(
            id = item.id,
            text = before.text,
            accentStart = 2,
            accentEnd = 14,
            gradientBlendFraction = 0.55f,
            customCategoryId = before.customCategoryId,
            checklistItems = before.checklistItems,
            attachments = before.attachments,
            voiceRecordings = before.voiceRecordings,
            location = before.location,
            referenceUrl = before.referenceUrl,
            musicBlocks = before.musicBlocks,
            contentBlockOrder = before.contentBlockOrder
        )

        val after = updatedList.first { it.id == item.id }

        // Verify color updates
        assertEquals(2, after.accentStart)
        assertEquals(14, after.accentEnd)
        assertEquals(0.55f, after.gradientBlendFraction!!, 0.001f)

        // Verify ALL other fields are completely preserved
        assertEquals(before.text, after.text)
        assertEquals(before.alertAt, after.alertAt)
        assertEquals(before.anchorAlertAt, after.anchorAlertAt)
        assertEquals(before.repeatRule, after.repeatRule)
        assertEquals(before.customCategoryId, after.customCategoryId)
        assertEquals(before.isChecklist, after.isChecklist)
        assertEquals(before.checklistItems.size, after.checklistItems.size)
        assertEquals(before.checklistItems[0].text, after.checklistItems[0].text)
        assertEquals(before.checklistItems[0].completedAt, after.checklistItems[0].completedAt)
        assertEquals(before.checklistItems[1].text, after.checklistItems[1].text)
        assertEquals(before.referenceUrl, after.referenceUrl)
        assertEquals(before.attachments.size, after.attachments.size)
        assertEquals(before.attachments[0].id, after.attachments[0].id)
        assertEquals(before.attachments[0].localPath, after.attachments[0].localPath)
        assertEquals(before.voiceRecordings.size, after.voiceRecordings.size)
        assertEquals(before.voiceRecordings[0].id, after.voiceRecordings[0].id)
        assertEquals(before.location?.placeName, after.location?.placeName)
        assertEquals(before.location?.address, after.location?.address)
        assertEquals(before.location?.latitude, after.location?.latitude)
        assertEquals(before.journal?.mood, after.journal?.mood)
        assertEquals(before.journal?.text, after.journal?.text)
        assertEquals(before.mood, after.mood)

        // Verify persistence roundtrip preserves all fields as well
        val reloaded = ReminderRepository(prefs).load().first { it.id == item.id }
        assertEquals(after.text, reloaded.text)
        assertEquals(after.alertAt, reloaded.alertAt)
        assertEquals(after.repeatRule, reloaded.repeatRule)
        assertEquals(after.checklistItems.size, reloaded.checklistItems.size)
        assertEquals(after.attachments.size, reloaded.attachments.size)
        assertEquals(after.voiceRecordings.size, reloaded.voiceRecordings.size)
        assertEquals(after.location?.placeName, reloaded.location?.placeName)
        assertEquals(after.journal?.text, reloaded.journal?.text)
        assertEquals(after.mood, reloaded.mood)
        assertEquals(2, reloaded.accentStart)
        assertEquals(14, reloaded.accentEnd)
        assertEquals(0.55f, reloaded.gradientBlendFraction!!, 0.001f)
    }

    @Test
    fun allPaletteColorsAreValidAndSurvivePersistence() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)

        assertEquals(24, ReminderColorChoices.size)
        assertEquals(ReminderColorChoices.indices, ReminderRepository.ACCENT_RANGE)

        // Iterate through all current colors and ensure both accentStart and accentEnd can take each index
        for (i in ReminderColorChoices.indices) {
            val item = repo.add("Color palette index $i")
            val partner = (i + 1) % ReminderColorChoices.size

            val updated = repo.edit(
                id = item.id,
                text = "Color palette index $i",
                accentStart = i,
                accentEnd = partner,
                starBorderColorIndex = i,
                gradientBlendFraction = 0.50f
            ).first { it.id == item.id }

            assertEquals(i, updated.accentStart)
            assertEquals(partner, updated.accentEnd)
            assertEquals(i, updated.starBorderColorIndex)

            val reloaded = ReminderRepository(prefs).load().first { it.id == item.id }
            assertEquals(i, reloaded.accentStart)
            assertEquals(partner, reloaded.accentEnd)
            assertEquals(i, reloaded.starBorderColorIndex)
        }
    }

    @Test
    fun outOfRangeColorIndicesAreSafelyRejected() {
        val prefs = FakeSharedPreferences()
        val repo = ReminderRepository(prefs)
        val item = repo.add("Out of range test")

        try {
            repo.edit(item.id, "Out of range test", accentStart = -1)
            fail("Expected IllegalArgumentException for negative accentStart")
        } catch (_: IllegalArgumentException) {}

        try {
            repo.edit(item.id, "Out of range test", accentEnd = ReminderColorChoices.size)
            fail("Expected IllegalArgumentException for accentEnd beyond the palette")
        } catch (_: IllegalArgumentException) {}

        try {
            repo.edit(item.id, "Out of range test", starBorderColorIndex = 99)
            fail("Expected IllegalArgumentException for starBorderColorIndex = 99")
        } catch (_: IllegalArgumentException) {}
    }
}
