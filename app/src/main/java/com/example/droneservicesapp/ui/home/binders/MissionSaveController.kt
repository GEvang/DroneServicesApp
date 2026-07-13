package com.example.droneservicesapp.ui.home.binders

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.storage.MissionFileStore
import com.example.droneservicesapp.domain.model.AltitudeReferenceMode
import com.example.droneservicesapp.domain.model.PlanningOperationMode
import com.example.droneservicesapp.domain.model.PlanningWorkflow
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel

/**
 * Owns the "Save mission" form state and delegates persistence to MissionFileStore.
 * Visibility is rendered by HomeMapPanelsBinder.
 */
class MissionSaveController(
    private val activity: FragmentActivity,
    private val rootView: View,
    private val activityViewModel: MainActivityViewModel,
) {

    private var isBound = false
    private var overrideFile = false
    private val store = MissionFileStore(activity)

    private val inputFilename: EditText by lazy {
        rootView.findViewById<EditText>(R.id.input_filename)
    }

    private val overrideCheckBox: CheckBox by lazy {
        rootView.findViewById<CheckBox>(R.id.override_checkbox)
    }

    private val buttonSaveMission: Button by lazy {
        rootView.findViewById<Button>(R.id.save_button)
    }

    private val buttonCancel: Button by lazy {
        rootView.findViewById<Button>(R.id.cancel_button)
    }

    fun show() {
        inputFilename.text.clear()
        inputFilename.isEnabled = true
        inputFilename.isFocusable = true
        inputFilename.isFocusableInTouchMode = true
        inputFilename.isClickable = true
        overrideFile = false
        overrideCheckBox.isChecked = false

        inputFilename.post {
            inputFilename.requestFocus()
            inputFilename.setSelection(inputFilename.text.length)
            showKeyboard()
        }

        if (!isBound) {
            bindOnce()
            isBound = true
        }
    }

    private fun bindOnce() {
        inputFilename.setOnClickListener {
            inputFilename.requestFocus()
            inputFilename.setSelection(inputFilename.text.length)
            showKeyboard()
        }

        inputFilename.setOnTouchListener { _, _ ->
            inputFilename.requestFocus()
            inputFilename.setSelection(inputFilename.text.length)
            showKeyboard()
            false
        }

        overrideCheckBox.setOnCheckedChangeListener { _, isChecked ->
            overrideFile = isChecked
        }

        buttonSaveMission.setOnClickListener {
            val trimmedFileName = inputFilename.text.toString().trim()

            if (trimmedFileName.isBlank()) {
                Toast.makeText(
                    activity.baseContext,
                    activity.baseContext.getString(R.string.error_name_empty),
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            val workflow = activityViewModel.activePlanningWorkflow.value ?: PlanningWorkflow.AREA
            val vertices = activityViewModel.missionArea.value?.vertices.orEmpty()
            val routeWaypoints = activityViewModel.routeWaypoints.value.orEmpty()

            if (workflow == PlanningWorkflow.AREA && activityViewModel.missionArea.value == null) {
                Toast.makeText(
                    activity.baseContext,
                    activity.baseContext.getString(R.string.no_area_model_available),
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            if (workflow == PlanningWorkflow.AREA && vertices.size < 3) {
                Toast.makeText(
                    activity.baseContext,
                    activity.baseContext.getString(R.string.polygon_requires_three_points),
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            if (workflow == PlanningWorkflow.POINTS && routeWaypoints.size < 2) {
                Toast.makeText(
                    activity.baseContext,
                    activity.baseContext.getString(R.string.route_requires_two_points),
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            val operationMode = activityViewModel.planningOperationMode.value ?: PlanningOperationMode.SURVEY
            val savedLineDistance = if (operationMode == PlanningOperationMode.SURVEY) {
                activityViewModel.surveyStripSpacing.value!!.toInt()
            } else {
                activityViewModel.lineDistanceProgress.value!!.toInt()
            }
            val savedAngle = if (operationMode == PlanningOperationMode.SURVEY) {
                activityViewModel.surveyGridAngle.value!!.toInt()
            } else {
                activityViewModel.angleProgress.value!!.toInt()
            }
            val savedAltitude = if (operationMode == PlanningOperationMode.SURVEY) {
                activityViewModel.surveyHeightAboveTerrain.value!!.toInt()
            } else {
                activityViewModel.flightAltProgress.value!!.toInt()
            }

            val isSaved = store.saveMissionXml(
                polygon = vertices,
                lineDist = savedLineDistance,
                angleDeg = savedAngle,
                alt = savedAltitude,
                altitudeReferenceMode = activityViewModel.altitudeReferenceMode.value
                    ?: AltitudeReferenceMode.RELATIVE,
                sprayerPct = activityViewModel.sprayerProgress.value!!.toInt(),
                flightSpeed = activityViewModel.flightSpeed.value ?: 1.0,
                planningWorkflow = workflow,
                planningOperationMode = operationMode,
                surveyGridParams = activityViewModel.surveyGridParams.value,
                routeWaypoints = routeWaypoints,
                fileName = trimmedFileName,
                overwrite = overrideFile
            )

            if (isSaved) {
                Toast.makeText(
                    activity.baseContext,
                    activity.baseContext.getString(R.string.file_successfully_saved),
                    Toast.LENGTH_LONG
                ).show()

                activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Idle)
            } else {
                val errorStringId = if (overrideFile) {
                    R.string.failed_file_creation
                } else {
                    R.string.file_already_exists
                }
                Toast.makeText(
                    activity.baseContext,
                    activity.baseContext.getString(errorStringId),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        buttonCancel.setOnClickListener {
            activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Idle)
        }
    }

    private fun showKeyboard() {
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(inputFilename, InputMethodManager.SHOW_IMPLICIT)
    }
}
