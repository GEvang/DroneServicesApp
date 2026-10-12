package com.example.droneservicesapp.ui.compose.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDestinationTest {
    @Test
    fun mapDestinationsReserveDragGesturesForMapInteraction() {
        assertFalse(AppDestination.Home.drawerGesturesEnabled)
        assertFalse(AppDestination.OrthoPreview.drawerGesturesEnabled)
        assertFalse(AppDestination.PointCloud.drawerGesturesEnabled)
        assertTrue(AppDestination.Settings.drawerGesturesEnabled)
    }

    @Test
    fun routesAreUniqueAndResolveToTheirTypedDestinations() {
        assertEquals(AppDestination.all.size, AppDestination.all.map { it.route }.distinct().size)
        AppDestination.all.forEach { destination ->
            assertSame(destination, AppDestination.fromRoute(destination.route))
        }
    }

    @Test
    fun unknownRouteFallsBackToHomeAndSecondaryPreviewsStayOutOfDrawer() {
        assertSame(AppDestination.Home, AppDestination.fromRoute("not-a-route"))
        assertSame(AppDestination.Home, AppDestination.fromRoute(null))
        assertFalse(AppDestination.PointCloud in AppDestination.drawerDestinations)
        assertFalse(AppDestination.OrthoPreview in AppDestination.drawerDestinations)
    }

    @Test
    fun onlyUnmigratedDestinationsExposeFragmentFactories() {
        val migrated = setOf(
            AppDestination.Settings,
            AppDestination.MavlinkNetwork,
            AppDestination.Rtk,
            AppDestination.Parameters,
            AppDestination.Debug,
            AppDestination.Datasets,
            AppDestination.GeoAwareness,
            AppDestination.OrthoPreview,
            AppDestination.PointCloud,
        )
        AppDestination.all.forEach { destination ->
            if (destination in migrated) {
                assertNull(destination.createFragment)
            } else {
                assertNotNull(destination.createFragment)
            }
        }
    }
}
