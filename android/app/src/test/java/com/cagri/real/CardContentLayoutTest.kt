package com.cagri.real

import com.cagri.real.data.*
import com.cagri.real.ui.CardDropZone
import com.cagri.real.ui.applyCardDrop
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CardContentLayoutTest {
    private fun card() = ReminderItem(id = 7, text = "Kart", createdAt = 1000,
        attachments = listOf(ReminderAttachment("a", localPath = "/test/photo-a.jpg", type = AttachmentType.IMAGE), ReminderAttachment("b", localPath = "/test/photo-b.jpg", type = AttachmentType.IMAGE)),
        musicBlocks = listOf(MusicBlock(id = "song", title = "Şarkı", artist = "Sanatçı", provider = "Apple", providerUrl = "https://example.com/song")))

    @Test fun legacySectionOrderExpandsWithoutFixingPhotoToTop() {
        val active = card().cardContentKeys()
        assertEquals(listOf("music:song", "photos:a", "photos:b", "text"),
            resolveCardContentOrder(listOf("music", "photos", "text"), active))
    }
    @Test fun duplicateAndRemovedKeysCannotHideOrDuplicateContent() {
        assertEquals(listOf("photos:b", "text", "photos:a", "music:song"),
            resolveCardContentOrder(listOf("photos:b", "missing", "photos:b"), card().cardContentKeys()))
    }
    @Test fun newContentIsAppendedToEndWithoutTypePriority() {
        val existingOrder = listOf("location", "photos:a", "checklist")
        val activeWithVoice = setOf("location", "photos:a", "checklist", "voice")
        assertEquals(
            listOf("location", "photos:a", "checklist", "voice"),
            resolveCardContentOrder(existingOrder, activeWithVoice)
        )
    }
    @Test fun manuallySortedOrderIsPreservedWhenAddingContent() {
        val manualOrder = listOf("checklist", "location", "photos:a")
        val activeWithVoiceAndMood = setOf("checklist", "location", "photos:a", "voice", "mood")
        assertEquals(
            listOf("checklist", "location", "photos:a", "voice", "mood"),
            resolveCardContentOrder(manualOrder, activeWithVoiceAndMood)
        )
    }
    @Test fun oppositeHalvesShareRowAndSameColumnStartsNextRow() {
        val placements = mapOf("photos:a" to CardBlockPlacement(span = 3), "photos:b" to CardBlockPlacement(span = 3, column = 3))
        assertEquals(listOf(listOf("photos:a", "photos:b"), listOf("text")), cardContentRows(listOf("photos:a", "photos:b", "text"), placements))
        assertEquals(listOf(listOf("photos:a"), listOf("photos:b")), cardContentRows(listOf("photos:a", "photos:b"), placements + ("photos:b" to CardBlockPlacement(span = 3))))
    }
    @Test fun rightHalfMayLeaveIntentionalEmptyLeftSpace() {
        assertEquals(listOf(listOf("photos:a"), listOf("text")), cardContentRows(listOf("photos:a", "text"), mapOf("photos:a" to CardBlockPlacement(span = 3, column = 3, gapBefore = 80))))
    }
    @Test fun dragOrderSupportsBothDirectionsAndStaleTargetsAreNoOps() {
        val keys = listOf("text", "photos:a", "music:song")
        assertEquals(listOf("music:song", "text", "photos:a"), moveCardContent(keys, "music:song", "text"))
        assertEquals(listOf("photos:a", "music:song", "text"), moveCardContent(keys, "text", "music:song"))
        assertEquals(keys, moveCardContent(keys, "text", "deleted"))
    }
    @Test fun geometryBoundsAndUnsupportedTypesAreNormalized() {
        assertEquals(CardBlockPlacement(6, 0, 160, 0), CardBlockPlacement(99, -1, 999, -1).normalized())
        assertEquals(CardBlockPlacement(2, 4, 12, 0), CardBlockPlacement(2, 9).normalized())
        assertEquals(setOf("text"), normalizeCardLayout(mapOf("text" to CardBlockPlacement(), "unknown" to CardBlockPlacement())).keys)
    }
    @Test fun deletingBlockRemovesGeometryAndNewContentHasNoStalePlacement() {
        val old = mapOf("photos:a" to CardBlockPlacement(span = 3, gapBefore = 80))
        assertTrue(cardLayoutForKeys(old, setOf("text", "photos:c")).isEmpty())
    }
    @Test fun editingPresentationDoesNotMutateCanonicalCardData() {
        val source = card()
        val draft = source.copy(contentBlockLayout = mapOf("photos:a" to CardBlockPlacement(3, 3, 40, 300)), contentBlockOrder = listOf("photos:a", "text"))
        assertEquals(source.attachments, draft.attachments)
        assertEquals(source.musicBlocks, draft.musicBlocks)
        assertTrue(source.contentBlockLayout.isEmpty())
        assertEquals(source, draft.copy(contentBlockLayout = emptyMap(), contentBlockOrder = emptyList()))
    }
    @Test fun layoutAndOrderSurviveExistingJsonRoundTrip() {
        val source = card().copy(contentBlockOrder = listOf("photos:b", "text", "photos:a"),
            contentBlockLayout = mapOf("photos:b" to CardBlockPlacement(3, 3, 42, 300)))
        val restored = ReminderJson.decode(ReminderJson.encode(listOf(source))).single()
        assertEquals(source.contentBlockLayout, restored.contentBlockLayout)
        assertEquals(source.contentBlockOrder, restored.contentBlockOrder)
        assertEquals(source.attachments.map { it.id }, restored.attachments.map { it.id })
    }
    @Test fun oldJsonDoesNotRequireNewFieldAndInvalidLayoutCannotLoseCard() {
        val restored = ReminderJson.decode("""[{"id":7,"text":"Eski kart","createdAt":1000,"contentBlockLayout":{"photos:a":null,"text":{"span":99,"gapBefore":-10}}}]""").single()
        assertEquals("Eski kart", restored.text)
        assertEquals(CardBlockPlacement(gapBefore = 0), restored.contentBlockLayout["text"])
        assertFalse(restored.contentBlockLayout.containsKey("photos:a"))
        val withoutLayout = JSONObject(ReminderJson.encode(listOf(card())).removePrefix("[").removeSuffix("]"))
        assertFalse(withoutLayout.has("contentBlockLayout"))
    }
    @Test fun droppingOnSideMakesBlocksShareARow() {
        val keys = listOf("text", "photos:a", "photos:b")
        val (order, layout) = applyCardDrop(keys, emptyMap(), "photos:b", "photos:a", CardDropZone.Right)
        assertEquals(listOf("text", "photos:a", "photos:b"), order)
        assertEquals(listOf(listOf("text"), listOf("photos:a", "photos:b")), cardContentRows(order, layout))
    }
    @Test fun droppingAboveOrBelowKeepsEverythingVisible() {
        val keys = listOf("text", "photos:a", "photos:b")
        val (order, _) = applyCardDrop(keys, emptyMap(), "photos:b", "text", CardDropZone.Before)
        assertEquals(listOf("photos:b", "text", "photos:a"), order)
    }
    @Test fun threeSmallBlocksShareOneRowButNeverFour() {
        val p = mapOf(
            "a" to CardBlockPlacement(2, 0), "b" to CardBlockPlacement(2, 2), "c" to CardBlockPlacement(2, 4), "d" to CardBlockPlacement(2, 0)
        )
        assertEquals(listOf(listOf("a", "b", "c"), listOf("d")), cardContentRows(listOf("a", "b", "c", "d"), p))
    }
    @Test fun droppingBesideAPairMakesThirds() {
        val keys = listOf("text", "photos:a", "photos:b")
        val (o1, l1) = applyCardDrop(keys, emptyMap(), "photos:b", "photos:a", CardDropZone.Right)
        val (o2, l2) = applyCardDrop(o1 + "music:song", l1, "music:song", "photos:a", CardDropZone.Left)
        assertEquals(listOf("text", "music:song", "photos:a", "photos:b"), o2)
        assertEquals(listOf(listOf("text"), listOf("music:song", "photos:a", "photos:b")), cardContentRows(o2, l2))
        assertTrue(l2.filterKeys { it != "text" }.values.all { it.span == 2 })
    }
    @Test fun fullRowFallsBackToAboveOrBelow() {
        val l = mapOf("a" to CardBlockPlacement(2, 0), "b" to CardBlockPlacement(2, 2), "c" to CardBlockPlacement(2, 4))
        val (order, layout) = applyCardDrop(listOf("a", "b", "c", "d"), l, "d", "b", CardDropZone.Right)
        assertEquals(listOf("a", "b", "c", "d"), order)
        assertEquals(4, cardContentRows(order, layout).sumOf { it.size })
        assertEquals(2, cardContentRows(order, layout).size)
    }
    @Test fun legacyTwoColumnJsonMapsToSixUnitGrid() {
        val r = ReminderJson.decode("""[{"id":7,"text":"Eski","createdAt":1000,"contentBlockLayout":{"photos:a":{"span":1,"column":1,"gapBefore":8},"text":{"span":2}}}]""").single()
        assertEquals(CardBlockPlacement(3, 3, 8, 0), r.contentBlockLayout["photos:a"])
        assertEquals(CardBlockPlacement(), r.contentBlockLayout["text"])
    }
}
