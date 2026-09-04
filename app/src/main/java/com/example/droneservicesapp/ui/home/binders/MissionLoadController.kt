package com.example.droneservicesapp.ui.home.binders

import android.view.View
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.storage.MissionFileStore
import com.example.droneservicesapp.data.storage.MissionXmlParser
import com.example.droneservicesapp.data.diagnostics.DiagnosticLog
import com.example.droneservicesapp.domain.model.PlanningOperationMode
import com.example.droneservicesapp.domain.model.PlanningWorkflow
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.google.android.gms.maps.model.LatLng
import java.io.File

/**
 * Owns the "Load mission" list content and delegates mission parsing to MissionXmlParser.
 * Visibility is rendered by HomeMapPanelsBinder.
 */
class MissionLoadController(
    private val activity: FragmentActivity,
    private val rootView: View,
    private val activityViewModel: MainActivityViewModel,
) {

    private var isBound = false

    private var currentFiles: List<File> = emptyList()
    private var selectedPosition: Int = -1

    private val store = MissionFileStore(activity)

    private val listView: ListView by lazy {
        rootView.findViewById<ListView>(R.id.file_list)
    }

    private val cancelButton: Button by lazy {
        rootView.findViewById<Button>(R.id.btn_cancel)
    }

    private val confirmButton: Button by lazy {
        rootView.findViewById<Button>(R.id.btn_confirm_load)
    }

    fun show() {
        val (files, names) = refreshList()

        if (files.isEmpty()) {
            Toast.makeText(activity, activity.getString(R.string.no_saved_missions_yet), Toast.LENGTH_LONG).show()
            activityViewModel.mapState.value = MainActivityViewModel.MapState.Idle
            return
        }

        val adapter =
            object : ArrayAdapter<String>(
                activity,
                R.layout.item_mission_file,
                names.toMutableList()
            ) {
                override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                    val view = super.getView(position, convertView, parent) as TextView
                    val isSelected = position == selectedPosition
                    view.setBackgroundResource(
                        if (isSelected) R.drawable.bg_ds_panel_pill_active
                        else android.R.color.transparent
                    )
                    view.setTextColor(
                        ContextCompat.getColor(
                            activity,
                            if (isSelected) R.color.ds_color_shell_selected_content
                            else R.color.ds_color_text_primary
                        )
                    )
                    return view
                }
            }
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_SINGLE
        listView.clearChoices()
        listView.requestLayout()

        if (!isBound) {
            bindOnce()
            isBound = true
        }

        currentFiles = files
        selectedPosition = -1
    }

    private fun bindOnce() {
        cancelButton.setOnClickListener {
            activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Idle)
        }

        confirmButton.setOnClickListener {
            val files = currentFiles
            if (selectedPosition !in files.indices) {
                Toast.makeText(activity, activity.getString(R.string.select_a_file), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedFile = files[selectedPosition]
            DiagnosticLog.event("mission", "mission_load_requested", data = mapOf("format" to selectedFile.extension))
            if (selectedFile.name.endsWith(activity.getString(R.string.waypoints))) {
                loadWaypointsFile(selectedFile)
            } else {
                MissionXmlParser(activity, activityViewModel).parseXml(
                    store.openMissionInputStream(selectedFile)
                )
            }
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val files = currentFiles
            if (position < 0 || position >= files.size) return@setOnItemClickListener
            selectedPosition = position
            listView.setItemChecked(position, true)
            (listView.adapter as? BaseAdapter)?.notifyDataSetChanged()
        }
    }

    private fun refreshList(): Pair<List<File>, List<String>> {
        val files = store.listMissionFiles()

        val waypointSuffix = activity.getString(R.string.waypoints)
        val names = files.map { file ->
            val lastDotIndex = file.name.lastIndexOf('.')
            if (file.name.endsWith(waypointSuffix)) {
                file.name
            } else if (lastDotIndex > 0) {
                file.name.substring(0, lastDotIndex)
            } else {
                file.name
            }
        }

        return Pair(files, names)
    }

    private fun loadWaypointsFile(file: File) {
        val items = store.parseWaypointsFile(store.openMissionInputStream(file))
        if (items.isEmpty()) {
            Toast.makeText(activity, activity.getString(R.string.no_survey_path_available), Toast.LENGTH_LONG).show()
            return
        }
        val path = items.map { LatLng(it.latitude, it.longitude) }
        val averageAltitude = items.map { it.altitudeMeters }.average().takeIf { !it.isNaN() } ?: 5.0
        activityViewModel.setPlanningWorkflow(PlanningWorkflow.AREA)
        activityViewModel.setPlanningOperationMode(PlanningOperationMode.SURVEY)
        activityViewModel.clearPolygonVertices()
        activityViewModel.surveyPath.postValue(path)
        activityViewModel.terrainSurveyWaypoints.postValue(emptyList())
        activityViewModel.updateSurveyHeightAboveTerrain(averageAltitude.toInt().coerceIn(0, 120))
        activityViewModel.mapState.postValue(MainActivityViewModel.MapState.SetFlightParams)
        DiagnosticLog.event("mission", "waypoints_loaded", data = mapOf("itemCount" to items.size, "averageAltitudeMeters" to averageAltitude))
    }
}
