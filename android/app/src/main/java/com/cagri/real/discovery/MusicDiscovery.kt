package com.cagri.real.discovery

import com.cagri.real.i18n.t
import com.cagri.real.i18n.tIn
import android.content.Context
import com.cagri.real.data.MusicBlock
import com.cagri.real.data.MusicCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Apple Music genres (iTunes genre ids). The charts come from the person's own country. */
enum class MusicGenre(val key: String, private val trLabel: String, val itunesId: Int) {
    Pop("pop", "Pop", 14), Rock("rock", "Rock", 21), HipHop("hiphop", "Hip-Hop / Rap", 18),
    Electronic("electronic", "Elektronik", 7), Dance("dance", "Dans / House", 17), Alternative("alternative", "Alternatif", 20),
    RnB("rnb", "R&B / Soul", 15), Jazz("jazz", "Caz", 11), Classical("classical", "Klasik", 5),
    Soundtrack("soundtrack", "Film Müzikleri", 16), Metal("metal", "Metal", 1153), Latin("latin", "Latin", 12),
    Folk("folk", "Folk", 10), Reggae("reggae", "Reggae", 24), Blues("blues", "Blues", 2), Country("country", "Country", 6);

    val label: String get() = tIn("genre", trLabel)

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

/**
 * Well-known artists per genre (global first, then Türkiye). Charts only show what is bought / played this week, so
 * a genre's big names (Travis Scott, Metro Boomin…) can be missing; their own top songs are mixed in from here.
 * Temporary until the Apple Music API (per-genre charts and playlists) is available.
 */
internal object MusicSeeds {
    /**
     * Deezer playlist searches per genre; only playlists made by Deezer's own editors are read. Global (mostly English)
     * and Türkiye are separate pools, so one long local playlist cannot fill the deck ("rap" alone found French lists).
     */
    val deezerGlobal: Map<MusicGenre, List<String>> = mapOf(
        MusicGenre.Pop to listOf("pop hits", "today's hits"), MusicGenre.Rock to listOf("rock hits", "rock"),
        MusicGenre.HipHop to listOf("us rap", "hip hop hits"), MusicGenre.Electronic to listOf("electronic", "electro"),
        MusicGenre.Dance to listOf("dance hits", "house"), MusicGenre.Alternative to listOf("indie", "alternative"),
        MusicGenre.RnB to listOf("r&b hits", "soul"), MusicGenre.Jazz to listOf("jazz"), MusicGenre.Classical to listOf("classical"),
        MusicGenre.Soundtrack to listOf("soundtrack", "film score"), MusicGenre.Metal to listOf("metal"),
        MusicGenre.Latin to listOf("latin hits", "reggaeton"), MusicGenre.Folk to listOf("folk"),
        MusicGenre.Reggae to listOf("reggae"), MusicGenre.Blues to listOf("blues"), MusicGenre.Country to listOf("country"),
    )
    val deezerLocal: Map<MusicGenre, List<String>> = mapOf(
        MusicGenre.Pop to listOf("türkçe pop"), MusicGenre.Rock to listOf("türkçe rock"), MusicGenre.HipHop to listOf("türkçe rap"),
        MusicGenre.Folk to listOf("türk halk"),
    )

