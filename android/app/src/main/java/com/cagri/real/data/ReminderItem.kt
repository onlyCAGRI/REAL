package com.cagri.real.data

enum class AttachmentType { IMAGE, FILE, TEXT_SNIPPET }
enum class SnippetSource(val label: String) {
    WHATSAPP_TEXT("WhatsApp"), EMAIL_TEXT("E-posta"), PLAIN_TEXT("Not")
}


data class ReminderAttachment(
    val id: String,
    val localPath: String = "",
    val type: AttachmentType = AttachmentType.IMAGE,
    val uri: String? = null,
    val mimeType: String? = null,
    val displayName: String? = null,
    val sizeBytes: Long? = null,
    val text: String? = null,
    val source: SnippetSource = SnippetSource.PLAIN_TEXT,
    val sourceTitle: String? = null
)



data class ReminderJournal(
    val mood: JournalMood?,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long = createdAt
)

/**
 * Kontrol ettir. [to]: who should look — uids in a shared category, [ME] for your own cards, [EVERYONE] = all members
 * but [from]. [at] identifies the request (a new one lights the card again); [startEpochDay]: it glows from that day;
 * [colorArgb]: null = automatic (blue for yourself, amber for someone else).
 */
data class CheckRequest(val to: List<String>, val from: String?, val at: Long, val startEpochDay: Long? = null, val colorArgb: Int? = null) {
    companion object {
        const val ME = "me"
        const val EVERYONE = "*"
    }
}

/** Real cards only: dividers (Ayraç) stay out of lists, counts and pickers; only a category's own list draws them. */
fun List<ReminderItem>.withoutDividers(): List<ReminderItem> = if (none { it.isDivider }) this else filterNot { it.isDivider }

data class ReminderItem(
    val id: Long,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val completedAt: Long? = null,
    val isImportant: Boolean = false,
    val alertAt: Long? = null,
    /** Date-only planning. Keeps a calendar day without inventing a clock time. */
    val plannedDateEpochDay: Long? = null,
    /**
     * Temporary rolling 24-hour focus bucket. Used when a REAL is explicitly moved into
     * the 24 hour view without inventing a clock time or notification.
     */
    val quick24HourUntilAt: Long? = null,
    val notifiedAlertAt: Long? = null,
    val accentStart: Int? = null,
    val accentEnd: Int? = null,
    val repeatRule: RecurrenceRule = RecurrenceRule.NONE,
    val anchorAlertAt: Long? = alertAt,
    val customCategoryId: Long? = null,
    val isChecklist: Boolean = false,
    val checklistItems: List<ChecklistItem> = emptyList(),
    val sendImmediateNotification: Boolean = true,
    val referenceUrl: String? = null,
    val starBorderColorIndex: Int? = null,
    val gradientBlendFraction: Float? = null,
    val customSolidColorArgb: Int? = null,
    val useDarkText: Boolean = false,
    val attachments: List<ReminderAttachment> = emptyList(),
    val voiceRecordings: List<ReminderVoiceRecording> = emptyList(),
    val journal: ReminderJournal? = null,
    /** Mood V2 detail block. Separate from journal notes. */
    val mood: JournalMood? = null,
    /** Optional richer Mood V2 metadata. Old reminders legitimately keep this null. */
    val moodDetails: MoodDetails? = null,
    val location: ReminderLocation? = null,
    val musicBlocks: List<MusicBlock> = emptyList(),
    val mediaBlocks: List<MediaBlock> = emptyList(),
    /** Oyun bloğu (its own field; see CardGameBlock). */
    val gameBlocks: List<CardGameBlock> = emptyList(),
    val cardStack: CardStackBlock? = null,
    val contentBlockOrder: List<String> = emptyList(),
    /** Weekday set for a WEEKLY repeat (bit0 = Monday ... bit6 = Sunday). 0 = plain single-day repeat. */
    val repeatWeekdaysMask: Int = 0,
    /** Inclusive last day of a repeat series ("sadece 1 ay"). Null = repeats forever. */
    val repeatUntilEpochDay: Long? = null,
    /** Server-authenticated sender attribution for an independent received copy. */
    val sentBy: String? = null,
    val shareOrigin: String? = null,
    val contentBlockLayout: Map<String, CardBlockPlacement> = emptyMap(),
    /**
     * Local epoch days of single occurrences the person deleted from a repeating series
     * ("sadece bu gün"). The series itself stays; projections and reschedules skip these days.
     */
    val repeatSkippedEpochDays: List<Long> = emptyList(),
    /**
     * When the event ends ("Bitiş"), possibly days later (12 Eki 10:00 → 15 Eki 18:00). Independent of the planner
     * duration: a 4-day course of 1 hour a day keeps "60 dk" and this end.
     */
    val endAt: Long? = null,
    /** Early warning ("Erken uyarı"): a heads-up this many minutes before [alertAt]. Null = none. */
    val earlyAlertMinutes: Int? = null,
    /**
     * Ayraç: not a card but a divider line in its category's list, [text] being its title (may be empty). It sits at
     * its [createdAt] among the cards and is left out of every other list, count, alert and share.
     */
    val isDivider: Boolean = false,
    /** Tamamlama notu: a short line left when the card was completed ("test edilecek"); shown under "Tamamlandı". */
    val completionNote: String? = null,
    /** Kontrol ettir: someone (or you) asked for this card to be looked at; it glows for them until they open it. */
    val checkRequest: CheckRequest? = null,
    /**
     * Ortak kategori: the server id of this card when it lives in a shared category. Such cards are kept on the device
     * like any other card but never enter personal sync; SharedCategorySync carries them.
     */
    val sharedCardId: String? = null,
    /** Who added it (shown bottom-right when it is someone else). */
    val sharedAuthorUid: String? = null,
    val sharedAuthorName: String? = null
) {
    val gradientBlendIndex: Int?
        get() = gradientBlendFraction?.let { frac ->
            GRADIENT_BLEND_FRACTIONS.indices.minByOrNull { kotlin.math.abs(GRADIENT_BLEND_FRACTIONS[it] - frac) }
        }
    val isCompleted: Boolean get() = completedAt != null
    val shouldPinNotification: Boolean get() = isImportant && !isCompleted
    val hasRepeat: Boolean get() = repeatRule != RecurrenceRule.NONE
    val isNoAlert: Boolean get() = CategoryRules.isNoAlert(
        isCompleted, alertAt, plannedDateEpochDay, quick24HourUntilAt, customCategoryId, System.currentTimeMillis()
    )

    fun toggleCompleted(now: Long): ReminderItem {
        if (isCompleted) {
            return copy(completedAt = null, notifiedAlertAt = null, updatedAt = now)
        }
        if (hasRepeat && alertAt != null) {
            if (notifiedAlertAt != null) {
                
                
                return copy(notifiedAlertAt = null, updatedAt = now)
            }
            
            val anchor = anchorAlertAt ?: alertAt
            val nextOccurrence = computeNextRepeat(
                baseAlertAt = anchor,
                rule = repeatRule,
                weekdaysMask = repeatWeekdaysMask,
                untilEpochDay = repeatUntilEpochDay,
                afterMillis = maxOf(now, alertAt),
                skippedEpochDays = repeatSkippedEpochDays
            )
            return if (nextOccurrence != null) {
                copy(
                    alertAt = nextOccurrence,
                    anchorAlertAt = anchor,
                    notifiedAlertAt = null,
                    updatedAt = now
                )
            } else {
                copy(completedAt = now, updatedAt = now)
            }
        }
        return copy(completedAt = now, updatedAt = now)
    }
}

