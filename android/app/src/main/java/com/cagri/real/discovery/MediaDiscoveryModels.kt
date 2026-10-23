package com.cagri.real.discovery

import com.cagri.real.i18n.t
import com.cagri.real.i18n.tIn
import com.cagri.real.data.MediaBlock
import java.text.Normalizer
import java.util.Locale

enum class DiscoveryFilter(val key: String, private val trLabel: String, val genre: Boolean = true, val aliases: List<String> = listOf(key), val themeGroup: String? = null) {
    Action("action", "Aksiyon"), Adventure("adventure", "Macera"), Comedy("comedy", "Komedi"),
    Drama("drama", "Dram"), Romance("romance", "Romantik"),
    ScienceFiction("science-fiction", "Bilim kurgu", aliases = listOf("science fiction", "sci-fi", "science-fiction")),
    Fantasy("fantasy", "Fantastik"), Thriller("thriller", "Gerilim"), Mystery("mystery", "Gizem"),
    Crime("crime", "Suç"), Horror("horror", "Korku"), Animation("animation", "Animasyon"),
    Anime("anime", "Anime"),
    Documentary("documentary", "Belgesel"), Family("family", "Aile"), History("history", "Tarih", aliases = listOf("history", "historical")),
    War("war", "Savaş"), FeelGood("feel-good", "İyi hissettiren", false), MindBending("mind-bending", "Zihin büken", false),
    SlowBurn("slow-burn", "Yavaş gelişen", false), FastPaced("fast-paced", "Tempolu", false),
    Emotional("emotional", "Duygulandıran", false),
    Heartbreaking("heartbreaking", "Hüzünlendiren", false),
    PlotTwist("plot-twist", "Ters köşe yapan", false),
    ThoughtProvoking("thought-provoking", "Düşündüren", false),
    Inspiring("inspiring", "İlham veren", false),
    Relaxing("relaxing", "Rahatlatan", false),
    DarkTone("dark-tone", "Karanlık atmosferli", false),
    Nostalgic("nostalgic", "Nostaljik", false),
    Dystopia("dystopia", "Distopya", false, themeGroup = "Gelecek ve bilim kurgu"),
    PostApocalyptic("post-apocalyptic", "Kıyamet sonrası", false, themeGroup = "Gelecek ve bilim kurgu"),
    TimeTravel("time-travel", "Zaman yolculuğu", false, themeGroup = "Gelecek ve bilim kurgu"),
    TimeLoop("time-loop", "Zaman döngüsü", false, themeGroup = "Gelecek ve bilim kurgu"),
    ParallelUniverse("parallel-universe", "Paralel evren", false, themeGroup = "Gelecek ve bilim kurgu"),
    Space("space", "Uzay", false, themeGroup = "Gelecek ve bilim kurgu"),
    Aliens("aliens", "Uzaylılar", false, themeGroup = "Gelecek ve bilim kurgu"),
    ArtificialIntelligence("artificial-intelligence", "Yapay zekâ", false, themeGroup = "Gelecek ve bilim kurgu"),
    Cyberpunk("cyberpunk", "Siberpunk", false, themeGroup = "Gelecek ve bilim kurgu"),
    Simulation("simulation", "Simülasyon", false, themeGroup = "Gelecek ve bilim kurgu"),
    Survival("survival", "Hayatta kalma", false, themeGroup = "Gizem ve mücadele"),
    Isolation("isolation", "İzolasyon", false, themeGroup = "Gizem ve mücadele"),
    Conspiracy("conspiracy", "Komplo", false, themeGroup = "Gizem ve mücadele"),
    Detective("detective", "Dedektiflik", false, themeGroup = "Gizem ve mücadele"),
    Espionage("espionage", "Casusluk", false, themeGroup = "Gizem ve mücadele"),
    Heist("heist", "Soygun", false, themeGroup = "Gizem ve mücadele"),
    TrueStory("true-story", "Gerçek hikâye", false, themeGroup = "İnsan ve yaşam"),
    ComingOfAge("coming-of-age", "Büyüme hikâyesi", false, themeGroup = "İnsan ve yaşam"),
    Friendship("friendship", "Dostluk", false, themeGroup = "İnsan ve yaşam"),
    FamilyTies("family-ties", "Aile bağları", false, themeGroup = "İnsan ve yaşam"),
    Mythology("mythology", "Mitoloji", false, themeGroup = "Fantastik ve doğaüstü"),
    Supernatural("supernatural", "Doğaüstü", false, themeGroup = "Fantastik ve doğaüstü"),
    Zombies("zombies", "Zombiler", false, themeGroup = "Fantastik ve doğaüstü"),
    Magic("magic", "Büyü", false, themeGroup = "Fantastik ve doğaüstü");
    val label: String get() = tIn("genre", trLabel)
    val selectionGroup: Int get() = when {
        genre -> 0
        themeGroup != null -> 1
        else -> 2
    }
    companion object {
        const val MAX_PER_GROUP = 3
        /** Feelings that only repeated a genre (removed); saved selections read as that genre. */
        val MERGED = mapOf("scary" to "horror", "funny" to "comedy", "suspenseful" to "thriller", "intriguing" to "mystery")
        fun fromKey(key: String) = entries.firstOrNull { it.key == key }
        fun validSelection(keys: Set<String>): Boolean = keys.all { fromKey(it) != null } &&
            keys.groupingBy { fromKey(it)!!.selectionGroup }.eachCount().values.all { it <= MAX_PER_GROUP }
    }
}

