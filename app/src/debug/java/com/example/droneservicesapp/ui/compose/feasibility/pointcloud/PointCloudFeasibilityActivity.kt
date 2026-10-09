package com.example.droneservicesapp.ui.compose.feasibility.pointcloud

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.droneservicesapp.data.pointcloud.PointCloudBounds
import com.example.droneservicesapp.data.pointcloud.PointCloudData
import com.example.droneservicesapp.ui.compose.theme.DroneServicesTheme
import com.example.droneservicesapp.ui.pointcloud.PointCloudGlView
import kotlin.math.sin

const val POINT_CLOUD_FEASIBILITY_ROOT_TAG = "point-cloud-feasibility-root"
const val POINT_CLOUD_RENDER_SURFACE_TAG = "point-cloud-render-surface"
const val POINT_CLOUD_RESET_TAG = "point-cloud-reset"

class PointCloudFeasibilityActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DroneServicesTheme { PointCloudFeasibilityScreen() }
        }
    }
}

@Composable
private fun PointCloudFeasibilityScreen() {
    val cloud = remember { createSyntheticPointCloud() }
    var glView by remember { mutableStateOf<PointCloudGlView?>(null) }
    var pointSize by remember { mutableFloatStateOf(4f) }
    var opacity by remember { mutableFloatStateOf(1f) }
    var heightColors by remember { mutableStateOf(true) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, glView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glView?.onResume()
                Lifecycle.Event.ON_PAUSE -> glView?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            glView?.onResume()
        }
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(POINT_CLOUD_FEASIBILITY_ROOT_TAG)
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .testTag(POINT_CLOUD_RENDER_SURFACE_TAG),
            factory = { context ->
                PointCloudGlView(context).also { view ->
                    view.setPointCloud(cloud)
                    glView = view
                }
            },
            update = { view ->
                view.setPointSize(pointSize)
                view.setPointCloudOpacity(opacity)
                view.setHeightColorModeEnabled(heightColors)
            },
            onRelease = { view ->
                glView = null
                view.release()
            },
        )

        Card(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .width(300.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Compose point-cloud feasibility", style = MaterialTheme.typography.titleMedium)
                Text("1,600 synthetic points", style = MaterialTheme.typography.bodySmall)
                Text("Point size: ${"%.1f".format(pointSize)}")
                Slider(value = pointSize, onValueChange = { pointSize = it }, valueRange = 1f..10f)
                Text("Opacity: ${(opacity * 100).toInt()}%")
                Slider(value = opacity, onValueChange = { opacity = it }, valueRange = 0.1f..1f)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Height colors")
                    Switch(checked = heightColors, onCheckedChange = { heightColors = it })
                    Button(
                        modifier = Modifier.testTag(POINT_CLOUD_RESET_TAG),
                        onClick = { glView?.resetCamera() }
                    ) { Text("Reset") }
                }
            }
        }
    }
}

private fun createSyntheticPointCloud(): PointCloudData {
    val side = 40
    val positions = FloatArray(side * side * 3)
    val colors = FloatArray(side * side * 3)
    var offset = 0
    for (row in 0 until side) {
        for (column in 0 until side) {
            val x = column - (side - 1) / 2f
            val y = row - (side - 1) / 2f
            val z = (sin(column * 0.25) + sin(row * 0.2)).toFloat() * 2f
            positions[offset] = x
            positions[offset + 1] = y
            positions[offset + 2] = z
            val normalizedHeight = (z + 4f) / 8f
            colors[offset] = normalizedHeight
            colors[offset + 1] = 0.7f
            colors[offset + 2] = 1f - normalizedHeight
            offset += 3
        }
    }
    return PointCloudData(
        positions = positions,
        colors = colors,
        totalPointCount = side * side,
        displayedPointCount = side * side,
        bounds = PointCloudBounds(-19.5f, 19.5f, -19.5f, 19.5f, -4f, 4f),
        hasRgb = true,
    )
}
