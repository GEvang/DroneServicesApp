# Jetpack Compose Phase 6 — Geo-awareness workflow

## Outcome

The Geo-awareness destination now renders entirely in Compose. Dataset status, health and live
status, validation details, event logs, imports and updates, destructive actions, exports, and the
verification checklist are driven by a configuration-safe `GeoAwarenessViewModel`. No Fragment,
View Binding, Android widget, or XML layout remains for this destination.

## Preserved behavior

- Imported and bundled dataset records continue to use the existing geo-awareness repository and
  domain coordinators.
- JSON and GeoJSON import/update workflows use the Activity Result document picker and preserve
  read access where the provider supports it.
- Dataset health, validation totals, staleness, active scope, live aircraft status, and map-overlay
  visibility remain connected to the shared application state.
- Standard logs, evidence packages, and encrypted incident exports keep their existing exporters.
- Automated verification tests and per-case status updates continue to use the existing
  verification controller and status store.
- Long-running operations expose a blocking progress state; failures and transient results are
  rendered by Compose dialogs, inline error content, or snackbars.

## Removed legacy UI

- `GeoAwarenessFragment`
- `fragment_geo_awareness.xml`
- `GeoAwarenessControllerFactory`
- Binding-oriented dataset renderers, picker/action controllers, presenters, and dialog controllers

## Architecture and tests

- `ProductionDestinationContent` maps `AppDestination.GeoAwareness` directly to
  `GeoAwarenessScreen`.
- `GeoAwarenessUiState` explicitly owns pending picker, busy/error, dataset, live-status,
  verification, and dialog state.
- Dataset and telemetry observers are lifecycle-independent subscriptions owned and cleared by the
  ViewModel, so workflows survive activity recreation without duplicate observers.
- Presenters that remain are pure formatters/mappers with no View, binding, or widget references.
- Unit tests cover durable picker and confirmation state plus existing geo-awareness workflow logic.
- Instrumentation verifies the production route and proves that the verification dialog survives
  activity recreation as exactly one dialog.

## Visible behavior and manual verification

The Geo-awareness screen keeps the original two-column tablet composition and collapses to one
scrollable column on narrow screens. Verify health/source/zone totals, overlay toggling, dataset
import/update/removal, validation details, event logs, exports, and checklist state across rotation.

## Verification results

- Debug and release APK assembly pass; `lintDebug` passes.
- Focused geo-awareness and destination unit tests pass.
- Connected instrumentation passes on a physical Samsung SM-X210 tablet.
- The complete unit suite has one unchanged Phase 0 baseline failure:
  `MissionServiceImmediateResponseTest.downloadRemembersLegacyProtocolAndReportsProgress`.
