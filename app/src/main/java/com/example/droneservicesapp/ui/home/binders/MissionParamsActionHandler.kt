package com.example.droneservicesapp.ui.home.binders

import android.content.Context
import android.location.Location
import android.util.Log
import android.widget.Toast
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.mavlink.MissionBuilder
import com.example.droneservicesapp.domain.model.AltitudeReferenceMode
import com.example.droneservicesapp.domain.model.PlanningOperationMode
import com.example.droneservicesapp.domain.model.PlanningWorkflow
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel

class MissionParamsActionHandler(
    private val context: Context,
    private val views: MissionParamsViews,
    private val activityViewModel: MainActivityViewModel,
    private val droneViewModel: DroneViewModel,
    private val droneLocationProvider: (() -> Location?)? = null,
    private val preferencesBridge: MissionParamsPreferencesBridge,
    private val beforeUploadGuard: (((onAllowed: () -> Unit) -> Unit))? = null,
) {
    fun bind() {
        views.uploadMissionButton?.setOnClickListener { uploadMission() }
        views.saveMissionButton?.setOnClickListener {
            activityViewModel.mapState.postValue(MainActivityViewModel.MapState.SaveMissionToFile)
        }
    }

    private fun uploadMission() {
        val connected = droneViewModel.conStateLiveData.value == true
        val droneLoc = droneLocationProvider?.invoke() ?: droneViewModel.droneLocationLiveData.value
        val path = activityViewModel.surveyPath.value
        val routeWaypoints = activityViewModel.routeWaypoints.value.orEmpty()
        val workflow = activityViewModel.activePlanningWorkflow.value ?: PlanningWorkflow.AREA
        val alt = activityViewModel.flightAltProgress.value
        val sprayer = activityViewModel.sprayerProgress.value
        val speed = activityViewModel.flightSpeed.value
        val angle = activityViewModel.angleProgress.value
        val operationMode = activityViewModel.planningOperationMode.value ?: PlanningOperationMode.SURVEY
        val altitudeReferenceMode = activityViewModel.altitudeReferenceMode.value ?: AltitudeReferenceMode.TERRAIN
        val srtmShouldBeEnabled = operationMode == PlanningOperationMode.SURVEY

        when {
            !connected -> {
                showMessage(context.getString(R.string.no_conn_msg))
                return
            }

            !droneViewModel.isSrtmSettingConfirmed(srtmShouldBeEnabled) -> {
                droneViewModel.setSrtmEnabled(srtmShouldBeEnabled)
                showMessage(context.getString(R.string.terrain_source_not_confirmed))
                return
            }

            droneLoc == null -> {
                showMessage(context.getString(R.string.drone_gps_not_available_yet))
                return
            }

            workflow == PlanningWorkflow.AREA && path.isNullOrEmpty() -> {
                showMessage(context.getString(R.string.no_survey_path_available))
                return
            }

            workflow == PlanningWorkflow.POINTS && routeWaypoints.size < 2 -> {
                showMessage(context.getString(R.string.route_requires_two_points))
                return
            }

            alt == null || sprayer == null || speed == null || angle == null -> {
                showMessage(context.getString(R.string.missing_mission_parameters))
                return
            }
        }

        val validatedDroneLoc = droneLoc ?: return
        val validatedAlt = alt ?: return
        val validatedSprayer = sprayer ?: return
        val validatedSpeed = speed ?: return
        val validatedAngle = angle ?: return
        var usedTerrainAltitudes = false
        var uploadAltitudeReferenceMode = altitudeReferenceMode

        val missionItems = if (workflow == PlanningWorkflow.POINTS) {
            MissionBuilder.buildPointRouteMission(
                routeWaypoints = routeWaypoints,
                currentPos = validatedDroneLoc,
                targetSystemId = droneViewModel.getTargetSystemId(),
                targetComponentId = droneViewModel.getTargetComponentId(),
                altitudeReferenceMode = altitudeReferenceMode
            )
        } else {
            val validatedPath = path ?: return
            if (operationMode == PlanningOperationMode.SPRAY) {
                val pointCloudAltitudes = activityViewModel.terrainSurveyWaypoints.value.orEmpty()
                    .takeIf {
                        activityViewModel.pointCloudCoversMissionArea.value == true &&
                            it.size == validatedPath.size
                    }
                    ?.map { it.missionAltitudeMeters.toFloat() }
                val sprayAltitudeReferenceMode = if (pointCloudAltitudes != null) {
                    AltitudeReferenceMode.RELATIVE
                } else {
                    AltitudeReferenceMode.TERRAIN
                }
                usedTerrainAltitudes = pointCloudAltitudes != null
                uploadAltitudeReferenceMode = sprayAltitudeReferenceMode
                MissionBuilder.buildSprayAreaMission(
                    waypoints = ArrayList(validatedPath),
                    currentPos = validatedDroneLoc,
                    alt = validatedAlt.toFloat(),
                    sprayerIntensity = validatedSprayer.toInt(),
                    flightSpeed = validatedSpeed.toFloat(),
                    angleProgress = validatedAngle.toFloat(),
                    targetSystemId = droneViewModel.getTargetSystemId(),
                    targetComponentId = droneViewModel.getTargetComponentId(),
                    altitudeReferenceMode = sprayAltitudeReferenceMode,
                    waypointAltitudes = pointCloudAltitudes
                )
            } else {
                usedTerrainAltitudes = false
                uploadAltitudeReferenceMode = altitudeReferenceMode
                MissionBuilder.buildSurveyAreaMission(
                    waypoints = ArrayList(validatedPath),
                    currentPos = validatedDroneLoc,
                    alt = (activityViewModel.surveyHeightAboveTerrain.value ?: validatedAlt).toFloat(),
                    flightSpeed = validatedSpeed.toFloat(),
                    angleProgress = (activityViewModel.surveyGridAngle.value ?: validatedAngle).toFloat(),
                    targetSystemId = droneViewModel.getTargetSystemId(),
                    targetComponentId = droneViewModel.getTargetComponentId(),
                    altitudeReferenceMode = altitudeReferenceMode,
                    waypointAltitudes = null
                )
            }
        }

        val proceedWithUpload = {
            Log.i(
                "MissionUpload",
                "Proceeding with upload workflow=$workflow altitudeReference=$uploadAltitudeReferenceMode " +
                    "terrainAware=$usedTerrainAltitudes " +
                    "altitude=${validatedAlt.toInt()}m missionItems=${missionItems.size}"
            )
            droneViewModel.uploadMissionNew(missionItems, activityViewModel)
            preferencesBridge.saveFromViewModel()
        }

        beforeUploadGuard?.invoke(proceedWithUpload) ?: proceedWithUpload()
    }

    private fun showMessage(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
