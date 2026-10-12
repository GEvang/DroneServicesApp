package com.example.droneservicesapp.ui.home.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.droneservicesapp.R
import com.example.droneservicesapp.domain.model.MissionObstacle
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.google.android.gms.maps.model.LatLng

/**
 * Forbidden-area editor: places circle obstacles with one tap, or polygon obstacles from a draft
 * of tapped points. Port of the former `OsmdroidObstacleEditor`.
 */
class ObstacleEditorState(
    private val viewModel: MainActivityViewModel,
    private val showMessage: (Int) -> Unit,
) {
    enum class Mode { IDLE, CIRCLE, POLYGON }

    var mode by mutableStateOf(Mode.IDLE)
        private set
    var obstacles by mutableStateOf<List<MissionObstacle>>(emptyList())
        private set
    var draftPoints by mutableStateOf<List<LatLng>>(emptyList())
        private set
    private var defaultRadiusMeters = 5.0

    fun startCirclePlacement(radiusMeters: Double) {
        draftPoints = emptyList()
        mode = Mode.CIRCLE
        defaultRadiusMeters = radiusMeters.coerceIn(2.0, 100.0)
    }

    fun startPolygonPlacement() {
        draftPoints = emptyList()
        mode = Mode.POLYGON
    }

    fun cancelPlacement() {
        mode = Mode.IDLE
        draftPoints = emptyList()
    }

    fun finishPolygon(): Boolean {
        if (draftPoints.size < 3) {
            showMessage(R.string.obstacle_polygon_requires_points)
            return false
        }
        viewModel.addPolygonMissionObstacle(draftPoints.toList())
        mode = Mode.IDLE
        draftPoints = emptyList()
        showMessage(R.string.obstacle_polygon_added)
        return true
    }

    fun renderObstacles(obstacles: List<MissionObstacle>) {
        this.obstacles = obstacles.toList()
    }

    /** Map tapped. Returns true when the tap placed an obstacle or a draft point. */
    fun onMapTapped(point: LatLng): Boolean = when (mode) {
        Mode.IDLE -> false
        Mode.CIRCLE -> {
            viewModel.addMissionObstacle(point.latitude, point.longitude, defaultRadiusMeters)
            mode = Mode.IDLE
            showMessage(R.string.obstacle_circle_added)
            true
        }
        Mode.POLYGON -> {
            draftPoints = draftPoints + point
            true
        }
    }

    fun release() {
        mode = Mode.IDLE
        draftPoints = emptyList()
        obstacles = emptyList()
    }
}
