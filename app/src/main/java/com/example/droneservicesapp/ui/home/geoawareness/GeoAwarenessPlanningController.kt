package com.example.droneservicesapp.ui.home.geoawareness

import com.example.droneservicesapp.domain.geoawareness.GeoAltitudeContext
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessChecker
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessResult
import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.model.LatLon

/** Builds a planning result without depending on Fragment or ViewModel state. */
class GeoAwarenessPlanningController(
    private val checker: GeoAwarenessChecker = GeoAwarenessChecker(),
) {
    data class Input(
        val missionPolygon: List<LatLon>?,
        val planningPath: List<LatLon>,
        val aglAltitudeMeters: Double?,
        val zones: List<GeoZone>,
    ) {
        val hasGeometry: Boolean
            get() = !missionPolygon.isNullOrEmpty() || planningPath.isNotEmpty()
    }

    fun evaluate(input: Input): GeoAwarenessResult {
        if (!input.hasGeometry || input.zones.isEmpty()) return GeoAwarenessResult.clear()
        return checker.checkMission(
            missionPolygon = input.missionPolygon,
            surveyPath = input.planningPath,
            altitudeContext = GeoAltitudeContext(aglMeters = input.aglAltitudeMeters),
            zones = input.zones,
        )
    }
}
