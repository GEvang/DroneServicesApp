package com.example.droneservicesapp.ui.home.map

import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.geoawareness.GeoZoneApplicabilityEvaluator
import com.example.droneservicesapp.domain.geoawareness.GeoZoneGeometry
import com.example.droneservicesapp.domain.geoawareness.GeoZoneRestriction
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.model.MissionObstacle
import com.example.droneservicesapp.domain.model.MissionObstacleShape
import com.example.droneservicesapp.domain.model.RouteWaypoint
import com.example.droneservicesapp.domain.terrain.TerrainCoveragePlanner
import com.example.droneservicesapp.domain.terrain.TerrainWaypoint
import com.example.droneservicesapp.ui.map.model.MapBounds
import com.example.droneservicesapp.ui.map.model.MapCoordinate
import com.example.droneservicesapp.ui.map.model.MapGeometry
import com.example.droneservicesapp.ui.map.model.MapLabelStyle
import com.example.droneservicesapp.ui.map.model.MapMarkerAnchor
import com.example.droneservicesapp.ui.map.model.MapMarkerDragMode
import com.example.droneservicesapp.ui.map.model.MapMarkerIcon
import com.example.droneservicesapp.ui.map.model.MapMarkerModel
import com.example.droneservicesapp.ui.map.model.MapOverlayLayer
import com.example.droneservicesapp.ui.map.model.MapPolygonModel
import com.example.droneservicesapp.ui.map.model.MapPolylineModel
import com.example.droneservicesapp.ui.map.model.MapRasterModel
import com.example.droneservicesapp.ui.map.model.MapScene
import com.example.droneservicesapp.ui.map.model.MapServiceStopKind
import com.example.droneservicesapp.ui.map.model.MapVertexStyle
import com.example.droneservicesapp.ui.preview.buildSurveyDirectionSegments
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Builds the engine-independent mission-map scene. Every function is pure so each layer can be
 * cached separately and the visual rules can be unit-tested without a map engine.
 *
 * Colours, widths and artwork match the former osmdroid editors.
 */
object MissionMapSceneBuilder {
    data class Layer(
        val polygons: List<MapPolygonModel> = emptyList(),
        val polylines: List<MapPolylineModel> = emptyList(),
        val markers: List<MapMarkerModel> = emptyList(),
    ) {
        companion object {
            val EMPTY = Layer()
        }
    }

    const val ACTIVE_COLOR = 0xFF29E6DA.toInt()
    const val FLIGHT_TRACE_COLOR = 0xFF46F29C.toInt()
    const val TERRAIN_PURPLE = 0xFF9C6BFF.toInt()
    private const val AREA_STROKE = 0xFF50C8FF.toInt()
    private const val AREA_FILL = 0x3C50C8FF
    private const val OBSTACLE_STROKE = 0xFFDC2626.toInt()
    private const val OBSTACLE_FILL = 0x55DC2626
    private const val DRAFT_STROKE = 0xFFF87171.toInt()
    private const val DRAFT_FILL = 0x37DC2626
    private const val MAX_DIRECTION_MARKERS = 80
    private const val ROUTE_LABEL_OFFSET_DP = 28f
    private const val TERRAIN_CIRCLE_SEGMENTS = 96
    private const val TERRAIN_HATCH_SPACING_METERS = 140.0
    private const val GEO_CIRCLE_SEGMENTS = 72
    private const val OBSTACLE_CIRCLE_STEP_DEGREES = 12

    fun compose(rasters: List<MapRasterModel>, layers: List<Layer>): MapScene = MapScene(
        rasters = rasters,
        polygons = layers.flatMap { it.polygons },
        polylines = layers.flatMap { it.polylines },
        markers = layers.flatMap { it.markers },
    )

    fun orthoRaster(raster: MissionMapLayers.OrthoRaster?): List<MapRasterModel> {
        raster ?: return emptyList()
        val bounds = runCatching {
            MapBounds(
                MapCoordinate(raster.bounds.minLat, raster.bounds.minLon),
                MapCoordinate(raster.bounds.maxLat, raster.bounds.maxLon),
            )
        }.getOrNull() ?: return emptyList()
        return listOf(MapRasterModel(ORTHO_RASTER_ID, bounds, raster.opacity.coerceIn(0f, 1f)))
    }