const val ROLLING_24_HOURS_MILLIS: Long = CategoryRules.ROLLING_24_HOURS_MILLIS

fun ReminderItem.isQuick24HourActive(nowMillis: Long = System.currentTimeMillis()): Boolean =
    CategoryRules.isQuick24HourActive(isCompleted, quick24HourUntilAt, nowMillis)

fun ReminderItem.isIn24HourWindow(
    nowMillis: Long = System.currentTimeMillis(),
    zone: java.time.ZoneId = java.time.ZoneId.systemDefault()
): Boolean {
    // Resolve the calendar boundary in Android/JVM; keep the membership rule portable.
    val today = java.time.Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().toEpochDay()
    return CategoryRules.isIn24HourWindow(
        isCompleted, alertAt, plannedDateEpochDay, quick24HourUntilAt, nowMillis, today
    )
}

/**
 * Where each Ayraç sits in a category's sorted [cards]: index → dividers drawn before cards[index] (index == size: after
 * the last card). A divider marks the moment it was added, so it only shows in the added-time orders.
 */
fun dividerSlots(cards: List<ReminderItem>, dividers: List<ReminderItem>, sort: ReminderSortOrder): Map<Int, List<ReminderItem>> {
    if (dividers.isEmpty() || (sort != ReminderSortOrder.RECENTLY_ADDED && sort != ReminderSortOrder.OLDEST_FIRST)) return emptyMap()
    val newestFirst = sort == ReminderSortOrder.RECENTLY_ADDED
    val ordered = if (newestFirst) dividers.sortedByDescending { it.createdAt } else dividers.sortedBy { it.createdAt }
    val out = linkedMapOf<Int, MutableList<ReminderItem>>()
    for (d in ordered) {
        val index = cards.indexOfFirst { c -> c.id >= 0 && if (newestFirst) c.createdAt < d.createdAt else c.createdAt > d.createdAt }
        out.getOrPut(if (index < 0) cards.size else index) { mutableListOf() } += d
    }
    return out
}

