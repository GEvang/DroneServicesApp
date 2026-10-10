# Jetpack Compose Phase 4 — Build the shared component system

## Outcome

The reusable Compose component layer required by later screen migrations is complete. Components
are state-driven, use the Phase 3 action pattern, and do not depend on Fragments, generated
bindings, Android widgets, repositories, or business coordinators.

Production navigation is intentionally unchanged. A debug-only component gallery provides an
interactive verification surface without exposing unfinished Compose screens to users.

## Component inventory

### Core controls

- `AppButton` with primary, secondary, destructive, and text styles.
- `AppTextField` and `NumericInputField` with error/supporting states.
- `AppDropdown` with externally owned selection and expanded state.
- `ExpandableSection` with controlled expansion state.

### Status, shell, and feedback

- `StatusIndicator` and `ConnectionStateBadge`.
- `TelemetryValue`.
- `ToolbarAction` and `NavigationItem`.
- `ProgressPanel` and `ErrorPanel` with accessible live-region behavior.

### Dialogs and workflows

- `ConfirmationDialog` with an explicit destructive style.
- `FileSelectionDialog` with externally owned selection.
- `SlideToArmControl` with an explicit completion result.
- `FlightModeSelector` with current, pending, confirmation, warning, and destructive states.

### Mission and operational panels

- `MissionParameterRow` and `MissionSummaryCard`.
- `ObstacleControls` for shape, radius, add, and clear actions.
- `WeatherWindPanel` with safe/stale states.
- `GeoAwarenessStatusPanel` with health, dataset, validation, overlay, and busy state.

These replace the visual responsibilities of repeated XML resources such as flight-mode and arm
popups, parameter fields, weather panels, obstacle panels, toolbar/status rows, live geo panels,
and terrain progress controls. The XML resources remain until their owning screens migrate.

## Component rules

1. State is supplied by the caller; components never query a ViewModel or repository.
2. User interactions are emitted through typed values or event lambdas.
3. Every button and row has at least a 48dp touch target or uses a Material control that provides
   the equivalent minimum interactive size.
4. Status changes and errors use accessibility live regions where appropriate.
5. Labels, values, selected state, and confirmation requirements are exposed through semantics.
6. Components use `DroneServicesTheme` colors, typography, shapes, and spacing.

## Preview and test coverage

- Core controls have focused previews for normal, error, progress, and destructive states.
- Safety controls preview slide-to-arm and every important flight-mode state.
- Mission panels render under all supported landscape preview sizes.
- `ComponentGalleryActivity` is debug-only and demonstrates the complete set interactively.
- Instrumentation verifies controlled state changes, confirmation behavior, and the minimum button
  touch height.

## Visible behavior

There are no changes in the normal application yet. Phase 4 creates the reusable visual language
that subsequent phases will use when production screens are replaced.

The debug gallery can be launched explicitly by developers as
`com.example.droneservicesapp.ui.compose.components.ComponentGalleryActivity`.

## Verification

- `assembleDebug`, `assembleDebugAndroidTest`, `assembleRelease`, and `lintDebug` pass.
- All 5 connected instrumentation tests pass on the Pixel Tablet API 35 emulator, including the
  2 component-gallery tests added in this phase.
- The unit suite runs 179 tests: 178 pass. The existing
  `MissionServiceImmediateResponseTest.downloadRemembersLegacyProtocolAndReportsProgress` test
  still fails because its local-JVM execution reaches the unmocked Android `Log` implementation;
  Phase 4 does not modify that service or test.
