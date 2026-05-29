package com.example.droneservicesapp.domain.geoawareness

object GeoZoneDatasetStalenessPolicy {
    const val DEFAULT_STALE_AFTER_MILLIS: Long = 7L * 24L * 60L * 60L * 1000L

    fun isStale(updatedAtMillis: Long?, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (updatedAtMillis == null) return true
        return nowMillis - updatedAtMillis > DEFAULT_STALE_AFTER_MILLIS
    }

    fun ageDescription(updatedAtMillis: Long?, nowMillis: Long = System.currentTimeMillis()): String {
        if (updatedAtMillis == null) {
            return "Update time unknown"
        }
        val ageMillis = (nowMillis - updatedAtMillis).coerceAtLeast(0L)
        val ageDays = ageMillis / (24L * 60L * 60L * 1000L)
        return when {
            ageDays <= 0L -> "Updated today"
            ageDays == 1L -> "Updated 1 day ago"
            else -> "Updated $ageDays days ago"
        }
    }
}
