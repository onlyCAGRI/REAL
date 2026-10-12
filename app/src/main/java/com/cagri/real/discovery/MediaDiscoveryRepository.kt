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

/** Local, per-REAL-user cache. Never an address-book, card or sync store. */
class MediaDiscoveryRepository(context: Context, uid: String) {
    private val prefs = context.applicationContext.getSharedPreferences("real_media_discovery_$uid", Context.MODE_PRIVATE)
    private val lock = Mutex()
    data class Snapshot(val type: String, val filters: Set<String>, val cards: List<DiscoveryCard>, val later: List<DiscoveryCard>, val excluded: List<String>)
    private fun decode(array: JSONArray?): List<DiscoveryCard> = buildList {
        if (array == null) return@buildList
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val block = MediaBlock.decodeArray(item.optJSONArray("media")).firstOrNull() ?: continue
            add(DiscoveryCard(block, strings(item.optJSONArray("tags")).toSet(), item.optString("reason")))
        }
    }
    private fun encode(cards: List<DiscoveryCard>) = JSONArray().apply {
        cards.take(40).forEach { card -> put(JSONObject().put("media", MediaBlock.encodeArray(listOf(card.media)))
            .put("tags", JSONArray(card.tags.toList())).put("reason", card.reason)) }
    }
    private fun strings(array: JSONArray?): List<String> = if (array == null) emptyList() else (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    private fun read(): Snapshot {
        val json = runCatching { JSONObject(prefs.getString("snapshot", "{}") ?: "{}") }.getOrElse { JSONObject() }
        return Snapshot(json.optString("type", "BOTH"), strings(json.optJSONArray("filters")).distinct().filter { DiscoveryFilter.fromKey(it) != null }
            .groupBy { DiscoveryFilter.fromKey(it)!!.selectionGroup }.values
            .flatMap { it.take(DiscoveryFilter.MAX_PER_GROUP) }.toSet(), decode(json.optJSONArray("cards")), decode(json.optJSONArray("later")), strings(json.optJSONArray("excluded")))
    }
    private fun write(s: Snapshot) {
        val json = JSONObject().put("type", s.type).put("filters", JSONArray(s.filters.toList())).put("cards", encode(s.cards))
            .put("later", encode(s.later)).put("excluded", JSONArray(s.excluded.takeLast(40)))
        if (!prefs.edit().putString("snapshot", json.toString()).commit()) throw IOException("Öneriler kaydedilemedi.")
    }
    suspend fun load(): Snapshot = withContext(Dispatchers.IO) { lock.withLock { read() } }
    suspend fun fetch(type: String, filters: Set<String>): Snapshot = withContext(Dispatchers.IO) {
        require(type in setOf("BOTH", "MOVIE", "TV") && DiscoveryFilter.validSelection(filters))
        val prior = load()
        val body = JSONObject().put("type", type).put("filters", JSONArray(filters.toList())).put("exclude", JSONArray(prior.excluded.takeLast(40))).toString()
        val response = WorkerClient(AndroidBackend.workerTransport).postJson(
            "${AndroidBackend.ORIGIN}/api/media-recommendations", body, 15_000, 25_000, readErrorBody = true)
        if (response.statusCode !in 200..299) throw IOException(com.cagri.real.ai.aiServiceFailureMessage(
            response.statusCode, runCatching { JSONObject(response.body).optString("error") }.getOrDefault("")))
        val array = JSONObject(response.body).optJSONArray("candidates") ?: throw IOException("Öneri yanıtı geçersiz.")
        val semaphore = Semaphore(3)
        val resolved = coroutineScope {
            (0 until minOf(array.length(), 12)).map { i -> async {
                semaphore.withPermit {
                    val item = array.optJSONObject(i) ?: return@withPermit null
                    val kind = MediaType.fromKey(item.optString("type")) ?: return@withPermit null
                    if (type != "BOTH" && kind.name != type) return@withPermit null
                    val title = item.optString("title").trim().take(180)
                    if (title.isBlank()) return@withPermit null
                    val candidate = MoodSuggestionMediaCandidate(title, item.optInt("year").takeIf { it > 0 }, title)
                    val block = if (kind == MediaType.TV) MediaCatalog.resolveTv(candidate) else MediaCatalog.resolveMovie(candidate)
                    coroutineContext.ensureActive() // Legacy catalog resolvers swallow cancellation; never publish stale work.
                    block?.let { DiscoveryCard(it, strings(item.optJSONArray("tags")).filter { key -> DiscoveryFilter.fromKey(key) != null }.toSet(), item.optString("reason").take(220)) }
                }
            } }.awaitAll().filterNotNull()
        }
        if (resolved.isEmpty()) throw IOException("Kataloglarda doğrulanmış bir sonuç bulunamadı. Tekrar deneyebilirsin.")
        lock.withLock {
            val current = read()
            val excluded = current.excluded.toSet()
            val laterKeys = current.later.map { it.key }.toSet()
            val ranked = DiscoveryRanking.rank(resolved.filter { it.media.title !in excluded && it.key !in laterKeys }, filters)
            val next = current.copy(type = type, filters = filters, cards = ranked)
            write(next); next
        }
    }
    suspend fun act(card: DiscoveryCard, action: DiscoveryAction): Snapshot = withContext(Dispatchers.IO) { lock.withLock {
        val s = read()
        val queues = DiscoveryQueueTransitions.apply(DiscoveryQueues(s.cards, s.later, s.excluded), card, action)
        val next = s.copy(cards = queues.cards, later = queues.watchlist, excluded = queues.excluded)
        write(next); next
    } }
}
