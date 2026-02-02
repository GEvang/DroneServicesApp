package com.example.droneservicesapp.activities

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.droneservicesapp.shape.PolygonArea

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

    val area: MutableLiveData<PolygonArea> by lazy {
        MutableLiveData(PolygonArea())
    }
}

data class MissionParams(
    val altitude: Double = 2.0,
    val lineDistance: Double = 1.0,
    val angle: Double = 1.0,
    val sprayer: Double = 0.0,
    val speed: Double = 1.0
)

val missionParams: MutableLiveData<MissionParams> = MutableLiveData()