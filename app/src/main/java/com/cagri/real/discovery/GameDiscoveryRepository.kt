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
    data class Snapshot(val filters: Set<String>, val platforms: Set<String>, val cards: List<GameCard>, val list: List<GameCard>, val excluded: List<String>,
                        /** "Oynadım": kept for the coming rating list; never suggested again. */
                        val played: List<GameCard> = emptyList())

    private fun strings(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) }
    private fun read(): Snapshot {
        val j = runCatching { JSONObject(prefs.getString("snapshot", "{}") ?: "{}") }.getOrElse { JSONObject() }
        return Snapshot(strings(j.optJSONArray("filters")).map { if (it == "scary") "horror" else it }.filter { GameFilter.fromKey(it) != null }.toSet(),
            strings(j.optJSONArray("platforms")).filter { p -> GamePlatformGroup.entries.any { it.key == p } }.toSet(),
            GameCodec.decode(j.optJSONArray("cards")), GameCodec.decode(j.optJSONArray("list")), strings(j.optJSONArray("excluded")),
            GameCodec.decode(j.optJSONArray("played")))
    }
    private fun write(s: Snapshot) {
        val j = JSONObject().put("filters", JSONArray(s.filters.toList())).put("platforms", JSONArray(s.platforms.toList()))
            .put("cards", GameCodec.encode(s.cards)).put("list", GameCodec.encode(s.list)).put("excluded", JSONArray(s.excluded.takeLast(40)))
            .put("played", GameCodec.encode(s.played.takeLast(2000), 2000))
        if (!prefs.edit().putString("snapshot", j.toString()).commit()) throw IOException("Öneriler kaydedilemedi.")
    }
    suspend fun load(): Snapshot = withContext(Dispatchers.IO) { lock.withLock { read() } }

    /**
     * [deckFilters] / [deckPlatforms]: the person's choice the deck belongs to; with [append] a widened request (fewer
     * keys, when the exact choice found too few games) adds to the deck instead of replacing it.
     */
    suspend fun fetch(filters: Set<String>, platforms: Set<String>, deckFilters: Set<String> = filters,
                      deckPlatforms: Set<String> = platforms, append: Boolean = false): Snapshot = withContext(Dispatchers.IO) {
        require(GameFilter.validSelection(filters))
        val prior = load()
        fun body(f: Set<String>, p: Set<String>) = JSONObject().put("filters", JSONArray(f.toList())).put("platforms", JSONArray(p.toList()))
            .put("exclude", JSONArray((prior.played.takeLast(10).map { it.game.title } + prior.excluded).distinct().takeLast(40))).toString()
        var response = WorkerClient(AndroidBackend.workerTransport).postJson("${AndroidBackend.ORIGIN}/api/game-recommendations", body(filters, platforms), 15_000, 40_000, readErrorBody = true)
        // A server from before the new genres / platforms rejects them: ask once more in its terms.
        if (response.statusCode == 400) {
            val legacyGenres = setOf("rpg", "strategy", "action", "platformer", "racing", "puzzle", "adventure", "shooter", "fighting",
                "sports", "simulation", "indie", "roguelike", "open-world", "metroidvania", "tactics")
            val legacyFilters = filters.mapNotNull { k -> if (k == "horror") "scary" else k.takeIf { GameFilter.fromKey(it)?.genre == false || it in legacyGenres } }.toSet()
            response = WorkerClient(AndroidBackend.workerTransport).postJson("${AndroidBackend.ORIGIN}/api/game-recommendations",
                body(legacyFilters, GamePlatformGroup.legacy(platforms)), 15_000, 40_000, readErrorBody = true)
        }
        if (response.statusCode == 503) throw IOException("Oyun kataloğu şu anda hazır değil.")
        if (response.statusCode !in 200..299) throw IOException(com.cagri.real.ai.aiServiceFailureMessage(
            response.statusCode, runCatching { JSONObject(response.body).optString("error") }.getOrDefault("")))
        val array = JSONObject(response.body).optJSONArray("games") ?: throw IOException("Öneri yanıtı geçersiz.")
        val resolved = (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let(GameCodec::fromWorker) }
        if (resolved.isEmpty()) throw IOException("IGDB'de doğrulanmış bir oyun bulunamadı. Tekrar deneyebilirsin.")
        lock.withLock {
            val cur = read()
            // Played games are filtered here in the app, by id and by title.
            val skip = cur.excluded.toSet() + cur.list.map { it.game.title } + cur.played.map { it.game.title }
            val playedKeys = cur.played.map { it.key }.toSet()
            val fresh = resolved.filter { it.game.title !in skip && it.key !in playedKeys }.sortedByDescending { c -> deckFilters.count { it in c.tags } }
            val next = cur.copy(filters = deckFilters, platforms = deckPlatforms,
                cards = ((if (append) cur.cards else emptyList()) + fresh).distinctBy { it.key })
            write(next); next
        }
    }

    /** Settings "Önerileri sıfırla": passed titles can come back and the deck starts over; the saved list and "Oynadım" stay. */
    suspend fun resetSeen() = withContext(Dispatchers.IO) { lock.withLock { val s = read(); write(s.copy(cards = emptyList(), excluded = emptyList())) } }

    suspend fun act(card: GameCard, action: GameAction): Snapshot = withContext(Dispatchers.IO) { com.cagri.real.updates.AppUpdateGate.requireWritable(); lock.withLock {
        val s = read()
        val q = GameQueueTransitions.apply(GameQueues(s.cards, s.list, s.excluded), card, action)
        val played = if (action == GameAction.PLAYED) s.played.filterNot { it.key == card.key } + card else s.played
        val next = s.copy(cards = q.cards, list = q.list, excluded = q.excluded, played = played)
        write(next); next
    } }
}
