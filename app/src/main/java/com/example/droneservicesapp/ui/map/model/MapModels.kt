package com.example.droneservicesapp.ui.map.model

/** Map-engine-independent coordinate used at the UI/platform boundary. */
data class MapCoordinate(val latitude: Double, val longitude: Double) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
    }
}

data class MapCamera(val center: MapCoordinate, val zoom: Double)

enum class MapOverlayLayer(val drawOrder: Int) {
    ORTHOPHOTO(10), TERRAIN(20), GEO_AWARENESS(30), MISSION(40), AIRCRAFT(50),
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

data class MapMarkerModel(
    val id: String,
    val position: MapCoordinate,
    val title: String? = null,
    val rotationDegrees: Float = 0f,
    val draggable: Boolean = false,
    val layer: MapOverlayLayer = MapOverlayLayer.MISSION,
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

    data class Move(override val token: Long, val camera: MapCamera) : MapCameraCommand
    data class Fit(override val token: Long, val bounds: MapBounds, val paddingPx: Int = 80) : MapCameraCommand
}

data class MapInteractionHandlers(
    val onTap: (MapCoordinate) -> Unit = {},
    val onLongPress: (MapCoordinate) -> Unit = {},
    val onMarkerMoved: (String, MapCoordinate) -> Unit = { _, _ -> },
    val onCameraChanged: (MapCamera) -> Unit = {},
)
