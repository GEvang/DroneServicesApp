package com.example.droneservicesapp.data.pointcloud

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import kotlin.math.ceil

class PlyPointCloudParser(
    private val maxDisplayPoints: Int = DEFAULT_MAX_DISPLAY_POINTS
) {
    fun parse(inputStream: InputStream): PointCloudData {
        val bytes = inputStream.use { stream ->
            ByteArrayOutputStream().use { output ->
                stream.copyTo(output)
                output.toByteArray()
            }
        }
        return parse(bytes)
    }

    fun parse(bytes: ByteArray): PointCloudData {
        val headerEnd = findHeaderEnd(bytes)
        val headerText = bytes.copyOfRange(0, headerEnd.headerLength)
            .toString(StandardCharsets.US_ASCII)
        val header = parseHeader(headerText)
        require(header.vertexCount > 0) { "PLY has no vertices." }

        return when (header.format) {
            PlyFormat.ASCII -> parseAscii(bytes, headerEnd.bodyOffset, header)
            PlyFormat.BINARY_LITTLE_ENDIAN -> parseBinary(bytes, headerEnd.bodyOffset, header, ByteOrder.LITTLE_ENDIAN)
            PlyFormat.BINARY_BIG_ENDIAN -> parseBinary(bytes, headerEnd.bodyOffset, header, ByteOrder.BIG_ENDIAN)
        }
    }

    private fun parseBinary(
        bytes: ByteArray,
        bodyOffset: Int,
        header: PlyHeader,
        byteOrder: ByteOrder
    ): PointCloudData {
        val vertexSize = header.vertexProperties.sumOf { it.type.byteSize }
        val expectedBytes = header.vertexCount.toLong() * vertexSize
        require(bytes.size - bodyOffset >= expectedBytes) {
            "PLY body is shorter than expected for ${header.vertexCount} vertices."
        }

        val stride = displayStride(header.vertexCount)
        val displayedCount = displayedCount(header.vertexCount, stride)
        val positions = FloatArray(displayedCount * VALUES_PER_POINT)
        val colors = FloatArray(displayedCount * VALUES_PER_POINT)
        val buffer = ByteBuffer.wrap(bytes, bodyOffset, bytes.size - bodyOffset).order(byteOrder)
        val state = ParseState()

        var displayIndex = 0
        repeat(header.vertexCount) { pointIndex ->
            var x = 0f
            var y = 0f
            var z = 0f
            var red: Int? = null
            var green: Int? = null
            var blue: Int? = null

            header.vertexProperties.forEach { property ->
                when (property.name) {
                    "x" -> x = buffer.readAsFloat(property.type)
                    "y" -> y = buffer.readAsFloat(property.type)
                    "z" -> z = buffer.readAsFloat(property.type)
                    "red", "r" -> red = buffer.readAsColor(property.type)
                    "green", "g" -> green = buffer.readAsColor(property.type)
                    "blue", "b" -> blue = buffer.readAsColor(property.type)
                    else -> buffer.position(buffer.position() + property.type.byteSize)
                }
            }

            state.includeInBounds(x, y, z)
            if (pointIndex % stride == 0) {
                val offset = displayIndex * VALUES_PER_POINT
                positions[offset] = x
                positions[offset + 1] = y
                positions[offset + 2] = z
                if (red != null && green != null && blue != null) {
                    colors[offset] = red!!.coerceIn(0, 255) / 255f
                    colors[offset + 1] = green!!.coerceIn(0, 255) / 255f
                    colors[offset + 2] = blue!!.coerceIn(0, 255) / 255f
                    state.hasRgb = true
                }
                displayIndex++
            }
        }

        return buildPointCloud(positions, colors, displayIndex, header.vertexCount, state)
    }

    private fun parseAscii(bytes: ByteArray, bodyOffset: Int, header: PlyHeader): PointCloudData {
        val body = bytes.copyOfRange(bodyOffset, bytes.size).toString(StandardCharsets.UTF_8)
        val propNames = header.vertexProperties.map { it.name }
        val xIndex = propNames.indexOf("x")
        val yIndex = propNames.indexOf("y")
        val zIndex = propNames.indexOf("z")
        val redIndex = firstExistingIndex(propNames, "red", "r")
        val greenIndex = firstExistingIndex(propNames, "green", "g")
        val blueIndex = firstExistingIndex(propNames, "blue", "b")
        val stride = displayStride(header.vertexCount)
        val displayedCount = displayedCount(header.vertexCount, stride)
        val positions = FloatArray(displayedCount * VALUES_PER_POINT)
        val colors = FloatArray(displayedCount * VALUES_PER_POINT)
        val state = ParseState()
        var pointIndex = 0
        var displayIndex = 0

        for (line in body.lineSequence()) {
            if (pointIndex >= header.vertexCount) break
            val values = line.trim().split(Regex("\\s+"))
            if (values.size < header.vertexProperties.size) continue
            val x = values[xIndex].toFloat()
            val y = values[yIndex].toFloat()
            val z = values[zIndex].toFloat()
            state.includeInBounds(x, y, z)

            if (pointIndex % stride == 0) {
                val offset = displayIndex * VALUES_PER_POINT
                positions[offset] = x
                positions[offset + 1] = y
                positions[offset + 2] = z
                if (redIndex >= 0 && greenIndex >= 0 && blueIndex >= 0) {
                    colors[offset] = values[redIndex].toFloat().coerceIn(0f, 255f) / 255f
                    colors[offset + 1] = values[greenIndex].toFloat().coerceIn(0f, 255f) / 255f
                    colors[offset + 2] = values[blueIndex].toFloat().coerceIn(0f, 255f) / 255f
                    state.hasRgb = true
                }
                displayIndex++
            }
            pointIndex++
        }

        require(pointIndex == header.vertexCount) {
            "PLY contains $pointIndex vertices, expected ${header.vertexCount}."
        }
        return buildPointCloud(positions, colors, displayIndex, header.vertexCount, state)
    }

    private fun buildPointCloud(
        positions: FloatArray,
        colors: FloatArray,
        displayedCount: Int,
        totalCount: Int,
        state: ParseState
    ): PointCloudData {
        val conversion = if (state.isLikelyWgs84()) {
            convertWgs84PositionsToLocalMeters(positions, displayedCount, state)
        } else {
            PointCloudConversion(state.toBounds(), null)
        }
        val bounds = conversion.bounds
        centerPositions(positions, displayedCount, bounds)
        if (!state.hasRgb) {
            applyHeightColors(positions, colors, displayedCount)
        }
        return PointCloudData(
            positions = positions.copyOf(displayedCount * VALUES_PER_POINT),
            colors = colors.copyOf(displayedCount * VALUES_PER_POINT),
            totalPointCount = totalCount,
            displayedPointCount = displayedCount,
            bounds = bounds,
            hasRgb = state.hasRgb,
            coordinateFrame = conversion.coordinateFrame
        )
    }

    private fun convertWgs84PositionsToLocalMeters(
        positions: FloatArray,
        displayedCount: Int,
        state: ParseState
    ): PointCloudConversion {
        val lon0 = state.centerX
        val lat0 = state.centerY
        val cosLat = kotlin.math.cos(Math.toRadians(lat0.toDouble())).toFloat()
        repeat(displayedCount) { index ->
            val offset = index * VALUES_PER_POINT
            val lon = positions[offset]
            val lat = positions[offset + 1]
            positions[offset] = (lon - lon0) * METERS_PER_DEGREE * cosLat
            positions[offset + 1] = (lat - lat0) * METERS_PER_DEGREE
        }
        val minLocalX = (state.minX - lon0) * METERS_PER_DEGREE * cosLat
        val maxLocalX = (state.maxX - lon0) * METERS_PER_DEGREE * cosLat
        val minLocalY = (state.minY - lat0) * METERS_PER_DEGREE
        val maxLocalY = (state.maxY - lat0) * METERS_PER_DEGREE
        val bounds = PointCloudBounds(
            minX = minOf(minLocalX, maxLocalX),
            maxX = maxOf(minLocalX, maxLocalX),
            minY = minOf(minLocalY, maxLocalY),
            maxY = maxOf(minLocalY, maxLocalY),
            minZ = state.minZ,
            maxZ = state.maxZ
        )
        val centeredOriginLat = lat0 + bounds.centerY / METERS_PER_DEGREE
        val centeredOriginLon = lon0 + bounds.centerX / (METERS_PER_DEGREE * cosLat.coerceAtLeast(1e-6f))
        val centeredOriginAlt = bounds.centerZ.toDouble()
        return PointCloudConversion(
            bounds = bounds,
            coordinateFrame = PointCloudCoordinateFrame(
                originLat = centeredOriginLat.toDouble(),
                originLon = centeredOriginLon.toDouble(),
                originAltMeters = centeredOriginAlt
            )
        )
    }

    private fun centerPositions(positions: FloatArray, displayedCount: Int, bounds: PointCloudBounds) {
        repeat(displayedCount) { index ->
            val offset = index * VALUES_PER_POINT
            positions[offset] -= bounds.centerX
            positions[offset + 1] -= bounds.centerY
            positions[offset + 2] -= bounds.centerZ
        }
    }

    private fun applyHeightColors(positions: FloatArray, colors: FloatArray, displayedCount: Int) {
        if (displayedCount == 0) return
        var minZ = Float.POSITIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY
        repeat(displayedCount) { index ->
            val z = positions[index * VALUES_PER_POINT + 2]
            if (z < minZ) minZ = z
            if (z > maxZ) maxZ = z
        }
        val range = (maxZ - minZ).coerceAtLeast(0.1f)
        repeat(displayedCount) { index ->
            val offset = index * VALUES_PER_POINT
            val t = ((positions[offset + 2] - minZ) / range).coerceIn(0f, 1f)
            colors[offset] = t
            colors[offset + 1] = 1f - kotlin.math.abs(t - 0.5f)
            colors[offset + 2] = 1f - t
        }
    }

    private fun findHeaderEnd(bytes: ByteArray): HeaderEnd {
        val marker = "end_header".toByteArray(StandardCharsets.US_ASCII)
        val index = bytes.indexOf(marker)
        require(index >= 0) { "Not a valid PLY file." }
        var bodyOffset = index + marker.size
        if (bodyOffset < bytes.size && bytes[bodyOffset] == '\r'.code.toByte()) bodyOffset++
        if (bodyOffset < bytes.size && bytes[bodyOffset] == '\n'.code.toByte()) bodyOffset++
        return HeaderEnd(bodyOffset, bodyOffset)
    }

    private fun parseHeader(headerText: String): PlyHeader {
        require(headerText.lineSequence().firstOrNull()?.trim() == "ply") { "Not a valid PLY file." }
        var format: PlyFormat? = null
        var vertexCount = 0
        var inVertex = false
        val vertexProperties = mutableListOf<PlyProperty>()

        headerText.lineSequence().forEach { rawLine ->
            val parts = rawLine.trim().split(Regex("\\s+"))
            when {
                parts.size >= 3 && parts[0] == "format" -> {
                    format = when (parts[1]) {
                        "ascii" -> PlyFormat.ASCII
                        "binary_little_endian" -> PlyFormat.BINARY_LITTLE_ENDIAN
                        "binary_big_endian" -> PlyFormat.BINARY_BIG_ENDIAN
                        else -> error("Unsupported PLY format: ${parts[1]}")
                    }
                }
                parts.size >= 3 && parts[0] == "element" -> {
                    inVertex = parts[1] == "vertex"
                    if (inVertex) vertexCount = parts[2].toInt()
                }
                inVertex && parts.size >= 3 && parts[0] == "property" -> {
                    require(parts[1] != "list") { "PLY vertex list properties are not supported." }
                    val type = PlyScalarType.fromName(parts[1])
                    vertexProperties += PlyProperty(parts[2], type)
                }
            }
        }

        val names = vertexProperties.map { it.name }
        require("x" in names && "y" in names && "z" in names) {
            "PLY is missing x/y/z vertex properties."
        }
        return PlyHeader(
            format = requireNotNull(format) { "PLY format is missing." },
            vertexCount = vertexCount,
            vertexProperties = vertexProperties
        )
    }

    private fun ByteBuffer.readAsFloat(type: PlyScalarType): Float = when (type) {
        PlyScalarType.FLOAT -> float
        PlyScalarType.DOUBLE -> double.toFloat()
        PlyScalarType.INT8 -> get().toFloat()
        PlyScalarType.UINT8 -> (get().toInt() and 0xFF).toFloat()
        PlyScalarType.INT16 -> short.toFloat()
        PlyScalarType.UINT16 -> (short.toInt() and 0xFFFF).toFloat()
        PlyScalarType.INT32 -> int.toFloat()
        PlyScalarType.UINT32 -> (int.toLong() and 0xFFFFFFFFL).toFloat()
    }

    private fun ByteBuffer.readAsColor(type: PlyScalarType): Int = readAsFloat(type).toInt()

    private fun displayStride(totalCount: Int): Int =
        if (maxDisplayPoints <= 0 || totalCount <= maxDisplayPoints) 1 else ceil(totalCount / maxDisplayPoints.toDouble()).toInt()

    private fun displayedCount(totalCount: Int, stride: Int): Int = ((totalCount - 1) / stride) + 1

    private fun firstExistingIndex(names: List<String>, vararg candidates: String): Int =
        candidates.firstNotNullOfOrNull { candidate ->
            names.indexOf(candidate).takeIf { it >= 0 }
        } ?: -1

    private fun ByteArray.indexOf(needle: ByteArray): Int {
        if (needle.isEmpty()) return 0
        for (index in 0..size - needle.size) {
            var matched = true
            for (needleIndex in needle.indices) {
                if (this[index + needleIndex] != needle[needleIndex]) {
                    matched = false
                    break
                }
            }
            if (matched) return index
        }
        return -1
    }

    private data class HeaderEnd(val headerLength: Int, val bodyOffset: Int)

    private data class PlyHeader(
        val format: PlyFormat,
        val vertexCount: Int,
        val vertexProperties: List<PlyProperty>
    )

    private data class PointCloudConversion(
        val bounds: PointCloudBounds,
        val coordinateFrame: PointCloudCoordinateFrame?
    )

    private data class PlyProperty(val name: String, val type: PlyScalarType)

    private enum class PlyFormat {
        ASCII,
        BINARY_LITTLE_ENDIAN,
        BINARY_BIG_ENDIAN
    }

    private enum class PlyScalarType(val byteSize: Int, vararg val names: String) {
        FLOAT(4, "float", "float32"),
        DOUBLE(8, "double", "float64"),
        INT8(1, "char", "int8"),
        UINT8(1, "uchar", "uint8"),
        INT16(2, "short", "int16"),
        UINT16(2, "ushort", "uint16"),
        INT32(4, "int", "int32"),
        UINT32(4, "uint", "uint32");

        companion object {
            fun fromName(name: String): PlyScalarType =
                values().firstOrNull { type -> name.lowercase() in type.names }
                    ?: error("Unsupported PLY property type: $name")
        }
    }

    private class ParseState {
        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY
        var hasRgb = false
        val centerX: Float get() = (minX + maxX) / 2f
        val centerY: Float get() = (minY + maxY) / 2f

        fun includeInBounds(x: Float, y: Float, z: Float) {
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
            if (z < minZ) minZ = z
            if (z > maxZ) maxZ = z
        }

        fun toBounds(): PointCloudBounds = PointCloudBounds(minX, maxX, minY, maxY, minZ, maxZ)

        fun isLikelyWgs84(): Boolean {
            val xSpan = maxX - minX
            val ySpan = maxY - minY
            return minX >= -180f &&
                maxX <= 180f &&
                minY >= -90f &&
                maxY <= 90f &&
                kotlin.math.abs(centerY) > 0.1f &&
                xSpan > 0f &&
                ySpan > 0f &&
                xSpan < 1f &&
                ySpan < 1f
        }
    }

    companion object {
        const val DEFAULT_MAX_DISPLAY_POINTS = 500_000
        private const val VALUES_PER_POINT = 3
        private const val METERS_PER_DEGREE = 111_320f
    }
}
