package com.cagri.real.discovery

import com.cagri.real.data.MediaBlock
import java.text.Normalizer
import java.util.Locale

enum class DiscoveryFilter(val key: String, val label: String, val genre: Boolean = true, val aliases: List<String> = listOf(key), val themeGroup: String? = null) {
    Action("action", "Aksiyon"), Adventure("adventure", "Macera"), Comedy("comedy", "Komedi"),
    Drama("drama", "Dram"), Romance("romance", "Romantik"),
    ScienceFiction("science-fiction", "Bilim kurgu", aliases = listOf("science fiction", "sci-fi", "science-fiction")),
    Fantasy("fantasy", "Fantastik"), Thriller("thriller", "Gerilim"), Mystery("mystery", "Gizem"),
    Crime("crime", "Suç"), Horror("horror", "Korku"), Animation("animation", "Animasyon"),
    Documentary("documentary", "Belgesel"), Family("family", "Aile"), History("history", "Tarih", aliases = listOf("history", "historical")),
    War("war", "Savaş"), FeelGood("feel-good", "İyi hissettiren", false), MindBending("mind-bending", "Zihin büken", false),
    SlowBurn("slow-burn", "Yavaş gelişen", false), FastPaced("fast-paced", "Tempolu", false),
    Scary("scary", "Korkutan", false),
    Suspenseful("suspenseful", "Gergin tutan", false),
    Emotional("emotional", "Duygulandıran", false),
    Heartbreaking("heartbreaking", "Hüzünlendiren", false),
    Intriguing("intriguing", "Merak uyandıran", false),
    PlotTwist("plot-twist", "Ters köşe yapan", false),
    ThoughtProvoking("thought-provoking", "Düşündüren", false),
    Inspiring("inspiring", "İlham veren", false),
    Funny("funny", "Güldüren", false),
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
    val selectionGroup: Int get() = when {
        genre -> 0
        themeGroup != null -> 1
        else -> 2
    }
    companion object {
        const val MAX_PER_GROUP = 3
        fun fromKey(key: String) = entries.firstOrNull { it.key == key }
        fun validSelection(keys: Set<String>): Boolean = keys.all { fromKey(it) != null } &&
            keys.groupingBy { fromKey(it)!!.selectionGroup }.eachCount().values.all { it <= MAX_PER_GROUP }
    }
}

data class DiscoveryCard(val media: MediaBlock, val tags: Set<String>, val reason: String) {
    val key: String get() = "${media.type}:${media.provider}:${media.providerId ?: media.providerUrl}"
}

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

/** A swipe advances a queue; only explicit dock buttons save or remove content. */
enum class DiscoveryAction { SKIP, ADD_TO_WATCHLIST, REMOVE_FROM_WATCHLIST, SAVE_DISCOVERY, SAVE_WATCHLIST, BROWSE_WATCHLIST }
data class DiscoveryQueues(val cards: List<DiscoveryCard>, val watchlist: List<DiscoveryCard>, val excluded: List<String>)
object DiscoveryQueueTransitions {
    fun apply(state: DiscoveryQueues, card: DiscoveryCard, action: DiscoveryAction): DiscoveryQueues {
        val remainder = state.cards.filterNot { it.key == card.key }
        fun rotated() = if (state.watchlist.any { it.key == card.key })
            state.watchlist.filterNot { it.key == card.key } + card else state.watchlist
        return when (action) {
            DiscoveryAction.BROWSE_WATCHLIST, DiscoveryAction.SAVE_WATCHLIST -> state.copy(watchlist = rotated())
            DiscoveryAction.REMOVE_FROM_WATCHLIST -> state.copy(watchlist = state.watchlist.filterNot { it.key == card.key })
            DiscoveryAction.SKIP, DiscoveryAction.SAVE_DISCOVERY -> state.copy(cards = remainder,
                excluded = (state.excluded + card.media.title).distinct().takeLast(40))
            DiscoveryAction.ADD_TO_WATCHLIST -> {
                require(state.watchlist.size < 40 || state.watchlist.any { it.key == card.key }) { "İzlenecek listen dolu. Önce bir afiş çıkar." }
                state.copy(cards = remainder, watchlist = if (state.watchlist.any { it.key == card.key }) state.watchlist else state.watchlist + card,
                    excluded = (state.excluded + card.media.title).distinct().takeLast(40))
            }
        }
    }
}
