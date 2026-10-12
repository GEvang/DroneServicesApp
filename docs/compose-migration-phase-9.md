# Jetpack Compose Phase 9 — Migrate the mission-planning map

## Outcome

The Home destination now renders entirely in Compose as `MissionMapScreen`. `MissionMapFragment`,
its View Binding, every map-screen XML layout, and the Fragment bridge (`LegacyFragmentHost`) are
gone. Every shell destination is now a Compose screen, so `AppDestination` no longer carries
Fragment factories or container ids, and View Binding is switched off in `app/build.gradle`.

The work was done "map layer first" (Phase 7b below), then all ten screen slices were migrated in
one step.

## Phase 7b — engine-independent map editing

Map editing no longer talks to osmdroid directly. The mission map is described as an immutable
`MapScene` and drawn by the shared `OsmdroidMapSurface` (the same surface Ortho Preview uses).

- `ui/map/model`: `MapMarkerIcon` (aircraft, home, numbered vertices, path/terrain waypoints,
  direction arrows, service stops, text and obstacle labels), marker anchors, click / double-tap /
  drag modes, dashed polygons, `Pan`/`Jump` camera commands, `MapSurfaceHandle` (viewport size,
  projection, user location, offline download) and `MapGeometry` (Mercator arrow rotation, bearing,
  great-circle circles, ring closing, "no fix" filtering).
- `ui/map/osmdroid`: `OsmdroidMapSurface` reconciles overlays by id, renders `MapMarkerIcon`s
  through `OsmdroidMarkerIcons` (bitmap code ported from the old editors), supports immediate and
  long-press marker drags, tap and double-tap on draggable markers, user location, and throttles
  redraws to 200 ms.
- `ui/home/map`: Compose-state layers that replace the old osmdroid controllers and editors —
  `AircraftLayerState`, `SurveyPathLayerState`, `PolygonEditorState`, `RouteEditorState`,
  `ObstacleEditorState`, `GeoZoneLayerState`, `TerrainCoverageLayerState` — gathered by
  `MissionMapLayers`, which also routes taps (home placement → obstacle placement → route → survey
  waypoint → area vertex → geo-zone details). `MissionMapSceneBuilder` turns the layers into the
  scene.

## Screen structure

- `MissionMapController` is the former Fragment orchestration: the same coordinators and
  observers, writing into small, independent state sections instead of widgets
  (`MapDockState`, `ObstaclePanelState`, `WaypointEditDockState`, `MissionSummaryCardState`,
  `WorkflowPanelState`, `WeatherPanelState`, `MissionDownloadState`, `MissionFilesState`,
  `SafetyControlsState`, `PreviewPanelState`). There is no single large `MissionMapUiState`.
- `CompositionLifecycleOwner` gives the controller the lifetime a Fragment view had: observers are
  registered while it is CREATED, it then follows the navigation entry up to RESUMED, and it is
  destroyed when the screen leaves composition. LiveData delivery order is therefore unchanged.
- Dialogs go through `MissionDialogQueue` (message, confirmation, overlapping geo-zone list);
  toasts and snackbars through `MissionMessages`.
- The map camera is kept in `MissionMapViewModel`, so returning to Home reopens the map where the
  operator left it.

| Slice | Compose implementation |
| --- | --- |
| Telemetry strip | `MapTopStatusStrip` (GPS/RTK detail dialogs, arm and flight-mode cards) |
| Workflow controls | `WorkflowPanel` |
| Survey / spray parameters | `MissionParamsPanel` + `MissionParamsPresentation` |
| Point workflow | `RouteEditorState`, `PointRouteCoordinator`, waypoint edit dock |
| Obstacle editing | `ObstacleEditorState`, `ObstaclePanel` |
| Terrain controls | `TerrainMissionCoordinator`, terrain coverage layer, params panel status |
| Preview mode | `PreviewModeRail`, embedded `PointCloudGlView` in 3D mode |
| Weather / geo panels | `WeatherPanel`, live geo status in the strip, geo-zone layer and dialogs |
| Upload / start / arm / confirmation | `MissionUploadCoordinator`, `MissionSummaryPresenter`, `FlightModePopup`, `SlideToArmPopup` |
| Responsive layout | `config_tablet_planning_dock`, `config_map_draw_bar_replaces_dock` and the existing panel dimensions |

