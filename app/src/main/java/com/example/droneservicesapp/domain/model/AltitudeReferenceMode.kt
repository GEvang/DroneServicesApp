package com.example.droneservicesapp.domain.model

enum class AltitudeReferenceMode {
    RELATIVE,
    TERRAIN;

    companion object {
        fun fromStorageValue(value: String?): AltitudeReferenceMode {
            return values().firstOrNull { it.name.equals(value, ignoreCase = true) } ?: RELATIVE
        }
    }
}
