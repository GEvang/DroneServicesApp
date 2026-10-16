package com.example.droneservicesapp.ui.map.model

/** Map-engine-independent coordinate used at the UI/platform boundary. */
data class MapCoordinate(val latitude: Double, val longitude: Double) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
    }
}

data class MapCamera(val center: MapCoordinate, val zoom: Double)

/**
 * Draw order of overlay groups, bottom to top. Within one layer the scene keeps the order
 * rasters, polygons, polylines, markers, and then each list's own order.
 */
enum class MapOverlayLayer(val drawOrder: Int) {
    ORTHOPHOTO(10),
    TERRAIN(20),
    GEO_AWARENESS(30),
    MISSION(40),
    FLIGHT_TRACE(42),
    MISSION_DETAIL(44),
    OBSTACLES(46),
    EDIT_HANDLES(48),
    /** Measurement and area labels: above waypoint and vertex handles so they stay readable. */
    LABELS(49),
    AIRCRAFT(50),
}

data class MapBounds(
    val southWest: MapCoordinate,
    val northEast: MapCoordinate,
) {
    init {
        require(southWest.latitude <= northEast.latitude)
        require(southWest.longitude <= northEast.longitude)
    }

    val center: MapCoordinate
        get() = MapCoordinate(
            (southWest.latitude + northEast.latitude) / 2.0,
            (southWest.longitude + northEast.longitude) / 2.0,
        )
}

enum class MapMarkerAnchor { CENTER, BOTTOM_CENTER }

/** How a draggable marker starts a drag gesture. */
enum class MapMarkerDragMode {
    /** The marker cannot be dragged. */
    NONE,

    /** The map engine's own long-press-then-drag gesture. */
    LONG_PRESS,

    /** Drag starts as soon as the finger moves past a small slop on the marker. */
    IMMEDIATE,
}

enum class MapServiceStopKind { BATTERY, TANK }

enum class MapLabelStyle {
    /** Perimeter side length label. */
    MEASUREMENT,

    /** Emphasized polygon-area label. */
    AREA,

    /** Compact point-route segment distance label. */
    DISTANCE,
}

enum class MapVertexStyle { AREA, ROUTE }

/** Engine-independent marker artwork. The map adapter owns the actual bitmaps. */
sealed interface MapMarkerIcon {
    /** The map engine's default pin. */
    data object Default : MapMarkerIcon

    /** Aircraft symbol; [highlighted] draws the offset-adjustment/simulation halo. */
    data class Aircraft(val highlighted: Boolean = false) : MapMarkerIcon

    data object Home : MapMarkerIcon

    data class NumberedVertex(
        val number: Int,
        val style: MapVertexStyle,
        val selected: Boolean = false,
    ) : MapMarkerIcon

    data class PathWaypoint(val selected: Boolean) : MapMarkerIcon

    data class TerrainWaypoint(val selected: Boolean) : MapMarkerIcon

    data class DirectionArrow(val withShadow: Boolean) : MapMarkerIcon

    data class ServiceStop(val kind: MapServiceStopKind) : MapMarkerIcon

    data class TextLabel(
        val text: String,
        val style: MapLabelStyle,
        /** Screen-space offset from the anchor point, in density-independent pixels. */
        val offsetXDp: Float = 0f,
        val offsetYDp: Float = 0f,
    ) : MapMarkerIcon

    data class ObstacleLabel(val letter: String) : MapMarkerIcon
}

data class MapMarkerModel(
    val id: String,
    val position: MapCoordinate,
    val title: String? = null,
    val rotationDegrees: Float = 0f,
    val draggable: Boolean = false,
    val layer: MapOverlayLayer = MapOverlayLayer.MISSION,
    val icon: MapMarkerIcon = MapMarkerIcon.Default,
    val anchor: MapMarkerAnchor = MapMarkerAnchor.BOTTOM_CENTER,
    /** Taps on the marker are reported through [MapInteractionHandlers.onMarkerClick]. */
    val clickable: Boolean = false,
    /** Double taps on the marker are reported through [MapInteractionHandlers.onMarkerDoubleTap]. */
    val doubleTappable: Boolean = false,
    val dragMode: MapMarkerDragMode = if (draggable) MapMarkerDragMode.LONG_PRESS else MapMarkerDragMode.NONE,
)

