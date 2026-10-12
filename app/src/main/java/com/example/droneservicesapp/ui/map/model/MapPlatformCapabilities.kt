package com.example.droneservicesapp.ui.map.model

enum class MapCapability {
    CUSTOM_IMAGERY, APP_MANAGED_OFFLINE_CACHE, POLYGONS, POLYLINES, DRAGGABLE_VERTICES,
    OBSTACLES, TERRAIN_OVERLAYS, RASTER_ORTHO_OVERLAYS, CAMERA_CONTROL, GESTURES,
    OVERLAY_ORDERING, ATTRIBUTION,
}

enum class MapApiMaturity { STABLE, PRE_STABLE }

enum class MapPlatform(
    val supportedCapabilities: Set<MapCapability>,
    val composeApiMaturity: MapApiMaturity,
) {
    GOOGLE_MAPS_COMPOSE(MapCapability.entries.toSet() - MapCapability.APP_MANAGED_OFFLINE_CACHE, MapApiMaturity.STABLE),
    MAPLIBRE_COMPOSE(MapCapability.entries.toSet(), MapApiMaturity.PRE_STABLE),
    OSMDROID_COMPOSE_INTEROP(MapCapability.entries.toSet(), MapApiMaturity.STABLE),
}

val requiredMapCapabilities: Set<MapCapability> = MapCapability.entries.toSet()
fun MapPlatform.satisfies(capabilities: Set<MapCapability>): Boolean = supportedCapabilities.containsAll(capabilities)
val selectedMapPlatform: MapPlatform = MapPlatform.OSMDROID_COMPOSE_INTEROP
