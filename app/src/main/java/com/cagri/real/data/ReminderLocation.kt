package com.cagri.real.data

data class ReminderLocation(
    val placeId: String? = null,
    val placeName: String,
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val mapsUrl: String? = null,
    val approximateCoordinates: Boolean = false
)
