package com.cagri.real.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class MediaType {
    MOVIE,
    TV;

    val persistKey: String get() = name

    companion object {
        fun fromKey(key: String?): MediaType? = entries.firstOrNull { it.name.equals(key, ignoreCase = true) }
    }
}

/** Verified movie / TV metadata. New records may come from TheTVDB or TVmaze; Apple remains for legacy data. */
data class MediaBlock(
    val id: String = UUID.randomUUID().toString(),
    val type: MediaType,
    val providerId: String? = null,
    val title: String,
    val subtitle: String? = null,
    val year: Int? = null,
    val genre: String? = null,
    val description: String? = null,
    val artworkUrl: String? = null,
    val provider: String = "Apple",
    val providerUrl: String,
    val durationMillis: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun encodeArray(blocks: List<MediaBlock>): JSONArray = JSONArray().apply {
            blocks.forEach { block ->
                put(JSONObject().apply {
                    put("id", block.id)
                    put("type", block.type.persistKey)
                    put("providerId", block.providerId ?: JSONObject.NULL)
                    put("title", block.title)
                    put("subtitle", block.subtitle ?: JSONObject.NULL)
                    put("year", block.year ?: JSONObject.NULL)
                    put("genre", block.genre ?: JSONObject.NULL)
                    put("description", block.description ?: JSONObject.NULL)
                    put("artworkUrl", block.artworkUrl ?: JSONObject.NULL)
                    put("provider", block.provider)
                    put("providerUrl", block.providerUrl)
                    put("durationMillis", block.durationMillis ?: JSONObject.NULL)
                    put("createdAt", block.createdAt)
                })
            }
        }

        fun decodeArray(array: JSONArray?): List<MediaBlock> = buildList {
            if (array == null) return@buildList
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                fun optional(key: String): String? = if (item.isNull(key)) null else item.optString(key).takeIf { it.isNotBlank() }
                val id = optional("id") ?: continue
                val type = MediaType.fromKey(optional("type")) ?: continue
                val title = optional("title") ?: continue
                val providerUrl = item.optString("providerUrl", "").takeIf { it.isNotBlank() } ?: continue
                val year = if (item.isNull("year")) null else item.optInt("year").takeIf { it > 0 }
                val durationMillis = if (item.isNull("durationMillis")) null else item.optLong("durationMillis").takeIf { it > 0 }

                add(
                    MediaBlock(
                        id = id,
                        type = type,
                        providerId = optional("providerId"),
                        title = title,
                        subtitle = optional("subtitle"),
                        year = year,
                        genre = optional("genre"),
                        description = optional("description"),
                        artworkUrl = optional("artworkUrl"),
                        provider = item.optString("provider", "Apple"),
                        providerUrl = providerUrl,
                        durationMillis = durationMillis,
                        createdAt = item.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }
    }
}