    fun terrainCoverage(coverage: TerrainCoverageLayerState.Coverage?): Layer {
        coverage ?: return Layer.EMPTY
        val center = coverage.center
        val radius = coverage.radiusMeters
        val ring = (0..TERRAIN_CIRCLE_SEGMENTS).mapNotNull { index ->
            val angle = 2.0 * PI * index / TERRAIN_CIRCLE_SEGMENTS
            TerrainCoveragePlanner.offset(center, northMeters = cos(angle) * radius, eastMeters = sin(angle) * radius)
                .toMapCoordinateOrNull()
        }
        val hatch = mutableListOf<MapPolylineModel>()
        val diagonal = sqrt(2.0)
        var perpendicular = -radius + TERRAIN_HATCH_SPACING_METERS
        var index = 0
        while (perpendicular < radius) {
            val halfLength = sqrt(radius * radius - perpendicular * perpendicular)
            fun endpoint(along: Double): MapCoordinate? = TerrainCoveragePlanner.offset(
                center,
                (along - perpendicular) / diagonal,
                (along + perpendicular) / diagonal,
            ).toMapCoordinateOrNull()
            val start = endpoint(-halfLength)
            val end = endpoint(halfLength)
            if (start != null && end != null) {
                hatch += MapPolylineModel(
                    "terrain-hatch-${index++}", listOf(start, end), withAlpha(TERRAIN_PURPLE, 105), 3f, MapOverlayLayer.TERRAIN,
                )
            }
            perpendicular += TERRAIN_HATCH_SPACING_METERS
        }
        return Layer(
            polygons = listOf(
                MapPolygonModel("terrain-coverage", ring, TERRAIN_PURPLE, withAlpha(TERRAIN_PURPLE, 28), 5f, MapOverlayLayer.TERRAIN),
            ),
            polylines = hatch,
        )
    }

    fun geoZones(zones: List<GeoZone>, isActiveNow: (GeoZone) -> Boolean = GeoZoneApplicabilityEvaluator::isActiveNow): Layer {
        val polygons = mutableListOf<MapPolygonModel>()
        zones.forEachIndexed { zoneIndex, zone ->
            val active = isActiveNow(zone)
            val style = geoZoneStyle(zone.restriction, active)
            zone.geometries.forEachIndexed { geometryIndex, geometry ->
                val ring = when (geometry) {
                    is GeoZoneGeometry.Circle -> geometry.center.toMapCoordinateOrNull()
                        ?.let { MapGeometry.circle(it, geometry.radiusMeters, GEO_CIRCLE_SEGMENTS) }
                        .orEmpty()
                    is GeoZoneGeometry.Polygon -> geometry.rings.firstOrNull().orEmpty()
                        .takeIf { it.size >= 3 }
                        ?.mapNotNull { it.toMapCoordinateOrNull() }
                        ?.let(MapGeometry::closeRing)
                        .orEmpty()
                }
                if (ring.size >= 4) {
                    polygons += MapPolygonModel(
                        id = "geo-$zoneIndex-$geometryIndex",
                        points = ring,
                        strokeColorArgb = style.stroke,
                        fillColorArgb = style.fill,
                        strokeWidthPx = if (active) 3f else 4f,
                        layer = MapOverlayLayer.GEO_AWARENESS,
                        dashed = !active,
                    )
                }
            }
        }
        return Layer(polygons = polygons)
    }

    fun missionArea(vertices: List<LatLng>, editable: Boolean): Layer {
        val points = vertices.mapNotNull { it.toMapCoordinateOrNull() }
        val polygon = if (points.size >= 2) {
            listOf(MapPolygonModel("area-polygon", points, AREA_STROKE, AREA_FILL, 10f, MapOverlayLayer.MISSION))
        } else emptyList()
        val markers = vertices.mapIndexedNotNull { index, vertex ->
            vertex.toMapCoordinateOrNull()?.let { position ->
                MapMarkerModel(
                    id = MissionMapMarkerIds.AREA_VERTEX + index,
                    position = position,
                    layer = MapOverlayLayer.EDIT_HANDLES,
                    icon = MapMarkerIcon.NumberedVertex(index + 1, MapVertexStyle.AREA),
                    anchor = MapMarkerAnchor.CENTER,
                    clickable = editable,
                    dragMode = if (editable) MapMarkerDragMode.LONG_PRESS else MapMarkerDragMode.NONE,
                )
            }
        }
        return Layer(polygons = polygon, markers = markers)
    }

