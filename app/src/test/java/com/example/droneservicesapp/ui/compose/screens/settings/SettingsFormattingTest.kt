package com.example.droneservicesapp.ui.compose.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsFormattingTest {
    @Test
    fun byteFormattingUsesReadableStorageUnits() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("1023 B", formatBytes(1023))
        assertEquals("1 KB", formatBytes(1024))
        assertEquals("1 MB", formatBytes(1024L * 1024L))
        assertEquals("1 GB", formatBytes(1024L * 1024L * 1024L))
    }
}
