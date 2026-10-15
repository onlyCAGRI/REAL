package com.cagri.real.discovery

import android.content.Context
import com.cagri.real.data.MusicBlock
import com.cagri.real.data.MusicCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Apple Music genres (iTunes genre ids). The charts come from the person's own country. */
enum class MusicGenre(val key: String, val label: String, val itunesId: Int) {
    Pop("pop", "Pop", 14), Rock("rock", "Rock", 21), HipHop("hiphop", "Hip-Hop / Rap", 18),
    Electronic("electronic", "Elektronik", 7), Dance("dance", "Dans / House", 17), Alternative("alternative", "Alternatif", 20),
    RnB("rnb", "R&B / Soul", 15), Jazz("jazz", "Caz", 11), Classical("classical", "Klasik", 5),
    Soundtrack("soundtrack", "Film Müzikleri", 16), Metal("metal", "Metal", 1153), Latin("latin", "Latin", 12),
    Folk("folk", "Folk", 10), Reggae("reggae", "Reggae", 24), Blues("blues", "Blues", 2), Country("country", "Country", 6);

    companion object { fun fromKey(key: String) = entries.firstOrNull { it.key == key } }
}

/** "Nasıl hissettirsin?": a mood is a mix of genres. */
enum class MusicMood(val key: String, val label: String, val genres: List<MusicGenre>) {
    Energetic("energetic", "Enerjik", listOf(MusicGenre.Dance, MusicGenre.Electronic, MusicGenre.HipHop, MusicGenre.Pop)),
    Calm("calm", "Sakin", listOf(MusicGenre.Jazz, MusicGenre.Classical, MusicGenre.RnB, MusicGenre.Soundtrack)),
    Focus("focus", "Odak", listOf(MusicGenre.Classical, MusicGenre.Soundtrack, MusicGenre.Electronic)),
    Sad("sad", "Hüzünlü", listOf(MusicGenre.Alternative, MusicGenre.RnB, MusicGenre.Blues, MusicGenre.Folk)),
    Party("party", "Parti", listOf(MusicGenre.Dance, MusicGenre.Pop, MusicGenre.Latin, MusicGenre.HipHop));

    companion object { fun fromKey(key: String) = entries.firstOrNull { it.key == key } }
}

data class MusicCard(val music: MusicBlock, val genre: String) {
    val key: String get() = "iTunes:${music.providerTrackId ?: music.providerUrl}"
}

/**
 * Müzik keşfi (Film / Dizi / Oyun'un müzik hali). Songs: Apple Music top charts per genre and country (no account,
 * no key; covers, 30 s previews, Apple Music links). Right swipe saves to "Müzik listem", left passes; passed and saved
 * songs never come back. Local, per REAL user.
 */
class MusicDiscoveryRepository(context: Context, uid: String) {
    private val prefs = context.applicationContext.getSharedPreferences("real_music_discovery_$uid", Context.MODE_PRIVATE)
    private val lock = Mutex()

    data class Snapshot(val selection: Set<String>, val cards: List<MusicCard>, val list: List<MusicCard>, val excluded: List<String>)

