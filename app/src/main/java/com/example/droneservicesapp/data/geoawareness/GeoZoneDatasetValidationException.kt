package com.example.droneservicesapp.data.geoawareness

import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneValidationResult

class GeoZoneDatasetValidationException(
    val validationResult: GeoZoneValidationResult,
    message: String
) : IllegalArgumentException(message)
