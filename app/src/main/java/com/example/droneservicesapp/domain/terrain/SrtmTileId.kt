package com.example.droneservicesapp.domain.terrain

import java.util.Locale
import kotlin.math.floor

/** One 1 x 1 degree SRTM source tile, named from its south-west corner. */
data class SrtmTileId(
    val southLatitude: Int,
    val westLongitude: Int,
) {
    init {
        require(southLatitude in -90..89) { "Invalid SRTM latitude: $southLatitude" }
        require(westLongitude in -180..179) { "Invalid SRTM longitude: $westLongitude" }
    }

    val baseName: String
        get() = String.format(
            Locale.US,
            "%c%02d%c%03d",
            if (southLatitude >= 0) 'N' else 'S',
            kotlin.math.abs(southLatitude),
            if (westLongitude >= 0) 'E' else 'W',
            kotlin.math.abs(westLongitude),
        )

    val downloadUrl: String
        get() = "$SRTM1_BASE_URL/$baseName.hgt.zip"

    companion object {
        const val SRTM1_BASE_URL = "https://terrain.ardupilot.org/SRTM1"

        fun containing(latitude: Double, longitude: Double): SrtmTileId {
            require(latitude >= -90.0 && latitude < 90.0) { "Latitude outside SRTM tile range" }
            require(longitude >= -180.0 && longitude < 180.0) { "Longitude outside SRTM tile range" }
            return SrtmTileId(floor(latitude).toInt(), floor(longitude).toInt())
        }
    }
}
