package com.cagri.bigboscagri.data

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



enum class MoodScope(val persistKey: String, val label: String) {
    MOMENT("moment", "Anlık his"),
    DAILY("daily", "Günün havası");

    companion object {
        fun fromKey(key: String?): MoodScope? = entries.firstOrNull { it.persistKey == key }
    }
}

enum class MoodEnergy(val persistKey: String, val label: String) {
    LOW("low", "Düşük"),
    BALANCED("balanced", "Dengeli"),
    HIGH("high", "Yüksek");

    companion object {
        fun fromKey(key: String?): MoodEnergy? = entries.firstOrNull { it.persistKey == key }
    }
}

data class MoodDetails(
    val scope: MoodScope = MoodScope.DAILY,
    val descriptors: List<String> = emptyList(),
    val energy: MoodEnergy? = null,
    val factors: List<String> = emptyList(),
    val loggedAt: Long = System.currentTimeMillis()
)
enum class JournalMood(val persistKey: String, val label: String) {
    VERY_GOOD("very_good", "Çok iyi"),
    GOOD("good", "İyi"),
    NEUTRAL("neutral", "Normal"),
    HARD("hard", "Zor"),
    BAD("bad", "Kötü"),
    HAPPY("happy", "Mutlu"),
    EXCITED("excited", "Heyecanlı"),
    CALM("calm", "Sakin"),
    TIRED("tired", "Yorgun"),
    BORED("bored", "Sıkılmış"),
    STRESSED("stressed", "Stresli"),
    ANGRY("angry", "Sinirli"),
    SAD("sad", "Üzgün"),
    LOW("low", "Keyifsiz"),
    FUCK_OFF("fuck_off", "F*ck off");

    companion object {
        fun fromKey(key: String?): JournalMood? = entries.firstOrNull { it.persistKey == key }
    }
}

data class ReminderJournal(
    val mood: JournalMood?,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long = createdAt
)

data class ReminderItem(
    val id: Long,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val completedAt: Long? = null,
    val isImportant: Boolean = false,
    val alertAt: Long? = null,
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
    val cardStack: CardStackBlock? = null,
    val contentBlockOrder: List<String> = emptyList()
) {
    val gradientBlendIndex: Int?
        get() = gradientBlendFraction?.let { frac ->
            GRADIENT_BLEND_FRACTIONS.indices.minByOrNull { kotlin.math.abs(GRADIENT_BLEND_FRACTIONS[it] - frac) }
        }
    val isCompleted: Boolean get() = completedAt != null
    val shouldPinNotification: Boolean get() = isImportant && !isCompleted
    val hasRepeat: Boolean get() = repeatRule != RecurrenceRule.NONE
    val isNoAlert: Boolean get() = !isCompleted && alertAt == null && customCategoryId == null

    fun toggleCompleted(now: Long): ReminderItem {
        if (isCompleted) {
            return copy(completedAt = null, notifiedAlertAt = null, updatedAt = now)
        }
        if (hasRepeat && alertAt != null) {
            if (notifiedAlertAt != null) {
                
                
                return copy(notifiedAlertAt = null, updatedAt = now)
            }
            
            val anchor = anchorAlertAt ?: alertAt
            val nextOccurrence = computeNextOccurrence(
                baseAlertAt = anchor,
                rule = repeatRule,
                afterMillis = maxOf(now, alertAt)
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

enum class ReminderSortOrder {
    RECENTLY_ADDED,
    REMINDER_TIME
}

const val COLOR_FILTER_DEFAULT = -1

fun filterAndSortReminders(
    reminders: List<ReminderItem>,
    colorFilter: Int? = null,
    sortOrder: ReminderSortOrder = ReminderSortOrder.RECENTLY_ADDED
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
