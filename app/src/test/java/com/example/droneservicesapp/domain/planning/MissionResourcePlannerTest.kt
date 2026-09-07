package com.example.droneservicesapp.domain.planning

import com.example.droneservicesapp.domain.model.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionResourcePlannerTest {
    @Test
    fun oneShortMissionUsesOneBatteryAndNoRefills() {
        val plan = MissionResourcePlanner.plan(
            path = listOf(LatLon(35.0, 25.0), LatLon(35.001, 25.0)),
            home = LatLon(35.0, 25.0),
            speedMetersPerSecond = 5.0,
            sprayRateLitersPerMinute = 5.0,
        )

        assertEquals(1, plan.batteryCount)
        assertTrue(plan.batteryReturnPoints.isEmpty())
        assertEquals(0, plan.tankRefillCount)
    }

    @Test
    fun longMissionPlacesBatteryReturnPoints() {
        val plan = MissionResourcePlanner.plan(
            path = listOf(LatLon(35.0, 25.0), LatLon(35.05, 25.0)),
            home = LatLon(35.0, 25.0),
            speedMetersPerSecond = 5.0,
            usableBatteryMinutes = 20.0,
        )

        assertTrue(plan.batteryCount > 1)
        assertEquals(plan.batteryCount - 1, plan.batteryReturnPoints.size)
    }

    @Test
    fun sprayConsumptionCreatesRefillPointsFromRateAndPathTime() {
        val plan = MissionResourcePlanner.plan(
            path = listOf(LatLon(35.0, 25.0), LatLon(35.01, 25.0)),
            home = LatLon(35.0, 25.0),
            speedMetersPerSecond = 2.0,
            sprayRateLitersPerMinute = 15.0,
        )

        assertTrue(plan.totalSprayLiters > 100.0)
        assertEquals(6, plan.tankRefillCount)
        assertEquals(plan.tankRefillCount, plan.tankRefillPoints.size)
        assertEquals(6, plan.serviceStops.count { it.requiresTankRefill })
    }

    @Test
    fun splitsPathAtEveryServiceStopForReturnAndResume() {
        val path = listOf(LatLon(35.0, 25.0), LatLon(35.01, 25.0))
        val plan = MissionResourcePlanner.plan(
            path = path,
            home = path.first(),
            speedMetersPerSecond = 2.0,
            sprayRateLitersPerMinute = 15.0,
        )

        val legs = MissionResourcePlanner.splitIntoServiceLegs(path, plan.serviceStops)

        assertEquals(plan.serviceStops.size + 1, legs.size)
        assertEquals(plan.serviceStops.first().point, legs.first().path.last())
        assertEquals(plan.serviceStops.first().point, legs[1].path.first())
        assertEquals(plan.serviceStops.first(), legs.first().serviceAfter)
        assertEquals(null, legs.last().serviceAfter)
    }
}
