# Jetpack Compose Phase 7 — Migrate datasets and file workflows

## Outcome

The Datasets destination now renders entirely in Compose. Dataset creation, selection, loading,
unloading, renaming, deletion, display options, and file attachment workflows are owned by a
screen-level `DatasetsViewModel`; no Fragment, View Binding, spinner adapter, or XML layout remains
for this destination.

## Preserved behavior

- Dataset records and the active dataset continue to use `PreviewDatasetStore`, so existing saved
  datasets remain compatible.
- Ortho TIFF images, TFW/WLD world files, and supported point-cloud files are selected through
  Activity Result document launchers with persisted read access.
- TIFF decoding, world-file parsing, and point-cloud loading run off the main thread.
- Point-cloud imports report progress and can be cancelled.
- Loading a dataset restores its preview assets and display preferences; unloading clears the
  current preview without deleting the dataset.
- Rename and destructive actions retain validation and confirmation dialogs.
- Display background, ortho opacity, point size, point-cloud detail, and height-color settings are
  persisted per dataset and applied immediately to the shared preview state.

## Removed legacy UI

- `DatasetsFragment`
- `fragment_datasets.xml`
- `item_dataset_spinner.xml`

## Architecture and tests

- `ProductionDestinationContent` maps `AppDestination.Datasets` directly to `DatasetsScreen`.
- `DatasetsViewModel` owns durable workflow state, active selection, progress, dialogs, errors, and
  persistence across configuration changes.
- The Compose screen owns only rendering and Activity Result launchers.
- Unit tests cover dataset-state derivation and accepted file extensions.
- Instrumentation navigates through the production drawer, creates a dataset, recreates the
  activity, and verifies that the active workflow remains visible.

## Visible behavior

The Datasets screen keeps the original two-column tablet composition: dataset management and
assets on the left, display options on the right. It uses the restored dark panels, teal outlines,
compact asset rows, Compose dropdowns, Material sliders and switches, and Compose dialogs. Narrow
screens collapse to a single scrollable column.

## Manual verification

- Create, rename, switch between, load, unload, delete, and delete all datasets.
- Attach a `.tif`/`.tiff`, then its `.tfw`/`.wld`, and confirm the map preview updates.
- Attach a supported point cloud (`.ply`, `.pcd`, `.csv`, `.txt`, or `.xyz`) and verify progress,
  cancellation, and the 3D preview.
- Change each display option, leave and return to Datasets, and confirm the values persist.
- Rotate or recreate the activity during an active dataset workflow and confirm the selected
  dataset and screen state remain available.

## Verification results

- Debug and release APK assembly pass; `lintDebug` passes.
- Focused dataset and destination unit tests pass.
- All 10 connected instrumentation tests pass on both the Pixel Tablet API 35 emulator and the
  SM-X210 tablet, including the new dataset creation/recreation workflow.
- The complete local unit suite runs 186 tests: 185 pass. The same pre-existing
  `MissionServiceImmediateResponseTest.downloadRemembersLegacyProtocolAndReportsProgress` failure
  remains because it reaches the unmocked Android `Log` implementation.
- Manual tablet-layout smoke testing verified the empty state and the populated two-column dataset
  manager/assets/display-options layout without clipping or crashes.