/** [aiTitle]: the title the recommender used; the catalog title can differ, so both are remembered as seen. */
data class DiscoveryCard(val media: MediaBlock, val tags: Set<String>, val reason: String, val aiTitle: String = "") {
    val key: String get() = "${media.type}:${media.provider}:${media.providerId ?: media.providerUrl}"
    val seenTitles: List<String> get() = listOf(media.title, aiTitle).filter { it.isNotBlank() }.distinct()
}

/** How many seen titles stay remembered locally; the service accepts the latest [DISCOVERY_EXCLUDE_SENT]. */
const val DISCOVERY_SEEN_LIMIT = 400
const val DISCOVERY_EXCLUDE_SENT = 40

data class DiscoveryMatch(val keys: Set<String>, val inferred: Set<String>)
object DiscoveryRanking {
    private fun normalized(value: String) = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim()
    /** Translate a catalog genre without changing the canonical MediaBlock. */
    fun displayGenre(genre: String?): String? {
        val first = genre.orEmpty().split(',', '/', '|').map { it.trim() }.firstOrNull { it.isNotBlank() } ?: return null
        val n = normalized(first)
        return DiscoveryFilter.entries.firstOrNull { filter ->
            filter.genre && filter.aliases.any { normalized(it) == n }
        }?.label ?: first
    }
    fun match(card: DiscoveryCard, selected: Set<String>): DiscoveryMatch {
        val genres = card.media.genre.orEmpty().split(',', '/', '|').map(::normalized).filter { it.isNotBlank() }
        val matched = mutableSetOf<String>()
        val inferred = mutableSetOf<String>()
        selected.forEach { key ->
            val filter = DiscoveryFilter.fromKey(key) ?: return@forEach
            if (filter.genre && genres.isNotEmpty()) {
                if (filter.aliases.any { normalized(it) in genres }) matched.add(key)
            } else if (key in card.tags) { matched.add(key); inferred.add(key) }
        }
        return DiscoveryMatch(matched, inferred)
    }
    fun rank(cards: List<DiscoveryCard>, selected: Set<String>): List<DiscoveryCard> = cards.distinctBy { it.key }
        .sortedByDescending { match(it, selected).keys.size }
}

/**
 * Swipes: right saves to the watchlist (in the watchlist: keep, next), left passes (in the watchlist: remove).
 * Dock: "REAL'e ekle" makes a card, "İzledim" marks it watched — never suggested again (future rating list).
 */
enum class DiscoveryAction { SKIP, ADD_TO_WATCHLIST, REMOVE_FROM_WATCHLIST, SAVE_DISCOVERY, SAVE_WATCHLIST, BROWSE_WATCHLIST, WATCHED }
data class DiscoveryQueues(val cards: List<DiscoveryCard>, val watchlist: List<DiscoveryCard>, val excluded: List<String>)
object DiscoveryQueueTransitions {
    fun apply(state: DiscoveryQueues, card: DiscoveryCard, action: DiscoveryAction): DiscoveryQueues {
        val remainder = state.cards.filterNot { it.key == card.key }
        fun rotated() = if (state.watchlist.any { it.key == card.key })
            state.watchlist.filterNot { it.key == card.key } + card else state.watchlist
        return when (action) {
            DiscoveryAction.BROWSE_WATCHLIST, DiscoveryAction.SAVE_WATCHLIST -> state.copy(watchlist = rotated())
            DiscoveryAction.REMOVE_FROM_WATCHLIST -> state.copy(watchlist = state.watchlist.filterNot { it.key == card.key })
            DiscoveryAction.WATCHED -> state.copy(cards = remainder, watchlist = state.watchlist.filterNot { it.key == card.key },
                excluded = (state.excluded + card.seenTitles).distinct().takeLast(DISCOVERY_SEEN_LIMIT))
            DiscoveryAction.SKIP, DiscoveryAction.SAVE_DISCOVERY -> state.copy(cards = remainder,
                excluded = (state.excluded + card.seenTitles).distinct().takeLast(DISCOVERY_SEEN_LIMIT))
            DiscoveryAction.ADD_TO_WATCHLIST -> {
                require(state.watchlist.size < 40 || state.watchlist.any { it.key == card.key }) { t("İzlenecek listen dolu. Önce bir afiş çıkar.") }
                state.copy(cards = remainder, watchlist = if (state.watchlist.any { it.key == card.key }) state.watchlist else state.watchlist + card,
                    excluded = (state.excluded + card.seenTitles).distinct().takeLast(DISCOVERY_SEEN_LIMIT))
            }
        }
    }
}
