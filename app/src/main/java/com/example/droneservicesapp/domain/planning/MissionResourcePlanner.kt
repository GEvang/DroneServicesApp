package com.example.droneservicesapp.domain.planning

import com.example.droneservicesapp.domain.model.LatLon
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

data class MissionResourcePlan(
    val batteryCount: Int = 0,
    val batteryReturnPoints: List<LatLon> = emptyList(),
    val tankRefillCount: Int = 0,
    val tankRefillPoints: List<LatLon> = emptyList(),
    val totalSprayLiters: Double = 0.0,
    val estimatedFlightSeconds: Double = 0.0,
    val serviceStops: List<MissionServiceStop> = emptyList(),
)

data class MissionServiceStop(
    val pathDistanceMeters: Double,
    val point: LatLon,
    val requiresBattery: Boolean,
    val requiresTankRefill: Boolean,
)

data class MissionServiceLeg(
    val path: List<LatLon>,
    val serviceAfter: MissionServiceStop?,
    val startPathDistanceMeters: Double,
    val endPathDistanceMeters: Double,
)

/**
 * Estimates the consumables needed to fly a planned path. Battery legs include
 * the trip from home to the work point and the return to home for every swap.
 */
object MissionResourcePlanner {
    const val USABLE_BATTERY_MINUTES = 20.0
    const val SPRAY_TANK_CAPACITY_LITERS = 20.0
    private const val MIN_PROGRESS_METERS = 0.5

    fun plan(
        path: List<LatLon>,
        home: LatLon?,
        speedMetersPerSecond: Double,
        sprayRateLitersPerMinute: Double = 0.0,
        usableBatteryMinutes: Double = USABLE_BATTERY_MINUTES,
        tankCapacityLiters: Double = SPRAY_TANK_CAPACITY_LITERS,
    ): MissionResourcePlan {
        if (path.size < 2 || speedMetersPerSecond <= 0.0 || !speedMetersPerSecond.isFinite()) {
            return MissionResourcePlan()
        }

        val cumulativeDistances = cumulativeDistances(path)
        val workDistance = cumulativeDistances.last()
        val effectiveHome = home ?: path.first()
        val outboundDistance = distanceMeters(effectiveHome, path.first())
        val returnDistance = distanceMeters(path.last(), effectiveHome)
        val estimatedSeconds = (outboundDistance + workDistance + returnDistance) / speedMetersPerSecond

        val batteryBudgetMeters = speedMetersPerSecond * usableBatteryMinutes * 60.0
        val batteryReturnPoints = mutableListOf<LatLon>()
        val batteryReturnDistances = mutableListOf<Double>()
        var batteryCount = 0
        var workStartDistance = 0.0
        while (workStartDistance < workDistance - MIN_PROGRESS_METERS) {
            val startPoint = pointAtDistance(path, cumulativeDistances, workStartDistance)
            val distanceFromHomeToStart = distanceMeters(effectiveHome, startPoint)
            val finalLegDistance = distanceFromHomeToStart +
                (workDistance - workStartDistance) + returnDistance
            batteryCount += 1
            if (finalLegDistance <= batteryBudgetMeters) break

            var low = workStartDistance
            var high = workDistance
            repeat(48) {
                val candidate = (low + high) / 2.0
                val point = pointAtDistance(path, cumulativeDistances, candidate)
                val legDistance = distanceFromHomeToStart +
                    (candidate - workStartDistance) + distanceMeters(point, effectiveHome)
                if (legDistance <= batteryBudgetMeters) low = candidate else high = candidate
            }
            if (low <= workStartDistance + MIN_PROGRESS_METERS) {
                // The work area is too far from home for the configured usable endurance.
                // Keep the estimate finite and avoid showing a misleading return point.
                batteryCount = maxOf(
                    batteryCount,
                    ceil((outboundDistance + workDistance + returnDistance) / batteryBudgetMeters)
                        .toInt()
                        .coerceAtLeast(1)
                )
                break
            }
            batteryReturnPoints += pointAtDistance(path, cumulativeDistances, low)
            batteryReturnDistances += low
            workStartDistance = low
        }
        if (batteryCount == 0) batteryCount = 1

        val validSprayRate = sprayRateLitersPerMinute
            .takeIf { it.isFinite() && it > 0.0 }
            ?: 0.0
        val totalSprayLiters = workDistance / speedMetersPerSecond / 60.0 * validSprayRate
        val refillPoints = mutableListOf<LatLon>()
        val refillDistances = mutableListOf<Double>()
        if (validSprayRate > 0.0 && tankCapacityLiters > 0.0) {
            val metersPerTank = tankCapacityLiters * speedMetersPerSecond * 60.0 / validSprayRate
            var refillDistance = metersPerTank
            while (refillDistance < workDistance - MIN_PROGRESS_METERS) {
                refillPoints += pointAtDistance(path, cumulativeDistances, refillDistance)
                refillDistances += refillDistance
                refillDistance += metersPerTank
            }
        }

        val serviceStops = mergeServiceStops(
            path = path,
            cumulativeDistances = cumulativeDistances,
            batteryDistances = batteryReturnDistances,
            refillDistances = refillDistances,
        )

        return MissionResourcePlan(
            batteryCount = batteryCount,
            batteryReturnPoints = batteryReturnPoints,
            tankRefillCount = refillPoints.size,
            tankRefillPoints = refillPoints,
            totalSprayLiters = totalSprayLiters,
            estimatedFlightSeconds = estimatedSeconds,
            serviceStops = serviceStops,
        )
    }

