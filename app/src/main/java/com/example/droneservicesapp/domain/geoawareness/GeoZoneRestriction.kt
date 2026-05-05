package com.example.droneservicesapp.domain.geoawareness

enum class GeoZoneRestriction {
    PROHIBITED,
    REQ_AUTHORISATION,
    CONDITIONAL,
    INFORMATION,
    UNKNOWN;

    companion object {
        fun fromRaw(raw: String?): GeoZoneRestriction {
            return when (normalize(raw)) {
                "PROHIBITED" -> PROHIBITED
                "REQ_AUTHORISATION",
                "REQ_AUTHORIZATION",
                "AUTHORISATION",
                "AUTHORIZATION",
                "AUTHORISATION_REQUIRED",
                "AUTHORIZATION_REQUIRED" -> REQ_AUTHORISATION
                "CONDITIONAL" -> CONDITIONAL
                "INFORMATION",
                "NO_RESTRICTION" -> INFORMATION
                else -> UNKNOWN
            }
        }

        private fun normalize(raw: String?): String? {
            return raw
                ?.trim()
                ?.uppercase()
                ?.replace('-', '_')
                ?.replace(' ', '_')
                ?.takeIf { it.isNotEmpty() }
        }
    }
}
