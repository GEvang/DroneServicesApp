package com.example.droneservicesapp.ui.compose.feasibility.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.droneservicesapp.data.ortho.OrthoBounds
import com.example.droneservicesapp.ui.compose.theme.DroneServicesTheme
import com.example.droneservicesapp.ui.home.components.EsriMapLayers
import com.example.droneservicesapp.ui.ortho.OrthoImageOverlay
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

const val MAP_FEASIBILITY_ROOT_TAG = "map-feasibility-root"
const val MAP_RENDER_SURFACE_TAG = "map-render-surface"

private val prototypeCenter = GeoPoint(37.9838, 23.7275)

class MapFeasibilityActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DroneServicesTheme { MapFeasibilityScreen() }
        }
    }
}

private data class MapResources(
    val layers: EsriMapLayers,
    val orthoBitmap: Bitmap,
    val fixedOverlays: List<Overlay>,
    var dynamicOverlays: List<Overlay> = emptyList(),
)

@Composable
private fun MapFeasibilityScreen() {
    val context = LocalContext.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var resources by remember { mutableStateOf<MapResources?>(null) }
    var status by remember { mutableStateOf("Long-press the map to add a vertex") }
    var vertices by remember {
        mutableStateOf(
            listOf(
                GeoPoint(37.9870, 23.7215),
                GeoPoint(37.9874, 23.7330),
                GeoPoint(37.9795, 23.7340),
                GeoPoint(37.9790, 23.7220),
            )
        )
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mapView?.onResume()
        }
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(MAP_FEASIBILITY_ROOT_TAG)
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .testTag(MAP_RENDER_SURFACE_TAG),
            factory = { context ->
                MapView(context).also { view ->
                    view.setMultiTouchControls(true)
                    view.controller.setZoom(14.5)
                    view.controller.setCenter(prototypeCenter)
                    val layers = EsriMapLayers.install(context, view)
                    val bitmap = createPrototypeOrthoBitmap()
                    val fixed = listOf(
                        MapEventsOverlay(object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(point: GeoPoint): Boolean {
                                status = "Map tap: %.5f, %.5f".format(point.latitude, point.longitude)
                                return true
                            }

                            override fun longPressHelper(point: GeoPoint): Boolean {
                                vertices = vertices + GeoPoint(point.latitude, point.longitude)
                                status = "Vertex added (${vertices.size})"
                                return true
                            }
                        }),
                        OrthoImageOverlay(
                            bitmap,
                            OrthoBounds(23.7240, 37.9810, 23.7290, 37.9850)
                        ),
                        Polygon(view).apply {
                            points = listOf(
                                GeoPoint(37.9860, 23.7205),
                                GeoPoint(37.9890, 23.7270),
                                GeoPoint(37.9855, 23.7300),
                            )
                            fillColor = Color.argb(65, 255, 193, 7)
                            outlinePaint.color = Color.rgb(255, 193, 7)
                            title = "Terrain coverage"
                        },
                        Marker(view).apply {
                            position = GeoPoint(37.9820, 23.7310)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = "Obstacle"
                        },
                    )
                    view.overlays.addAll(fixed)
                    layers.bringAttributionToFront()
                    resources = MapResources(layers, bitmap, fixed)
                    mapView = view
                }
            },
            update = { view ->
                val current = resources ?: return@AndroidView
                view.overlays.removeAll(current.dynamicOverlays.toSet())
                val polygon = Polygon(view).apply {
                    points = vertices.map { GeoPoint(it.latitude, it.longitude) }
                    fillColor = Color.argb(55, 0, 200, 255)
                    outlinePaint.color = Color.CYAN
                    outlinePaint.strokeWidth = 4f
                    title = "Editable mission area"
                }
                val route = Polyline(view).apply {
                    setPoints(vertices.map { GeoPoint(it.latitude, it.longitude) })
                    outlinePaint.color = Color.MAGENTA
                    outlinePaint.strokeWidth = 5f
                }
                val handles = vertices.mapIndexed { index, vertex ->
                    Marker(view).apply {
                        position = GeoPoint(vertex.latitude, vertex.longitude)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        title = "Vertex ${index + 1}"
                        isDraggable = true
                        setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                            override fun onMarkerDrag(marker: Marker) = Unit
                            override fun onMarkerDragStart(marker: Marker) = Unit
                            override fun onMarkerDragEnd(marker: Marker) {
                                vertices = vertices.toMutableList().also {
                                    it[index] = GeoPoint(marker.position.latitude, marker.position.longitude)
                                }
                                status = "Vertex ${index + 1} moved"
                            }
                        })
                    }
                }
                current.dynamicOverlays = listOf(polygon, route) + handles
                view.overlays.addAll(current.dynamicOverlays)
                current.layers.bringAttributionToFront()
                view.invalidate()
            },
            onRelease = { view ->
                view.onDetach()
                resources?.orthoBitmap?.recycle()
                resources = null
                mapView = null
            },
        )

        Card(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Compose map feasibility", style = MaterialTheme.typography.titleMedium)
                Text(status, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        mapView?.controller?.animateTo(prototypeCenter, 14.5, 500L)
                        status = "Camera centered"
                    }) { Text("Center") }
                    Button(onClick = {
                        val view = mapView
                        if (view != null) {
                            status = "Offline cache started for the visible area"
                            CacheManager(view).downloadAreaAsync(
                                context,
                                view.boundingBox,
                                14,
                                14,
                                object : CacheManager.CacheManagerCallback {
                                    override fun downloadStarted() = Unit
                                    override fun setPossibleTilesInArea(total: Int) = Unit
                                    override fun updateProgress(
                                        progress: Int,
                                        currentZoomLevel: Int,
                                        zoomMin: Int,
                                        zoomMax: Int,
                                    ) = Unit

                                    override fun onTaskComplete() {
                                        view.post { status = "Offline cache complete" }
                                    }

                                    override fun onTaskFailed(errors: Int) {
                                        view.post { status = "Offline cache failed ($errors errors)" }
                                    }
                                }
                            )
                        }
                    }) { Text("Cache view") }
                }
            }
        }
    }
}

private fun createPrototypeOrthoBitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply { alpha = 180 }
    val size = 32
    for (row in 0 until 4) {
        for (column in 0 until 4) {
            paint.color = if ((row + column) % 2 == 0) Color.GREEN else Color.BLUE
            canvas.drawRect(
                (column * size).toFloat(),
                (row * size).toFloat(),
                ((column + 1) * size).toFloat(),
                ((row + 1) * size).toFloat(),
                paint,
            )
        }
    }
    return bitmap
}
