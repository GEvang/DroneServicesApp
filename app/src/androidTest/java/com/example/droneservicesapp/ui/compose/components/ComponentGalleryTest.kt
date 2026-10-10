package com.example.droneservicesapp.ui.compose.components

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class ComponentGalleryTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentGalleryActivity>()

    @Test
    fun coreControlsAreAccessibleAndStateDriven() {
        composeRule.onNodeWithTag(COMPONENT_GALLERY_ROOT_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(COMPONENT_GALLERY_PRIMARY_BUTTON_TAG)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithText("Status: Primary clicked").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag(COMPONENT_GALLERY_EXPANDABLE_TAG)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("Grid angle").assertIsDisplayed()
    }

    @Test
    fun confirmationDialogEmitsOnlyTheConfirmedAction() {
        composeRule.onNodeWithTag(COMPONENT_GALLERY_DIALOG_BUTTON_TAG)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("Confirm example").assertIsDisplayed()
        composeRule.onNodeWithText("Confirm").performClick()
        composeRule.onNodeWithText("Status: Confirmed").performScrollTo().assertIsDisplayed()
    }
}
