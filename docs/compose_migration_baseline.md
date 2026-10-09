# Jetpack Compose migration baseline

Recorded on 2026-10-09 before production migration work.

## Repository baseline

- Branch: `Polishing`
- Commit: `dc8016a`
- Working tree before this document: clean
- Application namespace: `com.example.droneservicesapp`
- Minimum SDK: 30
- Compile SDK: 33
- Target SDK: 33

## Verified build environment

- JDK: Oracle JDK 17.0.19
- Gradle wrapper: 8.13
- Android Gradle Plugin: 8.13.2
- Kotlin Gradle plugin: 1.8.20-RC
- Installed Android platforms: 33, 34, 36

The machine-wide JDK 26 installation cannot create the Android unit-test tasks with this baseline toolchain. Migration verification must explicitly use JDK 17 until the toolchain phase establishes a new supported JDK.

## Build and test results

Commands were run with `JAVA_HOME=C:\Program Files\Java\jdk-17.0.19` and configuration cache disabled.

| Check | Result |
| --- | --- |
| `assembleDebug` | Passed |
| `assembleRelease` | Passed, including release vital lint |
| `testDebugUnitTest` | 161 passed, 1 failed out of 162 |

The single existing failure is:

`io.dronefleet.mavlink.MissionServiceImmediateResponseTest.downloadRemembersLegacyProtocolAndReportsProgress`

The failure is deterministic when run alone. It is caused by JVM unit-test execution reaching unmocked `android.util.Log` methods (`Log.w`, followed by other log calls), rather than a failed behavioral assertion. This is a pre-existing baseline failure and must not be attributed to Compose work. It should either be repaired separately or kept visible while migration regressions are evaluated.

The build also reports existing deprecation warnings, including legacy activity-result APIs, system UI flags, osmdroid zoom controls, and MAVLink message types. These warnings are baseline observations, not Phase 0 changes.

## UI migration inventory

| Surface | Baseline count |
| --- | ---: |
| Fragment destinations/classes | 10 |
| Layout XML files | 40 |
| Layout resource directories | 4 |
| Kotlin/Java files referencing generated bindings | 22 |
| Kotlin/Java files using layout inflation | 14 |
| XML menu resources | 3 |
| XML navigation graphs | 1 |
| Files coupled to osmdroid or `MapView` | 17 |
| Files containing the custom `GLSurfaceView` implementation | 1 |

Layout distribution:

- `res/layout`: 29
- `res/layout-land`: 3
- `res/layout-sw533dp-land`: 5
- `res/layout-sw600dp`: 3

Current destinations:

1. Mission map
2. Settings
3. MAVLink network
4. Datasets
5. RTK
6. Geo-awareness
7. Parameters
8. Point-cloud viewer
9. Orthophoto preview
10. Debug/vehicle parameters

## Critical regression workflows

These workflows must remain operational during the incremental migration and form the manual/device acceptance checklist for affected phases.

### Application shell and lifecycle

- Cold launch and process recreation
- Drawer, bottom navigation, toolbar, and back navigation
- Landscape layouts at the default, `sw533dp-land`, and `sw600dp` sizes
- Immersive/full-screen behavior and keeping the screen awake
- English and Greek locale switching
- Pause/resume without duplicated observers, dialogs, or MAVLink sessions

### Vehicle and MAVLink

- UDP/TCP MAVLink configuration and reconnection
- Connection-state and telemetry updates
- Reading and writing vehicle parameters
- Flight-mode changes
- Arm/disarm and slide-to-arm behavior
- Mission upload, download, start, cancellation, and confirmation flows
- RTK connection and RTCM forwarding

### Mission map

- Online imagery, cache behavior, and attribution
- Camera restoration and centering on vehicle/user
- Survey polygon creation and draggable vertex editing
- Point-route and waypoint editing
- Obstacle creation and editing
- Terrain coverage and terrain downloads
- Route preview, reversal, ordering, and simulation
- Geo-awareness zones, warnings, and upload guards
- Weather/wind presentation
- Orthophoto overlays and overlay ordering

### Files and large data

- Dataset import, selection, replacement, deletion, and error handling
- Mission file load/save
- Orthophoto plus world-file selection, decoding, pan, and zoom
- Point-cloud file selection, rendering, gestures, camera reset, and resource cleanup
- Responsiveness and memory behavior with representative large inputs

### Permissions and failure states

- Location permission grant, denial, and permanent denial
- File-picker cancellation
- Disconnected aircraft
- Unsupported firmware parameters
- Network loss and recovery
- Invalid, missing, or corrupt datasets
- Background work cancellation and stale-result suppression

## Phase 0 exit state

- No production source, resource, dependency, or build configuration was changed.
- The build environment and known failure are documented.
- Migration surface counts are recorded for later comparison.
- Critical workflows are recorded for phase-specific regression checks.
