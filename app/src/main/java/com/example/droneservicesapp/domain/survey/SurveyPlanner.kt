package com.example.droneservicesapp.domain.survey

import android.util.Log
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.model.MissionArea
import com.example.droneservicesapp.shape.Line
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil

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
        val resultLatLng = angledSurvey(missionArea, distanceMeters, angleDeg)

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

        val eastOrientationLines: ArrayList<Line> = ArrayList()
        val westOrientationLines: ArrayList<Line> = ArrayList()

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

            val pointLine = Line(point, angleD)

            for (i in 0 until polygonClosed.size - 1) {
                val p1: LatLng = polygonClosed[i]
                val p2: LatLng = polygonClosed[i + 1]
                val line = Line(p1, p2)

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

            val pointLine = Line(point, angleD)

            for (i in 0 until polygonClosed.size - 1) {
                val p1: LatLng = polygonClosed[i]
                val p2: LatLng = polygonClosed[i + 1]
                val line = Line(p1, p2)

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
        eastLines: ArrayList<Line>,
        westLines: ArrayList<Line>
    ): ArrayList<LatLng> {
        val result = ArrayList<LatLng>()
        lateinit var curLine: Line
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
    private fun getOuterIntersections(lineIntersections: ArrayList<LatLng>): Line {
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

        return Line(minP, maxP)
    }
}