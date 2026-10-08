package com.example.droneservicesapp.ui.home.components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import com.example.droneservicesapp.R
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.model.MissionObstacle
import com.example.droneservicesapp.domain.planning.MissionResourcePlan
import com.example.droneservicesapp.domain.planning.MissionServiceStop
import com.example.droneservicesapp.domain.survey.SurveyPlanner
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Owns mission-preview animation and its service-stop state machine. */
class MissionSimulationController(
    private val flightSpeedMetersPerSecond: () -> Double,
    private val missionObstacles: () -> List<MissionObstacle>,
    private val buildResourcePlan: (List<LatLng>, LatLon) -> MissionResourcePlan,
    private val setSimulationDrone: (LatLon, Float) -> Unit,
    private val clearSimulationDrone: () -> Unit,
    private val onStateChanged: (State) -> Unit,
    private val onWaitingForService: (MissionServiceStop) -> Unit,
) {
    enum class State {
        IDLE,
        FLYING,
        WAITING_FOR_SERVICE,
        COMPLETE,
    }

    var state: State = State.IDLE
        private set

    var usesLiveDroneHome: Boolean = false
        private set

    val pendingServiceStop: MissionServiceStop?
        get() = pendingStop

    private var animator: ValueAnimator? = null
    private var missionPath: List<LatLng> = emptyList()
    private var cumulativeDistances: List<Double> = emptyList()
    private var serviceStops: List<MissionServiceStop> = emptyList()
    private var nextServiceStopIndex = 0
    private var currentWorkDistance = 0.0
    private var home: LatLng? = null
    private var pendingStop: MissionServiceStop? = null

    fun start(path: List<LatLng>, homePosition: LatLon, useLiveDroneHome: Boolean) {
        require(path.size >= 2) { "A mission simulation requires at least two path points" }
        stop(notify = false)
        missionPath = path
        cumulativeDistances = calculateCumulativeDistances(path)
        serviceStops = buildResourcePlan(path, homePosition).serviceStops
        nextServiceStopIndex = 0
        currentWorkDistance = 0.0
        pendingStop = null
        home = LatLng(homePosition.lat, homePosition.lon)
        usesLiveDroneHome = useLiveDroneHome
        setState(State.FLYING)
        animatePath(listOf(home ?: path.first(), path.first()), ::animateWorkLeg)
    }

    fun resume() {
        val stop = pendingStop ?: return
        val workPoint = LatLng(stop.point.lat, stop.point.lon)
        setState(State.FLYING)
        animatePath(obstacleAwareTransit(home ?: workPoint, workPoint)) {
            nextServiceStopIndex += 1
            pendingStop = null
            animateWorkLeg()
        }
    }

    fun stop() = stop(notify = true)

    fun stopIfActive() {
        if (state != State.IDLE || animator != null) stop()
    }

    private fun animateWorkLeg() {
        val totalWorkDistance = cumulativeDistances.lastOrNull() ?: return
        val nextStop = serviceStops.getOrNull(nextServiceStopIndex)
        val targetDistance = nextStop?.pathDistanceMeters ?: totalWorkDistance
        animatePath(subpath(currentWorkDistance, targetDistance)) {
            currentWorkDistance = targetDistance
            if (nextStop != null) {
                pendingStop = nextStop
                val workPoint = LatLng(nextStop.point.lat, nextStop.point.lon)
                animatePath(obstacleAwareTransit(workPoint, home ?: workPoint)) {
                    animator = null
                    setState(State.WAITING_FOR_SERVICE)
                    onWaitingForService(nextStop)
                }
            } else {
                val end = missionPath.last()
                animatePath(listOf(end, home ?: end)) {
                    animator = null
                    setState(State.COMPLETE)
                }
            }
        }
    }

    private fun obstacleAwareTransit(from: LatLng, to: LatLng): List<LatLng> =
        SurveyPlanner().buildObstacleAvoidingTransitPath(
            from = LatLon(from.latitude, from.longitude),
            to = LatLon(to.latitude, to.longitude),
            obstacles = missionObstacles(),
        ).map { LatLng(it.lat, it.lon) }

    private fun animatePath(rawPath: List<LatLng>, onComplete: () -> Unit) {
        val path = rawPath.filterIndexed { index, point -> index == 0 || point != rawPath[index - 1] }
        if (path.size < 2) {
            path.firstOrNull()?.let { setSimulationDrone(LatLon(it.latitude, it.longitude), 0f) }
            onComplete()
            return
        }
        val cumulative = calculateCumulativeDistances(path)
        val totalDistance = cumulative.last()
        if (totalDistance <= 0.0) {
            onComplete()
            return
        }
        val durationMillis = (totalDistance / flightSpeedMetersPerSecond().coerceAtLeast(0.1) /
            SIMULATION_SPEED_MULTIPLIER * 1000.0).toLong()
            .coerceIn(MIN_SIMULATION_DURATION_MS, MAX_SIMULATION_DURATION_MS)
        val nextAnimator = ValueAnimator.ofFloat(0f, totalDistance.toFloat()).apply {
            duration = durationMillis
            addUpdateListener { animation ->
                val traveled = (animation.animatedValue as Float).toDouble()
                val upper = cumulative.indexOfFirst { it >= traveled }.takeIf { it >= 0 } ?: path.lastIndex
                val lower = (upper - 1).coerceAtLeast(0)
                val segmentLength = cumulative[upper] - cumulative[lower]
                val fraction = if (segmentLength > 0.0) {
                    ((traveled - cumulative[lower]) / segmentLength).coerceIn(0.0, 1.0)
                } else 0.0
                val point = SphericalUtil.interpolate(path[lower], path[upper], fraction)
                setSimulationDrone(
                    LatLon(point.latitude, point.longitude),
                    bearingDegrees(path[lower], path[upper]).toFloat(),
                )
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (animator === animation) {
                        animator = null
                        onComplete()
                    }
                }
            })
        }
        animator = nextAnimator
        nextAnimator.start()
    }

    private fun subpath(startDistance: Double, endDistance: Double): List<LatLng> {
        if (missionPath.size < 2 || cumulativeDistances.isEmpty()) return emptyList()
        return buildList {
            add(pointOnPath(startDistance))
            missionPath.forEachIndexed { index, point ->
                val distance = cumulativeDistances[index]
                if (distance > startDistance + 0.5 && distance < endDistance - 0.5) add(point)
            }
            add(pointOnPath(endDistance))
        }
    }

    private fun pointOnPath(distance: Double): LatLng {
        val target = distance.coerceIn(0.0, cumulativeDistances.last())
        val upper = cumulativeDistances.indexOfFirst { it >= target }.takeIf { it >= 0 } ?: missionPath.lastIndex
        if (upper == 0) return missionPath.first()
        val lower = upper - 1
        val segment = cumulativeDistances[upper] - cumulativeDistances[lower]
        val fraction = if (segment > 0.0) {
            ((target - cumulativeDistances[lower]) / segment).coerceIn(0.0, 1.0)
        } else 0.0
        return SphericalUtil.interpolate(missionPath[lower], missionPath[upper], fraction)
    }

    private fun stop(notify: Boolean) {
        animator?.let { running ->
            animator = null
            running.removeAllListeners()
            running.cancel()
        }
        missionPath = emptyList()
        cumulativeDistances = emptyList()
        serviceStops = emptyList()
        nextServiceStopIndex = 0
        currentWorkDistance = 0.0
        home = null
        pendingStop = null
        usesLiveDroneHome = false
        clearSimulationDrone()
        state = State.IDLE
        if (notify) onStateChanged(state)
    }

    private fun setState(newState: State) {
        state = newState
        onStateChanged(newState)
    }

    companion object {
        internal const val SIMULATION_SPEED_MULTIPLIER = 20.0
        internal const val MIN_SIMULATION_DURATION_MS = 4_000L
        internal const val MAX_SIMULATION_DURATION_MS = 60_000L

        fun serviceDescriptionResource(stop: MissionServiceStop): Int = when {
            stop.requiresBattery && stop.requiresTankRefill -> R.string.service_change_battery_and_refill_tank
            stop.requiresBattery -> R.string.service_change_battery
            else -> R.string.service_refill_tank
        }

        internal fun calculateCumulativeDistances(path: List<LatLng>): List<Double> {
            val cumulative = MutableList(path.size) { 0.0 }
            for (index in 1 until path.size) {
                cumulative[index] = cumulative[index - 1] +
                    SphericalUtil.computeDistanceBetween(path[index - 1], path[index])
            }
            return cumulative
        }

        private fun bearingDegrees(from: LatLng, to: LatLng): Double {
            val fromLat = Math.toRadians(from.latitude)
            val toLat = Math.toRadians(to.latitude)
            val longitudeDelta = Math.toRadians(to.longitude - from.longitude)
            val y = sin(longitudeDelta) * cos(toLat)
            val x = cos(fromLat) * sin(toLat) - sin(fromLat) * cos(toLat) * cos(longitudeDelta)
            return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
        }
    }
}
