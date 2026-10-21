package com.cagri.real

import com.cagri.real.data.*
import org.junit.Assert.*
import org.junit.Test

class CatalogCardSizeTest {
    @Test fun widthLadderMatchesFullNormalTitleAndCoverStages() {
        assertEquals(CatalogCardDisplay.FULL, catalogCardDisplay(6))
        assertEquals(CatalogCardDisplay.FULL, catalogCardDisplay(4))
        assertEquals(CatalogCardDisplay.COVER_TITLE, catalogCardDisplay(3))
        assertEquals(CatalogCardDisplay.COVER_ONLY, catalogCardDisplay(2))
    }
    @Test fun rightHandleUsesSameCatalogStepsAsPinch() {
        assertEquals(6, catalogCardWidthStep(6))
        assertEquals(4, catalogCardWidthStep(5))
        assertEquals(4, catalogCardWidthStep(4))
        assertEquals(3, catalogCardWidthStep(3))
        assertEquals(2, catalogCardWidthStep(2))
    }
    @Test fun oneVerticalGridStepHidesActionsAndGrowthRestoresThem() {
        assertTrue(catalogActionsHiddenAfterResize(false, -8f))
        assertFalse(catalogActionsHiddenAfterResize(true, 8f))
        assertFalse(catalogActionsHiddenAfterResize(false, -4f))
        assertTrue(catalogActionsHiddenAfterResize(true, 4f))
        assertTrue(catalogActionsHiddenAfterResize(true, Float.NaN))
    }
    @Test fun catalogPresentationSurvivesSaveWithoutMutatingMetadataOrUrls() {
        val source = ReminderItem(id = 1, text = "Card", createdAt = 1000,
            musicBlocks = listOf(MusicBlock(id = "song", title = "Song", artist = "Artist", provider = "Apple", providerUrl = "https://example.com/song")),
            mediaBlocks = listOf(
                MediaBlock(id = "film", type = MediaType.MOVIE, title = "Film", providerUrl = "https://example.com/film"),
                MediaBlock(id = "tv", type = MediaType.TV, title = "TV", providerUrl = "https://example.com/tv")))
        val draft = source.copy(contentBlockLayout = mapOf(
            "music:song" to CardBlockPlacement(span = 4, minHeight = 180, hideActions = true),
            "media:film" to CardBlockPlacement(span = 3), "media:tv" to CardBlockPlacement(span = 2)))
        val restored = ReminderJson.decode(ReminderJson.encode(listOf(draft))).single()
        assertEquals(draft.contentBlockLayout, restored.contentBlockLayout)
        assertEquals(source.musicBlocks, restored.musicBlocks)
        assertEquals(source.mediaBlocks, restored.mediaBlocks)
        assertTrue(source.contentBlockLayout.isEmpty())
    }
    @Test fun legacyCardsKeepActionsAndDefaultFullPresentation() {
        val source = ReminderJson.decode("""[{"id":1,"text":"Old","createdAt":1000,"contentBlockLayout":{"music:song":{"span":2}}}]""").single()
        val placement = source.contentBlockLayout.getValue("music:song")
        assertFalse(placement.hideActions)
        assertEquals(CatalogCardDisplay.FULL, catalogCardDisplay(placement.span))
    }
}
