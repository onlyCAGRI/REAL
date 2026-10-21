package com.cagri.real.speech

import com.cagri.real.data.AddChecklistResult
import com.cagri.real.data.AddChecklistStatus
import com.cagri.real.data.CustomCategory
import com.cagri.real.data.ReminderEditPatch
import com.cagri.real.data.ReminderItem
import com.cagri.real.data.SkipOccurrenceResult
import com.cagri.real.data.SkipStatus
import com.cagri.real.data.VoiceBulkShift
import com.cagri.real.data.patternLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * All voice-action logic lives here, OUTSIDE the huge ReminderListScreen composable.
 * That function already has 70+ parameters; adding more code to it made R8 emit bytecode the
 * Android verifier rejects (VerifyError), so ReminderListScreen only calls the small functions below.
 */

/** The three repository-backed callbacks the voice actions need, bundled as ONE parameter. */
class VoiceCardCallbacks(
    /** (cardId, occurrence epoch day or null for the upcoming occurrence). */
    val skipOccurrence: suspend (Long, Long?) -> SkipOccurrenceResult? = { _, _ -> null },
    val shiftMany: (List<VoiceBulkShift>) -> Unit = {},
    val addChecklistItems: suspend (Long, List<String>) -> AddChecklistResult? = { _, _ -> null },
    val deleteOccurrence: ((Long, Long, com.cagri.real.data.RepeatDeleteScope) -> Unit)? = null
)

/** Result shown to the person as a toast. [success] additionally triggers the success haptic. */
class VoiceCardOutcome(val message: String, val success: Boolean = false)

object VoiceRecentCards {
    private const val MAX_CARDS = 15

