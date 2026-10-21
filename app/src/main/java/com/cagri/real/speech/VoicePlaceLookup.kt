package com.cagri.real.speech

import android.content.Context
import android.location.Geocoder
import com.cagri.real.data.ReminderLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * A place said or typed in a voice command ("When Kitchen, Ordu") -> a card location with its address and map
 * position, found with the phone's own geocoder. When the venue is unknown the city is used (approximate); when
 * nothing is found the place keeps only its name and a Maps search link, as before.
 */
object VoicePlaceLookup {
    suspend fun resolve(context: Context, place: String): ReminderLocation {
        val name = place.trim()
        val base = ReminderLocation(
            placeName = name,
            mapsUrl = "https://www.google.com/maps/search/?api=1&query=" + java.net.URLEncoder.encode(name, Charsets.UTF_8.name())
        )
        if (name.isBlank() || !Geocoder.isPresent()) return base
        return withTimeoutOrNull(3_000L) {
            withContext(Dispatchers.IO) {
                val geocoder = Geocoder(context.applicationContext, Locale.getDefault())
                @Suppress("DEPRECATION")
                fun first(query: String) = runCatching { geocoder.getFromLocationName(query, 1)?.firstOrNull() }.getOrNull()
                    ?.takeIf { it.latitude.isFinite() && it.longitude.isFinite() }
                val exact = first(name)
                val match = exact ?: name.substringAfterLast(',', "").trim().takeIf { it.length >= 2 }?.let(::first)
                    ?: return@withContext base
                base.copy(
                    address = runCatching { match.getAddressLine(0) }.getOrNull().orEmpty(),
                    latitude = match.latitude,
                    longitude = match.longitude,
                    approximateCoordinates = exact == null
                )
            }
        } ?: base
    }
}
