package com.example.droneservicesapp.ui.shell.model

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.droneservicesapp.core.util.Event
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealth
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetRecord
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetInfo
import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneValidationResult
import com.example.droneservicesapp.domain.model.AltitudeReferenceMode
import com.example.droneservicesapp.domain.model.MissionArea
import com.example.droneservicesapp.domain.model.MissionParams
import com.example.droneservicesapp.domain.survey.SprayPresets
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
        object ClearAreaOnly : MapAction()
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

    val altitudeReferenceMode: MutableLiveData<AltitudeReferenceMode> by lazy {
        MutableLiveData(AltitudeReferenceMode.RELATIVE)
    }

    val selectedSprayPresetId: MutableLiveData<String> by lazy {
        MutableLiveData(SprayPresets.CUSTOM_ID)
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

    val geoAwarenessLayerVisible: MutableLiveData<Boolean> by lazy {
        MutableLiveData(true)
    }

    val geoZoneDatasetInfo: MutableLiveData<GeoZoneDatasetInfo?> by lazy {
        MutableLiveData(null)
    }

    val geoAwarenessHealth: MutableLiveData<GeoAwarenessHealth?> by lazy {
        MutableLiveData(null)
    }

    val geoZoneValidationResult: MutableLiveData<GeoZoneValidationResult?> by lazy {
        MutableLiveData(null)
    }

    val geoZoneDatasetRecords: MutableLiveData<List<GeoZoneDatasetRecord>> by lazy {
        MutableLiveData(emptyList())
    }

    val geoZoneImportedActive: MutableLiveData<Boolean> by lazy {
        MutableLiveData(false)
    }

    val geoZoneReloadToken: MutableLiveData<Long> by lazy {
        MutableLiveData(0L)
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

    fun setAltitudeReferenceMode(mode: AltitudeReferenceMode) {
        altitudeReferenceMode.value = mode
        missionParams.value = (missionParams.value ?: MissionParams()).copy(
            altitudeReferenceMode = mode
        )
    }

    fun applySprayPreset(presetId: String) {
        val preset = SprayPresets.byId(presetId)
        selectedSprayPresetId.value = preset.id
        if (preset.id == SprayPresets.CUSTOM_ID) return

        updateMissionAngle(preset.missionAngleDeg, markCustom = false)
        updateLineSpacing(preset.lineSpacingM, markCustom = false)
        updateAltitude(preset.altitudeM, markCustom = false)
        updateSprayIntensity(preset.sprayIntensityPercent, markCustom = false)
        updateMissionSpeed(preset.missionSpeedMs, markCustom = false)
    }

    fun updateMissionAngle(value: Int, markCustom: Boolean = true) {
        angleProgress.value = value.coerceIn(0, 90).toDouble()
        updateMissionParams { copy(angle = angleProgress.value ?: 0.0) }
        markPresetCustomIfNeeded(markCustom)
    }

    fun updateLineSpacing(value: Int, markCustom: Boolean = true) {
        lineDistanceProgress.value = value.coerceIn(2, 20).toDouble()
        updateMissionParams { copy(lineDistance = lineDistanceProgress.value ?: 2.0) }
        markPresetCustomIfNeeded(markCustom)
    }

    fun updateAltitude(value: Int, markCustom: Boolean = true) {
        flightAltProgress.value = value.coerceIn(2, 20).toDouble()
        updateMissionParams { copy(altitude = flightAltProgress.value ?: 2.0) }
        markPresetCustomIfNeeded(markCustom)
    }

    fun updateSprayIntensity(value: Int, markCustom: Boolean = true) {
        sprayerProgress.value = value.coerceIn(0, 100).toDouble()
        updateMissionParams { copy(sprayer = sprayerProgress.value ?: 0.0) }
        markPresetCustomIfNeeded(markCustom)
    }

    fun updateMissionSpeed(value: Double, markCustom: Boolean = true) {
        flightSpeed.value = value.coerceIn(1.0, 5.0)
        updateMissionParams { copy(speed = flightSpeed.value ?: 1.0) }
        markPresetCustomIfNeeded(markCustom)
    }

    private fun markPresetCustomIfNeeded(markCustom: Boolean) {
        if (markCustom && selectedSprayPresetId.value != SprayPresets.CUSTOM_ID) {
            selectedSprayPresetId.value = SprayPresets.CUSTOM_ID
        }
    }

    private fun updateMissionParams(update: MissionParams.() -> MissionParams) {
        missionParams.value = (missionParams.value ?: MissionParams()).update()
    }

    fun notifyGeoZoneDatasetReloaded() {
        geoZoneReloadToken.postValue(System.currentTimeMillis())
    }
}
