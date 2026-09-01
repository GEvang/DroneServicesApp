package com.example.droneservicesapp.data.preview

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

data class PreviewDatasetRecord(
    val id: String,
    val name: String,
    val orthoImageUri: Uri? = null,
    val orthoImageName: String? = null,
    val orthoSourceWidth: Int? = null,
    val orthoSourceHeight: Int? = null,
    val orthoWorldUri: Uri? = null,
    val orthoWorldName: String? = null,
    val pointCloudUri: Uri? = null,
    val pointCloudName: String? = null,
    val orthoOpacity: Float = 0.85f,
    val orthoBackgroundEnabled: Boolean = true,
    val pointCloudPointSize: Float = 2.5f,
    val heightColorModeEnabled: Boolean = false,
)

class PreviewDatasetStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadDatasets(): List<PreviewDatasetRecord> {
        val raw = preferences.getString(KEY_DATASETS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getJSONObject(index).toRecord())
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveDatasets(datasets: List<PreviewDatasetRecord>) {
        val array = JSONArray()
        datasets.forEach { array.put(it.toJson()) }
        preferences.edit {
            putString(KEY_DATASETS, array.toString())
        }
    }

    fun activeDatasetId(): String? = preferences.getString(KEY_ACTIVE_DATASET_ID, null)

    fun setActiveDatasetId(id: String?) {
        preferences.edit {
            if (id == null) remove(KEY_ACTIVE_DATASET_ID) else putString(KEY_ACTIVE_DATASET_ID, id)
        }
    }

    fun activeDataset(): PreviewDatasetRecord? {
        val id = activeDatasetId() ?: return null
        return loadDatasets().firstOrNull { it.id == id }
    }

    fun upsert(record: PreviewDatasetRecord) {
        val updated = loadDatasets().toMutableList()
        val index = updated.indexOfFirst { it.id == record.id }
        if (index >= 0) updated[index] = record else updated += record
        saveDatasets(updated)
    }

    fun delete(id: String) {
        saveDatasets(loadDatasets().filterNot { it.id == id })
        if (activeDatasetId() == id) setActiveDatasetId(null)
    }

    private fun JSONObject.toRecord(): PreviewDatasetRecord {
        return PreviewDatasetRecord(
            id = getString("id"),
            name = optString("name"),
            orthoImageUri = optUri("orthoImageUri"),
            orthoImageName = optStringOrNull("orthoImageName"),
            orthoSourceWidth = optIntOrNull("orthoSourceWidth"),
            orthoSourceHeight = optIntOrNull("orthoSourceHeight"),
            orthoWorldUri = optUri("orthoWorldUri"),
            orthoWorldName = optStringOrNull("orthoWorldName"),
            pointCloudUri = optUri("pointCloudUri"),
            pointCloudName = optStringOrNull("pointCloudName"),
            orthoOpacity = optDouble("orthoOpacity", 0.85).toFloat(),
            orthoBackgroundEnabled = optBoolean("orthoBackgroundEnabled", true),
            pointCloudPointSize = optDouble("pointCloudPointSize", 2.5).toFloat(),
            heightColorModeEnabled = optBoolean("heightColorModeEnabled", false),
        )
    }

    private fun PreviewDatasetRecord.toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("name", name)
            .putNullable("orthoImageUri", orthoImageUri?.toString())
            .putNullable("orthoImageName", orthoImageName)
            .putNullable("orthoSourceWidth", orthoSourceWidth)
            .putNullable("orthoSourceHeight", orthoSourceHeight)
            .putNullable("orthoWorldUri", orthoWorldUri?.toString())
            .putNullable("orthoWorldName", orthoWorldName)
            .putNullable("pointCloudUri", pointCloudUri?.toString())
            .putNullable("pointCloudName", pointCloudName)
            .put("orthoOpacity", orthoOpacity.toDouble())
            .put("orthoBackgroundEnabled", orthoBackgroundEnabled)
            .put("pointCloudPointSize", pointCloudPointSize.toDouble())
            .put("heightColorModeEnabled", heightColorModeEnabled)
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        return if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optIntOrNull(key: String): Int? {
        return if (isNull(key) || !has(key)) null else optInt(key)
    }

    private fun JSONObject.optUri(key: String): Uri? {
        return optStringOrNull(key)?.let(Uri::parse)
    }

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject {
        return if (value == null) put(key, JSONObject.NULL) else put(key, value)
    }

    companion object {
        private const val PREFS_NAME = "preview_datasets"
        private const val KEY_DATASETS = "datasets"
        private const val KEY_ACTIVE_DATASET_ID = "active_dataset_id"
    }
}
