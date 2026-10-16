package com.example.droneservicesapp.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.droneservicesapp.domain.weather.WindWeather
import com.example.droneservicesapp.mavserver.ArduCopterFlightMode
import com.example.droneservicesapp.ui.home.components.MissionFilesCoordinator
import com.example.droneservicesapp.ui.home.components.MissionSimulationController
import com.example.droneservicesapp.ui.home.map.ObstacleEditorState
import com.example.droneservicesapp.ui.map.model.MapScreenPoint

/*
 * Independent, stable state sections for the Compose mission screen. Each section is updated by
 * the mission controller and read by one area of the screen, so a change in one (for example a
 * telemetry tick) does not invalidate the others.
 */

/** Dock-button highlight state that is not derivable from [com.example.droneservicesapp.ui.home.model.HomeMapUiState]. */
class MapDockState {
    var obstaclesSelected by mutableStateOf(false)
    var offsetSelected by mutableStateOf(false)
}

/** The forbidden-area (obstacle) panel. */
class ObstaclePanelState {
    var visible by mutableStateOf(false)
    var mode by mutableStateOf(ObstacleEditorState.Mode.CIRCLE)
    var placementActive by mutableStateOf(false)
    var radiusMeters by mutableStateOf(5)
}

/** Selected survey/route waypoint editing dock. */
class WaypointEditDockState {
    var visible by mutableStateOf(false)
    var heightText by mutableStateOf("0.0")

    /** Screen position, in pixels within the map area, next to which the dock is placed. */
    var anchor by mutableStateOf<MapScreenPoint?>(null)
}

/** Mission summary card shown while editing flight parameters. */
class MissionSummaryCardState {
    var text by mutableStateOf<String?>(null)
}

/** Right-hand workflow panel content that is not plain view-model data. */
class WorkflowPanelState {
    var routeWaypointCount by mutableStateOf(0)
    var hasGeometry by mutableStateOf(false)
    var simulationState by mutableStateOf(MissionSimulationController.State.IDLE)
    var simulationEnabled by mutableStateOf(false)
    var resumeVisible by mutableStateOf(false)
    var resumeLabel by mutableStateOf<String?>(null)
    var addHomeVisible by mutableStateOf(true)
}

/** Live wind panel. */
class WeatherPanelState {
    enum class Status { LOADING, LIVE, UNAVAILABLE }

    var visible by mutableStateOf(false)
    var status by mutableStateOf(Status.LOADING)
    var wind by mutableStateOf<WindWeather?>(null)
}

/** Offline map-area download progress chip (0–100); null when no download is running. */
class OfflineDownloadState {
    var percent by mutableStateOf<Int?>(null)
}

/** Mission download (from aircraft) progress chip; null when idle. */
class MissionDownloadState {
    var percent by mutableStateOf<Int?>(null)
}

/** Save and load mission forms. */
class MissionFilesState {
    var saveVisible by mutableStateOf(false)
    var saveFileName by mutableStateOf("")
    var saveOverwrite by mutableStateOf(false)

    var loadVisible by mutableStateOf(false)
    var loadFiles by mutableStateOf<List<MissionFilesCoordinator.SavedMissionFile>>(emptyList())
    var loadSelectedIndex by mutableStateOf(-1)
}

/** Flight-mode selector and slide-to-arm popups anchored to the top status strip. */
class SafetyControlsState {
    var flightModeOpen by mutableStateOf(false)

    /** Mode awaiting its confirmation gesture: hold for RTL/LAND, slide for AUTO. */
    var pendingConfirmation by mutableStateOf<ArduCopterFlightMode?>(null)
    var armOpen by mutableStateOf(false)

    /** True between a sent arm request and its result, before the aircraft reports pending. */
    var armSubmitted by mutableStateOf(false)
}
