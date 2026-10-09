package com.example.droneservicesapp.ui.compose.feasibility

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.Lifecycle
import com.example.droneservicesapp.ui.compose.feasibility.pointcloud.POINT_CLOUD_FEASIBILITY_ROOT_TAG
import com.example.droneservicesapp.ui.compose.feasibility.pointcloud.POINT_CLOUD_RENDER_SURFACE_TAG
import com.example.droneservicesapp.ui.compose.feasibility.pointcloud.POINT_CLOUD_RESET_TAG
import com.example.droneservicesapp.ui.compose.feasibility.pointcloud.PointCloudFeasibilityActivity
import org.junit.Rule
import org.junit.Test

class PointCloudFeasibilityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<PointCloudFeasibilityActivity>()

    @Test
    fun composeControlsAndGlSurfaceSurviveLifecycle() {
        composeRule.onNodeWithTag(POINT_CLOUD_FEASIBILITY_ROOT_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(POINT_CLOUD_RENDER_SURFACE_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(POINT_CLOUD_RESET_TAG).assertIsDisplayed()

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        composeRule.onNodeWithTag(POINT_CLOUD_FEASIBILITY_ROOT_TAG).assertIsDisplayed()
    }
}
