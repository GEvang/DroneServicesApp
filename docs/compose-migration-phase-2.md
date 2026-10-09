# Jetpack Compose Phase 2 — Technical feasibility prototypes

## Outcome

The two highest-risk rendering areas have working, debug-only Compose prototypes. No production
screen or navigation route is changed.

The migration decision is to keep each specialized renderer behind a narrow Compose adapter:

- Mission maps: retain osmdroid inside one `AndroidView` rendering boundary while Compose owns
  screen layout, controls, dialogs, and state. This preserves the application's proven imagery,
  offline-cache, editing, terrain, orthophoto, attribution, and overlay behavior.
- Point clouds: retain the existing OpenGL renderer inside one `AndroidView` rendering boundary
  while Compose owns viewer controls and lifecycle. Rewriting a responsive renderer solely to
  remove `GLSurfaceView` would add substantial risk without user-visible benefit.

These are deliberate specialized-rendering exceptions. They must not spread into ordinary UI.

## Map capability decision

| Required capability | Google Maps Compose | MapLibre Compose | osmdroid + Compose adapter |
| --- | --- | --- | --- |
| Custom raster imagery | Yes, through tile overlays | Yes | Yes, already in production |
| App-managed offline region/cache | No equivalent to the current workflow | Yes | Yes, already in production |
| Polygon, polyline, marker, and camera state | Yes | Yes | Yes |
| Draggable mission vertices and map gestures | Custom implementation | Custom implementation | Existing implementation |
| Terrain and orthophoto overlays | Custom implementation | Custom implementation | Existing implementation |
| Explicit overlay ordering and attribution | Yes | Yes | Existing implementation |
| Compose API maturity | Stable | Pre-stable | Stable interop boundary |
| Migration risk for this application | High | High | Lowest |

Google Maps Compose documents polygon, polyline, ground, and tile overlays, but it does not replace
the application's explicit osmdroid offline-area download and cache-management workflow. MapLibre
Compose is the strongest future fully Compose-oriented candidate, including offline packs, but its
Compose API is still documented as unstable. Replacing the production engine now would combine an
SDK migration with a UI migration and make behavior parity much harder to prove.

Primary references:

- [Google Maps Compose shapes](https://developers.google.com/maps/documentation/android-sdk/maps-compose)
- [MapLibre Compose documentation](https://maplibre.org/maplibre-compose/)
- [osmdroid repository and API](https://github.com/osmdroid/osmdroid)

The chosen adapter must ultimately expose library-independent state and actions. Production screen
state must never contain `MapView`, osmdroid overlays, Android `View` objects, or callbacks that
manipulate widgets.

## What the map prototype proves

`MapFeasibilityActivity` is registered only in the debug manifest. Its layout and controls are
Compose, with one contained osmdroid render surface. It exercises:

- Esri World Imagery, reference labels, and attribution;
- offline download of the visible area;
- polygon and polyline rendering;
- draggable vertices and long-press vertex insertion;
- obstacle and terrain overlays;
- a generated orthophoto raster overlay;
- camera commands, tap gestures, overlay ordering, and invalidation;
- pause, resume, detach, and bitmap cleanup.

## What the point-cloud prototype proves

`PointCloudFeasibilityActivity` is also debug-only. It renders a 1,600-point synthetic cloud using
the existing `PointCloudGlView`, while Compose owns point-size, opacity, height-color, and camera
reset controls. The adapter forwards lifecycle events. `PointCloudGlView.release()` now releases
point/overlay buffers and its GL program when the surface permanently leaves composition.

## Guardrails for later phases

1. Keep both adapters small and rendering-only.
2. Put screen state, validation, effects, and commands outside Android Views and map SDK types.
3. Do not introduce additional `AndroidView`, `MapView`, or `GLSurfaceView` usage elsewhere.
4. Revisit MapLibre only as a separate map-engine project with a production-parity test matrix.
5. Preserve the debug prototypes until the corresponding production migrations pass equivalent
   device tests; then remove the prototypes.

## Verification gates

- Capability-decision unit tests cover the selected platform and rejected alternatives.
- Compose instrumentation tests assert both render surfaces and their Compose controls.
- The point-cloud instrumentation test pauses and resumes the activity to exercise renderer
  lifecycle handling.
- Debug, debug-test, release, lint, and device-test results are recorded when Phase 2 closes.

## Completed verification

- `assembleDebug`: passed.
- `assembleDebugAndroidTest`: passed.
- `assembleRelease`: passed.
- `lintDebug`: passed.
- `MapPlatformDecisionTest`: 3 tests passed.
- API 36 emulator: both map and point-cloud Compose instrumentation tests passed.
- Full debug unit suite: 164 passed and 1 pre-existing test failed. The remaining failure is
  `MissionServiceImmediateResponseTest.downloadRemembersLegacyProtocolAndReportsProgress`, which
  invokes Android `Log` in a local JVM test without mocking it; it is unrelated to this phase.
