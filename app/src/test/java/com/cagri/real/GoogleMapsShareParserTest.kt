package com.cagri.real

import com.cagri.real.data.GoogleMapsShareParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleMapsShareParserTest {
    @Test fun shortLinkKeepsOriginalAndUsesTrustedRedirectCoordinates() {
        val short = "https://maps.app.goo.gl/abcXYZ"
        val result = GoogleMapsShareParser.parse("Starbucks Piazza\nCanik, Samsun\n$short") {
            "https://www.google.com/maps/place/Starbucks+Piazza/data=!3d41.2801!4d36.3202"
        }!!
        assertEquals(short, result.mapsUrl)
        assertEquals("Starbucks Piazza", result.placeName)
        assertEquals("Canik, Samsun", result.address)
        assertEquals(41.2801, result.latitude!!, 0.000001)
        assertEquals(36.3202, result.longitude!!, 0.000001)
    }

    @Test fun unresolvedShortLinkDoesNotInventCoordinates() {
        val result = GoogleMapsShareParser.parse("https://maps.app.goo.gl/abcXYZ") { null }!!
        assertNull(result.latitude)
        assertNull(result.longitude)
        assertEquals("https://maps.app.goo.gl/abcXYZ", result.mapsUrl)
    }

    @Test fun placeCameraIsFlaggedApproximateAndNeverReplacesExactPin() {
        val result = GoogleMapsShareParser.parse("https://www.google.com/maps/place/Cafe/@41.0,36.0,15z")!!
        assertEquals("Cafe", result.placeName)
        assertEquals(41.0, result.latitude!!, 0.000001)
        assertEquals(36.0, result.longitude!!, 0.000001)
        assertEquals(true, result.approximateCoordinates)
        val exact = GoogleMapsShareParser.parse(
            "https://www.google.com/maps/place/Cafe/@40.0,35.0,15z/data=!3d41.2801!4d36.3202"
        )!!
        assertEquals(41.2801, exact.latitude!!, 0.000001)
        assertEquals(false, exact.approximateCoordinates)
    }


    @Test fun mobilePlaceSearchKeepsBusinessNameForKeyFreeGeocoderFallback() {
        val result = GoogleMapsShareParser.parse(
            "https://www.google.com/maps/search/?api=1&query=Vagon%20Cafe&query_place_id=ChIJExample"
        )!!
        assertEquals("Vagon Cafe", result.placeName)
        assertNull(result.latitude)
        assertNull(result.longitude)
    }

    @Test fun additionalCoordinateFormsAreParsed() {
        val placePath = GoogleMapsShareParser.parse(
            "https://www.google.com/maps/place/41.0301,37.5002"
        )!!
        assertEquals(41.0301, placePath.latitude!!, 0.000001)
        assertEquals(37.5002, placePath.longitude!!, 0.000001)

        val viewpoint = GoogleMapsShareParser.parse(
            "https://www.google.com/maps?viewpoint=41.0301%2C37.5002"
        )!!
        assertEquals(41.0301, viewpoint.latitude!!, 0.000001)
        assertEquals(37.5002, viewpoint.longitude!!, 0.000001)
    }

    @Test fun unrelatedAndUnsafeUrlsCannotBecomeLocations() {
        assertNull(GoogleMapsShareParser.parse("https://example.com/maps/place/Private"))
        assertNull(GoogleMapsShareParser.sharedUrl("https://maps.app.goo.gl.evil.example/abc"))
    }
}
