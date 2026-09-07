package com.example.droneservicesapp.ui.home.binders

import android.content.Context
import android.location.Location
import android.util.Log
import android.widget.Toast
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.mavlink.MissionBuilder
import com.example.droneservicesapp.domain.model.AltitudeReferenceMode
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.model.PlanningOperationMode
import com.example.droneservicesapp.domain.model.PlanningWorkflow
import com.example.droneservicesapp.domain.planning.MissionResourcePlanner
import com.example.droneservicesapp.domain.planning.MissionServiceLeg
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.mavserver.TerrainMissionReadiness
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel.ServiceMissionState
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import io.dronefleet.mavlink.common.MissionItemInt

class MissionParamsActionHandler(
    private val context: Context,
    private val views: MissionParamsViews,
    private val activityViewModel: MainActivityViewModel,
    private val droneViewModel: DroneViewModel,
    private val droneLocationProvider: (() -> Location?)? = null,
    private val preferencesBridge: MissionParamsPreferencesBridge,
    private val beforeUploadGuard: (((onAllowed: () -> Unit) -> Unit))? = null,
) {
    private data class MissionBuild(
        val items: ArrayList<MissionItemInt>,
        val altitudeReferenceMode: AltitudeReferenceMode,
        val usesTerrainAltitudes: Boolean,
    )

    fun bind() {
        views.uploadMissionButton?.setOnClickListener { uploadMission() }
        views.saveMissionButton?.setOnClickListener {
            activityViewModel.mapState.postValue(MainActivityViewModel.MapState.SaveMissionToFile)
        }
    }

    private fun uploadMission() {
        val connected = droneViewModel.conStateLiveData.value == true
        val droneLoc = currentDroneLocation()
        val path = activityViewModel.surveyPath.value
        val routeWaypoints = activityViewModel.routeWaypoints.value.orEmpty()
        val workflow = activityViewModel.activePlanningWorkflow.value ?: PlanningWorkflow.AREA
        val alt = activityViewModel.flightAltProgress.value
        val sprayer = activityViewModel.sprayerProgress.value
        val speed = activityViewModel.flightSpeed.value
        val angle = activityViewModel.angleProgress.value
        val operationMode = activityViewModel.planningOperationMode.value ?: PlanningOperationMode.SURVEY
        val altitudeReferenceMode = activityViewModel.altitudeReferenceMode.value ?: AltitudeReferenceMode.TERRAIN

        when {
            !connected -> return showMessage(context.getString(R.string.no_conn_msg))
            droneLoc == null -> return showMessage(context.getString(R.string.drone_gps_not_available_yet))
            workflow == PlanningWorkflow.AREA && path.isNullOrEmpty() -> {
                return showMessage(context.getString(R.string.no_survey_path_available))
            }
            workflow == PlanningWorkflow.POINTS && routeWaypoints.size < 2 -> {
                return showMessage(context.getString(R.string.route_requires_two_points))
            }
            alt == null || sprayer == null || speed == null || angle == null -> {
                return showMessage(context.getString(R.string.missing_mission_parameters))
            }
        }

        val validatedDroneLoc = droneLoc ?: return
        val validatedAlt = alt ?: return
        val validatedSprayer = sprayer ?: return
        val validatedSpeed = speed ?: return
        val validatedAngle = angle ?: return
        val fullPath = path.orEmpty().map { LatLon(it.latitude, it.longitude) }
        val serviceLegs = if (workflow == PlanningWorkflow.AREA) {
            val effectiveHome = activityViewModel.plannedHomePosition.value
                ?: LatLon(validatedDroneLoc.latitude, validatedDroneLoc.longitude)
            val plan = MissionResourcePlanner.plan(
                path = fullPath,
                home = effectiveHome,
                speedMetersPerSecond = validatedSpeed.coerceAtLeast(0.1),
                sprayRateLitersPerMinute = if (operationMode == PlanningOperationMode.SPRAY) {
                    activityViewModel.sprayFlowLitersPerMinute()
                } else {
                    0.0
                },
            )
            MissionResourcePlanner.splitIntoServiceLegs(fullPath, plan.serviceStops)
        } else {
            emptyList()
        }

        val firstLeg = serviceLegs.firstOrNull()
        val build = if (workflow == PlanningWorkflow.POINTS) {
            MissionBuild(
                items = MissionBuilder.buildPointRouteMission(
                    routeWaypoints = routeWaypoints,
                    currentPos = validatedDroneLoc,
                    targetSystemId = droneViewModel.getTargetSystemId(),
                    targetComponentId = droneViewModel.getTargetComponentId(),
                    altitudeReferenceMode = altitudeReferenceMode,
                ),
                altitudeReferenceMode = altitudeReferenceMode,
                usesTerrainAltitudes = false,
            )
        } else {
            buildAreaMission(
                missionPath = firstLeg?.path ?: fullPath,
                fullPath = fullPath,
                serviceLeg = firstLeg,
                currentLocation = validatedDroneLoc,
                altitude = validatedAlt,
                sprayer = validatedSprayer,
                speed = validatedSpeed,
                angle = validatedAngle,
                operationMode = operationMode,
                altitudeReferenceMode = altitudeReferenceMode,
            )
        }

        if (!terrainReady(build.altitudeReferenceMode)) return

        val proceedWithUpload = {
            if (serviceLegs.size > 1) {
                activityViewModel.beginServiceMission(serviceLegs)
                if (activityViewModel.plannedHomePosition.value == null) {
                    activityViewModel.setPlannedHomePosition(
                        LatLon(validatedDroneLoc.latitude, validatedDroneLoc.longitude)
                    )
                }
            } else {
                activityViewModel.clearServiceMission()
            }
            logUpload(workflow, build, validatedAlt)
            droneViewModel.uploadMissionNew(build.items, activityViewModel)
            preferencesBridge.saveFromViewModel()
        }
        beforeUploadGuard?.invoke(proceedWithUpload) ?: proceedWithUpload()
    }

    fun resumeServiceMission() {
        if (activityViewModel.serviceMissionState.value != ServiceMissionState.WAITING_FOR_SERVICE) return
        if (droneViewModel.conStateLiveData.value != true) {
            showMessage(context.getString(R.string.no_conn_msg))
            return
        }
        if (droneViewModel.armedState.value == true) {
            showMessage(context.getString(R.string.resume_requires_landed))
            return
        }
        val droneLoc = currentDroneLocation()
        if (droneLoc == null) {
            showMessage(context.getString(R.string.drone_gps_not_available_yet))
            return
        }

        val leg = activityViewModel.takeNextServiceMissionLeg() ?: return
        val fullPath = activityViewModel.surveyPath.value.orEmpty()
            .map { LatLon(it.latitude, it.longitude) }
        val altitude = activityViewModel.flightAltProgress.value ?: 0.0
        val build = buildAreaMission(
            missionPath = leg.path,
            fullPath = fullPath,
            serviceLeg = leg,
            currentLocation = droneLoc,
            altitude = altitude,
            sprayer = activityViewModel.sprayerProgress.value ?: 0.0,
            speed = activityViewModel.flightSpeed.value ?: 5.0,
            angle = activityViewModel.angleProgress.value ?: 90.0,
            operationMode = activityViewModel.planningOperationMode.value ?: PlanningOperationMode.SURVEY,
            altitudeReferenceMode = activityViewModel.altitudeReferenceMode.value ?: AltitudeReferenceMode.TERRAIN,
        )
        if (!terrainReady(build.altitudeReferenceMode)) {
            activityViewModel.markServiceLegUploadFailed()
            return
        }

        logUpload(PlanningWorkflow.AREA, build, altitude)
        droneViewModel.uploadMissionNew(build.items, activityViewModel)
        preferencesBridge.saveFromViewModel()
    }

    private fun buildAreaMission(
        missionPath: List<LatLon>,
        fullPath: List<LatLon>,
        serviceLeg: MissionServiceLeg?,
        currentLocation: Location,
        altitude: Double,
        sprayer: Double,
        speed: Double,
        angle: Double,
        operationMode: PlanningOperationMode,
        altitudeReferenceMode: AltitudeReferenceMode,
    ): MissionBuild {
        val mapPath = ArrayList(missionPath.map { LatLng(it.lat, it.lon) })
        if (operationMode == PlanningOperationMode.SPRAY) {
            val fullAltitudes = activityViewModel.terrainSurveyWaypoints.value.orEmpty()
                .takeIf {
                    activityViewModel.pointCloudCoversMissionArea.value == true &&
                        it.size == fullPath.size
                }
                ?.map { it.missionAltitudeMeters.toFloat() }
            val legAltitudes = when {
                fullAltitudes == null -> null
                serviceLeg == null -> fullAltitudes
                else -> terrainAltitudesForLeg(fullPath, fullAltitudes, serviceLeg)
            }
            val reference = if (legAltitudes != null) {
                AltitudeReferenceMode.RELATIVE
            } else {
                AltitudeReferenceMode.TERRAIN
            }
            return MissionBuild(
                items = MissionBuilder.buildSprayAreaMission(
                    waypoints = mapPath,
                    currentPos = currentLocation,
                    alt = altitude.toFloat(),
                    sprayerIntensity = sprayer.toInt(),
                    flightSpeed = speed.toFloat(),
                    angleProgress = angle.toFloat(),
                    targetSystemId = droneViewModel.getTargetSystemId(),
                    targetComponentId = droneViewModel.getTargetComponentId(),
                    altitudeReferenceMode = reference,
                    waypointAltitudes = legAltitudes,
                    startClosestToHome = true,
                    preserveWaypointOrder = serviceLeg != null,
                ),
                altitudeReferenceMode = reference,
                usesTerrainAltitudes = legAltitudes != null,
            )
        }

        return MissionBuild(
            items = MissionBuilder.buildSurveyAreaMission(
                waypoints = mapPath,
                currentPos = currentLocation,
                alt = (activityViewModel.surveyHeightAboveTerrain.value ?: altitude).toFloat(),
                flightSpeed = speed.toFloat(),
                angleProgress = (activityViewModel.surveyGridAngle.value ?: angle).toFloat(),
                targetSystemId = droneViewModel.getTargetSystemId(),
                targetComponentId = droneViewModel.getTargetComponentId(),
                altitudeReferenceMode = altitudeReferenceMode,
                waypointAltitudes = null,
                preserveWaypointOrder = serviceLeg != null,
            ),
            altitudeReferenceMode = altitudeReferenceMode,
            usesTerrainAltitudes = false,
        )
    }

    private fun terrainAltitudesForLeg(
        fullPath: List<LatLon>,
        fullAltitudes: List<Float>,
        leg: MissionServiceLeg,
    ): List<Float> {
        if (fullPath.size != fullAltitudes.size || fullPath.size < 2) return emptyList()
        val cumulative = MutableList(fullPath.size) { 0.0 }
        for (index in 1 until fullPath.size) {
            cumulative[index] = cumulative[index - 1] + SphericalUtil.computeDistanceBetween(
                LatLng(fullPath[index - 1].lat, fullPath[index - 1].lon),
                LatLng(fullPath[index].lat, fullPath[index].lon),
            )
        }
        val distances = buildList {
            add(leg.startPathDistanceMeters)
            cumulative.forEach { distance ->
                if (distance > leg.startPathDistanceMeters + 0.5 &&
                    distance < leg.endPathDistanceMeters - 0.5
                ) {
                    add(distance)
                }
            }
            add(leg.endPathDistanceMeters)
        }
        return distances.map { distance -> interpolateAltitude(cumulative, fullAltitudes, distance) }
    }

    private fun interpolateAltitude(
        cumulative: List<Double>,
        altitudes: List<Float>,
        distance: Double,
    ): Float {
        val target = distance.coerceIn(0.0, cumulative.last())
        val upper = cumulative.indexOfFirst { it >= target }.takeIf { it >= 0 } ?: cumulative.lastIndex
        if (upper == 0) return altitudes.first()
        val lower = upper - 1
        val segment = cumulative[upper] - cumulative[lower]
        if (segment <= 0.0) return altitudes[upper]
        val fraction = ((target - cumulative[lower]) / segment).toFloat()
        return altitudes[lower] + (altitudes[upper] - altitudes[lower]) * fraction
    }

    private fun terrainReady(referenceMode: AltitudeReferenceMode): Boolean {
        if (referenceMode != AltitudeReferenceMode.TERRAIN) return true
        when (droneViewModel.terrainMissionReadiness()) {
            TerrainMissionReadiness.READY -> return true
            TerrainMissionReadiness.CHECKING -> showMessage(context.getString(R.string.terrain_database_enabling))
            TerrainMissionReadiness.UNSUPPORTED -> showMessage(context.getString(R.string.terrain_database_unsupported))
            TerrainMissionReadiness.REJECTED -> showMessage(context.getString(R.string.terrain_database_rejected))
        }
        return false
    }

    private fun logUpload(workflow: PlanningWorkflow, build: MissionBuild, altitude: Double) {
        Log.i(
            "MissionUpload",
            "Proceeding with upload workflow=$workflow altitudeReference=${build.altitudeReferenceMode} " +
                "terrainAware=${build.usesTerrainAltitudes} " +
                "altitude=${altitude.toInt()}m missionItems=${build.items.size}",
        )
    }

    private fun currentDroneLocation(): Location? =
        droneLocationProvider?.invoke() ?: droneViewModel.droneLocationLiveData.value

    private fun showMessage(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
