package com.example.droneservicesapp.mavserver

import android.content.Context
import com.example.droneservicesapp.R
import android.location.Location
import androidx.lifecycle.MutableLiveData
import com.example.droneservicesapp.data.rtk.RtkForwardingState
import io.dronefleet.mavlink.common.MissionItemInt

internal class DroneUiStateStore(
    context: Context
) {
    val droneLocationLiveData: MutableLiveData<Location> =
        MutableLiveData<Location>().default(Location(""))
    val conStateLiveData: MutableLiveData<Boolean> =
        MutableLiveData<Boolean>().default(false)
    val telemetryAliveLiveData: MutableLiveData<Boolean> =
        MutableLiveData<Boolean>().default(false)
    val armedState: MutableLiveData<Boolean> =
        MutableLiveData<Boolean>().default(false)
    val droneHeading: MutableLiveData<Double> =
        MutableLiveData<Double>().default(0.0)
    val droneBatteryVoltage: MutableLiveData<Float> =
        MutableLiveData<Float>().default(0.0F)
    val droneBatteryPercentage: MutableLiveData<Float> =
        MutableLiveData<Float>().default(0.0F)
    val droneFrontDistance: MutableLiveData<Int> = MutableLiveData()
    val droneBackDistance: MutableLiveData<Int> = MutableLiveData()
    val droneFlightMode: MutableLiveData<Int> =
        MutableLiveData<Int>().default(0)
    val rcRSSI: MutableLiveData<Float> =
        MutableLiveData<Float>().default(0.0F)
    val missionItems: MutableLiveData<ArrayList<MissionItemInt>> =
        MutableLiveData<ArrayList<MissionItemInt>>().default(ArrayList())
    val liquidLevel: MutableLiveData<Float> =
        MutableLiveData<Float>().default(0.0F)
    val uploadProgressPercent: MutableLiveData<Int> =
        MutableLiveData<Int>().default(0)
    val rtkForwardingState: MutableLiveData<RtkForwardingState> =
        MutableLiveData<RtkForwardingState>().default(RtkForwardingState.Idle)
    val rtkGpsDebugStatus: MutableLiveData<String> =
        MutableLiveData<String>().default(context.getString(R.string.rtk_gps_debug_default))

    private fun <T : Any?> MutableLiveData<T>.default(initialValue: T) =
        apply { postValue(initialValue) }
}
