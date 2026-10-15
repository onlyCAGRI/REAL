package com.cagri.real.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

/**
 * Share → REAL: what a shared link is. Only runs for a link the person shared, on their device.
 * Apple Music → iTunes lookup (exact track, 30 s preview); YouTube / YouTube Music → oEmbed; Spotify → its page's Open
 * Graph tags (title, artist, album, cover, og:audio preview); everything else → Open Graph tags. Failure = no preview.
 */
object SharedLinkResolver {
    data class Result(val title: String? = null, val imageUrl: String? = null, val music: MusicBlock? = null)

    suspend fun resolve(url: String): Result = withContext(Dispatchers.IO) {
        runCatching { resolveBlocking(url) }.getOrDefault(Result())
    }

    private suspend fun resolveBlocking(raw: String): Result {
        val uri = URI(raw)
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return Result()
        appleTrackId(uri, host)?.let { id -> itunes(id, raw)?.let { return Result(it.title, it.artworkUrl, it) } }
        if (host == "youtube.com" || host == "m.youtube.com" || host == "youtu.be" || host == "music.youtube.com") {
            val o = fetchJson("https://www.youtube.com/oembed?format=json&url=" + URLEncoder.encode(raw, "UTF-8")) ?: return Result()
            val title = o.optString("title").takeIf { it.isNotBlank() }
            val image = o.optString("thumbnail_url").takeIf { it.startsWith("https://") }
            val music = if (host == "music.youtube.com" && title != null) MusicBlock(title = title.take(300),
                artist = o.optString("author_name").removeSuffix(" - Topic").trim().take(200), artworkUrl = image,
                provider = "YouTube Music", providerTrackId = queryParam(uri, "v"), providerUrl = raw) else null
            return Result(title, image, music)
        }
        val page = fetchPage(raw) ?: return Result()
        val tags = metaTags(page.html)
        val title = (tags["og:title"] ?: tags["twitter:title"] ?: titleTag(page.html))?.let(::decodeEntities)?.trim()?.take(300)
        val image = (tags["og:image"] ?: tags["twitter:image"])?.let(::decodeEntities)?.takeIf { it.startsWith("https://") }
        val finalUri = runCatching { URI(page.finalUrl) }.getOrNull()
        val spotifyTrack = finalUri?.takeIf { it.host?.lowercase() == "open.spotify.com" }?.path
            ?.let { Regex("^(?:/intl-[a-z-]+)?/track/([A-Za-z0-9]{22})").find(it)?.groupValues?.get(1) }
        if (spotifyTrack != null && title != null) {
            // og:description: "Artist · Album · Song · 2021"; og:audio: Spotify's own 30 s preview.
            val parts = tags["og:description"]?.let(::decodeEntities)?.split(" · ").orEmpty().map { it.trim() }
            val preview = tags["og:audio"]?.takeIf { it.startsWith("https://p.scdn.co/") }
            return Result(title, image, MusicBlock(title = title, artist = parts.getOrNull(0).orEmpty().take(200),
                album = parts.getOrNull(1)?.takeIf { it.isNotBlank() && parts.size >= 3 }?.take(200), artworkUrl = image,
                provider = "Spotify", providerTrackId = spotifyTrack, providerUrl = "https://open.spotify.com/track/$spotifyTrack",
                previewUrl = preview, previewDurationMillis = 30_000L.takeIf { preview != null }))
        }
        return Result(title, image)
    }

    private fun appleTrackId(uri: URI, host: String): String? {
        if (host != "music.apple.com" && host != "itunes.apple.com") return null
        queryParam(uri, "i")?.takeIf { it.all(Char::isDigit) }?.let { return it }
        return Regex("/song/[^/]+/(\\d+)").find(uri.path.orEmpty())?.groupValues?.get(1)
    }

    private fun itunes(id: String, link: String): MusicBlock? {
        val item = fetchJson("https://itunes.apple.com/lookup?id=$id&country=${MusicCatalog.storefront()}")?.optJSONArray("results")?.optJSONObject(0) ?: return null
        val title = item.optString("trackName").takeIf { it.isNotBlank() } ?: return null
        fun https(value: String?) = value?.takeIf { it.isNotBlank() }?.let { if (it.startsWith("http://")) "https://" + it.removePrefix("http://") else it }
            ?.takeIf { it.startsWith("https://") }
        return MusicBlock(title = title, artist = item.optString("artistName"), album = item.optString("collectionName").takeIf { it.isNotBlank() },
            artworkUrl = https(item.optString("artworkUrl100"))?.replace("100x100bb", "600x600bb"), provider = "iTunes",
            providerTrackId = id, providerUrl = https(item.optString("trackViewUrl")) ?: link,
            previewUrl = https(item.optString("previewUrl")), previewDurationMillis = 30_000L.takeIf { item.has("previewUrl") })
    }

    private fun queryParam(uri: URI, name: String): String? = uri.rawQuery?.split('&')?.firstNotNullOfOrNull { part ->
        val (key, value) = part.split('=', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        if (key == name) java.net.URLDecoder.decode(value, "UTF-8").takeIf { it.isNotBlank() } else null
    }

    private class Page(val finalUrl: String, val html: String)

    private fun open(url: String): HttpURLConnection {
        require(url.startsWith("https://")) { "Only https links are previewed" }
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000; readTimeout = 6000; instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) REAL/1.0")
            setRequestProperty("Accept-Language", "tr-TR,tr;q=0.9,en;q=0.8")
        }
    }

    private fun readLimited(connection: HttpURLConnection, limit: Int): String? = try {
        if (connection.responseCode !in 200..299) null
        else connection.inputStream.use { input ->
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            while (buffer.size() < limit) {
                val n = input.read(chunk); if (n <= 0) break
                buffer.write(chunk, 0, minOf(n, limit - buffer.size()))
            }
            buffer.toString("UTF-8")
        }
    } finally { connection.disconnect() }

    private fun fetchJson(url: String): JSONObject? = runCatching { readLimited(open(url), 256_000)?.let(::JSONObject) }.getOrNull()

    private fun fetchPage(url: String): Page? {
        val connection = open(url)
        val html = readLimited(connection, 400_000) ?: return null
        val finalUrl = connection.url.toString().takeIf { it.startsWith("https://") } ?: return null
        return Page(finalUrl, html)
    }

    private val META = Regex("<meta\\s[^>]*>", RegexOption.IGNORE_CASE)
    private val ATTR = Regex("([a-zA-Z:-]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)')")

    internal fun metaTags(html: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        for (tag in META.findAll(html)) {
            val attrs = ATTR.findAll(tag.value).associate { m ->
                m.groupValues[1].lowercase() to (m.groups[3]?.value ?: m.groups[4]?.value.orEmpty())
            }
            val key = (attrs["property"] ?: attrs["name"])?.lowercase() ?: continue
            val content = attrs["content"]?.takeIf { it.isNotBlank() } ?: continue
            result.putIfAbsent(key, content)
        }
        return result
    }

    private fun titleTag(html: String): String? =
        Regex("<title[^>]*>([^<]{1,300})</title>", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)

    internal fun decodeEntities(value: String): String = value
        .replace(Regex("&#x([0-9a-fA-F]+);")) { m -> m.groupValues[1].toIntOrNull(16)?.let { runCatching { String(Character.toChars(it)) }.getOrNull() } ?: m.value }
        .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.let { runCatching { String(Character.toChars(it)) }.getOrNull() } ?: m.value }
        .replace("&quot;", "\"").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
}
