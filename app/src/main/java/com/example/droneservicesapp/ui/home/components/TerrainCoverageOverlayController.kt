package com.example.droneservicesapp.ui.home.components

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import com.example.droneservicesapp.R
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.terrain.TerrainCoveragePlanner
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

class TerrainCoverageOverlayController(
    context: Context,
    private val mapView: MapView,
) {
    private val purple = ContextCompat.getColor(context, R.color.terrain_purple)
    private val overlays = mutableListOf<Overlay>()
    private var renderedCenter: LatLon? = null
    private var renderedRadiusMeters: Double? = null

    fun render(center: LatLon, radiusMeters: Double) {
        if (renderedCenter == center && renderedRadiusMeters == radiusMeters) return
        clear()
        if (radiusMeters <= 0.0) return

        val boundary = Polygon(mapView).apply {
            infoWindow = null
            points = circlePoints(center, radiusMeters)
            outlinePaint.color = purple
            outlinePaint.strokeWidth = 5f
            fillPaint.color = Color.argb(28, Color.red(purple), Color.green(purple), Color.blue(purple))
            title = "Terrain coverage · ${(radiusMeters / 1_000.0).toInt()} km radius"
        }
        addOverlay(boundary)

        hatchSegments(center, radiusMeters).forEach { segment ->
            addOverlay(
                Polyline(mapView).apply {
                    infoWindow = null
                    setPoints(segment.map { GeoPoint(it.lat, it.lon) })
                    outlinePaint.color = Color.argb(
                        105,
                        Color.red(purple),
                        Color.green(purple),
                        Color.blue(purple),
                    )
                    outlinePaint.strokeWidth = 3f
                }
            )
        }
        renderedCenter = center
        renderedRadiusMeters = radiusMeters
        mapView.invalidate()
    }

    fun clear() {
        if (overlays.isNotEmpty()) mapView.overlays.removeAll(overlays.toSet())
        overlays.clear()
        renderedCenter = null
        renderedRadiusMeters = null
        mapView.invalidate()
    }

    private fun addOverlay(overlay: Overlay) {
        mapView.overlays.add(overlay)
        overlays += overlay
    }

    private fun circlePoints(center: LatLon, radiusMeters: Double): List<GeoPoint> {
        return (0..CIRCLE_SEGMENTS).map { index ->
            val angle = 2.0 * PI * index / CIRCLE_SEGMENTS
            val point = TerrainCoveragePlanner.offset(
                center,
                northMeters = cos(angle) * radiusMeters,
                eastMeters = sin(angle) * radiusMeters,
            )
            GeoPoint(point.lat, point.lon)
        }
    }

    private fun hatchSegments(center: LatLon, radiusMeters: Double): List<List<LatLon>> {
        val diagonal = sqrt(2.0)
        val segments = mutableListOf<List<LatLon>>()
        var perpendicular = -radiusMeters + HATCH_SPACING_METERS
        while (perpendicular < radiusMeters) {
            val halfLength = sqrt(radiusMeters * radiusMeters - perpendicular * perpendicular)
            fun endpoint(along: Double): LatLon {
                val east = (along + perpendicular) / diagonal
                val north = (along - perpendicular) / diagonal
                return TerrainCoveragePlanner.offset(center, north, east)
            }
            segments += listOf(endpoint(-halfLength), endpoint(halfLength))
            perpendicular += HATCH_SPACING_METERS
        }
        return segments
    }

    companion object {
        private const val CIRCLE_SEGMENTS = 96
        private const val HATCH_SPACING_METERS = 140.0
    }
}
