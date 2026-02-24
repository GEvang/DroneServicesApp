package com.example.droneservicesapp.activities

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.droneservicesapp.domain.model.MissionArea

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

    // ✅ Was global before; now scoped to the ViewModel
    val missionParams: MutableLiveData<MissionParams> by lazy {
        MutableLiveData(
            MissionParams(
                altitude = flightAltProgress.value ?: 2.0,
                lineDistance = lineDistanceProgress.value ?: 1.0,
                angle = angleProgress.value ?: 1.0,
                sprayer = sprayerProgress.value ?: 0.0,
                speed = flightSpeed.value ?: 1.0
            )
        )
    }
}

data class MissionParams(
    val altitude: Double = 2.0,
    val lineDistance: Double = 1.0,
    val angle: Double = 1.0,
    val sprayer: Double = 0.0,
    val speed: Double = 1.0
)