package com.example.droneservicesapp.data.ortho

import java.io.InputStream
import java.util.Locale

class WorldFileParser {

    fun parse(inputStream: InputStream, imageWidth: Int, imageHeight: Int): OrthoBounds {
        val values = inputStream.bufferedReader().use { reader ->
            reader.readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { it.toDouble() }
        }
        require(values.size >= WORLD_FILE_VALUE_COUNT) {
            "World file must contain six numeric values."
        }
        val pixelSizeX = values[0]
        val rotationY = values[1]
        val rotationX = values[2]
        val pixelSizeY = values[3]
        val topLeftCenterLon = values[4]
        val topLeftCenterLat = values[5]

        require(rotationX == 0.0 && rotationY == 0.0) {
            String.format(Locale.US, "Rotated world files are not supported yet: %.6f, %.6f", rotationX, rotationY)
        }
        require(pixelSizeX != 0.0 && pixelSizeY != 0.0) {
            "World file pixel size cannot be zero."
        }

        val left = topLeftCenterLon - pixelSizeX / 2.0
        val right = topLeftCenterLon + (imageWidth - 0.5) * pixelSizeX
        val firstRowTopEdge = topLeftCenterLat - pixelSizeY / 2.0
        val lastRowBottomEdge = topLeftCenterLat + (imageHeight - 0.5) * pixelSizeY

        return OrthoBounds(
            minLon = minOf(left, right),
            minLat = minOf(firstRowTopEdge, lastRowBottomEdge),
            maxLon = maxOf(left, right),
            maxLat = maxOf(firstRowTopEdge, lastRowBottomEdge)
        )
    }

    companion object {
        private const val WORLD_FILE_VALUE_COUNT = 6
    }
}
