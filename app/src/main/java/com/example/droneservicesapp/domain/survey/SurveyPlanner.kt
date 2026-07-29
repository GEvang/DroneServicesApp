package com.example.droneservicesapp.domain.survey

import android.util.Log
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.model.MissionArea
import com.example.droneservicesapp.domain.model.MissionObstacle
import com.example.droneservicesapp.domain.model.MissionObstacleShape
import com.example.droneservicesapp.domain.survey.math.Line2D
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure computation planner for survey path generation.
 * No UI, map SDK objects, or Fragment dependencies.
 */
class SurveyPlanner {

    companion object {
        private const val TAG = "SurveyPlanner"
        private const val DEBUG_LOGS = false
        private const val MAX_AREA = 40000.0
    }

    /**
     * Builds a survey path for a given polygon area.
     *
     * @param polygon List of LatLon vertices defining the survey area
     * @param distanceMeters Spacing between survey lines in meters
     * @param angleDeg Angle of survey lines in degrees
     * @param maxAreaM2 Maximum allowed area in square meters (default 40000.0)
     * @return List of LatLon waypoints forming the survey path, empty if area too large or no valid path
     */
    fun buildSurveyPath(
        polygon: List<LatLon>,
        distanceMeters: Double,
        angleDeg: Int,
        obstacles: List<MissionObstacle> = emptyList(),
        maxAreaM2: Double = 40000.0
    ): List<LatLon> {
        // Early return for invalid polygon
        if (polygon.size < 3) {
            return emptyList()
        }

        // Convert LatLon to LatLng
        val latLngList = polygon.map { LatLng(it.lat, it.lon) }

        // Create a minimal mission area wrapper
        val missionArea = MissionArea(vertices = latLngList.toMutableList())

        // Check area constraint
        val area = SphericalUtil.computeArea(missionArea.vertices)
        if (area > maxAreaM2) {
            if (DEBUG_LOGS) Log.w(TAG, "Area too large: $area > $maxAreaM2")
            return emptyList()
        }

        // Generate survey path using core angled survey logic
        val resultLatLng = avoidObstacles(
            path = angledSurvey(missionArea, distanceMeters, angleDeg),
            obstacles = obstacles,
            bufferMeters = max(1.0, distanceMeters * 0.5)
        )

        // Convert back to LatLon
        return resultLatLng.map { LatLon(it.latitude, it.longitude) }
    }

    /**
     * Core angled survey algorithm (extracted from Survey class).
     * Generates survey waypoints by creating parallel lines at the specified angle.
     */
    private fun angledSurvey(
        area: MissionArea,
        distance: Double,
        angle: Int
    ): ArrayList<LatLng> {
        // Early return if polygon has fewer than 3 vertices
        if (area.vertices.size < 3) {
            return ArrayList()
        }

        val eastOrientationLines: ArrayList<Line2D> = ArrayList()
        val westOrientationLines: ArrayList<Line2D> = ArrayList()

        val angleD = angle.toDouble()
        val supplementaryAngleD = 90 - angleD

        // Find bounding points
        var left = LatLng(-1000.0, -1000.0)
        var right = LatLng(1000.0, 1000.0)
        var top = LatLng(-1000.0, -1000.0)
        var bot = LatLng(1000.0, 1000.0)

        for (edge in area.vertices) {
            if (edge.longitude < left.longitude) left = edge
            if (edge.longitude > right.longitude) right = edge
            if (edge.latitude < bot.latitude) bot = edge
            if (edge.latitude > top.latitude) top = edge
        }

        if (DEBUG_LOGS) {
            Log.d(TAG, "left  $left")
            Log.d(TAG, "right  $right")
            Log.d(TAG, "bot  $bot")
            Log.d(TAG, "top  $top")
        }

        val lineIntersections: ArrayList<LatLng> = ArrayList()

        // Create a closed polygon (append first point to end for edge iteration)
        val polygonClosed = ArrayList(area.vertices)
        polygonClosed.add(polygonClosed[0])

        val centroid = centroid(area.vertices)
        if (DEBUG_LOGS) Log.d(TAG, "centroid  $centroid")

        var point: LatLng = SphericalUtil.computeOffset(
            centroid,
            distance,
            supplementaryAngleD + 90.0
        )

        var intersectionsLocated = true

        // Move right until no intersections are found
        while (intersectionsLocated) {
            lineIntersections.clear()

            val pointLine = Line2D(point, angleD)

            for (i in 0 until polygonClosed.size - 1) {
                val p1: LatLng = polygonClosed[i]
                val p2: LatLng = polygonClosed[i + 1]
                val line = Line2D(p1, p2)

                val intersection = line.lineLinearSectionIntersection(pointLine, p1, p2)
                if (DEBUG_LOGS) Log.d(TAG, "intersection=$intersection")

                intersection?.let { lineIntersections.add(it) }
            }

            intersectionsLocated = lineIntersections.size >= 2
            point = SphericalUtil.computeOffset(point, distance, supplementaryAngleD + 90.0)
        }

        intersectionsLocated = true
        point = SphericalUtil.computeOffset(point, 2 * distance, supplementaryAngleD + 270)

        // Move left from the other side
        while (intersectionsLocated) {
            lineIntersections.clear()

            val pointLine = Line2D(point, angleD)

            for (i in 0 until polygonClosed.size - 1) {
                val p1: LatLng = polygonClosed[i]
                val p2: LatLng = polygonClosed[i + 1]
                val line = Line2D(p1, p2)

                val intersection = line.lineLinearSectionIntersection(pointLine, p1, p2)
                if (DEBUG_LOGS) Log.d(TAG, "intersection=$intersection")

                intersection?.let { lineIntersections.add(it) }
            }

            intersectionsLocated = lineIntersections.size >= 2

            if (intersectionsLocated) {
                westOrientationLines.add(getOuterIntersections(lineIntersections))
            }

            point = SphericalUtil.computeOffset(point, distance, supplementaryAngleD + 270.0)
        }

        return mergeEastWestWPlistsByMinDistanceSorting(eastOrientationLines, westOrientationLines)
    }