    private fun strings(a: JSONArray?) = if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) }
    private fun encode(cards: List<MusicCard>, limit: Int) = JSONArray().apply {
        cards.take(limit).forEach { put(JSONObject().put("music", MusicBlock.encodeArray(listOf(it.music)).getJSONObject(0)).put("genre", it.genre)) }
    }
    private fun decode(a: JSONArray?): List<MusicCard> = buildList {
        if (a == null) return@buildList
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val block = MusicBlock.decodeArray(JSONArray().put(o.optJSONObject("music") ?: continue)).firstOrNull() ?: continue
            add(MusicCard(block, o.optString("genre")))
        }
    }
    private fun read(): Snapshot {
        val j = runCatching { JSONObject(prefs.getString("snapshot", "{}") ?: "{}") }.getOrElse { JSONObject() }
        return Snapshot(strings(j.optJSONArray("selection")).toSet(), decode(j.optJSONArray("cards")), decode(j.optJSONArray("list")),
            strings(j.optJSONArray("excluded")))
    }
    private fun write(s: Snapshot) {
        val j = JSONObject().put("selection", JSONArray(s.selection.toList())).put("cards", encode(s.cards, 120))
            .put("list", encode(s.list, 500)).put("excluded", JSONArray(s.excluded.takeLast(3000)))
        if (!prefs.edit().putString("snapshot", j.toString()).commit()) throw IOException("Müzik önerileri kaydedilemedi.")
    }

    suspend fun load(): Snapshot = withContext(Dispatchers.IO) { lock.withLock { read() } }

    /** Genres from the selection (genre keys and mood keys). */
    fun genresOf(selection: Set<String>): List<MusicGenre> =
        (selection.mapNotNull { MusicGenre.fromKey(it) } + selection.mapNotNull { MusicMood.fromKey(it) }.flatMap { it.genres }).distinct()

    suspend fun fetch(selection: Set<String>): Snapshot = withContext(Dispatchers.IO) {
        val genres = genresOf(selection).ifEmpty { listOf(MusicGenre.Pop) }.take(6)
        val country = MusicCatalog.storefront().lowercase()
        val prior = load()
        val known = (prior.excluded + prior.list.map { it.key } + prior.cards.map { it.key }).toHashSet()
        val perGenre = coroutineScope {
            genres.map { g -> async { runCatching { chart(country, g) }.getOrDefault(emptyList()) } }.awaitAll()
        }
        if (perGenre.all { it.isEmpty() }) throw IOException("Müzik listeleri alınamadı. Bağlantını kontrol edip yeniden dene.")
        // Interleave the genres so the deck is mixed, each genre's order lightly shuffled.
        val queues = perGenre.map { list -> list.filter { it.key !in known }.shuffled().toMutableList() }
        val fresh = mutableListOf<MusicCard>()
        val seen = HashSet<String>()
        while (fresh.size < 40 && queues.any { it.isNotEmpty() }) {
            for (q in queues) {
                val next = q.removeFirstOrNull() ?: continue
                if (seen.add(next.key)) fresh += next
            }
        }
        if (fresh.isEmpty()) throw IOException("Bu seçimlerde yeni şarkı kalmadı. Başka bir tür dene.")
        lock.withLock {
            val cur = read()
            val same = cur.selection == selection
            val next = cur.copy(selection = selection, cards = (if (same) cur.cards else emptyList()) + fresh)
            write(next); next
        }
    }

    enum class Action { SAVE, SKIP, KEEP, REMOVE }

    suspend fun act(card: MusicCard, action: Action): Snapshot = withContext(Dispatchers.IO) { lock.withLock {
        val s = read()
        val rest = s.cards.filterNot { it.key == card.key }
        val excluded = (s.excluded + card.key).distinct()
        val next = when (action) {
            Action.SAVE -> s.copy(cards = rest, list = s.list.filterNot { it.key == card.key } + card, excluded = excluded)
            Action.SKIP -> s.copy(cards = rest, excluded = excluded)
            Action.KEEP -> s.copy(list = s.list.filterNot { it.key == card.key } + card)
            Action.REMOVE -> s.copy(list = s.list.filterNot { it.key == card.key })
        }
        write(next); next
    } }

    /** Apple Music top songs of one genre in one country (iTunes RSS). */
    private fun chart(country: String, genre: MusicGenre): List<MusicCard> {
        val connection = URL("https://itunes.apple.com/$country/rss/topsongs/limit=100/genre=${genre.itunesId}/json").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000; connection.readTimeout = 10_000
            if (connection.responseCode !in 200..299) return emptyList()
            val feed = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).optJSONObject("feed") ?: return emptyList()
            val entries = feed.optJSONArray("entry") ?: feed.optJSONObject("entry")?.let { JSONArray().put(it) } ?: return emptyList()
            return (0 until entries.length()).mapNotNull { i -> entries.optJSONObject(i)?.let { parse(it, genre) } }
        } finally { connection.disconnect() }
    }

    private fun label(o: JSONObject?, key: String) = o?.optJSONObject(key)?.optString("label")?.takeIf { it.isNotBlank() }

    private fun parse(e: JSONObject, genre: MusicGenre): MusicCard? {
        val title = label(e, "im:name") ?: return null
        val artist = label(e, "im:artist").orEmpty()
        val id = e.optJSONObject("id")?.optJSONObject("attributes")?.optString("im:id")?.takeIf { it.all(Char::isDigit) && it.isNotEmpty() }
        val links = e.optJSONArray("link") ?: e.optJSONObject("link")?.let { JSONArray().put(it) } ?: JSONArray()
        var page: String? = null; var preview: String? = null
        for (i in 0 until links.length()) {
            val a = links.optJSONObject(i)?.optJSONObject("attributes") ?: continue
            val href = a.optString("href").takeIf { it.startsWith("https://") } ?: continue
            if (a.optString("im:assetType") == "preview" || a.optString("type").startsWith("audio")) preview = href
            else if (a.optString("type") == "text/html") page = href
        }
        val images = e.optJSONArray("im:image")
        val art = images?.optJSONObject(images.length() - 1)?.optString("label")
            ?.replace(Regex("/\\d+x\\d+bb\\."), "/600x600bb.")?.takeIf { it.startsWith("https://") }
        val album = e.optJSONObject("im:collection")?.let { label(it, "im:name") }
        return MusicCard(MusicBlock(title = title.take(300), artist = artist.take(200), album = album?.take(200), artworkUrl = art,
            provider = "iTunes", providerTrackId = id, providerUrl = page ?: return null, previewUrl = preview,
            previewDurationMillis = 30_000L.takeIf { preview != null }), genre.key)
    }
}
