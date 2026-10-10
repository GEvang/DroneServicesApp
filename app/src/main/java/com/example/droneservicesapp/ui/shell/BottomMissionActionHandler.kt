package com.example.droneservicesapp.ui.shell

import android.content.Context
import android.widget.Toast
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.storage.MissionFileStore
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel

enum class BottomMissionAction { DRAW, ACCEPT, ERASE, CANCEL, LOAD }

/** Executes shell actions without depending on an Android menu or navigation View. */
class BottomMissionActionHandler(
    private val context: Context,
    private val activityViewModel: MainActivityViewModel,
    private val missionFileStore: MissionFileStore = MissionFileStore(context),
) {
    fun handle(action: BottomMissionAction) {
        when (action) {
            BottomMissionAction.DRAW -> {
                activityViewModel.missionArea.value?.clearAll()
                activityViewModel.mapState.value = MainActivityViewModel.MapState.Draw
            }
            BottomMissionAction.ACCEPT -> acceptMissionArea()
            BottomMissionAction.ERASE -> {
                activityViewModel.sendAction(MainActivityViewModel.MapAction.ClearAll)
            }
            BottomMissionAction.CANCEL -> {
                activityViewModel.sendAction(MainActivityViewModel.MapAction.ResetToIdle)
            }
            BottomMissionAction.LOAD -> loadMission()
        }
    }

    private fun acceptMissionArea() {
        val vertices = activityViewModel.missionArea.value?.vertices.orEmpty()
        if (vertices.size < 3) {
            Toast.makeText(context, R.string.wrong_schema_msg, Toast.LENGTH_LONG).show()
            return
        }
        activityViewModel.mapState.value = MainActivityViewModel.MapState.SetFlightParams
    }

    private fun loadMission() {
        if (missionFileStore.listMissionFiles().isEmpty()) {
            Toast.makeText(context, R.string.no_saved_missions_yet, Toast.LENGTH_LONG).show()
            activityViewModel.mapState.value = MainActivityViewModel.MapState.Idle
            return
        }
        activityViewModel.missionArea.value?.clearAll()
        activityViewModel.mapState.value = MainActivityViewModel.MapState.LoadMissionFromFile
    }
}
