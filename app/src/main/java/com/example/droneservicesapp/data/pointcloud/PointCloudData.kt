package com.example.droneservicesapp.data.pointcloud

data class PointCloudBounds(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float,
    val minZ: Float,
    val maxZ: Float
) {
    val centerX: Float get() = (minX + maxX) / 2f
    val centerY: Float get() = (minY + maxY) / 2f
    val centerZ: Float get() = (minZ + maxZ) / 2f
    val spanX: Float get() = maxX - minX
    val spanY: Float get() = maxY - minY
    val spanZ: Float get() = maxZ - minZ
    val maxSpan: Float get() = maxOf(spanX, spanY, spanZ)
}

data class PointCloudCoordinateFrame(
    val originLat: Double,
    val originLon: Double,
    val originAltMeters: Double = 0.0
) {
    fun localToLatLon(xMeters: Double, yMeters: Double): Pair<Double, Double> {
        val cosLat = kotlin.math.cos(Math.toRadians(originLat)).coerceAtLeast(1e-6)
        val lat = originLat + yMeters / METERS_PER_DEGREE
        val lon = originLon + xMeters / (METERS_PER_DEGREE * cosLat)
        return lat to lon
    }

    fun latLonToLocal(lat: Double, lon: Double): Pair<Double, Double> {
        val cosLat = kotlin.math.cos(Math.toRadians(originLat)).coerceAtLeast(1e-6)
        val x = (lon - originLon) * METERS_PER_DEGREE * cosLat
        val y = (lat - originLat) * METERS_PER_DEGREE
        return x to y
    }

    companion object {
        private const val METERS_PER_DEGREE = 111_320.0
    }
}

data class PointCloudData(
    val positions: FloatArray,
    val colors: FloatArray,
    val totalPointCount: Int,
    val displayedPointCount: Int,
    val bounds: PointCloudBounds,
    val hasRgb: Boolean,
    val coordinateFrame: PointCloudCoordinateFrame? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PointCloudData) return false
        return positions.contentEquals(other.positions) &&
            colors.contentEquals(other.colors) &&
            totalPointCount == other.totalPointCount &&
            displayedPointCount == other.displayedPointCount &&
            bounds == other.bounds &&
            hasRgb == other.hasRgb &&
            coordinateFrame == other.coordinateFrame
    }

    override fun hashCode(): Int {
        var result = positions.contentHashCode()
        result = 31 * result + colors.contentHashCode()
        result = 31 * result + totalPointCount
        result = 31 * result + displayedPointCount
        result = 31 * result + bounds.hashCode()
        result = 31 * result + hasRgb.hashCode()
        result = 31 * result + (coordinateFrame?.hashCode() ?: 0)
        return result
    }
}
