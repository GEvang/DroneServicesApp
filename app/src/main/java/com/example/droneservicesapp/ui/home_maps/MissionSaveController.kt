package com.example.droneservicesapp.ui.home_maps

import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import com.example.droneservicesapp.MissionFileHandler
import com.example.droneservicesapp.R
import com.example.droneservicesapp.activities.MainActivityViewModel

/**
 * Owns the "Save mission" UI (save_file_layout) and calls MissionFileHandler.saveMissionXML(...).
 *
 * Extracted from HomeMapsFragment.initMissionSave().
 */
class MissionSaveController(
    private val activity: FragmentActivity,
    private val rootView: View,
    private val activityViewModel: MainActivityViewModel,
) {

    private var isBound = false
    private var overrideFile = false

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

            Log.i("MissionSave", "initiated Mission Save Layout")
            Log.i("MissionSave", "polygonEdges=${activityViewModel.area.value?.polygonEdges}")
            Log.i("MissionSave", "lineDistance=${activityViewModel.lineDistanceProgress.value?.toInt()}")
            Log.i("MissionSave", "angle=${activityViewModel.angleProgress.value?.toInt()}")
            Log.i("MissionSave", "alt=${activityViewModel.flightAltProgress.value?.toInt()}")
            Log.i("MissionSave", "sprayer=${activityViewModel.sprayerProgress.value?.toInt()}")

            val area = activityViewModel.area.value
            if (area == null) {
                Toast.makeText(activity.baseContext, "No area defined", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val isSaved = MissionFileHandler(activity, activityViewModel).saveMissionXML(
                area.polygonEdges,
                activityViewModel.lineDistanceProgress.value!!.toInt(),
                activityViewModel.angleProgress.value!!.toInt(),
                activityViewModel.flightAltProgress.value!!.toInt(),
                activityViewModel.sprayerProgress.value!!.toInt(),
                inputFilename.text.toString(),
                overrideFile
            )

            if (isSaved) hide()
        }

        buttonCancel.setOnClickListener {
            hide()
        }
    }
}
