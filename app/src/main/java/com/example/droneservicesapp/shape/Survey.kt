package com.example.droneservicesapp.shape

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.example.droneservicesapp.R
import com.example.droneservicesapp.activities.MainActivityViewModel
import com.example.droneservicesapp.domain.model.MissionArea
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.maps.android.PolyUtil
import com.google.maps.android.SphericalUtil
import java.util.Collections

class Survey(private var area: MissionArea, var activity: FragmentActivity) {
    private var markers: ArrayList<Marker?> = ArrayList()

    private val MAX_AREA = 40000

    private companion object {
        private const val TAG = "Survey"
        private const val DEBUG_LOGS = false // set true only when debugging geometry
    }

    fun createSurveyPath(
        distance: Double,
        angle: Int,
        context: Context?,
    ): ArrayList<LatLng> {
        val activityViewModel = ViewModelProvider(activity)[MainActivityViewModel::class.java]

        var result = ArrayList<LatLng>()

        if (SphericalUtil.computeArea(area.vertices) > MAX_AREA) {
            Toast.makeText(
                context,
                context?.getString(R.string.area_too_big_msg),
                Toast.LENGTH_LONG
            ).show()
            return result
        }

        result = angledSurvey(distance, angle)

        var surveyDistance = 0.0
        if (result.size > 0 && result.size % 2 == 0) {
            for (i in 0 until result.size - 1) {
                surveyDistance += SphericalUtil.computeDistanceBetween(result[i], result[i + 1])
            }
        }
        activityViewModel.flightDistance.postValue(surveyDistance.toInt())

        return result
    }

    private fun angledSurvey(distance: Double, angle: Int): ArrayList<LatLng> {
        clearMarkers()

        val eastOrientationLines: ArrayList<Line> = ArrayList()
        val westOrientationLines: ArrayList<Line> = ArrayList()

        val angleD = angle.toDouble()
        val supplementaryAngleD = 90 - angleD

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

        Log.i(Log.INFO.toString(), "left  $left")
        Log.i(Log.INFO.toString(), "right  $right")
        Log.i(Log.INFO.toString(), "bot  $bot")
        Log.i(Log.INFO.toString(), "top  $top")

        val lineIntersections: ArrayList<LatLng> = ArrayList()

        // ✅ CRITICAL: copy list so we don't mutate model vertices
        val polygonClosed = ArrayList(area.vertices)
        if (polygonClosed.isNotEmpty()) polygonClosed.add(polygonClosed[0])

        val centoid = centoid(area.vertices)
        Log.i(Log.INFO.toString(), "centoid  $centoid")

        var point: LatLng = SphericalUtil.computeOffset(
            centoid,
            distance,
            supplementaryAngleD + 90.0
        )

        var intersectionsLocated = true

        // keep moving to the right till no intersections located
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
                westOrientationLines.add(geOuterIntersections(lineIntersections))
            }

            point = SphericalUtil.computeOffset(point, distance, supplementaryAngleD + 270.0)
        }

        return mergeEastWestWPlistsbyMinDistanceSorting(eastOrientationLines, westOrientationLines)
    }

    private fun mergeEastWestWPlistsbyMinDistanceSorting(
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
                curWP =
                    if (SphericalUtil.computeDistanceBetween(curLine.p1!!, curWP) <
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
                curWP =
                    if (SphericalUtil.computeDistanceBetween(curLine.p1!!, curWP) <
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

    private fun centoid(polygon: List<LatLng>): LatLng {
        var lats = 0.0
        var longs = 0.0
        for (p in polygon) {
            lats += p.latitude
            longs += p.longitude
        }
        return LatLng(lats / polygon.size, longs / polygon.size)
    }

    fun geOuterIntersections(lineIntersections: ArrayList<LatLng>): Line {
        var maxP: LatLng = lineIntersections.first()
        var minP: LatLng = lineIntersections.first()
        for (p in lineIntersections) {
            if (maxP.latitude < p.latitude) maxP = p
            if (minP.latitude > p.latitude) minP = p
        }

        if (maxP == minP) {
            for (p in lineIntersections) {
                if (maxP.longitude < p.longitude) maxP = p
                if (minP.longitude > p.longitude) minP = p
            }
        }

        return Line(minP, maxP)
    }

    fun verticalLinesSurvey(distance: Double): ArrayList<LatLng> {
        var left = LatLng(-1000.0, -1000.0)
        var right = LatLng(1000.0, 1000.0)

        for (edge in area.vertices) {
            if (edge.longitude < left.longitude) left = edge
            if (edge.longitude > right.longitude) right = edge
        }

        val result: ArrayList<LatLng> = ArrayList()
        val verticalLineIntersections: ArrayList<LatLng> = ArrayList()

        var point: LatLng = SphericalUtil.computeOffset(left, distance / 5, 90.0)
        right = SphericalUtil.computeOffset(right, 0.5 * distance, 90.0)

        while (point.longitude < right.longitude) {
            verticalLineIntersections.clear()

            for (i in 0 until area.vertices.size) {
                val line: List<LatLng> = if (i < area.vertices.size - 1)
                    listOf(area.vertices[i], area.vertices[i + 1])
                else
                    listOf(area.vertices[i], area.vertices[0])

                val step = 10e-8
                val tolerance = 1.0

                if ((line[0].longitude > point.longitude && point.longitude >= line[1].longitude) ||
                    (line[0].longitude < point.longitude && point.longitude <= line[1].longitude)
                ) {
                    if (line[0].latitude < line[1].latitude) {
                        var lat = line[0].latitude
                        while (lat <= line[1].latitude) {
                            if (PolyUtil.isLocationOnEdge(
                                    LatLng(lat, point.longitude),
                                    line,
                                    true,
                                    tolerance
                                )
                            ) {
                                verticalLineIntersections.add(LatLng(lat, point.longitude))
                                break
                            }
                            lat += step
                        }
                    } else if (line[0].latitude > line[1].latitude) {
                        var lat = line[1].latitude
                        while (lat <= line[0].latitude) {
                            if (PolyUtil.isLocationOnEdge(
                                    LatLng(lat, point.longitude),
                                    line,
                                    true,
                                    tolerance
                                )
                            ) {
                                verticalLineIntersections.add(LatLng(lat, point.longitude))
                                break
                            }
                            lat += step
                        }
                    }
                }
            }

            if (verticalLineIntersections.size >= 2) {
                var maxP: LatLng = verticalLineIntersections.first()
                var minP: LatLng = verticalLineIntersections.first()
                for (p in verticalLineIntersections) {
                    if (maxP.latitude < p.latitude) maxP = p
                    if (minP.latitude > p.latitude) minP = p
                }

                if (result.size > 0) {
                    val lastP = result.last()

                    if (SphericalUtil.computeDistanceBetween(lastP, minP) >
                        SphericalUtil.computeDistanceBetween(lastP, maxP)
                    ) {
                        result.add(maxP)
                        result.add(minP)
                    } else {
                        result.add(minP)
                        result.add(maxP)
                    }
                } else {
                    result.add(minP)
                    result.add(maxP)
                }
            }

            point = SphericalUtil.computeOffset(point, distance, 90.0)
        }

        return result
    }

    fun clearMarkers() {
        for (marker in markers) marker?.remove()
        markers.clear()
    }
}