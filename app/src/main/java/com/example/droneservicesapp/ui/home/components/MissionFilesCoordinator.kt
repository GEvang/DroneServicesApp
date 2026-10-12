package com.example.droneservicesapp.ui.home.components

import android.content.Context
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.diagnostics.DiagnosticLog
import com.example.droneservicesapp.data.storage.MissionFileStore
import com.example.droneservicesapp.data.storage.MissionXmlParser
import com.example.droneservicesapp.domain.model.AltitudeReferenceMode
import com.example.droneservicesapp.domain.model.PlanningOperationMode
import com.example.droneservicesapp.domain.model.PlanningWorkflow
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.google.android.gms.maps.model.LatLng
import java.io.File

/**
 * Saves the current mission and loads saved missions. Port of the former
 * `MissionSaveController` and `MissionLoadController` without their View code.
 */
class MissionFilesCoordinator(
    private val context: Context,
    private val activityViewModel: MainActivityViewModel,
    private val showMessage: (String) -> Unit,
    private val store: MissionFileStore = MissionFileStore(context),
) {
    data class SavedMissionFile(val file: File, val displayName: String)

    fun hasSavedMissions(): Boolean = store.listMissionFiles().isNotEmpty()

    fun listMissionFiles(): List<SavedMissionFile> {
        val waypointSuffix = context.getString(R.string.waypoints)
        return store.listMissionFiles().map { file ->
            val lastDotIndex = file.name.lastIndexOf('.')
            val name = when {
                file.name.endsWith(waypointSuffix) -> file.name
                lastDotIndex > 0 -> file.name.substring(0, lastDotIndex)
                else -> file.name
            }
            SavedMissionFile(file, name)
        }
    }

    fun cancel() {
        activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Idle)
    }

    /** Returns true when the mission was saved and the save form can close. */
    fun save(fileName: String, overwrite: Boolean): Boolean {
        val trimmedFileName = fileName.trim()
        if (trimmedFileName.isBlank()) {
            showMessage(context.getString(R.string.error_name_empty))
            return false
        }
        val workflow = activityViewModel.activePlanningWorkflow.value ?: PlanningWorkflow.AREA
        val vertices = activityViewModel.missionArea.value?.vertices.orEmpty().toList()
        val surveyPath = activityViewModel.surveyPath.value.orEmpty()
        val routeWaypoints = activityViewModel.routeWaypoints.value.orEmpty()

        if (workflow == PlanningWorkflow.AREA && activityViewModel.missionArea.value == null) {
            showMessage(context.getString(R.string.no_area_model_available))
            return false
        }
        if (workflow == PlanningWorkflow.AREA && vertices.size < 3) {
            showMessage(context.getString(R.string.polygon_requires_three_points))
            return false
        }
        if (workflow == PlanningWorkflow.POINTS && routeWaypoints.size < 2) {
            showMessage(context.getString(R.string.route_requires_two_points))
            return false
        }

        val operationMode = activityViewModel.planningOperationMode.value ?: PlanningOperationMode.SURVEY
        val survey = operationMode == PlanningOperationMode.SURVEY
        val savedLineDistance = if (survey) {
            activityViewModel.surveyStripSpacing.value?.toInt() ?: 70
        } else {
            activityViewModel.lineDistanceProgress.value?.toInt() ?: 5
        }
        val savedAngle = if (survey) {
            activityViewModel.surveyGridAngle.value?.toInt() ?: 90
        } else {
            activityViewModel.angleProgress.value?.toInt() ?: 90
        }
        val savedAltitude = if (survey) {
            activityViewModel.surveyHeightAboveTerrain.value?.toInt() ?: 50
        } else {
            activityViewModel.flightAltProgress.value?.toInt() ?: 0
        }

        val isSaved = store.saveMissionXml(
            polygon = vertices,
            lineDist = savedLineDistance,
            angleDeg = savedAngle,
            alt = savedAltitude,
            takeoffHeight = activityViewModel.takeoffHeight.value?.toInt() ?: 5,
            altitudeReferenceMode = activityViewModel.altitudeReferenceMode.value ?: AltitudeReferenceMode.RELATIVE,
            sprayerPct = activityViewModel.sprayerProgress.value?.toInt() ?: 75,
            flightSpeed = activityViewModel.flightSpeed.value ?: 5.0,
            planningWorkflow = workflow,
            planningOperationMode = operationMode,
            surveyGridParams = activityViewModel.surveyGridParams.value,
            surveyPath = surveyPath,
            terrainSurveyWaypoints = activityViewModel.terrainSurveyWaypoints.value.orEmpty(),
            routeWaypoints = routeWaypoints,
            plannedHomePosition = activityViewModel.plannedHomePosition.value,
            obstacles = activityViewModel.missionObstacles.value.orEmpty(),
            fileName = trimmedFileName,
            overwrite = overwrite,
        )
        val waypointPath = when (workflow) {
            PlanningWorkflow.AREA -> surveyPath
            PlanningWorkflow.POINTS -> routeWaypoints.map { LatLng(it.latitude, it.longitude) }
        }
        val waypointAltitudes = when (workflow) {
            PlanningWorkflow.AREA -> activityViewModel.terrainSurveyWaypoints.value.orEmpty()
                .takeIf { it.size == waypointPath.size }
                ?.map { it.missionAltitudeMeters.toFloat() }
            PlanningWorkflow.POINTS -> routeWaypoints.map { it.altitudeMeters.toFloat() }
        }
        val waypointsSaved = if (waypointPath.isNotEmpty()) {
            store.saveWaypointsFile(
                waypoints = waypointPath,
                altitudesMeters = waypointAltitudes,
                fallbackAltitudeMeters = savedAltitude.toFloat(),
                planningOperationMode = operationMode,
                fileName = trimmedFileName,
                overwrite = overwrite,
            )
        } else {
            true
        }

        return if (isSaved && waypointsSaved) {
            DiagnosticLog.event(
                "mission", "mission_saved",
                data = mapOf("workflow" to workflow.name, "operationMode" to operationMode.name, "waypointCount" to waypointPath.size),
            )
            showMessage(context.getString(R.string.file_successfully_saved))
            activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Idle)
            true
        } else {
            DiagnosticLog.event(
                "mission", "mission_save_failed", "ERROR",
                mapOf("workflow" to workflow.name, "xmlSaved" to isSaved, "waypointsSaved" to waypointsSaved),
            )
            showMessage(context.getString(if (overwrite) R.string.failed_file_creation else R.string.file_already_exists))
            false
        }
    }

    /** Loads [selected] into the planning model; a missing selection asks the operator to choose. */
    fun load(selected: SavedMissionFile?) {
        if (selected == null) {
            showMessage(context.getString(R.string.select_a_file))
            return
        }
        val file = selected.file
        DiagnosticLog.event("mission", "mission_load_requested", data = mapOf("format" to file.extension))
        if (file.name.endsWith(context.getString(R.string.waypoints))) {
            loadWaypointsFile(file)
        } else {
            MissionXmlParser(activityViewModel = activityViewModel).parseXml(store.openMissionInputStream(file))
        }
    }

    private fun loadWaypointsFile(file: File) {
        val items = store.parseWaypointsFile(store.openMissionInputStream(file))
        if (items.isEmpty()) {
            showMessage(context.getString(R.string.no_survey_path_available))
            return
        }
        val path = items.map { LatLng(it.latitude, it.longitude) }
        val averageAltitude = items.map { it.altitudeMeters }.average().takeIf { !it.isNaN() } ?: 5.0
        activityViewModel.setPlanningWorkflow(PlanningWorkflow.AREA)
        activityViewModel.setPlanningOperationMode(PlanningOperationMode.SURVEY)
        activityViewModel.clearPolygonVertices()
        activityViewModel.clearRouteWaypoints()
        activityViewModel.surveyPath.postValue(path)
        activityViewModel.clearPointCloudMissionProfile()
        activityViewModel.updateSurveyHeightAboveTerrain(averageAltitude.toInt().coerceIn(0, 120))
        activityViewModel.mapState.postValue(MainActivityViewModel.MapState.SetFlightParams)
        DiagnosticLog.event(
            "mission", "waypoints_loaded",
            data = mapOf("itemCount" to items.size, "averageAltitudeMeters" to averageAltitude),
        )
    }
}
