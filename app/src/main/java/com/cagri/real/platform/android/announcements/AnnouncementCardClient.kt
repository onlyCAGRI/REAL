package com.cagri.real.platform.android.announcements

import com.cagri.real.network.HttpRequest
import com.cagri.real.platform.android.auth.AndroidBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Cards arrive via the existing UID-scoped sync feed, never a second local importer. */
internal object AnnouncementCardClient {
    suspend fun receive(uid: String): Int = withContext(Dispatchers.IO) {
        check(AndroidBackend.auth.currentUser()?.id == uid) { "Account changed" }
        val response = AndroidBackend.syncTransport().execute(HttpRequest(
            "${AndroidBackend.ORIGIN}/api/announcements/v1/deliver", "POST",
            mapOf("Content-Type" to "application/json"), "{}", 5000, 10000))
        check(AndroidBackend.auth.currentUser()?.id == uid) { "Account changed" }
        check(response.statusCode in 200..299) { "Announcement delivery unavailable" }
        val result = JSONObject(response.body)
        check(result.optBoolean("ok"))
        result.getInt("delivered") + result.getInt("retired")
    }
}
