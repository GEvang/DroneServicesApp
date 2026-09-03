package com.example.droneservicesapp.ui.shell.model

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.droneservicesapp.core.util.Event
import com.example.droneservicesapp.data.storage.SavedMission
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealth
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetRecord
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetInfo
import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneValidationResult
import com.example.droneservicesapp.domain.model.AltitudeReferenceMode
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.model.MissionArea
import com.example.droneservicesapp.domain.model.MissionObstacle
import com.example.droneservicesapp.domain.model.MissionObstacleShape
import com.example.droneservicesapp.domain.model.MissionParams
import com.example.droneservicesapp.domain.model.PlanningOperationMode
import com.example.droneservicesapp.domain.model.PlanningWorkflow
import com.example.droneservicesapp.domain.model.RouteWaypoint
import com.example.droneservicesapp.domain.model.SurveyGridParams
import com.example.droneservicesapp.domain.survey.SprayPresets
import com.example.droneservicesapp.domain.terrain.TerrainWaypoint
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
        MutableLiveData(0.0)
    }

    val lineDistanceProgress: MutableLiveData<Double> by lazy {
        MutableLiveData(5.0)
    }

    val angleProgress: MutableLiveData<Double> by lazy {
        MutableLiveData(90.0)
    }

    val sprayerProgress: MutableLiveData<Double> by lazy {
        MutableLiveData(75.0)
    }

    val flightSpeed: MutableLiveData<Double> by lazy {
        MutableLiveData(5.0)
    }

    val altitudeReferenceMode: MutableLiveData<AltitudeReferenceMode> by lazy {
        MutableLiveData(AltitudeReferenceMode.TERRAIN)
    }

    val selectedSprayPresetId: MutableLiveData<String> by lazy {
        MutableLiveData(SprayPresets.CUSTOM_ID)
    }

    val flightDistance: MutableLiveData<Int> by lazy {
        MutableLiveData(0)
    }

    val estimatedFlightMinutes = MediatorLiveData<Int>().apply {
        fun recompute() {
            val speed = flightSpeed.value?.toDouble() ?: 5.0
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

    val surveyGridParams: MutableLiveData<SurveyGridParams> by lazy {
        MutableLiveData(SurveyGridParams())
    }

    val surveyStripSpacing: MutableLiveData<Double> by lazy {
        MutableLiveData(surveyGridParams.value?.stripSpacingMeters?.toDouble() ?: 70.0)
    }

    val surveyHeightAboveTerrain: MutableLiveData<Double> by lazy {
        MutableLiveData(surveyGridParams.value?.heightAboveTerrainMeters?.toDouble() ?: 50.0)
    }

    val surveyOverlapPercent: MutableLiveData<Double> by lazy {
        MutableLiveData(surveyGridParams.value?.overlapPercent?.toDouble() ?: 80.0)
    }

    val surveyGridAngle: MutableLiveData<Double> by lazy {
        MutableLiveData(surveyGridParams.value?.gridAngleDegrees?.toDouble() ?: 90.0)
    }

    val surveyTerrainSegment: MutableLiveData<Double> by lazy {
        MutableLiveData(surveyGridParams.value?.terrainSegmentMeters ?: 2.5)
    }

    val surveyCanopySmoothing: MutableLiveData<Double> by lazy {
        MutableLiveData(surveyGridParams.value?.canopySmoothingMeters?.toDouble() ?: 5.0)
    }

    // ✅ Survey path from mission planning (separate from pure area model)
    val surveyPath: MutableLiveData<List<LatLng>> by lazy {
        MutableLiveData(emptyList())
    }

    val terrainSurveyWaypoints: MutableLiveData<List<TerrainWaypoint>> by lazy {
        MutableLiveData(emptyList())
    }

    /** True only when a georeferenced point cloud contributes points inside the drawn area. */
    val pointCloudCoversMissionArea: MutableLiveData<Boolean> by lazy {
        MutableLiveData(false)
    }

    val missionObstacles: MutableLiveData<List<MissionObstacle>> by lazy {
        MutableLiveData(emptyList())
    }

    val obstacleRadiusMeters: MutableLiveData<Double> by lazy {
        MutableLiveData(5.0)
    }

    val activePlanningWorkflow: MutableLiveData<PlanningWorkflow> by lazy {
        MutableLiveData(PlanningWorkflow.AREA)
    }

    val planningOperationMode: MutableLiveData<PlanningOperationMode> by lazy {
        MutableLiveData(PlanningOperationMode.SURVEY)
    }

    val routeWaypoints: MutableLiveData<List<RouteWaypoint>> by lazy {
        MutableLiveData(emptyList())
    }

    val plannedHomePosition: MutableLiveData<LatLon?> by lazy {
        MutableLiveData(null)
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

    fun addMissionObstacle(latitude: Double, longitude: Double, radiusMeters: Double) {
        val obstacle = MissionObstacle(
            id = "obstacle-${System.nanoTime()}",
            shape = MissionObstacleShape.CIRCLE,
            center = LatLon(latitude, longitude),
            radiusMeters = radiusMeters.coerceIn(2.0, 100.0)
        )
        missionObstacles.value = missionObstacles.value.orEmpty() + obstacle
    }

    fun addPolygonMissionObstacle(vertices: List<LatLng>) {
        if (vertices.size < 3) return
        val obstacle = MissionObstacle(
            id = "obstacle-${System.nanoTime()}",
            shape = MissionObstacleShape.POLYGON,
            vertices = vertices.map { LatLon(it.latitude, it.longitude) }
        )
        missionObstacles.value = missionObstacles.value.orEmpty() + obstacle
    }

    fun updateObstacleRadius(value: Int) {
        val radius = value.coerceIn(2, 100).toDouble()
        obstacleRadiusMeters.value = radius
        updateLastCircleObstacleRadius(radius)
    }

    private fun updateLastCircleObstacleRadius(radiusMeters: Double) {
        val obstacles = missionObstacles.value.orEmpty()
        val lastCircleIndex = obstacles.indexOfLast { it.shape == MissionObstacleShape.CIRCLE }
        if (lastCircleIndex == -1) return

        missionObstacles.value = obstacles.mapIndexed { index, obstacle ->
            if (index == lastCircleIndex) obstacle.copy(radiusMeters = radiusMeters) else obstacle
        }
    }

    fun removeMissionObstacle(id: String) {
        missionObstacles.value = missionObstacles.value.orEmpty().filterNot { it.id == id }
    }

    fun clearMissionObstacles() {
        missionObstacles.value = emptyList()
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
        angleProgress.value = value.coerceIn(0, 180).toDouble()
        updateMissionParams { copy(angle = angleProgress.value ?: 0.0) }
        markPresetCustomIfNeeded(markCustom)
    }

    fun updateLineSpacing(value: Int, markCustom: Boolean = true) {
        lineDistanceProgress.value = value.coerceIn(2, 20).toDouble()
        updateMissionParams { copy(lineDistance = lineDistanceProgress.value ?: 5.0) }
        markPresetCustomIfNeeded(markCustom)
    }

    fun updateAltitude(value: Int, markCustom: Boolean = true) {
        flightAltProgress.value = value.coerceIn(0, 20).toDouble()
        updateMissionParams { copy(altitude = flightAltProgress.value ?: 0.0) }
        markPresetCustomIfNeeded(markCustom)
    }

    fun updateSprayIntensity(value: Int, markCustom: Boolean = true) {
        sprayerProgress.value = value.coerceIn(0, 100).toDouble()
        updateMissionParams { copy(sprayer = sprayerProgress.value ?: 75.0) }
        markPresetCustomIfNeeded(markCustom)
    }

    fun updateMissionSpeed(value: Double, markCustom: Boolean = true) {
        flightSpeed.value = value.coerceIn(1.0, 10.0)
        updateMissionParams { copy(speed = flightSpeed.value ?: 5.0) }
        markPresetCustomIfNeeded(markCustom)
    }

    fun setPlanningWorkflow(workflow: PlanningWorkflow) {
        activePlanningWorkflow.value = workflow
    }

    fun setPlanningOperationMode(mode: PlanningOperationMode) {
        planningOperationMode.value = mode
    }

    fun updateSurveyStripSpacing(value: Int) {
        val normalized = value.coerceIn(0, 95)
        surveyStripSpacing.value = normalized.toDouble()
        updateSurveyGridParams {
            copy(stripSpacingMeters = normalized)
        }
    }

    fun updateSurveyHeightAboveTerrain(value: Int) {
        val normalized = value.coerceIn(0, 120)
        surveyHeightAboveTerrain.value = normalized.toDouble()
        updateSurveyGridParams {
            copy(heightAboveTerrainMeters = normalized)
        }
    }

    fun updateSurveyOverlap(value: Int) {
        val normalized = value.coerceIn(0, 95)
        surveyOverlapPercent.value = normalized.toDouble()
        updateSurveyGridParams {
            copy(overlapPercent = normalized)
        }
    }

    fun updateSurveyGridAngle(value: Int) {
        val normalized = value.coerceIn(0, 180)
        surveyGridAngle.value = normalized.toDouble()
        updateSurveyGridParams {
            copy(gridAngleDegrees = normalized)
        }
    }

    fun updateSurveyTerrainSegment(value: Double) {
        val normalized = value.coerceIn(0.5, 20.0)
        surveyTerrainSegment.value = normalized
        updateSurveyGridParams {
            copy(terrainSegmentMeters = normalized)
        }
    }

    fun updateSurveyCanopySmoothing(value: Int) {
        val normalized = value.coerceIn(0, 30)
        surveyCanopySmoothing.value = normalized.toDouble()
        updateSurveyGridParams {
            copy(canopySmoothingMeters = normalized)
        }
    }

    fun addRouteWaypoint(latitude: Double, longitude: Double) {
        val existing = routeWaypoints.value.orEmpty()
        val index = existing.size + 1
        val waypoint = RouteWaypoint(
            id = "route-$index-${System.nanoTime()}",
            index = index,
            latitude = latitude,
            longitude = longitude,
            altitudeMeters = flightAltProgress.value ?: 0.0,
            speedMetersPerSecond = flightSpeed.value ?: 5.0,
            sprayEnabled = planningOperationMode.value == PlanningOperationMode.SPRAY,
            sprayerIntensityPercent = (sprayerProgress.value ?: 75.0).toInt().coerceIn(0, 100)
        )
        routeWaypoints.value = existing + waypoint
    }

    fun setRouteWaypoints(waypoints: List<RouteWaypoint>) {
        routeWaypoints.value = renumberRouteWaypoints(waypoints)
    }

    fun updateSurveyWaypoint(index: Int, point: LatLng, terrainWaypoint: TerrainWaypoint?) {
        val path = surveyPath.value.orEmpty()
        if (index !in path.indices) return
        surveyPath.value = path.mapIndexed { pathIndex, existing ->
            if (pathIndex == index) point else existing
        }

        val terrainPath = terrainSurveyWaypoints.value.orEmpty()
        terrainSurveyWaypoints.value = if (terrainPath.size == path.size && terrainWaypoint != null) {
            terrainPath.mapIndexed { waypointIndex, existing ->
                if (waypointIndex == index) terrainWaypoint else existing
            }
        } else {
            emptyList()
        }
    }

    fun removeSurveyWaypoint(index: Int) {
        val path = surveyPath.value.orEmpty()
        if (index !in path.indices) return
        surveyPath.value = path.filterIndexed { pathIndex, _ -> pathIndex != index }

        val terrainPath = terrainSurveyWaypoints.value.orEmpty()
        terrainSurveyWaypoints.value = if (terrainPath.size == path.size) {
            terrainPath.filterIndexed { waypointIndex, _ -> waypointIndex != index }
        } else {
            emptyList()
        }
    }

    fun updateSurveyWaypointAltitude(index: Int, altitudeMeters: Double) {
        val path = surveyPath.value.orEmpty()
        if (index !in path.indices) return
        val terrainPath = terrainSurveyWaypoints.value.orEmpty()
        val normalizedAltitude = altitudeMeters.coerceAtLeast(0.0)
        terrainSurveyWaypoints.value = if (terrainPath.size == path.size) {
            terrainPath.mapIndexed { waypointIndex, waypoint ->
                if (waypointIndex == index) {
                    waypoint.copy(
                        displayAltitudeMeters = normalizedAltitude,
                        missionAltitudeMeters = normalizedAltitude
                    )
                } else {
                    waypoint
                }
            }
        } else {
            path.mapIndexed { waypointIndex, point ->
                TerrainWaypoint(
                    latLon = LatLon(point.latitude, point.longitude),
                    displayAltitudeMeters = normalizedAltitude,
                    missionAltitudeMeters = normalizedAltitude
                ).takeIf { waypointIndex == index }
                    ?: TerrainWaypoint(
                        latLon = LatLon(point.latitude, point.longitude),
                        displayAltitudeMeters = surveyHeightAboveTerrain.value ?: 0.0,
                        missionAltitudeMeters = surveyHeightAboveTerrain.value ?: 0.0
                    )
            }
        }
    }

    fun undoLastRouteWaypoint() {
        val existing = routeWaypoints.value.orEmpty()
        if (existing.isEmpty()) return
        routeWaypoints.value = renumberRouteWaypoints(existing.dropLast(1))
    }

    fun clearRouteWaypoints() {
        routeWaypoints.value = emptyList()
    }

    fun setPlannedHomePosition(position: LatLon?) {
        plannedHomePosition.value = position
    }

    fun clearPlannedHomePosition() {
        plannedHomePosition.value = null
    }

    fun applySavedMission(mission: SavedMission) {
        setPlanningOperationMode(mission.operationMode)
        setPlanningWorkflow(mission.workflow)
        updateMissionAngle(mission.angleDegrees, markCustom = false)
        updateLineSpacing(mission.lineDistanceMeters, markCustom = false)
        updateAltitude(mission.altitudeMeters, markCustom = false)
        updateSprayIntensity(mission.sprayerIntensityPercent, markCustom = false)
        updateMissionSpeed(mission.flightSpeedMetersPerSecond, markCustom = false)
        setAltitudeReferenceMode(mission.altitudeReferenceMode)
        surveyGridParams.value = mission.surveyGridParams
        surveyStripSpacing.value = mission.surveyGridParams.stripSpacingMeters.toDouble()
        surveyHeightAboveTerrain.value = mission.surveyGridParams.heightAboveTerrainMeters.toDouble()
        surveyOverlapPercent.value = mission.surveyGridParams.overlapPercent.toDouble()
        surveyGridAngle.value = mission.surveyGridParams.gridAngleDegrees.toDouble()
        surveyTerrainSegment.value = mission.surveyGridParams.terrainSegmentMeters
        surveyCanopySmoothing.value = mission.surveyGridParams.canopySmoothingMeters.toDouble()
        setPolygonVertices(mission.polygon)
        setRouteWaypoints(mission.routeWaypoints)
        plannedHomePosition.value = mission.plannedHomePosition
        missionObstacles.value = mission.obstacles
        surveyPath.value = mission.surveyPath
        terrainSurveyWaypoints.value = mission.terrainSurveyWaypoints.map {
            TerrainWaypoint(
                latLon = it.position,
                displayAltitudeMeters = it.displayAltitudeMeters,
                missionAltitudeMeters = it.missionAltitudeMeters
            )
        }
        mapState.value = MapState.SetFlightParams
    }

    private fun markPresetCustomIfNeeded(markCustom: Boolean) {
        if (markCustom && selectedSprayPresetId.value != SprayPresets.CUSTOM_ID) {
            selectedSprayPresetId.value = SprayPresets.CUSTOM_ID
        }
    }

    private fun updateMissionParams(update: MissionParams.() -> MissionParams) {
        missionParams.value = (missionParams.value ?: MissionParams()).update()
    }

    private fun updateSurveyGridParams(update: SurveyGridParams.() -> SurveyGridParams) {
        surveyGridParams.value = (surveyGridParams.value ?: SurveyGridParams()).update()
    }

    private fun renumberRouteWaypoints(waypoints: List<RouteWaypoint>): List<RouteWaypoint> {
        return waypoints.mapIndexed { index, waypoint ->
            waypoint.copy(index = index + 1)
        }
    }

    fun notifyGeoZoneDatasetReloaded(): Long {
        val token = System.currentTimeMillis()
        geoZoneReloadToken.postValue(token)
        return token
    }
}
