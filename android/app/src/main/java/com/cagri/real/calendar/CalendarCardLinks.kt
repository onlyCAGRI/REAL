package com.cagri.real.calendar

import com.cagri.real.i18n.t
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import com.cagri.real.platform.android.share.CardShareClient
import com.cagri.real.share.validatedPublicShareUrl

internal data class CalendarCardAssociation(
    val eventId: Long, val calendarId: Long, val identity: String, val marker: String,
    val cardId: Long, val publicBlock: String? = null
)
internal data class CalendarPublication(
    val cardId: Long, val requestId: String, val cardVersion: Long, val optionKey: String,
    val url: String? = null, val applied: Boolean = false, val previousUrl: String? = null,
    val expiresAt: Long = System.currentTimeMillis() + 86_400_000L,
    val referenced: Boolean = false
)

/** Separate from export-instance mappings: external event dates remain authoritative in CalendarContract. */
internal class CalendarCardLinks(private val store: CalendarStore) {
    fun associations(): List<CalendarCardAssociation> {
        val a = JSONArray(store.preferences.getString("card_associations", "[]") ?: "[]")
        return (0 until a.length()).map { i -> a.getJSONObject(i).let { o ->
            CalendarCardAssociation(o.getLong("event"), o.getLong("calendar"), o.getString("identity"),
                o.getString("marker"), o.getLong("card"), o.optString("publicBlock").takeIf { it.isNotEmpty() })
        } }
    }
    fun associate(event: CalendarOccurrence, calendar: DeviceCalendar, cardId: Long) {
        val current = associations()
        val existing = current.firstOrNull { it.eventId == event.eventId && it.calendarId == event.calendarId && it.identity == calendar.identity }
        val item = existing?.copy(cardId = cardId) ?: CalendarCardAssociation(event.eventId, event.calendarId,
            calendar.identity, UUID.randomUUID().toString(), cardId)
        saveAssociations(current.filterNot { it.eventId == event.eventId && it.calendarId == event.calendarId } + item)
    }
    fun updateBlock(association: CalendarCardAssociation, block: String) {
        saveAssociations(associations().map { if (it.marker == association.marker) it.copy(publicBlock = block) else it })
    }
    private fun saveAssociations(items: List<CalendarCardAssociation>) {
        check(store.isCurrent())
        val a = JSONArray().apply { items.forEach { put(JSONObject().put("event", it.eventId).put("calendar", it.calendarId)
            .put("identity", it.identity).put("marker", it.marker).put("card", it.cardId)
            .put("publicBlock", it.publicBlock ?: JSONObject.NULL)) } }
        check(store.preferences.edit().putString("card_associations", a.toString()).commit())
    }
    fun publications(): Map<Long, CalendarPublication> {
        val a = JSONArray(store.preferences.getString("card_publications", "[]") ?: "[]")
        return (0 until a.length()).map { i -> a.getJSONObject(i).let { o ->
            val id = o.getString("request")
            CalendarPublication(o.getLong("card"), id, o.getLong("version"), o.getString("options"),
                o.optString("url").takeIf { validatedPublicShareUrl(id, it) != null }, o.optBoolean("applied"),
                o.optString("previousUrl").takeIf { value ->
                    val token = value.substringAfterLast("/s/", "")
                    validatedPublicShareUrl(token, value) != null
                }, o.optLong("expiresAt", 0), o.optBoolean("referenced"))
        } }.associateBy { it.cardId }
    }
    fun savePublication(item: CalendarPublication) {
        check(store.isCurrent())
        val items = publications() + (item.cardId to item)
        val a = JSONArray().apply { items.values.forEach { put(JSONObject().put("card", it.cardId).put("request", it.requestId)
            .put("version", it.cardVersion).put("options", it.optionKey).put("url", it.url ?: JSONObject.NULL)
            .put("applied", it.applied).put("previousUrl", it.previousUrl ?: JSONObject.NULL).put("expiresAt", it.expiresAt).put("referenced", it.referenced)) } }
        check(store.preferences.edit().putString("card_publications", a.toString()).commit())
    }
    fun publicUrls(): Map<Long, String> = publications().mapNotNull { (id, p) -> (if (p.applied) p.url else p.previousUrl)?.let { id to it } }.toMap()
    fun hasExternalTarget(cardId: Long): Boolean = associations().any { it.cardId == cardId }
    fun hasCalendarTarget(cardId: Long): Boolean = hasExternalTarget(cardId) ||
        store.links().any { it.key.substringBefore(':').toLongOrNull() == cardId && !it.suppressed }
}

internal fun calendarOptionKey(options: CardShareClient.Options): String =
    listOf(options.includeAttachments, options.includeLocation, options.includeVoice, options.includeJournal).joinToString(":")

internal fun externalCardLinkBlock(marker: String, url: String) = "REAL kartı: ${url}\n[REAL-CALENDAR:${marker}]"

/** Replace only our exact block; retain the user's original description and later additions. */
internal fun withExternalCardLink(description: String?, association: CalendarCardAssociation, url: String): String {
    val original = description.orEmpty()
    val block = externalCardLinkBlock(association.marker, url)
    val previous = association.publicBlock
    if (previous != null && original.contains(previous)) {
        check(original.indexOf(previous) == original.lastIndexOf(previous)) { t("Takvim bağlantısı birden fazla kez bulunuyor.") }
        return original.replace(previous, block)
    }
    // Recover a completed provider write whose local block receipt was interrupted.
    val marker = "[REAL-CALENDAR:${association.marker}]"
    if (original.contains(marker)) {
        val lines = original.lines()
        val index = lines.indexOf(marker)
        check(index > 0 && lines.count { it == marker } == 1 && lines[index - 1].startsWith("REAL kartı: ")) {
            t("Takvimdeki bağlantı dışarıda değiştirildi; açıklamayı kontrol et.")
        }
        return lines.toMutableList().apply { this[index - 1] = "REAL kartı: ${url}" }.joinToString("\n")
    }
    return if (original.isEmpty()) block else "$original\n\n$block"
}
