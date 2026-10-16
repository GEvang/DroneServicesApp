package com.example.droneservicesapp.ui.home.map

import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.geoawareness.GeoZoneGeometry
import com.example.droneservicesapp.domain.geoawareness.GeoZoneRestriction
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.model.MissionObstacle
import com.example.droneservicesapp.domain.model.MissionObstacleShape
import com.example.droneservicesapp.ui.map.model.MapCoordinate
import com.example.droneservicesapp.ui.map.model.MapMarkerIcon
import com.example.droneservicesapp.ui.map.model.MapScene
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 7 performance safeguard: a large but realistic mission (40 ha field, ~3,000 survey
 * waypoints, a long flight trace, many geo-zones and obstacles) must build quickly and keep the
 * number of decoration markers bounded. The time budget is generous on purpose; it only catches
 * accidental quadratic work, not small regressions.
 */
class MissionMapScenePerformanceTest {
    private val originLat = 35.36
    private val originLon = 24.47
    private val degreesPerMetreLat = 1.0 / 111_320.0
    private val degreesPerMetreLon = 1.0 / (111_320.0 * Math.cos(Math.toRadians(originLat)))

    private fun at(eastMetres: Double, northMetres: Double) =
        LatLng(originLat + northMetres * degreesPerMetreLat, originLon + eastMetres * degreesPerMetreLon)

    private val field = listOf(at(0.0, 0.0), at(632.0, 0.0), at(632.0, 632.0), at(0.0, 632.0))

    /** Boustrophedon passes every 4 m with a waypoint roughly every 40 m. */
    private val surveyPath: List<LatLng> = buildList {
        var east = 2.0
        var northward = true
        while (east < 630.0) {
            val stations = (0..15).map { it * 630.0 / 15.0 }
            (if (northward) stations else stations.reversed()).forEach { add(at(east, it)) }
            northward = !northward
            east += 4.0
        }
    }

    private val trace: List<MapCoordinate> = (0 until 5_000).map { index ->
        val point = at(316.0 + 300.0 * Math.cos(index / 300.0), 316.0 + 300.0 * Math.sin(index / 300.0))
        MapCoordinate(point.latitude, point.longitude)
    }

    private val zones: List<GeoZone> = (0 until 40).map { index ->
        val centre = at(-2_000.0 + index * 120.0, -1_000.0 + (index % 7) * 300.0)
        val geometry = if (index % 2 == 0) {
            GeoZoneGeometry.Circle(LatLon(centre.latitude, centre.longitude), 250.0, null, 120.0)
        } else {
            val ring = (0 until 24).map { step ->
                val angle = 2 * Math.PI * step / 24
                val point = at(-2_000.0 + index * 120.0 + 200 * Math.cos(angle), -1_000.0 + (index % 7) * 300.0 + 200 * Math.sin(angle))
                LatLon(point.latitude, point.longitude)
            }
            GeoZoneGeometry.Polygon(listOf(ring), null, 120.0)
        }
        GeoZone(
            id = "zone-$index", country = "GRC", name = "Zone $index", type = null,
            restriction = GeoZoneRestriction.entries[index % GeoZoneRestriction.entries.size],
            reason = emptyList(), otherReasonInfo = null, message = null, applicability = emptyList(),
            authorities = emptyList(), geometries = listOf(geometry), colorHex = null, arc = null, isDummy = false,
        )
    }

    private val obstacles: List<MissionObstacle> = (0 until 25).map { index ->
        val centre = at(50.0 + index * 22.0, 300.0)
        MissionObstacle("obstacle-$index", MissionObstacleShape.CIRCLE, LatLon(centre.latitude, centre.longitude), 6.0)
    }

    private fun buildScene(): MapScene = MissionMapSceneBuilder.compose(
        rasters = emptyList(),
        layers = listOf(
            MissionMapSceneBuilder.geoZones(zones) { true },
            MissionMapSceneBuilder.missionArea(field, editable = false),
            MissionMapSceneBuilder.surveyPath(surveyPath, field, null, true, null, emptyList(), emptyList()),
            MissionMapSceneBuilder.obstacles(obstacles, emptyList()),
            MissionMapSceneBuilder.aircraft(LatLon(originLat, originLon), 90f, false, LatLon(originLat, originLon), trace, null),
        ),
    )

    @Test
    fun largeMissionBuildsQuicklyWithBoundedDecorations() {
        assertTrue("fixture should be a large survey", surveyPath.size > 2_500)
        buildScene() // warm up class loading and JIT

        val runs = 5
        val started = System.nanoTime()
        var scene: MapScene? = null
        repeat(runs) { scene = buildScene() }
        val averageMillis = (System.nanoTime() - started) / 1_000_000.0 / runs

        val built = requireNotNull(scene)
        assertTrue("scene build took ${averageMillis}ms on average", averageMillis < 750.0)
        assertEquals(40, built.polygons.count { it.id.startsWith("geo-") })
        assertEquals(trace.size, built.polylines.single { it.id == "flight-trace" }.points.size)
        val arrows = built.markers.count { it.icon is MapMarkerIcon.DirectionArrow }
        assertTrue("direction arrows must stay capped, got $arrows", arrows <= 80)
        assertEquals(surveyPath.size, built.markers.count { it.icon is MapMarkerIcon.PathWaypoint })
    }
}
