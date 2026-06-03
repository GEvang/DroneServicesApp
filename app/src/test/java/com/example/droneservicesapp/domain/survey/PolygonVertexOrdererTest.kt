package com.example.droneservicesapp.domain.survey

import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PolygonVertexOrdererTest {
    @Test
    fun orderNonCrossing_uncrossesHourglassSquare() {
        val topLeft = LatLng(1.0, 0.0)
        val topRight = LatLng(1.0, 1.0)
        val bottomLeft = LatLng(0.0, 0.0)
        val bottomRight = LatLng(0.0, 1.0)

        val ordered = PolygonVertexOrderer.orderNonCrossing(
            listOf(topLeft, topRight, bottomLeft, bottomRight)
        )

        assertEquals(listOf(topLeft, topRight, bottomRight, bottomLeft), ordered)
        assertFalse(hasCrossingEdges(ordered))
    }

    @Test
    fun orderNonCrossing_keepsAlreadyValidSquareOrder() {
        val topLeft = LatLng(1.0, 0.0)
        val topRight = LatLng(1.0, 1.0)
        val bottomRight = LatLng(0.0, 1.0)
        val bottomLeft = LatLng(0.0, 0.0)
        val vertices = listOf(topLeft, topRight, bottomRight, bottomLeft)

        val ordered = PolygonVertexOrderer.orderNonCrossing(vertices)

        assertEquals(vertices, ordered)
        assertFalse(hasCrossingEdges(ordered))
    }

    private fun hasCrossingEdges(vertices: List<LatLng>): Boolean {
        for (i in vertices.indices) {
            val iNext = (i + 1) % vertices.size
            for (j in i + 2 until vertices.size) {
                val jNext = (j + 1) % vertices.size
                if (i == 0 && jNext == 0) continue
                if (segmentsIntersect(vertices[i], vertices[iNext], vertices[j], vertices[jNext])) {
                    return true
                }
            }
        }
        return false
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
