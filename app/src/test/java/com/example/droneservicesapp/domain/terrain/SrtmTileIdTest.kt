package com.example.droneservicesapp.domain.terrain

import org.junit.Assert.assertEquals
import org.junit.Test

class SrtmTileIdTest {
    @Test fun namesPositiveTile() {
        assertEquals("N35E024", SrtmTileId.containing(35.42, 24.18).baseName)
    }

    @Test fun floorsNegativeCoordinates() {
        assertEquals("S36W025", SrtmTileId.containing(-35.42, -24.18).baseName)
    }

    @Test fun buildsOfficialSrtm1Url() {
        assertEquals(
            "https://terrain.ardupilot.org/SRTM1/N35E024.hgt.zip",
            SrtmTileId(35, 24).downloadUrl,
        )
    }
}
