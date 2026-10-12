package com.example.droneservicesapp.ui.compose.navigation

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.rule.GrantPermissionRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.droneservicesapp.ui.compose.screens.debug.DEBUG_SCREEN_TAG
import com.example.droneservicesapp.ui.compose.screens.datasets.DATASETS_SCREEN_TAG
import com.example.droneservicesapp.ui.compose.screens.geoawareness.GEO_AWARENESS_SCREEN_TAG
import com.example.droneservicesapp.ui.compose.screens.geoawareness.GEO_AWARENESS_VERIFICATION_BUTTON_TAG
import com.example.droneservicesapp.ui.compose.screens.mavlink.MAVLINK_NETWORK_SCREEN_TAG
import com.example.droneservicesapp.ui.compose.screens.missionmap.MISSION_BOTTOM_DOCK_TAG
import com.example.droneservicesapp.ui.compose.screens.missionmap.MISSION_MAP_SCREEN_TAG
import com.example.droneservicesapp.ui.compose.screens.missionmap.MISSION_MAP_STATUS_STRIP_TAG
import com.example.droneservicesapp.ui.compose.screens.missionmap.MISSION_MAP_SURFACE_TAG
import com.example.droneservicesapp.ui.compose.screens.missionmap.MISSION_PLANNING_DOCK_BUTTON_TAG
import com.example.droneservicesapp.ui.compose.screens.missionmap.MISSION_PREVIEW_RAIL_TAG
import com.example.droneservicesapp.ui.compose.screens.missionmap.MISSION_WORKFLOW_PANEL_TAG
import com.example.droneservicesapp.ui.compose.screens.parameters.PARAMETERS_SCREEN_TAG
import com.example.droneservicesapp.ui.compose.screens.ortho.ORTHO_PREVIEW_SCREEN_TAG
import com.example.droneservicesapp.ui.compose.screens.ortho.ORTHO_MAP_SURFACE_TAG
import com.example.droneservicesapp.ui.compose.screens.pointcloud.POINT_CLOUD_SCREEN_TAG
import com.example.droneservicesapp.ui.compose.screens.pointcloud.POINT_CLOUD_SURFACE_TAG
import com.example.droneservicesapp.ui.compose.screens.rtk.RTK_SCREEN_TAG
import com.example.droneservicesapp.ui.compose.screens.settings.SETTINGS_SCREEN_TAG
import com.example.droneservicesapp.ui.shell.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class MainActivityShellTest {
    private val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.ACCESS_FINE_LOCATION)
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(permissionRule).around(composeRule)

    @Test
    fun selectedDestinationSurvivesActivityRecreation() {
        composeRule.onNodeWithTag(SHELL_ROOT_TAG).assertIsDisplayed()
        composeRule.runOnUiThread { composeRule.activity.openNavigation() }
        composeRule.onNodeWithTag("drawer-settings").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(SHELL_DESTINATION_TITLE_TAG)
            .assertContentDescriptionEquals("Settings")
            .assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithTag(SHELL_DESTINATION_TITLE_TAG)
            .assertContentDescriptionEquals("Settings")
            .assertIsDisplayed()
    }

    @Test
    fun migratedDestinationsRenderAsComposeScreens() {
        listOf(
            AppDestination.Home to MISSION_MAP_SCREEN_TAG,
            AppDestination.Settings to SETTINGS_SCREEN_TAG,
            AppDestination.MavlinkNetwork to MAVLINK_NETWORK_SCREEN_TAG,
            AppDestination.Rtk to RTK_SCREEN_TAG,
            AppDestination.Parameters to PARAMETERS_SCREEN_TAG,
            AppDestination.Debug to DEBUG_SCREEN_TAG,
            AppDestination.Datasets to DATASETS_SCREEN_TAG,
            AppDestination.GeoAwareness to GEO_AWARENESS_SCREEN_TAG,
            AppDestination.OrthoPreview to ORTHO_PREVIEW_SCREEN_TAG,
            AppDestination.PointCloud to POINT_CLOUD_SCREEN_TAG,
        ).forEach { (destination, tag) ->
            composeRule.runOnUiThread { composeRule.activity.navigateTo(destination) }
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
        }
    }

    @Test
    fun productionPreviewRenderersSurviveRouteAndActivityLifecycle() {
        composeRule.runOnUiThread { composeRule.activity.navigateTo(AppDestination.OrthoPreview) }
        composeRule.onNodeWithTag(ORTHO_MAP_SURFACE_TAG).assertIsDisplayed()
        composeRule.runOnUiThread { composeRule.activity.navigateTo(AppDestination.PointCloud) }
        composeRule.onNodeWithTag(POINT_CLOUD_SURFACE_TAG).assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithTag(POINT_CLOUD_SCREEN_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(POINT_CLOUD_SURFACE_TAG).assertIsDisplayed()
    }

    @Test
    fun datasetCreationSurvivesActivityRecreation() {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("preview_datasets", 0)
            .edit()
            .clear()
            .commit()

        composeRule.runOnUiThread { composeRule.activity.navigateTo(AppDestination.Datasets) }
        composeRule.onNodeWithText("No datasets. Create a new dataset to attach files.").assertIsDisplayed()
        composeRule.onNodeWithText("Create Dataset").performClick()
        composeRule.onNodeWithText("Dataset Assets").assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithTag(DATASETS_SCREEN_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Dataset Assets").assertIsDisplayed()
    }

    @Test
    fun geoAwarenessDialogSurvivesRecreationWithoutDuplication() {
        composeRule.runOnUiThread { composeRule.activity.navigateTo(AppDestination.GeoAwareness) }
        composeRule.onNodeWithTag(GEO_AWARENESS_SCREEN_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(GEO_AWARENESS_VERIFICATION_BUTTON_TAG)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.onAllNodesWithText("Verification checklist").assertCountEquals(1)

        composeRule.activityRule.scenario.recreate()

        composeRule.onAllNodesWithText("Verification checklist").assertCountEquals(1)
        composeRule.onNodeWithText("Verification checklist").assertIsDisplayed()
    }

    @Test
    fun homeRendersTheComposeMissionMap() {
        composeRule.onNodeWithTag(MISSION_MAP_SCREEN_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(MISSION_MAP_SURFACE_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(MISSION_MAP_STATUS_STRIP_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(MISSION_PREVIEW_RAIL_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(MISSION_BOTTOM_DOCK_TAG).assertIsDisplayed()
    }

    @Test
    fun planningPanelOpensAndSurvivesNavigationAndRecreation() {
        composeRule.onNodeWithTag(MISSION_PLANNING_DOCK_BUTTON_TAG).performClick()
        composeRule.onNodeWithTag(MISSION_WORKFLOW_PANEL_TAG).assertIsDisplayed()

        composeRule.runOnUiThread { composeRule.activity.navigateTo(AppDestination.Settings) }
        composeRule.onNodeWithTag(SETTINGS_SCREEN_TAG).assertIsDisplayed()
        composeRule.runOnUiThread { composeRule.activity.returnToHome() }
        composeRule.onNodeWithTag(MISSION_MAP_SCREEN_TAG).assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithTag(MISSION_MAP_SCREEN_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(MISSION_MAP_SURFACE_TAG).assertIsDisplayed()
    }
}
