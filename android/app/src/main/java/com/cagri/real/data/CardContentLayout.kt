package com.cagri.real.data

/** Presentation only. Content identity, notification dates and Pano transforms are independent. */
const val CARD_GRID_UNITS = 6
const val CARD_MAX_PER_ROW = 3

/**
 * 6 birimlik ızgara: tam = 6, yarım = 3, üçte bir = 2, üçte iki = 4.
 * [column] başlangıç birimidir; bir satırda en fazla [CARD_MAX_PER_ROW] blok yan yana durur.
 */
data class CardBlockPlacement(
    val span: Int = CARD_GRID_UNITS,
    val column: Int = 0,
    val gapBefore: Int = 12,
    val minHeight: Int = 0,
    /** Absolute grid top in dp; null preserves the legacy flowing layout. */
    val top: Int? = null,
    /** Catalog detail presentation only; native content and provider URLs stay unchanged. */
    val hideActions: Boolean = false,
    val textAlignment: String = "start",
    val textBold: Boolean = false,
    val textItalic: Boolean = false
) {
    fun normalized(): CardBlockPlacement {
        val s = span.coerceIn(1, CARD_GRID_UNITS)
        return copy(
            span = s, column = column.coerceIn(0, CARD_GRID_UNITS - s),
            gapBefore = gapBefore.coerceIn(0, 160), minHeight = minHeight.coerceIn(0, 800),
            top = top?.coerceIn(0, CARD_MAX_TOP_DP),
            textAlignment = textAlignment.takeIf { it in setOf("start", "center", "end") } ?: "start"
        )
    }
}

val CARD_CONTENT_TYPES = listOf("text", "card_stack", "mood", "checklist", "photos", "files", "voice", "music", "media", "game", "location", "weather", "journal")

fun isCardLayoutKey(key: String): Boolean = key.length <= 160 &&
    key.substringBefore(':') in CARD_CONTENT_TYPES &&
    key.all { it.isLetterOrDigit() || it in "_:-" }

fun normalizeCardLayout(layout: Map<String, CardBlockPlacement>): Map<String, CardBlockPlacement> =
    layout.entries.filter { isCardLayoutKey(it.key) }.take(100).associate { it.key to it.value.normalized() }

/**
 * Preserves the saved block order and expands legacy section keys without losing order.
 * Any newly added block not present in saved order is appended to the end of the list.
 */
fun resolveCardContentOrder(saved: List<String>?, active: Set<String>): List<String> {
    val ordered = linkedSetOf<String>()
    for (key in saved.orEmpty()) {
        if (key in active) ordered.add(key)
        else if (':' !in key) active.filter { it.substringBefore(':') == key }.forEach(ordered::add)
    }
    active.filter { it !in ordered && isCardLayoutKey(it) }.forEach(ordered::add)
    return ordered.toList()
}

/** Only neighboring blocks that fit side by side share a row (max 3); never overlap or hide content. */
fun cardContentRows(keys: List<String>, placements: Map<String, CardBlockPlacement>): List<List<String>> {
    val rows = mutableListOf<MutableList<String>>()
    for (key in keys.distinct()) {
        val p = placements[key]?.normalized() ?: CardBlockPlacement()
        val previous = rows.lastOrNull()
        val last = previous?.lastOrNull()?.let { placements[it]?.normalized() ?: CardBlockPlacement() }
        if (previous != null && last != null && previous.size < CARD_MAX_PER_ROW && last.column + last.span <= p.column) previous.add(key)
        else rows.add(mutableListOf(key))
    }
    return rows
}

fun moveCardContent(keys: List<String>, moving: String, target: String): List<String> {
    if (moving == target || moving !in keys || target !in keys) return keys
    return keys.toMutableList().apply { remove(moving); add(keys.indexOf(target).coerceAtMost(size), moving) }
}

fun ReminderItem.cardContentKeys(includeEmptyChecklist: Boolean = false, includeVoiceRecorder: Boolean = false, includeJournal: Boolean = false): Set<String> = linkedSetOf<String>().apply {
    add("text")
    cardStack?.layoutStacks()?.forEach { add(it.first) }
    if (mood != null) add("mood")
    if (checklistItems.isNotEmpty() || includeEmptyChecklist) add("checklist")
    attachments.filter { it.type == AttachmentType.IMAGE }.forEach { add("photos:${it.id}") }
    attachments.filter { it.type != AttachmentType.IMAGE }.forEach { add("files:${it.id}") }
    if (referenceUrl != null) add("files:reference")
    if (voiceRecordings.isNotEmpty() || includeVoiceRecorder) add("voice")
    musicBlocks.forEach { add("music:${it.id}") }
    mediaBlocks.forEach { add("media:${it.id}") }
    gameBlocks.forEach { add("game:${it.id}") }
    if (location != null) add("location")
    // Hava durumu: never added by hand; there whenever the card has a date and a place on the map.
    if ((alertAt != null || plannedDateEpochDay != null) && location?.latitude != null && location.longitude != null) add("weather")
    if (journal != null || includeJournal) add("journal")
}

/** Deleted content loses its geometry. A re-added block starts from the standard placement. */
fun cardLayoutForKeys(layout: Map<String, CardBlockPlacement>, keys: Set<String>): Map<String, CardBlockPlacement> =
    keys.mapNotNull { key -> (layout[key] ?: layout[key.substringBefore(':')])?.let { key to it.normalized() } }.toMap()
