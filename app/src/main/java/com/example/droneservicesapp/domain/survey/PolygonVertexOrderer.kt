package com.example.droneservicesapp.domain.survey

import com.google.android.gms.maps.model.LatLng

object PolygonVertexOrderer {
    fun orderNonCrossing(vertices: List<LatLng>): List<LatLng> {
        val order = orderNonCrossingIndices(vertices)
        return order.map(vertices::get)
    }

    fun orderNonCrossingIndices(vertices: List<LatLng>): List<Int> {
        if (vertices.size < 4) return vertices.indices.toList()

        val order = vertices.indices.toMutableList()
        var changed: Boolean
        var guard = 0
        val maxIterations = vertices.size * vertices.size

        do {
            changed = false
            for (i in order.indices) {
                val iNext = (i + 1) % order.size
                for (j in i + 2 until order.size) {
                    val jNext = (j + 1) % order.size
                    if (i == 0 && jNext == 0) continue

                    val a = vertices[order[i]]
                    val b = vertices[order[iNext]]
                    val c = vertices[order[j]]
                    val d = vertices[order[jNext]]

                    if (segmentsIntersect(a, b, c, d)) {
                        order.subList(iNext, j + 1).reverse()
                        changed = true
                    }
                }
            }
            guard++
        } while (changed && guard < maxIterations)

        return order
    }

    private fun segmentsIntersect(a: LatLng, b: LatLng, c: LatLng, d: LatLng): Boolean {
        val o1 = orientation(a, b, c)
        val o2 = orientation(a, b, d)
        val o3 = orientation(c, d, a)
        val o4 = orientation(c, d, b)

        return o1 * o2 < 0.0 && o3 * o4 < 0.0
    }

    private fun orientation(a: LatLng, b: LatLng, c: LatLng): Double {
        val abX = b.longitude - a.longitude
        val abY = b.latitude - a.latitude
        val acX = c.longitude - a.longitude
        val acY = c.latitude - a.latitude
        return abX * acY - abY * acX
    }
}
