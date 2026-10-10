package com.example.droneservicesapp.ui.compose.navigation

import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.droneservicesapp.ui.compose.components.ComponentGalleryActivity
import com.example.droneservicesapp.ui.compose.theme.DroneServicesTheme
import com.example.droneservicesapp.ui.home.model.HomeTelemetryUiState
import com.example.droneservicesapp.ui.shell.BottomMissionAction
import com.example.droneservicesapp.ui.shell.model.BottomActionBarUiState
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ComposeShellTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentGalleryActivity>()

    @Test
    fun drawerNavigatesBetweenTypedDestinations() {
        val navigation = MutableSharedFlow<ShellNavigationRequest>(extraBufferCapacity = 1)
        setShellContent(navigationRequests = navigation)

        composeRule.onNodeWithText("Destination: home").assertIsDisplayed()
        composeRule.runOnIdle {
            navigation.tryEmit(ShellNavigationRequest.Navigate(AppDestination.Settings))
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Destination: settings").assertIsDisplayed()
        composeRule.onNodeWithTag(SHELL_NAVIGATION_BUTTON_TAG).performClick()
        composeRule.onNodeWithTag("drawer-datasets").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Destination: datasets").assertIsDisplayed()
    }

    @Test
    fun bottomMissionActionsEmitExplicitCommands() {
        val actions = mutableListOf<BottomMissionAction>()
        setShellContent(onBottomAction = actions::add)

        composeRule.onNodeWithTag(SHELL_BOTTOM_ACTIONS_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SHELL_DRAW_ACTION_TAG).performClick()
        composeRule.onNodeWithTag(SHELL_LOAD_ACTION_TAG).performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(BottomMissionAction.DRAW, BottomMissionAction.LOAD),
                actions,
            )
        }
    }

    private fun setShellContent(
        navigationRequests: MutableSharedFlow<ShellNavigationRequest> = MutableSharedFlow(extraBufferCapacity = 1),
        onBottomAction: (BottomMissionAction) -> Unit = {},
    ) {
        composeRule.activity.setContent {
            DroneServicesTheme {
                DroneServicesApp(
                    telemetryState = HomeTelemetryUiState(
                        connectionText = "Disconnected",
                        armedText = "Disarmed",
                    ),
                    bottomActionState = BottomActionBarUiState(
                        showCancel = false,
                        showAccept = false,
                        showErase = false,
                        showDraw = true,
                        showLoad = true,
                    ),
                    bottomActionBarVisible = true,
                    onBottomAction = onBottomAction,
                    navigationRequests = navigationRequests,
                    destinationContent = { destination ->
                        Text("Destination: ${destination.route}")
                    },
                )
            }
        }
    }
}
