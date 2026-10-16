package com.example.droneservicesapp.ui.map

/**
 * The map engine's on-device tile cache: imagery browsed online plus areas downloaded for offline
 * use. Screens use this instead of reaching into the map SDK.
 */
interface MapTileCache {
    enum class ClearResult { CLEARED, FAILED, NOT_FOUND }

    /** Bytes used by cached tiles; 0 when the cache does not exist yet. Call off the main thread. */
    fun sizeBytes(): Long

    /** Deletes every cached tile. Call off the main thread. */
    fun clear(): ClearResult
}
