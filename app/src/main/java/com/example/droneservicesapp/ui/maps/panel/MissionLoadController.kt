package com.example.droneservicesapp.ui.maps.panel

import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.storage.MissionFileStore
import com.example.droneservicesapp.data.storage.MissionXmlParser
import com.example.droneservicesapp.ui.main.MainActivityViewModel
import java.io.File

/**
 * Owns the "Load mission" UI (load_file_selector_layout) and calls MissionFileHandler.parseXml(...).
 *
 * Extracted from MissionMapFragment.fileLoaderDialog().
 */
class MissionLoadController(
    private val activity: FragmentActivity,
    private val rootView: View,
    private val activityViewModel: MainActivityViewModel,
) {

    private var isBound = false

    private var currentFiles: List<File> = emptyList()

    private val store = MissionFileStore(activity)

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

            MissionXmlParser(activity, activityViewModel).parseXml(
                store.openMissionInputStream(selectedFile)
            )

            Toast.makeText(activity, "Selected file: ${selectedFile.path}", Toast.LENGTH_SHORT).show()

            loadFileView.isVisible = false
        }
    }

    private fun refreshList(): Pair<List<File>, List<String>> {
        val files = store.listMissionFiles()

        val names = files.map { file ->
            val lastDotIndex = file.name.lastIndexOf('.')
            if (lastDotIndex > 0) {
                file.name.substring(0, lastDotIndex)
            } else {
                file.name
            }
        }

        return Pair(files, names)
    }

}
