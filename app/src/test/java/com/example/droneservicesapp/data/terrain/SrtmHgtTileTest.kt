package com.example.droneservicesapp.data.terrain

import com.example.droneservicesapp.domain.terrain.SrtmTileId
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SrtmHgtTileTest {
    @Test fun readsBigEndianGridAndInterpolates() {
        val file = createGrid(shortArrayOf(
            100, 110, 120,
            200, 210, 220,
            300, 310, 320,
        ))
        SrtmHgtTile(SrtmTileId(35, 24), file, gridSize = 3).use { tile ->
            assertEquals(100.0, tile.elevationMeters(36.0, 24.0)!!, 0.001)
            assertEquals(210.0, tile.elevationMeters(35.5, 24.5)!!, 0.001)
            assertEquals(320.0, tile.elevationMeters(35.0, 25.0)!!, 0.001)
        }
        file.delete()
    }

    @Test fun rejectsVoidData() {
        val file = createGrid(shortArrayOf(100, 100, 100, 100, -32768, 100, 100, 100, 100))
        SrtmHgtTile(SrtmTileId(35, 24), file, gridSize = 3).use { tile ->
            assertNull(tile.elevationMeters(35.5, 24.5))
        }
        file.delete()
    }

    private fun createGrid(values: ShortArray): File {
        val file = File.createTempFile("srtm-test", ".hgt")
        DataOutputStream(FileOutputStream(file)).use { output -> values.forEach { output.writeShort(it.toInt()) } }
        return file
    }
}
