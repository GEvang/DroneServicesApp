package com.example.droneservicesapp.mavserver

import android.location.Location
import io.reactivex.disposables.Disposable

internal class DroneRuntimeState {
    @Volatile var bridgeAttached: Boolean = false
    @Volatile var lastNonHeartbeatMs: Long = 0L
    @Volatile var lastDroneLocation: Location? = null
    @Volatile var autopilotSysId: Int = -1
    @Volatile var autopilotCompId: Int = -1
    @Volatile var lastLoggedConnectionState: Boolean? = null
    @Volatile var mavlinkMessagesDisposable: Disposable? = null

    fun clearAutopilotTarget() {
        autopilotSysId = -1
        autopilotCompId = -1
    }
}
