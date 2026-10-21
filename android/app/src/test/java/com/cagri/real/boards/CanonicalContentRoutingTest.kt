package com.cagri.real.boards

import com.cagri.real.content.*
import com.cagri.real.data.*
import com.cagri.real.data.boards.BoardRealContent
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CanonicalContentRoutingTest {
    private val insertion = BoardTransform(x = 150f, y = 300f, z = 7)
    private fun request(kind: String = "LOCATION") = ContentRequest(
        ContentDestination.Board("board-A", insertion), "owner", kind, 1000, "request-A")

    @Test fun panoMapsResultDeliversToSameBoardWithoutCardChooser() {
        val restored = ContentRequest.from(request().json().toString())
        val route = ContentRouting.sharedResult(true, restored, 2000, "owner", "LOCATION")
        assertTrue(route is ContentRouteResult.Deliver)
        val destination = (route as ContentRouteResult.Deliver).request.destination as ContentDestination.Board
        assertEquals("board-A", destination.boardId)
        assertEquals(insertion, destination.insertion)
        assertNotEquals(ContentRouteResult.ChooseCard, route)
        val location = checkNotNull(GoogleMapsShareParser.parse("https://www.google.com/maps/place/Cafe/data=!3d41.2801!4d36.3202"))
        val item = PanoItem("location-block", destination.boardId, BoardBlockType.LOCATION, destination.insertion,
            BoardRealContent.locationPayload(location).toString(), "owner", "Name")
        assertEquals(location, BoardRealContent.location(PanoItem.from(item.json()).content()))
    }
    @Test fun normalMapsShareRetainsCardChooser() {
        assertEquals(ContentRouteResult.ChooseCard,
            ContentRouting.sharedResult(false, null, 2000, "owner", "LOCATION"))
    }
    @Test fun mapsStartedFromExistingCardStillDeliversToCard() {
        val req = ContentRequest(ContentDestination.Card(42), "owner", "LOCATION", 1000)
        val route = ContentRouting.sharedResult(true, ContentRequest.from(req.json().toString()), 2000, "owner", "LOCATION")
        assertEquals(ContentDestination.Card(42), (route as ContentRouteResult.Deliver).request.destination)
    }
    @Test fun cancelClearsBoardRequestAndNextIndependentShareUsesCardFlow() {
        val cancelled = ContentRouting.cancel(request(), "request-A")
        assertNull(cancelled)
        assertEquals(ContentRouteResult.ChooseCard, ContentRouting.sharedResult(false, cancelled, 2000, "owner", "LOCATION"))
    }
    @Test fun oldCancelCannotEraseNewRequest() {
        assertEquals(request(), ContentRouting.cancel(request(), "older-request"))
    }
    @Test fun staleAccountMismatchWrongKindAndMalformedRequestsAreDiscarded() {
        for (route in listOf(
            ContentRouting.sharedResult(true, request(), 1001 + ContentRequest.MAX_AGE, "owner", "LOCATION"),
            ContentRouting.sharedResult(true, request(), 999, "owner", "LOCATION"),
            ContentRouting.sharedResult(true, request(), 2000, "other", "LOCATION"),
            ContentRouting.sharedResult(true, request(), 2000, "owner", "MUSIC"),
            ContentRouting.sharedResult(true, null, 2000, "owner", "LOCATION"))) assertEquals(ContentRouteResult.Discard, route)
    }
    @Test fun canonicalMusicMetadataAndPreviewSurviveBoardSerialization() {
        val music = MusicBlock(id="track", title="Song", artist="Artist", album="Album", provider="Apple",
            providerTrackId="123", providerUrl="https://music.apple.com/track", artworkUrl="https://example.com/art",
            previewUrl="https://example.com/preview", previewDurationMillis=30000, createdAt=1000)
        val payload = BoardRealContent.musicPayload(music)
        assertEquals(music, BoardRealContent.music(JSONObject(payload.toString())))
        assertFalse(payload.has("text"))
        assertTrue(request("MUSIC").isCurrent(2000,"owner","MUSIC"))
    }
    @Test fun canonicalMovieAndTvArtworkAndProviderMetadataSurviveBoardSerialization() {
        for (type in MediaType.entries) {
            val media = MediaBlock(id="media", type=type, title="Silo", provider="TVmaze", providerId="456",
                providerUrl="https://www.tvmaze.com/shows/456", artworkUrl="https://example.com/art", year=2023, createdAt=1000)
            assertEquals(media, BoardRealContent.media(JSONObject(BoardRealContent.mediaPayload(media).toString())))
        }
    }
    @Test fun canonicalMoodV2DetailsSurviveBoardSerialization() {
        val details = MoodDetails(MoodScope.MOMENT, listOf("calm"), MoodEnergy.BALANCED, listOf("work"), 1000)
        assertEquals(JournalMood.GOOD to details,
            BoardRealContent.mood(JSONObject(BoardRealContent.moodPayload(JournalMood.GOOD, details).toString())))
    }
    @Test fun canonicalFileAndSnippetKeepRealAttachmentTypes() {
        val file = ReminderAttachment("file", "/owned/file.pdf", AttachmentType.FILE, mimeType="application/pdf", displayName="File", sizeBytes=300)
        val snippet = ReminderAttachment("snippet", type=AttachmentType.TEXT_SNIPPET, text="Metin", source=SnippetSource.WHATSAPP_TEXT)
        for (value in listOf(file, snippet)) assertEquals(value,
            BoardRealContent.attachment(JSONObject(BoardRealContent.attachmentPayload(value).toString())))
    }
    @Test fun nativeVoiceRemainsVoiceAndNeverBecomesFileAttachment() {
        val voice = ReminderVoiceRecording("voice", "/owned/voice.m4a", 12000, 1000)
        val payload = BoardRealContent.voicePayload(voice)
        assertEquals(voice, BoardRealContent.voice(JSONObject(payload.toString())))
        assertFalse(payload.has("attachment"))
        assertEquals(BoardBlockType.VOICE, PanoItem.from(PanoItem("block","board-A",BoardBlockType.VOICE,
            insertion,payload.toString(),"owner","Name").json()).type)
    }
    @Test fun checklistUsesCanonicalRealCompletionAndRetainsWireIds() {
        val original = listOf(ChecklistItem(1, "Milk"), ChecklistItem(2, "Bread", 1000))
        val payload = BoardRealContent.checklistPayload(JSONObject(), original)
        val toggled = BoardRealContent.checklist(payload).map { if(it.id==1L)it.toggleCompleted(2000) else it }
        val result = BoardRealContent.checklist(BoardRealContent.checklistPayload(payload, toggled))
        assertEquals(original.map { it.id }, result.map { it.id })
        assertTrue(result.all { it.isCompleted })
        assertFalse(original[0].isCompleted)
    }
    @Test fun cardReferenceReadsLatestOwnedCardAndNeverMatchesOtherAccountIds() {
        val original = ReminderItem(42,"Before",1000)
        val payload = BoardRealContent.cardSnapshot(original,false)
        val latest = original.copy(text="After")
        assertEquals(latest, BoardRealContent.referencedCard(payload,"owner","owner",listOf(latest)))
        assertNull(BoardRealContent.referencedCard(payload,"owner","other",listOf(latest)))
    }
    @Test fun dragResizeCurrentRotationAndExpansionDoNotMutateOriginalRealCard() {
        val original = ReminderItem(42,"Original",1000,isChecklist=true,checklistItems=listOf(ChecklistItem(1,"Task")))
        val initial = PanoItem("block","board-A",BoardBlockType.CARD,insertion,
            BoardRealContent.cardSnapshot(original,false).toString(),"owner","Name")
        val moved = initial.copy(transform = insertion.copy(x=500f,y=700f,width=450f,height=600f,rotation=12f))
        val expanded = moved.copy(payload=boardContentAfter(moved,JSONObject().put("expanded",true)))
        assertEquals(initial.content().getJSONObject("card").toString(), expanded.content().getJSONObject("card").toString())
        assertEquals("Original", original.text)
        assertFalse(original.checklistItems.single().isCompleted)
        assertEquals(original, BoardRealContent.referencedCard(expanded.content(),"owner","owner",listOf(original)))
    }
}
