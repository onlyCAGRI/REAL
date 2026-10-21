package com.cagri.real

import com.cagri.real.data.MediaBlock
import com.cagri.real.data.MediaType
import com.cagri.real.discovery.*
import org.junit.Assert.*
import org.junit.Test

class MediaDiscoveryQueueTest {
    private fun card(id: String) = DiscoveryCard(MediaBlock(type = MediaType.MOVIE, provider = "TheTVDB", providerId = id,
        title = id, providerUrl = "https://thetvdb.com/movies/$id"), emptySet(), "")
    @Test fun skippingDiscoveryDoesNotAddOrRemoveWatchlistRecords() {
        val a = card("a"); val b = card("b")
        val out = DiscoveryQueueTransitions.apply(DiscoveryQueues(listOf(a,b), listOf(a), emptyList()), a, DiscoveryAction.SKIP)
        assertEquals(listOf(b), out.cards); assertEquals(listOf(a), out.watchlist)
    }
    @Test fun onlyExplicitWatchlistAddInsertsAndNeverDuplicates() {
        val a = card("a")
        val first = DiscoveryQueueTransitions.apply(DiscoveryQueues(listOf(a), emptyList(), emptyList()), a, DiscoveryAction.ADD_TO_WATCHLIST)
        val retry = DiscoveryQueueTransitions.apply(first, a, DiscoveryAction.ADD_TO_WATCHLIST)
        assertEquals(listOf(a), retry.watchlist); assertTrue(retry.cards.isEmpty())
    }
    @Test fun browsingAndSavingWatchlistRetainContentWhileCloseRemovesOnlySelectedRecord() {
        val a = card("a"); val b = card("b")
        val initial = DiscoveryQueues(emptyList(), listOf(a,b), emptyList())
        assertEquals(listOf(b,a), DiscoveryQueueTransitions.apply(initial,a,DiscoveryAction.BROWSE_WATCHLIST).watchlist)
        assertEquals(listOf(b,a), DiscoveryQueueTransitions.apply(initial,a,DiscoveryAction.SAVE_WATCHLIST).watchlist)
        assertEquals(listOf(b), DiscoveryQueueTransitions.apply(initial,a,DiscoveryAction.REMOVE_FROM_WATCHLIST).watchlist)
    }
    @Test fun fullWatchlistRejectsAnAdditionInsteadOfSilentlyDroppingSavedRecords() {
        val initial = DiscoveryQueues(emptyList(), (1..40).map { card("$it") }, emptyList())
        try { DiscoveryQueueTransitions.apply(initial,card("new"),DiscoveryAction.ADD_TO_WATCHLIST); fail("Expected capacity rejection") }
        catch (_: IllegalArgumentException) { assertEquals(40, initial.watchlist.size) }
    }
}
