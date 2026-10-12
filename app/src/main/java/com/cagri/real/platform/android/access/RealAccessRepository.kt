package com.cagri.real.platform.android.access

import android.os.SystemClock
import com.cagri.real.access.RealAccessSnapshot
import com.cagri.real.access.RealFeature
import com.cagri.real.network.HttpRequest
import com.cagri.real.platform.android.auth.AndroidBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Small UID-bound in-memory cache. No entitlement is persisted or written into sync data. */
object RealAccessRepository {
    data class Cached(val snapshot: RealAccessSnapshot, val validUntilElapsed: Long) {
        fun valid(): Boolean = SystemClock.elapsedRealtime() < validUntilElapsed
    }
    @Volatile private var entry: Cached? = null
    fun cached(uid: String): Cached? = entry?.takeIf { it.snapshot.uid == uid && it.valid() }
    fun clear() { entry = null }

    suspend fun refresh(uid: String): Cached = withContext(Dispatchers.IO) {
        check(AndroidBackend.auth.currentUser()?.id == uid) { "Account changed" }
        val transport = AndroidBackend.syncTransport()
        check(AndroidBackend.auth.currentUser()?.id == uid) { "Account changed" }
        val response = transport.execute(HttpRequest("${AndroidBackend.ORIGIN}/api/access/v1/me", "GET",
            emptyMap(), "", 10000, 15000))
        check(AndroidBackend.auth.currentUser()?.id == uid) { "Account changed" }
        if (response.statusCode !in 200..299) throw AccessUnavailable(response.statusCode)
        val root = JSONObject(response.body)
        check(root.optBoolean("ok"))
        val body = root.getJSONObject("access")
        check(body.getInt("version") == 1 && body.getString("uid") == uid)
        val plan = body.getString("plan")
        val rollout = body.getString("rollout")
        check(plan in setOf("FREE", "PRO") && rollout in setOf("OBSERVE", "ENFORCE"))
        fun flags(name: String): Set<RealFeature> {
            val values = body.getJSONObject(name)
            return RealFeature.entries.filter { values.optBoolean(it.wireKey, false) }.toSet()
        }
        val usage = body.getJSONObject("ai")
        val snapshot = RealAccessSnapshot(uid, plan, body.getString("source"), body.getBoolean("tester"),
            if (body.isNull("expiresAt")) null else body.getLong("expiresAt"),
            body.getLong("serverTime"), body.getLong("cacheUntil"), rollout, flags("features"), flags("effectiveFeatures"),
            usage.getInt("used"), usage.getInt("limit"), usage.getLong("resetAt"))
        val ttl = (snapshot.cacheUntil - snapshot.serverTime).coerceIn(0L, 15 * 60 * 1000L)
        val value = Cached(snapshot, SystemClock.elapsedRealtime() + ttl)
        check(AndroidBackend.auth.currentUser()?.id == uid) { "Account changed" }
        entry = value
        value
    }
}
class AccessUnavailable(val status: Int) : Exception("Access unavailable")
