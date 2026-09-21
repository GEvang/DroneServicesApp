package com.example.droneservicesapp.domain.terrain

/** How the loaded point cloud relates to the generated spraying path. */
enum class PointCloudCoverage {
    CHECKING,
    NONE,
    PARTIAL,
    COMPLETE,

    ;

    companion object {
        fun classify(
            hasPointCloudOverlap: Boolean,
            allSprayingPathPointsCovered: Boolean,
        ): PointCloudCoverage = when {
            hasPointCloudOverlap && allSprayingPathPointsCovered -> COMPLETE
            hasPointCloudOverlap -> PARTIAL
            else -> NONE
        }
    }
}
