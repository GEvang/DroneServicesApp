package com.example.droneservicesapp.ui.home.binders

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.mavlink.MissionBuilder
import com.example.droneservicesapp.domain.model.AltitudeReferenceMode
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel

class MissionParamsActionHandler(
    private val context: Context,
    private val views: MissionParamsViews,
    private val activityViewModel: MainActivityViewModel,
    private val droneViewModel: DroneViewModel,
    private val preferencesBridge: MissionParamsPreferencesBridge,
    private val beforeUploadGuard: (((onAllowed: () -> Unit) -> Unit))? = null,
) {
    fun bind() {
        views.uploadMissionButton.setOnClickListener { uploadMission() }
        views.saveMissionButton.setOnClickListener {
            activityViewModel.mapState.postValue(MainActivityViewModel.MapState.SaveMissionToFile)
        }
    }

    private fun uploadMission() {
        val connected = droneViewModel.conStateLiveData.value == true
        val droneLoc = droneViewModel.droneLocationLiveData.value
        val path = activityViewModel.surveyPath.value
        val alt = activityViewModel.flightAltProgress.value
        val sprayer = activityViewModel.sprayerProgress.value
        val speed = activityViewModel.flightSpeed.value
        val angle = activityViewModel.angleProgress.value
        val altitudeReferenceMode = activityViewModel.altitudeReferenceMode.value ?: AltitudeReferenceMode.RELATIVE

        when {
            !connected -> {
                showMessage(context.getString(R.string.no_conn_msg))
                return
            }

            droneLoc == null -> {
                showMessage(context.getString(R.string.drone_gps_not_available_yet))
                return
            }

            path.isNullOrEmpty() -> {
                showMessage(context.getString(R.string.no_survey_path_available))
                return
            }

            alt == null || sprayer == null || speed == null || angle == null -> {
                showMessage(context.getString(R.string.missing_mission_parameters))
                return
            }
        }

        val validatedDroneLoc = droneLoc ?: return
        val validatedPath = path ?: return
        val validatedAlt = alt ?: return
        val validatedSprayer = sprayer ?: return
        val validatedSpeed = speed ?: return
        val validatedAngle = angle ?: return

        val missionItems = MissionBuilder.buildSurveyMission(
            waypoints = ArrayList(validatedPath),
            currentPos = validatedDroneLoc,
            alt = validatedAlt.toFloat(),
            sprayerIntensity = validatedSprayer.toInt(),
            flightSpeed = validatedSpeed.toFloat(),
            angleProgress = validatedAngle.toFloat(),
            targetSystemId = droneViewModel.getTargetSystemId(),
            targetComponentId = droneViewModel.getTargetComponentId(),
            altitudeReferenceMode = altitudeReferenceMode
        )

        val proceedWithUpload = {
            Log.i(
                "MissionUpload",
                "Proceeding with upload altitudeReference=$altitudeReferenceMode altitude=${validatedAlt.toInt()}m"
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
