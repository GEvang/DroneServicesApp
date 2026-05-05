package com.example.droneservicesapp.data.geoawareness

import android.content.Context
import android.util.Log
import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.geoawareness.GeoZoneGeometry
import com.example.droneservicesapp.domain.geoawareness.GeoZoneRestriction
import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneDatasetValidator
import com.example.droneservicesapp.domain.model.LatLon

object GeoZoneValidationDebugProbe {

    private const val TAG = "GeoValidationDebug"

    fun logValidationProbe(context: Context) {
        try {
            val repository = GeoZoneRepository(
                GeoZoneAssetDataSource(context.applicationContext)
            )
            val loadResult = repository.loadDummyRethymnoDataset()

            val dummyValid = loadResult.validationResult.isValid &&
                loadResult.validationResult.errorCount == 0 &&
                loadResult.zones.size == 7

            val blankInvalid = GeoZoneDatasetValidator.validate(
                rawJson = "",
                datasetInfo = null,
                zones = emptyList()
            ).hasErrors

            val missingFeatures = GeoZoneDatasetValidator.validate(
                rawJson = """{"title":"Broken dataset"}""",
                datasetInfo = loadResult.datasetInfo,
                zones = emptyList()
            ).issues.any { it.code == "FEATURES_MISSING" }

            val badCoordinateZone = syntheticZone(
                id = "BAD-COORD",
                geometry = GeoZoneGeometry.Circle(
                    center = LatLon(lat = 95.0, lon = 24.47),
                    radiusMeters = 100.0,
                    lowerLimitMeters = 0.0,
                    upperLimitMeters = 120.0
                )
            )
            val badCoordinate = GeoZoneDatasetValidator.validate(
                rawJson = """{"features":[]}""",
                datasetInfo = loadResult.datasetInfo,
                zones = listOf(badCoordinateZone)
            ).issues.any { it.code == "LATITUDE_OUT_OF_RANGE" || it.code == "LONGITUDE_OUT_OF_RANGE" }

            val duplicateOne = syntheticZone(
                id = "DUPLICATE-ID",
                geometry = GeoZoneGeometry.Circle(
                    center = LatLon(lat = 35.36, lon = 24.47),
                    radiusMeters = 100.0,
                    lowerLimitMeters = 0.0,
                    upperLimitMeters = 120.0
                )
            )
            val duplicateTwo = syntheticZone(
                id = "DUPLICATE-ID",
                geometry = GeoZoneGeometry.Circle(
                    center = LatLon(lat = 35.37, lon = 24.48),
                    radiusMeters = 120.0,
                    lowerLimitMeters = 0.0,
                    upperLimitMeters = 120.0
                )
            )
            val duplicateId = GeoZoneDatasetValidator.validate(
                rawJson = """{"features":[{},{}]}""",
                datasetInfo = loadResult.datasetInfo,
                zones = listOf(duplicateOne, duplicateTwo)
            ).issues.any { it.code == "ZONE_ID_DUPLICATE" }

            Log.d(
                TAG,
                "Validation probe: dummyValid=$dummyValid blankInvalid=$blankInvalid missingFeatures=$missingFeatures badCoordinate=$badCoordinate duplicateId=$duplicateId"
            )
        } catch (error: Exception) {
            Log.e(TAG, "Validation probe failed", error)
        }
    }

    private fun syntheticZone(
        id: String,
        geometry: GeoZoneGeometry
    ): GeoZone {
        return GeoZone(
            id = id,
            country = "GRC",
            name = "Synthetic test zone",
            type = "TEST",
            restriction = GeoZoneRestriction.INFORMATION,
            reason = emptyList(),
            otherReasonInfo = null,
            message = null,
            applicability = emptyList(),
            authorities = emptyList(),
            geometries = listOf(geometry),
            colorHex = null,
            arc = null,
            isDummy = false
        )
    }
}
