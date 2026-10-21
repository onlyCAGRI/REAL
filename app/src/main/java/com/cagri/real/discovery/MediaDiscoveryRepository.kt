package com.cagri.real.discovery

import android.content.Context
import com.cagri.real.data.MediaBlock
import com.cagri.real.data.MediaCatalog
import com.cagri.real.data.MediaType
import com.cagri.real.data.MoodSuggestionMediaCandidate
import com.cagri.real.network.WorkerClient
import com.cagri.real.platform.android.auth.AndroidBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.coroutineContext

private const val WATCHED_LIMIT = 2000

/** Local, per-REAL-user cache. Never an address-book, card or sync store. */
class MediaDiscoveryRepository(context: Context, uid: String) {
    private val prefs = context.applicationContext.getSharedPreferences("real_media_discovery_$uid", Context.MODE_PRIVATE)
    private val lock = Mutex()
    data class Snapshot(val type: String, val filters: Set<String>, val cards: List<DiscoveryCard>, val later: List<DiscoveryCard>, val excluded: List<String>,
                        /** "İzledim": kept for the coming rating list; never suggested again. */
                        val watched: List<DiscoveryCard> = emptyList())
    private fun decode(array: JSONArray?): List<DiscoveryCard> = buildList {
        if (array == null) return@buildList
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val block = MediaBlock.decodeArray(item.optJSONArray("media")).firstOrNull() ?: continue
            add(DiscoveryCard(block, strings(item.optJSONArray("tags")).toSet(), item.optString("reason"), item.optString("ai")))
        }
    }
    private fun encode(cards: List<DiscoveryCard>, limit: Int = 40) = JSONArray().apply {
        cards.take(limit).forEach { card -> put(JSONObject().put("media", MediaBlock.encodeArray(listOf(card.media)))
            .put("tags", JSONArray(card.tags.toList())).put("reason", card.reason).put("ai", card.aiTitle)) }
    }
    private fun strings(array: JSONArray?): List<String> = if (array == null) emptyList() else (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    private fun read(): Snapshot {
        val json = runCatching { JSONObject(prefs.getString("snapshot", "{}") ?: "{}") }.getOrElse { JSONObject() }
        return Snapshot(json.optString("type", "BOTH"), strings(json.optJSONArray("filters")).map { DiscoveryFilter.MERGED[it] ?: it }.distinct().filter { DiscoveryFilter.fromKey(it) != null }
            .groupBy { DiscoveryFilter.fromKey(it)!!.selectionGroup }.values
            .flatMap { it.take(DiscoveryFilter.MAX_PER_GROUP) }.toSet(), decode(json.optJSONArray("cards")), decode(json.optJSONArray("later")), strings(json.optJSONArray("excluded")),
            decode(json.optJSONArray("watched")))
    }
    private fun write(s: Snapshot) {
        val json = JSONObject().put("type", s.type).put("filters", JSONArray(s.filters.toList())).put("cards", encode(s.cards))
            .put("later", encode(s.later)).put("excluded", JSONArray(s.excluded.takeLast(DISCOVERY_SEEN_LIMIT)))
            .put("watched", encode(s.watched.takeLast(WATCHED_LIMIT), WATCHED_LIMIT))
        if (!prefs.edit().putString("snapshot", json.toString()).commit()) throw IOException("Öneriler kaydedilemedi.")
    }
    suspend fun load(): Snapshot = withContext(Dispatchers.IO) { lock.withLock { read() } }
    /**
     * Adds fresh posters behind the ones still in the deck. Titles already seen, saved or in the deck are dropped
     * here as well as by the service (which only takes the latest 40), and when a round brings back mostly known
     * titles another round is asked for with those added to the exclude list, so the deck never ends up empty
     * just because the recommender repeated itself.
     */
    /**
     * [deckFilters]: the person's choice the deck belongs to. A widened request ([filters] with fewer keys, when the
     * exact choice found too few titles) adds to that same deck.
     */
    suspend fun fetch(type: String, filters: Set<String>, deckFilters: Set<String> = filters): Snapshot = withContext(Dispatchers.IO) {
        require(type in setOf("BOTH", "MOVIE", "TV") && DiscoveryFilter.validSelection(filters))
        val prior = load()
        val sameDeck = prior.type == type && prior.filters == deckFilters
        val keep = if (sameDeck) prior.cards else emptyList()
        fun norm(t: String) = t.trim().lowercase(java.util.Locale.ROOT)
        // Watched titles are filtered here, in the app (the guarantee); only the latest few go into the request.
        val known = (prior.excluded + prior.later.flatMap { it.seenTitles } + keep.flatMap { it.seenTitles } +
            prior.watched.flatMap { it.seenTitles }).map(::norm).toMutableSet()
        val knownKeys = (prior.later + keep + prior.watched).map { it.key }.toMutableSet()
        val asked = mutableListOf<String>()
        val fresh = mutableListOf<DiscoveryCard>()
        var lastError: Exception? = null
        // Selected genres are required: a title whose catalog genres miss every one of them is left out.
        val genreKeys = filters.filter { DiscoveryFilter.fromKey(it)?.genre == true }.toSet()
        var legacyServer = false
        for (round in 0 until 3) {
            if (fresh.size >= 8) break
            val exclude = (prior.watched.takeLast(10).flatMap { it.seenTitles } + prior.excluded + keep.flatMap { it.seenTitles } + asked)
                .distinct().takeLast(DISCOVERY_EXCLUDE_SENT)
            // A server from before "anime" does not know it; it is then asked without it.
            val sent = if (legacyServer) filters - "anime" else filters
            val body = JSONObject().put("type", type).put("filters", JSONArray(sent.toList())).put("exclude", JSONArray(exclude)).toString()
            var response = WorkerClient(AndroidBackend.workerTransport).postJson(
                "${AndroidBackend.ORIGIN}/api/media-recommendations", body, 15_000, 25_000, readErrorBody = true)
            if (response.statusCode == 400 && !legacyServer && "anime" in filters &&
                runCatching { JSONObject(response.body).optString("error") }.getOrDefault("") == "INVALID_DISCOVERY_FILTERS") {
                legacyServer = true
                val retry = JSONObject().put("type", type).put("filters", JSONArray((filters - "anime").toList())).put("exclude", JSONArray(exclude)).toString()
                response = WorkerClient(AndroidBackend.workerTransport).postJson(
                    "${AndroidBackend.ORIGIN}/api/media-recommendations", retry, 15_000, 25_000, readErrorBody = true)
            }
            if (response.statusCode !in 200..299) {
                val e = IOException(com.cagri.real.ai.aiServiceFailureMessage(
                    response.statusCode, runCatching { JSONObject(response.body).optString("error") }.getOrDefault("")))
                if (fresh.isEmpty() && keep.isEmpty()) throw e
                lastError = e; break
            }
            val array = JSONObject(response.body).optJSONArray("candidates") ?: throw IOException("Öneri yanıtı geçersiz.")
            val items = (0 until array.length()).mapNotNull { array.optJSONObject(it) }
            asked += items.map { it.optString("title").trim() }.filter { it.isNotBlank() }
            val unseen = items.filter { norm(it.optString("title")) !in known }
            val semaphore = Semaphore(4)
            val resolved = coroutineScope {
                unseen.map { item -> async {
                    semaphore.withPermit {
                        val kind = MediaType.fromKey(item.optString("type")) ?: return@withPermit null
                        if (type != "BOTH" && kind.name != type) return@withPermit null
                        val title = item.optString("title").trim().take(180)
                        if (title.isBlank()) return@withPermit null
                        val candidate = MoodSuggestionMediaCandidate(title, item.optInt("year").takeIf { it > 0 }, title)
                        val block = if (kind == MediaType.TV) MediaCatalog.resolveTv(candidate) else MediaCatalog.resolveMovie(candidate)
                        coroutineContext.ensureActive() // Legacy catalog resolvers swallow cancellation; never publish stale work.
                        block?.let { DiscoveryCard(it, strings(item.optJSONArray("tags")).filter { key -> DiscoveryFilter.fromKey(key) != null }.toSet(),
                            item.optString("reason").take(220), title) }
                    }
                } }.awaitAll().filterNotNull()
            }
            for (card in resolved) {
                if (card.key in knownKeys || norm(card.media.title) in known) continue
                knownKeys += card.key
                known += card.seenTitles.map(::norm)
                if (genreKeys.isNotEmpty() && DiscoveryRanking.match(card, genreKeys).keys.isEmpty()) continue
                fresh += card
            }
        }
        // Nothing new also ends an automatic refill, so a repeating recommender can never loop the request.
        if (fresh.isEmpty()) {
            throw lastError ?: IOException("Bu seçimlerle yeni afiş kalmadı. Bir iki seçimi kaldırıp tekrar dene.")
        }
        lock.withLock {
            val current = read()
            val next = current.copy(type = type, filters = deckFilters,
                cards = (if (sameDeck) current.cards else emptyList()) + DiscoveryRanking.rank(fresh, filters))
            write(next); next
        }
    }
    /** Settings "Önerileri sıfırla": passed titles can come back and the deck starts over; the saved list and "İzledim" stay. */
    suspend fun resetSeen() = withContext(Dispatchers.IO) { lock.withLock { val s = read(); write(s.copy(cards = emptyList(), excluded = emptyList())) } }

    suspend fun act(card: DiscoveryCard, action: DiscoveryAction): Snapshot = withContext(Dispatchers.IO) { com.cagri.real.updates.AppUpdateGate.requireWritable(); lock.withLock {
        val s = read()
        val queues = DiscoveryQueueTransitions.apply(DiscoveryQueues(s.cards, s.later, s.excluded), card, action)
        val watched = if (action == DiscoveryAction.WATCHED) s.watched.filterNot { it.key == card.key } + card else s.watched
        val next = s.copy(cards = queues.cards, later = queues.watchlist, excluded = queues.excluded, watched = watched)
        write(next); next
    } }
}
