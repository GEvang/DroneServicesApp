package com.example.droneservicesapp.ui.home.binders

import com.example.droneservicesapp.ui.home.model.HomeMapScreenMode

class HomeMapModeEffectsBinder(
    private val missionParamsController: MissionParamsController,
    private val missionSaveController: MissionSaveController,
    private val missionLoadController: MissionLoadController,
    private val onEnterIdle: () -> Unit,
) {
    private var lastScreenMode: HomeMapScreenMode? = null

    fun render(screenMode: HomeMapScreenMode) {
        if (lastScreenMode == screenMode) return
        lastScreenMode = screenMode

        when (screenMode) {
            HomeMapScreenMode.Idle -> onEnterIdle()
            HomeMapScreenMode.Drawing -> Unit
            HomeMapScreenMode.EditingParams -> missionParamsController.show()
            HomeMapScreenMode.SavingMission -> missionSaveController.show()
            HomeMapScreenMode.LoadingMission -> missionLoadController.show()
        }
    }
}
