package com.cagri.real.boards

import com.cagri.real.data.*
import com.cagri.real.data.boards.BoardRealContent
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BoardRealContentTest {
    @Test fun stackViewReusesCanonicalStackAndPreservesSourceAfterTransform() {
        val stack = CardStackBlock(id = "native-stack", cards = listOf(
            CardStackCard(id = "one", title = "One", imageUri = "/local/photo.jpg"),
            CardStackCard(id = "two", title = "Two")))
        val source = ReminderItem(42, "Card", 100, cardStack = stack)
        val payload = BoardRealContent.cardSnapshot(source, false, stackView = true)
        assertTrue(BoardRealContent.isStackView(payload))
        val original = PanoItem("stack-block", "board", BoardBlockType.CARD, BoardTransform(), payload.toString(), "uid", "Name")
        val moved = original.copy(transform = original.transform.copy(x = 250f, width = 500f, height = 700f, rotation = 12f))
        assertEquals(original.payload, moved.payload)
        val restored = ReminderJson.decode(JSONArray().put(moved.content().getJSONObject("card")).toString()).single()
        assertEquals(stack, restored.cardStack)
        assertEquals(stack, source.cardStack)
        assertFalse(BoardRealContent.isStackView(BoardRealContent.cardSnapshot(source, false)))
    }

    @Test fun existingLocationResultRoundTripsThroughBoardAdapter() {
        val location = ReminderLocation("google-place", "Ordu", "Address", 40.98, 37.87,
            "https://maps.google.com/?q=40.98,37.87", true)
        assertEquals(location, BoardRealContent.location(BoardRealContent.locationPayload(location)))
    }

    @Test fun serverLocationWithoutOptionalLocalMetadataUsesCanonicalModel() {
        val wire = JSONObject().put("placeName", "Ordu").put("address", "")
            .put("latitude", JSONObject.NULL).put("longitude", JSONObject.NULL).put("mapsUrl", "https://maps.app.goo.gl/example")
        val result = BoardRealContent.location(wire)
        assertNull(result.latitude)
        assertNull(result.placeId)
        assertFalse(result.approximateCoordinates)
        assertEquals("https://maps.app.goo.gl/example", result.mapsUrl)
    }

    @Test fun realChecklistModelRetainsIdsTextAndCompletionAcrossBoardSerialization() {
        val entries = listOf(ChecklistItem(100, "A"), ChecklistItem(101, "B", 900L))
        val wire = BoardRealContent.checklistPayload(JSONObject().put("title", "Tasks"), entries)
        val decoded = BoardRealContent.checklist(JSONObject(wire.toString()))
        assertEquals(entries.map { it.id }, decoded.map { it.id })
        assertEquals(entries.map { it.text }, decoded.map { it.text })
        assertEquals(entries.map { it.isCompleted }, decoded.map { it.isCompleted })
        assertEquals("Tasks", wire.getString("title"))
    }

    @Test fun legacyBoardUuidIdentitySurvivesRealChecklistEditToggleAndRemoval() {
        val wire = JSONObject().put("title", "Tasks").put("items", JSONArray()
            .put(JSONObject().put("id", "legacy-uuid-a").put("text", "A").put("done", false))
            .put(JSONObject().put("id", "legacy-uuid-b").put("text", "B").put("done", true)))
        val items = BoardRealContent.checklist(wire)
        val next = BoardRealContent.checklistPayload(wire, listOf(items[1].copy(text = "Edited").toggleCompleted(999)))
        val row = next.getJSONArray("items").getJSONObject(0)
        assertEquals("legacy-uuid-b", row.getString("id"))
        assertEquals("Edited", row.getString("text"))
        assertFalse(row.getBoolean("done"))
        assertEquals("B", wire.getJSONArray("items").getJSONObject(1).getString("text"))
    }

    @Test fun newChecklistEntryDoesNotReplaceAnExistingWireIdentity() {
        val previous = JSONObject().put("items", JSONArray().put(JSONObject()
            .put("id", "old-uuid").put("text", "Old").put("done", false)))
        val old = BoardRealContent.checklist(previous).single()
        val result = BoardRealContent.checklistPayload(previous, listOf(old, ChecklistItem(123, "New")))
        assertEquals("old-uuid", result.getJSONArray("items").getJSONObject(0).getString("id"))
        assertEquals("123", result.getJSONArray("items").getJSONObject(1).getString("id"))
    }

    @Test fun cardReferenceAndCanonicalContentRemainIntactAfterBoardTransform() {
        val source = ReminderItem(42, "Real card", 100, customCategoryId = 77,
            plannedDateEpochDay = 20000, isChecklist = true,
            checklistItems = listOf(ChecklistItem(9, "Task")),
            location = ReminderLocation(placeName = "Place", latitude = 40.98, longitude = 37.87))
        val payload = BoardRealContent.cardSnapshot(source, true)
        val block = PanoItem("block", "board", BoardBlockType.CARD, BoardTransform(), payload.toString(), "uid", "Name")
        val moved = block.copy(transform = block.transform.copy(x = 250f, y = 100f, width = 400f, rotation = 12f, z = 3))
        val restored = PanoItem.from(moved.json())
        assertEquals("42", restored.content().getString("sourceCardKey"))
        assertEquals(block.payload, restored.payload)
        val card = ReminderJson.decode(JSONArray().put(restored.content().getJSONObject("card")).toString()).single()
        assertEquals(source, card)
        assertEquals(77L, source.customCategoryId)
        assertEquals(9L, source.checklistItems.single().id)
        assertEquals(source.location, card.location)
        assertEquals(40.98, source.location!!.latitude!!, 0.00001)
        assertEquals(37.87, source.location!!.longitude!!, 0.00001)
    }

    @Test fun canonicalCardCodecRejectsNameOnlyLocationWithoutMutatingSource() {
        // REAL requires valid coordinates or an HTTPS Maps URL for a persisted location.
        val source = ReminderItem(42, "Card", 100, location = ReminderLocation(placeName = "Name only"))
        val payload = BoardRealContent.cardSnapshot(source, true)
        val decoded = ReminderJson.decode(JSONArray().put(payload.getJSONObject("card")).toString()).single()
        assertNull(decoded.location)
        assertEquals(ReminderLocation(placeName = "Name only"), source.location)
    }

    @Test fun boardCardChecklistInteractionOnlyEditsIndependentSnapshot() {
        val source = ReminderItem(42, "Task", 100, isChecklist = true, checklistItems = listOf(ChecklistItem(9, "Task")))
        val block = PanoItem("block", "board", BoardBlockType.CARD, BoardTransform(),
            BoardRealContent.cardSnapshot(source, false).toString(), "uid", "Name")
        val next = boardContentAfter(block, JSONObject().put("checklistItems", JSONArray()
            .put(JSONObject().put("id", 9).put("done", true))))
        val card = ReminderJson.decode(JSONArray().put(JSONObject(next).getJSONObject("card")).toString()).single()
        assertTrue(card.checklistItems.single().isCompleted)
        assertFalse(source.checklistItems.single().isCompleted)
        assertTrue(block.content().getJSONObject("card").getJSONArray("checklistItems").getJSONObject(0).isNull("completedAt"))
    }

    @Test fun existingRotationContractRemainsCompatibleWithUnchangedWorker() {
        for (angle in listOf(-15f, 0f, 12.75f, 15f)) {
            val transform = BoardTransform(rotation = angle).clamped(BoardBlockType.CARD)
            assertEquals(angle, BoardTransform.from(JSONObject(transform.json().toString())).rotation, 0.0001f)
        }
        assertEquals(15f, BoardTransform(rotation = 90f).clamped(BoardBlockType.CARD).rotation, 0f)
    }
}
