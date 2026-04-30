package com.example.droneservicesapp.data.geoawareness

import android.content.Context
import android.util.Log
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealthEvaluator
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealthState
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetInfo

object GeoAwarenessHealthDebugProbe {

    private const val TAG = "GeoHealthDebug"

    fun logHealthValidation(context: Context) {
        try {
            val repository = GeoZoneRepository(
                GeoZoneAssetDataSource(context.applicationContext)
            )
            val loadResult = repository.loadDummyRethymnoDataset()
            val zones = loadResult.zones
            val now = System.currentTimeMillis()

            val dummyHealth = GeoAwarenessHealthEvaluator.evaluate(
                datasetInfo = loadResult.datasetInfo,
                zones = zones,
                nowMillis = now
            )
            val unavailableHealth = GeoAwarenessHealthEvaluator.evaluate(
                datasetInfo = null,
                zones = emptyList(),
                loadError = IllegalStateException("failed to load"),
                nowMillis = now
            )
            val degradedHealth = GeoAwarenessHealthEvaluator.evaluate(
                datasetInfo = GeoZoneDatasetInfo(
                    title = "Unofficial Rethymno dataset",
                    description = "Test unofficial dataset",
                    version = "unofficial-001",
                    source = "Synthetic",
                    sourceUrl = null,
                    country = "GRC",
                    isOfficial = false,
                    isDummy = false,
                    loadedAtMillis = now,
                    zoneCount = zones.size,
                    circleGeometryCount = loadResult.datasetInfo.circleGeometryCount,
                    polygonGeometryCount = loadResult.datasetInfo.polygonGeometryCount
                ),
                zones = zones,
                nowMillis = now
            )
            val staleHealth = GeoAwarenessHealthEvaluator.evaluate(
                datasetInfo = GeoZoneDatasetInfo(
                    title = "Official Rethymno dataset",
                    description = "Synthetic official stale dataset",
                    version = "official-stale-001",
                    source = "Synthetic",
                    sourceUrl = null,
                    country = "GRC",
                    isOfficial = true,
                    isDummy = false,
                    loadedAtMillis = now - GeoAwarenessHealthEvaluator.DEFAULT_STALE_AFTER_MILLIS - 1L,
                    zoneCount = zones.size,
                    circleGeometryCount = loadResult.datasetInfo.circleGeometryCount,
                    polygonGeometryCount = loadResult.datasetInfo.polygonGeometryCount
                ),
                zones = zones,
                nowMillis = now
            )
            val availableHealth = GeoAwarenessHealthEvaluator.evaluate(
                datasetInfo = GeoZoneDatasetInfo(
                    title = "Official Rethymno dataset",
                    description = "Synthetic official fresh dataset",
                    version = "official-fresh-001",
                    source = "Synthetic",
                    sourceUrl = null,
                    country = "GRC",
                    isOfficial = true,
                    isDummy = false,
                    loadedAtMillis = now,
                    zoneCount = zones.size,
                    circleGeometryCount = loadResult.datasetInfo.circleGeometryCount,
                    polygonGeometryCount = loadResult.datasetInfo.polygonGeometryCount
                ),
                zones = zones,
                nowMillis = now
            )

            val dummy = dummyHealth.state == GeoAwarenessHealthState.DUMMY_DATA
            val unavailable = unavailableHealth.state == GeoAwarenessHealthState.UNAVAILABLE
            val degraded = degradedHealth.state == GeoAwarenessHealthState.DEGRADED
            val stale = staleHealth.state == GeoAwarenessHealthState.STALE
            val available = availableHealth.state == GeoAwarenessHealthState.AVAILABLE

            if (!dummy) {
                Log.e(TAG, "Health validation failed: dummy expected DUMMY_DATA actual ${dummyHealth.state}")
            }
            if (!unavailable) {
                Log.e(TAG, "Health validation failed: unavailable expected UNAVAILABLE actual ${unavailableHealth.state}")
            }
            if (!degraded) {
                Log.e(TAG, "Health validation failed: degraded expected DEGRADED actual ${degradedHealth.state}")
            }
            if (!stale) {
                Log.e(TAG, "Health validation failed: stale expected STALE actual ${staleHealth.state}")
            }
            if (!available) {
                Log.e(TAG, "Health validation failed: available expected AVAILABLE actual ${availableHealth.state}")
            }

            Log.d(
                TAG,
                "Health validation: dummy=$dummy unavailable=$unavailable degraded=$degraded stale=$stale available=$available"
            )
        } catch (error: Exception) {
            Log.e(TAG, "Health validation probe failed", error)
        }
    }
}
