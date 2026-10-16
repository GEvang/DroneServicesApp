# Jetpack Compose plan Phase 7 — Map and orthophoto platform (completion)

The map platform was introduced earlier (see `jetpack-compose-phase-7-8.md`) and adopted by the
mission map in Phase 9. This change closes the remaining Phase 7 items: the engine boundary,
offline behaviour, and performance with realistic mission data.

## Engine boundary (osmdroid stays behind one adapter)

Every osmdroid import now lives in `ui/map/osmdroid`:

| Moved / added | Before | Now |
| --- | --- | --- |
| Esri imagery + labels layers | `ui/home/components/EsriWorldImageryTileSource.kt` | `ui/map/osmdroid/EsriMapLayers.kt` |
| Orthophoto raster overlay | `ui/ortho/OrthoImageOverlay.kt` | `ui/map/osmdroid/OrthoImageOverlay.kt` |
| Map label preference | `ui/home/components/MapDisplayPreferences.kt` | `ui/map/MapDisplayPreferences.kt` |
| osmdroid start-up configuration | inline in `Application.java` | `OsmdroidMapEngine.configure()` |
| Offline cache size / clear (Settings) | `Configuration.getInstance()` in the screen | `MapTileCache` via `MapEngine.tileCache` |

- `ui/map/MapTileCache` is engine-independent; `ui/map/MapEngine` is the single binding to the
  selected engine. Changing engines means changing that binding and the map surface only.
- `MapSurfaceHandle.visibleBounds()` exposes the visible area as `MapBounds`.
- `MapEngineBoundaryTest` (unit test) fails the build if osmdroid is imported outside
  `ui/map/osmdroid`, or if `domain`/`data` code imports Compose, osmdroid or the map UI models.

Known, deliberately out of scope: the domain layer still uses Google's `LatLng`/`SphericalUtil`
as geometry value types (not a rendering SDK), and `domain/planning/PointRouteCoordinator` reads
view models directly. Both pre-date the migration.

## Offline behaviour

- Unchanged: imagery and labels are cached by osmdroid (1 GB cap, trimmed to 800 MB); the dock's
  Offline button downloads the visible area at zoom 14–18 for both layers.
- New: before downloading, the area is sized with `MapGeometry.tileCount` (Web-Mercator XYZ grid).
  Areas above `OFFLINE_MAX_TILES` (20,000 imagery tiles) are refused with a message asking the
  operator to zoom in. A full landscape view at zoom 14 (~12,000 tiles) is allowed; zoom 13
  (~53,000) is not. This prevents multi-hour downloads and stops one oversized download from
  evicting previously saved offline areas when the cache trims.
- Offline display needs no special mode: osmdroid serves cached tiles when the network is absent.

## Performance with realistic mission data

- `MissionMapScenePerformanceTest` builds a 40 ha survey (~2,500 waypoints), a 5,000-point flight
  trace, 40 geo-zones and 25 obstacles, and checks the build stays well under a generous time
  budget, direction arrows stay capped at 80, and every layer is present.
- The map surface already reconciles overlays by id, throttles redraws to 200 ms and caches
  marker bitmaps (LRU 256).
- Watch item for device testing: one waypoint marker is drawn per survey waypoint (as in the
  original screen). Very large surveys are the case to profile.

## Device checklist (cannot be automated here)

1. **Offline area:** online, frame a field at zoom ~15, tap Offline; wait for "complete".
2. **Guard:** zoom out to ~13, tap Offline; expect the "zoom in" message and no download.
3. **Offline use:** enable airplane mode, restart the app, open that field at zoom 14–18;
   imagery and labels must appear. Areas outside it show the empty tile background.
4. **Cache:** Settings → offline maps shows a size; clear it; offline imagery disappears.
5. **Performance:** plan the largest realistic field; pan/zoom with the survey visible and
   during simulation; check smoothness and memory in Android Studio's profiler.
6. **Ortho:** load a large orthophoto, toggle Map/Ortho, change opacity; check alignment.
