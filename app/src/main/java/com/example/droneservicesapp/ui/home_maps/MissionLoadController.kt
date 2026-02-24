package com.example.droneservicesapp.ui.home_maps

import android.os.Environment
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import com.example.droneservicesapp.data.storage.MissionFileHandler
import com.example.droneservicesapp.R
import com.example.droneservicesapp.activities.MainActivityViewModel
import java.io.File
import java.io.FileInputStream
import java.util.Locale

/**
 * Owns the "Load mission" UI (load_file_selector_layout) and calls MissionFileHandler.parseXml(...).
 *
 * Extracted from HomeMapsFragment.fileLoaderDialog().
 */
class MissionLoadController(
    private val activity: FragmentActivity,
    private val rootView: View,
    private val activityViewModel: MainActivityViewModel,
) {

    private var isBound = false

    private var currentFiles: List<File> = emptyList()

    private val loadFileView: LinearLayoutCompat by lazy {
        rootView.findViewById<LinearLayoutCompat>(R.id.load_file_selector_layout)
    }

    private val listView: ListView by lazy {
        rootView.findViewById<ListView>(R.id.file_list)
    }

    private val cancelButton: Button by lazy {
        rootView.findViewById<Button>(R.id.btn_cancel)
    }

    fun show() {
        val (files, names) = refreshList()

        if (files == null) {
            Toast.makeText(activity, "Error in loading missions from directory", Toast.LENGTH_LONG).show()
            return
        }
        if (files.isEmpty()) {
            Toast.makeText(activity, "No missions saved yet", Toast.LENGTH_LONG).show()
            return
        }

        loadFileView.isVisible = true

        val adapter =
            ArrayAdapter(activity, android.R.layout.simple_list_item_1, names.toMutableList())
        listView.adapter = adapter

        if (!isBound) {
            bindOnce()
            isBound = true
        }

        // Keep current files list for click handler
        currentFiles = files
    }

    private fun bindOnce() {
        cancelButton.setOnClickListener {
            loadFileView.isVisible = false
            activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Idle)
        }


        listView.setOnItemClickListener { _, _, position, _ ->
            val files = currentFiles
            if (position < 0 || position >= files.size) return@setOnItemClickListener

            val selectedFile = files[position]

            MissionFileHandler(activity, activityViewModel).parseXml(
                FileInputStream(selectedFile.path)
            )

            Toast.makeText(activity, "Selected file: ${selectedFile.path}", Toast.LENGTH_SHORT).show()

            loadFileView.isVisible = false
        }
    }

    private fun refreshList(): Pair<List<File>?, List<String>> {
        val directory = activity.getString(R.string.mission_directory).let {
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                it
            )
        }

        val files = directory.listFiles { _, name ->
            name.lowercase(Locale.ROOT)
                .endsWith(activity.getString(R.string.DroneServicesFilePageSuffix).lowercase(Locale.ROOT))
        }?.toList()

        val names = files?.map { it.name.substring(0, it.name.lastIndexOf('.')) } ?: emptyList()
        return Pair(files, names)
    }

}
