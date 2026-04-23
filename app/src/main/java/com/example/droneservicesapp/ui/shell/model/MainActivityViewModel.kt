package com.example.droneservicesapp.ui.shell.model

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.droneservicesapp.core.util.Event
import com.example.droneservicesapp.domain.model.MissionArea
import com.example.droneservicesapp.domain.model.MissionParams
import com.google.android.gms.maps.model.LatLng

class MainActivityViewModel : ViewModel() {

    enum class MapState {
        Idle,
        Draw,
        SetFlightParams,
        LoadMissionFromFile,
        SaveMissionToFile
    }

    sealed class MapAction {
        object ClearAll : MapAction()
        object ClearKeepDrawing : MapAction()
        object ResetToIdle : MapAction()
        object UploadMissionSuccess : MapAction()
        data class UploadMissionFailed(val reason: String) : MapAction()
    }

    val mapState: MutableLiveData<MapState> by lazy {
        MutableLiveData(MapState.Idle)
    }

    val mapAction: MutableLiveData<Event<MapAction>> by lazy {
        MutableLiveData()
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
    val missionArea: MutableLiveData<MissionArea> by lazy {
        MutableLiveData(MissionArea())
    }

    // ✅ Scoped to the ViewModel
    val missionParams: MutableLiveData<MissionParams> by lazy {
        MutableLiveData(MissionParams())
    }

    // ✅ Survey path from mission planning (separate from pure area model)
    val surveyPath: MutableLiveData<List<LatLng>> by lazy {
        MutableLiveData(emptyList())
    }

    fun setPolygonVertices(vertices: List<LatLng>) {
        val a = missionArea.value ?: return
        a.vertices.clear()
        a.vertices.addAll(vertices)
        missionArea.postValue(a)
    }

    fun clearPolygonVertices() {
        val a = missionArea.value ?: return
        a.vertices.clear()
        missionArea.postValue(a)
    }

    fun sendAction(action: MapAction) {
        mapAction.postValue(Event(action))
    }
}
