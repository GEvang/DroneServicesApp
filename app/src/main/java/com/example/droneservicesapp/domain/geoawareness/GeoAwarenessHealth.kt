package com.example.droneservicesapp.domain.geoawareness

enum class GeoAwarenessHealthState {
    AVAILABLE,
    DEGRADED,
    STALE,
    UNAVAILABLE
}

data class GeoAwarenessHealth(
    val state: GeoAwarenessHealthState,
    val message: String,
    val canPlan: Boolean,
    val canUploadWithoutAcknowledgement: Boolean,
    val requiresAcknowledgementBeforeUpload: Boolean,
    val checkedAtMillis: Long
)
