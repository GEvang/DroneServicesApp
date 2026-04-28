package com.example.droneservicesapp.domain.geoawareness

enum class GeoZoneRestriction {
    PROHIBITED,
    REQ_AUTHORISATION,
    CONDITIONAL,
    INFORMATION,
    UNKNOWN;

    companion object {
        fun fromRaw(raw: String?): GeoZoneRestriction {
            return when (raw?.trim()?.uppercase()) {
                "PROHIBITED" -> PROHIBITED
                "REQ_AUTHORISATION", "REQ_AUTHORIZATION" -> REQ_AUTHORISATION
                "CONDITIONAL" -> CONDITIONAL
                "INFORMATION" -> INFORMATION
                else -> UNKNOWN
            }
        }
    }
}
