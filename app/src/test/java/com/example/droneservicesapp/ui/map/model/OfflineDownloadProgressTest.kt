package com.example.droneservicesapp.ui.map.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineDownloadProgressTest {
    @Test
    fun noPercentUntilATileCountIsKnown() {
        val progress = OfflineDownloadProgress(2)
        assertNull(progress.percent)
        progress.setProcessed(0, 10)
        assertNull(progress.percent)
    }

    @Test
    fun combinesImageryAndLabelLayersByTileCount() {
        val progress = OfflineDownloadProgress(2)
        progress.setTotal(0, 300) // imagery
        progress.setTotal(1, 100) // labels
        progress.setProcessed(0, 150)
        assertEquals(37, progress.percent) // 150 of 400
        progress.complete(1)
        assertEquals(62, progress.percent) // 250 of 400
        progress.complete(0)
        assertEquals(100, progress.percent)
    }

    @Test
    fun processedCountsAreClampedToTheLayerTotal() {
        val progress = OfflineDownloadProgress(1)
        progress.setTotal(0, 50)
        progress.setProcessed(0, 80) // osmdroid counts skipped tiles too
        assertEquals(100, progress.percent)
        progress.setProcessed(0, -5)
        assertEquals(0, progress.percent)
    }
}
