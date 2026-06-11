package com.example.droneservicesapp.domain.geoawareness

enum class GeoAltitudeUnit {
    M,
    FT,
    UNKNOWN;

    fun toMeters(value: Double?): Double? {
        return when {
            value == null -> null
            this == FT -> value * FEET_TO_METERS
            else -> value
        }
    }

    companion object {
        private const val FEET_TO_METERS = 0.3048

        fun fromRaw(raw: String?): GeoAltitudeUnit {
            return when (raw?.trim()?.uppercase()) {
                "M", "METER", "METERS", "METRE", "METRES" -> M
                "FT", "FEET" -> FT
                else -> UNKNOWN
            }
        }
    }
}

