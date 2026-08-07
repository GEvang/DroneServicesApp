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

data class PointCloudData(
    val positions: FloatArray,
    val colors: FloatArray,
    val totalPointCount: Int,
    val displayedPointCount: Int,
    val bounds: PointCloudBounds,
    val hasRgb: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PointCloudData) return false
        return positions.contentEquals(other.positions) &&
            colors.contentEquals(other.colors) &&
            totalPointCount == other.totalPointCount &&
            displayedPointCount == other.displayedPointCount &&
            bounds == other.bounds &&
            hasRgb == other.hasRgb
    }

    override fun hashCode(): Int {
        var result = positions.contentHashCode()
        result = 31 * result + colors.contentHashCode()
        result = 31 * result + totalPointCount
        result = 31 * result + displayedPointCount
        result = 31 * result + bounds.hashCode()
        result = 31 * result + hasRgb.hashCode()
        return result
    }
}
