package com.example.droneservicesapp.shape

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.location.Location
import android.text.Html
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import com.example.droneservicesapp.R
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import com.google.maps.android.SphericalUtil
import java.util.*
import kotlin.math.roundToInt

class PolygonArea {

    var latLngArrayListMarkers: ArrayList<Marker> = ArrayList()
    var mapLabelMarkers: ArrayList<Marker> = ArrayList()
    var polygonAreaMarker: Marker? = null
    var polygonEdges : ArrayList<LatLng> = ArrayList()
    var polygon: Polygon? = null
    private var distancesFromMidPointsOfPolygonEdges: ArrayList<Double> = ArrayList()

    private var polygonOptions : PolygonOptions = polygonOptionInit()

    private lateinit var polylineOptions : PolylineOptions
    private var polyline : Polyline? = null
    var surveyPath : ArrayList<LatLng> = ArrayList()

    private var firstPolygonEdgeAdded : Boolean = false


    private fun polygonOptionInit(): PolygonOptions {
        val polygonOptions = PolygonOptions()
        polygonOptions.strokeColor(Color.BLACK)
        polygonOptions.strokeWidth(5f)

        return polygonOptions
    }

    private fun polylineOptionsInit(): PolylineOptions {
        return PolylineOptions()
            .color(Color.RED)
            .width(4.0F)
            .visible(true)
            .addAll(surveyPath)
    }

    private fun ArrayList<LatLng>.adjustPolygonWithRespectTo(point: LatLng): ArrayList<LatLng> {

        var minDistance = 0.0
        if (this.size > 2) {
            distancesFromMidPointsOfPolygonEdges.clear()
            for (i in 0 until this.size) {
                // 1. Find the mid points of the edges of polygon
                val list: ArrayList<LatLng> = ArrayList()
                if (i == this.size - 1) {
                    list.add(this[this.size - 1])
                    list.add(this[0])
                } else {
                    list.add(this[i])
                    list.add(this[i + 1])
                }
                val midPoint = computeCentroid(list)

                // 2. Calculate the nearest coordinate by finding distance between mid point of each edge and the coordinate to be drawn
                val startPoint = Location("")
                startPoint.latitude = point.latitude
                startPoint.longitude = point.longitude
                val endPoint = Location("")
                endPoint.latitude = midPoint.latitude
                endPoint.longitude = midPoint.longitude
                val distance = startPoint.distanceTo(endPoint).toDouble()
                distancesFromMidPointsOfPolygonEdges.add(distance)
                if (i == 0) {
                    minDistance = distance
                } else {
                    if (distance < minDistance) {
                        minDistance = distance
                    }
                }
            }

            // 3. The nearest coordinate = the edge with minimum distance from mid point to the coordinate to be drawn
            val position = minIndex(distancesFromMidPointsOfPolygonEdges)


            // 4. move the nearest coordinate at the end by shifting array right
            val shiftByNumber: Int = this.size - position - 1
            if (shiftByNumber != this.size) {
                this.rotate(shiftByNumber)
            }
        }

        // 5. Now add coordinated to be drawn
        this.add(point)

        alignMarkersWithEdges()
        return this
    }

    private fun minIndex(list: ArrayList<Double>): Int {
        return list.indexOf(Collections.min(list))
    }

    private fun ArrayList<LatLng>.rotate(shift: Int): ArrayList<LatLng> {
        if (this.size == 0) return this
        var element: LatLng?
        for (i in 0 until shift) {
            // remove last element, add it to front of the ArrayList
            element = this[this.size - 1]
            this.removeAt(this.size - 1)
            this.add(0, element)
        }
        return this
    }

    private fun computeCentroid(points: List<LatLng>): LatLng {
        var latitude = 0.0
        var longitude = 0.0
        val n = points.size
        for (point in points) {
            latitude += point.latitude
            longitude += point.longitude
        }
        return LatLng(latitude / n, longitude / n)
    }


    private fun alignMarkersWithEdges()
    {
        val newMarkers : ArrayList<Marker> = ArrayList()

        for(e in polygonEdges)
        {
            for(m in latLngArrayListMarkers)
            {
                if(m.position == e)
                    newMarkers.add(m)
            }
        }

        latLngArrayListMarkers.clear()
        latLngArrayListMarkers = newMarkers
    }

