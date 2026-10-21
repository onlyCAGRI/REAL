package com.cagri.real

import com.cagri.real.discovery.MediaDetailRepository
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MediaDetailParsingTest {
    @Test fun movieCastAllowsMissingPhotoAndDoesNotAcceptUntrustedImageHosts() {
        val detail = MediaDetailRepository.parseMovie(JSONObject("""{"provider":"TheTVDB","providerId":"123","durationMillis":6600000,"cast":[{"id":"1","name":"One","role":"Role","imageUrl":"https://artworks.thetvdb.com/banners/a.jpg"},{"id":"2","name":"Two","imageUrl":"https://bad.test/a.jpg"}]}"""), "123")
        assertEquals(2, detail.cast.size)
        assertEquals(6600000L, detail.durationMillis)
        assertNotNull(detail.cast[0].imageUrl)
        assertNull(detail.cast[1].imageUrl)
    }
    @Test fun durationAndIdentityAreValidatedWithoutChangingCanonicalCardData() {
        val detail = MediaDetailRepository.parseMovie(JSONObject("""{"provider":"TheTVDB","providerId":"123","durationMillis":-1,"cast":[]}"""), "123")
        assertNull(detail.durationMillis)
        assertTrue(detail.cast.isEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            MediaDetailRepository.parseMovie(JSONObject("""{"provider":"TheTVDB","providerId":"456"}"""), "123")
        }
    }
}