    /**
     * Merges east and west orientation survey lines into a single optimized path.
     */
    private fun mergeEastWestWPlistsByMinDistanceSorting(
        eastLines: ArrayList<Line2D>,
        westLines: ArrayList<Line2D>
    ): ArrayList<LatLng> {
        val result = ArrayList<LatLng>()
        lateinit var curLine: Line2D
        lateinit var curWP: LatLng

        if (westLines.size > 0) {
            curLine = westLines.removeAt(westLines.size - 1)
            result.add(curLine.p1!!)
            result.add(curLine.p2!!)

            curWP = curLine.p2!!

            while (westLines.size > 0) {
                curLine = westLines.removeAt(westLines.size - 1)
                curWP = if (SphericalUtil.computeDistanceBetween(curLine.p1!!, curWP) <
                    SphericalUtil.computeDistanceBetween(curLine.p2!!, curWP)
                ) {
                    result.add(curLine.p1!!)
                    result.add(curLine.p2!!)
                    curLine.p2!!
                } else {
                    result.add(curLine.p2!!)
                    result.add(curLine.p1!!)
                    curLine.p1!!
                }
            }
        }

        if (eastLines.size > 0) {
            if (result.size == 0) {
                curLine = eastLines.removeAt(0)
                result.add(curLine.p1!!)
                result.add(curLine.p2!!)
                curWP = curLine.p2!!
            }

            while (eastLines.size > 0) {
                curLine = eastLines.removeAt(0)
                curWP = if (SphericalUtil.computeDistanceBetween(curLine.p1!!, curWP) <
                    SphericalUtil.computeDistanceBetween(curLine.p2!!, curWP)
                ) {
                    result.add(curLine.p1!!)
                    result.add(curLine.p2!!)
                    curLine.p2!!
                } else {
                    result.add(curLine.p2!!)
                    result.add(curLine.p1!!)
                    curLine.p1!!
                }
            }
        }

        return result
    }

    /**
     * Calculates the centroid (geometric center) of a polygon.
     */
    private fun centroid(polygon: List<LatLng>): LatLng {
        var lats = 0.0
        var longs = 0.0
        for (p in polygon) {
            lats += p.latitude
            longs += p.longitude
        }
        return LatLng(lats / polygon.size, longs / polygon.size)
    }

