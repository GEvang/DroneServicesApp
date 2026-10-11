# Jetpack Compose Phase 6 — Migrate the straightforward screens

## Outcome

Settings, MAVLink Network, RTK, Parameters, and Debug/vehicle parameters now render as native
Compose destinations. They no longer create Fragments, inflate XML layouts, use View Binding, or
depend on RecyclerView adapters. The production destination router sends these five routes directly
to Compose while the temporary Fragment bridge remains only for screens assigned to later phases.

## Preserved behavior

- Settings retains operation-mode and language selection, map-label preferences, geo-awareness
  dataset summaries, and offline-map cache reporting and clearing.
- MAVLink Network retains UDP/TCP selection, local and target addressing, GCS system ID, bridge
  configuration, preference-backed persistence, and input validation.
- RTK retains encrypted configuration persistence, mountpoint discovery and selection, connection
  testing, automatic forwarding reconfiguration, status and GPS diagnostics, base coordinates,
  and connection/disconnection tones.
- Parameters retains activity-scoped vehicle state, connection-triggered refresh, supported-state
  gating, parameter writes, and success/error feedback.
- Debug retains its PIN gate, access controls, terrain safeguard override, searchable and editable
  vehicle parameter catalog, DataFlash log transfer, diagnostics export and incident marking, and
  sprayer PWM controls.

## Removed legacy UI

- `ParametersFragment`, `MavlinkNetworkFragment`, `RtkFragment`, `SettingsFragment`, and
  `DebugFragment`
- `VehicleParameterAdapter`
- `fragment_parameters.xml`, `fragment_mavlink_network.xml`, `fragment_rtk.xml`,
  `fragment_settings.xml`, `fragment_debug.xml`, and `item_vehicle_parameter.xml`

## Architecture and tests

- `ProductionDestinationContent` is the single production mapping between typed destinations and
  either a Compose screen or the remaining legacy bridge.
- Migrated `AppDestination` entries intentionally have no Fragment factory; a unit test enforces
  this boundary.
- Each migrated screen has a stable semantics tag, and instrumentation visits all five production
  routes and verifies their Compose roots are displayed.
- Unit tests cover MAVLink IPv4 validation and offline-cache size formatting.

## Visible behavior

These five destinations now share the Compose dark theme, responsive one/two-column panel layout,
consistent touch targets, and Material dialogs and controls. On a tablet or landscape display,
Settings, MAVLink, and RTK use two columns; they collapse to one scrollable column on narrower
screens. Debug uses a Compose list for large vehicle parameter catalogs.

## Verification

- Debug, release, and instrumentation APK assembly pass; `lintDebug` passes.
- All Phase 6 unit tests pass, including destination-boundary, IPv4-validation, and storage-size
  formatting coverage.
- All 9 connected instrumentation tests pass on the Pixel Tablet API 33 emulator. The production
  route test opens each of the five migrated destinations and verifies its Compose root.
- The complete local unit suite runs 184 tests: 183 pass. The same pre-existing
  `MissionServiceImmediateResponseTest.downloadRemembersLegacyProtocolAndReportsProgress` test
  documented in Phase 5 still fails because it reaches the unmocked Android `Log` implementation.
- Manual tablet smoke testing verified Settings, MAVLink Network, RTK, Parameters, the Debug PIN
  gate, and the unlocked Debug tool list with no crashes or clipped primary controls.
