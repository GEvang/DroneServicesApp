package com.example.droneservicesapp.ui.compose.foundation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test

class ComposeFoundationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeFoundationActivity>()

    @Test
    fun foundationSurfaceIsDisplayed() {
        composeRule
            .onNodeWithTag(COMPOSE_FOUNDATION_TEST_TAG)
            .assertIsDisplayed()
    }
}