    /**
     * Extracts the two outer intersection points from a list of intersections.
     * These represent the endpoints of a survey line segment.
     */
    private fun getOuterIntersections(lineIntersections: ArrayList<LatLng>): Line2D {
        var maxP: LatLng = lineIntersections.first()
        var minP: LatLng = lineIntersections.first()
        for (p in lineIntersections) {
            if (maxP.latitude < p.latitude) maxP = p
            if (minP.latitude > p.latitude) minP = p
        }

        // If all points have same latitude, use longitude to find extremes
        if (maxP == minP) {
            for (p in lineIntersections) {
                if (maxP.longitude < p.longitude) maxP = p
                if (minP.longitude > p.longitude) minP = p
            }
        }

        return Line2D(minP, maxP)
    }

    private fun avoidObstacles(
        path: List<LatLng>,
        obstacles: List<MissionObstacle>,
        bufferMeters: Double
    ): ArrayList<LatLng> {
        if (path.size < 2 || obstacles.isEmpty()) {
            return ArrayList(path)
        }

        val routed = ArrayList<LatLng>()
        routed.add(path.first())

        path.zipWithNext().forEach { (from, to) ->
            val segmentPath = routeSegmentAroundObstacles(from, to, obstacles, bufferMeters)
            segmentPath.drop(1).forEach { point -> routed.add(point) }
        }

        return routed
    }

    private fun routeSegmentAroundObstacles(
        from: LatLng,
        to: LatLng,
        obstacles: List<MissionObstacle>,
        bufferMeters: Double
    ): List<LatLng> {
        val hit = obstacles
            .mapNotNull { obstacle -> segmentObstacleHit(from, to, obstacle, bufferMeters) }
            .minByOrNull { it.entryT }
            ?: return listOf(from, to)

        val before = if (hit.entryT > 0.001) listOf(from, hit.entryPoint) else listOf(from)
        val after = if (hit.exitT < 0.999) {
            routeSegmentAroundObstacles(hit.exitPoint, to, obstacles.filterNot { it.id == hit.obstacle.id }, bufferMeters)
        } else {
            listOf(hit.exitPoint, to)
        }

        return (before + hit.detourPoints + after.drop(1)).dedupeAdjacentPoints()
    }

    private fun segmentObstacleHit(
        from: LatLng,
        to: LatLng,
        obstacle: MissionObstacle,
        bufferMeters: Double
    ): SegmentObstacleHit? {
        return when (obstacle.shape) {
            MissionObstacleShape.CIRCLE -> segmentCircleHit(from, to, obstacle, bufferMeters)
                ?.let { circleHit ->
                    SegmentObstacleHit(
                        obstacle = obstacle,
                        entryT = circleHit.entryT,
                        exitT = circleHit.exitT,
                        entryPoint = circleHit.entryPoint,
                        exitPoint = circleHit.exitPoint,
                        detourPoints = circleDetourPoints(circleHit)
                    )
                }
            MissionObstacleShape.POLYGON -> segmentPolygonHit(from, to, obstacle)
        }
    }

    private fun segmentCircleHit(
        from: LatLng,
        to: LatLng,
        obstacle: MissionObstacle,
        bufferMeters: Double
    ): SegmentCircleHit? {
        val obstacleCenter = obstacle.center ?: return null
        val center = LatLng(obstacleCenter.lat, obstacleCenter.lon)
        val radius = obstacle.radiusMeters + bufferMeters
        val local = LocalProjection(from)
        val end = local.toXY(to)
        val circle = local.toXY(center)

        val dx = end.x
        val dy = end.y
        val fx = -circle.x
        val fy = -circle.y
        val a = dx * dx + dy * dy
        if (a <= 0.0) return null

        val b = 2.0 * (fx * dx + fy * dy)
        val c = fx * fx + fy * fy - radius * radius
        val discriminant = b * b - 4.0 * a * c
        if (discriminant < 0.0) return null

        val root = sqrt(discriminant)
        val t1 = ((-b - root) / (2.0 * a)).coerceIn(0.0, 1.0)
        val t2 = ((-b + root) / (2.0 * a)).coerceIn(0.0, 1.0)
        val entryT = min(t1, t2)
        val exitT = max(t1, t2)
        if (exitT <= 0.0 || entryT >= 1.0 || exitT - entryT < 0.001) return null

        val entryPoint = local.fromXY(PointXY(dx * entryT, dy * entryT))
        val exitPoint = local.fromXY(PointXY(dx * exitT, dy * exitT))
        val centerXY = local.toXY(center)
        return SegmentCircleHit(
            obstacle = obstacle,
            centerXY = centerXY,
            radiusMeters = radius,
            entryT = entryT,
            exitT = exitT,
            entryPoint = entryPoint,
            exitPoint = exitPoint,
            entryXY = PointXY(dx * entryT, dy * entryT),
            exitXY = PointXY(dx * exitT, dy * exitT),
            projection = local
        )
    }

