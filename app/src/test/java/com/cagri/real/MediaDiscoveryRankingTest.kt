package com.cagri.real
import com.cagri.real.data.MediaBlock
import com.cagri.real.data.MediaType
import com.cagri.real.discovery.*
import org.junit.Assert.*
import org.junit.Test
class MediaDiscoveryRankingTest {
    private fun card(title: String, genre: String?, tags: Set<String> = emptySet()) = DiscoveryCard(
        MediaBlock(type = MediaType.TV, providerId = title, title = title, genre = genre, provider = "TVmaze", providerUrl = "https://www.tvmaze.com/shows/$title"), tags, "Öneri")
    @Test fun fivePreferencesRankPartialMatchesWithoutDroppingThem() {
        val selected = setOf("drama", "crime", "romance", "action", "thriller")
        val a = card("a", "Drama, Crime, Thriller"); val b = card("b", "Romance"); val c = card("c", "Comedy")
        assertEquals(listOf(a, b, c), DiscoveryRanking.rank(listOf(c, b, a), selected))
    }
    @Test fun catalogGenreWinsOverConflictingAiTags() {
        val match = DiscoveryRanking.match(card("a", "Comedy", setOf("horror")), setOf("horror"))
        assertTrue(match.keys.isEmpty()); assertTrue(match.inferred.isEmpty())
    }
    @Test fun missingGenreAndToneUseExplicitAiFallback() {
        val match = DiscoveryRanking.match(card("a", null, setOf("drama", "slow-burn")), setOf("drama", "slow-burn"))
        assertEquals(setOf("drama", "slow-burn"), match.keys); assertEquals(match.keys, match.inferred)
    }
    @Test fun scienceFictionAliasAndThirdGenreSurviveRanking() {
        val match = DiscoveryRanking.match(card("a", "Drama, Crime, Science-Fiction"), setOf("science-fiction"))
        assertEquals(setOf("science-fiction"), match.keys); assertTrue(match.inferred.isEmpty())
    }
    @Test fun duplicateProviderRecordsAreCollapsedAndTiesRemainStable() {
        val a = card("a", "Drama"); val b = card("b", "Drama")
        assertEquals(listOf(a,b), DiscoveryRanking.rank(listOf(a,b,a), setOf("drama")))
    }
    @Test fun displayGenreUsesTurkishWithoutChangingUnknownLabels() {
        assertEquals("Aksiyon", DiscoveryRanking.displayGenre("Action, Drama"))
        assertEquals("Bilim kurgu", DiscoveryRanking.displayGenre("Science-Fiction, Mystery"))
        assertEquals("Western", DiscoveryRanking.displayGenre("Western"))
        assertNull(DiscoveryRanking.displayGenre(null))
        assertNull(DiscoveryRanking.displayGenre(" , / "))
    }
    @Test fun themesRemainInferredEvenWhenCatalogGenresExist() {
        val silo = card("Silo", "Drama, Science-Fiction", setOf("dystopia", "intriguing"))
        val match = DiscoveryRanking.match(silo, setOf("science-fiction", "dystopia", "intriguing"))
        assertEquals(setOf("science-fiction", "dystopia", "intriguing"), match.keys)
        assertEquals(setOf("dystopia", "intriguing"), match.inferred)
    }
    @Test fun darkToneDoesNotImplyDystopiaOrTimeTravel() {
        val match = DiscoveryRanking.match(card("a", "Drama", setOf("dark-tone")), setOf("dystopia", "time-travel", "dark-tone"))
        assertEquals(setOf("dark-tone"), match.keys)
    }
    @Test fun expandedFilterKeysAreUniqueAndOldPreferencesRemainAvailable() {
        assertEquals(DiscoveryFilter.entries.size, DiscoveryFilter.entries.map { it.key }.toSet().size)
        listOf("feel-good", "mind-bending", "slow-burn", "fast-paced").forEach { assertNotNull(DiscoveryFilter.fromKey(it)) }
        assertEquals(24, DiscoveryFilter.entries.count { it.themeGroup != null })
        assertEquals(16, DiscoveryFilter.entries.count { !it.genre && it.themeGroup == null })
    }
}
