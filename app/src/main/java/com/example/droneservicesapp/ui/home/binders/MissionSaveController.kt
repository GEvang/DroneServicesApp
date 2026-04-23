package com.example.droneservicesapp.ui.home.binders

import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.storage.MissionFileStore
import com.example.droneservicesapp.ui.home.model.MainActivityViewModel

/**
 * Owns the "Save mission" UI (save_file_layout) and calls MissionFileStore.saveMissionXml(...).
 */
class MissionSaveController(
    private val activity: FragmentActivity,
    private val rootView: View,
    private val activityViewModel: MainActivityViewModel,
) {

    private var isBound = false
    private var overrideFile = false
    private val store = MissionFileStore(activity)

    private val saveFileView: LinearLayoutCompat by lazy {
        rootView.findViewById<LinearLayoutCompat>(R.id.save_file_layout)
    }

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
        saveFileView.isVisible = true
        inputFilename.text.clear()
        overrideFile = false
        overrideCheckBox.isChecked = false

        if (!isBound) {
            bindOnce()
            isBound = true
        }
    }

    fun hide() {
        saveFileView.isVisible = false
    }

    private fun bindOnce() {
        overrideCheckBox.setOnCheckedChangeListener { _, isChecked ->
            overrideFile = isChecked
        }

        buttonSaveMission.setOnClickListener {
            if (inputFilename.text.isBlank()) {
                Toast.makeText(
                    activity.baseContext,
                    activity.baseContext.getString(R.string.error_name_empty),
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            val area = activityViewModel.missionArea.value
            if (area == null) {
                Toast.makeText(activity.baseContext, "No area model available", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // ✅ New model: vertices instead of polygonEdges
            val vertices = area.vertices
            if (vertices.size < 3) {
                Toast.makeText(activity.baseContext, "Polygon must have at least 3 points", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            Log.i("MissionSave", "initiated Mission Save Layout")
            Log.i("MissionSave", "vertices=$vertices")
            Log.i("MissionSave", "lineDistance=${activityViewModel.lineDistanceProgress.value?.toInt()}")
            Log.i("MissionSave", "angle=${activityViewModel.angleProgress.value?.toInt()}")
            Log.i("MissionSave", "alt=${activityViewModel.flightAltProgress.value?.toInt()}")
            Log.i("MissionSave", "sprayer=${activityViewModel.sprayerProgress.value?.toInt()}")


            val isSaved = store.saveMissionXml(
                polygon = vertices,
                lineDist = activityViewModel.lineDistanceProgress.value!!.toInt(),
                angleDeg = activityViewModel.angleProgress.value!!.toInt(),
                alt = activityViewModel.flightAltProgress.value!!.toInt(),
                sprayerPct = activityViewModel.sprayerProgress.value!!.toInt(),
                fileName = inputFilename.text.toString(),
                overwrite = overrideFile
            )

            if (isSaved) {
                Toast.makeText(
                    activity.baseContext,
                    activity.baseContext.getString(R.string.file_successfully_saved),
                    Toast.LENGTH_LONG
                ).show()

                // Return to idle state and hide the UI
                activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Idle)
                hide()
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
            hide() // Also hide on cancel if desired
        }
    }
}
