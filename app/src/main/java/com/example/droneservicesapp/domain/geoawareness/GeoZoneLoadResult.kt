package com.example.droneservicesapp.domain.geoawareness

data class GeoZoneLoadResult(
    val datasetInfo: GeoZoneDatasetInfo,
    val zones: List<GeoZone>
)
