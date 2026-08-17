package com.example.droneservicesapp.ui.preview

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

data class SurveyDirectionSegment(
    val from: LatLng,
    val to: LatLng
)

fun buildSurveyDirectionSegments(
    path: List<LatLng>,
    maxSegments: Int = 80
): List<SurveyDirectionSegment> {
    if (path.size < 2) return emptyList()

    val grouped = mutableListOf<SurveyDirectionSegment>()
    var groupStart: LatLng? = null
    var groupEnd: LatLng? = null
    var groupHeading: Double? = null

    fun closeGroup() {
        val start = groupStart
        val end = groupEnd
        if (start != null && end != null && SphericalUtil.computeDistanceBetween(start, end) >= MIN_ARROW_SEGMENT_METERS) {
            grouped += SurveyDirectionSegment(start, end)
        }
        groupStart = null
        groupEnd = null
        groupHeading = null
    }

    path.zipWithNext().forEach { (from, to) ->
        if (SphericalUtil.computeDistanceBetween(from, to) < MIN_ARROW_SEGMENT_METERS) return@forEach
        val heading = SphericalUtil.computeHeading(from, to)
        val currentHeading = groupHeading
        if (currentHeading == null || angularDifferenceDegrees(currentHeading, heading) <= MAX_COLLINEAR_HEADING_DELTA_DEGREES) {
            if (groupStart == null) groupStart = from
            groupEnd = to
            groupHeading = currentHeading ?: heading
        } else {
            closeGroup()
            groupStart = from
            groupEnd = to
            groupHeading = heading
        }
    }
    closeGroup()

    if (grouped.size <= maxSegments) return grouped
    val step = ceil(grouped.size / maxSegments.toDouble()).roundToInt().coerceAtLeast(1)
    return grouped.filterIndexed { index, _ -> index % step == 0 }
}

private fun angularDifferenceDegrees(first: Double, second: Double): Double {
    var delta = (first - second) % 360.0
    if (delta > 180.0) delta -= 360.0
    if (delta < -180.0) delta += 360.0
    return abs(delta)
}

private const val MIN_ARROW_SEGMENT_METERS = 0.5
private const val MAX_COLLINEAR_HEADING_DELTA_DEGREES = 18.0
