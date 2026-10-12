# Jetpack Compose Phases 7 and 8

## Phase 7 — Map and orthophoto platform

The production map boundary now uses engine-independent camera, bounds, marker, polyline, polygon,
raster, scene, command, and interaction models. `OsmdroidMapSurface` is the only production Compose
adapter for osmdroid. It owns renderer lifecycle, Esri imagery/labels/attribution, overlay ordering,
map gestures, camera observation, long-press input, and draggable marker callbacks.

The Ortho Preview route is a direct Compose destination. TIFF and world-file parsing runs on IO,
document permissions and references persist, raster opacity/background controls update live, camera
position survives recomposition/navigation, fit-to-raster is command-driven, and mission/aircraft
geometry is projected through the library-independent scene. Its Fragment and XML layout are gone.

The existing Mission Map remains available until Phase 9, as required by the migration plan. It can
adopt the same platform incrementally while its workflow controls are migrated.

## Phase 8 — Point-cloud screen

The Point Cloud route is a direct Compose destination. Compose owns all viewer controls and the file
workflow; the existing `PointCloudGlView` is retained behind one contained `AndroidView`. Parsing is
off the main thread, the shared preview/terrain asset is updated, mission geometry and the aircraft
remain visible, and pause/resume/release follow the Compose lifecycle.

The renderer now preserves its EGL context across pauses and renders on demand instead of
continuously. Camera gestures request frames explicitly, reducing idle GPU work and route-switching
pressure. The legacy PointCloud Fragment and XML layout are gone.

## Verification matrix

- Debug and release compilation.
- Unit tests for platform model invariants and destination routing.
- Compose instrumentation verifies both production routes and render surfaces.
- Existing TIFF/world-file, point-cloud parser, terrain, mission-overlay, and navigation tests remain
  part of the full suite.
- Manual device checks: raster alignment/opacity/background/fit; offline imagery behavior; point-cloud
  load/rotate/pan/zoom/reset; app background/foreground; repeated Map → Ortho → 3D → Map cycles.
