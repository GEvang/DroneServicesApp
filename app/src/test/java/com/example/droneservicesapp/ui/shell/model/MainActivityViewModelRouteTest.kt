package com.example.droneservicesapp.ui.shell.model

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.droneservicesapp.domain.model.AltitudeReferenceMode
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.model.PlanningOperationMode
import com.example.droneservicesapp.domain.terrain.TerrainWaypoint
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainActivityViewModelRouteTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Test
    fun planningDefaultsMatchRequestedSprayAndSurveyValues() {
        val viewModel = MainActivityViewModel()

        assertEquals(5.0, viewModel.lineDistanceProgress.value!!, 0.001)
        assertEquals(0.0, viewModel.flightAltProgress.value!!, 0.001)
        assertEquals(90.0, viewModel.angleProgress.value!!, 0.001)
        assertEquals(5.0, viewModel.flightSpeed.value!!, 0.001)
        assertEquals(75.0, viewModel.sprayerProgress.value!!, 0.001)
        assertEquals(AltitudeReferenceMode.TERRAIN, viewModel.altitudeReferenceMode.value)
        assertEquals(80.0, viewModel.surveyOverlapPercent.value!!, 0.001)
        assertEquals(70.0, viewModel.surveyStripSpacing.value!!, 0.001)
        assertEquals(50.0, viewModel.surveyHeightAboveTerrain.value!!, 0.001)
        assertEquals(90.0, viewModel.surveyGridAngle.value!!, 0.001)
    }

    @Test
    fun addRouteWaypointUsesCurrentMissionDefaults() {
        val viewModel = MainActivityViewModel()
        viewModel.updateAltitude(8)
        viewModel.updateMissionSpeed(2.5)
        viewModel.updateSprayIntensity(55)
        viewModel.setPlanningOperationMode(PlanningOperationMode.SPRAY)

        viewModel.addRouteWaypoint(latitude = 35.1, longitude = 24.2)

        val waypoint = viewModel.routeWaypoints.value!!.single()
        assertEquals(1, waypoint.index)
        assertEquals(35.1, waypoint.latitude, 0.000001)
        assertEquals(24.2, waypoint.longitude, 0.000001)
        assertEquals(8.0, waypoint.altitudeMeters, 0.001)
        assertEquals(2.5, waypoint.speedMetersPerSecond, 0.001)
        assertTrue(waypoint.sprayEnabled)
        assertEquals(55, waypoint.sprayerIntensityPercent)
    }

    @Test
    fun undoLastRouteWaypointRenumbersRemainingRoute() {
        val viewModel = MainActivityViewModel()
        viewModel.addRouteWaypoint(latitude = 35.1, longitude = 24.1)
        viewModel.addRouteWaypoint(latitude = 35.2, longitude = 24.2)
        viewModel.addRouteWaypoint(latitude = 35.3, longitude = 24.3)

        viewModel.undoLastRouteWaypoint()

        val waypoints = viewModel.routeWaypoints.value!!
        assertEquals(2, waypoints.size)
        assertEquals(1, waypoints[0].index)
        assertEquals(2, waypoints[1].index)
        assertEquals(35.2, waypoints[1].latitude, 0.000001)
    }

    @Test
    fun updateSurveyWaypointKeepsTerrainWaypointAligned() {
        val viewModel = MainActivityViewModel()
        viewModel.surveyPath.value = listOf(
            LatLng(35.1, 24.1),
            LatLng(35.2, 24.2)
        )
        viewModel.terrainSurveyWaypoints.value = listOf(
            TerrainWaypoint(LatLon(35.1, 24.1), displayAltitudeMeters = 10.0, missionAltitudeMeters = 15.0),
            TerrainWaypoint(LatLon(35.2, 24.2), displayAltitudeMeters = 11.0, missionAltitudeMeters = 16.0)
        )

        viewModel.updateSurveyWaypoint(
            index = 1,
            point = LatLng(35.25, 24.25),
            terrainWaypoint = TerrainWaypoint(
                latLon = LatLon(35.25, 24.25),
                displayAltitudeMeters = 12.0,
                missionAltitudeMeters = 17.0
            )
        )

        assertEquals(35.25, viewModel.surveyPath.value!![1].latitude, 0.000001)
        assertEquals(17.0, viewModel.terrainSurveyWaypoints.value!![1].missionAltitudeMeters, 0.001)
    }

    @Test
    fun removeSurveyWaypointKeepsTerrainWaypointAligned() {
        val viewModel = MainActivityViewModel()
        viewModel.surveyPath.value = listOf(
            LatLng(35.1, 24.1),
            LatLng(35.2, 24.2),
            LatLng(35.3, 24.3)
        )
        viewModel.terrainSurveyWaypoints.value = listOf(
            TerrainWaypoint(LatLon(35.1, 24.1), displayAltitudeMeters = 10.0, missionAltitudeMeters = 15.0),
            TerrainWaypoint(LatLon(35.2, 24.2), displayAltitudeMeters = 11.0, missionAltitudeMeters = 16.0),
            TerrainWaypoint(LatLon(35.3, 24.3), displayAltitudeMeters = 12.0, missionAltitudeMeters = 17.0)
        )

        viewModel.removeSurveyWaypoint(1)

        assertEquals(2, viewModel.surveyPath.value!!.size)
        assertEquals(35.3, viewModel.surveyPath.value!![1].latitude, 0.000001)
        assertEquals(17.0, viewModel.terrainSurveyWaypoints.value!![1].missionAltitudeMeters, 0.001)
    }

    @Test
    fun surveyHeightAboveTerrainCanBeZero() {
        val viewModel = MainActivityViewModel()

        viewModel.updateSurveyHeightAboveTerrain(0)

        assertEquals(0.0, viewModel.surveyHeightAboveTerrain.value!!, 0.001)
        assertEquals(0, viewModel.surveyGridParams.value!!.heightAboveTerrainMeters)
    }

    @Test
    fun updateSurveyWaypointAltitudeChangesSelectedTerrainWaypointOnly() {
        val viewModel = MainActivityViewModel()
        viewModel.surveyPath.value = listOf(
            LatLng(35.1, 24.1),
            LatLng(35.2, 24.2)
        )
        viewModel.terrainSurveyWaypoints.value = listOf(
            TerrainWaypoint(LatLon(35.1, 24.1), displayAltitudeMeters = 10.0, missionAltitudeMeters = 15.0),
            TerrainWaypoint(LatLon(35.2, 24.2), displayAltitudeMeters = 11.0, missionAltitudeMeters = 16.0)
        )

        viewModel.updateSurveyWaypointAltitude(index = 1, altitudeMeters = 22.0)

        assertEquals(15.0, viewModel.terrainSurveyWaypoints.value!![0].missionAltitudeMeters, 0.001)
        assertEquals(22.0, viewModel.terrainSurveyWaypoints.value!![1].missionAltitudeMeters, 0.001)
        assertEquals(22.0, viewModel.terrainSurveyWaypoints.value!![1].displayAltitudeMeters, 0.001)
    }
}
