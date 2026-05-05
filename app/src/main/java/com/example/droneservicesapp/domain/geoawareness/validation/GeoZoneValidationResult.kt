package com.example.droneservicesapp.domain.geoawareness.validation

data class GeoZoneValidationResult(
    val isValid: Boolean,
    val errorCount: Int,
    val warningCount: Int,
    val infoCount: Int,
    val issues: List<GeoZoneValidationIssue>
) {
    val hasErrors: Boolean get() = errorCount > 0
    val hasWarnings: Boolean get() = warningCount > 0

    companion object {
        fun ok(): GeoZoneValidationResult = GeoZoneValidationResult(
            isValid = true,
            errorCount = 0,
            warningCount = 0,
            infoCount = 0,
            issues = emptyList()
        )

        fun fromIssues(issues: List<GeoZoneValidationIssue>): GeoZoneValidationResult {
            val errorCount = issues.count { it.severity == GeoZoneValidationSeverity.ERROR }
            val warningCount = issues.count { it.severity == GeoZoneValidationSeverity.WARNING }
            val infoCount = issues.count { it.severity == GeoZoneValidationSeverity.INFO }
            return GeoZoneValidationResult(
                isValid = errorCount == 0,
                errorCount = errorCount,
                warningCount = warningCount,
                infoCount = infoCount,
                issues = issues
            )
        }

        fun combine(results: List<GeoZoneValidationResult>): GeoZoneValidationResult {
            if (results.isEmpty()) {
                return ok()
            }
            return fromIssues(results.flatMap { it.issues })
        }
    }
}
