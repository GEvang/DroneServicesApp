package com.example.droneservicesapp.ui.home.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.droneservicesapp.domain.survey.PolygonVertexOrderer
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.google.android.gms.maps.model.LatLng

/**
 * Mission-area polygon editor: tap the map to add a vertex, tap a vertex to remove it, and
 * long-press-drag a vertex to move it. Vertices are written to `MainActivityViewModel`.
 *
 * Port of the former `OsmdroidPolygonEditor` with identical ordering and sync behaviour.
 */
class PolygonEditorState(
    private val viewModel: MainActivityViewModel,
) {
    var vertices by mutableStateOf<List<LatLng>>(emptyList())
        private set
    var enabled by mutableStateOf(false)
        private set

    // The property enabled already owns the JVM name setEnabled.
    @JvmName("applyEnabled")
    fun setEnabled(isEnabled: Boolean) {
        enabled = isEnabled
    }

    fun clear() {
        vertices = emptyList()
        // Clear the pure model too.
        viewModel.missionArea.value?.clearAll()
        syncModel()
    }

    /** Mirrors model vertices into the editor without writing them back. */
    // The property vertices already owns the JVM name setVertices.
    @JvmName("applyVertices")
    fun setVertices(next: List<LatLng>) {
        if (next == vertices) return
        vertices = ordered(next.toList())
    }

    /** Map tapped. Returns true when a vertex was added. */
    fun onMapTapped(point: LatLng): Boolean {
        if (!enabled) return false
        vertices = ordered(vertices + point)
        syncModel()
        return true
    }

    /** Vertex marker tapped. Returns false when editing is disabled so the tap falls through. */
    fun onVertexClicked(index: Int): Boolean {
        if (!enabled || index !in vertices.indices) return false
        vertices = vertices.filterIndexed { i, _ -> i != index }
        syncModel()
        return true
    }

    fun onVertexMoved(index: Int, point: LatLng) {
        if (!enabled || index !in vertices.indices) return
        vertices = ordered(vertices.mapIndexed { i, vertex -> if (i == index) point else vertex })
        syncModel()
    }

    private fun ordered(points: List<LatLng>): List<LatLng> {
        if (points.size < 4) return points
        val order = PolygonVertexOrderer.orderNonCrossingIndices(points)
        if (order == points.indices.toList()) return points
        return order.map(points::get)
    }

    private fun syncModel() {
        // Always hand the view model a fresh list; it copies into the MissionArea's own list.
        viewModel.setPolygonVertices(vertices.toList())
    }
}
