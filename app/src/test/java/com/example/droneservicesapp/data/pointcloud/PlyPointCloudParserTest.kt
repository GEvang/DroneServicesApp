package com.example.droneservicesapp.data.pointcloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PlyPointCloudParserTest {

    @Test
    fun parseAsciiPlyWithRgb() {
        val ply = """
            ply
            format ascii 1.0
            element vertex 2
            property float x
            property float y
            property float z
            property uchar red
            property uchar green
            property uchar blue
            end_header
            1 2 3 255 0 128
            3 4 5 0 255 64
        """.trimIndent().toByteArray()

        val cloud = PlyPointCloudParser().parse(ply)

        assertEquals(2, cloud.totalPointCount)
        assertEquals(2, cloud.displayedPointCount)
        assertTrue(cloud.hasRgb)
        assertEquals(1f, cloud.colors[0], 0.001f)
        assertEquals(0f, cloud.colors[1], 0.001f)
        assertEquals(128f / 255f, cloud.colors[2], 0.001f)
        assertEquals(1f, cloud.bounds.minX, 0.001f)
        assertEquals(5f, cloud.bounds.maxZ, 0.001f)
        assertEquals(-1f, cloud.positions[0], 0.001f)
    }

    @Test
    fun parseBinaryLittleEndianPlyWithFlexibleProperties() {
        val header = """
            ply
            format binary_little_endian 1.0
            element vertex 2
            property float x
            property float y
            property float z
            property float nx
            property uchar red
            property uchar green
            property uchar blue
            property uchar class
            end_header
        """.trimIndent() + "\n"
        val body = ByteBuffer.allocate(2 * (4 * 4 + 4))
            .order(ByteOrder.LITTLE_ENDIAN)
            .putFloat(10f).putFloat(20f).putFloat(30f).putFloat(1f)
            .put(10).put(20).put(30).put(4)
            .putFloat(12f).putFloat(22f).putFloat(34f).putFloat(0f)
            .put(40).put(50).put(60).put(2)
            .array()

        val cloud = PlyPointCloudParser().parse(header.toByteArray() + body)

        assertEquals(2, cloud.totalPointCount)
        assertTrue(cloud.hasRgb)
        assertEquals(10f / 255f, cloud.colors[0], 0.001f)
        assertEquals(34f, cloud.bounds.maxZ, 0.001f)
    }

    @Test
    fun downSamplesDisplayedPoints() {
        val ply = """
            ply
            format ascii 1.0
            element vertex 4
            property float x
            property float y
            property float z
            end_header
            0 0 0
            1 0 0
            2 0 0
            3 0 0
        """.trimIndent().toByteArray()

        val cloud = PlyPointCloudParser(maxDisplayPoints = 2).parse(ply)

        assertEquals(4, cloud.totalPointCount)
        assertEquals(2, cloud.displayedPointCount)
        assertFalse(cloud.hasRgb)
        assertEquals(0f, cloud.bounds.minX, 0.001f)
        assertEquals(3f, cloud.bounds.maxX, 0.001f)
    }

    @Test
    fun convertsWgs84LonLatCoordinatesToLocalMeters() {
        val ply = """
            ply
            format ascii 1.0
            element vertex 2
            property float x
            property float y
            property float z
            end_header
            24.6220 35.3580 100
            24.6240 35.3600 110
        """.trimIndent().toByteArray()

        val cloud = PlyPointCloudParser().parse(ply)

        assertTrue(cloud.bounds.spanX > 150f)
        assertTrue(cloud.bounds.spanY > 200f)
        assertEquals(10f, cloud.bounds.spanZ, 0.001f)
    }
}
