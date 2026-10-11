package com.example.droneservicesapp.ui.compose.screens.datasets

import com.example.droneservicesapp.data.preview.PreviewDatasetRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DatasetsContractTest {
    @Test
    fun activeDatasetIsDerivedFromStableId() {
        val first = PreviewDatasetRecord("one", "One")
        val second = PreviewDatasetRecord("two", "Two")
        val state = DatasetsUiState(datasets = listOf(first, second), activeDatasetId = second.id)

        assertEquals(second, state.activeDataset)
        assertNull(state.copy(activeDatasetId = "missing").activeDataset)
    }

    @Test
    fun fileValidationAcceptsEverySupportedExtensionCaseInsensitively() {
        assertTrue(isSupportedOrthoImage("map.TIFF"))
        assertTrue(isSupportedWorldFile("map.TFW"))
        assertTrue(isSupportedPointCloud("survey.PLY"))
        assertTrue(isSupportedPointCloud("survey.xyz"))
        assertFalse(isSupportedOrthoImage("map.png"))
        assertFalse(isSupportedWorldFile("map.json"))
        assertFalse(isSupportedPointCloud("survey.las"))
    }
}
