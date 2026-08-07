package com.example.droneservicesapp.data.ortho

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class WorldFileParserTest {

    @Test
    fun parsesUnrotatedWorldFileBounds() {
        val tfw = """
            2.20049e-06
            0
            0
            -1.80266e-06
            24.62254619945714
            35.35824717735152
        """.trimIndent()

        val bounds = WorldFileParser().parse(
            ByteArrayInputStream(tfw.toByteArray()),
            imageWidth = 558,
            imageHeight = 522
        )

        assertEquals(24.62254509921214, bounds.minLon, 0.000000001)
        assertEquals(35.35730709016152, bounds.minLat, 0.000000001)
        assertEquals(24.62377297263214, bounds.maxLon, 0.000000001)
        assertEquals(35.35824807868152, bounds.maxLat, 0.000000001)
    }
}
