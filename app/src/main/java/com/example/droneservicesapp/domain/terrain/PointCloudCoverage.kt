package com.example.droneservicesapp.domain.terrain

/** How the loaded point cloud relates to the complete planned flight. */
enum class PointCloudCoverage {
    CHECKING,
    NONE,
    PARTIAL,
    COMPLETE,

    ;

    companion object {
        fun classify(
            hasPointCloudOverlap: Boolean,
            allRequiredFlightPathsCovered: Boolean,
        ): PointCloudCoverage = when {
            hasPointCloudOverlap && allRequiredFlightPathsCovered -> COMPLETE
            hasPointCloudOverlap -> PARTIAL
            else -> NONE
        }
    }
}
