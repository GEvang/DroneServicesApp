package com.example.droneservicesapp.data.pointcloud

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.security.MessageDigest
import java.util.concurrent.CancellationException

enum class PointCloudDetailLevel(val maxDisplayPoints: Int) {
    FAST(200_000),
    BALANCED(500_000),
    HIGH(1_000_000);

    companion object {
        fun fromStored(value: String?): PointCloudDetailLevel =
            values().firstOrNull { it.name == value } ?: BALANCED
    }
}

data class PointCloudLoadProgress(
    val percent: Int?,
    val message: String
)

/**
 * Keeps a compact, app-native copy of parsed point clouds. The cache is keyed by the source URI,
 * reported source size, and selected detail level, so reopening the same dataset avoids parsing it again.
 */
class PointCloudImportCache(private val context: Context) {

    fun load(
        uri: Uri,
        fileName: String,
        detailLevel: PointCloudDetailLevel = PointCloudDetailLevel.BALANCED,
        onProgress: (PointCloudLoadProgress) -> Unit = {},
        isCancelled: () -> Boolean = { false }
    ): PointCloudData {
        val sourceSize = querySize(uri)
        val cacheFile = File(cacheDirectory(), cacheKey(uri, fileName, sourceSize, detailLevel) + CACHE_SUFFIX)
        if (cacheFile.isFile) {
            try {
                onProgress(PointCloudLoadProgress(0, "Opening cached 3D data…"))
                return readCache(cacheFile, onProgress, isCancelled)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                cacheFile.delete()
            }
        }

        onProgress(PointCloudLoadProgress(0, "Reading $fileName…"))
        val pointCloud = context.contentResolver.openInputStream(uri)?.use { source ->
            ProgressInputStream(source, sourceSize, onProgress, isCancelled).use { input ->
                PlyPointCloudParser(detailLevel.maxDisplayPoints).parse(input, fileName)
            }
        } ?: error("Could not open file.")

        checkCancelled(isCancelled)
        onProgress(PointCloudLoadProgress(null, "Saving fast-load cache…"))
        writeCache(cacheFile, pointCloud, isCancelled)
        onProgress(PointCloudLoadProgress(100, "3D data ready"))
        return pointCloud
    }

