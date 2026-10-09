# Jetpack Compose Phase 1 — Modernize the build and add Compose

Completed on 2026-10-09.

## Toolchain and dependency changes

- Kotlin was upgraded from the `1.8.20-RC` compiler to stable Kotlin 2.1.21.
- The Kotlin Compose compiler plugin 2.1.21 was added and applied to the app module.
- `compileSdk` was raised from 33 to 36. `minSdk` 30 and `targetSdk` 33 were intentionally left unchanged because changing runtime targeting behavior is outside the UI migration.
- Compose was enabled alongside View Binding. View Binding remains required by unmigrated screens.
- The Compose BOM is `2025.08.01`.
- Compose Material 3, Foundation, LiveData integration, tooling/previews, UI testing, and the Activity integration were added.
- Lifecycle libraries were aligned at 2.8.7 and Compose lifecycle/ViewModel integrations were added.
- AndroidX Test was aligned to current stable components (`core`/`runner` 1.7.0, `monitor` 1.8.0, JUnit extension 1.3.0, and Espresso 3.7.0) so Compose UI tests run correctly on current Android versions.

## Compose foundation

The new `ui/compose` package contains:

- A dark `DroneServicesTheme` matching the existing application design tokens
- Semantic colors for background, surfaces, text, accent, success, warning, and danger
- Inter typography backed by the existing font resources
- Shared shapes
- Composition-local spacing and common size tokens
- Multi-device previews for default landscape, `sw533dp-land`, and `sw600dp`
- A small foundation surface used for previews and smoke verification

No production destination was migrated in this phase.

## Verification surface and tests

- Debug builds contain a non-exported `ComposeFoundationActivity` that renders the Compose foundation surface without changing production navigation.
- `ComposeFoundationTest` launches that activity and verifies the tagged Compose root is displayed.
- The debug Android-test APK compiles and packages successfully.
- The instrumentation test was executed successfully on the local `Medium_Phone_API_36.1` emulator.

## Verification results

All commands used JDK 17.0.19.

| Gate | Result |
| --- | --- |
| `assembleDebug` | Passed |
| `assembleDebugAndroidTest` | Passed |
| `assembleRelease` | Passed, including vital lint |
| `lintDebug` | Passed |
| Compose production source compilation | Passed |
| Compose instrumentation source compilation | Passed |
| `ComposeFoundationTest` on API 36.1 emulator | Passed |
| Existing unit suite | Baseline preserved: 161 passed, the same 1 pre-existing failure |

The known failure remains `MissionServiceImmediateResponseTest.downloadRemembersLegacyProtocolAndReportsProgress`, caused by unmocked `android.util.Log` calls in the local JVM test environment.

## Phase 1 exit state

- Compose can be used by production source code.
- Theme, tokens, typography, previews, and test infrastructure exist.
- Existing XML screens and Fragment navigation remain operational.
- View Binding remains enabled only for the incremental migration period.
- No production screen has been migrated.