data class MapPolylineModel(
    val id: String,
    val points: List<MapCoordinate>,
    val colorArgb: Int,
    val widthPx: Float = 5f,
    val layer: MapOverlayLayer = MapOverlayLayer.MISSION,
)

data class MapPolygonModel(
    val id: String,
    val points: List<MapCoordinate>,
    val strokeColorArgb: Int,
    val fillColorArgb: Int,
    val strokeWidthPx: Float = 4f,
    val layer: MapOverlayLayer = MapOverlayLayer.MISSION,
    val dashed: Boolean = false,
)

/** Bitmap pixels are supplied separately by id so this scene remains independent of Android. */
data class MapRasterModel(
    val id: String,
    val bounds: MapBounds,
    val opacity: Float = 1f,
    val layer: MapOverlayLayer = MapOverlayLayer.ORTHOPHOTO,
) {
    init { require(opacity in 0f..1f) }
}

fun MapScene.orderedOverlayIds(): List<String> = buildList {
    addAll(rasters.map { it.layer to it.id })
    addAll(polygons.map { it.layer to it.id })
    addAll(polylines.map { it.layer to it.id })
    addAll(markers.map { it.layer to it.id })
}.sortedBy { it.first.drawOrder }.map { it.second }

data class MapScene(
    val rasters: List<MapRasterModel> = emptyList(),
    val polygons: List<MapPolygonModel> = emptyList(),
    val polylines: List<MapPolylineModel> = emptyList(),
    val markers: List<MapMarkerModel> = emptyList(),
) {
    init {
        val ids = rasters.map { it.id } + polygons.map { it.id } +
            polylines.map { it.id } + markers.map { it.id }
        require(ids.size == ids.distinct().size) { "Map scene ids must be unique." }
    }
}

sealed interface MapCameraCommand {
    val token: Long

    /** Sets the zoom and animates to [camera]'s center. */
    data class Move(override val token: Long, val camera: MapCamera) : MapCameraCommand

    data class Fit(override val token: Long, val bounds: MapBounds, val paddingPx: Int = 80) : MapCameraCommand

    /** Animates to [center] while keeping the current zoom. */
    data class Pan(override val token: Long, val center: MapCoordinate) : MapCameraCommand

    /** Jumps to [center] and [zoom] without animation. */
    data class Jump(override val token: Long, val center: MapCoordinate, val zoom: Double) : MapCameraCommand
}

data class MapInteractionHandlers(
    val onTap: (MapCoordinate) -> Unit = {},
    val onLongPress: (MapCoordinate) -> Unit = {},
    val onMarkerMoved: (String, MapCoordinate) -> Unit = { _, _ -> },
    val onCameraChanged: (MapCamera) -> Unit = {},
    /** Returns true when the tap was handled; only markers with `clickable = true` are reported. */
    val onMarkerClick: (String) -> Boolean = { false },
    /** Returns true when the double tap was handled; only `doubleTappable` markers are reported. */
    val onMarkerDoubleTap: (String) -> Boolean = { false },
    val onMarkerDragStart: (String) -> Unit = {},
    /** Continuous position updates while a marker is being dragged. */
    val onMarkerDrag: (String, MapCoordinate) -> Unit = { _, _ -> },
)

data class MapScreenPoint(val x: Float, val y: Float)

/**
 * Engine-independent access to the live map surface for the few operations that are not
 * expressible as scene state: projection, the operator's location, and offline downloads.
 */
interface MapSurfaceHandle {
    val viewportWidthPx: Int
    val viewportHeightPx: Int
    fun currentCamera(): MapCamera
    fun userLocation(): MapCoordinate?
    fun toScreenPoint(coordinate: MapCoordinate): MapScreenPoint?

    /** The visible area, or null before layout or when it wraps the antimeridian. */
    fun visibleBounds(): MapBounds?

    /**
     * Downloads the visible area for offline use. [onProgress] reports 0–100 across all map layers
     * on the main thread; [onComplete] reports the number of failed tiles. Cancelling through the
     * returned handle stops the download without calling [onComplete].
     */
    fun downloadVisibleRegion(
        minZoom: Int,
        maxZoom: Int,
        onProgress: (percent: Int) -> Unit,
        onComplete: (errorCount: Int) -> Unit,
    ): MapDownloadHandle
}

/** A running offline download. */
fun interface MapDownloadHandle {
    fun cancel()
}
