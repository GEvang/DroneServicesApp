package com.example.droneservicesapp.ui.compose.foundation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.droneservicesapp.R
import com.example.droneservicesapp.ui.compose.preview.DroneServicesDevicePreviews
import com.example.droneservicesapp.ui.compose.theme.DroneServicesTheme
import com.example.droneservicesapp.ui.compose.theme.DroneServicesThemeTokens

const val COMPOSE_FOUNDATION_TEST_TAG = "compose-foundation-surface"

@Composable
fun ComposeFoundationSurface(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag(COMPOSE_FOUNDATION_TEST_TAG),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.padding(DroneServicesThemeTokens.spacing.extraLarge),
            verticalArrangement = Arrangement.spacedBy(DroneServicesThemeTokens.spacing.large),
        ) {
            Text(
                text = stringResource(R.string.compose_foundation_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                ),
            ) {
                Text(
                    text = stringResource(R.string.compose_foundation_ready),
                    modifier = Modifier.padding(DroneServicesThemeTokens.spacing.large),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@DroneServicesDevicePreviews
@Composable
private fun ComposeFoundationPreview() {
    DroneServicesTheme {
        ComposeFoundationSurface()
    }
}
