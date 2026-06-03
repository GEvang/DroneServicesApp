package com.example.droneservicesapp.ui.shell.model

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.droneservicesapp.domain.model.PlanningOperationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainActivityViewModelRouteTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

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
}
