package com.example.droneservicesapp.data.terrain

import com.example.droneservicesapp.domain.terrain.SrtmTileId
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.floor

/** Disk-backed reader for a one-arc-second, signed big-endian HGT elevation grid. */
class SrtmHgtTile(
    val id: SrtmTileId,
    file: File,
    private val gridSize: Int = SRTM1_GRID_SIZE,
) : Closeable {
    private val input = RandomAccessFile(file, "r")
    private val rowCache = object : LinkedHashMap<Int, ShortArray>(ROW_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ShortArray>?): Boolean =
            size > ROW_CACHE_SIZE
    }

    init {
        require(file.length() == gridSize.toLong() * gridSize * 2L) { "Unexpected HGT file size" }
    }

    @Synchronized
    fun elevationMeters(latitude: Double, longitude: Double): Double? {
        val south = id.southLatitude.toDouble()
        val west = id.westLongitude.toDouble()
        if (latitude !in south..(south + 1.0) || longitude !in west..(west + 1.0)) return null
        val cells = gridSize - 1
        val row = ((south + 1.0 - latitude) * cells).coerceIn(0.0, cells.toDouble())
        val column = ((longitude - west) * cells).coerceIn(0.0, cells.toDouble())
        val row0 = floor(row).toInt()
        val column0 = floor(column).toInt()
        val row1 = (row0 + 1).coerceAtMost(cells)
        val column1 = (column0 + 1).coerceAtMost(cells)
        val northFraction = row - row0
        val eastFraction = column - column0
        val nw = sample(row0, column0) ?: return null
        val ne = sample(row0, column1) ?: return null
        val sw = sample(row1, column0) ?: return null
        val se = sample(row1, column1) ?: return null
        val north = nw + (ne - nw) * eastFraction
        val southValue = sw + (se - sw) * eastFraction
        return north + (southValue - north) * northFraction
    }

    private fun sample(row: Int, column: Int): Double? {
        val value = row(row)[column]
        return if (value == VOID_VALUE) null else value.toDouble()
    }

    private fun row(index: Int): ShortArray = rowCache.getOrPut(index) {
        input.seek(index.toLong() * gridSize * 2L)
        ShortArray(gridSize) { input.readShort() }
    }

    @Synchronized
    override fun close() {
        rowCache.clear()
        input.close()
    }

    companion object {
        const val SRTM1_GRID_SIZE = 3601
        const val SRTM1_FILE_BYTES = SRTM1_GRID_SIZE.toLong() * SRTM1_GRID_SIZE * 2L
        private const val ROW_CACHE_SIZE = 8
        private const val VOID_VALUE: Short = -32768
    }
}