    /** Last card first, then spoken title/category matches, then open cards closest to now. */
    fun build(
        reminders: List<ReminderItem>,
        categories: List<CustomCategory>,
        lastCardId: Long?,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        transcript: String = ""
    ): List<VoiceRecentCard> {
        fun due(card: ReminderItem): Long? = card.alertAt ?: card.plannedDateEpochDay?.let {
            LocalDate.ofEpochDay(it).atStartOfDay(zone).toInstant().toEpochMilli()
        }
        val sorted = reminders.filter { it.text.isNotBlank() }.sortedWith(
            compareBy<ReminderItem> { it.id != lastCardId }
                .thenByDescending { voiceTitleRelevance(transcript, it.text, categories.firstOrNull { cat -> cat.id == it.customCategoryId }?.name) }
                .thenBy { it.isCompleted }
                .thenBy { due(it) == null }
                .thenBy { card -> due(card)?.let { d -> kotlin.math.abs(d - nowMillis) } ?: 0L }
                .thenByDescending { it.updatedAt }
        )
        return sorted.take(MAX_CARDS).map { card ->
            VoiceRecentCard(
                id = card.id,
                title = card.text.take(160),
                alertAtIso = card.alertAt?.let {
                    Instant.ofEpochMilli(it).atZone(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                },
                plannedDate = card.plannedDateEpochDay?.let { LocalDate.ofEpochDay(it).toString() },
                categoryName = categories.firstOrNull { it.id == card.customCategoryId }?.name,
                locationName = card.location?.placeName,
                repeatSummary = (card.patternLabel() ?: if (card.hasRepeat) card.repeatRule.name else null)?.let { summary ->
                    summary + (card.repeatUntilEpochDay?.let { " (son gün: ${LocalDate.ofEpochDay(it)})" } ?: "")
                },
                isChecklist = card.isChecklist,
                completed = card.isCompleted,
                isLast = card.id == lastCardId
            )
        }
    }
}

/** Executes (or prepares a confirmation for) a voice command aimed at an EXISTING card. */
suspend fun runVoiceCardAction(
    action: VoiceAction?,
    editRequest: GeminiVoiceEdit?,
    editTarget: ReminderItem?,
    reminders: List<ReminderItem>,
    categories: List<CustomCategory>,
    callbacks: VoiceCardCallbacks,
    onEdit: (Long, ReminderEditPatch) -> Unit,
    onToggleCompleted: (Long) -> Unit,
    onPlan: (VoiceBulkPlan) -> Unit,
    onFollowUp: (Long) -> Unit,
    onDeletePlan: ((VoiceDeletePlan) -> Unit)? = null
): VoiceCardOutcome {
    val type = action?.type ?: VoiceActionType.EDIT
    val targetId = action?.targetId
    // The backend can only name ids we sent; still re-check the card exists right now.
    val target: ReminderItem? = if (targetId != null) reminders.firstOrNull { it.id == targetId } else editTarget
    val label = target?.text?.take(40).orEmpty()

    return when (type) {
        VoiceActionType.EDIT, VoiceActionType.POSTPONE -> {
            if (target == null) return VoiceCardOutcome("Hangi kartı kastettiğini bulamadım.")
            if (editRequest == null) {
                return VoiceCardOutcome(
                    if (type == VoiceActionType.POSTPONE) "Ne zamana erteleyeyim?" else "Neyi değiştireceğimi anlayamadım."
                )
            }
            val resolved = VoiceEditResolver.resolve(target, editRequest, categories, System.currentTimeMillis())
            val patch = resolved.getOrNull()
                ?: return VoiceCardOutcome(resolved.exceptionOrNull()?.message ?: "Kart düzenlenemedi.")
            onEdit(target.id, patch)
            onFollowUp(target.id)
            val categoryLabel = categories.firstOrNull { it.id == patch.customCategoryId }?.name
            VoiceCardOutcome(VoiceEditResolver.confirmation(patch, categoryLabel), true)
        }
        VoiceActionType.SKIP_OCCURRENCE -> {
            if (target == null) return VoiceCardOutcome("Hangi kartı atlayacağımı bulamadım.")
            val outcome = callbacks.skipOccurrence(target.id, action?.occurrenceEpochDay)
            onFollowUp(target.id)
            when (outcome?.status) {
                SkipStatus.SKIPPED -> {
                    val next = outcome.nextAlertAt?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.forLanguageTag("tr-TR")))
                    }
                    VoiceCardOutcome(if (next != null) "Bu seferlik atlandı. Sıradaki: $next" else "Bu seferlik atlandı.", true)
                }
                SkipStatus.SERIES_ENDED -> VoiceCardOutcome("Son tekrar atlandı; seri tamamlandı.", true)
                SkipStatus.NOT_REPEATING -> VoiceCardOutcome("Bu kart tekrarlanmıyor, atlanacak bir sonraki tekrar yok.")
                SkipStatus.NO_SUCH_OCCURRENCE -> VoiceCardOutcome("Bu kartın o gün atlanabilecek bir tekrarı yok.")
                else -> VoiceCardOutcome("Kart bulunamadı.")
            }
        }
        VoiceActionType.COMPLETE -> when {
            target == null -> VoiceCardOutcome("Hangi kartı tamamlayacağımı bulamadım.")
            target.isCompleted -> VoiceCardOutcome("Bu kart zaten tamamlanmış.")
            else -> {
                onToggleCompleted(target.id)
                onFollowUp(target.id)
                VoiceCardOutcome("Tamamlandı: $label", true)
            }
        }
        VoiceActionType.REOPEN -> when {
            target == null -> VoiceCardOutcome("Hangi kartı yeniden açacağımı bulamadım.")
            !target.isCompleted -> VoiceCardOutcome("Bu kart zaten açık.")
            else -> {
                onToggleCompleted(target.id)
                onFollowUp(target.id)
                VoiceCardOutcome("Yeniden açıldı: $label", true)
            }
        }
        VoiceActionType.DELETE -> {
            if (target == null) return VoiceCardOutcome("Hangi kartı sileceğimi bulamadım.")
            if (onDeletePlan != null) {
                onDeletePlan(voiceDeletePlan(target, System.currentTimeMillis()))
                return VoiceCardOutcome("")
            }
            // Legacy adapter still requires explicit confirmation before deletion.
            onPlan(
                VoiceBulkPlan(
                    operation = VoiceBulkOperation.DELETE,
                    cardIds = listOf(target.id),
                    shifts = emptyList(),
                    question = "\u201C$label\u201D kartı silinsin mi?",
                    doneMessage = "Silindi: $label"
                )
            )
            VoiceCardOutcome("")
        }
        VoiceActionType.BULK -> {
            val bulk = action?.bulk ?: return VoiceCardOutcome("Toplu işlemi anlayamadım.")
            val planned = VoiceBulkResolver.resolve(bulk, reminders, categories, System.currentTimeMillis())
            val plan = planned.getOrNull()
                ?: return VoiceCardOutcome(planned.exceptionOrNull()?.message ?: "Toplu işlem hazırlanamadı.")
            onPlan(plan)
            VoiceCardOutcome("")
        }
        VoiceActionType.ADD_CHECKLIST_ITEMS -> {
            val items = action?.checklistItems.orEmpty()
            when {
                target == null -> VoiceCardOutcome("Hangi listeye ekleyeceğimi bulamadım.")
                !target.isChecklist -> VoiceCardOutcome("Bu kart bir liste değil.")
                items.isEmpty() -> VoiceCardOutcome("Eklenecek maddeyi anlayamadım.")
                else -> {
                    val outcome = callbacks.addChecklistItems(target.id, items)
                    onFollowUp(target.id)
                    val changed = (outcome?.added ?: 0) + (outcome?.reopened ?: 0)
                    when {
                        outcome == null || outcome.status != AddChecklistStatus.ADDED -> VoiceCardOutcome("Listeye eklenemedi.")
                        changed == 0 -> VoiceCardOutcome("Bu maddeler listede zaten var.")
                        else -> {
                            val dup = if (outcome.duplicates.isNotEmpty()) " (zaten vardı: ${outcome.duplicates.joinToString(", ")})" else ""
                            VoiceCardOutcome("Listeye eklendi: ${items.joinToString(", ")}$dup".take(120), true)
                        }
                    }
                }
            }
        }
        VoiceActionType.UNRESOLVED ->
            VoiceCardOutcome(action?.clarification ?: "Hangi kartı kastettiğini anlayamadım. Kartın adını biraz daha net söyler misin?")
        else -> VoiceCardOutcome("Bu sesli komut desteklenmiyor.")
    }
}

