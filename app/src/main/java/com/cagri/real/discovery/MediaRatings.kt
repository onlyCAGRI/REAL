package com.cagri.real.discovery

import android.content.Context
import androidx.core.text.HtmlCompat
import com.cagri.real.data.MediaBlock
import com.cagri.real.data.MediaType
import com.cagri.real.network.WorkerClient
import com.cagri.real.platform.android.auth.AndroidBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

/** Same key as [DiscoveryCard.key], so "İzledim" posters and ratings meet. */
fun ratingKey(media: MediaBlock) = "${media.type}:${media.provider}:${media.providerId ?: media.providerUrl}"

/** One episode; [summary] is the short reminder shown while rating. [translated]: name / summary are already Turkish. */
data class SeriesEpisode(val season: Int, val number: Int, val name: String, val summary: String?, val translated: Boolean = false) {
    val key: String get() = "$season:$number"
}

/**
 * A film or series in "Puanlarım". Films carry their own [score]; a series has no score of its own, it is the
 * average of its rated [episodes] ("season:number" → score). [addedAt] null = added from the past ("eskiden").
 */
data class RatingEntry(val key: String, val media: MediaBlock, val score: Double?, val episodes: Map<String, Double>,
                       val addedAt: Long?, val order: Long) {
    val series: Boolean get() = media.type == MediaType.TV
    val value: Double? get() = if (series) episodes.values.takeIf { it.isNotEmpty() }?.let { (it.average() * 10).roundToInt() / 10.0 } else score
}

/**
 * Local, per REAL user. Watched posters ("İzledim" in discovery) come in on [load] as the past ("eskiden"):
 * a suggested title marked watched was usually seen long ago. Removed titles are remembered so a watched poster does not come back.
 */
