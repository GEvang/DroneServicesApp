package com.example.droneservicesapp.domain.geoawareness

object GeoAwarenessHealthEvaluator {

    const val DEFAULT_STALE_AFTER_MILLIS = 7L * 24L * 60L * 60L * 1000L

    fun evaluate(
        datasetInfo: GeoZoneDatasetInfo?,
        zones: List<GeoZone>,
        loadError: Throwable? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): GeoAwarenessHealth {
        if (loadError != null) {
            return GeoAwarenessHealth(
                state = GeoAwarenessHealthState.UNAVAILABLE,
                message = "Geo-awareness data failed to load: ${loadError.message ?: "unknown error"}",
                canPlan = false,
                canUploadWithoutAcknowledgement = false,
                requiresAcknowledgementBeforeUpload = true,
                checkedAtMillis = nowMillis
            )
        }

        if (datasetInfo == null) {
            return GeoAwarenessHealth(
                state = GeoAwarenessHealthState.UNAVAILABLE,
                message = "Geo-awareness data is unavailable.",
                canPlan = false,
                canUploadWithoutAcknowledgement = false,
                requiresAcknowledgementBeforeUpload = true,
                checkedAtMillis = nowMillis
            )
        }

        if (zones.isEmpty()) {
            return GeoAwarenessHealth(
                state = GeoAwarenessHealthState.UNAVAILABLE,
                message = "Geo-awareness dataset contains no loaded zones.",
                canPlan = false,
                canUploadWithoutAcknowledgement = false,
                requiresAcknowledgementBeforeUpload = true,
                checkedAtMillis = nowMillis
            )
        }

        if (datasetInfo.isDummy) {
            return GeoAwarenessHealth(
                state = GeoAwarenessHealthState.DUMMY_DATA,
                message = "Using development-only dummy geo-awareness data.",
                canPlan = true,
                canUploadWithoutAcknowledgement = false,
                requiresAcknowledgementBeforeUpload = true,
                checkedAtMillis = nowMillis
            )
        }

        if (!datasetInfo.isOfficial) {
            return GeoAwarenessHealth(
                state = GeoAwarenessHealthState.DEGRADED,
                message = "Dataset is not marked official.",
                canPlan = true,
                canUploadWithoutAcknowledgement = false,
                requiresAcknowledgementBeforeUpload = true,
                checkedAtMillis = nowMillis
            )
        }

        if (nowMillis - datasetInfo.loadedAtMillis > DEFAULT_STALE_AFTER_MILLIS) {
            return GeoAwarenessHealth(
                state = GeoAwarenessHealthState.STALE,
                message = "Geo-awareness dataset may be stale.",
                canPlan = true,
                canUploadWithoutAcknowledgement = false,
                requiresAcknowledgementBeforeUpload = true,
                checkedAtMillis = nowMillis
            )
        }

        return GeoAwarenessHealth(
            state = GeoAwarenessHealthState.AVAILABLE,
            message = "Geo-awareness dataset is available.",
            canPlan = true,
            canUploadWithoutAcknowledgement = true,
            requiresAcknowledgementBeforeUpload = false,
            checkedAtMillis = nowMillis
        )
    }
}
