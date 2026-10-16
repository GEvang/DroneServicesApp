package com.example.droneservicesapp.ui.map.osmdroid

import android.content.Context
import androidx.preference.PreferenceManager
import com.example.droneservicesapp.ui.map.MapTileCache
import java.io.File
import org.osmdroid.config.Configuration

/**
 * Process-wide osmdroid set-up and its tile cache. This is the only place outside the map surface
 * that touches osmdroid's global configuration.
 */
object OsmdroidMapEngine : MapTileCache {
    /** The cache trims itself back to [TILE_CACHE_TRIM_BYTES] once it grows past this size. */
    const val TILE_CACHE_MAX_BYTES = 1024L * 1024L * 1024L
    const val TILE_CACHE_TRIM_BYTES = 800L * 1024L * 1024L

    /** Must run once in `Application.onCreate`, before any map is created. */
    @JvmStatic
    fun configure(context: Context) {
        val appContext = context.applicationContext
        Configuration.getInstance().apply {
            load(appContext, PreferenceManager.getDefaultSharedPreferences(appContext))
            userAgentValue = appContext.packageName
            tileFileSystemCacheMaxBytes = TILE_CACHE_MAX_BYTES
            tileFileSystemCacheTrimBytes = TILE_CACHE_TRIM_BYTES
        }
    }

    override fun sizeBytes(): Long = cacheDirectory()?.let(::directorySize) ?: 0L

    override fun clear(): MapTileCache.ClearResult {
        val directory = cacheDirectory() ?: return MapTileCache.ClearResult.NOT_FOUND
        return if (deleteRecursively(directory)) MapTileCache.ClearResult.CLEARED else MapTileCache.ClearResult.FAILED
    }

    private fun cacheDirectory(): File? = runCatching { Configuration.getInstance().osmdroidTileCache }.getOrNull()

    private fun directorySize(file: File): Long = when {
        !file.exists() -> 0L
        file.isFile -> file.length()
        else -> file.listFiles()?.sumOf(::directorySize) ?: 0L
    }

    private fun deleteRecursively(file: File): Boolean {
        if (!file.exists()) return true
        if (file.isDirectory && file.listFiles()?.all(::deleteRecursively) == false) return false
        return file.delete()
    }
}
