package com.cagri.real.discovery

import android.content.Context
import com.cagri.real.network.WorkerClient
import com.cagri.real.platform.android.auth.AndroidBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** Yerel, kullanıcı başına önbellek. Kart/senkron deposu değildir. */
class GameDiscoveryRepository(context: Context, uid: String) {
    private val prefs = context.applicationContext.getSharedPreferences("real_game_discovery_$uid", Context.MODE_PRIVATE)
    private val lock = Mutex()
    data class Snapshot(val filters: Set<String>, val platforms: Set<String>, val cards: List<GameCard>, val list: List<GameCard>, val excluded: List<String>)

    private fun strings(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) }
    private fun read(): Snapshot {
        val j = runCatching { JSONObject(prefs.getString("snapshot", "{}") ?: "{}") }.getOrElse { JSONObject() }
        return Snapshot(strings(j.optJSONArray("filters")).filter { GameFilter.fromKey(it) != null }.toSet(),
            strings(j.optJSONArray("platforms")).filter { p -> GamePlatformGroup.entries.any { it.key == p } }.toSet(),
            GameCodec.decode(j.optJSONArray("cards")), GameCodec.decode(j.optJSONArray("list")), strings(j.optJSONArray("excluded")))
    }
    private fun write(s: Snapshot) {
        val j = JSONObject().put("filters", JSONArray(s.filters.toList())).put("platforms", JSONArray(s.platforms.toList()))
            .put("cards", GameCodec.encode(s.cards)).put("list", GameCodec.encode(s.list)).put("excluded", JSONArray(s.excluded.takeLast(40)))
        if (!prefs.edit().putString("snapshot", j.toString()).commit()) throw IOException("Öneriler kaydedilemedi.")
    }
    suspend fun load(): Snapshot = withContext(Dispatchers.IO) { lock.withLock { read() } }

    suspend fun fetch(filters: Set<String>, platforms: Set<String>): Snapshot = withContext(Dispatchers.IO) {
        require(GameFilter.validSelection(filters))
        val prior = load()
        val body = JSONObject().put("filters", JSONArray(filters.toList())).put("platforms", JSONArray(platforms.toList()))
            .put("exclude", JSONArray(prior.excluded.takeLast(40))).toString()
        val response = WorkerClient(AndroidBackend.workerTransport).postJson("${AndroidBackend.ORIGIN}/api/game-recommendations", body, 15_000, 40_000, readErrorBody = true)
        if (response.statusCode == 503) throw IOException("Oyun kataloğu şu anda hazır değil.")
        if (response.statusCode !in 200..299) throw IOException(com.cagri.real.ai.aiServiceFailureMessage(
            response.statusCode, runCatching { JSONObject(response.body).optString("error") }.getOrDefault("")))
        val array = JSONObject(response.body).optJSONArray("games") ?: throw IOException("Öneri yanıtı geçersiz.")
        val resolved = (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let(GameCodec::fromWorker) }
        if (resolved.isEmpty()) throw IOException("IGDB'de doğrulanmış bir oyun bulunamadı. Tekrar deneyebilirsin.")
        lock.withLock {
            val cur = read()
            val skip = cur.excluded.toSet() + cur.list.map { it.game.title }
            val next = cur.copy(filters = filters, platforms = platforms,
                cards = resolved.filter { it.game.title !in skip }.distinctBy { it.key }
                    .sortedByDescending { c -> filters.count { it in c.tags } })
            write(next); next
        }
    }

    suspend fun act(card: GameCard, action: GameAction): Snapshot = withContext(Dispatchers.IO) { lock.withLock {
        val s = read()
        val q = GameQueueTransitions.apply(GameQueues(s.cards, s.list, s.excluded), card, action)
        val next = s.copy(cards = q.cards, list = q.list, excluded = q.excluded)
        write(next); next
    } }
}
