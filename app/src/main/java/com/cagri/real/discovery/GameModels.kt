package com.cagri.real.discovery

import org.json.JSONArray
import org.json.JSONObject

/** Tür ve hissiyat anahtarları Worker (worker/games/recommendations.mjs) ile birebir aynıdır. */
enum class GameFilter(val key: String, val label: String, val genre: Boolean) {
    Rpg("rpg", "RPG", true), Strategy("strategy", "Strateji", true), Action("action", "Aksiyon", true),
    Platformer("platformer", "Platform", true), Racing("racing", "Yarış", true), Puzzle("puzzle", "Bulmaca", true),
    Adventure("adventure", "Macera", true), Shooter("shooter", "Nişancı", true), Fighting("fighting", "Dövüş", true),
    Sports("sports", "Spor", true), Simulation("simulation", "Simülasyon", true), Indie("indie", "Bağımsız", true),
    Roguelike("roguelike", "Roguelike", true), OpenWorld("open-world", "Açık dünya", true),
    Metroidvania("metroidvania", "Metroidvania", true), Tactics("tactics", "Taktik", true),
    Relaxing("relaxing", "Rahatlatan", false), Challenging("challenging", "Zorlayan", false),
    StoryDriven("story-driven", "Hikâyeye sürükleyen", false), Scary("scary", "Korkutan", false),
    Cozy("cozy", "Sıcacık", false), FastPaced("fast-paced", "Tempolu", false),
    Competitive("competitive", "Rekabetçi", false), Atmospheric("atmospheric", "Atmosferik", false),
    ThoughtProvoking("thought-provoking", "Düşündüren", false), Nostalgic("nostalgic", "Nostaljik", false),
    CoOp("co-op", "Birlikte oynanan", false), ShortSession("short-session", "Kısa seanslık", false);

    companion object {
        const val MAX_PER_GROUP = 3
        fun fromKey(key: String) = entries.firstOrNull { it.key == key }
        fun validSelection(keys: Set<String>): Boolean = keys.all { fromKey(it) != null } &&
            keys.count { fromKey(it)!!.genre } <= MAX_PER_GROUP && keys.count { !fromKey(it)!!.genre } <= MAX_PER_GROUP
    }
}

/** IGDB tür adlarını (İngilizce) kartta Türkçe göster. Bilinmeyen ad olduğu gibi kalır. */
object GameGenres {
    private val tr = mapOf(
        "role-playing (rpg)" to "RPG", "strategy" to "Strateji", "shooter" to "Nişancı", "platform" to "Platform",
        "racing" to "Yarış", "puzzle" to "Bulmaca", "adventure" to "Macera", "fighting" to "Dövüş", "sport" to "Spor",
        "simulator" to "Simülasyon", "indie" to "Bağımsız", "turn-based strategy (tbs)" to "Sıra tabanlı strateji",
        "real time strategy (rts)" to "Gerçek zamanlı strateji", "tactical" to "Taktik", "hack and slash/beat 'em up" to "Aksiyon dövüş",
        "arcade" to "Arcade", "visual novel" to "Görsel roman", "card & board game" to "Kart ve masa oyunu",
        "point-and-click" to "Point-and-click", "music" to "Müzik", "quiz/trivia" to "Bilgi yarışması", "moba" to "MOBA", "pinball" to "Langırt")
    fun label(name: String): String = tr[name.trim().lowercase()] ?: name
}

enum class GamePlatformGroup(val key: String, val label: String) {
    Pc("pc", "PC"), Current("current", "Güncel konsollar"), Retro("retro", "Retro cihazlar")
}

data class GameRelease(val platform: String, val year: Int?)

/** IGDB ile doğrulanmış oyun. REAL kartı değildir; yalnızca keşif / "Oynanacak listem" için yerel kayıt. */
data class GameBlock(
    val providerId: String, val title: String, val year: Int?, val kind: String,
    val description: String?, val artworkUrl: String?, val genres: List<String>,
    val platforms: List<String>, val releases: List<GameRelease>, val providerUrl: String,
)

data class GameCard(val game: GameBlock, val tags: Set<String>, val reason: String) {
    val key: String get() = "IGDB:${game.providerId}"
}