    fun surveyPath(
        path: List<LatLng>,
        areaVertices: List<LatLng>,
        segmentColors: List<Int>?,
        editingEnabled: Boolean,
        selectedIndex: Int?,
        batteryReturnPoints: List<LatLon>,
        tankRefillPoints: List<LatLon>,
    ): Layer {
        val polylines = mutableListOf<MapPolylineModel>()
        val markers = mutableListOf<MapMarkerModel>()
        val points = path.map { it.toMapCoordinateOrNull() }
        if (segmentColors != null && segmentColors.size >= path.size - 1) {
            points.zipWithNext().forEachIndexed { index, (from, to) ->
                if (from != null && to != null) {
                    polylines += MapPolylineModel("survey-segment-$index", listOf(from, to), segmentColors.getOrElse(index) { ACTIVE_COLOR }, 6f)
                }
            }
        } else if (path.size >= 2) {
            polylines += MapPolylineModel("survey-path", points.filterNotNull(), ACTIVE_COLOR, 6f)
        }

        buildSurveyDirectionSegments(path, MAX_DIRECTION_MARKERS).forEachIndexed { index, segment ->
            val from = segment.from.toMapCoordinateOrNull() ?: return@forEachIndexed
            val to = segment.to.toMapCoordinateOrNull() ?: return@forEachIndexed
            markers += MapMarkerModel(
                id = "survey-arrow-$index",
                position = MapGeometry.midpoint(from, to),
                rotationDegrees = MapGeometry.screenArrowRotationDegrees(from, to),
                layer = MapOverlayLayer.MISSION_DETAIL,
                icon = MapMarkerIcon.DirectionArrow(withShadow = true),
                anchor = MapMarkerAnchor.CENTER,
            )
        }

        SurveyPathLayerState.perimeterSegments(areaVertices).forEachIndexed { index, (from, to) ->
            val mid = MapGeometry.coordinateOrNull((from.latitude + to.latitude) / 2.0, (from.longitude + to.longitude) / 2.0)
                ?: return@forEachIndexed
            markers += MapMarkerModel(
                id = "survey-measure-$index",
                position = mid,
                layer = MapOverlayLayer.MISSION_DETAIL,
                icon = MapMarkerIcon.TextLabel(formatDistanceLabel(SphericalUtil.computeDistanceBetween(from, to)), MapLabelStyle.MEASUREMENT),
                anchor = MapMarkerAnchor.CENTER,
            )
        }
        if (areaVertices.size >= 3) {
            MapGeometry.coordinateOrNull(areaVertices.map { it.latitude }.average(), areaVertices.map { it.longitude }.average())
                ?.let { centroid ->
                    markers += MapMarkerModel(
                        id = "survey-area-label",
                        position = centroid,
                        layer = MapOverlayLayer.MISSION_DETAIL,
                        icon = MapMarkerIcon.TextLabel("Area ${formatAreaLabel(SphericalUtil.computeArea(areaVertices))}", MapLabelStyle.AREA),
                        anchor = MapMarkerAnchor.CENTER,
                    )
                }
        }

        batteryReturnPoints.forEachIndexed { index, point ->
            point.toMapCoordinateOrNull(requireFix = true)?.let {
                markers += serviceMarker("service-battery-$index", it, MapServiceStopKind.BATTERY)
            }
        }
        tankRefillPoints.forEachIndexed { index, point ->
            point.toMapCoordinateOrNull(requireFix = true)?.let {
                markers += serviceMarker("service-tank-$index", it, MapServiceStopKind.TANK)
            }
        }

        path.forEachIndexed { index, point ->
            val position = MapGeometry.coordinateOrNull(point.latitude, point.longitude) ?: return@forEachIndexed
            markers += MapMarkerModel(
                id = MissionMapMarkerIds.SURVEY_WAYPOINT + index,
                position = position,
                layer = MapOverlayLayer.EDIT_HANDLES,
                icon = MapMarkerIcon.PathWaypoint(selected = index == selectedIndex),
                anchor = MapMarkerAnchor.CENTER,
                clickable = editingEnabled,
            )
        }
        return Layer(polylines = polylines, markers = markers)
    }

