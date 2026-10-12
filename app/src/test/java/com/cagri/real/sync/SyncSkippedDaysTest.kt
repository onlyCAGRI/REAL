package com.cagri.real.platform.android.sync

import com.cagri.real.data.RecurrenceRule
import com.cagri.real.data.ReminderItem
import com.cagri.real.data.ReminderJson
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SyncSkippedDaysTest {
    private val item = ReminderItem(
        id = 7, text = "Spor", createdAt = 1_000L, alertAt = 5_000L, anchorAlertAt = 5_000L,
        repeatRule = RecurrenceRule.DAILY, repeatSkippedEpochDays = listOf(20_000L, 20_002L)
    )

    private fun card(reminder: ReminderItem): JSONObject = JSONArray(ReminderJson.encode(listOf(reminder))).getJSONObject(0)

    @Test fun skippedDaysSurviveTheCanonicalSyncPayloadAndComeBack() {
        val payload = canonical(card(item))
        val restored = ReminderJson.decode(JSONArray().put(JSONObject(payload)).toString()).single()
        assertEquals(listOf(20_000L, 20_002L), restored.repeatSkippedEpochDays)
    }

    @Test fun retriedUploadsProduceTheSamePayload() {
        val shuffled = item.copy(repeatSkippedEpochDays = listOf(20_002L, 20_000L, 20_002L))
        assertEquals(canonical(card(item)), canonical(card(shuffled)))
    }

    @Test fun cardsWithoutDeletedDaysKeepTheirOldPayloadShape() {
        assertFalse(card(item.copy(repeatSkippedEpochDays = emptyList())).has("repeatSkippedEpochDays"))
    }
}
