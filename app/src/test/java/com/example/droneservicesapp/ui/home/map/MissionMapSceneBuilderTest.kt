package com.example.droneservicesapp.ui.home.map

import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.model.MissionObstacle
import com.example.droneservicesapp.domain.model.MissionObstacleShape
import com.example.droneservicesapp.ui.map.model.MapCoordinate
import com.example.droneservicesapp.ui.map.model.MapMarkerDragMode
import com.example.droneservicesapp.ui.map.model.MapMarkerIcon
import com.example.droneservicesapp.ui.map.model.MapOverlayLayer
import com.example.droneservicesapp.ui.map.model.MapVertexStyle
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionMapSceneBuilderTest {
    private val square = listOf(
        LatLng(35.0, 24.0),
        LatLng(35.0, 24.001),
        LatLng(35.001, 24.001),
        LatLng(35.001, 24.0),
    )

    @Test
    fun missionAreaHasNumberedVertexHandlesThatAreOnlyEditableWhileEditing() {
        val editable = MissionMapSceneBuilder.missionArea(square, editable = true)
        assertEquals(1, editable.polygons.size)
        assertEquals(4, editable.markers.size)
        val first = editable.markers.first()
        assertEquals(MissionMapMarkerIds.AREA_VERTEX + 0, first.id)
        assertEquals(MapMarkerIcon.NumberedVertex(1, MapVertexStyle.AREA), first.icon)
        assertTrue(first.clickable)
        assertEquals(MapMarkerDragMode.LONG_PRESS, first.dragMode)

        val locked = MissionMapSceneBuilder.missionArea(square, editable = false)
        assertTrue(locked.markers.none { it.clickable || it.dragMode != MapMarkerDragMode.NONE })
    }

    @Test
    fun singleVertexAreaShowsAHandleButNoPolygon() {
        val layer = MissionMapSceneBuilder.missionArea(square.take(1), editable = true)
        assertTrue(layer.polygons.isEmpty())
        assertEquals(1, layer.markers.size)
    }

    @Test
    fun aircraftWithoutFixIsHiddenAndOffsetModeMakesItDraggable() {
        val hidden = MissionMapSceneBuilder.aircraft(LatLon(0.0, 0.0), 0f, false, null, emptyList(), null)
        assertTrue(hidden.markers.isEmpty())

        val shown = MissionMapSceneBuilder.aircraft(LatLon(35.0, 24.0), 45f, true, LatLon(35.0, 24.001), emptyList(), null)
        val aircraft = shown.markers.single { it.id == MissionMapMarkerIds.AIRCRAFT }
        assertEquals(45f, aircraft.rotationDegrees)
        assertEquals(MapMarkerIcon.Aircraft(highlighted = true), aircraft.icon)
        assertEquals(MapMarkerDragMode.LONG_PRESS, aircraft.dragMode)
        assertEquals(MapMarkerIcon.Home, shown.markers.single { it.id == MissionMapMarkerIds.HOME }.icon)
    }

    @Test
    fun flightTraceNeedsTwoPoints() {
        val one = MissionMapSceneBuilder.aircraft(null, 0f, false, null, listOf(MapCoordinate(35.0, 24.0)), null)
        assertTrue(one.polylines.isEmpty())
        val two = MissionMapSceneBuilder.aircraft(
            null, 0f, false, null, listOf(MapCoordinate(35.0, 24.0), MapCoordinate(35.0, 24.1)), null,
        )
        assertEquals(MapOverlayLayer.FLIGHT_TRACE, two.polylines.single().layer)
    }

    @Test
    fun obstaclesAreLetteredAndInvalidOnesSkipped() {
        val obstacles = listOf(
            MissionObstacle("a", MissionObstacleShape.CIRCLE, center = LatLon(35.0, 24.0), radiusMeters = 10.0),
            MissionObstacle("bad", MissionObstacleShape.CIRCLE, center = null, radiusMeters = 10.0),
            MissionObstacle(
                "b",
                MissionObstacleShape.POLYGON,
                vertices = listOf(LatLon(35.0, 24.0), LatLon(35.0, 24.001), LatLon(35.001, 24.0)),
            ),
        )
        val layer = MissionMapSceneBuilder.obstacles(obstacles, emptyList())
        assertEquals(2, layer.polygons.size)
        assertEquals(
            listOf(MapMarkerIcon.ObstacleLabel("A"), MapMarkerIcon.ObstacleLabel("B")),
            layer.markers.map { it.icon },
        )
    }

    @Test
    fun obstacleDraftClosesOnceItHasThreePoints() {
        val two = MissionMapSceneBuilder.obstacles(emptyList(), square.take(2))
        assertEquals(2, two.polygons.single().points.size)
        val three = MissionMapSceneBuilder.obstacles(emptyList(), square.take(3))
        val ring = three.polygons.single().points
        assertEquals(4, ring.size)
        assertEquals(ring.first(), ring.last())
        assertEquals(3, three.markers.size)
    }

    @Test
    fun composedSceneKeepsIdsUnique() {
        val scene = MissionMapSceneBuilder.compose(
            rasters = emptyList(),
            layers = listOf(
                MissionMapSceneBuilder.missionArea(square, editable = true),
                MissionMapSceneBuilder.aircraft(LatLon(35.0, 24.0), 0f, false, LatLon(35.0, 24.001), emptyList(), null),
                MissionMapSceneBuilder.obstacles(emptyList(), square),
                MissionMapSceneBuilder.terrainCoverage(null),
            ),
        )
        val ids = scene.polygons.map { it.id } + scene.markers.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun labelsUseOperatorUnits() {
        assertEquals("950 m", MissionMapSceneBuilder.formatDistanceLabel(950.4))
        assertEquals("1.5 km", MissionMapSceneBuilder.formatDistanceLabel(1_500.0))
        assertEquals("9999 m²", MissionMapSceneBuilder.formatAreaLabel(9_999.0))
        assertEquals("1.25 ha", MissionMapSceneBuilder.formatAreaLabel(12_500.0))
        assertEquals("C", MissionMapSceneBuilder.obstacleLetter(2))
    }

    @Test
    fun markerIdsRoundTripToIndices() {
        assertEquals(3, MissionMapMarkerIds.indexOf(MissionMapMarkerIds.AREA_VERTEX, MissionMapMarkerIds.AREA_VERTEX + 3))
        assertNull(MissionMapMarkerIds.indexOf(MissionMapMarkerIds.AREA_VERTEX, MissionMapMarkerIds.ROUTE_WAYPOINT + 3))
        assertNull(MissionMapMarkerIds.indexOf(MissionMapMarkerIds.AREA_VERTEX, "area-vertex-x"))
    }

    @Test
    fun edgeLabelsSitOutsideTheFieldAndAboveHandles() {
        val centroid = MapCoordinate(35.0005, 24.0005)
        // Bottom edge (west -> east): the label goes below it, i.e. +y on screen.
        val south = MissionMapSceneBuilder.edgeLabelOffsetDp(
            MapCoordinate(35.0, 24.0), MapCoordinate(35.0, 24.001), MapCoordinate(35.0, 24.0005), centroid, "86 m",
        )
        assertEquals(0f, south.first, 0.01f)
        assertTrue(south.second > 0f)
        // West edge (north -> south): the label goes to the left, -x, clearing half its width.
        val west = MissionMapSceneBuilder.edgeLabelOffsetDp(
            MapCoordinate(35.001, 24.0), MapCoordinate(35.0, 24.0), MapCoordinate(35.0005, 24.0), centroid, "111 m",
        )
        assertTrue(west.first < -30f)
        assertEquals(0f, west.second, 0.01f)

        val layer = MissionMapSceneBuilder.surveyPath(
            path = square,
            areaVertices = square,
            segmentColors = null,
            editingEnabled = true,
            selectedIndex = null,
            batteryReturnPoints = emptyList(),
            tankRefillPoints = emptyList(),
        )
        val labels = layer.markers.filter { it.icon is MapMarkerIcon.TextLabel }
        assertEquals(5, labels.size)
        assertTrue(labels.all { it.layer == MapOverlayLayer.LABELS })
        assertTrue(MapOverlayLayer.LABELS.drawOrder > MapOverlayLayer.EDIT_HANDLES.drawOrder)
    }
}