    private fun alignEdgesWithMarkers()
    {
        val newEdges : ArrayList<LatLng> = ArrayList()

        for(m in latLngArrayListMarkers)
        {
            for(e in polygonEdges)
            {
                if(e == m.position)
                    newEdges.add(e)
            }
        }

        polygonEdges.clear()
        polygonEdges = newEdges
    }

    fun adjustEdgeToPolygon(newp0: Marker, map: GoogleMap,  activity : Activity)
    {
        val adjustingIndex = latLngArrayListMarkers.indexOf(newp0)
        try {
            polygonEdges.removeAt(adjustingIndex)
        } catch (e: IndexOutOfBoundsException) {
            Toast.makeText(
                activity.baseContext,
                activity.baseContext.getString(R.string.action_failed),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (polygon != null) {
            polygon!!.remove()
            polygon = null
        }

        if (firstPolygonEdgeAdded)
        {
            polygonEdges.add(newp0.position)
            alignEdgesWithMarkers()

            polygonOptions = polygonOptionInit()
            for (i in 0 until polygonEdges.size)
                polygonOptions.add( LatLng(polygonEdges[i].latitude,
                    polygonEdges[i].longitude) )
        }
        else
        {
            polygonOptions = polygonOptionInit().add(newp0.position)
            polygonEdges.add(newp0.position)
            firstPolygonEdgeAdded = true
        }

        refreshLabels(map, activity)
        polygon = map.addPolygon(polygonOptions)
    }

    fun addEdgeToPolygon(m0: Marker, map : GoogleMap, context: Context) {

        val p0 = LatLng(m0.position.latitude, m0.position.longitude)

        if (polygon != null) {
            polygon!!.remove()
            polygon = null
        }

        if (firstPolygonEdgeAdded)
        {
            polygonEdges.adjustPolygonWithRespectTo(p0)

            polygonOptions = polygonOptionInit()

            for (i in 0 until polygonEdges.size)
                polygonOptions.add( polygonEdges[i] )

            refreshLabels(map, context)
        }
        else
        {
            polygonOptions = polygonOptionInit().add(p0)
            polygonEdges.add(p0)
            firstPolygonEdgeAdded = true
        }

        polygon = map.addPolygon(polygonOptions)
    }

    fun removeEdgeFromPolygon(m0 : Marker, map : GoogleMap, context: Context)
    {
        // remove edge from polygon list
        m0.remove()
        latLngArrayListMarkers.remove(m0)
        polygonEdges.remove(m0.position)

        if (polygon != null) {
            polygon!!.remove()
            polygon = null
        }

        //recreate polygon shape and markers
        polygonOptions = polygonOptionInit()
        for (i in 0 until polygonEdges.size) {
            polygonOptions.add(polygonEdges[i])
        }

        refreshLabels(map, context)
        polygon = map.addPolygon(polygonOptions)
    }


    private fun refreshLabels(map: GoogleMap, context: Context)
    {
        for(labelMarker in mapLabelMarkers)
            labelMarker.remove()
        mapLabelMarkers.clear()

        if( polygonEdges.size > 1 ) {
            for (i in 0 until polygonEdges.size) {
                val midpoint = midPoint(
                    polygonEdges[i],
                    polygonEdges[(i + 1) % polygonEdges.size]
                )
                val distance = SphericalUtil.computeDistanceBetween(
                    polygonEdges[i],
                    polygonEdges[(i + 1) % polygonEdges.size]
                )

                val text = distance.roundToInt().toString() + "m"
                val markerOptions = MarkerOptions()
                    .position(midpoint)
                    .icon(BitmapDescriptorFactory.fromBitmap(getMarkerBitmapFromView(text, context)))
                val marker = map.addMarker(markerOptions)

                if (marker != null) {
                    mapLabelMarkers.add(marker)
                }
            }
        }

        polygonAreaMarker?.remove()
        polygonAreaMarker = if( polygonEdges.size > 2 )
        {
            val areaCalc = SphericalUtil.computeArea(polygonEdges)
            val text = areaCalc.toInt().toString() + Html.fromHtml("m<sup>2</sup>")
            val markerOptions = MarkerOptions()
                .position(computeCentroid(polygonEdges))
                .icon(BitmapDescriptorFactory.fromBitmap(getMarkerBitmapFromView(text, context)))
            map.addMarker(markerOptions)
        }
        else
        {
            null
        }
    }


    fun surveyPolylineOptions(map: GoogleMap): Polyline? {
        polylineOptions = polylineOptionsInit()
        polyline = map.addPolyline(polylineOptions)
        return polyline
    }

    fun clearDrawings()
    {
        clearPolygon()
        clearSurveyPath()
    }

    fun clearSurveyPath()
    {
        surveyPath.clear()
        polyline?.remove()
        polyline = null
    }



    private fun clearPolygon()
    {
        for(marker in latLngArrayListMarkers)
            marker.remove()
        for(marker in mapLabelMarkers)
            marker.remove()
        polygonAreaMarker?.remove()
        polygonAreaMarker = null

        mapLabelMarkers.clear()
        latLngArrayListMarkers.clear()
        polygonEdges.clear()

        polygon?.remove()
        polygon = null

        firstPolygonEdgeAdded = false
    }

    private fun midPoint(p1: LatLng, p2 : LatLng): LatLng {
        val lat1 = p1.latitude
        val lon1 = p1.longitude
        val lat2 = p2.latitude
        val lon2 = p2.longitude

        val lat3 = (lat1 + lat2) / 2
        val lon3 = (lon1 + lon2) / 2

        return LatLng(lat3, lon3)
    }



//    fun containsLocation(p : LatLng, rightLimit: LatLng): Boolean
//    {
//        val horizontalLine = Line(p, LatLng(p.latitude, rightLimit.longitude))
//        var intersections = 0
//
//        lines@ for( i in 0 until polygonEdges.size)
//        {
//            var p1 : LatLng
//            var p2 : LatLng
//
//            if( i < polygonEdges.size -1){
//                p1 = polygonEdges[i]
//                p2 = polygonEdges[i+1]
//            }
//            else{
//                p1 = polygonEdges[i]
//                p2 = polygonEdges[0]
//            }
//
//            val polygonLine = Line(p1, p2)
//
//            p1 = p1.round()
//            p2 = p2.round()
//
//            val intersectionPoint = horizontalLine.lineLineIntersection(polygonLine)
//            intersectionPoint?.round()
//
//            if(intersectionPoint != null &&
//                intersectionPoint.latitude in p1.latitude..p2.latitude &&
//                intersectionPoint.longitude in p1.longitude..p2.longitude )
//            {
//                intersections++
//            }
//        }
//
//        if( intersections % 2 == 0 )
//            return false
//
//        return true
//    }


//    private fun LatLng.round(decimals: Int = 6) : LatLng = LatLng(this.latitude.round(decimals), this.longitude.round(decimals))
//
//    private fun Double.round(decimals: Int = 2): Double = (kotlin.math.round(this * 10.0.pow(decimals)) /
//            10.0.pow(decimals))



    private fun getMarkerBitmapFromView(text: String, context: Context): Bitmap {
        val customMarkerView = LayoutInflater.from(context).inflate(R.layout.num_transp_marker, null)
        val markerTextView = customMarkerView.findViewById<TextView>(R.id.marker_text)
        markerTextView.text = text

        val displayMetrics = DisplayMetrics()
        (context as Activity).windowManager.defaultDisplay.getMetrics(displayMetrics)
        customMarkerView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        customMarkerView.measure(displayMetrics.widthPixels, displayMetrics.heightPixels)
        customMarkerView.layout(0, 0, displayMetrics.widthPixels, displayMetrics.heightPixels)

        val bitmap = Bitmap.createBitmap(customMarkerView.measuredWidth, customMarkerView.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        customMarkerView.draw(canvas)
        return bitmap
    }


    fun calculatePolygonBounds(): LatLngBounds {
        val builder = LatLngBounds.Builder()
        for (point in polygon!!.points) {
            builder.include(point)
        }
        return builder.build()
    }
}