package com.example.droneservicesapp.ui.compose.feasibility.map

import com.example.droneservicesapp.ui.map.model.MapApiMaturity
import com.example.droneservicesapp.ui.map.model.MapPlatform
import com.example.droneservicesapp.ui.map.model.requiredMapCapabilities
import com.example.droneservicesapp.ui.map.model.satisfies
import com.example.droneservicesapp.ui.map.model.selectedMapPlatform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapPlatformDecisionTest {
    @Test
    fun selectedPlatformMeetsEveryRequiredCapability() {
        assertEquals(MapPlatform.OSMDROID_COMPOSE_INTEROP, selectedMapPlatform)
        assertTrue(selectedMapPlatform.satisfies(requiredMapCapabilities))
    }

    @Test
    fun googleMapsComposeDoesNotMeetOfflineCustomImageryRequirements() {
        assertFalse(MapPlatform.GOOGLE_MAPS_COMPOSE.satisfies(requiredMapCapabilities))
    }

    @Test
    fun mapLibreMeetsCapabilitiesButItsComposeApiIsNotYetStable() {
        assertTrue(MapPlatform.MAPLIBRE_COMPOSE.satisfies(requiredMapCapabilities))
        assertEquals(MapApiMaturity.PRE_STABLE, MapPlatform.MAPLIBRE_COMPOSE.composeApiMaturity)
    }
}
