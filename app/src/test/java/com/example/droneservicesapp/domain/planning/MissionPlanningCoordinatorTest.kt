package com.example.droneservicesapp.domain.planning

import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.model.PlanningOperationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionPlanningCoordinatorTest {
    private val coordinator = MissionPlanningCoordinator()
    private val home = LatLon(35.0, 24.0)
    private val near = LatLon(35.0001, 24.0)
    private val far = LatLon(35.001, 24.0)

    @Test
    fun `spray path starts at endpoint nearest home`() {
        val ordered = coordinator.orderPathForHome(
            path = listOf(far, near),
            home = home,
            operationMode = PlanningOperationMode.SPRAY,
        )

        assertEquals(listOf(near, far), ordered)
    }

    @Test
    fun `survey path starts at endpoint farthest from home`() {
        val ordered = coordinator.orderPathForHome(
            path = listOf(near, far),
            home = home,
            operationMode = PlanningOperationMode.SURVEY,
        )

        assertEquals(listOf(far, near), ordered)
    }

    @Test
    fun `resource plan ignores spray rate in survey mode`() {
        val plan = coordinator.buildResourcePlan(
            path = listOf(near, far),
            home = home,
            operationMode = PlanningOperationMode.SURVEY,
            speedMetersPerSecond = 5.0,
            sprayRateLitersPerMinute = 4.0,
        )

        assertEquals(0.0, plan.totalSprayLiters, 0.0)
        assertTrue(plan.batteryCount >= 1)
    }
}