    /** Hand-picked names that always belong to the genre, mixed with the ones charting now (Deezer). */
    val artists: Map<MusicGenre, List<String>> = mapOf(
        MusicGenre.Pop to listOf("Taylor Swift", "The Weeknd", "Dua Lipa", "Billie Eilish", "Sabrina Carpenter", "Ariana Grande", "Harry Styles",
            "Olivia Rodrigo", "Bruno Mars", "Ed Sheeran", "Lady Gaga", "Tarkan", "Sezen Aksu", "Mabel Matiz", "Hadise", "Edis"),
        MusicGenre.Rock to listOf("Arctic Monkeys", "Queen", "Nirvana", "Foo Fighters", "Red Hot Chili Peppers", "The Killers", "Imagine Dragons",
            "Linkin Park", "Guns N' Roses", "AC/DC", "Duman", "maNga", "Teoman", "Barış Manço", "Cem Karaca"),
        MusicGenre.HipHop to listOf("Travis Scott", "Metro Boomin", "Kendrick Lamar", "Drake", "Future", "21 Savage", "Playboi Carti", "Don Toliver",
            "Kanye West", "J. Cole", "Tyler, The Creator", "A\$AP Rocky", "Eminem", "Lil Uzi Vert", "Ezhel", "UZI", "Lvbel C5", "BLOK3", "Ceza",
            "Sagopa Kajmer", "Ben Fero"),
        MusicGenre.Electronic to listOf("Daft Punk", "Fred again..", "Disclosure", "Calvin Harris", "Avicii", "Flume", "ODESZA", "Justice",
            "Kaytranada", "Bonobo", "Peggy Gou", "Mahmut Orhan"),
        MusicGenre.Dance to listOf("David Guetta", "Calvin Harris", "Swedish House Mafia", "Martin Garrix", "Tiësto", "Fisher", "Chris Lake",
            "Dom Dolla", "John Summit", "Black Coffee", "Kungs", "Mahmut Orhan"),
        MusicGenre.Alternative to listOf("Radiohead", "Tame Impala", "Arctic Monkeys", "The Strokes", "Lana Del Rey", "Cigarettes After Sex",
            "Glass Animals", "Mac DeMarco", "Phoebe Bridgers", "Gorillaz", "Gaye Su Akyol", "Altın Gün", "Adamlar", "Dolu Kadehi Ters Tut"),
        MusicGenre.RnB to listOf("SZA", "Frank Ocean", "The Weeknd", "Daniel Caesar", "Brent Faiyaz", "H.E.R.", "Summer Walker", "Giveon",
            "Beyoncé", "Usher", "Jhené Aiko", "Bruno Mars"),
        MusicGenre.Jazz to listOf("Miles Davis", "John Coltrane", "Bill Evans", "Chet Baker", "Ella Fitzgerald", "Herbie Hancock", "Norah Jones",
            "Kamasi Washington", "Robert Glasper", "Nina Simone", "Kerem Görsev", "İlhan Erşahin"),
        MusicGenre.Classical to listOf("Ludovico Einaudi", "Max Richter", "Johann Sebastian Bach", "Wolfgang Amadeus Mozart", "Ludwig van Beethoven",
            "Frédéric Chopin", "Claude Debussy", "Ólafur Arnalds", "Yiruma", "Fazıl Say"),
        MusicGenre.Soundtrack to listOf("Hans Zimmer", "John Williams", "Ludwig Göransson", "Ennio Morricone", "Howard Shore", "Ramin Djawadi",
            "Joe Hisaishi", "Trent Reznor", "Jóhann Jóhannsson", "Toygar Işıklı"),
        MusicGenre.Metal to listOf("Metallica", "Iron Maiden", "System of a Down", "Slipknot", "Tool", "Gojira", "Avenged Sevenfold",
            "Bring Me The Horizon", "Ghost", "Black Sabbath", "Pentagram"),
        MusicGenre.Latin to listOf("Bad Bunny", "Karol G", "J Balvin", "Rosalía", "Peso Pluma", "Feid", "Rauw Alejandro", "Shakira", "Daddy Yankee", "Ozuna"),
        MusicGenre.Folk to listOf("Bon Iver", "Fleet Foxes", "Mumford & Sons", "Hozier", "Iron & Wine", "Simon & Garfunkel", "Bob Dylan",
            "Neşet Ertaş", "Kazım Koyuncu", "Fikret Kızılok"),
        MusicGenre.Reggae to listOf("Bob Marley & The Wailers", "Damian Marley", "Peter Tosh", "Chronixx", "Protoje", "Toots & The Maytals", "UB40", "Steel Pulse"),
        MusicGenre.Blues to listOf("B.B. King", "Muddy Waters", "Gary Clark Jr.", "Stevie Ray Vaughan", "John Lee Hooker", "Etta James",
            "Joe Bonamassa", "Eric Clapton"),
        MusicGenre.Country to listOf("Morgan Wallen", "Zach Bryan", "Luke Combs", "Chris Stapleton", "Kacey Musgraves", "Johnny Cash",
            "Dolly Parton", "Tyler Childers", "Post Malone"),
    )
}

/**
 * "Dönem": one era at a time (none = every era). Songs are kept by their release year; Deezer's decade playlists give
 * the era's artists, and the weekly charts only join for 2020+ and every era.
 */
enum class MusicEra(val key: String, private val trLabel: String, val years: IntRange, val terms: List<String>, val title: Regex?) {
    Now("era_2020", "2020+", 2020..9999, emptyList(), null),
    Tens("era_2010", "2010–2020", 2010..2019, listOf("2010s", "10s"), Regex("""(20)?10'?s|2010""", RegexOption.IGNORE_CASE)),
    Noughties("era_2000", "2000–2010", 2000..2009, listOf("2000s"), Regex("""2000'?s|\b00'?s|2000""", RegexOption.IGNORE_CASE)),
    Before("era_old", "2000 öncesi", 0..1999, listOf("90s", "80s"), Regex("""[6-9]0'?s|19\d\d""", RegexOption.IGNORE_CASE));

