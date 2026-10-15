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
