# Jetpack Compose Phase 5 — Migrate the app shell and navigation

## Outcome

`MainActivity` is now a Compose host. The production drawer, non-map telemetry toolbar, mission
action bar, location-permission request, and destination back stack are implemented in Compose.
The existing screen implementations remain available through temporary Fragment hosts and will
be replaced individually in later phases.

The MAVLink session coordinator, activity-scoped ViewModels, planning-parameter policy, landscape
orientation, immersive navigation-bar behavior, and keep-screen-on safety flag remain owned by
the activity and retain their previous lifecycle behavior.

## Navigation architecture

- `AppDestination` provides route objects for all ten destinations; resource IDs are no longer
  used as navigation destinations.
- `DroneServicesApp` owns a Navigation Compose `NavHost`, `ModalNavigationDrawer`, telemetry bar,
  and mission action overlay.
- Drawer navigation saves and restores each top-level destination state.
- Point-cloud and orthophoto transitions now use `ShellNavigator` instead of Fragment Navigation.
- The map's existing **More** action opens the Compose drawer.
- `LegacyFragmentHost` attaches one Fragment per route, detaches it when its destination leaves
  composition, and reattaches it when selected again. This is an explicit migration bridge, not
  the final UI architecture.
- Back navigation returns top-level destinations to the map and secondary preview destinations to
  their previous route.

## Compose shell features

- Branded navigation drawer with selected-destination state.
- Live connection, GPS, RTK, battery, altitude, speed, sprayer, flight-mode, armed, and mission
  upload telemetry.
- GPS and RTK detail dialogs.
- State-driven draw, load, cancel, erase, and accept mission actions.
- Activity Result API location permission request from Compose.
- Selected destination restoration after activity recreation.
- Accessibility descriptions and minimum 48dp navigation/action touch targets.

## Removed legacy shell resources

- `activity_main.xml`
- `app_bar_main.xml` and its three layout variants
- `content_main.xml`
- `nav_header_main.xml`
- `mobile_navigation.xml`
- `activity_main_drawer.xml`
- `bottom_nav_menu.xml`
- `main.xml`
- `ActivityMainBinding`, `ShellToolbarBinder`, `ShellBottomNavBinder`, and the legacy permission
  requester are no longer used.
- Fragment Navigation and Navigation UI dependencies were replaced with Navigation Compose plus
  an explicit temporary Fragment dependency.

View Binding remains enabled only because the screens hosted by the shell have not yet migrated.

## Verification

- `assembleDebug`, `assembleDebugAndroidTest`, `assembleRelease`, and `lintDebug` pass.
- All 8 connected instrumentation tests pass on the Pixel Tablet API 35 emulator.
- Instrumentation covers typed drawer navigation, explicit mission actions, the real production
  Fragment host, and destination restoration after activity recreation.
- The unit suite runs 181 tests: 180 pass. The existing
  `MissionServiceImmediateResponseTest.downloadRemembersLegacyProtocolAndReportsProgress` test
  still fails because its local-JVM execution reaches the unmocked Android `Log` implementation;
  Phase 5 does not modify that service or test.
- Manual emulator smoke testing verified map startup, the runtime location permission contract,
  opening the Compose drawer from the map, rendering Settings under the Compose telemetry bar,
  and returning to the live map with Back without a crash.

## Visible behavior

- The navigation drawer is now Compose and uses the shared dark theme and teal selection state.
- Non-map destinations now display the Compose telemetry toolbar.
- The map remains full-screen and visually unchanged, including its own telemetry strip and
  bottom utility dock.
- Mission draw/load/accept/cancel/erase actions are rendered by Compose when requested by the map
  workflow.
- Location permission uses the modern Activity Result contract; the system dialog itself is
  unchanged.
