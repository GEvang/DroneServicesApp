package com.example.droneservicesapp.ui.preview

import com.example.droneservicesapp.data.pointcloud.PointCloudCoordinateFrame
import com.example.droneservicesapp.data.pointcloud.PointCloudData
import com.example.droneservicesapp.domain.model.PlanningWorkflow
import com.example.droneservicesapp.ui.pointcloud.PointCloudMissionOverlay
import com.google.android.gms.maps.model.LatLng
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class PointCloudMissionOverlayBuilder {
    data class Input(
        val pointCloud: PointCloudData?,
        val areaPoints: List<LatLng>,
        val areaZValues: List<Float>?,
        val surveyPoints: List<LatLng>,
        val surveyZValues: List<Float>?,
        val routePoints: List<LatLng>,
        val routeZValues: List<Float>?,
        val droneLocation: LatLng?,
        val droneHeadingDegrees: Double,
        val selectedWorkflow: PlanningWorkflow,
        val selectedWaypointIndex: Int?,
    )

    fun build(input: Input): PointCloudMissionOverlay? {
        val originPoints = input.areaPoints + input.surveyPoints + input.routePoints + listOfNotNull(input.droneLocation)
        val frame = input.pointCloud?.coordinateFrame ?: originPoints.takeIf { it.isNotEmpty() }?.let { points ->
            PointCloudCoordinateFrame(points.map { it.latitude }.average(), points.map { it.longitude }.average())
        } ?: return null
        val locals = originPoints.map { frame.latLonToLocal(it.latitude, it.longitude) }
        val horizontalSpan = if (locals.isEmpty()) 0f else max(
            locals.maxOf { it.first } - locals.minOf { it.first },
            locals.maxOf { it.second } - locals.minOf { it.second },
        ).toFloat()
        val displaySpan = max(input.pointCloud?.bounds?.maxSpan ?: 0f, horizontalSpan).coerceAtLeast(10f)
        val overlayZ = (input.pointCloud?.let { it.bounds.maxZ - it.bounds.centerZ } ?: 0f) +
            max(displaySpan * 0.02f, MIN_Z_OFFSET)
        val lines = Geometry()
        val points = Geometry()
        val selected = Geometry()
        val convert: (LatLng) -> Pair<Double, Double> = { frame.latLonToLocal(it.latitude, it.longitude) }

        closedStrip(input.areaPoints, 0f, input.areaZValues, POLYGON_COLOR, lines, convert)
        openStrip(input.surveyPoints, overlayZ + Z_STEP, input.surveyZValues, SURVEY_COLOR, lines, convert)
        input.surveyPoints.forEachIndexed { index, point ->
            vertex(point, input.surveyZValues?.getOrNull(index) ?: overlayZ + Z_STEP * 1.5f,
                if (input.selectedWorkflow == PlanningWorkflow.AREA && input.selectedWaypointIndex == index) SELECTED_COLOR else POINT_COLOR,
                points, convert)
        }
        arrows(input.surveyPoints, overlayZ + Z_STEP * 1.7f, input.surveyZValues,
            max(displaySpan * 0.012f, MIN_ARROW_SIZE), lines, convert)

        openStrip(input.routePoints, overlayZ + Z_STEP * 2f, input.routeZValues, ROUTE_COLOR, lines, convert)
        if (input.routeZValues != null) input.routePoints.forEachIndexed { index, point ->
            vertex(point, input.routeZValues.getOrNull(index) ?: overlayZ + Z_STEP * 2.2f,
                if (input.selectedWorkflow == PlanningWorkflow.POINTS && input.selectedWaypointIndex == index) SELECTED_COLOR else POINT_COLOR,
                points, convert)
        }
        arrows(input.routePoints, overlayZ + Z_STEP * 2.3f, input.routeZValues,
            max(displaySpan * 0.012f, MIN_ARROW_SIZE), lines, convert)

        input.selectedWaypointIndex?.let { index ->
            val source = if (input.selectedWorkflow == PlanningWorkflow.AREA) input.surveyPoints else input.routePoints
            val zValues = if (input.selectedWorkflow == PlanningWorkflow.AREA) input.surveyZValues else input.routeZValues
            source.getOrNull(index)?.let { vertex(it, zValues?.getOrNull(index) ?: overlayZ + Z_STEP * 1.5f, SELECTED_COLOR, selected, convert) }
        }

        var droneVertices = FloatArray(0)
        var droneColors = FloatArray(0)
        input.droneLocation?.let { drone ->
            val (x, y) = convert(drone)
            val z = overlayZ + Z_STEP * 3f
            droneGlyph(x, y, z, input.droneHeadingDegrees, max(displaySpan * 0.022f, MIN_DRONE_SIZE), lines)
            droneVertices = floatArrayOf(x.toFloat(), y.toFloat(), z)
            droneColors = DRONE_COLOR.copyOf()
        }
        if (lines.vertices.isEmpty() && points.vertices.isEmpty() && selected.vertices.isEmpty() && droneVertices.isEmpty()) return null
        return PointCloudMissionOverlay(
            vertices = lines.vertices.toFloatArray(), colors = lines.colors.toFloatArray(), lineVertexCount = lines.count,
            pointVertices = points.vertices.toFloatArray(), pointColors = points.colors.toFloatArray(), pointVertexCount = points.count,
            selectedPointVertices = selected.vertices.toFloatArray(), selectedPointColors = selected.colors.toFloatArray(), selectedPointVertexCount = selected.count,
            dronePointVertices = droneVertices, dronePointColors = droneColors, dronePointVertexCount = droneVertices.size / 3,
        )
    }

    private class Geometry(val vertices: MutableList<Float> = arrayListOf(), val colors: MutableList<Float> = arrayListOf()) {
        val count get() = vertices.size / 3
    }
    private fun closedStrip(source: List<LatLng>, z: Float, zs: List<Float>?, color: FloatArray, out: Geometry, convert: (LatLng) -> Pair<Double, Double>) {
        if (source.size >= 3) openStrip(source + source.first(), z, zs?.let { it + it.first() }, color, out, convert)
    }
    private fun openStrip(source: List<LatLng>, z: Float, zs: List<Float>?, color: FloatArray, out: Geometry, convert: (LatLng) -> Pair<Double, Double>) {
        source.zipWithNext().forEachIndexed { i, (from, to) ->
            vertex(from, zs?.getOrNull(i) ?: z, color, out, convert)
            vertex(to, zs?.getOrNull(i + 1) ?: z, color, out, convert)
        }
    }
    private fun vertex(point: LatLng, z: Float, color: FloatArray, out: Geometry, convert: (LatLng) -> Pair<Double, Double>) {
        val (x, y) = convert(point); localVertex(x, y, z, color, out)
    }
    private fun localVertex(x: Double, y: Double, z: Float, color: FloatArray, out: Geometry) {
        out.vertices.addAll(listOf(x.toFloat(), y.toFloat(), z)); out.colors.addAll(color.toList())
    }
    private fun arrows(source: List<LatLng>, z: Float, zs: List<Float>?, size: Float, out: Geometry, convert: (LatLng) -> Pair<Double, Double>) {
        buildSurveyDirectionSegments(source, MAX_ARROWS).forEach { segment ->
            val (fx, fy) = convert(segment.from); val (tx, ty) = convert(segment.to)
            val dx = tx - fx; val dy = ty - fy; val length = sqrt(dx * dx + dy * dy)
            if (length <= 0.001) return@forEach
            val ux = dx / length; val uy = dy / length; val arrowLength = min(size.toDouble(), length * 0.35)
            val width = arrowLength * 0.55; val mx = (fx + tx) / 2; val my = (fy + ty) / 2
            val fromIndex = source.indexOf(segment.from); val toIndex = source.indexOf(segment.to)
            val az = ((zs?.getOrNull(fromIndex) ?: z) + (zs?.getOrNull(toIndex) ?: z)) / 2f
            val tipX = mx + ux * arrowLength * .5; val tipY = my + uy * arrowLength * .5
            val baseX = mx - ux * arrowLength * .5; val baseY = my - uy * arrowLength * .5
            val leftX = baseX - uy * width * .5; val leftY = baseY + ux * width * .5
            val rightX = baseX + uy * width * .5; val rightY = baseY - ux * width * .5
            localVertex(tipX, tipY, az, SURVEY_COLOR, out); localVertex(leftX, leftY, az, SURVEY_COLOR, out)
            localVertex(tipX, tipY, az, SURVEY_COLOR, out); localVertex(rightX, rightY, az, SURVEY_COLOR, out)
        }
    }
    private fun droneGlyph(x: Double, y: Double, z: Float, headingDegrees: Double, size: Float, out: Geometry) {
        val heading = Math.toRadians(headingDegrees); val fx = sin(heading); val fy = cos(heading); val rx = fy; val ry = -fx; val arm = size * .5
        fun p(forward: Double, right: Double) = (x + fx * forward + rx * right) to (y + fy * forward + ry * right)
        fun line(a: Pair<Double, Double>, b: Pair<Double, Double>) { localVertex(a.first, a.second, z, DRONE_COLOR, out); localVertex(b.first, b.second, z, DRONE_COLOR, out) }
        line(p(-arm, -arm), p(arm, arm)); line(p(-arm, arm), p(arm, -arm)); line(p(0.0, 0.0), p(size.toDouble(), 0.0))
        line(p(size.toDouble(), 0.0), p(arm, -arm * .4)); line(p(size.toDouble(), 0.0), p(arm, arm * .4))
    }

    companion object {
        private const val MIN_Z_OFFSET = .5f; private const val Z_STEP = .2f; private const val MIN_ARROW_SIZE = 1f
        private const val MIN_DRONE_SIZE = 3f; private const val MAX_ARROWS = 160
        private val POLYGON_COLOR = floatArrayOf(.31f, .78f, 1f); private val SURVEY_COLOR = floatArrayOf(.16f, .90f, .85f)
        private val POINT_COLOR = floatArrayOf(.89f, .65f, .25f); private val SELECTED_COLOR = floatArrayOf(1f, 1f, 1f)
        private val ROUTE_COLOR = floatArrayOf(.3f, 1f, .35f); private val DRONE_COLOR = floatArrayOf(1f, .84f, .16f)
    }
}
