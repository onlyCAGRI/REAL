package com.cagri.real.calendar

import com.cagri.real.data.ReminderItem
import com.cagri.real.platform.android.share.CardShareClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/** Caller holds writeMutex. A stable calendar token refreshes an approved snapshot without copying files. */
internal suspend fun prepareCalendarPublication(
    state: CalendarConnectionState, card: ReminderItem, options: CardShareClient.Options
): CalendarPublication {
    val store = state.store
    check(store.isCurrent() && store.belongsToSignedInUser()) { "REAL hesabı değişti." }
    val links = CalendarCardLinks(store)
    val prior = links.publications()[card.id]
    val now = System.currentTimeMillis()
    val key = calendarOptionKey(options)
    if (prior != null && prior.referenced && prior.cardVersion == card.updatedAt && prior.optionKey == key &&
        prior.url != null && prior.expiresAt > now + 3_600_000L) {
        calendarSyncLog("publish: cache HIT card=${card.id}")
        return prior
    }
    calendarSyncLog("publish: cache MISS card=${card.id} updatedAt=${card.updatedAt} priorVersion=${prior?.cardVersion}")
    val pending = CalendarPublication(card.id,
        prior?.takeIf { it.referenced && it.expiresAt > now }?.requestId ?: UUID.randomUUID().toString(),
        card.updatedAt, key, previousUrl = prior?.let { if (it.applied) it.url else it.previousUrl }, referenced = true)
    // Receipt is durable before networking. A retry refreshes the same token, not another binary snapshot.
    links.savePublication(pending)
    val url = CardShareClient().createCalendar(card.id, card.updatedAt, options, pending.requestId).url
    check(store.isCurrent() && store.belongsToSignedInUser()) { "REAL hesabı değişti." }
    return pending.copy(url = url).also(links::savePublication)
}

/** External-event publication remains explicitly approved; a later 'No' leaves its approved snapshot unchanged. */
internal class CalendarCardPublisher(private val state: CalendarConnectionState) {
    suspend fun publish(card: ReminderItem, options: CardShareClient.Options): String = withContext(Dispatchers.IO) {
        CalendarConnectionState.writeMutex.withLock {
            val store = state.store; val provider = state.provider; val links = CalendarCardLinks(store)
            check(provider.canRead() && provider.canWrite()) { "Takvim okuma ve yazma izni gerekli." }
            val associations = links.associations().filter { it.cardId == card.id }
            check(associations.isNotEmpty()) { "Bu kartın bağlı bir harici takvim etkinliği yok." }
            val calendars = provider.calendars()
            check(associations.all { a -> calendars.any { it.id == a.calendarId && it.identity == a.identity && it.writable } &&
                provider.event(a.eventId)?.calendarId == a.calendarId }) {
                "Bağlı takvim etkinliği bulunamadı veya düzenleme yetkisi yok. REAL içeriğin korunuyor."
            }
            val pending = prepareCalendarPublication(state, card, options)
            val url = checkNotNull(pending.url)
            for (association in associations) {
                check(store.isCurrent() && store.belongsToSignedInUser())
                val existing = provider.event(association.eventId)
                check(existing != null && existing.calendarId == association.calendarId) { "Takvim etkinliği artık bulunamıyor." }
                val description = withExternalCardLink(existing.description, association, url)
                val updated = if (existing.description == description) existing else provider.updateDescription(existing, description)
                check(updated != null) { "Takvim etkinliği aynı anda değişti. REAL kartın kaydedildi; bağlantıyı yeniden dene." }
                links.updateBlock(association, externalCardLinkBlock(association.marker, url))
            }
            links.savePublication(pending.copy(applied = true))
            "Web bağlantısı takvime eklendi. Yeni içerik için yeniden onay ver."
        }
    }
}
