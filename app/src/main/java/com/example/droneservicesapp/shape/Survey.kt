package com.example.droneservicesapp.shape

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.example.droneservicesapp.R
import com.example.droneservicesapp.activities.MainActivityViewModel
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.maps.android.PolyUtil
import com.google.maps.android.SphericalUtil


class Survey(private var area: PolygonArea, var activity: FragmentActivity) {
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
        map: GoogleMap
    ): ArrayList<LatLng> {
        val activityViewModel = ViewModelProvider(activity)[MainActivityViewModel::class.java]

        var result = ArrayList<LatLng>()

        if (SphericalUtil.computeArea(area.polygonEdges) > MAX_AREA) {
            Toast.makeText(
                context,
                context?.getString(R.string.area_too_big_msg),
                Toast.LENGTH_LONG
            ).show()
            return result
        }

        result = angledSurvey(distance, angle, map)

        var surveyDistance = 0.0
        if (result.size > 0 && result.size % 2 == 0) {
            for (i in 0 until result.size - 1) {
                surveyDistance += SphericalUtil.computeDistanceBetween(result[i], result[i + 1])
            }
        }
        activityViewModel.flightDistance.postValue(surveyDistance.toInt())

        return result
    }


    private fun angledSurvey(distance: Double, angle: Int, map: GoogleMap): ArrayList<LatLng> {
        clearMarkers()

        val eastOrientationLines: ArrayList<Line> = ArrayList()
        val westOrientationLines: ArrayList<Line> = ArrayList()

        val angleD = angle.toDouble()
        val supplementaryAngleD = 90 - angleD

        var left = LatLng(-1000.0, -1000.0)
        var right = LatLng(1000.0, 1000.0)
        var top = LatLng(-1000.0, -1000.0)
        var bot = LatLng(1000.0, 1000.0)


        for (edge in area.polygonEdges) {
            if (edge.longitude < left.longitude)
                left = edge

            if (edge.longitude > right.longitude)
                right = edge

            if (edge.latitude < bot.latitude)
                bot = edge

            if (edge.latitude > top.latitude)
                top = edge
        }

        Log.i(Log.INFO.toString(), "left  $left")
        Log.i(Log.INFO.toString(), "right  $right")
        Log.i(Log.INFO.toString(), "bot  $bot")
        Log.i(Log.INFO.toString(), "top  $top")

        val lineIntersections: ArrayList<LatLng> = ArrayList()

        val polygonClosed = area.polygonEdges
        polygonClosed.add(area.polygonEdges[0])

        val centoid = centoid(area.polygonEdges)
        Log.i(Log.INFO.toString(), "centoid  $centoid")


        var point: LatLng = SphericalUtil.computeOffset(
            centoid,
            distance,
            supplementaryAngleD + 90.0
        ) // Shift distance meters vertically to line

        var intersectionsLocated = true

        // keep moving to the right till no intersections located
        while (intersectionsLocated) {
            Log.i(Log.INFO.toString(), "point  $point")
            Log.i(Log.INFO.toString(), "right  $right")

            lineIntersections.clear()

            val pointLine = Line(point, angleD)
            Log.i(Log.INFO.toString(), " y = ${pointLine.m}x + ${pointLine.b}")

            lines@ for (i in 0 until polygonClosed.size - 1) {
                val p1: LatLng = polygonClosed[i]
                val p2: LatLng = polygonClosed[i + 1]
                val line = Line(p1, p2)
                Log.i(Log.INFO.toString(), "line y = ${line.m}x + ${line.b}")

                val intersection = line.lineLinearSectionIntersection(pointLine, p1, p2)
                if (DEBUG_LOGS) Log.d(TAG, "intersection=$intersection")

                intersection?.let {
                    lineIntersections.add(it)
                }
            }

            intersectionsLocated = lineIntersections.size >= 2
            Log.i(Log.INFO.toString(), "east intersections located  $intersectionsLocated")
            point = SphericalUtil.computeOffset(
                point,
                distance,
                supplementaryAngleD + 90.0
            ) // Shift distance meters to the east
        }


        intersectionsLocated = true
        point = SphericalUtil.computeOffset(
            point,
            2 * distance,
            supplementaryAngleD + 270
        ) // Shift distance meters to the west

        Log.i(Log.INFO.toString(), "left  loop")

        while (intersectionsLocated) {
            Log.i(Log.INFO.toString(), "point  $point")
            Log.i(Log.INFO.toString(), "left  $left")

            lineIntersections.clear()

            val pointLine = Line(point, angleD)
            Log.i(Log.INFO.toString(), " y = ${pointLine.m}x + ${pointLine.b}")

            lines@ for (i in 0 until polygonClosed.size - 1) {
                val p1: LatLng = polygonClosed[i]
                val p2: LatLng = polygonClosed[i + 1]
                val line = Line(p1, p2)
                if (DEBUG_LOGS) Log.d(TAG, "line=$line")

                val intersection = line.lineLinearSectionIntersection(pointLine, p1, p2)
                if (DEBUG_LOGS) Log.d(TAG, "intersection=$intersection")

                intersection?.let {
                    lineIntersections.add(it)
                }
            }

            intersectionsLocated = lineIntersections.size >= 2
            Log.i(Log.INFO.toString(), "west intersections located  $intersectionsLocated")

            if (intersectionsLocated) {
                westOrientationLines.add(geOuterIntersections(lineIntersections))
            } else {
                Log.i(Log.INFO.toString(), "Not even 2 intersections")
            }

            point = SphericalUtil.computeOffset(
                point,
                distance,
                supplementaryAngleD + 270.0
            ) // Shift distance meters to the west
        }

        return mergeEastWestWPlistsbyMinDistanceSorting(
            eastOrientationLines,
            westOrientationLines,
            map
        )
    }


    private fun mergeEastWestWPlistsbyMinDistanceSorting(
        eastLines: ArrayList<Line>,
        westLines: ArrayList<Line>,
        map: GoogleMap
    ): ArrayList<LatLng> {
        val result = ArrayList<LatLng>()
        lateinit var curLine: Line
        lateinit var curWP: LatLng

        Log.i(Log.INFO.toString(), "westWPs.size  ${westLines.size}")
        Log.i(Log.INFO.toString(), "eastWPs.size  ${eastLines.size}")

        if (westLines.size > 0) {
            curLine = westLines.removeAt(westLines.size - 1)
            result.add(curLine.p1!!)
            result.add(curLine.p2!!)

            curWP = curLine.p2!!

            while (westLines.size > 0) {
                curLine = westLines.removeAt(westLines.size - 1)
                curWP = if (SphericalUtil.computeDistanceBetween(
                        curLine.p1!!,
                        curWP
                    ) < SphericalUtil.computeDistanceBetween(curLine.p2!!, curWP)
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


            // in case westWPs.size > 0 we continue from last element results we created
            // in case westWPs.size = 0 we start from the first element of the east WP list.
            while (eastLines.size > 0) {
                curLine = eastLines.removeAt(0)
                curWP = if (SphericalUtil.computeDistanceBetween(
                        curLine.p1!!,
                        curWP
                    ) < SphericalUtil.computeDistanceBetween(curLine.p2!!, curWP)
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


    private fun centoid(polygon: ArrayList<LatLng>): LatLng {
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
            if (maxP.latitude < p.latitude)
                maxP = p

            if (minP.latitude > p.latitude)
                minP = p
        }

        if (maxP == minP) {
            for (p in lineIntersections) {
                if (maxP.longitude < p.longitude)
                    maxP = p

                if (minP.longitude > p.longitude)
                    minP = p
            }
        }

        return Line(minP, maxP)
    }


    fun verticalLinesSurvey(distance: Double): ArrayList<LatLng> {

        var left = LatLng(-1000.0, -1000.0)
        var right = LatLng(1000.0, 1000.0)

        for (edge in area.polygonEdges) {
            if (edge.longitude < left.longitude)
                left = edge

            if (edge.longitude > right.longitude)
                right = edge
        }

        Log.i(Log.INFO.toString(), "left  $left")
        Log.i(Log.INFO.toString(), "right  $right")


        val result: ArrayList<LatLng> = ArrayList()
        val verticalLineIntersections: ArrayList<LatLng> = ArrayList()

        var point: LatLng = SphericalUtil.computeOffset(
            left,
            distance / 5,
            90.0
        ) // Shift distance meters to the east!!
        right = SphericalUtil.computeOffset(
            right,
            0.5 * distance,
            90.0
        ) // Shift distance meters to the east!!

        while (point.longitude < right?.longitude!!) {
            Log.i(Log.INFO.toString(), "point  $point")
            Log.i(Log.INFO.toString(), "right  $right")

            verticalLineIntersections.clear()

            lines@ for (i in 0 until area.polygonEdges.size) {
                val line: List<LatLng> = if (i < area.polygonEdges.size - 1)
                    listOf(area.polygonEdges[i], area.polygonEdges[i + 1])
                else
                    listOf(area.polygonEdges[i], area.polygonEdges[0])

                if (DEBUG_LOGS) Log.d(TAG, "line=$line")

                val step = 10e-8
                val tolerance = 1.0

                if ((line[0].longitude > point.longitude && point.longitude >= line[1].longitude) ||
                    (line[0].longitude < point.longitude && point.longitude <= line[1].longitude)
                ) {
                    if (line[0].latitude < line[1].latitude) {
                        var lat = line[0].latitude
                        Log.i(Log.INFO.toString(), "0lat0  ${line[0]}")
                        Log.i(Log.INFO.toString(), "0poin  $point")
                        Log.i(Log.INFO.toString(), "0lat1  ${line[1]}")

                        intersect@ while (lat <= line[1].latitude) {
                            if (PolyUtil.isLocationOnEdge(
                                    LatLng(lat, point.longitude),
                                    line,
                                    true,
                                    tolerance
                                )
                            ) {
                                verticalLineIntersections.add(LatLng(lat, point.longitude))
                                Log.i(Log.INFO.toString(), "result latlng  $result")
                                break@intersect
                            }

                            lat += step
                        }
                    } else if (line[0].latitude > line[1].latitude) {
                        var lat = line[1].latitude

                        Log.i(Log.INFO.toString(), "1lat0  ${line[0]}")
                        Log.i(Log.INFO.toString(), "0poin  $point")
                        Log.i(Log.INFO.toString(), "1lat1  ${line[1]}")

                        intersect@ while (lat <= line[0].latitude) {
                            if (PolyUtil.isLocationOnEdge(
                                    LatLng(lat, point.longitude),
                                    line,
                                    true,
                                    tolerance
                                )
                            ) {
                                verticalLineIntersections.add(LatLng(lat, point.longitude))
                                Log.i(Log.INFO.toString(), "result latlng  $result")
                                break@intersect
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
                    if (maxP.latitude < p.latitude)
                        maxP = p

                    if (minP.latitude > p.latitude)
                        minP = p
                }

                if (result.size > 0) {
                    val lastP = result.last()

                    if (SphericalUtil.computeDistanceBetween(
                            lastP,
                            minP
                        ) > SphericalUtil.computeDistanceBetween(
                            lastP,
                            maxP
                        )
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

            point = SphericalUtil.computeOffset(
                point,
                distance,
                90.0
            ) // Shift distance meters to the east
        }

        return result
    }


    fun clearMarkers() {
        for (marker in markers)
            marker?.remove()

        markers.clear()
    }

}