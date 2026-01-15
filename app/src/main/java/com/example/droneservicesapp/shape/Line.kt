package com.example.droneservicesapp.shape

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import java.util.Collections.max
import java.util.Collections.min
import kotlin.math.pow
import kotlin.math.round

class Line(p1 : LatLng, p2 : LatLng) {

    var m : Double? = null
    var b : Double? = null
    var p1 : LatLng? = null
    var p2 : LatLng? = null

    /*****************************************
     * Base constructor. Calculates m and b for y = mx + b
     * for 2 points on map
     */
    init {
        if(p1 != LatLng(0.0, 0.0))
            this.p1 = p1

        if(p2 != LatLng(0.0, 0.0))
            this.p2 = p2

        if(p1.longitude == p2.longitude) {
            this.m = 0.0
            this.b = p1.longitude
        }
        else
        {
            this.m = (p2.latitude - p1.latitude) / (p2.longitude - p1.longitude)
            this.b = p1.latitude - (m!! * p1.longitude)
        }
    }

    /*****************************************
     * Constructor. Sets m and b for y = mx + b
     */
    constructor(m : Double, b : Double) :
            this(LatLng(0.0, 0.0), LatLng(0.0, 0.0))
    {
        this.m = m
        this.b = b
    }

    /*****************************************
     * Base constructor. Calculates m and b for y = mx + b
     * from one point on map and angle of line
     */
    constructor(p : LatLng, angle : Double) :
            this(LatLng(0.0, 0.0), LatLng(0.0, 0.0))
    {
        if( angle==90.0 || angle == 270.0 )
            this.m = Math.tan(Math.toRadians(angle-0.001))
        else
            this.m = Math.tan(Math.toRadians(angle))//.round(2)

        Log.i(Log.INFO.toString(), "angle  $angle" )
        Log.i(Log.INFO.toString(), "tan(angle)  ${this.m}" )
        this.b = p.latitude - (m!! * p.longitude)
    }

    /*****************************************
     * Function: pointBelongsToLine
     * Checks if a point(p) belongs to current line, using the equation p.y == m * p.x + b
     */
    fun pointBelongsToLine(p : LatLng) : Boolean
    {
        if( ((m!! * p.longitude) + b!!).round(6) == p.latitude.round(6) )
            return true

        return false
    }

    /*****************************************
     * Function: getLineY
     * Returns y with input x, using the equation p.y == m * p.x + b
     */
    fun getLineY(x : Double) : Double?
    {
        if(m != null && b != null)
            return m!! * x + b!!
        else
            return null
    }

    /*****************************************
     * Function: getLineX
     * Returns x with input y, using the equation p.y == m * p.x + b
     */
    fun getLineX(y : Double) : Double?
    {
        if(m != null && b != null)
            return (y - b!!)/m!!
        else
            return null
    }

    /*****************************************
     * Function: lineLineIntersection
     * Calculates intersection point of two Linear Sections
     */
    fun lineLineIntersection(A: LatLng, B: LatLng, C: LatLng, D: LatLng): LatLng? {
        // Line AB represented as a1x + b1y = c1
        val a1: Double = B.latitude - A.latitude
        val b1: Double = A.longitude - B.longitude
        val c1: Double = a1 * A.longitude + b1 * A.latitude

        // Line CD represented as a2x + b2y = c2
        val a2: Double = D.latitude - C.latitude
        val b2: Double = C.longitude - D.longitude
        val c2: Double = a2 * C.longitude + b2 * C.latitude
        val determinant = a1 * b2 - a2 * b1
        return if (determinant == 0.0) {
            // The lines are parallel. This is simplified
            // by returning a pair of FLT_MAX
            null
        } else {
            val x = (b2 * c1 - b1 * c2) / determinant
            val y = (a1 * c2 - a2 * c1) / determinant
            LatLng(x, y)
        }
    }

    fun lineLineIntersection(line2 : Line): LatLng? {
        var x = 0.0
        var y = 0.0

        if(line2.m == 0.0 && this.m == 0.0)
        {
            return null
        }
        else if( this.m!! == 0.0 )
        {
            y = this.b!!
            x = (y - line2.b!!) / line2.m!!
        }

        else if( line2.m!! == 0.0 )
        {
            y = line2.b!!
            x = (y - this.b!!) / this.m!!
        }
        else
        {
            x = (line2.b!! - this.b!!) / (this.m!! - line2.m!!)
            y = this.m!! * x + this.b!!
        }

        return LatLng(y, x)
    }

    fun lineLinearSectionIntersection(line2 : Line, p1: LatLng, p2: LatLng): LatLng? {

        this.lineLineIntersection(line2)?.let {
            val x = it.longitude
            val y = it.latitude

            if( ((p1.longitude <= x && x<=p2.longitude) || (p1.longitude >= x && x>=p2.longitude)) &&
                ((p1.latitude <= y && y <= p2.latitude) || (p1.latitude >= y && y >=p2.latitude)))
                return LatLng(y, x)
        }

        return null
    }


    /*****************************************
     * Function: lineLineIntersection
     * Calculates intersection point of two Linear Sections
     */
    fun linearSectionsIntersection(A: LatLng, B: LatLng, C: LatLng, D: LatLng): LatLng? {

        val lats : ArrayList<Double> = ArrayList()
        lats.add(A.latitude)
        lats.add(B.latitude)
        lats.add(C.latitude)
        lats.add(D.latitude)

        val longs : ArrayList<Double> = ArrayList()
        longs.add(A.longitude)
        longs.add(B.longitude)
        longs.add(C.longitude)
        longs.add(D.longitude)

        val maxLat = max(lats)
        val maxLong = max(longs)
        val minLat = min(lats)
        val minLong = min(longs)

        this.lineLineIntersection(A, B, C, D)?.let {
            if(it.latitude in minLat..maxLat && it.longitude in minLong..maxLong)
                return it
        }

        return null
    }


    fun Double.round(decimals: Int = 2): Double = (round(this * 10.0.pow(decimals)) /
                                                    10.0.pow(decimals))

}