    fun route(
        waypoints: List<RouteWaypoint>,
        displayPath: List<LatLng>,
        terrainPath: List<TerrainWaypoint>,
        enabled: Boolean,
        selectedControlIndex: Int?,
        selectedTerrainIndex: Int?,
        dragPreview: Pair<Int, MapCoordinate>?,
    ): Layer {
        val polylines = mutableListOf<MapPolylineModel>()
        val markers = mutableListOf<MapMarkerModel>()
        val authored = waypoints.map { MapGeometry.coordinateOrNull(it.latitude, it.longitude) }
        val linePoints = if (dragPreview != null) {
            // While dragging, the line follows the authored waypoints, as it always has.
            authored.mapIndexed { index, point -> if (index == dragPreview.first) dragPreview.second else point }.filterNotNull()
        } else {
            displayPath.mapNotNull { it.toMapCoordinateOrNull() }
        }
        if (linePoints.isNotEmpty()) {
            polylines += MapPolylineModel("route-path", linePoints, ACTIVE_COLOR, 8f)
        }

        buildSurveyDirectionSegments(displayPath, MAX_DIRECTION_MARKERS).forEachIndexed { index, segment ->
            val from = segment.from.toMapCoordinateOrNull() ?: return@forEachIndexed
            val to = segment.to.toMapCoordinateOrNull() ?: return@forEachIndexed
            markers += MapMarkerModel(
                id = "route-arrow-$index",
                position = MapGeometry.midpoint(from, to),
                rotationDegrees = MapGeometry.bearingDegrees(from, to),
                layer = MapOverlayLayer.MISSION_DETAIL,
                icon = MapMarkerIcon.DirectionArrow(withShadow = false),
                anchor = MapMarkerAnchor.CENTER,
            )
        }

        waypoints.zipWithNext().forEachIndexed { index, (from, to) ->
            val start = MapGeometry.coordinateOrNull(from.latitude, from.longitude) ?: return@forEachIndexed
            val end = MapGeometry.coordinateOrNull(to.latitude, to.longitude) ?: return@forEachIndexed
            val direction = MapGeometry.screenDirection(start, end)
            val offsetX = direction?.let { (-it.second * ROUTE_LABEL_OFFSET_DP).toFloat() } ?: 0f
            val offsetY = direction?.let { (it.first * ROUTE_LABEL_OFFSET_DP).toFloat() } ?: 0f
            val meters = SphericalUtil.computeDistanceBetween(LatLng(from.latitude, from.longitude), LatLng(to.latitude, to.longitude))
            markers += MapMarkerModel(
                id = "route-distance-$index",
                position = MapGeometry.midpoint(start, end),
                layer = MapOverlayLayer.MISSION_DETAIL,
                icon = MapMarkerIcon.TextLabel(String.format(Locale.US, "%.1f m", meters), MapLabelStyle.DISTANCE, offsetX, offsetY),
                anchor = MapMarkerAnchor.CENTER,
            )
        }

        terrainPath.forEachIndexed { index, waypoint ->
            val position = waypoint.latLon.toMapCoordinateOrNull(requireFix = true) ?: return@forEachIndexed
            markers += MapMarkerModel(
                id = MissionMapMarkerIds.ROUTE_TERRAIN + index,
                position = position,
                layer = MapOverlayLayer.EDIT_HANDLES,
                icon = MapMarkerIcon.TerrainWaypoint(selected = index == selectedTerrainIndex),
                anchor = MapMarkerAnchor.CENTER,
                clickable = enabled,
            )
        }
        waypoints.forEachIndexed { index, waypoint ->
            val position = authored[index] ?: return@forEachIndexed
            markers += MapMarkerModel(
                id = MissionMapMarkerIds.ROUTE_WAYPOINT + index,
                position = if (dragPreview?.first == index) dragPreview.second else position,
                layer = MapOverlayLayer.EDIT_HANDLES,
                icon = MapMarkerIcon.NumberedVertex(waypoint.index, MapVertexStyle.ROUTE, selected = index == selectedControlIndex),
                anchor = MapMarkerAnchor.CENTER,
                clickable = enabled,
                doubleTappable = enabled,
                dragMode = if (enabled) MapMarkerDragMode.IMMEDIATE else MapMarkerDragMode.NONE,
            )
        }
        return Layer(polylines = polylines, markers = markers)
    }

