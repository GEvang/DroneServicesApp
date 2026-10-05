package com.example.droneservicesapp.ui.preview

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.util.Locale

/** Persists document references used by the map preview and owns URI metadata access. */
class PreviewAssetStore(context: Context) {
    data class PersistedAssets(
        val orthoImageUri: Uri?,
        val orthoImageName: String?,
        val orthoWorldUri: Uri?,
        val orthoWorldName: String?,
        val pointCloudUri: Uri?,
        val pointCloudName: String?,
    )

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun readPersistedAssets(): PersistedAssets = PersistedAssets(
        orthoImageUri = preferences.getString(KEY_ORTHO_IMAGE_URI, null)?.let(Uri::parse),
        orthoImageName = preferences.getString(KEY_ORTHO_IMAGE_NAME, null),
        orthoWorldUri = preferences.getString(KEY_ORTHO_WORLD_URI, null)?.let(Uri::parse),
        orthoWorldName = preferences.getString(KEY_ORTHO_WORLD_NAME, null),
        pointCloudUri = preferences.getString(KEY_POINT_CLOUD_URI, null)?.let(Uri::parse),
        pointCloudName = preferences.getString(KEY_POINT_CLOUD_NAME, null),
    )

    fun displayName(uri: Uri): String? =
        appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
        }

    fun persistReadPermission(uri: Uri, intentFlags: Int) {
        val readFlags = intentFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (readFlags == 0) return
        runCatching { appContext.contentResolver.takePersistableUriPermission(uri, readFlags) }
    }

    fun saveOrthoImage(uri: Uri, fileName: String) {
        preferences.edit()
            .putString(KEY_ORTHO_IMAGE_URI, uri.toString())
            .putString(KEY_ORTHO_IMAGE_NAME, fileName)
            .remove(KEY_ORTHO_WORLD_URI)
            .remove(KEY_ORTHO_WORLD_NAME)
            .apply()
    }

    fun saveOrthoWorld(uri: Uri, fileName: String) {
        preferences.edit()
            .putString(KEY_ORTHO_WORLD_URI, uri.toString())
            .putString(KEY_ORTHO_WORLD_NAME, fileName)
            .apply()
    }

    fun savePointCloud(uri: Uri, fileName: String) {
        preferences.edit()
            .putString(KEY_POINT_CLOUD_URI, uri.toString())
            .putString(KEY_POINT_CLOUD_NAME, fileName)
            .apply()
    }

    fun supportsPointCloud(fileName: String): Boolean {
        val lowerName = fileName.lowercase(Locale.US)
        return SUPPORTED_POINT_CLOUD_EXTENSIONS.any { extension -> lowerName.endsWith(extension) }
    }

    companion object {
        private const val PREFERENCES_NAME = "preview_assets"
        private const val KEY_ORTHO_IMAGE_URI = "ortho_image_uri"
        private const val KEY_ORTHO_IMAGE_NAME = "ortho_image_name"
        private const val KEY_ORTHO_WORLD_URI = "ortho_world_uri"
        private const val KEY_ORTHO_WORLD_NAME = "ortho_world_name"
        private const val KEY_POINT_CLOUD_URI = "point_cloud_uri"
        private const val KEY_POINT_CLOUD_NAME = "point_cloud_name"
        private val SUPPORTED_POINT_CLOUD_EXTENSIONS = listOf(".ply", ".pcd", ".csv", ".txt", ".xyz")
    }
}
