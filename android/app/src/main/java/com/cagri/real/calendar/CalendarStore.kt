package com.cagri.real.calendar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import com.cagri.real.platform.android.sync.SyncLocal

/** Device-local, workspace-scoped. Calendar IDs/mappings must never enter REAL cloud sync. */
internal class CalendarStore(context: Context) : CalendarLinkStorage {
    private val token = SyncLocal.workspaceToken()
    private val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
        .joinToString("") { "%02x".format(it) }
    val preferences = context.applicationContext.getSharedPreferences("real_device_calendar_$digest", Context.MODE_PRIVATE)
    override fun isCurrent() = token == SyncLocal.workspaceToken()
    fun belongsToSignedInUser(): Boolean = token.startsWith("u:") &&
        token.removePrefix("u:") == com.cagri.real.platform.android.auth.AndroidBackend.auth.currentUser()?.id
    fun options(): CalendarOptions = runCatching { readOptions() }.getOrElse { CalendarOptions() }
    private fun readOptions(): CalendarOptions {
        val root = JSONObject(preferences.getString("options", "{}") ?: "{}")
        val reads = root.optJSONArray("reads") ?: JSONArray()
        val identities = buildMap<Long, String> {
            for (i in 0 until reads.length()) {
                val item = reads.getJSONObject(i); put(item.getLong("id"), item.getString("identity"))
            }
        }
        val selected = root.optJSONArray("selected") ?: JSONArray()
        return CalendarOptions(identities.keys, identities,
            if (root.isNull("target")) null else root.optLong("target").takeIf { it > 0 },
            root.optString("targetIdentity").takeIf { it.isNotBlank() },
            root.optBoolean("enabled"), root.optBoolean("all"),
            (0 until selected.length()).map { selected.getLong(it) }.toSet())
    }
    fun saveOptions(options: CalendarOptions) {
        check(isCurrent()) { "Hesap değişti" }
        val reads = JSONArray().apply { options.readIdentities.forEach { (id, identity) ->
            put(JSONObject().put("id", id).put("identity", identity))
        } }
        val root = JSONObject().put("reads", reads).put("target", options.targetId ?: JSONObject.NULL)
            .put("targetIdentity", options.targetIdentity ?: JSONObject.NULL).put("enabled", options.exportEnabled)
            .put("all", options.exportAll).put("selected", JSONArray(options.selectedIds.toList()))
        check(preferences.edit().putString("options", root.toString()).commit())
    }
    private var cachedLinksRaw: String? = null
    private var cachedLinks: List<CalendarLink> = emptyList()
    @Synchronized override fun links(): List<CalendarLink> {
        val raw = preferences.getString("links", "[]") ?: "[]"
        if (raw == cachedLinksRaw) return cachedLinks
        val entries = JSONArray(raw)
        val decoded = (0 until entries.length()).map { i ->
            val o = entries.getJSONObject(i)
            CalendarLink(o.getString("key"), o.getString("marker"), o.getLong("calendar"),
                o.getString("identity"), if (o.isNull("event")) null else o.getLong("event"),
                if (o.isNull("hash")) null else o.getString("hash"), o.getLong("start"), o.optBoolean("suppressed"))
        }
        cachedLinksRaw = raw
        cachedLinks = decoded
        return decoded
    }
    @Synchronized override fun saveLinks(links: List<CalendarLink>) {
        check(isCurrent()) { "Hesap değişti" }
        if (links == cachedLinks && preferences.getString("links", "[]") == cachedLinksRaw) return
        val array = JSONArray().apply { links.forEach { l -> put(JSONObject()
            .put("key", l.key).put("marker", l.marker).put("calendar", l.calendarId)
            .put("identity", l.calendarIdentity).put("event", l.eventId ?: JSONObject.NULL)
            .put("hash", l.fingerprint ?: JSONObject.NULL).put("start", l.start).put("suppressed", l.suppressed)) } }
        val raw = array.toString()
        if (preferences.getString("links", "[]") != raw) check(preferences.edit().putString("links", raw).commit())
        cachedLinksRaw = raw
        cachedLinks = links.toList()
    }
    fun durationMinutes(): Map<Long, Int> {
        val prefs = SyncLocal.preferences(contextForDurations, "real_planner_v1", Context.MODE_PRIVATE)
        val root = JSONObject(prefs.getString("event_details", "{}") ?: "{}")
        val array = root.optJSONArray("events") ?: return emptyMap()
        return buildMap { for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            if (!o.isNull("durationMinutes")) put(o.getLong("reminderId"), o.getInt("durationMinutes"))
        } }
    }
    private val contextForDurations = context.applicationContext
}
