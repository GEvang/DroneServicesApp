package com.example.droneservicesapp.ui.main

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.droneservicesapp.domain.model.MissionArea
import com.example.droneservicesapp.domain.model.MissionParams

class MainActivityViewModel : ViewModel() {

    enum class MapState {
        Idle,
        Reset,
        ClearAll,
        ClearKeepDrawing,
        Draw,
        SetFlightParams,
        UploadMissionSuccess,
        LoadMissionFromFile,
        SaveMissionToFile
    }

    val mapState: MutableLiveData<MapState> by lazy {
        MutableLiveData(MapState.Idle)
    }

    val drawEnableLiveData: MutableLiveData<Boolean> by lazy {
        MutableLiveData(false)
    }

    val flightAltProgress: MutableLiveData<Double> by lazy {
        MutableLiveData(2.0)
    }

    val lineDistanceProgress: MutableLiveData<Double> by lazy {
        MutableLiveData(1.0)
    }

    val angleProgress: MutableLiveData<Double> by lazy {
        MutableLiveData(1.0)
    }

    val sprayerProgress: MutableLiveData<Double> by lazy {
        MutableLiveData(0.0)
    }

    val flightSpeed: MutableLiveData<Double> by lazy {
        MutableLiveData(1.0)
    }

    val flightDistance: MutableLiveData<Int> by lazy {
        MutableLiveData(0)
    }

    val estimatedFlightMinutes = MediatorLiveData<Int>().apply {
        fun recompute() {
            val speed = flightSpeed.value?.toDouble() ?: 1.0
            val dist = flightDistance.value?.toDouble() ?: 0.0

            val minutes = (dist / (speed * 60.0)).toInt()
            value = if (minutes > 0) minutes else 1
        }

        addSource(flightSpeed) { recompute() }
        addSource(flightDistance) { recompute() }

        recompute()
    }

    // ✅ Pure model
    val area: MutableLiveData<MissionArea> by lazy {
        MutableLiveData(MissionArea())
    }

    // ✅ Scoped to the ViewModel
    val missionParams: MutableLiveData<MissionParams> by lazy {
        MutableLiveData(MissionParams())
    }
}