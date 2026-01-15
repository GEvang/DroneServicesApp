package com.example.droneservicesapp.mavserver

import android.location.Location
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.dronefleet.mavlink.common.MissionItemInt

class DroneViewModel : ViewModel() {

    val mavlinkCommunicationLiveData : MutableLiveData<MavLinkComm> by lazy{
        MutableLiveData<MavLinkComm>().default(MavLinkComm(null))
    }

    val droneLocationLiveData: MutableLiveData<Location> by lazy {
        MutableLiveData<Location>().default(Location(""))
    }

    val conStateLiveData: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>().default(false)
    }

    val armedState: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>().default(false)
    }

    val droneHeading: MutableLiveData<Double> by lazy {
        MutableLiveData<Double>().default(0.0)
    }

    val droneBatteryVoltage: MutableLiveData<Float> by lazy {
        MutableLiveData<Float>().default(0.0F)
    }

    val droneBatteryPercentage: MutableLiveData<Float> by lazy {
        MutableLiveData<Float>().default(0.0F)
    }

    val droneFrontDistance: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>()
    }

    val droneBackDistance: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>()
    }

    val droneFlightMode: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>().default(0)
    }

    val rcRSSI: MutableLiveData<Float> by lazy{
        MutableLiveData<Float>().default(0.0F)
    }

    val missionItems: MutableLiveData<ArrayList<MissionItemInt>> by lazy{
        MutableLiveData<ArrayList<MissionItemInt>>().default(ArrayList())
    }

    val liquidLevel: MutableLiveData<Float> by lazy {
        MutableLiveData<Float>().default(0.0F)
    }

    private fun <T : Any?> MutableLiveData<T>.default(initialValue: T) = apply { setValue(initialValue) }

}


