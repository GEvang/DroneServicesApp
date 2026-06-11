package com.example.droneservicesapp.domain.geoawareness

enum class GeoVerticalReference {
    AGL,
    AMSL,
    UNKNOWN;

    companion object {
        fun fromRaw(raw: String?): GeoVerticalReference {
            return when (raw?.trim()?.uppercase()) {
                "AGL" -> AGL
                "AMSL" -> AMSL
                else -> UNKNOWN
            }
        }
    }
}

