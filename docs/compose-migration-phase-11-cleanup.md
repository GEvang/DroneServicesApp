# Jetpack Compose plan Phase 11 — Legacy cleanup and enforcement

The UI is now Compose-only. This phase removes what the View-based UI left behind and adds a test
that keeps it from coming back.

## Removed

**Already removed in Phase 9:** every `res/layout*` file, View Binding, the View adapters and
binders, the XML navigation graph and menus.

**Dependencies (`app/build.gradle`)**

| Removed | Why it was there |
| --- | --- |
| `com.google.android.material:material` | XML widgets and the `Theme.MaterialComponents` parent |
| `androidx.constraintlayout:constraintlayout` | XML layouts |
| `androidx.recyclerview:recyclerview` | XML lists |
| `androidx.fragment:fragment-ktx` | Fragment destinations |

`androidx.fragment:fragment` is kept only as a version *constraint* (1.8.9), not a dependency: it
contains `FragmentActivity`, the base class of `AppCompatActivity`, which would otherwise resolve
to the much older version AppCompat requests. No code uses the Fragment API.

**Theme:** `Theme.DroneServicesApp` now extends `Theme.AppCompat.Light.NoActionBar` instead of
`Theme.MaterialComponents.Light.NoActionBar` (light and night variants). The Material-only
attributes (`colorPrimaryVariant`, `colorSecondary`, `colorSurface`, `colorOn*`, `textInputStyle`,
`materialButtonStyle`, `materialAlertDialogTheme`) and `editTextStyle` were removed. Window
background, status/navigation bar colours, the startup splash and the dialog theme are unchanged.
Compose screens use `DroneServicesTheme`, so they are unaffected.

**Resources** — removed by a reference-graph analysis (code in every source set and both manifests
as roots; XML references, explicit and implicit style parents as edges; repeated to a fixpoint, so
resources used only by other unused resources are caught):

| Type | Before | After |
| --- | --- | --- |
| Styles | 68 | 4 (the app themes) |
| Dimensions | 119 | 9 |
| Drawables | 124 | 57 |
| Colours | 67 | 23 |
| Strings (distinct names; Greek translations removed with them) | 878 | 741 |
| String arrays | 4 | 1 |
| IDs | 3 | 0 |
| XML | 2 | 1 (`file_paths.xml`) |

Also removed: the unused launcher `*_foreground.png` mipmaps (the adaptive icon draws
`ic_launcher_foreground_inset`), the `inter.xml` font family wrapper (Compose loads the Inter
`.ttf` files directly), the old `root_preferences.xml` preference screen, and the emptied
`styles.xml`, `ids.xml` and `compose_phase6_strings.xml`. Removed preference-key strings were only
used by that preference screen; stored preferences are unaffected.

**Code:** `ui/common/TouchDelegateSeekBar` (unused View SeekBar). The three classes in
`ui/home/binders` were not View binders any more and moved to `ui/home/components`.

## Retained, as planned

Localized strings in use, the manifest, `file_paths.xml`, the app/startup/dialog themes, icons and
drawables still referenced, fonts, AppCompat (activity base class, `AppCompatDelegate`, locale
handling), `androidx.preference` (for `PreferenceManager`), and the two specialised renderers kept
by the Phase 2 decision (osmdroid `MapView`, OpenGL `PointCloudGlView`).

## Enforcement

`LegacyUiEnforcementTest` fails the unit-test run if any of these return: `layout`/`menu`/
`navigation` resources, View Binding, the removed dependencies (or `navigation-fragment`/
`navigation-ui`), Fragment / ConstraintLayout / RecyclerView / Material Components / View
Binding / Data Binding imports, platform `AlertDialog`/`ProgressDialog`/`Dialog` imports, or
osmdroid's dialog-showing `downloadAreaAsync` in production code. `MapEngineBoundaryTest` (Phase 7) keeps osmdroid behind its adapter.

## Final verification (from the migration plan)

Static checks run while making this change: every `R.*` reference in code resolves, no XML
reference points at a removed resource, all resource XML parses, no unused resources remain, and
all project imports resolve. It has not been compiled here; run:

1. `gradlew assembleDebug assembleRelease lintDebug` — lint also confirms no unused resources.
2. `gradlew testDebugUnitTest` and `connectedDebugAndroidTest` (Compose UI tests).
3. English and Greek: switch language in Settings; check Home, Settings, RTK, Datasets, dialogs.
4. Supported screen sizes: phone landscape, 533dp-class tablet, 600dp+ tablet.
5. Process recreation: "Don't keep activities" on; background/return on each destination.
6. MAVLink lifecycle: connect, background, return; change a MAVLink setting while connected.
7. Map editing and offline use: draw/edit areas and routes, obstacles; offline area download,
   airplane-mode use, cache clearing (see the Phase 7 checklist).
8. Mission upload and start: upload, arm (slide), AUTO (slide), RTL/LAND (hold).
9. Large orthophotos and point clouds: load, toggle Map/Ortho/3D, opacity, 3D picking.
10. Launcher icon, splash screen and the status/navigation bar colours look as before.
