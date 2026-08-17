package com.example.droneservicesapp.ui.home.binders

import android.content.Context
import android.location.Location
import android.view.View
import androidx.lifecycle.LifecycleOwner
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel

class MissionParamsController(
    context: Context,
    rootView: View,
    lifecycleOwner: LifecycleOwner,
    activityViewModel: MainActivityViewModel,
    droneViewModel: DroneViewModel,
    droneLocationProvider: (() -> Location?)? = null,
    beforeUploadGuard: (((onAllowed: () -> Unit) -> Unit))? = null,
    private val loadPreferencesOnShow: Boolean = true,
) {
    private var isBound = false

    private val views = MissionParamsViews(rootView)
    private val stateMapper = MissionParamsStateMapper(activityViewModel)
    private val preferencesBridge = MissionParamsPreferencesBridge(context, activityViewModel)
    private val renderer = MissionParamsRenderer(
        views = views,
        lifecycleOwner = lifecycleOwner,
        activityViewModel = activityViewModel,
        stateMapper = stateMapper
    )
    private val inputBinder = MissionParamsInputBinder(
        views = views,
        lifecycleOwner = lifecycleOwner,
        activityViewModel = activityViewModel
    )
    private val actionHandler = MissionParamsActionHandler(
        context = context,
        views = views,
        activityViewModel = activityViewModel,
        droneViewModel = droneViewModel,
        droneLocationProvider = droneLocationProvider,
        preferencesBridge = preferencesBridge,
        beforeUploadGuard = beforeUploadGuard
    )

    fun show() {
        if (isBound) return

        if (loadPreferencesOnShow) {
            preferencesBridge.loadIntoViewModel()
        }
        renderer.bind()
        inputBinder.bind()
        actionHandler.bind()
        isBound = true
    }
}