    private fun segmentPolygonHit(
        from: LatLng,
        to: LatLng,
        obstacle: MissionObstacle
    ): SegmentObstacleHit? {
        val vertices = obstacle.vertices.map { LatLng(it.lat, it.lon) }
        if (vertices.size < 3) return null

        val projection = LocalProjection(from)
        val start = PointXY(0.0, 0.0)
        val end = projection.toXY(to)
        val projectedVertices = vertices.map { projection.toXY(it) }
        val intersections = ArrayList<PolygonIntersection>()

        projectedVertices.indices.forEach { index ->
            val nextIndex = (index + 1) % projectedVertices.size
            segmentIntersection(
                start,
                end,
                projectedVertices[index],
                projectedVertices[nextIndex]
            )?.let { intersection ->
                if (intersection.t in 0.0..1.0) {
                    intersections += PolygonIntersection(
                        t = intersection.t,
                        edgeIndex = index,
                        point = projection.fromXY(intersection.point)
                    )
                }
            }
        }

        val distinctIntersections = intersections
            .sortedBy { it.t }
            .fold(ArrayList<PolygonIntersection>()) { acc, intersection ->
                if (acc.none { kotlin.math.abs(it.t - intersection.t) < 0.0001 }) {
                    acc += intersection
                }
                acc
            }

        if (distinctIntersections.size < 2) return null

        val entry = distinctIntersections.first()
        val exit = distinctIntersections.last()
        if (exit.t - entry.t < 0.001) return null

        return SegmentObstacleHit(
            obstacle = obstacle,
            entryT = entry.t,
            exitT = exit.t,
            entryPoint = entry.point,
            exitPoint = exit.point,
            detourPoints = polygonDetourPoints(vertices, entry, exit)
        )
    }

    private fun polygonDetourPoints(
        vertices: List<LatLng>,
        entry: PolygonIntersection,
        exit: PolygonIntersection
    ): List<LatLng> {
        val forward = polygonBoundaryPath(vertices, entry, exit, forward = true)
        val backward = polygonBoundaryPath(vertices, entry, exit, forward = false)
        return if (arcLength(forward) <= arcLength(backward)) forward else backward
    }

    private fun polygonBoundaryPath(
        vertices: List<LatLng>,
        entry: PolygonIntersection,
        exit: PolygonIntersection,
        forward: Boolean
    ): List<LatLng> {
        val path = ArrayList<LatLng>()
        path += entry.point
        var index = if (forward) {
            (entry.edgeIndex + 1) % vertices.size
        } else {
            entry.edgeIndex
        }

        repeat(vertices.size + 1) {
            val exitReached = if (forward) {
                index == (exit.edgeIndex + 1) % vertices.size
            } else {
                index == exit.edgeIndex
            }
            if (exitReached) {
                path += exit.point
                return path.dedupeAdjacentPoints()
            }
            path += vertices[index]
            index = if (forward) {
                (index + 1) % vertices.size
            } else {
                (index - 1 + vertices.size) % vertices.size
            }
        }

        path += exit.point
        return path.dedupeAdjacentPoints()
    }

    private fun segmentIntersection(
        a: PointXY,
        b: PointXY,
        c: PointXY,
        d: PointXY
    ): SegmentIntersection? {
        val rx = b.x - a.x
        val ry = b.y - a.y
        val sx = d.x - c.x
        val sy = d.y - c.y
        val denominator = cross(rx, ry, sx, sy)
        if (kotlin.math.abs(denominator) < 1e-9) return null

        val qpx = c.x - a.x
        val qpy = c.y - a.y
        val t = cross(qpx, qpy, sx, sy) / denominator
        val u = cross(qpx, qpy, rx, ry) / denominator
        if (t !in 0.0..1.0 || u !in 0.0..1.0) return null

        return SegmentIntersection(
            t = t,
            point = PointXY(
                x = a.x + t * rx,
                y = a.y + t * ry
            )
        )
    }

