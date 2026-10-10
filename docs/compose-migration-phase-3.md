# Jetpack Compose Phase 3 — Define UI architecture and state contracts

## Outcome

The application now has a platform-neutral UI contract layer that Compose screens can consume
without inheriting Fragment, View Binding, Android widget, or controller APIs. Production XML
screens continue to behave as before through temporary compatibility adapters.

## Standard screen contract

Every migrated screen follows the same data flow:

1. An immutable `UiState` is the complete, replayable rendering input.
2. A sealed `Action` describes every operator interaction.
3. A reducer or state holder applies state changes and sends domain commands.
4. A separate `Effect` stream describes one-time work such as navigation, file pickers,
   permissions, snackbars, or confirmation dialogs.
5. Compose collects state and effects with lifecycle awareness.

`UiStateStore` is Android-free and exposes `StateFlow` plus a non-state effect `Flow`.
`CollectUiEffects` uses `repeatOnLifecycle`, preventing inactive screens from handling effects.

## Implemented contracts

### Geo-awareness

- `GeoAwarenessUiState` owns dataset, validation, health, event, busy, and error state.
- `GeoAwarenessAction` replaces callback bundles with explicit operator and state-update actions.
- `GeoAwarenessEffect` represents dataset refresh, picker, export, validation, and verification
  work.
- `GeoAwarenessViewModel` exposes state and effects without a Fragment or binding dependency.
- Status and event-log decisions have pure presentation mappers. The former presenters that
  manipulate XML are explicitly named temporary `ViewBinder` adapters.

### Mission map and controls

- `MissionMapAction` covers map chrome, geometry, obstacles, preview controls, and waypoints.
- `MissionParamsAction` covers all parameter editing and mission commands.
- `MissionParamsPresenter` derives field visibility, altitude rules, terrain download state,
  progress, and upload availability without Android Views.
- `PreviewModePresenter` derives rendering-surface and asset-control state.
- Flight-mode and arm controls have immutable state, explicit actions, and pure mappers.
- `MissionMapViewModel` and `HomeTelemetryViewModel` expose `StateFlow` as the primary API.
  Their LiveData properties are temporary compatibility mirrors for current XML binders.

## Ownership rules for later phases

| Concern | Owner |
| --- | --- |
| Replayable screen content and enabled state | Immutable `UiState` |
| Operator interaction | Sealed `Action` |
| Navigation, picker, permission, snackbar, dialog | One-time `Effect` |
| Business operation | Existing domain coordinator/service |
| Rendering | Compose screen or specialized map/OpenGL adapter |
| Temporary XML mutation | Clearly named legacy `ViewBinder` only |

Business coordinators must not import Compose. Compose screens must not receive a View, binding,
Fragment, widget, map SDK object, or callback that mutates one.

## Visible behavior

Phase 3 deliberately changes no normal application visuals. The existing screens now feed the new
state contracts in parallel so later Compose replacements can be introduced without rewriting
business workflows at the same time.

## Manual regression checks

The automated gates cover reducers and state mapping. Before merging, the useful manual smoke
checks are:

1. Open Geo-awareness, toggle its overlay, refresh status, and open validation details.
2. Open the mission map and show/hide its planning panel.
3. Verify telemetry still updates and the arm/flight-mode popups still open normally.

No new control or screen should appear in the production app during this phase.

## Completed verification

- `assembleDebug`: passed.
- `assembleDebugAndroidTest`: passed.
- `assembleRelease`: passed.
- `lintDebug`: passed.
- New architecture/state/presentation tests: 15 passed.
- Full debug unit suite: 178 passed and 1 pre-existing test failed. The remaining failure is
  `MissionServiceImmediateResponseTest.downloadRemembersLegacyProtocolAndReportsProgress`, which
  invokes Android `Log` in a local JVM test without mocking it; it is unrelated to Phase 3.
- All 3 Compose instrumentation tests passed on both an API 35 Pixel Tablet emulator and an API 36
  Medium Phone emulator.
