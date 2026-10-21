package com.cagri.real.platform.android.sync

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncCanonicalJsonTest {
    @Test fun numericAndKeyOrderingDoNotCreateFalseEdits() {
        assertEquals(canonical(JSONObject("{\"z\":0.0,\"a\":1.00}")),canonical(JSONObject("{\"a\":1,\"z\":0}")))
    }
    @Test fun fullNestedPayloadRoundTripsUnicodeAndLists() {
        val value=JSONObject("{\"text\":\"Alışveriş\",\"items\":[{\"completedAt\":null,\"text\":\"Süt\"}],\"repeatWeekdaysMask\":21}")
        assertEquals(canonical(value),canonical(JSONObject(canonical(value))))
    }
}
