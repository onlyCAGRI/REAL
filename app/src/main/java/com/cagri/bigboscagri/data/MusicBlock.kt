package com.cagri.bigboscagri.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Catalog metadata only. Preview audio is streamed, never stored as an attachment. */
data class MusicBlock(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val provider: String,
    val providerTrackId: String? = null,
    val providerUrl: String,
    val previewUrl: String? = null,
    val previewDurationMillis: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun encodeArray(blocks: List<MusicBlock>): JSONArray = JSONArray().apply {
            blocks.forEach { block ->
                put(JSONObject().apply {
                    put("id", block.id); put("title", block.title); put("artist", block.artist)
                    put("album", block.album); put("artworkUrl", block.artworkUrl)
                    put("provider", block.provider); put("providerTrackId", block.providerTrackId)
                    put("providerUrl", block.providerUrl); put("previewUrl", block.previewUrl)
                    put("previewDurationMillis", block.previewDurationMillis); put("createdAt", block.createdAt)
                })
            }
        }

        fun decodeArray(array: JSONArray?): List<MusicBlock> = buildList {
            if (array == null) return@buildList
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                fun optional(key: String): String? = if (item.isNull(key)) null else item.optString(key).takeIf { it.isNotBlank() }
                val id = optional("id") ?: continue
                val title = optional("title") ?: continue
                add(MusicBlock(id = id, title = title, artist = item.optString("artist", ""),
                    album = optional("album"), artworkUrl = optional("artworkUrl"),
                    provider = item.optString("provider", ""), providerTrackId = optional("providerTrackId"),
                    providerUrl = item.optString("providerUrl", ""), previewUrl = optional("previewUrl"),
                    previewDurationMillis = item.optLong("previewDurationMillis", 0).takeIf { it > 0 },
                    createdAt = item.optLong("createdAt", 0)))
            }
        }
    }
}