object GameCodec {
    fun encode(cards: List<GameCard>) = JSONArray().apply {
        cards.take(40).forEach { c ->
            val g = c.game
            put(JSONObject().put("id", g.providerId).put("title", g.title).put("year", g.year ?: JSONObject.NULL).put("kind", g.kind)
                .put("description", g.description ?: JSONObject.NULL).put("artworkUrl", g.artworkUrl ?: JSONObject.NULL)
                .put("genres", JSONArray(g.genres)).put("platforms", JSONArray(g.platforms)).put("url", g.providerUrl)
                .put("releases", JSONArray().apply { g.releases.forEach { put(JSONObject().put("p", it.platform).put("y", it.year ?: JSONObject.NULL)) } })
                .put("tags", JSONArray(c.tags.toList())).put("reason", c.reason))
        }
    }

    private fun strings(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) }
    private fun opt(o: JSONObject, k: String): String? = if (o.isNull(k)) null else o.optString(k).trim().takeIf { it.isNotBlank() }

    fun decode(array: JSONArray?): List<GameCard> = buildList {
        if (array == null) return@buildList
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val id = opt(o, "id") ?: continue
            val title = opt(o, "title") ?: continue
            val url = opt(o, "url") ?: continue
            val releases = o.optJSONArray("releases")?.let { r -> (0 until r.length()).mapNotNull { j ->
                r.optJSONObject(j)?.let { GameRelease(it.optString("p"), if (it.isNull("y")) null else it.optInt("y").takeIf { y -> y > 0 }) } } } ?: emptyList()
            add(GameCard(GameBlock(id, title, if (o.isNull("year")) null else o.optInt("year").takeIf { it > 0 }, o.optString("kind", "main"),
                opt(o, "description"), opt(o, "artworkUrl"), strings(o.optJSONArray("genres")), strings(o.optJSONArray("platforms")), releases, url),
                strings(o.optJSONArray("tags")).toSet(), o.optString("reason")))
        }
    }

    /** Worker `games[]` satırını karta çevirir. */
    fun fromWorker(o: JSONObject): GameCard? {
        val card = decode(JSONArray().put(JSONObject().put("id", o.optString("providerId")).put("title", o.optString("title"))
            .put("year", if (o.isNull("year")) JSONObject.NULL else o.optInt("year")).put("kind", o.optString("kind", "main"))
            .put("description", o.opt("description") ?: JSONObject.NULL).put("artworkUrl", o.opt("artworkUrl") ?: JSONObject.NULL)
            .put("genres", o.optJSONArray("genres") ?: JSONArray())
            .put("platforms", JSONArray(o.optJSONArray("platforms")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("short") } } ?: emptyList<String>()))
            .put("url", o.optString("providerUrl")).put("releases", JSONArray(o.optJSONArray("releases")?.let { a -> (0 until a.length()).mapNotNull { i ->
                a.optJSONObject(i)?.let { JSONObject().put("p", it.optString("platform")).put("y", it.opt("year") ?: JSONObject.NULL) } } } ?: emptyList<JSONObject>()))
            .put("tags", o.optJSONArray("tags") ?: JSONArray()).put("reason", o.optString("reason")))).firstOrNull() ?: return null
        return card.copy(tags = card.tags.filter { GameFilter.fromKey(it) != null }.toSet())
    }
}

/** Kaydırma yalnızca desteyi ilerletir; ekleme/çıkarma yalnızca açık butonlarla olur (film ekranıyla aynı kural). */
enum class GameAction { SKIP, ADD_TO_LIST, REMOVE_FROM_LIST, BROWSE_LIST }
data class GameQueues(val cards: List<GameCard>, val list: List<GameCard>, val excluded: List<String>)
object GameQueueTransitions {
    fun apply(s: GameQueues, card: GameCard, action: GameAction): GameQueues {
        val rest = s.cards.filterNot { it.key == card.key }
        val excluded = (s.excluded + card.game.title).distinct().takeLast(40)
        return when (action) {
            GameAction.BROWSE_LIST -> s.copy(list = if (s.list.any { it.key == card.key }) s.list.filterNot { it.key == card.key } + card else s.list)
            GameAction.REMOVE_FROM_LIST -> s.copy(list = s.list.filterNot { it.key == card.key })
            GameAction.SKIP -> s.copy(cards = rest, excluded = excluded)
            GameAction.ADD_TO_LIST -> {
                require(s.list.size < 40 || s.list.any { it.key == card.key }) { "Oynanacak listen dolu. Önce bir oyun çıkar." }
                s.copy(cards = rest, list = if (s.list.any { it.key == card.key }) s.list else s.list + card, excluded = excluded)
            }
        }
    }
}
