package com.cagri.real.data

data class ReminderLocation(
    val placeId: String? = null,
    val placeName: String,
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val mapsUrl: String? = null,
    val approximateCoordinates: Boolean = false,
    /** Map zoom the user chose in the card detail (MapLibre 1..19); null = the default framing. */
    val mapZoom: Double? = null
)

/**
 * "Konuma varınca hatırlat": the card notifies once when the phone enters (or, with [onExit], leaves) this circle.
 * After it fires the trigger is removed, and with no trigger left REAL stops watching location altogether.
 */
data class LocationTrigger(
    val placeName: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int = DEFAULT_RADIUS,
    val onExit: Boolean = false
) {
    companion object {
        /** "Normal"; "Hassas" is [PRECISE_RADIUS]. */
        const val DEFAULT_RADIUS = 250
        const val PRECISE_RADIUS = 100
        val RADIUS_CHOICES = listOf(100, 200, 500)
    }
}
