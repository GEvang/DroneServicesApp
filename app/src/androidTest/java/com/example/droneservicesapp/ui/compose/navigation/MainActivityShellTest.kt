package com.example.droneservicesapp.ui.compose.navigation

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.rule.GrantPermissionRule
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
}