/** Applies a confirmed plan against the LIVE card list (cards may have changed while the chip was open). */
fun applyVoiceBulkPlan(
    plan: VoiceBulkPlan,
    reminders: List<ReminderItem>,
    callbacks: VoiceCardCallbacks,
    onToggleCompleted: (Long) -> Unit,
    onDeleteMany: (Set<Long>) -> Unit,
    nowMillis: Long = System.currentTimeMillis()
): VoiceCardOutcome {
    val live = reminders.associateBy { it.id }
    val planned = plan.cardIds.size
    val applied: Int = when (plan.operation) {
        VoiceBulkOperation.SHIFT -> {
            val valid = plan.shifts.filter { shift ->
                val alert = shift.alertAt
                live[shift.id]?.isCompleted == false && (alert == null || alert > nowMillis)
            }
            if (valid.isNotEmpty()) callbacks.shiftMany(valid)
            valid.size
        }
        VoiceBulkOperation.COMPLETE -> {
            val valid = plan.cardIds.filter { live[it]?.isCompleted == false }
            valid.forEach { onToggleCompleted(it) }
            valid.size
        }
        VoiceBulkOperation.DELETE -> {
            val valid = plan.cardIds.filter { live.containsKey(it) }
            if (valid.isNotEmpty()) onDeleteMany(valid.toSet())
            valid.size
        }
        VoiceBulkOperation.NONE -> 0
    }
    if (applied == 0) return VoiceCardOutcome("Kartlar değişmiş, işlem uygulanmadı.")
    if (applied == planned && plan.operation == VoiceBulkOperation.DELETE && planned == 1) {
        return VoiceCardOutcome(plan.doneMessage, true)
    }
    val verb = when (plan.operation) {
        VoiceBulkOperation.SHIFT -> "taşındı"
        VoiceBulkOperation.COMPLETE -> "tamamlandı"
        else -> "silindi"
    }
    val changedNote = if (applied < planned) " (${planned - applied} kart değişmişti)" else ""
    return VoiceCardOutcome("$applied kart $verb.$changedNote", true)
}
