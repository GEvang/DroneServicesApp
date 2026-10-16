# Jetpack Compose plan Phase 10 — Compose application shell and navigation

## Status of each plan item

| Plan item | Result |
| --- | --- |
| `MainActivity` uses `setContent` | Done (shell commit "Phase 5 — Migrate the app shell and navigation"). |
| Compose navigation host | `DroneServicesApp` hosts a `NavHost` over `AppDestination`. |
| Drawer, toolbar, bottom navigation | Compose drawer, telemetry top bar and mission bottom actions. |
| Remove the XML navigation graph | No `res/navigation` or `res/menu` resources remain. |
| Remove screen Fragments | None remain; Home was the last (Phase 9). The unused `FragmentActivity` parameter of `MissionXmlParser` was removed, so no code references the Fragment API. |
| Immersive mode | Preserved: navigation bar hidden, swipe to reveal transiently; screen kept on. |
| Permissions | Preserved: fine location requested at start (as before); the map's location overlay re-enables on resume, so granting takes effect immediately. |
| MAVLink session lifecycle | Preserved: `MavlinkSessionCoordinator.onResume/onPause` from `MainActivity`, as before. |
| State restoration | Selected destination and back stack restore through `rememberNavController`; map camera and planning state live in view models; process death behaves as before (view-model state is not persisted, as in the Fragment app). |

## Tests

- `MainActivityShellTest`
  - existing: destination survives recreation, every destination renders, Home map and planning
    panel survive navigation and recreation;
  - new: navigation bar stays hidden after launch and after recreation;
  - new: two background/foreground cycles (which pause/resume the MAVLink session and the map)
    leave the activity resumed with the map rendered.

## Left for Phase 11

The Fragment, ConstraintLayout, RecyclerView and Material Components dependencies are no longer
used by screens but are still declared; removing them and unused resources is Phase 11 cleanup.

## Device checklist

1. Launch: navigation bar hidden; swipe up reveals it briefly.
2. Deny location, relaunch: prompt appears again; grant it: the blue location dot appears without
   leaving Home.
3. Connect to the aircraft, press Home (background) and return: telemetry resumes.
4. Change a MAVLink setting on the MAVLink screen: the connection re-applies.
5. Open Settings, rotate/recreate: Settings stays selected; back returns to Home.
6. "Don't keep activities" on: background and return on each destination.