## Preserved behavior

- Map gestures, vertex numbering and ordering, long-press vertex drags, route waypoint drags,
  survey arrows (screen-space Mercator angle) and route arrows (geodesic bearing) match the old
  editors. While a route waypoint is dragged, the line still follows the authored waypoints.
- Entering Home still posts `MapState.Idle` and restores the view-model map state in the same
  order; the first-entry focus still prefers a pending preview, then the drone, then the operator.
- Upload summary, terrain download, resume-after-service, mission simulation, save/load (`.dsfp`),
  obstacle placement, drone offset adjustment and offline tile download keep their messages.
- Hold-to-confirm for RTL/LAND, slide-to-confirm for AUTO and slide-to-arm keep their deliberate
  gestures (thumb must reach the end with at least 65% finger travel).
- Phone and tablet keep their separate docks; on phones the drawing bar still replaces the dock.

## Intentional differences

- Tapping a measurement label no longer pans the map.
- Route waypoint tap and double-tap now work reliably (immediate-drag markers previously swallowed
  them).
- Save, Upload and Download terrain work before the parameters panel has been opened once.
- The preview rail's dataset file pickers, which were permanently hidden, were not ported; datasets
  are managed on the Datasets screen.
- The 3D view is created when 3D mode is selected and released when leaving it, so its camera
  resets on each entry.

## Removed legacy UI

- `MissionMapFragment`, `LegacyFragmentHost`, `compose_shell_ids.xml`
- All layouts under `res/layout*` (`fragment_home_maps.xml` in four configurations,
  `home_workflow_panel*.xml`, `mission_params_side_view.xml`, `home_top_status_strip.xml`,
  `home_obstacle_panel.xml`, `view_*` popups and panels, `item_mission_file.xml`, …)
- Osmdroid controllers/editors: `OsmdroidMapController`, `OsmdroidPolygonEditor`,
  `OsmdroidRouteWaypointEditor`, `OsmdroidObstacleEditor`, `TerrainCoverageOverlayController`,
  `GeoZoneOverlayController`, `OrthoOverlayController`
- View binders: `HomeMap{Chrome,Panels,ModeEffects,Telemetry}Binder`, `ArmUiBinder`,
  `FlightModeUiBinder`, `MissionParams{Controller,Renderer,InputBinder,Views}`,
  `MissionSaveController`, `MissionLoadController`, `MissionMapUiActionController`,
  `MissionMapControllerFactory`, `PlanningWorkflowUiController`, `HomeMapViewLifecycleController`,
  `LiveGeoAwarenessStatusViewBinder`, `GeoDatasetStatusViewBinder`

## Tests

- Unit: `MapGeometryTest`, `MissionMapSceneBuilderTest`, `MissionScreenStateTest` (dialog queue,
  screen lifecycle); `AppDestinationTest` updated now that no destination is a Fragment.
- Instrumentation: `MainActivityShellTest` checks that Home renders the Compose map, strip, preview
  rail and dock, and that the planning panel and map survive navigation and activity recreation.

## Manual verification

- Draw an area: add, drag and delete vertices, accept and decline; check the area label.
- Generate survey and spray missions; select a survey waypoint, change its height, delete it.
- Point workflow: add, drag, tap and double-tap route waypoints; reverse the route; edit terrain
  waypoint heights from the map and from 3D.
- Add circle and polygon obstacles; remove one; clear all.
- Switch Map / Ortho / 3D; verify ortho opacity and 3D point picking.
- Weather panel, geo-zone taps (single and overlapping zones), live geo status.
- Save and load a mission; upload, start, arm, flight-mode changes (RTL/LAND hold, AUTO slide).
- Rotate the device and leave/return to Home during planning; check phone and tablet layouts.

## Verification status

This change was written without a Gradle build (the build hosts were unreachable from the
authoring environment). Resource references, internal imports and controller/state members were
checked statically; compile, run the unit and instrumentation tests, and complete the manual
checks above before merging.
