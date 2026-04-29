package com.example.droneservicesapp.ui.home.geoawareness

import android.content.Context
import android.graphics.Color
import androidx.appcompat.app.AlertDialog
import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.geoawareness.GeoZoneGeometry
import com.example.droneservicesapp.domain.geoawareness.GeoZoneRestriction
import com.example.droneservicesapp.domain.model.LatLon
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class GeoZoneOverlayController(
    private val context: Context,
    private val mapView: MapView
) {
    private val overlays = mutableListOf<Overlay>()

    fun renderZones(zones: List<GeoZone>) {
        clear()

        zones.forEach { zone ->
            zone.geometries.forEach { geometry ->
                when (geometry) {
                    is GeoZoneGeometry.Circle -> renderCircle(zone, geometry)
                    is GeoZoneGeometry.Polygon -> renderPolygon(zone, geometry)
                }
            }
        }

        mapView.invalidate()
    }

    fun clear() {
        mapView.overlays.removeAll(overlays)
        overlays.clear()
        mapView.invalidate()
    }

    private fun renderCircle(zone: GeoZone, geometry: GeoZoneGeometry.Circle) {
        val points = createCirclePoints(geometry.center, geometry.radiusMeters)
        if (points.size < 4) {
            return
        }

        addZonePolygon(zone, geometry, points)
    }

    private fun renderPolygon(zone: GeoZone, geometry: GeoZoneGeometry.Polygon) {
        val outerRing = geometry.rings.firstOrNull().orEmpty()
        if (outerRing.size < 3) {
            return
        }

        val points = outerRing.map { GeoPoint(it.lat, it.lon) }.toMutableList()
        closePolygon(points)
        if (points.size < 4) {
            return
        }

        addZonePolygon(zone, geometry, points)
    }

    private fun addZonePolygon(
        zone: GeoZone,
        geometry: GeoZoneGeometry,
        points: List<GeoPoint>
    ) {
        val style = styleFor(zone.restriction)
        val polygon = Polygon(mapView).apply {
            this.points = points
            outlinePaint.color = style.strokeColor
            outlinePaint.strokeWidth = 3f
            fillPaint.color = style.fillColor
            title = zone.name
            subDescription = zone.message
            setOnClickListener { _, _, _ ->
                showZoneDetails(zone, geometry)
                true
            }
        }

        mapView.overlays.add(polygon)
        overlays += polygon
    }

    private fun showZoneDetails(zone: GeoZone, geometry: GeoZoneGeometry) {
        val message = buildString {
            appendLine("Restriction: ${zone.restriction}")
            appendLine("Message: ${zone.message ?: "No message"}")
            appendLine("Authority: ${zone.authorities.firstOrNull()?.name ?: "N/A"}")
            appendLine("Altitude: ${formatAltitude(geometry)}")
            append("Dummy data notice: Development-only dummy data. Verify official restrictions in DAGR before flight.")
        }

        AlertDialog.Builder(context)
            .setTitle(zone.name)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun formatAltitude(geometry: GeoZoneGeometry): String {
        val lower = geometry.lowerLimitMeters?.formatMeters()
        val upper = geometry.upperLimitMeters?.formatMeters()
        return when {
            lower != null && upper != null -> "$lower-$upper m AGL"
            upper != null -> "Up to $upper m AGL"
            lower != null -> "From $lower m AGL"
            else -> "Not specified"
        }
    }

    private fun createCirclePoints(
        center: LatLon,
        radiusMeters: Double,
        segments: Int = 72
    ): List<GeoPoint> {
        if (radiusMeters <= 0.0 || segments < 3) {
            return emptyList()
        }

        val earthRadiusMeters = 6_371_000.0
        val angularDistance = radiusMeters / earthRadiusMeters
        val lat1 = Math.toRadians(center.lat)
        val lon1 = Math.toRadians(center.lon)
        val points = mutableListOf<GeoPoint>()

        for (index in 0 until segments) {
            val bearing = 2.0 * Math.PI * index / segments
            val lat2 = asin(
                sin(lat1) * cos(angularDistance) +
                    cos(lat1) * sin(angularDistance) * cos(bearing)
            )
            val lon2 = lon1 + atan2(
                sin(bearing) * sin(angularDistance) * cos(lat1),
                cos(angularDistance) - sin(lat1) * sin(lat2)
            )
            points += GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lon2))
        }

        closePolygon(points)
        return points
    }

    private fun closePolygon(points: MutableList<GeoPoint>) {
        if (points.isEmpty()) {
            return
        }

        val first = points.first()
        val last = points.last()
        if (first.latitude != last.latitude || first.longitude != last.longitude) {
            points += GeoPoint(first.latitude, first.longitude)
        }
    }

    private fun styleFor(restriction: GeoZoneRestriction): ZoneStyle {
        return when (restriction) {
            GeoZoneRestriction.PROHIBITED -> ZoneStyle(
                strokeColor = Color.RED,
                fillColor = Color.argb(55, 255, 0, 0)
            )
            GeoZoneRestriction.REQ_AUTHORISATION -> ZoneStyle(
                strokeColor = Color.rgb(255, 165, 0),
                fillColor = Color.argb(55, 255, 165, 0)
            )
            GeoZoneRestriction.CONDITIONAL -> ZoneStyle(
                strokeColor = Color.YELLOW,
                fillColor = Color.argb(55, 255, 255, 0)
            )
            GeoZoneRestriction.INFORMATION -> ZoneStyle(
                strokeColor = Color.BLUE,
                fillColor = Color.argb(55, 0, 102, 255)
            )
            GeoZoneRestriction.UNKNOWN -> ZoneStyle(
                strokeColor = Color.GRAY,
                fillColor = Color.argb(55, 128, 128, 128)
            )
        }
    }

    private fun Double.formatMeters(): String {
        return if (this % 1.0 == 0.0) {
            toInt().toString()
        } else {
            toString()
        }
    }

    private data class ZoneStyle(
        val strokeColor: Int,
        val fillColor: Int
    )
}