    private fun cross(ax: Double, ay: Double, bx: Double, by: Double): Double {
        return ax * by - ay * bx
    }

    private fun circleDetourPoints(hit: SegmentCircleHit): List<LatLng> {
        val startAngle = atan2(hit.entryXY.y - hit.centerXY.y, hit.entryXY.x - hit.centerXY.x)
        val endAngle = atan2(hit.exitXY.y - hit.centerXY.y, hit.exitXY.x - hit.centerXY.x)
        val clockwise = arcPoints(hit, startAngle, endAngle, clockwise = true)
        val counterClockwise = arcPoints(hit, startAngle, endAngle, clockwise = false)
        return if (arcLength(clockwise) <= arcLength(counterClockwise)) clockwise else counterClockwise
    }

    private fun arcPoints(
        hit: SegmentCircleHit,
        startAngle: Double,
        endAngle: Double,
        clockwise: Boolean
    ): List<LatLng> {
        var sweep = if (clockwise) {
            normalizePositive(startAngle - endAngle)
        } else {
            normalizePositive(endAngle - startAngle)
        }
        if (sweep < 0.001) sweep = 2.0 * PI

        val steps = max(4, (sweep * hit.radiusMeters / 4.0).toInt().coerceAtMost(24))
        return (0..steps).map { index ->
            val progress = index.toDouble() / steps.toDouble()
            val angle = if (clockwise) {
                startAngle - sweep * progress
            } else {
                startAngle + sweep * progress
            }
            hit.projection.fromXY(
                PointXY(
                    x = hit.centerXY.x + cos(angle) * hit.radiusMeters,
                    y = hit.centerXY.y + kotlin.math.sin(angle) * hit.radiusMeters
                )
            )
        }
    }

    private fun arcLength(points: List<LatLng>): Double {
        return points.zipWithNext().sumOf { (from, to) ->
            SphericalUtil.computeDistanceBetween(from, to)
        }
    }

    private fun normalizePositive(angleRadians: Double): Double {
        var value = angleRadians % (2.0 * PI)
        if (value < 0.0) value += 2.0 * PI
        return value
    }

    private fun List<LatLng>.dedupeAdjacentPoints(): List<LatLng> {
        if (isEmpty()) return this
        val result = ArrayList<LatLng>()
        forEach { point ->
            if (result.isEmpty() || SphericalUtil.computeDistanceBetween(result.last(), point) > 0.05) {
                result.add(point)
            }
        }
        return result
    }

    private data class SegmentCircleHit(
        val obstacle: MissionObstacle,
        val centerXY: PointXY,
        val radiusMeters: Double,
        val entryT: Double,
        val exitT: Double,
        val entryPoint: LatLng,
        val exitPoint: LatLng,
        val entryXY: PointXY,
        val exitXY: PointXY,
        val projection: LocalProjection
    )

    private data class SegmentObstacleHit(
        val obstacle: MissionObstacle,
        val entryT: Double,
        val exitT: Double,
        val entryPoint: LatLng,
        val exitPoint: LatLng,
        val detourPoints: List<LatLng>
    )

    private data class PolygonIntersection(
        val t: Double,
        val edgeIndex: Int,
        val point: LatLng
    )

    private data class SegmentIntersection(
        val t: Double,
        val point: PointXY
    )

    private data class PointXY(val x: Double, val y: Double)

    private class LocalProjection(origin: LatLng) {
        private val originLat = origin.latitude
        private val originLon = origin.longitude
        private val metersPerLat = 111_320.0
        private val metersPerLon = 111_320.0 * cos(Math.toRadians(originLat)).coerceAtLeast(0.01)

        fun toXY(point: LatLng): PointXY {
            return PointXY(
                x = (point.longitude - originLon) * metersPerLon,
                y = (point.latitude - originLat) * metersPerLat
            )
        }

        fun fromXY(point: PointXY): LatLng {
            return LatLng(
                originLat + point.y / metersPerLat,
                originLon + point.x / metersPerLon
            )
        }
    }
}