    val label: String get() = t(trLabel)

    companion object { fun of(selection: Set<String>) = entries.firstOrNull { it.key in selection } }
}

/** [year]: release year when known (artist songs, charts); used for "Dönem". */
data class MusicCard(val music: MusicBlock, val genre: String, val year: Int? = null) {
    val key: String get() = "iTunes:${music.providerTrackId ?: music.providerUrl}"
}

/**
 * Müzik keşfi (Film / Dizi / Oyun'un müzik hali). Songs: Apple Music top charts per genre and country (no account,
 * no key; covers, 30 s previews, Apple Music links). Right swipe saves to "Müzik listem", left passes; passed and saved
 * songs never come back. Local, per REAL user.
 */
private const val DISCOVER_ENDPOINT = "https://real-backend-staging.cagrigul1907.workers.dev/api/music-discover"

/** Selection key of "Sana özel" (only offered with Apple Music connected): Apple's picks for the person in the deck. */
const val MusicPersonalKey = "personal_apple"

class MusicDiscoveryRepository(context: Context, uid: String) {
    private val appContext = context.applicationContext
    private val libraryScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    private val prefs = context.applicationContext.getSharedPreferences("real_music_discovery_$uid", Context.MODE_PRIVATE)
    private val lock = Mutex()
    /** Songs swiped off the deck, newest last (a shake brings the last one back). */
    private val swiped = ArrayDeque<Pair<MusicCard, Snapshot>>()

    data class Snapshot(val selection: Set<String>, val cards: List<MusicCard>, val list: List<MusicCard>, val excluded: List<String>)