    fun obstacles(obstacles: List<MissionObstacle>, draftPoints: List<LatLng>): Layer {
        val polygons = mutableListOf<MapPolygonModel>()
        val markers = mutableListOf<MapMarkerModel>()
        obstacles.filter { it.isValid() }.forEachIndexed { index, obstacle ->
            val ring = obstacleRing(obstacle)
            if (ring.isNotEmpty()) {
                polygons += MapPolygonModel("obstacle-$index", ring, OBSTACLE_STROKE, OBSTACLE_FILL, 5f, MapOverlayLayer.OBSTACLES)
            }
            obstacleCenter(obstacle)?.let { center ->
                markers += MapMarkerModel(
                    id = "obstacle-label-$index",
                    position = center,
                    layer = MapOverlayLayer.OBSTACLES,
                    icon = MapMarkerIcon.ObstacleLabel(obstacleLetter(index)),
                    anchor = MapMarkerAnchor.CENTER,
                )
            }
        }
        val draft = draftPoints.mapNotNull { it.toMapCoordinateOrNull() }
        if (draft.size >= 2) {
            polygons += MapPolygonModel(
                "obstacle-draft",
                if (draft.size >= 3) draft + draft.first() else draft,
                DRAFT_STROKE,
                DRAFT_FILL,
                4f,
                MapOverlayLayer.OBSTACLES,
            )
        }
        draft.forEachIndexed { index, point ->
            markers += MapMarkerModel(
                id = "obstacle-draft-point-$index",
                position = point,
                layer = MapOverlayLayer.OBSTACLES,
                anchor = MapMarkerAnchor.CENTER,
            )
        }
        return Layer(polygons = polygons, markers = markers)
    }

    fun aircraft(
        displayedPosition: LatLon?,
        headingDegrees: Float,
        offsetAdjustmentActive: Boolean,
        homePosition: LatLon?,
        flightTrace: List<MapCoordinate>,
        simulation: AircraftLayerState.SimulatedAircraft?,
    ): Layer {
        val markers = mutableListOf<MapMarkerModel>()
        homePosition?.toMapCoordinateOrNull(requireFix = true)?.let {
            markers += MapMarkerModel(
                id = MissionMapMarkerIds.HOME,
                position = it,
                layer = MapOverlayLayer.AIRCRAFT,
                icon = MapMarkerIcon.Home,
                anchor = MapMarkerAnchor.BOTTOM_CENTER,
            )
        }
        simulation?.position?.toMapCoordinateOrNull(requireFix = true)?.let {
            markers += MapMarkerModel(
                id = MissionMapMarkerIds.SIMULATION,
                position = it,
                rotationDegrees = simulation.headingDegrees,
                layer = MapOverlayLayer.AIRCRAFT,
                icon = MapMarkerIcon.Aircraft(highlighted = true),
                anchor = MapMarkerAnchor.CENTER,
            )
        }
        displayedPosition?.toMapCoordinateOrNull(requireFix = true)?.let {
            markers += MapMarkerModel(
                id = MissionMapMarkerIds.AIRCRAFT,
                position = it,
                rotationDegrees = headingDegrees,
                layer = MapOverlayLayer.AIRCRAFT,
                icon = MapMarkerIcon.Aircraft(highlighted = offsetAdjustmentActive),
                anchor = MapMarkerAnchor.CENTER,
                dragMode = if (offsetAdjustmentActive) MapMarkerDragMode.LONG_PRESS else MapMarkerDragMode.NONE,
            )
        }
        val trace = if (flightTrace.size >= 2) {
            listOf(MapPolylineModel("flight-trace", flightTrace, FLIGHT_TRACE_COLOR, 8f, MapOverlayLayer.FLIGHT_TRACE))
        } else emptyList()
        return Layer(polylines = trace, markers = markers)
    }

