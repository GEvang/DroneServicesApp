package com.example.droneservicesapp.ui.shell.binders

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleOwner
import com.example.droneservicesapp.R
import com.example.droneservicesapp.ui.shell.model.BottomActionBarUiState
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView

class ShellBottomNavBinder(
    private val activity: AppCompatActivity,
    private val bottomNavigationView: BottomNavigationView,
    private val activityViewModel: MainActivityViewModel,
) {
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
        menu.findItem(R.id.action_cancel).isVisible = state.showCancel
        menu.findItem(R.id.action_accept).isVisible = state.showAccept
        menu.findItem(R.id.action_erase).isVisible = state.showErase
        menu.findItem(R.id.action_draw).isVisible = state.showDraw
        menu.findItem(R.id.action_load).isVisible = state.showLoad
    }
}
