package com.example.droneservicesapp.ui.map.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapModelsTest {
    @Test
    fun boundsExposeStableCenterWithoutMapSdkTypes() {
        val bounds = MapBounds(MapCoordinate(35.0, 24.0), MapCoordinate(37.0, 28.0))
        assertEquals(MapCoordinate(36.0, 26.0), bounds.center)
        assertTrue(bounds::class.java.declaredFields.none { it.type.name.startsWith("org.osmdroid") })
    }

    @Test(expected = IllegalArgumentException::class)
    fun coordinateRejectsInvalidLatitude() {
        MapCoordinate(91.0, 20.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun sceneRejectsDuplicateIdsAcrossOverlayKinds() {
        MapScene(
            markers = listOf(MapMarkerModel("shared", MapCoordinate(35.0, 24.0))),
            polylines = listOf(MapPolylineModel("shared", emptyList(), 0)),
        )
    }

    @Test
    fun cameraCommandsUseTokensSoIdenticalRequestsCanBeRepeated() {
        val camera = MapCamera(MapCoordinate(35.0, 24.0), 17.0)
        val first = MapCameraCommand.Move(1, camera)
        val second = MapCameraCommand.Move(2, camera)
        assertTrue(first != second)
    }

    @Test
    fun terrainGeoMissionAndAircraftUseDeterministicDrawOrder() {
        val point = MapCoordinate(35.0, 24.0)
        val scene = MapScene(
            polygons = listOf(
                MapPolygonModel("geo", listOf(point), 0, 0, layer = MapOverlayLayer.GEO_AWARENESS),
                MapPolygonModel("terrain", listOf(point), 0, 0, layer = MapOverlayLayer.TERRAIN),
            ),
            polylines = listOf(MapPolylineModel("mission", listOf(point), 0)),
            markers = listOf(MapMarkerModel("aircraft", point, layer = MapOverlayLayer.AIRCRAFT)),
        )
        assertEquals(listOf("terrain", "geo", "mission", "aircraft"), scene.orderedOverlayIds())
    }
}