    fun formatDistanceLabel(distanceMeters: Double): String = if (distanceMeters >= 1000.0) {
        String.format(Locale.US, "%.1f km", distanceMeters / 1000.0)
    } else {
        "${distanceMeters.roundToInt().coerceAtLeast(0)} m"
    }

    fun formatAreaLabel(areaSquareMeters: Double): String = if (areaSquareMeters >= 10_000.0) {
        String.format(Locale.US, "%.2f ha", areaSquareMeters / 10_000.0)
    } else {
        "${areaSquareMeters.roundToInt().coerceAtLeast(0)} m²"
    }

    fun obstacleLetter(index: Int): String = ('A'.code + index).toChar().toString()

    private fun serviceMarker(id: String, position: MapCoordinate, kind: MapServiceStopKind) = MapMarkerModel(
        id = id,
        position = position,
        layer = MapOverlayLayer.MISSION_DETAIL,
        icon = MapMarkerIcon.ServiceStop(kind),
        anchor = MapMarkerAnchor.CENTER,
    )

    private fun obstacleRing(obstacle: MissionObstacle): List<MapCoordinate> = when (obstacle.shape) {
        MissionObstacleShape.CIRCLE -> {
            val center = obstacle.center
            if (center == null) emptyList() else {
                val centerLatLng = LatLng(center.lat, center.lon)
                val ring = (0 until 360 step OBSTACLE_CIRCLE_STEP_DEGREES).mapNotNull { bearing ->
                    SphericalUtil.computeOffset(centerLatLng, obstacle.radiusMeters, bearing.toDouble()).toMapCoordinateOrNull()
                }
                if (ring.isEmpty()) ring else ring + ring.first()
            }
        }
        MissionObstacleShape.POLYGON -> {
            val ring = obstacle.vertices.mapNotNull { it.toMapCoordinateOrNull() }
            if (ring.isEmpty()) ring else ring + ring.first()
        }
    }

    private fun obstacleCenter(obstacle: MissionObstacle): MapCoordinate? {
        obstacle.center?.let { return it.toMapCoordinateOrNull() }
        if (obstacle.vertices.isEmpty()) return null
        return LatLon(obstacle.vertices.map { it.lat }.average(), obstacle.vertices.map { it.lon }.average()).toMapCoordinateOrNull()
    }

    private data class ZoneStyle(val stroke: Int, val fill: Int)

    private fun geoZoneStyle(restriction: GeoZoneRestriction, activeNow: Boolean): ZoneStyle {
        if (!activeNow) return ZoneStyle(argb(235, 31, 41, 55), argb(58, 96, 108, 128))
        return when (restriction) {
            GeoZoneRestriction.PROHIBITED -> ZoneStyle(argb(255, 255, 0, 0), argb(55, 255, 0, 0))
            GeoZoneRestriction.REQ_AUTHORISATION -> ZoneStyle(argb(255, 255, 165, 0), argb(55, 255, 165, 0))
            GeoZoneRestriction.CONDITIONAL -> ZoneStyle(argb(255, 255, 255, 0), argb(55, 255, 255, 0))
            GeoZoneRestriction.INFORMATION -> ZoneStyle(argb(255, 0, 0, 255), argb(55, 0, 102, 255))
            GeoZoneRestriction.UNKNOWN -> ZoneStyle(argb(255, 136, 136, 136), argb(55, 128, 128, 128))
        }
    }

    private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
        (alpha shl 24) or (red shl 16) or (green shl 8) or blue

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    private fun LatLng.toMapCoordinateOrNull(): MapCoordinate? = coordinate(latitude, longitude)

    private fun LatLon.toMapCoordinateOrNull(requireFix: Boolean = false): MapCoordinate? =
        if (requireFix) MapGeometry.coordinateOrNull(lat, lon) else coordinate(lat, lon)

    private fun coordinate(latitude: Double, longitude: Double): MapCoordinate? =
        if (latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0) {
            MapCoordinate(latitude, longitude)
        } else null

    const val ORTHO_RASTER_ID = "ortho"
}
