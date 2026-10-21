package com.cagri.real.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Oyun bloğu: a game on a card (cover, name, year, genres, platforms, IGDB page). Stored in its own `gameBlocks`
 * field, not in mediaBlocks: other REAL versions read mediaBlocks strictly and would drop a whole card with an
 * unknown media type, while an unknown field is simply kept.
 */
data class CardGameBlock(
    val id: String = UUID.randomUUID().toString(),
    val providerId: String,
    val title: String,
    val year: Int? = null,
    val genres: List<String> = emptyList(),
    val platforms: List<String> = emptyList(),
    val artworkUrl: String? = null,
    val description: String? = null,
    val provider: String = "IGDB",
    val providerUrl: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun encodeArray(blocks: List<CardGameBlock>): JSONArray = JSONArray().apply {
            blocks.forEach { b ->
                put(JSONObject().put("id", b.id).put("providerId", b.providerId).put("title", b.title)
                    .put("year", b.year ?: JSONObject.NULL).put("genres", JSONArray(b.genres)).put("platforms", JSONArray(b.platforms))
                    .put("artworkUrl", b.artworkUrl ?: JSONObject.NULL).put("description", b.description ?: JSONObject.NULL)
                    .put("provider", b.provider).put("providerUrl", b.providerUrl).put("createdAt", b.createdAt))
            }
        }

        fun decodeArray(array: JSONArray?): List<CardGameBlock> = buildList {
            if (array == null) return@buildList
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                fun opt(k: String) = if (o.isNull(k)) null else o.optString(k).trim().takeIf { it.isNotEmpty() }
                fun list(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) } }.orEmpty()
                val id = opt("id") ?: continue
                val title = opt("title") ?: continue
                val url = opt("providerUrl")?.takeIf { it.startsWith("https://") } ?: continue
                add(CardGameBlock(id, opt("providerId") ?: id, title.take(300),
                    if (o.isNull("year")) null else o.optInt("year").takeIf { it in 1950..2200 },
                    list("genres").take(8), list("platforms").take(16), opt("artworkUrl")?.takeIf { it.startsWith("https://") },
                    opt("description")?.take(2000), opt("provider") ?: "IGDB", url, o.optLong("createdAt", 0)))
            }
        }
    }
}
