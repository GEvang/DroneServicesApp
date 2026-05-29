package com.example.droneservicesapp.domain.geoawareness

data class GeoAwarenessResult(
    val conflicts: List<GeoZoneConflict>,
    val highestRestriction: GeoZoneRestriction,
    val canUpload: Boolean,
    val requiresAcknowledgement: Boolean
) {
    val hasConflicts: Boolean
        get() = conflicts.isNotEmpty()

    companion object {
        fun clear(): GeoAwarenessResult {
            return GeoAwarenessResult(
                conflicts = emptyList(),
                highestRestriction = GeoZoneRestriction.UNKNOWN,
                canUpload = true,
                requiresAcknowledgement = false
            )
        }
    }
}
