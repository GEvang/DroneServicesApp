package com.example.droneservicesapp.ui.home.geoawareness

import android.content.Context
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.toColorInt
import com.example.droneservicesapp.R
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessGeometryUtils
import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.geoawareness.GeoZoneApplicability
import com.example.droneservicesapp.domain.geoawareness.GeoZoneApplicabilityEvaluator
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
    private var renderedZones: List<GeoZone> = emptyList()
    private var zoneDetailsEnabled: Boolean = true

    fun setZoneDetailsEnabled(enabled: Boolean) {
        zoneDetailsEnabled = enabled
    }

    fun renderZones(zones: List<GeoZone>) {
        clear()
        renderedZones = zones

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
        renderedZones = emptyList()
        mapView.invalidate()
    }

    private fun renderCircle(zone: GeoZone, geometry: GeoZoneGeometry.Circle) {
        val points = createCirclePoints(geometry.center, geometry.radiusMeters)
        if (points.size < 4) {
            return
        }

        addZonePolygon(zone, points)
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

        addZonePolygon(zone, points)
    }

    private fun addZonePolygon(
        zone: GeoZone,
        points: List<GeoPoint>
    ) {
        val activeNow = GeoZoneApplicabilityEvaluator.isActiveNow(zone)
        val style = styleFor(zone.restriction, activeNow)
        val polygon = Polygon(mapView).apply {
            // Zone details are handled by the app panel, never by osmdroid's stock bubble.
            infoWindow = null
            this.points = points
            outlinePaint.color = style.strokeColor
            outlinePaint.strokeWidth = if (activeNow) 3f else 4f
            outlinePaint.alpha = style.strokeAlpha
            outlinePaint.pathEffect = style.pathEffect
            fillPaint.color = style.fillColor
            title = zone.name
            subDescription = zone.message
            setOnClickListener { _, _, eventPosition ->
                if (!zoneDetailsEnabled) {
                    return@setOnClickListener false
                }
                handleZoneTap(
                    tappedPoint = eventPosition?.let { LatLon(lat = it.latitude, lon = it.longitude) }
                )
            }
        }

        mapView.overlays.add(polygon)
        overlays += polygon
    }

    private fun handleZoneTap(tappedPoint: LatLon?): Boolean {
        val point = tappedPoint ?: return false
        val matches = findZonesAt(point)
        return when {
            matches.isEmpty() -> false
            matches.size == 1 -> {
                val match = matches.first()
                showZoneDetails(match.zone, match.geometry)
                true
            }
            else -> {
                showMultipleZoneMatches(point, matches)
                true
            }
        }
    }

    private fun findZonesAt(point: LatLon): List<ZoneTapMatch> {
        return renderedZones.mapNotNull { zone ->
            val matchingGeometries = zone.geometries.filter { geometry ->
                GeoAwarenessGeometryUtils.pointInZone(
                    point = point,
                    geometry = geometry,
                    missionAltitudeMeters = null
                )
            }
            val selectedGeometry = matchingGeometries.minByOrNull(::approximateGeometryAreaSquareMeters)
                ?: return@mapNotNull null
            ZoneTapMatch(
                zone = zone,
                geometry = selectedGeometry,
                approximateAreaSquareMeters = approximateGeometryAreaSquareMeters(selectedGeometry)
            )
        }.sortedWith(
            compareBy<ZoneTapMatch>(
                { restrictionPriority(it.zone.restriction) },
                { it.approximateAreaSquareMeters },
                { it.zone.name.lowercase() }
            )
        )
    }

    private fun showMultipleZoneMatches(
        tappedPoint: LatLon,
        matches: List<ZoneTapMatch>
    ) {
        val density = context.resources.displayMetrics.density
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (20 * density).toInt(),
                (8 * density).toInt(),
                (20 * density).toInt(),
                0
            )
        }
        val messageView = TextView(context).apply {
            text = "Multiple overlapping geo-zones apply at this location."
            setTextColor("#21304A".toColorInt())
            textSize = 14f
        }
        val coordinateView = TextView(context).apply {
            text = "Latitude: ${tappedPoint.lat}, Longitude: ${tappedPoint.lon}"
            setTextColor("#5C6F8F".toColorInt())
            textSize = 12f
            setPadding(0, (8 * density).toInt(), 0, (12 * density).toInt())
        }
        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (360 * density).toInt()
            )
        }
        val listContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(listContainer)
        root.addView(messageView)
        root.addView(coordinateView)
        root.addView(scrollView)

        lateinit var dialog: AlertDialog
        matches.forEachIndexed { index, match ->
            if (index > 0) {
                listContainer.addView(View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (1 * density).toInt()
                    ).apply {
                        topMargin = (10 * density).toInt()
                        bottomMargin = (10 * density).toInt()
                    }
                    setBackgroundColor("#D8E2F0".toColorInt())
                })
            }
            listContainer.addView(createZoneMatchRow(match) {
                dialog.dismiss()
                showZoneDetails(match.zone, match.geometry)
            })
        }

        dialog = AlertDialog.Builder(context, R.style.Theme_DroneServicesApp_AlertDialog)
            .setTitle("Geo-zones at this location")
            .setView(root)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor("#212121".toColorInt())
    }

    private fun createZoneMatchRow(
        match: ZoneTapMatch,
        onClick: () -> Unit
    ): View {
        val density = context.resources.displayMetrics.density
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            setPadding(
                (12 * density).toInt(),
                (12 * density).toInt(),
                (12 * density).toInt(),
                (12 * density).toInt()
            )
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16f * density
                setColor("#EEF3FB".toColorInt())
                setStroke((1 * density).toInt(), "#CCD8EA".toColorInt())
            }
            setOnClickListener { onClick() }
            addView(TextView(context).apply {
                text = match.zone.name
                setTextColor("#17263E".toColorInt())
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
            })
        addView(TextView(context).apply {
                text = "Restriction: ${match.zone.restriction} | ${formatApplicabilityStatus(match.zone)}"
                setTextColor("#2C4063".toColorInt())
                textSize = 13f
                setPadding(0, (6 * density).toInt(), 0, 0)
            })
            addView(TextView(context).apply {
                text = "Altitude: ${formatDialogAltitude(match.geometry)}"
                setTextColor("#2C4063".toColorInt())
                textSize = 13f
                setPadding(0, (4 * density).toInt(), 0, 0)
            })
            match.zone.message?.takeIf { it.isNotBlank() }?.let { message ->
                addView(TextView(context).apply {
                    text = "Message: ${truncatePreview(message, 140)}"
                    setTextColor("#5C6F8F".toColorInt())
                    textSize = 12f
                    maxLines = 2
                    setPadding(0, (6 * density).toInt(), 0, 0)
                })
            }
        }
    }

    private fun showZoneDetails(zone: GeoZone, geometry: GeoZoneGeometry) {
        val message = buildString {
            appendLine("Restriction: ${zone.restriction}")
            appendLine("Applicability: ${formatApplicabilityStatus(zone)}")
            formatApplicabilityWindow(zone)?.let { appendLine("Window: $it") }
            appendLine("Message: ${zone.message ?: "No message"}")
            appendLine("Authority: ${zone.authorities.firstOrNull()?.name ?: "N/A"}")
            append("Altitude: ${formatAltitude(geometry)}")
        }

        val dialog = AlertDialog.Builder(context, R.style.Theme_DroneServicesApp_AlertDialog)
            .setTitle(zone.name)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor("#212121".toColorInt())
    }

    private fun restrictionPriority(restriction: GeoZoneRestriction): Int {
        return when (restriction) {
            GeoZoneRestriction.PROHIBITED -> 0
            GeoZoneRestriction.REQ_AUTHORISATION -> 1
            GeoZoneRestriction.CONDITIONAL -> 2
            GeoZoneRestriction.INFORMATION -> 3
            GeoZoneRestriction.UNKNOWN -> 4
        }
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

    private fun formatDialogAltitude(geometry: GeoZoneGeometry): String {
        val lower = geometry.lowerLimitMeters?.formatMeters()
        val upper = geometry.upperLimitMeters?.formatMeters()
        return when {
            lower != null && upper != null -> "$lower-$upper m"
            lower != null -> "$lower m and above"
            upper != null -> "Up to $upper m"
            else -> "Unknown"
        }
    }

    private fun truncatePreview(text: String, maxLength: Int): String {
        return if (text.length <= maxLength) {
            text
        } else {
            text.take(maxLength).trimEnd() + "..."
        }
    }

    private fun approximateGeometryAreaSquareMeters(geometry: GeoZoneGeometry): Double {
        return when (geometry) {
            is GeoZoneGeometry.Circle -> Math.PI * geometry.radiusMeters * geometry.radiusMeters
            is GeoZoneGeometry.Polygon -> approximatePolygonBoundingBoxAreaSquareMeters(geometry)
        }
    }

    private fun approximatePolygonBoundingBoxAreaSquareMeters(geometry: GeoZoneGeometry.Polygon): Double {
        val outerRing = geometry.rings.firstOrNull().orEmpty()
        if (outerRing.size < 3) {
            return Double.MAX_VALUE
        }
        val minLat = outerRing.minOf { it.lat }
        val maxLat = outerRing.maxOf { it.lat }
        val minLon = outerRing.minOf { it.lon }
        val maxLon = outerRing.maxOf { it.lon }
        val centerLat = (minLat + maxLat) / 2.0
        val centerLon = (minLon + maxLon) / 2.0
        val widthMeters = GeoAwarenessGeometryUtils.distanceMeters(
            LatLon(centerLat, minLon),
            LatLon(centerLat, maxLon)
        )
        val heightMeters = GeoAwarenessGeometryUtils.distanceMeters(
            LatLon(minLat, centerLon),
            LatLon(maxLat, centerLon)
        )
        return widthMeters * heightMeters
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

    private fun styleFor(restriction: GeoZoneRestriction, activeNow: Boolean): ZoneStyle {
        if (!activeNow) {
            return ZoneStyle(
                strokeColor = Color.rgb(31, 41, 55),
                fillColor = Color.argb(58, 96, 108, 128),
                strokeAlpha = 235,
                pathEffect = DashPathEffect(floatArrayOf(18f, 10f), 0f)
            )
        }
        return when (restriction) {
            GeoZoneRestriction.PROHIBITED -> ZoneStyle(
                strokeColor = Color.RED,
                fillColor = Color.argb(55, 255, 0, 0),
                strokeAlpha = 255,
                pathEffect = null
            )
            GeoZoneRestriction.REQ_AUTHORISATION -> ZoneStyle(
                strokeColor = Color.rgb(255, 165, 0),
                fillColor = Color.argb(55, 255, 165, 0),
                strokeAlpha = 255,
                pathEffect = null
            )
            GeoZoneRestriction.CONDITIONAL -> ZoneStyle(
                strokeColor = Color.YELLOW,
                fillColor = Color.argb(55, 255, 255, 0),
                strokeAlpha = 255,
                pathEffect = null
            )
            GeoZoneRestriction.INFORMATION -> ZoneStyle(
                strokeColor = Color.BLUE,
                fillColor = Color.argb(55, 0, 102, 255),
                strokeAlpha = 255,
                pathEffect = null
            )
            GeoZoneRestriction.UNKNOWN -> ZoneStyle(
                strokeColor = Color.GRAY,
                fillColor = Color.argb(55, 128, 128, 128),
                strokeAlpha = 255,
                pathEffect = null
            )
        }
    }

    private fun formatApplicabilityStatus(zone: GeoZone): String {
        return if (GeoZoneApplicabilityEvaluator.isActiveNow(zone)) {
            "Active now"
        } else {
            "Inactive by date"
        }
    }

    private fun formatApplicabilityWindow(zone: GeoZone): String? {
        if (zone.applicability.isEmpty()) {
            return "No applicability window specified; treated as active"
        }
        val permanentCount = zone.applicability.count { it.permanent }
        val start = zone.applicability.mapNotNull(GeoZoneApplicability::startDateTime).minOrNull()
        val end = zone.applicability.mapNotNull(GeoZoneApplicability::endDateTime).maxOrNull()
        return buildString {
            if (permanentCount > 0) {
                append("Permanent")
            } else {
                append("Temporary")
            }
            if (!start.isNullOrBlank() || !end.isNullOrBlank()) {
                append(" (")
                append(start ?: "open start")
                append(" to ")
                append(end ?: "open end")
                append(")")
            }
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
        val fillColor: Int,
        val strokeAlpha: Int,
        val pathEffect: DashPathEffect?
    )

    private data class ZoneTapMatch(
        val zone: GeoZone,
        val geometry: GeoZoneGeometry,
        val approximateAreaSquareMeters: Double
    )
}
