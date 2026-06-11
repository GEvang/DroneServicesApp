package com.example.droneservicesapp.domain.geoawareness

data class GeoAltitudeContext(
    val aglMeters: Double? = null,
    val amslMeters: Double? = null
) {
    fun altitudeFor(reference: GeoVerticalReference): Double? {
        return when (reference) {
            GeoVerticalReference.AGL -> aglMeters
            GeoVerticalReference.AMSL -> amslMeters
            GeoVerticalReference.UNKNOWN -> aglMeters ?: amslMeters
        }
    }
}