    fun splitIntoServiceLegs(
        path: List<LatLon>,
        serviceStops: List<MissionServiceStop>,
    ): List<MissionServiceLeg> {
        if (path.size < 2) return emptyList()
        val cumulative = cumulativeDistances(path)
        val totalDistance = cumulative.last()
        val stops = serviceStops
            .filter { it.pathDistanceMeters > MIN_PROGRESS_METERS && it.pathDistanceMeters < totalDistance - MIN_PROGRESS_METERS }
            .sortedBy { it.pathDistanceMeters }
        val boundaries = listOf(0.0) + stops.map { it.pathDistanceMeters } + totalDistance
        return boundaries.zipWithNext().mapIndexedNotNull { index, (start, end) ->
            if (end - start <= MIN_PROGRESS_METERS) return@mapIndexedNotNull null
            val points = buildList {
                add(pointAtDistance(path, cumulative, start))
                path.forEachIndexed { pointIndex, point ->
                    val distance = cumulative[pointIndex]
                    if (distance > start + MIN_PROGRESS_METERS && distance < end - MIN_PROGRESS_METERS) add(point)
                }
                add(pointAtDistance(path, cumulative, end))
            }
            MissionServiceLeg(
                path = points,
                serviceAfter = stops.getOrNull(index),
                startPathDistanceMeters = start,
                endPathDistanceMeters = end,
            )
        }
    }

    private fun mergeServiceStops(
        path: List<LatLon>,
        cumulativeDistances: List<Double>,
        batteryDistances: List<Double>,
        refillDistances: List<Double>,
    ): List<MissionServiceStop> {
        data class Event(val distance: Double, val battery: Boolean, val tank: Boolean)

        val events = (
            batteryDistances.map { Event(it, battery = true, tank = false) } +
                refillDistances.map { Event(it, battery = false, tank = true) }
            ).sortedBy { it.distance }
        val merged = mutableListOf<Event>()
        events.forEach { event ->
            val previous = merged.lastOrNull()
            if (previous != null && abs(previous.distance - event.distance) <= MIN_PROGRESS_METERS) {
                merged[merged.lastIndex] = Event(
                    distance = minOf(previous.distance, event.distance),
                    battery = previous.battery || event.battery,
                    tank = previous.tank || event.tank,
                )
            } else {
                merged += event
            }
        }
        return merged.map { event ->
            MissionServiceStop(
                pathDistanceMeters = event.distance,
                point = pointAtDistance(path, cumulativeDistances, event.distance),
                requiresBattery = event.battery,
                requiresTankRefill = event.tank,
            )
        }
    }

    private fun cumulativeDistances(path: List<LatLon>): List<Double> {
        val distances = MutableList(path.size) { 0.0 }
        for (index in 1 until path.size) {
            distances[index] = distances[index - 1] + distanceMeters(path[index - 1], path[index])
        }
        return distances
    }

    private fun pointAtDistance(
        path: List<LatLon>,
        cumulativeDistances: List<Double>,
        distance: Double,
    ): LatLon {
        val target = distance.coerceIn(0.0, cumulativeDistances.last())
        val upperIndex = cumulativeDistances.indexOfFirst { it >= target }
            .takeIf { it >= 0 }
            ?: path.lastIndex
        if (upperIndex == 0) return path.first()
        val lowerIndex = upperIndex - 1
        val segmentLength = cumulativeDistances[upperIndex] - cumulativeDistances[lowerIndex]
        if (segmentLength <= 0.0) return path[upperIndex]
        val fraction = (target - cumulativeDistances[lowerIndex]) / segmentLength
        val from = path[lowerIndex]
        val to = path[upperIndex]
        return LatLon(
            lat = from.lat + (to.lat - from.lat) * fraction,
            lon = from.lon + (to.lon - from.lon) * fraction,
        )
    }

    private fun distanceMeters(from: LatLon, to: LatLon): Double {
        val earthRadiusMeters = 6_371_000.0
        val lat1 = Math.toRadians(from.lat)
        val lat2 = Math.toRadians(to.lat)
        val deltaLat = lat2 - lat1
        val deltaLon = Math.toRadians(to.lon - from.lon)
        val a = sin(deltaLat / 2.0) * sin(deltaLat / 2.0) +
            cos(lat1) * cos(lat2) * sin(deltaLon / 2.0) * sin(deltaLon / 2.0)
        return earthRadiusMeters * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    }
}
