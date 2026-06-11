package com.example.droneservicesapp.domain.geoawareness

import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneValidationResult

object GeoAwarenessHealthEvaluator {

    const val DEFAULT_STALE_AFTER_MILLIS = 7L * 24L * 60L * 60L * 1000L

    fun evaluate(
        datasetInfo: GeoZoneDatasetInfo?,
        zones: List<GeoZone>,
        datasetRecords: List<GeoZoneDatasetRecord> = emptyList(),
        validationResult: GeoZoneValidationResult? = null,
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
                message = "Import a geo-zone JSON file to enable geo-awareness.",
                canPlan = false,
                canUploadWithoutAcknowledgement = false,
                requiresAcknowledgementBeforeUpload = true,
                checkedAtMillis = nowMillis
            )
        }

        if (zones.isEmpty()) {
            return GeoAwarenessHealth(
                state = GeoAwarenessHealthState.UNAVAILABLE,
                message = "Import a geo-zone JSON file to enable geo-awareness.",
                canPlan = false,
                canUploadWithoutAcknowledgement = false,
                requiresAcknowledgementBeforeUpload = true,
                checkedAtMillis = nowMillis
            )
        }

        if (validationResult?.hasErrors == true) {
            return GeoAwarenessHealth(
                state = if (zones.isEmpty()) GeoAwarenessHealthState.UNAVAILABLE else GeoAwarenessHealthState.DEGRADED,
                message = "Geo-awareness dataset validation failed.",
                canPlan = zones.isNotEmpty(),
                canUploadWithoutAcknowledgement = false,
                requiresAcknowledgementBeforeUpload = true,
                checkedAtMillis = nowMillis
            )
        }

        val staleRecords = datasetRecords.filter { !it.datasetInfo.isDummy && it.isStale }
        if (staleRecords.isNotEmpty()) {
            val baseMessage = if (staleRecords.size == 1) {
                "Geo-awareness dataset may be stale."
            } else {
                "One or more geo-awareness datasets may be stale."
            }
            return GeoAwarenessHealth(
                state = GeoAwarenessHealthState.STALE,
                message = baseMessage,
                canPlan = true,
                canUploadWithoutAcknowledgement = false,
                requiresAcknowledgementBeforeUpload = true,
                checkedAtMillis = nowMillis
            )
        }

        if (validationResult?.issues?.any { issue ->
                issue.severity == com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneValidationSeverity.WARNING &&
                    issue.code != "DATASET_IS_DUMMY"
            } == true
        ) {
            return GeoAwarenessHealth(
                state = GeoAwarenessHealthState.DEGRADED,
                message = "Geo-awareness dataset has validation warnings.",
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
