package com.example.droneservicesapp.ui.compose.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class AppDestinationTest {
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
}
