package com.example.droneservicesapp.ui.shell.binders

import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleOwner
import com.example.droneservicesapp.R
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
            render(mapState)
        }
    }

    private fun render(mapState: MainActivityViewModel.MapState) {
        Log.i("Map State", "Map State Changed to: $mapState")

        val menu = bottomNavigationView.menu
        fun setMenuVisibility(
            cancel: Boolean,
            accept: Boolean,
            erase: Boolean,
            draw: Boolean,
            load: Boolean,
        ) {
            menu.findItem(R.id.action_cancel).isVisible = cancel
            menu.findItem(R.id.action_accept).isVisible = accept
            menu.findItem(R.id.action_erase).isVisible = erase
            menu.findItem(R.id.action_draw).isVisible = draw
            menu.findItem(R.id.action_load).isVisible = load
        }

        when (mapState) {
            MainActivityViewModel.MapState.Idle -> {
                setMenuVisibility(cancel = false, accept = false, erase = false, draw = true, load = true)
            }
            MainActivityViewModel.MapState.Draw,
            MainActivityViewModel.MapState.SetFlightParams,
            MainActivityViewModel.MapState.LoadMissionFromFile -> {
                setMenuVisibility(cancel = true, accept = true, erase = true, draw = false, load = false)
            }
            MainActivityViewModel.MapState.SaveMissionToFile -> {
                // no-op
            }
        }
    }
}