    private fun strings(a: JSONArray?) = if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) }
    private fun encode(cards: List<MusicCard>, limit: Int) = JSONArray().apply {
        cards.take(limit).forEach { c ->
            put(JSONObject().put("music", MusicBlock.encodeArray(listOf(c.music)).getJSONObject(0)).put("genre", c.genre).apply { c.year?.let { put("year", it) } })
        }
    }
    private fun decode(a: JSONArray?): List<MusicCard> = buildList {
        if (a == null) return@buildList
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val block = MusicBlock.decodeArray(JSONArray().put(o.optJSONObject("music") ?: continue)).firstOrNull() ?: continue
            add(MusicCard(block, o.optString("genre"), o.optInt("year", 0).takeIf { it > 0 }))
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
        if (!prefs.edit().putString("snapshot", j.toString()).commit()) throw IOException(t("Müzik önerileri kaydedilemedi."))
    }

    suspend fun load(): Snapshot = withContext(Dispatchers.IO) { lock.withLock { read() } }

    /** Genres from the selection (genre keys and mood keys). */
    fun genresOf(selection: Set<String>): List<MusicGenre> =
        (selection.mapNotNull { MusicGenre.fromKey(it) } + selection.mapNotNull { MusicMood.fromKey(it) }.flatMap { it.genres }).distinct()

    suspend fun fetch(selection: Set<String>): Snapshot = withContext(Dispatchers.IO) {
        val genres = genresOf(selection).ifEmpty { listOf(MusicGenre.Pop) }.take(6)
        val era = MusicEra.of(selection)
        fun inEra(c: MusicCard) = era == null || (c.year != null && c.year in era.years)
        val country = MusicCatalog.storefront().lowercase()
        val prior = load()
        val known = (prior.excluded + prior.list.map { it.key } + prior.cards.map { it.key }).toHashSet()
        // The country's chart first; the US chart of the same genre is woven in, so small local charts (jazz,
        // classical…) do not fill the deck with songs nobody knows.
        // Each genre also gets songs of its well-known artists (MusicSeeds): at most 12 new artist lookups per
        // round (iTunes rate limit), the rest come from a week's cache.
        val budget = java.util.concurrent.atomic.AtomicInteger(12)
        val perGenre = coroutineScope {
            genres.map { g -> async {
                // Apple Music charts + Apple's own playlists through the REAL Worker (explicit originals too); the
                // iTunes charts + artist songs below only when the Worker fails.
                val apple = runCatching { appleMusic(country, g, era) }.getOrDefault(emptyList())
                    .filter { it.key !in known && inEra(it) }
                if (apple.isNotEmpty()) return@async apple.chunked(6)
                    .flatMap { window -> window.shuffled().sortedBy { if (it.music.previewUrl != null) 0 else 1 } }
                // The weekly charts only fit today's music; an older era is built from its artists alone.
                val withCharts = era == null || era == MusicEra.Now
                val local = async { if (withCharts) runCatching { chart(country, g) }.getOrDefault(emptyList()) else emptyList() }
                val us = async { if (!withCharts || country == "us") emptyList() else runCatching { chart("us", g) }.getOrDefault(emptyList()) }
                val seeds = async { seedSongs(country, g, known, budget, era) }
                val charts = interleave(local.await(), us.await()).filter { it.key !in known && inEra(it) }.chunked(6)
                    .flatMap { window -> window.shuffled().sortedBy { if (it.music.previewUrl != null) 0 else 1 } }
                weave(seeds.await(), charts)
            } }.awaitAll()
        }
        // "Sana özel": Apple's picks for the person within the genres, woven in as one more list.
        val personal = if (MusicPersonalKey !in selection) emptyList() else runCatching {
            AppleMusicAccount.personalSongs(appContext, genres.map { it.itunesId })?.let { cardsOf(it, genres.first().key) }.orEmpty()
        }.getOrDefault(emptyList()).filter { it.key !in known && inEra(it) }
        if (perGenre.all { it.isEmpty() } && personal.isEmpty()) throw IOException(t("Müzik listeleri alınamadı. Bağlantını kontrol edip yeniden dene."))
        // Interleave the genres so the deck is mixed. Each genre keeps Apple's ranking (most played first), only
        // mixed inside small windows; songs with a preview come first within a window.
        val queues = (listOf(personal) + perGenre).map { list -> list.filter { it.key !in known }.toMutableList() }
        val fresh = mutableListOf<MusicCard>()
        val seen = HashSet<String>()
        while (fresh.size < 40 && queues.any { it.isNotEmpty() }) {
            for (q in queues) {
                val next = q.removeFirstOrNull() ?: continue
                if (seen.add(next.key)) fresh += next
            }
        }
        if (fresh.isEmpty()) throw IOException(if (era != null) t("Bu dönemde yeni şarkı bulunamadı. Biraz sonra tekrar dene ya da başka bir tür seç.")
            else t("Bu seçimlerde yeni şarkı kalmadı. Başka bir tür dene."))
        lock.withLock {
            val cur = read()
            val same = cur.selection == selection
            val next = cur.copy(selection = selection, cards = (if (same) cur.cards else emptyList()) + fresh)
            write(next); next
        }
    }

    /** Up to 250 songs of the genre (and era) from the Worker's /api/music-discover, cached there for 12 hours. */
    private suspend fun appleMusic(country: String, genre: MusicGenre, era: MusicEra?): List<MusicCard> {
        val payload = JSONObject().put("storefront", country).put("genre", genre.itunesId).apply { era?.let { put("era", it.key) } }
        val response = com.cagri.real.network.WorkerClient(com.cagri.real.platform.android.auth.AndroidBackend.workerTransport)
            .postJson(DISCOVER_ENDPOINT, payload.toString(), 10_000, 20_000, readErrorBody = false)
        check(response.statusCode in 200..299) { "HTTP ${response.statusCode} from REAL Worker music-discover" }
        return cardsOf(JSONObject(response.body).optJSONArray("songs") ?: return emptyList(), genre.key)
    }

    /** The Worker's song objects (music-discover, music-me) as deck cards. */
    private fun cardsOf(songs: JSONArray, genreKey: String): List<MusicCard> {
        fun text(o: JSONObject, name: String) = o.optString(name).takeIf { it.isNotBlank() && it != "null" }
        return buildList {
            for (i in 0 until songs.length()) {
                val o = songs.optJSONObject(i) ?: continue
                val title = text(o, "title") ?: continue
                val link = text(o, "providerUrl")?.takeIf { it.startsWith("https://") } ?: continue
                val preview = text(o, "previewUrl")?.takeIf { it.startsWith("https://") }
                add(MusicCard(MusicBlock(title = title, artist = o.optString("artist"), album = text(o, "album"),
                    artworkUrl = text(o, "artworkUrl")?.takeIf { it.startsWith("https://") }, provider = "iTunes",
                    providerTrackId = text(o, "providerTrackId"), providerUrl = link, previewUrl = preview,
                    previewDurationMillis = if (preview != null) 30_000L else null, explicit = o.optBoolean("explicit", false)),
                    genreKey, o.optInt("year", 0).takeIf { it > 0 }))
            }
        }
    }

    /** Two artist songs, then one chart song, and so on. */
    private fun weave(seeds: List<MusicCard>, charts: List<MusicCard>): List<MusicCard> {
        val out = ArrayList<MusicCard>(seeds.size + charts.size)
        val seen = HashSet<String>()
        var s = 0; var c = 0
        while (s < seeds.size || c < charts.size) {
            repeat(2) { seeds.getOrNull(s++)?.let { if (seen.add(it.key)) out += it } }
            charts.getOrNull(c++)?.let { if (seen.add(it.key)) out += it }
        }
        return out
    }

    /** A few unseen songs (preview first) from six random well-known artists of the genre. */
    private suspend fun seedSongs(country: String, genre: MusicGenre, known: Set<String>,
                                  budget: java.util.concurrent.atomic.AtomicInteger, era: MusicEra?): List<MusicCard> = coroutineScope {
        // Artists from Deezer editors' playlists and the hand-picked list; cached ones first, so a round costs few
        // requests.
        // Three charting now worldwide, one barely known, one from Türkiye, the rest hand-picked.
        val global = runCatching { trendingArtists(genre, era, local = false) }.getOrDefault(emptyList())
        val local = if (era == null || era == MusicEra.Now) runCatching { trendingArtists(genre, era, local = true) }.getOrDefault(emptyList()) else emptyList()
        val hot = global.take(15)
        val fresh = global.drop(15)
        val taken = (global + local).map { it.lowercase() }.toSet()
        val curated = MusicSeeds.artists[genre].orEmpty().filter { it.lowercase() !in taken }
        fun pick(list: List<String>, n: Int, not: List<String> = emptyList()) =
            list.filter { it !in not }.shuffled().sortedBy { if (artistCache(country, it) != null) 0 else 1 }.take(n)
        val fromTrend = pick(hot, 3) + pick(fresh, 1)
        val fromLocal = pick(local.take(10), 1, fromTrend)
        val picked = fromTrend + fromLocal + pick(curated, 6 - fromTrend.size - fromLocal.size)
        picked.map { name -> async {
            val songs = artistCache(country, name) ?: if (budget.getAndDecrement() > 0)
                runCatching { artistSongs(country, name, genre) }.getOrNull()?.also { saveArtist(country, name, it) } else null
            songs.orEmpty().filter { it.key !in known && (era == null || (it.year != null && it.year in era.years)) }
                .shuffled().sortedBy { if (it.music.previewUrl != null) 0 else 1 }.take(3)
        } }.awaitAll().flatten().shuffled()
    }

    /**
     * Artists in Deezer's editor playlists for the genre right now, most tracks first. Refreshed once a day, so a new
     * name that starts charting comes in by itself. Only names are taken; the songs come from Apple.
     */
    private fun trendingArtists(genre: MusicGenre, era: MusicEra?, local: Boolean): List<String> {
        val key = "trend_${genre.key}_${era?.key ?: "all"}_${if (local) "tr" else "world"}"
        runCatching { JSONObject(prefs.getString(key, null) ?: "") }.getOrNull()?.let { j ->
            if (System.currentTimeMillis() - j.optLong("at") < 86_400_000L) {
                val a = j.optJSONArray("artists") ?: JSONArray()
                return (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }
            }
        }
        val counts = LinkedHashMap<String, Int>()
        val lists = LinkedHashMap<String, MutableSet<Long>>()
        val seenPlaylists = HashSet<Long>()
        // Today (every era / 2020+): current playlists. An older era: that decade's playlists ("2000s rap"…).
        val decade = era?.title
        val queries = (if (local) MusicSeeds.deezerLocal else MusicSeeds.deezerGlobal)[genre].orEmpty().let { base ->
            if (decade == null) base else base.take(2).flatMap { q -> era.terms.map { "$it $q" } }
        }
        for (query in queries) {
            val found = runCatching {
                JSONObject(get("https://api.deezer.com/search/playlist?q=${java.net.URLEncoder.encode(query, "UTF-8")}&limit=40"))
                    .optJSONArray("data")
            }.getOrNull() ?: continue
            // Today's playlists only: decade / classics lists and background (lofi, sleep, kids…) lists are skipped.
            val editors = (0 until found.length()).mapNotNull { found.optJSONObject(it) }
                .filter {
                    val title = it.optString("title")
                    it.optJSONObject("user")?.optString("name")?.contains("Deezer", true) == true && !Background.containsMatchIn(title) &&
                        (if (decade == null) !NotCurrent.containsMatchIn(title) else decade.containsMatchIn(title))
                }
                .take(2)
            for (playlist in editors) {
                if (!seenPlaylists.add(playlist.optLong("id"))) continue
                val tracks = runCatching {
                    JSONObject(get("https://api.deezer.com/playlist/${playlist.optLong("id")}/tracks?limit=80")).optJSONArray("data")
                }.getOrNull() ?: continue
                for (i in 0 until tracks.length()) {
                    val name = tracks.optJSONObject(i)?.optJSONObject("artist")?.optString("name")?.trim().orEmpty()
                    if (name.isNotBlank()) {
                        counts[name] = (counts[name] ?: 0) + 1
                        lists.getOrPut(name) { HashSet() } += playlist.optLong("id")
                    }
                }
            }
        }
        // In more playlists first, then more songs, so one long playlist cannot outweigh the rest.
        val artists = counts.keys.sortedWith(compareByDescending<String> { lists[it]?.size ?: 0 }.thenByDescending { counts[it] ?: 0 }).take(60)
        if (artists.isNotEmpty()) prefs.edit().putString(key, JSONObject().put("at", System.currentTimeMillis()).put("artists", JSONArray(artists)).toString()).apply()
        return artists
    }

    private val NotCurrent = Regex("""\d0'?s|19\d\d|20[01]\d|old ?school|classic|klasik|throwback|nostalji|legend|efsane""", RegexOption.IGNORE_CASE)
    private val Background = Regex("""lo-?fi|chill|sleep|study|kids|çocuk|skate""", RegexOption.IGNORE_CASE)

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000; c.readTimeout = 10_000
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    private fun artistCache(country: String, name: String): List<MusicCard>? {
        val raw = prefs.getString("artist_${country}_$name", null) ?: return null
        val j = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (System.currentTimeMillis() - j.optLong("at") > 7 * 86_400_000L || !j.optBoolean("years")) return null
        return decode(j.optJSONArray("songs"))
    }

    private fun saveArtist(country: String, name: String, songs: List<MusicCard>) {
        prefs.edit().putString("artist_${country}_$name",
            JSONObject().put("at", System.currentTimeMillis()).put("years", true).put("songs", encode(songs, 25)).toString()).apply()
    }

    /** The artist's own songs in this country's iTunes catalog (search by artist; songs of other artists are left out). */
    private fun artistSongs(country: String, name: String, genre: MusicGenre): List<MusicCard> {
        val url = "https://itunes.apple.com/search?term=${java.net.URLEncoder.encode(name, "UTF-8")}&entity=song&attribute=artistTerm&limit=25&country=$country"
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000; connection.readTimeout = 10_000
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
            val results = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).optJSONArray("results") ?: return emptyList()
            val wanted = name.lowercase()
            return (0 until results.length()).mapNotNull { i ->
                val o = results.optJSONObject(i) ?: return@mapNotNull null
                val artist = o.optString("artistName")
                if (!artist.lowercase().contains(wanted)) return@mapNotNull null
                val page = o.optString("trackViewUrl").takeIf { it.startsWith("https://") } ?: return@mapNotNull null
                val preview = o.optString("previewUrl").takeIf { it.startsWith("https://") }
                val art = o.optString("artworkUrl100").takeIf { it.startsWith("https://") }?.replace(Regex("/\\d+x\\d+bb\\."), "/600x600bb.")
                MusicCard(MusicBlock(title = o.optString("trackName").take(300), artist = artist.take(200),
                    album = o.optString("collectionName").takeIf { it.isNotBlank() }?.take(200), artworkUrl = art, provider = "iTunes",
                    providerTrackId = o.optLong("trackId").takeIf { it > 0 }?.toString(), providerUrl = page, previewUrl = preview,
                    previewDurationMillis = 30_000L.takeIf { preview != null }), genre.key, o.optString("releaseDate").take(4).toIntOrNull())
            }.distinctBy { it.key }
        } finally { connection.disconnect() }
    }

    private fun interleave(a: List<MusicCard>, b: List<MusicCard>): List<MusicCard> {
        val seen = HashSet<String>()
        val out = ArrayList<MusicCard>(a.size + b.size)
        for (i in 0 until maxOf(a.size, b.size)) {
            a.getOrNull(i)?.let { if (seen.add(it.key)) out += it }
            b.getOrNull(i)?.let { if (seen.add(it.key)) out += it }
        }
        return out
    }

    /**
     * Settings "Müzik önerilerini sıfırla": passed songs can come back and the deck starts over; "Müzik listem" stays
     * (its songs are still not suggested again). Artist / chart caches are refreshed too.
     */
    suspend fun resetSeen() = withContext(Dispatchers.IO) { lock.withLock {
        val s = read()
        write(s.copy(cards = emptyList(), excluded = emptyList()))
        val keys = prefs.all.keys.filter { it.startsWith("artist_") || it.startsWith("trend_") }
        prefs.edit().apply { keys.forEach { remove(it) } }.apply()
    } }

    enum class Action { SAVE, SKIP, KEEP, REMOVE }

    suspend fun act(card: MusicCard, action: Action): Snapshot = withContext(Dispatchers.IO) { com.cagri.real.updates.AppUpdateGate.requireWritable()
        // Apple Music connected: a saved song also goes to the person's library (best effort, the list is the record).
        val trackId = card.music.providerTrackId
        if (action == Action.SAVE && trackId != null && AppleMusicAccount.isConnected(appContext)) {
            libraryScope.launch { runCatching { AppleMusicAccount.addToLibrary(appContext, trackId) } }
        }
        lock.withLock {
        val s = read()
        if (s.cards.any { it.key == card.key } && (action == Action.SAVE || action == Action.SKIP)) {
            swiped.addLast(card to s); if (swiped.size > 10) swiped.removeFirst()
        }
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

    /** Shake: the last swiped song is back on top, and out of the list / passed songs again (only what that swipe did). */
    suspend fun undo(): Snapshot? = withContext(Dispatchers.IO) { lock.withLock {
        val (card, before) = swiped.removeLastOrNull() ?: return@withLock null
        val s = read()
        val next = s.copy(
            cards = listOf(card) + s.cards.filterNot { it.key == card.key },
            list = if (before.list.any { it.key == card.key }) s.list else s.list.filterNot { it.key == card.key },
            excluded = if (card.key in before.excluded) s.excluded else s.excluded.filterNot { it == card.key })
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
            previewDurationMillis = 30_000L.takeIf { preview != null }), genre.key,
            e.optJSONObject("im:releaseDate")?.optString("label")?.take(4)?.toIntOrNull())
    }
}
