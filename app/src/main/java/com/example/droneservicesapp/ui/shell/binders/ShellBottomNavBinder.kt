package com.example.droneservicesapp.ui.shell.binders

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleOwner
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.storage.MissionFileStore
import com.example.droneservicesapp.ui.shell.model.BottomActionBarUiState
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView

class ShellBottomNavBinder(
    private val activity: AppCompatActivity,
    private val bottomNavigationView: BottomNavigationView,
    private val activityViewModel: MainActivityViewModel,
) {
    private val missionFileStore = MissionFileStore(activity)

    fun bind(lifecycleOwner: LifecycleOwner) {
        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.action_draw -> {
                    activityViewModel.missionArea.value?.clearAll()
                    activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Draw)
                }
                R.id.action_accept -> {
                    val verts = activityViewModel.missionArea.value?.vertices ?: emptyList()
                    if (verts.size < 3) {
                        Toast.makeText(
                            activity,
                            activity.getString(R.string.wrong_schema_msg),
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        activityViewModel.mapState.postValue(MainActivityViewModel.MapState.SetFlightParams)
                    }
                }
                R.id.action_cancel -> {
                    activityViewModel.sendAction(MainActivityViewModel.MapAction.ResetToIdle)
                }
                R.id.action_erase -> {
                    activityViewModel.sendAction(MainActivityViewModel.MapAction.ClearAll)
                }
                R.id.action_load -> {
                    if (missionFileStore.listMissionFiles().isEmpty()) {
                        Toast.makeText(
                            activity,
                            activity.getString(R.string.no_saved_missions_yet),
                            Toast.LENGTH_LONG
                        ).show()
                        activityViewModel.mapState.value = MainActivityViewModel.MapState.Idle
                        return@setOnItemSelectedListener true
                    }
                    activityViewModel.missionArea.value?.clearAll()
                    activityViewModel.mapState.postValue(MainActivityViewModel.MapState.LoadMissionFromFile)
                }
            }
            true
        }

        activityViewModel.mapState.observe(lifecycleOwner) { mapState ->
            render(BottomActionBarUiState.fromMapState(mapState))
        }
    }

    private fun render(state: BottomActionBarUiState) {
        if (state.preserveExistingMenu) return

        val menu = bottomNavigationView.menu
        val cancelItem = menu.findItem(R.id.action_cancel)
        val acceptItem = menu.findItem(R.id.action_accept)
        val eraseItem = menu.findItem(R.id.action_erase)
        val drawItem = menu.findItem(R.id.action_draw)
        val loadItem = menu.findItem(R.id.action_load)

        cancelItem.isVisible = state.showCancel
        acceptItem.isVisible = state.showAccept
        eraseItem.isVisible = state.showErase
        drawItem.isVisible = state.showDraw
        loadItem.isVisible = state.showLoad

        val checkedItem = menu.findItem(bottomNavigationView.selectedItemId)
        if (checkedItem == null || !checkedItem.isVisible) {
            drawItem.isChecked = drawItem.isVisible
        }
    }
}
