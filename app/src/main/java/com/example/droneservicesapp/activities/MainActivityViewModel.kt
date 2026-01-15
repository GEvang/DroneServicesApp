package com.example.droneservicesapp.activities

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.droneservicesapp.shape.PolygonArea

class MainActivityViewModel : ViewModel() {

    enum class MapState{
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

    val mapState : MutableLiveData<MapState> by lazy {
        MutableLiveData<MapState>().default(MapState.Idle)
    }

    val drawEnableLiveData: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>().default(false)
    }

    val flightAltProgress: MutableLiveData<Double> by lazy {
        MutableLiveData<Double>().default(2.0)
    }

    val lineDistanceProgress: MutableLiveData<Double> by lazy {
        MutableLiveData<Double>().default(1.0)
    }

    val angleProgress: MutableLiveData<Double> by lazy {
        MutableLiveData<Double>().default(1.0)
    }

    val sprayerProgress: MutableLiveData<Double> by lazy {
        MutableLiveData<Double>().default(0.0)
    }

    val flightSpeed: MutableLiveData<Double> by lazy {
        MutableLiveData<Double>().default(1.0)
    }

    val flightDistance: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>().default(0)
    }

    val area : MutableLiveData<PolygonArea> by lazy{
        MutableLiveData<PolygonArea>().default(PolygonArea())
    }

    private fun <T : Any?> MutableLiveData<T>.default(initialValue: T) = apply { setValue(initialValue) }
}