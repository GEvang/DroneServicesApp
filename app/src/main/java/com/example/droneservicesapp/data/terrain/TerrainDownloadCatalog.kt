package com.example.droneservicesapp.data.terrain

import android.content.Context
import android.location.Geocoder
import com.example.droneservicesapp.domain.terrain.TerrainCoveragePlan
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

data class TerrainDownloadRecord(
    val id: String,
    val areaName: String?,
    val centerLatitude: Double,
    val centerLongitude: Double,
    val downloadedAtMillis: Long,
    val tileNames: List<String>,
)

/** Persistent area-level index for the SRTM files stored on this device. */
class TerrainDownloadCatalog(context: Context) {
    private val appContext = context.applicationContext
    private val catalogFile = File(appContext.filesDir, CATALOG_PATH)

    fun recordPreparedArea(plan: TerrainCoveragePlan, tileFiles: List<File>) {
        val tileNames = plan.sourceTiles.map { it.baseName }.sorted()
        val id = areaId(plan, tileNames)
        val downloadedAt = tileFiles
            .filter(File::isFile)
            .maxOfOrNull(File::lastModified)
            ?.takeIf { it > 0L }
            ?: System.currentTimeMillis()
        synchronized(CATALOG_LOCK) {
            val records = readRecords().toMutableList()
            val existingIndex = records.indexOfFirst { it.id == id }
            val existing = records.getOrNull(existingIndex)
            val record = TerrainDownloadRecord(
                id = id,
                areaName = existing?.areaName,
                centerLatitude = plan.center.lat,
                centerLongitude = plan.center.lon,
                downloadedAtMillis = downloadedAt,
                tileNames = tileNames,
            )
            if (existingIndex >= 0) records[existingIndex] = record else records += record
            writeRecords(records)
        }
    }

    fun list(resolveAreaNames: Boolean = false): List<TerrainDownloadRecord> = synchronized(CATALOG_LOCK) {
        val records = readRecords()
        if (!resolveAreaNames) return@synchronized records.sortedByDescending { it.downloadedAtMillis }
        var changed = false
        val resolved = records.map { record ->
            if (!record.areaName.isNullOrBlank()) {
                record
            } else {
                val areaName = resolveAreaName(record)
                if (areaName == null) record else {
                    changed = true
                    record.copy(areaName = areaName)
                }
            }
        }
        if (changed) writeRecords(resolved)
        resolved.sortedByDescending { it.downloadedAtMillis }
    }

    @Suppress("DEPRECATION")
    private fun resolveAreaName(record: TerrainDownloadRecord): String? {
        if (!Geocoder.isPresent()) return null
        val address = runCatching {
            Geocoder(appContext, Locale.getDefault())
                .getFromLocation(record.centerLatitude, record.centerLongitude, 1)
                ?.firstOrNull()
        }.getOrNull() ?: return null
        return sequenceOf(address.subLocality, address.locality, address.featureName, address.subAdminArea, address.adminArea)
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .firstOrNull()
    }

    private fun readRecords(): List<TerrainDownloadRecord> {
        if (!catalogFile.isFile) return emptyList()
        return runCatching {
            val root = JSONArray(catalogFile.readText(Charsets.UTF_8))
            buildList {
                for (index in 0 until root.length()) {
                    val item = root.getJSONObject(index)
                    val tiles = item.optJSONArray("tileNames") ?: JSONArray()
                    add(
                        TerrainDownloadRecord(
                            id = item.getString("id"),
                            areaName = item.optString("areaName").takeIf { it.isNotBlank() && it != "null" },
                            centerLatitude = item.getDouble("centerLatitude"),
                            centerLongitude = item.getDouble("centerLongitude"),
                            downloadedAtMillis = item.getLong("downloadedAtMillis"),
                            tileNames = buildList {
                                for (tileIndex in 0 until tiles.length()) add(tiles.getString(tileIndex))
                            },
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeRecords(records: List<TerrainDownloadRecord>) {
        catalogFile.parentFile?.mkdirs()
        val root = JSONArray()
        records.forEach { record ->
            root.put(JSONObject().apply {
                put("id", record.id)
                put("areaName", record.areaName)
                put("centerLatitude", record.centerLatitude)
                put("centerLongitude", record.centerLongitude)
                put("downloadedAtMillis", record.downloadedAtMillis)
                put("tileNames", JSONArray(record.tileNames))
            })
        }
        catalogFile.writeText(root.toString(), Charsets.UTF_8)
    }

    private fun areaId(plan: TerrainCoveragePlan, tileNames: List<String>): String = String.format(
        Locale.US,
        "%.4f,%.4f:%s",
        plan.center.lat,
        plan.center.lon,
        tileNames.joinToString(","),
    )

    companion object {
        private const val CATALOG_PATH = "terrain/download_catalog.json"
        private val CATALOG_LOCK = Any()
    }
}
