package com.example.droneservicesapp.ui.preview

import android.graphics.Color
import com.example.droneservicesapp.domain.terrain.PointCloudTerrainModel
import com.example.droneservicesapp.domain.terrain.TerrainWaypoint
import com.google.android.gms.maps.model.LatLng

/** Maps terrain-planning data into renderer-ready point-cloud heights and colors. */
object PointCloudTerrainStyleMapper {
    fun groundZValues(
        points: List<LatLng>,
        terrainModel: PointCloudTerrainModel?,
    ): List<Float>? {
        val model = terrainModel ?: return null
        val frame = model.coordinateFrame ?: return null
        if (points.isEmpty()) return null
        return points.map { point ->
            val (x, y) = frame.latLonToLocal(point.latitude, point.longitude)
            (model.terrainHeightAt(x, y) + 0.15).toFloat()
        }
    }

    fun surveyZValues(
        surveyPoints: List<LatLng>,
        terrainWaypoints: List<TerrainWaypoint>,
    ): List<Float>? = terrainWaypoints
        .takeIf { it.size == surveyPoints.size && it.isNotEmpty() }
        ?.map { it.displayAltitudeMeters.toFloat() }

    fun surveySegmentColors(
        enabled: Boolean,
        surveyPoints: List<LatLng>,
        terrainWaypoints: List<TerrainWaypoint>,
    ): List<Int>? {
        if (!enabled || surveyPoints.size < 2) return null
        val heights = terrainWaypoints
            .takeIf { it.size == surveyPoints.size && it.isNotEmpty() }
            ?.map { it.missionAltitudeMeters }
            ?: return null
        val minHeight = heights.minOrNull() ?: return null
        val maxHeight = heights.maxOrNull() ?: return null
        val range = (maxHeight - minHeight).coerceAtLeast(0.1)
        return heights.zipWithNext().map { (from, to) ->
            val normalized = ((((from + to) / 2.0) - minHeight) / range)
                .coerceIn(0.0, 1.0)
                .toFloat()
            Color.HSVToColor(floatArrayOf(220f - 180f * normalized, 0.9f, 1.0f))
        }
    }
}