enum class ReminderSortOrder {
    RECENTLY_ADDED,
    REMINDER_TIME,
    /** Category screens: newest added at the bottom. */
    OLDEST_FIRST,
    /** Category screens: closest date to now first (past or future), undated last. */
    NEAREST_DATE
}

const val COLOR_FILTER_DEFAULT = -1

fun filterAndSortReminders(
    reminders: List<ReminderItem>,
    colorFilter: Int? = null,
    sortOrder: ReminderSortOrder = ReminderSortOrder.RECENTLY_ADDED,
    nowMillis: Long = System.currentTimeMillis(),
    zone: java.time.ZoneId = java.time.ZoneId.systemDefault()
): List<ReminderItem> {
    var result = reminders
    if (colorFilter != null) {
        result = if (colorFilter == COLOR_FILTER_DEFAULT) {
            result.filter { it.accentStart == null }
        } else {
            result.filter { it.accentStart == colorFilter || it.accentEnd == colorFilter }
        }
    }
    result = when (sortOrder) {
        ReminderSortOrder.RECENTLY_ADDED -> {
            result.sortedByDescending { it.createdAt }
        }
        ReminderSortOrder.OLDEST_FIRST -> {
            result.sortedWith(compareBy<ReminderItem> { it.createdAt }.thenBy { it.id })
        }
        ReminderSortOrder.NEAREST_DATE -> {
            fun due(r: ReminderItem): Long? = r.alertAt ?: r.plannedDateEpochDay?.let {
                java.time.LocalDate.ofEpochDay(it).atStartOfDay(zone).toInstant().toEpochMilli()
            }
            result.sortedWith(
                compareBy<ReminderItem> { due(it) == null }
                    .thenBy { due(it)?.let { d -> kotlin.math.abs(d - nowMillis) } ?: 0L }
                    .thenByDescending { it.createdAt }
            )
        }
        ReminderSortOrder.REMINDER_TIME -> {
            result.sortedWith(
                compareBy<ReminderItem> { it.alertAt == null }
                    .thenBy { it.alertAt }
                    .thenByDescending { it.createdAt }
            )
        }
    }
    return result
}

const val HOME_COMPLETED_PREVIEW_LIMIT = 8
const val HOME_COMPLETED_GLASS_HEIGHT_DP = 108f

fun isHomeCompletedGlassActive(completedCount: Int): Boolean =
    completedCount >= HOME_COMPLETED_PREVIEW_LIMIT

fun buildReminderShareText(reminder: ReminderItem): String {
    if (!reminder.isChecklist) {
        return reminder.text
    }
    val validItems = reminder.checklistItems.filter { it.text.isNotBlank() }
    if (validItems.isEmpty()) {
        return reminder.text
    }
    val itemsText = validItems.joinToString("\n") { item ->
        val prefix = if (item.isCompleted) "☑ " else "☐ "
        prefix + item.text.trim()
    }
    return "${reminder.text.trim()}\n\n$itemsText"
}

/**
 * Builds the existing plain-text share payload plus only the selected Card Stack cards.
 * The card list is always traversed in persisted stack order, never selection order.
 */
fun buildReminderShareText(reminder: ReminderItem, selectedCardIds: Set<String>): String {
    val base = buildReminderShareText(reminder).trim()
    val selectedCards = reminder.cardStack?.cards.orEmpty().filter { it.id in selectedCardIds }
    val cardTexts = selectedCards.map { card ->
        val parts = buildList {
            card.title.trim().takeIf { it.isNotBlank() }?.let(::add)
            card.body.trim().takeIf { it.isNotBlank() }?.let(::add)
            if (isEmpty() && !card.imageUri.isNullOrBlank()) add("[Görsel kart]")
        }
        parts.joinToString("\n")
    }.filter { it.isNotBlank() }

    return buildList {
        if (base.isNotBlank()) add(base)
        addAll(cardTexts)
    }.joinToString("\n\n")
}

val GRADIENT_BLEND_FRACTIONS = listOf(0.20f, 0.35f, 0.50f, 0.65f, 0.80f)
const val DEFAULT_GRADIENT_BLEND_INDEX = 2
const val DEFAULT_GRADIENT_BLEND_FRACTION = 0.50f

fun resolveGradientBlendFraction(fraction: Float?): Float {
    if (fraction == null) return DEFAULT_GRADIENT_BLEND_FRACTION
    return fraction.coerceIn(0.0f, 1.0f)
}

fun migrateLegacyGradientBlendIndex(index: Int?): Float {
    if (index == null || index !in GRADIENT_BLEND_FRACTIONS.indices) {
        return DEFAULT_GRADIENT_BLEND_FRACTION
    }
    return GRADIENT_BLEND_FRACTIONS[index]
}
