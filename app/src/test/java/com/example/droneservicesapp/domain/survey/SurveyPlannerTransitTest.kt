package com.example.droneservicesapp.domain.survey

import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.model.MissionObstacle
import com.example.droneservicesapp.domain.model.MissionObstacleShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SurveyPlannerTransitTest {
    @Test
    fun transitPathDetoursAroundCircleObstacle() {
        val from = LatLon(35.0, 25.0)
        val to = LatLon(35.0, 25.002)
        val obstacle = MissionObstacle(
            id = "middle",
            shape = MissionObstacleShape.CIRCLE,
            center = LatLon(35.0, 25.001),
            radiusMeters = 20.0,
        )

        val route = SurveyPlanner().buildObstacleAvoidingTransitPath(from, to, listOf(obstacle))

        assertEquals(from, route.first())
        assertEquals(to, route.last())
        assertTrue(route.size > 2)
    }
}
