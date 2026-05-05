package com.example.droneservicesapp.domain.geoawareness.testing

data class GeoAwarenessTestRunResult(
    val runAtMillis: Long,
    val results: List<GeoAwarenessTestResult>
) {
    val passCount: Int get() = results.count { it.status == GeoAwarenessTestStatus.PASS }
    val failCount: Int get() = results.count { it.status == GeoAwarenessTestStatus.FAIL }
    val warningCount: Int get() = results.count { it.status == GeoAwarenessTestStatus.WARNING }
    val skippedCount: Int get() = results.count { it.status == GeoAwarenessTestStatus.SKIPPED }
    val overallStatus: GeoAwarenessTestStatus
        get() = when {
            failCount > 0 -> GeoAwarenessTestStatus.FAIL
            warningCount > 0 -> GeoAwarenessTestStatus.WARNING
            skippedCount > 0 -> GeoAwarenessTestStatus.WARNING
            else -> GeoAwarenessTestStatus.PASS
        }
}
