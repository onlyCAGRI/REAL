package com.cagri.bigboscagri.data

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
    val imageUri: String? = null
) {
    companion object {
        fun encode(card: CardStackCard): JSONObject = JSONObject().apply {
            put("id", card.id)
            put("title", card.title)
            put("body", card.body)
            put("colorArgb", card.colorArgb ?: JSONObject.NULL)
            put("imageUri", card.imageUri ?: JSONObject.NULL)
        }

        fun decode(obj: JSONObject?): CardStackCard? {
            if (obj == null) return null
            return CardStackCard(
                id = obj.optString("id", UUID.randomUUID().toString()),
                title = obj.optString("title", ""),
                body = obj.optString("body", ""),
                colorArgb = if (obj.isNull("colorArgb")) null else obj.optInt("colorArgb"),
                imageUri = if (obj.isNull("imageUri")) null else obj.optString("imageUri").takeIf { it.isNotBlank() }
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
