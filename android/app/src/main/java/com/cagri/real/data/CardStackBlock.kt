package com.cagri.real.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * A single card within a CardStackBlock.
 * The production model is independent of any demo classes.
 */
data class CardStackCard(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val body: String = "",
    val colorArgb: Int? = null,
    val imageUri: String? = null,
    /** Presentation grouping inside the existing canonical stack payload. Null is the legacy stack. */
    val stackGroupId: String? = null,
    /** Bold / italic / underline / strike ranges over [body]; [body] itself stays plain text. */
    val bodyStyles: List<BodySpan> = emptyList(),
    /** Stack-wide writing settings (size, first-line indent, paragraph gap), kept on each card. */
    val bodyFormat: BodyFormat = BodyFormat()
) {
    companion object {
        fun encode(card: CardStackCard): JSONObject = JSONObject().apply {
            put("id", card.id)
            put("title", card.title)
            put("body", card.body)
            put("colorArgb", card.colorArgb ?: JSONObject.NULL)
            put("imageUri", card.imageUri ?: JSONObject.NULL)
            card.stackGroupId?.let { put("stackGroupId", it) }
            BodySpans.encode(card.bodyStyles)?.let { put("bodyStyles", it) }
            BodyFormat.encode(card.bodyFormat)?.let { put("bodyFormat", it) }
        }

        fun decode(obj: JSONObject?): CardStackCard? {
            if (obj == null) return null
            val body = obj.optString("body", "")
            return CardStackCard(
                id = obj.optString("id", UUID.randomUUID().toString()),
                title = obj.optString("title", ""),
                body = body,
                bodyStyles = BodySpans.decode(obj.optJSONArray("bodyStyles"), body.length),
                bodyFormat = BodyFormat.decode(obj.optJSONObject("bodyFormat")),
                colorArgb = if (obj.isNull("colorArgb")) null else obj.optInt("colorArgb"),
                imageUri = if (obj.isNull("imageUri")) null else obj.optString("imageUri").takeIf { it.isNotBlank() },
                stackGroupId = obj.optString("stackGroupId", "").takeIf {
                    it.isNotBlank() && it.length <= 80 && it.all { c -> c.isLetterOrDigit() || c in "_-" }
                }
            )
        }

        fun encodeArray(cards: List<CardStackCard>): JSONArray = JSONArray().apply {
            cards.forEach { put(encode(it)) }
        }

        fun decodeArray(array: JSONArray?): List<CardStackCard> = buildList {
            if (array == null) return@buildList
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                decode(item)?.let { add(it) }
            }
        }
    }
}

private const val LEGACY_TEXT_BOX_SOURCE = "REAL_TEXT_BLOCK"

/**
 * Text boxes were folded into card stacks: each written box becomes a one-card stack in the same grid
 * slot (order and placement keep their position), empty boxes are dropped.
 */
fun ReminderItem.withTextBoxesAsCardStacks(): ReminderItem {
    val boxes = attachments.filter { it.type == AttachmentType.TEXT_SNIPPET && it.sourceTitle == LEGACY_TEXT_BOX_SOURCE }
    if (boxes.isEmpty()) return this
    val keys = mutableMapOf<String, String?>()
    val cards = boxes.mapNotNull { box ->
        val body = box.text.orEmpty().trim()
        if (body.isEmpty()) { keys["files:${box.id}"] = null; return@mapNotNull null }
        val group = "tb" + box.id.filter { it.isLetterOrDigit() || it in "_-" }.take(70)
        keys["files:${box.id}"] = "card_stack:$group"
        CardStackCard(id = box.id.ifBlank { UUID.randomUUID().toString() }, body = body, stackGroupId = group)
    }
    val stack = cardStack ?: CardStackBlock()
    return copy(
        attachments = attachments.filterNot { it in boxes },
        cardStack = if (cards.isEmpty()) cardStack else stack.copy(cards = stack.cards + cards),
        contentBlockOrder = contentBlockOrder.mapNotNull { if (it in keys) keys[it] else it },
        contentBlockLayout = buildMap {
            contentBlockLayout.forEach { (key, placement) ->
                if (key !in keys) put(key, placement) else keys[key]?.let { put(it, placement) }
            }
        }
    )
}

/**
 * Persistent Card Stack content block.
 * Holds an optional contextual prompt and a list of CardStackCards.
 */
data class CardStackBlock(
    val id: String = UUID.randomUUID().toString(),
    val prompt: String? = null,
    val cards: List<CardStackCard> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
) {
    /** Old cards stay together; newly added stacks have independent, stable layout keys. */
    fun layoutStacks(): List<Pair<String, CardStackBlock>> {
        if (cards.isEmpty()) return listOf("card_stack" to this)
        return cards.groupBy { it.stackGroupId }.map { (group, groupedCards) ->
            val key = group?.let { "card_stack:$it" } ?: "card_stack"
            key to copy(id = if (group == null) id else "$id:$group", cards = groupedCards,
                prompt = if (group == null) prompt else null)
        }
    }

    fun replaceLayoutStack(key: String, replacement: CardStackBlock?): CardStackBlock? {
        if (layoutStacks().none { it.first == key }) return this
        val group = key.substringAfter(':', "").takeIf { it.isNotEmpty() }
        val updated = replacement?.cards.orEmpty().map { it.copy(stackGroupId = group) }
        val first = cards.indexOfFirst { it.stackGroupId == group }.coerceAtLeast(0)
        val remaining = cards.filterNot { it.stackGroupId == group }.toMutableList()
        remaining.addAll(first.coerceAtMost(remaining.size), updated)
        return if (remaining.isEmpty()) null else copy(cards = remaining)
    }

    companion object {
        fun encode(block: CardStackBlock): JSONObject = JSONObject().apply {
            put("id", block.id)
            put("prompt", block.prompt ?: JSONObject.NULL)
            put("cards", CardStackCard.encodeArray(block.cards))
            put("createdAt", block.createdAt)
        }

        fun decode(obj: JSONObject?): CardStackBlock? {
            if (obj == null) return null
            val cards = CardStackCard.decodeArray(obj.optJSONArray("cards"))
            return CardStackBlock(
                id = obj.optString("id", UUID.randomUUID().toString()),
                prompt = if (obj.isNull("prompt")) null else obj.optString("prompt").takeIf { it.isNotBlank() },
                cards = cards,
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            )
        }

        fun encodeArray(blocks: List<CardStackBlock>): JSONArray = JSONArray().apply {
            blocks.forEach { put(encode(it)) }
        }

        fun decodeArray(array: JSONArray?): List<CardStackBlock> = buildList {
            if (array == null) return@buildList
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                decode(item)?.let { add(it) }
            }
        }
    }
}