class MediaRatingRepository(context: Context, private val uid: String) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("real_media_ratings_$uid", Context.MODE_PRIVATE)
    private val lock = Mutex()

    private data class State(val entries: List<RatingEntry>, val removed: Set<String>, val imported: Boolean)

    private fun read(): State {
        val j = runCatching { JSONObject(prefs.getString("state", "{}") ?: "{}") }.getOrElse { JSONObject() }
        val list = j.optJSONArray("entries") ?: JSONArray()
        val entries = (0 until list.length()).mapNotNull { i ->
            val o = list.optJSONObject(i) ?: return@mapNotNull null
            val media = MediaBlock.decodeArray(JSONArray().put(o.optJSONObject("media") ?: return@mapNotNull null)).firstOrNull()
                ?: return@mapNotNull null
            val eps = o.optJSONObject("episodes")
            val map = buildMap { eps?.keys()?.forEach { k -> eps.optDouble(k).takeIf { !it.isNaN() }?.let { put(k, it) } } }
            RatingEntry(o.optString("key").ifBlank { ratingKey(media) }, media,
                o.optDouble("score").takeIf { !it.isNaN() && o.has("score") }, map,
                if (o.has("addedAt")) o.optLong("addedAt") else null, o.optLong("order"))
        }
        val removed = j.optJSONArray("removed")?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() } ?: emptySet()
        return State(entries, removed, j.optBoolean("imported"))
    }

    private fun write(s: State) {
        val list = JSONArray()
        s.entries.forEach { e ->
            val o = JSONObject().put("key", e.key).put("media", MediaBlock.encodeArray(listOf(e.media)).getJSONObject(0)).put("order", e.order)
            e.score?.let { o.put("score", it) }
            e.addedAt?.let { o.put("addedAt", it) }
            if (e.episodes.isNotEmpty()) o.put("episodes", JSONObject().apply { e.episodes.forEach { (k, v) -> put(k, v) } })
            list.put(o)
        }
        val j = JSONObject().put("entries", list).put("removed", JSONArray(s.removed.toList().takeLast(2000))).put("imported", s.imported)
        if (!prefs.edit().putString("state", j.toString()).commit()) throw IOException("Puanlar kaydedilemedi.")
    }

    private fun mutate(block: (State) -> State): List<RatingEntry> { val next = block(read()); write(next); return next.entries }

    /** Read-only snapshot (no "İzledim" import, no write); the Home context card reads this. */
    fun peek(): List<RatingEntry> = read().entries

    suspend fun load(): List<RatingEntry> = withContext(Dispatchers.IO) {
        val watched = runCatching { MediaDiscoveryRepository(app, uid).load().watched }.getOrDefault(emptyList())
        lock.withLock {
            val s = read()
            val known = s.entries.map { it.key }.toSet() + s.removed
            val fresh = watched.filter { it.key !in known }
            if (fresh.isEmpty() && s.imported) return@withLock s.entries
            val now = System.currentTimeMillis()
            val added = fresh.mapIndexed { i, c -> RatingEntry(c.key, c.media, null, emptyMap(), null, now + i) }
            mutate { it.copy(entries = it.entries + added, imported = true) }
        }
    }

    private fun upsert(s: State, media: MediaBlock, past: Boolean, change: (RatingEntry) -> RatingEntry): State {
        val key = ratingKey(media)
        val now = System.currentTimeMillis()
        val current = s.entries.firstOrNull { it.key == key } ?: RatingEntry(key, media, null, emptyMap(), if (past) null else now, now)
        return s.copy(entries = s.entries.filterNot { it.key == key } + change(current), removed = s.removed - key)
    }

    /** Film score; [past] only matters when the film is new to the list (added from "Geçmişten ekle"). */
    suspend fun rate(media: MediaBlock, score: Double, past: Boolean = false) = withContext(Dispatchers.IO) {
        com.cagri.real.updates.AppUpdateGate.requireWritable()
        lock.withLock { mutate { upsert(it, media, past) { e -> e.copy(score = score) } } }
    }

    suspend fun add(media: MediaBlock, past: Boolean) = withContext(Dispatchers.IO) {
        com.cagri.real.updates.AppUpdateGate.requireWritable()
        lock.withLock { mutate { upsert(it, media, past) { e -> e } } }
    }

    /** Episode scores of a series; a null score clears that episode. */
    suspend fun rateEpisodes(media: MediaBlock, scores: Map<String, Double?>) = withContext(Dispatchers.IO) {
        com.cagri.real.updates.AppUpdateGate.requireWritable()
        lock.withLock {
            mutate { upsert(it, media, false) { e ->
                e.copy(episodes = e.episodes.toMutableMap().apply { scores.forEach { (k, v) -> if (v == null) remove(k) else put(k, v) } })
            } }
        }
    }

    suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        lock.withLock { mutate { s -> s.copy(entries = s.entries.filterNot { it.key == key }, removed = s.removed + key) } }
    }

    /** Settings "Puanlarımı sıfırla": empty list; earlier "İzledim" posters do not come back, new ones do. */
    suspend fun reset() = withContext(Dispatchers.IO) {
        val watched = runCatching { MediaDiscoveryRepository(app, uid).load().watched.map { it.key } }.getOrDefault(emptyList())
        lock.withLock {
            val keys = prefs.all.keys.filter { it.startsWith("episodes_") || it.startsWith("tr_") || it.startsWith("film_tr_") }
            prefs.edit().apply { keys.forEach { remove(it) } }.apply()
            write(State(emptyList(), watched.toSet(), imported = true))
        }
    }

    /**
     * Episodes from TVmaze (no key), names and summaries in Turkish from TheTVDB where it has them (through the
     * REAL server, which holds the key). Cached for a week; an older cache is used when offline.
     */
    suspend fun episodes(media: MediaBlock): List<SeriesEpisode> = withContext(Dispatchers.IO) {
        val cacheKey = "episodes_v2_${ratingKey(media)}"
        val cached = runCatching { JSONObject(prefs.getString(cacheKey, null) ?: "") }.getOrNull()
        if (cached != null && System.currentTimeMillis() - cached.optLong("at") < 7 * 86_400_000L) {
            return@withContext decodeEpisodes(cached.optJSONArray("list"))
        }
        val id = media.providerId?.takeIf { media.provider.equals("TVmaze", true) && it.all(Char::isDigit) }
        val list: List<SeriesEpisode>
        val tvdbId: String?
        try {
            val show = if (id != null) JSONObject(get("https://api.tvmaze.com/shows/$id?embed=episodes"))
                else JSONObject(get("https://api.tvmaze.com/singlesearch/shows?q=${URLEncoder.encode(media.title, "UTF-8")}&embed=episodes"))
            list = parseEpisodes(show.optJSONObject("_embedded")?.optJSONArray("episodes") ?: JSONArray())
            tvdbId = show.optJSONObject("externals")?.opt("thetvdb")?.toString()?.takeIf { it.isNotBlank() && it.all(Char::isDigit) && it != "0" }
        } catch (e: IOException) {
            if (cached != null) return@withContext decodeEpisodes(cached.optJSONArray("list"))
            throw IOException("Bölümler alınamadı. Bağlantını kontrol edip tekrar dene.")
        }
        val turkish = tvdbId?.let { runCatching { tvdbTurkish(it) }.getOrNull() }.orEmpty()
        val merged = list.map { ep ->
            val tr = turkish[ep.key] ?: return@map ep
            ep.copy(name = tr.first ?: ep.name, summary = tr.second ?: ep.summary, translated = tr.second != null || ep.summary == null)
        }
        prefs.edit().putString(cacheKey, JSONObject().put("at", System.currentTimeMillis()).put("list", encodeEpisodes(merged)).toString()).apply()
        merged
    }

    /** TheTVDB Turkish episode names / summaries: "season:number" → (name, summary). */
    private suspend fun tvdbTurkish(tvdbId: String): Map<String, Pair<String?, String?>> {
        val response = WorkerClient(AndroidBackend.workerTransport).postJson("${AndroidBackend.ORIGIN}/api/media-episodes",
            JSONObject().put("tvdbId", tvdbId).put("lang", "tur").toString(), 10_000, 20_000, readErrorBody = true)
        if (response.statusCode !in 200..299) return emptyMap()
        val a = JSONObject(response.body).optJSONArray("episodes") ?: return emptyMap()
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            fun str(k: String) = o.optString(k).takeIf { it.isNotBlank() && it != "null" }
            "${o.optInt("season")}:${o.optInt("number")}" to (str("name") to str("overview"))
        }.toMap()
    }

    /**
     * AI translation of one episode's name and summary when TheTVDB has no Turkish; kept on the phone so the same
     * episode is never sent twice. Null when it cannot be translated (the English text stays).
     */
    suspend fun translate(media: MediaBlock, episode: SeriesEpisode): SeriesEpisode? = withContext(Dispatchers.IO) {
        if (episode.translated) return@withContext episode
        val key = "tr_${ratingKey(media)}_${episode.key}"
        prefs.getString(key, null)?.let { saved ->
            runCatching { JSONObject(saved) }.getOrNull()?.let { o ->
                return@withContext episode.copy(name = o.optString("name").ifBlank { episode.name },
                    summary = o.optString("summary").takeIf { it.isNotBlank() } ?: episode.summary, translated = true)
            }
        }
        val texts = listOfNotNull(episode.name, episode.summary?.take(1500))
        val out = aiTranslate(texts) ?: return@withContext null
        val name = out[0].ifBlank { episode.name }
        val summary = if (episode.summary != null) out[1].ifBlank { episode.summary } else null
        prefs.edit().putString(key, JSONObject().put("name", name).put("summary", summary ?: "").toString()).apply()
        episode.copy(name = name, summary = summary, translated = true)
    }

    /**
     * A film's reminder in Turkish: TheTVDB's own Turkish description first (REAL server), otherwise the
     * description is translated by AI once and kept on the phone. Null = no description at all.
     */
    suspend fun filmSummary(media: MediaBlock): String? = withContext(Dispatchers.IO) {
        val key = "film_tr_${ratingKey(media)}"
        prefs.getString(key, null)?.let { return@withContext it.takeIf(String::isNotBlank) }
        var text = media.description?.takeIf { it.isNotBlank() }
        var turkish = false
        val id = media.providerId?.takeIf { media.provider.equals("TheTVDB", true) && media.type == MediaType.MOVIE && it.matches(Regex("[1-9]\\d{0,9}")) }
        // "No description" is kept only when TheTVDB really answered; a network failure is tried again next time.
        var answered = id == null
        if (id != null) runCatching {
            val response = WorkerClient(AndroidBackend.workerTransport).postJson("${AndroidBackend.ORIGIN}/api/media-details",
                JSONObject().put("provider", "TheTVDB").put("type", "MOVIE").put("providerId", id).toString(), 10_000, 15_000, readErrorBody = false)
            if (response.statusCode in 200..299) answered = true
            if (response.statusCode in 200..299) JSONObject(response.body).optJSONObject("details")?.let { d ->
                d.optString("description").takeIf { it.isNotBlank() && it != "null" }?.let { text = it }
                turkish = d.optString("language") == "tur"
            }
        }
        val source = text ?: run { if (answered) prefs.edit().putString(key, "").apply(); return@withContext null }
        val result = if (turkish) source else aiTranslate(listOf(source.take(1500)))?.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return@withContext source // Not saved: a later try may translate it.
        prefs.edit().putString(key, result).apply()
        result
    }

    /** REAL server AI translation into Turkish; null when it is unavailable. */
    private suspend fun aiTranslate(texts: List<String>): List<String>? {
        val response = runCatching {
            WorkerClient(AndroidBackend.workerTransport).postJson("${AndroidBackend.ORIGIN}/api/media-translate",
                JSONObject().put("texts", JSONArray(texts)).put("lang", "tr").toString(), 10_000, 25_000, readErrorBody = true)
        }.getOrNull() ?: return null
        if (response.statusCode !in 200..299) return null
        val out = runCatching { JSONObject(response.body).optJSONArray("translations") }.getOrNull() ?: return null
        if (out.length() != texts.size) return null
        return (0 until out.length()).map { out.optString(it) }
    }

    private fun encodeEpisodes(list: List<SeriesEpisode>) = JSONArray().apply {
        list.forEach { put(JSONObject().put("s", it.season).put("n", it.number).put("name", it.name).put("summary", it.summary ?: "").put("tr", it.translated)) }
    }

    private fun decodeEpisodes(a: JSONArray?): List<SeriesEpisode> = if (a == null) emptyList() else (0 until a.length()).mapNotNull { i ->
        val o = a.optJSONObject(i) ?: return@mapNotNull null
        SeriesEpisode(o.optInt("s"), o.optInt("n"), o.optString("name"), o.optString("summary").takeIf { it.isNotBlank() }, o.optBoolean("tr"))
    }

    private fun parseEpisodes(a: JSONArray): List<SeriesEpisode> = (0 until a.length()).mapNotNull { i ->
        val o = a.optJSONObject(i) ?: return@mapNotNull null
        val season = o.optInt("season", -1).takeIf { it > 0 } ?: return@mapNotNull null
        val number = o.optInt("number", -1).takeIf { it > 0 } ?: return@mapNotNull null // Specials have no number.
        val summary = o.optString("summary").takeIf { it.isNotBlank() && it != "null" }
            ?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim() }?.takeIf { it.isNotBlank() }
        SeriesEpisode(season, number, o.optString("name").takeIf { it.isNotBlank() && it != "null" } ?: "Bölüm $number", summary)
    }.distinctBy { it.key }.sortedWith(compareBy({ it.season }, { it.number }))

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000; c.readTimeout = 15_000
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }
}