    private fun readCache(
        file: File,
        onProgress: (PointCloudLoadProgress) -> Unit,
        isCancelled: () -> Boolean
    ): PointCloudData = DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
        require(input.readInt() == CACHE_MAGIC) { "Invalid point-cloud cache." }
        require(input.readInt() == CACHE_VERSION) { "Unsupported point-cloud cache." }
        val totalPointCount = input.readInt()
        val displayedPointCount = input.readInt()
        val bounds = PointCloudBounds(
            minX = input.readFloat(), maxX = input.readFloat(),
            minY = input.readFloat(), maxY = input.readFloat(),
            minZ = input.readFloat(), maxZ = input.readFloat()
        )
        val hasRgb = input.readBoolean()
        val frame = if (input.readBoolean()) {
            PointCloudCoordinateFrame(input.readDouble(), input.readDouble(), input.readDouble())
        } else {
            null
        }
        val positions = readFloatArray(input, isCancelled)
        val colors = readFloatArray(input, isCancelled)
        onProgress(PointCloudLoadProgress(100, "Cached 3D data ready"))
        PointCloudData(positions, colors, totalPointCount, displayedPointCount, bounds, hasRgb, frame)
    }

    private fun writeCache(file: File, pointCloud: PointCloudData, isCancelled: () -> Boolean) {
        val temporary = File(file.parentFile, "${file.name}.tmp")
        try {
            DataOutputStream(BufferedOutputStream(FileOutputStream(temporary))).use { output ->
                output.writeInt(CACHE_MAGIC)
                output.writeInt(CACHE_VERSION)
                output.writeInt(pointCloud.totalPointCount)
                output.writeInt(pointCloud.displayedPointCount)
                output.writeFloat(pointCloud.bounds.minX); output.writeFloat(pointCloud.bounds.maxX)
                output.writeFloat(pointCloud.bounds.minY); output.writeFloat(pointCloud.bounds.maxY)
                output.writeFloat(pointCloud.bounds.minZ); output.writeFloat(pointCloud.bounds.maxZ)
                output.writeBoolean(pointCloud.hasRgb)
                output.writeBoolean(pointCloud.coordinateFrame != null)
                pointCloud.coordinateFrame?.let { frame ->
                    output.writeDouble(frame.originLat)
                    output.writeDouble(frame.originLon)
                    output.writeDouble(frame.originAltMeters)
                }
                writeFloatArray(output, pointCloud.positions, isCancelled)
                writeFloatArray(output, pointCloud.colors, isCancelled)
            }
            if (!temporary.renameTo(file)) {
                file.delete()
                require(temporary.renameTo(file)) { "Could not save point-cloud cache." }
            }
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }

    private fun readFloatArray(input: DataInputStream, isCancelled: () -> Boolean): FloatArray {
        val size = input.readInt()
        require(size >= 0 && size <= MAX_ARRAY_VALUES) { "Invalid point-cloud cache array." }
        return FloatArray(size).also { values ->
            values.indices.forEach { index ->
                if (index % CANCELLATION_CHECK_INTERVAL == 0) checkCancelled(isCancelled)
                values[index] = input.readFloat()
            }
        }
    }

    private fun writeFloatArray(output: DataOutputStream, values: FloatArray, isCancelled: () -> Boolean) {
        output.writeInt(values.size)
        values.forEachIndexed { index, value ->
            if (index % CANCELLATION_CHECK_INTERVAL == 0) checkCancelled(isCancelled)
            output.writeFloat(value)
        }
    }

    private fun cacheDirectory(): File = File(context.filesDir, CACHE_DIRECTORY).also { directory ->
        directory.mkdirs()
        val files = directory.listFiles()?.sortedByDescending(File::lastModified).orEmpty()
        var retainedBytes = 0L
        files.forEach { file ->
            retainedBytes += file.length()
            if (retainedBytes > MAX_CACHE_BYTES || file.lastModified() < System.currentTimeMillis() - MAX_CACHE_AGE_MS) {
                file.delete()
            }
        }
    }

    private fun cacheKey(uri: Uri, fileName: String, sourceSize: Long?, detailLevel: PointCloudDetailLevel): String {
        val source = "${uri}|$fileName|${sourceSize ?: -1}|${detailLevel.name}"
        return MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun querySize(uri: Uri): Long? = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) cursor.getLong(index) else null
    }

    private fun checkCancelled(isCancelled: () -> Boolean) {
        if (isCancelled()) throw CancellationException("Point-cloud import cancelled.")
    }

    private class ProgressInputStream(
        input: java.io.InputStream,
        private val totalBytes: Long?,
        private val onProgress: (PointCloudLoadProgress) -> Unit,
        private val isCancelled: () -> Boolean
    ) : FilterInputStream(input) {
        private var bytesRead = 0L
        private var lastPercent = -1

        override fun read(): Int {
            throwIfCancelled()
            return super.read().also { if (it >= 0) reportProgress(1) }
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            throwIfCancelled()
            return super.read(buffer, offset, length).also { count -> if (count > 0) reportProgress(count) }
        }

        private fun reportProgress(read: Int) {
            bytesRead += read
            val total = totalBytes ?: return
            val percent = ((bytesRead * 100L) / total.coerceAtLeast(1L)).toInt().coerceIn(0, 99)
            if (percent != lastPercent) {
                lastPercent = percent
                onProgress(PointCloudLoadProgress(percent, "Reading 3D data: $percent%"))
            }
        }

        private fun throwIfCancelled() {
            if (isCancelled()) throw CancellationException("Point-cloud import cancelled.")
        }
    }

    companion object {
        private const val CACHE_DIRECTORY = "pointcloud-cache"
        private const val CACHE_SUFFIX = ".pcache"
        private const val CACHE_MAGIC = 0x50434348
        private const val CACHE_VERSION = 1
        private const val MAX_ARRAY_VALUES = 6_000_000
        private const val CANCELLATION_CHECK_INTERVAL = 4096
        private const val MAX_CACHE_BYTES = 512L * 1024L * 1024L
        private const val MAX_CACHE_AGE_MS = 30L * 24L * 60L * 60L * 1000L
    }
}
