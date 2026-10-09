package com.example.droneservicesapp.ui.compose.feasibility

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.droneservicesapp.ui.compose.feasibility.map.MAP_FEASIBILITY_ROOT_TAG
import com.example.droneservicesapp.ui.compose.feasibility.map.MAP_RENDER_SURFACE_TAG
import com.example.droneservicesapp.ui.compose.feasibility.map.MapFeasibilityActivity
import org.junit.Rule
import org.junit.Test

class MapFeasibilityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MapFeasibilityActivity>()

    @Test
    fun composeControlsAndMapSurfaceAreDisplayed() {
        composeRule.onNodeWithTag(MAP_FEASIBILITY_ROOT_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(MAP_RENDER_SURFACE_TAG).assertIsDisplayed()
    }
}
