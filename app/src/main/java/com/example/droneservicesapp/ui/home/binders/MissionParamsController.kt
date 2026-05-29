package com.example.droneservicesapp.ui.home.binders

import android.content.Context
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
    beforeUploadGuard: (((onAllowed: () -> Unit) -> Unit))? = null,
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
        activityViewModel = activityViewModel
    )
    private val actionHandler = MissionParamsActionHandler(
        context = context,
        views = views,
        activityViewModel = activityViewModel,
        droneViewModel = droneViewModel,
        preferencesBridge = preferencesBridge,
        beforeUploadGuard = beforeUploadGuard
    )

    fun show() {
        if (isBound) return

        preferencesBridge.loadIntoViewModel()
        renderer.bind()
        inputBinder.bind()
        actionHandler.bind()
        isBound = true
    }
}
